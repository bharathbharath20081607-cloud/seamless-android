@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.messaging

import android.util.Log
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.DeliveryStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages message retry logic for failed sends
 * Provides automatic retry with exponential backoff and manual retry capability
 */
class MessageRetryManager {
    
    companion object {
        private const val TAG = "MessageRetryManager"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        
        val shared = MessageRetryManager()
    }
    
    // Delegate for sending messages
    var delegate: MessageRetryDelegate? = null
    
    // Pending retry queue: messageId -> PendingMessage
    private val pendingRetries = ConcurrentHashMap<String, PendingMessage>()
    
    // Messages currently being retried
    private val _retryingMessages = MutableStateFlow<Set<String>>(emptySet())
    val retryingMessages: StateFlow<Set<String>> = _retryingMessages.asStateFlow()
    
    // Failed messages that exceeded max retries
    private val _failedMessages = MutableStateFlow<Set<String>>(emptySet())
    val failedMessages: StateFlow<Set<String>> = _failedMessages.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val retryJobs = ConcurrentHashMap<String, Job>()
    
    /**
     * Pending message data
     */
    data class PendingMessage(
        val messageId: String,
        val content: String,
        val recipientPeerID: String?,
        val channel: String?,
        val isPrivate: Boolean,
        var retryCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Queue a message for retry after send failure
     */
    fun queueForRetry(
        messageId: String,
        content: String,
        recipientPeerID: String?,
        channel: String?,
        isPrivate: Boolean
    ) {
        if (pendingRetries.containsKey(messageId)) {
            Log.d(TAG, "Message $messageId already queued for retry")
            return
        }
        
        val pending = PendingMessage(
            messageId = messageId,
            content = content,
            recipientPeerID = recipientPeerID,
            channel = channel,
            isPrivate = isPrivate
        )
        
        pendingRetries[messageId] = pending
        Log.d(TAG, "⏳ Queued message $messageId for retry")
        
        // Start auto-retry
        scheduleRetry(messageId)
    }
    
    /**
     * Schedule automatic retry with exponential backoff
     */
    private fun scheduleRetry(messageId: String) {
        val pending = pendingRetries[messageId] ?: return
        
        if (pending.retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "❌ Max retries exceeded for message $messageId")
            markAsFailed(messageId)
            return
        }
        
        val delay = calculateBackoffDelay(pending.retryCount)
        Log.d(TAG, "📅 Scheduling retry ${pending.retryCount + 1}/$MAX_RETRY_ATTEMPTS for $messageId in ${delay}ms")
        
        retryJobs[messageId]?.cancel()
        retryJobs[messageId] = scope.launch {
            delay(delay)
            executeRetry(messageId)
        }
    }
    
    /**
     * Execute retry attempt
     */
    private suspend fun executeRetry(messageId: String) {
        val pending = pendingRetries[messageId] ?: return
        
        _retryingMessages.value = _retryingMessages.value + messageId
        pending.retryCount++
        
        Log.d(TAG, "🔄 Retry attempt ${pending.retryCount}/$MAX_RETRY_ATTEMPTS for $messageId")
        
        try {
            val success = delegate?.retrySendMessage(
                messageId = pending.messageId,
                content = pending.content,
                recipientPeerID = pending.recipientPeerID,
                channel = pending.channel,
                isPrivate = pending.isPrivate
            ) ?: false
            
            if (success) {
                Log.i(TAG, "✅ Retry successful for $messageId")
                clearMessage(messageId)
            } else {
                Log.w(TAG, "⚠️ Retry failed for $messageId, scheduling next attempt")
                scheduleRetry(messageId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during retry for $messageId: ${e.message}")
            scheduleRetry(messageId)
        } finally {
            _retryingMessages.value = _retryingMessages.value - messageId
        }
    }
    
    /**
     * Manually retry a specific message
     */
    fun manualRetry(messageId: String) {
        val pending = pendingRetries[messageId]
        if (pending == null) {
            // Check if it's a failed message
            if (_failedMessages.value.contains(messageId)) {
                // Re-queue it
                _failedMessages.value = _failedMessages.value - messageId
                Log.d(TAG, "🔄 Re-queuing failed message $messageId for manual retry")
            }
            return
        }
        
        // Reset retry count for manual retry
        pending.retryCount = 0
        scheduleRetry(messageId)
    }
    
    /**
     * Called when message was delivered successfully
     */
    fun onMessageDelivered(messageId: String) {
        clearMessage(messageId)
        Log.d(TAG, "📬 Message $messageId delivered, removed from retry queue")
    }
    
    /**
     * Mark message as permanently failed
     */
    private fun markAsFailed(messageId: String) {
        pendingRetries.remove(messageId)
        retryJobs.remove(messageId)?.cancel()
        _failedMessages.value = _failedMessages.value + messageId
        
        // Notify delegate
        delegate?.onMessageFailedPermanently(messageId)
    }
    
    /**
     * Clear message from all queues
     */
    private fun clearMessage(messageId: String) {
        pendingRetries.remove(messageId)
        retryJobs.remove(messageId)?.cancel()
        _retryingMessages.value = _retryingMessages.value - messageId
        _failedMessages.value = _failedMessages.value - messageId
    }
    
    /**
     * Calculate backoff delay with exponential growth
     */
    private fun calculateBackoffDelay(retryCount: Int): Long {
        val delay = INITIAL_RETRY_DELAY_MS * (1 shl retryCount.coerceAtMost(5))
        return delay.coerceAtMost(MAX_RETRY_DELAY_MS)
    }
    
    /**
     * Check if a message is pending retry
     */
    fun isPendingRetry(messageId: String): Boolean {
        return pendingRetries.containsKey(messageId) || _retryingMessages.value.contains(messageId)
    }
    
    /**
     * Check if a message failed permanently
     */
    fun hasFailed(messageId: String): Boolean {
        return _failedMessages.value.contains(messageId)
    }
    
    /**
     * Get pending message count
     */
    fun pendingCount(): Int = pendingRetries.size
    
    /**
     * Clear all pending retries
     */
    fun clearAll() {
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        pendingRetries.clear()
        _retryingMessages.value = emptySet()
        _failedMessages.value = emptySet()
    }
}

/**
 * Delegate for MessageRetryManager
 */
interface MessageRetryDelegate {
    /**
     * Retry sending a message
     * @return true if send was successful
     */
    suspend fun retrySendMessage(
        messageId: String,
        content: String,
        recipientPeerID: String?,
        channel: String?,
        isPrivate: Boolean
    ): Boolean
    
    /**
     * Called when message has failed all retry attempts
     */
    fun onMessageFailedPermanently(messageId: String)
}
