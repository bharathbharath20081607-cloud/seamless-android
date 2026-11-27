package com.seamless.android.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.seamless.android.crypto.AegisGroupCallCrypto
import com.seamless.android.crypto.AegisTorCrypto
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorCircuitManager
import com.seamless.android.net.TorGroupMessaging
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
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min

enum class GroupCallState {
    IDLE,
    INCOMING_RINGING,
    JOINING,
    ACTIVE,
    ENDED
}

enum class GroupTopologyMode {
    DIRECT_MESH,
    ELECTED_SFU_HUB
}

enum class GroupStageLayoutMode {
    AUTO_SPOTLIGHT,
    EQUAL_GRID
}

data class GroupCallReactionEvent(
    val id: Long,
    val senderOnion: String,
    val senderNickname: String,
    val emoji: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class GroupCallParticipant(
    val onionAddress: String,
    val senderTag4: Int,
    val nickname: String,
    val publicKey: String = "",
    val isLocal: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val isHandRaised: Boolean = false,
    val voiceLevel: Float = 0f,
    val rttMs: Int = 0,
    val transportMode: CallTransportMode = CallTransportMode.TOR_ONION,
    val hasSenderKeyForEpoch: Boolean = false,
    val videoAspectRatio: Float = 9f / 16f,
    val joinedAtMs: Long = System.currentTimeMillis(),
    val localVolumeGain: Float = 1.0f,
    val lastReactionEmoji: String? = null,
    val lastReactionAtMs: Long = 0L,
    val isVideoSubscribed: Boolean = true
)

data class ActiveGroupRoomBanner(
    val groupId: String,
    val groupName: String,
    val callId: String,
    val initiatorNickname: String,
    val isVideo: Boolean,
    val participantCount: Int,
    val startedAtMs: Long,
    val lastUpdatedAtMs: Long = startedAtMs
)

data class GroupCallSessionInfo(
    val callId: String,
    val groupId: String,
    val groupName: String,
    val state: GroupCallState,
    val epoch: Int = 1,
    val topologyMode: GroupTopologyMode = GroupTopologyMode.DIRECT_MESH,
    val electedHubOnion: String? = null,
    val standbyHubOnion: String? = null,
    val amIElectedHub: Boolean = false,
    val forceTorOnly: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerphone: Boolean = true,
    val audioRoute: AudioRoute = AudioRoute.SPEAKER,
    val availableAudioRoutes: Set<AudioRoute> = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    val isLocalVideoEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isHandRaised: Boolean = false,
    val isMinimized: Boolean = false,
    val activeSpeakerOnion: String? = null,
    val pinnedSpeakerOnion: String? = null,
    val securityEmojis: List<String> = emptyList(),
    val startedAtMs: Long = System.currentTimeMillis(),
    val connectedAtMs: Long? = null,
    val durationSeconds: Long = 0L,
    val participants: List<GroupCallParticipant> = emptyList(),
    val statusMessage: String = "",
    val isPushToTalkActive: Boolean = false,
    val layoutMode: GroupStageLayoutMode = GroupStageLayoutMode.AUTO_SPOTLIGHT,
    val medianRttMs: Int = 0,
    val directPeersCount: Int = 0,
    val torPeersCount: Int = 0
)

/**
 * Aegis Swarm Conference™ Coordinator (2–12 Participants).
 *
 * Combines:
 * - RFC 9605 SFrame + Hard Epoch Rotation (`AegisGroupCallCrypto`)
 * - Single Low-Latency Android AudioTrack + NetEq Per-Peer Mixer (`GroupCallAudioMixer`)
 * - Adaptive Swarm Topology (`DIRECT_MESH` for 2–5 peers, `ELECTED_SFU_HUB` zero-knowledge
 *   SFrame forwarding relay with <800 ms standby failover for 6–12 peers)
 * - Tri-Path Transport (`PHANTOM_LAN` / Direct UDP + Express Audio Tor Lane + Bulk Video/FEC Lane)
 * - Active-Speaker Spotlight & DELTA1 Tile-Delta Group Video
 */
class GroupCallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GroupCallManager"
        const val MAX_GROUP_CALL_PARTICIPANTS = 12
        const val MESH_TO_SFU_THRESHOLD = 6

        const val SCOPE_GROUP_CALL = "GROUP_CALL"
        private const val RING_TIMEOUT_MS = 45_000L
        private const val PEER_STALE_TIMEOUT_MS = 22_000L
        private const val HUB_STALE_TIMEOUT_MS = 6_500L
        private const val BANNER_STALE_MS = 35_000L
        private const val HEARTBEAT_INTERVAL_MS = 3_800L
        private const val ANNOUNCE_REFRESH_MS = 12_000L
        private const val ACTIVE_SPEAKER_HOLD_MS = 1_200L

        private const val UDP_MAGIC_PROBE: Byte = 0x11
        private const val UDP_MAGIC_PROBE_ACK: Byte = 0x12
        private const val UDP_MAGIC_SFRAME: Byte = 0x13
        private const val DIRECT_UDP_STALE_MS = 4_500L
        private const val MAX_UDP_CANDIDATES = 12
        private const val REACTION_TTL_MS = 4_500L
        private val ALLOWED_CALL_REACTIONS = setOf("👍", "❤️", "🔥", "👏", "😂", "🎉")

        data class HubCandidateMetrics(
            val onionAddress: String,
            val medianRttMs: Int,
            val directUdpCount: Int,
            val hasCompleteKeys: Boolean,
            val joinedAtMs: Long
        )

        /**
         * Pure filter returning only participants whose screen is actively subscribed to video
         * (skips minimized audio-only listeners to conserve Tor & mobile bandwidth).
         */
        fun filterVideoTargetOnions(candidates: List<Pair<String, Boolean>>): List<String> =
            candidates.mapNotNull { (onion, subscribed) ->
                if (subscribed && onion.isNotBlank()) onion else null
            }

        /**
         * Pure banner pruner removing expired room announcements while always keeping the
         * currently joined group banner intact.
         */
        fun pruneStaleBanners(
            current: Map<String, ActiveGroupRoomBanner>,
            activeSessionGroupId: String?,
            nowMs: Long,
            maxAgeMs: Long = BANNER_STALE_MS
        ): Map<String, ActiveGroupRoomBanner> = current.filter { (gid, banner) ->
            gid == activeSessionGroupId || (nowMs - banner.lastUpdatedAtMs) <= maxAgeMs
        }

        /**
         * Pure deterministic SFU Hub + Standby Hub election across room participants.
         * Prefers nodes with complete keys, lowest median RTT + Direct UDP capability,
         * with canonical onion address tie-breaking so every node converges identically.
         */
        fun electSwarmHubs(candidates: List<HubCandidateMetrics>): Pair<String?, String?> {
            if (candidates.isEmpty()) return null to null
            val sorted = candidates.sortedWith(
                compareByDescending<HubCandidateMetrics> { it.hasCompleteKeys }
                    .thenBy { it.medianRttMs.coerceAtLeast(1) + if (it.directUdpCount > 0) 0 else 80 }
                    .thenBy { it.joinedAtMs }
                    .thenBy { AegisGroupCallCrypto.canonicalizeOnion(it.onionAddress) }
            )
            val primary = sorted.getOrNull(0)?.onionAddress
            val standby = sorted.getOrNull(1)?.onionAddress
            return primary to standby
        }

        @Volatile
        private var instance: GroupCallManager? = null

        fun getInstance(context: Context): GroupCallManager = instance ?: synchronized(this) {
            instance ?: GroupCallManager(context.applicationContext).also { instance = it }
        }
    }

    private data class PeerTransportState(
        val onionAddress: String,
        val senderTag4: Int,
        var nickname: String,
        var publicKey: String = "",
        var pinnedSigningPubKey: String? = null,
        var isMuted: Boolean = false,
        var isVideoEnabled: Boolean = false,
        var isHandRaised: Boolean = false,
        @Volatile var isVideoSubscribed: Boolean = true,
        @Volatile var localVolumeGain: Float = 1.0f,
        var lastReactionEmoji: String? = null,
        var lastReactionAtMs: Long = 0L,
        var lastReactionSignalSentAtMs: Long = 0L,
        var voiceLevel: Float = 0f,
        var rttMs: Int = 180,
        var joinedAtMs: Long = System.currentTimeMillis(),
        var lastSeenAtMs: Long = System.currentTimeMillis(),
        val udpCandidates: CopyOnWriteArrayList<InetSocketAddress> = CopyOnWriteArrayList(),
        @Volatile var verifiedUdpEndpoint: InetSocketAddress? = null,
        @Volatile var lastUdpVerifiedAtMs: Long = 0L,
        var fecEncoder: CallFluxFec.Encoder? = null
    )

    private class PeerVideoDecoder(
        val onionAddress: String,
        val senderTag4: Int,
        private val onRequestPli: (String) -> Unit,
        private val onDecodedBitmap: (String, Bitmap, Float) -> Unit
    ) {
        private var deltaBaseBitmap: Bitmap? = null
        private var deltaBaseCanvas: Canvas? = null

        val reassembler = CallVideoEngine.VideoFrameReassembler(
            maxInFlightFrames = 6,
            frameTimeoutMs = 950L,
            pliMinIntervalMs = 450L,
            onRequestKeyframe = { onRequestPli(onionAddress) }
        )

        fun onFragment(fragment: ByteArray) {
            val complete = reassembler.onFragment(fragment) ?: return
            decodeReassembledFrame(complete)
        }

        private fun decodeReassembledFrame(frame: CallVideoEngine.ReassembledFrame) {
            try {
                val isDelta = (frame.flags and CallVideoEngine.FLAG_TILE_DELTA) != 0
                val rot = ((frame.rotationDeg % 360) + 360) % 360
                val rawOut: Bitmap
                if (isDelta) {
                    if (frame.data.size <= 12) return
                    val bb = ByteBuffer.wrap(frame.data)
                    val magic = bb.int
                    val tileMask = bb.int
                    val fullW = bb.short.toInt() and 0xFFFF
                    val fullH = bb.short.toInt() and 0xFFFF
                    if (magic != 0x444C5431 || fullW <= 0 || fullH <= 0) return

                    val base = deltaBaseBitmap
                    val canvas = deltaBaseCanvas
                    if (base == null || canvas == null || base.isRecycled || base.width != fullW || base.height != fullH) {
                        onRequestPli(onionAddress)
                        return
                    }
                    val stripOpts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val stripBmp = BitmapFactory.decodeByteArray(
                        frame.data, 12, frame.data.size - 12, stripOpts
                    ) ?: return
                    try {
                        val cols = 8
                        val rows = 4
                        val tileW = fullW / cols
                        val tileH = fullH / rows
                        var slot = 0
                        for (bit in 0 until (cols * rows)) {
                            if ((tileMask and (1 shl bit)) == 0) continue
                            val col = bit % cols
                            val row = bit / cols
                            val srcTop = slot * tileH
                            if (srcTop + tileH > stripBmp.height) break
                            val srcRect = Rect(0, srcTop, min(tileW, stripBmp.width), srcTop + tileH)
                            val dstLeft = col * tileW
                            val dstTop = row * tileH
                            val dstRect = Rect(dstLeft, dstTop, dstLeft + tileW, dstTop + tileH)
                            canvas.drawBitmap(stripBmp, srcRect, dstRect, null)
                            slot++
                        }
                    } finally {
                        stripBmp.recycle()
                    }
                    rawOut = if (rot != 0) {
                        val m = Matrix().apply { postRotate(rot.toFloat()) }
                        Bitmap.createBitmap(base, 0, 0, base.width, base.height, m, true)
                    } else {
                        base.copy(Bitmap.Config.RGB_565, false)
                    }
                } else {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    val bmp = BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size, opts) ?: return
                    if (bmp.width in 16..1280 && bmp.height in 16..1280) {
                        var base = deltaBaseBitmap
                        if (base == null || base.isRecycled || base.width != bmp.width || base.height != bmp.height) {
                            base?.recycle()
                            base = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.RGB_565)
                            deltaBaseBitmap = base
                            deltaBaseCanvas = Canvas(base)
                        }
                        deltaBaseCanvas?.drawBitmap(bmp, 0f, 0f, null)
                    }
                    rawOut = if (rot != 0) {
                        val m = Matrix().apply { postRotate(rot.toFloat()) }
                        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also {
                            if (it !== bmp) bmp.recycle()
                        }
                    } else {
                        bmp
                    }
                }
                val aspect = rawOut.width.toFloat() / rawOut.height.toFloat().coerceAtLeast(1f)
                onDecodedBitmap(onionAddress, rawOut, aspect)
            } catch (e: Exception) {
                Log.w(TAG, "Peer video decode skipped: ${e.message}")
            }
        }

        fun release() {
            reassembler.reset()
            deltaBaseBitmap?.recycle()
            deltaBaseBitmap = null
            deltaBaseCanvas = null
        }
    }

    private data class OutboundTorItem(
        val recipients: List<String>,
        val sequence: Long,
        val timestampMs: Long,
        val payload: ByteArray,
        val isFecParity: Boolean = false
    )

    private data class OutboundTorVideoItem(
        val recipients: List<String>,
        val frameSeq: Long,
        val timestampMs: Long,
        val sealedFragments: List<ByteArray>
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val callMutex = Mutex()
    private val secureRandom = SecureRandom()

    private val _sessionFlow = MutableStateFlow<GroupCallSessionInfo?>(null)
    val sessionFlow: StateFlow<GroupCallSessionInfo?> = _sessionFlow.asStateFlow()

    private val _activeRoomsByGroupFlow = MutableStateFlow<Map<String, ActiveGroupRoomBanner>>(emptyMap())
    val activeRoomsByGroupFlow: StateFlow<Map<String, ActiveGroupRoomBanner>> = _activeRoomsByGroupFlow.asStateFlow()

    private val _participantVideoBitmapsFlow = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val participantVideoBitmapsFlow: StateFlow<Map<String, Bitmap>> = _participantVideoBitmapsFlow.asStateFlow()

    private val _localVoiceLevelFlow = MutableStateFlow(0f)
    val localVoiceLevelFlow: StateFlow<Float> = _localVoiceLevelFlow.asStateFlow()

    private val _reactionEventsFlow = MutableStateFlow<List<GroupCallReactionEvent>>(emptyList())
    val reactionEventsFlow: StateFlow<List<GroupCallReactionEvent>> = _reactionEventsFlow.asStateFlow()

    private val reactionSeq = AtomicLong(0L)
    private var lastLocalReactionAtMs = 0L
    private var localReactionEmoji: String? = null
    private var localReactionAtMs: Long = 0L
    private var wasMutedBeforePtt = false
    private var consecutiveSpeakingHandFrames = 0

    val localVideoEngine: CallVideoEngine by lazy {
        CallVideoEngine(
            context = context,
            onOutboundFragment = { _, _, _, _ -> },
            onRequestRemoteKeyframe = { requestKeyframeFromVisibleVideoPeers() },
            onOutboundFrame = { seq, ts, fragments -> dispatchOutboundVideoFrame(seq, ts, fragments) }
        )
    }

    private var audioMixer: GroupCallAudioMixer? = null
    private val sharedFecEncoder = CallFluxFec.Encoder()

    private var roomCrypto: AegisGroupCallCrypto.GroupCryptoRoomSession? = null
    private val peersByOnion = ConcurrentHashMap<String, PeerTransportState>()
    private val peerByTag = ConcurrentHashMap<Int, PeerTransportState>()
    private val peerVideoDecoders = ConcurrentHashMap<Int, PeerVideoDecoder>()

    private var udpSocket: DatagramSocket? = null
    private val localUdpCandidates = CopyOnWriteArrayList<String>()
    private val pendingStunTxIds = ConcurrentHashMap<String, Long>()
    private val p2pBurstCounter = AtomicInteger(0)

    private var audioOutboundSeq = AtomicLong(0L)
    private var torSenderJob: Job? = null
    private var heartbeatJob: Job? = null
    private var timerJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var p2pProbeJob: Job? = null
    private var p2pRecvJob: Job? = null
    private var stunJob: Job? = null
    private var voiceLevelsJob: Job? = null
    private var wasVideoPausedByBg = false
    private var ringtone: Ringtone? = null

    private var lastActiveSpeakerSwitchAtMs = 0L
    private var lastActiveSpeakerOnion: String? = null

    private val torAudioChannel = Channel<OutboundTorItem>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val torVideoChannel = Channel<OutboundTorVideoItem>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun hasActiveCall(): Boolean {
        val s = _sessionFlow.value ?: return false
        return s.state != GroupCallState.IDLE && s.state != GroupCallState.ENDED
    }

    fun startOrJoinGroupCall(
        groupId: String,
        isVideo: Boolean = false,
        forceTorOnly: Boolean = false
    ) {
        scope.launch {
            callMutex.withLock {
                val existing = _sessionFlow.value
                if (existing != null && existing.state != GroupCallState.IDLE && existing.state != GroupCallState.ENDED) {
                    if (existing.groupId == groupId) {
                        _sessionFlow.update { it?.copy(isMinimized = false) }
                        if (existing.state == GroupCallState.INCOMING_RINGING) {
                            launch { acceptIncomingGroupCall(withVideo = isVideo) }
                        }
                    }
                    return@withLock
                }
                if (SeamlessCallManager.getInstance(context).sessionFlow.value?.let {
                        it.state != CallState.IDLE && it.state != CallState.ENDED
                    } == true
                ) {
                    Log.w(TAG, "Cannot start group call while a 1-to-1 call is active")
                    return@withLock
                }

                val groupService = TorGroupMessaging.getInstance(context)
                val messaging = TorMessagingService.getInstance(context)
                val me = messaging.getMyContactInfo()
                val myCanonical = canonical(me.onionAddress)
                if (myCanonical.isBlank()) return@withLock

                val verifiedMembers = groupService.getVerifiedCallMembers(groupId)
                val groupName = groupService.getGroupName(groupId) ?: "Private Group"
                val existingBanner = _activeRoomsByGroupFlow.value[groupId]
                    ?.takeIf { System.currentTimeMillis() - it.startedAtMs < BANNER_STALE_MS * 10L }

                val callId = existingBanner?.callId ?: UUID.randomUUID().toString()
                val isJoiningExisting = existingBanner != null
                val effectiveForceTor = forceTorOnly || PrivacyPreferenceManager.alwaysRouteCallsOverTor.value

                cleanupLocked()
                val myTag = AegisGroupCallCrypto.senderTag4(myCanonical)
                val crypto = AegisGroupCallCrypto.GroupCryptoRoomSession(groupId, callId, myCanonical)
                crypto.ensureLocalEpochKey(1)
                roomCrypto = crypto

                val localParticipant = GroupCallParticipant(
                    onionAddress = myCanonical,
                    senderTag4 = myTag,
                    nickname = me.nickname.ifBlank { "You" },
                    publicKey = me.publicKey,
                    isLocal = true,
                    isMuted = false,
                    isVideoEnabled = isVideo,
                    hasSenderKeyForEpoch = true
                )

                val now = System.currentTimeMillis()
                val session = GroupCallSessionInfo(
                    callId = callId,
                    groupId = groupId,
                    groupName = groupName,
                    state = GroupCallState.ACTIVE,
                    epoch = 1,
                    topologyMode = GroupTopologyMode.DIRECT_MESH,
                    forceTorOnly = effectiveForceTor,
                    isMuted = false,
                    isSpeakerphone = true,
                    audioRoute = AudioRoute.SPEAKER,
                    isLocalVideoEnabled = isVideo,
                    isFrontCamera = true,
                    startedAtMs = existingBanner?.startedAtMs ?: now,
                    connectedAtMs = now,
                    participants = listOf(localParticipant),
                    statusMessage = if (effectiveForceTor) {
                        "Encrypted Group • Tor Onion Only"
                    } else {
                        "Encrypted Group • Swarm Mesh"
                    }
                )
                _sessionFlow.value = session
                _activeRoomsByGroupFlow.update { map ->
                    map + (groupId to ActiveGroupRoomBanner(
                        groupId = groupId,
                        groupName = groupName,
                        callId = callId,
                        initiatorNickname = existingBanner?.initiatorNickname ?: me.nickname,
                        isVideo = isVideo,
                        participantCount = (existingBanner?.participantCount ?: 0).coerceAtLeast(1),
                        startedAtMs = session.startedAtMs
                    ))
                }

                CallForegroundService.start(context)
                groupService.speculativeWarmGroupCircuits(groupId, includeBulk = true)
                startAudioPipelineLocked(isVideo)
                startVideoPipelineIfNeededLocked(isVideo, frontCam = true, forceTor = effectiveForceTor)

                if (!effectiveForceTor) {
                    openDirectUdpSocket(callId)
                    startUdpProbingLoop(callId)
                }

                startTorMediaSenderLoop(callId)
                startCallTimerAndHousekeeping(callId)
                updateRoomTopologyAndEmojisLocked()

                // Announce room or join + broadcast SenderKey to verified group members
                launch {
                    if (messaging.serviceState.value == TorMessagingService.ServiceState.STOPPED) {
                        TorForegroundService.start(context)
                        messaging.start()
                    }
                    val otherMembers = verifiedMembers
                        .filter { canonical(it.onionAddress) != myCanonical }
                        .take(MAX_GROUP_CALL_PARTICIPANTS - 1)

                    if (!isJoiningExisting) {
                        groupService.recordGroupCallTimelineEvent(
                            groupId,
                            if (isVideo) "📹 Group video call started" else "📞 Group voice call started"
                        )
                    }

                    val mySeed = crypto.ensureLocalEpochKey(1)
                    val announceType = if (isJoiningExisting) "G_ROOM_JOIN" else "G_ROOM_ANNOUNCE"
                    otherMembers.forEach { m ->
                        launch {
                            sendGroupSignal(
                                targetOnion = m.onionAddress,
                                type = announceType,
                                groupId = groupId,
                                callId = callId,
                                extras = mapOf(
                                    "groupName" to groupName,
                                    "isVideo" to isVideo,
                                    "initialRing" to !isJoiningExisting,
                                    "epoch" to 1,
                                    "candidates" to if (effectiveForceTor) emptyList<String>() else localUdpCandidates.toList()
                                )
                            )
                            sendSenderKeyPacket(m.onionAddress, groupId, callId, 1, mySeed)
                        }
                    }
                }
            }
        }
    }

    fun acceptIncomingGroupCall(withVideo: Boolean? = null) {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                if (current.state != GroupCallState.INCOMING_RINGING) return@withLock
                ringTimeoutJob?.cancel()
                stopRingingAndVibration()
                delay(60L)

                val enableVideo = withVideo ?: current.isLocalVideoEnabled
                val now = System.currentTimeMillis()
                _sessionFlow.update { s ->
                    s?.copy(
                        state = GroupCallState.ACTIVE,
                        connectedAtMs = now,
                        durationSeconds = 0L,
                        isLocalVideoEnabled = enableVideo,
                        isSpeakerphone = true,
                        statusMessage = if (s.forceTorOnly) "Encrypted Group • Tor Onion Only" else "Encrypted Group • Swarm Mesh"
                    )
                }

                val crypto = roomCrypto ?: AegisGroupCallCrypto.GroupCryptoRoomSession(
                    current.groupId,
                    current.callId,
                    canonical(TorMessagingService.getInstance(context).getMyContactInfo().onionAddress)
                ).also { roomCrypto = it }
                val mySeed = crypto.ensureLocalEpochKey(current.epoch)

                TorGroupMessaging.getInstance(context).speculativeWarmGroupCircuits(current.groupId, includeBulk = true)
                startAudioPipelineLocked(enableVideo)
                startVideoPipelineIfNeededLocked(enableVideo, current.isFrontCamera, current.forceTorOnly)

                if (!current.forceTorOnly) {
                    openDirectUdpSocket(current.callId)
                    startUdpProbingLoop(current.callId)
                }
                startTorMediaSenderLoop(current.callId)
                startCallTimerAndHousekeeping(current.callId)
                updateRoomTopologyAndEmojisLocked()

                val mySeedCopy = mySeed.copyOf()
                launch {
                    val activePeers = peersByOnion.keys.toList()
                    val allVerified = TorGroupMessaging.getInstance(context)
                        .getVerifiedCallMembers(current.groupId)
                        .map { canonical(it.onionAddress) }
                    val targets = (activePeers + allVerified).distinct()
                        .filter { it != crypto.localOnion }
                        .take(MAX_GROUP_CALL_PARTICIPANTS - 1)
                    targets.forEach { target ->
                        launch {
                            sendGroupSignal(
                                targetOnion = target,
                                type = "G_ROOM_JOIN",
                                groupId = current.groupId,
                                callId = current.callId,
                                extras = mapOf(
                                    "groupName" to current.groupName,
                                    "isVideo" to enableVideo,
                                    "epoch" to current.epoch,
                                    "candidates" to if (current.forceTorOnly) emptyList<String>() else localUdpCandidates.toList()
                                )
                            )
                            sendSenderKeyPacket(target, current.groupId, current.callId, current.epoch, mySeedCopy)
                        }
                    }
                    AegisTorCrypto.zeroize(mySeedCopy)
                }
            }
        }
    }

    fun declineIncomingGroupCall() {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                if (current.state != GroupCallState.INCOMING_RINGING) return@withLock
                cleanupLocked()
                _sessionFlow.value = null
                CallForegroundService.stop(context)
            }
        }
    }

    fun leaveCall() {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                val targets = peersByOnion.keys.toList()
                val duration = current.durationSeconds
                val groupId = current.groupId
                val callId = current.callId
                cleanupLocked()
                if (targets.isEmpty()) {
                    _activeRoomsByGroupFlow.update { it - groupId }
                }
                _sessionFlow.value = current.copy(state = GroupCallState.ENDED, statusMessage = "Left group call")
                launch {
                    targets.forEach { target ->
                        sendGroupSignal(
                            targetOnion = target,
                            type = "G_ROOM_LEAVE",
                            groupId = groupId,
                            callId = callId
                        )
                    }
                }
                if (duration > 0L) {
                    launch {
                        TorGroupMessaging.getInstance(context).recordGroupCallTimelineEvent(
                            groupId,
                            "📞 Group call left (${formatDurationShort(duration)})"
                        )
                    }
                }
                launch {
                    delay(1_100L)
                    callMutex.withLock {
                        if (_sessionFlow.value?.callId == callId) {
                            _sessionFlow.value = null
                            CallForegroundService.stop(context)
                        }
                    }
                }
            }
        }
    }

    fun endCallForAll() {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                val isAdmin = TorGroupMessaging.getInstance(context).isLocalAdmin(current.groupId)
                if (!isAdmin) {
                    launch { leaveCall() }
                    return@withLock
                }
                val targets = (peersByOnion.keys.toList() + TorGroupMessaging.getInstance(context)
                    .getVerifiedCallMembers(current.groupId).map { canonical(it.onionAddress) }).distinct()
                    .filter { it != roomCrypto?.localOnion }
                val groupId = current.groupId
                val callId = current.callId
                val duration = current.durationSeconds
                cleanupLocked()
                _activeRoomsByGroupFlow.update { it - groupId }
                _sessionFlow.value = current.copy(state = GroupCallState.ENDED, statusMessage = "Group call ended")
                launch {
                    targets.forEach { target ->
                        sendGroupSignal(
                            targetOnion = target,
                            type = "G_END_FOR_ALL",
                            groupId = groupId,
                            callId = callId
                        )
                    }
                    TorGroupMessaging.getInstance(context).recordGroupCallTimelineEvent(
                        groupId,
                        "📞 Group call ended (${formatDurationShort(duration)})"
                    )
                }
                launch {
                    delay(1_100L)
                    callMutex.withLock {
                        if (_sessionFlow.value?.callId == callId) {
                            _sessionFlow.value = null
                            CallForegroundService.stop(context)
                        }
                    }
                }
            }
        }
    }

    fun toggleMute() {
        val current = _sessionFlow.value ?: return
        val nextMuted = !current.isMuted
        audioMixer?.setMuted(nextMuted)
        _sessionFlow.update { it?.copy(isMuted = nextMuted) }
        broadcastMediaState(current)
    }

    fun toggleSpeakerphone() {
        val mixer = audioMixer ?: return
        val nextRoute = if (mixer.audioRoute.value == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
        mixer.setAudioRoute(nextRoute)
        _sessionFlow.update {
            it?.copy(
                audioRoute = nextRoute,
                isSpeakerphone = nextRoute == AudioRoute.SPEAKER
            )
        }
    }

    fun cycleAudioRoute() {
        val mixer = audioMixer ?: return
        val nextRoute = mixer.cycleAudioRoute()
        _sessionFlow.update {
            it?.copy(
                audioRoute = nextRoute,
                availableAudioRoutes = mixer.availableRoutes.value,
                isSpeakerphone = nextRoute == AudioRoute.SPEAKER
            )
        }
    }

    fun toggleLocalVideo(explicitEnabled: Boolean? = null) {
        val current = _sessionFlow.value ?: return
        if (current.state != GroupCallState.ACTIVE) return
        val nextEnabled = explicitEnabled ?: !current.isLocalVideoEnabled
        localVideoEngine.configureSession(
            isCallActive = true,
            isLocalEnabled = nextEnabled,
            isFrontCam = current.isFrontCamera,
            isDirectP2p = !current.forceTorOnly && peersByOnion.values.any { it.verifiedUdpEndpoint != null },
            codec = CallVideoEngine.CODEC_DELTA_PULSE
        )
        val updated = current.copy(isLocalVideoEnabled = nextEnabled)
        _sessionFlow.value = updated
        broadcastMediaState(updated)
    }

    fun switchCamera() {
        val current = _sessionFlow.value ?: return
        val nextFront = !current.isFrontCamera
        _sessionFlow.update { it?.copy(isFrontCamera = nextFront) }
        localVideoEngine.switchCamera(nextFront)
        if (current.isLocalVideoEnabled) {
            scope.launch {
                delay(300L)
                localVideoEngine.requestImmediateKeyframe()
            }
        }
    }

    fun toggleHandRaised() {
        val current = _sessionFlow.value ?: return
        val nextHand = !current.isHandRaised
        consecutiveSpeakingHandFrames = 0
        val updated = current.copy(isHandRaised = nextHand)
        _sessionFlow.value = updated
        broadcastMediaState(updated)
    }

    fun setPushToTalkPressed(pressed: Boolean) {
        val current = _sessionFlow.value ?: return
        if (current.state != GroupCallState.ACTIVE) return
        if (pressed) {
            if (current.isMuted && !current.isPushToTalkActive) {
                wasMutedBeforePtt = true
                audioMixer?.setMuted(false)
                val updated = current.copy(isMuted = false, isPushToTalkActive = true)
                _sessionFlow.value = updated
                broadcastMediaState(updated)
            }
        } else if (wasMutedBeforePtt || current.isPushToTalkActive) {
            wasMutedBeforePtt = false
            audioMixer?.setMuted(true)
            val updated = current.copy(isMuted = true, isPushToTalkActive = false)
            _sessionFlow.value = updated
            broadcastMediaState(updated)
        }
    }

    fun toggleStageLayoutMode() {
        _sessionFlow.update { s ->
            if (s == null) null
            else s.copy(
                layoutMode = if (s.layoutMode == GroupStageLayoutMode.AUTO_SPOTLIGHT) {
                    GroupStageLayoutMode.EQUAL_GRID
                } else {
                    GroupStageLayoutMode.AUTO_SPOTLIGHT
                }
            )
        }
    }

    fun setParticipantVolume(onionAddress: String, gain: Float) {
        val canon = canonical(onionAddress)
        val clamped = gain.coerceIn(0f, 2.0f)
        val peer = peersByOnion[canon] ?: return
        peer.localVolumeGain = clamped
        audioMixer?.setPeerVolumeGain(peer.senderTag4, clamped)
        _sessionFlow.update { s ->
            s?.copy(
                participants = s.participants.map { p ->
                    if (p.onionAddress == canon) p.copy(localVolumeGain = clamped) else p
                }
            )
        }
    }

    fun sendCallReaction(emoji: String) {
        if (emoji !in ALLOWED_CALL_REACTIONS) return
        val current = _sessionFlow.value ?: return
        if (current.state != GroupCallState.ACTIVE) return
        val now = System.currentTimeMillis()
        if (now - lastLocalReactionAtMs < 350L) return
        lastLocalReactionAtMs = now
        localReactionEmoji = emoji
        localReactionAtMs = now

        val localP = current.participants.firstOrNull { it.isLocal }
        val ev = GroupCallReactionEvent(
            id = reactionSeq.incrementAndGet(),
            senderOnion = localP?.onionAddress ?: (roomCrypto?.localOnion ?: "local"),
            senderNickname = localP?.nickname ?: "You",
            emoji = emoji,
            timestampMs = now
        )
        appendReactionEvent(ev)
        _sessionFlow.update { s ->
            s?.copy(
                participants = s.participants.map { p ->
                    if (p.isLocal) p.copy(lastReactionEmoji = emoji, lastReactionAtMs = now) else p
                }
            )
        }

        val peers = peersByOnion.keys.toList()
        scope.launch {
            peers.forEach { target ->
                sendGroupSignal(
                    targetOnion = target,
                    type = "G_REACTION",
                    groupId = current.groupId,
                    callId = current.callId,
                    extras = mapOf("emoji" to emoji)
                )
            }
        }
    }

    private fun appendReactionEvent(ev: GroupCallReactionEvent) {
        val now = System.currentTimeMillis()
        _reactionEventsFlow.update { list ->
            (list.filter { now - it.timestampMs <= REACTION_TTL_MS } + ev).takeLast(14)
        }
    }

    fun toggleForceTorOnly() {
        val current = _sessionFlow.value ?: return
        val nextForceTor = !current.forceTorOnly
        scope.launch {
            callMutex.withLock {
                if (nextForceTor) {
                    closeUdpLocked()
                    peersByOnion.values.forEach {
                        it.verifiedUdpEndpoint = null
                        it.udpCandidates.clear()
                        audioMixer?.updatePeerTransport(it.senderTag4, false)
                    }
                } else {
                    openDirectUdpSocket(current.callId)
                    startUdpProbingLoop(current.callId)
                }
                _sessionFlow.update {
                    it?.copy(
                        forceTorOnly = nextForceTor,
                        statusMessage = if (nextForceTor) "Encrypted Group • Tor Onion Only" else "Encrypted Group • Hybrid Swarm"
                    )
                }
                updateRoomTopologyAndEmojisLocked()
            }
        }
    }

    fun pinSpeaker(onionAddress: String?) {
        _sessionFlow.update { s ->
            if (s == null) return@update null
            val nextPin = if (onionAddress != null && s.pinnedSpeakerOnion == onionAddress) null else onionAddress
            s.copy(pinnedSpeakerOnion = nextPin)
        }
    }

    fun setMinimized(minimized: Boolean) {
        val prev = _sessionFlow.value ?: return
        if (prev.isMinimized == minimized) return
        val updated = prev.copy(isMinimized = minimized)
        _sessionFlow.value = updated
        broadcastMediaState(updated)
        if (!minimized) {
            requestKeyframeFromVisibleVideoPeers()
        }
    }

    fun refreshRoutes() {
        val current = _sessionFlow.value ?: return
        if (current.state != GroupCallState.ACTIVE || current.forceTorOnly) return
        scope.launch {
            callMutex.withLock {
                peersByOnion.values.forEach { it.verifiedUdpEndpoint = null }
                closeUdpLocked()
                openDirectUdpSocket(current.callId)
                startUdpProbingLoop(current.callId)
            }
        }
    }

    fun onAppForegroundStateChanged(inForeground: Boolean) {
        val current = _sessionFlow.value ?: return
        if (current.state != GroupCallState.ACTIVE) return
        if (!inForeground && current.isLocalVideoEnabled) {
            wasVideoPausedByBg = true
            toggleLocalVideo(false)
        } else if (inForeground && wasVideoPausedByBg) {
            wasVideoPausedByBg = false
            if (!current.isLocalVideoEnabled) {
                toggleLocalVideo(true)
            }
        }
    }

    fun onGroupMembershipChanged(groupId: String) {
        scope.launch {
            callMutex.withLock {
                val current = _sessionFlow.value ?: return@withLock
                if (current.groupId != groupId || current.state != GroupCallState.ACTIVE) return@withLock
                val groupService = TorGroupMessaging.getInstance(context)
                val myCanonical = roomCrypto?.localOnion ?: return@withLock
                val validOnions = groupService.getVerifiedCallMembers(groupId)
                    .mapTo(HashSet()) { canonical(it.onionAddress) }
                if (myCanonical !in validOnions) {
                    launch { leaveCall() }
                    return@withLock
                }
                val evicted = peersByOnion.keys.filter { it !in validOnions }
                if (evicted.isNotEmpty()) {
                    evicted.forEach { rem ->
                        val peer = peersByOnion.remove(rem)
                        if (peer != null) {
                            peerByTag.remove(peer.senderTag4)
                            audioMixer?.removePeer(peer.senderTag4)
                            peerVideoDecoders.remove(peer.senderTag4)?.release()
                            _participantVideoBitmapsFlow.update { it - rem }
                        }
                    }
                    rotateRoomEpochAndDistributeLocked(retainPreviousGraceMs = 0L)
                }
            }
        }
    }

    // ───────────────────────────── Inbound Signaling ─────────────────────────────

    fun onIncomingGroupCallSignal(senderOnion: String, json: JSONObject) {
        val type = json.optString("type")
        val groupId = json.optString("groupId")
        val callId = json.optString("callId")
        if (type.isBlank() || groupId.isBlank() || callId.isBlank()) return
        val canonicalSender = canonical(senderOnion)
        if (canonicalSender.isBlank()) return

        scope.launch {
            val groupService = TorGroupMessaging.getInstance(context)
            if (!groupService.isVerifiedGroupMemberForCall(groupId, canonicalSender)) {
                Log.w(TAG, "Rejected spoofed group call signal ($type) from non-member")
                return@launch
            }
            val verifiedMembers = groupService.getVerifiedCallMembers(groupId)
            val senderInfo = verifiedMembers.firstOrNull { canonical(it.onionAddress) == canonicalSender } ?: return@launch
            val groupName = groupService.getGroupName(groupId) ?: json.optString("groupName").ifBlank { "Private Group" }

            callMutex.withLock {
                val now = System.currentTimeMillis()
                val current = _sessionFlow.value
                val me = TorMessagingService.getInstance(context).getMyContactInfo()
                val myCanonical = canonical(me.onionAddress)
                if (canonicalSender == myCanonical) return@withLock

                when (type) {
                    "G_ROOM_ANNOUNCE", "G_ROOM_JOIN" -> {
                        val isVideo = json.optBoolean("isVideo", false)
                        val initialRing = json.optBoolean("initialRing", type == "G_ROOM_ANNOUNCE")
                        val countHint = json.optInt("participantCount", 1).coerceIn(1, MAX_GROUP_CALL_PARTICIPANTS)
                        _activeRoomsByGroupFlow.update { map ->
                            map + (groupId to ActiveGroupRoomBanner(
                                groupId = groupId,
                                groupName = groupName,
                                callId = callId,
                                initiatorNickname = senderInfo.nickname,
                                isVideo = isVideo,
                                participantCount = if (current?.callId == callId) {
                                    (peersByOnion.size + 1).coerceAtLeast(countHint)
                                } else countHint,
                                startedAtMs = map[groupId]?.startedAtMs ?: now
                            ))
                        }

                        if (current == null || current.state == GroupCallState.IDLE || current.state == GroupCallState.ENDED) {
                            val oneToOneBusy = SeamlessCallManager.getInstance(context).sessionFlow.value?.let {
                                it.state != CallState.IDLE && it.state != CallState.ENDED
                            } == true
                            if (!initialRing || oneToOneBusy) return@withLock

                            cleanupLocked()
                            val forceTor = PrivacyPreferenceManager.alwaysRouteCallsOverTor.value
                            val crypto = AegisGroupCallCrypto.GroupCryptoRoomSession(groupId, callId, myCanonical)
                            crypto.ensureLocalEpochKey(1)
                            roomCrypto = crypto

                            addOrUpdatePeerLocked(
                                canonicalSender,
                                senderInfo.nickname,
                                senderInfo.publicKey,
                                senderInfo.pinnedSigningPubKey,
                                isVideo = isVideo,
                                candidates = json.optJSONArray("candidates")
                            )

                            _sessionFlow.value = GroupCallSessionInfo(
                                callId = callId,
                                groupId = groupId,
                                groupName = groupName,
                                state = GroupCallState.INCOMING_RINGING,
                                epoch = 1,
                                forceTorOnly = forceTor,
                                isLocalVideoEnabled = isVideo,
                                isSpeakerphone = true,
                                startedAtMs = now,
                                statusMessage = if (isVideo) "${senderInfo.nickname} started a group video call" else "${senderInfo.nickname} started a group voice call"
                            )
                            updateRoomTopologyAndEmojisLocked()
                            CallForegroundService.start(context)
                            startIncomingRingtoneAndVibration()
                            startIncomingRingTimeout(callId)
                            return@withLock
                        }

                        if (current.groupId != groupId || current.callId != callId) return@withLock
                        val wasKnown = peersByOnion.containsKey(canonicalSender)
                        addOrUpdatePeerLocked(
                            canonicalSender,
                            senderInfo.nickname,
                            senderInfo.publicKey,
                            senderInfo.pinnedSigningPubKey,
                            isVideo = isVideo,
                            candidates = json.optJSONArray("candidates")
                        )

                        if (!wasKnown && current.state == GroupCallState.ACTIVE) {
                            // Zero-drop join epoch grace (1.5s) + redistribute SenderKey to the joining peer
                            val crypto = roomCrypto
                            if (crypto != null) {
                                val nextEp = crypto.currentEpoch + 1
                                val newSeed = crypto.rotateEpoch(nextEp, retainPreviousGraceMs = 1_500L)
                                _sessionFlow.update { it?.copy(epoch = nextEp) }
                                launch {
                                    peersByOnion.keys.toList().forEach { peer ->
                                        sendSenderKeyPacket(peer, groupId, callId, nextEp, newSeed)
                                    }
                                    AegisTorCrypto.zeroize(newSeed)
                                    sendRosterSync(canonicalSender, groupId, callId)
                                }
                            }
                        } else if (current.state == GroupCallState.ACTIVE) {
                            val crypto = roomCrypto
                            val mySeed = crypto?.ensureLocalEpochKey(crypto.currentEpoch)
                            if (crypto != null && mySeed != null) {
                                val ep = crypto.currentEpoch
                                launch {
                                    sendSenderKeyPacket(canonicalSender, groupId, callId, ep, mySeed)
                                    AegisTorCrypto.zeroize(mySeed)
                                }
                            }
                        }
                        updateRoomTopologyAndEmojisLocked()
                    }

                    "G_SENDER_KEY" -> {
                        if (current == null || current.callId != callId || current.groupId != groupId) return@withLock
                        val crypto = roomCrypto ?: return@withLock
                        val ep = json.optInt("epoch", 0)
                        val senderTag4 = json.optInt("senderTag4", 0)
                        val seedHex = json.optString("rootSeedHex")
                        val sigHex = json.optString("sigHex")
                        val sigPub = json.optString("signingPubKey").ifBlank { senderInfo.pinnedSigningPubKey ?: "" }
                        val seedBytes = com.seamless.android.crypto.AegisCallCrypto.fromHex(seedHex) ?: return@withLock
                        if (ep <= 0 || seedBytes.size != AegisGroupCallCrypto.SENDER_SEED_BYTES) return@withLock

                        if (sigPub.isNotBlank() && sigHex.isNotBlank()) {
                            val validSig = AegisGroupCallCrypto.verifySenderKeyAnnouncement(
                                ed25519PublicKeyHex = sigPub,
                                signatureHex = sigHex,
                                groupId = groupId,
                                callId = callId,
                                epoch = ep,
                                senderOnion = canonicalSender,
                                senderTag4 = senderTag4,
                                rootSeed = seedBytes
                            )
                            if (!validSig) {
                                Log.w(TAG, "Rejected invalid Ed25519 signature on G_SENDER_KEY from $canonicalSender")
                                AegisTorCrypto.zeroize(seedBytes)
                                return@withLock
                            }
                        }

                        addOrUpdatePeerLocked(canonicalSender, senderInfo.nickname, senderInfo.publicKey, senderInfo.pinnedSigningPubKey)
                        if (ep > crypto.currentEpoch) {
                            val myNewSeed = crypto.rotateEpoch(ep, retainPreviousGraceMs = 1_500L)
                            _sessionFlow.update { it?.copy(epoch = ep) }
                            val seedCopy = myNewSeed.copyOf()
                            launch {
                                peersByOnion.keys.toList().forEach { peer ->
                                    sendSenderKeyPacket(peer, groupId, callId, ep, seedCopy)
                                }
                                AegisTorCrypto.zeroize(seedCopy)
                            }
                        }
                        crypto.installPeerSenderKey(canonicalSender, ep, seedBytes)
                        AegisTorCrypto.zeroize(seedBytes)
                        updateRoomTopologyAndEmojisLocked()
                    }

                    "G_ROSTER_SYNC" -> {
                        if (current == null || current.callId != callId || current.groupId != groupId) return@withLock
                        val roster = json.optJSONArray("members") ?: return@withLock
                        val ep = current.epoch
                        val mySeed = roomCrypto?.ensureLocalEpochKey(ep)
                        for (i in 0 until min(roster.length(), MAX_GROUP_CALL_PARTICIPANTS)) {
                            val mOnion = canonical(roster.optString(i))
                            if (mOnion.isBlank() || mOnion == myCanonical || peersByOnion.containsKey(mOnion)) continue
                            val mInfo = verifiedMembers.firstOrNull { canonical(it.onionAddress) == mOnion } ?: continue
                            addOrUpdatePeerLocked(mOnion, mInfo.nickname, mInfo.publicKey, mInfo.pinnedSigningPubKey)
                            if (mySeed != null) {
                                val seedCopy = mySeed.copyOf()
                                launch {
                                    sendGroupSignal(
                                        targetOnion = mOnion,
                                        type = "G_ROOM_JOIN",
                                        groupId = groupId,
                                        callId = callId,
                                        extras = mapOf(
                                            "groupName" to current.groupName,
                                            "isVideo" to current.isLocalVideoEnabled,
                                            "epoch" to ep,
                                            "candidates" to if (current.forceTorOnly) emptyList<String>() else localUdpCandidates.toList()
                                        )
                                    )
                                    sendSenderKeyPacket(mOnion, groupId, callId, ep, seedCopy)
                                    AegisTorCrypto.zeroize(seedCopy)
                                }
                            }
                        }
                        if (mySeed != null) AegisTorCrypto.zeroize(mySeed)
                        updateRoomTopologyAndEmojisLocked()
                    }

                    "G_MEDIA_STATE" -> {
                        if (current?.callId != callId) return@withLock
                        val peer = peersByOnion[canonicalSender] ?: return@withLock
                        peer.lastSeenAtMs = now
                        peer.isMuted = json.optBoolean("isMuted", peer.isMuted)
                        peer.isVideoEnabled = json.optBoolean("isVideo", peer.isVideoEnabled)
                        peer.isHandRaised = json.optBoolean("isHandRaised", peer.isHandRaised)
                        peer.isVideoSubscribed = json.optBoolean("videoSubscribed", true)
                        if (!peer.isVideoEnabled) {
                            peerVideoDecoders.remove(peer.senderTag4)?.release()
                            _participantVideoBitmapsFlow.update { it - canonicalSender }
                        }
                        updateRoomTopologyAndEmojisLocked()
                    }

                    "G_REACTION" -> {
                        if (current?.callId != callId) return@withLock
                        val peer = peersByOnion[canonicalSender] ?: return@withLock
                        val emoji = json.optString("emoji")
                        if (emoji !in ALLOWED_CALL_REACTIONS) return@withLock
                        if (now - peer.lastReactionSignalSentAtMs < 300L) return@withLock
                        peer.lastReactionSignalSentAtMs = now
                        peer.lastReactionEmoji = emoji
                        peer.lastReactionAtMs = now
                        peer.lastSeenAtMs = now
                        appendReactionEvent(
                            GroupCallReactionEvent(
                                id = reactionSeq.incrementAndGet(),
                                senderOnion = canonicalSender,
                                senderNickname = peer.nickname,
                                emoji = emoji,
                                timestampMs = now
                            )
                        )
                        updateRoomTopologyAndEmojisLocked()
                    }

                    "G_HEARTBEAT" -> {
                        if (current?.callId != callId) return@withLock
                        val peer = peersByOnion[canonicalSender] ?: return@withLock
                        peer.lastSeenAtMs = now
                        mergeCandidates(peer, json.optJSONArray("candidates"))
                        val sentTs = json.optLong("timestamp", 0L)
                        launch {
                            sendGroupSignal(
                                targetOnion = canonicalSender,
                                type = "G_HEARTBEAT_ACK",
                                groupId = groupId,
                                callId = callId,
                                extras = mapOf("echoTs" to sentTs)
                            )
                        }
                    }

                    "G_HEARTBEAT_ACK" -> {
                        if (current?.callId != callId) return@withLock
                        val peer = peersByOnion[canonicalSender] ?: return@withLock
                        peer.lastSeenAtMs = now
                        val echo = json.optLong("echoTs", 0L)
                        if (echo > 0L) {
                            peer.rttMs = (now - echo).toInt().coerceIn(10, 4_000)
                        }
                        updateRoomTopologyAndEmojisLocked()
                    }

                    "G_VIDEO_PLI" -> {
                        if (current?.callId == callId && current.isLocalVideoEnabled) {
                            localVideoEngine.requestImmediateKeyframe()
                        }
                    }

                    "G_ROOM_LEAVE" -> {
                        val rem = peersByOnion.remove(canonicalSender)
                        if (rem != null) {
                            peerByTag.remove(rem.senderTag4)
                            audioMixer?.removePeer(rem.senderTag4)
                            peerVideoDecoders.remove(rem.senderTag4)?.release()
                            _participantVideoBitmapsFlow.update { it - canonicalSender }
                            if (current?.callId == callId) {
                                // Hard Epoch Rotation immediately locking out departed member!
                                rotateRoomEpochAndDistributeLocked(retainPreviousGraceMs = 0L)
                                updateRoomTopologyAndEmojisLocked()
                            } else if (peersByOnion.isEmpty()) {
                                _activeRoomsByGroupFlow.update { it - groupId }
                            }
                        }
                    }

                    "G_END_FOR_ALL" -> {
                        if (!groupService.isAdmin(groupId, canonicalSender)) return@withLock
                        _activeRoomsByGroupFlow.update { it - groupId }
                        if (current?.callId == callId) {
                            cleanupLocked()
                            _sessionFlow.value = current.copy(state = GroupCallState.ENDED, statusMessage = "Ended by group admin")
                            launch {
                                delay(1_100L)
                                callMutex.withLock {
                                    if (_sessionFlow.value?.callId == callId) {
                                        _sessionFlow.value = null
                                        CallForegroundService.stop(context)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────── Inbound Media (SFrame) ─────────────────────────────

    fun onIncomingGroupCallAudio(
        senderOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ) {
        processIncomingSFrame(senderOnion = canonical(senderOnion), timestampMs = timestampMs, packet = payload, allowRelayForward = true)
    }

    fun onIncomingGroupCallVideo(
        senderOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ) {
        processIncomingSFrame(senderOnion = canonical(senderOnion), timestampMs = timestampMs, packet = payload, allowRelayForward = true)
    }

    private fun processIncomingSFrame(
        senderOnion: String?,
        timestampMs: Long,
        packet: ByteArray,
        allowRelayForward: Boolean,
        fromUdpEndpoint: InetSocketAddress? = null
    ) {
        val current = _sessionFlow.value ?: return
        if (current.state != GroupCallState.ACTIVE) return
        val crypto = roomCrypto ?: return

        val header = AegisGroupCallCrypto.parseHeader(packet) ?: return
        if (header.roomHash8 != crypto.roomHash8) return
        val originPeer = peerByTag[header.senderTag4] ?: return

        originPeer.lastSeenAtMs = System.currentTimeMillis()
        if (fromUdpEndpoint != null) {
            originPeer.verifiedUdpEndpoint = fromUdpEndpoint
            originPeer.lastUdpVerifiedAtMs = originPeer.lastSeenAtMs
            audioMixer?.updatePeerTransport(header.senderTag4, true)
        }

        // Zero-Knowledge SFU Relay: forward raw encrypted SFrame untouched when acting as Hub!
        if (allowRelayForward && current.amIElectedHub && current.topologyMode == GroupTopologyMode.ELECTED_SFU_HUB) {
            forwardSFrameAsZeroKnowledgeHub(originPeer.senderTag4, header.magic, packet)
        }

        val opened = crypto.open(packet) ?: return
        when (opened.header.magic) {
            AegisGroupCallCrypto.GROUP_SFRAME_AUDIO_MAGIC -> {
                audioMixer?.onRemoteAudioFrame(header.senderTag4, header.counter, timestampMs, opened.plaintext)
            }
            AegisGroupCallCrypto.GROUP_SFRAME_AUDIO_FEC_MAGIC -> {
                audioMixer?.onRemoteAudioFecFrame(header.senderTag4, timestampMs, opened.plaintext)
            }
            AegisGroupCallCrypto.GROUP_SFRAME_VIDEO_MAGIC -> {
                val decoder = peerVideoDecoders.getOrPut(header.senderTag4) {
                    PeerVideoDecoder(
                        onionAddress = originPeer.onionAddress,
                        senderTag4 = originPeer.senderTag4,
                        onRequestPli = { target ->
                            scope.launch {
                                sendGroupSignal(target, "G_VIDEO_PLI", current.groupId, current.callId)
                            }
                        },
                        onDecodedBitmap = { onion, bmp, aspect ->
                            peersByOnion[onion]?.isVideoEnabled = true
                            _participantVideoBitmapsFlow.update { it + (onion to bmp) }
                            _sessionFlow.update { s ->
                                if (s == null) null
                                else s.copy(
                                    participants = s.participants.map { p ->
                                        if (p.onionAddress == onion) p.copy(isVideoEnabled = true, videoAspectRatio = aspect)
                                        else p
                                    }
                                )
                            }
                        }
                    )
                }
                decoder.onFragment(opened.plaintext)
            }
        }
    }

    private fun forwardSFrameAsZeroKnowledgeHub(originTag4: Int, magic: Byte, rawSFrame: ByteArray) {
        val targets = peersByOnion.values.filter { it.senderTag4 != originTag4 }
        if (targets.isEmpty()) return
        val now = System.currentTimeMillis()
        val torRecipients = ArrayList<String>(targets.size)
        val forceTor = _sessionFlow.value?.forceTorOnly == true

        for (peer in targets) {
            if (magic == AegisGroupCallCrypto.GROUP_SFRAME_VIDEO_MAGIC && !peer.isVideoSubscribed) {
                continue
            }
            val udpEp = peer.verifiedUdpEndpoint
            if (!forceTor && udpEp != null && (now - peer.lastUdpVerifiedAtMs) <= DIRECT_UDP_STALE_MS) {
                if (!sendUdpSFrame(udpEp, rawSFrame)) {
                    torRecipients.add(peer.onionAddress)
                }
            } else {
                torRecipients.add(peer.onionAddress)
            }
        }
        if (torRecipients.isNotEmpty()) {
            when (magic) {
                AegisGroupCallCrypto.GROUP_SFRAME_AUDIO_MAGIC -> torAudioChannel.trySend(
                    OutboundTorItem(torRecipients, System.nanoTime(), now, rawSFrame, isFecParity = false)
                )
                AegisGroupCallCrypto.GROUP_SFRAME_AUDIO_FEC_MAGIC -> torAudioChannel.trySend(
                    OutboundTorItem(torRecipients, System.nanoTime(), now, rawSFrame, isFecParity = true)
                )
                AegisGroupCallCrypto.GROUP_SFRAME_VIDEO_MAGIC -> torVideoChannel.trySend(
                    OutboundTorVideoItem(torRecipients, System.nanoTime(), now, listOf(rawSFrame))
                )
            }
        }
    }

    // ───────────────────────────── Outbound Media Dispatch ─────────────────────────────

    private fun dispatchOutboundAudioFrame(
        sequence: Long,
        timestampMs: Long,
        isDtxSid: Boolean,
        encodedAdpcm: ByteArray
    ) {
        val current = _sessionFlow.value ?: return
        val crypto = roomCrypto ?: return
        if (current.state != GroupCallState.ACTIVE) return

        val sealedAudio = crypto.sealAudio(encodedAdpcm) ?: return
        val fecParities = if (!isDtxSid) {
            val hasTorPeers = current.forceTorOnly || peersByOnion.values.any { it.verifiedUdpEndpoint == null }
            sharedFecEncoder.onVoiceFrame2D(sequence, encodedAdpcm, fecEnabled = true, burstMode2D = hasTorPeers)
        } else {
            emptyList()
        }
        val sealedFec = fecParities.mapNotNull { crypto.sealAudioFec(it) }

        val targetOnions = targetMediaRecipientOnions(current)
        if (targetOnions.isEmpty()) return

        val now = System.currentTimeMillis()
        val torTargets = ArrayList<String>(targetOnions.size)
        for (onion in targetOnions) {
            val peer = peersByOnion[onion]
            val udpEp = peer?.verifiedUdpEndpoint
            if (!current.forceTorOnly && udpEp != null && (now - peer.lastUdpVerifiedAtMs) <= DIRECT_UDP_STALE_MS) {
                sendUdpSFrame(udpEp, sealedAudio)
                sealedFec.forEach { sendUdpSFrame(udpEp, it) }
            } else {
                torTargets.add(onion)
            }
        }
        if (torTargets.isNotEmpty()) {
            torAudioChannel.trySend(OutboundTorItem(torTargets, sequence, timestampMs, sealedAudio, isFecParity = false))
            sealedFec.forEach { fecPkt ->
                torAudioChannel.trySend(OutboundTorItem(torTargets, sequence, timestampMs, fecPkt, isFecParity = true))
            }
        }
    }

    private fun dispatchOutboundVideoFrame(
        sequence: Long,
        timestampMs: Long,
        fragments: List<ByteArray>
    ) {
        val current = _sessionFlow.value ?: return
        val crypto = roomCrypto ?: return
        if (current.state != GroupCallState.ACTIVE || !current.isLocalVideoEnabled || fragments.isEmpty()) return

        val sealedList = fragments.mapNotNull { crypto.sealVideoFragment(it) }
        if (sealedList.isEmpty()) return
        val rawTargets = targetMediaRecipientOnions(current)
        val targetOnions = if (current.topologyMode == GroupTopologyMode.DIRECT_MESH) {
            filterVideoTargetOnions(
                rawTargets.map { onion -> onion to (peersByOnion[onion]?.isVideoSubscribed ?: true) }
            )
        } else {
            rawTargets
        }
        if (targetOnions.isEmpty()) return

        val now = System.currentTimeMillis()
        val torTargets = ArrayList<String>(targetOnions.size)
        for (onion in targetOnions) {
            val peer = peersByOnion[onion]
            val udpEp = peer?.verifiedUdpEndpoint
            if (!current.forceTorOnly && udpEp != null && (now - peer.lastUdpVerifiedAtMs) <= DIRECT_UDP_STALE_MS) {
                sealedList.forEach { sendUdpSFrame(udpEp, it) }
            } else {
                torTargets.add(onion)
            }
        }
        if (torTargets.isNotEmpty()) {
            torVideoChannel.trySend(OutboundTorVideoItem(torTargets, sequence, timestampMs, sealedList))
        }
    }

    private fun targetMediaRecipientOnions(session: GroupCallSessionInfo): List<String> {
        if (session.topologyMode == GroupTopologyMode.ELECTED_SFU_HUB &&
            !session.amIElectedHub &&
            !session.electedHubOnion.isNullOrBlank() &&
            peersByOnion.containsKey(session.electedHubOnion)
        ) {
            return listOf(session.electedHubOnion)
        }
        return peersByOnion.keys.toList()
    }

    private fun startTorMediaSenderLoop(callId: String) {
        torSenderJob?.cancel()
        while (torAudioChannel.tryReceive().isSuccess) {}
        while (torVideoChannel.tryReceive().isSuccess) {}

        torSenderJob = scope.launch(Dispatchers.IO) {
            val messaging = TorMessagingService.getInstance(context)

            suspend fun sendAudioItem(item: OutboundTorItem) {
                item.recipients.forEach { target ->
                    if (item.isFecParity) {
                        messaging.sendCallAudioFecFrame(target, item.sequence, item.timestampMs, item.payload, preferBulkLane = true)
                    } else {
                        messaging.sendCallAudioFrame(target, item.sequence, item.timestampMs, item.payload)
                    }
                }
            }

            suspend fun drainAudio(): Boolean {
                while (true) {
                    val s = _sessionFlow.value ?: return false
                    if (s.callId != callId || s.state != GroupCallState.ACTIVE) return false
                    val item = torAudioChannel.tryReceive().getOrNull() ?: return true
                    sendAudioItem(item)
                }
            }

            suspend fun sendVideoWithAudioPriority(v: OutboundTorVideoItem) {
                for (recipient in v.recipients) {
                    for (chunk in v.sealedFragments.chunked(4)) {
                        if (!drainAudio()) return
                        messaging.sendCallVideoFramesBurst(recipient, v.frameSeq, v.timestampMs, chunk)
                    }
                }
            }

            while (isActive) {
                val s = _sessionFlow.value ?: break
                if (s.callId != callId || s.state == GroupCallState.IDLE || s.state == GroupCallState.ENDED) break
                if (!drainAudio()) break

                val qv = torVideoChannel.tryReceive().getOrNull()
                if (qv != null) {
                    sendVideoWithAudioPriority(qv)
                    continue
                }
                select<Unit> {
                    torAudioChannel.onReceive { item -> sendAudioItem(item) }
                    torVideoChannel.onReceive { vid -> sendVideoWithAudioPriority(vid) }
                }
            }
        }
    }

    // ───────────────────────────── Internals, Topology & UDP ─────────────────────────────

    private fun startAudioPipelineLocked(isVideo: Boolean) {
        audioMixer?.stop()
        val mixer = GroupCallAudioMixer(context) { seq, ts, isDtxSid, payload ->
            dispatchOutboundAudioFrame(seq, ts, isDtxSid, payload)
        }
        audioMixer = mixer
        mixer.start(initialSpeakerphone = true, initialMuted = false)

        voiceLevelsJob?.cancel()
        voiceLevelsJob = scope.launch {
            launch {
                mixer.localVoiceLevel.collect { lvl ->
                    _localVoiceLevelFlow.value = lvl
                    val s = _sessionFlow.value
                    if (s != null && s.isHandRaised && !s.isMuted && lvl > 0.22f) {
                        consecutiveSpeakingHandFrames++
                        if (consecutiveSpeakingHandFrames >= 4) {
                            consecutiveSpeakingHandFrames = 0
                            val updated = s.copy(isHandRaised = false)
                            _sessionFlow.value = updated
                            broadcastMediaState(updated)
                        }
                    } else if (lvl <= 0.14f) {
                        consecutiveSpeakingHandFrames = 0
                    }
                }
            }
            launch {
                mixer.peerVoiceLevels.collect { lvlMap ->
                    val now = System.currentTimeMillis()
                    var loudestOnion: String? = null
                    var loudestLevel = 0.08f
                    for ((tag, lvl) in lvlMap) {
                        val peer = peerByTag[tag] ?: continue
                        peer.voiceLevel = lvl
                        if (lvl > loudestLevel) {
                            loudestLevel = lvl
                            loudestOnion = peer.onionAddress
                        }
                    }
                    val chosenActive = if (loudestOnion != null &&
                        (lastActiveSpeakerOnion == null || now - lastActiveSpeakerSwitchAtMs >= ACTIVE_SPEAKER_HOLD_MS)
                    ) {
                        if (loudestOnion != lastActiveSpeakerOnion) {
                            lastActiveSpeakerSwitchAtMs = now
                            lastActiveSpeakerOnion = loudestOnion
                        }
                        loudestOnion
                    } else {
                        lastActiveSpeakerOnion
                    }
                    _sessionFlow.update { s ->
                        if (s == null) null
                        else s.copy(
                            activeSpeakerOnion = chosenActive,
                            audioRoute = mixer.audioRoute.value,
                            availableAudioRoutes = mixer.availableRoutes.value,
                            participants = s.participants.map { p ->
                                if (p.isLocal) p.copy(voiceLevel = _localVoiceLevelFlow.value)
                                else p.copy(voiceLevel = lvlMap[p.senderTag4] ?: 0f)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startVideoPipelineIfNeededLocked(enableLocalVideo: Boolean, frontCam: Boolean, forceTor: Boolean) {
        localVideoEngine.configureSession(
            isCallActive = true,
            isLocalEnabled = enableLocalVideo,
            isFrontCam = frontCam,
            isDirectP2p = !forceTor && peersByOnion.values.any { it.verifiedUdpEndpoint != null },
            codec = CallVideoEngine.CODEC_DELTA_PULSE
        )
    }

    private fun rotateRoomEpochAndDistributeLocked(retainPreviousGraceMs: Long) {
        val current = _sessionFlow.value ?: return
        val crypto = roomCrypto ?: return
        val nextEp = crypto.currentEpoch + 1
        val newSeed = crypto.rotateEpoch(nextEp, retainPreviousGraceMs = retainPreviousGraceMs)
        _sessionFlow.update { it?.copy(epoch = nextEp) }
        val targets = peersByOnion.keys.toList()
        val groupId = current.groupId
        val callId = current.callId
        val seedCopy = newSeed.copyOf()
        scope.launch {
            targets.forEach { target ->
                sendSenderKeyPacket(target, groupId, callId, nextEp, seedCopy)
            }
            AegisTorCrypto.zeroize(seedCopy)
        }
    }

    private fun updateRoomTopologyAndEmojisLocked() {
        val current = _sessionFlow.value ?: return
        val crypto = roomCrypto ?: return
        val myCanonical = crypto.localOnion
        val totalCount = peersByOnion.size + 1
        val nextTopo = if (totalCount >= MESH_TO_SFU_THRESHOLD) {
            GroupTopologyMode.ELECTED_SFU_HUB
        } else {
            GroupTopologyMode.DIRECT_MESH
        }

        val now = System.currentTimeMillis()
        val participants = ArrayList<GroupCallParticipant>(totalCount)
        val hubMetrics = ArrayList<HubCandidateMetrics>(totalCount)

        participants.add(
            GroupCallParticipant(
                onionAddress = myCanonical,
                senderTag4 = crypto.localSenderTag4,
                nickname = current.participants.firstOrNull { it.isLocal }?.nickname ?: "You",
                isLocal = true,
                isMuted = current.isMuted,
                isVideoEnabled = current.isLocalVideoEnabled,
                isHandRaised = current.isHandRaised,
                voiceLevel = _localVoiceLevelFlow.value,
                hasSenderKeyForEpoch = true,
                joinedAtMs = current.startedAtMs,
                lastReactionEmoji = localReactionEmoji,
                lastReactionAtMs = localReactionAtMs,
                isVideoSubscribed = !current.isMinimized
            )
        )
        hubMetrics.add(
            HubCandidateMetrics(
                onionAddress = myCanonical,
                medianRttMs = 40,
                directUdpCount = peersByOnion.values.count { it.verifiedUdpEndpoint != null },
                hasCompleteKeys = true,
                joinedAtMs = current.startedAtMs
            )
        )

        var directCount = 0
        var torCount = 0
        val rttSamples = ArrayList<Int>(peersByOnion.size)

        for (p in peersByOnion.values) {
            val isDirect = !current.forceTorOnly &&
                p.verifiedUdpEndpoint != null &&
                (now - p.lastUdpVerifiedAtMs) <= DIRECT_UDP_STALE_MS
            if (isDirect) directCount++ else torCount++
            if (p.rttMs > 0) rttSamples.add(p.rttMs)
            val hasKey = crypto.hasKeyForPeer(p.onionAddress, crypto.currentEpoch)
            participants.add(
                GroupCallParticipant(
                    onionAddress = p.onionAddress,
                    senderTag4 = p.senderTag4,
                    nickname = p.nickname,
                    publicKey = p.publicKey,
                    isLocal = false,
                    isMuted = p.isMuted,
                    isVideoEnabled = p.isVideoEnabled,
                    isHandRaised = p.isHandRaised,
                    voiceLevel = p.voiceLevel,
                    rttMs = p.rttMs,
                    transportMode = if (isDirect) CallTransportMode.DIRECT_P2P else CallTransportMode.TOR_ONION,
                    hasSenderKeyForEpoch = hasKey,
                    joinedAtMs = p.joinedAtMs,
                    localVolumeGain = p.localVolumeGain,
                    lastReactionEmoji = p.lastReactionEmoji,
                    lastReactionAtMs = p.lastReactionAtMs,
                    isVideoSubscribed = p.isVideoSubscribed
                )
            )
            hubMetrics.add(
                HubCandidateMetrics(
                    onionAddress = p.onionAddress,
                    medianRttMs = p.rttMs,
                    directUdpCount = if (isDirect) 1 else 0,
                    hasCompleteKeys = hasKey,
                    joinedAtMs = p.joinedAtMs
                )
            )
        }

        rttSamples.sort()
        val medianRtt = if (rttSamples.isEmpty()) 0 else rttSamples[rttSamples.size / 2]

        val (hub, standby) = if (nextTopo == GroupTopologyMode.ELECTED_SFU_HUB) {
            electSwarmHubs(hubMetrics)
        } else {
            null to null
        }

        val commitments = crypto.getCommitmentMap(participants.map { it.onionAddress })
        val emojis = AegisGroupCallCrypto.deriveConferenceEmojis(
            groupId = current.groupId,
            callId = current.callId,
            epoch = crypto.currentEpoch,
            participantCommitments = commitments
        )

        val status = when {
            current.forceTorOnly -> "Encrypted Group • Tor Onion Only"
            nextTopo == GroupTopologyMode.ELECTED_SFU_HUB && hub == myCanonical -> "Encrypted Swarm • Relayed via You"
            nextTopo == GroupTopologyMode.ELECTED_SFU_HUB -> "Encrypted Swarm • Adaptive SFU"
            participants.any { !it.isLocal && it.transportMode == CallTransportMode.DIRECT_P2P } -> "Encrypted Mesh • Hybrid P2P + Tor"
            else -> "Encrypted Mesh • Tor Onion"
        }

        _sessionFlow.update { s ->
            s?.copy(
                topologyMode = nextTopo,
                electedHubOnion = hub,
                standbyHubOnion = standby,
                amIElectedHub = hub == myCanonical,
                securityEmojis = emojis,
                participants = participants.sortedWith(
                    compareByDescending<GroupCallParticipant> { it.isLocal }.thenBy { it.joinedAtMs }
                ),
                statusMessage = status,
                medianRttMs = medianRtt,
                directPeersCount = directCount,
                torPeersCount = torCount
            )
        }
    }

    private fun startCallTimerAndHousekeeping(callId: String) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                val cur = _sessionFlow.value ?: break
                if (cur.callId != callId || cur.state != GroupCallState.ACTIVE) break
                val startAt = cur.connectedAtMs ?: cur.startedAtMs
                _sessionFlow.update { it?.copy(durationSeconds = ((System.currentTimeMillis() - startAt) / 1000L).coerceAtLeast(0L)) }
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                tick++
                val cur = _sessionFlow.value ?: break
                if (cur.callId != callId || cur.state != GroupCallState.ACTIVE) break
                val now = System.currentTimeMillis()
                _activeRoomsByGroupFlow.update { map ->
                    pruneStaleBanners(map, cur.groupId, now, BANNER_STALE_MS * 10L)
                }
                _reactionEventsFlow.update { list ->
                    list.filter { now - it.timestampMs <= REACTION_TTL_MS }
                }

                // Evict stale silent/disconnected peers and trigger Epoch Rotation
                val timedOut = peersByOnion.values
                    .filter { now - it.lastSeenAtMs > PEER_STALE_TIMEOUT_MS }
                    .map { it.onionAddress }
                if (timedOut.isNotEmpty()) {
                    callMutex.withLock {
                        timedOut.forEach { rem ->
                            peersByOnion.remove(rem)?.let { p ->
                                peerByTag.remove(p.senderTag4)
                                audioMixer?.removePeer(p.senderTag4)
                                peerVideoDecoders.remove(p.senderTag4)?.release()
                                _participantVideoBitmapsFlow.update { it - rem }
                            }
                        }
                        rotateRoomEpochAndDistributeLocked(retainPreviousGraceMs = 0L)
                        updateRoomTopologyAndEmojisLocked()
                    }
                }

                // Failover hub if current elected hub goes silent
                if (cur.topologyMode == GroupTopologyMode.ELECTED_SFU_HUB && !cur.amIElectedHub) {
                    val hubPeer = cur.electedHubOnion?.let { peersByOnion[it] }
                    if (hubPeer != null && now - hubPeer.lastSeenAtMs > HUB_STALE_TIMEOUT_MS && !cur.standbyHubOnion.isNullOrBlank()) {
                        _sessionFlow.update { s ->
                            s?.copy(
                                electedHubOnion = s.standbyHubOnion,
                                amIElectedHub = s.standbyHubOnion == roomCrypto?.localOnion
                            )
                        }
                    }
                }

                val cands = if (cur.forceTorOnly) emptyList() else localUdpCandidates.toList()
                peersByOnion.keys.toList().forEach { peer ->
                    launch {
                        sendGroupSignal(
                            targetOnion = peer,
                            type = "G_HEARTBEAT",
                            groupId = cur.groupId,
                            callId = callId,
                            extras = mapOf("candidates" to cands)
                        )
                    }
                }

                if (tick % 3 == 0) {
                    val allVerified = TorGroupMessaging.getInstance(context).getVerifiedCallMembers(cur.groupId)
                    val myCanon = roomCrypto?.localOnion
                    allVerified
                        .filter { canonical(it.onionAddress) != myCanon && !peersByOnion.containsKey(canonical(it.onionAddress)) }
                        .forEach { member ->
                            launch {
                                sendGroupSignal(
                                    targetOnion = member.onionAddress,
                                    type = "G_ROOM_ANNOUNCE",
                                    groupId = cur.groupId,
                                    callId = callId,
                                    extras = mapOf(
                                        "groupName" to cur.groupName,
                                        "isVideo" to cur.isLocalVideoEnabled,
                                        "initialRing" to false,
                                        "participantCount" to cur.participants.size,
                                        "epoch" to cur.epoch
                                    )
                                )
                            }
                        }
                }
            }
        }
    }

    private fun openDirectUdpSocket(callId: String) {
        if (udpSocket != null) return
        try {
            val socket = DatagramSocket(0).apply {
                reuseAddress = true
                trafficClass = 0xB8
                runCatching {
                    receiveBufferSize = 512 * 1024
                    sendBufferSize = 512 * 1024
                }
            }
            udpSocket = socket
            val port = socket.localPort
            val candidates = LinkedHashSet<String>()
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces != null && ifaces.hasMoreElements()) {
                val nif = ifaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isMulticastAddress) continue
                    when (addr) {
                        is Inet4Address -> addr.hostAddress?.takeIf { it.isNotBlank() }?.let { candidates.add("$it:$port") }
                        is Inet6Address -> if (!addr.isSiteLocalAddress) {
                            addr.hostAddress?.substringBefore('%')?.takeIf { it.isNotBlank() }?.let { candidates.add("[$it]:$port") }
                        }
                    }
                }
            }
            localUdpCandidates.clear()
            localUdpCandidates.addAll(candidates)
            startUdpReceiveLoop(callId, socket)
            discoverStunAsync(socket)
        } catch (e: Exception) {
            Log.w(TAG, "Group UDP socket bind skipped: ${e.message}")
        }
    }

    private fun discoverStunAsync(socket: DatagramSocket) {
        stunJob?.cancel()
        stunJob = scope.launch(Dispatchers.IO) {
            val servers = listOf("stun.cloudflare.com" to 3478, "stun.l.google.com" to 19302)
            for ((host, port) in servers) {
                launch {
                    runCatching {
                        val ip = InetAddress.getByName(host)
                        val txId = ByteArray(12).also { secureRandom.nextBytes(it) }
                        pendingStunTxIds[bytesToHex(txId)] = System.currentTimeMillis()
                        val req = ByteBuffer.allocate(20)
                            .putShort(0x0001.toShort())
                            .putShort(0x0000.toShort())
                            .putInt(0x2112A442)
                            .put(txId)
                            .array()
                        socket.send(DatagramPacket(req, req.size, InetSocketAddress(ip, port)))
                    }
                }
            }
        }
    }

    private fun startUdpProbingLoop(callId: String) {
        p2pProbeJob?.cancel()
        p2pBurstCounter.set(0)
        p2pProbeJob = scope.launch(Dispatchers.IO) {
            val crypto = roomCrypto ?: return@launch
            while (isActive) {
                val cur = _sessionFlow.value ?: break
                if (cur.callId != callId || cur.state != GroupCallState.ACTIVE || cur.forceTorOnly) break
                val now = System.currentTimeMillis()
                for (peer in peersByOnion.values) {
                    val endpoints = LinkedHashSet<InetSocketAddress>()
                    peer.verifiedUdpEndpoint?.let { endpoints.add(it) }
                    endpoints.addAll(peer.udpCandidates)
                    for (ep in endpoints) {
                        val pkt = ByteBuffer.allocate(1 + 8 + 4 + 8)
                            .put(UDP_MAGIC_PROBE)
                            .putLong(crypto.roomHash8)
                            .putInt(crypto.localSenderTag4)
                            .putLong(now)
                            .array()
                        runCatching { udpSocket?.send(DatagramPacket(pkt, pkt.size, ep)) }
                    }
                }
                val n = p2pBurstCounter.incrementAndGet()
                delay(if (n <= 12) 120L else 1_600L)
            }
        }
    }

    private fun startUdpReceiveLoop(callId: String, socket: DatagramSocket) {
        p2pRecvJob?.cancel()
        p2pRecvJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            while (isActive && !socket.isClosed) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    val data = pkt.data.copyOfRange(pkt.offset, pkt.offset + pkt.length)
                    if (data.size >= 20 && data[0] == 0x01.toByte() && data[1] == 0x01.toByte()) {
                        parseStunResponse(data)?.let { mapped ->
                            if (!localUdpCandidates.contains(mapped)) localUdpCandidates.add(0, mapped)
                        }
                        continue
                    }
                    val s = _sessionFlow.value
                    val crypto = roomCrypto
                    if (s == null || s.callId != callId || s.forceTorOnly || crypto == null || data.size < 13) continue

                    val fromEp = InetSocketAddress(pkt.address, pkt.port)
                    when (data[0]) {
                        UDP_MAGIC_PROBE -> {
                            if (data.size < 21) continue
                            val bb = ByteBuffer.wrap(data, 1, 20)
                            if (bb.long != crypto.roomHash8) continue
                            val peerTag = bb.int
                            val echoTs = bb.long
                            val peer = peerByTag[peerTag] ?: continue
                            if (!peer.udpCandidates.contains(fromEp) && peer.udpCandidates.size < MAX_UDP_CANDIDATES) {
                                peer.udpCandidates.add(0, fromEp)
                            }
                            val ack = ByteBuffer.allocate(21)
                                .put(UDP_MAGIC_PROBE_ACK)
                                .putLong(crypto.roomHash8)
                                .putInt(crypto.localSenderTag4)
                                .putLong(echoTs)
                                .array()
                            runCatching { socket.send(DatagramPacket(ack, ack.size, fromEp)) }
                        }
                        UDP_MAGIC_PROBE_ACK -> {
                            if (data.size < 21) continue
                            val bb = ByteBuffer.wrap(data, 1, 20)
                            if (bb.long != crypto.roomHash8) continue
                            val peerTag = bb.int
                            val echoTs = bb.long
                            val now = System.currentTimeMillis()
                            val rtt = (now - echoTs).toInt()
                            if (rtt !in 0..5_000) continue
                            val peer = peerByTag[peerTag] ?: continue
                            peer.verifiedUdpEndpoint = fromEp
                            peer.lastUdpVerifiedAtMs = now
                            peer.lastSeenAtMs = now
                            peer.rttMs = rtt.coerceAtLeast(1)
                            audioMixer?.updatePeerTransport(peerTag, true)
                        }
                        UDP_MAGIC_SFRAME -> {
                            val sframeBytes = data.copyOfRange(1, data.size)
                            processIncomingSFrame(
                                senderOnion = null,
                                timestampMs = System.currentTimeMillis(),
                                packet = sframeBytes,
                                allowRelayForward = true,
                                fromUdpEndpoint = fromEp
                            )
                        }
                    }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
    }

    private fun sendUdpSFrame(target: InetSocketAddress, sealedSFrame: ByteArray): Boolean {
        val sock = udpSocket ?: return false
        return try {
            val out = ByteArray(1 + sealedSFrame.size)
            out[0] = UDP_MAGIC_SFRAME
            System.arraycopy(sealedSFrame, 0, out, 1, sealedSFrame.size)
            sock.send(DatagramPacket(out, out.size, target))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun addOrUpdatePeerLocked(
        canonicalOnion: String,
        nickname: String,
        publicKey: String,
        pinnedSigningPubKey: String?,
        isVideo: Boolean? = null,
        candidates: JSONArray? = null
    ): PeerTransportState {
        val tag = AegisGroupCallCrypto.senderTag4(canonicalOnion)
        val existing = peersByOnion[canonicalOnion]
        if (existing != null) {
            existing.nickname = nickname.ifBlank { existing.nickname }
            if (publicKey.isNotBlank()) existing.publicKey = publicKey
            if (!pinnedSigningPubKey.isNullOrBlank()) existing.pinnedSigningPubKey = pinnedSigningPubKey
            if (isVideo != null) existing.isVideoEnabled = isVideo
            existing.lastSeenAtMs = System.currentTimeMillis()
            mergeCandidates(existing, candidates)
            return existing
        }
        val peer = PeerTransportState(
            onionAddress = canonicalOnion,
            senderTag4 = tag,
            nickname = nickname.ifBlank { canonicalOnion.take(12) },
            publicKey = publicKey,
            pinnedSigningPubKey = pinnedSigningPubKey,
            isVideoEnabled = isVideo ?: false
        )
        mergeCandidates(peer, candidates)
        peersByOnion[canonicalOnion] = peer
        peerByTag[tag] = peer
        return peer
    }

    private fun mergeCandidates(peer: PeerTransportState, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until min(arr.length(), MAX_UDP_CANDIDATES)) {
            val sock = SeamlessCallManager.parseStrictIpLiteralSocketAddress(arr.optString(i)) ?: continue
            if (!peer.udpCandidates.contains(sock) && peer.udpCandidates.size < MAX_UDP_CANDIDATES) {
                peer.udpCandidates.add(sock)
            }
        }
    }

    private fun broadcastMediaState(session: GroupCallSessionInfo) {
        val peers = peersByOnion.keys.toList()
        scope.launch {
            peers.forEach { target ->
                sendGroupSignal(
                    targetOnion = target,
                    type = "G_MEDIA_STATE",
                    groupId = session.groupId,
                    callId = session.callId,
                    extras = mapOf(
                        "isMuted" to session.isMuted,
                        "isVideo" to session.isLocalVideoEnabled,
                        "isHandRaised" to session.isHandRaised,
                        "videoSubscribed" to !session.isMinimized
                    )
                )
            }
        }
    }

    private fun requestKeyframeFromVisibleVideoPeers() {
        val current = _sessionFlow.value ?: return
        peersByOnion.values.filter { it.isVideoEnabled }.forEach { p ->
            scope.launch { sendGroupSignal(p.onionAddress, "G_VIDEO_PLI", current.groupId, current.callId) }
        }
    }

    private suspend fun sendRosterSync(targetOnion: String, groupId: String, callId: String) {
        val members = peersByOnion.keys.toList()
        sendGroupSignal(
            targetOnion = targetOnion,
            type = "G_ROSTER_SYNC",
            groupId = groupId,
            callId = callId,
            extras = mapOf("members" to members)
        )
    }

    private suspend fun sendSenderKeyPacket(
        targetOnion: String,
        groupId: String,
        callId: String,
        epoch: Int,
        rootSeed: ByteArray
    ) {
        val myOnion = canonical(TorMessagingService.getInstance(context).getMyContactInfo().onionAddress)
        val myTag = AegisGroupCallCrypto.senderTag4(myOnion)
        val (sigPub, sigHex) = TorGroupMessaging.getInstance(context).signGroupCallPayload(
            groupId = groupId,
            callId = callId,
            epoch = epoch,
            senderOnion = myOnion,
            senderTag4 = myTag,
            rootSeed = rootSeed
        )
        sendGroupSignal(
            targetOnion = targetOnion,
            type = "G_SENDER_KEY",
            groupId = groupId,
            callId = callId,
            extras = mapOf(
                "epoch" to epoch,
                "senderTag4" to myTag,
                "rootSeedHex" to com.seamless.android.crypto.AegisCallCrypto.toHex(rootSeed),
                "signingPubKey" to sigPub,
                "sigHex" to sigHex
            )
        )
    }

    private suspend fun sendGroupSignal(
        targetOnion: String,
        type: String,
        groupId: String,
        callId: String,
        extras: Map<String, Any> = emptyMap()
    ): Boolean {
        val messaging = TorMessagingService.getInstance(context)
        val me = messaging.getMyContactInfo()
        val obj = JSONObject().apply {
            put("scope", SCOPE_GROUP_CALL)
            put("type", type)
            put("groupId", groupId)
            put("callId", callId)
            put("senderOnion", me.onionAddress)
            put("senderNickname", me.nickname)
            put("timestamp", System.currentTimeMillis())
            extras.forEach { (k, v) ->
                when (v) {
                    is List<*> -> {
                        val arr = JSONArray()
                        v.forEach { arr.put(it) }
                        put(k, arr)
                    }
                    else -> put(k, v)
                }
            }
        }
        return messaging.sendCallSignalFrame(targetOnion, obj.toString().toByteArray(Charsets.UTF_8))
    }

    private fun startIncomingRingtoneAndVibration() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }
            if (am.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                val eff = VibrationEffect.createWaveform(longArrayOf(0L, 450L, 250L, 450L, 1200L), 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vib?.vibrate(eff, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(eff)
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopRingingAndVibration() {
        runCatching { ringtone?.stop() }
        ringtone = null
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vib?.cancel()
        } catch (_: Exception) {}
    }

    private fun startIncomingRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            callMutex.withLock {
                val s = _sessionFlow.value
                if (s?.callId == callId && s.state == GroupCallState.INCOMING_RINGING) {
                    cleanupLocked()
                    _sessionFlow.value = null
                    CallForegroundService.stop(context)
                }
            }
        }
    }

    private fun closeUdpLocked() {
        p2pProbeJob?.cancel(); p2pProbeJob = null
        p2pRecvJob?.cancel(); p2pRecvJob = null
        stunJob?.cancel(); stunJob = null
        runCatching { udpSocket?.close() }
        udpSocket = null
        localUdpCandidates.clear()
        pendingStunTxIds.clear()
    }

    private fun cleanupLocked() {
        timerJob?.cancel(); timerJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
        ringTimeoutJob?.cancel(); ringTimeoutJob = null
        torSenderJob?.cancel(); torSenderJob = null
        voiceLevelsJob?.cancel(); voiceLevelsJob = null
        stopRingingAndVibration()
        closeUdpLocked()

        audioMixer?.stop()
        audioMixer = null
        sharedFecEncoder.reset()
        localVideoEngine.stop()
        peerVideoDecoders.values.forEach { it.release() }
        peerVideoDecoders.clear()
        _participantVideoBitmapsFlow.value = emptyMap()
        _localVoiceLevelFlow.value = 0f
        _reactionEventsFlow.value = emptyList()
        localReactionEmoji = null
        localReactionAtMs = 0L
        wasMutedBeforePtt = false
        consecutiveSpeakingHandFrames = 0

        roomCrypto?.zeroize()
        roomCrypto = null
        peersByOnion.clear()
        peerByTag.clear()
        audioOutboundSeq.set(0L)
        wasVideoPausedByBg = false
        lastActiveSpeakerOnion = null
        lastActiveSpeakerSwitchAtMs = 0L
    }

    private fun parseStunResponse(data: ByteArray): String? {
        if (data.size < 28) return null
        val bb = ByteBuffer.wrap(data)
        if ((bb.short.toInt() and 0xFFFF) != 0x0101) return null
        val msgLen = bb.short.toInt() and 0xFFFF
        if (bb.int != 0x2112A442) return null
        val txId = ByteArray(12); bb.get(txId)
        val sentAt = pendingStunTxIds[bytesToHex(txId)] ?: return null
        if (System.currentTimeMillis() - sentAt > 15_000L) return null

        val cookie = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)
        var off = 20
        val end = min(data.size, 20 + msgLen)
        while (off + 4 <= end) {
            val type = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
            val len = ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)
            val vOff = off + 4
            if (vOff + len > end) break
            if ((type == 0x0020 || type == 0x8020) && len >= 8) {
                val fam = data[vOff + 1].toInt() and 0xFF
                val port = (((data[vOff + 2].toInt() and 0xFF) shl 8) or (data[vOff + 3].toInt() and 0xFF)) xor 0x2112
                if (fam == 0x01) {
                    val ip = InetAddress.getByAddress(ByteArray(4) { i -> (data[vOff + 4 + i].toInt() xor cookie[i].toInt()).toByte() }).hostAddress
                    if (!ip.isNullOrBlank() && port in 1..65535) return "$ip:$port"
                }
            }
            off = vOff + ((len + 3) and 0xFFFC)
        }
        return null
    }

    private fun canonical(endpoint: String): String =
        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(endpoint).ifBlank {
            endpoint.trim().lowercase()
        }

    private fun bytesToHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it) }

    private fun formatDurationShort(sec: Long): String {
        val m = sec / 60L
        val s = sec % 60L
        return if (m > 0L) "${m}m ${s}s" else "${s}s"
    }
}
