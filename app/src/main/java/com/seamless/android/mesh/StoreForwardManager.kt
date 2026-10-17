@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.mesh
import java.util.concurrent.ConcurrentHashMap

import android.util.Log
import com.seamless.android.protocol.BitchatPacket
import com.seamless.android.protocol.MessageType
import com.seamless.android.protocol.SpecialRecipients
import com.seamless.android.util.toHexString
import kotlinx.coroutines.*
import java.util.*

/**
 * Manages store-and-forward messaging for offline peers
 * Extracted from BluetoothMeshService for better separation of concerns
 */
class StoreForwardManager(private val relayDao: com.seamless.android.data.RelayDao) {
    
    companion object {
        private const val TAG = "StoreForwardManager"
        private const val MESSAGE_CACHE_TIMEOUT = com.seamless.android.util.AppConstants.StoreForward.MESSAGE_CACHE_TIMEOUT_MS  // 12 hours for regular peers
        private const val MAX_CACHED_MESSAGES = com.seamless.android.util.AppConstants.StoreForward.MAX_CACHED_MESSAGES  // For regular peers
        private const val MAX_CACHED_MESSAGES_FAVORITES = com.seamless.android.util.AppConstants.StoreForward.MAX_CACHED_MESSAGES_FAVORITES  // For favorites
        private const val CLEANUP_INTERVAL = com.seamless.android.util.AppConstants.StoreForward.CLEANUP_INTERVAL_MS // 10 minutes
    }
    
    /**
     * Data class for stored messages
     */
    private data class StoredMessage(
        val packet: BitchatPacket,
        val timestamp: Long,
        val messageID: String,
        val isForFavorite: Boolean
    )
    
    // Message storage - NOW PERSISTENT via RelayDao
    // private val messageCache = Collections.synchronizedList(mutableListOf<StoredMessage>()) // REMOVED
    // private val favoriteMessageQueue = ConcurrentHashMap<String, MutableList<StoredMessage>>() // REMOVED
    private val deliveredMessages = Collections.synchronizedSet(mutableSetOf<String>())
    private val cachedMessagesSentToPeer = Collections.synchronizedSet(mutableSetOf<String>())
    
    // Delegate for callbacks
    var delegate: StoreForwardManagerDelegate? = null
    
    // Coroutines
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        startPeriodicCleanup()
    }
    
    /**
     * Cache message for offline delivery
     */
    fun cacheMessage(packet: BitchatPacket, messageID: String) {
        // Skip certain message types (same as iOS)
        if (packet.type == MessageType.NOISE_HANDSHAKE.value ||
            packet.type == MessageType.NOISE_ENCRYPTED.value ||
            packet.type == MessageType.ANNOUNCE.value ||
            packet.type == MessageType.LEAVE.value) {
            Log.d(TAG, "Skipping cache for message type: ${packet.type}")
            return
        }
        
        // Don't cache broadcast messages
        if (packet.recipientID != null && packet.recipientID.contentEquals(SpecialRecipients.BROADCAST)) {
            Log.d(TAG, "Skipping cache for broadcast message")
            return
        }
        
        // Determine if this is for a favorite peer
        val recipientPeerID = packet.recipientID?.toHexString()?.lowercase()
        
        if (recipientPeerID.isNullOrEmpty()) {
            Log.w(TAG, "Cannot cache message without valid recipient")
            return
        }
        
        val isForFavorite = delegate?.isFavorite(recipientPeerID) ?: false
        
        val relayMessage = com.seamless.android.data.RelayMessageEntity(
            messageId = messageID,
            recipientId = recipientPeerID,
            senderId = packet.senderID?.toHexString()?.lowercase(),
            timestamp = System.currentTimeMillis(),
            packetData = com.seamless.android.protocol.BinaryProtocol.encode(packet) ?: ByteArray(0),
            isForFavorite = isForFavorite
        )
        
        managerScope.launch {
            relayDao.insertRelayMessage(relayMessage)
            Log.d(TAG, "Persisted relay message for peer $recipientPeerID (fav=$isForFavorite)")
            
            // Optional: Enforce limits if needed, or rely on periodic cleanup
        }
    }
    
    /**
     * Send cached messages to peer when they come online
     */
    fun sendCachedMessages(peerID: String) {
        val normalizedPeerID = peerID.lowercase()
        if (cachedMessagesSentToPeer.contains(normalizedPeerID)) {
            Log.d(TAG, "Already sent cached messages to $normalizedPeerID")
            return // Already sent cached messages to this peer
        }
        
        cachedMessagesSentToPeer.add(normalizedPeerID)
        
        managerScope.launch {
            try {
                // Load from DB
                val messagesToSend = relayDao.getMessagesForRecipient(normalizedPeerID)
                
                if (messagesToSend.isNotEmpty()) {
                    Log.i(TAG, "Sending ${messagesToSend.size} persistent cached messages to $normalizedPeerID")
                }
                
                // Mark as delivered (tracking in memory for session to avoid re-sending in loop)
                val messageIDsToRemove = messagesToSend.map { it.messageId }
                deliveredMessages.addAll(messageIDsToRemove)
                
                // Send with delays
                messagesToSend.forEachIndexed { index, storedMessage ->
                    delay(index * 20L) // 20ms between messages
                    try {
                        val packet = com.seamless.android.protocol.BinaryProtocol.decode(storedMessage.packetData) ?: return@forEachIndexed
                        delegate?.sendPacket(packet)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to deserialize packet for relay: ${e.message}")
                    }
                }
                
                // Remove sent messages from DB
                messageIDsToRemove.forEach { id ->
                    relayDao.deleteRelayMessage(id)
                }
                
                if (messagesToSend.isNotEmpty()) {
                    Log.d(TAG, "Finished sending and deleting ${messagesToSend.size} cached messages for $normalizedPeerID")
                }
            } finally {
                delay(5_000L)
                cachedMessagesSentToPeer.remove(normalizedPeerID)
            }
        }
    }
    
    /**
     * Check if message should be cached for peer
     */
    fun shouldCacheForPeer(recipientPeerID: String): Boolean {
        // Check if recipient is offline and should cache for favorites
        val isOffline = !(delegate?.isPeerOnline(recipientPeerID) ?: false)
        val isRecipientFavorite = delegate?.isFavorite(recipientPeerID) ?: false
        
        return isOffline && isRecipientFavorite
    }
    
    /**
     * Mark message as delivered
     */
    fun markMessageAsDelivered(messageID: String) {
        deliveredMessages.add(messageID)
    }
    
    /**
     * Get cached message count for peer
     */
    suspend fun getCachedMessageCount(peerID: String): Int {
        return try {
            relayDao.getMessageCountForRecipient(peerID)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached message count for $peerID: ${e.message}")
            0
        }
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Store-Forward Manager Debug Info (Persistent) ===")
            appendLine("Delivered Messages (Session): ${deliveredMessages.size}")
            appendLine("Peers Sent Cache (Session): ${cachedMessagesSentToPeer.size}")
        }
    }
    
    /**
     * Start periodic cleanup
     */
    private fun startPeriodicCleanup() {
        managerScope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL)
                cleanupMessageCache()
                cleanupDeliveredMessages()
            }
        }
    }
    
    /**
     * Clean up old cached messages (not for favorites)
     */
    private fun cleanupMessageCache() {
        val cutoffTime = System.currentTimeMillis() - MESSAGE_CACHE_TIMEOUT
        managerScope.launch {
            relayDao.deleteOldMessages(cutoffTime)
            Log.d(TAG, "Cleaned up old cached messages < $cutoffTime")
        }
    }
    
    /**
     * Clean up delivered messages set (prevent memory leak)
     */
    private fun cleanupDeliveredMessages() {
        if (deliveredMessages.size > 1000) {
            Log.d(TAG, "Clearing delivered messages set (${deliveredMessages.size} entries)")
            deliveredMessages.clear()
        }
        
        if (cachedMessagesSentToPeer.size > 200) {
            Log.d(TAG, "Clearing cached messages sent tracking (${cachedMessagesSentToPeer.size} entries)")
            cachedMessagesSentToPeer.clear()
        }
    }
    
    /**
     * Clear all cached data
     */
    fun clearAllCache() {
        managerScope.launch {
            relayDao.clearAllRelayMessages()
        }
        deliveredMessages.clear()
        cachedMessagesSentToPeer.clear()
        Log.d(TAG, "Cleared all cached message data")
    }
    
    /**
     * Force cleanup for testing
     */
    fun forceCleanup() {
        cleanupMessageCache()
        cleanupDeliveredMessages()
    }
    
    /**
     * Shutdown the manager
     */
    fun shutdown() {
        managerScope.cancel()
        clearAllCache()
    }
}

/**
 * Delegate interface for store-forward manager callbacks
 */
interface StoreForwardManagerDelegate {
    fun isFavorite(peerID: String): Boolean
    fun isPeerOnline(peerID: String): Boolean
    fun sendPacket(packet: BitchatPacket)
}
