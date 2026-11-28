@file:Suppress("DEPRECATION")
package com.seamless.android.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seamless.android.MainActivity
import com.seamless.android.R
import com.seamless.android.crypto.AegisCallCrypto
import com.seamless.android.crypto.AegisTorCrypto
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.TorCircuitManager
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorMessagingService
import com.seamless.android.service.CallForegroundService
import com.seamless.android.service.TorForegroundService
import com.seamless.android.ui.privacy.PrivacyPreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

enum class CallState {
    IDLE,
    OUTGOING_DIALING,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    ACTIVE,
    ENDED
}

enum class CallTransportMode {
    TOR_ONION,
    DIRECT_P2P
}

data class CallSessionInfo(
    val callId: String,
    val peerOnion: String,
    val peerNickname: String,
    val peerPublicKey: String,
    val contactId: String?,
    val isIncoming: Boolean,
    val state: CallState,
    val transportMode: CallTransportMode = CallTransportMode.TOR_ONION,
    val isMultipathActive: Boolean = true,
    val forceTorOnly: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerphone: Boolean = false,
    val isMinimized: Boolean = false,
    val isVideoCall: Boolean = false,
    val isLocalVideoEnabled: Boolean = false,
    val isRemoteVideoEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isVideoSwapped: Boolean = false,
    val negotiatedVideoCodec: String = CallVideoEngine.CODEC_AVC,
    val remoteVideoAspectRatio: Float = 9f / 16f,
    val startedAtMs: Long = System.currentTimeMillis(),
    val connectedAtMs: Long? = null,
    val durationSeconds: Long = 0L,
    val rttMs: Int = 0,
    val jitterBufferMs: Int = 0,
    val localVoiceLevel: Float = 0f,
    val remoteVoiceLevel: Float = 0f,
    val statusMessage: String = ""
)

/**
 * Coordinates end-to-end encrypted long-distance & nearby voice calls over:
 * 1. Tor v3 .onion P2P circuits (global long-distance, zero central servers, IP-hidden)
 * 2. Direct Internet / LAN P2P UDP sockets (STUN + Sequential CGNAT Port Prediction + Global IPv6)
 * 3. Tailscale magicsock-style Active-Active Multipath (UDP + Tor simultaneously during transitions
 *    and asymmetric NAT conditions so voice never drops).
 */
class SeamlessCallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SeamlessCallManager"
        private const val RING_TIMEOUT_MS = 45_000L
        private const val DIRECT_P2P_PROMOTE_WINDOW_MS = 1_500L
        private const val DIRECT_P2P_DEMOTE_WINDOW_MS = 3_500L
        private const val DIRECT_P2P_HOLD_MS = 350L
        private const val DIRECT_P2P_STALE_MS = 4_500L
        private const val INITIAL_MULTIPATH_WARMUP_MS = 1_200L
        private const val DIRECT_UDP_NONCE_BYTES = 12
        private const val TOR_AUDIO_MAGIC_V2: Byte = 0x5A
        private const val TOR_VIDEO_MAGIC_V1: Byte = 0x5B
        private const val TOR_AUDIO_FEC_MAGIC: Byte = 0x5C

        private const val UDP_TYPE_PROBE: Byte = 0x01
        private const val UDP_TYPE_PROBE_ACK: Byte = 0x02
        private const val UDP_TYPE_AUDIO: Byte = 0x03
        private const val UDP_TYPE_VIDEO: Byte = 0x04
        private const val UDP_TYPE_VIDEO_CTRL: Byte = 0x05
        private const val UDP_TYPE_AUDIO_FEC: Byte = 0x06

        private const val MAX_PEER_UDP_CANDIDATES = 16
        private const val MAX_SDP_DECOMPRESSED_BYTES = 64 * 1024
        private const val MAX_SDP_SIGNAL_BYTES = 3_400
        private const val MAX_SDP_PARTS = 8
        private const val PROBE_ACK_MIN_INTERVAL_MS = 150L
        private const val MISSED_CALL_CHANNEL_ID = "seamless_missed_calls_channel"
        private const val MISSED_CALL_NOTIFICATION_BASE_ID = 8900

        private const val VIDEO_CTRL_PLI: Byte = 0x01
        private const val VIDEO_CTRL_STATE: Byte = 0x02
        private const val VIDEO_CTRL_WEBRTC_SIG: Byte = 0x03

        /**
         * Strictly parses numeric IPv4 or bracketed IPv6 literal "ip:port" / "[ipv6]:port"
         * WITHOUT ever invoking system DNS resolution (prevents zero-click ISP IP leaks).
         */
        internal fun parseStrictIpLiteralSocketAddress(entry: String): InetSocketAddress? {
            val clean = entry.trim()
            val lastColon = clean.lastIndexOf(':')
            if (lastColon <= 0 || lastColon >= clean.length - 1) return null
            val rawHost = clean.substring(0, lastColon).trim()
            val port = clean.substring(lastColon + 1).trim().toIntOrNull() ?: return null
            if (port !in 1..65535) return null

            if (rawHost.startsWith("[") && rawHost.endsWith("]")) {
                val ipv6Text = rawHost.substring(1, rawHost.length - 1).substringBefore('%').trim()
                val bytes = parseNumericIpv6Bytes(ipv6Text) ?: return null
                val addr = runCatching { InetAddress.getByAddress(bytes) }.getOrNull() ?: return null
                if (addr.isAnyLocalAddress || addr.isMulticastAddress || addr.isLoopbackAddress) return null
                return InetSocketAddress(addr, port)
            }

            val ipv4Bytes = parseNumericIpv4Bytes(rawHost) ?: return null
            val addr = runCatching { InetAddress.getByAddress(ipv4Bytes) }.getOrNull() ?: return null
            if (addr.isAnyLocalAddress || addr.isMulticastAddress || addr.isLoopbackAddress) return null
            return InetSocketAddress(addr, port)
        }

        private fun parseNumericIpv4Bytes(host: String): ByteArray? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val out = ByteArray(4)
            for (i in 0 until 4) {
                val p = parts[i]
                if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
                val v = p.toIntOrNull() ?: return null
                if (v !in 0..255) return null
                out[i] = v.toByte()
            }
            return out
        }

        private fun parseNumericIpv6Bytes(host: String): ByteArray? {
            if (host.isEmpty() || host.length > 45) return null
            if (!host.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' }) return null
            val doubleColonCount = host.split("::").size - 1
            if (doubleColonCount > 1) return null

            val groups = if (doubleColonCount == 1) {
                val sides = host.split("::", limit = 2)
                val left = if (sides[0].isEmpty()) emptyList() else sides[0].split(':')
                val right = if (sides[1].isEmpty()) emptyList() else sides[1].split(':')
                val missing = 8 - (left.size + right.size)
                if (missing < 1) return null
                left + List(missing) { "0" } + right
            } else {
                val all = host.split(':')
                if (all.size != 8) return null
                all
            }
            if (groups.size != 8) return null
            val out = ByteArray(16)
            for (i in 0 until 8) {
                val g = groups[i]
                if (g.isEmpty() || g.length > 4) return null
                val v = g.toIntOrNull(16) ?: return null
                out[i * 2] = ((v ushr 8) and 0xFF).toByte()
                out[i * 2 + 1] = (v and 0xFF).toByte()
            }
            return out
        }

        internal fun decompressSdpGzipBase64Bounded(b64: String): String {
            return try {
                if (b64.length > 48 * 1024) return ""
                val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                if (raw.size > 32 * 1024) return ""
                val baos = java.io.ByteArrayOutputStream()
                java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(raw)).use { gz ->
                    val buf = ByteArray(1024)
                    var total = 0
                    while (true) {
                        val read = gz.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_SDP_DECOMPRESSED_BYTES) return ""
                        baos.write(buf, 0, read)
                    }
                }
                baos.toByteArray().toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }

        // 64-symbol Short Authentication String alphabet (6 bits per symbol).
        // Both peers derive the same 4 symbols from the shared ephemeral call key,
        // so users can verbally confirm there is no man-in-the-middle.
        private val SAS_EMOJIS = listOf(
            "🦊", "🐼", "🐙", "🦄", "🐢", "🦋", "🐝", "🐬",
            "🦉", "🐧", "🦁", "🐯", "🐸", "🐳", "🦀", "🐌",
            "🌵", "🌻", "🍄", "🌙", "⭐", "🔥", "🌈", "❄️",
            "🍎", "🍋", "🍇", "🍉", "🍒", "🥑", "🌽", "🍩",
            "🚀", "⚓", "🎈", "🎸", "🎲", "🎯", "🔑", "💎",
            "⏰", "📷", "🔔", "🎧", "🧭", "🪁", "🛸", "🚲",
            "🏔️", "🌊", "🌋", "🏝️", "🗿", "🎪", "🏰", "⛵",
            "🍀", "🌸", "🍁", "🌴", "🐞", "🦜", "🐿️", "🦔"
        )

        @Volatile
        private var instance: SeamlessCallManager? = null

        fun getInstance(context: Context): SeamlessCallManager {
            return instance ?: synchronized(this) {
                instance ?: SeamlessCallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private data class OutboundTorAudioItem(
        val recipientOnion: String,
        val sequence: Long,
        val timestampMs: Long,
        val payload: ByteArray,
        val isFecParity: Boolean = false
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val callMutex = Mutex()
    private val secureRandom = SecureRandom()

    private val _sessionFlow = MutableStateFlow<CallSessionInfo?>(null)
    val sessionFlow: StateFlow<CallSessionInfo?> = _sessionFlow.asStateFlow()

    fun isCallActiveForPeer(peerIdOrOnion: String): Boolean {
        val session = _sessionFlow.value ?: return false
        if (session.state == CallState.IDLE || session.state == CallState.ENDED) return false
        val needle = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerIdOrOnion)
            .ifBlank { peerIdOrOnion.trim().lowercase() }
        val activeOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(session.peerOnion)
            .ifBlank { session.peerOnion.trim().lowercase() }
        return needle == activeOnion || peerIdOrOnion == session.contactId
    }

    // Dedicated high-frequency flows for 60 FPS UI visualizers (prevents recomposing FullScreenCallView 50x/sec)
    private val _localVoiceLevelFlow = MutableStateFlow(0f)
    val localVoiceLevelFlow: StateFlow<Float> = _localVoiceLevelFlow.asStateFlow()

    private val _remoteVoiceLevelFlow = MutableStateFlow(0f)
    val remoteVoiceLevelFlow: StateFlow<Float> = _remoteVoiceLevelFlow.asStateFlow()

    private val _jitterBufferMsFlow = MutableStateFlow(0)
    val jitterBufferMsFlow: StateFlow<Int> = _jitterBufferMsFlow.asStateFlow()

    private val _txPacketPulseFlow = MutableStateFlow(0L)
    val txPacketPulseFlow: StateFlow<Long> = _txPacketPulseFlow.asStateFlow()

    private val _rxPacketPulseFlow = MutableStateFlow(0L)
    val rxPacketPulseFlow: StateFlow<Long> = _rxPacketPulseFlow.asStateFlow()

    // 4-emoji Short Authentication String derived from the ephemeral call key (empty when unavailable)
    private val _securityCodeFlow = MutableStateFlow<List<String>>(emptyList())
    val securityCodeFlow: StateFlow<List<String>> = _securityCodeFlow.asStateFlow()

    private val _audioRouteFlow = MutableStateFlow(AudioRoute.EARPIECE)
    val audioRouteFlow: StateFlow<AudioRoute> = _audioRouteFlow.asStateFlow()

    private val _availableAudioRoutesFlow = MutableStateFlow(setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER))
    val availableAudioRoutesFlow: StateFlow<Set<AudioRoute>> = _availableAudioRoutesFlow.asStateFlow()

    private val txAudioFramesCounter = AtomicLong(0L)
    private val rxAudioFramesCounter = AtomicLong(0L)

    private data class OutboundTorVideoFrame(
        val recipientOnion: String,
        val frameSeq: Long,
        val timestampMs: Long,
        val encryptedFragments: List<ByteArray>
    )

    // Conflating 6-frame FIFO ring buffer for outbound Tor audio + 2D Burst-FEC parity (prevents coroutine reordering & TCP bufferbloat)
    private val torAudioOutChannel = Channel<OutboundTorAudioItem>(
        capacity = 6,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val torVideoFrameChannel = Channel<OutboundTorVideoFrame>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var audioEngine: CallAudioEngine? = null
    private val webrtcGeneration = AtomicInteger(0)
    private val processedWebRtcSdps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val videoEngine: CallVideoEngine = CallVideoEngine(
        context = context,
        onOutboundFragment = { seq, ts, _, bytes ->
            dispatchOutboundVideoFrame(seq, ts, listOf(bytes))
        },
        onRequestRemoteKeyframe = {
            sendVideoPliRequest()
        },
        onOutboundFrame = { seq, ts, fragments ->
            dispatchOutboundVideoFrame(seq, ts, fragments)
        }
    )
    val webrtcVideoEngine: WebRtcVideoCallEngine = WebRtcVideoCallEngine(
        context = context,
        listener = object : WebRtcVideoCallEngine.Listener {
            override fun onLocalSdp(generation: Int, type: String, sdp: String) {
                sendWebRtcSdpSignal(generation, type, sdp)
            }

            override fun onLocalIceCandidates(generation: Int, candidates: List<org.webrtc.IceCandidate>) {
                sendWebRtcIceSignal(generation, candidates)
            }

            override fun onTransportConnected(generation: Int) {
                videoEngine.clearRemoteFrameBitmap()
                val cur = _sessionFlow.value
                if (cur != null && !cur.forceTorOnly) {
                    audioEngine?.setTransportMode(true)
                }
                _sessionFlow.update { s ->
                    if (s != null && s.state == CallState.ACTIVE && !s.forceTorOnly) {
                        s.copy(
                            transportMode = CallTransportMode.DIRECT_P2P,
                            statusMessage = "Encrypted • WebRTC 720p Direct P2P"
                        )
                    } else s
                }
            }

            override fun onTransportLost(generation: Int) {
                // Automatic fallback tap on WebRtcVideoCallEngine resumes feeding CallVideoEngine
            }

            override fun onFallbackVideoFrame(frame: org.webrtc.VideoFrame) {
                videoEngine.onExternalWebRtcFrame(frame)
            }
        }
    )
    private var timerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var torSenderJob: Job? = null
    private var torVideoSenderJob: Job? = null
    private var outgoingOfferJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var ringbackJob: Job? = null
    private var p2pProbeJob: Job? = null
    private var p2pReceiveJob: Job? = null
    private var stunDiscoveryJob: Job? = null
    @Volatile
    private var wasLocalVideoPausedByBg: Boolean = false
    private val p2pFastBurstCounter = AtomicInteger(0)
    @Volatile
    private var lastCandidateTrickleAtMs: Long = 0L
    @Volatile
    private var torVideoConserveUntilMs: Long = 0L
    @Volatile
    private var lastAutoProbeAckAtMs: Long = 0L

    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var udpSocket: DatagramSocket? = null

    private data class PendingSdpParts(
        val generation: Int,
        val sdpType: String,
        val isGzip: Boolean,
        val totalParts: Int,
        val parts: MutableMap<Int, String> = mutableMapOf(),
        val createdAtMs: Long = System.currentTimeMillis()
    )

    private val pendingSdpParts = ConcurrentHashMap<String, PendingSdpParts>()
    private val fecEncoder = CallFluxFec.Encoder()
    private val fecDecoder = CallFluxFec.Decoder()

    @Volatile
    private var activeCallCrypto: AegisCallCrypto.CallKeySession? = null
    @Volatile
    private var preAcceptCrypto: AegisCallCrypto.CallKeySession? = null
    private var acceptRetryJob: Job? = null
    @Volatile
    private var localEphemeralPriv: ByteArray? = null
    @Volatile
    private var localEphemeralPubHex: String = ""
    @Volatile
    private var localIdentityPubHexForCall: String = ""
    @Volatile
    private var remoteIdentityPubHexForCall: String = ""
    private val dtlsFingerprints = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var currentSessionKey: ByteArray? = null
    @Volatile
    private var currentSessionKeySpec: SecretKeySpec? = null
    @Volatile
    private var currentCallUuid: UUID? = null
    @Volatile
    private var gcmNoncePrefix: Int = 0
    private val gcmNonceCounter = AtomicLong(0L)
    private val encryptCipherTl = ThreadLocal.withInitial { Cipher.getInstance("AES/GCM/NoPadding") }
    private val decryptCipherTl = ThreadLocal.withInitial { Cipher.getInstance("AES/GCM/NoPadding") }
    private val pendingStunTxIds = ConcurrentHashMap<String, Long>()
    private var candidateTrickleJob: Job? = null
    private var callNetworkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var lastCallNetworkHandle: Long = 0L
    @Volatile
    private var firstContinuousDirectOkAtMs: Long = 0L
    private val localUdpCandidates = CopyOnWriteArrayList<String>()
    private val peerUdpCandidates = CopyOnWriteArrayList<InetSocketAddress>()

    // Strict separation between 1-way seen endpoint vs 2-way ACK-verified bidirectional endpoint
    @Volatile
    private var candidateDirectPeerEndpoint: InetSocketAddress? = null
    @Volatile
    private var verifiedDirectPeerEndpoint: InetSocketAddress? = null
    @Volatile
    private var lastDirectP2pVerifiedAt: Long = 0L
    @Volatile
    private var lastPeerActivityAtMs: Long = 0L

    private fun bindSessionCrypto(callId: String, sessionKey: ByteArray, isCaller: Boolean = true) {
        activeCallCrypto?.zeroize()
        val legacy = AegisCallCrypto.deriveLegacySession(callId, isCaller, sessionKey)
        activeCallCrypto = legacy
        currentSessionKey = legacy.sendKey
        currentSessionKeySpec = legacy.sendKeySpec
        currentCallUuid = runCatching { UUID.fromString(callId) }.getOrNull()
        gcmNoncePrefix = secureRandom.nextInt()
        gcmNonceCounter.set(0L)
    }

    private fun bindPfsSessionCrypto(session: AegisCallCrypto.CallKeySession) {
        activeCallCrypto?.zeroize()
        activeCallCrypto = session
        currentSessionKey = session.sendKey
        currentSessionKeySpec = session.sendKeySpec
        currentCallUuid = runCatching { UUID.fromString(session.callId) }.getOrNull()
        gcmNoncePrefix = secureRandom.nextInt()
        gcmNonceCounter.set(0L)
    }

    private fun extractNonceCounter(nonce: ByteArray): Long {
        if (nonce.size < DIRECT_UDP_NONCE_BYTES) return -1L
        return ((nonce[4].toLong() and 0xFF) shl 56) or
            ((nonce[5].toLong() and 0xFF) shl 48) or
            ((nonce[6].toLong() and 0xFF) shl 40) or
            ((nonce[7].toLong() and 0xFF) shl 32) or
            ((nonce[8].toLong() and 0xFF) shl 24) or
            ((nonce[9].toLong() and 0xFF) shl 16) or
            ((nonce[10].toLong() and 0xFF) shl 8) or
            (nonce[11].toLong() and 0xFF)
    }

    private fun nextMonotonicNonce(): ByteArray {
        val seq = gcmNonceCounter.incrementAndGet()
        val prefix = gcmNoncePrefix
        return byteArrayOf(
            (prefix ushr 24).toByte(),
            (prefix ushr 16).toByte(),
            (prefix ushr 8).toByte(),
            prefix.toByte(),
            (seq ushr 56).toByte(),
            (seq ushr 48).toByte(),
            (seq ushr 40).toByte(),
            (seq ushr 32).toByte(),
            (seq ushr 24).toByte(),
            (seq ushr 16).toByte(),
            (seq ushr 8).toByte(),
            seq.toByte()
        )
    }

    private fun bytesToHexLower(bytes: ByteArray): String {
        val hex = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }

    private suspend fun sendCallSignalWithRetry(
        messaging: TorMessagingService,
        peerOnion: String,
        payload: ByteArray,
        maxAttempts: Int = 2
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (messaging.sendCallSignalFrame(peerOnion, payload)) return true
            if (attempt + 1 < maxAttempts) {
                delay(600L * (attempt + 1))
            }
        }
        return false
    }

    private fun registerCallNetworkCallback() {
        if (callNetworkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        lastCallNetworkHandle = cm.activeNetwork?.networkHandle ?: 0L
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val handle = network.networkHandle
                val previous = lastCallNetworkHandle
                lastCallNetworkHandle = handle
                if (previous != 0L && previous != handle) {
                    val active = _sessionFlow.value
                    if (active != null && (active.state == CallState.ACTIVE || active.state == CallState.OUTGOING_DIALING || active.state == CallState.OUTGOING_RINGING)) {
                        scope.launch {
                            delay(400L)
                            refreshCallRoute()
                        }
                    }
                }
            }
        }
        callNetworkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private fun unregisterCallNetworkCallback() {
        val cb = callNetworkCallback ?: return
        callNetworkCallback = null
        lastCallNetworkHandle = 0L
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    init {
        val engine = CallAudioEngine(context) { seq, ts, encodedAdpcm ->
            dispatchOutboundAudioFrame(seq, ts, encodedAdpcm)
        }
        audioEngine = engine

        scope.launch {
            engine.localVoiceLevel.collect { level ->
                _localVoiceLevelFlow.value = level
            }
        }
        scope.launch {
            engine.remoteVoiceLevel.collect { level ->
                _remoteVoiceLevelFlow.value = level
            }
        }
        scope.launch {
            engine.jitterBufferMs.collect { jbMs ->
                _jitterBufferMsFlow.value = jbMs
            }
        }
        scope.launch {
            engine.audioRoute.collect { route ->
                _audioRouteFlow.value = route
                _sessionFlow.update { cur ->
                    if (cur != null && cur.isSpeakerphone != (route == AudioRoute.SPEAKER)) {
                        cur.copy(isSpeakerphone = route == AudioRoute.SPEAKER)
                    } else cur
                }
            }
        }
        scope.launch {
            engine.availableRoutes.collect { routes ->
                _availableAudioRoutesFlow.value = routes
            }
        }
        scope.launch {
            engine.estimatedOneWayLatencyMs.collect { estMs ->
                _sessionFlow.update { current ->
                    if (current != null && current.state == CallState.ACTIVE && current.rttMs == 0 && estMs > 0) {
                        current.copy(rttMs = estMs)
                    } else current
                }
            }
        }
        scope.launch {
            videoEngine.remoteVideoAspectRatio.collect { ratio ->
                if (!webrtcVideoEngine.mediaConnected.value) {
                    _sessionFlow.update { current ->
                        if (current != null && current.remoteVideoAspectRatio != ratio) {
                            current.copy(remoteVideoAspectRatio = ratio)
                        } else current
                    }
                }
            }
        }
        scope.launch {
            webrtcVideoEngine.remoteAspectRatio.collect { ratio ->
                if (webrtcVideoEngine.mediaConnected.value) {
                    _sessionFlow.update { current ->
                        if (current != null && current.remoteVideoAspectRatio != ratio) {
                            current.copy(remoteVideoAspectRatio = ratio)
                        } else current
                    }
                }
            }
        }
        scope.launch {
            webrtcVideoEngine.stats.collect { st ->
                if (webrtcVideoEngine.mediaConnected.value) {
                    _sessionFlow.update { current ->
                        if (current != null && current.state == CallState.ACTIVE) {
                            current.copy(
                                rttMs = if (st.rttMs > 0) st.rttMs else current.rttMs,
                                negotiatedVideoCodec = if (st.codec.isNotBlank()) st.codec else current.negotiatedVideoCodec
                            )
                        } else current
                    }
                }
            }
        }
        scope.launch {
            webrtcVideoEngine.activeFrontCamera.collect { actualFront ->
                _sessionFlow.update { current ->
                    if (current != null && current.isFrontCamera != actualFront) {
                        current.copy(isFrontCamera = actualFront)
                    } else current
                }
            }
        }
    }

    /**
     * Initiate an outgoing voice or HD video call to a Tor P2P contact.
     */
    fun startOutgoingCall(
        contact: OnionContactManager.OnionContact,
        forceTorOnly: Boolean = false,
        isVideoCall: Boolean = false
    ) {
        startOutgoingCallByOnion(
            peerOnion = contact.onionAddress,
            peerNickname = contact.nickname,
            peerPublicKey = contact.publicKey,
            contactId = contact.id,
            forceTorOnly = forceTorOnly,
            isVideoCall = isVideoCall
        )
    }

    fun startOutgoingCallByOnion(
        peerOnion: String,
        peerNickname: String,
        peerPublicKey: String = "",
        contactId: String? = null,
        forceTorOnly: Boolean = false,
        isVideoCall: Boolean = false
    ) {
        val canonicalOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerOnion)
            .ifBlank { peerOnion.trim() }
        if (canonicalOnion.isBlank() || canonicalOnion.startsWith("rendezvous:")) {
            return
        }

        scope.launch {
            callMutex.withLock {
                if (GroupCallManager.getInstance(context).hasActiveCall()) {
                    Log.w(TAG, "Cannot start 1-to-1 call while a group call is active")
                    return@withLock
                }
                val existing = _sessionFlow.value
                if (existing != null && existing.state != CallState.IDLE && existing.state != CallState.ENDED) {
                    Log.w(TAG, "Cannot start call: another call is already in state ${existing.state}")
                    return@withLock
                }

                cleanupCallResourcesLocked()

                val effectiveForceTor = forceTorOnly || PrivacyPreferenceManager.alwaysRouteCallsOverTor.value
                val callId = UUID.randomUUID().toString()
                val eph = AegisCallCrypto.generateEphemeralKeyPair()
                localEphemeralPriv = eph.privateKey
                localEphemeralPubHex = AegisCallCrypto.toHex(eph.publicKey)
                val myStaticPub = runCatching {
                    TorMessagingService.getInstance(context).getMyContactInfo().publicKey
                }.getOrDefault("")
                localIdentityPubHexForCall = myStaticPub
                remoteIdentityPubHexForCall = peerPublicKey
                val sessionKey = ByteArray(32).also { secureRandom.nextBytes(it) }
                bindSessionCrypto(callId, sessionKey, isCaller = true)
                preAcceptCrypto?.zeroize()
                preAcceptCrypto = AegisCallCrypto.deriveLegacySession(callId, isCaller = true, sessionKey)
                val defaultCodec = CallVideoEngine.CODEC_AVC

                // Transition UI state IMMEDIATELY (< 2ms) so CallOverlayScreen opens without any delay
                val newSession = CallSessionInfo(
                    callId = callId,
                    peerOnion = canonicalOnion,
                    peerNickname = peerNickname.ifBlank { canonicalOnion.take(12) },
                    peerPublicKey = peerPublicKey,
                    contactId = contactId,
                    isIncoming = false,
                    state = CallState.OUTGOING_DIALING,
                    transportMode = CallTransportMode.TOR_ONION,
                    isMultipathActive = !effectiveForceTor,
                    forceTorOnly = effectiveForceTor,
                    isSpeakerphone = isVideoCall,
                    isVideoCall = isVideoCall,
                    isLocalVideoEnabled = isVideoCall,
                    negotiatedVideoCodec = defaultCodec,
                    statusMessage = if (isVideoCall) "Connecting encrypted HD video..." else "Connecting encrypted circuit..."
                )
                _sessionFlow.value = newSession
                if (isVideoCall) {
                    webrtcVideoEngine.configureLocalCamera(enabled = true, frontCamera = true)
                    videoEngine.configureSession(
                        isCallActive = false,
                        isLocalEnabled = true,
                        isFrontCam = true,
                        isDirectP2p = false,
                        codec = defaultCodec
                    )
                }
                CallForegroundService.start(context)
                registerCallNetworkCallback()
                startRingTimeout(callId)
                startTorAudioSenderLoop(callId)
                startTorVideoSenderLoop(callId)

                val initialCandidates = if (!effectiveForceTor) {
                    openDirectUdpSocketFast(callId, canonicalOnion)
                } else {
                    emptyList()
                }

                // Ensure Tor service is running, recycle any silent stale circuit, and send OFFER
                outgoingOfferJob?.cancel()
                val offerEphHex = localEphemeralPubHex
                outgoingOfferJob = launch {
                    val messaging = TorMessagingService.getInstance(context)
                    if (messaging.serviceState.value == TorMessagingService.ServiceState.STOPPED) {
                        TorForegroundService.start(context)
                        messaging.start()
                    }
                    runCatching {
                        messaging.preparePeerForUrgentCallSignal(canonicalOnion)
                    }

                    val supportedCodecs = if (isVideoCall) {
                        videoEngine.getSupportedHardwareCodecs()
                    } else {
                        listOf(CallVideoEngine.CODEC_AVC)
                    }
                    val myInfo = messaging.getMyContactInfo()
                    localIdentityPubHexForCall = myInfo.publicKey
                    val offerPayload = buildSignalJson(
                        type = "OFFER",
                        callId = callId,
                        senderOnion = myInfo.onionAddress,
                        senderNickname = myInfo.nickname,
                        senderPubKey = myInfo.publicKey,
                        sessionKey = sessionKey,
                        ephemeralPubHex = offerEphHex,
                        forceTorOnly = effectiveForceTor,
                        candidates = initialCandidates,
                        isVideo = isVideoCall,
                        localVideoEnabled = isVideoCall,
                        videoCodecs = supportedCodecs
                    )
                    val sessionKeyCopy = sessionKey.copyOf()
                    AegisTorCrypto.zeroize(sessionKey)

                    val sent = sendCallSignalWithRetry(messaging, canonicalOnion, offerPayload, maxAttempts = 3)
                    callMutex.withLock {
                        val current = _sessionFlow.value
                        if (current?.callId == callId) {
                            if (sent) {
                                _sessionFlow.update { s ->
                                    if (s?.callId == callId && s.state == CallState.OUTGOING_DIALING) {
                                        s.copy(statusMessage = "Calling ${s.peerNickname}...")
                                    } else s
                                }
                                startOutgoingRingbackTone()
                                scope.launch {
                                    for ((idx, waitMs) in listOf(1_200L, 1_800L, 2_800L).withIndex()) {
                                        delay(waitMs)
                                        val stillDialing = _sessionFlow.value
                                        if (stillDialing?.callId != callId || stillDialing.state != CallState.OUTGOING_DIALING) {
                                            break
                                        }
                                        if (idx == 1) {
                                            runCatching { messaging.preparePeerForUrgentCallSignal(canonicalOnion) }
                                        }
                                        val refreshedOffer = buildSignalJson(
                                            type = "OFFER",
                                            callId = callId,
                                            senderOnion = myInfo.onionAddress,
                                            senderNickname = myInfo.nickname,
                                            senderPubKey = myInfo.publicKey,
                                            sessionKey = sessionKeyCopy,
                                            ephemeralPubHex = offerEphHex,
                                            forceTorOnly = effectiveForceTor,
                                            candidates = if (effectiveForceTor) emptyList() else localUdpCandidates.toList(),
                                            isVideo = isVideoCall,
                                            localVideoEnabled = isVideoCall,
                                            videoCodecs = supportedCodecs
                                        )
                                        messaging.sendCallSignalFrame(canonicalOnion, refreshedOffer)
                                    }
                                    AegisTorCrypto.zeroize(sessionKeyCopy)
                                }
                            } else {
                                AegisTorCrypto.zeroize(sessionKeyCopy)
                                endCallInternal(
                                    callId = callId,
                                    reason = "Peer unreachable over Tor",
                                    notifyPeer = false,
                                    logText = if (isVideoCall) "📹 Outgoing video call (Unreachable)" else "📞 Outgoing voice call (Unreachable)"
                                )
                            }
                        } else {
                            AegisTorCrypto.zeroize(sessionKeyCopy)
                        }
                    }
                }
            }
        }
    }

    /**
     * Accept the currently ringing incoming call (with or without camera video).
     */
    fun acceptIncomingCall(withVideo: Boolean? = null) {
        scope.launch {
            callMutex.withLock {
                val session = _sessionFlow.value ?: return@withLock
                if (session.state != CallState.INCOMING_RINGING) return@withLock

                ringTimeoutJob?.cancel()
                stopRingingAndVibration()

                // Give Android AudioPolicyManager a brief moment to release Ringtone focus cleanly
                delay(60L)

                val initialCandidates = if (!session.forceTorOnly) {
                    if (udpSocket == null || udpSocket?.isClosed == true) {
                        openDirectUdpSocketFast(session.callId, session.peerOnion)
                    } else {
                        localUdpCandidates.toList()
                    }
                } else {
                    emptyList()
                }

                val enableLocalVideo = withVideo ?: session.isVideoCall
                val useVideoCall = session.isVideoCall || enableLocalVideo || session.isRemoteVideoEnabled
                val useSpeaker = session.isSpeakerphone || useVideoCall
                val now = System.currentTimeMillis()
                lastPeerActivityAtMs = now
                _sessionFlow.value = session.copy(
                    state = CallState.ACTIVE,
                    connectedAtMs = now,
                    durationSeconds = 0L,
                    isMultipathActive = !session.forceTorOnly,
                    isSpeakerphone = useSpeaker,
                    isVideoCall = useVideoCall,
                    isLocalVideoEnabled = enableLocalVideo,
                    statusMessage = if (session.forceTorOnly) {
                        "Encrypted • Tor Onion Only"
                    } else {
                        "Encrypted • Hybrid P2P + Tor"
                    }
                )

                startTorAudioSenderLoop(session.callId)
                startTorVideoSenderLoop(session.callId)
                publishSecurityCode(session.callId)

                val audioStarted = audioEngine?.start(
                    initialSpeakerphone = useSpeaker,
                    isDirectP2p = false
                ) ?: false

                if (!audioStarted) {
                    endCallInternal(
                        callId = session.callId,
                        reason = "Microphone unavailable",
                        notifyPeer = true,
                        logText = if (useVideoCall) "📹 Incoming video call (Mic error)" else "📞 Incoming voice call (Mic error)"
                    )
                    return@withLock
                }

                videoEngine.configureSession(
                    isCallActive = true,
                    isLocalEnabled = enableLocalVideo,
                    isFrontCam = session.isFrontCamera,
                    isDirectP2p = false,
                    codec = session.negotiatedVideoCodec
                )
                if (useVideoCall) {
                    webrtcVideoEngine.configureLocalCamera(
                        enabled = enableLocalVideo,
                        frontCamera = session.isFrontCamera
                    )
                    if (!session.forceTorOnly) {
                        webrtcVideoEngine.startPeerConnection(
                            generation = webrtcGeneration.incrementAndGet(),
                            isOfferer = true,
                            frontCamera = session.isFrontCamera,
                            videoEnabled = enableLocalVideo
                        )
                    }
                }

                startCallTimer(session.callId)
                startCallHeartbeatLoop(session.callId, session.peerOnion)
                runCatching { TorCircuitManager.getInstance(context).ensureBulkConnectionAsync(session.peerOnion) }
                if (!session.forceTorOnly) {
                    startDirectP2pProbing(session.callId)
                }

                // Send ACCEPT signal back to caller immediately over BOTH Direct UDP and Tor (with automatic retry until caller acknowledges)
                val acceptEphHex = localEphemeralPubHex
                val acceptForceTor = session.forceTorOnly
                acceptRetryJob?.cancel()
                acceptRetryJob = launch {
                    val messaging = TorMessagingService.getInstance(context)
                    val myInfo = messaging.getMyContactInfo()
                    val acceptPayload = buildSignalJson(
                        type = "ACCEPT",
                        callId = session.callId,
                        senderOnion = myInfo.onionAddress,
                        senderNickname = myInfo.nickname,
                        senderPubKey = myInfo.publicKey,
                        sessionKey = if (activeCallCrypto?.isPfs == true) null else currentSessionKey,
                        ephemeralPubHex = acceptEphHex.takeIf { it.isNotBlank() },
                        forceTorOnly = acceptForceTor,
                        candidates = initialCandidates,
                        isVideo = useVideoCall,
                        localVideoEnabled = enableLocalVideo,
                        videoCodecs = videoEngine.getSupportedHardwareCodecs()
                    )
                    if (!acceptForceTor) {
                        launch {
                            for (delayMs in longArrayOf(0L, 100L, 220L, 450L, 900L)) {
                                if (delayMs > 0L) delay(delayMs)
                                val cur = _sessionFlow.value
                                if (cur?.callId != session.callId || cur.state != CallState.ACTIVE) break
                                if (preAcceptCrypto == null || rxAudioFramesCounter.get() > 0L) break
                                sendPreAcceptUdpSignal(session.callId, acceptPayload)
                            }
                        }
                    }
                    messaging.preparePeerForUrgentCallSignal(session.peerOnion)
                    val ok = messaging.sendCallSignalFrame(session.peerOnion, acceptPayload)
                    if (!ok) {
                        sendCallSignalWithRetry(messaging, session.peerOnion, acceptPayload, maxAttempts = 2)
                    }
                    for (retryWait in longArrayOf(1_200L, 1_800L)) {
                        delay(retryWait)
                        val cur = _sessionFlow.value
                        if (cur?.callId != session.callId || cur.state != CallState.ACTIVE) break
                        if (preAcceptCrypto == null || lastDirectP2pVerifiedAt > 0L || rxAudioFramesCounter.get() > 0L) break
                        messaging.preparePeerForUrgentCallSignal(session.peerOnion)
                        messaging.sendCallSignalFrame(session.peerOnion, acceptPayload)
                    }
                    if (session.isRemoteVideoEnabled) {
                        sendVideoPliRequest()
                    }
                }
            }
        }
    }

    /**
     * Decline an incoming ringing call.
     */
    fun declineIncomingCall() {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                if (current.state != CallState.INCOMING_RINGING) return@withLock
                val messaging = TorMessagingService.getInstance(context)
                val myInfo = messaging.getMyContactInfo()
                val payload = buildSignalJson(
                    type = "DECLINE",
                    callId = current.callId,
                    senderOnion = myInfo.onionAddress,
                    senderNickname = myInfo.nickname,
                    senderPubKey = myInfo.publicKey
                )
                launch { sendCallSignalWithRetry(messaging, current.peerOnion, payload, maxAttempts = 2) }
                endCallInternal(
                    callId = current.callId,
                    reason = "Call declined",
                    notifyPeer = false,
                    logText = if (current.isVideoCall) "📹 Declined video call" else "📞 Declined voice call"
                )
            }
        }
    }

    /**
     * Hang up an active or dialing call.
     */
    fun endCall(reason: String = "Call ended", notifyPeer: Boolean = true) {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                val icon = if (current.isVideoCall) "📹" else "📞"
                val kind = if (current.isVideoCall) "video" else "voice"
                val duration = current.durationSeconds
                val logSummary = when {
                    current.state == CallState.ACTIVE -> {
                        val mins = duration / 60
                        val secs = duration % 60
                        val durStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                        if (current.isIncoming) "$icon Incoming $kind call ($durStr)"
                        else "$icon Outgoing $kind call ($durStr)"
                    }
                    current.isIncoming -> "$icon Missed $kind call"
                    else -> "$icon Cancelled $kind call"
                }
                endCallInternal(
                    callId = current.callId,
                    reason = reason,
                    notifyPeer = notifyPeer,
                    logText = logSummary
                )
            }
        }
    }

    fun toggleMute() {
        val current = _sessionFlow.value ?: return
        val nextMute = !current.isMuted
        audioEngine?.setMuted(nextMute)
        _sessionFlow.update { it?.copy(isMuted = nextMute) }
    }

    fun toggleSpeakerphone() {
        val current = _sessionFlow.value ?: return
        val routes = _availableAudioRoutesFlow.value
        if (routes.size > 2) {
            val next = audioEngine?.cycleAudioRoute() ?: return
            _sessionFlow.update { it?.copy(isSpeakerphone = next == AudioRoute.SPEAKER) }
        } else {
            val nextSpeaker = !current.isSpeakerphone
            audioEngine?.setSpeakerphone(nextSpeaker)
            _sessionFlow.update { it?.copy(isSpeakerphone = nextSpeaker) }
        }
    }

    fun cycleAudioRoute(): AudioRoute? {
        val next = audioEngine?.cycleAudioRoute() ?: return null
        _sessionFlow.update { it?.copy(isSpeakerphone = next == AudioRoute.SPEAKER) }
        return next
    }

    fun setAudioRoute(route: AudioRoute) {
        audioEngine?.setAudioRoute(route)
        _sessionFlow.update { it?.copy(isSpeakerphone = route == AudioRoute.SPEAKER) }
    }

    fun toggleForceTorOnly() {
        val current = _sessionFlow.value ?: return
        val nextForceTor = !current.forceTorOnly
        applyForceTorOnlyInternal(current, nextForceTor, notifyPeer = true)
    }

    private fun applyForceTorOnlyInternal(
        current: CallSessionInfo,
        nextForceTor: Boolean,
        notifyPeer: Boolean
    ) {
        if (nextForceTor) {
            p2pProbeJob?.cancel()
            p2pProbeJob = null
            p2pReceiveJob?.cancel()
            p2pReceiveJob = null
            stunDiscoveryJob?.cancel()
            stunDiscoveryJob = null
            try { udpSocket?.close() } catch (_: Exception) {}
            udpSocket = null
            localUdpCandidates.clear()
            peerUdpCandidates.clear()
            candidateDirectPeerEndpoint = null
            verifiedDirectPeerEndpoint = null
            lastDirectP2pVerifiedAt = 0L
            audioEngine?.setTransportMode(false)
            webrtcVideoEngine.stopPeerConnectionOnly()
            videoEngine.updateNetworkProfile(isDirectP2p = false, rttMs = current.rttMs, lossDetected = false)
            _sessionFlow.update {
                it?.copy(
                    forceTorOnly = true,
                    isMultipathActive = false,
                    transportMode = CallTransportMode.TOR_ONION,
                    statusMessage = "Encrypted • Tor Onion Only"
                )
            }
        } else {
            _sessionFlow.update {
                it?.copy(
                    forceTorOnly = false,
                    isMultipathActive = true,
                    statusMessage = "Encrypted • Hybrid P2P + Tor"
                )
            }
            if (current.state == CallState.ACTIVE) {
                if (udpSocket == null) {
                    openDirectUdpSocketFast(current.callId, current.peerOnion)
                } else {
                    udpSocket?.let { discoverStunAndTrickleCandidatesAsync(current.callId, current.peerOnion, it) }
                }
                startDirectP2pProbing(current.callId)
                if (current.isVideoCall || current.isLocalVideoEnabled || current.isRemoteVideoEnabled) {
                    webrtcVideoEngine.startPeerConnection(
                        generation = webrtcGeneration.incrementAndGet(),
                        isOfferer = true,
                        frontCamera = current.isFrontCamera,
                        videoEnabled = current.isLocalVideoEnabled
                    )
                }
            }
        }
        if (notifyPeer && current.peerOnion.isNotBlank()) {
            scope.launch {
                val messaging = TorMessagingService.getInstance(context)
                val myInfo = messaging.getMyContactInfo()
                val modePayload = buildSignalJson(
                    type = "TOR_MODE",
                    callId = current.callId,
                    senderOnion = myInfo.onionAddress,
                    senderNickname = myInfo.nickname,
                    senderPubKey = myInfo.publicKey,
                    forceTorOnly = nextForceTor
                )
                messaging.sendCallSignalFrame(current.peerOnion, modePayload)
            }
        }
    }

    /**
     * Manual 1-tap route boost & circuit resync from the Call UI.
     */
    fun refreshCallRoute() {
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.ACTIVE) return
        _sessionFlow.update {
            it?.copy(
                isMultipathActive = !it.forceTorOnly,
                statusMessage = "Boosting P2P + Tor audio route..."
            )
        }
        scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            if (!current.forceTorOnly) {
                verifiedDirectPeerEndpoint = null
                candidateDirectPeerEndpoint = null
                lastDirectP2pVerifiedAt = 0L
                firstContinuousDirectOkAtMs = 0L
                localUdpCandidates.clear()
                synchronized(discoveredStunPortsByIp) {
                    discoveredStunPortsByIp.clear()
                }
                runCatching { udpSocket?.close() }
                udpSocket = null
                openDirectUdpSocketFast(current.callId, current.peerOnion)
                startDirectP2pProbing(current.callId)
                if (current.isVideoCall || current.isLocalVideoEnabled || current.isRemoteVideoEnabled) {
                    if (webrtcVideoEngine.sessionActive.value) {
                        webrtcVideoEngine.requestIceRestart()
                    } else {
                        webrtcVideoEngine.startPeerConnection(
                            generation = webrtcGeneration.incrementAndGet(),
                            isOfferer = true,
                            frontCamera = current.isFrontCamera,
                            videoEnabled = current.isLocalVideoEnabled
                        )
                    }
                }
            }
            val hb = buildSignalJson(
                type = "HEARTBEAT",
                callId = current.callId,
                senderOnion = myInfo.onionAddress,
                senderNickname = myInfo.nickname,
                senderPubKey = myInfo.publicKey,
                candidates = localUdpCandidates.toList()
            )
            messaging.sendCallSignalFrame(current.peerOnion, hb)
        }
    }

    fun setMinimized(minimized: Boolean) {
        _sessionFlow.update { it?.copy(isMinimized = minimized) }
    }

    fun toggleLocalVideo(explicitEnabled: Boolean? = null) {
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.ACTIVE &&
            current.state != CallState.OUTGOING_DIALING &&
            current.state != CallState.OUTGOING_RINGING
        ) return
        val nextEnabled = explicitEnabled ?: !current.isLocalVideoEnabled
        setLocalVideoInternal(nextEnabled)
    }

    private fun setLocalVideoInternal(enabled: Boolean) {
        val current = _sessionFlow.value ?: return
        val nextSpeaker = if (enabled && !current.isSpeakerphone && current.state == CallState.ACTIVE) {
            audioEngine?.setSpeakerphone(true)
            true
        } else {
            current.isSpeakerphone
        }
        _sessionFlow.update { s ->
            s?.copy(
                isVideoCall = s.isVideoCall || enabled,
                isLocalVideoEnabled = enabled,
                isSpeakerphone = nextSpeaker,
                isVideoSwapped = if (!enabled) false else s.isVideoSwapped
            )
        }
        val isDirect = current.transportMode == CallTransportMode.DIRECT_P2P && !current.forceTorOnly
        webrtcVideoEngine.configureLocalCamera(
            enabled = enabled,
            frontCamera = current.isFrontCamera
        )
        if (enabled && current.state == CallState.ACTIVE && !current.forceTorOnly && !webrtcVideoEngine.sessionActive.value) {
            webrtcVideoEngine.startPeerConnection(
                generation = webrtcGeneration.incrementAndGet(),
                isOfferer = true,
                frontCamera = current.isFrontCamera,
                videoEnabled = true
            )
        }
        videoEngine.configureSession(
            isCallActive = current.state == CallState.ACTIVE,
            isLocalEnabled = enabled,
            isFrontCam = current.isFrontCamera,
            isDirectP2p = isDirect,
            codec = current.negotiatedVideoCodec
        )
        sendVideoStateNotification(current.callId, current.peerOnion, enabled)
        if (!enabled && current.isRemoteVideoEnabled) {
            sendVideoPliRequest()
        }
    }

    fun switchCamera() {
        val current = _sessionFlow.value ?: return
        if (webrtcVideoEngine.isSwitchingCamera.value) return
        val nextFront = !current.isFrontCamera
        _sessionFlow.update { it?.copy(isFrontCamera = nextFront) }
        webrtcVideoEngine.switchCamera(nextFront)
        videoEngine.switchCamera(nextFront)
        if (current.state == CallState.ACTIVE && current.isLocalVideoEnabled) {
            scope.launch {
                delay(350L)
                videoEngine.requestImmediateKeyframe()
            }
        }
    }

    fun toggleSwapVideoViews() {
        _sessionFlow.update { s ->
            if (s != null && (s.isLocalVideoEnabled || s.isRemoteVideoEnabled)) {
                s.copy(isVideoSwapped = !s.isVideoSwapped)
            } else s
        }
    }

    fun onAppForegroundStateChanged(inForeground: Boolean) {
        val current = _sessionFlow.value ?: return
        if (!inForeground) {
            if (current.state == CallState.ACTIVE && current.isLocalVideoEnabled) {
                wasLocalVideoPausedByBg = true
                setLocalVideoInternal(false)
            }
        } else if (wasLocalVideoPausedByBg) {
            wasLocalVideoPausedByBg = false
            if (current.state == CallState.ACTIVE && !current.isLocalVideoEnabled) {
                setLocalVideoInternal(true)
            }
        }
    }

    /**
     * Called by TorMessagingService when an encrypted CALL_SIGNAL frame arrives.
     */
    fun onIncomingTorCallSignal(senderOnion: String, payload: ByteArray) {
        val json = try {
            JSONObject(String(payload, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Malformed CALL_SIGNAL payload: ${e.message}")
            return
        }

        if (json.optString("scope") == GroupCallManager.SCOPE_GROUP_CALL) {
            GroupCallManager.getInstance(context).onIncomingGroupCallSignal(senderOnion, json)
            return
        }

        val type = json.optString("type")
        val callId = json.optString("callId")
        if (type.isBlank() || callId.isBlank()) return

        // Always trust the authenticated transport senderOnion over unauthenticated JSON fields
        val canonicalSender = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(senderOnion)
            .ifBlank {
                OnionContactManager.OnionContact.canonicalizeOnionEndpoint(json.optString("senderOnion"))
            }
            .ifBlank { senderOnion.trim().lowercase() }
        if (canonicalSender.isBlank()) return

        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value
                // Every non-OFFER signal MUST originate from the active call's authenticated peer
                if (type != "OFFER") {
                    if (current == null || current.callId != callId) return@withLock
                    val activePeerCanonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(current.peerOnion)
                        .ifBlank { current.peerOnion.trim().lowercase() }
                    if (canonicalSender != activePeerCanonical) {
                        Log.w(TAG, "Rejected spoofed CALL_SIGNAL($type) from mismatched peer")
                        return@withLock
                    }
                }
                when (type) {
                    "OFFER" -> {
                        val contactManager = OnionContactManager.getInstance(context)
                        val contact = contactManager.getContactByOnion(canonicalSender)
                            ?: json.optString("senderPubKey").takeIf { it.isNotBlank() }
                                ?.let { contactManager.getContactByPublicKey(it) }
                        if (contact != null &&
                            (contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
                                contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED)
                        ) {
                            Log.w(TAG, "Blocked incoming call OFFER from untrusted/blocked contact")
                            return@withLock
                        }

                        if (GroupCallManager.getInstance(context).hasActiveCall()) {
                            val messaging = TorMessagingService.getInstance(context)
                            val myInfo = messaging.getMyContactInfo()
                            val busyPayload = buildSignalJson(
                                type = "BUSY",
                                callId = callId,
                                senderOnion = myInfo.onionAddress,
                                senderNickname = myInfo.nickname,
                                senderPubKey = myInfo.publicKey
                            )
                            launch { messaging.sendCallSignalFrame(canonicalSender, busyPayload) }
                            return@withLock
                        }

                        if (current != null && current.state != CallState.IDLE && current.state != CallState.ENDED) {
                            if (current.callId == callId) return@withLock
                            val currentCanonicalPeer = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(current.peerOnion)
                            val isGlare = (current.state == CallState.OUTGOING_DIALING || current.state == CallState.OUTGOING_RINGING) &&
                                currentCanonicalPeer == canonicalSender
                            if (isGlare) {
                                val myCanonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(
                                    TorMessagingService.getInstance(context).getMyContactInfo().onionAddress
                                )
                                if (myCanonical.isNotBlank() && myCanonical < canonicalSender) {
                                    Log.i(TAG, "⚡ Resolving simultaneous call glare: yielding outgoing offer to $canonicalSender")
                                    cleanupCallResourcesLocked()
                                } else {
                                    Log.i(TAG, "⚡ Resolving simultaneous call glare: keeping outgoing offer over $canonicalSender")
                                    return@withLock
                                }
                            } else {
                                val messaging = TorMessagingService.getInstance(context)
                                val myInfo = messaging.getMyContactInfo()
                                val busyPayload = buildSignalJson(
                                    type = "BUSY",
                                    callId = callId,
                                    senderOnion = myInfo.onionAddress,
                                    senderNickname = myInfo.nickname,
                                    senderPubKey = myInfo.publicKey
                                )
                                launch { messaging.sendCallSignalFrame(canonicalSender, busyPayload) }
                                return@withLock
                            }
                        }

                        cleanupCallResourcesLocked()

                        val callerName = contact?.nickname
                            ?: json.optString("senderNickname").ifBlank { canonicalSender.take(12) }
                        val callerPubKey = contact?.publicKey
                            ?: json.optString("senderPubKey")
                        val myStaticPub = runCatching {
                            TorMessagingService.getInstance(context).getMyContactInfo().publicKey
                        }.getOrDefault("")
                        localIdentityPubHexForCall = myStaticPub
                        remoteIdentityPubHexForCall = callerPubKey

                        val sessionKeyHex = json.optString("sessionKeyHex")
                        val preAcceptKeyBytes = if (sessionKeyHex.length == 64) hexToBytes(sessionKeyHex) else null
                        if (preAcceptKeyBytes != null) {
                            preAcceptCrypto?.zeroize()
                            preAcceptCrypto = AegisCallCrypto.deriveLegacySession(callId, isCaller = false, preAcceptKeyBytes)
                        }

                        val peerEphHex = json.optString("ephemeralPubHex")
                        val peerEphBytes = if (peerEphHex.length == 64) AegisCallCrypto.fromHex(peerEphHex) else null
                        if (peerEphBytes != null && peerEphBytes.size == 32) {
                            val myEph = AegisCallCrypto.generateEphemeralKeyPair()
                            localEphemeralPubHex = AegisCallCrypto.toHex(myEph.publicKey)
                            try {
                                val pfsSession = AegisCallCrypto.derivePfsSession(
                                    callId = callId,
                                    isCaller = false,
                                    localEphemeralPriv = myEph.privateKey,
                                    localEphemeralPub = myEph.publicKey,
                                    remoteEphemeralPub = peerEphBytes,
                                    localIdentityPubHex = myStaticPub,
                                    remoteIdentityPubHex = callerPubKey
                                )
                                bindPfsSessionCrypto(pfsSession)
                            } catch (e: Exception) {
                                Log.w(TAG, "Rejected invalid ephemeral X25519 call OFFER: ${e.message}")
                                preAcceptKeyBytes?.let { AegisTorCrypto.zeroize(it) }
                                preAcceptCrypto?.zeroize()
                                preAcceptCrypto = null
                                return@withLock
                            } finally {
                                AegisTorCrypto.zeroize(myEph.privateKey)
                            }
                        } else if (preAcceptKeyBytes != null) {
                            bindSessionCrypto(callId, preAcceptKeyBytes, isCaller = false)
                        } else {
                            activeCallCrypto?.zeroize()
                            activeCallCrypto = null
                            currentSessionKey = null
                            currentSessionKeySpec = null
                            currentCallUuid = runCatching { UUID.fromString(callId) }.getOrNull()
                        }
                        preAcceptKeyBytes?.let { AegisTorCrypto.zeroize(it) }

                        val effectiveForceTor = json.optBoolean("forceTorOnly", false) ||
                            PrivacyPreferenceManager.alwaysRouteCallsOverTor.value
                        if (!effectiveForceTor) {
                            mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))
                        }
                        val offerIsVideo = json.optBoolean("isVideo", false)
                        val negotiatedCodec = negotiateVideoCodec(json.optJSONArray("videoCodecs"))

                        val incomingSession = CallSessionInfo(
                            callId = callId,
                            peerOnion = canonicalSender,
                            peerNickname = callerName,
                            peerPublicKey = callerPubKey,
                            contactId = contact?.id,
                            isIncoming = true,
                            state = CallState.INCOMING_RINGING,
                            transportMode = CallTransportMode.TOR_ONION,
                            isMultipathActive = !effectiveForceTor,
                            forceTorOnly = effectiveForceTor,
                            isSpeakerphone = offerIsVideo,
                            isVideoCall = offerIsVideo,
                            isRemoteVideoEnabled = offerIsVideo,
                            negotiatedVideoCodec = negotiatedCodec,
                            statusMessage = if (offerIsVideo) {
                                "Incoming Encrypted HD Video Call..."
                            } else {
                                "Incoming Encrypted Call..."
                            }
                        )
                        _sessionFlow.value = incomingSession
                        val calleeCandidates = if (!effectiveForceTor) {
                            val opened = openDirectUdpSocketFast(callId, canonicalSender)
                            startDirectP2pProbing(callId)
                            opened
                        } else {
                            emptyList()
                        }
                        CallForegroundService.start(context)
                        registerCallNetworkCallback()
                        startIncomingRingtoneAndVibration()
                        startRingTimeout(callId)

                        // Reply with RINGING immediately over BOTH Direct UDP and Tor
                        launch {
                            val messaging = TorMessagingService.getInstance(context)
                            val myInfo = messaging.getMyContactInfo()
                            val ringingPayload = buildSignalJson(
                                type = "RINGING",
                                callId = callId,
                                senderOnion = myInfo.onionAddress,
                                senderNickname = myInfo.nickname,
                                senderPubKey = myInfo.publicKey,
                                candidates = calleeCandidates
                            )
                            if (!effectiveForceTor) {
                                sendPreAcceptUdpSignal(callId, ringingPayload)
                            }
                            messaging.sendCallSignalFrame(canonicalSender, ringingPayload)
                        }
                    }

                    "RINGING" -> {
                        if (current?.callId == callId &&
                            (current.state == CallState.OUTGOING_DIALING || current.state == CallState.OUTGOING_RINGING)
                        ) {
                            if (!current.forceTorOnly) {
                                mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))
                                startDirectP2pProbing(callId)
                            }
                            if (current.state == CallState.OUTGOING_DIALING) {
                                _sessionFlow.value = current.copy(
                                    state = CallState.OUTGOING_RINGING,
                                    statusMessage = "Ringing..."
                                )
                            }
                        }
                    }

                    "ACCEPT" -> {
                        if (current?.callId == callId &&
                            (current.state == CallState.OUTGOING_DIALING || current.state == CallState.OUTGOING_RINGING)
                        ) {
                            outgoingOfferJob?.cancel()
                            ringTimeoutJob?.cancel()
                            stopRingingAndVibration()
                            delay(60L)

                            val peerForceTor = json.optBoolean("forceTorOnly", false)
                            val effectiveForceTor = current.forceTorOnly || peerForceTor
                            if (effectiveForceTor && !current.forceTorOnly) {
                                applyForceTorOnlyInternal(current, true, notifyPeer = false)
                            }
                            if (!effectiveForceTor) {
                                mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))
                            }

                            // Complete caller-side Ephemeral X25519 PFS key derivation if peer returned ephemeralPubHex
                            val peerEphHex = json.optString("ephemeralPubHex")
                            val peerEphBytes = if (peerEphHex.length == 64) AegisCallCrypto.fromHex(peerEphHex) else null
                            val myPriv = localEphemeralPriv
                            val myPub = AegisCallCrypto.fromHex(localEphemeralPubHex)
                            if (peerEphBytes != null && peerEphBytes.size == 32 &&
                                myPriv != null && myPriv.size == 32 &&
                                myPub != null && myPub.size == 32
                            ) {
                                try {
                                    val peerStaticPub = current.peerPublicKey.ifBlank { json.optString("senderPubKey") }
                                    remoteIdentityPubHexForCall = peerStaticPub
                                    val pfsSession = AegisCallCrypto.derivePfsSession(
                                        callId = callId,
                                        isCaller = true,
                                        localEphemeralPriv = myPriv,
                                        localEphemeralPub = myPub,
                                        remoteEphemeralPub = peerEphBytes,
                                        localIdentityPubHex = localIdentityPubHexForCall,
                                        remoteIdentityPubHex = peerStaticPub
                                    )
                                    bindPfsSessionCrypto(pfsSession)
                                } catch (e: Exception) {
                                    Log.w(TAG, "PFS ACCEPT key derivation failed: ${e.message}")
                                } finally {
                                    AegisTorCrypto.zeroize(myPriv)
                                    localEphemeralPriv = null
                                }
                            }

                            p2pFastBurstCounter.set(0)
                            val peerVideoOn = json.optBoolean("localVideoEnabled", json.optBoolean("isVideo", false))
                            val negotiatedCodec = negotiateVideoCodec(json.optJSONArray("videoCodecs"))

                            val now = System.currentTimeMillis()
                            lastPeerActivityAtMs = now
                            _sessionFlow.value = current.copy(
                                state = CallState.ACTIVE,
                                connectedAtMs = now,
                                durationSeconds = 0L,
                                forceTorOnly = effectiveForceTor,
                                isMultipathActive = !effectiveForceTor,
                                isRemoteVideoEnabled = peerVideoOn,
                                isVideoCall = current.isVideoCall || peerVideoOn,
                                negotiatedVideoCodec = negotiatedCodec,
                                statusMessage = if (effectiveForceTor) {
                                    "Encrypted • Tor Onion Only"
                                } else {
                                    "Encrypted • Hybrid P2P + Tor"
                                }
                            )
                            startTorVideoSenderLoop(callId)
                            publishSecurityCode(callId)

                            val started = audioEngine?.start(
                                initialSpeakerphone = current.isSpeakerphone || current.isLocalVideoEnabled || peerVideoOn,
                                isDirectP2p = false
                            ) ?: false

                            if (!started) {
                                endCallInternal(
                                    callId = callId,
                                    reason = "Microphone unavailable",
                                    notifyPeer = true,
                                    logText = if (current.isVideoCall) "📹 Outgoing video call (Mic error)" else "📞 Outgoing voice call (Mic error)"
                                )
                                return@withLock
                            }

                            videoEngine.configureSession(
                                isCallActive = true,
                                isLocalEnabled = current.isLocalVideoEnabled,
                                isFrontCam = current.isFrontCamera,
                                isDirectP2p = false,
                                codec = negotiatedCodec
                            )
                            if (current.isVideoCall || peerVideoOn) {
                                webrtcVideoEngine.configureLocalCamera(
                                    enabled = current.isLocalVideoEnabled,
                                    frontCamera = current.isFrontCamera
                                )
                                if (!effectiveForceTor) {
                                    // Callee sends WEBRTC_SDP offer immediately on ACCEPT; if none arrives within 1.8s, caller initiates offer.
                                    launch {
                                        delay(1_800L)
                                        val s = _sessionFlow.value
                                        if (s?.callId == callId &&
                                            s.state == CallState.ACTIVE &&
                                            !s.forceTorOnly &&
                                            !webrtcVideoEngine.sessionActive.value
                                        ) {
                                            webrtcVideoEngine.startPeerConnection(
                                                generation = webrtcGeneration.incrementAndGet(),
                                                isOfferer = true,
                                                frontCamera = s.isFrontCamera,
                                                videoEnabled = s.isLocalVideoEnabled
                                            )
                                        }
                                    }
                                }
                            }
                            if (current.isLocalVideoEnabled) {
                                videoEngine.requestImmediateKeyframe()
                            }

                            startCallTimer(callId)
                            startCallHeartbeatLoop(callId, current.peerOnion)
                            if (!effectiveForceTor) {
                                startDirectP2pProbing(callId)
                            }
                            preAcceptCrypto?.zeroize()
                            preAcceptCrypto = null
                            launch {
                                val messaging = TorMessagingService.getInstance(context)
                                val myInfo = messaging.getMyContactInfo()
                                val ackPayload = buildSignalJson(
                                    type = "ACCEPT_ACK",
                                    callId = callId,
                                    senderOnion = myInfo.onionAddress,
                                    senderNickname = myInfo.nickname,
                                    senderPubKey = myInfo.publicKey
                                )
                                if (!effectiveForceTor) {
                                    val udpCtrl = ByteArray(1 + ackPayload.size).also {
                                        it[0] = VIDEO_CTRL_WEBRTC_SIG
                                        System.arraycopy(ackPayload, 0, it, 1, ackPayload.size)
                                    }
                                    for (target in getActiveUdpTargets()) {
                                        sendDirectUdpVideoControl(callId, target, udpCtrl)
                                    }
                                }
                                messaging.sendCallSignalFrame(canonicalSender, ackPayload)
                            }
                        }
                    }

                    "ACCEPT_ACK" -> {
                        if (current?.callId == callId && current.state == CallState.ACTIVE) {
                            lastPeerActivityAtMs = System.currentTimeMillis()
                            acceptRetryJob?.cancel()
                            preAcceptCrypto?.zeroize()
                            preAcceptCrypto = null
                        }
                    }

                    "TOR_MODE" -> {
                        if (current?.callId == callId) {
                            val remoteWantsTorOnly = json.optBoolean("forceTorOnly", false)
                            if (remoteWantsTorOnly && !current.forceTorOnly) {
                                applyForceTorOnlyInternal(current, true, notifyPeer = false)
                            }
                        }
                    }

                    "VIDEO_STATE" -> {
                        if (current?.callId == callId) {
                            val remoteEnabled = json.optBoolean("localVideoEnabled", false)
                            _sessionFlow.update { s ->
                                if (s?.callId == callId) {
                                    s.copy(
                                        isVideoCall = s.isVideoCall || remoteEnabled,
                                        isRemoteVideoEnabled = remoteEnabled,
                                        isVideoSwapped = if (!remoteEnabled) false else s.isVideoSwapped
                                    )
                                } else s
                            }
                            if (remoteEnabled) {
                                sendVideoPliRequest()
                            }
                        }
                    }

                    "VIDEO_PLI" -> {
                        if (current?.callId == callId && current.state == CallState.ACTIVE && current.isLocalVideoEnabled) {
                            videoEngine.requestImmediateKeyframe()
                        }
                    }

                    "WEBRTC_SDP_PART" -> {
                        if (current?.callId == callId && !current.forceTorOnly) {
                            val partId = json.optString("partId")
                            val partIdx = json.optInt("partIndex", -1)
                            val totalParts = json.optInt("totalParts", 0)
                            val gen = json.optInt("generation", 1)
                            val sdpType = json.optString("sdpType")
                            val isGzip = json.optBoolean("isGzip", true)
                            val chunk = json.optString("chunk")
                            if (partId.isNotBlank() && totalParts in 2..MAX_SDP_PARTS &&
                                partIdx in 0 until totalParts && chunk.isNotEmpty()
                            ) {
                                val now = System.currentTimeMillis()
                                pendingSdpParts.entries.removeIf { now - it.value.createdAtMs > 30_000L }
                                val buf = pendingSdpParts.getOrPut("$callId:$partId") {
                                    PendingSdpParts(gen, sdpType, isGzip, totalParts, createdAtMs = now)
                                }
                                if (buf.totalParts == totalParts && buf.generation == gen) {
                                    buf.parts[partIdx] = chunk
                                    if (buf.parts.size == totalParts) {
                                        pendingSdpParts.remove("$callId:$partId")
                                        val joined = (0 until totalParts).joinToString("") { buf.parts[it].orEmpty() }
                                        val fullSdp = if (isGzip) decompressSdpGzipBase64Bounded(joined) else joined
                                        if (fullSdp.isNotBlank()) {
                                            handleCompleteRemoteWebRtcSdpLocked(current, canonicalSender, gen, sdpType, fullSdp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "WEBRTC_SDP" -> {
                        if (current?.callId == callId && !current.forceTorOnly) {
                            val gen = json.optInt("generation", 1)
                            val sdpType = json.optString("sdpType")
                            val sdpGz = json.optString("sdpGz")
                            val sdp = if (sdpGz.isNotBlank()) {
                                decompressSdpGzipBase64Bounded(sdpGz).ifBlank { json.optString("sdp") }
                            } else {
                                json.optString("sdp")
                            }
                            if (sdpType.isNotBlank() && sdp.isNotBlank()) {
                                handleCompleteRemoteWebRtcSdpLocked(current, canonicalSender, gen, sdpType, sdp)
                            }
                        }
                    }

                    "WEBRTC_ICE" -> {
                        if (current?.callId == callId && !current.forceTorOnly) {
                            val gen = json.optInt("generation", 1)
                            val arr = json.optJSONArray("candidates")
                            if (arr != null && arr.length() > 0) {
                                val list = ArrayList<org.webrtc.IceCandidate>(arr.length())
                                for (i in 0 until arr.length()) {
                                    val c = arr.optJSONObject(i) ?: continue
                                    val sdp = c.optString("sdp")
                                    if (sdp.isBlank()) continue
                                    list.add(
                                        org.webrtc.IceCandidate(
                                            c.optString("sdpMid"),
                                            c.optInt("sdpMLineIndex", 0),
                                            sdp
                                        )
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    webrtcVideoEngine.onRemoteCandidates(gen, list)
                                }
                            }
                        }
                    }

                    "CANDIDATES" -> {
                        if (current?.callId == callId) {
                            mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))
                            p2pFastBurstCounter.set(0)
                            if (current.state != CallState.IDLE && current.state != CallState.ENDED && !current.forceTorOnly) {
                                startDirectP2pProbing(callId)
                            }
                        }
                    }

                    "HEARTBEAT" -> {
                        if (current?.callId == callId && current.state == CallState.ACTIVE) {
                            lastPeerActivityAtMs = System.currentTimeMillis()
                            mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))
                            val sentTs = json.optLong("timestamp", 0L)
                            launch {
                                val messaging = TorMessagingService.getInstance(context)
                                val myInfo = messaging.getMyContactInfo()
                                val ack = JSONObject().apply {
                                    put("type", "HEARTBEAT_ACK")
                                    put("callId", callId)
                                    put("senderOnion", myInfo.onionAddress)
                                    put("echoTimestamp", sentTs)
                                    put("timestamp", System.currentTimeMillis())
                                }.toString().toByteArray(Charsets.UTF_8)
                                messaging.sendCallSignalFrame(canonicalSender, ack)
                            }
                        }
                    }

                    "HEARTBEAT_ACK" -> {
                        if (current?.callId == callId && current.state == CallState.ACTIVE) {
                            lastPeerActivityAtMs = System.currentTimeMillis()
                            val echoTs = json.optLong("echoTimestamp", 0L)
                            if (echoTs > 0L) {
                                val torRtt = (System.currentTimeMillis() - echoTs).toInt().coerceIn(10, 5000)
                                _sessionFlow.update { s ->
                                    if (s?.callId == callId && s.transportMode == CallTransportMode.TOR_ONION) {
                                        s.copy(rttMs = torRtt)
                                    } else s
                                }
                            }
                        }
                    }

                    "DECLINE" -> {
                        if (current?.callId == callId) {
                            endCallInternal(
                                callId = callId,
                                reason = "Call declined",
                                notifyPeer = false,
                                logText = if (current.isVideoCall) "📹 Outgoing video call (Declined)" else "📞 Outgoing voice call (Declined)"
                            )
                        }
                    }

                    "BUSY" -> {
                        if (current?.callId == callId) {
                            endCallInternal(
                                callId = callId,
                                reason = "User is on another call",
                                notifyPeer = false,
                                logText = if (current.isVideoCall) "📹 Outgoing video call (Busy)" else "📞 Outgoing voice call (Busy)"
                            )
                        }
                    }

                    "HANGUP" -> {
                        if (current?.callId == callId && current.state != CallState.ENDED) {
                            val wasMissedIncoming = current.isIncoming && current.state == CallState.INCOMING_RINGING
                            val dur = current.durationSeconds
                            val icon = if (current.isVideoCall) "📹" else "📞"
                            val kind = if (current.isVideoCall) "video" else "voice"
                            val logText = if (current.state == CallState.ACTIVE) {
                                val mins = dur / 60
                                val secs = dur % 60
                                val durStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                                if (current.isIncoming) "$icon Incoming $kind call ($durStr)"
                                else "$icon Outgoing $kind call ($durStr)"
                            } else if (current.isIncoming) {
                                "$icon Missed $kind call"
                            } else {
                                "$icon $kind call ended"
                            }
                            if (wasMissedIncoming) {
                                showMissedCallNotification(current)
                            }
                            endCallInternal(
                                callId = callId,
                                reason = if (wasMissedIncoming) "Missed call" else "Call ended",
                                notifyPeer = false,
                                logText = logText
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleCompleteRemoteWebRtcSdpLocked(
        current: CallSessionInfo,
        canonicalSender: String,
        gen: Int,
        sdpType: String,
        sdp: String
    ) {
        val dedupKey = "${current.callId}:$gen:${sdpType.lowercase()}:${sdp.hashCode()}"
        if (!processedWebRtcSdps.add(dedupKey)) return
        if (gen > webrtcGeneration.get()) {
            webrtcGeneration.set(gen)
        }
        val fps = AegisCallCrypto.extractDtlsFingerprintsFromSdp(sdp)
        if (fps.isNotEmpty() && dtlsFingerprints.addAll(fps)) {
            publishSecurityCode(current.callId)
        }
        val myCanonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(
            TorMessagingService.getInstance(context).getMyContactInfo().onionAddress
        )
        val shouldYield = myCanonical.isBlank() || myCanonical < canonicalSender
        _sessionFlow.update { s ->
            if (s?.callId == current.callId && !s.isVideoCall) {
                s.copy(isVideoCall = true)
            } else s
        }
        webrtcVideoEngine.onRemoteSdp(
            generation = gen,
            type = sdpType,
            sdp = sdp,
            frontCamera = current.isFrontCamera,
            videoEnabled = current.isLocalVideoEnabled,
            shouldYieldOnGlare = shouldYield
        )
    }

    /**
     * Called by TorMessagingService when a compact CALL_AUDIO (or Flux XOR-FEC) frame arrives over Tor.
     * Decrypts with directional [AegisCallCrypto.CallKeySession.recvKey] and enforces ReplayWindow64.
     */
    fun onIncomingTorCallAudio(
        senderOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ) {
        if (payload.isNotEmpty() &&
            (payload[0] == com.seamless.android.crypto.AegisGroupCallCrypto.GROUP_SFRAME_AUDIO_MAGIC ||
                payload[0] == com.seamless.android.crypto.AegisGroupCallCrypto.GROUP_SFRAME_AUDIO_FEC_MAGIC)
        ) {
            GroupCallManager.getInstance(context).onIncomingGroupCallAudio(senderOnion, sequence, timestampMs, payload)
            return
        }
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.ACTIVE || payload.size < 1 + 16 + DIRECT_UDP_NONCE_BYTES + 16) return
        val canonicalSender = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(senderOnion)
            .ifBlank { senderOnion.trim().lowercase() }
        val canonicalPeer = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(current.peerOnion)
            .ifBlank { current.peerOnion.trim().lowercase() }
        if (canonicalSender != canonicalPeer) return

        val magic = payload[0]
        if (magic != TOR_AUDIO_MAGIC_V2 && magic != TOR_AUDIO_FEC_MAGIC) return
        val buf = ByteBuffer.wrap(payload)
        buf.get() // magic
        val incomingCallUuid = UUID(buf.long, buf.long)
        if (incomingCallUuid.toString() != current.callId) return
        val crypto = activeCallCrypto ?: return
        val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
        buf.get(nonce)
        val cipherBytes = ByteArray(buf.remaining())
        buf.get(cipherBytes)
        val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
            .put(magic)
            .putLong(incomingCallUuid.mostSignificantBits)
            .putLong(incomingCallUuid.leastSignificantBits)
            .putLong(sequence)
            .putLong(timestampMs)
            .array()
        val plaintext = decryptUdpGcm(cipherBytes, crypto.recvKey, nonce, aad, crypto.recvKeySpec) ?: return
        val nonceSeq = extractNonceCounter(nonce)
        if (nonceSeq >= 0L && !crypto.audioReplayWindow.checkAndMark(nonceSeq)) return

        lastPeerActivityAtMs = System.currentTimeMillis()
        val rxCount = rxAudioFramesCounter.incrementAndGet()
        if ((rxCount and 3L) == 0L) {
            _rxPacketPulseFlow.value = System.currentTimeMillis()
        }
        if (magic == TOR_AUDIO_FEC_MAGIC) {
            for ((recSeq, recPayload) in fecDecoder.onFecPacketMulti(plaintext)) {
                audioEngine?.onRemoteAudioFrame(recSeq, timestampMs, recPayload)
            }
        } else {
            audioEngine?.onRemoteAudioFrame(sequence, timestampMs, plaintext)
            if (!CallAudioEngine.isDtxSidFrame(plaintext)) {
                for ((recSeq, recPayload) in fecDecoder.onVoiceFrameMulti(sequence, plaintext)) {
                    audioEngine?.onRemoteAudioFrame(recSeq, timestampMs, recPayload)
                }
            }
        }
    }

    /**
     * Called by TorMessagingService when a real-time encrypted CALL_VIDEO fragment arrives over Tor.
     * Enforces canonical peer verification, directional decryption, and ReplayWindow64 before reassembly.
     */
    fun onIncomingTorCallVideo(
        senderOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ) {
        if (payload.isNotEmpty() &&
            payload[0] == com.seamless.android.crypto.AegisGroupCallCrypto.GROUP_SFRAME_VIDEO_MAGIC
        ) {
            GroupCallManager.getInstance(context).onIncomingGroupCallVideo(senderOnion, sequence, timestampMs, payload)
            return
        }
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.ACTIVE || payload.size < 1 + 16 + DIRECT_UDP_NONCE_BYTES + 16) return
        val canonicalSender = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(senderOnion)
            .ifBlank { senderOnion.trim().lowercase() }
        val canonicalPeer = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(current.peerOnion)
            .ifBlank { current.peerOnion.trim().lowercase() }
        if (canonicalSender != canonicalPeer) return

        val buf = ByteBuffer.wrap(payload)
        if (payload[0] != TOR_VIDEO_MAGIC_V1) return
        buf.get()
        val incomingCallUuid = UUID(buf.long, buf.long)
        if (incomingCallUuid.toString() != current.callId) return
        val crypto = activeCallCrypto ?: return
        val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
        buf.get(nonce)
        val cipherBytes = ByteArray(buf.remaining())
        buf.get(cipherBytes)
        val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
            .put(TOR_VIDEO_MAGIC_V1)
            .putLong(incomingCallUuid.mostSignificantBits)
            .putLong(incomingCallUuid.leastSignificantBits)
            .putLong(sequence)
            .putLong(timestampMs)
            .array()
        val plainFragment = decryptUdpGcm(cipherBytes, crypto.recvKey, nonce, aad, crypto.recvKeySpec) ?: return
        val nonceSeq = extractNonceCounter(nonce)
        if (nonceSeq >= 0L && !crypto.videoReplayWindow.checkAndMark(nonceSeq)) return

        lastPeerActivityAtMs = System.currentTimeMillis()
        if (!current.isRemoteVideoEnabled) {
            _sessionFlow.update { s ->
                if (s?.callId == current.callId && !s.isRemoteVideoEnabled) {
                    s.copy(isVideoCall = true, isRemoteVideoEnabled = true)
                } else s
            }
        }
        videoEngine.onRemoteVideoFragment(plainFragment)
    }

    private fun getActiveUdpTargets(): List<InetSocketAddress> {
        val now = System.currentTimeMillis()
        val verified = verifiedDirectPeerEndpoint
        if (verified != null && (now - lastDirectP2pVerifiedAt) <= DIRECT_P2P_STALE_MS) {
            return listOf(verified)
        }
        val out = ArrayList<InetSocketAddress>(4)
        val candidate = candidateDirectPeerEndpoint
        if (candidate != null) {
            out.add(candidate)
        }
        var firstPublic: InetSocketAddress? = null
        var firstPrivate: InetSocketAddress? = null
        for (addr in peerUdpCandidates) {
            val inet = addr.address
            val isPublic = inet != null && !inet.isSiteLocalAddress && !inet.isLoopbackAddress && !inet.isLinkLocalAddress
            if (isPublic && firstPublic == null) {
                firstPublic = addr
            } else if (!isPublic && firstPrivate == null) {
                firstPrivate = addr
            }
            if (firstPublic != null && firstPrivate != null) break
        }
        if (firstPrivate != null && !out.contains(firstPrivate) && out.size < 4) {
            out.add(firstPrivate)
        }
        if (firstPublic != null && !out.contains(firstPublic) && out.size < 4) {
            out.add(firstPublic)
        }
        return out
    }

    /**
     * Active-Active Multipath Router (Tailscale magicsock architecture):
     * - Transmits over verified Direct UDP when available.
     * - Simultaneously streams over Tor .onion during the initial 8s warmup window AND whenever
     *   bidirectional UDP ACK age exceeds 1.8s, ensuring zero audio loss across CGNAT/firewalls.
     */
    private fun dispatchOutboundAudioFrame(sequence: Long, timestampMs: Long, encodedAdpcm: ByteArray) {
        val session = _sessionFlow.value ?: return
        if (session.state != CallState.ACTIVE) return

        val txCount = txAudioFramesCounter.incrementAndGet()
        if ((txCount and 3L) == 0L) {
            _txPacketPulseFlow.value = System.currentTimeMillis()
        }

        val now = System.currentTimeMillis()
        val verifiedEndpoint = verifiedDirectPeerEndpoint
        val verifiedAgeMs = if (verifiedEndpoint != null) (now - lastDirectP2pVerifiedAt) else Long.MAX_VALUE
        val callAgeMs = session.connectedAtMs?.let { now - it } ?: 0L
        val webrtcConnected = webrtcVideoEngine.mediaConnected.value

        var sentViaDirectUdp = false
        val jitterMs = audioEngine?.currentJitterMs() ?: 0.0
        val fecNeeded = !session.isMultipathActive || jitterMs >= 45.0 || session.transportMode == CallTransportMode.TOR_ONION
        val burst2D = session.transportMode == CallTransportMode.TOR_ONION || jitterMs >= 75.0
        val fecParities = if (!CallAudioEngine.isDtxSidFrame(encodedAdpcm)) {
            fecEncoder.onVoiceFrame2D(
                sequence = sequence,
                encodedPayload = encodedAdpcm,
                fecEnabled = fecNeeded,
                burstMode2D = burst2D
            )
        } else {
            emptyList()
        }

        if (!session.forceTorOnly) {
            val targets = getActiveUdpTargets()
            for (udpTarget in targets) {
                if (sendDirectUdpAudioPacket(
                        session.callId,
                        udpTarget,
                        sequence,
                        timestampMs,
                        encodedAdpcm
                    )
                ) {
                    sentViaDirectUdp = true
                }
                for (fecParity in fecParities) {
                    sendDirectUdpPacketWithType(
                        packetType = UDP_TYPE_AUDIO_FEC,
                        callId = session.callId,
                        target = udpTarget,
                        sequence = sequence,
                        timestampMs = timestampMs,
                        plaintext = fecParity
                    )
                }
            }
        }

        if (verifiedAgeMs <= DIRECT_P2P_PROMOTE_WINDOW_MS) {
            if (firstContinuousDirectOkAtMs == 0L) {
                firstContinuousDirectOkAtMs = now
            }
        } else if (verifiedAgeMs > DIRECT_P2P_DEMOTE_WINDOW_MS) {
            firstContinuousDirectOkAtMs = 0L
        }

        val pureDirectP2pReady = !session.forceTorOnly &&
            verifiedEndpoint != null &&
            verifiedAgeMs <= DIRECT_P2P_PROMOTE_WINDOW_MS &&
            callAgeMs >= INITIAL_MULTIPATH_WARMUP_MS &&
            sentViaDirectUdp &&
            firstContinuousDirectOkAtMs > 0L &&
            (now - firstContinuousDirectOkAtMs) >= DIRECT_P2P_HOLD_MS

        if (pureDirectP2pReady) {
            audioEngine?.setTransportMode(true)
            videoEngine.updateNetworkProfile(isDirectP2p = true, rttMs = session.rttMs, lossDetected = false)
            if (!webrtcConnected && (session.transportMode != CallTransportMode.DIRECT_P2P || session.isMultipathActive)) {
                _sessionFlow.update {
                    it?.copy(
                        transportMode = CallTransportMode.DIRECT_P2P,
                        isMultipathActive = false,
                        statusMessage = "Encrypted • Direct P2P (${it.rttMs.coerceAtLeast(15)}ms)"
                    )
                }
            }
            return
        }

        // Hysteresis zone: stay on multipath while UDP age <= 3.5s, and only demote to pure Tor above 3.5s.
        if (!session.forceTorOnly && verifiedEndpoint != null && verifiedAgeMs <= DIRECT_P2P_DEMOTE_WINDOW_MS) {
            audioEngine?.setTransportMode(false) // Use 40ms frames so both Tor & UDP share the exact frame
            videoEngine.updateNetworkProfile(isDirectP2p = true, rttMs = session.rttMs, lossDetected = false)
            if (!webrtcConnected && (session.transportMode != CallTransportMode.DIRECT_P2P || !session.isMultipathActive)) {
                _sessionFlow.update {
                    it?.copy(
                        transportMode = CallTransportMode.DIRECT_P2P,
                        isMultipathActive = true,
                        statusMessage = "Encrypted • Multipath P2P + Tor"
                    )
                }
            }
        } else if (session.transportMode == CallTransportMode.DIRECT_P2P && !webrtcConnected) {
            audioEngine?.setTransportMode(false)
            videoEngine.updateNetworkProfile(isDirectP2p = false, rttMs = session.rttMs, lossDetected = false)
            _sessionFlow.update {
                it?.copy(
                    transportMode = CallTransportMode.TOR_ONION,
                    isMultipathActive = !it.forceTorOnly,
                    statusMessage = "Encrypted • Tor Onion P2P"
                )
            }
        }

        // Encrypt in RAM with directional sendKey & counter nonce and enqueue into the non-blocking FIFO channel
        val callUuid = currentCallUuid ?: runCatching { UUID.fromString(session.callId) }.getOrNull() ?: return
        val crypto = activeCallCrypto ?: return
        val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
            .put(TOR_AUDIO_MAGIC_V2)
            .putLong(callUuid.mostSignificantBits)
            .putLong(callUuid.leastSignificantBits)
            .putLong(sequence)
            .putLong(timestampMs)
            .array()
        val nonce = nextMonotonicNonce()
        val cipherBytes = encryptUdpGcm(encodedAdpcm, crypto.sendKey, nonce, aad, crypto.sendKeySpec) ?: return

        val torPayload = ByteBuffer.allocate(1 + 16 + DIRECT_UDP_NONCE_BYTES + cipherBytes.size)
            .put(TOR_AUDIO_MAGIC_V2)
            .putLong(callUuid.mostSignificantBits)
            .putLong(callUuid.leastSignificantBits)
            .put(nonce)
            .put(cipherBytes)
            .array()

        torAudioOutChannel.trySend(
            OutboundTorAudioItem(
                recipientOnion = session.peerOnion,
                sequence = sequence,
                timestampMs = timestampMs,
                payload = torPayload
            )
        )

        for (fecParity in fecParities) {
            val fecAad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                .put(TOR_AUDIO_FEC_MAGIC)
                .putLong(callUuid.mostSignificantBits)
                .putLong(callUuid.leastSignificantBits)
                .putLong(sequence)
                .putLong(timestampMs)
                .array()
            val fecNonce = nextMonotonicNonce()
            val fecCipher = encryptUdpGcm(fecParity, crypto.sendKey, fecNonce, fecAad, crypto.sendKeySpec)
            if (fecCipher != null) {
                val fecTorPayload = ByteBuffer.allocate(1 + 16 + DIRECT_UDP_NONCE_BYTES + fecCipher.size)
                    .put(TOR_AUDIO_FEC_MAGIC)
                    .putLong(callUuid.mostSignificantBits)
                    .putLong(callUuid.leastSignificantBits)
                    .put(fecNonce)
                    .put(fecCipher)
                    .array()
                torAudioOutChannel.trySend(
                    OutboundTorAudioItem(
                        recipientOnion = session.peerOnion,
                        sequence = sequence,
                        timestampMs = timestampMs,
                        payload = fecTorPayload,
                        isFecParity = true
                    )
                )
            }
        }
    }

    private fun startTorAudioSenderLoop(callId: String) {
        torSenderJob?.cancel()
        while (torAudioOutChannel.tryReceive().isSuccess) {}
        while (torVideoFrameChannel.tryReceive().isSuccess) {}
        torVideoConserveUntilMs = 0L

        torSenderJob = scope.launch(Dispatchers.IO) {
            val messaging = TorMessagingService.getInstance(context)

            suspend fun transmitTorAudioItem(item: OutboundTorAudioItem, cur: CallSessionInfo) {
                if (item.isFecParity) {
                    messaging.sendCallAudioFecFrame(
                        recipientOnion = item.recipientOnion,
                        sequence = item.sequence,
                        timestampMs = item.timestampMs,
                        payload = item.payload,
                        preferBulkLane = !cur.isLocalVideoEnabled
                    )
                } else {
                    messaging.sendCallAudioFrame(
                        recipientOnion = item.recipientOnion,
                        sequence = item.sequence,
                        timestampMs = item.timestampMs,
                        payload = item.payload
                    )
                }
            }

            suspend fun drainPendingAudio(): Boolean {
                while (true) {
                    val s = _sessionFlow.value
                    if (s == null || s.callId != callId || s.state == CallState.ENDED || s.state == CallState.IDLE) {
                        return false
                    }
                    val item = torAudioOutChannel.tryReceive().getOrNull() ?: return true
                    if (s.state == CallState.ACTIVE) {
                        transmitTorAudioItem(item, s)
                    }
                }
            }

            suspend fun sendVideoFrameWithAudioPriority(video: OutboundTorVideoFrame) {
                if (System.currentTimeMillis() < torVideoConserveUntilMs) {
                    // Drain audio instead when Tor circuit is temporarily backpressured
                    return
                }
                var slowWrite = false
                for (batch in video.encryptedFragments.chunked(4)) {
                    if (!drainPendingAudio()) return
                    val s = _sessionFlow.value
                    if (s == null || s.callId != callId || s.state != CallState.ACTIVE || !s.isLocalVideoEnabled) {
                        return
                    }
                    val t0 = System.currentTimeMillis()
                    val ok = messaging.sendCallVideoFramesBurst(
                        recipientOnion = video.recipientOnion,
                        sequence = video.frameSeq,
                        timestampMs = video.timestampMs,
                        payloads = batch
                    )
                    if (!ok || System.currentTimeMillis() - t0 > 200L) {
                        slowWrite = true
                    }
                }
                if (slowWrite) {
                    // Back off between complete frames instead of aborting mid-frame so fragments remain decodable.
                    torVideoConserveUntilMs = System.currentTimeMillis() + 250L
                }
            }

            while (isActive) {
                val s = _sessionFlow.value
                if (s == null || s.callId != callId || s.state == CallState.ENDED || s.state == CallState.IDLE) {
                    break
                }

                if (!drainPendingAudio()) break

                val queuedVideo = torVideoFrameChannel.tryReceive().getOrNull()
                if (queuedVideo != null) {
                    sendVideoFrameWithAudioPriority(queuedVideo)
                    continue
                }

                // Wait non-blocking until either audio or video arrives (audio clause first!)
                select<Unit> {
                    torAudioOutChannel.onReceive { audio ->
                        val cur = _sessionFlow.value
                        if (cur != null && cur.callId == callId && cur.state == CallState.ACTIVE) {
                            transmitTorAudioItem(audio, cur)
                        }
                    }
                    torVideoFrameChannel.onReceive { video ->
                        sendVideoFrameWithAudioPriority(video)
                    }
                }
            }
        }
    }

    private fun startTorVideoSenderLoop(callId: String) {
        // Unified into the voice-first priority sender loop (torSenderJob) to avoid Tor TCP socket contention
        torVideoSenderJob?.cancel()
        torVideoSenderJob = null
    }

    private fun dispatchOutboundVideoFrame(
        sequence: Long,
        timestampMs: Long,
        fragments: List<ByteArray>
    ) {
        val session = _sessionFlow.value ?: return
        if (session.state != CallState.ACTIVE || !session.isLocalVideoEnabled || fragments.isEmpty()) return

        val now = System.currentTimeMillis()
        val verifiedEndpoint = verifiedDirectPeerEndpoint
        val candidateEndpoint = candidateDirectPeerEndpoint
        val verifiedAgeMs = if (verifiedEndpoint != null) (now - lastDirectP2pVerifiedAt) else Long.MAX_VALUE

        var sentViaDirectUdp = false
        if (!session.forceTorOnly) {
            val targets = getActiveUdpTargets()
            for (udpTarget in targets) {
                var allSent = true
                fragments.forEachIndexed { idx, frag ->
                    if (fragments.size >= 8 && idx > 0 && (idx and 3) == 0) {
                        LockSupport.parkNanos(350_000L)
                    }
                    val ok = sendDirectUdpPacketWithType(
                        packetType = UDP_TYPE_VIDEO,
                        callId = session.callId,
                        target = udpTarget,
                        sequence = sequence,
                        timestampMs = timestampMs,
                        plaintext = frag
                    )
                    if (!ok) allSent = false
                }
                if (allSent) sentViaDirectUdp = true
            }
        }

        // Video stays strictly on Direct P2P UDP whenever UDP path is alive so we never bloat Tor circuits;
        // fallback to conflated complete-frame Tor queue only when Force Tor Only is active or UDP cannot carry video.
        if (!session.forceTorOnly && verifiedEndpoint != null && verifiedAgeMs <= DIRECT_P2P_STALE_MS && sentViaDirectUdp) {
            return
        }

        val callUuid = currentCallUuid ?: runCatching { UUID.fromString(session.callId) }.getOrNull() ?: return
        val crypto = activeCallCrypto ?: return
        val encryptedList = ArrayList<ByteArray>(fragments.size)
        for (frag in fragments) {
            val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                .put(TOR_VIDEO_MAGIC_V1)
                .putLong(callUuid.mostSignificantBits)
                .putLong(callUuid.leastSignificantBits)
                .putLong(sequence)
                .putLong(timestampMs)
                .array()
            val nonce = nextMonotonicNonce()
            val cipherBytes = encryptUdpGcm(frag, crypto.sendKey, nonce, aad, crypto.sendKeySpec) ?: return
            val torPayload = ByteBuffer.allocate(1 + 16 + DIRECT_UDP_NONCE_BYTES + cipherBytes.size)
                .put(TOR_VIDEO_MAGIC_V1)
                .putLong(callUuid.mostSignificantBits)
                .putLong(callUuid.leastSignificantBits)
                .put(nonce)
                .put(cipherBytes)
                .array()
            encryptedList.add(torPayload)
        }

        torVideoFrameChannel.trySend(
            OutboundTorVideoFrame(
                recipientOnion = session.peerOnion,
                frameSeq = sequence,
                timestampMs = timestampMs,
                encryptedFragments = encryptedList
            )
        )
    }

    private fun sendVideoPliRequest() {
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.ACTIVE) return
        val target = if (!current.forceTorOnly) verifiedDirectPeerEndpoint ?: candidateDirectPeerEndpoint else null
        if (target != null && sendDirectUdpVideoControl(current.callId, target, byteArrayOf(VIDEO_CTRL_PLI))) {
            return
        }
        scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            val payload = buildSignalJson(
                type = "VIDEO_PLI",
                callId = current.callId,
                senderOnion = myInfo.onionAddress,
                senderNickname = myInfo.nickname,
                senderPubKey = myInfo.publicKey
            )
            messaging.sendCallSignalFrame(current.peerOnion, payload)
        }
    }

    private fun sendVideoStateNotification(callId: String, peerOnion: String, enabled: Boolean) {
        val current = _sessionFlow.value
        val target = if (current?.forceTorOnly == false) {
            verifiedDirectPeerEndpoint ?: candidateDirectPeerEndpoint
        } else null
        if (target != null) {
            sendDirectUdpVideoControl(callId, target, byteArrayOf(VIDEO_CTRL_STATE, if (enabled) 1 else 0))
        }
        scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            val payload = buildSignalJson(
                type = "VIDEO_STATE",
                callId = callId,
                senderOnion = myInfo.onionAddress,
                senderNickname = myInfo.nickname,
                senderPubKey = myInfo.publicKey,
                isVideo = enabled,
                localVideoEnabled = enabled
            )
            messaging.sendCallSignalFrame(peerOnion, payload)
        }
    }

    private fun sendWebRtcSdpSignal(generation: Int, sdpType: String, sdp: String) {
        val current = _sessionFlow.value ?: return
        if (current.forceTorOnly || current.state == CallState.ENDED || current.state == CallState.IDLE) return
        val localFps = AegisCallCrypto.extractDtlsFingerprintsFromSdp(sdp)
        if (localFps.isNotEmpty() && dtlsFingerprints.addAll(localFps)) {
            publishSecurityCode(current.callId)
        }
        scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            val sdpGz = compressSdpGzipBase64(sdp)
            val useGzip = sdpGz.isNotBlank()
            val encodedBody = if (useGzip) sdpGz else sdp
            val payload = JSONObject().apply {
                put("type", "WEBRTC_SDP")
                put("callId", current.callId)
                put("senderOnion", myInfo.onionAddress)
                put("generation", generation)
                put("sdpType", sdpType)
                if (useGzip) put("sdpGz", sdpGz) else put("sdp", sdp)
                put("timestamp", System.currentTimeMillis())
            }.toString().toByteArray(Charsets.UTF_8)

            // Fast-path: also transmit compact SDPs over Direct UDP; large SDPs stay strictly on Tor signaling.
            val udpCtrl = ByteArray(1 + payload.size).also {
                it[0] = VIDEO_CTRL_WEBRTC_SIG
                System.arraycopy(payload, 0, it, 1, payload.size)
            }
            if (udpCtrl.size <= 1_200) {
                for (target in getActiveUdpTargets()) {
                    sendDirectUdpVideoControl(current.callId, target, udpCtrl)
                }
            }

            if (payload.size <= MAX_SDP_SIGNAL_BYTES) {
                sendCallSignalWithRetry(messaging, current.peerOnion, payload, maxAttempts = 2)
            } else {
                // Fragment oversized SDPs cleanly across bounded WEBRTC_SDP_PART frames
                val chunks = encodedBody.chunked(2_200).take(MAX_SDP_PARTS)
                val partId = UUID.randomUUID().toString()
                chunks.forEachIndexed { idx, part ->
                    val partPayload = JSONObject().apply {
                        put("type", "WEBRTC_SDP_PART")
                        put("callId", current.callId)
                        put("senderOnion", myInfo.onionAddress)
                        put("generation", generation)
                        put("sdpType", sdpType)
                        put("isGzip", useGzip)
                        put("partId", partId)
                        put("partIndex", idx)
                        put("totalParts", chunks.size)
                        put("chunk", part)
                        put("timestamp", System.currentTimeMillis())
                    }.toString().toByteArray(Charsets.UTF_8)
                    sendCallSignalWithRetry(messaging, current.peerOnion, partPayload, maxAttempts = 2)
                }
            }
        }
    }

    private fun sendWebRtcIceSignal(generation: Int, candidates: List<org.webrtc.IceCandidate>) {
        val current = _sessionFlow.value ?: return
        if (current.forceTorOnly || current.state == CallState.ENDED || current.state == CallState.IDLE) return
        if (candidates.isEmpty()) return
        scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            val arr = JSONArray()
            for (c in candidates) {
                arr.put(
                    JSONObject().apply {
                        put("sdpMid", c.sdpMid ?: "0")
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("sdp", c.sdp)
                    }
                )
            }
            val payload = JSONObject().apply {
                put("type", "WEBRTC_ICE")
                put("callId", current.callId)
                put("senderOnion", myInfo.onionAddress)
                put("generation", generation)
                put("candidates", arr)
                put("timestamp", System.currentTimeMillis())
            }.toString().toByteArray(Charsets.UTF_8)

            val udpCtrl = ByteArray(1 + payload.size).also {
                it[0] = VIDEO_CTRL_WEBRTC_SIG
                System.arraycopy(payload, 0, it, 1, payload.size)
            }
            for (target in getActiveUdpTargets()) {
                sendDirectUdpVideoControl(current.callId, target, udpCtrl)
            }

            messaging.sendCallSignalFrame(current.peerOnion, payload)
        }
    }

    private fun compressSdpGzipBase64(sdp: String): String {
        return try {
            val baos = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(baos).use { gz ->
                gz.write(sdp.toByteArray(Charsets.UTF_8))
            }
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun decompressSdpGzipBase64(b64: String): String =
        decompressSdpGzipBase64Bounded(b64)

    private fun negotiateVideoCodec(remoteCodecs: JSONArray?): String {
        val localSupported = videoEngine.getSupportedHardwareCodecs()
        if (remoteCodecs == null) return CallVideoEngine.CODEC_AVC
        val remoteList = ArrayList<String>(remoteCodecs.length())
        for (i in 0 until remoteCodecs.length()) {
            remoteList.add(remoteCodecs.optString(i).uppercase())
        }
        return when {
            localSupported.contains(CallVideoEngine.CODEC_DELTA_PULSE) &&
                remoteList.contains(CallVideoEngine.CODEC_DELTA_PULSE) ->
                CallVideoEngine.CODEC_DELTA_PULSE
            localSupported.contains(CallVideoEngine.CODEC_HEVC) &&
                remoteList.contains(CallVideoEngine.CODEC_HEVC) ->
                CallVideoEngine.CODEC_HEVC
            else -> CallVideoEngine.CODEC_AVC
        }
    }

    // ========== Direct P2P UDP Transport (STUN + Port Prediction + IPv6 + LAN) ==========

    private fun openDirectUdpSocketFast(callId: String, peerOnion: String): List<String> {
        return try {
            val socket = DatagramSocket(0).apply {
                reuseAddress = true
                trafficClass = 0xB8 // Expedited Forwarding (EF) DSCP for VoIP
                runCatching {
                    receiveBufferSize = 512 * 1024
                    sendBufferSize = 512 * 1024
                }
            }
            udpSocket = socket
            val port = socket.localPort

            val candidates = LinkedHashSet<String>()
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isMulticastAddress) continue
                    when (addr) {
                        is Inet4Address -> {
                            val ip = addr.hostAddress
                            if (!ip.isNullOrBlank()) {
                                candidates.add("$ip:$port")
                            }
                        }
                        is Inet6Address -> {
                            if (!addr.isSiteLocalAddress) {
                                val ip6 = addr.hostAddress?.substringBefore('%')
                                if (!ip6.isNullOrBlank()) {
                                    candidates.add("[$ip6]:$port")
                                }
                            }
                        }
                    }
                }
            }

            localUdpCandidates.clear()
            localUdpCandidates.addAll(candidates)

            // Start receiver loop immediately and launch non-blocking Multi-STUN + Port Prediction
            startDirectUdpReceiverLoop(callId, socket)
            discoverStunAndTrickleCandidatesAsync(callId, peerOnion, socket)

            Log.i(TAG, "⚡ Bound Direct P2P UDP voice port $port with ${candidates.size} immediate candidates")
            candidates.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Could not bind Direct P2P UDP socket: ${e.message}")
            emptyList()
        }
    }

    /**
     * Sends RFC 5389 STUN Binding Requests to multiple distinct STUN servers.
     * The receiver loop parses the incoming STUN responses (which start with 0x01 0x01),
     * computes sequential CGNAT port predictions if ports differ, and trickles a CANDIDATES
     * signal frame to the peer over Tor without blocking call setup!
     */
    private fun discoverStunAndTrickleCandidatesAsync(
        callId: String,
        peerOnion: String,
        socket: DatagramSocket
    ) {
        stunDiscoveryJob?.cancel()
        stunDiscoveryJob = scope.launch(Dispatchers.IO) {
            val stunServers = listOf(
                "stun.cloudflare.com" to 3478,
                "stun.l.google.com" to 19302,
                "stun1.l.google.com" to 19302,
                "stun2.l.google.com" to 19302
            )
            // Query all STUN servers in parallel so slow DNS on one never stalls the others
            for ((host, port) in stunServers) {
                launch(Dispatchers.IO) {
                    runCatching {
                        val resolvedIp = InetAddress.getByName(host)
                        val target = InetSocketAddress(resolvedIp, port)
                        repeat(2) { attempt ->
                            if (attempt > 0) delay(220L)
                            if (socket.isClosed) return@launch
                            val txId = ByteArray(12).also { secureRandom.nextBytes(it) }
                            val sentAt = System.currentTimeMillis()
                            pendingStunTxIds[bytesToHexLower(txId)] = sentAt
                            if (pendingStunTxIds.size > 32) {
                                pendingStunTxIds.entries.removeIf { sentAt - it.value > 15_000L }
                            }
                            val request = ByteBuffer.allocate(20)
                                .putShort(0x0001.toShort()) // Binding Request
                                .putShort(0x0000.toShort()) // Message Length = 0
                                .putInt(0x2112A442)         // Magic Cookie
                                .put(txId)
                                .array()
                            socket.send(DatagramPacket(request, request.size, target))
                        }
                    }
                }
            }

            // Initial settle trickle, plus late STUN replies also trigger coalesced trickle in handlePotentialStunPacket
            delay(500L)
            trickleLocalCandidatesAsync(callId, peerOnion)
        }
    }

    private fun trickleLocalCandidatesAsync(callId: String, peerOnion: String) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCandidateTrickleAtMs
        if (elapsed < 200L) {
            if (candidateTrickleJob?.isActive != true) {
                val waitMs = (220L - elapsed).coerceIn(30L, 220L)
                candidateTrickleJob = scope.launch(Dispatchers.IO) {
                    delay(waitMs)
                    trickleLocalCandidatesAsync(callId, peerOnion)
                }
            }
            return
        }
        val current = _sessionFlow.value ?: return
        if (current.callId != callId || current.forceTorOnly || localUdpCandidates.isEmpty()) return
        lastCandidateTrickleAtMs = now
        scope.launch(Dispatchers.IO) {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            val tricklePayload = buildSignalJson(
                type = "CANDIDATES",
                callId = callId,
                senderOnion = myInfo.onionAddress,
                senderNickname = myInfo.nickname,
                senderPubKey = myInfo.publicKey,
                candidates = localUdpCandidates.toList()
            )
            messaging.sendCallSignalFrame(peerOnion, tricklePayload)
        }
    }

    private val discoveredStunPortsByIp = HashMap<String, MutableList<Int>>()

    private fun handlePotentialStunPacket(data: ByteArray, length: Int) {
        val mapped = parseStunBindingResponse(data, length) ?: return
        var isNewEndpoint = false
        if (!localUdpCandidates.contains(mapped)) {
            localUdpCandidates.add(0, mapped)
            isNewEndpoint = true
            Log.i(TAG, "🌍 Discovered STUN public endpoint: $mapped")
        }

        // Tailscale magicsock Sequential CGNAT Port Prediction:
        // If two STUN servers report the same public IPv4 with sequential ports (e.g. delta 1..16),
        // add predicted next ports so simultaneous UDP hole punching pierces sequential CGNATs!
        val lastColon = mapped.lastIndexOf(':')
        if (lastColon > 0 && !mapped.startsWith("[")) {
            val ip = mapped.substring(0, lastColon)
            val port = mapped.substring(lastColon + 1).toIntOrNull()
            if (port != null) {
                val ports = synchronized(discoveredStunPortsByIp) {
                    val list = discoveredStunPortsByIp.getOrPut(ip) { mutableListOf() }
                    if (!list.contains(port)) list.add(port)
                    list.toList()
                }
                if (ports.size >= 2) {
                    val p1 = ports[ports.size - 2]
                    val p2 = ports[ports.size - 1]
                    val delta = p2 - p1
                    if (abs(delta) in 1..16) {
                        for (step in 1..4) {
                            val predictedPort = (p2 + delta * step).coerceIn(1025, 65534)
                            val pred = "$ip:$predictedPort"
                            if (!localUdpCandidates.contains(pred)) {
                                localUdpCandidates.add(pred)
                                isNewEndpoint = true
                            }
                        }
                    }
                }
            }
        }

        if (isNewEndpoint) {
            val session = _sessionFlow.value
            if (session != null && !session.forceTorOnly) {
                trickleLocalCandidatesAsync(session.callId, session.peerOnion)
            }
        }
    }

    private fun parseStunBindingResponse(data: ByteArray, length: Int): String? {
        if (length < 20) return null
        val bb = ByteBuffer.wrap(data, 0, length)
        val msgType = bb.short.toInt() and 0xFFFF
        val msgLen = bb.short.toInt() and 0xFFFF
        val cookie = bb.int
        if (msgType != 0x0101 || cookie != 0x2112A442) return null
        val rxTxId = ByteArray(12)
        bb.get(rxTxId)
        val sentAt = pendingStunTxIds[bytesToHexLower(rxTxId)] ?: return null
        if (System.currentTimeMillis() - sentAt > 15_000L) return null

        val endOffset = (20 + msgLen).coerceAtMost(length)
        var offset = 20
        var fallbackMapped: String? = null
        val cookieBytes = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)

        while (offset + 4 <= endOffset) {
            val attrType = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val attrLen = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            val valOffset = offset + 4
            if (valOffset + attrLen > endOffset) break

            if ((attrType == 0x0020 || attrType == 0x8020) && attrLen >= 8) {
                val family = data[valOffset + 1].toInt() and 0xFF
                val xPort = ((data[valOffset + 2].toInt() and 0xFF) shl 8) or (data[valOffset + 3].toInt() and 0xFF)
                val port = xPort xor 0x2112
                if (family == 0x01 && attrLen >= 8) {
                    val ipBytes = ByteArray(4) { i -> (data[valOffset + 4 + i].toInt() xor cookieBytes[i].toInt()).toByte() }
                    val ip = InetAddress.getByAddress(ipBytes).hostAddress
                    if (!ip.isNullOrBlank() && port in 1..65535) return "$ip:$port"
                } else if (family == 0x02 && attrLen >= 20) {
                    val xorKey = cookieBytes + rxTxId
                    val ipBytes = ByteArray(16) { i -> (data[valOffset + 4 + i].toInt() xor xorKey[i].toInt()).toByte() }
                    val ip6 = InetAddress.getByAddress(ipBytes).hostAddress?.substringBefore('%')
                    if (!ip6.isNullOrBlank() && port in 1..65535) return "[$ip6]:$port"
                }
            } else if (attrType == 0x0001 && attrLen >= 8 && fallbackMapped == null) {
                val family = data[valOffset + 1].toInt() and 0xFF
                val port = ((data[valOffset + 2].toInt() and 0xFF) shl 8) or (data[valOffset + 3].toInt() and 0xFF)
                if (family == 0x01) {
                    val ipBytes = data.copyOfRange(valOffset + 4, valOffset + 8)
                    val ip = InetAddress.getByAddress(ipBytes).hostAddress
                    if (!ip.isNullOrBlank() && port in 1..65535) fallbackMapped = "$ip:$port"
                }
            }

            val paddedLen = (attrLen + 3) and 0xFFFC
            offset = valOffset + paddedLen
        }
        return fallbackMapped
    }

    private fun startDirectP2pProbing(callId: String) {
        p2pProbeJob?.cancel()
        p2pFastBurstCounter.set(0)
        p2pProbeJob = scope.launch {
            val callUuid = runCatching { UUID.fromString(callId) }.getOrNull() ?: return@launch
            while (isActive) {
                val session = _sessionFlow.value
                if (session == null ||
                    session.callId != callId ||
                    session.state == CallState.IDLE ||
                    session.state == CallState.ENDED
                ) {
                    break
                }
                if (!session.forceTorOnly) {
                    val targets = LinkedHashSet<InetSocketAddress>()
                    verifiedDirectPeerEndpoint?.let { targets.add(it) }
                    candidateDirectPeerEndpoint?.let { targets.add(it) }
                    targets.addAll(peerUdpCandidates)
                    for (target in targets) {
                        sendDirectUdpProbe(callUuid, target, isAck = false, timestampMs = System.currentTimeMillis())
                    }
                }
                val count = p2pFastBurstCounter.incrementAndGet()
                delay(when {
                    count <= 15 -> 60L
                    count <= 40 -> 180L
                    else -> 1_100L
                })
            }
        }
    }

    private fun startDirectUdpReceiverLoop(callId: String, socket: DatagramSocket) {
        p2pReceiveJob?.cancel()
        p2pReceiveJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            while (isActive && !socket.isClosed) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val pktBytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    // Check if this is a STUN Binding Response (0x0101)
                    if (pktBytes.size >= 20 && pktBytes[0] == 0x01.toByte() && pktBytes[1] == 0x01.toByte()) {
                        handlePotentialStunPacket(pktBytes, pktBytes.size)
                        continue
                    }
                    handleIncomingUdpPacket(
                        callId = callId,
                        sender = InetSocketAddress(packet.address, packet.port),
                        data = pktBytes
                    )
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
    }

    private fun decryptControlWithFallback(
        cipherBytes: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): Pair<AegisCallCrypto.CallKeySession, ByteArray>? {
        val primary = activeCallCrypto ?: preAcceptCrypto ?: return null
        val pPlain = decryptUdpGcm(cipherBytes, primary.recvKey, nonce, aad, primary.recvKeySpec)
        if (pPlain != null) return primary to pPlain
        val fallback = preAcceptCrypto
        if (fallback != null && fallback !== primary) {
            val fPlain = decryptUdpGcm(cipherBytes, fallback.recvKey, nonce, aad, fallback.recvKeySpec)
            if (fPlain != null) return fallback to fPlain
        }
        return null
    }

    private fun sendPreAcceptUdpSignal(callId: String, payloadBytes: ByteArray) {
        val crypto = preAcceptCrypto ?: activeCallCrypto ?: return
        val ctrl = ByteArray(1 + payloadBytes.size).also {
            it[0] = VIDEO_CTRL_WEBRTC_SIG
            System.arraycopy(payloadBytes, 0, it, 1, payloadBytes.size)
        }
        if (ctrl.size > 1_350) return
        val targets = LinkedHashSet<InetSocketAddress>(4).apply {
            addAll(getActiveUdpTargets())
            addAll(peerUdpCandidates.take(4))
        }
        for (target in targets) {
            sendDirectUdpPacketWithCrypto(
                packetType = UDP_TYPE_VIDEO_CTRL,
                callId = callId,
                target = target,
                sequence = System.nanoTime(),
                timestampMs = System.currentTimeMillis(),
                plaintext = ctrl,
                crypto = crypto
            )
        }
    }

    private fun handleIncomingUdpPacket(
        callId: String,
        sender: InetSocketAddress,
        data: ByteArray
    ) {
        val session = _sessionFlow.value ?: return
        if (session.callId != callId || session.forceTorOnly) return
        val crypto = activeCallCrypto ?: preAcceptCrypto ?: return
        if (data.size < 1 + 16 + 8 + DIRECT_UDP_NONCE_BYTES + 16) return

        val bb = ByteBuffer.wrap(data)
        val pktType = bb.get()
        val packetCallId = UUID(bb.long, bb.long)
        if (packetCallId.toString() != callId) return

        when (pktType) {
            UDP_TYPE_PROBE -> {
                val ts = bb.long
                val aad = ByteBuffer.allocate(1 + 16 + 8)
                    .put(pktType)
                    .putLong(packetCallId.mostSignificantBits)
                    .putLong(packetCallId.leastSignificantBits)
                    .putLong(ts)
                    .array()
                val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
                bb.get(nonce)
                val cipherBytes = ByteArray(bb.remaining())
                bb.get(cipherBytes)
                val (usedCrypto, plain) = decryptControlWithFallback(cipherBytes, nonce, aad) ?: return
                if (plain.isEmpty()) return
                val nonceSeq = extractNonceCounter(nonce)
                if (nonceSeq >= 0L && !usedCrypto.ctrlReplayWindow.checkAndMark(nonceSeq)) return
                if (usedCrypto === activeCallCrypto && session.state == CallState.ACTIVE && acceptRetryJob?.isActive == true) {
                    acceptRetryJob?.cancel()
                    preAcceptCrypto?.zeroize()
                    preAcceptCrypto = null
                }

                // Record candidate endpoint & echo PROBE_ACK at a bounded rate (NO unsolicited reverse PROBE storm!)
                candidateDirectPeerEndpoint = sender
                if (!peerUdpCandidates.contains(sender) && peerUdpCandidates.size < MAX_PEER_UDP_CANDIDATES) {
                    peerUdpCandidates.add(0, sender)
                }
                val now = System.currentTimeMillis()
                if (now - lastAutoProbeAckAtMs >= PROBE_ACK_MIN_INTERVAL_MS) {
                    lastAutoProbeAckAtMs = now
                    sendDirectUdpProbe(packetCallId, sender, isAck = true, timestampMs = ts, overrideCrypto = usedCrypto)
                }
            }

            UDP_TYPE_PROBE_ACK -> {
                val ts = bb.long
                val aad = ByteBuffer.allocate(1 + 16 + 8)
                    .put(pktType)
                    .putLong(packetCallId.mostSignificantBits)
                    .putLong(packetCallId.leastSignificantBits)
                    .putLong(ts)
                    .array()
                val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
                bb.get(nonce)
                val cipherBytes = ByteArray(bb.remaining())
                bb.get(cipherBytes)
                val (usedCrypto, plain) = decryptControlWithFallback(cipherBytes, nonce, aad) ?: return
                if (plain.isEmpty()) return
                val nonceSeq = extractNonceCounter(nonce)
                if (nonceSeq >= 0L && !usedCrypto.ctrlReplayWindow.checkAndMark(nonceSeq)) return

                // Only a genuine echo of our own probe timestamp proves two-way NAT reachability.
                val now = System.currentTimeMillis()
                val rttRaw = now - ts
                if (rttRaw !in 0L..5_000L) return
                lastPeerActivityAtMs = now
                verifiedDirectPeerEndpoint = sender
                candidateDirectPeerEndpoint = sender
                lastDirectP2pVerifiedAt = now

                val rtt = rttRaw.toInt().coerceIn(1, 2000)
                videoEngine.updateNetworkProfile(isDirectP2p = true, rttMs = rtt, lossDetected = false)
                if (!webrtcVideoEngine.mediaConnected.value) {
                    _sessionFlow.update {
                        it?.copy(
                            transportMode = CallTransportMode.DIRECT_P2P,
                            rttMs = rtt,
                            statusMessage = if (it.isMultipathActive) {
                                "Encrypted • Multipath P2P + Tor (${rtt}ms)"
                            } else {
                                "Encrypted • Direct P2P (${rtt}ms)"
                            }
                        )
                    }
                }
            }

            UDP_TYPE_AUDIO -> {
                if (data.size < 1 + 16 + 8 + 8 + DIRECT_UDP_NONCE_BYTES + 16) return
                val seq = bb.long
                val ts = bb.long
                val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                    .put(pktType)
                    .putLong(packetCallId.mostSignificantBits)
                    .putLong(packetCallId.leastSignificantBits)
                    .putLong(seq)
                    .putLong(ts)
                    .array()
                val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
                bb.get(nonce)
                val cipherBytes = ByteArray(bb.remaining())
                bb.get(cipherBytes)
                val decodedAudio = decryptUdpGcm(cipherBytes, crypto.recvKey, nonce, aad, crypto.recvKeySpec) ?: return
                val nonceSeq = extractNonceCounter(nonce)
                if (nonceSeq >= 0L && !crypto.audioReplayWindow.checkAndMark(nonceSeq)) return

                val now = System.currentTimeMillis()
                lastPeerActivityAtMs = now
                candidateDirectPeerEndpoint = sender

                val rxCount = rxAudioFramesCounter.incrementAndGet()
                if ((rxCount and 3L) == 0L) {
                    _rxPacketPulseFlow.value = now
                }
                audioEngine?.onRemoteAudioFrame(seq, ts, decodedAudio)
                if (!CallAudioEngine.isDtxSidFrame(decodedAudio)) {
                    for ((recSeq, recPayload) in fecDecoder.onVoiceFrameMulti(seq, decodedAudio)) {
                        audioEngine?.onRemoteAudioFrame(recSeq, ts, recPayload)
                    }
                }
            }

            UDP_TYPE_AUDIO_FEC -> {
                if (data.size < 1 + 16 + 8 + 8 + DIRECT_UDP_NONCE_BYTES + 16) return
                val seq = bb.long
                val ts = bb.long
                val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                    .put(pktType)
                    .putLong(packetCallId.mostSignificantBits)
                    .putLong(packetCallId.leastSignificantBits)
                    .putLong(seq)
                    .putLong(ts)
                    .array()
                val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
                bb.get(nonce)
                val cipherBytes = ByteArray(bb.remaining())
                bb.get(cipherBytes)
                val fecBytes = decryptUdpGcm(cipherBytes, crypto.recvKey, nonce, aad, crypto.recvKeySpec) ?: return
                val nonceSeq = extractNonceCounter(nonce)
                if (nonceSeq >= 0L && !crypto.audioReplayWindow.checkAndMark(nonceSeq)) return

                lastPeerActivityAtMs = System.currentTimeMillis()
                for ((recSeq, recPayload) in fecDecoder.onFecPacketMulti(fecBytes)) {
                    audioEngine?.onRemoteAudioFrame(recSeq, ts, recPayload)
                }
            }

            UDP_TYPE_VIDEO -> {
                if (data.size < 1 + 16 + 8 + 8 + DIRECT_UDP_NONCE_BYTES + 16) return
                val seq = bb.long
                val ts = bb.long
                val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                    .put(pktType)
                    .putLong(packetCallId.mostSignificantBits)
                    .putLong(packetCallId.leastSignificantBits)
                    .putLong(seq)
                    .putLong(ts)
                    .array()
                val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
                bb.get(nonce)
                val cipherBytes = ByteArray(bb.remaining())
                bb.get(cipherBytes)
                val plainFragment = decryptUdpGcm(cipherBytes, crypto.recvKey, nonce, aad, crypto.recvKeySpec) ?: return
                val nonceSeq = extractNonceCounter(nonce)
                if (nonceSeq >= 0L && !crypto.videoReplayWindow.checkAndMark(nonceSeq)) return

                val now = System.currentTimeMillis()
                lastPeerActivityAtMs = now
                candidateDirectPeerEndpoint = sender

                if (!session.isRemoteVideoEnabled) {
                    _sessionFlow.update { s ->
                        if (s?.callId == callId && !s.isRemoteVideoEnabled) {
                            s.copy(isVideoCall = true, isRemoteVideoEnabled = true)
                        } else s
                    }
                }
                videoEngine.onRemoteVideoFragment(plainFragment)
            }

            UDP_TYPE_VIDEO_CTRL -> {
                if (data.size < 1 + 16 + 8 + 8 + DIRECT_UDP_NONCE_BYTES + 16) return
                val seq = bb.long
                val ts = bb.long
                val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                    .put(pktType)
                    .putLong(packetCallId.mostSignificantBits)
                    .putLong(packetCallId.leastSignificantBits)
                    .putLong(seq)
                    .putLong(ts)
                    .array()
                val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
                bb.get(nonce)
                val cipherBytes = ByteArray(bb.remaining())
                bb.get(cipherBytes)
                val (usedCrypto, plainCtrl) = decryptControlWithFallback(cipherBytes, nonce, aad) ?: return
                val nonceSeq = extractNonceCounter(nonce)
                if (nonceSeq >= 0L && !usedCrypto.ctrlReplayWindow.checkAndMark(nonceSeq)) return
                if (plainCtrl.isEmpty()) return

                lastPeerActivityAtMs = System.currentTimeMillis()
                when (plainCtrl[0]) {
                    VIDEO_CTRL_PLI -> videoEngine.requestImmediateKeyframe()
                    VIDEO_CTRL_WEBRTC_SIG -> if (plainCtrl.size > 2) {
                        val sigBytes = plainCtrl.copyOfRange(1, plainCtrl.size)
                        onIncomingTorCallSignal(session.peerOnion, sigBytes)
                    }
                    VIDEO_CTRL_STATE -> if (plainCtrl.size >= 2) {
                        val enabled = plainCtrl[1].toInt() != 0
                        _sessionFlow.update { s ->
                            if (s?.callId == callId) {
                                s.copy(
                                    isVideoCall = s.isVideoCall || enabled,
                                    isRemoteVideoEnabled = enabled,
                                    isVideoSwapped = if (!enabled) false else s.isVideoSwapped
                                )
                            } else s
                        }
                        if (enabled) sendVideoPliRequest()
                    }
                }
            }
        }
    }

    private fun sendDirectUdpProbe(
        callUuid: UUID,
        target: InetSocketAddress,
        isAck: Boolean,
        timestampMs: Long,
        overrideCrypto: AegisCallCrypto.CallKeySession? = null
    ) {
        val socket = udpSocket ?: return
        val session = _sessionFlow.value
        val sendList = if (overrideCrypto != null) {
            listOf(overrideCrypto)
        } else {
            val primary = activeCallCrypto
            val pre = preAcceptCrypto
            if (pre != null && pre !== primary && (session?.state != CallState.ACTIVE || acceptRetryJob?.isActive == true)) {
                listOfNotNull(primary, pre)
            } else {
                listOfNotNull(primary ?: pre)
            }
        }
        val type = if (isAck) UDP_TYPE_PROBE_ACK else UDP_TYPE_PROBE
        val aad = ByteBuffer.allocate(1 + 16 + 8)
            .put(type)
            .putLong(callUuid.mostSignificantBits)
            .putLong(callUuid.leastSignificantBits)
            .putLong(timestampMs)
            .array()
        for (crypto in sendList) {
            try {
                val nonce = nextMonotonicNonce()
                val cipher = encryptUdpGcm(byteArrayOf(0x50, 0x32, 0x50), crypto.sendKey, nonce, aad, crypto.sendKeySpec) ?: continue
                val packetBytes = ByteBuffer.allocate(aad.size + nonce.size + cipher.size)
                    .put(aad)
                    .put(nonce)
                    .put(cipher)
                    .array()
                socket.send(DatagramPacket(packetBytes, packetBytes.size, target))
            } catch (_: Exception) {}
        }
    }

    private fun sendDirectUdpAudioPacket(
        callId: String,
        target: InetSocketAddress,
        sequence: Long,
        timestampMs: Long,
        encodedAdpcm: ByteArray
    ): Boolean = sendDirectUdpPacketWithType(UDP_TYPE_AUDIO, callId, target, sequence, timestampMs, encodedAdpcm)

    private fun sendDirectUdpVideoControl(
        callId: String,
        target: InetSocketAddress,
        ctrlBytes: ByteArray
    ): Boolean = sendDirectUdpPacketWithType(
        UDP_TYPE_VIDEO_CTRL,
        callId,
        target,
        System.nanoTime(),
        System.currentTimeMillis(),
        ctrlBytes
    )

    private fun sendDirectUdpPacketWithType(
        packetType: Byte,
        callId: String,
        target: InetSocketAddress,
        sequence: Long,
        timestampMs: Long,
        plaintext: ByteArray
    ): Boolean {
        val crypto = activeCallCrypto ?: return false
        return sendDirectUdpPacketWithCrypto(packetType, callId, target, sequence, timestampMs, plaintext, crypto)
    }

    private fun sendDirectUdpPacketWithCrypto(
        packetType: Byte,
        callId: String,
        target: InetSocketAddress,
        sequence: Long,
        timestampMs: Long,
        plaintext: ByteArray,
        crypto: AegisCallCrypto.CallKeySession
    ): Boolean {
        val socket = udpSocket ?: return false
        return try {
            val callUuid = currentCallUuid ?: UUID.fromString(callId)
            val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                .put(packetType)
                .putLong(callUuid.mostSignificantBits)
                .putLong(callUuid.leastSignificantBits)
                .putLong(sequence)
                .putLong(timestampMs)
                .array()
            val nonce = nextMonotonicNonce()
            val cipher = encryptUdpGcm(plaintext, crypto.sendKey, nonce, aad, crypto.sendKeySpec) ?: return false
            val packetBytes = ByteBuffer.allocate(aad.size + nonce.size + cipher.size)
                .put(aad)
                .put(nonce)
                .put(cipher)
                .array()
            socket.send(DatagramPacket(packetBytes, packetBytes.size, target))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveKeySpec(key: ByteArray, cachedSpec: SecretKeySpec? = null): SecretKeySpec {
        if (cachedSpec != null) return cachedSpec
        val cached = currentSessionKeySpec
        return if (cached != null && currentSessionKey === key) cached else SecretKeySpec(key, "AES")
    }

    private fun encryptUdpGcm(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        keySpec: SecretKeySpec? = null
    ): ByteArray? {
        return try {
            val cipher = encryptCipherTl.get() ?: Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, resolveKeySpec(key, keySpec), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptUdpGcm(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        keySpec: SecretKeySpec? = null
    ): ByteArray? {
        return try {
            val cipher = decryptCipherTl.get() ?: Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, resolveKeySpec(key, keySpec), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    // ========== Timers, Heartbeat, Ringing & Cleanup ==========

    private fun startCallTimer(callId: String) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val current = _sessionFlow.value
                if (current == null || current.callId != callId || current.state != CallState.ACTIVE) break
                val connectedAt = current.connectedAtMs ?: System.currentTimeMillis()
                val elapsed = ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
                _sessionFlow.update {
                    if (it?.callId == callId) it.copy(durationSeconds = elapsed) else it
                }
            }
        }
    }

    private fun startCallHeartbeatLoop(callId: String, peerOnion: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            while (isActive) {
                delay(4_000L)
                val current = _sessionFlow.value
                if (current == null || current.callId != callId || current.state != CallState.ACTIVE) break
                val now = System.currentTimeMillis()
                if (lastPeerActivityAtMs > 0L && (now - lastPeerActivityAtMs) > 25_000L) {
                    callMutex.withLock {
                        if (_sessionFlow.value?.callId == callId && _sessionFlow.value?.state == CallState.ACTIVE) {
                            endCallInternal(
                                callId = callId,
                                reason = "Connection lost",
                                notifyPeer = false,
                                logText = if (current.isVideoCall) "📹 Video call ended (Lost connection)" else "📞 Voice call ended (Lost connection)"
                            )
                        }
                    }
                    break
                }
                val directVerified = verifiedDirectPeerEndpoint != null &&
                    (now - lastDirectP2pVerifiedAt) <= DIRECT_P2P_STALE_MS
                val myInfo = messaging.getMyContactInfo()
                val hbPayload = buildSignalJson(
                    type = "HEARTBEAT",
                    callId = callId,
                    senderOnion = myInfo.onionAddress,
                    senderNickname = myInfo.nickname,
                    senderPubKey = myInfo.publicKey,
                    candidates = if (current.forceTorOnly || directVerified) emptyList() else localUdpCandidates.toList()
                )
                messaging.sendCallSignalFrame(peerOnion, hbPayload)
            }
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            callMutex.withLock {
                val current = _sessionFlow.value
                if (current != null && current.callId == callId &&
                    (current.state == CallState.OUTGOING_DIALING ||
                        current.state == CallState.OUTGOING_RINGING ||
                        current.state == CallState.INCOMING_RINGING)
                ) {
                    val icon = if (current.isVideoCall) "📹" else "📞"
                    val kind = if (current.isVideoCall) "video" else "voice"
                    val logText = if (current.isIncoming) "$icon Missed $kind call" else "$icon Outgoing $kind call (No answer)"
                    if (current.isIncoming) {
                        showMissedCallNotification(current)
                    }
                    endCallInternal(
                        callId = callId,
                        reason = if (current.isIncoming) "Missed call" else "No answer",
                        notifyPeer = !current.isIncoming,
                        logText = logText
                    )
                }
            }
        }
    }

    private fun startIncomingRingtoneAndVibration() {
        stopRingingAndVibration()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dndActive = nm != null && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        if (ringerMode == AudioManager.RINGER_MODE_NORMAL && !dndActive) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(context, uri)?.also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.isLooping = true
                    }
                    it.play()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ringtone play error: ${e.message}")
            }
        }

        if (ringerMode != AudioManager.RINGER_MODE_SILENT && !dndActive) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    val timings = longArrayOf(0L, 65L, 110L, 85L, 180L, 65L, 130L, 95L, 1450L)
                    val amplitudes = intArrayOf(0, 85, 195, 70, 0, 110, 235, 80, 0)
                    val fallbackTimings = longArrayOf(0L, 260L, 180L, 320L, 1450L)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = if (vibrator.hasAmplitudeControl()) {
                            VibrationEffect.createWaveform(timings, amplitudes, 0)
                        } else {
                            VibrationEffect.createWaveform(fallbackTimings, 0)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val attrs = VibrationAttributes.Builder()
                                .setUsage(VibrationAttributes.USAGE_RINGTONE)
                                .build()
                            vibrator.vibrate(effect, attrs)
                        } else {
                            vibrator.vibrate(effect)
                        }
                    } else {
                        vibrator.vibrate(fallbackTimings, 0)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vibrator error: ${e.message}")
            }
        }
    }

    private fun showMissedCallNotification(session: CallSessionInfo) {
        if (com.seamless.android.util.AppForegroundTracker.isAppInForeground(context)) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    MISSED_CALL_CHANNEL_ID,
                    "Missed Encrypted Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Missed voice and video calls over Tor"
                }
                nm.createNotificationChannel(channel)
            }
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!session.contactId.isNullOrBlank()) {
                    putExtra(com.seamless.android.net.TorMessageNotifier.EXTRA_OPEN_TOR_CHAT, true)
                    putExtra(com.seamless.android.net.TorMessageNotifier.EXTRA_TOR_CONTACT_ID, session.contactId)
                }
            }
            val pi = PendingIntent.getActivity(
                context,
                (session.callId.hashCode() and 0x7FFF),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val kind = if (session.isVideoCall) "video" else "voice"
            val notif = NotificationCompat.Builder(context, MISSED_CALL_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Missed $kind call")
                .setContentText("From ${session.peerNickname}")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(MISSED_CALL_NOTIFICATION_BASE_ID + (session.callId.hashCode() and 0xFF), notif)
        }
    }

    private fun startOutgoingRingbackTone() {
        ringbackJob?.cancel()
        ringbackJob = scope.launch {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55)
                toneGenerator = tg
                while (isActive) {
                    val s = _sessionFlow.value
                    if (s == null || (s.state != CallState.OUTGOING_DIALING && s.state != CallState.OUTGOING_RINGING)) {
                        break
                    }
                    tg.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                    delay(3000L)
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopRingingAndVibration() {
        ringbackJob?.cancel()
        ringbackJob = null
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { toneGenerator?.stopTone(); toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private suspend fun endCallInternal(
        callId: String,
        reason: String,
        notifyPeer: Boolean,
        logText: String?
    ) {
        val existing = _sessionFlow.value
        if (existing == null || existing.callId != callId) return

        val peerOnion = existing.peerOnion
        val isIncoming = existing.isIncoming
        cleanupCallResourcesLocked()

        _sessionFlow.value = existing.copy(
            state = CallState.ENDED,
            localVoiceLevel = 0f,
            remoteVoiceLevel = 0f,
            statusMessage = reason
        )

        if (notifyPeer && peerOnion.isNotBlank()) {
            scope.launch {
                val messaging = TorMessagingService.getInstance(context)
                val myInfo = messaging.getMyContactInfo()
                val hangupPayload = buildSignalJson(
                    type = "HANGUP",
                    callId = callId,
                    senderOnion = myInfo.onionAddress,
                    senderNickname = myInfo.nickname,
                    senderPubKey = myInfo.publicKey
                )
                sendCallSignalWithRetry(messaging, peerOnion, hangupPayload, maxAttempts = 3)
            }
        }

        if (!logText.isNullOrBlank()) {
            scope.launch {
                persistCallLogToChat(peerOnion, existing.contactId, callId, logText, isIncoming)
            }
        }

        scope.launch {
            delay(1_600L)
            callMutex.withLock {
                if (_sessionFlow.value?.callId == callId) {
                    _sessionFlow.value = null
                    CallForegroundService.stop(context)
                }
            }
        }
    }

    private fun cleanupCallResourcesLocked() {
        timerJob?.cancel()
        timerJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        torSenderJob?.cancel()
        torSenderJob = null
        torVideoSenderJob?.cancel()
        torVideoSenderJob = null
        outgoingOfferJob?.cancel()
        outgoingOfferJob = null
        acceptRetryJob?.cancel()
        acceptRetryJob = null
        while (torVideoFrameChannel.tryReceive().isSuccess) {}
        p2pFastBurstCounter.set(0)
        lastCandidateTrickleAtMs = 0L
        torVideoConserveUntilMs = 0L
        lastAutoProbeAckAtMs = 0L
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        p2pProbeJob?.cancel()
        p2pProbeJob = null
        p2pReceiveJob?.cancel()
        p2pReceiveJob = null
        stunDiscoveryJob?.cancel()
        stunDiscoveryJob = null
        candidateTrickleJob?.cancel()
        candidateTrickleJob = null
        unregisterCallNetworkCallback()

        stopRingingAndVibration()
        audioEngine?.stop()
        webrtcVideoEngine.endSession()
        webrtcGeneration.set(0)
        processedWebRtcSdps.clear()
        pendingSdpParts.clear()
        dtlsFingerprints.clear()
        fecEncoder.reset()
        fecDecoder.reset()
        videoEngine.stop()
        wasLocalVideoPausedByBg = false

        try { udpSocket?.close() } catch (_: Exception) {}
        udpSocket = null

        activeCallCrypto?.zeroize()
        activeCallCrypto = null
        preAcceptCrypto?.zeroize()
        preAcceptCrypto = null
        AegisTorCrypto.zeroize(localEphemeralPriv, currentSessionKey)
        localEphemeralPriv = null
        localEphemeralPubHex = ""
        localIdentityPubHexForCall = ""
        remoteIdentityPubHexForCall = ""
        currentSessionKey = null
        currentSessionKeySpec = null
        currentCallUuid = null
        pendingStunTxIds.clear()
        firstContinuousDirectOkAtMs = 0L
        localUdpCandidates.clear()
        peerUdpCandidates.clear()
        synchronized(discoveredStunPortsByIp) {
            discoveredStunPortsByIp.clear()
        }
        candidateDirectPeerEndpoint = null
        verifiedDirectPeerEndpoint = null
        lastDirectP2pVerifiedAt = 0L
        lastPeerActivityAtMs = 0L
        _localVoiceLevelFlow.value = 0f
        _remoteVoiceLevelFlow.value = 0f
        _jitterBufferMsFlow.value = 0
        txAudioFramesCounter.set(0L)
        rxAudioFramesCounter.set(0L)
        _securityCodeFlow.value = emptyList()
    }

    /**
     * Derives a MITM-proof 4-emoji Bound Short Authentication String cryptographically binding
     * the ephemeral X25519 secret, sorted static identity keys, callId, and WebRTC DTLS fingerprints.
     */
    private fun publishSecurityCode(callId: String) {
        val crypto = activeCallCrypto
        if (crypto == null || crypto.sasSecret.size != 32) {
            _securityCodeFlow.value = emptyList()
            return
        }
        _securityCodeFlow.value = AegisCallCrypto.deriveBoundSasEmojis(
            sasSecret = crypto.sasSecret,
            callId = callId,
            localIdentityPubHex = localIdentityPubHexForCall,
            remoteIdentityPubHex = remoteIdentityPubHexForCall,
            dtlsFingerprints = dtlsFingerprints,
            alphabet = SAS_EMOJIS
        )
    }

    private suspend fun persistCallLogToChat(
        peerOnion: String,
        contactId: String?,
        callId: String,
        logText: String,
        isIncoming: Boolean
    ) {
        try {
            val contactManager = OnionContactManager.getInstance(context)
            val contact = (contactId?.let { contactManager.getContact(it) }
                ?: contactManager.getContactByOnion(peerOnion)) ?: return
            val repo = TorMessageRepository.getInstance(context)
            val now = System.currentTimeMillis()
            if (isIncoming) {
                repo.saveReceivedMessage(
                    messageId = "call-$callId",
                    contact = contact,
                    text = logText,
                    timestamp = now
                )
            } else {
                repo.saveSentMessage(
                    messageId = "call-$callId",
                    contact = contact,
                    text = logText,
                    timestamp = now
                )
                repo.updateDeliveryState("call-$callId", OnionMessageProtocol.DeliveryState.DELIVERED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist call log: ${e.message}")
        }
    }

    private fun buildSignalJson(
        type: String,
        callId: String,
        senderOnion: String,
        senderNickname: String,
        senderPubKey: String,
        sessionKey: ByteArray? = null,
        ephemeralPubHex: String? = null,
        forceTorOnly: Boolean = false,
        candidates: List<String> = emptyList(),
        isVideo: Boolean = false,
        localVideoEnabled: Boolean = false,
        videoCodecs: List<String> = emptyList()
    ): ByteArray {
        val obj = JSONObject().apply {
            put("type", type)
            put("callId", callId)
            put("senderOnion", senderOnion)
            put("senderNickname", senderNickname)
            put("senderPubKey", senderPubKey)
            put("timestamp", System.currentTimeMillis())
            if (isVideo) {
                put("isVideo", true)
            }
            if (forceTorOnly || type == "TOR_MODE") {
                put("forceTorOnly", forceTorOnly)
            }
            if (!ephemeralPubHex.isNullOrBlank()) {
                put("ephemeralPubHex", ephemeralPubHex)
            }
            if (localVideoEnabled || type == "VIDEO_STATE" || type == "ACCEPT") {
                put("localVideoEnabled", localVideoEnabled)
            }
            if (videoCodecs.isNotEmpty()) {
                val codecsArr = JSONArray()
                videoCodecs.forEach { codecsArr.put(it) }
                put("videoCodecs", codecsArr)
            }
            if (sessionKey != null) {
                put("sessionKeyHex", sessionKey.joinToString("") { "%02x".format(it) })
            }
            if (candidates.isNotEmpty()) {
                val arr = JSONArray()
                candidates.forEach { arr.put(it) }
                put("p2pCandidates", arr)
            }
        }
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    private fun mergeCandidatesIntoList(arr: JSONArray?) {
        if (arr == null) return
        val maxEntries = minOf(arr.length(), MAX_PEER_UDP_CANDIDATES * 2)
        for (i in 0 until maxEntries) {
            if (peerUdpCandidates.size >= MAX_PEER_UDP_CANDIDATES) break
            val entry = arr.optString(i).trim()
            val sockAddr = parseStrictIpLiteralSocketAddress(entry) ?: continue
            if (!peerUdpCandidates.contains(sockAddr)) {
                val addr = sockAddr.address
                val isPublicRoutable = addr != null && !addr.isSiteLocalAddress && !addr.isLoopbackAddress && !addr.isLinkLocalAddress
                if (isPublicRoutable) {
                    peerUdpCandidates.add(0, sockAddr)
                } else {
                    peerUdpCandidates.add(sockAddr)
                }
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i + 1 < clean.length) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
