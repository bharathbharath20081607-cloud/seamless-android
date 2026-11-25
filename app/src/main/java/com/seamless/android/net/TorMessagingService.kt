@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.app.Application
import android.content.Context
import android.util.Log
import com.seamless.android.crypto.EncryptionService
import com.seamless.android.crypto.X25519
import com.seamless.android.crypto.X25519KeyPair
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.identity.SecureIdentityStateManager
import com.seamless.android.noise.SeamlessRatchetManager
import com.seamless.android.sync.BrambleSyncEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
        // Onion-service circuits can pause while a rendezvous path is rebuilt.
        // A verified peer should not flicker offline during one of those normal
        // pauses; authenticated PONGs and receipts refresh this lease.
        private const val PEER_PRESENCE_TIMEOUT_MS = 120_000L
        private const val KNOWN_CONTACT_REDIAL_INTERVAL_MS = 30_000L
        // How long a delivery waits for a cold onion rendezvous before also
        // depositing in the peer's mailbox. The dial itself is never cancelled
        // (see TorCircuitManager.getConnectionWithin), so this only bounds latency.
        private const val INITIAL_DIRECT_DIAL_BUDGET_MS = 4_500L
        // Leave room for the encrypted envelope and fixed Tor frame metadata.
        // Enforce this at queue time so an accepted message is always sendable.
        private const val MAX_DIRECT_TEXT_BYTES = 3_700
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
    private val _lanPeersFlow = MutableStateFlow<Set<String>>(emptySet())
    val lanPeersFlow: StateFlow<Set<String>> = _lanPeersFlow.asStateFlow()
    private val _typingPeers = MutableStateFlow<Map<String, Long>>(emptyMap())
    val typingPeersFlow: StateFlow<Set<String>> = _typingPeers
        .map { it.keys }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    // 1-to-1 Rich Chat State sidecar (zero Room DB schema migration)
    private val directRichLock = Any()
    @Suppress("DEPRECATION")
    private val directRichPrefs: android.content.SharedPreferences by lazy {
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(
                context,
                androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS
            )
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "tor_direct_rich_state",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable for rich state (${e.message}); falling back")
            context.getSharedPreferences("tor_direct_rich_state_plain", Context.MODE_PRIVATE)
        }
    }

    private val _directReactionsFlow = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val directReactionsFlow: StateFlow<Map<String, Map<String, String>>> = _directReactionsFlow.asStateFlow()

    private val _directEditedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val directEditedIdsFlow: StateFlow<Set<String>> = _directEditedIdsFlow.asStateFlow()

    private val _directDeletedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val directDeletedIdsFlow: StateFlow<Set<String>> = _directDeletedIdsFlow.asStateFlow()

    private val _directBurnedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val directBurnedIdsFlow: StateFlow<Set<String>> = _directBurnedIdsFlow.asStateFlow()

    private val _directShredReceiptsFlow = MutableStateFlow<Set<String>>(emptySet())
    val directShredReceiptsFlow: StateFlow<Set<String>> = _directShredReceiptsFlow.asStateFlow()

    init {
        loadDirectRichState()
    }

    private fun loadDirectRichState() {
        runCatching {
            val reactions = mutableMapOf<String, Map<String, String>>()
            val edited = mutableSetOf<String>()
            val deleted = mutableSetOf<String>()
            val burned = mutableSetOf<String>()
            val shredReceipts = mutableSetOf<String>()
            synchronized(directRichLock) {
                directRichPrefs.all.forEach { (key, value) ->
                    when {
                        key.startsWith("react:") && value is String -> {
                            val msgId = key.removePrefix("react:")
                            val json = org.json.JSONObject(value)
                            val perSender = mutableMapOf<String, String>()
                            json.keys().forEach { senderKey ->
                                val emoji = json.optString(senderKey)
                                if (emoji.isNotBlank()) perSender[senderKey] = emoji
                            }
                            if (perSender.isNotEmpty()) reactions[msgId] = perSender
                        }
                        key.startsWith("edited:") && value == true -> edited.add(key.removePrefix("edited:"))
                        key.startsWith("deleted:") && value == true -> deleted.add(key.removePrefix("deleted:"))
                        key.startsWith("burned:") && value == true -> burned.add(key.removePrefix("burned:"))
                        key.startsWith("shred:") && value == true -> shredReceipts.add(key.removePrefix("shred:"))
                    }
                }
            }
            _directReactionsFlow.value = reactions
            _directEditedIdsFlow.value = edited
            _directDeletedIdsFlow.value = deleted
            _directBurnedIdsFlow.value = burned
            _directShredReceiptsFlow.value = shredReceipts
        }
    }

    private fun updateDirectReactionState(targetMessageId: String, actorKey: String, emoji: String, action: String) {
        synchronized(directRichLock) {
            val current = _directReactionsFlow.value[targetMessageId]?.toMutableMap() ?: mutableMapOf()
            if (action == "remove" || emoji.isBlank()) {
                current.remove(actorKey)
            } else {
                current[actorKey] = emoji
            }
            val editor = directRichPrefs.edit()
            if (current.isEmpty()) {
                editor.remove("react:$targetMessageId")
                _directReactionsFlow.update { it - targetMessageId }
            } else {
                val json = org.json.JSONObject()
                current.forEach { (k, v) -> json.put(k, v) }
                editor.putString("react:$targetMessageId", json.toString())
                _directReactionsFlow.update { it + (targetMessageId to current.toMap()) }
            }
            editor.apply()
        }
    }

    enum class ActiveTransportTier {
        PHANTOM_LAN,
        WARP_DUAL_TOR,
        TOR_EXPRESS
    }

    private val phantomLanBridge: PhantomLanBridge by lazy {
        PhantomLanBridge(
            context = context,
            contactManager = contactManager,
            onIncomingLanSocket = { socket -> handleIncomingSocket(socket) },
            onPeerDiscoveredOnLan = { peer ->
                scope.launch {
                    if (circuitManager.upgradeToLanIfAvailable(peer)) {
                        refreshLanPeersState()
                        ensureReadLoop(peer)
                        wakeQueueDispatcher()
                    }
                }
            }
        )
    }

    private fun refreshLanPeersState() {
        _lanPeersFlow.value = circuitManager.getConnectedLanPeers().mapTo(HashSet()) { canonicalPeerKey(it) }
    }

    fun getActiveTransportTier(onionAddress: String): ActiveTransportTier {
        val canonical = canonicalPeerKey(onionAddress)
        return when {
            circuitManager.isLanDirectConnection(canonical) -> ActiveTransportTier.PHANTOM_LAN
            circuitManager.hasActiveBulkConnection(canonical) -> ActiveTransportTier.WARP_DUAL_TOR
            else -> ActiveTransportTier.TOR_EXPRESS
        }
    }
    
    private var processingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var queueDeliveryJob: Job? = null
    private var mailboxPollJob: Job? = null
    private var mailboxHostJob: Job? = null
    private var hiddenServiceObserverJob: Job? = null
    private val pendingContactExchanges = ConcurrentHashMap.newKeySet<String>()
    private val queueWakeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val queueWakeChannel = Channel<Unit>(Channel.CONFLATED)

    private fun wakeQueueDispatcher() {
        queueWakeChannel.trySend(Unit)
        queueWakeSignal.tryEmit(Unit)
    }
    
    // Track peers that have a dedicated continuous read loop
    // to prevent processIncomingMessages from racing on the same socket
    private val peersWithReadLoop = ConcurrentHashMap.newKeySet<String>()
    private val readLoopConnections = ConcurrentHashMap<String, String>()
    private val bulkReadLoopConnections = ConcurrentHashMap<String, String>()
    private val visibleChatContacts = ConcurrentHashMap.newKeySet<String>()
    
    // Track peers we've introduced ourselves to on outbound connections.
    // Ensures we always send CONTACT_EXCHANGE as the first frame on a new
    // outbound circuit, so the receiver can identify us without destructive
    // trial decryption that would corrupt ratchet sessions.
    private val peersIntroducedOnConnection = ConcurrentHashMap.newKeySet<String>()
    // Which concrete connection each peer was introduced on; a reconnect yields a
    // new connection id and therefore requires a fresh introduction.
    private val introducedConnectionIds = ConcurrentHashMap<String, String>()
    private var connectionEventsJob: Job? = null
    private val lastVerifiedPeerActivity = ConcurrentHashMap<String, Long>()
    private val lastPeerDialAttempt = ConcurrentHashMap<String, Long>()
    private val lastContactExchangeReplyAt = ConcurrentHashMap<String, Long>()
    private val lastTypingSignalAt = ConcurrentHashMap<String, Long>()
    private val deliveryRoutes = ConcurrentHashMap<String, TorDeliveryRoute>()
    private val mailboxFallbackDisabled = ConcurrentHashMap.newKeySet<String>()
    private val attachmentPartToMessage = ConcurrentHashMap<String, String>()
    private val pendingAttachmentParts = ConcurrentHashMap<String, MutableSet<String>>()
    private val activeRecipientDeliveries = ConcurrentHashMap.newKeySet<String>()
    private val recipientDeliveryLocks = ConcurrentHashMap<String, Mutex>()
    private val secureRandom = SecureRandom()
    private val peerBulkChannels = ConcurrentHashMap<String, Channel<ByteArray>>()
    private val peerBulkWorkers = ConcurrentHashMap<String, Job>()
    private val directSendCounters = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    private val directReplayWindows = ConcurrentHashMap<String, com.seamless.android.crypto.AegisTorCrypto.ReplayWindow512>()
    private val deferredFinalChunkIds = ConcurrentHashMap<String, UUID>()
    private val lastSpeculativeTypingDialAt = ConcurrentHashMap<String, Long>()
    private val lastSpeculativeBulkDialAt = ConcurrentHashMap<String, Long>()
    private val previewShownTransfers = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var ownMailboxOnion: String? = null

    private fun canonicalPeerKey(peerOnion: String): String =
        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerOnion)
            .ifBlank { peerOnion.trim().lowercase() }

    private fun fastHexEncode(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = hexChars[v ushr 4]
            chars[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(chars)
    }
    
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
        if (visible) {
            visibleChatContacts.add(contactId)
            messageNotifier.cancelContact(contactId)
            scope.launch {
                refreshPeerConnection(contactId)
                wakeQueueDispatcher()
            }
        } else {
            visibleChatContacts.remove(contactId)
        }
    }

    fun cancelContactNotification(contactId: String) {
        messageNotifier.cancelContact(contactId)
    }

    /** Clear all visible-chat markers (e.g., when Activity pauses or goes to background). */
    fun clearAllVisibleChats() {
        visibleChatContacts.clear()
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
            
            // Both peers must pick the same socket when they dial each other at once.
            circuitManager.preferIncomingResolver = { peer -> shouldPreferIncomingCircuit(peer) }
            circuitManager.bulkHelloFrameProvider = { peer -> createEncryptedBulkHelloFrame(peer) }
            circuitManager.lanEndpointResolver = { peer -> phantomLanBridge.getVerifiedLanEndpoint(peer) }

            // ━━━ START HIDDEN SERVICE (inbound) ━━━
            // This makes us reachable via .onion address.
            // Without this, no one can connect to us and messages can never arrive.
            val hiddenServiceStarted = startHiddenService()

            // Start Phantom-LAN blinded Wi-Fi discovery BEFORE waitForTor so LAN chats/calls
            // become ready in seconds even during an upstream ISP blackout.
            phantomLanBridge.start()

            // Wait for Tor SOCKS proxy to be ready (outbound). If Arti is slow or
            // unavailable but the embedded daemon is connected, continue with daemon
            // SOCKS fallback so direct onion messaging can still work.
            _serviceState.value = ServiceState.WAITING_FOR_TOR
            val outboundTorReady = waitForTor()
            if (!hiddenServiceStarted && !outboundTorReady && !phantomLanBridge.hasActiveLanInterface()) {
                error(_lastError.value ?: "Tor could not start")
            }

            mailboxHostJob?.cancel()
            mailboxHostJob = scope.launch {
                startMailboxHost()
            }

            observeHiddenServiceReadiness()
            
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
            prewarmActiveContacts()
            Log.i(TAG, "✅ TorMessagingService started successfully")
            
        } catch (e: Exception) {
            val message = e.message ?: e::class.java.simpleName
            _lastError.value = message
            Log.e(TAG, "Failed to start TorMessagingService: $message", e)
            _serviceState.value = ServiceState.ERROR
        }
    }

    private fun observeHiddenServiceReadiness() {
        hiddenServiceObserverJob?.cancel()
        hiddenServiceObserverJob = scope.launch {
            hiddenServiceManager.statusFlow.collect { status ->
                if (status.state == TorHiddenServiceManager.HiddenServiceState.RUNNING &&
                    status.onionAddress.isNotBlank()
                ) {
                    val pending = pendingContactExchanges.toList()
                    if (pending.isNotEmpty()) {
                        pendingContactExchanges.clear()
                        Log.i(TAG, "📤 Flushing ${pending.size} pending contact exchange(s) after HS publish")
                        pending.forEach { peer -> sendContactExchange(peer) }
                    }
                    wakeQueueDispatcher()
                }
            }
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
        mailboxHostJob?.cancel()
        hiddenServiceObserverJob?.cancel()
        peerBulkWorkers.values.forEach { it.cancel() }
        peerBulkWorkers.clear()
        peerBulkChannels.values.forEach { it.close() }
        peerBulkChannels.clear()
        pendingContactExchanges.clear()
        connectionEventsJob?.cancel()
        introducedConnectionIds.clear()
        bulkReadLoopConnections.clear()
        previewShownTransfers.clear()
        deferredFinalChunkIds.clear()
        runCatching { messageQueue.flush() }
        runCatching { attachmentTransfer.cleanupStaleTransfers() }
        mailboxClient.stopPolling()
        mailboxService.stop()
        ownMailboxOnion = null
        syncEngine.stopSyncLoop()
        phantomLanBridge.stop()
        circuitManager.lanEndpointResolver = null
        _lanPeersFlow.value = emptySet()
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
        return contactManager.getMyQrData(ownMailboxOnion)
    }
    
    /**
     * Generate shareable link
     */
    fun getMyShareableLink(): String {
        return contactManager.getMyShareableLink(ownMailboxOnion)
    }
    
    /**
     * Add a contact from shareable link or QR data
     * Automatically sends your info for auto-pairing
     */
    suspend fun addContact(data: String): OnionContactManager.OnionContact? {
        if (_serviceState.value == ServiceState.STOPPED || _serviceState.value == ServiceState.ERROR) {
            com.seamless.android.service.TorForegroundService.start(context.applicationContext)
            scope.launch { start() }
        }
        val contact = contactManager.addContactFromLink(data)
            ?: contactManager.addContactFromQr(data)

        // Auto-pairing: Always send our contact info to them so bidirectional pairing
        // succeeds even if one side already existed or missed an earlier exchange.
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
            val canonicalRecipient = OnionContactManager.OnionContact
                .canonicalizeOnionEndpoint(recipientOnion)
                .ifBlank { recipientOnion }
            val myInfo = contactManager.getMyContactInfo()
            if (myInfo.requiresRendezvous() || myInfo.onionAddress.isBlank()) {
                pendingContactExchanges.add(canonicalRecipient)
                Log.w(TAG, "Queueing contact exchange for ${redactEndpoint(canonicalRecipient)} until our onion address is published")
                return
            }
            
            val seqManager = sequenceManagers.getOrPut(canonicalRecipient) {
                OnionMessageProtocol.SequenceManager()
            }
            
            val frame = OnionMessageProtocol.createContactExchangeFrame(
                myOnionAddress = myInfo.onionAddress,
                myNickname = myInfo.nickname,
                myPublicKey = myInfo.publicKey,
                myAvatarId = myInfo.avatarId,
                sequence = seqManager.nextOutbound()
            )
            
            // DO NOT ENCRYPT - recipient doesn't have our public key yet!
            // The frame contains our public key, which they need to decrypt future messages
            lastContactExchangeReplyAt[canonicalRecipient] = System.currentTimeMillis()
            messageQueue.enqueue(
                recipientOnion = canonicalRecipient,
                frame = frame,
                completesUiMessage = false
            )
            dispatchRecipientNow(canonicalRecipient)
            
            Log.i(TAG, "📤 Sent contact exchange (unencrypted) to ${redactEndpoint(canonicalRecipient)} for auto-pairing")
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
     * Send a text message (optionally with quoted reply or Aegis Burn-On-Read™ capsule) to a contact.
     * Returns the message ID for tracking delivery status.
     */
    suspend fun sendMessage(
        contactId: String,
        text: String,
        replyToId: String? = null,
        burnOnRead: Boolean = false,
        burnTtlSeconds: Int = 12
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

        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Message cannot be empty" }
        val bodyPayload = if (burnOnRead) {
            TorRichChatCodec.encodeBurnText(cleanText, burnTtlSeconds)
        } else {
            cleanText
        }
        val replyTarget = replyToId?.takeIf { it.isNotBlank() }?.let { messageRepository.getMessage(it) }
        val wireText = if (replyTarget != null) {
            val senderLabel = if (replyTarget.isFromMe) {
                contactManager.getMyContactInfo().nickname.ifBlank { "You" }
            } else {
                contact.nickname
            }
            val preview = TorRichChatCodec.friendlyPreviewText(
                replyTarget.text,
                replyTarget.attachmentMimeType
            ).take(90)
            TorRichChatCodec.formatWithReply(bodyPayload, replyTarget.id, senderLabel, preview)
        } else {
            bodyPayload
        }

        return enqueueTorMessage(contact, wireText, replyToId).map { it.messageId }
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
            val canonicalRecipient = OnionContactManager.OnionContact
                .canonicalizeOnionEndpoint(recipientOnion)
            require(OnionContactManager.OnionContact.isValidOnionEndpoint(canonicalRecipient)) {
                "Invalid Tor v3 onion endpoint"
            }
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_DIRECT_TEXT_BYTES) {
                "Message is too large for a single Tor frame"
            }
            require(replyToId == null || replyToId.toByteArray(Charsets.UTF_8).size <= 128) {
                "Reply reference is too large"
            }
            Log.d(TAG, "📤 Sending message to ${redactEndpoint(canonicalRecipient)}")
            
            val contact = contactManager.getContactByOnion(canonicalRecipient)

            // Get or create sequence manager
            val seqManager = sequenceManagers.getOrPut(canonicalRecipient) {
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
                previousHash = getChainHead(canonicalRecipient),
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
                recipientOnion = canonicalRecipient,
                frame = frame,
                uiMessageId = messageId.toString(),
                completesUiMessage = completesUiMessage
            )
            deliveryRoutes[queuedMessageId] = TorDeliveryRoute.QUEUED
            updateChainHead(canonicalRecipient, envelope.chainHash())
            scope.launch {
                syncEngine.addMessageForSync(messageId.toString(), frame.encode(), canonicalRecipient)
            }
            dispatchRecipientNow(canonicalRecipient)
            
            Log.i(TAG, "📨 Message $messageId queued for ${redactEndpoint(canonicalRecipient)}")
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
        mimeType: String,
        groupId: String? = null,
        groupMessageId: String? = null,
        finalChunkMessageId: UUID? = null,
        previewBytes: ByteArray? = null
    ): Result<String> = sendAttachment(
        contactId = contactId,
        bytes = imageBytes,
        mimeType = mimeType,
        groupId = groupId,
        groupMessageId = groupMessageId,
        finalChunkMessageId = finalChunkMessageId,
        previewBytes = previewBytes
    )

    /** Send a bounded image, voice note, or document (up to 1 MB) over Fountain-Warp Tor frames. */
    suspend fun sendAttachment(
        contactId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "",
        durationMs: Int = 0,
        viewOnce: Boolean = false,
        groupId: String? = null,
        groupMessageId: String? = null,
        finalChunkMessageId: UUID? = null,
        previewBytes: ByteArray? = null
    ): Result<String> {
        val contact = contactManager.getContact(contactId)
            ?: return Result.failure(IllegalArgumentException("Contact not found"))
        if (contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
            contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED
        ) {
            return Result.failure(IllegalStateException("Verify this contact before sending media"))
        }

        return try {
            val transferId = UUID.randomUUID()
            val attachmentMessageId = UUID.randomUUID()
            val meta = attachmentTransfer.createMeta(
                transferId = transferId,
                mimeType = mimeType,
                totalBytes = bytes.size,
                senderPublicKey = getMyStaticPublicKeyHex(),
                groupId = groupId,
                groupMessageId = groupMessageId,
                previewBytes = if (viewOnce) null else previewBytes,
                fileName = fileName,
                durationMs = durationMs,
                viewOnce = viewOnce
            )
            val localPath = if (groupId == null) {
                attachmentTransfer.saveOutgoing(transferId, bytes, meta.mimeType)
            } else {
                ""
            }
            if (contact.publicKey.isNotBlank()) {
                circuitManager.ensureBulkConnectionAsync(contact.onionAddress)
            }
            val sequenceManager = getSequenceManager(contact.onionAddress)
            val now = System.currentTimeMillis()
            val kind = when {
                meta.mimeType.startsWith("audio/") -> "voice"
                meta.mimeType.startsWith("image/") -> "photo"
                else -> "file"
            }
            val rowText = if (kind == "photo" && !viewOnce && meta.fileName.isBlank()) {
                "Photo"
            } else {
                TorRichChatCodec.encodeAttachmentDescriptor(
                    TorRichChatCodec.AttachmentDescriptor(
                        kind = kind,
                        fileName = meta.fileName,
                        totalBytes = bytes.size,
                        durationMs = durationMs,
                        viewOnce = viewOnce
                    )
                )
            }

            if (groupId == null) messageRepository.saveSentMessage(
                messageId = attachmentMessageId.toString(),
                contact = contact,
                text = rowText,
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
            val chunks = attachmentTransfer.split(bytes)
            val paritiesByAfterIdx = attachmentTransfer
                .generateFountainParities(transferId, chunks)
                .associateBy { it.afterChunkIndex }
            val batchSpecs = ArrayList<OnionMessageQueue.BatchFrameSpec>(1 + chunks.size + paritiesByAfterIdx.size)
            batchSpecs.add(
                OnionMessageQueue.BatchFrameSpec(
                    frame = metaFrame,
                    uiMessageId = attachmentMessageId.toString(),
                    completesUiMessage = false
                )
            )
            var completionFrameId = ""
            chunks.forEachIndexed { index, chunk ->
                val chunkFrame = OnionMessageProtocol.OnionFrame(
                    // Every frame needs its own transport identity. The final
                    // chunk alone completes the single visible attachment message.
                    messageId = if (index == chunks.lastIndex && finalChunkMessageId != null) {
                        finalChunkMessageId
                    } else {
                        UUID.randomUUID()
                    },
                    sequence = sequenceManager.nextOutbound(),
                    timestamp = now,
                    frameType = OnionMessageProtocol.FrameType.FILE_CHUNK,
                    payload = attachmentTransfer.encodeChunk(transferId, index, chunk)
                )
                val parity = paritiesByAfterIdx[index]
                if (index == chunks.lastIndex) {
                    if (parity != null) {
                        batchSpecs.add(
                            OnionMessageQueue.BatchFrameSpec(
                                frame = OnionMessageProtocol.OnionFrame(
                                    messageId = UUID.randomUUID(),
                                    sequence = sequenceManager.nextOutbound(),
                                    timestamp = now,
                                    frameType = OnionMessageProtocol.FrameType.FILE_PARITY,
                                    payload = parity.payload
                                ),
                                uiMessageId = attachmentMessageId.toString(),
                                completesUiMessage = false
                            )
                        )
                    }
                    completionFrameId = chunkFrame.messageId.toString()
                    batchSpecs.add(
                        OnionMessageQueue.BatchFrameSpec(
                            frame = chunkFrame,
                            uiMessageId = attachmentMessageId.toString(),
                            completesUiMessage = true
                        )
                    )
                } else {
                    batchSpecs.add(
                        OnionMessageQueue.BatchFrameSpec(
                            frame = chunkFrame,
                            uiMessageId = attachmentMessageId.toString(),
                            completesUiMessage = false
                        )
                    )
                    if (parity != null) {
                        batchSpecs.add(
                            OnionMessageQueue.BatchFrameSpec(
                                frame = OnionMessageProtocol.OnionFrame(
                                    messageId = UUID.randomUUID(),
                                    sequence = sequenceManager.nextOutbound(),
                                    timestamp = now,
                                    frameType = OnionMessageProtocol.FrameType.FILE_PARITY,
                                    payload = parity.payload
                                ),
                                uiMessageId = attachmentMessageId.toString(),
                                completesUiMessage = false
                            )
                        )
                    }
                }
            }
            val queuedIds = messageQueue.enqueueBatch(contact.onionAddress, batchSpecs)
            val finalChunkId = completionFrameId.ifBlank { queuedIds.lastOrNull() ?: "" }
            deliveryRoutes[attachmentMessageId.toString()] = TorDeliveryRoute.QUEUED
            dispatchRecipientNow(contact.onionAddress)
            Result.success(finalChunkId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue attachment: ${e.message}")
            Result.failure(e)
        }
    }

    /** Marks local messages read and sends batched READ_RECEIPT(0x0B) frames so sender sees emerald double ticks. */
    suspend fun markContactReadAndSendReceipts(contact: OnionContactManager.OnionContact) {
        val unreadIds = runCatching { messageRepository.markAsReadReturningIds(contact) }
            .getOrDefault(emptyList())
        messageNotifier.cancelContact(contact.id)
        if (unreadIds.isEmpty() ||
            contact.requiresRendezvous() ||
            contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
            contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED
        ) {
            return
        }
        val uuids = unreadIds.mapNotNull { id -> runCatching { UUID.fromString(id) }.getOrNull() }
        if (uuids.isEmpty()) return
        val canonicalRecipient = canonicalPeerKey(contact.onionAddress)
        if (!OnionContactManager.OnionContact.isValidOnionEndpoint(canonicalRecipient)) return
        val seqManager = getSequenceManager(canonicalRecipient)
        uuids.chunked(20).forEach { batch ->
            val receiptFrame = OnionMessageProtocol.createBatchReadReceiptFrame(
                messageIds = batch,
                sequence = seqManager.nextOutbound()
            )
            messageQueue.enqueue(
                recipientOnion = canonicalRecipient,
                frame = receiptFrame,
                completesUiMessage = false
            )
        }
        dispatchRecipientNow(canonicalRecipient)
    }

    /** Sends a 1-to-1 quick emoji reaction toggle over Tor. */
    suspend fun sendDirectReaction(
        contactId: String,
        targetMessageId: String,
        emoji: String
    ): Result<Unit> = runCatching {
        val contact = requireNotNull(contactManager.getContact(contactId)) { "Contact not found" }
        val cleanEmoji = emoji.trim().take(8)
        require(cleanEmoji.isNotEmpty()) { "Invalid reaction" }
        val existingEmoji = _directReactionsFlow.value[targetMessageId]?.get("me")
        val action = if (existingEmoji == cleanEmoji) "remove" else "add"
        updateDirectReactionState(targetMessageId, "me", cleanEmoji, action)
        val ctrl = TorRichChatCodec.encodeDirectControl(
            TorRichChatCodec.DirectControlEvent(
                action = "react",
                targetMessageId = targetMessageId,
                emoji = cleanEmoji,
                reactionAction = action
            )
        )
        sendMessageToOnion(contact.onionAddress, ctrl, completesUiMessage = false).getOrThrow()
    }

    /** Sender-only 1-to-1 message edit over Tor (preserving reply header). */
    suspend fun editDirectMessage(
        contactId: String,
        targetMessageId: String,
        newText: String
    ): Result<Unit> = runCatching {
        val cleanText = newText.trim()
        require(cleanText.isNotEmpty()) { "Message cannot be empty" }
        val contact = requireNotNull(contactManager.getContact(contactId)) { "Contact not found" }
        val existing = requireNotNull(messageRepository.getMessage(targetMessageId)) { "Message not found" }
        require(existing.isFromMe && existing.attachmentPath == null) {
            "Only your own text messages can be edited"
        }
        val body = TorRichChatCodec.parseReply(existing.text).body
        require(
            existing.id !in _directDeletedIdsFlow.value &&
                existing.id !in _directBurnedIdsFlow.value &&
                body != TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT &&
                body != TorRichChatCodec.BURNED_CAPSULE_TEXT &&
                !TorRichChatCodec.isUnburnedSecretText(existing.text)
        ) {
            "This message cannot be edited"
        }
        val parsed = TorRichChatCodec.parseReply(existing.text)
        val updatedStored = TorRichChatCodec.formatWithReply(
            cleanText,
            parsed.replyToId,
            parsed.replyToSender,
            parsed.replyToPreview
        )
        messageRepository.updateMessageText(targetMessageId, updatedStored)
        synchronized(directRichLock) {
            directRichPrefs.edit().putBoolean("edited:$targetMessageId", true).apply()
        }
        _directEditedIdsFlow.update { it + targetMessageId }

        val ctrl = TorRichChatCodec.encodeDirectControl(
            TorRichChatCodec.DirectControlEvent(
                action = "edit",
                targetMessageId = targetMessageId,
                text = cleanText
            )
        )
        sendMessageToOnion(contact.onionAddress, ctrl, completesUiMessage = false).getOrThrow()
    }

    /** Sender-only 1-to-1 delete for everyone over Tor + local attachment shredding. */
    suspend fun deleteDirectMessageForEveryone(
        contactId: String,
        targetMessageId: String
    ): Result<Unit> = runCatching {
        val contact = requireNotNull(contactManager.getContact(contactId)) { "Contact not found" }
        val existing = requireNotNull(messageRepository.getMessage(targetMessageId)) { "Message not found" }
        require(existing.isFromMe) { "Only your own messages can be deleted for everyone" }
        TorRichChatCodec.AegisShredder.shredPath(existing.attachmentPath)
        messageRepository.clearAttachmentAndUpdateText(targetMessageId, TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT)
        synchronized(directRichLock) {
            directRichPrefs.edit().putBoolean("deleted:$targetMessageId", true).apply()
        }
        _directDeletedIdsFlow.update { it + targetMessageId }

        val ctrl = TorRichChatCodec.encodeDirectControl(
            TorRichChatCodec.DirectControlEvent(
                action = "delete",
                targetMessageId = targetMessageId
            )
        )
        sendMessageToOnion(contact.onionAddress, ctrl, completesUiMessage = false).getOrThrow()
    }

    /** Cryptographically shreds a viewed View-Once capsule on disk & DB and transmits a SHRED_RECEIPT. */
    suspend fun consumeAndShredDirectCapsule(
        contactId: String,
        messageId: String
    ): Result<Unit> = runCatching {
        val contact = contactManager.getContact(contactId)
        val existing = messageRepository.getMessage(messageId)
        TorRichChatCodec.AegisShredder.shredPath(existing?.attachmentPath)
        messageRepository.clearAttachmentAndUpdateText(messageId, TorRichChatCodec.BURNED_CAPSULE_TEXT)
        synchronized(directRichLock) {
            directRichPrefs.edit().putBoolean("burned:$messageId", true).apply()
        }
        _directBurnedIdsFlow.update { it + messageId }
        if (contact != null && existing != null && !existing.isFromMe) {
            val ctrl = TorRichChatCodec.encodeDirectControl(
                TorRichChatCodec.DirectControlEvent(
                    action = "shred_receipt",
                    targetMessageId = messageId
                )
            )
            sendMessageToOnion(contact.onionAddress, ctrl, completesUiMessage = false)
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
        val canonical = OnionContactManager.OnionContact
            .canonicalizeOnionEndpoint(onionAddress)
            .ifBlank { onionAddress }
        // A delivery worker streaming to this peer owns the socket introduction and
        // its traffic already proves liveness; a concurrent ping could slip ahead of
        // CONTACT_EXCHANGE on a fresh socket and get the connection rejected.
        if (activeRecipientDeliveries.contains(canonical) || activeRecipientDeliveries.contains(onionAddress)) {
            return true
        }
        val hasPendingExchange = pendingContactExchanges.contains(canonical) ||
            messageQueue.getPendingMessages(canonical).any {
                OnionMessageProtocol.OnionFrame.decode(it.frameData)?.frameType ==
                    OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
            }
        if (hasPendingExchange) {
            dispatchRecipientNow(canonical)
            return true
        }
        return try {
            val connection = circuitManager.getConnection(onionAddress)
            if (connection != null) {
                ensureReadLoop(onionAddress)
                val currentConnId = circuitManager.getActiveConnectionId(onionAddress)
                if (currentConnId != null && introducedConnectionIds[onionAddress] != currentConnId) {
                    peersIntroducedOnConnection.remove(onionAddress)
                }
                if (peersIntroducedOnConnection.add(onionAddress)) {
                    val myInfo = contactManager.getMyContactInfo()
                    if (myInfo.onionAddress.isNotBlank() && !myInfo.requiresRendezvous()) {
                        val introFrame = OnionMessageProtocol.createContactExchangeFrame(
                            myOnionAddress = myInfo.onionAddress,
                            myNickname = myInfo.nickname,
                            myPublicKey = myInfo.publicKey,
                            myAvatarId = myInfo.avatarId,
                            sequence = getSequenceManager(onionAddress).nextOutbound()
                        )
                        if (circuitManager.sendData(onionAddress, introFrame.encode())) {
                            currentConnId?.let { introducedConnectionIds[onionAddress] = it }
                        } else {
                            peersIntroducedOnConnection.remove(onionAddress)
                        }
                    } else {
                        peersIntroducedOnConnection.remove(onionAddress)
                    }
                }
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

        val startMs = System.currentTimeMillis()
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

                if (System.currentTimeMillis() - startMs >= 6_000L && phantomLanBridge.hasActiveLanInterface()) {
                    Log.i(TAG, "⚡ Local Wi-Fi active — starting Phantom-LAN messaging immediately while Tor bootstraps in background")
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

    /**
     * Proactively prefetches v3 onion descriptors and warms rendezvous circuits for
     * top active contacts while the user is browsing Contacts/Groups so sends & calls start at 0ms.
     */
    fun prewarmActiveContacts(maxPeers: Int = 6) {
        if (_serviceState.value != ServiceState.RUNNING) return
        scope.launch {
            runCatching { prefetchKnownContactDescriptors() }
            val now = System.currentTimeMillis()
            val topPeers = contactManager.getAllContacts()
                .asSequence()
                .filter { !it.requiresRendezvous() }
                .filter {
                    it.effectiveTrustState() != TorContactTrustState.BLOCKED &&
                        it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED
                }
                .sortedWith(
                    compareByDescending<OnionContactManager.OnionContact> { it.isFavorite }
                        .thenByDescending { it.lastSeenOnline }
                )
                .take(maxPeers)
                .toList()
            for (contact in topPeers) {
                val canonical = canonicalPeerKey(contact.onionAddress)
                if (circuitManager.hasActiveConnection(canonical)) continue
                val lastTry = lastPeerDialAttempt[canonical] ?: 0L
                if (now - lastTry < 20_000L) continue
                lastPeerDialAttempt[canonical] = now
                scope.launch { pingPeer(canonical) }
            }
        }
    }

    /**
     * Send an encrypted real-time call signaling frame (OFFER, RINGING, ACCEPT, DECLINE, HANGUP)
     * directly over the Tor peer circuit without storing in offline mailboxes.
     */
    suspend fun sendCallSignalFrame(recipientOnion: String, payload: ByteArray): Boolean {
        return try {
            val canonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(recipientOnion)
                .ifBlank { recipientOnion.trim() }
            val connection = circuitManager.getConnectionWithin(canonical, 45_000L) ?: return false
            ensureReadLoop(canonical)
            val currentConnId = circuitManager.getActiveConnectionId(canonical)
            if (currentConnId != null && introducedConnectionIds[canonical] != currentConnId) {
                peersIntroducedOnConnection.remove(canonical)
            }
            if (peersIntroducedOnConnection.add(canonical)) {
                val myInfo = contactManager.getMyContactInfo()
                if (myInfo.onionAddress.isNotBlank() && !myInfo.requiresRendezvous()) {
                    val introFrame = OnionMessageProtocol.createContactExchangeFrame(
                        myOnionAddress = myInfo.onionAddress,
                        myNickname = myInfo.nickname,
                        myPublicKey = myInfo.publicKey,
                        myAvatarId = myInfo.avatarId,
                        sequence = getSequenceManager(canonical).nextOutbound()
                    )
                    if (circuitManager.sendData(canonical, introFrame.encode())) {
                        currentConnId?.let { introducedConnectionIds[canonical] = it }
                    } else {
                        peersIntroducedOnConnection.remove(canonical)
                    }
                } else {
                    peersIntroducedOnConnection.remove(canonical)
                }
            }
            val frame = OnionMessageProtocol.OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = getSequenceManager(canonical).nextOutbound(),
                timestamp = System.currentTimeMillis(),
                frameType = OnionMessageProtocol.FrameType.CALL_SIGNAL,
                payload = payload
            )
            val sent = circuitManager.sendData(canonical, encryptFrame(canonical, frame).encode())
            if (sent) ensureReadLoop(canonical)
            sent
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send CALL_SIGNAL to ${redactEndpoint(recipientOnion)}: ${e.message}")
            false
        }
    }

    private val lastAudioReconnectTriggerAt = ConcurrentHashMap<String, Long>()

    /**
     * Send a compact real-time voice frame (fits inside 1 Tor cell, payload already
     * AES-256-GCM encrypted in RAM with the call's ephemeral 256-bit session key).
     */
    suspend fun sendCallAudioFrame(
        recipientOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ): Boolean {
        return try {
            val canonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(recipientOnion)
                .ifBlank { recipientOnion.trim() }
            if (!circuitManager.hasActiveConnection(canonical)) {
                val now = System.currentTimeMillis()
                val lastTry = lastAudioReconnectTriggerAt[canonical] ?: 0L
                if (now - lastTry >= 2_500L) {
                    lastAudioReconnectTriggerAt[canonical] = now
                    scope.launch {
                        if (circuitManager.getConnectionWithin(canonical, 15_000L) != null) {
                            ensureReadLoop(canonical)
                        }
                    }
                }
                return false
            }
            ensureReadLoop(canonical)
            val frame = OnionMessageProtocol.OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = sequence,
                timestamp = timestampMs,
                frameType = OnionMessageProtocol.FrameType.CALL_AUDIO,
                payload = payload
            )
            circuitManager.sendDataIfConnected(canonical, frame.encode(padToFixedSize = false))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send CALL_AUDIO to ${redactEndpoint(recipientOnion)}: ${e.message}")
            false
        }
    }

    /**
     * Send an unpadded real-time encrypted video fragment frame over the Tor peer circuit.
     */
    suspend fun sendCallVideoFrame(
        recipientOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray
    ): Boolean {
        return try {
            val canonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(recipientOnion)
                .ifBlank { recipientOnion.trim() }
            if (!circuitManager.hasActiveConnection(canonical)) {
                return false
            }
            ensureReadLoop(canonical)
            val frame = OnionMessageProtocol.OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = sequence,
                timestamp = timestampMs,
                frameType = OnionMessageProtocol.FrameType.CALL_VIDEO,
                payload = payload
            )
            circuitManager.sendDataIfConnected(canonical, frame.encode(padToFixedSize = false))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send CALL_VIDEO to ${redactEndpoint(recipientOnion)}: ${e.message}")
            false
        }
    }

    /**
     * Send an encrypted voice FEC parity frame over Lane 1 (BULK_MEDIA) when available,
     * falling back to Lane 0 so primary voice and 2D parity traverse independent Tor paths.
     */
    suspend fun sendCallAudioFecFrame(
        recipientOnion: String,
        sequence: Long,
        timestampMs: Long,
        payload: ByteArray,
        preferBulkLane: Boolean = true
    ): Boolean {
        return try {
            val canonical = canonicalPeerKey(recipientOnion)
            if (preferBulkLane && circuitManager.hasActiveBulkConnection(canonical)) {
                ensureBulkReadLoop(canonical)
                val frame = OnionMessageProtocol.OnionFrame(
                    messageId = UUID.randomUUID(),
                    sequence = sequence,
                    timestamp = timestampMs,
                    frameType = OnionMessageProtocol.FrameType.CALL_AUDIO,
                    payload = payload
                )
                if (circuitManager.sendBulkDataIfConnected(canonical, frame.encode(padToFixedSize = false))) {
                    return true
                }
            }
            sendCallAudioFrame(recipientOnion, sequence, timestampMs, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send CALL_AUDIO FEC to ${redactEndpoint(recipientOnion)}: ${e.message}")
            false
        }
    }

    /**
     * Burst-flush up to a small slice of real-time CALL_VIDEO fragments over Lane 1 (BULK_MEDIA)
     * or Lane 0 while warming Lane 1 in the background.
     */
    suspend fun sendCallVideoFramesBurst(
        recipientOnion: String,
        sequence: Long,
        timestampMs: Long,
        payloads: List<ByteArray>
    ): Boolean {
        if (payloads.isEmpty()) return true
        return try {
            val canonical = canonicalPeerKey(recipientOnion)
            val wireFrames = payloads.map { payload ->
                OnionMessageProtocol.OnionFrame(
                    messageId = UUID.randomUUID(),
                    sequence = sequence,
                    timestamp = timestampMs,
                    frameType = OnionMessageProtocol.FrameType.CALL_VIDEO,
                    payload = payload
                ).encode(padToFixedSize = false)
            }
            if (circuitManager.hasActiveBulkConnection(canonical)) {
                ensureBulkReadLoop(canonical)
                return circuitManager.sendBulkFramesBurst(canonical, wireFrames, allowPrimaryFallback = false)
            }
            circuitManager.ensureBulkConnectionAsync(canonical)
            if (!circuitManager.hasActiveConnection(canonical)) {
                return false
            }
            ensureReadLoop(canonical)
            circuitManager.sendFramesBurstIfConnected(canonical, wireFrames)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send CALL_VIDEO burst to ${redactEndpoint(recipientOnion)}: ${e.message}")
            false
        }
    }

    /** Sends a short-lived direct presence hint and speculatively warms circuits on compose. */
    fun setTyping(contactId: String, isTyping: Boolean) {
        val contact = contactManager.getContact(contactId) ?: return
        val onion = contact.onionAddress
        if (contact.requiresRendezvous()) return
        val now = System.currentTimeMillis()
        if (circuitManager.getConnectionState(onion) != TorCircuitManager.ConnectionState.CONNECTED) {
            val canonical = canonicalPeerKey(onion)
            if (isTyping && now - (lastSpeculativeTypingDialAt[canonical] ?: 0L) >= 15_000L) {
                lastSpeculativeTypingDialAt[canonical] = now
                scope.launch { pingPeer(onion) }
            }
            return
        }
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
            circuitManager.sendDataIfConnected(onion, encryptFrame(onion, frame).encode())
        }
    }

    /** Chrono-Sense speculative warm-up when user opens Photo picker or media flow. */
    fun speculativePrewarmBulk(contactId: String) {
        val contact = contactManager.getContact(contactId) ?: return
        if (contact.requiresRendezvous() ||
            contact.effectiveTrustState() == TorContactTrustState.BLOCKED ||
            contact.effectiveTrustState() == TorContactTrustState.KEY_CHANGED
        ) {
            return
        }
        val canonical = canonicalPeerKey(contact.onionAddress)
        val now = System.currentTimeMillis()
        if (now - (lastSpeculativeBulkDialAt[canonical] ?: 0L) < 20_000L) return
        lastSpeculativeBulkDialAt[canonical] = now
        scope.launch {
            if (!circuitManager.hasActiveConnection(contact.onionAddress)) {
                pingPeer(contact.onionAddress)
            }
            if (contact.publicKey.isNotBlank()) {
                circuitManager.ensureBulkConnectionAsync(contact.onionAddress)
            }
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
    private fun readFirstIncomingFrame(input: java.io.DataInputStream): ByteArray? {
        val header = ByteArray(4)
        var headerRead = 0
        var stalls = 0
        while (headerRead < 4) {
            try {
                val n = input.read(header, headerRead, 4 - headerRead)
                if (n < 0) return null
                headerRead += n
            } catch (e: java.net.SocketTimeoutException) {
                if (headerRead == 0 || ++stalls > 3) throw e
            }
        }
        val size = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        if (size <= 0 || size > OnionMessageProtocol.FRAME_SIZE * 2) {
            Log.w(TAG, "Invalid incoming frame size: $size")
            return null
        }
        val data = ByteArray(size)
        var bodyRead = 0
        stalls = 0
        while (bodyRead < size) {
            try {
                val n = input.read(data, bodyRead, size - bodyRead)
                if (n < 0) return null
                bodyRead += n
            } catch (e: java.net.SocketTimeoutException) {
                if (++stalls > 3) throw e
            }
        }
        return data
    }

    private suspend fun handleIncomingSocket(socket: java.net.Socket) {
        try {
            socket.soTimeout = TorCircuitManager.READ_TIMEOUT_MS
            val input = java.io.DataInputStream(socket.inputStream)

            val data = readFirstIncomingFrame(input)
            if (data == null) {
                socket.close()
                return
            }
            
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
                    // CONTACT_EXCHANGE is only allowed to supply a missing onion or create a new contact;
                    // never allow an unauthenticated frame to hijack a pinned contact's socket/route.
                    val exchange = OnionMessageProtocol.ContactExchange.fromBytes(frame.payload)
                    if (exchange != null) {
                        val existingContact = contactManager.getContactByPublicKey(exchange.publicKey)
                        var peerOnion = exchange.onionAddress
                        val isPlaceholder = peerOnion.isBlank() ||
                            peerOnion.equals("not-available.onion:18888", ignoreCase = true)
                        if (!isPlaceholder) {
                            val canonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerOnion)
                            if (canonical.isNotBlank()) peerOnion = canonical
                        }
                        if (existingContact != null && !existingContact.requiresRendezvous()) {
                            val pinnedOnion = OnionContactManager.OnionContact
                                .canonicalizeOnionEndpoint(existingContact.onionAddress)
                            if (isPlaceholder) {
                                peerOnion = pinnedOnion
                            } else if (!peerOnion.equals(pinnedOnion, ignoreCase = true)) {
                                Log.w(TAG, "Blocked unauthenticated CONTACT_EXCHANGE route hijack for ${existingContact.nickname}")
                                socket.close()
                                return
                            }
                        } else if (isPlaceholder) {
                            peerOnion = "pubkey:${exchange.publicKey.take(32)}"
                            Log.w(TAG, "📥 Sender has no onion address, using pubkey-based ID: ${peerOnion.take(20)}")
                        }
                        resolvedPeerOnion = peerOnion
                        val accepted = circuitManager.addIncomingConnection(
                            peerOnion,
                            socket,
                            shouldPreferIncomingCircuit(peerOnion)
                        )
                        if (accepted) {
                            circuitManager.getActiveConnectionId(peerOnion)?.let { connId ->
                                introducedConnectionIds[peerOnion] = connId
                                peersIntroducedOnConnection.add(peerOnion)
                            }
                            messageQueue.getPendingMessages(peerOnion)
                                .filter {
                                    OnionMessageProtocol.OnionFrame.decode(it.frameData)?.frameType ==
                                        OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
                                }
                                .forEach { messageQueue.markDelivered(it.id) }
                        }
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
                        val canonicalPeer = OnionContactManager.OnionContact
                            .canonicalizeOnionEndpoint(identified.first)
                            .ifBlank { identified.first }
                        resolvedPeerOnion = canonicalPeer
                        if (frame.frameType == OnionMessageProtocol.FrameType.BULK_HELLO) {
                            val bulkAccepted = circuitManager.addIncomingBulkConnection(canonicalPeer, socket)
                            if (bulkAccepted) {
                                markPeerResponsive(canonicalPeer)
                                ensureBulkReadLoop(canonicalPeer)
                                wakeQueueDispatcher()
                            }
                            return
                        }
                        val accepted = circuitManager.addIncomingConnection(
                            canonicalPeer,
                            socket,
                            shouldPreferIncomingCircuit(canonicalPeer)
                        )
                        if (accepted) {
                            circuitManager.getActiveConnectionId(canonicalPeer)?.let { connId ->
                                introducedConnectionIds[canonicalPeer] = connId
                                peersIntroducedOnConnection.add(canonicalPeer)
                            }
                            messageQueue.getPendingMessages(canonicalPeer)
                                .filter {
                                    OnionMessageProtocol.OnionFrame.decode(it.frameData)?.frameType ==
                                        OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
                                }
                                .forEach { messageQueue.markDelivered(it.id) }
                        }
                        Log.i(TAG, "📥 Incoming connection from ${canonicalPeer.take(20)}... (identified via decryption)")
                        handleIncomingData(canonicalPeer, identified.second, isAlreadyDecrypted = true)
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
            // so we can receive subsequent messages on this same socket, and wake the
            // outbound delivery loop immediately so queued frames flush right away.
            resolvedPeerOnion?.let { peer ->
                ensureReadLoop(peer)
                wakeQueueDispatcher()
            }
            
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
                        if (data.size >= OnionMessageProtocol.HEADER_SIZE &&
                            (data[33] == OnionMessageProtocol.FrameType.FILE_CHUNK.code ||
                                data[33] == OnionMessageProtocol.FrameType.FILE_PARITY.code ||
                                data[33] == OnionMessageProtocol.FrameType.SYNC_BATCH.code)
                        ) {
                            enqueueBulkInboundFrame(peerOnion, data)
                        } else {
                            handleIncomingData(peerOnion, data, isAlreadyDecrypted = false)
                        }
                    } else if (circuitManager.getActiveConnectionId(peerOnion) != connectionId ||
                        circuitManager.getConnectionState(peerOnion) != TorCircuitManager.ConnectionState.CONNECTED
                    ) {
                        Log.d(TAG, "📡 Connection no longer active for ${redactEndpoint(peerOnion)}")
                        break
                    } else {
                        // No data available (timeout or no connection) — small delay to avoid spinning
                        delay(10)
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
            refreshLanPeersState()
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
    
    private fun healPeerCircuit(onion: String, reconnectFirst: Boolean) {
        scope.launch {
            if (reconnectFirst && circuitManager.warmReconnect(onion) != null) {
                ensureReadLoop(onion)
            }
            pingPeer(onion)
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    // A connected TCP socket is not proof that a Tor peer is
                    // reachable. Only show a contact online after we have seen
                    // valid encrypted traffic from it recently.
                    val peers = circuitManager.getConnectedPeers()
                    val peerSet = peers.mapTo(HashSet()) { canonicalPeerKey(it) }
                    val cutoff = now - PEER_PRESENCE_TIMEOUT_MS
                    val verifiedPeers = peers.filter { peer ->
                        val canonical = canonicalPeerKey(peer)
                        val lastActive = maxOf(
                            lastVerifiedPeerActivity[canonical] ?: 0L,
                            lastVerifiedPeerActivity[peer] ?: 0L
                        )
                        lastActive >= cutoff
                    }.toSet()
                    _connectedPeers.value = verifiedPeers
                    refreshLanPeersState()
                    _typingPeers.update { active ->
                        active.filterValues { expiresAt -> expiresAt > now }
                    }

                    // Traffic-aware keepalives: skip PINGs while messages/ACKs are actively flowing,
                    // respect a 35s grace window on new circuits, and warm-reconnect only after 45s silence.
                    for (onion in peers) {
                        val canonical = canonicalPeerKey(onion)
                        val lastActive = maxOf(
                            lastVerifiedPeerActivity[canonical] ?: 0L,
                            lastVerifiedPeerActivity[onion] ?: 0L
                        )
                        val silenceMs = now - lastActive
                        val connectedAt = circuitManager.getConnectionConnectedAt(canonical)
                        val circuitAgeMs = if (connectedAt > 0L) now - connectedAt else Long.MAX_VALUE
                        if (silenceMs >= 8_000L) {
                            val shouldReconnect = circuitAgeMs >= 35_000L && silenceMs >= 45_000L
                            healPeerCircuit(canonical, reconnectFirst = shouldReconnect)
                        }
                    }

                    // Proactively self-heal visibly open chats if their circuit is absent/silent
                    val visibleOnions = visibleChatContacts.mapNotNull { id ->
                        contactManager.getContact(id)
                            ?.takeIf {
                                !it.requiresRendezvous() &&
                                    it.effectiveTrustState() != TorContactTrustState.BLOCKED &&
                                    it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED
                            }
                            ?.onionAddress
                            ?.let(::canonicalPeerKey)
                    }.distinct()
                    for (onion in visibleOnions) {
                        if (onion in peerSet) continue
                        val silenceMs = now - (lastVerifiedPeerActivity[onion] ?: 0L)
                        val lastDial = lastPeerDialAttempt[onion] ?: 0L
                        if (silenceMs >= 45_000L && now - lastDial >= 15_000L) {
                            lastPeerDialAttempt[onion] = now
                            healPeerCircuit(onion, reconnectFirst = true)
                        }
                    }

                    reconnectKnownContacts()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat loop: ${e.message}")
                }
                delay(TorCircuitManager.KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    private fun launchRecipientDelivery(
        recipientOnion: String,
        preloadedMessages: List<OnionMessageQueue.QueuedMessage>? = null
    ) {
        if (preloadedMessages != null && preloadedMessages.isEmpty()) return
        if (!activeRecipientDeliveries.add(recipientOnion)) {
            wakeQueueDispatcher()
            return
        }
        scope.launch {
            val lock = recipientDeliveryLocks.getOrPut(recipientOnion) { Mutex() }
            try {
                lock.withLock {
                    val messages = preloadedMessages ?: messageQueue.getPendingMessages(recipientOnion)
                    if (messages.isNotEmpty()) {
                        deliverPendingForRecipient(recipientOnion, messages)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error delivering to ${redactEndpoint(recipientOnion)}: ${e.message}")
            } finally {
                activeRecipientDeliveries.remove(recipientOnion)
                // If new messages were queued or ACKs opened attachment window
                // slots while this worker was running, wake the dispatcher.
                if (messageQueue.getPendingMessages(recipientOnion).isNotEmpty()) {
                    wakeQueueDispatcher()
                }
            }
        }
    }

    private fun dispatchRecipientNow(recipientOnion: String) {
        launchRecipientDelivery(recipientOnion, preloadedMessages = null)
    }

    /**
     * Queue delivery loop - dispatches pending outbound messages concurrently per recipient.
     * Already-connected peers are prioritized and never wait behind an offline peer's
     * 25-second initial onion dial or mailbox fallback.
     */
    private fun startQueueDeliveryLoop() {
        queueDeliveryJob?.cancel()
        // Flush queued frames and start read loop the moment any connection becomes usable.
        connectionEventsJob?.cancel()
        connectionEventsJob = scope.launch {
            circuitManager.connectionEvents.collect { peer ->
                ensureReadLoop(peer)
                if (circuitManager.hasActiveBulkConnection(peer)) {
                    ensureBulkReadLoop(peer)
                }
                wakeQueueDispatcher()
            }
        }
        queueDeliveryJob = scope.launch {
            while (isActive) {
                try {
                    val pending = messageQueue.getAllPendingMessages()
                    val sortedRecipients = pending.entries.sortedByDescending { (recipientOnion, _) ->
                        circuitManager.hasActiveConnection(recipientOnion)
                    }
                    for ((recipientOnion, messages) in sortedRecipients) {
                        launchRecipientDelivery(recipientOnion, messages)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in queue delivery loop: ${e.message}")
                }
                withTimeoutOrNull(400L) { queueWakeChannel.receive() }
            }
        }
    }

    private suspend fun deliverPendingForRecipient(
        recipientOnion: String,
        messages: List<OnionMessageQueue.QueuedMessage>
    ) {
        // Preserve a healthy circuit through a small number of
        // missed ACKs. Recreating an onion circuit on every retry
        // produced avoidable presence flapping and 20-60 second
        // delivery delays. A genuinely stalled circuit is replaced
        // via make-before-break after repeated unacked retransmissions.
        val activeConnId = circuitManager.getActiveConnectionId(recipientOnion)
        val introducedActive = activeConnId != null &&
            introducedConnectionIds[recipientOnion] == activeConnId &&
            peersIntroducedOnConnection.contains(recipientOnion)
        val stuckCandidates = if (introducedActive) {
            messages.filterNot { queued ->
                OnionMessageProtocol.OnionFrame.decode(queued.frameData)?.frameType ==
                    OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
            }
        } else {
            messages
        }
        val hasStuckSent = stuckCandidates.any {
            it.state == OnionMessageProtocol.DeliveryState.SENT &&
                it.retryCount >= UNACKED_RETRIES_BEFORE_RECONNECT
        }
        val canonicalRecipient = canonicalPeerKey(recipientOnion)
        val lastInbound = maxOf(
            lastVerifiedPeerActivity[canonicalRecipient] ?: 0L,
            lastVerifiedPeerActivity[recipientOnion] ?: 0L
        )
        val peerRecentlyActive = (System.currentTimeMillis() - lastInbound) <= 30_000L
        if (hasStuckSent && !peerRecentlyActive && circuitManager.hasActiveConnection(recipientOnion)) {
            val replaced = circuitManager.warmReconnect(recipientOnion)
            if (replaced != null) {
                introducedConnectionIds.remove(recipientOnion)
                peersIntroducedOnConnection.remove(recipientOnion)
                readLoopConnections.remove(recipientOnion)
                peersWithReadLoop.remove(recipientOnion)
                ensureReadLoop(recipientOnion)
            }
        }

        // Give a first direct onion dial a bounded head start. The dial keeps
        // running in the background after this budget, so a slow cold rendezvous
        // still completes for the next cycle while this message falls through to
        // the encrypted mailbox instead of freezing the whole recipient queue.
        val connection = try {
            circuitManager.getConnectionWithin(recipientOnion, INITIAL_DIRECT_DIAL_BUDGET_MS)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not connect to ${redactEndpoint(recipientOnion)}: ${e.message}")
            null
        }
        
        if (connection == null) {
            Log.d(TAG, "📭 No connection to ${redactEndpoint(recipientOnion)} — trying mailbox fallback")
            for (message in messages) {
                // The mailbox accepted this exact encrypted frame already.
                // Re-uploading it on every ACK retry creates duplicate blobs
                // and can exhaust the recipient's bounded mailbox while they
                // are offline. Keep the durable copy and retry direct/ACK
                // delivery on the normal backoff schedule instead.
                if (message.mailboxStored) {
                    messageQueue.deferAcknowledgementRetry(message.id)
                    continue
                }
                val delivered = tryMailboxDelivery(recipientOnion, message)
                if (!delivered) {
                    messageQueue.handleSendFailure(
                        message.id,
                        "Direct connection unavailable and mailbox unavailable"
                    )
                } else {
                    groupMessaging.onTransportSent(message.id)
                }
            }
            return
        }

        // Start consuming the return stream before the first
        // write. Tor peers can ACK immediately after a durable
        // receive; registering the reader afterwards left a
        // race in which receipts and reply messages could wait
        // behind subsequent outbound queue work.
        ensureReadLoop(recipientOnion)
        
        // ━━━ CRITICAL: Send CONTACT_EXCHANGE on first use of new connection ━━━
        // This ensures the remote hidden service can identify who connected
        // WITHOUT destructive ratchet trial decryption (which corrupts sessions).
        // The introduction is tracked per connection id: after any reconnect the
        // new socket must be introduced again, otherwise the remote side drops it
        // as an unidentified inbound connection.
        val currentConnectionId = circuitManager.getActiveConnectionId(recipientOnion)
        if (currentConnectionId != null && introducedConnectionIds[recipientOnion] != currentConnectionId) {
            peersIntroducedOnConnection.remove(recipientOnion)
        }
        val queuedExchangeFrame = messages.firstNotNullOfOrNull { queued ->
            OnionMessageProtocol.OnionFrame.decode(queued.frameData)?.takeIf {
                it.frameType == OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
            }
        }
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
                // If our hidden service hasn't published yet, record pending intro
                // and let the next queue cycle (or HS readiness observer) retry.
                if (myInfo.onionAddress.isBlank() || myInfo.requiresRendezvous()) {
                    Log.w(TAG, "⏳ Skipping auto CONTACT_EXCHANGE — our hidden service is not published yet")
                    pendingContactExchanges.add(recipientOnion)
                    peersIntroducedOnConnection.remove(recipientOnion) // allow retry next cycle
                } else {
                    val seqManager = getSequenceManager(recipientOnion)
                    val baseIntroFrame = OnionMessageProtocol.createContactExchangeFrame(
                        myOnionAddress = myInfo.onionAddress,
                        myNickname = myInfo.nickname,
                        myPublicKey = myInfo.publicKey,
                        myAvatarId = myInfo.avatarId,
                        sequence = seqManager.nextOutbound()
                    )
                    val introFrame = if (queuedExchangeFrame != null) {
                        baseIntroFrame.copy(messageId = queuedExchangeFrame.messageId)
                    } else {
                        baseIntroFrame
                    }
                    introductionReady = circuitManager.sendData(recipientOnion, introFrame.encode())
                    if (introductionReady) {
                        currentConnectionId?.let { introducedConnectionIds[recipientOnion] = it }
                        Log.i(TAG, "📤 Auto-sent CONTACT_EXCHANGE on new outbound connection to ${redactEndpoint(recipientOnion)}")
                        scope.launch {
                            delay(1500)
                            sendMailboxExchange(recipientOnion)
                        }
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
            return
        }

        val isCallActive = com.seamless.android.call.SeamlessCallManager
            .getInstance(context)
            .isCallActiveForPeer(recipientOnion)

        var idx = 0
        while (idx < messages.size) {
            val firstMsg = messages[idx]
            val isBulkFrame = firstMsg.frameData.size >= OnionMessageProtocol.HEADER_SIZE &&
                (firstMsg.frameData[33] == OnionMessageProtocol.FrameType.FILE_CHUNK.code ||
                    firstMsg.frameData[33] == OnionMessageProtocol.FrameType.FILE_PARITY.code ||
                    firstMsg.frameData[33] == OnionMessageProtocol.FrameType.SYNC_BATCH.code)
            if (isBulkFrame) {
                if (isCallActive && !circuitManager.hasActiveBulkConnection(recipientOnion)) {
                    circuitManager.ensureBulkConnectionAsync(recipientOnion)
                    idx++
                    continue
                }
                val maxSlice = if (isCallActive) 8 else OnionMessageQueue.ATTACHMENT_DISPATCH_SLICE
                val slice = ArrayList<OnionMessageQueue.QueuedMessage>(maxSlice)
                while (idx < messages.size && slice.size < maxSlice) {
                    val m = messages[idx]
                    val mBulk = m.frameData.size >= OnionMessageProtocol.HEADER_SIZE &&
                        (m.frameData[33] == OnionMessageProtocol.FrameType.FILE_CHUNK.code ||
                            m.frameData[33] == OnionMessageProtocol.FrameType.FILE_PARITY.code ||
                            m.frameData[33] == OnionMessageProtocol.FrameType.SYNC_BATCH.code)
                    if (!mBulk) break
                    slice.add(m)
                    idx++
                }
                try {
                    val sliceIds = slice.map { it.id }
                    messageQueue.markSendingBatch(sliceIds)
                    for (m in slice) {
                        if (m.completesUiMessage) {
                            messageRepository.updateDeliveryState(
                                m.uiMessageId,
                                OnionMessageProtocol.DeliveryState.SENDING
                            )
                        }
                    }
                    val wireBurst = slice.map { prepareDirectFrameForDelivery(recipientOnion, it) }
                    val burstOk = circuitManager.sendBulkFramesBurst(
                        onionAddress = recipientOnion,
                        frames = wireBurst,
                        allowPrimaryFallback = !isCallActive
                    )
                    if (circuitManager.hasActiveBulkConnection(recipientOnion)) {
                        ensureBulkReadLoop(recipientOnion)
                    }
                    if (burstOk) {
                        messageQueue.markSentBatch(sliceIds)
                        for (m in slice) {
                            deliveryRoutes[m.uiMessageId] = TorDeliveryRoute.DIRECT
                            groupMessaging.onTransportSent(m.id)
                            if (m.completesUiMessage) {
                                messageRepository.updateDelivery(
                                    m.uiMessageId,
                                    OnionMessageProtocol.DeliveryState.SENT,
                                    OnionMessageProtocol.DeliveryRoute.DIRECT
                                )
                            }
                        }
                        Log.i(TAG, "⚡ Delivered bulk burst (${slice.size} frames) to ${redactEndpoint(recipientOnion)}")
                    } else if (!isCallActive) {
                        peersIntroducedOnConnection.remove(recipientOnion)
                        for (m in slice) {
                            if (m.mailboxStored) {
                                messageQueue.deferAcknowledgementRetry(m.id)
                                continue
                            }
                            val delivered = tryMailboxDelivery(recipientOnion, m)
                            if (!delivered) {
                                messageQueue.handleSendFailure(m.id, "Direct + Mailbox both failed")
                            } else {
                                groupMessaging.onTransportSent(m.id)
                            }
                        }
                    } else {
                        for (m in slice) {
                            messageQueue.handleSendFailure(m.id, "Bulk lane paused during active call")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Bulk burst send failed for ${redactEndpoint(recipientOnion)}: ${e.message}")
                    peersIntroducedOnConnection.remove(recipientOnion)
                    if (!isCallActive) {
                        for (m in slice) {
                            if (m.mailboxStored) {
                                messageQueue.deferAcknowledgementRetry(m.id)
                                continue
                            }
                            val delivered = tryMailboxDelivery(recipientOnion, m)
                            if (!delivered) {
                                messageQueue.handleSendFailure(m.id, e.message ?: "Unknown error")
                            } else {
                                groupMessaging.onTransportSent(m.id)
                            }
                        }
                    } else {
                        for (m in slice) {
                            messageQueue.handleSendFailure(m.id, e.message ?: "Bulk lane paused during active call")
                        }
                    }
                }
                continue
            }

            val message = messages[idx++]
            try {
                if (introducedOnThisConnection && introductionReady &&
                    OnionMessageProtocol.OnionFrame.decode(message.frameData)?.frameType ==
                    OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
                ) {
                    // Keep awaiting remote ACK or reverse CONTACT_EXCHANGE instead of
                    // dropping the pairing frame immediately after writing to local SOCKS.
                    messageQueue.markSent(message.id)
                    continue
                }
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
                    groupMessaging.onTransportSent(message.id)
                    if (message.completesUiMessage) {
                        messageRepository.updateDelivery(
                            message.uiMessageId,
                            OnionMessageProtocol.DeliveryState.SENT,
                            OnionMessageProtocol.DeliveryRoute.DIRECT
                        )
                    }
                    Log.i(TAG, "📤 Delivered queued message ${message.id} to ${redactEndpoint(recipientOnion)} (DIRECT)")
                } else {
                    // Direct send failed even with connection — try mailbox
                    peersIntroducedOnConnection.remove(recipientOnion)
                    if (message.mailboxStored) {
                        messageQueue.deferAcknowledgementRetry(message.id)
                        continue
                    }
                    val delivered = tryMailboxDelivery(recipientOnion, message)
                    if (!delivered) {
                        messageQueue.handleSendFailure(message.id, "Direct + Mailbox both failed")
                    } else {
                        groupMessaging.onTransportSent(message.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Send failed for ${message.id}: ${e.message}")
                peersIntroducedOnConnection.remove(recipientOnion)
                val delivered = tryMailboxDelivery(recipientOnion, message)
                if (!delivered) {
                    messageQueue.handleSendFailure(message.id, e.message ?: "Unknown error")
                } else {
                    groupMessaging.onTransportSent(message.id)
                }
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
        circuitManager.getConnectedBulkPeers()
            .toList()
            .forEach(::ensureBulkReadLoop)
    }

    private val peerInboundLocks = ConcurrentHashMap<String, Mutex>()
    private val directTransportKeyCache = ConcurrentHashMap<String, ByteArray>()
    @Volatile private var cachedLocalStaticKeyPair: Pair<ByteArray, ByteArray>? = null
    
    private suspend fun enqueueBulkInboundFrame(peerOnion: String, data: ByteArray) {
        val canonical = canonicalPeerKey(peerOnion)
        var channel = peerBulkChannels[canonical]
        if (channel == null) {
            val created = Channel<ByteArray>(capacity = 32)
            val existing = peerBulkChannels.putIfAbsent(canonical, created)
            channel = existing ?: created.also { ch ->
                peerBulkWorkers[canonical] = scope.launch {
                    for (frameData in ch) {
                        try {
                            handleIncomingData(canonical, frameData, isAlreadyDecrypted = false)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            Log.w(TAG, "Bulk inbound frame error from ${redactEndpoint(canonical)}: ${e.message}")
                        }
                    }
                }
            }
        }
        if (!channel.trySend(data).isSuccess) {
            channel.send(data)
        }
    }

    private suspend fun handleIncomingCallSignalFast(
        senderOnion: String,
        data: ByteArray,
        isAlreadyDecrypted: Boolean,
        allowOfflineTimestamp: Boolean
    ): Boolean {
        return try {
            val frame = if (isAlreadyDecrypted) {
                OnionMessageProtocol.OnionFrame.decode(data) ?: return false
            } else {
                decryptFrame(senderOnion, data)
            }
            if (!OnionMessageProtocol.isMessageFresh(frame, allowOfflineWindow = allowOfflineTimestamp)) {
                Log.w(TAG, "Received stale CALL_SIGNAL from ${redactEndpoint(senderOnion)}")
                return false
            }
            if (deduplicator.isDuplicate(frame.messageId)) {
                return true
            }
            markPeerResponsive(senderOnion)
            Log.i(TAG, "📞 Received CALL_SIGNAL from ${redactEndpoint(senderOnion)}")
            com.seamless.android.call.SeamlessCallManager.getInstance(context)
                .onIncomingTorCallSignal(senderOnion, frame.payload)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed fast-path CALL_SIGNAL from ${redactEndpoint(senderOnion)}: ${e.message}")
            false
        }
    }

    private suspend fun handleIncomingData(
        senderOnion: String,
        data: ByteArray,
        isAlreadyDecrypted: Boolean = false,
        allowOfflineTimestamp: Boolean = false
    ): Boolean {
        if (data.size >= OnionMessageProtocol.HEADER_SIZE) {
            val fastCode = data[33]
            if (fastCode == OnionMessageProtocol.FrameType.CALL_AUDIO.code ||
                fastCode == OnionMessageProtocol.FrameType.CALL_VIDEO.code
            ) {
                val fastFrame = OnionMessageProtocol.OnionFrame.decode(data)
                if (fastFrame != null &&
                    (fastFrame.frameType == OnionMessageProtocol.FrameType.CALL_AUDIO ||
                        fastFrame.frameType == OnionMessageProtocol.FrameType.CALL_VIDEO)
                ) {
                    markPeerResponsive(senderOnion)
                    val mediaPayload = if (!isAlreadyDecrypted && isDirectSealedPayload(fastFrame.payload)) {
                        runCatching { decryptFrame(senderOnion, data).payload }.getOrNull() ?: return false
                    } else {
                        fastFrame.payload
                    }
                    val cm = com.seamless.android.call.SeamlessCallManager.getInstance(context)
                    if (fastFrame.frameType == OnionMessageProtocol.FrameType.CALL_VIDEO) {
                        cm.onIncomingTorCallVideo(senderOnion, fastFrame.sequence, fastFrame.timestamp, mediaPayload)
                    } else {
                        cm.onIncomingTorCallAudio(senderOnion, fastFrame.sequence, fastFrame.timestamp, mediaPayload)
                    }
                    return true
                }
            }
            if (fastCode == OnionMessageProtocol.FrameType.CALL_SIGNAL.code) {
                return handleIncomingCallSignalFast(senderOnion, data, isAlreadyDecrypted, allowOfflineTimestamp)
            }
        }
        val lockKey = canonicalPeerKey(senderOnion)
        val peerLock = peerInboundLocks.getOrPut(lockKey) { Mutex() }
        return peerLock.withLock {
            handleIncomingDataLocked(senderOnion, data, isAlreadyDecrypted, allowOfflineTimestamp)
        }
    }

    private suspend fun handleIncomingDataLocked(
        senderOnion: String,
        data: ByteArray,
        isAlreadyDecrypted: Boolean = false,
        allowOfflineTimestamp: Boolean = false
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
                if (!OnionMessageProtocol.isMessageFresh(frame, allowOfflineWindow = allowOfflineTimestamp)) {
                    Log.w(TAG, "Received stale contact exchange from ${redactEndpoint(senderOnion)}")
                    return false
                }

                // Check for duplicates
                if (deduplicator.isDuplicate(frame.messageId)) {
                    val canonicalPeer = OnionMessageProtocol.ContactExchange.fromBytes(frame.payload)
                        ?.onionAddress
                        ?.let { OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it) }
                        ?.takeIf { it.isNotBlank() }
                        ?: senderOnion
                    markPeerResponsive(canonicalPeer)
                    sendMessageAck(canonicalPeer, frame.messageId)
                    wakeQueueDispatcher()
                    Log.d(TAG, "Acked duplicate contact exchange ${frame.messageId} from ${redactEndpoint(canonicalPeer)}")
                    return true
                }
                
                markPeerResponsive(senderOnion)
                handleContactExchange(senderOnion, frame)
                return true
            }
            
            // Otherwise, decrypt directly into decoded frame (no redundant encode/decode roundtrip)
            frame = if (isAlreadyDecrypted) {
                OnionMessageProtocol.OnionFrame.decode(data) ?: return false
            } else {
                decryptFrame(senderOnion, data)
            }
            // A frame that decrypted with our session keys proves the peer is alive,
            // even if it later turns out to be a duplicate retransmission.
            markPeerResponsive(senderOnion)
            
            // Check freshness (allow 7-day offline window for mailbox & BSP synced messages)
            if (!OnionMessageProtocol.isMessageFresh(frame, allowOfflineWindow = allowOfflineTimestamp)) {
                Log.w(TAG, "Received stale message from ${redactEndpoint(senderOnion)}")
                return false
            }
            
            // Fast-path real-time voice/video frames before message deduplicator
            if (frame.frameType == OnionMessageProtocol.FrameType.CALL_AUDIO) {
                com.seamless.android.call.SeamlessCallManager.getInstance(context)
                    .onIncomingTorCallAudio(senderOnion, frame.sequence, frame.timestamp, frame.payload)
                return true
            }
            if (frame.frameType == OnionMessageProtocol.FrameType.CALL_VIDEO) {
                com.seamless.android.call.SeamlessCallManager.getInstance(context)
                    .onIncomingTorCallVideo(senderOnion, frame.sequence, frame.timestamp, frame.payload)
                return true
            }

            // Check for duplicates
            if (deduplicator.isDuplicate(frame.messageId)) {
                Log.d(TAG, "Ignoring duplicate message ${frame.messageId}")
                if (frame.frameType == OnionMessageProtocol.FrameType.MESSAGE ||
                    frame.frameType == OnionMessageProtocol.FrameType.READ_RECEIPT ||
                    frame.frameType == OnionMessageProtocol.FrameType.FILE_META ||
                    frame.frameType == OnionMessageProtocol.FrameType.FILE_CHUNK ||
                    frame.frameType == OnionMessageProtocol.FrameType.FILE_PARITY ||
                    frame.frameType == OnionMessageProtocol.FrameType.MAILBOX_EXCHANGE ||
                    frame.frameType == OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
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
                        OnionMessageProtocol.FrameType.FILE_PARITY -> attachmentTransfer.senderPublicKeyForParity(frame.payload)
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
                OnionMessageProtocol.FrameType.READ_RECEIPT -> handleReadReceipt(senderOnion, frame)
                OnionMessageProtocol.FrameType.FILE_META -> {
                    val canonicalSender = attachmentTransfer.decodeMeta(frame.payload)
                        ?.senderPublicKey
                        ?.takeIf { it.isNotBlank() }
                        ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        ?: senderOnion
                    val transportContact = contactManager.getContactByOnion(senderOnion)
                    if (transportContact != null && !transportContact.onionAddress.equals(canonicalSender, ignoreCase = true)) {
                        Log.w(TAG, "Rejected FILE_META with mismatched sender from ${redactEndpoint(senderOnion)}")
                        deduplicator.forget(frame.messageId)
                        return false
                    }
                    handleFileMeta(canonicalSender, frame)
                }
                OnionMessageProtocol.FrameType.FILE_CHUNK -> {
                    val canonicalSender = attachmentTransfer.senderPublicKeyForChunk(frame.payload)
                        ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        ?: senderOnion
                    val transportContact = contactManager.getContactByOnion(senderOnion)
                    if (transportContact != null && !transportContact.onionAddress.equals(canonicalSender, ignoreCase = true)) {
                        Log.w(TAG, "Rejected FILE_CHUNK with mismatched sender from ${redactEndpoint(senderOnion)}")
                        deduplicator.forget(frame.messageId)
                        return false
                    }
                    handleFileChunk(canonicalSender, frame)
                }
                OnionMessageProtocol.FrameType.FILE_PARITY -> {
                    val canonicalSender = attachmentTransfer.senderPublicKeyForParity(frame.payload)
                        ?.let { contactManager.getContactByPublicKey(it)?.onionAddress }
                        ?: senderOnion
                    val transportContact = contactManager.getContactByOnion(senderOnion)
                    if (transportContact != null && !transportContact.onionAddress.equals(canonicalSender, ignoreCase = true)) {
                        Log.w(TAG, "Rejected FILE_PARITY with mismatched sender from ${redactEndpoint(senderOnion)}")
                        deduplicator.forget(frame.messageId)
                        return false
                    }
                    handleFileParity(canonicalSender, frame)
                }
                OnionMessageProtocol.FrameType.ACK -> { handleAck(frame); true }
                OnionMessageProtocol.FrameType.NACK -> { handleNack(frame); true }
                OnionMessageProtocol.FrameType.PING -> { handlePing(senderOnion, frame); true }
                OnionMessageProtocol.FrameType.PONG -> { handlePong(senderOnion, frame); true }
                OnionMessageProtocol.FrameType.TYPING -> { handleTyping(senderOnion, frame); true }
                OnionMessageProtocol.FrameType.BULK_HELLO -> true
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
                    if (handleMailboxExchange(senderOnion, frame)) {
                        // An ACK means the endpoint is durably stored locally,
                        // not merely that the frame reached a socket. This lets
                        // a sender safely stop retrying mailbox setup.
                        sendMessageAck(senderOnion, frame.messageId)
                        true
                    } else {
                        deduplicator.forget(frame.messageId)
                        false
                    }
                }
                OnionMessageProtocol.FrameType.CALL_SIGNAL -> {
                    Log.i(TAG, "📞 Received CALL_SIGNAL from ${redactEndpoint(senderOnion)}")
                    com.seamless.android.call.SeamlessCallManager.getInstance(context)
                        .onIncomingTorCallSignal(senderOnion, frame.payload)
                    true
                }
                OnionMessageProtocol.FrameType.CALL_AUDIO -> {
                    com.seamless.android.call.SeamlessCallManager.getInstance(context)
                        .onIncomingTorCallAudio(senderOnion, frame.sequence, frame.timestamp, frame.payload)
                    true
                }
                OnionMessageProtocol.FrameType.CALL_VIDEO -> {
                    com.seamless.android.call.SeamlessCallManager.getInstance(context)
                        .onIncomingTorCallVideo(senderOnion, frame.sequence, frame.timestamp, frame.payload)
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
        val canonicalExchangeOnion = OnionContactManager.OnionContact
            .canonicalizeOnionEndpoint(exchange.onionAddress)
        val validExchange = OnionContactManager.OnionContact
            .isValidOnionEndpoint(canonicalExchangeOnion) &&
            exchange.nickname.trim().length in 1..80 &&
            exchange.publicKey.matches(Regex("^[0-9a-fA-F]{64}$"))
        if (!validExchange) {
            Log.w(TAG, "Rejected malformed contact exchange from ${redactEndpoint(senderOnion)}")
            return
        }
        
        Log.i(TAG, "📩 Received contact exchange from ${exchange.nickname} (${exchange.onionAddress.take(16)}...)")
        
        val existingByOnion = contactManager.getContactByOnion(canonicalExchangeOnion)
        val existingByKey = contactManager.getContactByPublicKey(exchange.publicKey)
        if (existingByKey != null && !existingByKey.requiresRendezvous()) {
            val pinnedOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(existingByKey.onionAddress)
            if (!pinnedOnion.equals(canonicalExchangeOnion, ignoreCase = true)) {
                Log.w(TAG, "Rejected unauthenticated CONTACT_EXCHANGE onion overwrite for ${existingByKey.nickname}")
                return
            }
        }
        val keyChangedForOnion = existingByOnion != null &&
            !existingByOnion.publicKey.equals(exchange.publicKey, ignoreCase = true)
        val now = System.currentTimeMillis()
        val lastReplyAt = lastContactExchangeReplyAt[canonicalExchangeOnion] ?: 0L
        val shouldRespond = existingByOnion == null ||
            keyChangedForOnion ||
            existingByKey?.requiresRendezvous() == true ||
            (now - lastReplyAt) >= 15_000L

        // Add, update, or mark key-change state for the contact.
        try {
            val exchangedContact = OnionContactManager.OnionContact(
                id = java.util.UUID.randomUUID().toString(),
                nickname = exchange.nickname,
                onionAddress = canonicalExchangeOnion,
                publicKey = exchange.publicKey,
                avatarId = OnionContactManager.OnionContact.canonicalAvatarId(exchange.avatarId),
                addedAt = System.currentTimeMillis()
            )
            
            contactManager.addContact(exchangedContact)
            val currentContact = contactManager.getContactByPublicKey(exchange.publicKey)
                ?: contactManager.getContactByOnion(canonicalExchangeOnion)
                ?: exchangedContact
            contactManager.updateLastSeen(currentContact.id)
            registerContactMailboxForPolling(currentContact)
            markPeerResponsive(canonicalExchangeOnion)
            sendMessageAck(canonicalExchangeOnion, frame.messageId)
            listOf(canonicalExchangeOnion, senderOnion).distinct().forEach { endpoint ->
                messageQueue.getPendingMessages(endpoint)
                    .filter {
                        OnionMessageProtocol.OnionFrame.decode(it.frameData)?.frameType ==
                            OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
                    }
                    .forEach { messageQueue.markDelivered(it.id) }
            }
            wakeQueueDispatcher()
            Log.i(TAG, "✅ Auto-paired with ${exchange.nickname}!")
            
            // Notify listener that a new contact was added
            messageListener?.onPeerOnline(currentContact)
            
            // ━━━ CRITICAL: Send our own CONTACT_EXCHANGE back ━━━
            // The sender needs to know we received their pairing request
            // and to get our info (address, key) for bidirectional messaging
            if (shouldRespond) {
                lastContactExchangeReplyAt[canonicalExchangeOnion] = now
                scope.launch {
                    delay(150) // Short delay to let connection stabilize
                    sendContactExchange(canonicalExchangeOnion)
                    Log.i(TAG, "Sent CONTACT_EXCHANGE response back to ${exchange.nickname}")
                }
            }

            // ━━━ Exchange mailbox addresses for offline delivery ━━━
            // After successful pairing, share our mailbox so they can reach us offline
            scope.launch {
                delay(3000) // Wait for encryption session to establish
                sendMailboxExchange(canonicalExchangeOnion)
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

        // Verify recipientKey so a synced message intended for another peer is never accepted as ours
        val myStaticKeyHex = getMyStaticPublicKeyHex()
        if (envelope.recipientKey.isNotEmpty() &&
            myStaticKeyHex.isNotEmpty() &&
            !envelope.recipientKey.equals(myStaticKeyHex, ignoreCase = true)
        ) {
            Log.w(TAG, "Rejected misdirected MESSAGE intended for another recipientKey")
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
        val transportContact = contactManager.getContactByOnion(senderOnion)
        if (transportContact != null && senderContact != null &&
            !transportContact.publicKey.equals(senderContact.publicKey, ignoreCase = true)
        ) {
            Log.w(TAG, "Rejected MESSAGE with mismatched senderKey from ${redactEndpoint(senderOnion)}")
            deduplicator.forget(frame.messageId)
            return false
        }
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
                    TorGroupMessaging.InboundResult.DEFERRED -> {
                        Log.d(TAG, "Deferred encrypted group event pending invite from ${redactEndpoint(effectiveSenderOnion)}")
                        deduplicator.forget(frame.messageId)
                        return false
                    }
                    TorGroupMessaging.InboundResult.REJECTED -> {
                        Log.w(TAG, "Rejected invalid encrypted group event from ${redactEndpoint(effectiveSenderOnion)}")
                        deduplicator.forget(frame.messageId)
                        return false
                    }
                    TorGroupMessaging.InboundResult.NOT_GROUP -> Unit
                }
                val ctrl = TorRichChatCodec.parseDirectControl(chatMessage.text)
                if (ctrl != null) {
                    handleDirectControlEvent(contact, ctrl)
                    updateChainHead(effectiveSenderOnion, envelope.chainHash())
                    syncEngine.markAsReceived(messageId.toString())
                    sendMessageAck(effectiveSenderOnion, messageId)
                    return true
                }
                messageRepository.saveReceivedMessage(
                    messageId = messageId.toString(),
                    contact = contact,
                    text = chatMessage.text,
                    timestamp = timestamp
                )
                updateChainHead(effectiveSenderOnion, envelope.chainHash())
                syncEngine.markAsReceived(messageId.toString())
                sendMessageAck(effectiveSenderOnion, messageId)
                Log.i(TAG, "Message $messageId persisted to database")
                if (!visibleChatContacts.contains(contact.id) &&
                    !com.seamless.android.util.AppForegroundTracker.isAppInForeground(context)
                ) {
                    val previewText = TorRichChatCodec.friendlyPreviewText(chatMessage.text)
                    messageNotifier.showIncoming(contact, previewText)
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
    
    private suspend fun handleDirectControlEvent(
        contact: OnionContactManager.OnionContact,
        ctrl: TorRichChatCodec.DirectControlEvent
    ) {
        val actorKey = canonicalPeerKey(contact.onionAddress)
        when (ctrl.action) {
            "react" -> {
                val cleanEmoji = ctrl.emoji.trim().take(8)
                val action = if (ctrl.reactionAction == "remove" || cleanEmoji.isEmpty()) "remove" else "add"
                updateDirectReactionState(ctrl.targetMessageId, actorKey, cleanEmoji, action)
            }
            "edit" -> {
                val cleanText = ctrl.text.trim()
                if (cleanText.isEmpty() || cleanText.toByteArray(Charsets.UTF_8).size > MAX_DIRECT_TEXT_BYTES) return
                val target = messageRepository.getMessage(ctrl.targetMessageId) ?: return
                if (target.isFromMe || target.contactId != contact.id || target.attachmentPath != null) return
                val currentBody = TorRichChatCodec.parseReply(target.text).body
                if (target.id in _directDeletedIdsFlow.value ||
                    target.id in _directBurnedIdsFlow.value ||
                    currentBody == TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT ||
                    currentBody == TorRichChatCodec.BURNED_CAPSULE_TEXT ||
                    TorRichChatCodec.isUnburnedSecretText(target.text)
                ) return
                val parsed = TorRichChatCodec.parseReply(target.text)
                val updated = TorRichChatCodec.formatWithReply(
                    cleanText,
                    parsed.replyToId,
                    parsed.replyToSender,
                    parsed.replyToPreview
                )
                messageRepository.updateMessageText(target.id, updated)
                synchronized(directRichLock) {
                    directRichPrefs.edit().putBoolean("edited:${target.id}", true).apply()
                }
                _directEditedIdsFlow.update { it + target.id }
            }
            "delete" -> {
                val target = messageRepository.getMessage(ctrl.targetMessageId) ?: return
                if (target.isFromMe || target.contactId != contact.id) return
                TorRichChatCodec.AegisShredder.shredPath(target.attachmentPath)
                messageRepository.clearAttachmentAndUpdateText(target.id, TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT)
                synchronized(directRichLock) {
                    directRichPrefs.edit().putBoolean("deleted:${target.id}", true).apply()
                }
                _directDeletedIdsFlow.update { it + target.id }
            }
            "shred_receipt" -> {
                val target = messageRepository.getMessage(ctrl.targetMessageId) ?: return
                if (!target.isFromMe || target.contactId != contact.id) return
                synchronized(directRichLock) {
                    directRichPrefs.edit().putBoolean("shred:${target.id}", true).apply()
                }
                _directShredReceiptsFlow.update { it + target.id }
                messageRepository.markOutgoingReadForContact(contact, listOf(target.id))
                messageQueue.markRead(target.id)
                messageListener?.onDeliveryStatusChanged(target.id, OnionMessageProtocol.DeliveryState.READ)
            }
        }
    }

    private suspend fun handleReadReceipt(senderOnion: String, frame: OnionMessageProtocol.OnionFrame): Boolean {
        val contact = contactManager.getContactByOnion(senderOnion) ?: run {
            deduplicator.forget(frame.messageId)
            return false
        }
        val ids = OnionMessageProtocol.parseBatchReadReceipt(frame.payload).map { it.toString() }
        if (ids.isNotEmpty()) {
            messageRepository.markOutgoingReadForContact(contact, ids)
            ids.forEach { msgId ->
                messageQueue.markRead(msgId)
                messageListener?.onDeliveryStatusChanged(msgId, OnionMessageProtocol.DeliveryState.READ)
            }
        }
        sendMessageAck(contact.onionAddress.ifBlank { senderOnion }, frame.messageId)
        return true
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

    /** Sends a receipt through our Tier 1 outbox mailbox and/or the sender's encrypted mailbox when no live circuit exists. */
    private suspend fun storeAckInSenderMailbox(
        senderOnion: String,
        ackFrame: OnionMessageProtocol.OnionFrame
    ): Boolean {
        val sender = contactManager.getContactByOnion(senderOnion) ?: return false
        val payload = createSealedMailboxPayload(sender, ackFrame) ?: return false
        val recipientId = fingerprintForPublicKeyHex(sender.publicKey)
        // Tier 1: Deposit in our local outbox mailbox so the sender can pull the ACK when polling us
        val localDeposited = mailboxService.depositLocalBlob(recipientId, payload, getMyOnionAddress()) != null
        // Tier 2: Also push to sender's remote mailbox if known
        val mailboxOnion = sender.mailboxOnion
        val remoteDeposited = if (!mailboxOnion.isNullOrBlank()) {
            mailboxClient.storeInMailbox(
                mailboxOnion = mailboxOnion,
                recipientId = recipientId,
                encryptedPayload = payload
            )
        } else {
            false
        }
        return remoteDeposited || (localDeposited && !ownMailboxOnion.isNullOrBlank())
    }

    /**
     * Called when connectivity changes or Doze exits while the service is running.
     * Refreshes hidden-service descriptors, wakes the retry dispatcher, reconnects
     * known contacts, and pulls any queued mailbox messages right away.
     */
    fun onNetworkRestored() {
        if (_serviceState.value != ServiceState.RUNNING) return
        scope.launch {
            try {
                prefetchKnownContactDescriptors()
            } catch (e: Exception) {
                Log.w(TAG, "Descriptor prefetch on network restore failed: ${e.message}")
            }
            wakeQueueDispatcher()
            reconnectKnownContacts()
            if (mailboxClient.isInitialized()) {
                try {
                    mailboxClient.pollAllMailboxes()
                } catch (e: Exception) {
                    Log.w(TAG, "Mailbox poll on network restore failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun prefetchKnownContactDescriptors() {
        val app = context.applicationContext as? Application ?: return
        val endpoints = LinkedHashSet<String>()
        contactManager.getAllContacts()
            .asSequence()
            .filter {
                it.trustState != TorContactTrustState.BLOCKED &&
                    it.trustState != TorContactTrustState.KEY_CHANGED
            }
            .forEach { contact ->
                if (!contact.requiresRendezvous() && contact.onionAddress.isNotBlank()) {
                    endpoints.add(contact.onionAddress)
                }
                contact.mailboxOnion?.takeIf { it.isNotBlank() }?.let { endpoints.add(it) }
            }
        if (mailboxClient.isInitialized()) {
            endpoints.addAll(mailboxClient.getMailboxes())
        }
        if (endpoints.isNotEmpty()) {
            TorDaemonManager.getInstance(app).prefetchOnionDescriptors(endpoints)
        }
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
                if (circuitManager.hasActiveConnection(contact.onionAddress)) return@forEach
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
        val previewBytes = meta.previewBytes
        if (!meta.viewOnce && previewBytes != null && previewBytes.isNotEmpty()) {
            val contact = meta.senderPublicKey
                .takeIf { it.isNotBlank() }
                ?.let { contactManager.getContactByPublicKey(it) }
                ?: contactManager.getContactByOnion(senderOnion)
            val previewPath = attachmentTransfer.savePreview(meta.transferId, previewBytes)
            if (contact != null && previewPath != null) {
                if (meta.groupId != null && meta.groupMessageId != null) {
                    if (groupMessaging.handleIncomingImagePreview(
                            sender = contact,
                            groupId = meta.groupId,
                            messageId = meta.groupMessageId,
                            timestamp = frame.timestamp,
                            previewPath = previewPath
                        )
                    ) {
                        previewShownTransfers.add(meta.transferId.toString())
                    } else {
                        attachmentTransfer.deletePreview(meta.transferId)
                    }
                } else {
                    runCatching {
                        messageRepository.saveReceivedMessage(
                            messageId = frame.messageId.toString(),
                            contact = contact,
                            text = "Photo",
                            timestamp = frame.timestamp,
                            attachmentPath = previewPath,
                            attachmentMimeType = "image/webp"
                        )
                        previewShownTransfers.add(meta.transferId.toString())
                        if (!visibleChatContacts.contains(contact.id) &&
                            !com.seamless.android.util.AppForegroundTracker.isAppInForeground(context)
                        ) {
                            messageNotifier.showIncoming(contact, "📷 Photo", isPhoto = true)
                        }
                    }
                }
            }
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
            val transferId = attachmentTransfer.peekTransferId(frame.payload)
            if (transferId != null && attachmentTransfer.isFinalChunkPayload(frame.payload)) {
                // Hold the final chunk ACK until earlier out-of-order chunks finish arriving;
                // otherwise sender's markAttachmentDelivered() would delete still-missing chunks.
                deferredFinalChunkIds[transferId.toString()] = frame.messageId
                return true
            }
            sendMessageAck(senderOnion, frame.messageId)
            return true
        }
        return finalizeCompletedAttachment(senderOnion, frame, completed)
    }

    private suspend fun handleFileParity(senderOnion: String, frame: OnionMessageProtocol.OnionFrame): Boolean {
        val result = attachmentTransfer.acceptParityDroplet(senderOnion, frame.payload)
        if (result is TorAttachmentTransfer.ChunkResult.Rejected) {
            Log.w(TAG, "Rejected parity droplet from ${redactEndpoint(senderOnion)}")
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
        return finalizeCompletedAttachment(senderOnion, frame, completed)
    }

    private suspend fun finalizeCompletedAttachment(
        senderOnion: String,
        frame: OnionMessageProtocol.OnionFrame,
        completed: TorAttachmentTransfer.CompletedAttachment
    ): Boolean {
        val contact = completed.senderPublicKey
            .takeIf { it.isNotBlank() }
            ?.let { contactManager.getContactByPublicKey(it) }
            ?: contactManager.getContactByOnion(senderOnion)
        if (contact == null) {
            deduplicator.forget(frame.messageId)
            return false
        }
        if (completed.groupId != null && completed.groupMessageId != null) {
            val accepted = groupMessaging.handleIncomingAttachment(
                sender = contact,
                groupId = completed.groupId,
                messageId = completed.groupMessageId,
                timestamp = completed.timestamp,
                path = completed.path,
                mimeType = completed.mimeType,
                fileName = completed.fileName,
                durationMs = completed.durationMs,
                viewOnce = completed.viewOnce
            )
            if (!accepted) { deduplicator.forget(frame.messageId); return false }
            previewShownTransfers.remove(completed.transferId.toString())
            attachmentTransfer.deletePreview(completed.transferId)
            attachmentTransfer.completeIncoming(senderOnion, completed.transferId)
            sendMessageAck(senderOnion, frame.messageId)
            deferredFinalChunkIds.remove(completed.transferId.toString())
                ?.takeIf { it != frame.messageId }
                ?.let { sendMessageAck(senderOnion, it) }
            return true
        }
        val hadPreview = previewShownTransfers.remove(completed.transferId.toString())
        val kind = when {
            completed.mimeType.startsWith("audio/") -> "voice"
            completed.mimeType.startsWith("image/") -> "photo"
            else -> "file"
        }
        val sizeBytes = runCatching { java.io.File(completed.path).length().toInt() }.getOrDefault(0)
        val rowText = if (kind == "photo" && !completed.viewOnce && completed.fileName.isBlank()) {
            "Photo"
        } else {
            TorRichChatCodec.encodeAttachmentDescriptor(
                TorRichChatCodec.AttachmentDescriptor(
                    kind = kind,
                    fileName = completed.fileName,
                    totalBytes = sizeBytes,
                    durationMs = completed.durationMs,
                    viewOnce = completed.viewOnce
                )
            )
        }
        try {
            if (hadPreview) {
                messageRepository.updateAttachment(
                    messageId = completed.messageId.toString(),
                    path = completed.path,
                    mimeType = completed.mimeType
                )
                if (rowText != "Photo") {
                    messageRepository.updateMessageText(completed.messageId.toString(), rowText)
                }
            } else {
                messageRepository.saveReceivedMessage(
                    messageId = completed.messageId.toString(),
                    contact = contact,
                    text = rowText,
                    timestamp = completed.timestamp,
                    attachmentPath = completed.path,
                    attachmentMimeType = completed.mimeType
                )
            }
            attachmentTransfer.deletePreview(completed.transferId)
            attachmentTransfer.completeIncoming(senderOnion, completed.transferId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist received attachment: ${e.message}")
            deduplicator.forget(frame.messageId)
            return false
        }
        sendMessageAck(senderOnion, frame.messageId)
        deferredFinalChunkIds.remove(completed.transferId.toString())
            ?.takeIf { it != frame.messageId }
            ?.let { sendMessageAck(senderOnion, it) }
        val previewLabel = TorRichChatCodec.friendlyPreviewText(rowText, completed.mimeType)
        if (!hadPreview &&
            !visibleChatContacts.contains(contact.id) &&
            !com.seamless.android.util.AppForegroundTracker.isAppInForeground(context)
        ) {
            messageNotifier.showIncoming(
                contact,
                previewLabel,
                isPhoto = !completed.viewOnce && kind == "photo" && completed.fileName.isBlank()
            )
        }
        messageListener?.onMessageReceived(
            contact,
            OnionMessage(
                id = completed.messageId.toString(),
                text = rowText,
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
        try {
            groupMessaging.onTransportAck(ackedId.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Group delivery tracking failed: ${e.message}")
        }

        // Preserve the queue record long enough to map the transport ACK back to
        // its visible chat item. Photo metadata and intermediate chunks must not
        // mark the photo delivered; only its final chunk is allowed to do that.
        val queuedMessage = messageQueue.getQueuedMessage(ackedId.toString())
        messageQueue.markDelivered(ackedId.toString())
        syncEngine.markMessageDelivered(ackedId.toString())
        wakeQueueDispatcher()

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
        circuitManager.sendDataIfConnected(senderOnion, encryptFrame(senderOnion, pongFrame).encode())
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
        val canonical = canonicalPeerKey(peerOnion)
        val wasOnline = _connectedPeers.value.contains(canonical)
        val now = System.currentTimeMillis()
        lastVerifiedPeerActivity[canonical] = now
        refreshLanPeersState()
        if (!wasOnline) {
            _connectedPeers.update { it + canonical }
            contactManager.getContactByOnion(canonical)?.let { contact ->
                messageListener?.onPeerOnline(contact)
            }
            scope.launch {
                runCatching { groupMessaging.onPeerOnline(canonical) }
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
        val canonical = canonicalPeerKey(peerOnion)
        val connectionId = circuitManager.getActiveConnectionId(canonical) ?: return
        refreshLanPeersState()
        synchronized(readLoopConnections) {
            if (readLoopConnections[canonical] == connectionId) return
            readLoopConnections[canonical] = connectionId
            peersWithReadLoop.add(canonical)
        }
        scope.launch {
            continuousReadLoop(canonical, connectionId)
        }
    }

    private fun createEncryptedBulkHelloFrame(peerOnion: String): ByteArray? {
        val canonical = canonicalPeerKey(peerOnion)
        val contact = contactManager.getContactByOnion(canonical) ?: return null
        if (contact.publicKey.isBlank()) return null
        val myOnion = getMyOnionAddress()
        if (myOnion.isBlank()) return null
        val frame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = getSequenceManager(canonical).nextOutbound(),
            timestamp = System.currentTimeMillis(),
            frameType = OnionMessageProtocol.FrameType.BULK_HELLO,
            payload = myOnion.toByteArray(Charsets.UTF_8)
        )
        return runCatching { encryptFrame(canonical, frame).encode() }.getOrNull()
    }

    private fun ensureBulkReadLoop(peerOnion: String) {
        val canonical = canonicalPeerKey(peerOnion)
        val connectionId = circuitManager.getActiveBulkConnectionId(canonical) ?: return
        synchronized(bulkReadLoopConnections) {
            if (bulkReadLoopConnections[canonical] == connectionId) return
            bulkReadLoopConnections[canonical] = connectionId
        }
        scope.launch {
            continuousBulkReadLoop(canonical, connectionId)
        }
    }

    private suspend fun continuousBulkReadLoop(peerOnion: String, connectionId: String) {
        Log.d(TAG, "⚡ Starting Warp-Lane BULK read loop for ${redactEndpoint(peerOnion)}")
        try {
            while (circuitManager.getActiveBulkConnectionId(peerOnion) == connectionId) {
                try {
                    val data = circuitManager.receiveBulkData(peerOnion, connectionId)
                    if (data != null) {
                        if (data.size >= OnionMessageProtocol.HEADER_SIZE &&
                            (data[33] == OnionMessageProtocol.FrameType.FILE_CHUNK.code ||
                                data[33] == OnionMessageProtocol.FrameType.FILE_PARITY.code ||
                                data[33] == OnionMessageProtocol.FrameType.SYNC_BATCH.code)
                        ) {
                            enqueueBulkInboundFrame(peerOnion, data)
                        } else {
                            handleIncomingData(peerOnion, data, isAlreadyDecrypted = false)
                        }
                    } else if (circuitManager.getActiveBulkConnectionId(peerOnion) != connectionId ||
                        !circuitManager.hasActiveBulkConnection(peerOnion)
                    ) {
                        break
                    } else {
                        delay(10)
                    }
                } catch (e: java.io.EOFException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Warp-Lane BULK read loop error for ${redactEndpoint(peerOnion)}: ${e.message}")
                    break
                }
            }
        } finally {
            bulkReadLoopConnections.remove(peerOnion, connectionId)
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

        if (frame.frameType == OnionMessageProtocol.FrameType.CONTACT_EXCHANGE) {
            return frame.copy(timestamp = System.currentTimeMillis()).encode()
        }

        if (isDirectSealedPayload(frame.payload)) {
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
        val ver = payload[DIRECT_SEALED_MAGIC.size]
        return ver == DIRECT_SEALED_VERSION ||
            ver == com.seamless.android.crypto.AegisTorCrypto.DIRECT_V2_VERSION
    }

    private fun createSealedMailboxPayload(
        contact: OnionContactManager.OnionContact,
        frame: OnionMessageProtocol.OnionFrame
    ): ByteArray? {
        if (contact.publicKey.isEmpty()) return null

        return try {
            val recipientPublicKey = hexStringToByteArray(contact.publicKey)
            val localKeyPair = cachedLocalStaticKeyPair
                ?: SecureIdentityStateManager(context).loadStaticKey()?.also { cachedLocalStaticKeyPair = it }
                ?: return null
            com.seamless.android.crypto.AegisTorCrypto.sealMailboxV2(
                plaintext = frame.encode(),
                senderStaticPriv = localKeyPair.first,
                senderStaticPub = localKeyPair.second,
                recipientStaticPub = recipientPublicKey
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create STMB v2 mailbox sealed payload: ${e.message}")
            null
        }
    }

    /**
     * Returns true only when the payload passed structural validation and was handed
     * to the normal inbound path. The mailbox client deletes only these committed
     * messages, so a transient decode/decryption failure cannot silently lose mail.
     */
    private suspend fun handleMailboxPayload(payload: ByteArray, mailboxOnion: String): Boolean {
        val (unsealed, authenticatedSenderKey) = decryptSealedMailboxPayloadWithSender(payload)
            ?: run {
                Log.w(TAG, "Rejected unsealed mailbox payload from ${redactEndpoint(mailboxOnion)}")
                return false
            }

        val frame = OnionMessageProtocol.OnionFrame.decode(unsealed) ?: return false
        val innerSenderKey = when (frame.frameType) {
            OnionMessageProtocol.FrameType.MESSAGE -> TorMessageEnvelope.decode(frame.payload)?.senderKey
            OnionMessageProtocol.FrameType.FILE_META -> attachmentTransfer.decodeMeta(frame.payload)?.senderPublicKey
            OnionMessageProtocol.FrameType.FILE_CHUNK -> attachmentTransfer.senderPublicKeyForChunk(frame.payload)
            OnionMessageProtocol.FrameType.FILE_PARITY -> attachmentTransfer.senderPublicKeyForParity(frame.payload)
            else -> null
        }

        if (authenticatedSenderKey != null) {
            if (!innerSenderKey.isNullOrBlank() && !innerSenderKey.equals(authenticatedSenderKey, ignoreCase = true)) {
                Log.w(TAG, "Rejected STMB v2 mailbox payload: inner senderKey mismatch")
                return false
            }
        } else if (frame.frameType == OnionMessageProtocol.FrameType.MESSAGE) {
            val chatText = TorMessageEnvelope.decode(frame.payload)
                ?.let { OnionMessageProtocol.ChatMessage.fromBytes(it.ciphertext)?.text }
            if (chatText?.startsWith("SGRP1:") == true || chatText?.startsWith("SGRP2:") == true) {
                Log.w(TAG, "Rejected unauthenticated legacy v1 mailbox group payload")
                return false
            }
        }

        val effectiveSenderKey = authenticatedSenderKey ?: innerSenderKey
        val contact = effectiveSenderKey
            ?.takeIf { it.isNotEmpty() }
            ?.let { contactManager.getContactByPublicKey(it) }

        return handleIncomingData(
            senderOnion = run {
                val mailboxOwner = contactManager.getAllContacts().find {
                    it.mailboxOnion.equals(mailboxOnion, ignoreCase = true)
                }
                if (mailboxOwner != null && !mailboxOnion.equals(ownMailboxOnion, ignoreCase = true)) {
                    if (contact != null && !contact.publicKey.equals(mailboxOwner.publicKey, ignoreCase = true)) {
                        Log.w(TAG, "Rejected mailbox payload with mismatched senderKey for contact outbox ${redactEndpoint(mailboxOnion)}")
                        return false
                    }
                    mailboxOwner.onionAddress.takeIf { it.isNotEmpty() } ?: mailboxOnion
                } else {
                    contact?.onionAddress?.takeIf { it.isNotEmpty() } ?: mailboxOnion
                }
            },
            data = unsealed,
            isAlreadyDecrypted = true,
            allowOfflineTimestamp = true
        )
    }

    private fun decryptSealedMailboxPayloadWithSender(payload: ByteArray): Pair<ByteArray, String?>? {
        return try {
            if (payload.size < MAILBOX_SEALED_MAGIC.size + 1) return null
            val magic = payload.copyOfRange(0, MAILBOX_SEALED_MAGIC.size)
            if (!magic.contentEquals(MAILBOX_SEALED_MAGIC)) return null

            val version = payload[MAILBOX_SEALED_MAGIC.size]
            val localKeyPair = cachedLocalStaticKeyPair
                ?: SecureIdentityStateManager(context).loadStaticKey()?.also { cachedLocalStaticKeyPair = it }
                ?: return null
            val identityKey = localKeyPair.first
            val myPublicKey = localKeyPair.second

            if (version == com.seamless.android.crypto.AegisTorCrypto.MAILBOX_V2_VERSION) {
                val opened = com.seamless.android.crypto.AegisTorCrypto.openMailboxV2(
                    payload = payload,
                    recipientStaticPriv = identityKey,
                    recipientStaticPub = myPublicKey
                ) ?: return null
                return opened.plaintext to opened.senderPublicKeyHex
            }

            if (version != MAILBOX_SEALED_VERSION ||
                payload.size < MAILBOX_SEALED_MAGIC.size + 1 + 32 + MAILBOX_NONCE_SIZE + 4
            ) {
                return null
            }

            val buffer = ByteBuffer.wrap(payload)
            buffer.position(MAILBOX_SEALED_MAGIC.size + 1)

            val ephemeralPublicKey = ByteArray(32)
            buffer.get(ephemeralPublicKey)

            val nonce = ByteArray(MAILBOX_NONCE_SIZE)
            buffer.get(nonce)

            val ciphertextSize = buffer.getInt()
            if (ciphertextSize < 0 || ciphertextSize > buffer.remaining()) return null

            val ciphertext = ByteArray(ciphertextSize)
            buffer.get(ciphertext)

            val sharedSecret = X25519.computeSharedSecret(identityKey, ephemeralPublicKey)
            val aad = MAILBOX_SEALED_MAGIC + byteArrayOf(version) + ephemeralPublicKey

            val plain = aesGcmDecrypt(
                ciphertext = ciphertext,
                key = deriveMailboxKey(sharedSecret, ephemeralPublicKey, myPublicKey),
                nonce = nonce,
                aad = aad
            ) ?: return null
            plain to null
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
        peerPublicKey: ByteArray,
        peerPublicKeyHex: String? = null
    ): ByteArray {
        val localKeyPair = cachedLocalStaticKeyPair
            ?: SecureIdentityStateManager(context).loadStaticKey()?.also { cachedLocalStaticKeyPair = it }
        if (localKeyPair != null && peerPublicKey.size == 32) {
            val peerKey = peerPublicKeyHex?.lowercase() ?: fastHexEncode(peerPublicKey)
            val counter = directSendCounters
                .getOrPut(peerKey) { java.util.concurrent.atomic.AtomicLong(0L) }
                .getAndIncrement()
            return com.seamless.android.crypto.AegisTorCrypto.sealDirectV2(
                plaintext = frame.payload,
                frameAad = directFrameAad(frame),
                senderStaticPriv = localKeyPair.first,
                senderStaticPub = localKeyPair.second,
                recipientStaticPub = peerPublicKey,
                counter = counter
            )
        }

        val nonce = ByteArray(DIRECT_NONCE_SIZE).also { secureRandom.nextBytes(it) }
        val aad = directFrameAad(frame)
        val sealedHeader = DIRECT_SEALED_MAGIC + byteArrayOf(DIRECT_SEALED_VERSION)
        val ciphertext = aesGcmEncrypt(
            plaintext = frame.payload,
            key = getOrDeriveDirectTransportKey(peerPublicKey, peerPublicKeyHex),
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
        peerPublicKey: ByteArray,
        peerPublicKeyHex: String? = null
    ): ByteArray? {
        return try {
            val payload = frame.payload
            if (payload.size < DIRECT_SEALED_MAGIC.size + 1) return null

            val magic = payload.copyOfRange(0, DIRECT_SEALED_MAGIC.size)
            if (!magic.contentEquals(DIRECT_SEALED_MAGIC)) return null

            val version = payload[DIRECT_SEALED_MAGIC.size]
            if (version == com.seamless.android.crypto.AegisTorCrypto.DIRECT_V2_VERSION) {
                val localKeyPair = cachedLocalStaticKeyPair
                    ?: SecureIdentityStateManager(context).loadStaticKey()?.also { cachedLocalStaticKeyPair = it }
                    ?: return null
                val opened = com.seamless.android.crypto.AegisTorCrypto.openDirectV2(
                    payload = payload,
                    frameAad = directFrameAad(frame),
                    recipientStaticPriv = localKeyPair.first,
                    recipientStaticPub = localKeyPair.second,
                    senderStaticPub = peerPublicKey
                ) ?: return null
                val peerKey = peerPublicKeyHex?.lowercase() ?: fastHexEncode(peerPublicKey)
                val replayWindow = directReplayWindows.getOrPut(peerKey) {
                    com.seamless.android.crypto.AegisTorCrypto.ReplayWindow512()
                }
                if (!replayWindow.checkAndMark(opened.counter)) {
                    Log.w(TAG, "Rejected replayed STDP v2 frame ${frame.messageId} (counter=${opened.counter})")
                    return null
                }
                return opened.plaintext
            }

            if (version != DIRECT_SEALED_VERSION ||
                payload.size < DIRECT_SEALED_MAGIC.size + 1 + DIRECT_NONCE_SIZE + 4
            ) {
                return null
            }

            val buffer = ByteBuffer.wrap(payload)
            buffer.position(DIRECT_SEALED_MAGIC.size + 1)

            val nonce = ByteArray(DIRECT_NONCE_SIZE)
            buffer.get(nonce)

            val ciphertextSize = buffer.getInt()
            if (ciphertextSize < 0 || ciphertextSize > buffer.remaining()) return null

            val ciphertext = ByteArray(ciphertextSize)
            buffer.get(ciphertext)

            val sealedHeader = DIRECT_SEALED_MAGIC + byteArrayOf(version)

            aesGcmDecrypt(
                ciphertext = ciphertext,
                key = getOrDeriveDirectTransportKey(peerPublicKey, peerPublicKeyHex),
                nonce = nonce,
                aad = directFrameAad(frame) + sealedHeader
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct sealed payload decrypt failed for ${frame.messageId}: ${e.message}")
            null
        }
    }

    private fun getOrDeriveDirectTransportKey(
        peerPublicKey: ByteArray,
        peerPublicKeyHex: String? = null
    ): ByteArray {
        val cacheKey = peerPublicKeyHex?.lowercase() ?: fastHexEncode(peerPublicKey)
        directTransportKeyCache[cacheKey]?.let { return it }
        val localKeyPair = cachedLocalStaticKeyPair
            ?: SecureIdentityStateManager(context).loadStaticKey()?.also { cachedLocalStaticKeyPair = it }
            ?: throw IllegalStateException("Local static key unavailable")
        // Key is derived once and cached via getOrDeriveDirectTransportKey(peerPublicKey)
        val sharedSecret = X25519.computeSharedSecret(localKeyPair.first, peerPublicKey)
        return deriveDirectTransportKey(sharedSecret, localKeyPair.second, peerPublicKey).also {
            directTransportKeyCache[cacheKey] = it
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
                val encrypted = createDirectSealedPayload(frame, publicKeyBytes, publicKeyHex)
                
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
    
    private fun decryptFrame(senderOnion: String, data: ByteArray): OnionMessageProtocol.OnionFrame {
        val frame = OnionMessageProtocol.OnionFrame.decode(data)
            ?: throw IllegalArgumentException("Invalid Tor frame")

        // Get the sender's public key for decryption
        val contact = contactManager.getContactByOnion(senderOnion)
        val publicKeyHex = contact?.publicKey

        // First try: direct decryption with the known sender's public key.
        if (!publicKeyHex.isNullOrEmpty()) {
            try {
                val publicKeyBytes = hexStringToByteArray(publicKeyHex)
                val decrypted = openDirectSealedPayload(frame, publicKeyBytes, publicKeyHex)
                if (decrypted != null) {
                    return OnionMessageProtocol.OnionFrame(
                        messageId = frame.messageId,
                        sequence = frame.sequence,
                        timestamp = frame.timestamp,
                        frameType = frame.frameType,
                        payload = decrypted
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct Tor frame decryption failed for ${redactEndpoint(senderOnion)}: ${e.message}")
            }
        }

        // Fall back across all contacts when the inbound socket address did not map cleanly.
        if (isDirectSealedPayload(frame.payload)) {
            val allContacts = contactManager.getAllContacts()
            for (candidate in allContacts) {
                val candidateKey = candidate.publicKey
                if (candidateKey.isBlank()) continue
                if (candidateKey.equals(publicKeyHex, ignoreCase = true)) continue
                try {
                    val candidateKeyBytes = hexStringToByteArray(candidateKey)
                    val decrypted = openDirectSealedPayload(frame, candidateKeyBytes, candidateKey)
                    if (decrypted != null) {
                        Log.i(TAG, "🔓 Decrypted via contact fallback: ${candidate.nickname} (onion lookup for '$senderOnion' had failed)")
                        return OnionMessageProtocol.OnionFrame(
                            messageId = frame.messageId,
                            sequence = frame.sequence,
                            timestamp = frame.timestamp,
                            frameType = frame.frameType,
                            payload = decrypted
                        )
                    }
                } catch (_: Exception) {
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
                )
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
        // Register BRP callback unconditionally BEFORE checking pending list so
        // contacts added later in the session also deliver their resolved onion.
        rendezvous.setCallback(object : BrambleRendezvous.RendezvousCallback {
            override fun onRendezvousConnected(peerId: String, peerOnion: String) {
                Log.i(TAG, "✅ BRP connected: $peerId → $peerOnion")
                contactManager.resolveRendezvousAddress(peerId, peerOnion)

                scope.launch {
                    sendContactExchange(peerOnion)
                }
            }

            override fun onRendezvousFailed(peerId: String, reason: String) {
                Log.w(TAG, "❌ BRP failed for $peerId: $reason")
            }
        })

        val brpContacts = contactManager.getRendezvousContacts()
        if (brpContacts.isEmpty()) return

        Log.i(TAG, "🔑 Starting BRP for ${brpContacts.size} pending contacts")

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
                    try {
                        groupMessaging.onTransportAck(messageId)
                    } catch (_: Exception) {}
                    try {
                        messageRepository.updateDeliveryState(
                            messageId,
                            OnionMessageProtocol.DeliveryState.DELIVERED
                        )
                    } catch (_: Exception) {}
                    messageListener?.onDeliveryStatusChanged(
                        messageId,
                        OnionMessageProtocol.DeliveryState.DELIVERED
                    )
                }
            }

            override fun onBatchReceived(peerId: String, frames: List<OnionMessageProtocol.OnionFrame>) {
                Log.i(TAG, "📨 BSP: Received batch of ${frames.size} messages from $peerId")
                scope.launch {
                    for (frame in frames) {
                        // Process each synced message through the normal handler with 7-day offline window
                        handleIncomingData(
                            senderOnion = peerId,
                            data = frame.encode(),
                            isAlreadyDecrypted = true,
                            allowOfflineTimestamp = true
                        )
                    }
                }
            }

            override fun onSyncStateChanged(peerId: String, state: BrambleSyncEngine.SyncState) {
                Log.d(TAG, "🔄 BSP: $peerId → $state")
            }
        })

        // Wire TorCircuitManager to BrambleSyncEngine with pairwise frame encryption
        syncEngine.registerTransport(object : BrambleSyncEngine.BspTransport {
            override suspend fun getConnectedPeers(): List<String> {
                return circuitManager.getConnectedPeers()
            }
            override suspend fun sendData(peerId: String, data: ByteArray): Boolean {
                val rawFrame = OnionMessageProtocol.OnionFrame.decode(data) ?: return false
                val encryptedBytes = runCatching {
                    encryptFrame(peerId, rawFrame.copy(timestamp = System.currentTimeMillis())).encode()
                }.getOrNull() ?: return false
                return circuitManager.sendData(peerId, encryptedBytes)
            }
        })

        syncEngine.startSyncLoop()
        Log.i(TAG, "🔄 Bramble Sync Engine started")
    }

    // ===== Mailbox Protocol =====

    /**
     * Try to deliver a message via the 3-Tier Hybrid Offline Mailbox architecture:
     * - Tier 1: Deposit sealed blob in our local Sender-Hosted Outbox Mailbox so the
     *           recipient can pull it from our .onion mailbox as soon as they wake up
     *           (even if their phone was powered off when we sent the message!).
     * - Tier 2: Push sealed blob to the recipient's remote/self-hosted mailbox (.onion)
     *           if reachable.
     *
     * @return true if the message was stored in Tier 2 remote mailbox or Tier 1 local outbox
     */
    private suspend fun tryMailboxDelivery(
        recipientOnion: String,
        message: OnionMessageQueue.QueuedMessage
    ): Boolean {
        if (mailboxFallbackDisabled.contains(message.id)) {
            return false
        }

        // Look up the contact to find their public key and optional remote mailbox address
        val contact = contactManager.getContactByOnion(recipientOnion) ?: return false

        return try {
            val recipientId = fingerprintForPublicKeyHex(contact.publicKey)
            val payload = createMailboxPayloadForDelivery(contact, message)
            if (payload == null) {
                Log.d(TAG, "Mailbox payload unavailable for ${message.id}")
                return false
            }

            // Tier 1: Always deposit into our local Sender-Hosted Outbox Mailbox so the offline
            // recipient can pull the sealed blob from our mailbox .onion when they come online.
            val depositedLocally = mailboxService.depositLocalBlob(
                recipientId = recipientId,
                payload = payload,
                senderOnion = getMyOnionAddress()
            ) != null
            val localOutboxPublished = depositedLocally && !ownMailboxOnion.isNullOrBlank()

            // Tier 2: Push to recipient's remote mailbox (.onion) if known and online
            val mailboxOnion = contact.mailboxOnion
            val storedRemotely = if (!mailboxOnion.isNullOrBlank()) {
                mailboxClient.storeInMailbox(
                    mailboxOnion = mailboxOnion,
                    recipientId = recipientId,
                    encryptedPayload = payload
                )
            } else {
                false
            }

            val isContactExchange = OnionMessageProtocol.OnionFrame.decode(message.frameData)?.frameType ==
                OnionMessageProtocol.FrameType.CONTACT_EXCHANGE
            // An unpaired peer cannot poll our local outbox until they know we exist,
            // so CONTACT_EXCHANGE must keep retrying direct delivery unless stored remotely.
            val stored = if (isContactExchange) storedRemotely else (storedRemotely || localOutboxPublished)
            if (stored) {
                deliveryRoutes[message.uiMessageId] = TorDeliveryRoute.MAILBOX
                // Only freeze mailbox retries when a remote Tier 2 mailbox accepted the blob;
                // local Tier 1 deposit alone must still allow Tier 2 uploads on retry cycles.
                if (storedRemotely) {
                    messageQueue.markMailboxStored(message.id)
                }
                if (message.completesUiMessage) {
                    messageRepository.updateDelivery(
                        message.uiMessageId,
                        OnionMessageProtocol.DeliveryState.SENT,
                        OnionMessageProtocol.DeliveryRoute.MAILBOX
                    )
                }
                Log.i(
                    TAG,
                    "📬 Message ${message.id} queued in offline mailbox (remote=$storedRemotely, localOutbox=$localOutboxPublished)"
                )

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
     * Registers a contact's mailbox (or fallback to their main .onion on MAILBOX_PORT)
     * in MailboxClient so we automatically poll their Tier 1 Sender-Hosted Outbox Mailbox
     * for messages and ACKs queued for our identity fingerprint while we were offline.
     */
    suspend fun registerContactMailboxForPolling(contact: OnionContactManager.OnionContact) {
        if (!mailboxClient.isInitialized()) {
            mailboxClient.initialize(encryptionService)
        }
        val myFolderId = encryptionService.getIdentityFingerprint().lowercase()
        if (myFolderId.isBlank()) return
        val peerPubBytes = runCatching { hexStringToByteArray(contact.publicKey) }
            .getOrNull()
            ?.takeIf { it.size == 32 }
        val localKeyPair = cachedLocalStaticKeyPair
            ?: SecureIdentityStateManager(context).loadStaticKey()?.also { cachedLocalStaticKeyPair = it }

        if (peerPubBytes != null && localKeyPair != null) {
            val peerFolderId = fingerprintForPublicKeyHex(contact.publicKey)
            runCatching {
                val peerOutboxToken = com.seamless.android.crypto.AegisTorCrypto.derivePairwiseOutboxToken(
                    localStaticPriv = localKeyPair.first,
                    peerStaticPub = peerPubBytes,
                    recipientFolderId = peerFolderId
                )
                mailboxService.registerContactOutboxToken(peerFolderId, peerOutboxToken)
            }
        }

        val outboxToken = if (peerPubBytes != null && localKeyPair != null) {
            runCatching {
                com.seamless.android.crypto.AegisTorCrypto.derivePairwiseOutboxToken(
                    localStaticPriv = localKeyPair.first,
                    peerStaticPub = peerPubBytes,
                    recipientFolderId = myFolderId
                )
            }.getOrElse { mailboxService.deriveContactOutboxToken(myFolderId) }
        } else {
            mailboxService.deriveContactOutboxToken(myFolderId)
        }

        // Register explicit contact.mailboxOnion if present
        val explicitMailbox = contact.mailboxOnion
            ?.let { OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it) }
            ?.takeIf { OnionContactManager.OnionContact.isValidOnionEndpoint(it) }
        if (!explicitMailbox.isNullOrBlank()) {
            mailboxClient.registerContactOutboxMailbox(
                contactMailboxOnion = explicitMailbox,
                accessToken = outboxToken,
                targetFolderId = myFolderId
            )
        }
    }

    /**
     * Handle incoming MAILBOX_EXCHANGE frame from a peer.
     * The peer is sharing their mailbox .onion address so we can deliver
     * messages to them even when they're offline and poll their Tier 1 outbox.
     */
    private suspend fun handleMailboxExchange(
        senderOnion: String,
        frame: OnionMessageProtocol.OnionFrame
    ): Boolean {
        val exchange = OnionMessageProtocol.MailboxExchange.fromBytes(frame.payload)
        if (exchange == null) {
            Log.w(TAG, "Failed to parse mailbox exchange from ${redactEndpoint(senderOnion)}")
            return false
        }

        Log.i(TAG, "📬 Peer ${redactEndpoint(senderOnion)} shared mailbox: ${redactEndpoint(exchange.mailboxOnion)}")

        // Update the contact's mailbox address and register for Tier 1 outbox polling
        val contact = contactManager.getContactByOnion(senderOnion)
        if (contact == null) {
            // Do not acknowledge an endpoint that cannot yet be bound to the
            // authenticated contact exchange. The sender will retry after the
            // pairing frame has been processed.
            Log.w(TAG, "Deferred mailbox exchange from unknown contact ${redactEndpoint(senderOnion)}")
            return false
        }
        val endpoint = OnionContactManager.OnionContact
            .canonicalizeOnionEndpoint(exchange.mailboxOnion)
        if (!OnionContactManager.OnionContact.isValidOnionEndpoint(endpoint)) {
            Log.w(TAG, "Rejected invalid mailbox endpoint from ${redactEndpoint(senderOnion)}")
            return false
        }
        contactManager.updateMailboxAddress(contact.id, endpoint)
        val updated = contactManager.getContact(contact.id) ?: contact.copy(mailboxOnion = endpoint)
        registerContactMailboxForPolling(updated)
        // A peer may have queued mail while we were disconnected. Fetch it
        // now; do not add a needless periodic-poll delay after a successful,
        // authenticated endpoint exchange.
        mailboxClient.requestImmediatePoll()
        return true
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

        // Persist the sealed blob in our local MailboxService
        val storedId = mailboxService.depositLocalBlob(
            recipientId = recipientFolderId,
            payload = encryptedPayload,
            senderOnion = senderOnion
        )
        if (storedId != null) {
            Log.d(TAG, "📬 Stored relayed message $storedId in local mailbox for ${recipientFolderId.take(12)}...")
        }
    }

    /**
     * Start polling registered mailboxes for offline messages.
     * This is the receiving side of the store-and-forward architecture.
     *
     * Flow: Peer sends → Peer's Mailbox / Outbox stores → We poll → We receive
     */
    private fun startMailboxPolling() {
        // Initialize mailbox client with our encryption service
        if (!mailboxClient.isInitialized()) {
            mailboxClient.initialize(encryptionService)
        }

        // Register all existing contacts' mailboxes for Tier 1 outbox polling
        scope.launch {
            for (contact in contactManager.getAllContacts()) {
                registerContactMailboxForPolling(contact)
            }
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
        val mailboxEndpoint = ownMailboxOnion ?: contactManager.getMyMailboxOnion()
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

            // Queue the setup frame just like user traffic. A direct write can
            // be lost during a Tor reconnect, leaving both sides with a
            // "mailbox ready" status but no route for offline delivery.
            // The receiver ACKs only after it persists and registers the
            // endpoint for polling.
            messageQueue.enqueue(
                recipientOnion = recipientOnion,
                frame = frame,
                completesUiMessage = false
            )
            wakeQueueDispatcher()
            Log.i(TAG, "📬 Queued mailbox exchange to ${redactEndpoint(recipientOnion)}")
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
        contactManager.setMyMailboxOnion(endpoint)
        mailboxClient.registerLocalMailbox(endpoint, mailboxAccessToken)
        for (contact in contactManager.getAllContacts()) {
            registerContactMailboxForPolling(contact)
        }
        Log.i(TAG, "Mailbox ready at ${redactEndpoint(endpoint)}")
    }

    /**
     * Re-publish the mailbox hidden service after Tor reconnects. The private
     * key is retained by TorDaemonManager, so the endpoint remains stable when
     * possible; registering again also refreshes polling after a service reset.
     */
    suspend fun refreshMailboxHost() {
        if (_serviceState.value !in setOf(ServiceState.WAITING_FOR_TOR, ServiceState.RUNNING)) {
            return
        }
        try {
            startMailboxHost()
        } catch (e: Exception) {
            Log.w(TAG, "Mailbox refresh failed: ${e.message}")
        }
    }

    private fun getOrCreateMailboxAccessToken(): String {
        val ownerFingerprint = encryptionService.getIdentityFingerprint()
        val storageKey = "tor_mailbox_access_${ownerFingerprint.take(16)}"
        val secureState = SecureIdentityStateManager(context)
        secureState.getSecureValue(storageKey)?.takeIf { it.isNotBlank() }?.let { return it }

        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        ).also { secureState.storeSecureValue(storageKey, it) }
    }
}
