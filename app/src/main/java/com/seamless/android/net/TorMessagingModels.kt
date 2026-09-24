package com.seamless.android.net

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

enum class TorDeliveryRoute {
    DIRECT,
    MAILBOX,
    QUEUED;

    fun toProtocolRoute(): OnionMessageProtocol.DeliveryRoute = when (this) {
        DIRECT -> OnionMessageProtocol.DeliveryRoute.DIRECT
        MAILBOX -> OnionMessageProtocol.DeliveryRoute.MAILBOX
        QUEUED -> OnionMessageProtocol.DeliveryRoute.QUEUED
    }
}

enum class TorDeliveryState {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED;

    fun toProtocolState(): OnionMessageProtocol.DeliveryState = when (this) {
        PENDING -> OnionMessageProtocol.DeliveryState.PENDING
        SENDING -> OnionMessageProtocol.DeliveryState.SENDING
        SENT -> OnionMessageProtocol.DeliveryState.SENT
        DELIVERED -> OnionMessageProtocol.DeliveryState.DELIVERED
        READ -> OnionMessageProtocol.DeliveryState.READ
        FAILED -> OnionMessageProtocol.DeliveryState.FAILED
    }

    companion object {
        fun fromProtocolState(state: OnionMessageProtocol.DeliveryState): TorDeliveryState = when (state) {
            OnionMessageProtocol.DeliveryState.PENDING -> PENDING
            OnionMessageProtocol.DeliveryState.SENDING -> SENDING
            OnionMessageProtocol.DeliveryState.SENT -> SENT
            OnionMessageProtocol.DeliveryState.DELIVERED -> DELIVERED
            OnionMessageProtocol.DeliveryState.READ -> READ
            OnionMessageProtocol.DeliveryState.FAILED -> FAILED
        }
    }
}

enum class TorContactTrustState {
    UNVERIFIED,
    VERIFIED,
    KEY_CHANGED,
    BLOCKED
}

enum class TorEnvelopePayloadType(val code: Byte) {
    MESSAGE(0x01),
    ACK(0x02),
    READ_RECEIPT(0x03),
    CONTACT_EXCHANGE(0x04),
    MAILBOX_EXCHANGE(0x05),
    MAILBOX_STORE(0x06);

    companion object {
        fun fromCode(code: Byte): TorEnvelopePayloadType? = values().find { it.code == code }
    }
}

data class MailboxDescriptor(
    val onionAddress: String,
    val ownerFingerprint: String,
    val registrationState: String,
    val lastPollAt: Long = 0L
)

data class TorSendOptions(
    val allowMailboxFallback: Boolean = true,
    val requireVerifiedContact: Boolean = false
)

data class TorSendProgress(
    val messageId: String,
    val contactId: String,
    val state: TorDeliveryState,
    val route: TorDeliveryRoute,
    val retryCount: Int = 0,
    val error: String? = null
)

/**
 * Versioned encrypted-message envelope carried inside OnionMessageProtocol frames.
 *
 * The envelope is encoded before transport encryption, so sender/recipient keys,
 * sequence numbers, and integrity-chain metadata are confidential on the wire.
 */
data class TorMessageEnvelope(
    val version: Byte = VERSION,
    val messageId: UUID,
    val senderKey: String,
    val recipientKey: String,
    val timestamp: Long,
    val sequence: Long,
    val previousHash: String?,
    val payloadType: TorEnvelopePayloadType,
    val ciphertext: ByteArray
) {
    fun encode(): ByteArray {
        val senderBytes = senderKey.toByteArray(Charsets.UTF_8)
        val recipientBytes = recipientKey.toByteArray(Charsets.UTF_8)
        val previousHashBytes = previousHash?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)

        require(senderBytes.size <= 65_535) { "senderKey too large" }
        require(recipientBytes.size <= 65_535) { "recipientKey too large" }
        require(previousHashBytes.size <= 65_535) { "previousHash too large" }

        val buffer = ByteBuffer.allocate(
            MAGIC.size +
                1 +                 // version
                16 +                // UUID
                8 +                 // timestamp
                8 +                 // sequence
                1 +                 // payload type
                2 + senderBytes.size +
                2 + recipientBytes.size +
                2 + previousHashBytes.size +
                4 + ciphertext.size
        )

        buffer.put(MAGIC)
        buffer.put(version)
        buffer.putLong(messageId.mostSignificantBits)
        buffer.putLong(messageId.leastSignificantBits)
        buffer.putLong(timestamp)
        buffer.putLong(sequence)
        buffer.put(payloadType.code)
        putBytes16(buffer, senderBytes)
        putBytes16(buffer, recipientBytes)
        putBytes16(buffer, previousHashBytes)
        buffer.putInt(ciphertext.size)
        buffer.put(ciphertext)
        return buffer.array()
    }

    fun chainHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encode())
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TorMessageEnvelope) return false
        return version == other.version &&
            messageId == other.messageId &&
            senderKey == other.senderKey &&
            recipientKey == other.recipientKey &&
            timestamp == other.timestamp &&
            sequence == other.sequence &&
            previousHash == other.previousHash &&
            payloadType == other.payloadType &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + senderKey.hashCode()
        result = 31 * result + recipientKey.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + (previousHash?.hashCode() ?: 0)
        result = 31 * result + payloadType.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    companion object {
        const val VERSION: Byte = 1
        private val MAGIC = byteArrayOf(0x53, 0x54, 0x4D, 0x45) // STME

        fun decode(data: ByteArray): TorMessageEnvelope? {
            if (data.size < MAGIC.size + 1 + 16 + 8 + 8 + 1) return null

            return try {
                val buffer = ByteBuffer.wrap(data)
                val magic = ByteArray(MAGIC.size)
                buffer.get(magic)
                if (!magic.contentEquals(MAGIC)) return null

                val version = buffer.get()
                if (version != VERSION) return null

                val messageId = UUID(buffer.getLong(), buffer.getLong())
                val timestamp = buffer.getLong()
                val sequence = buffer.getLong()
                val payloadType = TorEnvelopePayloadType.fromCode(buffer.get()) ?: return null
                val senderKey = String(readBytes16(buffer), Charsets.UTF_8)
                val recipientKey = String(readBytes16(buffer), Charsets.UTF_8)
                val previousHashBytes = readBytes16(buffer)
                val ciphertextLen = buffer.getInt()
                if (ciphertextLen < 0 || ciphertextLen > buffer.remaining()) return null
                val ciphertext = ByteArray(ciphertextLen)
                buffer.get(ciphertext)

                TorMessageEnvelope(
                    version = version,
                    messageId = messageId,
                    senderKey = senderKey,
                    recipientKey = recipientKey,
                    timestamp = timestamp,
                    sequence = sequence,
                    previousHash = previousHashBytes.takeIf { it.isNotEmpty() }?.let {
                        String(it, Charsets.UTF_8)
                    },
                    payloadType = payloadType,
                    ciphertext = ciphertext
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun putBytes16(buffer: ByteBuffer, data: ByteArray) {
            buffer.putShort(data.size.toShort())
            buffer.put(data)
        }

        private fun readBytes16(buffer: ByteBuffer): ByteArray {
            val length = buffer.getShort().toInt() and 0xFFFF
            require(length <= buffer.remaining()) { "Invalid length" }
            val data = ByteArray(length)
            buffer.get(data)
            return data
        }
    }
}
