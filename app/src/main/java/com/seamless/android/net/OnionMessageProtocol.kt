@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.util.Log
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-grade Onion Messaging Protocol
 * 
 * Security Properties:
 * - Fixed-size padded frames (4KB) prevent traffic analysis
 * - Message IDs for deduplication
 * - Sequence numbers for ordering
 * - Timestamps for freshness validation
 * - ACK/NACK for reliable delivery
 * 
 * Wire Format (4096 bytes total):
 * [1B version][16B msgId][8B seq][8B timestamp][1B type][2B payloadLen][payload][padding]
 */
object OnionMessageProtocol {
    private const val TAG = "OnionMessageProtocol"
    
    // Frame constants
    const val FRAME_SIZE = 4096          // Fixed frame size for traffic analysis resistance
    const val COMPACT_CELL_FRAME_SIZE = 480 // Fits inside a single 514-byte Tor cell (480B + 4B length prefix)
    const val HEADER_SIZE = 36           // 1 + 16 + 8 + 8 + 1 + 2 = 36 bytes
    const val MAX_PAYLOAD_SIZE = FRAME_SIZE - HEADER_SIZE  // 4060 bytes
    const val PROTOCOL_VERSION: Byte = 0x01
    
    // Message freshness window (5 minutes live, 7 days offline mailbox / BSP sync)
    const val MESSAGE_FRESHNESS_MS = 5 * 60 * 1000L
    const val OFFLINE_MESSAGE_FRESHNESS_MS = 7 * 24 * 60 * 60 * 1000L
    const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L
    
    private val secureRandom = SecureRandom()
    
    /**
     * Frame types for the messaging protocol
     */
    enum class FrameType(val code: Byte) {
        MESSAGE(0x01),           // Regular chat message
        ACK(0x02),               // Acknowledgment
        NACK(0x03),              // Negative acknowledgment (request resend)
        PING(0x04),              // Keepalive ping
        PONG(0x05),              // Keepalive response
        FILE_CHUNK(0x06),        // File transfer chunk
        FILE_META(0x07),         // File metadata
        HANDSHAKE(0x08),         // Cryptographic handshake
        KEY_UPDATE(0x09),        // Key rotation notification
        CONTACT_EXCHANGE(0x0A),  // Auto-pairing contact exchange
        READ_RECEIPT(0x0B),      // Read confirmation from recipient
        CONTACT_REQUEST(0x0C),   // Contact request (requires accept/reject)
        CONTACT_RESPONSE(0x0D),  // Contact response (accepted/rejected)
        TYPING(0x0E),            // Ephemeral direct-only typing presence
        BULK_HELLO(0x0F),        // Secondary bulk-media circuit authentication handshake

        // === Bramble Sync Protocol (BSP) frame types ===
        SYNC_OFFER(0x10),        // "I have these messages" — sends message IDs + GCS filter
        SYNC_REQUEST(0x11),      // "Send me these specific messages" — requests by ID
        SYNC_BATCH(0x12),        // Batch of requested messages
        SYNC_ACK(0x13),          // "I received these messages" — acknowledgment
        FILE_PARITY(0x14),       // Fountain-Warp systematic block-XOR parity droplet for 0-RTT tail recovery

        // === Concealed / DPI-resistant frame types ===
        CONCEALED(0x20),         // Encrypted wrapper — looks like random noise on the wire

        // === Mailbox Protocol frame types ===
        MAILBOX_EXCHANGE(0x30),  // Exchange mailbox .onion addresses with peer
        MAILBOX_STORE(0x31),     // Store-and-forward message via mailbox relay
        CALL_SIGNAL(0x40),       // Encrypted call signaling (OFFER, RINGING, ACCEPT, DECLINE, HANGUP, P2P_PROBE)
        CALL_AUDIO(0x41),        // Compact real-time encrypted voice frame (fits inside 1 Tor cell)
        CALL_VIDEO(0x42);        // Unpadded real-time encrypted video fragment frame
        
        companion object {
            fun fromCode(code: Byte): FrameType? = values().find { it.code == code }
        }
    }
    
    /**
     * Delivery states for message tracking
     */
    enum class DeliveryState {
        PENDING,    // Created, not yet sent
        SENDING,    // Currently being transmitted
        SENT,       // Transmitted, awaiting ACK
        DELIVERED,  // ACK received
        READ,       // Read receipt received
        FAILED      // Delivery failed after retries
    }

    /**
     * Delivery route tracking — how a message was delivered.
     * Inspired by Session's multi-hop routing and Briar's transport-agnostic approach.
     */
    enum class DeliveryRoute {
        DIRECT,     // Delivered via direct Tor P2P connection
        MAILBOX,    // Delivered via store-and-forward mailbox relay
        QUEUED,     // Queued locally, not yet delivered
        BLUETOOTH,  // Delivered via Bluetooth mesh
        WIFI_DIRECT // Delivered via Wi-Fi Direct
    }
    
    /**
     * Represents a protocol frame
     */
    data class OnionFrame(
        val version: Byte = PROTOCOL_VERSION,
        val messageId: UUID,
        val sequence: Long,
        val timestamp: Long,
        val frameType: FrameType,
        val payload: ByteArray
    ) {
        /**
         * Encode frame to fixed-size wire format
         */
        fun encode(
            padToFixedSize: Boolean = frameType != FrameType.CALL_AUDIO && frameType != FrameType.CALL_VIDEO && frameType != FrameType.CALL_SIGNAL
        ): ByteArray {
            require(payload.size <= MAX_PAYLOAD_SIZE) {
                "Payload too large: ${payload.size} > $MAX_PAYLOAD_SIZE"
            }

            val useCompactCell = padToFixedSize &&
                (HEADER_SIZE + payload.size <= COMPACT_CELL_FRAME_SIZE) &&
                (frameType == FrameType.MESSAGE ||
                    frameType == FrameType.ACK ||
                    frameType == FrameType.NACK ||
                    frameType == FrameType.PING ||
                    frameType == FrameType.PONG ||
                    frameType == FrameType.TYPING ||
                    frameType == FrameType.BULK_HELLO ||
                    frameType == FrameType.READ_RECEIPT ||
                    frameType == FrameType.CALL_SIGNAL ||
                    frameType == FrameType.CONTACT_EXCHANGE ||
                    frameType == FrameType.MAILBOX_EXCHANGE ||
                    frameType == FrameType.FILE_META)

            val totalSize = when {
                !padToFixedSize -> HEADER_SIZE + payload.size
                useCompactCell -> COMPACT_CELL_FRAME_SIZE
                frameType == FrameType.FILE_CHUNK || frameType == FrameType.FILE_PARITY ->
                    com.seamless.android.crypto.PadmeCellPadder
                        .torCellAlignedLength(HEADER_SIZE + payload.size)
                        .coerceIn(HEADER_SIZE + payload.size, FRAME_SIZE)
                else -> FRAME_SIZE
            }
            val buffer = ByteBuffer.allocate(totalSize)
            
            // Header
            buffer.put(version)
            buffer.putLong(messageId.mostSignificantBits)
            buffer.putLong(messageId.leastSignificantBits)
            buffer.putLong(sequence)
            buffer.putLong(timestamp)
            buffer.put(frameType.code)
            buffer.putShort(payload.size.toShort())
            
            // Payload
            buffer.put(payload)
            
            // Pad to fixed/cell size (FILE_CHUNK is already STDP-v2 AEAD ciphertext, avoiding redundant CSPRNG calls)
            val paddingSize = (totalSize - HEADER_SIZE - payload.size).coerceAtLeast(0)
            if (paddingSize > 0 && frameType != FrameType.FILE_CHUNK) {
                val padding = ByteArray(paddingSize)
                secureRandom.nextBytes(padding)
                buffer.put(padding)
            }
            
            return buffer.array()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OnionFrame) return false
            return messageId == other.messageId && sequence == other.sequence
        }
        
        override fun hashCode(): Int = messageId.hashCode() * 31 + sequence.hashCode()
        
        companion object {
            /**
             * Decode frame from wire format
             */
            fun decode(data: ByteArray): OnionFrame? {
                if (data.size < HEADER_SIZE || data.size > FRAME_SIZE) {
                    Log.e(TAG, "Invalid frame size: ${data.size}")
                    return null
                }
                
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    
                    val version = buffer.get()
                    if (version != PROTOCOL_VERSION) {
                        Log.w(TAG, "Unknown protocol version: $version")
                    }
                    
                    val msgIdHigh = buffer.getLong()
                    val msgIdLow = buffer.getLong()
                    val messageId = UUID(msgIdHigh, msgIdLow)
                    
                    val sequence = buffer.getLong()
                    val timestamp = buffer.getLong()
                    
                    val frameTypeCode = buffer.get()
                    val frameType = FrameType.fromCode(frameTypeCode)
                    if (frameType == null) {
                        Log.e(TAG, "Unknown frame type: $frameTypeCode")
                        return null
                    }
                    
                    val payloadLen = buffer.getShort().toInt() and 0xFFFF
                    if (data.size < HEADER_SIZE + payloadLen || data.size > FRAME_SIZE) {
                        Log.e(TAG, "Invalid frame size ${data.size} for $frameType (payloadLen=$payloadLen)")
                        return null
                    }
                    if (payloadLen > MAX_PAYLOAD_SIZE || payloadLen > buffer.remaining()) {
                        Log.e(TAG, "Invalid payload length: $payloadLen")
                        return null
                    }
                    
                    val payload = ByteArray(payloadLen)
                    buffer.get(payload)
                    
                    OnionFrame(
                        version = version,
                        messageId = messageId,
                        sequence = sequence,
                        timestamp = timestamp,
                        frameType = frameType,
                        payload = payload
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode frame: ${e.message}")
                    null
                }
            }
        }
    }
    
    /**
     * Message payload structure for chat messages
     */
    data class ChatMessage(
        val text: String,
        val replyToId: String? = null,
        val attachmentId: String? = null
    ) {
        fun toBytes(): ByteArray {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val replyBytes = replyToId?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val attachBytes = attachmentId?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            
            val buffer = ByteBuffer.allocate(
                2 + textBytes.size +
                2 + replyBytes.size +
                2 + attachBytes.size
            )
            
            buffer.putShort(textBytes.size.toShort())
            buffer.put(textBytes)
            buffer.putShort(replyBytes.size.toShort())
            buffer.put(replyBytes)
            buffer.putShort(attachBytes.size.toShort())
            buffer.put(attachBytes)
            
            return buffer.array()
        }
        
        companion object {
            fun fromBytes(data: ByteArray): ChatMessage? {
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    
                    val textLen = buffer.getShort().toInt() and 0xFFFF
                    val textBytes = ByteArray(safeLen(buffer, textLen))
                    buffer.get(textBytes)
                    
                    val replyLen = buffer.getShort().toInt() and 0xFFFF
                    val replyBytes = ByteArray(safeLen(buffer, replyLen))
                    buffer.get(replyBytes)
                    
                    val attachLen = buffer.getShort().toInt() and 0xFFFF
                    val attachBytes = ByteArray(safeLen(buffer, attachLen))
                    buffer.get(attachBytes)
                    
                    ChatMessage(
                        text = String(textBytes, Charsets.UTF_8),
                        replyToId = if (replyLen > 0) String(replyBytes, Charsets.UTF_8) else null,
                        attachmentId = if (attachLen > 0) String(attachBytes, Charsets.UTF_8) else null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse chat message: ${e.message}")
                    null
                }
            }
        }
    }
    
    /**
     * Create a MESSAGE frame
     */
    fun createMessageFrame(
        text: String,
        sequence: Long,
        replyToId: String? = null
    ): OnionFrame {
        val message = ChatMessage(text = text, replyToId = replyToId)
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.MESSAGE,
            payload = message.toBytes()
        )
    }
    
    /**
     * Create an ACK frame for a received message
     */
    fun createAckFrame(originalMessageId: UUID, sequence: Long): OnionFrame {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(originalMessageId.mostSignificantBits)
        buffer.putLong(originalMessageId.leastSignificantBits)
        
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.ACK,
            payload = buffer.array()
        )
    }
    
    /**
     * Create a NACK frame requesting resend
     */
    fun createNackFrame(missingMessageId: UUID, sequence: Long): OnionFrame {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(missingMessageId.mostSignificantBits)
        buffer.putLong(missingMessageId.leastSignificantBits)
        
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.NACK,
            payload = buffer.array()
        )
    }
    
    /**
     * Create a PING frame for keepalive
     */
    fun createPingFrame(): OnionFrame {
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 0,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.PING,
            payload = ByteArray(0)
        )
    }
    
    /**
     * Create a PONG frame in response to PING
     */
    fun createPongFrame(pingId: UUID): OnionFrame {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(pingId.mostSignificantBits)
        buffer.putLong(pingId.leastSignificantBits)
        
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 0,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.PONG,
            payload = buffer.array()
        )
    }
    
    /**
     * Extract the acknowledged message ID from an ACK frame
     */
    fun getAckedMessageId(ackFrame: OnionFrame): UUID? {
        if ((ackFrame.frameType != FrameType.ACK && ackFrame.frameType != FrameType.NACK) || ackFrame.payload.size < 16) {
            return null
        }
        val buffer = ByteBuffer.wrap(ackFrame.payload)
        return UUID(buffer.getLong(), buffer.getLong())
    }
    
    /**
     * Validate message freshness (prevent replay attacks while supporting 7-day offline mailbox & BSP sync)
     */
    fun isMessageFresh(frame: OnionFrame, allowOfflineWindow: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val age = now - frame.timestamp
        val maxPastAge = if (allowOfflineWindow) OFFLINE_MESSAGE_FRESHNESS_MS else MESSAGE_FRESHNESS_MS
        return age in -MAX_FUTURE_SKEW_MS..maxPastAge
    }
    
    /**
     * Deduplication helper - tracks seen message IDs
     */
    class MessageDeduplicator(private val maxSize: Int = 32_768) {
        private val seenMessages = LinkedHashMap<UUID, Long>(maxSize, 0.75f, true)
        private var checkCounter = 0
        private var lastPrunedAt = 0L
        
        @Synchronized
        fun isDuplicate(messageId: UUID): Boolean {
            val now = System.currentTimeMillis()
            val retentionMs = OFFLINE_MESSAGE_FRESHNESS_MS + MAX_FUTURE_SKEW_MS
            // Amortize full-map pruning across 128 checks or 30s windows instead of O(N) on every frame.
            if ((++checkCounter and 127) == 0 || now - lastPrunedAt >= 30_000L) {
                seenMessages.entries.removeIf { now - it.value > retentionMs }
                lastPrunedAt = now
            }

            val existingTs = seenMessages[messageId]
            return if (existingTs != null && now - existingTs <= retentionMs) {
                true
            } else {
                seenMessages[messageId] = now
                while (seenMessages.size > maxSize) {
                    seenMessages.remove(seenMessages.keys.first())
                }
                false
            }
        }

        /**
         * Releases a frame that could not be durably processed so a transport
         * retry is allowed to reach the receiver again.
         */
        @Synchronized
        fun forget(messageId: UUID) {
            seenMessages.remove(messageId)
        }
        
        @Synchronized
        fun clear() {
            seenMessages.clear()
        }
    }
    
    /**
     * Sequence number manager for ordering
     */
    class SequenceManager {
        private var outboundSequence = 0L
        private var expectedInbound = 0L
        private val outOfOrderBuffer = mutableMapOf<Long, OnionFrame>()
        
        @Synchronized
        fun nextOutbound(): Long = outboundSequence++
        
        @Synchronized
        fun processInbound(frame: OnionFrame): List<OnionFrame> {
            // If it's control message (ACK, PING, etc), don't track sequence
            if (frame.frameType != FrameType.MESSAGE && frame.frameType != FrameType.FILE_CHUNK) {
                return listOf(frame)
            }
            
            val result = mutableListOf<OnionFrame>()
            
            when {
                frame.sequence == expectedInbound -> {
                    // In order - deliver and check buffer
                    result.add(frame)
                    expectedInbound++
                    
                    // Deliver any buffered frames that are now in order
                    while (outOfOrderBuffer.containsKey(expectedInbound)) {
                        result.add(outOfOrderBuffer.remove(expectedInbound)!!)
                        expectedInbound++
                    }
                }
                frame.sequence > expectedInbound -> {
                    // Out of order - buffer for later
                    outOfOrderBuffer[frame.sequence] = frame
                    Log.d(TAG, "Buffered out-of-order frame: seq=${frame.sequence}, expected=$expectedInbound")
                }
                // else: Old message, ignore
            }
            
            return result
        }
        
        @Synchronized
        fun reset() {
            outboundSequence = 0
            expectedInbound = 0
            outOfOrderBuffer.clear()
        }
        
        @Synchronized
        fun getMissingSequences(): List<Long> {
            if (outOfOrderBuffer.isEmpty()) return emptyList()
            val maxBuffered = outOfOrderBuffer.keys.maxOrNull() ?: return emptyList()
            return (expectedInbound until maxBuffered).filter { it !in outOfOrderBuffer }
        }
    }
    
    /**
     * Contact exchange payload for auto-pairing
     * 
     * When you add someone, this is automatically sent to them so they add you back
     */
    data class ContactExchange(
        val onionAddress: String,
        val nickname: String,
        val publicKey: String,
        val avatarId: String = "aurora"
    ) {
        fun toBytes(): ByteArray {
            val onionBytes = onionAddress.toByteArray(Charsets.UTF_8)
            val nickBytes = nickname.toByteArray(Charsets.UTF_8)
            val keyBytes = publicKey.toByteArray(Charsets.UTF_8)
            val avatarBytes = avatarId.toByteArray(Charsets.UTF_8)
            require(onionBytes.size in 1..96) { "Invalid onion endpoint length" }
            require(nickBytes.size in 1..160) { "Invalid nickname length" }
            require(keyBytes.size == 64) { "Invalid public key length" }
            require(avatarBytes.size in 1..16) { "Invalid avatar identifier length" }
            
            val buffer = ByteBuffer.allocate(
                2 + onionBytes.size +
                2 + nickBytes.size +
                2 + keyBytes.size +
                1 + avatarBytes.size
            )
            
            buffer.putShort(onionBytes.size.toShort())
            buffer.put(onionBytes)
            buffer.putShort(nickBytes.size.toShort())
            buffer.put(nickBytes)
            buffer.putShort(keyBytes.size.toShort())
            buffer.put(keyBytes)
            buffer.put(avatarBytes.size.toByte())
            buffer.put(avatarBytes)
            
            return buffer.array()
        }
        
        companion object {
            fun fromBytes(data: ByteArray): ContactExchange? {
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    
                    val onionLen = buffer.getShort().toInt() and 0xFFFF
                    if (onionLen !in 1..96 || onionLen > buffer.remaining()) return null
                    val onionBytes = ByteArray(safeLen(buffer, onionLen))
                    buffer.get(onionBytes)
                    
                    val nickLen = buffer.getShort().toInt() and 0xFFFF
                    if (nickLen !in 1..160 || nickLen > buffer.remaining()) return null
                    val nickBytes = ByteArray(safeLen(buffer, nickLen))
                    buffer.get(nickBytes)
                    
                    val keyLen = buffer.getShort().toInt() and 0xFFFF
                    if (keyLen != 64 || keyLen > buffer.remaining()) return null
                    val keyBytes = ByteArray(safeLen(buffer, keyLen))
                    buffer.get(keyBytes)
                    val avatarId = if (buffer.hasRemaining()) {
                        val avatarLength = buffer.get().toInt() and 0xFF
                        if (avatarLength !in 1..16 || avatarLength > buffer.remaining()) return null
                        String(ByteArray(safeLen(buffer, avatarLength)).also { buffer.get(it) }, Charsets.UTF_8)
                    } else {
                        "aurora"
                    }
                    if (buffer.hasRemaining()) return null
                    
                    ContactExchange(
                        onionAddress = String(onionBytes, Charsets.UTF_8),
                        nickname = String(nickBytes, Charsets.UTF_8),
                        publicKey = String(keyBytes, Charsets.UTF_8),
                        avatarId = avatarId
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse contact exchange: ${e.message}")
                    null
                }
            }
        }
    }
    
    /**
     * Create a CONTACT_EXCHANGE frame for auto-pairing
     */
    fun createContactExchangeFrame(
        myOnionAddress: String,
        myNickname: String,
        myPublicKey: String,
        myAvatarId: String = "aurora",
        sequence: Long
    ): OnionFrame {
        val exchange = ContactExchange(
            onionAddress = myOnionAddress,
            nickname = myNickname,
            publicKey = myPublicKey,
            avatarId = myAvatarId
        )
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.CONTACT_EXCHANGE,
            payload = exchange.toBytes()
        )
    }
    
    /**
     * Create a READ_RECEIPT frame to confirm message was read
     */
    fun createReadReceiptFrame(originalMessageId: UUID, senderOnion: String): ByteArray {
        val payload = ByteBuffer.allocate(16 + 2 + senderOnion.length)
        payload.putLong(originalMessageId.mostSignificantBits)
        payload.putLong(originalMessageId.leastSignificantBits)
        payload.putShort(senderOnion.length.toShort())
        payload.put(senderOnion.toByteArray())
        
        val frame = OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.READ_RECEIPT,
            payload = payload.array()
        )
        return frame.encode()
    }

    private const val BATCH_READ_MAGIC: Short = 0x5242

    /** Create a compact batched READ_RECEIPT frame carrying up to 24 UUIDs inside a 480B cell. */
    fun createBatchReadReceiptFrame(messageIds: List<UUID>, sequence: Long): OnionFrame {
        val bounded = messageIds.distinct().take(24)
        val buffer = ByteBuffer.allocate(4 + bounded.size * 16)
        buffer.putShort(BATCH_READ_MAGIC)
        buffer.putShort(bounded.size.toShort())
        for (id in bounded) {
            buffer.putLong(id.mostSignificantBits)
            buffer.putLong(id.leastSignificantBits)
        }
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.READ_RECEIPT,
            payload = buffer.array()
        )
    }

    /** Parse batched READ_RECEIPT payload, falling back to legacy single-UUID receipt format. */
    fun parseBatchReadReceipt(payload: ByteArray): List<UUID> {
        if (payload.size >= 4) {
            val buffer = ByteBuffer.wrap(payload)
            val magic = buffer.short
            if (magic == BATCH_READ_MAGIC) {
                val count = buffer.short.toInt() and 0xFFFF
                if (count in 1..64 && buffer.remaining() >= count * 16) {
                    return List(count) { UUID(buffer.getLong(), buffer.getLong()) }
                }
                return emptyList()
            }
        }
        return parseReadReceipt(payload)?.first?.let { listOf(it) } ?: emptyList()
    }
    
    /**
     * Parse a READ_RECEIPT frame payload
     */
    fun parseReadReceipt(payload: ByteArray): Pair<UUID, String>? {
        return try {
            val buffer = ByteBuffer.wrap(payload)
            val msgIdHigh = buffer.getLong()
            val msgIdLow = buffer.getLong()
            val originalMessageId = UUID(msgIdHigh, msgIdLow)
            
            val senderLen = buffer.getShort().toInt() and 0xFFFF
            val senderBytes = ByteArray(safeLen(buffer, senderLen))
            buffer.get(senderBytes)
            val senderOnion = String(senderBytes)
            
            Pair(originalMessageId, senderOnion)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse read receipt: ${e.message}")
            null
        }
    }

    // ===== Bramble Sync Protocol (BSP) =====

    /**
     * Sync Offer payload — sent during the Offer phase.
     * Contains a list of message IDs we have that the peer might not.
     * Uses GCS (Golomb-Coded Sets) for bandwidth-efficient set representation.
     */
    data class SyncOffer(
        val messageIds: List<String>,    // Message IDs we're offering
        val gcsFilterData: ByteArray?,   // Optional GCS filter for large sets
        val gcsP: Int = 0,               // GCS parameter P
        val gcsM: Long = 0               // GCS range M
    ) {
        fun toBytes(): ByteArray {
            val idsBytes = messageIds.joinToString("\n").toByteArray(Charsets.UTF_8)
            val gcsData = gcsFilterData ?: ByteArray(0)

            val buffer = ByteBuffer.allocate(
                4 + idsBytes.size +   // messageIds
                4 + gcsData.size +    // gcsFilterData
                4 +                   // gcsP
                8                     // gcsM
            )
            buffer.putInt(idsBytes.size)
            buffer.put(idsBytes)
            buffer.putInt(gcsData.size)
            buffer.put(gcsData)
            buffer.putInt(gcsP)
            buffer.putLong(gcsM)
            return buffer.array()
        }

        companion object {
            fun fromBytes(data: ByteArray): SyncOffer? {
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    val idsLen = buffer.getInt()
                    val idsBytes = ByteArray(safeLen(buffer, idsLen))
                    buffer.get(idsBytes)
                    val ids = String(idsBytes, Charsets.UTF_8).split("\n").filter { it.isNotEmpty() }

                    val gcsLen = buffer.getInt()
                    val gcsData = if (gcsLen > 0) {
                        ByteArray(safeLen(buffer, gcsLen)).also { buffer.get(it) }
                    } else null

                    val gcsP = buffer.getInt()
                    val gcsM = buffer.getLong()

                    SyncOffer(ids, gcsData, gcsP, gcsM)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SyncOffer: ${e.message}")
                    null
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SyncOffer) return false
            return messageIds == other.messageIds
        }
        override fun hashCode(): Int = messageIds.hashCode()
    }

    /**
     * Sync Request payload — peer responds with which messages they want.
     */
    data class SyncRequest(
        val requestedIds: List<String>   // Message IDs the peer wants us to send
    ) {
        fun toBytes(): ByteArray {
            val idsBytes = requestedIds.joinToString("\n").toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(4 + idsBytes.size)
            buffer.putInt(idsBytes.size)
            buffer.put(idsBytes)
            return buffer.array()
        }

        companion object {
            fun fromBytes(data: ByteArray): SyncRequest? {
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    val idsLen = buffer.getInt()
                    val idsBytes = ByteArray(safeLen(buffer, idsLen))
                    buffer.get(idsBytes)
                    val ids = String(idsBytes, Charsets.UTF_8).split("\n").filter { it.isNotEmpty() }
                    SyncRequest(ids)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SyncRequest: ${e.message}")
                    null
                }
            }
        }
    }

    /**
     * Sync Batch payload — contains the actual messages being sent.
     * Each entry is a serialized OnionFrame.
     */
    data class SyncBatch(
        val frames: List<ByteArray>      // Serialized OnionFrame data
    ) {
        fun toBytes(): ByteArray {
            // Format: [count:4B] [size1:4B][data1] [size2:4B][data2] ...
            var totalSize = 4 // count
            for (frame in frames) {
                totalSize += 4 + frame.size
            }
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.putInt(frames.size)
            for (frame in frames) {
                buffer.putInt(frame.size)
                buffer.put(frame)
            }
            return buffer.array()
        }

        companion object {
            fun fromBytes(data: ByteArray): SyncBatch? {
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    val count = buffer.getInt()
                    if (count < 0 || count > buffer.remaining() / 4) return null
                    val frames = mutableListOf<ByteArray>()
                    for (i in 0 until count) {
                        val size = buffer.getInt()
                        val frameData = ByteArray(safeLen(buffer, size))
                        buffer.get(frameData)
                        frames.add(frameData)
                    }
                    SyncBatch(frames)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SyncBatch: ${e.message}")
                    null
                }
            }
        }
    }

    /**
     * Sync Ack payload — confirms receipt of a batch.
     */
    data class SyncAck(
        val ackedIds: List<String>       // Message IDs successfully received
    ) {
        fun toBytes(): ByteArray {
            val idsBytes = ackedIds.joinToString("\n").toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(4 + idsBytes.size)
            buffer.putInt(idsBytes.size)
            buffer.put(idsBytes)
            return buffer.array()
        }

        companion object {
            fun fromBytes(data: ByteArray): SyncAck? {
                return try {
                    val buffer = ByteBuffer.wrap(data)
                    val idsLen = buffer.getInt()
                    val idsBytes = ByteArray(safeLen(buffer, idsLen))
                    buffer.get(idsBytes)
                    val ids = String(idsBytes, Charsets.UTF_8).split("\n").filter { it.isNotEmpty() }
                    SyncAck(ids)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SyncAck: ${e.message}")
                    null
                }
            }
        }
    }

    // ===== BSP Frame Creators =====

    /**
     * Create a SYNC_OFFER frame
     */
    fun createSyncOfferFrame(
        messageIds: List<String>,
        gcsFilterData: ByteArray? = null,
        gcsP: Int = 0,
        gcsM: Long = 0,
        sequence: Long = 0
    ): OnionFrame {
        val offer = SyncOffer(messageIds, gcsFilterData, gcsP, gcsM)
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.SYNC_OFFER,
            payload = offer.toBytes()
        )
    }

    /**
     * Create a SYNC_REQUEST frame
     */
    fun createSyncRequestFrame(
        requestedIds: List<String>,
        sequence: Long = 0
    ): OnionFrame {
        val request = SyncRequest(requestedIds)
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.SYNC_REQUEST,
            payload = request.toBytes()
        )
    }

    /**
     * Create a SYNC_BATCH frame
     */
    fun createSyncBatchFrame(
        frames: List<ByteArray>,
        sequence: Long = 0
    ): OnionFrame {
        val batch = SyncBatch(frames)
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.SYNC_BATCH,
            payload = batch.toBytes()
        )
    }

    /**
     * Create a SYNC_ACK frame
     */
    fun createSyncAckFrame(
        ackedIds: List<String>,
        sequence: Long = 0
    ): OnionFrame {
        val ack = SyncAck(ackedIds)
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.SYNC_ACK,
            payload = ack.toBytes()
        )
    }

    // ===== BTP Concealed Frame (DPI Resistance) =====

    /**
     * Concealed frame wrapper for DPI resistance.
     * Encrypts the ENTIRE frame (including header) using AES-GCM,
     * making it indistinguishable from random noise on the wire.
     *
     * Wire format:
     *   [12B nonce][encrypted_blob][16B tag]
     *
     * No plaintext version bytes, no frame type markers, no fixed sizes.
     * Traffic morphing: random padding added to vary frame sizes.
     */
    object ConcealedFrame {
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private const val MIN_PADDING = 0
        private const val MAX_PADDING = 256  // Random padding up to 256 bytes

        /**
         * Wrap a regular frame in concealment encryption.
         * Output looks like random noise — no distinguishable markers.
         *
         * @param frame  The OnionFrame to conceal
         * @param key    256-bit AES-GCM key (from Double Ratchet or shared secret)
         * @return       Encrypted blob indistinguishable from random data
         */
        fun conceal(frame: OnionFrame, key: ByteArray): ByteArray? {
            require(key.size == 32) { "Key must be 32 bytes" }

            return try {
                val plaintext = frame.encode()

                // Add random-length padding for traffic morphing
                val paddingLen = secureRandom.nextInt(MAX_PADDING - MIN_PADDING + 1) + MIN_PADDING
                val padding = ByteArray(paddingLen)
                secureRandom.nextBytes(padding)

                // Prepend actual data length so receiver can strip padding
                val paddedPlaintext = ByteBuffer.allocate(4 + plaintext.size + paddingLen)
                paddedPlaintext.putInt(plaintext.size)
                paddedPlaintext.put(plaintext)
                paddedPlaintext.put(padding)

                // Generate random nonce
                val nonce = ByteArray(NONCE_SIZE)
                secureRandom.nextBytes(nonce)

                // AES-256-GCM encrypt
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val keySpec = SecretKeySpec(key, "AES")
                val gcmSpec = GCMParameterSpec(TAG_SIZE * 8, nonce)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                val ciphertext = cipher.doFinal(paddedPlaintext.array())

                // Output: nonce || ciphertext (includes GCM tag)
                nonce + ciphertext
            } catch (e: Exception) {
                Log.e(TAG, "Failed to conceal frame: ${e.message}")
                null
            }
        }

        /**
         * Reveal a concealed frame by decrypting.
         *
         * @param data   Encrypted blob from the wire
         * @param key    256-bit AES-GCM key
         * @return       The original OnionFrame, or null if decryption fails
         */
        fun reveal(data: ByteArray, key: ByteArray): OnionFrame? {
            require(key.size == 32) { "Key must be 32 bytes" }

            if (data.size < NONCE_SIZE + TAG_SIZE + 4) return null

            return try {
                val nonce = data.copyOfRange(0, NONCE_SIZE)
                val ciphertext = data.copyOfRange(NONCE_SIZE, data.size)

                // AES-256-GCM decrypt
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val keySpec = SecretKeySpec(key, "AES")
                val gcmSpec = GCMParameterSpec(TAG_SIZE * 8, nonce)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                val paddedPlaintext = cipher.doFinal(ciphertext)

                // Extract actual data length and strip padding
                val buffer = ByteBuffer.wrap(paddedPlaintext)
                val dataLen = buffer.getInt()
                if (dataLen <= 0 || dataLen > paddedPlaintext.size - 4) return null

                val frameBytes = ByteArray(dataLen)
                buffer.get(frameBytes)

                OnionFrame.decode(frameBytes)
            } catch (e: Exception) {
                // Decryption failure is expected when trying wrong keys
                null
            }
        }

        /**
         * Generate a dummy/chaff frame for traffic analysis resistance.
         * Sent periodically to prevent timing-based correlation.
         */
        fun generateChaff(key: ByteArray): ByteArray? {
            val dummyFrame = OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = 0,
                timestamp = System.currentTimeMillis(),
                frameType = FrameType.PING,  // Will be discarded by receiver
                payload = ByteArray(0)
            )
            return conceal(dummyFrame, key)
        }
    }

    // ===== Mailbox Protocol =====

    /**
     * Mailbox exchange payload — peers share their mailbox .onion addresses
     * so messages can be delivered even when the recipient is offline.
     *
     * Inspired by Briar's Mailbox and Session's Swarm storage approach:
     * - Each user can optionally run a MailboxService (always-on device)
     * - The mailbox stores encrypted blobs until the owner fetches them
     * - Senders route messages to the recipient's mailbox when direct P2P fails
     */
    data class MailboxExchange(
        val senderOnion: String,       // Sender's permanent .onion address
        val mailboxOnion: String,      // Sender's mailbox .onion:port for offline delivery
        val capabilities: Int = 0      // Bitmask: 0x01=store, 0x02=relay, 0x04=community
    ) {
        fun toBytes(): ByteArray {
            val senderBytes = senderOnion.toByteArray(Charsets.UTF_8)
            val mailboxBytes = mailboxOnion.toByteArray(Charsets.UTF_8)
            val buffer = java.nio.ByteBuffer.allocate(
                2 + senderBytes.size +
                2 + mailboxBytes.size +
                4
            )

            buffer.putShort(senderBytes.size.toShort())
            buffer.put(senderBytes)
            buffer.putShort(mailboxBytes.size.toShort())
            buffer.put(mailboxBytes)
            buffer.putInt(capabilities)

            return buffer.array()
        }

        companion object {
            fun fromBytes(data: ByteArray): MailboxExchange? {
                return try {
                    val buffer = java.nio.ByteBuffer.wrap(data)

                    val senderLen = buffer.getShort().toInt() and 0xFFFF
                    val senderBytes = ByteArray(safeLen(buffer, senderLen))
                    buffer.get(senderBytes)

                    val mailboxLen = buffer.getShort().toInt() and 0xFFFF
                    val mailboxBytes = ByteArray(safeLen(buffer, mailboxLen))
                    buffer.get(mailboxBytes)

                    val capabilities = if (buffer.remaining() >= 4) buffer.getInt() else 0
                    MailboxExchange(
                        senderOnion = String(senderBytes, Charsets.UTF_8),
                        mailboxOnion = String(mailboxBytes, Charsets.UTF_8),
                        capabilities = capabilities
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse mailbox exchange: ${e.message}")
                    null
                }
            }
        }
    }

    /**
     * Create a MAILBOX_EXCHANGE frame to share our mailbox address with a peer.
     */
    fun createMailboxExchangeFrame(
        senderOnion: String,
        mailboxOnion: String,
        capabilities: Int = 0x01, // Default: store-only
        sequence: Long = 0
    ): OnionFrame {
        val exchange = MailboxExchange(senderOnion, mailboxOnion, capabilities)
        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.MAILBOX_EXCHANGE,
            payload = exchange.toBytes()
        )
    }

    /**
     * Create a MAILBOX_STORE frame wrapping a message for mailbox relay delivery.
     * The payload contains the original encrypted frame destined for the recipient.
     *
     * @param recipientFolderId The recipient's identifier on the mailbox server
     * @param encryptedPayload  The E2E encrypted message payload
     */
    fun createMailboxStoreFrame(
        recipientFolderId: String,
        encryptedPayload: ByteArray,
        sequence: Long = 0
    ): OnionFrame {
        val folderBytes = recipientFolderId.toByteArray(Charsets.UTF_8)
        val buffer = java.nio.ByteBuffer.allocate(
            2 + folderBytes.size + 4 + encryptedPayload.size
        )
        buffer.putShort(folderBytes.size.toShort())
        buffer.put(folderBytes)
        buffer.putInt(encryptedPayload.size)
        buffer.put(encryptedPayload)

        return OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = sequence,
            timestamp = System.currentTimeMillis(),
            frameType = FrameType.MAILBOX_STORE,
            payload = buffer.array()
        )
    }

    /**
     * Parse a MAILBOX_STORE frame to extract the recipient folder ID and encrypted payload.
     */
    fun parseMailboxStore(payload: ByteArray): Pair<String, ByteArray>? {
        return try {
            val buffer = java.nio.ByteBuffer.wrap(payload)
            val folderLen = buffer.getShort().toInt() and 0xFFFF
            val folderBytes = ByteArray(safeLen(buffer, folderLen))
            buffer.get(folderBytes)
            val payloadLen = buffer.getInt()
            val encryptedPayload = ByteArray(safeLen(buffer, payloadLen))
            buffer.get(encryptedPayload)
            Pair(String(folderBytes, Charsets.UTF_8), encryptedPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse mailbox store: ${e.message}")
            null
        }
    }
}


/** SECURITY: validates an attacker-controlled length field before allocating a buffer for it. */
private fun safeLen(buffer: java.nio.ByteBuffer, len: Int): Int {
    if (len < 0 || len > buffer.remaining()) throw IllegalArgumentException("Invalid length field: $len")
    return len
}