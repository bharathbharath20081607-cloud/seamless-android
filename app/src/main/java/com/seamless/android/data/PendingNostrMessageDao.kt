package com.seamless.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for pending Nostr messages.
 * Used to persist outgoing messages for offline-first reliability.
 */
@Dao
interface PendingNostrMessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingNostrMessage)
    
    @Delete
    suspend fun delete(message: PendingNostrMessage)
    
    @Query("DELETE FROM pending_nostr_messages WHERE messageID = :messageID")
    suspend fun deleteById(messageID: String)
    
    @Query("SELECT * FROM pending_nostr_messages ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingNostrMessage>
    
    @Query("SELECT COUNT(*) FROM pending_nostr_messages")
    suspend fun getPendingCount(): Int
    
    @Query("UPDATE pending_nostr_messages SET retryCount = retryCount + 1, lastAttemptTime = :timestamp WHERE messageID = :messageID")
    suspend fun incrementRetryCount(messageID: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM pending_nostr_messages WHERE createdAt < :expirationTime")
    suspend fun deleteExpired(expirationTime: Long)
}
