package com.seamless.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorGroupMessaging
import com.seamless.android.net.TorMessageNotifier
import com.seamless.android.net.TorMessagingService
import com.seamless.android.service.TorForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles inline "Reply" and "Mark read" actions directly from Tor P2P notifications
 * without forcing the user to leave their current screen.
 */
class TorNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TorNotifActionReceiver"
        private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val contactId = intent.getStringExtra(TorMessageNotifier.EXTRA_TOR_CONTACT_ID)
        val groupId = intent.getStringExtra(TorMessageNotifier.EXTRA_TOR_GROUP_ID)
        val notifId = intent.getIntExtra(TorMessageNotifier.EXTRA_NOTIFICATION_ID, 0)
        val replyText = if (action == TorMessageNotifier.ACTION_DIRECT_REPLY) {
            RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(TorMessageNotifier.KEY_TEXT_REPLY)
                ?.toString()
                ?.trim()
        } else {
            null
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val notifier = TorMessageNotifier(appContext)
                when (action) {
                    TorMessageNotifier.ACTION_MARK_READ -> {
                        markConversationReadAndCancel(appContext, notifier, contactId, groupId, notifId)
                    }
                    TorMessageNotifier.ACTION_DIRECT_REPLY -> {
                        if (!replyText.isNullOrBlank()) {
                            runCatching { TorForegroundService.start(appContext) }
                            val messagingService = TorMessagingService.getInstance(appContext)
                            if (!contactId.isNullOrBlank()) {
                                markConversationReadAndCancel(appContext, notifier, contactId, null, notifId)
                                messagingService.sendMessage(contactId, replyText)
                                    .onFailure { Log.w(TAG, "Direct reply to $contactId failed: ${it.message}") }
                            } else if (!groupId.isNullOrBlank()) {
                                markConversationReadAndCancel(appContext, notifier, null, groupId, notifId)
                                TorGroupMessaging.getInstance(appContext).sendGroupMessage(groupId, replyText)
                                    .onFailure { Log.w(TAG, "Group direct reply to $groupId failed: ${it.message}") }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notification action failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun markConversationReadAndCancel(
        appContext: Context,
        notifier: TorMessageNotifier,
        contactId: String?,
        groupId: String?,
        notifId: Int
    ) {
        if (!contactId.isNullOrBlank()) {
            val contact = OnionContactManager.getInstance(appContext).getContact(contactId)
            if (contact != null) {
                runCatching { TorMessagingService.getInstance(appContext).markContactReadAndSendReceipts(contact) }
            }
            notifier.cancelContact(contactId)
        } else if (!groupId.isNullOrBlank()) {
            runCatching { TorGroupMessaging.getInstance(appContext).markRead(groupId) }
            notifier.cancelGroup(groupId)
        } else if (notifId != 0) {
            runCatching { NotificationManagerCompat.from(appContext).cancel(notifId) }
        }
    }
}
