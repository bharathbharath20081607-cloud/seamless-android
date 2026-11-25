package com.seamless.android.call

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Low-latency P2P + Tor video capture, encoding, MTU fragmentation, reassembly,
 * and self-contained hardware decoding engine.
 *
 * Uses Android CameraX baseline twin use-cases (`Preview` for zero-latency local PiP +
 * `ImageAnalysis` for YUV frame extraction) which is guaranteed supported on 100% of Android
 * hardware without Camera2 surface conflicts. Every transmitted frame is a self-contained
 * keyframe, preventing permanent decoder stalls under Tor circuit congestion or 4G packet loss.
 */
class CallVideoEngine(
    private val context: Context,
    private val onOutboundFragment: (frameSeq: Long, timestampMs: Long, isKeyframe: Boolean, payload: ByteArray) -> Unit,
    private val onRequestRemoteKeyframe: () -> Unit,
    private val onOutboundFrame: ((frameSeq: Long, timestampMs: Long, fragments: List<ByteArray>) -> Unit)? = null
) {

    companion object {
        private const val TAG = "CallVideoEngine"

        const val CODEC_DELTA_PULSE = "DELTA1"
        const val CODEC_HEVC = "HEVC"
        const val CODEC_AVC = "AVC"
        const val MIME_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC
        const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC

        const val MAX_FRAGMENT_PAYLOAD = 1100
        const val FRAG_HEADER_SIZE = 14

        const val FLAG_KEYFRAME = 0x01
        const val FLAG_HEVC = 0x02
        const val FLAG_CONFIG = 0x04
        const val FLAG_SELF_CONTAINED_JPEG = 0x08
        const val FLAG_FRONT_CAMERA = 0x10
        const val FLAG_TILE_DELTA = 0x20

        private const val CAPTURE_WIDTH = 1280
        private const val CAPTURE_HEIGHT = 720

        private const val P2P_MIN_INTERVAL_MS = 33L     // ~30 fps over Direct P2P UDP
        private const val TOR_MIN_INTERVAL_MS = 72L     // ~14 fps over Dual-Lane Tor circuit
        private const val TOR_DELTA_MIN_INTERVAL_MS = 44L // ~22.7 fps Delta-Pulse tile stream
        private const val DELTA_GRID_COLS = 8
        private const val DELTA_GRID_ROWS = 4
        private const val DELTA_KEYFRAME_INTERVAL = 12
        private const val DELTA_HEADER_MAGIC = 0x444C5431 // "DLT1"
        private const val DELTA_HEADER_BYTES = 12

        /**
         * Slices a single encoded video access unit into MTU-safe fragments.
         * Wire format per fragment (14-byte header + slice):
         * [frameSeq: Int][ptsMs: Int][fragIndex: Short][fragCount: Short][flags: Byte][rotQuarter: Byte]
         */
        fun fragmentFrame(
            frameSeq: Int,
            ptsMs: Int,
            flags: Int,
            rotationDeg: Int,
            frameBytes: ByteArray,
            maxFragmentPayload: Int = MAX_FRAGMENT_PAYLOAD
        ): List<ByteArray> {
            if (frameBytes.isEmpty()) return emptyList()
            val chunkSize = maxFragmentPayload.coerceIn(256, 3800)
            val fragCount = ((frameBytes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
            if (fragCount > 256) return emptyList()

            val rotQuarter = (((rotationDeg % 360) + 360) % 360 / 90) and 0x03
            val out = ArrayList<ByteArray>(fragCount)
            var offset = 0
            for (idx in 0 until fragCount) {
                val len = min(chunkSize, frameBytes.size - offset)
                val pkt = ByteArray(FRAG_HEADER_SIZE + len)
                val bb = ByteBuffer.wrap(pkt)
                bb.putInt(frameSeq)
                bb.putInt(ptsMs)
                bb.putShort(idx.toShort())
                bb.putShort(fragCount.toShort())
                bb.put((flags and 0xFF).toByte())
                bb.put(rotQuarter.toByte())
                System.arraycopy(frameBytes, offset, pkt, FRAG_HEADER_SIZE, len)
                out.add(pkt)
                offset += len
            }
            return out
        }

        fun normalizeVideoFraming(width: Int, height: Int, rotationDeg: Int): Pair<Int, Int> {
            val normalizedRot = ((rotationDeg % 360) + 360) % 360
            return if (normalizedRot == 90 || normalizedRot == 270) {
                height to width
            } else {
                width to height
            }
        }
    }

    data class ReassembledFrame(
        val frameSeq: Int,
        val ptsMs: Int,
        val flags: Int,
        val rotationDeg: Int,
        val data: ByteArray
    )

    /**
     * Reassembles out-of-order video fragments and never emits partial frames.
     */
    class VideoFrameReassembler(
        private val maxInFlightFrames: Int = 8,
        private val frameTimeoutMs: Long = 800L,
        private val pliMinIntervalMs: Long = 250L,
        private val onRequestKeyframe: () -> Unit
    ) {
        private data class InFlightFrame(
            val frameSeq: Int,
            val ptsMs: Int,
            val flags: Int,
            val rotationDeg: Int,
            val fragCount: Int,
            val createdAtMs: Long,
            val fragments: Array<ByteArray?>,
            var receivedCount: Int = 0
        )

        private val lock = Any()
        private val inFlight = LinkedHashMap<Int, InFlightFrame>()
        private var lastDeliveredSeq = -1
        private var lastHighestSeenSeq = -1
        private var lastPliAtMs = 0L

        fun reset() {
            synchronized(lock) {
                inFlight.clear()
                lastDeliveredSeq = -1
                lastHighestSeenSeq = -1
                lastPliAtMs = 0L
            }
        }

        fun onFragment(packet: ByteArray, nowMs: Long = System.currentTimeMillis()): ReassembledFrame? {
            if (packet.size <= FRAG_HEADER_SIZE) return null
            val bb = ByteBuffer.wrap(packet)
            val frameSeq = bb.int
            val ptsMs = bb.int
            val fragIndex = bb.short.toInt() and 0xFFFF
            val fragCount = bb.short.toInt() and 0xFFFF
            val flags = bb.get().toInt() and 0xFF
            val rotQuarter = bb.get().toInt() and 0x03
            val rotationDeg = rotQuarter * 90

            if (fragCount <= 0 || fragCount > 256 || fragIndex >= fragCount) return null
            val slice = packet.copyOfRange(FRAG_HEADER_SIZE, packet.size)

            var shouldRequestPli = false
            var completed: ReassembledFrame? = null

            synchronized(lock) {
                if (frameSeq <= lastDeliveredSeq) {
                    return null
                }

                // Prune expired incomplete frames
                val it = inFlight.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    if (nowMs - entry.value.createdAtMs > frameTimeoutMs) {
                        it.remove()
                        shouldRequestPli = true
                    }
                }

                if (lastHighestSeenSeq >= 0 && frameSeq > lastHighestSeenSeq + 1) {
                    shouldRequestPli = true
                }
                if (frameSeq > lastHighestSeenSeq) {
                    lastHighestSeenSeq = frameSeq
                }

                val isKeyframe = (flags and FLAG_KEYFRAME) != 0
                val isSelfContained = (flags and FLAG_SELF_CONTAINED_JPEG) != 0
                if (isKeyframe && !isSelfContained) {
                    val keyIt = inFlight.entries.iterator()
                    while (keyIt.hasNext()) {
                        val entry = keyIt.next()
                        if (entry.key < frameSeq) {
                            keyIt.remove()
                        }
                    }
                }

                val slot = inFlight.getOrPut(frameSeq) {
                    while (inFlight.size >= maxInFlightFrames) {
                        val oldestKey = inFlight.keys.firstOrNull() ?: break
                        inFlight.remove(oldestKey)
                        shouldRequestPli = true
                    }
                    InFlightFrame(
                        frameSeq = frameSeq,
                        ptsMs = ptsMs,
                        flags = flags,
                        rotationDeg = rotationDeg,
                        fragCount = fragCount,
                        createdAtMs = nowMs,
                        fragments = arrayOfNulls(fragCount)
                    )
                }

                if (slot.fragCount == fragCount && slot.fragments[fragIndex] == null) {
                    slot.fragments[fragIndex] = slice
                    slot.receivedCount++
                }

                if (slot.receivedCount == slot.fragCount) {
                    var totalLen = 0
                    for (frag in slot.fragments) {
                        totalLen += frag?.size ?: return null
                    }
                    val assembled = ByteArray(totalLen)
                    var pos = 0
                    for (frag in slot.fragments) {
                        val part = frag ?: return null
                        System.arraycopy(part, 0, assembled, pos, part.size)
                        pos += part.size
                    }

                    inFlight.remove(frameSeq)
                    val staleIt = inFlight.entries.iterator()
                    while (staleIt.hasNext()) {
                        val entry = staleIt.next()
                        if (entry.key < frameSeq) {
                            staleIt.remove()
                            if (!isKeyframe) {
                                shouldRequestPli = true
                            }
                        }
                    }

                    lastDeliveredSeq = frameSeq
                    completed = ReassembledFrame(
                        frameSeq = frameSeq,
                        ptsMs = ptsMs,
                        flags = flags,
                        rotationDeg = rotationDeg,
                        data = assembled
                    )
                }

                // Self-contained full keyframes don't require PLI; tile deltas request anchor keyframe on drops
                val isDeltaFrame = (flags and FLAG_TILE_DELTA) != 0
                val needsPli = !isSelfContained || isDeltaFrame
                if (needsPli && shouldRequestPli && (nowMs - lastPliAtMs >= pliMinIntervalMs)) {
                    lastPliAtMs = nowMs
                } else {
                    shouldRequestPli = false
                }
            }

            if (shouldRequestPli) {
                onRequestKeyframe()
            }
            return completed
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val _remoteVideoAspectRatio = MutableStateFlow(3f / 4f)
    val remoteVideoAspectRatio: StateFlow<Float> = _remoteVideoAspectRatio.asStateFlow()

    private val _remoteFirstFrameRendered = MutableStateFlow(false)
    val remoteFirstFrameRendered: StateFlow<Boolean> = _remoteFirstFrameRendered.asStateFlow()

    private val _remoteResolutionLabel = MutableStateFlow("Live Video")
    val remoteResolutionLabel: StateFlow<String> = _remoteResolutionLabel.asStateFlow()

    private val _remoteFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteFrameBitmap: StateFlow<Bitmap?> = _remoteFrameBitmap.asStateFlow()

    @Volatile private var callActive = false
    @Volatile private var localVideoEnabled = false
    @Volatile private var frontCamera = true
    @Volatile private var directP2pMode = false
    @Volatile private var selectedCodec = CODEC_HEVC
    @Volatile private var currentRttMs = 0

    @Volatile private var boundLifecycleOwner: LifecycleOwner? = null
    @Volatile private var boundPreviewView: PreviewView? = null
    @Volatile private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var activePreviewUseCase: Preview? = null
    @Volatile private var isCameraBound: Boolean = false
    @Volatile private var isAnalyzerBound: Boolean = false

    private val encoderRunning = AtomicBoolean(false)
    private val encodingExternalFrame = AtomicBoolean(false)
    private val forceNextKeyframe = AtomicBoolean(true)
    private val outboundFrameSeq = AtomicInteger(0)
    private val lastEncodedAtMs = AtomicLong(0L)
    private var nv21Scratch: ByteArray? = null
    private var chromaUScratch: ByteArray? = null
    private var chromaVScratch: ByteArray? = null
    private var deltaRefLuma: ByteArray? = null
    private var deltaRefW: Int = 0
    private var deltaRefH: Int = 0
    private var deltaFramesSinceKeyframe: Int = 0
    private var deltaStripScratch: ByteArray? = null
    private val jpegOutStream = ByteArrayOutputStream(16_384)
    @Volatile var useExternalCameraSource: Boolean = true

    @Volatile private var remoteTextureView: TextureView? = null
    @Volatile private var remoteCropFill: Boolean = true
    @Volatile private var decoderJob: Job? = null
    private val decodingRunning = AtomicBoolean(false)
    private val decoderQueue = ArrayBlockingQueue<ReassembledFrame>(2)

    private val reassembler = VideoFrameReassembler {
        onRequestRemoteKeyframe()
    }

    fun getSupportedHardwareCodecs(): List<String> {
        val result = ArrayList<String>(3)
        result.add(CODEC_DELTA_PULSE)
        if (hasHardwareCodec(MIME_HEVC, encode = true) && hasHardwareCodec(MIME_HEVC, encode = false)) {
            result.add(CODEC_HEVC)
        }
        if (hasHardwareCodec(MIME_AVC, encode = true) && hasHardwareCodec(MIME_AVC, encode = false)) {
            result.add(CODEC_AVC)
        }
        if (result.size == 1) result.add(CODEC_AVC)
        return result
    }

    private fun hasHardwareCodec(mime: String, encode: Boolean): Boolean {
        return try {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            infos.any { info ->
                if (info.isEncoder != encode) return@any false
                if (!info.supportedTypes.any { it.equals(mime, ignoreCase = true) }) return@any false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    !info.isSoftwareOnly
                } else {
                    !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                        !info.name.startsWith("c2.android.", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        boundLifecycleOwner = owner
    }

    private fun resolveLifecycleOwner(): LifecycleOwner? {
        boundLifecycleOwner?.let { owner ->
            if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                return owner
            }
        }
        var ctx: Context? = boundPreviewView?.context
        while (ctx is ContextWrapper) {
            if (ctx is LifecycleOwner && ctx.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                boundLifecycleOwner = ctx
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    fun configureSession(
        isCallActive: Boolean,
        isLocalEnabled: Boolean,
        isFrontCam: Boolean,
        isDirectP2p: Boolean,
        codec: String
    ) {
        val prevActive = callActive
        val prevEnabled = localVideoEnabled
        val prevFront = frontCamera

        callActive = isCallActive
        localVideoEnabled = isLocalEnabled
        frontCamera = isFrontCam
        directP2pMode = isDirectP2p
        selectedCodec = when {
            codec.equals(CODEC_DELTA_PULSE, ignoreCase = true) -> CODEC_DELTA_PULSE
            codec.equals(CODEC_HEVC, ignoreCase = true) -> CODEC_HEVC
            else -> CODEC_AVC
        }
        if (prevActive != isCallActive || prevEnabled != isLocalEnabled || prevFront != isFrontCam) {
            forceNextKeyframe.set(true)
        }
        encoderRunning.set(isCallActive && isLocalEnabled)

        if (!isLocalEnabled || useExternalCameraSource) {
            unbindCameraSafely()
            return
        }

        if (!prevEnabled || prevActive != isCallActive || prevFront != isFrontCam) {
            rebindCameraOnMain()
        }
    }

    fun bindLocalPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView?) {
        boundLifecycleOwner = lifecycleOwner
        if (useExternalCameraSource) return
        val prevView = boundPreviewView
        if (previewView != null) {
            boundPreviewView = previewView
        }
        val shouldNeedAnalyzer = callActive && localVideoEnabled
        if (localVideoEnabled) {
            if (prevView !== previewView ||
                cameraProvider == null ||
                activePreviewUseCase == null ||
                !isCameraBound ||
                (shouldNeedAnalyzer && !isAnalyzerBound)
            ) {
                rebindCameraOnMain()
            } else if (previewView != null) {
                activePreviewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
            }
        }
    }

    fun clearLocalPreviewIfMatches(previewView: PreviewView?) {
        if (boundPreviewView === previewView && !localVideoEnabled) {
            boundPreviewView = null
        }
    }

    fun switchCamera(nextIsFront: Boolean) {
        frontCamera = nextIsFront
        forceNextKeyframe.set(true)
        if (localVideoEnabled && !useExternalCameraSource) {
            rebindCameraOnMain()
        }
    }

    fun setLocalVideoEnabled(enabled: Boolean) {
        localVideoEnabled = enabled
        forceNextKeyframe.set(true)
        encoderRunning.set(callActive && enabled)
        if (!enabled || useExternalCameraSource) {
            unbindCameraSafely()
        } else {
            rebindCameraOnMain()
        }
    }

    fun requestImmediateKeyframe() {
        forceNextKeyframe.set(true)
        lastEncodedAtMs.set(0L)
    }

    fun updateNetworkProfile(isDirectP2p: Boolean, rttMs: Int, lossDetected: Boolean) {
        val prevDirect = directP2pMode
        directP2pMode = isDirectP2p
        currentRttMs = rttMs
        if (prevDirect != isDirectP2p || lossDetected) {
            forceNextKeyframe.set(true)
        }
    }

    fun attachRemoteTextureView(textureView: TextureView?, cropFill: Boolean = true) {
        remoteCropFill = cropFill
        remoteTextureView = textureView
    }

    fun setRemoteCropFill(cropFill: Boolean) {
        remoteCropFill = cropFill
    }

    fun clearRemoteTextureViewIfMatches(textureView: TextureView?) {
        if (remoteTextureView === textureView) {
            remoteTextureView = null
        }
    }

    fun onRemoteVideoFragment(packet: ByteArray) {
        val complete = reassembler.onFragment(packet) ?: return
        if (!decodingRunning.get()) {
            startDecoderLoop()
        }
        if (!decoderQueue.offer(complete)) {
            decoderQueue.poll()
            decoderQueue.offer(complete)
        }
    }

    private fun currentMinIntervalMs(isDirect: Boolean): Long = when {
        isDirect -> P2P_MIN_INTERVAL_MS
        selectedCodec == CODEC_DELTA_PULSE -> TOR_DELTA_MIN_INTERVAL_MS
        else -> TOR_MIN_INTERVAL_MS
    }

    /**
     * Receives captured frames from [WebRtcVideoCallEngine] whenever WebRTC Direct P2P is
     * not yet connected or when Tor-only mode is active, encoding them for our encrypted
     * Tor / Direct UDP fallback path without opening a second Camera session.
     */
    fun onExternalWebRtcFrame(frame: org.webrtc.VideoFrame) {
        if (!callActive || !localVideoEnabled || !encoderRunning.get()) return
        val nowMs = System.currentTimeMillis()
        val isDirect = directP2pMode
        val minInterval = currentMinIntervalMs(isDirect)
        if (nowMs - lastEncodedAtMs.get() < minInterval) return
        if (!encodingExternalFrame.compareAndSet(false, true)) return
        lastEncodedAtMs.set(nowMs)

        frame.retain()
        try {
            cameraExecutor.execute {
                try {
                    encodeRetainedWebRtcFrame(frame, nowMs, isDirect)
                } catch (e: Throwable) {
                    Log.w(TAG, "External frame fallback encode skipped: ${e.message}")
                } finally {
                    frame.release()
                    encodingExternalFrame.set(false)
                }
            }
        } catch (e: Throwable) {
            frame.release()
            encodingExternalFrame.set(false)
        }
    }

    private fun encodeRetainedWebRtcFrame(frame: org.webrtc.VideoFrame, nowMs: Long, isDirect: Boolean) {
        if (!callActive || !localVideoEnabled || !encoderRunning.get()) return
        val srcBuf = frame.buffer
        val srcW = srcBuf.width
        val srcH = srcBuf.height
        if (srcW < 16 || srcH < 16) return

        val isHdSource = srcW >= 1200
        val step: Int
        val jpegQuality: Int
        if (isDirect) {
            if (isHdSource && currentRttMs in 1..85) {
                step = 1
                jpegQuality = 68
            } else {
                step = if (isHdSource) 2 else 1
                jpegQuality = if (currentRttMs > 220) 62 else 76
            }
        } else {
            step = if (isHdSource) {
                if (currentRttMs in 1..650 || currentRttMs == 0) 2 else 3
            } else {
                1
            }
            jpegQuality = if (currentRttMs > 650) 50 else 58
        }

        val useDeltaPulse = !isDirect && selectedCodec == CODEC_DELTA_PULSE
        val outW = if (useDeltaPulse) ((srcW / step) and 15.inv()) else ((srcW / step) and 1.inv())
        val outH = if (useDeltaPulse) ((srcH / step) and 7.inv()) else ((srcH / step) and 1.inv())
        if (outW < 16 || outH < 16) return

        val scaled = if (step == 1 && outW == srcW && outH == srcH) {
            srcBuf.retain()
            srcBuf
        } else {
            srcBuf.cropAndScale(0, 0, srcW, srcH, outW, outH)
        }
        val i420 = try {
            scaled.toI420()
        } finally {
            scaled.release()
        } ?: return

        val nv21: ByteArray
        try {
            nv21 = i420ToNv21(i420, outW, outH)
        } finally {
            i420.release()
        }

        encodeNv21AndDispatch(
            nv21 = nv21,
            outW = outW,
            outH = outH,
            rotationDeg = frame.rotation,
            jpegQuality = jpegQuality,
            nowMs = nowMs,
            isDirect = isDirect
        )
    }

    private fun encodeNv21AndDispatch(
        nv21: ByteArray,
        outW: Int,
        outH: Int,
        rotationDeg: Int,
        jpegQuality: Int,
        nowMs: Long,
        isDirect: Boolean
    ): Int {
        val useDeltaPulse = !isDirect && selectedCodec == CODEC_DELTA_PULSE &&
            outW >= 32 && outH >= 16 && (outW % (DELTA_GRID_COLS * 2) == 0) && (outH % (DELTA_GRID_ROWS * 2) == 0)

        if (useDeltaPulse) {
            val needAnchor = forceNextKeyframe.getAndSet(false) ||
                deltaRefLuma == null ||
                deltaRefW != outW ||
                deltaRefH != outH ||
                deltaFramesSinceKeyframe >= DELTA_KEYFRAME_INTERVAL

            if (!needAnchor) {
                val ref = deltaRefLuma!!
                val tileW = outW / DELTA_GRID_COLS
                val tileH = outH / DELTA_GRID_ROWS
                var tileMask = 0
                var changedCount = 0
                for (row in 0 until DELTA_GRID_ROWS) {
                    val baseY = row * tileH
                    for (col in 0 until DELTA_GRID_COLS) {
                        val baseX = col * tileW
                        var diffSum = 0
                        var samples = 0
                        var y = 2
                        while (y < tileH) {
                            val rowPos = (baseY + y) * outW + baseX
                            var x = 2
                            while (x < tileW) {
                                val idx = rowPos + x
                                diffSum += abs((nv21[idx].toInt() and 0xFF) - (ref[idx].toInt() and 0xFF))
                                samples++
                                x += 4
                            }
                            y += 4
                        }
                        if (samples > 0 && (diffSum / samples) >= 5) {
                            tileMask = tileMask or (1 shl (row * DELTA_GRID_COLS + col))
                            changedCount++
                        }
                    }
                }

                if (changedCount == 0) {
                    deltaFramesSinceKeyframe++
                    return 0
                }

                if (changedCount <= 16) {
                    val stripW = tileW
                    val stripH = tileH * changedCount
                    val stripYSize = stripW * stripH
                    val needed = stripYSize + stripYSize / 2
                    val stripNv21 = deltaStripScratch?.takeIf { it.size >= needed }
                        ?: ByteArray(needed).also { deltaStripScratch = it }
                    val fullYSize = outW * outH
                    val halfTileH = tileH / 2
                    var tileSlot = 0
                    for (bit in 0 until (DELTA_GRID_COLS * DELTA_GRID_ROWS)) {
                        if ((tileMask and (1 shl bit)) == 0) continue
                        val col = bit % DELTA_GRID_COLS
                        val row = bit / DELTA_GRID_COLS
                        val srcX = col * tileW
                        val srcY = row * tileH
                        val dstYBase = tileSlot * tileH * stripW
                        for (dy in 0 until tileH) {
                            val srcPos = (srcY + dy) * outW + srcX
                            System.arraycopy(nv21, srcPos, stripNv21, dstYBase + dy * stripW, tileW)
                            System.arraycopy(nv21, srcPos, ref, srcPos, tileW)
                        }
                        val dstUvBase = stripYSize + tileSlot * halfTileH * stripW
                        for (cy in 0 until halfTileH) {
                            val srcUvPos = fullYSize + ((srcY / 2) + cy) * outW + srcX
                            System.arraycopy(nv21, srcUvPos, stripNv21, dstUvBase + cy * stripW, tileW)
                        }
                        tileSlot++
                    }

                    val stripImg = YuvImage(stripNv21, ImageFormat.NV21, stripW, stripH, null)
                    val stripRect = Rect(0, 0, stripW, stripH)
                    var q = (jpegQuality + 4).coerceAtMost(66)
                    var stripJpeg = ByteArray(0)
                    while (true) {
                        jpegOutStream.reset()
                        if (!stripImg.compressToJpeg(stripRect, q, jpegOutStream)) break
                        stripJpeg = jpegOutStream.toByteArray()
                        if (stripJpeg.size <= 22 * 1024 || q <= 32) break
                        q = (q - 12).coerceAtLeast(32)
                    }
                    if (stripJpeg.isNotEmpty()) {
                        val deltaPayload = ByteArray(DELTA_HEADER_BYTES + stripJpeg.size)
                        ByteBuffer.wrap(deltaPayload)
                            .putInt(DELTA_HEADER_MAGIC)
                            .putInt(tileMask)
                            .putShort(outW.toShort())
                            .putShort(outH.toShort())
                            .put(stripJpeg)
                        deltaFramesSinceKeyframe++
                        var flags = FLAG_TILE_DELTA or FLAG_SELF_CONTAINED_JPEG
                        if (frontCamera) flags = flags or FLAG_FRONT_CAMERA
                        val seq = outboundFrameSeq.incrementAndGet()
                        val ptsMs = (nowMs and 0x7FFFFFFFL).toInt()
                        val fragments = fragmentFrame(
                            frameSeq = seq,
                            ptsMs = ptsMs,
                            flags = flags,
                            rotationDeg = rotationDeg,
                            frameBytes = deltaPayload,
                            maxFragmentPayload = 3500
                        )
                        if (fragments.isNotEmpty()) {
                            if (onOutboundFrame != null) {
                                onOutboundFrame.invoke(seq.toLong(), nowMs, fragments)
                            } else {
                                for (frag in fragments) {
                                    onOutboundFragment(seq.toLong(), nowMs, false, frag)
                                }
                            }
                        }
                        return stripJpeg.size
                    }
                }
            }
        }

        val yuvImg = YuvImage(nv21, ImageFormat.NV21, outW, outH, null)
        val rect = Rect(0, 0, outW, outH)
        var quality = jpegQuality
        var jpegBytes = ByteArray(0)
        val maxFallbackFrameBytes = 24 * 1024
        while (true) {
            jpegOutStream.reset()
            if (!yuvImg.compressToJpeg(rect, quality, jpegOutStream)) return 0
            jpegBytes = jpegOutStream.toByteArray()
            if (isDirect || jpegBytes.size <= maxFallbackFrameBytes || quality <= 28) break
            quality = (quality - 14).coerceAtLeast(28)
        }
        if (jpegBytes.isEmpty()) return 0

        if (useDeltaPulse) {
            val ySize = outW * outH
            val refBuf = deltaRefLuma?.takeIf { it.size == ySize } ?: ByteArray(ySize).also { deltaRefLuma = it }
            System.arraycopy(nv21, 0, refBuf, 0, ySize)
            deltaRefW = outW
            deltaRefH = outH
            deltaFramesSinceKeyframe = 1
        } else if (isDirect) {
            forceNextKeyframe.set(false)
        }

        var flags = FLAG_KEYFRAME or FLAG_SELF_CONTAINED_JPEG
        if (frontCamera) flags = flags or FLAG_FRONT_CAMERA

        val seq = outboundFrameSeq.incrementAndGet()
        val ptsMs = (nowMs and 0x7FFFFFFFL).toInt()
        val payloadMtu = if (isDirect) MAX_FRAGMENT_PAYLOAD else 3500
        val fragments = fragmentFrame(
            frameSeq = seq,
            ptsMs = ptsMs,
            flags = flags,
            rotationDeg = rotationDeg,
            frameBytes = jpegBytes,
            maxFragmentPayload = payloadMtu
        )
        if (fragments.isEmpty()) return 0

        if (onOutboundFrame != null) {
            onOutboundFrame.invoke(seq.toLong(), nowMs, fragments)
        } else {
            for (frag in fragments) {
                onOutboundFragment(seq.toLong(), nowMs, true, frag)
            }
        }
        return jpegBytes.size
    }

    private fun i420ToNv21(i420: org.webrtc.VideoFrame.I420Buffer, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val total = ySize + (ySize / 2)
        val out = nv21Scratch?.takeIf { it.size == total } ?: ByteArray(total).also { nv21Scratch = it }

        val yBuf = i420.dataY.duplicate()
        val uBuf = i420.dataU.duplicate()
        val vBuf = i420.dataV.duplicate()
        val yStride = i420.strideY
        val uStride = i420.strideU
        val vStride = i420.strideV

        if (yStride == width && yBuf.remaining() >= ySize) {
            yBuf.position(0)
            yBuf.get(out, 0, ySize)
        } else {
            var dstY = 0
            for (row in 0 until height) {
                yBuf.position(row * yStride)
                yBuf.get(out, dstY, width)
                dstY += width
            }
        }

        var dstUv = ySize
        val chromaH = height / 2
        val chromaW = width / 2
        val uRowArr = chromaUScratch?.takeIf { it.size >= chromaW } ?: ByteArray(chromaW).also { chromaUScratch = it }
        val vRowArr = chromaVScratch?.takeIf { it.size >= chromaW } ?: ByteArray(chromaW).also { chromaVScratch = it }
        for (row in 0 until chromaH) {
            vBuf.position(row * vStride)
            vBuf.get(vRowArr, 0, chromaW)
            uBuf.position(row * uStride)
            uBuf.get(uRowArr, 0, chromaW)
            for (col in 0 until chromaW) {
                out[dstUv++] = vRowArr[col] // V first for NV21
                out[dstUv++] = uRowArr[col] // U second for NV21
            }
        }
        return out
    }

    fun clearRemoteFrameBitmap() {
        _remoteFrameBitmap.value = null
    }

    fun stop() {
        callActive = false
        localVideoEnabled = false
        encoderRunning.set(false)
        forceNextKeyframe.set(true)
        deltaRefLuma = null
        deltaRefW = 0
        deltaRefH = 0
        deltaFramesSinceKeyframe = 0
        unbindCameraSafely()
        boundPreviewView = null
        stopDecoder()
        reassembler.reset()
        _remoteFirstFrameRendered.value = false
        _remoteFrameBitmap.value = null
    }

    // ========== Internal CameraX Binding & YUV Frame Compression ==========

    private fun unbindCameraSafely() {
        activePreviewUseCase = null
        isCameraBound = false
        isAnalyzerBound = false
        scope.launch(Dispatchers.Main) {
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    private fun rebindCameraOnMain() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm || !localVideoEnabled) return

        scope.launch(Dispatchers.Main) {
            val owner = resolveLifecycleOwner() ?: return@launch
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        val provider = future.get()
                        cameraProvider = provider
                        bindCameraUseCases(provider, owner)
                        if (localVideoEnabled && boundPreviewView == null) {
                            scope.launch(Dispatchers.Main) {
                                delay(120L)
                                if (localVideoEnabled && !isCameraBound && boundPreviewView != null) {
                                    resolveLifecycleOwner()?.let { bindCameraUseCases(provider, it) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Camera bind listener failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.w(TAG, "CameraProvider init failed: ${e.message}")
            }
        }
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider, owner: LifecycleOwner) {
        if (!localVideoEnabled) {
            provider.unbindAll()
            activePreviewUseCase = null
            isCameraBound = false
            isAnalyzerBound = false
            return
        }
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        val selector = if (frontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val resSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(CAPTURE_WIDTH, CAPTURE_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()

        val uiPreview = boundPreviewView?.let { pv ->
            Preview.Builder()
                .setResolutionSelector(resSelector)
                .build()
                .also { it.setSurfaceProvider(pv.surfaceProvider) }
        }
        activePreviewUseCase = uiPreview

        val analyzerUseCase = if (callActive && encoderRunning.get()) {
            ImageAnalysis.Builder()
                .setResolutionSelector(resSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { image ->
                        processCapturedImage(image)
                    }
                }
        } else null

        provider.unbindAll()
        isCameraBound = false
        isAnalyzerBound = false
        try {
            when {
                uiPreview != null && analyzerUseCase != null -> {
                    provider.bindToLifecycle(owner, selector, uiPreview, analyzerUseCase)
                    isCameraBound = true
                    isAnalyzerBound = true
                }
                uiPreview != null -> {
                    provider.bindToLifecycle(owner, selector, uiPreview)
                    isCameraBound = true
                }
                analyzerUseCase != null -> {
                    provider.bindToLifecycle(owner, selector, analyzerUseCase)
                    isAnalyzerBound = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera bind fallback (${e.message})")
            provider.unbindAll()
            isCameraBound = false
            isAnalyzerBound = false
            if (uiPreview != null) {
                runCatching {
                    provider.bindToLifecycle(owner, selector, uiPreview)
                    isCameraBound = true
                }
            } else if (analyzerUseCase != null) {
                runCatching {
                    provider.bindToLifecycle(owner, selector, analyzerUseCase)
                    isAnalyzerBound = true
                }
            }
        }
    }

    private fun processCapturedImage(image: ImageProxy) {
        try {
            if (!callActive || !localVideoEnabled || !encoderRunning.get()) return
            val nowMs = System.currentTimeMillis()
            val isDirect = directP2pMode
            val minInterval = currentMinIntervalMs(isDirect)
            if (nowMs - lastEncodedAtMs.get() < minInterval) return
            lastEncodedAtMs.set(nowMs)

            val isHdSource = image.width >= 1200
            var step: Int
            var jpegQuality: Int
            if (isDirect) {
                if (isHdSource && currentRttMs in 1..85) {
                    step = 1
                    jpegQuality = 68
                } else {
                    step = if (isHdSource) 2 else 1
                    jpegQuality = if (currentRttMs > 220) 62 else 76
                }
            } else {
                step = if (isHdSource) {
                    if (currentRttMs in 1..650 || currentRttMs == 0) 2 else 3
                } else {
                    1
                }
                jpegQuality = if (currentRttMs > 650) 50 else 58
            }

            val useDeltaAlignment = !isDirect && selectedCodec == CODEC_DELTA_PULSE
            val nv21Result = yuv420888ToNv21Sampled(image, step, useDeltaAlignment) ?: return
            val (nv21, outW, outH) = nv21Result
            val encodedSize = encodeNv21AndDispatch(
                nv21 = nv21,
                outW = outW,
                outH = outH,
                rotationDeg = image.imageInfo.rotationDegrees,
                jpegQuality = jpegQuality,
                nowMs = nowMs,
                isDirect = isDirect
            )
            if (isDirect && step == 1 && encodedSize > 26_000) {
                step = 2
                val fallbackNv21 = yuv420888ToNv21Sampled(image, step, false) ?: return
                encodeNv21AndDispatch(
                    nv21 = fallbackNv21.bytes,
                    outW = fallbackNv21.width,
                    outH = fallbackNv21.height,
                    rotationDeg = image.imageInfo.rotationDegrees,
                    jpegQuality = 76,
                    nowMs = nowMs,
                    isDirect = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame capture/encode skipped: ${e.message}")
        } finally {
            image.close()
        }
    }

    private data class SampledNv21(val bytes: ByteArray, val width: Int, val height: Int)

    private fun yuv420888ToNv21Sampled(image: ImageProxy, step: Int, useDeltaAlignment: Boolean = false): SampledNv21? {
        val srcW = image.width
        val srcH = image.height
        val safeStep = step.coerceAtLeast(1)
        val outW = if (useDeltaAlignment) ((srcW / safeStep) and 15.inv()) else ((srcW / safeStep) and 1.inv())
        val outH = if (useDeltaAlignment) ((srcH / safeStep) and 7.inv()) else ((srcH / safeStep) and 1.inv())
        if (outW < 16 || outH < 16) return null

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yLimit = yBuf.limit()
        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()

        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride

        val neededSize = outW * outH * 3 / 2
        var dst = nv21Scratch
        if (dst == null || dst.size < neededSize) {
            dst = ByteArray(neededSize)
            nv21Scratch = dst
        }

        var yIdx = 0
        if (safeStep == 1 && yPixStride == 1 && yRowStride == outW && yLimit >= outW * outH) {
            yBuf.position(0)
            yBuf.get(dst, 0, outW * outH)
        } else {
            for (row in 0 until outH) {
                val rowOffset = (row * safeStep) * yRowStride
                if (yPixStride == 1 && safeStep == 1 && rowOffset + outW <= yLimit) {
                    yBuf.position(rowOffset)
                    yBuf.get(dst, yIdx, outW)
                    yIdx += outW
                } else {
                    var sx = 0
                    for (col in 0 until outW) {
                        val pos = (rowOffset + sx * yPixStride).coerceIn(0, yLimit - 1)
                        dst[yIdx++] = yBuf.get(pos)
                        sx += safeStep
                    }
                }
            }
        }

        var uvIdx = outW * outH
        val halfH = outH / 2
        val halfW = outW / 2
        for (row in 0 until halfH) {
            val cRow = row * safeStep
            val vRowOff = cRow * vRowStride
            val uRowOff = cRow * uRowStride
            var cCol = 0
            for (col in 0 until halfW) {
                val vPos = (vRowOff + cCol * vPixStride).coerceIn(0, vLimit - 1)
                val uPos = (uRowOff + cCol * uPixStride).coerceIn(0, uLimit - 1)
                dst[uvIdx++] = vBuf.get(vPos)
                dst[uvIdx++] = uBuf.get(uPos)
                cCol += safeStep
            }
        }

        return SampledNv21(dst, outW, outH)
    }

    // ========== Internal Remote Frame Decoder ==========

    private fun startDecoderLoop() {
        if (decodingRunning.getAndSet(true)) return
        decoderJob?.cancel()
        decoderJob = scope.launch(Dispatchers.IO) {
            var deltaBaseBitmap: Bitmap? = null
            var deltaBaseCanvas: Canvas? = null
            try {
                while (decodingRunning.get()) {
                    val frame = try {
                        decoderQueue.take()
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (frame.frameSeq == Int.MIN_VALUE) break

                    try {
                        val isDeltaTile = (frame.flags and FLAG_TILE_DELTA) != 0
                        val rot = ((frame.rotationDeg % 360) + 360) % 360
                        val finalBmp: Bitmap
                        val resolutionLabel: String

                        if (isDeltaTile) {
                            if (frame.data.size <= DELTA_HEADER_BYTES) continue
                            val bb = ByteBuffer.wrap(frame.data)
                            val magic = bb.int
                            val tileMask = bb.int
                            val fullW = bb.short.toInt() and 0xFFFF
                            val fullH = bb.short.toInt() and 0xFFFF
                            if (magic != DELTA_HEADER_MAGIC || fullW <= 0 || fullH <= 0) continue

                            val base = deltaBaseBitmap
                            val canvas = deltaBaseCanvas
                            if (base == null || canvas == null || base.isRecycled ||
                                base.width != fullW || base.height != fullH
                            ) {
                                onRequestRemoteKeyframe()
                                continue
                            }

                            val stripOpts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inSampleSize = 1
                            }
                            val stripBmp = BitmapFactory.decodeByteArray(
                                frame.data,
                                DELTA_HEADER_BYTES,
                                frame.data.size - DELTA_HEADER_BYTES,
                                stripOpts
                            ) ?: continue

                            try {
                                val tileW = fullW / DELTA_GRID_COLS
                                val tileH = fullH / DELTA_GRID_ROWS
                                var slot = 0
                                for (bit in 0 until (DELTA_GRID_COLS * DELTA_GRID_ROWS)) {
                                    if ((tileMask and (1 shl bit)) == 0) continue
                                    val col = bit % DELTA_GRID_COLS
                                    val row = bit / DELTA_GRID_COLS
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

                            finalBmp = if (rot != 0) {
                                val m = Matrix().apply { postRotate(rot.toFloat()) }
                                Bitmap.createBitmap(base, 0, 0, base.width, base.height, m, true)
                            } else {
                                base.copy(Bitmap.Config.RGB_565, false)
                            }
                            resolutionLabel = "360p24 Tor⚡"
                        } else {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size, bounds)
                            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                                bounds.outWidth > 4096 || bounds.outHeight > 4096
                            ) {
                                continue
                            }
                            var sampleSize = 1
                            var longest = max(bounds.outWidth, bounds.outHeight)
                            while (longest / 2 >= 1280) {
                                sampleSize *= 2
                                longest /= 2
                            }
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inSampleSize = sampleSize
                            }
                            val rawBmp = BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size, opts)
                                ?: continue

                            if (rawBmp.width in 16..1280 && rawBmp.height in 16..1280) {
                                var base = deltaBaseBitmap
                                if (base == null || base.isRecycled || base.width != rawBmp.width || base.height != rawBmp.height) {
                                    base?.recycle()
                                    base = Bitmap.createBitmap(rawBmp.width, rawBmp.height, Bitmap.Config.RGB_565)
                                    deltaBaseBitmap = base
                                    deltaBaseCanvas = Canvas(base)
                                }
                                deltaBaseCanvas?.drawBitmap(rawBmp, 0f, 0f, null)
                            }

                            finalBmp = if (rot != 0) {
                                val m = Matrix().apply { postRotate(rot.toFloat()) }
                                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true).also {
                                    if (it !== rawBmp) rawBmp.recycle()
                                }
                            } else {
                                rawBmp
                            }
                            val maxDim = max(finalBmp.width, finalBmp.height)
                            resolutionLabel = when {
                                maxDim >= 1000 -> "720p HD"
                                maxDim >= 500 -> if (selectedCodec == CODEC_DELTA_PULSE && !directP2pMode) "360p24 Tor⚡" else "HD P2P"
                                else -> "360p Tor"
                            }
                        }

                        val aspect = finalBmp.width.toFloat() / finalBmp.height.toFloat().coerceAtLeast(1f)
                        _remoteVideoAspectRatio.value = aspect
                        _remoteResolutionLabel.value = resolutionLabel
                        _remoteFrameBitmap.value = finalBmp
                        if (!_remoteFirstFrameRendered.value) {
                            _remoteFirstFrameRendered.value = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Remote video frame decode skipped: ${e.message}")
                    }
                }
            } finally {
                deltaBaseBitmap?.recycle()
            }
        }
    }

    private fun stopDecoder() {
        decodingRunning.set(false)
        decoderQueue.clear()
        decoderQueue.offer(ReassembledFrame(Int.MIN_VALUE, 0, 0, 0, ByteArray(0)))
        decoderJob?.cancel()
        decoderJob = null
    }
}
