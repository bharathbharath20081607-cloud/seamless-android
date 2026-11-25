package com.seamless.android.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CapturerObserver
import org.webrtc.DataChannel
import org.webrtc.DefaultBlacklistedVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStatsReport
import org.webrtc.RtpCapabilities
import org.webrtc.RtpParameters
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.UniversalHardwareVideoEncoderFactory
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Production HD video media engine for Seamless video calls.
 *
 * Key production guarantees:
 * 1. Voice audio stays 100% on [CallAudioEngine] (hybrid Tor + Direct P2P UDP) so voice
 *    never waits for WebRTC SDP/ICE and never has microphone/AudioTrack conflicts.
 * 2. Single persistent Camera2 pipeline (Camera2Enumerator -> SurfaceTextureHelper ->
 *    VideoSource -> local VideoTrack) reused across calls on the same handler thread,
 *    eliminating CameraDevice close/open thread races on 2nd/3rd calls.
 * 3. Uses [UniversalHardwareVideoEncoderFactory] + [DefaultBlacklistedVideoDecoderFactory]
 *    so Qualcomm, MediaTek, Exynos, Tensor, Kirin, and Unisoc chipsets all get hardware
 *    H.264 / VP8 acceleration with 16-pixel alignment.
 * 4. Embeds initial LAN + STUN srflx candidates directly into the SDP offer/answer (up to
 *    320ms gather settle) AND trickles any late candidates, while keeping the Tor circuit
 *    free of fallback video fragments during the first 1.6s of WebRTC negotiation.
 */
class WebRtcVideoCallEngine(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onLocalSdp(generation: Int, type: String, sdp: String)
        fun onLocalIceCandidates(generation: Int, candidates: List<IceCandidate>)
        fun onTransportConnected(generation: Int)
        fun onTransportLost(generation: Int)
        fun onFallbackVideoFrame(frame: VideoFrame)
    }

    data class Stats(
        val rttMs: Int = 0,
        val inboundWidth: Int = 0,
        val inboundHeight: Int = 0,
        val inboundFps: Int = 0,
        val outboundWidth: Int = 0,
        val outboundHeight: Int = 0,
        val outboundFps: Int = 0,
        val codec: String = "",
        val lossPct: Float = 0f,
        val outgoingKbps: Int = 0,
        val routeLabel: String = ""
    ) {
        val qualityLabel: String
            get() {
                val shortSide = min(inboundWidth, inboundHeight)
                if (shortSide <= 0) return if (codec.isNotBlank()) "720p HD • $codec" else "720p HD"
                val tier = when {
                    shortSide >= 1000 -> "1080p"
                    shortSide >= 680 -> "720p HD"
                    shortSide >= 500 -> "540p HD"
                    shortSide >= 340 -> "360p"
                    shortSide >= 220 -> "240p"
                    else -> "180p"
                }
                val fpsPart = if (inboundFps > 0) " ${inboundFps}fps" else ""
                return "$tier$fpsPart"
            }
    }

    companion object {
        private const val TAG = "WebRtcVideoEngine"

        private const val CAPTURE_WIDTH = 1280
        private const val CAPTURE_HEIGHT = 720
        private const val CAPTURE_FPS = 30

        private const val MIN_VIDEO_BITRATE_BPS = 350_000
        private const val START_VIDEO_BITRATE_BPS = 1_400_000
        private const val MAX_VIDEO_BITRATE_BPS = 2_500_000

        private const val CAMERA_HAL_SETTLE_MS = 150L
        private const val CAMERA_STOP_TIMEOUT_MS = 2_000L
        private const val CAMERA_FIRST_FRAME_TIMEOUT_MS = 2_800L
        private const val CAMERA_RECOVERY_COOLDOWN_MS = 3_000L

        private const val SDP_GATHER_SETTLE_MAX_MS = 320L
        private const val FALLBACK_HANDSHAKE_QUIET_MS = 3_500L
        private const val CONNECT_TIMEOUT_MS = 16_000L
        private const val ICE_RESTART_AFTER_MS = 1_600L
        private const val TRANSPORT_LOST_AFTER_MS = 7_500L
        private const val STATS_INTERVAL_MS = 2_000L

        private val STUN_URLS = listOf(
            "stun:stun.cloudflare.com:3478",
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302"
        )

        @Volatile
        private var factoryInitialized = false

        internal data class CaptureProfile(
            val width: Int,
            val height: Int,
            val fps: Int
        )

        internal fun selectBestCaptureFormat(
            formats: List<CameraEnumerationAndroid.CaptureFormat>,
            targetWidth: Int = CAPTURE_WIDTH,
            targetHeight: Int = CAPTURE_HEIGHT,
            targetFps: Int = CAPTURE_FPS
        ): CaptureProfile {
            if (formats.isEmpty()) return CaptureProfile(targetWidth, targetHeight, targetFps)
            fun normFps(raw: Int): Int = (if (raw >= 1000) raw / 1000 else raw).coerceIn(1, 120)
            val hdPreferred = formats.filter {
                it.width in 960..1280 && it.height in 540..960 && normFps(it.framerate.max) >= 24
            }
            val pool = when {
                hdPreferred.isNotEmpty() -> hdPreferred
                else -> {
                    val fhdOrSmaller = formats.filter { it.width <= 1920 && it.height <= 1080 }
                    if (fhdOrSmaller.isNotEmpty()) fhdOrSmaller else formats
                }
            }
            val best = pool.minWithOrNull(
                compareBy<CameraEnumerationAndroid.CaptureFormat>(
                    { kotlin.math.abs(it.width - targetWidth) + kotlin.math.abs(it.height - targetHeight) },
                    { kotlin.math.abs(normFps(it.framerate.max) - targetFps) }
                )
            ) ?: return CaptureProfile(targetWidth, targetHeight, targetFps)
            val chosenFps = normFps(best.framerate.max).coerceIn(15, targetFps)
            return CaptureProfile(best.width, best.height, chosenFps)
        }

        internal fun injectVideoBitrateSdpHints(sdp: String): String {
            val lines = sdp.replace("\r\n", "\n").split("\n")
            val videoCodecs = setOf("H264", "VP8", "VP9", "AV1")
            val bitrateSuffix = "x-google-start-bitrate=1400;x-google-min-bitrate=350;x-google-max-bitrate=2500"
            val opusSuffix = "useinbandfec=1;usedtx=1;maxaveragebitrate=32000"

            val videoCodecPts = HashSet<String>()
            val opusCodecPts = HashSet<String>()
            val existingFmtpPts = HashSet<String>()
            var currentMedia = ""
            for (line in lines) {
                if (line.startsWith("m=")) {
                    currentMedia = when {
                        line.startsWith("m=video ") -> "video"
                        line.startsWith("m=audio ") -> "audio"
                        else -> ""
                    }
                    continue
                }
                if (currentMedia.isEmpty()) continue
                if (line.startsWith("a=rtpmap:")) {
                    val rest = line.removePrefix("a=rtpmap:")
                    val spaceIdx = rest.indexOf(' ')
                    if (spaceIdx > 0) {
                        val pt = rest.substring(0, spaceIdx).trim()
                        val codecName = rest.substring(spaceIdx + 1).substringBefore('/').trim().uppercase()
                        if (currentMedia == "video" && codecName in videoCodecs) {
                            videoCodecPts.add(pt)
                        } else if (currentMedia == "audio" && codecName == "OPUS") {
                            opusCodecPts.add(pt)
                        }
                    }
                } else if (line.startsWith("a=fmtp:")) {
                    val pt = line.removePrefix("a=fmtp:").substringBefore(' ').trim()
                    if (pt.isNotEmpty()) existingFmtpPts.add(pt)
                }
            }

            val out = ArrayList<String>(lines.size + videoCodecPts.size + opusCodecPts.size)
            var section = ""
            for (line in lines) {
                if (line.startsWith("m=")) {
                    section = when {
                        line.startsWith("m=video ") -> "video"
                        line.startsWith("m=audio ") -> "audio"
                        else -> ""
                    }
                    out.add(line)
                    continue
                }
                if (section == "video" && line.startsWith("a=fmtp:")) {
                    val pt = line.removePrefix("a=fmtp:").substringBefore(' ').trim()
                    if (pt in videoCodecPts && !line.contains("x-google-start-bitrate")) {
                        out.add("$line;$bitrateSuffix")
                    } else {
                        out.add(line)
                    }
                    continue
                }
                if (section == "audio" && line.startsWith("a=fmtp:")) {
                    val pt = line.removePrefix("a=fmtp:").substringBefore(' ').trim()
                    if (pt in opusCodecPts && !line.contains("useinbandfec")) {
                        out.add("$line;$opusSuffix")
                    } else {
                        out.add(line)
                    }
                    continue
                }
                out.add(line)
                if (section == "video" && line.startsWith("a=rtpmap:")) {
                    val pt = line.removePrefix("a=rtpmap:").substringBefore(' ').trim()
                    if (pt in videoCodecPts && pt !in existingFmtpPts) {
                        out.add("a=fmtp:$pt $bitrateSuffix")
                        existingFmtpPts.add(pt)
                    }
                } else if (section == "audio" && line.startsWith("a=rtpmap:")) {
                    val pt = line.removePrefix("a=rtpmap:").substringBefore(' ').trim()
                    if (pt in opusCodecPts && pt !in existingFmtpPts) {
                        out.add("a=fmtp:$pt $opusSuffix")
                        existingFmtpPts.add(pt)
                    }
                }
            }
            return out.joinToString("\r\n")
        }

        private fun ensureGlobalInit(appContext: Context) {
            if (factoryInitialized) return
            synchronized(this) {
                if (factoryInitialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                factoryInitialized = true
            }
        }
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "seamless-webrtc").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    /** Shared GL context for capture, HW codecs and UI renderers (lives for the process). */
    val eglBase: EglBase by lazy { EglBase.create() }

    private var factory: PeerConnectionFactory? = null

    // ---- Persistent Camera capture state (serialized on engine thread) ----
    private var capturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localVideo: VideoTrack? = null
    private var switchingObserver: SwitchingCapturerObserver? = null
    private var capturing = false
    private var wantVideo = false
    private var useFrontCamera = true
    @Volatile
    private var activeCameraFront = true
    @Volatile
    private var cameraSwitchInProgress = false
    private var pendingCameraFront: Boolean? = null
    private var usingCamera1Fallback = false
    private var captureThreadSeq = 0
    private var lastCameraRecoveryAtMs = 0L
    private var currentCaptureWidth = CAPTURE_WIDTH
    private var currentCaptureHeight = CAPTURE_HEIGHT
    private var currentCaptureFps = CAPTURE_FPS
    @Volatile
    private var thermalBitrateCapBps = MAX_VIDEO_BITRATE_BPS
    @Volatile
    private var thermalFpsCap = CAPTURE_FPS
    private var thermalListener: Any? = null

    // ---- PeerConnection transport state (engine thread) ----
    private var pc: PeerConnection? = null
    private var generation: Int = 0
    private var isOfferer = false
    private var remoteVideo: VideoTrack? = null
    private var remoteDescriptionSet = false
    @Volatile
    private var transportUp = false
    @Volatile
    private var peerConnectStartedAtMs = 0L
    @Volatile
    private var hasGatheredSrflx = false
    @Volatile
    private var iceGatheringComplete = false
    @Volatile
    private var initialSdpEmitted = false
    private val pendingRemoteCandidates = ArrayList<Pair<Int, IceCandidate>>()
    private val outgoingCandidates = ArrayList<IceCandidate>()
    private var candidateFlushJob: Job? = null
    private var recoveryJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var statsJob: Job? = null
    private var lastPacketsLost = 0L
    private var lastPacketsReceived = 0L

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _mediaConnected = MutableStateFlow(false)
    val mediaConnected: StateFlow<Boolean> = _mediaConnected.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _remoteFirstFrame = MutableStateFlow(false)
    val remoteFirstFrame: StateFlow<Boolean> = _remoteFirstFrame.asStateFlow()

    private val _remoteAspectRatio = MutableStateFlow(9f / 16f)
    val remoteAspectRatio: StateFlow<Float> = _remoteAspectRatio.asStateFlow()

    private val _localMirror = MutableStateFlow(true)
    val localMirror: StateFlow<Boolean> = _localMirror.asStateFlow()

    private val _activeFrontCamera = MutableStateFlow(true)
    val activeFrontCamera: StateFlow<Boolean> = _activeFrontCamera.asStateFlow()

    private val _isSwitchingCamera = MutableStateFlow(false)
    val isSwitchingCamera: StateFlow<Boolean> = _isSwitchingCamera.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    /**
     * Taps local captured frames and forwards them to [CallVideoEngine] ONLY when WebRTC
     * Direct P2P is not connected and after the 1.6s SDP handshake quiet window (or
     * immediately in Tor-Only mode when [peerConnectStartedAtMs] == 0L).
     */
    private val fallbackCaptureTap = VideoSink { frame ->
        if (!transportUp && wantVideo) {
            val quietStart = peerConnectStartedAtMs
            val inHandshakeQuietWindow = quietStart > 0L &&
                (System.currentTimeMillis() - quietStart) < FALLBACK_HANDSHAKE_QUIET_MS
            if (!inHandshakeQuietWindow) {
                listener.onFallbackVideoFrame(frame)
            }
        }
    }

    private val remoteFrameProbe = object : VideoSink {
        @Volatile private var lastW = 0
        @Volatile private var lastH = 0
        override fun onFrame(frame: VideoFrame) {
            val w = frame.rotatedWidth
            val h = frame.rotatedHeight
            if (!_remoteFirstFrame.value) _remoteFirstFrame.value = true
            if (w > 0 && h > 0 && (w != lastW || h != lastH)) {
                lastW = w
                lastH = h
                _remoteAspectRatio.value = w.toFloat() / h.toFloat()
            }
        }
    }

    // ======================= Public API (thread-safe) =======================

    fun configureLocalCamera(enabled: Boolean, frontCamera: Boolean) {
        scope.launch {
            try {
                useFrontCamera = frontCamera
                wantVideo = enabled
                if (enabled) {
                    if (!capturing && activeCameraFront != frontCamera) {
                        stopAndDisposeCapturerBounded(capturer)
                        capturer = null
                        activeCameraFront = frontCamera
                        _activeFrontCamera.value = frontCamera
                        _localMirror.value = frontCamera
                    }
                    ensureLocalVideoPipelineInternal(frontCamera)
                    localVideo?.setEnabled(true)
                    pc?.let { attachLocalVideoToTransceiver(it) }
                    updateCaptureInternal()
                    if (activeCameraFront != frontCamera) {
                        switchOrReopenCameraInternal(frontCamera, forceReopen = false)
                    } else {
                        _activeFrontCamera.value = frontCamera
                        _localMirror.value = frontCamera
                    }
                } else {
                    localVideo?.setEnabled(false)
                    stopCaptureInternal()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "configureLocalCamera failed: ${e.message}", e)
            }
        }
    }

    fun startPeerConnection(
        generation: Int,
        isOfferer: Boolean,
        frontCamera: Boolean,
        videoEnabled: Boolean
    ) {
        scope.launch {
            try {
                useFrontCamera = frontCamera
                wantVideo = videoEnabled
                if (videoEnabled && !capturing && activeCameraFront != frontCamera) {
                    stopAndDisposeCapturerBounded(capturer)
                    capturer = null
                    activeCameraFront = frontCamera
                    _activeFrontCamera.value = frontCamera
                    _localMirror.value = frontCamera
                }
                ensureLocalVideoPipelineInternal(frontCamera)
                localVideo?.setEnabled(videoEnabled)
                updateCaptureInternal()
                if (videoEnabled && activeCameraFront != frontCamera) {
                    switchOrReopenCameraInternal(frontCamera, forceReopen = false)
                }

                disposePeerConnectionInternal(clearRemoteCandidates = false)
                pendingRemoteCandidates.removeAll { it.first != generation }
                createPeerConnectionInternal(generation, isOfferer)
                if (isOfferer) {
                    createAndSendOffer()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "startPeerConnection failed: ${e.message}", e)
                disposePeerConnectionInternal()
                listener.onTransportLost(generation)
            }
        }
    }

    fun stopPeerConnectionOnly() {
        scope.launch {
            disposePeerConnectionInternal()
        }
    }

    fun onRemoteSdp(
        generation: Int,
        type: String,
        sdp: String,
        frontCamera: Boolean,
        videoEnabled: Boolean,
        shouldYieldOnGlare: Boolean = true
    ) {
        scope.launch {
            try {
                useFrontCamera = frontCamera
                wantVideo = videoEnabled
                if (videoEnabled && !capturing && activeCameraFront != frontCamera) {
                    stopAndDisposeCapturerBounded(capturer)
                    capturer = null
                    activeCameraFront = frontCamera
                    _activeFrontCamera.value = frontCamera
                    _localMirror.value = frontCamera
                }
                ensureLocalVideoPipelineInternal(frontCamera)
                localVideo?.setEnabled(videoEnabled)
                updateCaptureInternal()

                if (type.equals("offer", ignoreCase = true)) {
                    val existing = pc
                    val isGlare = existing != null &&
                        existing.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER
                    if (isGlare && !shouldYieldOnGlare) {
                        Log.i(TAG, "⚡ Glare resolved: keeping local WebRTC offer (gen=${this@WebRtcVideoCallEngine.generation})")
                        return@launch
                    }
                    var rolledBack = false
                    if (isGlare && generation == this@WebRtcVideoCallEngine.generation) {
                        rolledBack = setLocal(existing, SessionDescription(SessionDescription.Type.ROLLBACK, ""))
                        if (rolledBack) {
                            Log.i(TAG, "⚡ Glare resolved via RFC 8829 ROLLBACK (gen=$generation)")
                        }
                    }
                    if (existing == null || (isGlare && !rolledBack) || generation != this@WebRtcVideoCallEngine.generation) {
                        disposePeerConnectionInternal(clearRemoteCandidates = false)
                        pendingRemoteCandidates.removeAll { it.first != generation }
                        createPeerConnectionInternal(generation, offerer = false)
                    }
                    val peer = pc ?: return@launch
                    if (!setRemote(peer, SessionDescription(SessionDescription.Type.OFFER, injectVideoBitrateSdpHints(sdp)))) {
                        return@launch
                    }
                    peer.setBitrate(MIN_VIDEO_BITRATE_BPS, START_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
                    attachLocalVideoToTransceiver(peer)
                    tuneVideoSenders(peer)
                    remoteDescriptionSet = true
                    drainPendingRemoteCandidates(peer)
                    val answer = createSdp(peer, isOffer = false) ?: return@launch
                    if (!setLocal(peer, answer)) return@launch
                    awaitInitialCandidateSettle()
                    val fullAnswerSdp = injectVideoBitrateSdpHints(peer.localDescription?.description ?: answer.description)
                    markInitialSdpEmitted()
                    listener.onLocalSdp(generation, "answer", fullAnswerSdp)
                } else if (type.equals("answer", ignoreCase = true)) {
                    if (generation != this@WebRtcVideoCallEngine.generation) return@launch
                    val peer = pc ?: return@launch
                    if (peer.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) return@launch
                    if (!setRemote(peer, SessionDescription(SessionDescription.Type.ANSWER, injectVideoBitrateSdpHints(sdp)))) {
                        return@launch
                    }
                    peer.setBitrate(MIN_VIDEO_BITRATE_BPS, START_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
                    tuneVideoSenders(peer)
                    remoteDescriptionSet = true
                    drainPendingRemoteCandidates(peer)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "onRemoteSdp($type) failed: ${e.message}", e)
            }
        }
    }

    fun onRemoteCandidates(generation: Int, candidates: List<IceCandidate>) {
        scope.launch {
            val peer = pc
            if (peer == null || !remoteDescriptionSet || generation != this@WebRtcVideoCallEngine.generation) {
                if (generation >= this@WebRtcVideoCallEngine.generation) {
                    candidates.forEach { pendingRemoteCandidates.add(generation to it) }
                }
                return@launch
            }
            candidates.forEach { runCatching { peer.addIceCandidate(it) } }
        }
    }

    fun switchCamera(front: Boolean) {
        scope.launch {
            useFrontCamera = front
            if (cameraSwitchInProgress) {
                pendingCameraFront = front
                return@launch
            }
            switchOrReopenCameraInternal(front, forceReopen = false)
        }
    }

    fun requestIceRestart() {
        scope.launch {
            val peer = pc ?: return@launch
            Log.i(TAG, "🔁 Manual ICE restart requested (gen=$generation)")
            isOfferer = true
            hasGatheredSrflx = false
            iceGatheringComplete = false
            runCatching { peer.restartIce() }
            createAndSendOffer()
        }
    }

    fun endSession() {
        scope.launch {
            unregisterThermalListener()
            disposePeerConnectionInternal()
            wantVideo = false
            localVideo?.setEnabled(false)
            stopCaptureInternal()
            val oldCap = capturer
            capturer = null
            stopAndDisposeCapturerBounded(oldCap)
            localVideo?.let { track ->
                runCatching { track.removeSink(fallbackCaptureTap) }
                runCatching { track.dispose() }
            }
            localVideo = null
            _localVideoTrack.value = null
            runCatching { videoSource?.dispose() }
            videoSource = null
            switchingObserver = null
            runCatching { textureHelper?.dispose() }
            textureHelper = null
            usingCamera1Fallback = false
            thermalBitrateCapBps = MAX_VIDEO_BITRATE_BPS
            thermalFpsCap = CAPTURE_FPS
        }
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || thermalListener != null) return
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            scope.launch {
                val (capBps, capFps) = when {
                    status >= PowerManager.THERMAL_STATUS_CRITICAL -> 500_000 to 15
                    status >= PowerManager.THERMAL_STATUS_SEVERE -> 900_000 to 20
                    status >= PowerManager.THERMAL_STATUS_MODERATE -> 1_600_000 to 24
                    else -> MAX_VIDEO_BITRATE_BPS to CAPTURE_FPS
                }
                if (capBps != thermalBitrateCapBps || capFps != thermalFpsCap) {
                    thermalBitrateCapBps = capBps
                    thermalFpsCap = capFps
                    Log.i(TAG, "🌡️ Thermal status=$status -> video cap ${capBps / 1000}kbps@${capFps}fps")
                    pc?.let { peer ->
                        tuneVideoSenders(peer)
                        runCatching {
                            peer.setBitrate(
                                MIN_VIDEO_BITRATE_BPS.coerceAtMost(capBps),
                                START_VIDEO_BITRATE_BPS.coerceAtMost(capBps),
                                capBps
                            )
                        }
                    }
                }
            }
        }
        thermalListener = listener
        runCatching { pm.addThermalStatusListener(listener) }
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val l = thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
        thermalListener = null
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        runCatching { pm.removeThermalStatusListener(l) }
    }

    // ======================= Camera & Factory Lifecycle =======================

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        ensureGlobalInit(appContext)
        val created = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(UniversalHardwareVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultBlacklistedVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factory = created
        return created
    }

    private fun ensureLocalVideoPipelineInternal(frontCamera: Boolean) {
        if (localVideo != null && videoSource != null && switchingObserver != null) return
        val f = ensureFactory()

        val helper = textureHelper ?: SurfaceTextureHelper.create(
            "seamless-capture-${++captureThreadSeq}",
            eglBase.eglBaseContext
        ).also { textureHelper = it }
        val vSource = videoSource ?: f.createVideoSource(false).also {
            videoSource = it
        }
        val obs = switchingObserver ?: SwitchingCapturerObserver(vSource.capturerObserver).also {
            switchingObserver = it
        }
        if (capturer == null) {
            val enumerator: CameraEnumerator = if (usingCamera1Fallback) {
                Camera1Enumerator(true)
            } else {
                Camera2Enumerator(appContext)
            }
            val cameraName = findBestCameraName(enumerator, frontCamera)
            if (cameraName != null) {
                val cap = runCatching {
                    enumerator.createCapturer(cameraName, createCameraEventsHandler(frontCamera))
                }.getOrNull()
                if (cap != null) {
                    runCatching {
                        cap.initialize(helper, appContext, obs)
                        capturer = cap
                        activeCameraFront = frontCamera
                        _activeFrontCamera.value = frontCamera
                        _localMirror.value = frontCamera
                    }.onFailure {
                        Log.w(TAG, "capturer initialize failed: ${it.message}")
                        runCatching { cap.dispose() }
                    }
                }
            }
        }
        if (localVideo == null) {
            val vTrack = f.createVideoTrack("seamless-video", vSource)
            vTrack.setEnabled(wantVideo)
            vTrack.addSink(fallbackCaptureTap)
            localVideo = vTrack
            _activeFrontCamera.value = activeCameraFront
            _localMirror.value = activeCameraFront
            _localVideoTrack.value = vTrack
        }
    }

    private suspend fun switchOrReopenCameraInternal(targetFront: Boolean, forceReopen: Boolean) {
        cameraSwitchInProgress = true
        _isSwitchingCamera.value = true
        try {
            var requestedFront: Boolean? = targetFront
            var applyForce = forceReopen
            while (requestedFront != null) {
                val nextFront = requestedFront
                pendingCameraFront = null

                if (!applyForce && nextFront == activeCameraFront && (capturer != null || !capturing)) {
                    _activeFrontCamera.value = activeCameraFront
                    _localMirror.value = activeCameraFront
                    requestedFront = pendingCameraFront
                    continue
                }
                applyForce = false

                if (!wantVideo || !capturing) {
                    stopAndDisposeCapturerBounded(capturer)
                    capturer = null
                    capturing = false
                    activeCameraFront = nextFront
                    _activeFrontCamera.value = nextFront
                    _localMirror.value = nextFront
                    requestedFront = pendingCameraFront
                    continue
                }

                val previousFront = activeCameraFront
                val obs = switchingObserver
                obs?.suppressStopNotification = true
                obs?.firstFrameDeferred?.cancel()
                obs?.firstFrameDeferred = null

                val oldCap = capturer
                capturer = null
                capturing = false
                stopAndDisposeCapturerBounded(oldCap)
                delay(CAMERA_HAL_SETTLE_MS)

                var switchResult = openAndVerifyCameraInternal(nextFront, useCamera1 = usingCamera1Fallback)
                if (!switchResult.first && !usingCamera1Fallback) {
                    Log.w(TAG, "📷 Camera2 switch to front=$nextFront failed — retrying with Camera1")
                    delay(CAMERA_HAL_SETTLE_MS)
                    val cam1Result = openAndVerifyCameraInternal(nextFront, useCamera1 = true)
                    if (cam1Result.first) {
                        usingCamera1Fallback = true
                        switchResult = cam1Result
                    }
                }

                if (switchResult.first) {
                    activeCameraFront = nextFront
                    useFrontCamera = nextFront
                    _activeFrontCamera.value = nextFront
                    _localMirror.value = nextFront
                    val profile = switchResult.second
                    boostVideoQualityAfterCameraChange(profile.width, profile.height, profile.fps)
                } else {
                    Log.e(TAG, "📷 Both Camera2 and Camera1 failed for front=$nextFront — restoring front=$previousFront")
                    delay(CAMERA_HAL_SETTLE_MS)
                    var restoreResult = openAndVerifyCameraInternal(previousFront, useCamera1 = usingCamera1Fallback)
                    if (!restoreResult.first && !usingCamera1Fallback) {
                        val cam1Restore = openAndVerifyCameraInternal(previousFront, useCamera1 = true)
                        if (cam1Restore.first) {
                            usingCamera1Fallback = true
                            restoreResult = cam1Restore
                        }
                    }
                    activeCameraFront = previousFront
                    useFrontCamera = previousFront
                    _activeFrontCamera.value = previousFront
                    _localMirror.value = previousFront
                    if (restoreResult.first) {
                        val rp = restoreResult.second
                        boostVideoQualityAfterCameraChange(rp.width, rp.height, rp.fps)
                    }
                }

                requestedFront = pendingCameraFront
            }
        } finally {
            switchingObserver?.suppressStopNotification = false
            cameraSwitchInProgress = false
            _isSwitchingCamera.value = false
        }
    }

    private suspend fun openAndVerifyCameraInternal(
        front: Boolean,
        useCamera1: Boolean
    ): Pair<Boolean, CaptureProfile> {
        val vSource = videoSource ?: return false to CaptureProfile(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
        val obs = switchingObserver ?: SwitchingCapturerObserver(vSource.capturerObserver).also {
            switchingObserver = it
        }
        val helper = textureHelper ?: SurfaceTextureHelper.create(
            "seamless-capture-${++captureThreadSeq}",
            eglBase.eglBaseContext
        ).also { textureHelper = it }

        val enumerator: CameraEnumerator = if (useCamera1) {
            Camera1Enumerator(true)
        } else {
            Camera2Enumerator(appContext)
        }
        val camName = findBestCameraName(enumerator, front)
        if (camName == null) {
            Log.w(TAG, "📷 No camera found for front=$front (camera1=$useCamera1)")
            return false to CaptureProfile(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
        }
        val formats = runCatching { enumerator.getSupportedFormats(camName) }.getOrNull().orEmpty()
        val profile = selectBestCaptureFormat(formats, CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)

        val newCap = runCatching {
            enumerator.createCapturer(camName, createCameraEventsHandler(front))
        }.getOrNull() ?: return false to profile

        val frameSignal = CompletableDeferred<Boolean>()
        obs.firstFrameDeferred = frameSignal
        capturer = newCap

        val started = runCatching {
            newCap.initialize(helper, appContext, obs)
            newCap.startCapture(profile.width, profile.height, profile.fps)
            true
        }.onFailure {
            Log.e(TAG, "📷 startCapture failed (cam=$camName, camera1=$useCamera1): ${it.message}")
        }.getOrDefault(false)

        if (!started) {
            obs.firstFrameDeferred = null
            stopAndDisposeCapturerBounded(newCap)
            if (capturer === newCap) capturer = null
            return false to profile
        }

        val gotFrame = withTimeoutOrNull(CAMERA_FIRST_FRAME_TIMEOUT_MS) { frameSignal.await() } == true
        obs.firstFrameDeferred = null
        if (!gotFrame) {
            Log.w(TAG, "📷 First frame timeout/error (cam=$camName, front=$front, camera1=$useCamera1)")
            stopAndDisposeCapturerBounded(newCap)
            if (capturer === newCap) capturer = null
            return false to profile
        }

        capturing = true
        currentCaptureWidth = profile.width
        currentCaptureHeight = profile.height
        currentCaptureFps = profile.fps
        Log.i(TAG, "📷 Active camera $camName (front=$front, camera1=$useCamera1) ${profile.width}x${profile.height}@${profile.fps}")
        return true to profile
    }

    private suspend fun stopAndDisposeCapturerBounded(oldCap: CameraVideoCapturer?) {
        if (oldCap == null) return
        val completed = withTimeoutOrNull(CAMERA_STOP_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching { oldCap.stopCapture() }
                runCatching { oldCap.dispose() }
            }
            true
        }
        if (completed == null) {
            Log.w(TAG, "📷 Timed out stopping old capturer after ${CAMERA_STOP_TIMEOUT_MS}ms — allocating fresh SurfaceTextureHelper")
            val staleHelper = textureHelper
            textureHelper = SurfaceTextureHelper.create(
                "seamless-capture-${++captureThreadSeq}",
                eglBase.eglBaseContext
            )
            if (staleHelper != null) {
                scope.launch(Dispatchers.IO) { runCatching { staleHelper.dispose() } }
            }
        }
    }

    private fun boostVideoQualityAfterCameraChange(w: Int, h: Int, fps: Int) {
        runCatching {
            videoSource?.adaptOutputFormat(w, h, fps.coerceIn(15, 30))
        }.onFailure { Log.w(TAG, "adaptOutputFormat failed: ${it.message}") }
        pc?.let { peer ->
            tuneVideoSenders(peer)
            runCatching {
                peer.setBitrate(MIN_VIDEO_BITRATE_BPS, START_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
            }
        }
    }

    private fun updateCaptureInternal() {
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!wantVideo || !hasCameraPerm) {
            if (capturing) stopCaptureInternal()
            return
        }
        if (capturing) return

        val vSource = videoSource ?: return
        val obs = switchingObserver ?: SwitchingCapturerObserver(vSource.capturerObserver).also {
            switchingObserver = it
        }
        val helper = textureHelper ?: SurfaceTextureHelper.create(
            "seamless-capture-${++captureThreadSeq}",
            eglBase.eglBaseContext
        ).also { textureHelper = it }

        val enumerator: CameraEnumerator = if (usingCamera1Fallback) {
            Camera1Enumerator(true)
        } else {
            Camera2Enumerator(appContext)
        }
        val chosenFront = activeCameraFront
        val camName = findBestCameraName(enumerator, chosenFront)
        var cap = capturer
        if (cap == null && camName != null) {
            cap = runCatching {
                enumerator.createCapturer(camName, createCameraEventsHandler(chosenFront))
            }.getOrNull()?.also { created ->
                runCatching { created.initialize(helper, appContext, obs) }
                    .onFailure {
                        Log.w(TAG, "capturer initialize failed in updateCapture: ${it.message}")
                        runCatching { created.dispose() }
                        return
                    }
                capturer = created
            }
        }
        if (cap == null) return

        val formats = camName?.let { runCatching { enumerator.getSupportedFormats(it) }.getOrNull() }.orEmpty()
        val profile = selectBestCaptureFormat(formats, CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
        val firstFrameSignal = CompletableDeferred<Boolean>()
        obs.firstFrameDeferred = firstFrameSignal

        val started = runCatching {
            cap.startCapture(profile.width, profile.height, profile.fps)
            capturing = true
            currentCaptureWidth = profile.width
            currentCaptureHeight = profile.height
            currentCaptureFps = profile.fps
            Log.i(TAG, "📷 Video capture started ${profile.width}x${profile.height}@${profile.fps} (front=$chosenFront, camera1=$usingCamera1Fallback)")
            true
        }.getOrElse { e ->
            Log.e(TAG, "startCapture failed: ${e.message}")
            obs.firstFrameDeferred = null
            false
        }

        if (started) {
            boostVideoQualityAfterCameraChange(profile.width, profile.height, profile.fps)
            if (!usingCamera1Fallback) {
                scope.launch {
                    val gotFrame = withTimeoutOrNull(CAMERA_FIRST_FRAME_TIMEOUT_MS) { firstFrameSignal.await() } == true
                    if (!gotFrame && capturer === cap && wantVideo && capturing && !cameraSwitchInProgress && !usingCamera1Fallback) {
                        Log.w(TAG, "📷 Initial Camera2 capture stalled — switching to Camera1 fallback")
                        usingCamera1Fallback = true
                        switchOrReopenCameraInternal(activeCameraFront, forceReopen = true)
                    }
                }
            }
        } else if (!usingCamera1Fallback) {
            usingCamera1Fallback = true
            runCatching { cap.dispose() }
            if (capturer === cap) capturer = null
            scope.launch {
                switchOrReopenCameraInternal(useFrontCamera, forceReopen = true)
            }
        }
    }

    private fun stopCaptureInternal() {
        switchingObserver?.suppressStopNotification = false
        switchingObserver?.firstFrameDeferred?.cancel()
        switchingObserver?.firstFrameDeferred = null
        if (!capturing) return
        runCatching { capturer?.stopCapture() }
        capturing = false
        Log.i(TAG, "📷 Video capture stopped")
    }

    private fun createCameraEventsHandler(forFront: Boolean): CameraVideoCapturer.CameraEventsHandler {
        return object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) {
                Log.w(TAG, "📷 Camera error (front=$forFront): $errorDescription")
                switchingObserver?.firstFrameDeferred?.complete(false)
                maybeScheduleLiveCameraRecovery(forFront)
            }

            override fun onCameraDisconnected() {
                Log.w(TAG, "📷 Camera disconnected (front=$forFront)")
                switchingObserver?.firstFrameDeferred?.complete(false)
                maybeScheduleLiveCameraRecovery(forFront)
            }

            override fun onCameraFreezed(errorDescription: String?) {
                Log.w(TAG, "📷 Camera freezed (front=$forFront): $errorDescription")
                switchingObserver?.firstFrameDeferred?.complete(false)
                maybeScheduleLiveCameraRecovery(forFront)
            }

            override fun onCameraOpening(cameraName: String?) {
                Log.d(TAG, "📷 Opening camera $cameraName (front=$forFront)")
            }

            override fun onFirstFrameAvailable() {
                Log.i(TAG, "📷 First frame available (front=$forFront)")
                switchingObserver?.firstFrameDeferred?.complete(true)
            }

            override fun onCameraClosed() {
                Log.d(TAG, "📷 Camera closed (front=$forFront)")
            }
        }
    }

    private fun maybeScheduleLiveCameraRecovery(forFront: Boolean) {
        val now = System.currentTimeMillis()
        if (cameraSwitchInProgress || !wantVideo || !capturing) return
        if (now - lastCameraRecoveryAtMs < CAMERA_RECOVERY_COOLDOWN_MS) return
        lastCameraRecoveryAtMs = now
        scope.launch {
            if (cameraSwitchInProgress || !wantVideo || !capturing) return@launch
            Log.w(TAG, "🔁 Self-healing stalled/errored camera (front=$forFront)")
            switchOrReopenCameraInternal(targetFront = forFront, forceReopen = true)
        }
    }

    private fun findBestCameraName(enumerator: CameraEnumerator, front: Boolean): String? {
        val candidates = enumerator.deviceNames.filter { id ->
            if (front) enumerator.isFrontFacing(id) else enumerator.isBackFacing(id)
        }
        if (candidates.isEmpty()) return enumerator.deviceNames.firstOrNull()

        val cameraManager = if (enumerator is Camera2Enumerator) {
            runCatching { appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }.getOrNull()
        } else null

        data class ScoredCam(val id: String, val score: Int, val numericId: Int)
        val scored = candidates.mapNotNull { id ->
            val formats = runCatching { enumerator.getSupportedFormats(id) }.getOrNull().orEmpty()
            if (formats.isEmpty()) return@mapNotNull null

            var compatible = true
            if (cameraManager != null) {
                val chars = runCatching { cameraManager.getCameraCharacteristics(id) }.getOrNull()
                val caps = chars?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                if (caps != null && !caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                    compatible = false
                }
            }
            if (!compatible) return@mapNotNull null

            val supports720p = formats.any { it.width >= 1280 && it.height >= 720 }
            val isPrimaryZeroOne = id == "0" || id == "1"
            val score = (if (supports720p) 100 else 0) + (if (isPrimaryZeroOne) 20 else 0)
            ScoredCam(id, score, id.toIntOrNull() ?: Int.MAX_VALUE)
        }

        return scored.minWithOrNull(
            compareByDescending<ScoredCam> { it.score }.thenBy { it.numericId }
        )?.id ?: candidates.firstOrNull()
    }

    private inner class SwitchingCapturerObserver(
        private val delegate: CapturerObserver
    ) : CapturerObserver {
        @Volatile var suppressStopNotification = false
        @Volatile var firstFrameDeferred: CompletableDeferred<Boolean>? = null
        private var startedOnce = false

        override fun onCapturerStarted(success: Boolean) {
            if (success) {
                if (!startedOnce) {
                    startedOnce = true
                    delegate.onCapturerStarted(true)
                }
            } else {
                firstFrameDeferred?.complete(false)
                if (!suppressStopNotification) {
                    startedOnce = false
                    delegate.onCapturerStarted(false)
                }
            }
        }

        override fun onCapturerStopped() {
            if (!suppressStopNotification) {
                startedOnce = false
                delegate.onCapturerStopped()
            }
        }

        override fun onFrameCaptured(frame: VideoFrame) {
            firstFrameDeferred?.complete(true)
            delegate.onFrameCaptured(frame)
        }
    }

    // ======================= PeerConnection Lifecycle =======================

    private fun createPeerConnectionInternal(gen: Int, offerer: Boolean) {
        val f = ensureFactory()
        generation = gen
        isOfferer = offerer
        remoteDescriptionSet = false
        transportUp = false
        hasGatheredSrflx = false
        iceGatheringComplete = false
        initialSdpEmitted = false
        peerConnectStartedAtMs = System.currentTimeMillis()
        lastPacketsLost = 0L
        lastPacketsReceived = 0L
        _remoteFirstFrame.value = false
        _stats.value = Stats()

        val config = PeerConnection.RTCConfiguration(
            STUN_URLS.map { PeerConnection.IceServer.builder(it).createIceServer() }
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            enableDscp = true
            enableCpuOveruseDetection = true
            iceCandidatePoolSize = 2
            iceCheckMinInterval = 25
            iceConnectionReceivingTimeout = 2_500
            iceUnwritableTimeMs = 2_000
            iceBackupCandidatePairPingInterval = 2_000
        }

        val peer = f.createPeerConnection(config, observer)
            ?: throw IllegalStateException("createPeerConnection returned null")
        pc = peer
        registerThermalListener()

        if (offerer) {
            val vTrack = localVideo
            if (vTrack != null) {
                peer.addTransceiver(
                    vTrack,
                    RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.SEND_RECV,
                        listOf("seamless")
                    )
                )
            } else {
                peer.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.SEND_RECV,
                        listOf("seamless")
                    )
                )
            }
            applyCodecPreferencesOnOfferer(peer)
            tuneVideoSenders(peer)
        }

        _sessionActive.value = true

        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!transportUp && pc === peer) {
                Log.w(TAG, "⏱️ WebRTC gen=$gen did not connect in ${CONNECT_TIMEOUT_MS}ms (Tor/UDP fallback stays active)")
                peerConnectStartedAtMs = 0L
                listener.onTransportLost(gen)
            }
        }
        Log.i(TAG, "🎬 WebRTC PeerConnection gen=$gen created (offerer=$offerer, front=$useFrontCamera, video=$wantVideo)")
    }

    private fun attachLocalVideoToTransceiver(peer: PeerConnection) {
        val vTrack = localVideo
        for (t in peer.transceivers) {
            if (t.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                if (vTrack != null && t.sender.track() == null) {
                    t.sender.setTrack(vTrack, false)
                }
                t.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            }
        }
        tuneVideoSenders(peer)
    }

    /** Order H.264 Constrained Baseline first, then H.264, then VP8, plus RTX/RED/ULPFEC on the offerer. */
    private fun applyCodecPreferencesOnOfferer(peer: PeerConnection) {
        val f = factory ?: return
        for (t in peer.transceivers) {
            if (t.mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) continue
            runCatching {
                val sendCodecs = f.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
                val ordered = orderVideoCodecs(sendCodecs)
                if (ordered.isNotEmpty()) {
                    t.setCodecPreferences(ordered)
                }
            }.onFailure { Log.w(TAG, "setCodecPreferences skipped: ${it.message}") }
        }
    }

    private fun orderVideoCodecs(codecs: List<RtpCapabilities.CodecCapability>): List<RtpCapabilities.CodecCapability> {
        fun rank(c: RtpCapabilities.CodecCapability): Int {
            val name = c.name.uppercase()
            val profile = c.parameters["profile-level-id"]?.lowercase() ?: ""
            val pmode = c.parameters["packetization-mode"]
            return when {
                name == "H264" && pmode == "1" && profile.startsWith("42e0") -> 0
                name == "H264" && pmode == "1" -> 1
                name == "VP8" -> 2
                name == "H264" -> 3
                name == "RTX" || name == "RED" || name == "ULPFEC" || name == "FLEXFEC-03" -> 10
                else -> -1
            }
        }
        return codecs.filter { rank(it) >= 0 }.sortedBy { rank(it) }
    }

    private fun tuneVideoSenders(peer: PeerConnection) {
        val maxBps = thermalBitrateCapBps.coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
        val maxFps = thermalFpsCap.coerceIn(15, CAPTURE_FPS)
        for (sender in peer.senders) {
            val track = sender.track() ?: continue
            if (track.kind() != MediaStreamTrack.VIDEO_TRACK_KIND) continue
            runCatching {
                val params = sender.parameters
                params.degradationPreference = RtpParameters.DegradationPreference.BALANCED
                params.encodings.forEach { e ->
                    e.scaleResolutionDownBy = 1.0
                    e.maxBitrateBps = maxBps
                    e.minBitrateBps = MIN_VIDEO_BITRATE_BPS
                    e.maxFramerate = maxFps
                    e.bitratePriority = 1.0
                }
                sender.parameters = params
            }.onFailure { Log.w(TAG, "tuneVideoSenders: ${it.message}") }
        }
    }

    private fun disposePeerConnectionInternal(clearRemoteCandidates: Boolean = true) {
        candidateFlushJob?.cancel(); candidateFlushJob = null
        recoveryJob?.cancel(); recoveryJob = null
        connectTimeoutJob?.cancel(); connectTimeoutJob = null
        statsJob?.cancel(); statsJob = null
        outgoingCandidates.clear()
        if (clearRemoteCandidates) {
            pendingRemoteCandidates.clear()
        }
        initialSdpEmitted = false

        _sessionActive.value = false
        _mediaConnected.value = false
        _remoteVideoTrack.value = null
        _remoteFirstFrame.value = false
        transportUp = false
        peerConnectStartedAtMs = 0L
        remoteDescriptionSet = false
        hasGatheredSrflx = false
        iceGatheringComplete = false

        remoteVideo?.let { runCatching { it.removeSink(remoteFrameProbe) } }
        remoteVideo = null

        val peer = pc
        pc = null
        if (peer != null) {
            runCatching {
                for (sender in peer.senders) {
                    sender.setTrack(null, false)
                }
            }
            runCatching { peer.dispose() }
            Log.i(TAG, "🧹 WebRTC PeerConnection gen=$generation disposed")
        }
    }

    // ======================= Negotiation =======================

    private fun markInitialSdpEmitted() {
        initialSdpEmitted = true
        candidateFlushJob?.cancel()
        candidateFlushJob = null
        outgoingCandidates.clear()
    }

    private suspend fun createAndSendOffer() {
        val peer = pc ?: return
        val gen = generation
        initialSdpEmitted = false
        val offer = createSdp(peer, isOffer = true) ?: return
        if (!setLocal(peer, offer)) return
        awaitInitialCandidateSettle()
        val fullOfferSdp = injectVideoBitrateSdpHints(peer.localDescription?.description ?: offer.description)
        markInitialSdpEmitted()
        listener.onLocalSdp(gen, "offer", fullOfferSdp)
    }

    /**
     * Waits up to [SDP_GATHER_SETTLE_MAX_MS] (320ms) or until a STUN srflx candidate /
     * COMPLETE state is reached so the emitted SDP already includes the LAN + WAN ICE
     * candidates in a single atomic message.
     */
    private suspend fun awaitInitialCandidateSettle() {
        val deadline = System.currentTimeMillis() + SDP_GATHER_SETTLE_MAX_MS
        while (System.currentTimeMillis() < deadline) {
            if (iceGatheringComplete || hasGatheredSrflx) break
            delay(35L)
        }
    }

    private suspend fun createSdp(peer: PeerConnection, isOffer: Boolean): SessionDescription? =
        suspendCoroutine { cont ->
            val obs = object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) { cont.resume(desc) }
                override fun onCreateFailure(error: String?) {
                    Log.w(TAG, "create${if (isOffer) "Offer" else "Answer"} failed: $error")
                    cont.resume(null)
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }
            val constraints = MediaConstraints()
            if (isOffer) peer.createOffer(obs, constraints) else peer.createAnswer(obs, constraints)
        }

    private suspend fun setLocal(peer: PeerConnection, desc: SessionDescription): Boolean =
        suspendCoroutine { cont ->
            peer.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(d: SessionDescription?) {}
                override fun onCreateFailure(e: String?) {}
                override fun onSetSuccess() { cont.resume(true) }
                override fun onSetFailure(error: String?) {
                    Log.w(TAG, "setLocalDescription failed: $error")
                    cont.resume(false)
                }
            }, desc)
        }

    private suspend fun setRemote(peer: PeerConnection, desc: SessionDescription): Boolean =
        suspendCoroutine { cont ->
            peer.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(d: SessionDescription?) {}
                override fun onCreateFailure(e: String?) {}
                override fun onSetSuccess() { cont.resume(true) }
                override fun onSetFailure(error: String?) {
                    Log.w(TAG, "setRemoteDescription failed: $error")
                    cont.resume(false)
                }
            }, desc)
        }

    private fun drainPendingRemoteCandidates(peer: PeerConnection) {
        if (pendingRemoteCandidates.isEmpty()) return
        val matching = pendingRemoteCandidates.filter { it.first == generation }.map { it.second }
        pendingRemoteCandidates.removeAll { it.first <= generation }
        matching.forEach { runCatching { peer.addIceCandidate(it) } }
    }

    private fun queueOutgoingCandidate(candidate: IceCandidate) {
        if (candidate.sdp.contains(" typ srflx ")) {
            hasGatheredSrflx = true
        }
        if (!initialSdpEmitted) {
            return
        }
        outgoingCandidates.add(candidate)
        if (candidateFlushJob?.isActive == true) return
        val gen = generation
        candidateFlushJob = scope.launch {
            delay(120L)
            if (gen != generation) return@launch
            val batch = ArrayList(outgoingCandidates)
            outgoingCandidates.clear()
            if (batch.isNotEmpty()) listener.onLocalIceCandidates(gen, batch)
        }
    }

    // ======================= Connection & Stats Monitoring =======================

    private fun onIceStateChanged(state: PeerConnection.IceConnectionState) {
        val gen = generation
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                recoveryJob?.cancel(); recoveryJob = null
                connectTimeoutJob?.cancel(); connectTimeoutJob = null
                if (!transportUp) {
                    transportUp = true
                    _mediaConnected.value = true
                    pc?.setBitrate(MIN_VIDEO_BITRATE_BPS, START_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)
                    startStatsLoop()
                    Log.i(TAG, "✅ WebRTC gen=$gen Direct P2P 720p HD video transport connected")
                    listener.onTransportConnected(gen)
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.FAILED -> {
                if (!transportUp) return
                if (recoveryJob?.isActive == true) return
                recoveryJob = scope.launch {
                    delay(ICE_RESTART_AFTER_MS)
                    if (gen != generation) return@launch
                    Log.i(TAG, "🔁 WebRTC automatic ICE restart on path change gen=$gen (offerer=$isOfferer)")
                    isOfferer = true
                    hasGatheredSrflx = false
                    iceGatheringComplete = false
                    runCatching { pc?.restartIce() }
                    createAndSendOffer()
                    delay(TRANSPORT_LOST_AFTER_MS - ICE_RESTART_AFTER_MS)
                    if (gen != generation) return@launch
                    val now = pc?.iceConnectionState()
                    if (now != PeerConnection.IceConnectionState.CONNECTED &&
                        now != PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        Log.w(TAG, "⚠️ WebRTC gen=$gen transport lost (state=$now) — resuming Tor/UDP fallback")
                        transportUp = false
                        peerConnectStartedAtMs = 0L
                        _mediaConnected.value = false
                        listener.onTransportLost(gen)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                val peer = pc ?: break
                val report = suspendCoroutine<RTCStatsReport?> { cont ->
                    runCatching { peer.getStats { r -> cont.resume(r) } }.onFailure { cont.resume(null) }
                }
                if (report != null) parseStats(report)
                delay(STATS_INTERVAL_MS)
            }
        }
    }

    private fun parseStats(report: RTCStatsReport) {
        val map = report.statsMap
        var rttMs = 0
        var outKbps = 0
        var route = ""
        var inW = 0; var inH = 0; var inFps = 0
        var outW = 0; var outH = 0; var outFps = 0
        var codec = ""
        var lost = 0L; var received = 0L

        fun num(v: Any?): Double = (v as? Number)?.toDouble() ?: 0.0

        for (s in map.values) {
            val m = s.members
            when (s.type) {
                "candidate-pair" -> {
                    if (m["state"] == "succeeded" && (m["nominated"] == true)) {
                        rttMs = (num(m["currentRoundTripTime"]) * 1000).roundToInt()
                        outKbps = (num(m["availableOutgoingBitrate"]) / 1000).roundToInt()
                        val local = map[m["localCandidateId"] as? String]?.members
                        val remote = map[m["remoteCandidateId"] as? String]?.members
                        val lt = local?.get("candidateType") as? String
                        val rt = remote?.get("candidateType") as? String
                        route = when {
                            lt == "relay" || rt == "relay" -> "Relay"
                            lt == "host" && rt == "host" -> "LAN Direct"
                            else -> "P2P Direct"
                        }
                    }
                }
                "inbound-rtp" -> {
                    if (m["kind"] == "video") {
                        inW = num(m["frameWidth"]).toInt()
                        inH = num(m["frameHeight"]).toInt()
                        inFps = num(m["framesPerSecond"]).roundToInt()
                        lost += num(m["packetsLost"]).toLong()
                        received += num(m["packetsReceived"]).toLong()
                        val codecId = m["codecId"] as? String
                        val mime = map[codecId]?.members?.get("mimeType") as? String
                        if (mime != null) codec = mime.substringAfter('/').uppercase()
                    }
                }
                "outbound-rtp" -> {
                    if (m["kind"] == "video") {
                        outW = num(m["frameWidth"]).toInt()
                        outH = num(m["frameHeight"]).toInt()
                        outFps = num(m["framesPerSecond"]).roundToInt()
                    }
                }
            }
        }
        val dLost = max(0L, lost - lastPacketsLost)
        val dRecv = max(0L, received - lastPacketsReceived)
        lastPacketsLost = lost
        lastPacketsReceived = received
        val lossPct = if (dLost + dRecv > 0) (dLost * 100f) / (dLost + dRecv) else 0f

        _stats.value = Stats(
            rttMs = rttMs,
            inboundWidth = inW,
            inboundHeight = inH,
            inboundFps = inFps,
            outboundWidth = outW,
            outboundHeight = outH,
            outboundFps = outFps,
            codec = codec,
            lossPct = lossPct,
            outgoingKbps = outKbps,
            routeLabel = route
        )
    }

    // ======================= PeerConnection.Observer =======================

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state != null) scope.launch { onIceStateChanged(state) }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                iceGatheringComplete = true
            }
        }
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) scope.launch { queueOutgoingCandidate(candidate) }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track() as? VideoTrack ?: return
            scope.launch {
                if (remoteVideo === track) return@launch
                remoteVideo?.let { runCatching { it.removeSink(remoteFrameProbe) } }
                remoteVideo = track
                track.setEnabled(true)
                runCatching { track.addSink(remoteFrameProbe) }
                _remoteVideoTrack.value = track
            }
        }
    }
}
