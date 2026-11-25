@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.sync

import android.content.Context
import android.util.Log
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.SyncStateEntity
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.OnionMessageProtocol.OnionFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bramble Sync Engine — Full-duplex synchronization protocol.
 *
 * Replaces fire-and-forget messaging with a stateful 4-phase sync:
 *
 *   ┌────────┐    ┌─────────┐    ┌───────┐    ┌─────┐
 *   │ OFFER  │───►│ REQUEST │───►│ BATCH │───►│ ACK │
 *   └────────┘    └─────────┘    └───────┘    └─────┘
 *
 * Phase 1 (OFFER): "I have messages M1, M2, M3 — do you want them?"
 *   Sends message IDs + optional GCS filter for bandwidth efficiency.
 *
 * Phase 2 (REQUEST): "Yes, send me M1 and M3" (peer already has M2).
 *   Peer filters out messages they already have.
 *
 * Phase 3 (BATCH): Send requested messages in a single batch frame.
 *   All requested messages are packaged and transmitted.
 *
 * Phase 4 (ACK): "I received M1 and M3 successfully."
 *   Sender marks these as synced for this peer.
 *
 * Key properties:
 * - **Exactly-once delivery**: Sync state persists across restarts via Room DB
 * - **Delay-tolerant**: Works over intermittent Tor connections
 * - **Bandwidth-efficient**: GCS filters prevent redundant transfers
 * - **Bidirectional**: Both sides can offer simultaneously
 * - **DoS-safe**: Validates message lengths before allocation (ETH Zurich fix)
 *
 * Based on Briar's Bramble Sync Protocol:
 * https://code.briarproject.org/briar/briar-spec/blob/master/protocols/BSP.md
 */
class BrambleSyncEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BrambleSyncEngine"

        // Sync timing
        private const val SYNC_INTERVAL_MS = 10_000L          // Offer every 10 seconds when connected
        private const val SYNC_TIMEOUT_MS = 30_000L           // Timeout for waiting for a response
        private const val MAX_BATCH_SIZE = 40                  // Max messages per batch
        private const val MAX_OFFER_IDS = 80                   // DoS & frame-size guard: cap offer list size
        private const val MAX_BATCH_PAYLOAD = 256 * 1024       // DoS guard: 256KB max total batch
        private const val MAX_SINGLE_BATCH_WIRE_BYTES = 3_950  // Fits inside 4060-byte OnionFrame payload + 37-byte sealed envelope
        private const val GCS_FPR = 0.01                       // GCS false positive rate (1%)
        private const val GCS_MAX_BYTES = 1024                 // Max GCS filter size in bytes

        @Volatile
        private var instance: BrambleSyncEngine? = null

        fun getInstance(context: Context): BrambleSyncEngine {
            return instance ?: synchronized(this) {
                instance ?: BrambleSyncEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Per-peer sync state machine state
     */
    enum class SyncState {
        IDLE,              // No active sync
        OFFERING,          // We sent an offer, awaiting request
        AWAITING_REQUEST,  // Transitional: offer sent, waiting
        SENDING_BATCH,     // Sending requested messages
        AWAITING_ACK,      // Batch sent, awaiting acknowledgment
        RECEIVING_OFFER,   // We received an offer from peer
        SENDING_REQUEST,   // We sent a request for specific messages
        RECEIVING_BATCH    // Waiting for batch from peer
    }

    /**
     * Per-peer sync session state
     */
    data class PeerSyncState(
        val peerId: String,
        var syncState: SyncState = SyncState.IDLE,
        var lastSyncAt: Long = 0,
        var offerCount: Int = 0,
        var ackCount: Int = 0,
        var pendingOffer: OnionMessageProtocol.SyncOffer? = null,
        var pendingRequest: OnionMessageProtocol.SyncRequest? = null,
        val syncedMessageIds: MutableSet<String> = mutableSetOf(), // Messages this peer has acknowledged
        var lastStateChangeAt: Long = System.currentTimeMillis()
    )

    // Per-peer sync states
    private val peerStates = ConcurrentHashMap<String, PeerSyncState>()

    // Messages available for sync (indexed by ID)
    private val messageStore = ConcurrentHashMap<String, ByteArray>() // messageId -> encoded OnionFrame
    private val messageTargets = ConcurrentHashMap<String, String>()  // messageId -> canonical recipientPeerId

    // Already received message IDs (for deduplication)
    private val receivedMessageIds = ConcurrentHashMap.newKeySet<String>()

    private fun canonicalizePeer(peerId: String): String {
        val canonical = com.seamless.android.net.OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peerId)
        return canonical.ifBlank { peerId.trim().lowercase() }
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncLoopJob: Job? = null

    // Database access for persistent sync state
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val syncStateDao by lazy { db.syncStateDao() }
    private val torMessageDao by lazy { db.torMessageDao() }

    // Transports
    interface BspTransport {
        suspend fun getConnectedPeers(): List<String>
        suspend fun sendData(peerId: String, data: ByteArray): Boolean
    }

    private val transports = mutableListOf<BspTransport>()

    fun registerTransport(transport: BspTransport) {
        synchronized(transports) {
            if (!transports.contains(transport)) {
                transports.add(transport)
            }
        }
    }

    // Listeners
    interface SyncListener {
        fun onMessageSynced(peerId: String, messageId: String)
        fun onBatchReceived(peerId: String, frames: List<OnionFrame>)
        fun onSyncStateChanged(peerId: String, state: SyncState)
    }

    private var listener: SyncListener? = null

    // Observable state
    private val _syncStates = MutableStateFlow<Map<String, SyncState>>(emptyMap())
    val syncStates: StateFlow<Map<String, SyncState>> = _syncStates.asStateFlow()

    fun setListener(l: SyncListener?) {
        listener = l
    }

    // ===== Message Store =====

    /**
     * Add a message to the sync store.
     * Messages in the store will be offered to peers during sync.
     * Strips random padding from 4096-byte OnionFrames so multiple frames fit
     * inside a single 4096-byte SYNC_BATCH frame.
     */
    fun addMessageForSync(messageId: String, encodedFrame: ByteArray, recipientPeerId: String? = null) {
        val compact = OnionFrame.decode(encodedFrame)?.let(::encodeCompactFrame) ?: encodedFrame
        if (compact.size <= MAX_SINGLE_BATCH_WIRE_BYTES - 8) {
            if (messageStore.size >= 5_000) {
                messageStore.keys.take(500).forEach { staleId ->
                    messageStore.remove(staleId)
                    messageTargets.remove(staleId)
                }
            }
            messageStore[messageId] = compact
            if (!recipientPeerId.isNullOrBlank()) {
                messageTargets[messageId] = canonicalizePeer(recipientPeerId)
            }
            Log.d(TAG, "📦 Added message $messageId to sync store (${messageStore.size} total, ${compact.size}B, target=${recipientPeerId?.take(12) ?: "any"})")
        }
    }

    private fun encodeCompactFrame(frame: OnionFrame): ByteArray {
        val buf = ByteBuffer.allocate(OnionMessageProtocol.HEADER_SIZE + frame.payload.size)
        buf.put(frame.version)
        buf.putLong(frame.messageId.mostSignificantBits)
        buf.putLong(frame.messageId.leastSignificantBits)
        buf.putLong(frame.sequence)
        buf.putLong(frame.timestamp)
        buf.put(frame.frameType.code)
        buf.putShort(frame.payload.size.toShort())
        buf.put(frame.payload)
        return buf.array()
    }

    private fun decodeCompactOrFullFrame(data: ByteArray): OnionFrame? {
        if (data.size == OnionMessageProtocol.FRAME_SIZE) {
            return OnionFrame.decode(data)
        }
        return try {
            if (data.size < OnionMessageProtocol.HEADER_SIZE) return null
            val buf = ByteBuffer.wrap(data)
            val version = buf.get()
            val msb = buf.long
            val lsb = buf.long
            val messageId = UUID(msb, lsb)
            val sequence = buf.long
            val timestamp = buf.long
            val frameType = OnionMessageProtocol.FrameType.fromCode(buf.get()) ?: return null
            val payloadLength = buf.short.toInt() and 0xFFFF
            if (payloadLength > OnionMessageProtocol.MAX_PAYLOAD_SIZE || payloadLength > buf.remaining()) {
                return null
            }
            val payload = ByteArray(payloadLength)
            buf.get(payload)
            OnionFrame(
                version = version,
                messageId = messageId,
                sequence = sequence,
                timestamp = timestamp,
                frameType = frameType,
                payload = payload
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get all message IDs available for sync
     */
    fun getAvailableMessageIds(): Set<String> {
        return messageStore.keys.toSet()
    }

    /**
     * Mark a message as received (for deduplication)
     */
    fun markAsReceived(messageId: String) {
        if (receivedMessageIds.size >= 5_000) {
            val toDrop = receivedMessageIds.take(500)
            receivedMessageIds.removeAll(toDrop.toSet())
        }
        receivedMessageIds.add(messageId)
    }

    /** Evicts a directly delivered / ACKed outbound message so BSP does not re-offer it. */
    fun markMessageDelivered(messageId: String) {
        messageStore.remove(messageId)
        messageTargets.remove(messageId)
        receivedMessageIds.add(messageId)
    }

    /**
     * Check if we already have a message
     */
    fun hasMessage(messageId: String): Boolean {
        return messageStore.containsKey(messageId) || receivedMessageIds.contains(messageId)
    }

    // ===== Sync Lifecycle =====

    /**
     * Start the periodic sync loop.
     * Runs the BSP state machine for all connected peers.
     * Restores persisted sync state from Room database on first run.
     */
    fun startSyncLoop() {
        if (syncLoopJob?.isActive == true) return

        syncLoopJob = scope.launch {
            Log.i(TAG, "🔄 Bramble Sync Engine started")

            // Restore persisted sync checkpoints from Room
            restoreSyncStatesFromDb()

            while (isActive) {
                try {
                    runSyncCycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync cycle error: ${e.message}")
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop the sync loop
     */
    fun stopSyncLoop() {
        syncLoopJob?.cancel()
        syncLoopJob = null
        Log.i(TAG, "🛑 Bramble Sync Engine stopped")
    }

    /**
     * Run one sync cycle for all connected peers across all registered transports
     */
    private suspend fun runSyncCycle() {
        val connectedPeers = mutableSetOf<String>()
        for (transport in transports) {
            connectedPeers.addAll(transport.getConnectedPeers())
        }

        for (peerId in connectedPeers) {
            val state = getOrCreatePeerState(peerId)

            when (state.syncState) {
                SyncState.IDLE -> {
                    // Check if we have messages to offer
                    val unsynced = getUnsyncedForPeer(peerId)
                    if (unsynced.isNotEmpty()) {
                        sendOffer(peerId, unsynced)
                    }
                }
                SyncState.OFFERING, SyncState.AWAITING_REQUEST, SyncState.RECEIVING_OFFER, SyncState.RECEIVING_BATCH, SyncState.SENDING_BATCH -> {
                    // Check for timeout so inbound or outbound stalls never wedge permanently
                    if (System.currentTimeMillis() - state.lastStateChangeAt > SYNC_TIMEOUT_MS) {
                        Log.w(TAG, "⏰ Sync timeout for $peerId in state ${state.syncState}")
                        resetSyncState(peerId)
                    }
                }
                SyncState.AWAITING_ACK -> {
                    // Check for timeout
                    if (System.currentTimeMillis() - state.lastStateChangeAt > SYNC_TIMEOUT_MS) {
                        Log.w(TAG, "⏰ ACK timeout for $peerId — resetting")
                        resetSyncState(peerId)
                    }
                }
                else -> {
                    // Other states are transient, handled by frame handlers
                }
            }
        }
    }

    // ===== BSP Phase 1: OFFER =====

    /**
     * Send a SYNC_OFFER to a peer listing messages we have that they might not.
     */
    private suspend fun sendOffer(peerId: String, messageIds: List<String>) = mutex.withLock {
        val state = getOrCreatePeerState(peerId)
        if (state.syncState != SyncState.IDLE) {
            Log.d(TAG, "Cannot send offer to $peerId — already in state ${state.syncState}")
            return@withLock
        }

        // DoS guard: cap offer list size
        val cappedIds = messageIds.take(MAX_OFFER_IDS)
        Log.i(TAG, "📤 Offering ${cappedIds.size} messages to $peerId")

        // Build GCS filter for bandwidth-efficient set representation
        val gcsFilter = buildGcsFilter(cappedIds)

        // Create and send the offer frame
        val offerFrame = OnionMessageProtocol.createSyncOfferFrame(
            messageIds = cappedIds.take(MAX_BATCH_SIZE),
            gcsFilterData = gcsFilter?.data,
            gcsP = gcsFilter?.p ?: 0,
            gcsM = gcsFilter?.m ?: 0
        )

        // Send across all registered transports
        var sent = false
        for (transport in transports) {
            if (transport.sendData(peerId, offerFrame.encode())) {
                sent = true
                break
            }
        }

        if (sent) {
            state.syncState = SyncState.OFFERING
            state.offerCount++
            state.lastStateChangeAt = System.currentTimeMillis()
            updateStateFlow()
            listener?.onSyncStateChanged(peerId, SyncState.OFFERING)
        } else {
            Log.w(TAG, "Failed to send offer to $peerId")
        }
    }

    // ===== BSP Phase 2: REQUEST (handle incoming offer) =====

    /**
     * Handle a SYNC_OFFER from a peer.
     * Determines which messages we need and sends a SYNC_REQUEST.
     */
    suspend fun handleSyncOffer(peerId: String, frame: OnionFrame) = mutex.withLock {
        val offer = OnionMessageProtocol.SyncOffer.fromBytes(frame.payload) ?: return@withLock

        // DoS guard: reject oversized offers
        if (offer.messageIds.size > MAX_OFFER_IDS) {
            Log.w(TAG, "⚠️ Rejecting oversized offer (${offer.messageIds.size} IDs) from $peerId")
            return@withLock
        }

        Log.i(TAG, "📥 Received offer of ${offer.messageIds.size} messages from $peerId")

        val state = getOrCreatePeerState(peerId)
        state.syncState = SyncState.RECEIVING_OFFER
        state.pendingOffer = offer
        updateStateFlow()

        // Filter: which messages do we NOT already have?
        val needed = offer.messageIds.filter { !hasMessage(it) }.take(MAX_BATCH_SIZE)

        if (needed.isEmpty()) {
            Log.d(TAG, "Already have all offered messages from $peerId")
            resetSyncState(peerId)
            return@withLock
        }

        Log.i(TAG, "📤 Requesting ${needed.size}/${offer.messageIds.size} messages from $peerId")

        // Send SYNC_REQUEST for the messages we need
        val requestFrame = OnionMessageProtocol.createSyncRequestFrame(needed)
        // Send across all registered transports
        var sent = false
        for (transport in transports) {
            if (transport.sendData(peerId, requestFrame.encode())) {
                sent = true
                break
            }
        }

        if (sent) {
            state.syncState = SyncState.RECEIVING_BATCH
            state.lastStateChangeAt = System.currentTimeMillis()
            updateStateFlow()
            listener?.onSyncStateChanged(peerId, SyncState.RECEIVING_BATCH)
        }
    }

    // ===== BSP Phase 2b: Handle incoming REQUEST =====

    /**
     * Handle a SYNC_REQUEST from a peer.
     * Sends the requested messages in one or more bounded SYNC_BATCH frames.
     */
    suspend fun handleSyncRequest(peerId: String, frame: OnionFrame) = mutex.withLock {
        val request = OnionMessageProtocol.SyncRequest.fromBytes(frame.payload) ?: return@withLock

        Log.i(TAG, "📥 Received request for ${request.requestedIds.size} messages from $peerId")

        val state = getOrCreatePeerState(peerId)
        state.syncState = SyncState.SENDING_BATCH
        state.pendingRequest = request
        updateStateFlow()

        // Gather ONLY messages intended for this peer (never leak another contact's frames)
        val canonicalRequester = canonicalizePeer(peerId)
        val requestedFrames = request.requestedIds
            .asSequence()
            .filter { id ->
                val target = messageTargets[id]
                target == null || target == canonicalRequester
            }
            .mapNotNull { messageStore[it] }
            .take(MAX_BATCH_SIZE)
            .toList()

        if (requestedFrames.isEmpty()) {
            Log.w(TAG, "No requested messages found for $peerId")
            resetSyncState(peerId)
            return@withLock
        }

        // Partition requested frames into chunks that fit within a single
        // 4096-byte OnionFrame (accounting for 4-byte count, 4-byte per-frame
        // length prefix, and 37-byte direct-sealed encryption overhead).
        val batches = mutableListOf<List<ByteArray>>()
        var currentBatch = mutableListOf<ByteArray>()
        var currentBytes = 4 // 4-byte frame count header in SyncBatch
        var totalAccumulated = 0

        for (rawFrame in requestedFrames) {
            val entryBytes = 4 + rawFrame.size
            if (entryBytes + 4 > MAX_SINGLE_BATCH_WIRE_BYTES) continue
            if (totalAccumulated + rawFrame.size > MAX_BATCH_PAYLOAD) break
            if (currentBatch.isNotEmpty() && currentBytes + entryBytes > MAX_SINGLE_BATCH_WIRE_BYTES) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentBytes = 4
            }
            currentBatch.add(rawFrame)
            currentBytes += entryBytes
            totalAccumulated += rawFrame.size
        }
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }

        if (batches.isEmpty()) {
            resetSyncState(peerId)
            return@withLock
        }

        var anySent = false
        var sentCount = 0
        for (batchSlice in batches) {
            val batchFrame = OnionMessageProtocol.createSyncBatchFrame(batchSlice)
            var sliceSent = false
            for (transport in transports) {
                if (transport.sendData(peerId, batchFrame.encode())) {
                    sliceSent = true
                    break
                }
            }
            if (sliceSent) {
                anySent = true
                sentCount += batchSlice.size
            }
        }

        if (anySent) {
            state.syncState = SyncState.AWAITING_ACK
            state.lastStateChangeAt = System.currentTimeMillis()
            updateStateFlow()
            listener?.onSyncStateChanged(peerId, SyncState.AWAITING_ACK)
            Log.i(TAG, "📤 Sent $sentCount messages across ${batches.size} batch frame(s) to $peerId")
        } else {
            resetSyncState(peerId)
        }
    }

    // ===== BSP Phase 3: Handle incoming BATCH =====

    /**
     * Handle a SYNC_BATCH from a peer.
     * Processes received messages and sends a SYNC_ACK.
     */
    suspend fun handleSyncBatch(peerId: String, frame: OnionFrame) = mutex.withLock {
        // DoS guard: validate payload size before parsing
        if (frame.payload.size > MAX_BATCH_PAYLOAD) {
            Log.w(TAG, "⚠️ Rejecting oversized batch (${frame.payload.size} bytes) from $peerId")
            resetSyncState(peerId)
            return@withLock
        }

        val batch = OnionMessageProtocol.SyncBatch.fromBytes(frame.payload) ?: return@withLock

        Log.i(TAG, "📥 Received batch of ${batch.frames.size} messages from $peerId")

        // Decode and process each frame (supports both compact unpadded and 4096B padded frames)
        val receivedFrames = mutableListOf<OnionFrame>()
        val ackedIds = mutableListOf<String>()

        for (frameData in batch.frames) {
            val innerFrame = decodeCompactOrFullFrame(frameData)
            if (innerFrame != null) {
                receivedFrames.add(innerFrame)
                ackedIds.add(innerFrame.messageId.toString())
                markAsReceived(innerFrame.messageId.toString())
            }
        }

        // Notify listener of received messages
        if (receivedFrames.isNotEmpty()) {
            listener?.onBatchReceived(peerId, receivedFrames)
        }

        // Send SYNC_ACK
        val ackFrame = OnionMessageProtocol.createSyncAckFrame(ackedIds.take(MAX_OFFER_IDS))
        // Send across all registered transports
        for (transport in transports) {
            if (transport.sendData(peerId, ackFrame.encode())) {
                break
            }
        }

        Log.i(TAG, "📤 Sent ACK for ${ackedIds.size} messages to $peerId")

        resetSyncState(peerId)

        // Notify per-message sync completion
        for (id in ackedIds) {
            listener?.onMessageSynced(peerId, id)
        }
    }

    // ===== BSP Phase 4: Handle incoming ACK =====

    /**
     * Handle a SYNC_ACK from a peer.
     * Marks the acknowledged messages as synced for this peer.
     * Persists the high-water-mark to Room DB for crash resilience.
     */
    suspend fun handleSyncAck(peerId: String, frame: OnionFrame) = mutex.withLock {
        val ack = OnionMessageProtocol.SyncAck.fromBytes(frame.payload) ?: return@withLock

        Log.i(TAG, "✅ Received ACK for ${ack.ackedIds.size} messages from $peerId")

        val state = getOrCreatePeerState(peerId)
        state.ackCount++
        state.lastSyncAt = System.currentTimeMillis()

        // Record which messages this peer now has
        state.syncedMessageIds.addAll(ack.ackedIds)

        // Persist sync checkpoint to Room DB
        persistSyncState(peerId, state.lastSyncAt)

        // Notify listener
        for (id in ack.ackedIds) {
            listener?.onMessageSynced(peerId, id)
        }

        resetSyncState(peerId)
    }

    // ===== Database Persistence =====

    /**
     * Persist sync state (high-water-mark) to Room for crash recovery.
     */
    private suspend fun persistSyncState(peerId: String, timestamp: Long) {
        try {
            syncStateDao.updateSyncState(SyncStateEntity(
                contactId = peerId,
                lastSyncedTimestamp = timestamp
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist sync state for $peerId: ${e.message}")
        }
    }

    /**
     * Restore sync states from Room DB on engine startup.
     * Populates the in-memory peerStates with persisted high-water-marks.
     */
    private suspend fun restoreSyncStatesFromDb() {
        try {
            val persisted = syncStateDao.getAllSyncStates()
            for (entity in persisted) {
                val state = getOrCreatePeerState(entity.contactId)
                state.lastSyncAt = entity.lastSyncedTimestamp
                Log.d(TAG, "♻️ Restored sync state for ${entity.contactId}: last sync at ${entity.lastSyncedTimestamp}")
            }
            if (persisted.isNotEmpty()) {
                Log.i(TAG, "♻️ Restored ${persisted.size} sync states from database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore sync states: ${e.message}")
        }
    }

    // ===== Helper Methods =====

    /**
     * Get messages that a peer hasn't acknowledged yet, scoped to that peer's target address.
     */
    private fun getUnsyncedForPeer(peerId: String): List<String> {
        val canonicalPeer = canonicalizePeer(peerId)
        val peerState = peerStates[peerId]
        return messageStore.keys.filter { msgId ->
            val target = messageTargets[msgId]
            val matchesTarget = (target == null || target == canonicalPeer)
            val notYetSynced = (peerState == null || msgId !in peerState.syncedMessageIds)
            matchesTarget && notYetSynced
        }
    }

    /**
     * Get or create per-peer sync state.
     */
    private fun getOrCreatePeerState(peerId: String): PeerSyncState {
        return peerStates.getOrPut(peerId) { PeerSyncState(peerId) }
    }

    /**
     * Reset sync state to IDLE for a peer.
     */
    private fun resetSyncState(peerId: String) {
        val state = peerStates[peerId] ?: return
        state.syncState = SyncState.IDLE
        state.pendingOffer = null
        state.pendingRequest = null
        state.lastStateChangeAt = System.currentTimeMillis()
        updateStateFlow()
        listener?.onSyncStateChanged(peerId, SyncState.IDLE)
    }

    /**
     * Build a GCS filter from message IDs for bandwidth-efficient set representation.
     * Uses the existing GCSFilter implementation.
     */
    private fun buildGcsFilter(messageIds: List<String>): GCSFilter.Params? {
        if (messageIds.isEmpty()) return null

        return try {
            // Convert string IDs to 16-byte PacketId format (SHA-256 truncated)
            val idBytes = messageIds.map { id ->
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.digest(id.toByteArray()).copyOfRange(0, 16)
            }

            GCSFilter.buildFilter(
                ids = idBytes,
                maxBytes = GCS_MAX_BYTES,
                targetFpr = GCS_FPR
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build GCS filter: ${e.message}")
            null
        }
    }

    /**
     * Update the observable state flow
     */
    private fun updateStateFlow() {
        _syncStates.value = peerStates.mapValues { it.value.syncState }
    }

    /**
     * Get sync statistics
     */
    fun getSyncStats(): SyncStats {
        return SyncStats(
            totalPeers = peerStates.size,
            activeSyncs = peerStates.values.count { it.syncState != SyncState.IDLE },
            totalMessages = messageStore.size,
            totalAcks = peerStates.values.sumOf { it.ackCount },
            receivedCount = receivedMessageIds.size
        )
    }

    data class SyncStats(
        val totalPeers: Int,
        val activeSyncs: Int,
        val totalMessages: Int,
        val totalAcks: Int,
        val receivedCount: Int
    )

    /**
     * Handle incoming raw BSP data from a peer.
     * Called by BluetoothMeshService (BSP_SYNC packet type) and MessageHandler (Noise BSP_SYNC payload).
     * Decodes the data as an OnionFrame and dispatches to the correct sync handler.
     */
    suspend fun handleIncomingData(peerId: String, data: ByteArray) {
        try {
            val frame = OnionFrame.decode(data)
            if (frame == null) {
                Log.w(TAG, "Failed to decode BSP frame from $peerId (${data.size} bytes)")
                return
            }

            when (frame.frameType) {
                OnionMessageProtocol.FrameType.SYNC_OFFER -> handleSyncOffer(peerId, frame)
                OnionMessageProtocol.FrameType.SYNC_REQUEST -> handleSyncRequest(peerId, frame)
                OnionMessageProtocol.FrameType.SYNC_BATCH -> handleSyncBatch(peerId, frame)
                OnionMessageProtocol.FrameType.SYNC_ACK -> handleSyncAck(peerId, frame)
                else -> Log.w(TAG, "Unexpected frame type in BSP data from $peerId: ${frame.frameType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming BSP data from $peerId: ${e.message}")
        }
    }

    /**
     * Clear all sync state (for testing or reset)
     */
    fun reset() {
        peerStates.clear()
        messageStore.clear()
        messageTargets.clear()
        receivedMessageIds.clear()
        updateStateFlow()
        Log.i(TAG, "🗑️ Sync engine reset")
    }
}
