@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min
import kotlin.math.pow

/**
 * Persistent message queue for reliable offline delivery
 * 
 * Features:
 * - Encrypted-at-rest storage
 * - Exponential backoff retry (5s, 10s, 20s... max 5min)
 * - Delivery state tracking
 * - Crash recovery
 * - Automatic cleanup of old messages
 * 
 * Based on best practices from:
 * - Briar's delay-tolerant networking
 * - SimpleX's queue-based delivery
 * - Mobile offline-first patterns
 */
class OnionMessageQueue private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "OnionMessageQueue"
        private const val QUEUE_FILE = "onion_message_queue.json"
        private const val DELIVERY_STATUS_FILE = "delivery_status.json"
        private const val ENCRYPTED_SNAPSHOT_FILE = "onion_queue_v2.enc"
        private const val QUEUE_PREFS = "onion_message_queue_secure"
        private const val QUEUE_DATA_KEY = "queue_data"
        private const val DELIVERY_DATA_KEY = "delivery_data"
        private const val QUEUE_FILE_AES_KEY = "queue_file_aes_key"
        private const val SNAPSHOT_VERSION: Byte = 1
        
        // Retry configuration
        const val BASE_RETRY_DELAY_MS = 2_500L        // 2.5 seconds fast initial retry
        const val MAX_RETRY_DELAY_MS = 300_000L       // 5 minutes
        // Delivery is intentionally bounded by MESSAGE_TTL_MS rather than a
        // small attempt count. A Tor peer can be legitimately unreachable for
        // many hours (offline, Doze, or an onion-service rendezvous failure),
        // and treating that as a terminal send failure loses user data.
        const val MAX_RETRY_ATTEMPTS = Int.MAX_VALUE
        const val BACKOFF_MULTIPLIER = 1.8
        // Unacknowledged frames are retransmitted on an escalating schedule
        // (4.5s, 6.3s, 8.8s, 12.3s ... capped) so a slow circuit is not flooded
        // with duplicates while a dead one is still detected quickly.
        const val ACK_RETRY_DELAY_MS = 4_500L
        // Bulk image chunks traverse high-latency Tor circuits and must not flood duplicates at 4.5s.
        const val ATTACHMENT_ACK_RETRY_DELAY_MS = 14_000L
        const val MAX_ACK_RETRY_DELAY_MS = 90_000L
        const val ACK_RETRY_GROWTH = 1.4
        // Persisting the whole queue (every photo chunk) through encrypted
        // preferences is expensive. Writes are coalesced into one snapshot
        // per debounce window instead of one per state transition.
        const val SAVE_DEBOUNCE_MS = 250L
        // High-throughput bulk transfer window sized for up to 50 MB documents/videos while
        // still letting small control, chat, and call frames interleave cleanly.
        const val ATTACHMENT_WINDOW_SIZE = 384
        const val ATTACHMENT_DISPATCH_SLICE = 96
        private const val COMPACT_DELIVERED_THRESHOLD = 128
        
        // Cleanup
        const val MESSAGE_TTL_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L     // 1 hour
        
        @Volatile
        private var instance: OnionMessageQueue? = null
        
        fun getInstance(context: Context): OnionMessageQueue {
            val appCtx = context.applicationContext
            val current = instance
            if (current != null && current.appFilesDir == appCtx.filesDir) {
                return current
            }
            return synchronized(this) {
                val existing = instance
                if (existing != null && existing.appFilesDir == appCtx.filesDir) {
                    existing
                } else {
                    existing?.scope?.cancel()
                    OnionMessageQueue(appCtx).also { instance = it }
                }
            }
        }
    }
    
    private val appFilesDir: File = context.filesDir
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val mutex = Mutex()
    private val persistMutex = Mutex()
    private val saveRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    @Suppress("DEPRECATION")
    private val persistencePrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                QUEUE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable (${e.message}); using fallback prefs")
            context.getSharedPreferences("${QUEUE_PREFS}_fallback", Context.MODE_PRIVATE)
        }
    }
    
    // Message queues per recipient
    private val queues = ConcurrentHashMap<String, MutableList<QueuedMessage>>()
    // O(1) index from frame messageId -> QueuedMessage (protected by mutex)
    private val messageById = HashMap<String, QueuedMessage>()
    private var deliveredAttachmentChunksSinceCompact = 0

    // Live outgoing attachment progress tracking: uiMessageId -> counters
    data class OutgoingAttachmentStats(
        val sentUnits: Int,
        val ackedUnits: Int,
        val totalUnits: Int
    )

    private data class OutgoingTracker(
        val totalUnits: Int,
        val sentIds: HashSet<String> = HashSet(),
        val ackedIds: HashSet<String> = HashSet()
    )

    private val outgoingTrackers = ConcurrentHashMap<String, OutgoingTracker>()
    private val _outgoingAttachmentProgressFlow = MutableStateFlow<Map<String, OutgoingAttachmentStats>>(emptyMap())
    val outgoingAttachmentProgressFlow: StateFlow<Map<String, OutgoingAttachmentStats>> =
        _outgoingAttachmentProgressFlow.asStateFlow()
    
    // Delivery status tracking
    private val deliveryStatus = ConcurrentHashMap<String, DeliveryInfo>()
    
    // Flow for observing delivery status changes
    private val _deliveryUpdates = MutableSharedFlow<DeliveryUpdate>(replay = 0)
    val deliveryUpdates: SharedFlow<DeliveryUpdate> = _deliveryUpdates.asSharedFlow()
    
    // Queue processing state
    private var processingJob: Job? = null
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    init {
        loadFromDisk()
        startCleanupLoop()
    }

    data class LazyFileFrameSpec(
        val frameTypeCode: Byte,
        val sequence: Long,
        val timestamp: Long,
        val transferId: String,
        val sourcePath: String,
        val indexOrBlock: Int,
        val chunkStride: Int,
        val totalBytes: Int
    )
    
    /**
     * Queued message data structure
     */
    data class QueuedMessage(
        val id: String = UUID.randomUUID().toString(),
        val recipientOnion: String,
        val frameData: ByteArray,
        val mailboxPayload: ByteArray? = null,
        /** Chat row whose status this transport frame represents. */
        val uiMessageId: String = id,
        /** Only the final transport frame completes a multi-frame chat item. */
        val completesUiMessage: Boolean = true,
        /** The frame is durably held by the recipient's encrypted mailbox. */
        var mailboxStored: Boolean = false,
        var state: OnionMessageProtocol.DeliveryState = OnionMessageProtocol.DeliveryState.PENDING,
        var retryCount: Int = 0,
        var nextRetryAt: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
        var lastAttemptAt: Long = 0L,
        val lazyFileSpec: LazyFileFrameSpec? = null
    ) {
        fun materializeFrameData(attachmentTransfer: TorAttachmentTransfer): ByteArray {
            if (frameData.isNotEmpty()) return frameData
            val spec = lazyFileSpec ?: error("Missing frame data and lazy file spec for $id")
            val transferUuid = UUID.fromString(spec.transferId)
            val sourceFile = File(spec.sourcePath)
            val payload = when (spec.frameTypeCode) {
                OnionMessageProtocol.FrameType.FILE_CHUNK.code ->
                    attachmentTransfer.readChunkPayloadFromFile(
                        sourceFile = sourceFile,
                        transferId = transferUuid,
                        index = spec.indexOrBlock,
                        stride = spec.chunkStride,
                        totalBytes = spec.totalBytes
                    )
                OnionMessageProtocol.FrameType.FILE_PARITY.code ->
                    attachmentTransfer.readParityPayloadFromFile(
                        sourceFile = sourceFile,
                        transferId = transferUuid,
                        blockIndex = spec.indexOrBlock,
                        stride = spec.chunkStride,
                        totalBytes = spec.totalBytes
                    )
                else -> error("Unsupported lazy frame type: ${spec.frameTypeCode}")
            }
            val type = OnionMessageProtocol.FrameType.fromCode(spec.frameTypeCode)
                ?: error("Unknown lazy frame type: ${spec.frameTypeCode}")
            return OnionMessageProtocol.OnionFrame(
                messageId = UUID.fromString(id),
                sequence = spec.sequence,
                timestamp = spec.timestamp,
                frameType = type,
                payload = payload
            ).encode()
        }

        // For JSON serialization
        fun toSerializable(): SerializableQueuedMessage {
            return SerializableQueuedMessage(
                id = id,
                recipientOnion = recipientOnion,
                frameDataBase64 = if (frameData.isNotEmpty()) {
                    android.util.Base64.encodeToString(frameData, android.util.Base64.NO_WRAP)
                } else {
                    ""
                },
                mailboxPayloadBase64 = mailboxPayload?.let {
                    android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                },
                uiMessageId = uiMessageId,
                completesUiMessage = completesUiMessage,
                mailboxStored = mailboxStored,
                state = state.name,
                retryCount = retryCount,
                nextRetryAt = nextRetryAt,
                createdAt = createdAt,
                lastAttemptAt = lastAttemptAt,
                lazyFileSpec = lazyFileSpec
            )
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QueuedMessage) return false
            return id == other.id
        }
        
        override fun hashCode(): Int = id.hashCode()
    }
    
    data class SerializableQueuedMessage(
        val id: String,
        val recipientOnion: String,
        val frameDataBase64: String = "",
        val mailboxPayloadBase64: String? = null,
        // Nullable so queues written before this format upgrade can still load.
        val uiMessageId: String? = null,
        val completesUiMessage: Boolean? = null,
        val mailboxStored: Boolean? = null,
        val state: String,
        val retryCount: Int,
        val nextRetryAt: Long,
        val createdAt: Long,
        val lastAttemptAt: Long,
        val lazyFileSpec: LazyFileFrameSpec? = null
    ) {
        fun toQueuedMessage(): QueuedMessage {
            return QueuedMessage(
                id = id,
                recipientOnion = recipientOnion,
                frameData = if (frameDataBase64.isNotEmpty()) {
                    android.util.Base64.decode(frameDataBase64, android.util.Base64.NO_WRAP)
                } else {
                    ByteArray(0)
                },
                mailboxPayload = mailboxPayloadBase64?.let {
                    android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
                },
                uiMessageId = uiMessageId?.takeIf { it.isNotBlank() } ?: id,
                completesUiMessage = completesUiMessage ?: true,
                mailboxStored = mailboxStored ?: false,
                state = OnionMessageProtocol.DeliveryState.valueOf(state),
                retryCount = retryCount,
                nextRetryAt = nextRetryAt,
                createdAt = createdAt,
                lastAttemptAt = lastAttemptAt,
                lazyFileSpec = lazyFileSpec
            )
        }
    }
    
    /**
     * Delivery status information
     */
    data class DeliveryInfo(
        val messageId: String,
        var state: OnionMessageProtocol.DeliveryState,
        var sentAt: Long? = null,
        var deliveredAt: Long? = null,
        var readAt: Long? = null,
        var failReason: String? = null
    )
    
    /**
     * Delivery update event
     */
    data class DeliveryUpdate(
        val messageId: String,
        val newState: OnionMessageProtocol.DeliveryState,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Enqueue a message for delivery
     */
    suspend fun enqueue(
        recipientOnion: String,
        frame: OnionMessageProtocol.OnionFrame,
        mailboxPayload: ByteArray? = null,
        uiMessageId: String = frame.messageId.toString(),
        completesUiMessage: Boolean = true
    ): String = mutex.withLock {
        val message = QueuedMessage(
            id = frame.messageId.toString(),
            recipientOnion = recipientOnion,
            frameData = frame.encode(),
            mailboxPayload = mailboxPayload,
            uiMessageId = uiMessageId,
            completesUiMessage = completesUiMessage,
            state = OnionMessageProtocol.DeliveryState.PENDING,
            nextRetryAt = System.currentTimeMillis()
        )
        
        val queue = queues.getOrPut(recipientOnion) { mutableListOf() }
        queue.add(message)
        messageById[message.id] = message
        
        // Track delivery status
        deliveryStatus[message.id] = DeliveryInfo(
            messageId = message.id,
            state = OnionMessageProtocol.DeliveryState.PENDING
        )
        
        saveToDisk()
        
        Log.d(TAG, "📬 Enqueued message ${message.id} for $recipientOnion")
        
        // Transport delivery is owned by TorMessagingService so all messages use
        // the same direct-first/mailbox-fallback policy and CONTACT_EXCHANGE flow.
        
        message.id
    }

    data class BatchFrameSpec(
        val frame: OnionMessageProtocol.OnionFrame? = null,
        val mailboxPayload: ByteArray? = null,
        val uiMessageId: String = frame?.messageId?.toString().orEmpty(),
        val completesUiMessage: Boolean = true,
        val lazyId: UUID? = null,
        val lazySpec: LazyFileFrameSpec? = null
    )

    suspend fun enqueueBatch(
        recipientOnion: String,
        specs: List<BatchFrameSpec>
    ): List<String> = mutex.withLock {
        if (specs.isEmpty()) return@withLock emptyList()
        val now = System.currentTimeMillis()
        val queue = queues.getOrPut(recipientOnion) { mutableListOf() }
        val ids = ArrayList<String>(specs.size)
        val trackedByUi = HashMap<String, Int>()
        for (spec in specs) {
            val resolvedFrame = spec.frame
            val id = resolvedFrame?.messageId?.toString()
                ?: spec.lazyId?.toString()
                ?: UUID.randomUUID().toString()
            val encodedBytes = resolvedFrame?.encode() ?: ByteArray(0)
            val uiId = spec.uiMessageId.ifBlank { id }
            val qm = QueuedMessage(
                id = id,
                recipientOnion = recipientOnion,
                frameData = encodedBytes,
                mailboxPayload = spec.mailboxPayload,
                uiMessageId = uiId,
                completesUiMessage = spec.completesUiMessage,
                state = OnionMessageProtocol.DeliveryState.PENDING,
                nextRetryAt = now,
                lazyFileSpec = spec.lazySpec
            )
            queue.add(qm)
            messageById[id] = qm
            deliveryStatus[id] = DeliveryInfo(
                messageId = id,
                state = OnionMessageProtocol.DeliveryState.PENDING
            )
            if (isAttachmentFrame(qm)) {
                trackedByUi[uiId] = (trackedByUi[uiId] ?: 0) + 1
            }
            ids.add(id)
        }
        if (trackedByUi.isNotEmpty()) {
            trackedByUi.forEach { (uiId, count) ->
                val prev = outgoingTrackers[uiId]
                val updated = if (prev == null) {
                    OutgoingTracker(totalUnits = count)
                } else {
                    prev.copy(totalUnits = prev.totalUnits + count)
                }
                outgoingTrackers[uiId] = updated
            }
            publishOutgoingProgress()
        }
        saveToDisk()
        Log.d(TAG, "📬 Batch-enqueued ${ids.size} frames for $recipientOnion")
        ids
    }

    suspend fun markSendingBatch(messageIds: Collection<String>) = mutex.withLock {
        if (messageIds.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        for (messageId in messageIds) {
            findMessage(messageId)?.let { message ->
                if (message.state == OnionMessageProtocol.DeliveryState.SENT) {
                    message.retryCount++
                }
                message.lastAttemptAt = now
            }
            updateMessageState(messageId, OnionMessageProtocol.DeliveryState.SENDING)
        }
        saveToDisk()
    }

    suspend fun markSentBatch(messageIds: Collection<String>) = mutex.withLock {
        if (messageIds.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        var trackerChanged = false
        for (messageId in messageIds) {
            updateMessageState(messageId, OnionMessageProtocol.DeliveryState.SENT)
            val msg = findMessage(messageId)
            msg?.lastAttemptAt = now
            deliveryStatus[messageId]?.sentAt = now
            if (msg != null) {
                outgoingTrackers[msg.uiMessageId]?.let { tracker ->
                    if (tracker.sentIds.add(messageId)) trackerChanged = true
                }
            }
        }
        if (trackerChanged) publishOutgoingProgress()
        saveToDisk()
    }

    /**
     * Mark a message as actively sending.
     */
    suspend fun markSending(messageId: String) = mutex.withLock {
        findMessage(messageId)?.let { message ->
            if (message.state == OnionMessageProtocol.DeliveryState.SENT) {
                message.retryCount++
            }
            message.lastAttemptAt = System.currentTimeMillis()
        }
        updateMessageState(messageId, OnionMessageProtocol.DeliveryState.SENDING)
        saveToDisk()
    }
    
    /**
     * Mark a message as sent (awaiting ACK)
     */
    suspend fun markSent(messageId: String) = mutex.withLock {
        updateMessageState(messageId, OnionMessageProtocol.DeliveryState.SENT)
        val now = System.currentTimeMillis()
        findMessage(messageId)?.lastAttemptAt = now
        deliveryStatus[messageId]?.sentAt = now
        saveToDisk()
    }

    /**
     * A mailbox already holds a durable copy, so there is nothing useful to
     * upload again while we wait for its end-to-end ACK. Keep the frame in the
     * outbox and schedule a normal ACK/direct retry instead. This avoids
     * filling a peer's mailbox with duplicate encrypted blobs after a network
     * outage or process restart.
     */
    suspend fun deferAcknowledgementRetry(messageId: String) = mutex.withLock {
        val message = findMessage(messageId) ?: return@withLock
        if (message.state == OnionMessageProtocol.DeliveryState.DELIVERED ||
            message.state == OnionMessageProtocol.DeliveryState.FAILED
        ) return@withLock

        message.retryCount = message.retryCount.coerceAtMost(10_000 - 1) + 1
        message.state = OnionMessageProtocol.DeliveryState.SENT
        message.lastAttemptAt = System.currentTimeMillis()
        deliveryStatus[messageId]?.state = OnionMessageProtocol.DeliveryState.SENT
        saveToDisk()
    }

    /**
     * The recipient's mailbox accepted this frame. Keep it locally until its
     * end-to-end ACK arrives, but allow the next frame of the same attachment
     * to upload instead of waiting for a direct circuit.
     */
    suspend fun markMailboxStored(messageId: String) = mutex.withLock {
        findMessage(messageId)?.let { message ->
            message.mailboxStored = true
            message.state = OnionMessageProtocol.DeliveryState.SENT
            message.lastAttemptAt = System.currentTimeMillis()
        }
        updateMessageState(messageId, OnionMessageProtocol.DeliveryState.SENT)
        deliveryStatus[messageId]?.sentAt = System.currentTimeMillis()
        saveToDisk()
    }
    
    /**
     * Mark a message as delivered (ACK received)
     */
    suspend fun markDelivered(messageId: String) = mutex.withLock {
        val msg = findMessage(messageId)
        updateMessageState(messageId, OnionMessageProtocol.DeliveryState.DELIVERED)
        deliveryStatus[messageId]?.deliveredAt = System.currentTimeMillis()
        if (msg != null) {
            outgoingTrackers[msg.uiMessageId]?.let { tracker ->
                if (tracker.ackedIds.add(messageId)) {
                    publishOutgoingProgress()
                }
            }
        }
        if (msg != null && !msg.completesUiMessage && isAttachmentFrame(msg)) {
            if (++deliveredAttachmentChunksSinceCompact >= COMPACT_DELIVERED_THRESHOLD) {
                deliveredAttachmentChunksSinceCompact = 0
                compactDeliveredAttachmentChunksLocked()
            }
        } else {
            removeFromQueue(messageId)
        }
        saveToDisk()
        
        Log.i(TAG, "✅ Message $messageId delivered")
    }

    /** Marks a batch of chunk ACKs delivered under a single lock + coalesced snapshot. */
    suspend fun markDeliveredBatch(messageIds: Collection<String>) = mutex.withLock {
        if (messageIds.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        var trackerChanged = false
        for (messageId in messageIds) {
            val msg = findMessage(messageId)
            updateMessageState(messageId, OnionMessageProtocol.DeliveryState.DELIVERED)
            deliveryStatus[messageId]?.deliveredAt = now
            if (msg != null) {
                outgoingTrackers[msg.uiMessageId]?.let { tracker ->
                    if (tracker.ackedIds.add(messageId)) {
                        trackerChanged = true
                    }
                }
            }
            if (msg != null && !msg.completesUiMessage && isAttachmentFrame(msg)) {
                deliveredAttachmentChunksSinceCompact++
            } else {
                removeFromQueue(messageId)
            }
        }
        if (deliveredAttachmentChunksSinceCompact >= COMPACT_DELIVERED_THRESHOLD) {
            deliveredAttachmentChunksSinceCompact = 0
            compactDeliveredAttachmentChunksLocked()
        }
        if (trackerChanged) publishOutgoingProgress()
        saveToDisk()
    }

    /** Wakes any queued/failed frames for a UI message so a user-triggered retry sends immediately. */
    suspend fun resetFailedOrPendingForUiMessage(uiMessageId: String): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        var resetCount = 0
        for (queue in queues.values) {
            for (msg in queue) {
                if (msg.uiMessageId == uiMessageId &&
                    msg.state != OnionMessageProtocol.DeliveryState.DELIVERED &&
                    msg.state != OnionMessageProtocol.DeliveryState.READ
                ) {
                    msg.state = OnionMessageProtocol.DeliveryState.PENDING
                    msg.retryCount = 0
                    msg.nextRetryAt = now
                    msg.mailboxStored = false
                    deliveryStatus[msg.id]?.apply {
                        state = OnionMessageProtocol.DeliveryState.PENDING
                        failReason = null
                    }
                    resetCount++
                }
            }
        }
        if (resetCount > 0) saveToDisk()
        resetCount
    }

    private fun compactDeliveredAttachmentChunksLocked() {
        for (queue in queues.values) {
            val iter = queue.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                if (item.state == OnionMessageProtocol.DeliveryState.DELIVERED &&
                    !item.completesUiMessage &&
                    isAttachmentFrame(item)
                ) {
                    iter.remove()
                    messageById.remove(item.id)
                }
            }
        }
    }

    /** Removes all transport frames once the final frame of one attachment is ACKed. */
    suspend fun markAttachmentDelivered(uiMessageId: String) = mutex.withLock {
        var removed = false
        for (queue in queues.values) {
            val iter = queue.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                if (item.uiMessageId == uiMessageId) {
                    iter.remove()
                    messageById.remove(item.id)
                    deliveryStatus[item.id]?.apply {
                        state = OnionMessageProtocol.DeliveryState.DELIVERED
                        deliveredAt = System.currentTimeMillis()
                    }
                    removed = true
                }
            }
        }
        if (outgoingTrackers.remove(uiMessageId) != null) {
            publishOutgoingProgress()
        }
        if (removed) saveToDisk()
    }
    
    /**
     * Mark a message as read
     */
    suspend fun markRead(messageId: String) = mutex.withLock {
        updateMessageState(messageId, OnionMessageProtocol.DeliveryState.READ)
        deliveryStatus[messageId]?.readAt = System.currentTimeMillis()
        saveToDisk()
    }
    
    /**
     * Handle send failure with retry logic
     */
    suspend fun handleSendFailure(messageId: String, reason: String) = mutex.withLock {
        for (queue in queues.values) {
            val message = queue.find { it.id == messageId }
            if (message != null) {
                message.retryCount++
                message.lastAttemptAt = System.currentTimeMillis()
                
                if (isExpired(message, message.lastAttemptAt)) {
                    // A terminal failure is only valid after the explicit
                    // retention window has elapsed. Offline is a normal
                    // state for a Tor messenger, not a failed delivery.
                    message.state = OnionMessageProtocol.DeliveryState.FAILED
                    deliveryStatus[messageId]?.apply {
                        state = OnionMessageProtocol.DeliveryState.FAILED
                        failReason = reason
                    }
                    emitUpdate(messageId, OnionMessageProtocol.DeliveryState.FAILED)
                    Log.e(TAG, "❌ Message $messageId expired after ${message.retryCount} attempts: $reason")
                } else {
                    // Schedule retry with exponential backoff
                    val delay = calculateBackoff(message.retryCount)
                    message.nextRetryAt = System.currentTimeMillis() + delay
                    message.state = OnionMessageProtocol.DeliveryState.PENDING
                    Log.w(TAG, "🔄 Message $messageId retry ${message.retryCount}/$MAX_RETRY_ATTEMPTS in ${delay}ms")
                }
                
                saveToDisk()
                break
            }
        }
    }
    
    /**
     * Get pending messages for a recipient
     */
    suspend fun getPendingMessages(recipientOnion: String): List<QueuedMessage> = mutex.withLock {
        val now = System.currentTimeMillis()
        val messages = queues[recipientOnion] ?: return@withLock emptyList()
        buildDispatchBatch(messages, now)
    }
    
    /**
     * Get all pending messages across all recipients
     */
    suspend fun getAllPendingMessages(): Map<String, List<QueuedMessage>> = mutex.withLock {
        val now = System.currentTimeMillis()
        queues.mapValues { (_, messages) ->
            buildDispatchBatch(messages, now)
        }.filterValues { it.isNotEmpty() }
    }

    private fun buildDispatchBatch(messages: List<QueuedMessage>, now: Long): List<QueuedMessage> {
        val regular = messages
            .asSequence()
            .filter { isDueForDelivery(it, now) && !isAttachmentFrame(it) }
            .sortedWith(compareBy<QueuedMessage> { dispatchPriority(it) }.thenBy { it.createdAt })
            .toList()
        val attachmentSlice = nextAttachmentWindow(messages, now).take(ATTACHMENT_DISPATCH_SLICE)
        return regular + attachmentSlice
    }

    private fun dispatchPriority(message: QueuedMessage): Int = when (frameType(message)) {
        OnionMessageProtocol.FrameType.CALL_SIGNAL,
        OnionMessageProtocol.FrameType.CONTACT_EXCHANGE,
        OnionMessageProtocol.FrameType.MAILBOX_EXCHANGE,
        OnionMessageProtocol.FrameType.ACK,
        OnionMessageProtocol.FrameType.NACK -> 0
        OnionMessageProtocol.FrameType.SYNC_OFFER,
        OnionMessageProtocol.FrameType.SYNC_REQUEST,
        OnionMessageProtocol.FrameType.SYNC_BATCH,
        OnionMessageProtocol.FrameType.SYNC_ACK -> 2
        else -> 1
    }
    
    /**
     * Get delivery status for a message
     */
    fun getDeliveryStatus(messageId: String): OnionMessageProtocol.DeliveryState? {
        return deliveryStatus[messageId]?.state
    }

    /** Returns the transport record before an ACK removes it from the queue. */
    suspend fun getQueuedMessage(messageId: String): QueuedMessage? = mutex.withLock {
        findMessage(messageId)
    }
    
    /**
     * Get delivery status as flow
     */
    fun deliveryStatusFlow(messageId: String): Flow<OnionMessageProtocol.DeliveryState> {
        return deliveryUpdates
            .filter { it.messageId == messageId }
            .map { it.newState }
            .onStart { 
                deliveryStatus[messageId]?.state?.let { emit(it) }
            }
    }
    
    /**
     * Get queue stats
     */
    suspend fun getStats(): QueueStats = mutex.withLock {
        var pending = 0
        var sending = 0
        var failed = 0
        
        for (queue in queues.values) {
            for (msg in queue) {
                when (msg.state) {
                    OnionMessageProtocol.DeliveryState.PENDING -> pending++
                    OnionMessageProtocol.DeliveryState.SENDING,
                    OnionMessageProtocol.DeliveryState.SENT -> sending++
                    OnionMessageProtocol.DeliveryState.FAILED -> failed++
                    else -> {}
                }
            }
        }
        
        QueueStats(pending, sending, failed, queues.size)
    }
    
    data class QueueStats(
        val pendingCount: Int,
        val sendingCount: Int,
        val failedCount: Int,
        val recipientCount: Int
    )
    
    // ========== Private Helpers ==========
    
    private fun calculateBackoff(retryCount: Int): Long {
        val delay = (BASE_RETRY_DELAY_MS * BACKOFF_MULTIPLIER.pow(retryCount.toDouble())).toLong()
        // Add jitter to prevent thundering herd
        val jitter = (Math.random() * 1000).toLong()
        return min(delay + jitter, MAX_RETRY_DELAY_MS)
    }
    
    private fun updateMessageState(messageId: String, newState: OnionMessageProtocol.DeliveryState) {
        findMessage(messageId)?.let { message ->
            message.state = newState
        }
        deliveryStatus[messageId]?.state = newState
        scope.launch { emitUpdate(messageId, newState) }
    }

    private fun isAttachmentFrame(message: QueuedMessage): Boolean {
        return when (frameType(message)) {
            OnionMessageProtocol.FrameType.FILE_META,
            OnionMessageProtocol.FrameType.FILE_CHUNK,
            OnionMessageProtocol.FrameType.FILE_PARITY -> true
            else -> false
        }
    }

    /**
     * Returns the metadata frame (when due) plus a bounded in-order window of
     * chunks for the first unfinished attachment. Because Tor circuits and
     * mailbox uploads preserve frame order, pipelining the initial chunk window
     * immediately behind FILE_META avoids a full Tor RTT stall before chunk 0.
     * The next attachment still cannot overtake the current one.
     */
    private fun nextAttachmentWindow(messages: List<QueuedMessage>, now: Long): List<QueuedMessage> {
        val attachments = messages
            .asSequence()
            .filter(::isAttachmentFrame)
            .filter {
                it.state != OnionMessageProtocol.DeliveryState.DELIVERED &&
                    it.state != OnionMessageProtocol.DeliveryState.FAILED
            }
            .sortedBy { it.createdAt }
            .toList()
        if (attachments.isEmpty()) return emptyList()

        val first = attachments.first()
        if (frameType(first) == OnionMessageProtocol.FrameType.FILE_META) {
            val chunks = attachments.drop(1)
                .takeWhile { frameType(it) != OnionMessageProtocol.FrameType.FILE_META }
            val metaPrefix = if (!first.mailboxStored && isDueForDelivery(first, now)) {
                listOf(first)
            } else {
                emptyList()
            }
            val window = nextAttachmentChunkWindow(chunks, now)
            if (metaPrefix.isNotEmpty() || window.isNotEmpty()) {
                return metaPrefix + window
            }
            if (first.mailboxStored && chunks.all { it.mailboxStored }) {
                return nextAttachmentWindow(attachments.drop(1 + chunks.size), now)
            }
            return emptyList()
        }

        // The queued frames for one image are contiguous: its metadata has
        // already been ACKed and removed, and the next FILE_META starts another
        // image. Never interleave two images on the same ordered receiver state.
        val currentTransfer = attachments.takeWhile { frameType(it) != OnionMessageProtocol.FrameType.FILE_META }
        return nextAttachmentChunkWindow(currentTransfer, now)
    }

    private fun nextAttachmentChunkWindow(
        currentTransfer: List<QueuedMessage>,
        now: Long
    ): List<QueuedMessage> {
        val retryFrames = currentTransfer.filter {
            !it.mailboxStored &&
                it.state == OnionMessageProtocol.DeliveryState.SENT &&
                isDueForDelivery(it, now)
        }
        if (retryFrames.isNotEmpty()) return retryFrames.take(ATTACHMENT_WINDOW_SIZE)

        val inFlight = currentTransfer.count {
            !it.mailboxStored && it.state == OnionMessageProtocol.DeliveryState.SENT
        }
        val slots = ATTACHMENT_WINDOW_SIZE - inFlight
        if (slots <= 0) return emptyList()

        return currentTransfer
            .asSequence()
            .filter { it.state == OnionMessageProtocol.DeliveryState.PENDING }
            .filter { isDueForDelivery(it, now) }
            .take(slots)
            .toList()
    }

    private fun publishOutgoingProgress() {
        _outgoingAttachmentProgressFlow.value = outgoingTrackers.mapValues { (_, tracker) ->
            OutgoingAttachmentStats(
                sentUnits = tracker.sentIds.size,
                ackedUnits = tracker.ackedIds.size,
                totalUnits = tracker.totalUnits
            )
        }
    }

    private fun frameType(message: QueuedMessage): OnionMessageProtocol.FrameType? {
        message.lazyFileSpec?.let { spec ->
            OnionMessageProtocol.FrameType.fromCode(spec.frameTypeCode)?.let { return it }
        }
        val data = message.frameData
        return if (data.size >= OnionMessageProtocol.HEADER_SIZE) {
            OnionMessageProtocol.FrameType.fromCode(data[33])
        } else {
            OnionMessageProtocol.OnionFrame.decode(data)?.frameType
        }
    }

    private fun findMessage(messageId: String): QueuedMessage? = messageById[messageId]

    private fun ackRetryDelay(retryCount: Int, isBulkChunk: Boolean = false): Long {
        val baseDelay = if (isBulkChunk) ATTACHMENT_ACK_RETRY_DELAY_MS else ACK_RETRY_DELAY_MS
        val delay = baseDelay * ACK_RETRY_GROWTH.pow(retryCount.coerceIn(0, 12).toDouble())
        return min(delay.toLong(), MAX_ACK_RETRY_DELAY_MS)
    }
    
    private fun isDueForDelivery(message: QueuedMessage, now: Long): Boolean {
        if (isExpired(message, now)) return false
        return when (message.state) {
            OnionMessageProtocol.DeliveryState.PENDING -> message.nextRetryAt <= now
            OnionMessageProtocol.DeliveryState.SENT -> {
                // Older persisted queues can contain SENT frames without an
                // attempt timestamp. Treat those as due instead of leaving a
                // message permanently stuck on a single tick.
                val type = frameType(message)
                val isBulkChunk = type == OnionMessageProtocol.FrameType.FILE_CHUNK ||
                    type == OnionMessageProtocol.FrameType.FILE_PARITY
                message.lastAttemptAt <= 0L ||
                    now - message.lastAttemptAt >= ackRetryDelay(message.retryCount, isBulkChunk)
            }
            else -> false
        }
    }

    private fun isExpired(message: QueuedMessage, now: Long): Boolean =
        now - message.createdAt >= MESSAGE_TTL_MS
    
    private fun removeFromQueue(messageId: String) {
        val removed = messageById.remove(messageId) ?: return
        queues[removed.recipientOnion]?.remove(removed)
    }
    
    private suspend fun emitUpdate(messageId: String, state: OnionMessageProtocol.DeliveryState) {
        _deliveryUpdates.emit(DeliveryUpdate(messageId, state))
    }
    
    private fun triggerProcessing() {
        if (processingJob?.isActive == true) return
        processingJob = scope.launch {
            _isProcessing.value = true
            try {
                processQueues()
            } finally {
                _isProcessing.value = false
            }
        }
    }
    
    private suspend fun processQueues() {
        val pending = getAllPendingMessages()
        if (pending.isEmpty()) return
        
        val circuitManager = TorCircuitManager.getInstance(context)
        
        for ((recipientOnion, messages) in pending) {
            for (message in messages) {
                try {
                    // Mark as sending
                    mutex.withLock {
                        message.state = OnionMessageProtocol.DeliveryState.SENDING
                    }
                    emitUpdate(message.id, OnionMessageProtocol.DeliveryState.SENDING)
                    
                    // Attempt to send via TorCircuitManager (routes through Arti SOCKS proxy)
                    val success = circuitManager.sendData(recipientOnion, message.frameData)
                    
                    if (success) {
                        markSent(message.id)
                    } else {
                        handleSendFailure(message.id, "Send returned false")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message ${message.id}: ${e.message}")
                    handleSendFailure(message.id, e.message ?: "Unknown error")
                }
            }
        }
    }
    
    private fun startCleanupLoop() {
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                cleanup()
            }
        }
    }
    
    private suspend fun cleanup() = mutex.withLock {
        val now = System.currentTimeMillis()
        var removed = 0
        val expiredIds = mutableListOf<String>()
        
        for (queue in queues.values) {
            val toRemove = queue.filter {
                val expired = isExpired(it, now)
                if (expired && it.state != OnionMessageProtocol.DeliveryState.DELIVERED) {
                    expiredIds += it.id
                    deliveryStatus[it.id]?.apply {
                        state = OnionMessageProtocol.DeliveryState.FAILED
                        failReason = "Delivery window expired"
                    }
                }
                expired || it.state == OnionMessageProtocol.DeliveryState.FAILED
            }
            removed += toRemove.size
            toRemove.forEach { messageById.remove(it.id) }
            queue.removeAll(toRemove.toSet())
        }
        
        // Remove empty queues
        queues.entries.removeIf { it.value.isEmpty() }

        // Drop completed/expired UI trackers no longer present in any queue
        val activeUiIds = queues.values.flatten().mapTo(HashSet()) { it.uiMessageId }
        if (outgoingTrackers.keys.removeAll { it !in activeUiIds }) {
            publishOutgoingProgress()
        }

        // Cleanup old delivery status entries (never delete entries for still-pending queued messages)
        val activeIds = queues.values.flatten().mapTo(HashSet()) { it.id }
        deliveryStatus.entries.removeIf { (id, info) ->
            if (activeIds.contains(id) ||
                info.state == OnionMessageProtocol.DeliveryState.PENDING ||
                info.state == OnionMessageProtocol.DeliveryState.SENDING
            ) {
                false
            } else {
                val age = now - (info.readAt ?: info.deliveredAt ?: info.sentAt ?: now)
                age > MESSAGE_TTL_MS
            }
        }
        
        if (removed > 0) {
            expiredIds.forEach { emitUpdate(it, OnionMessageProtocol.DeliveryState.FAILED) }
            Log.d(TAG, "🧹 Cleaned up $removed old messages")
            saveToDisk()
        }
    }
    
    // ========== Persistence ==========
    
    private data class EncryptedQueuePayload(
        val queues: Map<String, List<SerializableQueuedMessage>> = emptyMap(),
        val deliveryStatus: Map<String, DeliveryInfo> = emptyMap()
    )

    private fun getOrCreateSnapshotKey(): ByteArray {
        val existingB64 = persistencePrefs.getString(QUEUE_FILE_AES_KEY, null)
        if (!existingB64.isNullOrBlank()) {
            val decoded = runCatching { android.util.Base64.decode(existingB64, android.util.Base64.NO_WRAP) }.getOrNull()
            if (decoded != null && decoded.size == 32) return decoded
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        persistencePrefs.edit()
            .putString(QUEUE_FILE_AES_KEY, android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP))
            .commit()
        return key
    }

    private fun readEncryptedSnapshotFile(file: File): EncryptedQueuePayload? = runCatching {
        if (!file.exists() || !file.isFile) return null
        val raw = file.readBytes()
        if (raw.size <= 1 + 12 + 16 || raw[0] != SNAPSHOT_VERSION) return null
        val iv = raw.copyOfRange(1, 13)
        val ciphertext = raw.copyOfRange(13, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(getOrCreateSnapshotKey(), "AES"), GCMParameterSpec(128, iv))
        val plain = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        gson.fromJson(plain, EncryptedQueuePayload::class.java)
    }.onFailure {
        Log.w(TAG, "Encrypted queue snapshot unreadable: ${it.message}")
    }.getOrNull()

    private fun writeEncryptedSnapshotFile(file: File, payloadJson: String) {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(getOrCreateSnapshotKey(), "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { out ->
            out.write(byteArrayOf(SNAPSHOT_VERSION))
            out.write(iv)
            out.write(encrypted)
            out.flush()
        }
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun loadFromDisk() {
        try {
            val snapshotFile = File(context.filesDir, ENCRYPTED_SNAPSHOT_FILE)
            val snapshot = readEncryptedSnapshotFile(snapshotFile)
            val legacyQueueFile = File(context.filesDir, QUEUE_FILE)
            val legacyStatusFile = File(context.filesDir, DELIVERY_STATUS_FILE)

            queues.clear()
            messageById.clear()
            deliveryStatus.clear()
            outgoingTrackers.clear()

            if (snapshot != null) {
                snapshot.queues.forEach { (recipient, messages) ->
                    val list = messages.map { it.toQueuedMessage() }.toMutableList()
                    queues[recipient] = list
                    list.forEach { messageById[it.id] = it }
                }
                deliveryStatus.putAll(snapshot.deliveryStatus)
            } else {
                val queueJson = persistencePrefs.getString(QUEUE_DATA_KEY, null)
                    ?: legacyQueueFile.takeIf { it.exists() }?.readText()
                val statusJson = persistencePrefs.getString(DELIVERY_DATA_KEY, null)
                    ?: legacyStatusFile.takeIf { it.exists() }?.readText()

                if (!queueJson.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, List<SerializableQueuedMessage>>>() {}.type
                    val data: Map<String, List<SerializableQueuedMessage>> = gson.fromJson(queueJson, type)
                    data.forEach { (recipient, messages) ->
                        val list = messages.map { it.toQueuedMessage() }.toMutableList()
                        queues[recipient] = list
                        list.forEach { messageById[it.id] = it }
                    }
                }
                if (!statusJson.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, DeliveryInfo>>() {}.type
                    val data: Map<String, DeliveryInfo> = gson.fromJson(statusJson, type)
                    deliveryStatus.putAll(data)
                }
                if (queues.isNotEmpty() || legacyQueueFile.exists() || legacyStatusFile.exists()) {
                    saveToDisk()
                }
            }
            rebuildTrackersFromQueuesLocked()
            Log.d(TAG, "Loaded ${queues.values.sumOf { it.size }} queued messages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load secure queue: ${e.message}")
        }
    }

    private fun rebuildTrackersFromQueuesLocked() {
        val trackers = HashMap<String, OutgoingTracker>()
        queues.values.forEach { list ->
            list.forEach { msg ->
                if (isAttachmentFrame(msg)) {
                    val existing = trackers.getOrPut(msg.uiMessageId) { OutgoingTracker(totalUnits = 0) }
                    val nextTotal = existing.totalUnits + 1
                    val updated = if (existing.totalUnits == nextTotal) existing else existing.copy(totalUnits = nextTotal)
                    if (msg.state == OnionMessageProtocol.DeliveryState.SENT ||
                        msg.state == OnionMessageProtocol.DeliveryState.SENDING
                    ) {
                        updated.sentIds.add(msg.id)
                    } else if (msg.state == OnionMessageProtocol.DeliveryState.DELIVERED ||
                        msg.state == OnionMessageProtocol.DeliveryState.READ
                    ) {
                        updated.sentIds.add(msg.id)
                        updated.ackedIds.add(msg.id)
                    }
                    trackers[msg.uiMessageId] = updated
                }
            }
        }
        outgoingTrackers.clear()
        outgoingTrackers.putAll(trackers)
        publishOutgoingProgress()
    }
    
    /**
     * Requests a durable snapshot of the queue. Requests are coalesced so at most
     * one AES-256-GCM encrypted file snapshot is written per [SAVE_DEBOUNCE_MS].
     */
    private fun saveToDisk() {
        if (!saveRequested.compareAndSet(false, true)) return
        scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveRequested.set(false)
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        persistMutex.withLock {
            try {
                // Snapshot under the queue lock so concurrent mutations cannot
                // produce a torn view; encrypt and commit outside it so delivery
                // never waits on disk or AES-GCM encryption.
                val snapshotJson = mutex.withLock {
                    val queueData = queues.mapValues { (_, messages) ->
                        messages.map { it.toSerializable() }
                    }
                    gson.toJson(EncryptedQueuePayload(queueData, deliveryStatus.toMap()))
                }
                withContext(Dispatchers.IO) {
                    writeEncryptedSnapshotFile(File(context.filesDir, ENCRYPTED_SNAPSHOT_FILE), snapshotJson)
                    if (persistencePrefs.contains(QUEUE_DATA_KEY) || persistencePrefs.contains(DELIVERY_DATA_KEY)) {
                        persistencePrefs.edit()
                            .remove(QUEUE_DATA_KEY)
                            .remove(DELIVERY_DATA_KEY)
                            .apply()
                    }
                }
                File(context.filesDir, QUEUE_FILE).delete()
                File(context.filesDir, DELIVERY_STATUS_FILE).delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save secure queue: ${e.message}")
            }
        }
    }

    /** Forces any pending snapshot to disk (used before shutdown). */
    suspend fun flush() {
        persistSnapshot()
    }
    
    /**
     * Clear all queues (for testing or reset)
     */
    suspend fun clearAll() = mutex.withLock {
        queues.clear()
        messageById.clear()
        deliveryStatus.clear()
        outgoingTrackers.clear()
        publishOutgoingProgress()
        persistencePrefs.edit().clear().commit()
        File(context.filesDir, ENCRYPTED_SNAPSHOT_FILE).delete()
        File(context.filesDir, QUEUE_FILE).delete()
        File(context.filesDir, DELIVERY_STATUS_FILE).delete()
        Log.i(TAG, "🗑️ Cleared all queued messages")
    }
}
