package com.seamless.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seamless.android.model.BitchatMessageType
import com.seamless.android.model.DeliveryStatus
import java.util.Date

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["channel"]),
        Index(value = ["senderPeerID"]),
        Index(value = ["timestamp"]),
        Index(value = ["isPrivate", "senderPeerID"]),
        Index(value = ["channel", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val content: String,
    val timestamp: Long, // Store as timestamp
    val isRelay: Boolean,
    val originalSender: String?,
    val isPrivate: Boolean,
    val recipientNickname: String?,
    val senderPeerID: String?,
    val mentions: String?, // Store as comma-separated string or JSON
    val channel: String?,
    val deliveryStatus: String, // Store as name
    val type: String, // Store as name
    val vectorClock: String? = null // JSON representation of VectorClock
)
