package com.seamless.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayDao {
    @Query("SELECT * FROM relay_messages ORDER BY timestamp ASC")
    fun getAllRelayMessages(): Flow<List<RelayMessageEntity>>

    @Query("SELECT * FROM relay_messages WHERE recipientId = :recipientId ORDER BY timestamp ASC")
    suspend fun getMessagesForRecipient(recipientId: String): List<RelayMessageEntity>

    @Query("SELECT COUNT(*) FROM relay_messages WHERE recipientId = :recipientId")
    suspend fun getMessageCountForRecipient(recipientId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelayMessage(message: RelayMessageEntity)

    @Query("DELETE FROM relay_messages WHERE messageId = :messageId")
    suspend fun deleteRelayMessage(messageId: String)

    @Query("DELETE FROM relay_messages WHERE timestamp < :timestamp AND isForFavorite = 0")
    suspend fun deleteOldMessages(timestamp: Long)

    @Query("DELETE FROM relay_messages")
    suspend fun clearAllRelayMessages()
    
    @Query("SELECT COUNT(*) FROM relay_messages")
    suspend fun getTotalMessageCount(): Int
}
