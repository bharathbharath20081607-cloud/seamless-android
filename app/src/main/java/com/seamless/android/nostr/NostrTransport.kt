package com.seamless.android.nostr

import android.content.Context
import android.util.Log
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.PendingNostrMessage
import com.seamless.android.model.ReadReceipt
import com.seamless.android.model.NoisePayloadType
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Minimal Nostr transport for offline sending
 * Direct port from iOS NostrTransport for 100% compatibility
 */
class NostrTransport(
    private val context: Context,
    var senderPeerID: String = ""
) {
    
    // Throttle READ receipts to avoid relay rate limits (like iOS)
    private data class QueuedRead(
        val receipt: ReadReceipt,
        val peerID: String
    )
    
    private val readQueue = ConcurrentLinkedQueue<QueuedRead>()
    private var isSendingReadAcks = false
    private val transportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // ROBUST MESSAGING: Message queue for offline/retry support
    private data class PendingMessage(
        val content: String,
        val recipientPeerID: String,
        val recipientNickname: String,
        val messageID: String,
        var retryCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    private val pendingMessages = ConcurrentLinkedQueue<PendingMessage>()
    private var isProcessingQueue = false
    
    // OFFLINE-FIRST: Room database for persistent queue
    private val pendingDao by lazy { AppDatabase.getDatabase(context).pendingNostrMessageDao() }
    
    companion object {
        private const val TAG = "NostrTransport"
        private const val READ_ACK_INTERVAL = com.seamless.android.util.AppConstants.Nostr.READ_ACK_INTERVAL_MS
        // PRODUCTION: Enhanced retry with exponential backoff
        private const val MAX_RETRY_COUNT = 5  // Increased from 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L  // Start at 2s
        private const val MAX_RETRY_DELAY_MS = 30000L  // Cap at 30s
        private const val MESSAGE_EXPIRE_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        @Volatile
        private var INSTANCE: NostrTransport? = null
        
        fun getInstance(context: Context): NostrTransport {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NostrTransport(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Check if there are pending messages waiting to be sent.
     * Used by NostrSyncWorker to determine if sync is complete.
     */
    fun hasPendingMessages(): Boolean = pendingMessages.isNotEmpty()
    
    init {
        // Start connection monitor to process queued messages when relay connects
        startConnectionMonitor()
        // OFFLINE-FIRST: Load any persisted messages from previous sessions
        loadPersistedMessages()
    }
    
    private fun loadPersistedMessages() {
        transportScope.launch {
            try {
                val persisted = pendingDao.getAllPending()
                if (persisted.isNotEmpty()) {
                    Log.i(TAG, "📂 Loading ${persisted.size} persisted pending messages from database")
                    persisted.forEach { dbMsg ->
                        // Only add if not already in memory queue
                        if (pendingMessages.none { it.messageID == dbMsg.messageID }) {
                            pendingMessages.offer(PendingMessage(
                                content = dbMsg.content,
                                recipientPeerID = dbMsg.recipientPeerID,
                                recipientNickname = dbMsg.recipientNickname,
                                messageID = dbMsg.messageID,
                                retryCount = dbMsg.retryCount,
                                createdAt = dbMsg.createdAt
                            ))
                        }
                    }
                    // Trigger processing if relays connected
                    val relayManager = NostrRelayManager.getInstance(context)
                    if (relayManager.isConnected.value == true) {
                        processMessageQueue()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load persisted messages: ${e.message}")
            }
        }
    }
    
    private fun startConnectionMonitor() {
        transportScope.launch {
            try {
                NostrRelayManager.getInstance(context).isConnected.observeForever { connected ->
                    if (connected) {
                        Log.i(TAG, "🌐 Relay connected - processing pending messages")
                        processMessageQueue()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start connection monitor: ${e.message}")
            }
        }
    }
    
    private fun processMessageQueue() {
        if (isProcessingQueue) return
        if (pendingMessages.isEmpty()) return
        
        isProcessingQueue = true
        transportScope.launch {
            try {
                val now = System.currentTimeMillis()
                // Process all pending messages
                while (pendingMessages.isNotEmpty()) {
                    val msg = pendingMessages.peek() ?: break
                    
                    // Skip expired messages
                    if (now - msg.createdAt > MESSAGE_EXPIRE_MS) {
                        pendingMessages.poll()
                        // OFFLINE-FIRST: Also remove from persistent storage
                        try { pendingDao.deleteById(msg.messageID) } catch (_: Exception) {}
                        Log.w(TAG, "⏰ Expired pending message ${msg.messageID.take(8)}...")
                        continue
                    }
                    
                    val success = sendMessageNow(msg.content, msg.recipientPeerID, msg.recipientNickname, msg.messageID)
                    if (success) {
                        pendingMessages.poll()
                        // OFFLINE-FIRST: Remove from persistent storage on success
                        try { pendingDao.deleteById(msg.messageID) } catch (_: Exception) {}
                        Log.i(TAG, "✅ Sent queued message ${msg.messageID.take(8)}...")
                    } else if (msg.retryCount < MAX_RETRY_COUNT) {
                        msg.retryCount++
                        // OFFLINE-FIRST: Update retry count in DB
                        try { pendingDao.incrementRetryCount(msg.messageID) } catch (_: Exception) {}
                        // PRODUCTION: Exponential backoff (2s, 4s, 8s, 16s, 30s cap)
                        val backoffDelay = (INITIAL_RETRY_DELAY_MS * (1 shl (msg.retryCount - 1)))
                            .coerceAtMost(MAX_RETRY_DELAY_MS)
                        Log.d(TAG, "⏳ Retry ${msg.retryCount}/$MAX_RETRY_COUNT in ${backoffDelay/1000}s for ${msg.messageID.take(8)}...")
                        delay(backoffDelay)
                    } else {
                        pendingMessages.poll()
                        // OFFLINE-FIRST: Remove from persistent storage after max retries
                        try { pendingDao.deleteById(msg.messageID) } catch (_: Exception) {}
                        Log.e(TAG, "❌ Failed to send message after $MAX_RETRY_COUNT retries: ${msg.messageID.take(8)}...")
                    }
                }
            } finally {
                isProcessingQueue = false
            }
        }
    }
    
    // MARK: - Transport Interface Methods
    
    val myPeerID: String get() = senderPeerID
    
    /**
     * Send a private message with robust queuing and retry support
     * Messages are queued if relays aren't connected and auto-sent when connection is established
     */
    fun sendPrivateMessage(
        content: String,
        to: String,
        recipientNickname: String,
        messageID: String
    ) {
        // OFFLINE-FIRST: Persist to database FIRST for crash safety
        transportScope.launch {
            try {
                pendingDao.insert(PendingNostrMessage(
                    messageID = messageID,
                    recipientPeerID = to,
                    recipientNickname = recipientNickname,
                    content = content
                ))
                Log.d(TAG, "💾 Persisted message to DB: ${messageID.take(8)}...")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist message to DB: ${e.message}")
            }
            
            // Try immediate send
            val success = sendMessageNow(content, to, recipientNickname, messageID)
            if (success) {
                // Remove from DB on success
                try { pendingDao.deleteById(messageID) } catch (_: Exception) {}
            } else {
                val relayManager = NostrRelayManager.getInstance(context)
                val isConnected = relayManager.isConnected.value == true
                val queueReason = if (!isConnected) "Relays Disconnected" else "Send Failed (Check Logs)"
                
                // Queue for retry when relay connects (in-memory for fast processing)
                pendingMessages.offer(PendingMessage(content, to, recipientNickname, messageID))
                Log.i(TAG, "📬 Message queued for retry ($queueReason): ${messageID.take(8)}...")
                
                // ROBUST DELIVERY: Schedule background sync to ensure delivery even if app is killed
                NostrSyncWorker.scheduleSync(context)
            }
        }
    }
    
    /**
     * Send a file privately via Nostr for long-distance messaging
     * PRODUCTION FIX: Enable photo/file sending over internet when Bluetooth unavailable
     */
    fun sendFilePrivate(
        filePacket: com.seamless.android.model.BitchatFilePacket,
        to: String
    ) {
        transportScope.launch {
            try {
                // Check if relays are connected
                val relayManager = NostrRelayManager.getInstance(context)
                val isConnected = relayManager.isConnected.value == true
                
                if (!isConnected) {
                    Log.w(TAG, "⚠️ Relays not connected, will attempt to queue file chunks internally...")
                    // We continue instead of returning, relying on NostrRelayManager's outgoing queue
                }
                
                // Resolve recipient's Nostr public key
                val recipientNostrPubkey = resolveNostrPublicKey(to)
                if (recipientNostrPubkey == null) {
                    Log.w(TAG, "No Nostr public key for peerID: $to - cannot send file")
                    return@launch
                }
                
                val senderIdentity = NostrIdentityBridge.getCurrentNostrIdentity(context)
                if (senderIdentity == null) {
                    Log.e(TAG, "No Nostr identity available for file send")
                    return@launch
                }
                
                Log.d(TAG, "📷 Sending file to ${recipientNostrPubkey.take(16)}... via Nostr")
                
                // Convert recipient npub -> hex
                val recipientHex = try {
                    val (hrp, data) = Bech32.decode(recipientNostrPubkey)
                    if (hrp != "npub") {
                        Log.e(TAG, "Recipient key not npub (hrp=$hrp)")
                        return@launch
                    }
                    data.joinToString("") { "%02x".format(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode npub -> hex: $e")
                    return@launch
                }
                
                // Lookup the recipient's current BitChat peer ID
                // For internet-only users, fallback to "nostr:npub" format
                val recipientPeerIDForEmbed = try {
                    com.seamless.android.favorites.FavoritesPersistenceService.shared
                        .findPeerIDForNostrPubkey(recipientNostrPubkey)
                } catch (_: Exception) { null }
                    ?: if (recipientNostrPubkey.startsWith("npub")) {
                        "nostr:$recipientNostrPubkey".also {
                            Log.d(TAG, "Using Nostr-prefixed peerID for internet-only file recipient")
                        }
                    } else null
                
                if (recipientPeerIDForEmbed.isNullOrBlank()) {
                    Log.e(TAG, "No peerID stored for recipient npub for file")
                    return@launch
                }
                
                // PRODUCTION FIX: Chunking Logic for Reliable Delivery
                // Split file into 40KB chunks to fit within Relay limits
                val CHUNK_SIZE = 40 * 1024
                val totalContent = filePacket.content
                val totalChunks = (totalContent.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                val transferId = java.util.UUID.randomUUID().toString()
                
                Log.i(TAG, "📤 Chunking file ${filePacket.fileName} (${totalContent.size} bytes) into $totalChunks chunks (ID: ${transferId.take(8)})")
                
                for (i in 0 until totalChunks) {
                    val start = i * CHUNK_SIZE
                    val end = kotlin.math.min(start + CHUNK_SIZE, totalContent.size)
                    val chunkContent = totalContent.copyOfRange(start, end)
                    
                    // Create chunk packet using copy()
                    val chunkPacket = filePacket.copy(
                        content = chunkContent,
                        chunkIndex = i,
                        totalChunks = totalChunks,
                        transferId = transferId
                    )
                    
                    val embedded = NostrEmbeddedBitChat.encodeFileForNostr(
                        filePacket = chunkPacket,
                        recipientPeerID = recipientPeerIDForEmbed,
                        senderPeerID = senderPeerID
                    )
                    
                    if (embedded == null) {
                        Log.e(TAG, "Failed to embed chunk $i for Nostr")
                        continue
                    }
                    
                    val giftWraps = NostrProtocol.createPrivateMessage(
                        content = embedded,
                        recipientPubkey = recipientHex,
                        senderIdentity = senderIdentity
                    )
                    
                    // Send to network
                    giftWraps.forEach { event ->
                        relayManager.sendEvent(event)
                    }
                    
                    // Throttle to avoid flooding relays
                    delay(50)
                }
                
                Log.i(TAG, "✅ Sent all $totalChunks chunks for ${filePacket.fileName}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file via Nostr: ${e.message}")
            }
        }
    }
    
    /**
     * Attempt immediate message send - returns true if successful
     * PRODUCTION: Uses NIP-65 Gossip model for intelligent relay routing
     */
    private suspend fun sendMessageNow(
        content: String,
        to: String,
        recipientNickname: String,
        messageID: String
    ): Boolean {
        return try {
            // Check if relays are connected
            val relayManager = NostrRelayManager.getInstance(context)
            val isConnected = relayManager.isConnected.value == true
            if (!isConnected) {
                Log.w(TAG, "⏳ Relays not connected, message will be queued")
                return false
            }
            
            // Resolve recipient's Nostr public key
            val recipientNostrPubkey = resolveNostrPublicKey(to)
            if (recipientNostrPubkey == null) {
                Log.e(TAG, "❌ SEND FAILED: No Nostr public key found for peerID: $to")
                Log.e(TAG, "   → Is this user added as a favorite? Check FavoritesPersistenceService")
                Log.e(TAG, "   → PeerID format: ${if (to.startsWith("npub") || to.startsWith("nostr:")) "Nostr" else if (to.length == 16) "16-hex mesh" else if (to.length == 64) "64-hex noise" else "unknown"}")
                return false
            }
            
            val senderIdentity = NostrIdentityBridge.getCurrentNostrIdentity(context)
            if (senderIdentity == null) {
                Log.e(TAG, "No Nostr identity available")
                return false
            }
            
            Log.d(TAG, "📤 Sending PM to ${recipientNostrPubkey.take(16)}... for peerID ${to.take(8)}... id=${messageID.take(8)}...")
            
            // Convert recipient npub -> hex (x-only)
            val recipientHex = try {
                val (hrp, data) = Bech32.decode(recipientNostrPubkey)
                if (hrp != "npub") {
                    Log.e(TAG, "NostrTransport: recipient key not npub (hrp=$hrp)")
                    return false
                }
                data.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e(TAG, "NostrTransport: failed to decode npub -> hex: $e")
                return false
            }
            
            // PRODUCTION: NIP-65 Gossip model - get recipient's preferred relays
            val deliveryRelays = try {
                Nip65RelayRegistry.shared.getDeliveryRelays(recipientHex)
            } catch (e: Exception) {
                Log.w(TAG, "NIP-65 registry unavailable, using defaults")
                Nip65RelayRegistry.DEFAULT_RELAYS.toSet()
            }
            
            // Trigger background NIP-65 refresh if we don't have their data
            try {
                if (!Nip65RelayRegistry.shared.hasRelayData(recipientHex)) {
                    Nip65RelayRegistry.shared.requestRelayListRefresh(recipientHex)
                }
            } catch (_: Exception) { /* Ignore */ }
            
            // Lookup the recipient's current BitChat peer ID using favorites mapping
            // For internet-only users (no mesh contact), fallback to "nostr:npub" as peerID
            val recipientPeerIDForEmbed = try {
                com.seamless.android.favorites.FavoritesPersistenceService.shared
                    .findPeerIDForNostrPubkey(recipientNostrPubkey)
            } catch (_: Exception) { null }
                ?: if (recipientNostrPubkey.startsWith("npub")) {
                    // Internet-only user: use nostr:npub as peerID format
                    "nostr:$recipientNostrPubkey".also {
                        Log.d(TAG, "Using Nostr-prefixed peerID for internet-only recipient")
                    }
                } else null
            
            if (recipientPeerIDForEmbed.isNullOrBlank()) {
                Log.e(TAG, "NostrTransport: no peerID stored for recipient npub; cannot embed PM. npub=${recipientNostrPubkey.take(16)}...")
                return false
            }
            
            val embedded = NostrEmbeddedBitChat.encodePMForNostr(
                content = content,
                messageID = messageID,
                recipientPeerID = recipientPeerIDForEmbed,
                senderPeerID = senderPeerID
            )
            
            if (embedded == null) {
                Log.e(TAG, "NostrTransport: failed to embed PM packet")
                return false
            }
            
            val giftWraps = NostrProtocol.createPrivateMessage(
                content = embedded,
                recipientPubkey = recipientHex,
                senderIdentity = senderIdentity
            )
            
            // PRODUCTION: Send to ALL delivery relays (Gossip model)
            Log.d(TAG, "📡 Publishing to ${deliveryRelays.size} relays (Gossip model)")
            giftWraps.forEach { event ->
                Log.d(TAG, "📤 Sending PM giftWrap id=${event.id.take(16)}...")
                relayManager.sendEvent(event, deliveryRelays.toList())
            }
            
            Log.i(TAG, "✅ Message sent successfully: ${messageID.take(8)}... to ${deliveryRelays.size} relays")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send private message via Nostr: ${e.message}")
            false
        }
    }
    
    fun sendReadReceipt(receipt: ReadReceipt, to: String) {
        // Enqueue and process with throttling to avoid relay rate limits
        readQueue.offer(QueuedRead(receipt, to))
        processReadQueueIfNeeded()
    }
    
    private fun processReadQueueIfNeeded() {
        if (isSendingReadAcks) return
        if (readQueue.isEmpty()) return
        
        isSendingReadAcks = true
        sendNextReadAck()
    }
    
    private fun sendNextReadAck() {
        val item = readQueue.poll()
        if (item == null) {
            isSendingReadAcks = false
            return
        }
        
        transportScope.launch {
            try {
                var recipientNostrPubkey: String? = null
                
                // Try to resolve from favorites persistence service
                recipientNostrPubkey = resolveNostrPublicKey(item.peerID)
                
                if (recipientNostrPubkey == null) {
                    Log.w(TAG, "No Nostr public key found for read receipt to: ${item.peerID}")
                    scheduleNextReadAck()
                    return@launch
                }
                
                val senderIdentity = NostrIdentityBridge.getCurrentNostrIdentity(context)
                if (senderIdentity == null) {
                    Log.e(TAG, "No Nostr identity available for read receipt")
                    scheduleNextReadAck()
                    return@launch
                }
                
                Log.d(TAG, "NostrTransport: preparing READ ack for id=${item.receipt.originalMessageID.take(8)}... to ${recipientNostrPubkey.take(16)}...")
                
                // Convert recipient npub -> hex
                val recipientHex = try {
                    val (hrp, data) = Bech32.decode(recipientNostrPubkey)
                    if (hrp != "npub") {
                        scheduleNextReadAck()
                        return@launch
                    }
                    data.joinToString("") { "%02x".format(it) }
                } catch (e: Exception) {
                    scheduleNextReadAck()
                    return@launch
                }
                
                val ack = NostrEmbeddedBitChat.encodeAckForNostr(
                    type = NoisePayloadType.READ_RECEIPT,
                    messageID = item.receipt.originalMessageID,
                    recipientPeerID = item.peerID,
                    senderPeerID = senderPeerID
                )
                
                if (ack == null) {
                    Log.e(TAG, "NostrTransport: failed to embed READ ack")
                    scheduleNextReadAck()
                    return@launch
                }
                
                val giftWraps = NostrProtocol.createPrivateMessage(
                    content = ack,
                    recipientPubkey = recipientHex,
                    senderIdentity = senderIdentity
                )
                
                giftWraps.forEach { event ->
                    Log.d(TAG, "NostrTransport: sending READ ack giftWrap id=${event.id.take(16)}...")
                    NostrRelayManager.getInstance(context).sendEvent(event)
                }
                
                scheduleNextReadAck()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send read receipt via Nostr: ${e.message}")
                scheduleNextReadAck()
            }
        }
    }
    
    private fun scheduleNextReadAck() {
        transportScope.launch {
            delay(READ_ACK_INTERVAL)
            isSendingReadAcks = false
            processReadQueueIfNeeded()
        }
    }
    
    fun sendFavoriteNotification(to: String, isFavorite: Boolean) {
        transportScope.launch {
            try {
                var recipientNostrPubkey: String? = null
                
                // Try to resolve from favorites persistence service
                recipientNostrPubkey = resolveNostrPublicKey(to)
                
                if (recipientNostrPubkey == null) {
                    Log.w(TAG, "No Nostr public key found for favorite notification to: $to")
                    return@launch
                }
                
                val senderIdentity = NostrIdentityBridge.getCurrentNostrIdentity(context)
                if (senderIdentity == null) {
                    Log.e(TAG, "No Nostr identity available for favorite notification")
                    return@launch
                }
                
                val content = if (isFavorite) "[FAVORITED]:${senderIdentity.npub}" else "[UNFAVORITED]:${senderIdentity.npub}"
                
                Log.d(TAG, "NostrTransport: preparing FAVORITE($isFavorite) to ${recipientNostrPubkey.take(16)}...")
                
                // Convert recipient npub -> hex
                val recipientHex = try {
                    val (hrp, data) = Bech32.decode(recipientNostrPubkey)
                    if (hrp != "npub") return@launch
                    data.joinToString("") { "%02x".format(it) }
                } catch (e: Exception) {
                    return@launch
                }
                
                val embedded = NostrEmbeddedBitChat.encodePMForNostr(
                    content = content,
                    messageID = UUID.randomUUID().toString(),
                    recipientPeerID = to,
                    senderPeerID = senderPeerID
                )
                
                if (embedded == null) {
                    Log.e(TAG, "NostrTransport: failed to embed favorite notification")
                    return@launch
                }
                
                val giftWraps = NostrProtocol.createPrivateMessage(
                    content = embedded,
                    recipientPubkey = recipientHex,
                    senderIdentity = senderIdentity
                )
                
                giftWraps.forEach { event ->
                    Log.d(TAG, "NostrTransport: sending favorite giftWrap id=${event.id.take(16)}...")
                    NostrRelayManager.getInstance(context).sendEvent(event)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send favorite notification via Nostr: ${e.message}")
            }
        }
    }
    
    fun sendDeliveryAck(messageID: String, to: String) {
        transportScope.launch {
            try {
                var recipientNostrPubkey: String? = null
                
                // Try to resolve from favorites persistence service
                recipientNostrPubkey = resolveNostrPublicKey(to)
                
                if (recipientNostrPubkey == null) {
                    Log.w(TAG, "No Nostr public key found for delivery ack to: $to")
                    return@launch
                }
                
                val senderIdentity = NostrIdentityBridge.getCurrentNostrIdentity(context)
                if (senderIdentity == null) {
                    Log.e(TAG, "No Nostr identity available for delivery ack")
                    return@launch
                }
                
                Log.d(TAG, "NostrTransport: preparing DELIVERED ack for id=${messageID.take(8)}... to ${recipientNostrPubkey.take(16)}...")
                
                val recipientHex = try {
                    val (hrp, data) = Bech32.decode(recipientNostrPubkey)
                    if (hrp != "npub") return@launch
                    data.joinToString("") { "%02x".format(it) }
                } catch (e: Exception) {
                    return@launch
                }
                
                val ack = NostrEmbeddedBitChat.encodeAckForNostr(
                    type = NoisePayloadType.DELIVERED,
                    messageID = messageID,
                    recipientPeerID = to,
                    senderPeerID = senderPeerID
                )
                
                if (ack == null) {
                    Log.e(TAG, "NostrTransport: failed to embed DELIVERED ack")
                    return@launch
                }
                
                val giftWraps = NostrProtocol.createPrivateMessage(
                    content = ack,
                    recipientPubkey = recipientHex,
                    senderIdentity = senderIdentity
                )
                
                giftWraps.forEach { event ->
                    Log.d(TAG, "NostrTransport: sending DELIVERED ack giftWrap id=${event.id.take(16)}...")
                    NostrRelayManager.getInstance(context).sendEvent(event)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send delivery ack via Nostr: ${e.message}")
            }
        }
    }
    
    // MARK: - Geohash ACK helpers (for per-geohash identity DMs)
    
    fun sendDeliveryAckGeohash(
        messageID: String,
        toRecipientHex: String,
        fromIdentity: NostrIdentity
    ) {
        transportScope.launch {
            try {
                Log.d(TAG, "GeoDM: send DELIVERED -> recip=${toRecipientHex.take(8)}... mid=${messageID.take(8)}... from=${fromIdentity.publicKeyHex.take(8)}...")
                
                val embedded = NostrEmbeddedBitChat.encodeAckForNostrNoRecipient(
                    type = NoisePayloadType.DELIVERED,
                    messageID = messageID,
                    senderPeerID = senderPeerID
                )
                
                if (embedded == null) return@launch
                
                val giftWraps = NostrProtocol.createPrivateMessage(
                    content = embedded,
                    recipientPubkey = toRecipientHex,
                    senderIdentity = fromIdentity
                )
                
                // Register pending gift wrap for deduplication and send all
                giftWraps.forEach { event ->
                    NostrRelayManager.registerPendingGiftWrap(event.id)
                    NostrRelayManager.getInstance(context).sendEvent(event)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send geohash delivery ack: ${e.message}")
            }
        }
    }
    
    fun sendReadReceiptGeohash(
        messageID: String,
        toRecipientHex: String,
        fromIdentity: NostrIdentity
    ) {
        transportScope.launch {
            try {
                Log.d(TAG, "GeoDM: send READ -> recip=${toRecipientHex.take(8)}... mid=${messageID.take(8)}... from=${fromIdentity.publicKeyHex.take(8)}...")
                
                val embedded = NostrEmbeddedBitChat.encodeAckForNostrNoRecipient(
                    type = NoisePayloadType.READ_RECEIPT,
                    messageID = messageID,
                    senderPeerID = senderPeerID
                )
                
                if (embedded == null) return@launch
                
                val giftWraps = NostrProtocol.createPrivateMessage(
                    content = embedded,
                    recipientPubkey = toRecipientHex,
                    senderIdentity = fromIdentity
                )
                
                // Register pending gift wrap for deduplication and send all
                giftWraps.forEach { event ->
                    NostrRelayManager.registerPendingGiftWrap(event.id)
                    NostrRelayManager.getInstance(context).sendEvent(event)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send geohash read receipt: ${e.message}")
            }
        }
    }
    
    // MARK: - Geohash DMs (per-geohash identity)
    
    fun sendPrivateMessageGeohash(
        content: String,
        toRecipientHex: String,
        messageID: String,
        sourceGeohash: String? = null
    ) {
        // Use provided geohash or derive from current location
        val geohash = sourceGeohash ?: run {
            val selected = try {
                com.seamless.android.geohash.LocationChannelManager.getInstance(context).selectedChannel.value
            } catch (_: Exception) { null }
            if (selected !is com.seamless.android.geohash.ChannelID.Location) {
                Log.w(TAG, "NostrTransport: cannot send geohash PM - not in a location channel and no geohash provided")
                return
            }
            selected.channel.geohash
        }
        
        val fromIdentity = try {
            NostrIdentityBridge.deriveIdentity(geohash, context)
        } catch (e: Exception) {
            Log.e(TAG, "NostrTransport: cannot derive geohash identity for $geohash: ${e.message}")
            return
        }
        
        transportScope.launch {
            try {
                if (toRecipientHex.isEmpty()) return@launch

                Log.d(
                    TAG,
                    "GeoDM: send PM -> recip=${toRecipientHex.take(8)}... mid=${messageID.take(8)}... from=${fromIdentity.publicKeyHex.take(8)}... geohash=$geohash"
                )

                // Build embedded BitChat packet without recipient peer ID
                val embedded = NostrEmbeddedBitChat.encodePMForNostrNoRecipient(
                    content = content,
                    messageID = messageID,
                    senderPeerID = senderPeerID
                ) ?: run {
                    Log.e(TAG, "NostrTransport: failed to embed geohash PM packet")
                    return@launch
                }

                val giftWraps = NostrProtocol.createPrivateMessage(
                    content = embedded,
                    recipientPubkey = toRecipientHex,
                    senderIdentity = fromIdentity
                )

                giftWraps.forEach { event ->
                    Log.d(TAG, "NostrTransport: sending geohash PM giftWrap id=${event.id.take(16)}...")
                    NostrRelayManager.registerPendingGiftWrap(event.id)
                    NostrRelayManager.getInstance(context).sendEvent(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send geohash private message: ${e.message}")
            }
        }
    }
    
    // MARK: - Helper Methods
    
    /**
     * Resolve Nostr public key for a peer ID
     * Supports: 16-hex mesh peerID, npub, nostr:npub, nostr_npub formats
     */
    private fun resolveNostrPublicKey(peerID: String): String? {
        try {
            // 0) Direct npub: if peerID is already an npub, return it directly
            if (peerID.startsWith("npub")) {
                Log.d(TAG, "resolveNostrPublicKey: peerID is already npub format")
                return peerID
            }
            
            // 0.1) Handle nostr: or nostr_ prefixed peerIDs (from Nostr-only favorites)
            if (peerID.startsWith("nostr:") || peerID.startsWith("nostr_")) {
                val npubPart = peerID.removePrefix("nostr:").removePrefix("nostr_")
                if (npubPart.startsWith("npub")) {
                    Log.d(TAG, "resolveNostrPublicKey: extracted npub from nostr-prefixed peerID")
                    return npubPart
                }
            }
            
            // 1) Fast path: direct peerID→npub mapping (mutual favorites after mesh mapping)
            com.seamless.android.favorites.FavoritesPersistenceService.shared.findNostrPubkeyForPeerID(peerID)?.let { return it }

            // 2) Legacy path: resolve by noise public key association
            val noiseKey = hexStringToByteArray(peerID)
            val favoriteStatus = com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKey)
            if (favoriteStatus?.peerNostrPublicKey != null) return favoriteStatus.peerNostrPublicKey

            // 3) Prefix match on noiseHex from 16-hex peerID
            if (peerID.length == 16) {
                val fallbackStatus = com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(peerID)
                return fallbackStatus?.peerNostrPublicKey
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Nostr public key for $peerID: ${e.message}")
            return null
        }
    }
    
    /**
     * Convert full hex string to byte array
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val clean = if (hexString.length % 2 == 0) hexString else "0$hexString"
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    fun cleanup() {
        transportScope.cancel()
    }
}
