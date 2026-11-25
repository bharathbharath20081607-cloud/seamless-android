@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.seamless.android.MainActivity
import com.seamless.android.R
import com.seamless.android.receiver.TorNotificationActionReceiver
import com.seamless.android.util.InAppSoundPlayer
import java.util.concurrent.ConcurrentHashMap

/** Displays local high-priority notifications and foreground chimes for encrypted Tor P2P messages. */
class TorMessageNotifier(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "TorMessageNotifier"
        private const val LEGACY_CHANNEL_ID = "tor_messages"
        private const val LEGACY_CHANNEL_ID_V2 = "seamless_tor_messages_v2"
        const val CHANNEL_ID = "seamless_tor_messages_v3"
        private const val GROUP_KEY_TOR = "seamless_tor_messages_group"
        private const val SUMMARY_NOTIFICATION_ID = 77910
        private const val MAX_STACKED_MESSAGES = 6

        const val EXTRA_OPEN_TOR_CHAT = "open_tor_chat"
        const val EXTRA_TOR_CONTACT_ID = "tor_contact_id"
        const val EXTRA_TOR_GROUP_ID = "tor_group_id"
        const val ACTION_DIRECT_REPLY = "com.seamless.android.tor.ACTION_DIRECT_REPLY"
        const val ACTION_MARK_READ = "com.seamless.android.tor.ACTION_MARK_READ"
        const val KEY_TEXT_REPLY = "tor_direct_reply_text"
        const val EXTRA_NOTIFICATION_ID = "tor_notification_id"
        const val EXTRA_CONVERSATION_KEY = "tor_conversation_key"

        private data class UnreadEntry(
            val senderName: String,
            val text: String,
            val timestamp: Long
        )

        private val unreadConversations = ConcurrentHashMap<String, MutableList<UnreadEntry>>()
        private val conversationTitles = ConcurrentHashMap<String, String>()
        private val groupConversations = ConcurrentHashMap.newKeySet<String>()
        @Volatile private var lastChimeAtMs = 0L

        internal fun buildSoundUri(context: Context): Uri =
            Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/seamless_notification")
    }

    init {
        ensureChannelsCreated()
    }

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ID_V2) }

        val soundUri = buildSoundUri(appContext)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tor Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New encrypted messages received through Tor P2P"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        runCatching { nm.createNotificationChannel(channel) }
            .onFailure { Log.w(TAG, "Failed to create Tor notification channel: ${it.message}") }
    }

    fun playForegroundChime() {
        // Silent while inside the app — notifications and sounds only fire when app is backgrounded/locked.
    }

    fun cancelAllMessageNotifications() {
        val nm = NotificationManagerCompat.from(appContext)
        for (key in unreadConversations.keys.toList()) {
            val id = if (key.startsWith("dm:")) key.removePrefix("dm:").hashCode() else key.hashCode()
            runCatching { nm.cancel(id) }
        }
        unreadConversations.clear()
        conversationTitles.clear()
        groupConversations.clear()
        runCatching { nm.cancel(SUMMARY_NOTIFICATION_ID) }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping Tor notification: POST_NOTIFICATIONS permission not granted")
            return false
        }
        val enabled = runCatching { NotificationManagerCompat.from(appContext).areNotificationsEnabled() }
            .getOrDefault(true)
        if (!enabled) {
            Log.w(TAG, "Skipping Tor notification: system notifications disabled for Seamless")
            return false
        }
        return true
    }

    fun showIncoming(contact: OnionContactManager.OnionContact, preview: String, isPhoto: Boolean = false) {
        if (com.seamless.android.util.AppForegroundTracker.isAppInForeground(appContext)) {
            cancelContact(contact.id)
            return
        }
        ensureChannelsCreated()
        if (!canPostNotifications()) return

        val conversationKey = "dm:${contact.id}"
        val text = if (isPhoto) "Sent a photo" else preview.take(160)
        val now = System.currentTimeMillis()
        groupConversations.remove(conversationKey)
        conversationTitles[conversationKey] = contact.nickname

        val entries = appendUnread(
            conversationKey = conversationKey,
            senderName = contact.nickname,
            text = text,
            timestamp = now
        )

        val notifId = contact.id.hashCode()
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TOR_CHAT, true)
            putExtra(EXTRA_TOR_CONTACT_ID, contact.id)
        }
        val contentPi = PendingIntent.getActivity(
            appContext,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val selfPerson = Person.Builder().setName("You").setKey("self").build()
        val style = NotificationCompat.MessagingStyle(selfPerson)
            .setGroupConversation(false)
        for (entry in entries) {
            val senderPerson = Person.Builder().setName(entry.senderName).setKey(contact.id).build()
            style.addMessage(entry.text, entry.timestamp, senderPerson)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contact.nickname)
            .setContentText(text)
            .setStyle(style)
            .setPublicVersion(buildPublicLockscreenVersion())
            .setSound(buildSoundUri(appContext))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setWhen(now)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_TOR)
            .setContentIntent(contentPi)
            .addAction(buildReplyAction(conversationKey, notifId, contactId = contact.id, groupId = null))
            .addAction(buildMarkReadAction(conversationKey, notifId, contactId = contact.id, groupId = null))
            .build()

        runCatching { NotificationManagerCompat.from(appContext).notify(notifId, notification) }
            .onFailure { Log.w(TAG, "Failed to post Tor DM notification: ${it.message}") }
        updateSummaryNotification()
    }

    /** Group notification: one per group, updated in place with MessagingStyle + Reply/Mark Read actions. */
    fun showGroupEvent(groupId: String, title: String, body: String, senderName: String? = null) {
        if (com.seamless.android.util.AppForegroundTracker.isAppInForeground(appContext)) {
            cancelGroup(groupId)
            return
        }
        ensureChannelsCreated()
        if (!canPostNotifications()) return

        val conversationKey = "group:$groupId"
        val now = System.currentTimeMillis()
        val parsedSender = senderName?.takeIf { it.isNotBlank() }
            ?: body.substringBefore(": ", "").takeIf { body.contains(": ") && it.length in 1..40 }
        val cleanText = if (parsedSender != null && body.startsWith("$parsedSender: ")) {
            body.removePrefix("$parsedSender: ").take(200)
        } else {
            body.take(200)
        }

        groupConversations.add(conversationKey)
        conversationTitles[conversationKey] = title
        val entries = appendUnread(
            conversationKey = conversationKey,
            senderName = parsedSender ?: title,
            text = cleanText,
            timestamp = now
        )

        val notifId = conversationKey.hashCode()
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TOR_CHAT, true)
            putExtra(EXTRA_TOR_GROUP_ID, groupId)
        }
        val contentPi = PendingIntent.getActivity(
            appContext,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val selfPerson = Person.Builder().setName("You").setKey("self").build()
        val style = NotificationCompat.MessagingStyle(selfPerson)
            .setConversationTitle(title)
            .setGroupConversation(true)
        for (entry in entries) {
            val senderPerson = Person.Builder().setName(entry.senderName).setKey("$conversationKey:${entry.senderName}").build()
            style.addMessage(entry.text, entry.timestamp, senderPerson)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.take(200))
            .setStyle(style)
            .setPublicVersion(buildPublicLockscreenVersion())
            .setSound(buildSoundUri(appContext))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setWhen(now)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_TOR)
            .setContentIntent(contentPi)
            .addAction(buildReplyAction(conversationKey, notifId, contactId = null, groupId = groupId))
            .addAction(buildMarkReadAction(conversationKey, notifId, contactId = null, groupId = groupId))
            .build()

        runCatching { NotificationManagerCompat.from(appContext).notify(notifId, notification) }
            .onFailure { Log.w(TAG, "Failed to post Tor group notification: ${it.message}") }
        updateSummaryNotification()
    }

    fun cancelContact(contactId: String) {
        val conversationKey = "dm:$contactId"
        clearConversation(conversationKey)
        runCatching { NotificationManagerCompat.from(appContext).cancel(contactId.hashCode()) }
        updateSummaryNotification()
    }

    fun cancelGroup(groupId: String) {
        val conversationKey = "group:$groupId"
        clearConversation(conversationKey)
        runCatching { NotificationManagerCompat.from(appContext).cancel(conversationKey.hashCode()) }
        updateSummaryNotification()
    }

    private fun clearConversation(conversationKey: String) {
        unreadConversations.remove(conversationKey)
        conversationTitles.remove(conversationKey)
        groupConversations.remove(conversationKey)
    }

    private fun appendUnread(
        conversationKey: String,
        senderName: String,
        text: String,
        timestamp: Long
    ): List<UnreadEntry> {
        val list = unreadConversations.computeIfAbsent(conversationKey) { mutableListOf() }
        return synchronized(list) {
            list.add(UnreadEntry(senderName, text, timestamp))
            while (list.size > MAX_STACKED_MESSAGES) {
                list.removeAt(0)
            }
            list.toList()
        }
    }

    private fun buildPublicLockscreenVersion(): Notification =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Seamless")
            .setContentText("New encrypted message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun buildReplyAction(
        conversationKey: String,
        notifId: Int,
        contactId: String?,
        groupId: String?
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply privately")
            .build()
        val intent = Intent(appContext, TorNotificationActionReceiver::class.java).apply {
            action = ACTION_DIRECT_REPLY
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            if (contactId != null) putExtra(EXTRA_TOR_CONTACT_ID, contactId)
            if (groupId != null) putExtra(EXTRA_TOR_GROUP_ID, groupId)
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPi = PendingIntent.getBroadcast(
            appContext,
            conversationKey.hashCode() * 31 + 1,
            intent,
            piFlags
        )
        return NotificationCompat.Action.Builder(0, "Reply", replyPi)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun buildMarkReadAction(
        conversationKey: String,
        notifId: Int,
        contactId: String?,
        groupId: String?
    ): NotificationCompat.Action {
        val intent = Intent(appContext, TorNotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            if (contactId != null) putExtra(EXTRA_TOR_CONTACT_ID, contactId)
            if (groupId != null) putExtra(EXTRA_TOR_GROUP_ID, groupId)
        }
        val markPi = PendingIntent.getBroadcast(
            appContext,
            conversationKey.hashCode() * 31 + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "Mark read", markPi).build()
    }

    private fun updateSummaryNotification() {
        val activeKeys = unreadConversations.keys.toList()
        if (activeKeys.size < 2) {
            runCatching { NotificationManagerCompat.from(appContext).cancel(SUMMARY_NOTIFICATION_ID) }
            return
        }
        if (!canPostNotifications()) return

        var totalMessages = 0
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle("${activeKeys.size} Tor conversations")
        for (key in activeKeys.take(5)) {
            val entries = unreadConversations[key]?.let { synchronized(it) { it.toList() } } ?: continue
            if (entries.isEmpty()) continue
            totalMessages += entries.size
            val last = entries.last()
            val title = conversationTitles[key] ?: last.senderName
            val line = if (key in groupConversations && last.senderName != title) {
                "$title • ${last.senderName}: ${last.text}"
            } else {
                "$title: ${last.text}"
            }
            inbox.addLine(line)
        }
        if (totalMessages == 0) {
            runCatching { NotificationManagerCompat.from(appContext).cancel(SUMMARY_NOTIFICATION_ID) }
            return
        }

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val summaryPi = PendingIntent.getActivity(
            appContext,
            SUMMARY_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Seamless")
            .setContentText("$totalMessages new Tor messages across ${activeKeys.size} chats")
            .setStyle(inbox)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_TOR)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(summaryPi)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(SUMMARY_NOTIFICATION_ID, summary) }
    }
}
