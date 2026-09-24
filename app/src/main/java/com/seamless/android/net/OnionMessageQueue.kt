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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
        private const val QUEUE_PREFS = "onion_message_queue_secure"
        private const val QUEUE_DATA_KEY = "queue_data"
        private const val DELIVERY_DATA_KEY = "delivery_data"
        
        // Retry configuration
        const val BASE_RETRY_DELAY_MS = 5_000L        // 5 seconds
        const val MAX_RETRY_DELAY_MS = 300_000L       // 5 minutes
        const val MAX_RETRY_ATTEMPTS = 50             // ~4 hours of retries
        const val BACKOFF_MULTIPLIER = 2.0
        const val ACK_RETRY_DELAY_MS = 20_000L        // Retry if no ACK after 20 seconds
        // File chunks are ordered, but serializing one ACK per 3 KB chunk makes
        // a small photo take minutes over Tor. Keep a modest bounded window on a
        // single circuit so loss stays recoverable without flooding it.
        const val ATTACHMENT_WINDOW_SIZE = 8
        
        // Cleanup
        const val MESSAGE_TTL_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L     // 1 hour
        
        @Volatile
        private var instance: OnionMessageQueue? = null
        
        fun getInstance(context: Context): OnionMessageQueue {
            return instance ?: synchronized(this) {
                instance ?: OnionMessageQueue(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val mutex = Mutex()
    @Suppress("DEPRECATION")
    private val persistencePrefs by lazy {
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
    }
    
    // Message queues per recipient
    private val queues = ConcurrentHashMap<String, MutableList<QueuedMessage>>()
    
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
        var lastAttemptAt: Long = 0L
    ) {
        // For JSON serialization
        fun toSerializable(): SerializableQueuedMessage {
            return SerializableQueuedMessage(
                id = id,
                recipientOnion = recipientOnion,
                frameDataBase64 = android.util.Base64.encodeToString(frameData, android.util.Base64.NO_WRAP),
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
                lastAttemptAt = lastAttemptAt
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
        val frameDataBase64: String,
        val mailboxPayloadBase64: String? = null,
        // Nullable so queues written before this format upgrade can still load.
        val uiMessageId: String? = null,
        val completesUiMessage: Boolean? = null,
        val mailboxStored: Boolean? = null,
        val state: String,
        val retryCount: Int,
        val nextRetryAt: Long,
        val createdAt: Long,
        val lastAttemptAt: Long
    ) {
        fun toQueuedMessage(): QueuedMessage {
            return QueuedMessage(
                id = id,
                recipientOnion = recipientOnion,
                frameData = android.util.Base64.decode(frameDataBase64, android.util.Base64.NO_WRAP),
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
                lastAttemptAt = lastAttemptAt
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
        deliveryStatus[messageId]?.sentAt = System.currentTimeMillis()
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
        updateMessageState(messageId, OnionMessageProtocol.DeliveryState.DELIVERED)
        deliveryStatus[messageId]?.deliveredAt = System.currentTimeMillis()
        
        // Remove from queue
        removeFromQueue(messageId)
        saveToDisk()
        
        Log.i(TAG, "✅ Message $messageId delivered")
    }

    /** Removes all transport frames once the final frame of one attachment is ACKed. */
    suspend fun markAttachmentDelivered(uiMessageId: String) = mutex.withLock {
        var removed = false
        for (queue in queues.values) {
            val matching = queue.filter { it.uiMessageId == uiMessageId }
            if (matching.isNotEmpty()) {
                queue.removeAll(matching.toSet())
                matching.forEach { deliveryStatus[it.id]?.apply {
                    state = OnionMessageProtocol.DeliveryState.DELIVERED
                    deliveredAt = System.currentTimeMillis()
                } }
                removed = true
            }
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
                
                if (message.retryCount >= MAX_RETRY_ATTEMPTS) {
                    // Move to failed state
                    message.state = OnionMessageProtocol.DeliveryState.FAILED
                    deliveryStatus[messageId]?.apply {
                        state = OnionMessageProtocol.DeliveryState.FAILED
                        failReason = reason
                    }
                    emitUpdate(messageId, OnionMessageProtocol.DeliveryState.FAILED)
                    Log.e(TAG, "❌ Message $messageId failed after ${message.retryCount} attempts: $reason")
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
        queues[recipientOnion]?.filter { isDueForDelivery(it, now) } ?: emptyList()
    }
    
    /**
     * Get all pending messages across all recipients
     */
    suspend fun getAllPendingMessages(): Map<String, List<QueuedMessage>> = mutex.withLock {
        val now = System.currentTimeMillis()
        queues.mapValues { (_, messages) ->
            val regular = messages.filter {
                isDueForDelivery(it, now) && !isAttachmentFrame(it)
            }
            regular + nextAttachmentWindow(messages, now)
        }.filterValues { it.isNotEmpty() }
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
        return when (OnionMessageProtocol.OnionFrame.decode(message.frameData)?.frameType) {
            OnionMessageProtocol.FrameType.FILE_META,
            OnionMessageProtocol.FrameType.FILE_CHUNK -> true
            else -> false
        }
    }

    /**
     * Returns one meta frame or a bounded in-order window of chunks for the
     * first unfinished attachment. The next attachment cannot overtake it.
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
            if (!first.mailboxStored) {
                return if (isDueForDelivery(first, now)) listOf(first) else emptyList()
            }

            // The metadata was committed to the recipient's mailbox. Its chunks
            // may now progress without waiting for an ACK that cannot be sent
            // until the recipient comes back online.
            val chunks = attachments.drop(1)
                .takeWhile { frameType(it) != OnionMessageProtocol.FrameType.FILE_META }
            val window = nextAttachmentChunkWindow(chunks, now)
            if (window.isNotEmpty()) return window
            if (chunks.all { it.mailboxStored }) {
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

    private fun frameType(message: QueuedMessage): OnionMessageProtocol.FrameType? =
        OnionMessageProtocol.OnionFrame.decode(message.frameData)?.frameType

    private fun findMessage(messageId: String): QueuedMessage? {
        for (queue in queues.values) {
            val message = queue.find { it.id == messageId }
            if (message != null) return message
        }
        return null
    }

    private fun isDueForDelivery(message: QueuedMessage, now: Long): Boolean {
        return when (message.state) {
            OnionMessageProtocol.DeliveryState.PENDING -> message.nextRetryAt <= now
            OnionMessageProtocol.DeliveryState.SENT -> {
                message.lastAttemptAt > 0L &&
                    now - message.lastAttemptAt >= ACK_RETRY_DELAY_MS &&
                    message.retryCount < MAX_RETRY_ATTEMPTS
            }
            else -> false
        }
    }
    
    private fun removeFromQueue(messageId: String) {
        for (queue in queues.values) {
            queue.removeIf { it.id == messageId }
        }
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
        
        for (queue in queues.values) {
            val toRemove = queue.filter { 
                now - it.createdAt > MESSAGE_TTL_MS ||
                it.state == OnionMessageProtocol.DeliveryState.FAILED
            }
            removed += toRemove.size
            queue.removeAll(toRemove.toSet())
        }
        
        // Remove empty queues
        queues.entries.removeIf { it.value.isEmpty() }
        
        // Cleanup old delivery status entries
        deliveryStatus.entries.removeIf { (_, info) ->
            val age = now - (info.readAt ?: info.deliveredAt ?: info.sentAt ?: 0L)
            age > MESSAGE_TTL_MS
        }
        
        if (removed > 0) {
            Log.d(TAG, "🧹 Cleaned up $removed old messages")
            saveToDisk()
        }
    }
    
    // ========== Persistence ==========
    
    private fun loadFromDisk() {
        try {
            // Migrate legacy files once, then retain all pending frames in
            // Keystore-backed encrypted preferences. Synchronous loading avoids
            // a new enqueue overwriting data that has not finished loading yet.
            val legacyQueueFile = File(context.filesDir, QUEUE_FILE)
            val legacyStatusFile = File(context.filesDir, DELIVERY_STATUS_FILE)
            val queueJson = persistencePrefs.getString(QUEUE_DATA_KEY, null)
                ?: legacyQueueFile.takeIf { it.exists() }?.readText()
            val statusJson = persistencePrefs.getString(DELIVERY_DATA_KEY, null)
                ?: legacyStatusFile.takeIf { it.exists() }?.readText()

            if (!queueJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, List<SerializableQueuedMessage>>>() {}.type
                val data: Map<String, List<SerializableQueuedMessage>> = gson.fromJson(queueJson, type)
                queues.clear()
                data.forEach { (recipient, messages) ->
                    queues[recipient] = messages.map { it.toQueuedMessage() }.toMutableList()
                }
            }
            if (!statusJson.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, DeliveryInfo>>() {}.type
                val data: Map<String, DeliveryInfo> = gson.fromJson(statusJson, type)
                deliveryStatus.clear()
                deliveryStatus.putAll(data)
            }
            if (legacyQueueFile.exists() || legacyStatusFile.exists()) saveToDisk()
            Log.d(TAG, "Loaded ${queues.values.sumOf { it.size }} queued messages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load secure queue: ${e.message}")
        }
    }
    
    private fun saveToDisk() {
        try {
            val queueData = queues.mapValues { (_, messages) ->
                messages.map { it.toSerializable() }
            }
            val committed = persistencePrefs.edit()
                .putString(QUEUE_DATA_KEY, gson.toJson(queueData))
                .putString(DELIVERY_DATA_KEY, gson.toJson(deliveryStatus.toMap()))
                .commit()
            if (!committed) throw IllegalStateException("Secure queue commit failed")
            File(context.filesDir, QUEUE_FILE).delete()
            File(context.filesDir, DELIVERY_STATUS_FILE).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save secure queue: ${e.message}")
        }
    }
    
    /**
     * Clear all queues (for testing or reset)
     */
    suspend fun clearAll() = mutex.withLock {
        queues.clear()
        deliveryStatus.clear()
        persistencePrefs.edit().clear().commit()
        File(context.filesDir, QUEUE_FILE).delete()
        File(context.filesDir, DELIVERY_STATUS_FILE).delete()
        Log.i(TAG, "🗑️ Cleared all queued messages")
    }
}
