package com.seamless.android.data

import androidx.room.*

/**
 * Room entity for persisted Mailbox messages.
 *
 * Previously, the MailboxService stored all messages in a ConcurrentHashMap
 * which was lost on app restart. This entity backs persistent storage so
 * store-and-forward messages survive crashes, restarts, and reboots.
 */
@Entity(
    tableName = "mailbox_messages",
    indices = [
        Index("recipientId"),
        Index("timestamp")
    ]
)
data class MailboxMessageEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "recipientId")
    val recipientId: String,

    @ColumnInfo(name = "senderOnion")
    val senderOnion: String,

    @ColumnInfo(name = "encryptedPayload")
    val encryptedPayload: ByteArray,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "expiresAt")
    val expiresAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MailboxMessageEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Dao
interface MailboxMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MailboxMessageEntity)

    @Query("SELECT * FROM mailbox_messages WHERE recipientId = :recipientId ORDER BY timestamp ASC")
    suspend fun getByRecipient(recipientId: String): List<MailboxMessageEntity>

    @Query("DELETE FROM mailbox_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("DELETE FROM mailbox_messages WHERE recipientId = :recipientId")
    suspend fun deleteByRecipient(recipientId: String)

    @Query("DELETE FROM mailbox_messages WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM mailbox_messages WHERE recipientId = :recipientId")
    suspend fun countByRecipient(recipientId: String): Int

    @Query("SELECT COUNT(*) FROM mailbox_messages")
    suspend fun countAll(): Int

    @Query("SELECT DISTINCT recipientId FROM mailbox_messages")
    suspend fun getAllRecipientIds(): List<String>
}
