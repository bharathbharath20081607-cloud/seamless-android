package com.seamless.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE channel = :channelId ORDER BY timestamp ASC")
    fun getChannelMessages(channelId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isPrivate = 1 AND (senderPeerID = :peerId OR (sender = :myNickname AND recipientNickname = :peerNickname)) ORDER BY timestamp ASC")
    fun getPrivateMessages(peerId: String, myNickname: String, peerNickname: String): Flow<List<MessageEntity>>
    
    // Simplified private message query: Get all private messages where the other party is peerID
    // This assumes we store the "other party" ID somewhere or infer it. 
    // For now, let's just query by senderPeerID or if we sent it to them.
    // A better approach for private chat query might be needed.
    // Let's stick to a simpler query for now and refine later if needed.
    @Query("SELECT * FROM messages WHERE isPrivate = 1 AND (senderPeerID = :peerId OR (senderPeerID = :myPeerId AND recipientNickname = :peerNickname)) ORDER BY timestamp ASC")
    fun getPrivateMessagesByPeer(peerId: String, myPeerId: String, peerNickname: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
    
    @Query("DELETE FROM messages WHERE channel = :channelId")
    suspend fun clearChannelMessages(channelId: String)
    
    @Query("DELETE FROM messages WHERE isPrivate = 1 AND (senderPeerID = :peerId OR (senderPeerID = :myPeerId AND recipientNickname = :peerNickname))")
    suspend fun clearPrivateMessages(peerId: String, myPeerId: String, peerNickname: String)
    
    // PRODUCTION FIX: Clear mesh timeline messages on app restart (preserve private chats and channels)
    @Query("DELETE FROM messages WHERE isPrivate = 0 AND channel IS NULL")
    suspend fun clearMeshMessages()
}
