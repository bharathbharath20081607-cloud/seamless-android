@file:Suppress("DEPRECATION")
package com.seamless.android.call

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.TorMessagingService
import com.seamless.android.service.CallForegroundService
import com.seamless.android.service.TorForegroundService
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
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
        private const val DIRECT_P2P_PURE_WINDOW_MS = 1_800L
        private const val DIRECT_P2P_STALE_MS = 4_500L
        private const val INITIAL_MULTIPATH_WARMUP_MS = 8_000L
        private const val DIRECT_UDP_NONCE_BYTES = 12
        private const val TOR_AUDIO_MAGIC_V2: Byte = 0x5A

        private const val UDP_TYPE_PROBE: Byte = 0x01
        private const val UDP_TYPE_PROBE_ACK: Byte = 0x02
        private const val UDP_TYPE_AUDIO: Byte = 0x03

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
        val payload: ByteArray
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val callMutex = Mutex()
    private val secureRandom = SecureRandom()

    private val _sessionFlow = MutableStateFlow<CallSessionInfo?>(null)
    val sessionFlow: StateFlow<CallSessionInfo?> = _sessionFlow.asStateFlow()

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

    private val txAudioFramesCounter = AtomicLong(0L)
    private val rxAudioFramesCounter = AtomicLong(0L)

    // Conflating 3-frame FIFO ring buffer for outbound Tor audio (prevents coroutine reordering & TCP bufferbloat)
    private val torAudioOutChannel = Channel<OutboundTorAudioItem>(
        capacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var audioEngine: CallAudioEngine? = null
    private var timerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var torSenderJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var ringbackJob: Job? = null
    private var p2pProbeJob: Job? = null
    private var p2pReceiveJob: Job? = null
    private var stunDiscoveryJob: Job? = null

    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var udpSocket: DatagramSocket? = null

    @Volatile
    private var currentSessionKey: ByteArray? = null
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
            engine.estimatedOneWayLatencyMs.collect { estMs ->
                _sessionFlow.update { current ->
                    if (current != null && current.state == CallState.ACTIVE && current.rttMs == 0 && estMs > 0) {
                        current.copy(rttMs = estMs)
                    } else current
                }
            }
        }
    }

    /**
     * Initiate an outgoing voice call to a Tor P2P contact.
     */
    fun startOutgoingCall(
        contact: OnionContactManager.OnionContact,
        forceTorOnly: Boolean = false
    ) {
        startOutgoingCallByOnion(
            peerOnion = contact.onionAddress,
            peerNickname = contact.nickname,
            peerPublicKey = contact.publicKey,
            contactId = contact.id,
            forceTorOnly = forceTorOnly
        )
    }

    fun startOutgoingCallByOnion(
        peerOnion: String,
        peerNickname: String,
        peerPublicKey: String = "",
        contactId: String? = null,
        forceTorOnly: Boolean = false
    ) {
        val canonicalOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerOnion)
            .ifBlank { peerOnion.trim() }
        if (canonicalOnion.isBlank() || canonicalOnion.startsWith("rendezvous:")) {
            return
        }

        scope.launch {
            callMutex.withLock {
                val existing = _sessionFlow.value
                if (existing != null && existing.state != CallState.IDLE && existing.state != CallState.ENDED) {
                    Log.w(TAG, "Cannot start call: another call is already in state ${existing.state}")
                    return@withLock
                }

                cleanupCallResourcesLocked()

                val callId = UUID.randomUUID().toString()
                val sessionKey = ByteArray(32).also { secureRandom.nextBytes(it) }
                currentSessionKey = sessionKey

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
                    isMultipathActive = !forceTorOnly,
                    forceTorOnly = forceTorOnly,
                    statusMessage = "Connecting encrypted circuit..."
                )
                _sessionFlow.value = newSession
                CallForegroundService.start(context)
                startRingTimeout(callId)
                startTorAudioSenderLoop(callId)

                val initialCandidates = if (!forceTorOnly) {
                    openDirectUdpSocketFast(callId, canonicalOnion)
                } else {
                    emptyList()
                }

                // Ensure Tor service is running and send OFFER
                launch {
                    val messaging = TorMessagingService.getInstance(context)
                    if (messaging.serviceState.value == TorMessagingService.ServiceState.STOPPED) {
                        TorForegroundService.start(context)
                        messaging.start()
                    }

                    val myInfo = messaging.getMyContactInfo()
                    val offerPayload = buildSignalJson(
                        type = "OFFER",
                        callId = callId,
                        senderOnion = myInfo.onionAddress,
                        senderNickname = myInfo.nickname,
                        senderPubKey = myInfo.publicKey,
                        sessionKey = sessionKey,
                        candidates = initialCandidates
                    )

                    val sent = messaging.sendCallSignalFrame(canonicalOnion, offerPayload)
                    val current = _sessionFlow.value
                    if (current?.callId == callId) {
                        if (sent) {
                            _sessionFlow.update { s ->
                                if (s?.callId == callId && s.state == CallState.OUTGOING_DIALING) {
                                    s.copy(statusMessage = "Calling ${s.peerNickname}...")
                                } else s
                            }
                            startOutgoingRingbackTone()
                        } else {
                            endCallInternal(
                                callId = callId,
                                reason = "Peer unreachable over Tor",
                                notifyPeer = false,
                                logText = "📞 Outgoing voice call (Unreachable)"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Accept the currently ringing incoming call.
     */
    fun acceptIncomingCall() {
        scope.launch {
            callMutex.withLock {
                val session = _sessionFlow.value ?: return@withLock
                if (session.state != CallState.INCOMING_RINGING) return@withLock

                ringTimeoutJob?.cancel()
                stopRingingAndVibration()

                // Give Android AudioPolicyManager a brief moment to release Ringtone focus cleanly
                delay(60L)

                val initialCandidates = if (!session.forceTorOnly) {
                    openDirectUdpSocketFast(session.callId, session.peerOnion)
                } else {
                    emptyList()
                }

                val now = System.currentTimeMillis()
                lastPeerActivityAtMs = now
                _sessionFlow.value = session.copy(
                    state = CallState.ACTIVE,
                    connectedAtMs = now,
                    durationSeconds = 0L,
                    isMultipathActive = !session.forceTorOnly,
                    statusMessage = if (session.forceTorOnly) {
                        "Encrypted • Tor Onion Only"
                    } else {
                        "Encrypted • Hybrid P2P + Tor"
                    }
                )

                startTorAudioSenderLoop(session.callId)
                publishSecurityCode(session.callId)

                val audioStarted = audioEngine?.start(
                    initialSpeakerphone = session.isSpeakerphone,
                    isDirectP2p = false
                ) ?: false

                if (!audioStarted) {
                    endCallInternal(
                        callId = session.callId,
                        reason = "Microphone unavailable",
                        notifyPeer = true,
                        logText = "📞 Incoming voice call (Mic error)"
                    )
                    return@withLock
                }

                startCallTimer(session.callId)
                startCallHeartbeatLoop(session.callId, session.peerOnion)
                if (!session.forceTorOnly) {
                    startDirectP2pProbing(session.callId)
                }

                // Send ACCEPT signal back to caller immediately
                launch {
                    val messaging = TorMessagingService.getInstance(context)
                    val myInfo = messaging.getMyContactInfo()
                    val acceptPayload = buildSignalJson(
                        type = "ACCEPT",
                        callId = session.callId,
                        senderOnion = myInfo.onionAddress,
                        senderNickname = myInfo.nickname,
                        senderPubKey = myInfo.publicKey,
                        sessionKey = currentSessionKey,
                        candidates = initialCandidates
                    )
                    messaging.sendCallSignalFrame(session.peerOnion, acceptPayload)
                }
            }
        }
    }

    /**
     * Decline an incoming ringing call.
     */
    fun declineIncomingCall() {
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.INCOMING_RINGING) return
        scope.launch {
            val messaging = TorMessagingService.getInstance(context)
            val myInfo = messaging.getMyContactInfo()
            val payload = buildSignalJson(
                type = "DECLINE",
                callId = current.callId,
                senderOnion = myInfo.onionAddress,
                senderNickname = myInfo.nickname,
                senderPubKey = myInfo.publicKey
            )
            launch { messaging.sendCallSignalFrame(current.peerOnion, payload) }
            endCallInternal(
                callId = current.callId,
                reason = "Call declined",
                notifyPeer = false,
                logText = "📞 Declined voice call"
            )
        }
    }

    /**
     * Hang up an active or dialing call.
     */
    fun endCall(reason: String = "Call ended", notifyPeer: Boolean = true) {
        val current = _sessionFlow.value ?: return
        scope.launch {
            val duration = current.durationSeconds
            val logSummary = when {
                current.state == CallState.ACTIVE -> {
                    val mins = duration / 60
                    val secs = duration % 60
                    val durStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                    if (current.isIncoming) "📞 Incoming voice call ($durStr)"
                    else "📞 Outgoing voice call ($durStr)"
                }
                current.isIncoming -> "📞 Missed voice call"
                else -> "📞 Cancelled voice call"
            }
            endCallInternal(
                callId = current.callId,
                reason = reason,
                notifyPeer = notifyPeer,
                logText = logSummary
            )
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
        val nextSpeaker = !current.isSpeakerphone
        audioEngine?.setSpeakerphone(nextSpeaker)
        _sessionFlow.update { it?.copy(isSpeakerphone = nextSpeaker) }
    }

    fun toggleForceTorOnly() {
        val current = _sessionFlow.value ?: return
        val nextForceTor = !current.forceTorOnly
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
                val sock = udpSocket
                if (sock != null && !sock.isClosed) {
                    discoverStunAndTrickleCandidatesAsync(current.callId, current.peerOnion, sock)
                } else {
                    openDirectUdpSocketFast(current.callId, current.peerOnion)
                }
                startDirectP2pProbing(current.callId)
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

        val type = json.optString("type")
        val callId = json.optString("callId")
        if (type.isBlank() || callId.isBlank()) return

        val canonicalSender = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(
            json.optString("senderOnion").ifBlank { senderOnion }
        ).ifBlank { senderOnion }

        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value
                when (type) {
                    "OFFER" -> {
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

                        val contactManager = OnionContactManager.getInstance(context)
                        val contact = contactManager.getContactByOnion(canonicalSender)
                            ?: json.optString("senderPubKey").takeIf { it.isNotBlank() }
                                ?.let { contactManager.getContactByPublicKey(it) }

                        val callerName = contact?.nickname
                            ?: json.optString("senderNickname").ifBlank { canonicalSender.take(12) }
                        val callerPubKey = contact?.publicKey
                            ?: json.optString("senderPubKey")

                        val sessionKeyHex = json.optString("sessionKeyHex")
                        currentSessionKey = if (sessionKeyHex.length == 64) {
                            hexToBytes(sessionKeyHex)
                        } else null

                        mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))

                        val incomingSession = CallSessionInfo(
                            callId = callId,
                            peerOnion = canonicalSender,
                            peerNickname = callerName,
                            peerPublicKey = callerPubKey,
                            contactId = contact?.id,
                            isIncoming = true,
                            state = CallState.INCOMING_RINGING,
                            transportMode = CallTransportMode.TOR_ONION,
                            statusMessage = "Incoming Encrypted Call..."
                        )
                        _sessionFlow.value = incomingSession
                        CallForegroundService.start(context)
                        startIncomingRingtoneAndVibration()
                        startRingTimeout(callId)

                        // Reply with RINGING immediately
                        launch {
                            val messaging = TorMessagingService.getInstance(context)
                            val myInfo = messaging.getMyContactInfo()
                            val ringingPayload = buildSignalJson(
                                type = "RINGING",
                                callId = callId,
                                senderOnion = myInfo.onionAddress,
                                senderNickname = myInfo.nickname,
                                senderPubKey = myInfo.publicKey
                            )
                            messaging.sendCallSignalFrame(canonicalSender, ringingPayload)
                        }
                    }

                    "RINGING" -> {
                        if (current?.callId == callId && current.state == CallState.OUTGOING_DIALING) {
                            _sessionFlow.value = current.copy(
                                state = CallState.OUTGOING_RINGING,
                                statusMessage = "Ringing..."
                            )
                        }
                    }

                    "ACCEPT" -> {
                        if (current?.callId == callId &&
                            (current.state == CallState.OUTGOING_DIALING || current.state == CallState.OUTGOING_RINGING)
                        ) {
                            ringTimeoutJob?.cancel()
                            stopRingingAndVibration()
                            delay(60L)
                            mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))

                            val now = System.currentTimeMillis()
                            lastPeerActivityAtMs = now
                            _sessionFlow.value = current.copy(
                                state = CallState.ACTIVE,
                                connectedAtMs = now,
                                durationSeconds = 0L,
                                isMultipathActive = !current.forceTorOnly,
                                statusMessage = if (current.forceTorOnly) {
                                    "Encrypted • Tor Onion Only"
                                } else {
                                    "Encrypted • Hybrid P2P + Tor"
                                }
                            )
                            publishSecurityCode(callId)

                            val started = audioEngine?.start(
                                initialSpeakerphone = current.isSpeakerphone,
                                isDirectP2p = false
                            ) ?: false

                            if (!started) {
                                endCallInternal(
                                    callId = callId,
                                    reason = "Microphone unavailable",
                                    notifyPeer = true,
                                    logText = "📞 Outgoing voice call (Mic error)"
                                )
                                return@withLock
                            }

                            startCallTimer(callId)
                            startCallHeartbeatLoop(callId, current.peerOnion)
                            if (!current.forceTorOnly) {
                                startDirectP2pProbing(callId)
                            }
                        }
                    }

                    "CANDIDATES" -> {
                        if (current?.callId == callId) {
                            mergeCandidatesIntoList(json.optJSONArray("p2pCandidates"))
                            if (current.state == CallState.ACTIVE && !current.forceTorOnly) {
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
                                logText = "📞 Outgoing voice call (Declined)"
                            )
                        }
                    }

                    "BUSY" -> {
                        if (current?.callId == callId) {
                            endCallInternal(
                                callId = callId,
                                reason = "User is on another call",
                                notifyPeer = false,
                                logText = "📞 Outgoing voice call (Busy)"
                            )
                        }
                    }

                    "HANGUP" -> {
                        if (current?.callId == callId && current.state != CallState.ENDED) {
                            val dur = current.durationSeconds
                            val logText = if (current.state == CallState.ACTIVE) {
                                val mins = dur / 60
                                val secs = dur % 60
                                val durStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                                if (current.isIncoming) "📞 Incoming voice call ($durStr)"
                                else "📞 Outgoing voice call ($durStr)"
                            } else if (current.isIncoming) {
                                "📞 Missed voice call"
                            } else {
                                "📞 Voice call ended"
                            }
                            endCallInternal(
                                callId = callId,
                                reason = "Call ended",
                                notifyPeer = false,
                                logText = logText
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by TorMessagingService when a compact CALL_AUDIO frame arrives over the Tor circuit.
     * Decrypts in RAM in ~0.01ms using [currentSessionKey] (AES-256-GCM).
     */
    fun onIncomingTorCallAudio(
        senderOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ) {
        val current = _sessionFlow.value ?: return
        if (current.state != CallState.ACTIVE || payload.size < 1 + 16 + DIRECT_UDP_NONCE_BYTES + 16) return
        val canonicalSender = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(senderOnion)
            .ifBlank { senderOnion.trim().lowercase() }
        val canonicalPeer = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(current.peerOnion)
            .ifBlank { current.peerOnion.trim().lowercase() }
        if (canonicalSender != canonicalPeer) return

        val buf = ByteBuffer.wrap(payload)
        if (payload[0] != TOR_AUDIO_MAGIC_V2) return
        buf.get() // magic
        val incomingCallUuid = UUID(buf.long, buf.long)
        if (incomingCallUuid.toString() != current.callId) return
        val key = currentSessionKey ?: return
        val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES)
        buf.get(nonce)
        val cipherBytes = ByteArray(buf.remaining())
        buf.get(cipherBytes)
        val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
            .put(TOR_AUDIO_MAGIC_V2)
            .putLong(incomingCallUuid.mostSignificantBits)
            .putLong(incomingCallUuid.leastSignificantBits)
            .putLong(sequence)
            .putLong(timestampMs)
            .array()
        val encodedAudio = decryptUdpGcm(cipherBytes, key, nonce, aad) ?: return

        lastPeerActivityAtMs = System.currentTimeMillis()
        val rxCount = rxAudioFramesCounter.incrementAndGet()
        if ((rxCount and 3L) == 0L) {
            _rxPacketPulseFlow.value = System.currentTimeMillis()
        }
        audioEngine?.onRemoteAudioFrame(sequence, timestampMs, encodedAudio)
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
        val candidateEndpoint = candidateDirectPeerEndpoint
        val verifiedAgeMs = if (verifiedEndpoint != null) (now - lastDirectP2pVerifiedAt) else Long.MAX_VALUE
        val callAgeMs = session.connectedAtMs?.let { now - it } ?: 0L

        var sentViaDirectUdp = false
        if (!session.forceTorOnly) {
            val primaryUdpTarget = verifiedEndpoint ?: candidateEndpoint ?: peerUdpCandidates.firstOrNull()
            if (primaryUdpTarget != null) {
                sentViaDirectUdp = sendDirectUdpAudioPacket(
                    session.callId,
                    primaryUdpTarget,
                    sequence,
                    timestampMs,
                    encodedAdpcm
                )
            }
        }

        // Pure Direct P2P mode only when 2-way UDP was verified within the last 1.8s AND past warmup
        val pureDirectP2pReady = !session.forceTorOnly &&
            verifiedEndpoint != null &&
            verifiedAgeMs <= DIRECT_P2P_PURE_WINDOW_MS &&
            callAgeMs >= INITIAL_MULTIPATH_WARMUP_MS &&
            sentViaDirectUdp

        if (pureDirectP2pReady) {
            if (session.transportMode != CallTransportMode.DIRECT_P2P || session.isMultipathActive) {
                audioEngine?.setTransportMode(true)
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

        // If 2-way UDP is within 4.5s but in warmup or multipath window, keep UI badge as Direct P2P (Multipath)
        // while still sending the redundant copy over Tor .onion below!
        if (!session.forceTorOnly && verifiedEndpoint != null && verifiedAgeMs <= DIRECT_P2P_STALE_MS) {
            audioEngine?.setTransportMode(false) // Use 40ms frames so both Tor & UDP share the exact frame
            if (session.transportMode != CallTransportMode.DIRECT_P2P || !session.isMultipathActive) {
                _sessionFlow.update {
                    it?.copy(
                        transportMode = CallTransportMode.DIRECT_P2P,
                        isMultipathActive = true,
                        statusMessage = "Encrypted • Multipath P2P + Tor"
                    )
                }
            }
        } else if (session.transportMode == CallTransportMode.DIRECT_P2P) {
            audioEngine?.setTransportMode(false)
            _sessionFlow.update {
                it?.copy(
                    transportMode = CallTransportMode.TOR_ONION,
                    isMultipathActive = !it.forceTorOnly,
                    statusMessage = "Encrypted • Tor Onion P2P"
                )
            }
        }

        // Encrypt in RAM with currentSessionKey (0.01ms) and enqueue into the non-blocking FIFO channel
        val callUuid = runCatching { UUID.fromString(session.callId) }.getOrNull() ?: return
        val key = currentSessionKey ?: return
        val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
            .put(TOR_AUDIO_MAGIC_V2)
            .putLong(callUuid.mostSignificantBits)
            .putLong(callUuid.leastSignificantBits)
            .putLong(sequence)
            .putLong(timestampMs)
            .array()
        val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val cipherBytes = encryptUdpGcm(encodedAdpcm, key, nonce, aad) ?: return

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
    }

    private fun startTorAudioSenderLoop(callId: String) {
        torSenderJob?.cancel()
        // Drain any stale frames from previous call
        while (torAudioOutChannel.tryReceive().isSuccess) {}

        torSenderJob = scope.launch(Dispatchers.IO) {
            val messaging = TorMessagingService.getInstance(context)
            for (item in torAudioOutChannel) {
                val s = _sessionFlow.value
                if (s == null || s.callId != callId || s.state == CallState.ENDED || s.state == CallState.IDLE) {
                    break
                }
                if (s.state == CallState.ACTIVE) {
                    messaging.sendCallAudioFrame(
                        recipientOnion = item.recipientOnion,
                        sequence = item.sequence,
                        timestampMs = item.timestampMs,
                        payload = item.payload
                    )
                }
            }
        }
    }

    // ========== Direct P2P UDP Transport (STUN + Port Prediction + IPv6 + LAN) ==========

    private fun openDirectUdpSocketFast(callId: String, peerOnion: String): List<String> {
        return try {
            val socket = DatagramSocket(0).apply {
                reuseAddress = true
                trafficClass = 0xB8 // Expedited Forwarding (EF) DSCP for VoIP
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
                "stun1.l.google.com" to 19302
            )
            for ((host, port) in stunServers) {
                runCatching {
                    val txId = ByteArray(12).also { secureRandom.nextBytes(it) }
                    val request = ByteBuffer.allocate(20)
                        .putShort(0x0001.toShort()) // Binding Request
                        .putShort(0x0000.toShort()) // Message Length = 0
                        .putInt(0x2112A442)         // Magic Cookie
                        .put(txId)
                        .array()
                    val target = InetSocketAddress(InetAddress.getByName(host), port)
                    socket.send(DatagramPacket(request, request.size, target))
                }
            }

            // Wait 550ms for STUN replies to be harvested by startDirectUdpReceiverLoop
            delay(550L)
            val current = _sessionFlow.value
            if (current?.callId == callId && !current.forceTorOnly && localUdpCandidates.isNotEmpty()) {
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
    }

    private val discoveredStunPortsByIp = HashMap<String, MutableList<Int>>()

    private fun handlePotentialStunPacket(data: ByteArray, length: Int) {
        val mapped = parseStunBindingResponse(data, length) ?: return
        if (!localUdpCandidates.contains(mapped)) {
            localUdpCandidates.add(0, mapped)
            Log.i(TAG, "🌍 Discovered STUN public endpoint: $mapped")
        }

        // Tailscale magicsock Sequential CGNAT Port Prediction:
        // If two STUN servers report the same public IPv4 with sequential ports (e.g. delta 1..16),
        // add predicted next ports so simultaneous UDP hole punching pierces sequential CGNATs!
        val lastColon = mapped.lastIndexOf(':')
        if (lastColon > 0 && !mapped.startsWith("[")) {
            val ip = mapped.substring(0, lastColon)
            val port = mapped.substring(lastColon + 1).toIntOrNull() ?: return
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
                        }
                    }
                }
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
        p2pProbeJob = scope.launch {
            val callUuid = runCatching { UUID.fromString(callId) }.getOrNull() ?: return@launch
            var burstCount = 0
            while (isActive) {
                val session = _sessionFlow.value
                if (session == null || session.callId != callId || session.state != CallState.ACTIVE) break
                if (!session.forceTorOnly) {
                    val targets = LinkedHashSet<InetSocketAddress>()
                    verifiedDirectPeerEndpoint?.let { targets.add(it) }
                    candidateDirectPeerEndpoint?.let { targets.add(it) }
                    targets.addAll(peerUdpCandidates)
                    for (target in targets) {
                        sendDirectUdpProbe(callUuid, target, isAck = false, timestampMs = System.currentTimeMillis())
                    }
                }
                burstCount++
                delay(if (burstCount <= 12) 350L else 1_200L)
            }
        }
    }

    private fun startDirectUdpReceiverLoop(callId: String, socket: DatagramSocket) {
        p2pReceiveJob?.cancel()
        p2pReceiveJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(1500)
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

    private fun handleIncomingUdpPacket(
        callId: String,
        sender: InetSocketAddress,
        data: ByteArray
    ) {
        val session = _sessionFlow.value ?: return
        if (session.callId != callId || session.forceTorOnly) return
        val key = currentSessionKey ?: return
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
                val plain = decryptUdpGcm(cipherBytes, key, nonce, aad) ?: return
                if (plain.isEmpty()) return

                // Record candidate endpoint & reply with PROBE_ACK immediately,
                // but do NOT stop Tor audio until we receive PROBE_ACK or UDP_TYPE_AUDIO!
                candidateDirectPeerEndpoint = sender
                if (!peerUdpCandidates.contains(sender)) {
                    peerUdpCandidates.add(0, sender)
                }
                sendDirectUdpProbe(packetCallId, sender, isAck = true, timestampMs = ts)
                sendDirectUdpProbe(packetCallId, sender, isAck = false, timestampMs = System.currentTimeMillis())
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
                val plain = decryptUdpGcm(cipherBytes, key, nonce, aad) ?: return
                if (plain.isEmpty()) return

                // Two-way UDP reachability is now cryptographically verified!
                val now = System.currentTimeMillis()
                lastPeerActivityAtMs = now
                verifiedDirectPeerEndpoint = sender
                candidateDirectPeerEndpoint = sender
                lastDirectP2pVerifiedAt = now

                val rtt = (now - ts).toInt().coerceIn(1, 2000)
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
                val decodedAudio = decryptUdpGcm(cipherBytes, key, nonce, aad) ?: return

                val now = System.currentTimeMillis()
                lastPeerActivityAtMs = now
                verifiedDirectPeerEndpoint = sender
                candidateDirectPeerEndpoint = sender
                lastDirectP2pVerifiedAt = now

                val rxCount = rxAudioFramesCounter.incrementAndGet()
                if ((rxCount and 3L) == 0L) {
                    _rxPacketPulseFlow.value = now
                }
                audioEngine?.onRemoteAudioFrame(seq, ts, decodedAudio)
            }
        }
    }

    private fun sendDirectUdpProbe(
        callUuid: UUID,
        target: InetSocketAddress,
        isAck: Boolean,
        timestampMs: Long
    ) {
        val socket = udpSocket ?: return
        val key = currentSessionKey ?: return
        try {
            val type = if (isAck) UDP_TYPE_PROBE_ACK else UDP_TYPE_PROBE
            val aad = ByteBuffer.allocate(1 + 16 + 8)
                .put(type)
                .putLong(callUuid.mostSignificantBits)
                .putLong(callUuid.leastSignificantBits)
                .putLong(timestampMs)
                .array()
            val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = encryptUdpGcm(byteArrayOf(0x50, 0x32, 0x50), key, nonce, aad) ?: return
            val packetBytes = ByteBuffer.allocate(aad.size + nonce.size + cipher.size)
                .put(aad)
                .put(nonce)
                .put(cipher)
                .array()
            socket.send(DatagramPacket(packetBytes, packetBytes.size, target))
        } catch (_: Exception) {}
    }

    private fun sendDirectUdpAudioPacket(
        callId: String,
        target: InetSocketAddress,
        sequence: Long,
        timestampMs: Long,
        encodedAdpcm: ByteArray
    ): Boolean {
        val socket = udpSocket ?: return false
        val key = currentSessionKey ?: return false
        return try {
            val callUuid = UUID.fromString(callId)
            val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
                .put(UDP_TYPE_AUDIO)
                .putLong(callUuid.mostSignificantBits)
                .putLong(callUuid.leastSignificantBits)
                .putLong(sequence)
                .putLong(timestampMs)
                .array()
            val nonce = ByteArray(DIRECT_UDP_NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = encryptUdpGcm(encodedAdpcm, key, nonce, aad) ?: return false
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

    private fun encryptUdpGcm(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptUdpGcm(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
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
                delay(2_800L)
                val current = _sessionFlow.value
                if (current == null || current.callId != callId || current.state != CallState.ACTIVE) break
                if (lastPeerActivityAtMs > 0L && (System.currentTimeMillis() - lastPeerActivityAtMs) > 25_000L) {
                    callMutex.withLock {
                        if (_sessionFlow.value?.callId == callId && _sessionFlow.value?.state == CallState.ACTIVE) {
                            endCallInternal(
                                callId = callId,
                                reason = "Connection lost",
                                notifyPeer = false,
                                logText = "📞 Voice call ended (Lost connection)"
                            )
                        }
                    }
                    break
                }
                val myInfo = messaging.getMyContactInfo()
                val hbPayload = buildSignalJson(
                    type = "HEARTBEAT",
                    callId = callId,
                    senderOnion = myInfo.onionAddress,
                    senderNickname = myInfo.nickname,
                    senderPubKey = myInfo.publicKey,
                    candidates = if (current.forceTorOnly) emptyList() else localUdpCandidates.toList()
                )
                messaging.sendCallSignalFrame(peerOnion, hbPayload)
            }
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            val current = _sessionFlow.value
            if (current != null && current.callId == callId &&
                (current.state == CallState.OUTGOING_DIALING ||
                    current.state == CallState.OUTGOING_RINGING ||
                    current.state == CallState.INCOMING_RINGING)
            ) {
                val logText = if (current.isIncoming) "📞 Missed voice call" else "📞 Outgoing voice call (No answer)"
                endCallInternal(
                    callId = callId,
                    reason = if (current.isIncoming) "Missed call" else "No answer",
                    notifyPeer = !current.isIncoming,
                    logText = logText
                )
            }
        }
    }

    private fun startIncomingRingtoneAndVibration() {
        stopRingingAndVibration()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context, uri)?.also { it.play() }
        } catch (e: Exception) {
            Log.w(TAG, "Ringtone play error: ${e.message}")
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0L, 600L, 500L, 600L, 1200L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    vibrator.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator error: ${e.message}")
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
                messaging.sendCallSignalFrame(peerOnion, hangupPayload)
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
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        p2pProbeJob?.cancel()
        p2pProbeJob = null
        p2pReceiveJob?.cancel()
        p2pReceiveJob = null
        stunDiscoveryJob?.cancel()
        stunDiscoveryJob = null

        stopRingingAndVibration()
        audioEngine?.stop()

        try { udpSocket?.close() } catch (_: Exception) {}
        udpSocket = null

        currentSessionKey = null
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
     * Derives a 4-emoji Short Authentication String from the shared ephemeral call key.
     * Both peers hold the identical key + callId, so both screens show the same symbols.
     */
    private fun publishSecurityCode(callId: String) {
        val key = currentSessionKey
        if (key == null || key.size != 32) {
            _securityCodeFlow.value = emptyList()
            return
        }
        val digest = MessageDigest.getInstance("SHA-256").run {
            update("SeamlessCallSAS/v1".toByteArray(Charsets.UTF_8))
            update(key)
            update(callId.toByteArray(Charsets.UTF_8))
            digest()
        }
        val bits = ((digest[0].toInt() and 0xFF) shl 16) or
            ((digest[1].toInt() and 0xFF) shl 8) or
            (digest[2].toInt() and 0xFF)
        _securityCodeFlow.value = List(4) { i ->
            SAS_EMOJIS[(bits shr (18 - i * 6)) and 0x3F]
        }
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
        candidates: List<String> = emptyList()
    ): ByteArray {
        val obj = JSONObject().apply {
            put("type", type)
            put("callId", callId)
            put("senderOnion", senderOnion)
            put("senderNickname", senderNickname)
            put("senderPubKey", senderPubKey)
            put("timestamp", System.currentTimeMillis())
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
        for (i in 0 until arr.length()) {
            val entry = arr.optString(i).trim()
            val lastColon = entry.lastIndexOf(':')
            if (lastColon > 0 && lastColon < entry.length - 1) {
                val host = entry.substring(0, lastColon).trim().removeSurrounding("[", "]").substringBefore('%')
                val port = entry.substring(lastColon + 1).trim().toIntOrNull() ?: continue
                if (host.isNotBlank() && port in 1..65535) {
                    runCatching {
                        val sockAddr = InetSocketAddress(host, port)
                        if (!peerUdpCandidates.contains(sockAddr)) {
                            peerUdpCandidates.add(sockAddr)
                        }
                    }
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
