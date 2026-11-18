package com.seamless.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.seamless.android.MainActivity
import com.seamless.android.R
import com.seamless.android.call.CallState
import com.seamless.android.call.CallTransportMode
import com.seamless.android.call.SeamlessCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps P2P + Tor voice calls alive in the background
 * and displays an ongoing call notification with Hang Up / Answer / Decline actions.
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val CHANNEL_ID = "seamless_voice_call_channel"
        private const val NOTIFICATION_ID = 8842

        const val ACTION_START_OR_UPDATE = "com.seamless.android.call.ACTION_START_OR_UPDATE"
        const val ACTION_ACCEPT_CALL = "com.seamless.android.call.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.seamless.android.call.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL = "com.seamless.android.call.ACTION_END_CALL"
        const val EXTRA_CALL_ACTION = "com.seamless.android.call.EXTRA_CALL_ACTION"

        fun start(context: Context) {
            try {
                val intent = Intent(context.applicationContext, CallForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start CallForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, CallForegroundService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop CallForegroundService: ${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateCollectorJob: Job? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
        promoteToForeground(buildCallNotification())

        val callManager = SeamlessCallManager.getInstance(applicationContext)
        var lastState: CallState? = null
        stateCollectorJob = scope.launch {
            callManager.sessionFlow.collect { session ->
                if (session == null || session.state == CallState.IDLE) {
                    stopSelf()
                } else {
                    if (session.state == CallState.INCOMING_RINGING && session.state != lastState) {
                        wakeScreenForIncomingCall()
                    }
                    val notification = buildCallNotification()
                    if (session.state != lastState && session.state == CallState.ACTIVE) {
                        promoteToForeground(notification)
                    } else {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIFICATION_ID, notification)
                    }
                    lastState = session.state
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callManager = SeamlessCallManager.getInstance(applicationContext)
        when (intent?.action) {
            ACTION_ACCEPT_CALL -> {
                promoteToForeground(buildCallNotification())
                callManager.acceptIncomingCall()
            }
            ACTION_DECLINE_CALL -> callManager.declineIncomingCall()
            ACTION_END_CALL -> callManager.endCall("Call ended", notifyPeer = true)
            else -> promoteToForeground(buildCallNotification())
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateCollectorJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(notification: Notification) {
        try {
            val session = SeamlessCallManager.getInstance(applicationContext).sessionFlow.value
            val isActiveOrDialing = session?.state == CallState.ACTIVE ||
                session?.state == CallState.OUTGOING_DIALING ||
                session?.state == CallState.OUTGOING_RINGING
            val hasMicPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgsType = if (isActiveOrDialing && hasMicPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val fgsType = if (isActiveOrDialing && hasMicPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service type fallback: ${e.message}")
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun buildCallNotification(): Notification {
        val session = SeamlessCallManager.getInstance(applicationContext).sessionFlow.value
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val peerName = session?.peerNickname?.ifBlank { "Encrypted Peer" } ?: "Encrypted Peer"
        val transportBadge = when (session?.transportMode) {
            CallTransportMode.DIRECT_P2P -> "Direct P2P"
            else -> "Tor Onion P2P"
        }

        val (title, subtitle) = when (session?.state) {
            CallState.INCOMING_RINGING -> "Incoming Call • $peerName" to "End-to-End Encrypted ($transportBadge)"
            CallState.OUTGOING_DIALING -> "Calling $peerName..." to "Connecting $transportBadge circuit..."
            CallState.OUTGOING_RINGING -> "Ringing $peerName..." to "End-to-End Encrypted ($transportBadge)"
            CallState.ACTIVE -> {
                val mins = (session.durationSeconds / 60).toString().padStart(2, '0')
                val secs = (session.durationSeconds % 60).toString().padStart(2, '0')
                "Active Call • $peerName ($mins:$secs)" to "Encrypted via $transportBadge"
            }
            CallState.ENDED -> "Call Ended • $peerName" to session.statusMessage
            else -> "Seamless Voice Call" to "End-to-End Encrypted"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (session?.state == CallState.INCOMING_RINGING) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
            builder.setFullScreenIntent(contentPendingIntent, true)
            val acceptActivityIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALL_ACTION, ACTION_ACCEPT_CALL)
            }
            val acceptPi = PendingIntent.getActivity(
                this,
                1,
                acceptActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val declineIntent = Intent(this, CallForegroundService::class.java).apply {
                action = ACTION_DECLINE_CALL
            }
            val declinePi = PendingIntent.getService(
                this,
                2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_call, "Accept", acceptPi)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePi)
        } else {
            val endIntent = Intent(this, CallForegroundService::class.java).apply {
                action = ACTION_END_CALL
            }
            val endPi = PendingIntent.getService(
                this,
                3,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hang Up", endPi)
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Encrypted Voice Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "P2P and Tor Onion encrypted voice calls"
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Seamless:VoiceCallCpu").apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun wakeScreenForIncomingCall() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Seamless:IncomingCallScreen"
            ).apply {
                setReferenceCounted(false)
                acquire(30_000L)
            }
            screenWakeLock?.let { if (it.isHeld) runCatching { it.release() } }
            screenWakeLock = wl
        } catch (e: Exception) {
            Log.w(TAG, "Screen WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            cpuWakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        cpuWakeLock = null
        try {
            screenWakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        screenWakeLock = null
    }
}
