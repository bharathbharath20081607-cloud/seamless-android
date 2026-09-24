package com.seamless.android.data

import android.content.Context
import android.util.Log
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.OnionMessageProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for Tor P2P messages
 * 
 * Handles message persistence and provides clean API for the UI
 */
class TorMessageRepository private constructor(context: Context) {
    
    companion object {
        private const val TAG = "TorMessageRepository"
        
        @Volatile
        private var instance: TorMessageRepository? = null
        
        fun getInstance(context: Context): TorMessageRepository {
            return instance ?: synchronized(this) {
                instance ?: TorMessageRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.torMessageDao()
    
    /**
     * Save a sent message
     */
    suspend fun saveSentMessage(
        messageId: String,
        contact: OnionContactManager.OnionContact,
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        deliveryRoute: OnionMessageProtocol.DeliveryRoute = OnionMessageProtocol.DeliveryRoute.QUEUED,
        attachmentPath: String? = null,
        attachmentMimeType: String? = null
    ) {
        val entity = TorMessageEntity(
            id = messageId,
            contactId = contact.id,
            contactOnionAddress = contact.onionAddress,
            contactNickname = contact.nickname,
            text = text,
            timestamp = timestamp,
            isFromMe = true,
            deliveryState = OnionMessageProtocol.DeliveryState.PENDING.name,
            deliveryRoute = deliveryRoute.name,
            attachmentPath = attachmentPath,
            attachmentMimeType = attachmentMimeType
        )
        dao.insert(entity)
        Log.d(TAG, "Saved sent message: $messageId")
    }
    
    /**
     * Save a received message
     */
    suspend fun saveReceivedMessage(
        messageId: String,
        contact: OnionContactManager.OnionContact,
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        attachmentPath: String? = null,
        attachmentMimeType: String? = null
    ) {
        val entity = TorMessageEntity(
            id = messageId,
            contactId = contact.id,
            contactOnionAddress = contact.onionAddress,
            contactNickname = contact.nickname,
            text = text,
            timestamp = timestamp,
            isFromMe = false,
            deliveryState = OnionMessageProtocol.DeliveryState.DELIVERED.name,
            attachmentPath = attachmentPath,
            attachmentMimeType = attachmentMimeType
        )
        dao.insert(entity)
        Log.d(TAG, "Saved received message: $messageId")
    }
    
    /**
     * Get messages for a contact as Flow
     */
    fun getMessagesForContact(onionAddress: String): Flow<List<TorMessageEntity>> {
        return dao.getMessagesForContact(onionAddress)
    }

    /**
     * Prefer the stable local contact ID. Address aliases retain conversations
     * created by older builds before onion endpoints were normalized.
     */
    fun getMessagesForContact(contact: OnionContactManager.OnionContact): Flow<List<TorMessageEntity>> {
        return dao.getMessagesForContactIdentity(contact.id, addressAliases(contact.onionAddress))
    }
    
    /**
     * Get messages for a contact (one-shot)
     */
    suspend fun getMessagesForContactOnce(onionAddress: String): List<TorMessageEntity> {
        return dao.getMessagesForContactOnce(onionAddress)
    }
    
    /**
     * Get recent conversations
     */
    fun getRecentConversations(): Flow<List<TorMessageEntity>> {
        return dao.getRecentConversations()
    }
    
    /**
     * Get unread count for a contact
     */
    suspend fun getUnreadCount(onionAddress: String): Int {
        return dao.getUnreadCount(onionAddress)
    }
    
    /**
     * Get total unread count
     */
    fun getTotalUnreadCount(): Flow<Int> {
        return dao.getTotalUnreadCount()
    }
    
    /**
     * Mark messages as read
     */
    suspend fun markAsRead(onionAddress: String) {
        dao.markAsRead(onionAddress)
        Log.d(TAG, "Marked messages as read for: $onionAddress")
    }

    suspend fun markAsRead(contact: OnionContactManager.OnionContact) {
        dao.markAsReadIdentity(contact.id, addressAliases(contact.onionAddress))
        Log.d(TAG, "Marked messages as read for contact: ${contact.id}")
    }
    
    /**
     * Update delivery state
     */
    suspend fun updateDeliveryState(messageId: String, state: OnionMessageProtocol.DeliveryState) {
        dao.updateDeliveryState(messageId, state.name)
        Log.d(TAG, "Updated delivery state for $messageId: $state")
    }

    suspend fun updateDeliveryRoute(messageId: String, route: OnionMessageProtocol.DeliveryRoute) {
        dao.updateDeliveryRoute(messageId, route.name)
        Log.d(TAG, "Updated delivery route for $messageId: $route")
    }

    suspend fun updateDelivery(
        messageId: String,
        state: OnionMessageProtocol.DeliveryState,
        route: OnionMessageProtocol.DeliveryRoute
    ) {
        dao.updateDelivery(messageId, state.name, route.name)
        Log.d(TAG, "Updated delivery for $messageId: $state via $route")
    }
    
    /**
     * Delete conversation
     */
    suspend fun deleteConversation(onionAddress: String) {
        dao.deleteMessagesForContact(onionAddress)
        Log.d(TAG, "Deleted conversation for: $onionAddress")
    }
    
    /**
     * Cleanup old messages (default: 30 days)
     */
    suspend fun cleanupOldMessages(olderThanDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        dao.deleteOldMessages(cutoffTime)
        Log.d(TAG, "Cleaned up messages older than $olderThanDays days")
    }

    /**
     * Cleanup messages older than a specific timestamp (for disappearing messages)
     */
    suspend fun cleanupOldMessages(beforeTimestamp: Long) {
        dao.deleteOldMessages(beforeTimestamp)
        Log.d(TAG, "Cleaned up messages before timestamp $beforeTimestamp")
    }

    private fun addressAliases(onionAddress: String): List<String> {
        val canonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(onionAddress)
        val host = canonical.substringBefore(':')
        return listOf(onionAddress, canonical, host, "$host:18888")
            .filter { it.isNotBlank() }
            .distinct()
    }
}
