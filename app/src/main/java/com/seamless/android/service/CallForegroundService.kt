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
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.seamless.android.MainActivity
import com.seamless.android.R
import com.seamless.android.call.CallSessionInfo
import com.seamless.android.call.CallState
import com.seamless.android.call.CallTransportMode
import com.seamless.android.call.SeamlessCallManager
import com.seamless.android.receiver.CallNotificationActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android 14-16 compliant foreground service for P2P + Tor voice and video calls.
 *
 * - Uses native NotificationCompat.CallStyle with SystemUI chronometer (zero 1 Hz notification spam)
 * - Routes Decline/Hang Up through CallNotificationActionReceiver (BroadcastReceiver)
 * - Checks NotificationManager.canUseFullScreenIntent() on API 34+
 * - Declares phoneCall|microphone|camera|remoteMessaging FGS types according to granted permissions
 * - Periodically renews CPU WakeLock so calls over 1 hour never drop CPU
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val CHANNEL_ID = "seamless_voice_call_channel"
        private const val NOTIFICATION_ID = 8842
        private const val WAKELOCK_RENEW_INTERVAL_MS = 45 * 60 * 1000L

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

    private data class NotificationKey(
        val callId: String,
        val state: CallState,
        val transport: CallTransportMode,
        val isVideo: Boolean,
        val isLocalVideo: Boolean,
        val isMuted: Boolean,
        val peerName: String
    )

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateCollectorJob: Job? = null
    private var wakeLockRenewJob: Job? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
        startWakeLockRenewal()
        promoteToForeground(buildCallNotification(SeamlessCallManager.getInstance(applicationContext).sessionFlow.value))

        val callManager = SeamlessCallManager.getInstance(applicationContext)
        var lastState: CallState? = null
        var lastNotifKey: NotificationKey? = null
        stateCollectorJob = scope.launch {
            callManager.sessionFlow.collect { session ->
                if (session == null || session.state == CallState.IDLE) {
                    stopSelf()
                } else {
                    if (session.state == CallState.INCOMING_RINGING && session.state != lastState) {
                        wakeScreenForIncomingCall()
                    }
                    val currentKey = NotificationKey(
                        callId = session.callId,
                        state = session.state,
                        transport = session.transportMode,
                        isVideo = session.isVideoCall,
                        isLocalVideo = session.isLocalVideoEnabled,
                        isMuted = session.isMuted,
                        peerName = session.peerNickname
                    )
                    val stateChanged = session.state != lastState
                    val mediaFgsChanged = lastNotifKey?.isLocalVideo != currentKey.isLocalVideo
                    if (stateChanged || mediaFgsChanged || currentKey != lastNotifKey) {
                        val notification = buildCallNotification(session)
                        if (stateChanged && session.state == CallState.ACTIVE || mediaFgsChanged) {
                            promoteToForeground(notification, session)
                        } else {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(NOTIFICATION_ID, notification)
                        }
                        lastNotifKey = currentKey
                        lastState = session.state
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callManager = SeamlessCallManager.getInstance(applicationContext)
        val session = callManager.sessionFlow.value
        when (intent?.action) {
            ACTION_ACCEPT_CALL -> {
                promoteToForeground(buildCallNotification(session), session)
                callManager.acceptIncomingCall()
            }
            ACTION_DECLINE_CALL -> callManager.declineIncomingCall()
            ACTION_END_CALL -> callManager.endCall("Call ended", notifyPeer = true)
            else -> promoteToForeground(buildCallNotification(session), session)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateCollectorJob?.cancel()
        wakeLockRenewJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(
        notification: Notification,
        session: CallSessionInfo? = SeamlessCallManager.getInstance(applicationContext).sessionFlow.value
    ) {
        try {
            val isActiveOrDialing = session?.state == CallState.ACTIVE ||
                session?.state == CallState.OUTGOING_DIALING ||
                session?.state == CallState.OUTGOING_RINGING
            val hasMicPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val hasCamPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            val wantsCam = session?.isLocalVideoEnabled == true || session?.isVideoCall == true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                if (isActiveOrDialing && hasMicPerm) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (isActiveOrDialing && wantsCam && hasCamPerm) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                if (isActiveOrDialing && hasMicPerm) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (isActiveOrDialing && wantsCam && hasCamPerm) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service type fallback: ${e.message}")
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildCallNotification(session: CallSessionInfo?): Notification {
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
        val transportBadge = when {
            session?.forceTorOnly == true -> "Tor Onion Strict"
            session?.transportMode == CallTransportMode.DIRECT_P2P -> "Direct P2P"
            else -> "Tor Onion P2P"
        }
        val callKind = if (session?.isVideoCall == true) "Video" else "Voice"
        val muteTag = if (session?.isMuted == true) " • Muted" else ""

        val subtitle = when (session?.state) {
            CallState.INCOMING_RINGING -> "Incoming Encrypted $callKind Call ($transportBadge)"
            CallState.OUTGOING_DIALING -> "Connecting $transportBadge circuit..."
            CallState.OUTGOING_RINGING -> "Ringing via $transportBadge..."
            CallState.ACTIVE -> "Encrypted $callKind • $transportBadge$muteTag"
            CallState.ENDED -> session.statusMessage.ifBlank { "Call ended" }
            else -> "End-to-End Encrypted"
        }

        val callerPerson = Person.Builder()
            .setName(peerName)
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(peerName)
            .setContentText(subtitle)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(session?.state != CallState.INCOMING_RINGING)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (session?.state == CallState.ACTIVE && session.connectedAtMs != null) {
            builder.setUsesChronometer(true)
            builder.setWhen(session.connectedAtMs)
        }

        if (session?.state == CallState.INCOMING_RINGING) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canFsi = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm.canUseFullScreenIntent()
            if (canFsi) {
                builder.setFullScreenIntent(contentPendingIntent, true)
            }
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
            val declineIntent = Intent(this, CallNotificationActionReceiver::class.java).apply {
                action = ACTION_DECLINE_CALL
            }
            val declinePi = PendingIntent.getBroadcast(
                this,
                2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(callerPerson, declinePi, acceptPi)
                    .setIsVideo(session.isVideoCall)
            )
        } else {
            val endIntent = Intent(this, CallNotificationActionReceiver::class.java).apply {
                action = ACTION_END_CALL
            }
            val endPi = PendingIntent.getBroadcast(
                this,
                3,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(callerPerson, endPi)
                    .setIsVideo(session?.isVideoCall == true)
            )
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Encrypted Voice & Video Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "P2P and Tor Onion encrypted voice and video calls"
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = cpuWakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Seamless:VoiceCallCpu").apply {
                setReferenceCounted(false)
            }.also { cpuWakeLock = it }
            wl.acquire(60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun startWakeLockRenewal() {
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(WAKELOCK_RENEW_INTERVAL_MS)
                acquireWakeLock()
            }
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
