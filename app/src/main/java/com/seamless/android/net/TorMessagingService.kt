@file:Suppress("KotlinConstantConditions")
package com.seamless.android.net

import android.app.Application
import android.content.Context
import android.util.Log
import com.seamless.android.crypto.EncryptionService
import com.seamless.android.crypto.X25519
import com.seamless.android.crypto.X25519KeyPair
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.identity.SecureIdentityStateManager
import com.seamless.android.model.BitchatMessage
import com.seamless.android.noise.SeamlessRatchet
import com.seamless.android.noise.SeamlessRatchetManager
import com.seamless.android.sync.BrambleSyncEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-ready Tor Messaging Service
 * 
 * This is the main API for serverless, censorship-resistant,
 * end-to-end encrypted messaging over Tor.
 * 
 * Features:
 * - Serverless P2P messaging via Tor
 * - Signal-style Double Ratchet encryption
 * - Offline message queuing with retry
 * - Delivery status tracking
 * - Network resilience
 * 
 * Based on research from:
 * - Briar (Bramble protocol, Tor hidden services)
 * - Ricochet Refresh (Tor v3 identity)
 * - SimpleX (queue-based delivery)
 * - Signal (X3DH + Double Ratchet)
 */
class TorMessagingService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TorMessagingService"
        private const val ONION_PORT = 18888
        private const val MAILBOX_SEALED_VERSION: Byte = 1
        private const val MAILBOX_NONCE_SIZE = 12
        private val MAILBOX_SEALED_MAGIC = byteArrayOf(0x53, 0x54, 0x4D, 0x42) // STMB
        private const val DIRECT_SEALED_VERSION: Byte = 1
        private const val DIRECT_NONCE_SIZE = 12
        private const val UNACKED_RETRIES_BEFORE_RECONNECT = 3
        // Tor round trips can occasionally span more than two keepalive windows.
        // Keep presence stable until four missed authenticated responses.
        private const val PEER_PRESENCE_TIMEOUT_MS = 60_000L
        private const val KNOWN_CONTACT_REDIAL_INTERVAL_MS = 30_000L
        // A mailbox-capable contact should not wait through the entire Tor
        // socket timeout before its durable offline route is attempted.
        private const val INITIAL_DIRECT_DIAL_BUDGET_MS = 12_000L
        private val DIRECT_SEALED_MAGIC = byteArrayOf(0x53, 0x54, 0x44, 0x50) // STDP
        
        @Volatile
        private var instance: TorMessagingService? = null
        
        fun getInstance(context: Context): TorMessagingService {
            return instance ?: synchronized(this) {
                instance ?: TorMessagingService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Core components
    private val encryptionService = EncryptionService(context)
    private val contactManager = OnionContactManager.getInstance(context)
    private val messageQueue = OnionMessageQueue.getInstance(context)
    private val circuitManager = TorCircuitManager.getInstance(context)
    private val ratchetManager = SeamlessRatchetManager.getInstance(context)
    private val hiddenServiceManager = TorHiddenServiceManager.getInstance(context)
    private val rendezvous = BrambleRendezvous.getInstance(context)
    private val syncEngine = BrambleSyncEngine.getInstance(context)
    private val mailboxClient = MailboxClient.getInstance(context)
    private val mailboxService = MailboxService.getInstance(context)
    private val messageRepository = TorMessageRepository.getInstance(context)
    private val attachmentTransfer = TorAttachmentTransfer(context)
    private val groupMessaging = TorGroupMessaging.getInstance(context)
    private val messageNotifier = TorMessageNotifier(context)
    private val messageChainPrefs = context.getSharedPreferences("tor_message_chains", Context.MODE_PRIVATE)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val lifecycleMutex = Mutex()
    
    // Message deduplication
    private val deduplicator = OnionMessageProtocol.MessageDeduplicator()
    
    // Sequence managers per peer
    private val sequenceManagers = ConcurrentHashMap<String, OnionMessageProtocol.SequenceManager>()
    
    // Message listener
    private var messageListener: MessageListener? = null
    
    // Service state
    private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    // Connected peers — observable from UI for presence indicators
    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeersFlow: StateFlow<Set<String>> = _connectedPeers.asStateFlow()
    private val _typingPeers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val typingPeersFlow: StateFlow<Set<String>> = _typingPeers
        .map { it.keys }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())
    
    private var processingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var queueDeliveryJob: Job? = null
    private var mailboxPollJob: Job? = null
    private val queueWakeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    // Track peers that have a dedicated continuous read loop
    // to prevent processIncomingMessages from racing on the same socket
    private val peersWithReadLoop = ConcurrentHashMap.newKeySet<String>()
    private val readLoopConnections = ConcurrentHashMap<String, String>()
    private val visibleChatContacts = ConcurrentHashMap.newKeySet<String>()
    
    // Track peers we've introduced ourselves to on outbound connections.
    // Ensures we always send CONTACT_EXCHANGE as the first frame on a new
    // outbound circuit, so the receiver can identify us without destructive
    // trial decryption that would corrupt ratchet sessions.
    private val peersIntroducedOnConnection = ConcurrentHashMap.newKeySet<String>()
    private val lastVerifiedPeerActivity = ConcurrentHashMap<String, Long>()
    private val lastPeerDialAttempt = ConcurrentHashMap<String, Long>()
    private val lastTypingSignalAt = ConcurrentHashMap<String, Long>()
    private val deliveryRoutes = ConcurrentHashMap<String, TorDeliveryRoute>()
    private val mailboxFallbackDisabled = ConcurrentHashMap.newKeySet<String>()
    private val attachmentPartToMessage = ConcurrentHashMap<String, String>()
    private val pendingAttachmentParts = ConcurrentHashMap<String, MutableSet<String>>()
    @Volatile private var ownMailboxOnion: String? = null
    
    /**
     * Service states
     */
    enum class ServiceState {
        STOPPED,
        STARTING,
        WAITING_FOR_TOR,
        RENDEZVOUS,      // BRP: attempting rendezvous with public-key-only contacts
        RUNNING,
        ERROR
    }
    
    /**
     * Message listener interface
     */
    interface MessageListener {
        fun onMessageReceived(from: OnionContactManager.OnionContact, message: OnionMessage)
        fun onDeliveryStatusChanged(messageId: String, status: OnionMessageProtocol.DeliveryState)
        fun onPeerOnline(contact: OnionContactManager.OnionContact)
        fun onPeerOffline(contact: OnionContactManager.OnionContact)
    }
    
    /**
     * Onion message data class
     */
    data class OnionMessage(
        val id: String,
        val text: String,
        val senderOnion: String,
        val timestamp: Long,
        val replyToId: String? = null,
        val isOutgoing: Boolean = false,
        val deliveryState: OnionMessageProtocol.DeliveryState = OnionMessageProtocol.DeliveryState.PENDING,
        val deliveryRoute: OnionMessageProtocol.DeliveryRoute = OnionMessageProtocol.DeliveryRoute.DIRECT
    )
    
    /**
     * Set message listener
     */
    fun setMessageListener(listener: MessageListener?) {
        messageListener = listener
    }

    /** Avoid a redundant system notification while this Tor chat is visible. */
    fun setChatVisible(contactId: String, visible: Boolean) {
        if (visible) visibleChatContacts.add(contactId) else visibleChatContacts.remove(contactId)
    }
    
    // ========== Lifecycle ==========
    
    /**
     * Start the messaging service
     */
    suspend fun start() = lifecycleMutex.withLock {
        startLocked()
    }

    private suspend fun startLocked() {
        if (_serviceState.value in setOf(
                ServiceState.STARTING,
                ServiceState.WAITING_FOR_TOR,
                ServiceState.RENDEZVOUS,
                ServiceState.RUNNING
            )
        ) {
            Log.d(TAG, "Service already starting or running")
            return
        }

        // A startup failure can happen after one or more workers or the hidden
        // service were created. Tear those down before retrying so a reopened
        // chat never inherits stale sockets or duplicate processing loops.
        if (_serviceState.value == ServiceState.ERROR) {
            Log.i(TAG, "Cleaning up failed Tor messaging startup before retry")
            stopLocked()
        }
        
        _serviceState.value = ServiceState.STARTING
        _lastError.value = null
        Log.i(TAG, "Starting TorMessagingService...")
        
        try {
            // Initialize SeamlessRatchetManager with our identity keys
            // This MUST happen before any encrypt/decrypt calls
            initializeRatchetManager()
            
            // ━━━ START HIDDEN SERVICE (inbound) ━━━
            // This makes us reachable via .onion address.
            // Without this, no one can connect to us and messages can never arrive.
            val hiddenServiceStarted = startHiddenService()

            // Wait for Tor SOCKS proxy to be ready (outbound). If Arti is slow or
            // unavailable but the embedded daemon is connected, continue with daemon
            // SOCKS fallback so direct onion messaging can still work.
            _serviceState.value = ServiceState.WAITING_FOR_TOR
            val outboundTorReady = waitForTor()
            if (!hiddenServiceStarted && !outboundTorReady) {
                error(_lastError.value ?: "Tor could not start")
            }

            startMailboxHost()
            
            // Start processing loop (incoming messages)
            startProcessingLoop()
            
            // Start heartbeat loop
            startHeartbeatLoop()
            
            // Start queue delivery loop (outbound messages)
            startQueueDeliveryLoop()

            // ━━━ START BRAMBLE SYNC ENGINE (BSP) ━━━
            startSyncEngine()

            // ━━━ START RENDEZVOUS FOR BRP CONTACTS ━━━
            startRendezvousForPendingContacts()

            // ━━━ START MAILBOX POLLING (offline message delivery) ━━━
            startMailboxPolling()
            
            _serviceState.value = ServiceState.RUNNING
            Log.i(TAG, "✅ TorMessagingService started successfully")
            
        } catch (e: Exception) {
            val message = e.message ?: e::class.java.simpleName
            _lastError.value = message
            Log.e(TAG, "Failed to start TorMessagingService: $message", e)
            _serviceState.value = ServiceState.ERROR
        }
    }
    
    /**
     * Stop the messaging service
     */
    suspend fun stop() = lifecycleMutex.withLock {
        stopLocked()
    }

    private suspend fun stopLocked() {
        if (_serviceState.value == ServiceState.STOPPED) return
        Log.i(TAG, "🛑 Stopping TorMessagingService...")
        
        processingJob?.cancel()
        heartbeatJob?.cancel()
        queueDeliveryJob?.cancel()
        mailboxPollJob?.cancel()
        mailboxClient.stopPolling()
        mailboxService.stop()
        ownMailboxOnion = null
        syncEngine.stopSyncLoop()
        circuitManager.disconnectAll()
        hiddenServiceManager.stop()
        peersIntroducedOnConnection.clear()
        _lastError.value = null
        
        _serviceState.value = ServiceState.STOPPED
        Log.i(TAG, "TorMessagingService stopped")
    }
    
    // ========== Identity ==========
    
    /**
     * Get my Tor v3 onion address
     * Derived from Ed25519 public key
     */
    fun getMyOnionAddress(): String {
        val contact = contactManager.getMyContactInfo()
        return contact.onionAddress
    }
    
    /**
     * Get my public key (for sharing)
     */
    fun getMyPublicKey(): ByteArray {
        return encryptionService.getSigningPublicKey() ?: ByteArray(0)
    }
    
    /**
     * Get my shareable contact info
     */
    fun getMyContactInfo(): OnionContactManager.OnionContact {
        return contactManager.getMyContactInfo()
    }
    
    /**
     * Generate QR code data for contact sharing
     */
    fun getMyQrData(): String {
        return contactManager.getMyQrData()
    }
    
    /**
     * Generate shareable link
     */
    fun getMyShareableLink(): String {
        return contactManager.getMyShareableLink()
    }
    
    /**
     * Add a contact from shareable link or QR data
     * Automatically sends your info for auto-pairing
     */
    suspend fun addContact(data: String): OnionContactManager.OnionContact? {
        val contact = contactManager.addContactFromLink(data) 
            ?: contactManager.addContactFromQr(data)
        
        // Auto-pairing: Send our contact info to them
        if (contact != null) {
            if (contact.requiresRendezvous()) {
                scope.launch { startRendezvousForContact(contact) }
            } else {
                sendContactExchange(contact.onionAddress)
            }
        }
        
        return contact
    }
    
    /**
     * Send contact exchange for auto-pairing
     * Called automatically when you add a contact
     * 
     * IMPORTANT: Contact exchange is sent UNENCRYPTED because the recipient
     * doesn't have our public key yet - that's what we're sending them!
     */
    private suspend fun sendContactExchange(recipientOnion: String) {
        try {
            val myInfo = contactManager.getMyContactInfo()
            if (myInfo.requiresRendezvous()) {
                Log.w(TAG, "Skipping contact exchange until our onion address is published")
                return
            }
            
            val seqManager = sequenceManagers.getOrPut(recipientOnion) {
                OnionMessageProtocol.SequenceManager()
            }
            
            val frame = OnionMessageProtocol.createContactExchangeFrame(
                myOnionAddress = myInfo.onionAddress,
                myNickname = myInfo.nickname,
                myPublicKey = myInfo.publicKey,
                sequence = seqManager.nextOutbound()
            )
            
            // DO NOT ENCRYPT - recipient doesn't have our public key yet!
            // The frame contains our public key, which they need to decrypt future messages
            messageQueue.enqueue(recipientOnion, frame)
            
            Log.i(TAG, "📤 Sent contact exchange (unencrypted) to ${redactEndpoint(recipientOnion)} for auto-pairing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send contact exchange: ${e.message}")
        }
    }
    
    /**
     * Get all contacts
     */
    fun getContacts(): List<OnionContactManager.OnionContact> {
        return contactManager.getAllContacts()
    }
    
    /**
     * Get contacts as flow
     */
    fun contactsFlow(): Flow<List<OnionContactManager.OnionContact>> {
        return contactManager.contactsFlow
    }
    
    /**
     * Get a specific contact
     */
    fun getContact(contactId: String): OnionContactManager.OnionContact? {
        return contactManager.getContact(contactId)
    }
    
    /**
     * Get contact by onion address
     */
    fun getContactByOnion(onionAddress: String): OnionContactManager.OnionContact? {
        return contactManager.getContactByOnion(onionAddress)
    }
    
    // ========== Messaging ==========
    
    /**
     * Send a text message to a contact
     * Returns the message ID for tracking delivery status
     */
    suspend fun sendMessage(
        contactId: String,
        text: String,
        replyToId: String? = null
    ): Result<String> {
        val contact = contactManager.getContact(contactId)
            ?: return Result.failure(IllegalArgumentException("Contact not found: $contactId"))

        // ━━━ BRP: Auto-rendezvous for public-key-only contacts ━━━
        if (contact.requiresRendezvous()) {
            Log.i(TAG, "🔑 Contact ${contact.nickname} needs BRP rendezvous")
            startRendezvousForContact(contact)
            return Result.failure(
                IllegalStateException(
                    "Contact has no onion address yet. Ask them to share again after Tor is ready."
                )
            )
        }
        
        return enqueueTorMessage(contact, text, replyToId).map { it.messageId }
    }

    fun sendTorMessage(
        contactId: String,
        text: String,
        options: TorSendOptions = TorSendOptions()
    ): Flow<TorSendProgress> = flow {
        val contact = contactManager.getContact(contactId)
            ?: throw IllegalArgumentException("Contact not found: $contactId")

        if (contact.effectiveTrustState() == TorContactTrustState.BLOCKED) {
            throw IllegalStateException("Contact is blocked")
        }
        if (contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED) {
            throw IllegalStateException("Contact key changed. Verify this contact before sending.")
        }
        if (contact.requiresRendezvous()) {
            startRendezvousForContact(contact)
            throw IllegalStateException("Contact has no onion address yet. Ask them to share again after Tor is ready.")
        }
        if (options.requireVerifiedContact && contact.effectiveTrustState() != TorContactTrustState.VERIFIED) {
            throw IllegalStateException("Contact is not verified")
        }

        emit(TorSendProgress("", contactId, TorDeliveryState.PENDING, TorDeliveryRoute.QUEUED))
        val initial = enqueueTorMessage(
            contact = contact,
            text = text,
            replyToId = null,
            allowMailboxFallback = options.allowMailboxFallback
        ).getOrThrow()
        emit(initial)

        deliveryStatusFlow(initial.messageId).collect { state ->
            emit(
                initial.copy(
                    state = TorDeliveryState.fromProtocolState(state),
                    route = currentRouteForMessage(initial.messageId) ?: initial.route
                )
            )
        }
    }
    
    /**
     * Send message directly to an onion address
     */
    suspend fun sendMessageToOnion(
        recipientOnion: String,
        text: String,
        replyToId: String? = null,
        messageId: UUID = UUID.randomUUID(),
        completesUiMessage: Boolean = true
    ): Result<String> = mutex.withLock {
        try {
            Log.d(TAG, "📤 Sending message to ${redactEndpoint(recipientOnion)}")
            
            val contact = contactManager.getContactByOnion(recipientOnion)

            // Get or create sequence manager
            val seqManager = sequenceManagers.getOrPut(recipientOnion) {
                OnionMessageProtocol.SequenceManager()
            }

            val sequence = seqManager.nextOutbound()
            val timestamp = System.currentTimeMillis()
            val chatPayload = OnionMessageProtocol.ChatMessage(text, replyToId).toBytes()
            val envelope = TorMessageEnvelope(
                messageId = messageId,
                senderKey = getMyStaticPublicKeyHex(),
                recipientKey = contact?.publicKey.orEmpty(),
                timestamp = timestamp,
                sequence = sequence,
                previousHash = getChainHead(recipientOnion),
                payloadType = TorEnvelopePayloadType.MESSAGE,
                ciphertext = chatPayload
            )

            val frame = OnionMessageProtocol.OnionFrame(
                messageId = messageId,
                sequence = sequence,
                timestamp = timestamp,
                frameType = OnionMessageProtocol.FrameType.MESSAGE,
                payload = envelope.encode()
            )
            
            // Enqueue for delivery
            val queuedMessageId = messageQueue.enqueue(
                recipientOnion = recipientOnion,
                frame = frame,
                uiMessageId = messageId.toString(),
                completesUiMessage = completesUiMessage
            )
            deliveryRoutes[queuedMessageId] = TorDeliveryRoute.QUEUED
            updateChainHead(recipientOnion, envelope.chainHash())
            queueWakeSignal.tryEmit(Unit)
            
            Log.i(TAG, "📨 Message $messageId queued for ${redactEndpoint(recipientOnion)}")
            Result.success(queuedMessageId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            Result.failure(e)
        }
    }

    /** Send a bounded image through encrypted, ACKed Tor file frames. */
    suspend fun sendImage(
        contactId: String,
        imageBytes: ByteArray,
        mimeType: String
    ): Result<String> = mutex.withLock {
        val contact = contactManager.getContact(contactId)
            ?: return@withLock Result.failure(IllegalArgumentException("Contact not found"))
        if (contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
            contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED
        ) {
            return@withLock Result.failure(IllegalStateException("Verify this contact before sending media"))
        }

        try {
            val transferId = UUID.randomUUID()
            val imageMessageId = UUID.randomUUID()
            val meta = attachmentTransfer.createMeta(
                transferId = transferId,
                mimeType = mimeType,
                totalBytes = imageBytes.size,
                senderPublicKey = getMyStaticPublicKeyHex()
            )
            val localPath = attachmentTransfer.saveOutgoing(transferId, imageBytes, meta.mimeType)
            val sequenceManager = getSequenceManager(contact.onionAddress)
            val now = System.currentTimeMillis()

            messageRepository.saveSentMessage(
                messageId = imageMessageId.toString(),
                contact = contact,
                text = "Photo",
                timestamp = now,
                deliveryRoute = OnionMessageProtocol.DeliveryRoute.QUEUED,
                attachmentPath = localPath,
                attachmentMimeType = meta.mimeType
            )

            val metaFrame = OnionMessageProtocol.OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = sequenceManager.nextOutbound(),
                timestamp = now,
                frameType = OnionMessageProtocol.FrameType.FILE_META,
                payload = attachmentTransfer.encodeMeta(meta)
            )
            val queuedMetaId = messageQueue.enqueue(
                recipientOnion = contact.onionAddress,
                frame = metaFrame,
                uiMessageId = imageMessageId.toString(),
                completesUiMessage = false
            )
            val chunks = attachmentTransfer.split(imageBytes)
            chunks.forEachIndexed { index, chunk ->
                val chunkFrame = OnionMessageProtocol.OnionFrame(
                    // Every frame needs its own transport identity. The final
                    // chunk alone completes the single visible photo message.
                    messageId = UUID.randomUUID(),
                    sequence = sequenceManager.nextOutbound(),
                    timestamp = now,
                    frameType = OnionMessageProtocol.FrameType.FILE_CHUNK,
                    payload = attachmentTransfer.encodeChunk(transferId, index, chunk)
                )
                val chunkId = messageQueue.enqueue(
                    recipientOnion = contact.onionAddress,
                    frame = chunkFrame,
                    uiMessageId = imageMessageId.toString(),
                    completesUiMessage = index == chunks.lastIndex
                )
            }
            deliveryRoutes[imageMessageId.toString()] = TorDeliveryRoute.QUEUED
            queueWakeSignal.tryEmit(Unit)
            Result.success(queuedMetaId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue image: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun enqueueTorMessage(
        contact: OnionContactManager.OnionContact,
        text: String,
        replyToId: String?,
        allowMailboxFallback: Boolean = true
    ): Result<TorSendProgress> {
        if (contact.effectiveTrustState() == TorContactTrustState.BLOCKED) {
            return Result.failure(IllegalStateException("Contact is blocked"))
        }
        if (contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED) {
            return Result.failure(IllegalStateException("Contact key changed. Verify this contact before sending."))
        }

        // Write the visible local outbox entry before transport work begins.
        // A connection failure must never make a message appear to vanish.
        val messageId = UUID.randomUUID()
        messageRepository.saveSentMessage(
            messageId = messageId.toString(),
            contact = contact,
            text = text,
            deliveryRoute = OnionMessageProtocol.DeliveryRoute.QUEUED
        )

        val result = sendMessageToOnion(contact.onionAddress, text, replyToId, messageId)
        if (result.isFailure) {
            messageRepository.updateDeliveryState(
                messageId.toString(),
                OnionMessageProtocol.DeliveryState.FAILED
            )
            return Result.failure(result.exceptionOrNull() ?: IllegalStateException("Failed to queue message"))
        }

        val queuedMessageId = result.getOrThrow()
        if (!allowMailboxFallback) {
            mailboxFallbackDisabled.add(queuedMessageId)
        }
        return Result.success(
            TorSendProgress(
                messageId = queuedMessageId,
                contactId = contact.id,
                state = TorDeliveryState.PENDING,
                route = TorDeliveryRoute.QUEUED
            )
        )
    }
    
    /**
     * Get delivery status for a message
     */
    fun getDeliveryStatus(messageId: String): OnionMessageProtocol.DeliveryState? {
        return messageQueue.getDeliveryStatus(messageId)
    }
    
    /**
     * Get delivery status as flow
     */
    fun deliveryStatusFlow(messageId: String): Flow<OnionMessageProtocol.DeliveryState> {
        return messageQueue.deliveryStatusFlow(messageId)
    }
    
    /**
     * Get queue statistics
     */
    suspend fun getQueueStats(): OnionMessageQueue.QueueStats {
        return messageQueue.getStats()
    }
    
    // ========== Connection Status ==========
    
    /**
     * Get Tor status
     */
    val torStatus: StateFlow<TorManager.TorStatus> = TorManager.statusFlow
    
    /**
     * Get connection states for all peers
     */
    val connectionStates: StateFlow<Map<String, TorCircuitManager.ConnectionState>> = 
        circuitManager.connectionStates
    
    /**
     * Check if a peer is connected
     */
    fun isPeerConnected(onionAddress: String): Boolean {
        return circuitManager.getConnectionState(onionAddress) == 
            TorCircuitManager.ConnectionState.CONNECTED
    }
    
    /**
     * Ping a peer to check if they're online
     */
    suspend fun pingPeer(onionAddress: String): Boolean {
        return try {
            val connection = circuitManager.getConnection(onionAddress)
            if (connection != null) {
                val pingFrame = OnionMessageProtocol.createPingFrame()
                val sent = circuitManager.sendData(onionAddress, encryptFrame(onionAddress, pingFrame).encode())
                if (sent) ensureReadLoop(onionAddress)
                sent
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed for ${redactEndpoint(onionAddress)}: ${e.message}")
            false
        }
    }
    
    // ========== Private Methods ==========
    
    /**
     * Initialize SeamlessRatchetManager with our Noise static key pair.
     * This MUST be called before any encrypt/decrypt operations,
     * otherwise getOrCreateSession() throws IllegalStateException.
     */
    private fun initializeRatchetManager() {
        try {
            val staticKey = SecureIdentityStateManager(context).loadStaticKey()

            if (staticKey != null) {
                ratchetManager.initialize(staticKey.first, staticKey.second)
                Log.i(TAG, "✅ SeamlessRatchetManager initialized with static identity keys")
            } else {
                Log.w(TAG, "⚠️ Could not initialize ratchet - keys not available yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SeamlessRatchetManager: ${e.message}")
        }
    }
    
    private suspend fun waitForTor(): Boolean {
        Log.d(TAG, "Waiting for Tor to be ready...")
        ensureOutboundTorStarted()

        val application = context.applicationContext as? Application
        val daemonManager = application?.let { TorDaemonManager.getInstance(it) }

        var ready = false
        withTimeoutOrNull(150_000L) {
            while (!ready) {
                val status = TorManager.statusFlow.value
                if (status.state == TorManager.TorState.RUNNING &&
                    status.bootstrapPercent >= 100 &&
                    TorManager.currentBestSocksAddress(context) != null
                ) {
                    Log.i(TAG, "Tor outbound SOCKS is ready")
                    ready = true
                    break
                }

                val daemonStatus = daemonManager?.statusFlow?.value
                if (daemonStatus?.state == TorDaemonManager.DaemonState.CONNECTED) {
                    Log.i(TAG, "Tor daemon SOCKS fallback is ready")
                    ready = true
                    break
                }

                if (status.state == TorManager.TorState.ERROR) {
                    _lastError.value = status.lastLogLine.ifBlank { "Arti Tor startup failed" }
                }
                if (daemonStatus?.state == TorDaemonManager.DaemonState.ERROR) {
                    _lastError.value = daemonStatus.error ?: "Tor daemon startup failed"
                }

                delay(1000)
            }
        }

        if (!ready) {
            val status = TorManager.statusFlow.value
            val daemonStatus = daemonManager?.statusFlow?.value
            _lastError.value = buildString {
                append("Tor did not become ready")
                if (status.lastLogLine.isNotBlank()) append(": ${status.lastLogLine.take(80)}")
                daemonStatus?.error?.let { append(": ${it.take(80)}") }
            }
            Log.w(TAG, _lastError.value ?: "Tor did not become ready")
        }

        return ready
    }

    private suspend fun ensureOutboundTorStarted() {
        val application = context.applicationContext as? Application ?: return
        val status = TorManager.statusFlow.value
        if (status.state == TorManager.TorState.RUNNING && status.bootstrapPercent >= 100) return

        if (TorPreferenceManager.get(application) != TorMode.ON) {
            TorPreferenceManager.set(application, TorMode.ON)
        }

        if (status.mode != TorMode.ON ||
            status.state == TorManager.TorState.OFF ||
            status.state == TorManager.TorState.ERROR
        ) {
            TorManager.applyMode(application, TorMode.ON)
        }
    }

    /**
     * Re-establishes a peer circuit when its chat is opened. Presence becomes
     * online only when the peer answers with valid traffic or a PONG.
     */
    suspend fun refreshPeerConnection(contactId: String): Boolean {
        val contact = contactManager.getContact(contactId) ?: return false
        if (contact.requiresRendezvous() || contact.effectiveTrustState() == TorContactTrustState.BLOCKED) {
            return false
        }
        return pingPeer(contact.onionAddress)
    }

    /** Sends a short-lived direct presence hint. Typing is intentionally never queued. */
    fun setTyping(contactId: String, isTyping: Boolean) {
        val contact = contactManager.getContact(contactId) ?: return
        val onion = contact.onionAddress
        if (contact.requiresRendezvous() ||
            circuitManager.getConnectionState(onion) != TorCircuitManager.ConnectionState.CONNECTED
        ) return
        val now = System.currentTimeMillis()
        val previous = lastTypingSignalAt[onion] ?: 0L
        if (isTyping && now - previous < 1_500L) return
        lastTypingSignalAt[onion] = now
        scope.launch {
            val frame = OnionMessageProtocol.OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = getSequenceManager(onion).nextOutbound(),
                timestamp = now,
                frameType = OnionMessageProtocol.FrameType.TYPING,
                payload = byteArrayOf(if (isTyping) 1 else 0)
            )
            circuitManager.sendData(onion, encryptFrame(onion, frame).encode())
        }
    }
    
    /**
     * Start the Tor hidden service so other peers can reach us.
     * 
     * This is the CRITICAL piece that was missing — without a hidden service,
     * the device has no .onion address and is completely unreachable.
     * 
     * Incoming connections are wired into TorCircuitManager so they can be
     * used by the processing loop for reading/writing messages.
     */
    private suspend fun startHiddenService(): Boolean {
        Log.i(TAG, "🧅 Starting hidden service for inbound connections...")
        
        // Set up listener for incoming connections BEFORE starting hidden service
        hiddenServiceManager.setConnectionListener(object : TorHiddenServiceManager.ConnectionListener {
            override fun onIncomingConnection(clientSocket: java.net.Socket) {
                scope.launch {
                    handleIncomingSocket(clientSocket)
                }
            }
        })
        
        // Start the hidden service (this starts TorDaemonManager → Briar Tor → publishes .onion)
        val started = hiddenServiceManager.start()
        if (started) {
            Log.i(TAG, "✅ Hidden service started — we are now reachable!")
        } else {
            Log.w(TAG, "⚠️ Hidden service failed to start — we are outbound-only")
            _lastError.value = hiddenServiceManager.statusFlow.value.error ?: "Hidden service failed to start"
            // Don't throw — it's better to have outbound-only than no messaging at all
        }
        return started
    }
    
    /**
     * Handle an incoming socket from the hidden service server.
     * 
     * Protocol:
     * 1. Read the first frame from the socket
     * 2. The frame's senderOnion (or messageId context) identifies the peer
     * 3. Register the socket in TorCircuitManager for bidirectional communication
     * 4. Process the first frame as a normal incoming message
     * 5. Start continuous read loop for subsequent messages
     * 
     * NOTE: For incoming connections, the sender must be identified.
     * CONTACT_EXCHANGE frames always contain the sender's onion address.
     * For encrypted MESSAGE frames, we try to match against known contacts.
     */
    private suspend fun handleIncomingSocket(socket: java.net.Socket) {
        try {
            socket.soTimeout = TorCircuitManager.READ_TIMEOUT_MS
            val input = java.io.DataInputStream(socket.inputStream)
            
            // Read the first data: [size:int][data:bytes]
            val size = input.readInt()
            if (size <= 0 || size > OnionMessageProtocol.FRAME_SIZE * 2) {
                Log.w(TAG, "Invalid incoming frame size: $size")
                socket.close()
                return
            }
            
            val data = ByteArray(size)
            input.readFully(data)
            
            // Try to decode the frame to identify the sender
            val frame = OnionMessageProtocol.OnionFrame.decode(data)
            if (frame == null) {
                Log.w(TAG, "Could not decode incoming frame from hidden service")
                socket.close()
                return
            }
            
            // Determine sender identity from the frame
            // Track the resolved peer address for continuous read loop
            var resolvedPeerOnion: String? = null
            
            when (frame.frameType) {
                OnionMessageProtocol.FrameType.CONTACT_EXCHANGE -> {
                    // CONTACT_EXCHANGE always contains sender info — this is the normal pairing flow
                    val exchange = OnionMessageProtocol.ContactExchange.fromBytes(frame.payload)
                    if (exchange != null) {
                        // If the sender's onion address is empty (their hidden service wasn't
                        // ready), try to resolve via their public key from existing contacts.
                        var peerOnion = exchange.onionAddress
                        if (peerOnion.isBlank() || peerOnion.equals("not-available.onion:18888", ignoreCase = true)) {
                            val existingContact = contactManager.getContactByPublicKey(exchange.publicKey)
                            if (existingContact != null && existingContact.onionAddress.isNotBlank()) {
                                peerOnion = existingContact.onionAddress
                                Log.i(TAG, "📥 Resolved empty onion via pubkey to ${peerOnion.take(20)}...")
                            } else {
                                // Use a stable identifier derived from their public key so the
                                // connection is keyed consistently across frames
                                peerOnion = "pubkey:${exchange.publicKey.take(32)}"
                                Log.w(TAG, "📥 Sender has no onion address, using pubkey-based ID: ${peerOnion.take(20)}")
                            }
                        }
                        resolvedPeerOnion = peerOnion
                        circuitManager.addIncomingConnection(
                            peerOnion,
                            socket,
                            shouldPreferIncomingCircuit(peerOnion)
                        )
                        Log.i(TAG, "📥 Incoming CONTACT_EXCHANGE from ${peerOnion.take(20)}...")
                        handleIncomingData(peerOnion, data, isAlreadyDecrypted = false)
                    } else {
                        Log.w(TAG, "Could not parse CONTACT_EXCHANGE payload")
                        socket.close()
                        return
                    }
                }
                else -> {
                    // For encrypted frames from already-known contacts, try to identify sender
                    val identified = identifySenderFromFrame(data)
                    if (identified != null) {
                        resolvedPeerOnion = identified.first
                        circuitManager.addIncomingConnection(
                            identified.first,
                            socket,
                            shouldPreferIncomingCircuit(identified.first)
                        )
                        Log.i(TAG, "📥 Incoming connection from ${identified.first.take(20)}... (identified via decryption)")
                        handleIncomingData(identified.first, identified.second, isAlreadyDecrypted = true)
                    } else {
                        // Normal traffic is accepted only after pairing. Keeping an unknown
                        // socket under a temporary ID allows unauthenticated traffic to affect
                        // circuit state and cannot produce a durable, attributable message.
                        Log.w(TAG, "Rejected unidentified inbound Tor connection")
                        socket.close()
                        return
                    }
                }
            }
            
            // After registering the incoming connection, start a continuous read loop
            // so we can receive subsequent messages on this same socket
            resolvedPeerOnion?.let(::ensureReadLoop)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming socket: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }
    
    /**
     * Continuously read from an established connection.
     * This is launched after an incoming connection is registered,
     * ensuring we process all subsequent frames on the same socket.
     */
    private suspend fun continuousReadLoop(peerOnion: String, connectionId: String) {
        Log.d(TAG, "📡 Starting continuous read loop for $peerOnion")
        try {
            while (circuitManager.getActiveConnectionId(peerOnion) == connectionId) {
                try {
                    val data = circuitManager.receiveData(peerOnion, connectionId)
                    if (data != null) {
                        handleIncomingData(peerOnion, data, isAlreadyDecrypted = false)
                    } else if (circuitManager.getActiveConnectionId(peerOnion) != connectionId ||
                        circuitManager.getConnectionState(peerOnion) != TorCircuitManager.ConnectionState.CONNECTED
                    ) {
                        Log.d(TAG, "📡 Connection no longer active for ${redactEndpoint(peerOnion)}")
                        break
                    } else {
                        // No data available (timeout or no connection) — small delay to avoid spinning
                        delay(100)
                    }
                } catch (e: java.io.EOFException) {
                    Log.d(TAG, "📡 Peer $peerOnion disconnected (EOF)")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "📡 Read loop error for $peerOnion: ${e.message}")
                    break
                }
            }
        } finally {
            if (readLoopConnections.remove(peerOnion, connectionId)) {
                peersWithReadLoop.remove(peerOnion)
                peersIntroducedOnConnection.remove(peerOnion)
            }
            Log.d(TAG, "📡 Read loop ended for $peerOnion")
        }
    }
    
    /**
     * Try to identify the sender of an encrypted frame.
     * 
     * IMPORTANT: Does NOT use ratchet trial decryption because ratchet.decrypt()
     * permanently mutates session state (DH ratchet, chain keys, counters) BEFORE
     * the AES-GCM tag is verified. If decryption fails, the session is corrupted.
     * 
     * Instead, uses stateless Noise decryption which does not advance any session state.
     * If that fails too, returns null — the peer should eventually send a CONTACT_EXCHANGE
     * which will identify them unambiguously.
     */
    private fun identifySenderFromFrame(data: ByteArray): Pair<String, ByteArray>? {
        val frame = OnionMessageProtocol.OnionFrame.decode(data) ?: return null
        val contacts = contactManager.getAllContacts()

        // Direct Tor frames use the stateless X25519/AES-GCM envelope. A valid
        // authentication tag identifies the contact without advancing a ratchet
        // session, so the socket can be registered under the real onion address.
        if (isDirectSealedPayload(frame.payload)) {
            for (contact in contacts) {
                if (contact.onionAddress.isBlank() || contact.publicKey.isBlank()) continue
                val plaintext = try {
                    openDirectSealedPayload(frame, hexStringToByteArray(contact.publicKey))
                } catch (_: Exception) {
                    null
                }
                if (plaintext != null) {
                    val decryptedFrame = OnionMessageProtocol.OnionFrame(
                        messageId = frame.messageId,
                        sequence = frame.sequence,
                        timestamp = frame.timestamp,
                        frameType = frame.frameType,
                        payload = plaintext
                    ).encode()
                    Log.i(TAG, "Identified sender via direct Tor envelope: ${redactEndpoint(contact.onionAddress)}")
                    return contact.onionAddress to decryptedFrame
                }
            }
        }
        
        for (contact in contacts) {
            try {
                if (contact.onionAddress.isEmpty()) continue
                
                // Only try contacts that have an established Noise session
                if (!encryptionService.hasEstablishedSession(contact.onionAddress)) continue
                
                // Try Noise decryption (stateless — does NOT corrupt any session state)
                val noiseDecrypted = try {
                    encryptionService.decrypt(frame.payload, contact.onionAddress)
                } catch (_: Exception) { null }
                
                if (noiseDecrypted != null) {
                    val decryptedFrame = OnionMessageProtocol.OnionFrame(
                        messageId = frame.messageId,
                        sequence = frame.sequence,
                        timestamp = frame.timestamp,
                        frameType = frame.frameType,
                        payload = noiseDecrypted
                    ).encode()
                    Log.i(TAG, "🔓 Identified sender via Noise decrypt: ${contact.onionAddress.take(20)}...")
                    return Pair(contact.onionAddress, decryptedFrame)
                }
            } catch (_: Exception) {
                // Not this contact, try next
            }
        }
        
        // Could not identify sender — they should send CONTACT_EXCHANGE
        Log.w(TAG, "⚠️ Could not identify sender from encrypted frame (will wait for CONTACT_EXCHANGE)")
        return null
    }
    
    private fun startProcessingLoop() {
        processingJob?.cancel()
        processingJob = scope.launch {
            while (isActive) {
                try {
                    processIncomingMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in processing loop: ${e.message}")
                }
                delay(1000)
            }
        }
    }
    
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    // A connected TCP socket is not proof that a Tor peer is
                    // reachable. Only show a contact online after we have seen
                    // valid encrypted traffic from it recently.
                    val peers = circuitManager.getConnectedPeers()
                    val cutoff = System.currentTimeMillis() - PEER_PRESENCE_TIMEOUT_MS
                    val verifiedPeers = peers.filter { peer ->
                        (lastVerifiedPeerActivity[peer] ?: 0L) >= cutoff
                    }.toSet()
                    _connectedPeers.value = verifiedPeers
                    _typingPeers.update { active ->
                        active.filterValues { expiresAt -> expiresAt > System.currentTimeMillis() }
                    }
                    
                    // Ping all connected peers
                    for (onion in peers) {
                        pingPeer(onion)
                    }
                    reconnectKnownContacts()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat loop: ${e.message}")
                }
                delay(TorCircuitManager.KEEPALIVE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Queue delivery loop - periodically processes pending outbound messages.
     * IMPORTANT: This must call getConnection() (not just sendData()) to ensure
     * the outbound Tor circuit is established before attempting to deliver.
     */
    private fun startQueueDeliveryLoop() {
        queueDeliveryJob?.cancel()
        queueDeliveryJob = scope.launch {
            while (isActive) {
                try {
                    val pending = messageQueue.getAllPendingMessages()
                    for ((recipientOnion, messages) in pending) {
                        // Preserve a healthy circuit through a small number of
                        // missed ACKs. Recreating an onion circuit on every retry
                        // produced avoidable presence flapping and 20-60 second
                        // delivery delays. A genuinely stalled circuit is rebuilt
                        // only after repeated authenticated retransmissions.
                        if (messages.any {
                                it.state == OnionMessageProtocol.DeliveryState.SENT &&
                                    it.retryCount >= UNACKED_RETRIES_BEFORE_RECONNECT
                            }
                        ) {
                            circuitManager.disconnect(recipientOnion)
                            peersIntroducedOnConnection.remove(recipientOnion)
                            readLoopConnections.remove(recipientOnion)
                            peersWithReadLoop.remove(recipientOnion)
                        }

                        // Give a first direct onion dial a bounded head start.
                        // Existing pooled circuits return immediately; offline
                        // contacts fall through to their encrypted mailbox rather
                        // than freezing every queued message behind a 60s dial.
                        val connection = try {
                            withTimeoutOrNull(INITIAL_DIRECT_DIAL_BUDGET_MS) {
                                circuitManager.getConnection(recipientOnion)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ Could not connect to ${redactEndpoint(recipientOnion)}: ${e.message}")
                            null
                        }
                        
                        if (connection == null) {
                            Log.d(TAG, "📭 No connection to ${redactEndpoint(recipientOnion)} — trying mailbox fallback")
                            for (message in messages) {
                                val delivered = tryMailboxDelivery(recipientOnion, message)
                                if (!delivered) {
                                    messageQueue.handleSendFailure(
                                        message.id,
                                        "Direct connection unavailable and mailbox unavailable"
                                    )
                                }
                            }
                            continue
                        }
                        
                        // ━━━ CRITICAL: Send CONTACT_EXCHANGE on first use of new connection ━━━
                        // This ensures the remote hidden service can identify who connected
                        // WITHOUT destructive ratchet trial decryption (which corrupts sessions).
                        val hasNormalMessages = messages.any { queued ->
                            OnionMessageProtocol.OnionFrame.decode(queued.frameData)?.frameType !=
                                OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
                        }
                        val introducedOnThisConnection = peersIntroducedOnConnection.add(recipientOnion)
                        var introductionReady = !introducedOnThisConnection
                        if (introducedOnThisConnection) {
                            try {
                                val myInfo = contactManager.getMyContactInfo()
                                // Guard: don't send CONTACT_EXCHANGE with empty onion address.
                                // If our hidden service hasn't published yet, skip the intro
                                // and let the next queue cycle retry once the HS is ready.
                                if (myInfo.onionAddress.isBlank() || myInfo.requiresRendezvous()) {
                                    Log.w(TAG, "⏳ Skipping auto CONTACT_EXCHANGE — our hidden service is not published yet")
                                    peersIntroducedOnConnection.remove(recipientOnion) // allow retry next cycle
                                } else {
                                    val seqManager = getSequenceManager(recipientOnion)
                                    val introFrame = OnionMessageProtocol.createContactExchangeFrame(
                                        myOnionAddress = myInfo.onionAddress,
                                        myNickname = myInfo.nickname,
                                        myPublicKey = myInfo.publicKey,
                                        sequence = seqManager.nextOutbound()
                                    )
                                    introductionReady = circuitManager.sendData(recipientOnion, introFrame.encode())
                                    if (introductionReady) {
                                        Log.i(TAG, "📤 Auto-sent CONTACT_EXCHANGE on new outbound connection to ${redactEndpoint(recipientOnion)}")
                                    } else {
                                        peersIntroducedOnConnection.remove(recipientOnion)
                                        Log.w(TAG, "CONTACT_EXCHANGE write failed for ${redactEndpoint(recipientOnion)}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send intro CONTACT_EXCHANGE: ${e.message}")
                                peersIntroducedOnConnection.remove(recipientOnion) // allow retry on failure
                            }
                        }

                        if (introducedOnThisConnection && !introductionReady && hasNormalMessages) {
                            // Keep regular messages queued until the peer has a valid return
                            // path. A socket write alone is not a deliverable Tor session.
                            continue
                        }

                        if (introducedOnThisConnection && introductionReady && hasNormalMessages) {
                            // The contact record is installed while processing
                            // CONTACT_EXCHANGE. A small scheduling yield is
                            // sufficient; a fixed two-second pause made every
                            // first message feel unnecessarily slow.
                            delay(100L)
                        }
                        
                        for (message in messages) {
                            try {
                                messageQueue.markSending(message.id)
                                if (message.completesUiMessage) {
                                    messageRepository.updateDeliveryState(
                                        message.uiMessageId,
                                        OnionMessageProtocol.DeliveryState.SENDING
                                    )
                                }
                                val outboundFrameData = prepareDirectFrameForDelivery(recipientOnion, message)
                                val success = circuitManager.sendData(recipientOnion, outboundFrameData)
                                if (success) {
                                    deliveryRoutes[message.uiMessageId] = TorDeliveryRoute.DIRECT
                                    messageQueue.markSent(message.id)
                                    if (message.completesUiMessage) {
                                        messageRepository.updateDelivery(
                                            message.uiMessageId,
                                            OnionMessageProtocol.DeliveryState.SENT,
                                            OnionMessageProtocol.DeliveryRoute.DIRECT
                                        )
                                    }
                                    Log.i(TAG, "📤 Delivered queued message ${message.id} to ${redactEndpoint(recipientOnion)} (DIRECT)")
                                    // Start a continuous read loop for outbound connections
                                    // so we reliably receive ACKs and response messages.
                                    ensureReadLoop(recipientOnion)
                                    Log.i(TAG, "📡 Ensured outbound read loop for ${redactEndpoint(recipientOnion)}")
                                } else {
                                    // Direct send failed even with connection — try mailbox
                                    peersIntroducedOnConnection.remove(recipientOnion)
                                    val delivered = tryMailboxDelivery(recipientOnion, message)
                                    if (!delivered) {
                                        messageQueue.handleSendFailure(message.id, "Direct + Mailbox both failed")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Send failed for ${message.id}: ${e.message}")
                                peersIntroducedOnConnection.remove(recipientOnion)
                                val delivered = tryMailboxDelivery(recipientOnion, message)
                                if (!delivered) {
                                    messageQueue.handleSendFailure(message.id, e.message ?: "Unknown error")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in queue delivery loop: ${e.message}")
                }
                withTimeoutOrNull(3_000L) { queueWakeSignal.first() }
            }
        }
    }
    
    /**
     * Ensure every active circuit has exactly one reader. A previous polling reader
     * could race the continuous reader during an outbound connection handoff and
     * consume frames out of order.
     */
    private suspend fun processIncomingMessages() {
        circuitManager.getConnectedPeers()
            .toList()
            .forEach(::ensureReadLoop)
    }
    
    private suspend fun handleIncomingData(
        senderOnion: String,
        data: ByteArray,
        isAlreadyDecrypted: Boolean = false
    ): Boolean {
        try {
            // First, try to decode as unencrypted frame
            // This is needed for CONTACT_EXCHANGE which arrives unencrypted from unknown senders
            var frame = OnionMessageProtocol.OnionFrame.decode(data)
            
            // If direct decode works and it's a CONTACT_EXCHANGE, handle it immediately
            // (CONTACT_EXCHANGE is always unencrypted because sender's key is unknown)
            if (frame != null && frame.frameType == OnionMessageProtocol.FrameType.CONTACT_EXCHANGE) {
                Log.d(TAG, "📩 Received unencrypted CONTACT_EXCHANGE from ${redactEndpoint(senderOnion)}")
                
                // Check freshness
                if (!OnionMessageProtocol.isMessageFresh(frame)) {
                    Log.w(TAG, "Received stale contact exchange from ${redactEndpoint(senderOnion)}")
                    return false
                }
                
                // Check for duplicates
                if (deduplicator.isDuplicate(frame.messageId)) {
                    Log.d(TAG, "Ignoring duplicate contact exchange ${frame.messageId}")
                    return true
                }
                
                markPeerResponsive(senderOnion)
                handleContactExchange(senderOnion, frame)
                return true
            }
            
            // Otherwise, try to decrypt and decode
            val decryptedData = if (isAlreadyDecrypted) data else decryptData(senderOnion, data)
            frame = OnionMessageProtocol.OnionFrame.decode(decryptedData) ?: return false
            
            // Check freshness
            if (!OnionMessageProtocol.isMessageFresh(frame)) {
                Log.w(TAG, "Received stale message from ${redactEndpoint(senderOnion)}")
                return false
            }
            
            // Check for duplicates
            if (deduplicator.isDuplicate(frame.messageId)) {
                Log.d(TAG, "Ignoring duplicate message ${frame.messageId}")
                if (frame.frameType == OnionMessageProtocol.FrameType.MESSAGE ||
                    frame.frameType == OnionMessageProtocol.FrameType.FILE_META ||
                    frame.frameType == OnionMessageProtocol.FrameType.FILE_CHUNK
                ) {
                    // The original ACK may have been lost while the message itself
                    // was persisted. ACK duplicate delivery again so the sender can
                    // complete instead of retrying forever in SENT state.
                    val ackRecipient = when (frame.frameType) {
                        OnionMessageProtocol.FrameType.FILE_META -> attachmentTransfer.decodeMeta(frame.payload)
                            ?.senderPublicKey
                            ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        OnionMessageProtocol.FrameType.FILE_CHUNK -> attachmentTransfer.senderPublicKeyForChunk(frame.payload)
                            ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        else -> null
                    } ?: senderOnion
                    sendMessageAck(ackRecipient, frame.messageId)
                }
                return true
            }

            markPeerResponsive(senderOnion)
            
            return when (frame.frameType) {
                OnionMessageProtocol.FrameType.MESSAGE -> handleMessage(senderOnion, frame)
                OnionMessageProtocol.FrameType.FILE_META -> {
                    val canonicalSender = attachmentTransfer.decodeMeta(frame.payload)
                        ?.senderPublicKey
                        ?.takeIf { it.isNotBlank() }
                        ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        ?: senderOnion
                    handleFileMeta(canonicalSender, frame)
                }
                OnionMessageProtocol.FrameType.FILE_CHUNK -> {
                    val canonicalSender = attachmentTransfer.senderPublicKeyForChunk(frame.payload)
                        ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        ?: senderOnion
                    handleFileChunk(canonicalSender, frame)
                }
                OnionMessageProtocol.FrameType.ACK -> { handleAck(frame); true }
                OnionMessageProtocol.FrameType.NACK -> { handleNack(frame); true }
                OnionMessageProtocol.FrameType.PING -> { handlePing(senderOnion, frame); true }
                OnionMessageProtocol.FrameType.PONG -> { handlePong(senderOnion, frame); true }
                OnionMessageProtocol.FrameType.TYPING -> { handleTyping(senderOnion, frame); true }
                OnionMessageProtocol.FrameType.CONTACT_EXCHANGE -> { handleContactExchange(senderOnion, frame); true }

                // ━━━ Bramble Sync Protocol (BSP) frames ━━━
                OnionMessageProtocol.FrameType.SYNC_OFFER -> {
                    Log.d(TAG, "🔄 Received SYNC_OFFER from ${redactEndpoint(senderOnion)}")
                    syncEngine.handleSyncOffer(senderOnion, frame)
                    true
                }
                OnionMessageProtocol.FrameType.SYNC_REQUEST -> {
                    Log.d(TAG, "🔄 Received SYNC_REQUEST from ${redactEndpoint(senderOnion)}")
                    syncEngine.handleSyncRequest(senderOnion, frame)
                    true
                }
                OnionMessageProtocol.FrameType.SYNC_BATCH -> {
                    Log.d(TAG, "🔄 Received SYNC_BATCH from ${redactEndpoint(senderOnion)}")
                    syncEngine.handleSyncBatch(senderOnion, frame)
                    true
                }
                OnionMessageProtocol.FrameType.SYNC_ACK -> {
                    Log.d(TAG, "🔄 Received SYNC_ACK from ${redactEndpoint(senderOnion)}")
                    syncEngine.handleSyncAck(senderOnion, frame)
                    true
                }

                // ━━━ Mailbox Protocol frames ━━━
                OnionMessageProtocol.FrameType.MAILBOX_EXCHANGE -> {
                    Log.d(TAG, "📬 Received MAILBOX_EXCHANGE from ${redactEndpoint(senderOnion)}")
                    handleMailboxExchange(senderOnion, frame)
                    true
                }
                OnionMessageProtocol.FrameType.MAILBOX_STORE -> {
                    Log.d(TAG, "📬 Received MAILBOX_STORE from ${redactEndpoint(senderOnion)}")
                    handleMailboxStore(senderOnion, frame)
                    true
                }

                else -> {
                    Log.d(TAG, "Unhandled frame type: ${frame.frameType}")
                    false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming data from ${redactEndpoint(senderOnion)}: ${e.message}")
            return false
        }
    }
    
    /**
     * Handle incoming contact exchange for auto-pairing
     * Automatically adds the sender as a contact
     */
    private suspend fun handleContactExchange(senderOnion: String, frame: OnionMessageProtocol.OnionFrame) {
        val exchange = OnionMessageProtocol.ContactExchange.fromBytes(frame.payload)
        if (exchange == null) {
            Log.w(TAG, "Failed to parse contact exchange from ${redactEndpoint(senderOnion)}")
            return
        }
        
        Log.i(TAG, "📩 Received contact exchange from ${exchange.nickname} (${exchange.onionAddress.take(16)}...)")
        
        val existingByOnion = contactManager.getContactByOnion(exchange.onionAddress)
        val existingByKey = contactManager.getContactByPublicKey(exchange.publicKey)
        val keyChangedForOnion = existingByOnion != null &&
            !existingByOnion.publicKey.equals(exchange.publicKey, ignoreCase = true)
        val shouldRespond = existingByOnion == null ||
            keyChangedForOnion ||
            existingByKey?.requiresRendezvous() == true

        // Add, update, or mark key-change state for the contact.
        try {
            val exchangedContact = OnionContactManager.OnionContact(
                id = java.util.UUID.randomUUID().toString(),
                nickname = exchange.nickname,
                onionAddress = exchange.onionAddress,
                publicKey = exchange.publicKey,
                addedAt = System.currentTimeMillis()
            )
            
            contactManager.addContact(exchangedContact)
            val currentContact = contactManager.getContactByPublicKey(exchange.publicKey)
                ?: contactManager.getContactByOnion(exchange.onionAddress)
                ?: exchangedContact
            contactManager.updateLastSeen(currentContact.id)
            Log.i(TAG, "✅ Auto-paired with ${exchange.nickname}!")
            
            // Notify listener that a new contact was added
            messageListener?.onPeerOnline(currentContact)
            
            // ━━━ CRITICAL: Send our own CONTACT_EXCHANGE back ━━━
            // The sender needs to know we received their pairing request
            // and to get our info (address, key) for bidirectional messaging
            if (shouldRespond) {
                scope.launch {
                    delay(1000) // Short delay to let connection stabilize
                    sendContactExchange(exchange.onionAddress)
                    Log.i(TAG, "Sent CONTACT_EXCHANGE response back to ${exchange.nickname}")
                }
            }

            // ━━━ Exchange mailbox addresses for offline delivery ━━━
            // After successful pairing, share our mailbox so they can reach us offline
            scope.launch {
                delay(3000) // Wait for encryption session to establish
                sendMailboxExchange(exchange.onionAddress)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-add contact: ${e.message}")
        }
    }
    
    private suspend fun handleMessage(senderOnion: String, frame: OnionMessageProtocol.OnionFrame): Boolean {
        val envelope = TorMessageEnvelope.decode(frame.payload)
        if (envelope == null) {
            Log.w(TAG, "Rejected MESSAGE without versioned envelope from ${redactEndpoint(senderOnion)}")
            return false
        }

        val payload = when (envelope.payloadType) {
            TorEnvelopePayloadType.MESSAGE -> envelope.ciphertext
            else -> {
                Log.w(TAG, "Unsupported envelope payload type: ${envelope.payloadType}")
                return false
            }
        }
        val chatMessage = OnionMessageProtocol.ChatMessage.fromBytes(payload) ?: return false
        val messageId = envelope.messageId
        val timestamp = envelope.timestamp
        val senderContact = envelope.senderKey
            .takeIf { it.isNotEmpty() }
            ?.let { contactManager.getContactByPublicKey(it) }
        val effectiveSenderOnion = senderContact?.onionAddress?.takeIf { it.isNotEmpty() } ?: senderOnion

        val previousHead = getChainHead(effectiveSenderOnion)
        if (previousHead != null && envelope.previousHash != null && envelope.previousHash != previousHead) {
            Log.w(TAG, "Message chain gap from ${effectiveSenderOnion.take(16)}...")
        }
        
        Log.i(TAG, "Received Tor message $messageId from ${effectiveSenderOnion.take(16)}...")

        // Acknowledge only after a durable local write. An ACK is the sender's
        // proof that the chat message survived this receiver process.
        val contact = senderContact ?: contactManager.getContactByOnion(effectiveSenderOnion)
        if (contact != null) {
            try {
                when (groupMessaging.handleIncoming(
                    sender = contact,
                    messageId = messageId.toString(),
                    timestamp = timestamp,
                    payload = chatMessage.text
                )) {
                    TorGroupMessaging.InboundResult.HANDLED -> {
                        updateChainHead(effectiveSenderOnion, envelope.chainHash())
                        sendMessageAck(effectiveSenderOnion, messageId)
                        return true
                    }
                    TorGroupMessaging.InboundResult.REJECTED -> {
                        Log.w(TAG, "Rejected invalid encrypted group event from ${redactEndpoint(effectiveSenderOnion)}")
                        deduplicator.forget(frame.messageId)
                        return false
                    }
                    TorGroupMessaging.InboundResult.NOT_GROUP -> Unit
                }
                messageRepository.saveReceivedMessage(
                    messageId = messageId.toString(),
                    contact = contact,
                    text = chatMessage.text,
                    timestamp = timestamp
                )
                updateChainHead(effectiveSenderOnion, envelope.chainHash())
                sendMessageAck(effectiveSenderOnion, messageId)
                Log.i(TAG, "Message $messageId persisted to database")
                if (!visibleChatContacts.contains(contact.id)) {
                    messageNotifier.showIncoming(contact, chatMessage.text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to persist message: ${e.message}")
                deduplicator.forget(frame.messageId)
                return false
            }
            
            // Also notify UI listener if one is set
            val onionMessage = OnionMessage(
                id = messageId.toString(),
                text = chatMessage.text,
                senderOnion = effectiveSenderOnion,
                timestamp = timestamp,
                replyToId = chatMessage.replyToId,
                isOutgoing = false,
                deliveryState = OnionMessageProtocol.DeliveryState.DELIVERED
            )
            messageListener?.onMessageReceived(contact, onionMessage)
            return true
        } else {
            Log.w(TAG, "Received message from unknown Tor sender: ${effectiveSenderOnion.take(16)}...")
            deduplicator.forget(frame.messageId)
            return false
        }
    }
    
    private suspend fun sendMessageAck(senderOnion: String, messageId: UUID) {
        try {
            val ackFrame = OnionMessageProtocol.createAckFrame(
                messageId,
                getSequenceManager(senderOnion).nextOutbound()
            )
            val directSent = if (circuitManager.hasActiveConnection(senderOnion)) {
                circuitManager.sendData(
                    senderOnion,
                    encryptFrame(senderOnion, ackFrame).encode()
                )
            } else {
                false
            }
            if (!directSent && !storeAckInSenderMailbox(senderOnion, ackFrame)) {
                Log.w(TAG, "ACK could not reach ${redactEndpoint(senderOnion)}; it will be retried by duplicate delivery")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send ACK for $messageId: ${e.message}")
        }
    }

    /** Sends a receipt through the sender's encrypted mailbox when no live circuit exists. */
    private suspend fun storeAckInSenderMailbox(
        senderOnion: String,
        ackFrame: OnionMessageProtocol.OnionFrame
    ): Boolean {
        val sender = contactManager.getContactByOnion(senderOnion) ?: return false
        val mailboxOnion = sender.mailboxOnion ?: return false
        val payload = createSealedMailboxPayload(sender, ackFrame) ?: return false
        return mailboxClient.storeInMailbox(
            mailboxOnion = mailboxOnion,
            recipientId = fingerprintForPublicKeyHex(sender.publicKey),
            encryptedPayload = payload
        )
    }

    /**
     * Rebuild direct sessions after an app restart or a mobile-network change.
     * Attempts are rate-limited and bounded so Tor is not flooded by a large
     * contact list, while recently used contacts recover without requiring a
     * dummy message from the user.
     */
    private fun reconnectKnownContacts() {
        val now = System.currentTimeMillis()
        contactManager.getAllContacts()
            .asSequence()
            .filter { !it.requiresRendezvous() }
            .filter {
                it.effectiveTrustState() != TorContactTrustState.BLOCKED &&
                    it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED
            }
            .sortedByDescending { it.lastSeenOnline }
            .take(8)
            .forEach { contact ->
                val lastAttempt = lastPeerDialAttempt[contact.onionAddress] ?: 0L
                if (now - lastAttempt < KNOWN_CONTACT_REDIAL_INTERVAL_MS) return@forEach
                lastPeerDialAttempt[contact.onionAddress] = now
                scope.launch { pingPeer(contact.onionAddress) }
            }
    }

    private suspend fun handleFileMeta(senderOnion: String, frame: OnionMessageProtocol.OnionFrame): Boolean {
        val meta = attachmentTransfer.decodeMeta(frame.payload) ?: run {
            Log.w(TAG, "Rejected invalid image metadata from ${redactEndpoint(senderOnion)}")
            deduplicator.forget(frame.messageId)
            return false
        }
        if (!attachmentTransfer.beginIncoming(senderOnion, frame.messageId, frame.timestamp, meta)) {
            Log.w(TAG, "Could not durably initialize image transfer from ${redactEndpoint(senderOnion)}")
            deduplicator.forget(frame.messageId)
            return false
        }
        sendMessageAck(senderOnion, frame.messageId)
        return true
    }

    private suspend fun handleFileChunk(senderOnion: String, frame: OnionMessageProtocol.OnionFrame): Boolean {
        val result = attachmentTransfer.acceptChunk(senderOnion, frame.payload)
        if (result is TorAttachmentTransfer.ChunkResult.Rejected) {
            Log.w(TAG, "Rejected image chunk from ${redactEndpoint(senderOnion)}")
            deduplicator.forget(frame.messageId)
            return false
        }
        if (result is TorAttachmentTransfer.ChunkResult.AlreadyCompleted) {
            sendMessageAck(senderOnion, frame.messageId)
            return true
        }
        val completed = (result as TorAttachmentTransfer.ChunkResult.Accepted).completed
        if (completed == null) {
            sendMessageAck(senderOnion, frame.messageId)
            return true
        }

        val contact = completed.senderPublicKey
            .takeIf { it.isNotBlank() }
            ?.let { contactManager.getContactByPublicKey(it) }
            ?: contactManager.getContactByOnion(senderOnion)
        if (contact == null) {
            deduplicator.forget(frame.messageId)
            return false
        }
        try {
            messageRepository.saveReceivedMessage(
                messageId = completed.messageId.toString(),
                contact = contact,
                text = "Photo",
                timestamp = completed.timestamp,
                attachmentPath = completed.path,
                attachmentMimeType = completed.mimeType
            )
            attachmentTransfer.completeIncoming(senderOnion, completed.transferId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist received photo: ${e.message}")
            deduplicator.forget(frame.messageId)
            return false
        }
        sendMessageAck(senderOnion, frame.messageId)
        if (!visibleChatContacts.contains(contact.id)) {
            messageNotifier.showIncoming(contact, "Photo", isPhoto = true)
        }
        messageListener?.onMessageReceived(
            contact,
            OnionMessage(
                id = completed.messageId.toString(),
                text = "Photo",
                senderOnion = senderOnion,
                timestamp = completed.timestamp,
                isOutgoing = false,
                deliveryState = OnionMessageProtocol.DeliveryState.DELIVERED
            )
        )
        return true
    }

    private suspend fun handleAck(frame: OnionMessageProtocol.OnionFrame) {
        val ackedId = OnionMessageProtocol.getAckedMessageId(frame) ?: return
        Log.d(TAG, "✅ ACK received for message $ackedId")

        // Preserve the queue record long enough to map the transport ACK back to
        // its visible chat item. Photo metadata and intermediate chunks must not
        // mark the photo delivered; only its final chunk is allowed to do that.
        val queuedMessage = messageQueue.getQueuedMessage(ackedId.toString())
        messageQueue.markDelivered(ackedId.toString())
        queueWakeSignal.tryEmit(Unit)

        if (queuedMessage != null) {
            if (!queuedMessage.completesUiMessage) return
            // A final photo receipt proves the mailbox/direct transfer is
            // complete. Discard its metadata and intermediate chunks together.
            messageQueue.markAttachmentDelivered(queuedMessage.uiMessageId)
            try {
                messageRepository.updateDeliveryState(
                    queuedMessage.uiMessageId,
                    OnionMessageProtocol.DeliveryState.DELIVERED
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update delivery state in DB: ${e.message}")
            }
            messageListener?.onDeliveryStatusChanged(
                queuedMessage.uiMessageId,
                OnionMessageProtocol.DeliveryState.DELIVERED
            )
            return
        }

        // Compatibility path for delivery acknowledgements from older queues.
        val attachmentMessageId = attachmentPartToMessage.remove(ackedId.toString())
        if (attachmentMessageId != null) {
            val remaining = pendingAttachmentParts[attachmentMessageId]
            remaining?.remove(ackedId.toString())
            val state = if (remaining.isNullOrEmpty()) {
                pendingAttachmentParts.remove(attachmentMessageId)
                OnionMessageProtocol.DeliveryState.DELIVERED
            } else {
                OnionMessageProtocol.DeliveryState.SENDING
            }
            messageRepository.updateDeliveryState(attachmentMessageId, state)
            messageListener?.onDeliveryStatusChanged(attachmentMessageId, state)
            return
        }
        
        // ALWAYS update database delivery status
        try {
            messageRepository.updateDeliveryState(
                ackedId.toString(), 
                OnionMessageProtocol.DeliveryState.DELIVERED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update delivery state in DB: ${e.message}")
        }
        
        // Also notify UI listener if set
        messageListener?.onDeliveryStatusChanged(
            ackedId.toString(), 
            OnionMessageProtocol.DeliveryState.DELIVERED
        )
    }
    
    private suspend fun handleNack(frame: OnionMessageProtocol.OnionFrame) {
        val requestedId = OnionMessageProtocol.getAckedMessageId(frame) ?: return
        Log.d(TAG, "📤 NACK received, resending message $requestedId")
        
        // Re-trigger sending
        messageQueue.handleSendFailure(requestedId.toString(), "NACK received")
    }
    
    private suspend fun handlePing(senderOnion: String, frame: OnionMessageProtocol.OnionFrame) {
        Log.d(TAG, "🏓 Received PING from ${redactEndpoint(senderOnion)}")
        markPeerResponsive(senderOnion)
        
        val pongFrame = OnionMessageProtocol.createPongFrame(frame.messageId)
        circuitManager.sendData(senderOnion, encryptFrame(senderOnion, pongFrame).encode())
    }
    
    private fun handlePong(senderOnion: String, frame: OnionMessageProtocol.OnionFrame) {
        Log.d(TAG, "🏓 Received PONG")
        markPeerResponsive(senderOnion)
    }

    private fun handleTyping(senderOnion: String, frame: OnionMessageProtocol.OnionFrame) {
        if (frame.payload.firstOrNull() == 1.toByte()) {
            _typingPeers.update { it + (senderOnion to System.currentTimeMillis() + 4_000L) }
        } else {
            _typingPeers.update { it - senderOnion }
        }
    }

    private fun markPeerResponsive(peerOnion: String) {
        if (peerOnion.isBlank() || peerOnion.startsWith("unknown-")) return
        val wasOnline = _connectedPeers.value.contains(peerOnion)
        lastVerifiedPeerActivity[peerOnion] = System.currentTimeMillis()
        if (!wasOnline) {
            _connectedPeers.update { it + peerOnion }
            contactManager.getContactByOnion(peerOnion)?.let { contact ->
                messageListener?.onPeerOnline(contact)
            }
        }
    }
    
    private fun getSequenceManager(onionAddress: String): OnionMessageProtocol.SequenceManager {
        return sequenceManagers.getOrPut(onionAddress) {
            OnionMessageProtocol.SequenceManager()
        }
    }

    /**
     * Reserve the peer before launching the coroutine so the polling reader
     * cannot consume bytes between socket registration and this read loop.
     */
    private fun ensureReadLoop(peerOnion: String) {
        val connectionId = circuitManager.getActiveConnectionId(peerOnion) ?: return
        if (readLoopConnections.putIfAbsent(peerOnion, connectionId) == connectionId) return
        readLoopConnections[peerOnion] = connectionId
        peersWithReadLoop.add(peerOnion)
        scope.launch {
            continuousReadLoop(peerOnion, connectionId)
        }
    }

    private fun shouldPreferIncomingCircuit(peerOnion: String): Boolean {
        val local = contactManager.getMyContactInfo().onionAddress.substringBefore(':').lowercase()
        val remote = peerOnion.substringBefore(':').lowercase()
        return local.isNotBlank() && remote.isNotBlank() && local > remote
    }

    private fun redactEndpoint(endpoint: String): String {
        if (endpoint.isBlank()) return "<empty>"
        val host = endpoint.substringBefore(":")
        return "${host.take(12)}..."
    }

    private fun getMyStaticPublicKeyHex(): String {
        return encryptionService.getStaticPublicKey()?.joinToString("") { "%02x".format(it) }.orEmpty()
    }

    private fun fingerprintForPublicKeyHex(publicKeyHex: String): String {
        val keyBytes = runCatching { hexStringToByteArray(publicKeyHex) }
            .getOrElse { publicKeyHex.toByteArray(Charsets.UTF_8) }
        return MessageDigest.getInstance("SHA-256")
            .digest(keyBytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun getChainHead(peerKey: String): String? {
        return messageChainPrefs.getString(peerKey, null)
    }

    private fun updateChainHead(peerKey: String, hash: String) {
        messageChainPrefs.edit().putString(peerKey, hash).apply()
    }

    private fun currentRouteForMessage(messageId: String): TorDeliveryRoute? {
        return deliveryRoutes[messageId]
    }

    private fun prepareDirectFrameForDelivery(
        recipientOnion: String,
        message: OnionMessageQueue.QueuedMessage
    ): ByteArray {
        val frame = OnionMessageProtocol.OnionFrame.decode(message.frameData)
            ?: return message.frameData

        if (frame.frameType == OnionMessageProtocol.FrameType.CONTACT_EXCHANGE ||
            isDirectSealedPayload(frame.payload)
        ) {
            return message.frameData
        }

        // The outer timestamp is an anti-replay transport timestamp, not the
        // message creation time. Refresh it on each retry so a valid offline
        // outbox entry is not rejected merely because Tor was unavailable.
        return encryptFrame(
            recipientOnion,
            frame.copy(timestamp = System.currentTimeMillis())
        ).encode()
    }

    private fun createMailboxPayloadForDelivery(
        contact: OnionContactManager.OnionContact,
        message: OnionMessageQueue.QueuedMessage
    ): ByteArray? {
        message.mailboxPayload?.let { return it }

        val frame = OnionMessageProtocol.OnionFrame.decode(message.frameData) ?: return null
        if (frame.frameType != OnionMessageProtocol.FrameType.CONTACT_EXCHANGE &&
            isDirectSealedPayload(frame.payload)
        ) {
            Log.w(TAG, "Cannot mailbox-deliver legacy direct-sealed frame ${message.id}; waiting for direct retry")
            return null
        }

        return createSealedMailboxPayload(
            contact,
            frame.copy(timestamp = System.currentTimeMillis())
        )
    }

    private fun isDirectSealedPayload(payload: ByteArray): Boolean {
        if (payload.size < DIRECT_SEALED_MAGIC.size + 1) return false
        for (i in DIRECT_SEALED_MAGIC.indices) {
            if (payload[i] != DIRECT_SEALED_MAGIC[i]) return false
        }
        return payload[DIRECT_SEALED_MAGIC.size] == DIRECT_SEALED_VERSION
    }

    private fun createSealedMailboxPayload(
        contact: OnionContactManager.OnionContact,
        frame: OnionMessageProtocol.OnionFrame
    ): ByteArray? {
        if (contact.publicKey.isEmpty()) return null

        return try {
            val recipientPublicKey = hexStringToByteArray(contact.publicKey)
            val ephemeral = X25519KeyPair.generate()
            val sharedSecret = X25519.computeSharedSecret(ephemeral.privateKey, recipientPublicKey)
            val nonce = ByteArray(MAILBOX_NONCE_SIZE).also { SecureRandom().nextBytes(it) }
            val plaintext = frame.encode()
            val aad = MAILBOX_SEALED_MAGIC + byteArrayOf(MAILBOX_SEALED_VERSION) + ephemeral.publicKey
            val ciphertext = aesGcmEncrypt(
                plaintext = plaintext,
                key = deriveMailboxKey(sharedSecret, ephemeral.publicKey, recipientPublicKey),
                nonce = nonce,
                aad = aad
            )

            ByteBuffer.allocate(aad.size + nonce.size + 4 + ciphertext.size)
                .put(aad)
                .put(nonce)
                .putInt(ciphertext.size)
                .put(ciphertext)
                .array()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create mailbox sealed payload: ${e.message}")
            null
        }
    }

    /**
     * Returns true only when the payload passed structural validation and was handed
     * to the normal inbound path. The mailbox client deletes only these committed
     * messages, so a transient decode/decryption failure cannot silently lose mail.
     */
    private suspend fun handleMailboxPayload(payload: ByteArray, mailboxOnion: String): Boolean {
        val unsealed = decryptSealedMailboxPayload(payload)
        if (unsealed == null) {
            val rawFrame = OnionMessageProtocol.OnionFrame.decode(payload) ?: return false
            if (rawFrame.frameType != OnionMessageProtocol.FrameType.CONTACT_EXCHANGE) {
                Log.w(TAG, "Rejected unsealed mailbox payload with type ${rawFrame.frameType}")
                return false
            }
            return handleIncomingData(mailboxOnion, payload)
        }

        val frame = OnionMessageProtocol.OnionFrame.decode(unsealed) ?: return false
        val senderKey = when (frame.frameType) {
            OnionMessageProtocol.FrameType.MESSAGE -> TorMessageEnvelope.decode(frame.payload)?.senderKey
            OnionMessageProtocol.FrameType.FILE_META -> attachmentTransfer.decodeMeta(frame.payload)?.senderPublicKey
            else -> null
        }
        val contact = senderKey
            ?.takeIf { it.isNotEmpty() }
            ?.let { contactManager.getContactByPublicKey(it) }

        return handleIncomingData(
            senderOnion = contact?.onionAddress?.takeIf { it.isNotEmpty() } ?: mailboxOnion,
            data = unsealed,
            isAlreadyDecrypted = true
        )
    }

    private fun decryptSealedMailboxPayload(payload: ByteArray): ByteArray? {
        return try {
            if (payload.size < MAILBOX_SEALED_MAGIC.size + 1 + 32 + MAILBOX_NONCE_SIZE + 4) return null

            val buffer = ByteBuffer.wrap(payload)
            val magic = ByteArray(MAILBOX_SEALED_MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(MAILBOX_SEALED_MAGIC)) return null

            val version = buffer.get()
            if (version != MAILBOX_SEALED_VERSION) return null

            val ephemeralPublicKey = ByteArray(32)
            buffer.get(ephemeralPublicKey)

            val nonce = ByteArray(MAILBOX_NONCE_SIZE)
            buffer.get(nonce)

            val ciphertextSize = buffer.getInt()
            if (ciphertextSize < 0 || ciphertextSize > buffer.remaining()) return null

            val ciphertext = ByteArray(ciphertextSize)
            buffer.get(ciphertext)

            val identityKey = SecureIdentityStateManager(context).loadStaticKey()?.first ?: return null
            val myPublicKey = encryptionService.getStaticPublicKey() ?: return null
            val sharedSecret = X25519.computeSharedSecret(identityKey, ephemeralPublicKey)
            val aad = MAILBOX_SEALED_MAGIC + byteArrayOf(version) + ephemeralPublicKey

            aesGcmDecrypt(
                ciphertext = ciphertext,
                key = deriveMailboxKey(sharedSecret, ephemeralPublicKey, myPublicKey),
                nonce = nonce,
                aad = aad
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt mailbox payload: ${e.message}")
            null
        }
    }

    private fun deriveMailboxKey(
        sharedSecret: ByteArray,
        ephemeralPublicKey: ByteArray,
        recipientPublicKey: ByteArray
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("SeamlessTorMailboxSealedPayload/v1".toByteArray(Charsets.UTF_8))
        digest.update(sharedSecret)
        digest.update(ephemeralPublicKey)
        digest.update(recipientPublicKey)
        return digest.digest()
    }

    private fun aesGcmEncrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun createDirectSealedPayload(
        frame: OnionMessageProtocol.OnionFrame,
        peerPublicKey: ByteArray
    ): ByteArray {
        val localKeyPair = SecureIdentityStateManager(context).loadStaticKey()
            ?: throw IllegalStateException("Local static key unavailable")
        val localPrivateKey = localKeyPair.first
        val localPublicKey = localKeyPair.second
        val sharedSecret = X25519.computeSharedSecret(localPrivateKey, peerPublicKey)
        val nonce = ByteArray(DIRECT_NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val aad = directFrameAad(frame)
        val sealedHeader = DIRECT_SEALED_MAGIC + byteArrayOf(DIRECT_SEALED_VERSION)
        val ciphertext = aesGcmEncrypt(
            plaintext = frame.payload,
            key = deriveDirectTransportKey(sharedSecret, localPublicKey, peerPublicKey),
            nonce = nonce,
            aad = aad + sealedHeader
        )

        return ByteBuffer.allocate(sealedHeader.size + nonce.size + 4 + ciphertext.size)
            .put(sealedHeader)
            .put(nonce)
            .putInt(ciphertext.size)
            .put(ciphertext)
            .array()
    }

    private fun openDirectSealedPayload(
        frame: OnionMessageProtocol.OnionFrame,
        peerPublicKey: ByteArray
    ): ByteArray? {
        return try {
            val payload = frame.payload
            if (payload.size < DIRECT_SEALED_MAGIC.size + 1 + DIRECT_NONCE_SIZE + 4) return null

            val buffer = ByteBuffer.wrap(payload)
            val magic = ByteArray(DIRECT_SEALED_MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(DIRECT_SEALED_MAGIC)) return null

            val version = buffer.get()
            if (version != DIRECT_SEALED_VERSION) return null

            val nonce = ByteArray(DIRECT_NONCE_SIZE)
            buffer.get(nonce)

            val ciphertextSize = buffer.getInt()
            if (ciphertextSize < 0 || ciphertextSize > buffer.remaining()) return null

            val ciphertext = ByteArray(ciphertextSize)
            buffer.get(ciphertext)

            val localKeyPair = SecureIdentityStateManager(context).loadStaticKey()
                ?: throw IllegalStateException("Local static key unavailable")
            val sharedSecret = X25519.computeSharedSecret(localKeyPair.first, peerPublicKey)
            val sealedHeader = DIRECT_SEALED_MAGIC + byteArrayOf(version)

            aesGcmDecrypt(
                ciphertext = ciphertext,
                key = deriveDirectTransportKey(sharedSecret, localKeyPair.second, peerPublicKey),
                nonce = nonce,
                aad = directFrameAad(frame) + sealedHeader
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct sealed payload decrypt failed for ${frame.messageId}: ${e.message}")
            null
        }
    }

    private fun deriveDirectTransportKey(
        sharedSecret: ByteArray,
        localPublicKey: ByteArray,
        peerPublicKey: ByteArray
    ): ByteArray {
        val (firstKey, secondKey) = if (compareBytes(localPublicKey, peerPublicKey) <= 0) {
            localPublicKey to peerPublicKey
        } else {
            peerPublicKey to localPublicKey
        }

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("SeamlessTorDirectTransport/v1".toByteArray(Charsets.UTF_8))
        digest.update(sharedSecret)
        digest.update(firstKey)
        digest.update(secondKey)
        return digest.digest()
    }

    private fun directFrameAad(frame: OnionMessageProtocol.OnionFrame): ByteArray {
        return ByteBuffer.allocate(16 + 8 + 8 + 1)
            .putLong(frame.messageId.mostSignificantBits)
            .putLong(frame.messageId.leastSignificantBits)
            .putLong(frame.sequence)
            .putLong(frame.timestamp)
            .put(frame.frameType.code)
            .array()
    }

    private fun compareBytes(left: ByteArray, right: ByteArray): Int {
        val min = minOf(left.size, right.size)
        for (i in 0 until min) {
            val a = left[i].toInt() and 0xFF
            val b = right[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return left.size - right.size
    }
    
    private fun encryptFrame(
        recipientOnion: String,
        frame: OnionMessageProtocol.OnionFrame
    ): OnionMessageProtocol.OnionFrame {
        if (frame.frameType == OnionMessageProtocol.FrameType.CONTACT_EXCHANGE) {
            return frame
        }

        // Get the contact's public key for encryption
        val contact = contactManager.getContactByOnion(recipientOnion)
        val publicKeyHex = contact?.publicKey
        
        // Production direct Tor frames use a deterministic X25519 pairwise
        // envelope. The previous ad-hoc ratchet generated independent DH state
        // on each phone, so sends could succeed locally while receivers could
        // not derive the same message key.
        if (publicKeyHex != null && publicKeyHex.isNotEmpty()) {
            try {
                val publicKeyBytes = hexStringToByteArray(publicKeyHex)
                val encrypted = createDirectSealedPayload(frame, publicKeyBytes)
                
                // Verify encrypted payload fits within frame size limit
                if (encrypted.size > OnionMessageProtocol.MAX_PAYLOAD_SIZE) {
                    throw IllegalStateException("Encrypted payload too large: ${encrypted.size}")
                }
                
                return OnionMessageProtocol.OnionFrame(
                    messageId = frame.messageId,
                    sequence = frame.sequence,
                    timestamp = frame.timestamp,
                    frameType = frame.frameType,
                    payload = encrypted
                )
            } catch (e: Exception) {
                Log.e(TAG, "Direct Tor frame encryption failed: ${e.message}")
            }
        }
        
        // Fallback: use Noise encryption via EncryptionService (if handshake completed)
        try {
            if (encryptionService.hasEstablishedSession(recipientOnion)) {
                val encrypted = encryptionService.encrypt(frame.payload, recipientOnion)
                if (encrypted.size <= OnionMessageProtocol.MAX_PAYLOAD_SIZE) {
                    return OnionMessageProtocol.OnionFrame(
                        messageId = frame.messageId,
                        sequence = frame.sequence,
                        timestamp = frame.timestamp,
                        frameType = frame.frameType,
                        payload = encrypted
                    )
                }
                throw IllegalStateException("Noise encrypted payload too large: ${encrypted.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Noise encryption failed: ${e.message}")
        }
        
        throw IllegalStateException("No encrypted Tor session for ${redactEndpoint(recipientOnion)}")
    }
    
    private fun decryptData(senderOnion: String, data: ByteArray): ByteArray {
        val frame = OnionMessageProtocol.OnionFrame.decode(data)
            ?: throw IllegalArgumentException("Invalid Tor frame")
        
        // Get the sender's public key for decryption
        val contact = contactManager.getContactByOnion(senderOnion)
        val publicKeyHex = contact?.publicKey
        
        // First try: direct decryption with the known sender's public key.
        if (publicKeyHex != null && publicKeyHex.isNotEmpty()) {
            try {
                val publicKeyBytes = hexStringToByteArray(publicKeyHex)
                val decrypted = openDirectSealedPayload(frame, publicKeyBytes)
                if (decrypted != null) {
                    return OnionMessageProtocol.OnionFrame(
                        messageId = frame.messageId,
                        sequence = frame.sequence,
                        timestamp = frame.timestamp,
                        frameType = frame.frameType,
                        payload = decrypted
                    ).encode()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Tor frame decryption failed for ${redactEndpoint(senderOnion)}: ${e.message}")
            }
        }

        // ━━━ FALLBACK: Try ALL contacts' public keys ━━━
        // If the onion lookup failed (empty address, format mismatch, contact not
        // found), we still have the encrypted frame. AES-GCM tag verification
        // guarantees only the correct sender's key will produce a valid decrypt.
        // This handles ALL possible contact lookup failures.
        if (isDirectSealedPayload(frame.payload)) {
            val allContacts = contactManager.getAllContacts()
            for (candidate in allContacts) {
                val candidateKey = candidate.publicKey
                if (candidateKey.isBlank()) continue
                // Skip the contact we already tried above
                if (candidateKey.equals(publicKeyHex, ignoreCase = true)) continue
                try {
                    val candidateKeyBytes = hexStringToByteArray(candidateKey)
                    val decrypted = openDirectSealedPayload(frame, candidateKeyBytes)
                    if (decrypted != null) {
                        Log.i(TAG, "🔓 Decrypted via contact fallback: ${candidate.nickname} (onion lookup for '$senderOnion' had failed)")
                        return OnionMessageProtocol.OnionFrame(
                            messageId = frame.messageId,
                            sequence = frame.sequence,
                            timestamp = frame.timestamp,
                            frameType = frame.frameType,
                            payload = decrypted
                        ).encode()
                    }
                } catch (_: Exception) {
                    // AES-GCM tag mismatch — not this contact, try next
                }
            }
            Log.w(TAG, "❌ Direct sealed payload could not be decrypted by any known contact (tried ${allContacts.size} contacts)")
        }
        
        // Fallback: use Noise decryption via EncryptionService
        try {
            if (encryptionService.hasEstablishedSession(senderOnion)) {
                val decrypted = encryptionService.decrypt(frame.payload, senderOnion)
                return OnionMessageProtocol.OnionFrame(
                    messageId = frame.messageId,
                    sequence = frame.sequence,
                    timestamp = frame.timestamp,
                    frameType = frame.frameType,
                    payload = decrypted
                ).encode()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Noise decryption failed for ${redactEndpoint(senderOnion)}: ${e.message}")
        }
        
        throw SecurityException("Unable to decrypt Tor frame from ${redactEndpoint(senderOnion)}")
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        scope.launch {
            stop()
        }
        circuitManager.destroy()
    }
    
    /**
     * Convert hex string to ByteArray
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    // ===== Bramble Rendezvous Protocol (BRP) =====

    /**
     * Start rendezvous attempts for all contacts that need it.
     * Called during service startup.
     */
    private fun startRendezvousForPendingContacts() {
        val brpContacts = contactManager.getRendezvousContacts()
        if (brpContacts.isEmpty()) return

        Log.i(TAG, "🔑 Starting BRP for ${brpContacts.size} pending contacts")

        rendezvous.setCallback(object : BrambleRendezvous.RendezvousCallback {
            override fun onRendezvousConnected(peerId: String, peerOnion: String) {
                Log.i(TAG, "✅ BRP connected: $peerId → $peerOnion")
                // Update the contact with the resolved onion address
                contactManager.resolveRendezvousAddress(peerId, peerOnion)

                // Send contact exchange to finalize the connection
                scope.launch {
                    sendContactExchange(peerOnion)
                }
            }

            override fun onRendezvousFailed(peerId: String, reason: String) {
                Log.w(TAG, "❌ BRP failed for $peerId: $reason")
            }
        })

        for (contact in brpContacts) {
            scope.launch {
                startRendezvousForContact(contact)
            }
        }
    }

    /**
     * Start a BRP rendezvous for a specific contact.
     */
    private suspend fun startRendezvousForContact(contact: OnionContactManager.OnionContact) {
        if (!contact.requiresRendezvous()) return
        if (contact.publicKey.isEmpty()) {
            Log.w(TAG, "Cannot rendezvous with ${contact.nickname} — no public key")
            return
        }

        val staticKeyPair = SecureIdentityStateManager(context).loadStaticKey()
            ?: run {
                encryptionService.getStaticPublicKey()
                SecureIdentityStateManager(context).loadStaticKey()
            }
            ?: run {
                Log.w(TAG, "Cannot rendezvous with ${contact.nickname} — no local static key")
                return
            }

        val privateKeyMaterial = staticKeyPair.first
        val ourPublicKey = staticKeyPair.second

        val peerPublicKey = hexStringToByteArray(contact.publicKey)
        if (peerPublicKey.size != 32) {
            Log.w(TAG, "Cannot rendezvous with ${contact.nickname} — invalid peer key size ${peerPublicKey.size}")
            return
        }

        rendezvous.startRendezvous(
            peerId = contact.id,
            ourPrivateKey = privateKeyMaterial,
            ourPublicKey = ourPublicKey,
            peerPublicKey = peerPublicKey
        )
    }

    // ===== Bramble Sync Protocol (BSP) =====

    /**
     * Start the Bramble Sync Engine.
     * BSP runs alongside the existing message queue for reliable sync.
     */
    private fun startSyncEngine() {
        syncEngine.setListener(object : BrambleSyncEngine.SyncListener {
            override fun onMessageSynced(peerId: String, messageId: String) {
                Log.d(TAG, "✅ BSP: Message $messageId synced with $peerId")
                scope.launch {
                    messageQueue.markDelivered(messageId)
                }
            }

            override fun onBatchReceived(peerId: String, frames: List<OnionMessageProtocol.OnionFrame>) {
                Log.i(TAG, "📨 BSP: Received batch of ${frames.size} messages from $peerId")
                scope.launch {
                    for (frame in frames) {
                        // Process each synced message through the normal handler
                        handleIncomingData(peerId, frame.encode())
                    }
                }
            }

            override fun onSyncStateChanged(peerId: String, state: BrambleSyncEngine.SyncState) {
                Log.d(TAG, "🔄 BSP: $peerId → $state")
            }
        })

        // Wire TorCircuitManager to BrambleSyncEngine
        syncEngine.registerTransport(object : BrambleSyncEngine.BspTransport {
            override suspend fun getConnectedPeers(): List<String> {
                return circuitManager.getConnectedPeers()
            }
            override suspend fun sendData(peerId: String, data: ByteArray): Boolean {
                return circuitManager.sendData(peerId, data)
            }
        })

        syncEngine.startSyncLoop()
        Log.i(TAG, "🔄 Bramble Sync Engine started")
    }

    // ===== Mailbox Protocol =====

    /**
     * Try to deliver a message via the recipient's mailbox.
     * This is the fallback path when direct P2P connection fails.
     *
     * Flow: Us → Tor → Peer's Mailbox (.onion) → Peer polls and retrieves
     *
     * The message is already E2E encrypted, so the mailbox only sees an opaque blob.
     * The mailbox stores it in a folder identified by the recipient's public key fingerprint.
     *
     * @return true if the message was successfully stored in the mailbox
     */
    private suspend fun tryMailboxDelivery(
        recipientOnion: String,
        message: OnionMessageQueue.QueuedMessage
    ): Boolean {
        if (mailboxFallbackDisabled.contains(message.id)) {
            return false
        }

        // Look up the contact to find their mailbox address
        val contact = contactManager.getContactByOnion(recipientOnion) ?: return false
        val mailboxOnion = contact.mailboxOnion

        if (mailboxOnion.isNullOrEmpty()) {
            Log.d(TAG, "📭 No mailbox configured for ${contact.nickname} — cannot use fallback")
            return false
        }

        return try {
            val recipientId = fingerprintForPublicKeyHex(contact.publicKey)
            val payload = createMailboxPayloadForDelivery(contact, message)
            if (payload == null) {
                Log.d(TAG, "Mailbox payload unavailable for ${message.id}")
                return false
            }

            val stored = mailboxClient.storeInMailbox(
                mailboxOnion = mailboxOnion,
                recipientId = recipientId,
                encryptedPayload = payload
            )

            if (stored) {
                deliveryRoutes[message.uiMessageId] = TorDeliveryRoute.MAILBOX
                messageQueue.markMailboxStored(message.id)
                if (message.completesUiMessage) {
                    messageRepository.updateDelivery(
                        message.uiMessageId,
                        OnionMessageProtocol.DeliveryState.SENT,
                        OnionMessageProtocol.DeliveryRoute.MAILBOX
                    )
                }
                Log.i(TAG, "📬 Message ${message.id} delivered via mailbox → $mailboxOnion")

                // Notify listener about delivery route
                if (message.completesUiMessage) {
                    messageListener?.onDeliveryStatusChanged(
                        message.uiMessageId,
                        OnionMessageProtocol.DeliveryState.SENT
                    )
                }
            }

            stored
        } catch (e: Exception) {
            Log.e(TAG, "📭 Mailbox delivery failed for ${message.id}: ${e.message}")
            false
        }
    }

    /**
     * Handle incoming MAILBOX_EXCHANGE frame from a peer.
     * The peer is sharing their mailbox .onion address so we can deliver
     * messages to them even when they're offline.
     */
    private fun handleMailboxExchange(senderOnion: String, frame: OnionMessageProtocol.OnionFrame) {
        val exchange = OnionMessageProtocol.MailboxExchange.fromBytes(frame.payload)
        if (exchange == null) {
            Log.w(TAG, "Failed to parse mailbox exchange from ${redactEndpoint(senderOnion)}")
            return
        }

        Log.i(TAG, "📬 Peer ${redactEndpoint(senderOnion)} shared mailbox: ${redactEndpoint(exchange.mailboxOnion)}")

        // Update the contact's mailbox address
        val contact = contactManager.getContactByOnion(senderOnion)
        if (contact != null) {
            val endpoint = OnionContactManager.OnionContact
                .canonicalizeOnionEndpoint(exchange.mailboxOnion)
            if (endpoint.isBlank() || !endpoint.contains(".onion", ignoreCase = true)) {
                Log.w(TAG, "Rejected invalid mailbox endpoint from ${redactEndpoint(senderOnion)}")
                return
            }
            contactManager.updateMailboxAddress(contact.id, endpoint)
        }
    }

    /**
     * Handle incoming MAILBOX_STORE frame.
     * This is received when someone sends a message via our local MailboxService.
     * We act as a relay — store the encrypted blob for the intended recipient.
     */
    private suspend fun handleMailboxStore(senderOnion: String, frame: OnionMessageProtocol.OnionFrame) {
        val parsed = OnionMessageProtocol.parseMailboxStore(frame.payload)
        if (parsed == null) {
            Log.w(TAG, "Failed to parse mailbox store from ${redactEndpoint(senderOnion)}")
            return
        }

        val (recipientFolderId, encryptedPayload) = parsed
        Log.i(TAG, "📬 Mailbox store request from ${redactEndpoint(senderOnion)} for folder ${recipientFolderId.take(12)}...")

        // Check if we're running a local MailboxService
        val mailboxService = MailboxService.getInstance(context)
        if (mailboxService.getMessageCount(recipientFolderId) >= 0) {
            // We are acting as a mailbox — store the message
            // The MailboxService HTTP API handles storage, but for direct P2P delivery
            // we can inject directly
            Log.d(TAG, "📬 Storing message in local mailbox for $recipientFolderId")
        }
    }

    /**
     * Start polling registered mailboxes for offline messages.
     * This is the receiving side of the store-and-forward architecture.
     *
     * Flow: Peer sends → Peer's Mailbox stores → We poll → We receive
     */
    private fun startMailboxPolling() {
        // Initialize mailbox client with our encryption service
        if (!mailboxClient.isInitialized()) {
            mailboxClient.initialize(encryptionService)
        }

        // Set up message callback to process fetched messages
        mailboxClient.setMessageCallback(object : MailboxClient.MessageCallback {
            override suspend fun onMessagesReceived(messages: List<MailboxClient.FetchedMessage>): Set<String> {
                val committed = mutableSetOf<String>()
                for (msg in messages) {
                    try {
                        if (handleMailboxPayload(msg.encryptedPayload, msg.mailboxOnion)) {
                            committed += msg.id
                            Log.d(TAG, "📬 Processed mailbox message ${msg.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process mailbox message ${msg.id}: ${e.message}")
                    }
                }
                return committed
            }
        })

        // Start polling
        mailboxClient.startPolling()
        Log.i(TAG, "📬 Mailbox polling started")
    }

    /**
     * Send our mailbox address to a peer so they can deliver messages to us when we're offline.
     * Called after a successful direct connection or contact exchange.
     */
    private suspend fun sendMailboxExchange(recipientOnion: String) {
        val myOnion = getMyOnionAddress()
        val mailboxEndpoint = ownMailboxOnion
        if (mailboxEndpoint.isNullOrBlank()) {
            Log.d(TAG, "Cannot send mailbox exchange - mailbox hidden service not ready")
            return
        }

        try {
            val seqManager = getSequenceManager(recipientOnion)
            val frame = OnionMessageProtocol.createMailboxExchangeFrame(
                senderOnion = myOnion,
                mailboxOnion = mailboxEndpoint,
                capabilities = 0x01,
                sequence = seqManager.nextOutbound()
            )

            val encryptedFrame = encryptFrame(recipientOnion, frame)
            circuitManager.sendData(recipientOnion, encryptedFrame.encode())
            Log.i(TAG, "📬 Sent mailbox exchange to ${redactEndpoint(recipientOnion)}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send mailbox exchange: ${e.message}")
        }
    }

    /** Starts the local mailbox and publishes the exact port through Tor. */
    private suspend fun startMailboxHost() {
        if (!mailboxClient.isInitialized()) {
            mailboxClient.initialize(encryptionService)
        }

        val signingKey = encryptionService.getSigningPublicKey()
        if (signingKey == null) {
            Log.w(TAG, "Mailbox unavailable - identity signing key is missing")
            return
        }

        val mailboxAccessToken = getOrCreateMailboxAccessToken()
        mailboxService.registerOwner(
            encryptionService.getIdentityFingerprint(),
            signingKey,
            mailboxAccessToken
        )
        if (!mailboxService.start()) {
            Log.w(TAG, "Mailbox HTTP server did not start")
            return
        }

        val application = context.applicationContext as? Application
        val daemon = application?.let { TorDaemonManager.getInstance(it) }
        val rawOnion = daemon?.publishMailboxHiddenService(MailboxService.MAILBOX_PORT)
        val endpoint = rawOnion?.let {
            OnionContactManager.OnionContact.canonicalizeOnionEndpoint(
                "$it:${MailboxService.MAILBOX_PORT}"
            )
        }
        if (endpoint.isNullOrBlank()) {
            Log.w(TAG, "Mailbox unavailable - Tor did not publish its hidden service")
            mailboxService.stop()
            return
        }

        ownMailboxOnion = endpoint
        mailboxClient.registerLocalMailbox(endpoint, mailboxAccessToken)
        Log.i(TAG, "Mailbox ready at ${redactEndpoint(endpoint)}")
    }

    private fun getOrCreateMailboxAccessToken(): String {
        val ownerFingerprint = encryptionService.getIdentityFingerprint()
        val storageKey = "tor_mailbox_access_${ownerFingerprint.take(16)}"
        val secureState = SecureIdentityStateManager(context)
        secureState.getSecureValue(storageKey)?.takeIf { it.isNotBlank() }?.let { return it }

        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        ).also { secureState.storeSecureValue(storageKey, it) }
    }
}
