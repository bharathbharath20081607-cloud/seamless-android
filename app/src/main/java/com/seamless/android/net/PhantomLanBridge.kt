package com.seamless.android.net

import android.content.Context
import android.util.Base64
import android.util.Log
import com.seamless.android.crypto.AegisTorCrypto
import com.seamless.android.crypto.X25519
import com.seamless.android.identity.SecureIdentityStateManager
import com.seamless.android.ui.privacy.PrivacyPreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Phantom-LAN™ Zero-Knowledge Blinded Local Bypass.
 *
 * Emits and listens for 44-byte static-DH keyed AES-256-GCM UDP beacons across active
 * Wi-Fi / hotspot subnets on port 18889. Beacons contain no .onion address or public key
 * and are indistinguishable from uniform random noise to any non-contact device on the LAN.
 * When a paired contact responds, direct TCP traffic upgrades from multi-hop Tor to ~1.5 ms
 * E2EE LAN sockets — remaining operational even during total ISP / WAN blackouts unless
 * strict "Always Route Calls Over Tor" mode is active.
 */
class PhantomLanBridge(
    private val context: Context,
    private val contactManager: OnionContactManager,
    private val onIncomingLanSocket: suspend (Socket) -> Unit,
    private val onPeerDiscoveredOnLan: (canonicalOnion: String) -> Unit
) {
    companion object {
        private const val TAG = "PhantomLanBridge"
        const val DISCOVERY_PORT = 18889
        private const val NONCE_BYTES = 12
        private const val PLAINTEXT_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = 16
        private const val BEACON_BYTES = NONCE_BYTES + PLAINTEXT_BYTES + GCM_TAG_BYTES // 44 bytes
        private val BEACON_MAGIC = byteArrayOf(0x50, 0x4C, 0x41, 0x4E) // "PLAN"
        private val BEACON_AAD = "SPHL1".toByteArray(Charsets.UTF_8)
        private val HKDF_SALT = "SeamlessPhantomLan/v1".toByteArray(Charsets.UTF_8)
        private val HKDF_LABEL = "PhantomLanBeacon".toByteArray(Charsets.UTF_8)

        private const val ENDPOINT_TTL_MS = 45_000L
        private const val BROADCAST_INTERVAL_MS = 12_000L
        private const val UNICAST_ECHO_COOLDOWN_MS = 5_000L
        private const val MAX_CLOCK_SKEW_MS = 120_000L
        private const val MAX_REPLAY_NONCES = 256
    }

    private data class VerifiedEndpoint(
        val address: InetSocketAddress,
        val verifiedAtMs: Long
    )

    private data class PairwiseBeaconKeys(
        val txKey: SecretKeySpec,
        val rxKey: SecretKeySpec
    )

    private data class LanInterfaceSnapshot(
        val localAddresses: Set<InetAddress>,
        val broadcastAddresses: List<InetAddress>,
        val hasUsableLan: Boolean
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)
    private val secureRandom = SecureRandom()

    @Volatile private var tcpServer: ServerSocket? = null
    @Volatile private var localTcpPort: Int = 0
    @Volatile private var udpSocket: DatagramSocket? = null
    @Volatile private var cachedLocalStaticKeys: Pair<ByteArray, ByteArray>? = null

    private var acceptJob: Job? = null
    private var udpRecvJob: Job? = null
    private var udpBroadcastJob: Job? = null

    private val verifiedEndpoints = ConcurrentHashMap<String, VerifiedEndpoint>()
    private val pairwiseKeys = ConcurrentHashMap<String, PairwiseBeaconKeys>()
    private val lastUnicastEchoAt = ConcurrentHashMap<String, Long>()
    private val replayLock = Any()
    private val seenNonces = LinkedHashSet<String>()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (isStrictTorOnly()) {
            verifiedEndpoints.clear()
            Log.i(TAG, "Phantom-LAN standby — strict Tor-only privacy mode is active")
        }

        try {
            val server = ServerSocket(0).apply {
                reuseAddress = true
            }
            tcpServer = server
            localTcpPort = server.localPort
            acceptJob = scope.launch { acceptLoop(server) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start Phantom-LAN TCP listener: ${e.message}")
            running.set(false)
            return
        }

        try {
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            udpSocket = sock
            udpRecvJob = scope.launch { udpReceiveLoop(sock) }
            udpBroadcastJob = scope.launch { udpBroadcastLoop(sock) }
            Log.i(TAG, "⚡ Phantom-LAN bridge active (TCP=$localTcpPort, UDP=$DISCOVERY_PORT)")
        } catch (e: Exception) {
            Log.w(TAG, "Phantom-LAN UDP discovery socket unavailable on $DISCOVERY_PORT: ${e.message}")
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { tcpServer?.close() }
        runCatching { udpSocket?.close() }
        tcpServer = null
        udpSocket = null
        localTcpPort = 0

        acceptJob?.cancel()
        udpRecvJob?.cancel()
        udpBroadcastJob?.cancel()
        acceptJob = null
        udpRecvJob = null
        udpBroadcastJob = null

        verifiedEndpoints.clear()
        pairwiseKeys.clear()
        lastUnicastEchoAt.clear()
        synchronized(replayLock) {
            seenNonces.clear()
        }
        cachedLocalStaticKeys?.let {
            AegisTorCrypto.zeroize(it.first, it.second)
        }
        cachedLocalStaticKeys = null
        Log.i(TAG, "Phantom-LAN bridge stopped")
    }

    fun getVerifiedLanEndpoint(onionAddress: String): InetSocketAddress? {
        if (isStrictTorOnly()) {
            verifiedEndpoints.clear()
            return null
        }
        val canonical = canonicalPeerKey(onionAddress)
        if (canonical.isBlank()) return null
        val entry = verifiedEndpoints[canonical] ?: return null
        if (System.currentTimeMillis() - entry.verifiedAtMs > ENDPOINT_TTL_MS) {
            verifiedEndpoints.remove(canonical, entry)
            return null
        }
        return entry.address
    }

    fun hasActiveLanInterface(): Boolean {
        if (isStrictTorOnly()) return false
        return inspectLanInterfaces().hasUsableLan
    }

    private fun isStrictTorOnly(): Boolean =
        PrivacyPreferenceManager.alwaysRouteCallsOverTor.value

    private fun canonicalPeerKey(peerIdentifier: String): String =
        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerIdentifier)
            .ifBlank { peerIdentifier.trim().lowercase() }

    private fun isValidLanAddress(addr: InetAddress?): Boolean {
        if (addr == null || addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isMulticastAddress) {
            return false
        }
        return addr.isSiteLocalAddress || addr.isLinkLocalAddress
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (running.get() && !server.isClosed) {
            try {
                val socket = server.accept()
                val addr = socket.inetAddress
                if (isStrictTorOnly() || !isValidLanAddress(addr)) {
                    runCatching { socket.close() }
                    continue
                }
                socket.tcpNoDelay = true
                scope.launch {
                    onIncomingLanSocket(socket)
                }
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                if (running.get() && !server.isClosed) {
                    Log.w(TAG, "Phantom-LAN TCP accept error: ${e.message}")
                    delay(150L)
                } else {
                    break
                }
            }
        }
    }

    private suspend fun udpBroadcastLoop(sock: DatagramSocket) {
        delay(600L)
        while (running.get() && !sock.isClosed) {
            try {
                if (isStrictTorOnly()) {
                    verifiedEndpoints.clear()
                } else {
                    pruneExpiredEndpoints()
                    val tcpPort = localTcpPort
                    if (tcpPort in 1..65535) {
                        val interfaces = inspectLanInterfaces()
                        if (interfaces.broadcastAddresses.isNotEmpty()) {
                            broadcastBeaconsOnce(sock, tcpPort, interfaces.broadcastAddresses)
                        }
                    }
                }
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                Log.d(TAG, "Phantom-LAN broadcast cycle skipped: ${e.message}")
            }
            delay(BROADCAST_INTERVAL_MS)
        }
    }

    private fun broadcastBeaconsOnce(
        sock: DatagramSocket,
        tcpPort: Int,
        broadcastAddresses: List<InetAddress>
    ) {
        val contacts = contactManager.getAllContacts()
        for (contact in contacts) {
            if (contact.requiresRendezvous()) continue
            if (contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
                contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED
            ) continue
            val keys = getOrDerivePairwiseKeys(contact.publicKey) ?: continue
            val packetBytes = sealBeaconPacket(keys.txKey, tcpPort) ?: continue
            for (bcast in broadcastAddresses) {
                runCatching {
                    sock.send(DatagramPacket(packetBytes, packetBytes.size, bcast, DISCOVERY_PORT))
                }
            }
        }
    }

    private suspend fun udpReceiveLoop(sock: DatagramSocket) {
        val buf = ByteArray(128)
        while (running.get() && !sock.isClosed) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                if (packet.length != BEACON_BYTES || isStrictTorOnly()) continue
                val senderAddr = packet.address ?: continue
                if (!isValidLanAddress(senderAddr)) continue

                val localAddrs = inspectLanInterfaces().localAddresses
                if (senderAddr in localAddrs) continue

                val copy = buf.copyOfRange(0, BEACON_BYTES)
                handleIncomingBeacon(sock, senderAddr, copy)
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                if (running.get() && !sock.isClosed) {
                    Log.d(TAG, "Phantom-LAN UDP receive notice: ${e.message}")
                    delay(100L)
                } else {
                    break
                }
            }
        }
    }

    private fun handleIncomingBeacon(
        sock: DatagramSocket,
        senderAddr: InetAddress,
        beacon: ByteArray
    ) {
        val nonce = beacon.copyOfRange(0, NONCE_BYTES)
        val nonceKey = Base64.encodeToString(nonce, Base64.NO_WRAP)
        synchronized(replayLock) {
            if (seenNonces.contains(nonceKey)) return
        }

        val now = System.currentTimeMillis()
        val ciphertext = beacon.copyOfRange(NONCE_BYTES, BEACON_BYTES)
        val contacts = contactManager.getAllContacts()
        for (contact in contacts) {
            if (contact.requiresRendezvous()) continue
            if (contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
                contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED
            ) continue
            val keys = getOrDerivePairwiseKeys(contact.publicKey) ?: continue
            val plain = openBeaconPacket(keys.rxKey, nonce, ciphertext) ?: continue
            if (plain.size != PLAINTEXT_BYTES) continue

            for (i in BEACON_MAGIC.indices) {
                if (plain[i] != BEACON_MAGIC[i]) return
            }
            val bb = ByteBuffer.wrap(plain, BEACON_MAGIC.size, PLAINTEXT_BYTES - BEACON_MAGIC.size)
            val peerPort = bb.short.toInt() and 0xFFFF
            bb.short // reserved flags
            val timestampMs = bb.long
            if (peerPort !in 1..65535) continue
            if (abs(now - timestampMs) > MAX_CLOCK_SKEW_MS) continue

            markNonceSeen(nonceKey)

            val canonicalOnion = canonicalPeerKey(contact.onionAddress)
            if (canonicalOnion.isBlank()) return
            val endpoint = InetSocketAddress(senderAddr, peerPort)
            verifiedEndpoints[canonicalOnion] = VerifiedEndpoint(endpoint, now)
            onPeerDiscoveredOnLan(canonicalOnion)

            val myTcpPort = localTcpPort
            val lastEcho = lastUnicastEchoAt[canonicalOnion] ?: 0L
            if (myTcpPort in 1..65535 && now - lastEcho >= UNICAST_ECHO_COOLDOWN_MS) {
                lastUnicastEchoAt[canonicalOnion] = now
                sealBeaconPacket(keys.txKey, myTcpPort)?.let { replyBytes ->
                    runCatching {
                        sock.send(DatagramPacket(replyBytes, replyBytes.size, senderAddr, DISCOVERY_PORT))
                    }
                }
            }
            return
        }
    }

    private fun markNonceSeen(nonceKey: String) {
        synchronized(replayLock) {
            if (seenNonces.size >= MAX_REPLAY_NONCES) {
                val it = seenNonces.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            seenNonces.add(nonceKey)
        }
    }

    private fun pruneExpiredEndpoints() {
        val now = System.currentTimeMillis()
        verifiedEndpoints.entries.removeIf { now - it.value.verifiedAtMs > ENDPOINT_TTL_MS }
    }

    private fun sealBeaconPacket(txKey: SecretKeySpec, tcpPort: Int): ByteArray? {
        return try {
            val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val plain = ByteBuffer.allocate(PLAINTEXT_BYTES)
                .put(BEACON_MAGIC)
                .putShort((tcpPort and 0xFFFF).toShort())
                .putShort(0)
                .putLong(System.currentTimeMillis())
                .array()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, txKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(BEACON_AAD)
            val ct = cipher.doFinal(plain)
            if (ct.size != PLAINTEXT_BYTES + GCM_TAG_BYTES) return null
            ByteArray(BEACON_BYTES).apply {
                System.arraycopy(nonce, 0, this, 0, NONCE_BYTES)
                System.arraycopy(ct, 0, this, NONCE_BYTES, ct.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openBeaconPacket(rxKey: SecretKeySpec, nonce: ByteArray, ciphertext: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, rxKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(BEACON_AAD)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrDerivePairwiseKeys(peerPubHex: String): PairwiseBeaconKeys? {
        val cleanHex = peerPubHex.trim().lowercase()
        if (cleanHex.length != 64) return null
        pairwiseKeys[cleanHex]?.let { return it }

        val localKeys = ensureLocalStaticKeys() ?: return null
        val peerPub = hexToBytesOrNull(cleanHex) ?: return null
        if (peerPub.size != 32) return null

        var dh: ByteArray? = null
        var txRaw: ByteArray? = null
        var rxRaw: ByteArray? = null
        return try {
            dh = X25519.computeSharedSecret(localKeys.first, peerPub)
            AegisTorCrypto.requireNonZeroSharedSecret(dh)
            txRaw = AegisTorCrypto.hkdfSha256(
                ikm = dh,
                salt = HKDF_SALT,
                info = HKDF_LABEL + localKeys.second + peerPub,
                length = 32
            )
            rxRaw = AegisTorCrypto.hkdfSha256(
                ikm = dh,
                salt = HKDF_SALT,
                info = HKDF_LABEL + peerPub + localKeys.second,
                length = 32
            )
            PairwiseBeaconKeys(
                txKey = SecretKeySpec(txRaw, "AES"),
                rxKey = SecretKeySpec(rxRaw, "AES")
            ).also { derived ->
                pairwiseKeys.putIfAbsent(cleanHex, derived)
            }
        } catch (_: Exception) {
            null
        } finally {
            AegisTorCrypto.zeroize(dh, txRaw, rxRaw)
        }
    }

    private fun ensureLocalStaticKeys(): Pair<ByteArray, ByteArray>? {
        cachedLocalStaticKeys?.let { return it }
        val loaded = runCatching { SecureIdentityStateManager(context).loadStaticKey() }.getOrNull()
        if (loaded != null) {
            cachedLocalStaticKeys = loaded
        }
        return loaded
    }

    private fun hexToBytesOrNull(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun inspectLanInterfaces(): LanInterfaceSnapshot {
        val locals = LinkedHashSet<InetAddress>()
        val broadcasts = LinkedHashSet<InetAddress>()
        var usable = false
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return LanInterfaceSnapshot(emptySet(), emptyList(), false)
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                for (ia in iface.interfaceAddresses) {
                    val addr = ia.address ?: continue
                    if (!addr.isLoopbackAddress && !addr.isAnyLocalAddress && !addr.isMulticastAddress) {
                        locals.add(addr)
                    }
                    if (addr is Inet4Address && (addr.isSiteLocalAddress || addr.isLinkLocalAddress)) {
                        usable = true
                        ia.broadcast?.let { bcast ->
                            if (!bcast.isLoopbackAddress) broadcasts.add(bcast)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return LanInterfaceSnapshot(locals, broadcasts.toList(), usable)
    }
}
