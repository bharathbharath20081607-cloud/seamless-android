package com.seamless.android.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Multi-Party Low-Latency NetEq Voice Engine & Soft-Knee Mastering Mixer.
 *
 * Designed specifically for Android AudioFlinger + Tor Swarm Group Calls:
 * - Maintains ONE single 16 kHz AudioRecord + ONE single 16 kHz AudioTrack so hardware
 *   AcousticEchoCanceler cancels ALL remote speakers cleanly without AudioFlinger underruns.
 * - Allocates an isolated [PeerVoiceStream] per participant `senderTag4` with independent
 *   128-slot multipath deduplication, 2D Burst-FEC recovery, adaptive jitter buffering,
 *   pitch-preserving WSOLA time-scale compression, and pitch-matched autocorrelation PLC.
 * - Hysteresis VAD + 6-byte DTX comfort-noise suppression cuts idle uplink bandwidth by >90%.
 * - Active-Top-4 Float32 summing + Padé soft-knee limiter prevents clipping when multiple peers speak.
 */
class GroupCallAudioMixer(
    private val context: Context,
    private val onEncodedFrameReady: (sequence: Long, timestampMs: Long, isDtxSid: Boolean, payload: ByteArray) -> Unit
) {

    companion object {
        private const val TAG = "GroupCallAudioMixer"
        const val SAMPLE_RATE_HZ = CallAudioEngine.SAMPLE_RATE_HZ
        const val FRAME_DURATION_MS = CallAudioEngine.TOR_FRAME_DURATION_MS
        const val SAMPLES_PER_FRAME = CallAudioEngine.TOR_SAMPLES_PER_FRAME // 640 samples
        const val MAX_ACTIVE_MIX_SPEAKERS = 4

        private const val DEDUP_WINDOW_SIZE = 128
        private const val DEDUP_WINDOW_MASK = DEDUP_WINDOW_SIZE - 1
        private const val MAX_JITTER_QUEUE_FRAMES = 36
        private const val MAX_PLC_FRAMES = 4
        private const val VAD_RMS_ENTER_THRESHOLD = 165.0
        private const val VAD_RMS_STAY_THRESHOLD = 110.0
        private const val VAD_HANGOVER_FRAMES = 4 // 160 ms tail
        private const val DTX_HEARTBEAT_INTERVAL_FRAMES = 10 // 400 ms comfort keepalive

        /**
         * Pure DSP conference summing & soft-knee mastering limiter (also exercised in JVM unit tests).
         *
         * - Ranks active speaker buffers by RMS energy and mixes up to [maxActiveSpeakers] streams.
         * - Below soft knee (|x| <= 0.72 FS): strictly unity gain (single speaker preserves full dynamics).
         * - Above knee (|x| > 0.72 FS): smooth rational Padé saturation curve strictly bounded to [-32767, 32767].
         */
        fun mixActiveFramesSoftLimited(
            frames: List<ShortArray>,
            sampleCount: Int = SAMPLES_PER_FRAME,
            maxActiveSpeakers: Int = MAX_ACTIVE_MIX_SPEAKERS,
            outBuffer: ShortArray = ShortArray(sampleCount),
            gains: List<Float>? = null
        ): ShortArray {
            if (sampleCount <= 0) return outBuffer
            if (frames.isEmpty()) {
                outBuffer.fill(0, 0, min(sampleCount, outBuffer.size))
                return outBuffer
            }

            val indexedSelected: List<Pair<ShortArray, Float>> = if (frames.size <= maxActiveSpeakers) {
                frames.mapIndexed { idx, buf ->
                    val g = (gains?.getOrNull(idx) ?: 1.0f).coerceIn(0f, 2.0f)
                    buf to g
                }
            } else {
                frames
                    .mapIndexed { idx, buf ->
                        val g = (gains?.getOrNull(idx) ?: 1.0f).coerceIn(0f, 2.0f)
                        val weightedRms = computeFrameRms(buf, min(sampleCount, buf.size)) * g
                        Triple(buf, g, weightedRms)
                    }
                    .sortedByDescending { it.third }
                    .take(maxActiveSpeakers)
                    .map { it.first to it.second }
            }

            val limitN = min(sampleCount, outBuffer.size)
            val knee = 0.72f
            val headroom = 1.0f - knee // 0.28f

            for (i in 0 until limitN) {
                var sum = 0f
                for (sIdx in indexedSelected.indices) {
                    val (buf, gain) = indexedSelected[sIdx]
                    if (gain > 0.001f && i < buf.size) {
                        sum += (buf[i].toFloat() * gain) / 32768f
                    }
                }
                val mag = abs(sum)
                val shaped = if (mag <= knee) {
                    sum
                } else {
                    val u = (mag - knee) / headroom
                    val comp = knee + headroom * (u / (1.0f + u + 0.48f * u * u))
                    if (sum >= 0f) comp else -comp
                }
                outBuffer[i] = (shaped * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            }
            return outBuffer
        }

        internal fun computeFrameRms(pcm: ShortArray, len: Int): Double {
            if (len <= 0) return 0.0
            var sumSq = 0.0
            for (i in 0 until len) {
                val v = pcm[i].toDouble()
                sumSq += v * v
            }
            return sqrt(sumSq / len)
        }
    }

    private data class QueuedPeerAudio(
        val sequence: Long,
        val timestampMs: Long,
        val payload: ByteArray
    ) : Comparable<QueuedPeerAudio> {
        override fun compareTo(other: QueuedPeerAudio): Int = sequence.compareTo(other.sequence)
    }

    private inner class PeerVoiceStream(val senderTag4: Int) {
        private val seenSequences = LongArray(DEDUP_WINDOW_SIZE) { Long.MIN_VALUE }
        private val fecDecoder = CallFluxFec.Decoder(windowSize = 64)
        private val jitterQueue = PriorityBlockingQueue<QueuedPeerAudio>(24)

        @Volatile var isDirectP2p: Boolean = false
        @Volatile var voiceLevel: Float = 0f
        @Volatile var smoothedJitterMs: Double = 0.0

        private var lastArrivalNs = 0L
        private var lastPlayedSeq = Long.MIN_VALUE
        private var jitterPrimed = false
        private var consecutivePlc = 0
        private var lastGoodPcm = ShortArray(SAMPLES_PER_FRAME)
        private var hasLastGoodPcm = false
        private var plcNoiseSeed = 0x51A7BEEF xor senderTag4
        private var lowWaterTicks = 0
        private var underruns = 0
        private var lastUnderrunDecayAtMs = System.currentTimeMillis()
        private var activePullTickCount = 0

        @Synchronized
        fun markSeen(seq: Long): Boolean {
            if (seq < 0L) return false
            val slot = (seq and DEDUP_WINDOW_MASK.toLong()).toInt()
            if (seenSequences[slot] == seq) return false
            seenSequences[slot] = seq
            return true
        }

        fun onDirectAudio(seq: Long, timestampMs: Long, payload: ByteArray) {
            if (CallAudioEngine.isDtxSidFrame(payload)) {
                voiceLevel *= 0.65f
                return
            }
            enqueueVoiceLocked(seq, timestampMs, payload)
            for ((recSeq, recPayload) in fecDecoder.onVoiceFrameMulti(seq, payload)) {
                enqueueVoiceLocked(recSeq, timestampMs, recPayload)
            }
        }

        fun onFecPacket(timestampMs: Long, fecPayload: ByteArray) {
            for ((recSeq, recPayload) in fecDecoder.onFecPacketMulti(fecPayload)) {
                enqueueVoiceLocked(recSeq, timestampMs, recPayload)
            }
        }

        private fun enqueueVoiceLocked(seq: Long, timestampMs: Long, payload: ByteArray) {
            if (!markSeen(seq)) return
            val played = lastPlayedSeq
            if (played != Long.MIN_VALUE && seq <= played) return

            val nowNs = SystemClock.elapsedRealtimeNanos()
            if (lastArrivalNs != 0L) {
                val deltaMs = (nowNs - lastArrivalNs) / 1_000_000.0
                val deviation = abs(deltaMs - FRAME_DURATION_MS)
                smoothedJitterMs = smoothedJitterMs + (deviation - smoothedJitterMs) / 16.0
            }
            lastArrivalNs = nowNs

            jitterQueue.offer(QueuedPeerAudio(seq, timestampMs, payload))
            while (jitterQueue.size > MAX_JITTER_QUEUE_FRAMES) {
                jitterQueue.poll()
            }
        }

        private fun targetPrimeFrames(): Int {
            val baseTarget = if (isDirectP2p) 2 else 3
            val jitterExtra = when {
                smoothedJitterMs >= 95.0 -> 2
                smoothedJitterMs >= 50.0 -> 1
                else -> 0
            }
            val now = System.currentTimeMillis()
            if (underruns > 0 && now - lastUnderrunDecayAtMs >= 10_000L) {
                underruns = (underruns - 1).coerceAtLeast(0)
                lastUnderrunDecayAtMs = now
            }
            val underrunBoost = underruns.coerceAtMost(2)
            return (baseTarget + jitterExtra + underrunBoost).coerceIn(2, 8)
        }

        fun pullNextFrame(): ShortArray? {
            if (!jitterPrimed) {
                if (jitterQueue.size < targetPrimeFrames()) {
                    voiceLevel *= 0.85f
                    return null
                }
                jitterPrimed = true
            }

            while (true) {
                val head = jitterQueue.peek() ?: break
                if (lastPlayedSeq != Long.MIN_VALUE && head.sequence <= lastPlayedSeq) {
                    jitterQueue.poll()
                } else {
                    break
                }
            }

            val head = jitterQueue.peek()
            val frame: QueuedPeerAudio? = if (head == null) {
                null
            } else if (lastPlayedSeq != Long.MIN_VALUE && head.sequence > lastPlayedSeq + 1L) {
                // Missing packet at head of queue — briefly conceal or skip if queue is full
                if (consecutivePlc < 1 && jitterQueue.size < targetPrimeFrames() + 2) {
                    null
                } else {
                    jitterQueue.poll()
                }
            } else {
                jitterQueue.poll()
            }

            if (frame != null) {
                val target = targetPrimeFrames()
                if (jitterQueue.size <= 1) lowWaterTicks++ else lowWaterTicks = 0
                if (lowWaterTicks >= 200) {
                    underruns = (underruns - 1).coerceAtLeast(0)
                    lowWaterTicks = 0
                }
                activePullTickCount++
                // If backlog grows beyond target + 4 frames, shed stale frames or WSOLA compress
                var usePcm: ShortArray? = null
                val decoded = ImaAdpcmCodec.decode(frame.payload)
                if (decoded != null) {
                    voiceLevel = (voiceLevel * 0.45f) + (decoded.voiceLevel * 0.55f)
                    usePcm = decoded.samples
                    if (jitterQueue.size >= target + 4 && (activePullTickCount % 3 == 0)) {
                        usePcm = CallAudioEngine.wsolaTimeCompressPcm(usePcm)
                    }
                    lastGoodPcm = decoded.samples
                    hasLastGoodPcm = true
                    lastPlayedSeq = frame.sequence
                    consecutivePlc = 0
                }
                return usePcm
            }

            // Packet missing -> PLC up to MAX_PLC_FRAMES, then go silent and re-prime
            consecutivePlc++
            voiceLevel *= 0.7f
            if (hasLastGoodPcm && consecutivePlc <= MAX_PLC_FRAMES) {
                plcNoiseSeed = plcNoiseSeed * 1664525 + 1013904223
                return CallAudioEngine.synthesizePitchMatchedPlc(lastGoodPcm, consecutivePlc, plcNoiseSeed)
            }
            if (consecutivePlc > MAX_PLC_FRAMES) {
                jitterPrimed = false
                consecutivePlc = 0
                underruns = (underruns + 1).coerceAtMost(4)
                lastUnderrunDecayAtMs = System.currentTimeMillis()
                voiceLevel = 0f
            }
            return null
        }

        fun reset() {
            jitterQueue.clear()
            fecDecoder.reset()
            seenSequences.fill(Long.MIN_VALUE)
            lastPlayedSeq = Long.MIN_VALUE
            jitterPrimed = false
            consecutivePlc = 0
            hasLastGoodPcm = false
            voiceLevel = 0f
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val speakerphone = AtomicBoolean(true)
    private val outboundSequence = AtomicLong(0L)

    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphone = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var hardwareAec: AcousticEchoCanceler? = null
    private var hardwareNs: NoiseSuppressor? = null
    private var hardwareAgc: AutomaticGainControl? = null
    private var captureThread: Thread? = null
    private var mixerThread: Thread? = null

    private val peerStreams = ConcurrentHashMap<Int, PeerVoiceStream>()
    private val peerVolumeGains = ConcurrentHashMap<Int, Float>()

    private val _audioRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val audioRoute: StateFlow<AudioRoute> = _audioRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow(setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER))
    val availableRoutes: StateFlow<Set<AudioRoute>> = _availableRoutes.asStateFlow()

    private val _localVoiceLevel = MutableStateFlow(0f)
    val localVoiceLevel: StateFlow<Float> = _localVoiceLevel.asStateFlow()

    private val _peerVoiceLevels = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val peerVoiceLevels: StateFlow<Map<Int, Float>> = _peerVoiceLevels.asStateFlow()

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(initialSpeakerphone: Boolean = true, initialMuted: Boolean = false): Boolean {
        if (running.get()) return true
        if (!hasMicPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission missing")
            return false
        }

        muted.set(initialMuted)
        speakerphone.set(initialSpeakerphone)
        outboundSequence.set(0L)
        peerStreams.clear()
        _localVoiceLevel.value = 0f
        _peerVoiceLevels.value = emptyMap()

        configureAudioSession(initialSpeakerphone)

        val minRecBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLES_PER_FRAME * 8)

        val minPlayBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLES_PER_FRAME * 6)

        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                restoreAudioSession()
                return false
            }

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minPlayBuf)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val track = trackBuilder.build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                rec.release()
                track.release()
                restoreAudioSession()
                return false
            }

            attachVoiceEffects(rec.audioSessionId)
            audioRecord = rec
            audioTrack = track
            running.set(true)

            rec.startRecording()
            track.play()

            startCaptureThread(rec)
            startMixerPlaybackThread(track)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GroupCallAudioMixer: ${e.message}")
            stop()
            false
        }
    }

    fun setMuted(isMuted: Boolean) {
        muted.set(isMuted)
        if (isMuted) _localVoiceLevel.value = 0f
    }

    fun updatePeerTransport(senderTag4: Int, isDirectP2p: Boolean) {
        peerStreams[senderTag4]?.isDirectP2p = isDirectP2p
    }

    fun setPeerVolumeGain(senderTag4: Int, gain: Float) {
        val clamped = gain.coerceIn(0f, 2.0f)
        if (clamped == 1.0f) peerVolumeGains.remove(senderTag4)
        else peerVolumeGains[senderTag4] = clamped
    }

    fun getPeerVolumeGain(senderTag4: Int): Float = peerVolumeGains[senderTag4] ?: 1.0f

    fun removePeer(senderTag4: Int) {
        peerStreams.remove(senderTag4)?.reset()
        peerVolumeGains.remove(senderTag4)
        _peerVoiceLevels.value = _peerVoiceLevels.value - senderTag4
    }

    fun onRemoteAudioFrame(senderTag4: Int, sequence: Long, timestampMs: Long, payload: ByteArray) {
        if (!running.get()) return
        val stream = peerStreams.getOrPut(senderTag4) { PeerVoiceStream(senderTag4) }
        stream.onDirectAudio(sequence, timestampMs, payload)
    }

    fun onRemoteAudioFecFrame(senderTag4: Int, timestampMs: Long, fecPayload: ByteArray) {
        if (!running.get()) return
        val stream = peerStreams.getOrPut(senderTag4) { PeerVoiceStream(senderTag4) }
        stream.onFecPacket(timestampMs, fecPayload)
    }

    private fun startCaptureThread(rec: AudioRecord) {
        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val pcm = ShortArray(SAMPLES_PER_FRAME)
            val encoder = ImaAdpcmCodec.Encoder()
            val dspState = CallAudioEngine.Companion.MicDspState()
            var hangoverRemaining = 0
            var ticksSinceSid = 0
            val hwAgcOn = hardwareAgc?.enabled == true

            while (running.get()) {
                var readTotal = 0
                while (readTotal < SAMPLES_PER_FRAME && running.get()) {
                    val r = rec.read(pcm, readTotal, SAMPLES_PER_FRAME - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                if (!running.get() || readTotal < SAMPLES_PER_FRAME) continue

                val seq = outboundSequence.incrementAndGet()
                val nowMs = System.currentTimeMillis()

                if (muted.get()) {
                    _localVoiceLevel.value = 0f
                    hangoverRemaining = 0
                    ticksSinceSid++
                    if (ticksSinceSid >= DTX_HEARTBEAT_INTERVAL_FRAMES) {
                        ticksSinceSid = 0
                        onEncodedFrameReady(seq, nowMs, true, CallAudioEngine.encodeDtxSidFrame())
                    }
                    continue
                }

                CallAudioEngine.applyDcBlockAndSmoothSpeechGain(
                    pcm = pcm,
                    length = SAMPLES_PER_FRAME,
                    state = dspState,
                    hardwareAgcActive = hwAgcOn
                )

                val rms = computeFrameRms(pcm, SAMPLES_PER_FRAME)
                val normalizedLevel = (rms.toFloat() / 6500f).coerceIn(0f, 1f)
                _localVoiceLevel.value = (_localVoiceLevel.value * 0.4f) + (normalizedLevel * 0.6f)

                val isSpeech = if (hangoverRemaining > 0) {
                    rms >= VAD_RMS_STAY_THRESHOLD
                } else {
                    rms >= VAD_RMS_ENTER_THRESHOLD
                }

                if (isSpeech) {
                    hangoverRemaining = VAD_HANGOVER_FRAMES
                    ticksSinceSid = 0
                    val adpcm = encoder.encode(pcm, SAMPLES_PER_FRAME)
                    onEncodedFrameReady(seq, nowMs, false, adpcm)
                } else if (hangoverRemaining > 0) {
                    hangoverRemaining--
                    ticksSinceSid = 0
                    val adpcm = encoder.encode(pcm, SAMPLES_PER_FRAME)
                    onEncodedFrameReady(seq, nowMs, false, adpcm)
                } else {
                    _localVoiceLevel.value *= 0.55f
                    ticksSinceSid++
                    if (ticksSinceSid >= DTX_HEARTBEAT_INTERVAL_FRAMES) {
                        ticksSinceSid = 0
                        onEncodedFrameReady(seq, nowMs, true, CallAudioEngine.encodeDtxSidFrame())
                    }
                }
            }
        }, "GroupVoiceCapture").apply { start() }
    }

    private fun startMixerPlaybackThread(track: AudioTrack) {
        mixerThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val mixedOut = ShortArray(SAMPLES_PER_FRAME)
            val activeFrames = ArrayList<ShortArray>(8)
            val activeGains = ArrayList<Float>(8)
            var nextTickNs = SystemClock.elapsedRealtimeNanos()
            val tickNs = FRAME_DURATION_MS * 1_000_000L
            var tickCounter = 0

            while (running.get()) {
                nextTickNs += tickNs
                activeFrames.clear()
                activeGains.clear()

                val levelMap = HashMap<Int, Float>(peerStreams.size)
                for ((tag, stream) in peerStreams) {
                    val pcm = stream.pullNextFrame()
                    if (pcm != null) {
                        activeFrames.add(pcm)
                        activeGains.add(getPeerVolumeGain(tag))
                    }
                    levelMap[tag] = stream.voiceLevel
                }

                mixActiveFramesSoftLimited(
                    frames = activeFrames,
                    sampleCount = SAMPLES_PER_FRAME,
                    maxActiveSpeakers = MAX_ACTIVE_MIX_SPEAKERS,
                    outBuffer = mixedOut,
                    gains = activeGains
                )

                try {
                    track.write(mixedOut, 0, SAMPLES_PER_FRAME)
                } catch (_: Exception) {
                    if (!running.get()) break
                }

                tickCounter++
                if ((tickCounter and 1) == 0) { // ~12.5 Hz smooth level snapshot
                    _peerVoiceLevels.value = levelMap
                }

                val nowNs = SystemClock.elapsedRealtimeNanos()
                val sleepNs = nextTickNs - nowNs
                if (sleepNs > 1_000_000L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                    } catch (_: InterruptedException) {
                        break
                    }
                } else if (sleepNs < -tickNs * 3L) {
                    nextTickNs = nowNs
                }
            }
        }, "GroupVoiceMixer").apply { start() }
    }

    fun setAudioRoute(route: AudioRoute) {
        speakerphone.set(route == AudioRoute.SPEAKER)
        _audioRoute.value = route
        updateProximityLock(route)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = when (route) {
                    AudioRoute.BLUETOOTH -> listOf(
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET
                    )
                    AudioRoute.WIRED_HEADSET -> listOf(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET
                    )
                    AudioRoute.SPEAKER -> listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    AudioRoute.EARPIECE -> listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                }
                val device = audioManager.availableCommunicationDevices.firstOrNull { it.type in targetType }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.isSpeakerphoneOn = (route == AudioRoute.SPEAKER)
                }
            } else {
                audioManager.isSpeakerphoneOn = (route == AudioRoute.SPEAKER)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio route switch failed: ${e.message}")
        }
    }

    fun cycleAudioRoute(): AudioRoute {
        val routes = refreshAvailableRoutes()
        val ordered = listOf(
            AudioRoute.BLUETOOTH,
            AudioRoute.WIRED_HEADSET,
            AudioRoute.SPEAKER,
            AudioRoute.EARPIECE
        ).filter { it in routes }
        if (ordered.isEmpty()) return AudioRoute.SPEAKER
        val curIdx = ordered.indexOf(_audioRoute.value)
        val next = ordered[(curIdx + 1) % ordered.size]
        setAudioRoute(next)
        return next
    }

    private fun refreshAvailableRoutes(): Set<AudioRoute> {
        val set = linkedSetOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (d in outs) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> set.add(AudioRoute.BLUETOOTH)
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> set.add(AudioRoute.WIRED_HEADSET)
                }
            }
        }
        _availableRoutes.value = set
        return set
    }

    private fun defaultRoute(preferSpeaker: Boolean): AudioRoute {
        val routes = refreshAvailableRoutes()
        return when {
            AudioRoute.BLUETOOTH in routes -> AudioRoute.BLUETOOTH
            AudioRoute.WIRED_HEADSET in routes -> AudioRoute.WIRED_HEADSET
            preferSpeaker -> AudioRoute.SPEAKER
            else -> AudioRoute.EARPIECE
        }
    }

    private fun attachVoiceEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                hardwareAec = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                hardwareNs = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                hardwareAgc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware voice effect attach warning: ${e.message}")
        }
    }

    private fun configureAudioSession(enableSpeaker: Boolean) {
        try {
            previousAudioMode = audioManager.mode
            previousSpeakerphone = audioManager.isSpeakerphoneOn

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cb = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        if (!running.get()) return
                        val routes = refreshAvailableRoutes()
                        if (AudioRoute.BLUETOOTH in routes || AudioRoute.WIRED_HEADSET in routes) {
                            setAudioRoute(defaultRoute(speakerphone.get()))
                        }
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        if (!running.get()) return
                        val routes = refreshAvailableRoutes()
                        if (_audioRoute.value !in routes) {
                            setAudioRoute(defaultRoute(speakerphone.get()))
                        }
                    }
                }
                audioDeviceCallback = cb
                audioManager.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setAudioRoute(defaultRoute(enableSpeaker))
        } catch (e: Exception) {
            Log.w(TAG, "Audio session config warning: ${e.message}")
        }
    }

    private fun restoreAudioSession() {
        try {
            audioDeviceCallback?.let { runCatching { audioManager.unregisterAudioDeviceCallback(it) } }
            audioDeviceCallback = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { audioManager.clearCommunicationDevice() }
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = previousSpeakerphone
            audioManager.mode = previousAudioMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            } else {
                @Suppress("DEPRECATION")
                runCatching { audioManager.abandonAudioFocus(audioFocusListener) }
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.w(TAG, "Audio session restore warning: ${e.message}")
        }
    }

    private fun updateProximityLock(route: AudioRoute) {
        if (route != AudioRoute.EARPIECE) {
            releaseProximityLock()
            return
        }
        try {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                val lock = proximityWakeLock ?: powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Seamless:GroupCallProximity"
                ).also { proximityWakeLock = it }
                if (!lock.isHeld) {
                    lock.acquire(60 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseProximityLock() {
        try {
            proximityWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        proximityWakeLock = null
    }

    fun stop() {
        running.set(false)
        captureThread?.interrupt()
        mixerThread?.interrupt()
        captureThread = null
        mixerThread = null

        runCatching { hardwareAec?.release() }; hardwareAec = null
        runCatching { hardwareNs?.release() }; hardwareNs = null
        runCatching { hardwareAgc?.release() }; hardwareAgc = null

        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null

        peerStreams.values.forEach { it.reset() }
        peerStreams.clear()
        peerVolumeGains.clear()
        _localVoiceLevel.value = 0f
        _peerVoiceLevels.value = emptyMap()

        releaseProximityLock()
        restoreAudioSession()
    }
}
