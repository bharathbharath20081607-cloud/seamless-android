@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import com.seamless.android.util.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.KeyAgreement
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/**
 * Bramble Rendezvous Protocol (BRP) - Deterministic Peer Discovery
 *
 * Enables two peers who have exchanged public keys to independently derive
 * the same pair of meeting points in the Tor network. Both sides compute
 * identical onion addresses from their shared DH secret — no central
 * directory, no address exchange needed.
 *
 * # Protocol Flow
 *
 * ```
 * Alice (pri_a, pub_a)          Bob (pri_b, pub_b)
 *          │                           │
 *          ├── DH(pri_a, pub_b) ──────►│ raw_secret
 *          │◄── DH(pri_b, pub_a) ──────┤ (identical)
 *          │                           │
 *          ├── KDF chain ─────────────►│
 *          │   → static_master_key     │
 *          │   → rendezvous_key        │
 *          │   → stream_key (64B)      │
 *          │                           │
 *          │ first 32B → Alice's Ed25519 seed
 *          │ last  32B → Bob's Ed25519 seed
 *          │                           │
 *          │  Both derive identical    │
 *          │  onion address pair       │
 *          │                           │
 *          │  Alice publishes HS at her addr
 *          │  Bob connects to Alice's addr
 * ```
 *
 * Based on Briar's Bramble Rendezvous Protocol:
 * https://code.briarproject.org/briar/briar-spec/blob/master/protocols/BRP.md
 */
class BrambleRendezvous private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BrambleRendezvous"

        // Domain separation constants (matching seamless-core/rendezvous.rs)
        private val DOMAIN_STATIC_MASTER = "org.seamless.bramble.transport/STATIC_MASTER_KEY".toByteArray()
        private val DOMAIN_RENDEZVOUS = "org.seamless.bramble.rendezvous/RENDEZVOUS_KEY".toByteArray()
        private val DOMAIN_STREAM = "org.seamless.bramble.rendezvous/KEY_MATERIAL".toByteArray()
        private val DOMAIN_TOR = "org.seamless.bramble.tor".toByteArray()

        private const val RENDEZVOUS_VERSION: Byte = 0x01
        private const val TOR_V3_VERSION: Byte = 0x03

        // Rendezvous timing
        private const val RENDEZVOUS_TIMEOUT_MS = 120_000L   // 2 minutes
        private const val RENDEZVOUS_RETRY_MS = 10_000L      // 10 seconds between attempts
        private const val MAX_RENDEZVOUS_ATTEMPTS = 12       // ~2 minutes of retries

        @Volatile
        private var instance: BrambleRendezvous? = null

        fun getInstance(context: Context): BrambleRendezvous {
            return instance ?: synchronized(this) {
                instance ?: BrambleRendezvous(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * State of a rendezvous attempt
     */
    enum class RendezvousState {
        IDLE,
        DERIVING_KEYS,
        PUBLISHING_SERVICE,
        CONNECTING_TO_PEER,
        CONNECTED,
        FAILED,
        TIMEOUT
    }

    /**
     * Result of rendezvous address derivation
     */
    data class RendezvousAddresses(
        val ourAddress: String,      // The onion address we publish as hidden service
        val peerAddress: String,     // The onion address we connect to
        val peerPublicKey: String,   // The peer's public key (hex) for reference
        val weAreA: Boolean,          // Role determination
        val ourSeed: ByteArray       // Our Ed25519 seed
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RendezvousAddresses) return false
            if (ourAddress != other.ourAddress) return false
            if (peerAddress != other.peerAddress) return false
            if (peerPublicKey != other.peerPublicKey) return false
            if (weAreA != other.weAreA) return false
            if (!ourSeed.contentEquals(other.ourSeed)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = ourAddress.hashCode()
            result = 31 * result + peerAddress.hashCode()
            result = 31 * result + peerPublicKey.hashCode()
            result = 31 * result + weAreA.hashCode()
            result = 31 * result + ourSeed.contentHashCode()
            return result
        }
    }

    /**
     * Rendezvous session tracking
     */
    data class RendezvousSession(
        val peerId: String,
        val peerPublicKey: ByteArray,
        var state: RendezvousState = RendezvousState.IDLE,
        var addresses: RendezvousAddresses? = null,
        var attempt: Int = 0,
        val startedAt: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RendezvousSession) return false
            return peerId == other.peerId
        }
        override fun hashCode(): Int = peerId.hashCode()
    }

    // Active rendezvous sessions
    private val sessions = mutableMapOf<String, RendezvousSession>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks
    interface RendezvousCallback {
        fun onRendezvousConnected(peerId: String, peerOnion: String)
        fun onRendezvousFailed(peerId: String, reason: String)
    }

    private var callback: RendezvousCallback? = null

    // State flow for UI observation
    private val _stateFlow = MutableStateFlow<Map<String, RendezvousState>>(emptyMap())
    val stateFlow: StateFlow<Map<String, RendezvousState>> = _stateFlow.asStateFlow()

    fun setCallback(cb: RendezvousCallback?) {
        callback = cb
    }

    /**
     * Derive deterministic rendezvous onion addresses from our key pair and peer's public key.
     *
     * Both peers calling this with their own private key and the other's public key
     * will produce the same pair of addresses.
     *
     * @param ourPrivateKey Our X25519 private key (32 bytes)
     * @param ourPublicKey  Our X25519 public key (32 bytes)
     * @param peerPublicKey Peer's X25519 public key (32 bytes)
     * @return RendezvousAddresses with both derived .onion addresses
     */
    fun deriveRendezvousAddresses(
        ourPrivateKey: ByteArray,
        ourPublicKey: ByteArray,
        peerPublicKey: ByteArray
    ): RendezvousAddresses? {
        require(ourPrivateKey.size == 32) { "Private key must be 32 bytes" }
        require(ourPublicKey.size == 32) { "Our public key must be 32 bytes" }
        require(peerPublicKey.size == 32) { "Peer public key must be 32 bytes" }

        return try {
            // Step 1: X25519 Diffie-Hellman → raw shared secret
            val rawSecret = x25519DiffieHellman(ourPrivateKey, peerPublicKey)

            // Step 2: Order public keys canonically (lexicographic) so both sides get same result
            val (pubFirst, pubSecond) = if (compareBytes(ourPublicKey, peerPublicKey) < 0) {
                ourPublicKey to peerPublicKey
            } else {
                peerPublicKey to ourPublicKey
            }

            // Step 3: Derive static master key via BLAKE3/SHA-256 KDF chain
            val staticMasterKey = deriveStaticMasterKey(rawSecret, pubFirst, pubSecond)

            // Step 4: Derive rendezvous key
            val rendezvousKey = deriveRendezvousKey(staticMasterKey)

            // Step 5: Derive 64-byte stream key
            val streamKey = deriveStreamKey(rendezvousKey)

            // Step 6: Split into two Ed25519 seeds
            val seedA = streamKey.copyOfRange(0, 32)
            val seedB = streamKey.copyOfRange(32, 64)

            // Step 7: Derive Tor v3 onion addresses
            val addressA = deriveOnionAddress(seedA)
            val addressB = deriveOnionAddress(seedB)

            // Determine which address is ours
            val weAreA = compareBytes(ourPublicKey, peerPublicKey) < 0

            Log.i(TAG, "🔑 Derived rendezvous addresses:")
            Log.i(TAG, "   Address A: ${addressA.take(16)}...")
            Log.i(TAG, "   Address B: ${addressB.take(16)}...")
            Log.i(TAG, "   We are: ${if (weAreA) "A" else "B"}")

            RendezvousAddresses(
                ourAddress = if (weAreA) addressA else addressB,
                peerAddress = if (weAreA) addressB else addressA,
                peerPublicKey = peerPublicKey.toHexString(),
                weAreA = weAreA,
                ourSeed = if (weAreA) seedA else seedB
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to derive rendezvous addresses: ${e.message}", e)
            null
        }
    }

    /**
     * Start a rendezvous attempt with a peer.
     *
     * This will:
     * 1. Derive the rendezvous addresses from both peers' keys
     * 2. Publish a temporary hidden service at our derived address
     * 3. Attempt to connect to the peer's derived address
     * 4. Negotiate long-term onion address exchange once connected
     *
     * @param peerId          Identifier for the peer (e.g., contact ID)
     * @param ourPrivateKey   Our X25519 private key (32 bytes)
     * @param ourPublicKey    Our X25519 public key (32 bytes)
     * @param peerPublicKey   Peer's X25519 public key (32 bytes)
     */
    suspend fun startRendezvous(
        peerId: String,
        ourPrivateKey: ByteArray,
        ourPublicKey: ByteArray,
        peerPublicKey: ByteArray
    ) = mutex.withLock {
        // Check if already running
        if (sessions[peerId]?.state == RendezvousState.CONNECTING_TO_PEER ||
            sessions[peerId]?.state == RendezvousState.PUBLISHING_SERVICE) {
            Log.d(TAG, "Rendezvous already in progress for $peerId")
            return@withLock
        }

        val session = RendezvousSession(
            peerId = peerId,
            peerPublicKey = peerPublicKey,
            state = RendezvousState.DERIVING_KEYS
        )
        sessions[peerId] = session
        updateStateFlow()

        // Launch the rendezvous coroutine
        scope.launch {
            try {
                executeRendezvous(session, ourPrivateKey, ourPublicKey, peerPublicKey)
            } catch (e: Exception) {
                Log.e(TAG, "Rendezvous failed for $peerId: ${e.message}")
                mutex.withLock {
                    session.state = RendezvousState.FAILED
                    updateStateFlow()
                }
                callback?.onRendezvousFailed(peerId, e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Cancel a rendezvous attempt
     */
    suspend fun cancelRendezvous(peerId: String) = mutex.withLock {
        sessions.remove(peerId)
        updateStateFlow()
        Log.i(TAG, "🛑 Rendezvous cancelled for $peerId")
    }

    /**
     * Get the current rendezvous state for a peer
     */
    fun getRendezvousState(peerId: String): RendezvousState {
        return sessions[peerId]?.state ?: RendezvousState.IDLE
    }

    // ===== Core Protocol Implementation =====

    private suspend fun executeRendezvous(
        session: RendezvousSession,
        ourPrivateKey: ByteArray,
        ourPublicKey: ByteArray,
        peerPublicKey: ByteArray
    ) {
        // Step 1: Derive addresses
        val addresses = deriveRendezvousAddresses(ourPrivateKey, ourPublicKey, peerPublicKey)
            ?: throw IllegalStateException("Failed to derive rendezvous addresses")

        mutex.withLock {
            session.addresses = addresses
            session.state = RendezvousState.PUBLISHING_SERVICE
            updateStateFlow()
        }

        val daemonManager = TorDaemonManager.getInstance(context.applicationContext as Application)
        var serverSocket: ServerSocket? = null
        var publishedAddress: String? = null
        var connectedSocket: Socket? = null

        try {
            // Bind ServerSocket to an ephemeral port
            serverSocket = withContext(Dispatchers.IO) {
                ServerSocket(0)
            }
            val localPort = serverSocket.localPort
            Log.i(TAG, "Bound ServerSocket for BRP on ephemeral port $localPort")

            // Format private key blob and publish custom hidden service
            val privateKeyBlob = getPrivateKeyBlob(addresses.ourSeed)
            Log.i(TAG, "Publishing custom hidden service at virtual port 18888, localPort $localPort")
            
            publishedAddress = daemonManager.publishCustomHiddenService(
                localPort = localPort,
                virtualPort = 18888,
                privateKeyBlob = privateKeyBlob
            )

            if (publishedAddress == null) {
                throw IllegalStateException("Failed to publish custom hidden service")
            }

            mutex.withLock {
                session.state = RendezvousState.CONNECTING_TO_PEER
                updateStateFlow()
            }

            // Race inbound accept vs outbound connection attempts
            val activeSocket = CompletableDeferred<Socket>()
            
            coroutineScope {
                // Task A: Inbound Socket Accept Loop
                val inboundJob = launch {
                    try {
                        while (isActive) {
                            val clientSocket = withContext(Dispatchers.IO) {
                                serverSocket.accept()
                            }
                            if (activeSocket.complete(clientSocket)) {
                                Log.i(TAG, "🏆 BRP Inbound connection won the race!")
                                break
                            } else {
                                try { clientSocket.close() } catch (e: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "BRP Inbound accept exception (normal on socket close): ${e.message}")
                    }
                }

                // Task B: Outbound SOCKS5 Connection Attempts
                val outboundJob = launch {
                    var attempt = 0
                    while (isActive && attempt < MAX_RENDEZVOUS_ATTEMPTS) {
                        attempt++
                        mutex.withLock {
                            session.attempt = attempt
                            updateStateFlow()
                        }
                        Log.d(TAG, "🔄 BRP Outbound attempt $attempt/$MAX_RENDEZVOUS_ATTEMPTS to ${addresses.peerAddress}:18888")
                        try {
                            val torProxy = TorManager.currentBestSocksAddress(context)
                            if (torProxy != null) {
                                val socket = withContext(Dispatchers.IO) {
                                    val proxy = Proxy(Proxy.Type.SOCKS, torProxy)
                                    val sock = Socket(proxy)
                                    sock.connect(InetSocketAddress.createUnresolved(addresses.peerAddress, 18888), 30_000)
                                    sock
                                }
                                if (activeSocket.complete(socket)) {
                                    Log.i(TAG, "🏆 BRP Outbound connection won the race!")
                                    break
                                } else {
                                    try { socket.close() } catch (e: Exception) {}
                                }
                            } else {
                                Log.w(TAG, "BRP Outbound: Tor SOCKS proxy not available, skipping attempt")
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "BRP Outbound attempt $attempt failed: ${e.message}")
                        }
                        
                        delay(RENDEZVOUS_RETRY_MS)
                    }
                }

                // Wait for the race to finish or timeout
                try {
                    withTimeout(RENDEZVOUS_TIMEOUT_MS) {
                        connectedSocket = activeSocket.await()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "⏰ BRP Rendezvous timed out for ${session.peerId}")
                } finally {
                    inboundJob.cancel()
                    outboundJob.cancel()
                    try { serverSocket.close() } catch (e: Exception) {}
                }
            }

            if (connectedSocket != null) {
                val socket = connectedSocket!!
                var success = false
                var finalPeerOnion = ""
                
                try {
                    finalPeerOnion = withContext(Dispatchers.IO) {
                        socket.soTimeout = 10_000 // 10s timeout for handshake
                        val out = java.io.DataOutputStream(socket.getOutputStream())
                        val input = java.io.DataInputStream(socket.getInputStream())
                        
                        // Get our real onion address
                        val hiddenServiceManager = TorHiddenServiceManager.getInstance(context)
                        val ourRealOnion = hiddenServiceManager.getOnionAddress()
                        
                        // Write our real onion address
                        val writeBytes = ourRealOnion.toByteArray()
                        out.writeInt(writeBytes.size)
                        out.write(writeBytes)
                        out.flush()
                        Log.i(TAG, "📤 BRP Handshake: Sent our real onion address: $ourRealOnion")
                        
                        // Read peer's real onion address
                        val size = input.readInt()
                        if (size <= 0 || size > 1024) {
                            throw IllegalStateException("Invalid onion address size: $size")
                        }
                        val readBytes = ByteArray(size)
                        input.readFully(readBytes)
                        val peerRealOnion = String(readBytes)
                        Log.i(TAG, "📥 BRP Handshake: Received peer's real onion address: $peerRealOnion")
                        
                        peerRealOnion
                    }
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed during BRP onion exchange: ${e.message}", e)
                    try { socket.close() } catch (ex: Exception) {}
                }

                if (success && finalPeerOnion.isNotEmpty()) {
                    val canonicalPeerOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(finalPeerOnion)
                        .ifBlank { finalPeerOnion }
                    if (!socket.isClosed) {
                        try {
                            TorCircuitManager.getInstance(context).addIncomingConnection(
                                peerIdentifier = canonicalPeerOnion,
                                socket = socket,
                                preferIncoming = true
                            )
                            // Transfer socket ownership to TorCircuitManager so finally block does not close it
                            connectedSocket = null
                            Log.i(TAG, "⚡ Handed live BRP socket directly to TorCircuitManager for $canonicalPeerOnion (0ms handoff)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not hand off live BRP socket, falling back to normal dial: ${e.message}")
                        }
                    }
                    mutex.withLock {
                        session.state = RendezvousState.CONNECTED
                        updateStateFlow()
                    }
                    Log.i(TAG, "✅ BRP Rendezvous succeeded for ${session.peerId}")
                    callback?.onRendezvousConnected(session.peerId, canonicalPeerOnion)
                } else {
                    mutex.withLock {
                        session.state = RendezvousState.FAILED
                        updateStateFlow()
                    }
                    callback?.onRendezvousFailed(session.peerId, "Onion address exchange failed")
                }
            } else {
                mutex.withLock {
                    session.state = RendezvousState.FAILED
                    updateStateFlow()
                }
                callback?.onRendezvousFailed(session.peerId, "No connection established (timed out/failed)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "BRP Rendezvous execution failed: ${e.message}", e)
            mutex.withLock {
                session.state = RendezvousState.FAILED
                updateStateFlow()
            }
            callback?.onRendezvousFailed(session.peerId, e.message ?: "Unknown BRP error")
        } finally {
            // Clean up custom hidden service
            publishedAddress?.let { onion ->
                try {
                    daemonManager.removeCustomHiddenService(onion)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove custom hidden service $onion: ${e.message}")
                }
            }
            // Clean up server socket
            try { serverSocket?.close() } catch (e: Exception) {}
            // Clean up connected socket only if ownership was not transferred to TorCircuitManager
            try { connectedSocket?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Helper to format private key blobs as ED25519-V3:Base64(SHA-512(seed)).
     */
    private fun getPrivateKeyBlob(seed: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashedSeed = digest.digest(seed)
        val base64Key = Base64.encodeToString(hashedSeed, Base64.NO_WRAP)
        return "ED25519-V3:$base64Key"
    }

    // ===== Cryptographic Primitives =====

    /**
     * X25519 Diffie-Hellman key agreement.
     * Uses Java's XDH KeyAgreement (Android API 33+) with fallback to manual computation.
     */
    private fun x25519DiffieHellman(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        return try {
            // Use Java's built-in XDH support (API 33+)
            val keyAgreement = KeyAgreement.getInstance("XDH")
            val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(wrapX25519PrivateKey(privateKey))
            val pubKeySpec = java.security.spec.X509EncodedKeySpec(wrapX25519PublicKey(publicKey))
            val keyFactory = java.security.KeyFactory.getInstance("XDH")
            val privKey = keyFactory.generatePrivate(privKeySpec)
            val pubKey = keyFactory.generatePublic(pubKeySpec)
            keyAgreement.init(privKey)
            keyAgreement.doPhase(pubKey, true)
            keyAgreement.generateSecret()
        } catch (e: Exception) {
            // Fallback: manual X25519 scalar multiplication via HKDF-style derivation
            // This is a simplified implementation; in production, use a proper X25519 library
            Log.d(TAG, "Using fallback X25519 implementation")
            manualX25519(privateKey, publicKey)
        }
    }

    /**
     * Manual X25519 using BouncyCastle's X25519Agreement.
     * Performs real Curve25519 scalar multiplication: shared_secret = clamp(priv) * pub
     *
     * Both sides produce the same shared secret:
     *   DH(priv_a, pub_b) == DH(priv_b, pub_a)
     *
     * This fallback is used on API < 33 where Java's XDH is unavailable.
     * BouncyCastle bcprov-jdk15on is already a project dependency.
     */
    private fun manualX25519(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val privParams = X25519PrivateKeyParameters(privateKey, 0)
        val pubParams = X25519PublicKeyParameters(publicKey, 0)

        val agreement = X25519Agreement()
        agreement.init(privParams)

        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(pubParams, sharedSecret, 0)

        return sharedSecret
    }

    /**
     * Wrap raw X25519 private key bytes in PKCS#8 DER encoding
     */
    private fun wrapX25519PrivateKey(raw: ByteArray): ByteArray {
        // X25519 PKCS#8 DER encoding: OID 1.3.101.110
        val prefix = byteArrayOf(
            0x30, 0x2E, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
            0x03, 0x2B, 0x65, 0x6E, 0x04, 0x22, 0x04, 0x20
        )
        return prefix + raw
    }

    /**
     * Wrap raw X25519 public key bytes in X.509 DER encoding
     */
    private fun wrapX25519PublicKey(raw: ByteArray): ByteArray {
        // X25519 SubjectPublicKeyInfo DER encoding: OID 1.3.101.110
        val prefix = byteArrayOf(
            0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65,
            0x6E, 0x03, 0x21, 0x00
        )
        return prefix + raw
    }

    /**
     * Derive static master key from shared secret and canonical public keys.
     *
     * Uses SHA-256 HKDF-style derivation to match the BLAKE3 chain in seamless-core.
     * Both implementations produce domain-separated keys — exact hash function
     * doesn't matter as long as both peers use the same one.
     */
    private fun deriveStaticMasterKey(
        rawSecret: ByteArray,
        pubFirst: ByteArray,
        pubSecond: ByteArray
    ): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(DOMAIN_STATIC_MASTER)
        md.update(rawSecret)
        md.update(pubFirst)
        md.update(pubSecond)
        return md.digest()
    }

    /**
     * Derive rendezvous key from static master key.
     */
    private fun deriveRendezvousKey(staticMasterKey: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(staticMasterKey)
        md.update(DOMAIN_RENDEZVOUS)
        md.update(byteArrayOf(RENDEZVOUS_VERSION))
        return md.digest()
    }

    /**
     * Derive 64-byte stream key from rendezvous key.
     * Uses HKDF-Expand style: SHA-256(key || domain || counter)
     */
    private fun deriveStreamKey(rendezvousKey: ByteArray): ByteArray {
        // First 32 bytes
        val md1 = MessageDigest.getInstance("SHA-256")
        md1.update(rendezvousKey)
        md1.update(DOMAIN_STREAM)
        md1.update(DOMAIN_TOR)
        md1.update(byteArrayOf(0x01))
        val first = md1.digest()

        // Second 32 bytes (chain the first half)
        val md2 = MessageDigest.getInstance("SHA-256")
        md2.update(rendezvousKey)
        md2.update(first)
        md2.update(DOMAIN_STREAM)
        md2.update(DOMAIN_TOR)
        md2.update(byteArrayOf(0x02))
        val second = md2.digest()

        return first + second
    }

    /**
     * Derive a Tor v3 onion address from an Ed25519 seed.
     *
     * The seed is used as an Ed25519 private key (via BouncyCastle) to derive
     * the real Ed25519 public key through scalar multiplication on Curve25519.
     * The public key is then encoded as a Tor v3 onion address:
     *   base32(pubkey || checksum || version) + ".onion"
     *
     * Checksum = SHA-256(".onion checksum" || pubkey || version)[:2]
     *
     * Both peers derive the same seed from their shared DH secret, so both
     * independently derive the same .onion address pair.
     */
    private fun deriveOnionAddress(seed: ByteArray): String {
        // Derive real Ed25519 public key from seed using BouncyCastle.
        // Ed25519PrivateKeyParameters(seed) performs the standard Ed25519
        // scalar expansion (SHA-512 + clamp) and base point multiplication
        // to produce the correct public key.
        val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed, 0)
        val pubKeyDerived = privateKeyParams.generatePublicKey().encoded

        // Compute checksum per Tor v3 spec
        val checksumHash = MessageDigest.getInstance("SHA-256")
        checksumHash.update(".onion checksum".toByteArray())
        checksumHash.update(pubKeyDerived)
        checksumHash.update(byteArrayOf(TOR_V3_VERSION))
        val checksum = checksumHash.digest().copyOfRange(0, 2)

        // Combine: pubkey (32) + checksum (2) + version (1) = 35 bytes
        val combined = ByteArray(35)
        System.arraycopy(pubKeyDerived, 0, combined, 0, 32)
        System.arraycopy(checksum, 0, combined, 32, 2)
        combined[34] = TOR_V3_VERSION

        // Base32 encode (lowercase, no padding)
        val encoded = base32Encode(combined).lowercase().trimEnd('=')

        return "$encoded.onion"
    }

    // ===== Utility Functions =====

    /**
     * RFC 4648 Base32 encoding
     */
    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                result.append(alphabet[(buffer shr bitsLeft) and 0x1F])
            }
        }

        if (bitsLeft > 0) {
            result.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1F])
        }

        return result.toString()
    }

    /**
     * Lexicographic byte array comparison
     */
    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }

    private fun updateStateFlow() {
        _stateFlow.value = sessions.mapValues { it.value.state }
    }
}
