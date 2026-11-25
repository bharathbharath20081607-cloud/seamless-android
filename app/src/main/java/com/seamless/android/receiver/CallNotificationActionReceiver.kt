package com.seamless.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seamless.android.call.SeamlessCallManager
import com.seamless.android.service.CallForegroundService

/**
 * Android 14-16 compliant BroadcastReceiver for call notification actions (Decline / Hang Up)
 * so dismissing a call never attempts a background foreground-service start.
 */
class CallNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val callManager = SeamlessCallManager.getInstance(appContext)
        when (intent?.action) {
            CallForegroundService.ACTION_DECLINE_CALL -> callManager.declineIncomingCall()
            CallForegroundService.ACTION_END_CALL -> callManager.endCall("Call ended", notifyPeer = true)
        }
    }
}
