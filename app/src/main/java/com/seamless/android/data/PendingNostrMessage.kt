package com.seamless.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pending Nostr message entity for offline-first reliability.
 * Messages are stored here until successfully sent to relays.
 */
@Entity(
    tableName = "pending_nostr_messages",
    indices = [Index(value = ["createdAt"])]
)
data class PendingNostrMessage(
    @PrimaryKey
    val messageID: String,
    val recipientPeerID: String,
    val recipientNickname: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastAttemptTime: Long? = null
)
