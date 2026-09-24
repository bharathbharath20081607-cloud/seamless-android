package com.seamless.android.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for Tor P2P messages
 * 
 * Stores all messages sent/received via Tor for persistence
 */
@Entity(
    tableName = "tor_messages",
    indices = [
        Index(value = ["contactOnionAddress"]),
        Index(value = ["timestamp"]),
        Index(value = ["deliveryState"]),
        Index(value = ["isFromMe", "readAt", "contactOnionAddress"])
    ]
)
data class TorMessageEntity(
    @PrimaryKey val id: String,
    val contactId: String,               // Contact ID from OnionContactManager
    val contactOnionAddress: String,     // Onion address for quick lookup
    val contactNickname: String,         // Cached nickname for display
    val text: String,                    // Message content
    val timestamp: Long,                 // Message timestamp
    val isFromMe: Boolean,               // true = sent, false = received
    val deliveryState: String,           // PENDING, SENDING, SENT, DELIVERED, READ, FAILED
    @ColumnInfo(name = "deliveryRoute", defaultValue = "DIRECT")
    val deliveryRoute: String = "DIRECT", // DIRECT, MAILBOX, QUEUED, BLUETOOTH, WIFI_DIRECT
    val attachmentPath: String? = null,
    val attachmentMimeType: String? = null,
    val readAt: Long? = null,            // When the message was read (for received messages)
    val createdAt: Long = System.currentTimeMillis()
)
