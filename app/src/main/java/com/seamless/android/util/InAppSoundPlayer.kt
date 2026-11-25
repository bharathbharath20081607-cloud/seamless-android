package com.seamless.android.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import com.seamless.android.R
import com.seamless.android.call.CallState
import com.seamless.android.call.SeamlessCallManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Plays the subtle Seamless incoming-message chime when a message arrives
 * while the user is actively viewing that chat in the foreground.
 */
object InAppSoundPlayer {
    private const val TAG = "InAppSoundPlayer"
    private const val MIN_INTERVAL_MS = 600L

    private val lastPlayedAtMs = AtomicLong(0L)
    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "seamless-inapp-chime").apply { isDaemon = true }
    }

    fun playIncomingMessageSound(context: Context) {
        val appContext = context.applicationContext

        val inCall = runCatching {
            val state = SeamlessCallManager.getInstance(appContext).sessionFlow.value?.state
            state == CallState.OUTGOING_DIALING ||
                state == CallState.OUTGOING_RINGING ||
                state == CallState.INCOMING_RINGING ||
                state == CallState.ACTIVE
        }.getOrDefault(false)
        if (inCall) return

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val prev = lastPlayedAtMs.get()
        if (prev > 0L && now - prev < MIN_INTERVAL_MS) return
        if (!lastPlayedAtMs.compareAndSet(prev, now)) return

        audioExecutor.execute {
            var player: MediaPlayer? = null
            try {
                val afd = appContext.resources.openRawResourceFd(R.raw.seamless_notification) ?: return@execute
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    afd.use { fd ->
                        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                    setOnCompletionListener { mp ->
                        runCatching { mp.release() }
                    }
                    setOnErrorListener { mp, _, _ ->
                        runCatching { mp.release() }
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to play foreground chime: ${e.message}")
                runCatching { player?.release() }
            }
        }
    }
}
