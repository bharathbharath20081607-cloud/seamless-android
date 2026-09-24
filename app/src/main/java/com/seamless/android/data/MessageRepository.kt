package com.seamless.android.data

import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.DeliveryStatus
import com.seamless.android.model.BitchatMessageType
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

class MessageRepository(private val messageDao: MessageDao) {
    private val gson = Gson()

    val allMessages: Flow<List<BitchatMessage>> = messageDao.getAllMessages().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun getAllMessagesSync(): List<BitchatMessage> {
        return messageDao.getAllMessagesSync().map { it.toDomainModel() }
    }

    fun getChannelMessages(channelId: String): Flow<List<BitchatMessage>> {
        return messageDao.getChannelMessages(channelId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getPrivateMessages(peerId: String, myNickname: String, peerNickname: String, myPeerId: String): Flow<List<BitchatMessage>> {
        return messageDao.getPrivateMessagesByPeer(peerId, myPeerId, peerNickname).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun insertMessage(message: BitchatMessage) {
        messageDao.insertMessage(message.toEntity())
    }

    suspend fun updateDeliveryStatus(messageId: String, status: DeliveryStatus) {
        messageDao.updateDeliveryStatus(messageId, gson.toJson(status))
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }

    suspend fun clearAllMessages() {
        messageDao.clearAllMessages()
    }
    
    suspend fun clearChannelMessages(channelId: String) {
        messageDao.clearChannelMessages(channelId)
    }
    
    suspend fun clearPrivateMessages(peerId: String, myPeerId: String, peerNickname: String) {
        messageDao.clearPrivateMessages(peerId, myPeerId, peerNickname)
    }
    
    // PRODUCTION FIX: Clear mesh timeline on app restart (preserves private chats and channels)
    suspend fun clearMeshMessages() {
        messageDao.clearMeshMessages()
    }

    // Mappers
    private fun MessageEntity.toDomainModel(): BitchatMessage {
        // PRODUCTION FIX: Gson can't deserialize sealed classes directly
        // Handle DeliveryStatus separately with proper fallback
        val parsedDeliveryStatus: DeliveryStatus = try {
            if (deliveryStatus.isNullOrBlank()) {
                DeliveryStatus.Sent
            } else {
                // Try to parse the JSON to determine the actual type
                parseDeliveryStatusSafely(deliveryStatus)
            }
        } catch (e: Exception) {
            android.util.Log.w("MessageRepository", "Failed to parse deliveryStatus: ${e.message}")
            DeliveryStatus.Sent
        }
        
        return BitchatMessage(
            id = id,
            sender = sender,
            content = content,
            type = try { BitchatMessageType.valueOf(type) } catch(e: Exception) { BitchatMessageType.Message },
            timestamp = Date(timestamp),
            isRelay = isRelay,
            originalSender = originalSender,
            isPrivate = isPrivate,
            recipientNickname = recipientNickname,
            senderPeerID = senderPeerID,
            mentions = Converters().fromStringList(mentions),
            channel = channel,
            deliveryStatus = parsedDeliveryStatus,
            vectorClock = if (vectorClock != null) com.seamless.android.sync.VectorClock.fromJson(vectorClock).clock else null
        )
    }
    
    /**
     * Safely parse DeliveryStatus JSON without using Gson's abstract class instantiation
     */
    private fun parseDeliveryStatusSafely(json: String): DeliveryStatus {
        return try {
            // Use a JsonObject to inspect the type
            val jsonElement = com.google.gson.JsonParser.parseString(json)
            
            if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                
                // Check for specific fields to determine the type
                when {
                    obj.has("to") && obj.has("at") -> {
                        val to = obj.get("to").asString
                        val at = Date(obj.get("at").asLong)
                        DeliveryStatus.Delivered(to, at)
                    }
                    obj.has("by") && obj.has("at") -> {
                        val by = obj.get("by").asString
                        val at = Date(obj.get("at").asLong)
                        DeliveryStatus.Read(by, at)
                    }
                    obj.has("reason") -> {
                        val reason = obj.get("reason").asString
                        DeliveryStatus.Failed(reason)
                    }
                    obj.has("reached") && obj.has("total") -> {
                        val reached = obj.get("reached").asInt
                        val total = obj.get("total").asInt
                        DeliveryStatus.PartiallyDelivered(reached, total)
                    }
                    else -> DeliveryStatus.Sent
                }
            } else if (jsonElement.isJsonPrimitive) {
                // Handle simple string enum-like values
                when (jsonElement.asString.lowercase()) {
                    "sending" -> DeliveryStatus.Sending
                    "sent" -> DeliveryStatus.Sent
                    else -> DeliveryStatus.Sent
                }
            } else {
                DeliveryStatus.Sent
            }
        } catch (e: Exception) {
            android.util.Log.w("MessageRepository", "Error parsing delivery status: ${e.message}")
            DeliveryStatus.Sent
        }
    }

    private fun BitchatMessage.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            sender = sender,
            content = content,
            type = type.name,
            timestamp = timestamp.time,
            isRelay = isRelay,
            originalSender = originalSender,
            isPrivate = isPrivate,
            recipientNickname = recipientNickname,
            senderPeerID = senderPeerID,
            mentions = Converters().toStringList(mentions),
            channel = channel,
            deliveryStatus = gson.toJson(deliveryStatus),
            vectorClock = if (vectorClock != null) com.seamless.android.sync.VectorClock(vectorClock).toJson() else null
        )
    }
}
