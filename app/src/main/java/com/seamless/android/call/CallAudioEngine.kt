@file:Suppress("DEPRECATION")
package com.seamless.android.call

import android.content.Context
import android.media.AudioAttributes
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
import android.os.PowerManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-duplex low-latency 16 kHz Wideband HD Voice engine engineered for:
 * 1. Direct P2P UDP sockets (20 ms frames, ultra-low latency)
 * 2. Tor v3 .onion TCP circuits & Active-Active Multipath (40 ms self-contained IMA-ADPCM frames
 *    that fit inside a single 514-byte Tor cell, paired with Smart VAD/DTX silence suppression,
 *    128-slot sliding-window multipath deduplication, Waveform Packet Loss Concealment (PLC),
 *    and a WebRTC NetEq-inspired Adaptive Catch-Up Jitter Buffer).
 */
class CallAudioEngine(
    private val context: Context,
    private val onEncodedFrameReady: (sequence: Long, timestampMs: Long, payload: ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "CallAudioEngine"

        // 16,000 Hz High-Res Wideband HD Voice:
        // - 40 ms frame = 640 samples = 324 bytes ADPCM (fits inside 1 Tor cell & 1 UDP packet)
        // - 20 ms frame = 320 samples = 164 bytes ADPCM (pure Direct P2P ultra-low latency)
        const val SAMPLE_RATE_HZ = 16_000
        const val TOR_FRAME_DURATION_MS = 40
        const val P2P_FRAME_DURATION_MS = 20

        const val TOR_SAMPLES_PER_FRAME = (SAMPLE_RATE_HZ * TOR_FRAME_DURATION_MS) / 1000 // 640 samples
        const val P2P_SAMPLES_PER_FRAME = (SAMPLE_RATE_HZ * P2P_FRAME_DURATION_MS) / 1000 // 320 samples

        private const val DEDUP_WINDOW_SIZE = 128
        private const val DEDUP_WINDOW_MASK = DEDUP_WINDOW_SIZE - 1
    }

    private data class QueuedAudioFrame(
        val sequence: Long,
        val timestampMs: Long,
        val pcm: ShortArray,
        val voiceLevel: Float,
        val receivedAtMs: Long = System.currentTimeMillis()
    ) : Comparable<QueuedAudioFrame> {
        override fun compareTo(other: QueuedAudioFrame): Int = this.sequence.compareTo(other.sequence)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val speakerphone = AtomicBoolean(false)
    private val useDirectP2pFraming = AtomicBoolean(false)

    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false

    private val outboundSequence = AtomicLong(0L)
    private val jitterQueue = PriorityBlockingQueue<QueuedAudioFrame>(64)
    private val dedupLock = Any()
    private val recentSequences = LongArray(DEDUP_WINDOW_SIZE) { -1L }

    private val _localVoiceLevel = MutableStateFlow(0f)
    val localVoiceLevel: StateFlow<Float> = _localVoiceLevel.asStateFlow()

    private val _remoteVoiceLevel = MutableStateFlow(0f)
    val remoteVoiceLevel: StateFlow<Float> = _remoteVoiceLevel.asStateFlow()

    private val _jitterBufferMs = MutableStateFlow(0)
    val jitterBufferMs: StateFlow<Int> = _jitterBufferMs.asStateFlow()

    private val _estimatedOneWayLatencyMs = MutableStateFlow(0)
    val estimatedOneWayLatencyMs: StateFlow<Int> = _estimatedOneWayLatencyMs.asStateFlow()

    fun start(initialSpeakerphone: Boolean = false, isDirectP2p: Boolean = false): Boolean {
        if (!running.compareAndSet(false, true)) return true

        muted.set(false)
        speakerphone.set(initialSpeakerphone)
        useDirectP2pFraming.set(isDirectP2p)
        outboundSequence.set(0L)
        jitterQueue.clear()
        synchronized(dedupLock) {
            recentSequences.fill(-1L)
        }

        try {
            configureAudioSession(initialSpeakerphone)
            updateProximityLock(initialSpeakerphone)

            val minRecordBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(TOR_SAMPLES_PER_FRAME * 4)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecordBuf * 2
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                running.set(false)
                restoreAudioSession()
                return false
            }
            audioRecord = record
            attachHardwareDsp(record.audioSessionId)

            val minTrackBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(TOR_SAMPLES_PER_FRAME * 4)

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
                .setBufferSizeInBytes(minTrackBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(record.audioSessionId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val track = trackBuilder.build()
            audioTrack = track
            record.startRecording()
            track.play()

            // Re-assert communication device routing after active streams are started (Android 12-15 HAL requirement)
            setSpeakerphone(initialSpeakerphone)

            captureJob = scope.launch(Dispatchers.IO) { runCaptureLoop(record) }
            playbackJob = scope.launch(Dispatchers.IO) { runPlaybackLoop(track) }

            Log.i(TAG, "🎙️ CallAudioEngine started (sampleRate=${SAMPLE_RATE_HZ}Hz, speaker=$initialSpeakerphone, directP2P=$isDirectP2p)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CallAudioEngine: ${e.message}", e)
            stop()
            return false
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        captureJob?.cancel()
        playbackJob?.cancel()
        captureJob = null
        playbackJob = null
        jitterQueue.clear()
        synchronized(dedupLock) {
            recentSequences.fill(-1L)
        }
        _localVoiceLevel.value = 0f
        _remoteVoiceLevel.value = 0f
        _jitterBufferMs.value = 0

        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { gainControl?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        releaseProximityLock()
        restoreAudioSession()
        Log.i(TAG, "🛑 CallAudioEngine stopped")
    }

    fun setMuted(isMuted: Boolean) {
        muted.set(isMuted)
        if (isMuted) {
            _localVoiceLevel.value = 0f
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        speakerphone.set(enabled)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetDevice = if (enabled) {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    val externalTypes = listOf(
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    )
                    externalTypes.firstNotNullOfOrNull { extType ->
                        devices.firstOrNull { it.type == extType }
                    } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                }
                if (targetDevice != null) {
                    val ok = audioManager.setCommunicationDevice(targetDevice)
                    if (!ok) {
                        audioManager.isSpeakerphoneOn = enabled
                    }
                } else {
                    audioManager.isSpeakerphoneOn = enabled
                }
            } else {
                audioManager.isSpeakerphoneOn = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Speakerphone switch fallback: ${e.message}")
            try { audioManager.isSpeakerphoneOn = enabled } catch (_: Exception) {}
        }
        updateProximityLock(enabled)
    }

    fun setTransportMode(isDirectP2p: Boolean) {
        useDirectP2pFraming.set(isDirectP2p)
    }

    /**
     * Feed an incoming encoded IMA-ADPCM voice frame from either Tor or Direct P2P.
     * Uses a 128-slot sliding sequence deduplicator so Active-Active Multipath (UDP + Tor)
     * plays whichever copy arrives first and silently drops the duplicate.
     */
    fun onRemoteAudioFrame(sequence: Long, timestampMs: Long, encodedData: ByteArray) {
        if (!running.get() || encodedData.size < 6) return

        synchronized(dedupLock) {
            val slot = (sequence.toInt() and DEDUP_WINDOW_MASK)
            if (recentSequences[slot] == sequence) {
                return // Exact duplicate from multipath redundant stream
            }
            recentSequences[slot] = sequence
        }

        val decoded = ImaAdpcmCodec.decode(encodedData) ?: return
        val now = System.currentTimeMillis()
        val rawDelta = (now - timestampMs).toInt()
        if (rawDelta in 5..5000) {
            _estimatedOneWayLatencyMs.value = rawDelta
        }

        // Prevent unbounded queue growth during extreme network stalls
        while (jitterQueue.size >= 48) {
            jitterQueue.poll()
        }

        jitterQueue.offer(
            QueuedAudioFrame(
                sequence = sequence,
                timestampMs = timestampMs,
                pcm = decoded.samples,
                voiceLevel = decoded.voiceLevel,
                receivedAtMs = now
            )
        )
    }

    private fun runCaptureLoop(record: AudioRecord) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {}

        val encoder = ImaAdpcmCodec.Encoder()
        val pcmBuffer = ShortArray(TOR_SAMPLES_PER_FRAME)
        var hangoverFrames = 8
        var silentSkippedCount = 0

        while (running.get() && scope.isActive) {
            val directMode = useDirectP2pFraming.get()
            val targetSamples = if (directMode) {
                P2P_SAMPLES_PER_FRAME
            } else {
                TOR_SAMPLES_PER_FRAME
            }

            var totalRead = 0
            while (totalRead < targetSamples && running.get()) {
                val read = record.read(pcmBuffer, totalRead, targetSamples - totalRead)
                if (read <= 0) {
                    try { Thread.sleep(10L) } catch (_: Exception) {}
                    break
                }
                totalRead += read
            }
            if (totalRead < targetSamples || !running.get()) continue

            val level: Float
            if (muted.get()) {
                pcmBuffer.fill(0, 0, targetSamples)
                level = 0f
                _localVoiceLevel.value = 0f
            } else {
                applySoftKneeSpeechGain(pcmBuffer, targetSamples)
                level = computeVoiceLevel(pcmBuffer, targetSamples)
                _localVoiceLevel.value = level
            }

            // Smart VAD + Discontinuous Transmission (DTX) when on Tor circuit:
            // Avoids flooding the 6-hop Tor TCP circuit with silent frames while listening,
            // keeping the Tor pipe completely unbloated for immediate speech bursts.
            if (level >= 0.014f) {
                hangoverFrames = 7 // ~280ms post-speech hangover tail
                silentSkippedCount = 0
            } else if (!directMode) {
                if (hangoverFrames > 0) {
                    hangoverFrames--
                } else {
                    silentSkippedCount++
                    // During silence on Tor, send 1 comfort-sync packet every 10 frames (~400ms)
                    if (silentSkippedCount % 10 != 0) {
                        continue
                    }
                }
            }

            val encoded = encoder.encode(pcmBuffer, targetSamples)
            val seq = outboundSequence.incrementAndGet()
            val now = System.currentTimeMillis()
            try {
                onEncodedFrameReady(seq, now, encoded)
            } catch (e: Exception) {
                Log.w(TAG, "Error dispatching encoded audio frame: ${e.message}")
            }
        }
    }

    private fun runPlaybackLoop(track: AudioTrack) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {}

        var prebuffering = true
        var lastPlayedSequence = -1L
        var lastTailSample: Short = 0
        var lastVoicedPcm: ShortArray? = null
        var plcConsecutiveCount = 0
        var lastUnderrunCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) track.underrunCount else 0
        var adaptiveExtraPrebuffer = 0
        val comfortSilence = ShortArray(P2P_SAMPLES_PER_FRAME)

        while (running.get() && scope.isActive) {
            val directMode = useDirectP2pFraming.get()
            val frameMs = if (directMode) P2P_FRAME_DURATION_MS else TOR_FRAME_DURATION_MS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val underruns = track.underrunCount
                if (underruns > lastUnderrunCount) {
                    lastUnderrunCount = underruns
                    adaptiveExtraPrebuffer = (adaptiveExtraPrebuffer + 1).coerceAtMost(2)
                }
            }

            val minPrebufferFrames = (if (directMode) 2 else 3) + adaptiveExtraPrebuffer
            val softMaxFrames = if (directMode) 7 else 9    // ~140ms on P2P, ~360ms on Tor
            val hardMaxFrames = if (directMode) 14 else 16  // ~280ms on P2P, ~640ms on Tor

            val currentQueueSize = jitterQueue.size
            _jitterBufferMs.value = currentQueueSize * frameMs

            if (prebuffering) {
                if (currentQueueSize < minPrebufferFrames) {
                    for (i in comfortSilence.indices) {
                        lastTailSample = ((lastTailSample * 7) / 8).toShort()
                        comfortSilence[i] = lastTailSample
                    }
                    _remoteVoiceLevel.value = 0f
                    track.write(comfortSilence, 0, comfortSilence.size)
                    continue
                }
                prebuffering = false
                plcConsecutiveCount = 0
            }

            // Hard cap: if Tor circuit paused and delivered a large burst, drop stale backlog
            // immediately to stay locked onto live conversation.
            while (jitterQueue.size > hardMaxFrames) {
                val dropped = jitterQueue.poll()
                if (dropped != null && dropped.sequence > lastPlayedSequence) {
                    lastPlayedSequence = dropped.sequence
                }
            }

            var frame = jitterQueue.poll()
            // Strictly drop any frame at or behind lastPlayedSequence so late packets
            // never cause time-reversal glitches.
            while (frame != null && lastPlayedSequence != -1L && frame.sequence <= lastPlayedSequence) {
                frame = jitterQueue.poll()
            }

            if (frame == null) {
                // Waveform Packet Loss Concealment (PLC): bridge 1-2 missing frame slots smoothly
                // before re-entering prebuffering so Tor cell boundaries never click or chop.
                val prevPcm = lastVoicedPcm
                if (plcConsecutiveCount < 2 && prevPcm != null && prevPcm.isNotEmpty()) {
                    plcConsecutiveCount++
                    val plcFrame = synthesizePlcFrame(prevPcm, plcConsecutiveCount)
                    lastTailSample = plcFrame[plcFrame.lastIndex]
                    _remoteVoiceLevel.value *= 0.65f
                    track.write(plcFrame, 0, plcFrame.size)
                    continue
                }

                prebuffering = true
                for (i in comfortSilence.indices) {
                    lastTailSample = ((lastTailSample * 7) / 8).toShort()
                    comfortSilence[i] = lastTailSample
                }
                _remoteVoiceLevel.value = 0f
                track.write(comfortSilence, 0, comfortSilence.size)
                continue
            }

            plcConsecutiveCount = 0

            // Soft catch-up: if queue exceeds softMaxFrames, drop silent frames or time-compress
            // speech by 12.5% so latency never drifts upward over a long call.
            val backlog = jitterQueue.size
            if (backlog >= softMaxFrames && frame.voiceLevel < 0.03f) {
                val nextFrame = jitterQueue.poll()
                if (nextFrame != null) {
                    frame = nextFrame
                }
            }

            if (frame.sequence > lastPlayedSequence) {
                lastPlayedSequence = frame.sequence
            }
            _remoteVoiceLevel.value = frame.voiceLevel

            val pcmToPlay = if (jitterQueue.size >= softMaxFrames) {
                timeCompressPcm(frame.pcm)
            } else {
                frame.pcm
            }

            if (pcmToPlay.isNotEmpty()) {
                lastVoicedPcm = pcmToPlay
                lastTailSample = pcmToPlay[pcmToPlay.lastIndex]
                track.write(pcmToPlay, 0, pcmToPlay.size)
            }
        }
    }

    /**
     * Synthesizes a smooth attenuated continuation frame for Packet Loss Concealment (PLC).
     */
    private fun synthesizePlcFrame(previousPcm: ShortArray, plcIndex: Int): ShortArray {
        val attenuation = if (plcIndex == 1) 0.72f else 0.45f
        val out = ShortArray(previousPcm.size)
        val n = previousPcm.size
        for (i in 0 until n) {
            // Crossfade tail-to-head so phase remains continuous without clicks
            val srcIdx = (n / 2 + i) % n
            val env = attenuation * (1f - (i.toFloat() / n) * 0.25f)
            out[i] = (previousPcm[srcIdx] * env).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /**
     * Soft-knee microphone speech gain booster (~1.65x boost with smooth saturation limiter)
     * so quiet speech is loud and intelligible on every Android OEM handset.
     */
    private fun applySoftKneeSpeechGain(pcm: ShortArray, length: Int) {
        for (i in 0 until length) {
            val s = pcm[i].toInt()
            // Noise gate for tiny ADC hiss below ±90
            if (s in -90..90) {
                pcm[i] = (s / 2).toShort()
                continue
            }
            val boosted = (s * 13) / 8 // 1.625x linear speech boost
            val limited = when {
                boosted > 24000 -> 24000 + ((boosted - 24000) / 4)
                boosted < -24000 -> -24000 + ((boosted + 24000) / 4)
                else -> boosted
            }
            pcm[i] = limited.coerceIn(-32767, 32767).toShort()
        }
    }

    /**
     * Smooth 8:7 overlap time-compression (12.5% faster playback) used to drain jitter backlog
     * without pitch-clipping artifacts.
     */
    private fun timeCompressPcm(input: ShortArray): ShortArray {
        if (input.size < 16) return input
        val outSize = (input.size * 7) / 8
        val out = ShortArray(outSize)
        var inIdx = 0
        var outIdx = 0
        while (outIdx < outSize && inIdx < input.size) {
            if ((inIdx and 7) == 6 && inIdx + 1 < input.size) {
                out[outIdx++] = ((input[inIdx].toInt() + input[inIdx + 1].toInt()) / 2).toShort()
                inIdx += 2
            } else {
                out[outIdx++] = input[inIdx++]
            }
        }
        return out
    }

    private fun computeVoiceLevel(pcm: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until length) {
            val normalized = pcm[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / length).toFloat()
        return (rms * 3.8f).coerceIn(0f, 1f)
    }

    private fun attachHardwareDsp(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware AEC unavailable: ${e.message}")
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware NS unavailable: ${e.message}")
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware AGC unavailable: ${e.message}")
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
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Ensure STREAM_VOICE_CALL is audible if user previously turned call volume all the way down
            try {
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                if (maxVol > 0 && curVol <= 1) {
                    val targetVol = (maxVol * 0.65f).toInt().coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVol, 0)
                }
            } catch (_: Exception) {}

            setSpeakerphone(enableSpeaker)
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure audio mode: ${e.message}")
        }
    }

    private fun restoreAudioSession() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.isSpeakerphoneOn = previousSpeakerphone
            audioManager.mode = previousAudioMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                audioManager.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore audio session: ${e.message}")
        }
    }

    private fun updateProximityLock(isSpeakerphone: Boolean) {
        if (isSpeakerphone) {
            releaseProximityLock()
            return
        }
        try {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                val lock = proximityWakeLock ?: powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Seamless:VoiceCallProximity"
                ).also { proximityWakeLock = it }
                if (!lock.isHeld) {
                    lock.acquire(60 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Proximity wake lock unavailable: ${e.message}")
        }
    }

    private fun releaseProximityLock() {
        try {
            proximityWakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        proximityWakeLock = null
    }
}

/**
 * Self-contained 4-bit IMA-ADPCM Speech Codec.
 * Each frame carries a 4-byte header:
 * - [0..1]: Predictor state (16-bit signed BE) prior to sample 0
 * - [2]: Step index (0..88) prior to sample 0
 * - [3]: Quantized voice level (0..255) for UI waveform rendering
 * - [4..N]: 2 nibbles per byte (low nibble = even sample, high nibble = odd sample)
 */
object ImaAdpcmCodec {
    private val INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    data class DecodedFrame(
        val samples: ShortArray,
        val voiceLevel: Float
    )

    class Encoder {
        private var predictor = 0
        private var stepIndex = 0

        fun encode(pcm: ShortArray, sampleCount: Int): ByteArray {
            val evenCount = sampleCount and 0x7FFFFFFE
            val out = ByteArray(4 + evenCount / 2)

            // Snapshot state at start of frame so decoder can decode this frame independently
            out[0] = ((predictor shr 8) and 0xFF).toByte()
            out[1] = (predictor and 0xFF).toByte()
            out[2] = stepIndex.toByte()

            var peak = 0
            var outIdx = 4
            var i = 0
            while (i < evenCount) {
                val s0 = pcm[i].toInt()
                val s1 = pcm[i + 1].toInt()
                peak = maxOf(peak, abs(s0), abs(s1))

                val nibble0 = encodeSample(s0)
                val nibble1 = encodeSample(s1)
                out[outIdx++] = ((nibble0 and 0x0F) or ((nibble1 and 0x0F) shl 4)).toByte()
                i += 2
            }

            val levelByte = min(255, (peak * 255) / 20000)
            out[3] = levelByte.toByte()
            return out
        }

        private fun encodeSample(sample: Int): Int {
            val step = STEP_TABLE[stepIndex]
            var diff = sample - predictor
            var nibble = 0
            if (diff < 0) {
                nibble = 8
                diff = -diff
            }
            var vpdiff = step shr 3
            if (diff >= step) {
                nibble = nibble or 4
                diff -= step
                vpdiff += step
            }
            if (diff >= (step shr 1)) {
                nibble = nibble or 2
                diff -= (step shr 1)
                vpdiff += (step shr 1)
            }
            if (diff >= (step shr 2)) {
                nibble = nibble or 1
                vpdiff += (step shr 2)
            }

            if ((nibble and 8) != 0) {
                predictor -= vpdiff
            } else {
                predictor += vpdiff
            }
            predictor = predictor.coerceIn(-32768, 32767)
            stepIndex = (stepIndex + INDEX_TABLE[nibble]).coerceIn(0, 88)
            return nibble
        }
    }

    fun decode(data: ByteArray): DecodedFrame? {
        if (data.size < 6) return null
        var predictor = ((data[0].toInt() shl 8) or (data[1].toInt() and 0xFF)).toShort().toInt()
        var stepIndex = (data[2].toInt() and 0xFF).coerceIn(0, 88)
        val levelByte = data[3].toInt() and 0xFF
        val voiceLevel = (levelByte / 255f).coerceIn(0f, 1f)

        val payloadBytes = data.size - 4
        val out = ShortArray(payloadBytes * 2)
        var outIdx = 0

        for (bIdx in 4 until data.size) {
            val packed = data[bIdx].toInt() and 0xFF
            val nibble0 = packed and 0x0F
            val nibble1 = (packed shr 4) and 0x0F

            for (pass in 0..1) {
                val nibble = if (pass == 0) nibble0 else nibble1
                val step = STEP_TABLE[stepIndex]
                var vpdiff = step shr 3
                if ((nibble and 4) != 0) vpdiff += step
                if ((nibble and 2) != 0) vpdiff += (step shr 1)
                if ((nibble and 1) != 0) vpdiff += (step shr 2)

                if ((nibble and 8) != 0) {
                    predictor -= vpdiff
                } else {
                    predictor += vpdiff
                }
                predictor = predictor.coerceIn(-32768, 32767)
                stepIndex = (stepIndex + INDEX_TABLE[nibble]).coerceIn(0, 88)
                out[outIdx++] = predictor.toShort()
            }
        }
        return DecodedFrame(out, voiceLevel)
    }
}
