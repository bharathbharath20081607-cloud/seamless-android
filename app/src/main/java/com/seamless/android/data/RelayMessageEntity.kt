package com.seamless.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seamless.android.protocol.BitchatPacket

@Entity(
    tableName = "relay_messages",
    indices = [Index(value = ["recipientId", "timestamp"])]
)
data class RelayMessageEntity(
    @PrimaryKey val messageId: String,
    val recipientId: String, // Hex string of recipient ID
    val senderId: String?,   // Hex string of sender ID (if known/public)
    val timestamp: Long,
    val packetData: ByteArray, // The raw encrypted packet
    val isForFavorite: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RelayMessageEntity

        if (messageId != other.messageId) return false
        if (recipientId != other.recipientId) return false
        if (senderId != other.senderId) return false
        if (timestamp != other.timestamp) return false
        if (!packetData.contentEquals(other.packetData)) return false
        if (isForFavorite != other.isForFavorite) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + (senderId?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + packetData.contentHashCode()
        result = 31 * result + isForFavorite.hashCode()
        return result
    }
}
