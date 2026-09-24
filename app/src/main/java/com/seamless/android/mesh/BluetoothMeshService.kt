@file:Suppress("KotlinConstantConditions")
package com.seamless.android.mesh

import android.content.Context
import android.util.Log
import com.seamless.android.crypto.EncryptionService
import com.seamless.android.model.BitchatMessage
import com.seamless.android.protocol.MessagePadding
import com.seamless.android.util.hexToByteArray
import com.seamless.android.model.RoutedPacket
import com.seamless.android.model.IdentityAnnouncement
import com.seamless.android.protocol.BitchatPacket
import com.seamless.android.protocol.MessageType
import com.seamless.android.protocol.SpecialRecipients
import com.seamless.android.model.RequestSyncPacket
import com.seamless.android.sync.GossipSyncManager
import com.seamless.android.util.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.math.sign
import kotlin.random.Random

/**
 * Bluetooth mesh service - REFACTORED to use component-based architecture
 * 100% compatible with iOS version and maintains exact same UUIDs, packet format, and protocol logic
 * 
 * This is now a coordinator that orchestrates the following components:
 * - PeerManager: Peer lifecycle management
 * - FragmentManager: Message fragmentation and reassembly  
 * - SecurityManager: Security, duplicate detection, encryption
 * - StoreForwardManager: Offline message caching
 * - MessageHandler: Message type processing and relay logic
 * - BluetoothConnectionManager: BLE connections and GATT operations
 * - PacketProcessor: Incoming packet routing
 */
class BluetoothMeshService(private val context: Context) {
    private val debugManager by lazy { try { com.seamless.android.ui.debug.DebugSettingsManager.getInstance() } catch (e: Exception) { null } }
    
    companion object {
        private const val TAG = "BluetoothMeshService"
        private val MAX_TTL: UByte = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS

        @Volatile
        private var instance: BluetoothMeshService? = null

        /**
         * Get the current BluetoothMeshService instance, if initialized.
         * Set by MeshForegroundService during lifecycle.
         */
        fun getInstance(): BluetoothMeshService? = instance

        internal fun setInstance(service: BluetoothMeshService?) {
            instance = service
        }
    }
    
    // Core components - each handling specific responsibilities
    private val encryptionService = EncryptionService(context)

    // My peer identification - derived from persisted Noise identity fingerprint (first 16 hex chars)
    val myPeerID: String = encryptionService.getIdentityFingerprint().take(16)
    private val peerManager = PeerManager()
    private val fragmentManager = FragmentManager()
    private val securityManager = SecurityManager(encryptionService, myPeerID)
    private val storeForwardManager = StoreForwardManager(
        com.seamless.android.data.AppDatabase.getDatabase(context.applicationContext).relayDao()
    )
    private val messageHandler = MessageHandler(myPeerID, context.applicationContext)
    internal val connectionManager = BluetoothConnectionManager(context, myPeerID, fragmentManager) // Made internal for access
    private val packetProcessor = PacketProcessor(myPeerID)
    private lateinit var gossipSyncManager: GossipSyncManager
    
    // Service state management
    var isActive = false
        private set
    
    // PRODUCTION FIX: Pending message queue for messages sent before Noise handshake completes
    private data class PendingPrivateMessage(
        val content: String,
        val recipientPeerID: String,
        val recipientNickname: String,
        val messageID: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val pendingMessagesQueue = mutableMapOf<String, MutableList<PendingPrivateMessage>>()
    
    // PRODUCTION FIX: Pending file queue for files sent before Noise handshake completes
    private data class PendingPrivateFile(
        val filePacket: com.seamless.android.model.BitchatFilePacket,
        val recipientPeerID: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val pendingFilesQueue = mutableMapOf<String, MutableList<PendingPrivateFile>>()
    
    // PRODUCTION FIX: Pending mesh message queue for private mesh messages
    private data class PendingMeshMessage(
        val meshId: String,
        val content: String,
        val recipientPeerID: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val pendingMeshMessagesQueue = mutableMapOf<String, MutableList<PendingMeshMessage>>()
    
    // Delegate for message callbacks (maintains same interface)
    var delegate: BluetoothMeshDelegate? = null
    
    // Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        setupDelegates()
        messageHandler.packetProcessor = packetProcessor
        //startPeriodicDebugLogging()

        // Initialize sync manager (needs serviceScope)
        gossipSyncManager = GossipSyncManager(
            myPeerID = myPeerID,
            scope = serviceScope,
            configProvider = object : GossipSyncManager.ConfigProvider {
                override fun seenCapacity(): Int = try {
                    com.seamless.android.ui.debug.DebugPreferenceManager.getSeenPacketCapacity(500)
                } catch (_: Exception) { 500 }

                override fun gcsMaxBytes(): Int = try {
                    com.seamless.android.ui.debug.DebugPreferenceManager.getGcsMaxFilterBytes(400)
                } catch (_: Exception) { 400 }

                override fun gcsTargetFpr(): Double = try {
                    com.seamless.android.ui.debug.DebugPreferenceManager.getGcsFprPercent(1.0) / 100.0
                } catch (_: Exception) { 0.01 }
            }
        )

        // Wire sync manager delegate
        gossipSyncManager.delegate = object : GossipSyncManager.Delegate {
            override fun sendPacket(packet: BitchatPacket) {
                connectionManager.broadcastPacket(RoutedPacket(packet))
            }
            override fun sendPacketToPeer(peerID: String, packet: BitchatPacket) {
                connectionManager.sendPacketToPeer(peerID, packet)
            }
            override fun signPacketForBroadcast(packet: BitchatPacket): BitchatPacket {
                return signPacketBeforeBroadcast(packet)
            }
        }

        // Wire BrambleSyncEngine for Tor+BLE integration
        com.seamless.android.sync.BrambleSyncEngine.getInstance(context).registerTransport(object : com.seamless.android.sync.BrambleSyncEngine.BspTransport {
            override suspend fun getConnectedPeers(): List<String> {
                // BLE peer aliases are their addresses/pubkeys
                // getPeerNicknames gives us all active peers in the mesh
                return peerManager.getAllPeerNicknames().keys.toList()
            }

            override suspend fun sendData(peerId: String, data: ByteArray): Boolean {
                val packet = BitchatPacket(
                    version = 1u,
                    type = MessageType.BSP_SYNC.value,
                    senderID = hexStringToByteArray(myPeerID),
                    recipientID = hexStringToByteArray(peerId),
                    timestamp = System.currentTimeMillis().toULong(),
                    payload = data,
                    ttl = MAX_TTL
                )

                // Encrypt payload if we have a Noise session
                if (encryptionService.hasEstablishedSession(peerId)) {
                    val noisePayload = com.seamless.android.model.NoisePayload(
                        type = com.seamless.android.model.NoisePayloadType.BSP_SYNC,
                        data = data
                    )
                    val encrypted = encryptionService.encrypt(noisePayload.encode(), peerId)
                    if (encrypted != null) {
                        val securePacket = BitchatPacket(
                            version = 1u,
                            type = MessageType.NOISE_ENCRYPTED.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hexStringToByteArray(peerId),
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = encrypted,
                            ttl = MAX_TTL
                        )
                        return connectionManager.sendPacketToPeer(peerId, signPacketBeforeBroadcast(securePacket))
                    }
                }

                // If not encrypted, send plaintext BSP_SYNC (still signed)
                return connectionManager.sendPacketToPeer(peerId, signPacketBeforeBroadcast(packet))
            }
        })
    }
    
    /**
     * Start periodic debug logging every 10 seconds
     */
    private fun startPeriodicDebugLogging() {
        serviceScope.launch {
            while (isActive) {
                try {
                    delay(10000) // 10 seconds
                    if (isActive) { // Double-check before logging
                        val debugInfo = getDebugStatus()
                        Log.d(TAG, "=== PERIODIC DEBUG STATUS ===\n$debugInfo\n=== END DEBUG STATUS ===")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic debug logging: ${e.message}")
                }
            }
        }
    }

    /**
     * Send broadcast announcement every 30 seconds
     */
    private fun sendPeriodicBroadcastAnnounce() {
        serviceScope.launch {
            while (isActive) {
                try {
                    delay(30000) // 30 seconds
                    sendBroadcastAnnounce()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic broadcast announce: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Setup delegate connections between components
     */
    private fun setupDelegates() {
        // Provide nickname resolver to BLE broadcaster for detailed logs
        try {
            connectionManager.setNicknameResolver { pid -> peerManager.getPeerNickname(pid) }
        } catch (_: Exception) { }
        // PeerManager delegates to main mesh service delegate
        peerManager.delegate = object : PeerManagerDelegate {
            override fun onPeerListUpdated(peerIDs: List<String>) {
                delegate?.didUpdatePeerList(peerIDs)
                
                // PRODUCTION FIX: Automatically initiate Noise handshakes for connected peers
                // This ensures DM capability is set up proactively when peers connect
                serviceScope.launch {
                    delay(500) // Brief delay to let connection stabilize
                    peerIDs.forEach { peerID ->
                        try {
                            val hasSession = hasEstablishedSession(peerID)
                            val info = peerManager.getPeerInfo(peerID)
                            val isDirectlyConnected = info?.isDirectConnection == true
                            
                            if (!hasSession && isDirectlyConnected) {
                                Log.d(TAG, "🤝 Auto-initiating Noise handshake with directly connected peer: $peerID")
                                initiateNoiseHandshake(peerID)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to auto-initiate handshake for $peerID: ${e.message}")
                        }
                    }
                }
            }
            override fun onPeerRemoved(peerID: String) {
                try { gossipSyncManager.removeAnnouncementForPeer(peerID) } catch (_: Exception) { }
                // Also drop any Noise session state for this peer when they go offline
                try {
                    encryptionService.removePeer(peerID)
                    Log.d(TAG, "Removed Noise session for offline peer $peerID")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove Noise session for $peerID: ${e.message}")
                }
            }
        }
        
        // SecurityManager delegate for key exchange notifications
        securityManager.delegate = object : SecurityManagerDelegate {
            override fun onKeyExchangeCompleted(peerID: String, peerPublicKeyData: ByteArray) {
                // Send announcement and cached messages after key exchange
                serviceScope.launch {
                    delay(100)
                    sendAnnouncementToPeer(peerID)
                    
                    delay(500)
                    // PRODUCTION FIX: Flush any pending private messages for this peer
                    flushPendingMessages(peerID)
                    
                    // PRODUCTION FIX: Flush any pending mesh messages for this peer
                    flushPendingMeshMessages(peerID)
                    
                    // PRODUCTION FIX: Flush any pending files for this peer
                    flushPendingFiles(peerID)
                    
                    delay(500)
                    storeForwardManager.sendCachedMessages(peerID)
                }
            }
            
            override fun sendHandshakeResponse(peerID: String, response: ByteArray) {
                // Send Noise handshake response
                val responsePacket = BitchatPacket(
                    version = 1u,
                    type = MessageType.NOISE_HANDSHAKE.value,
                    senderID = hexStringToByteArray(myPeerID),
                    recipientID = hexStringToByteArray(peerID),
                    timestamp = System.currentTimeMillis().toULong(),
                    payload = response,
                    ttl = MAX_TTL
                )
                // Sign the handshake response
                val signedPacket = signPacketBeforeBroadcast(responsePacket)
                connectionManager.broadcastPacket(RoutedPacket(signedPacket))
                Log.d(TAG, "Sent Noise handshake response to $peerID (${response.size} bytes)")
            }
            
            override fun getPeerInfo(peerID: String): PeerInfo? {
                return peerManager.getPeerInfo(peerID)
            }
        }
        
        // StoreForwardManager delegates
        storeForwardManager.delegate = object : StoreForwardManagerDelegate {
            override fun isFavorite(peerID: String): Boolean {
                return delegate?.isFavorite(peerID) ?: false
            }
            
            override fun isPeerOnline(peerID: String): Boolean {
                return peerManager.isPeerActive(peerID)
            }
            
            override fun sendPacket(packet: BitchatPacket) {
                connectionManager.broadcastPacket(RoutedPacket(packet))
            }
        }
        
        // MessageHandler delegates
        messageHandler.delegate = object : MessageHandlerDelegate {
            // Peer management
            override fun addOrUpdatePeer(peerID: String, nickname: String): Boolean {
                return peerManager.addOrUpdatePeer(peerID, nickname)
            }
            
            override fun removePeer(peerID: String) {
                peerManager.removePeer(peerID)
            }
            
            override fun updatePeerNickname(peerID: String, nickname: String) {
                peerManager.addOrUpdatePeer(peerID, nickname)
            }
            
            override fun getPeerNickname(peerID: String): String? {
                return peerManager.getPeerNickname(peerID)
            }
            
            override fun getNetworkSize(): Int {
                return peerManager.getActivePeerCount()
            }
            
            override fun getMyNickname(): String? {
                return delegate?.getNickname()
            }
            
            override fun getPeerInfo(peerID: String): PeerInfo? {
                return peerManager.getPeerInfo(peerID)
            }
            
            override fun updatePeerInfo(peerID: String, nickname: String, noisePublicKey: ByteArray, signingPublicKey: ByteArray, isVerified: Boolean): Boolean {
                return peerManager.updatePeerInfo(peerID, nickname, noisePublicKey, signingPublicKey, isVerified)
            }
            
            // Packet operations
            override fun sendPacket(packet: BitchatPacket) {
                // Sign the packet before broadcasting
                val signedPacket = signPacketBeforeBroadcast(packet)
                connectionManager.broadcastPacket(RoutedPacket(signedPacket))
            }
            
            override fun relayPacket(routed: RoutedPacket) {
                connectionManager.broadcastPacket(routed)
            }
            
            override fun getBroadcastRecipient(): ByteArray {
                return SpecialRecipients.BROADCAST
            }
            
            // Cryptographic operations
            override fun verifySignature(packet: BitchatPacket, peerID: String): Boolean {
                return securityManager.verifySignature(packet, peerID)
            }
            
            override fun encryptForPeer(data: ByteArray, recipientPeerID: String): ByteArray? {
                return securityManager.encryptForPeer(data, recipientPeerID)
            }
            
            override fun decryptFromPeer(encryptedData: ByteArray, senderPeerID: String): ByteArray? {
                return securityManager.decryptFromPeer(encryptedData, senderPeerID)
            }
            
            override fun verifyEd25519Signature(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean {
                return encryptionService.verifyEd25519Signature(signature, data, publicKey)
            }
            
            // Noise protocol operations
            override fun hasNoiseSession(peerID: String): Boolean {
                return encryptionService.hasEstablishedSession(peerID)
            }
            
            override fun initiateNoiseHandshake(peerID: String) {
                try {
                    // Initiate proper Noise handshake with specific peer
                    val handshakeData = encryptionService.initiateHandshake(peerID)

                    if (handshakeData != null) {
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.NOISE_HANDSHAKE.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hexStringToByteArray(peerID),
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = handshakeData,
                            ttl = MAX_TTL
                        )

                        // Sign the handshake packet before broadcasting
                        val signedPacket = signPacketBeforeBroadcast(packet)
                        connectionManager.broadcastPacket(RoutedPacket(signedPacket))
                        Log.d(TAG, "Initiated Noise handshake with $peerID (${handshakeData.size} bytes)")
                    } else {
                        Log.w(TAG, "Failed to generate Noise handshake data for $peerID")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initiate Noise handshake with $peerID: ${e.message}")
                }
            }
            
            override fun processNoiseHandshakeMessage(payload: ByteArray, peerID: String): ByteArray? {
                return try {
                    encryptionService.processHandshakeMessage(payload, peerID)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process handshake message from $peerID: ${e.message}")
                    null
                }
            }
            
            override fun updatePeerIDBinding(newPeerID: String, nickname: String,
                                           publicKey: ByteArray, previousPeerID: String?) {

                Log.d(TAG, "Updating peer ID binding: $newPeerID (was: $previousPeerID) with nickname: $nickname and public key: ${publicKey.toHexString().take(16)}...")
                // Update peer mapping in the PeerManager for peer ID rotation support
                peerManager.addOrUpdatePeer(newPeerID, nickname)
                
                // Store fingerprint for the peer via centralized fingerprint manager
                val fingerprint = peerManager.storeFingerprintForPeer(newPeerID, publicKey)

                // Index existing Nostr mapping by the new peerID if we have it
                try {
                    com.seamless.android.favorites.FavoritesPersistenceService.shared.findNostrPubkey(publicKey)?.let { npub ->
                        com.seamless.android.favorites.FavoritesPersistenceService.shared.updateNostrPublicKeyForPeerID(newPeerID, npub)
                    }
                } catch (_: Exception) { }
                
                // If there was a previous peer ID, remove it to avoid duplicates
                previousPeerID?.let { oldPeerID ->
                    peerManager.removePeer(oldPeerID)
                }
                
                Log.d(TAG, "Updated peer ID binding: $newPeerID (was: $previousPeerID), fingerprint: ${fingerprint.take(16)}...")
            }
            
            
            // Message operations  
            override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
                return delegate?.decryptChannelMessage(encryptedContent, channel)
            }
            
            // Vector Clock sync
            override fun updateVectorClock(clock: Map<String, Long>) {
                gossipSyncManager.updateVectorClock(clock)
            }
            
            // Callbacks
            override fun onMessageReceived(message: BitchatMessage) {
                delegate?.didReceiveMessage(message)
            }
            
            override fun onChannelLeave(channel: String, fromPeer: String) {
                delegate?.didReceiveChannelLeave(channel, fromPeer)
            }
            
            override fun onDeliveryAckReceived(messageID: String, peerID: String) {
                delegate?.didReceiveDeliveryAck(messageID, peerID)
            }
            
            override fun onReadReceiptReceived(messageID: String, peerID: String) {
                delegate?.didReceiveReadReceipt(messageID, peerID)
            }
        }
        
        // PacketProcessor delegates
        packetProcessor.delegate = object : PacketProcessorDelegate {
            override fun validatePacketSecurity(packet: BitchatPacket, peerID: String): Boolean {
                return securityManager.validatePacket(packet, peerID)
            }
            
            override fun updatePeerLastSeen(peerID: String) {
                peerManager.updatePeerLastSeen(peerID)
            }
            
            override fun getPeerNickname(peerID: String): String? {
                return peerManager.getPeerNickname(peerID)
            }
            
            // Network information for relay manager
            override fun getNetworkSize(): Int {
                return peerManager.getActivePeerCount()
            }
            
            override fun getBroadcastRecipient(): ByteArray {
                return SpecialRecipients.BROADCAST
            }
            
            override suspend fun handleNoiseHandshake(routed: RoutedPacket): Boolean {
                return try {
                    val success = securityManager.handleNoiseHandshake(routed)
                    
                    // PRODUCTION FIX: If handshake handled, check if session established and flush ALL queues
                    if (success) {
                        val peerID = routed.peerID ?: routed.packet.senderID.toHexString()
                        serviceScope.launch {
                            if (encryptionService.hasEstablishedSession(peerID)) {
                                Log.d(TAG, "🔓 Session established with $peerID, flushing all pending queues")
                                flushPendingMessages(peerID)
                                flushPendingFiles(peerID)
                                flushPendingMeshMessages(peerID)
                            }
                        }
                    }
                    success
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling Noise handshake: ${e.message}")
                    false
                }
            }
            
            override fun handleNoiseEncrypted(routed: RoutedPacket) {
                serviceScope.launch { messageHandler.handleNoiseEncrypted(routed) }
            }
            
            override fun handleAnnounce(routed: RoutedPacket) {
                serviceScope.launch {
                    // Process the announce
                    val isFirst = messageHandler.handleAnnounce(routed)

                    // Map device address -> peerID on first announce seen over this device connection
                    val deviceAddress = routed.relayAddress
                    val pid = routed.peerID
                    if (deviceAddress != null && pid != null) {
                        // First ANNOUNCE over a device connection defines a direct neighbor.
                        if (!connectionManager.hasSeenFirstAnnounce(deviceAddress)) {
                            // Bind or rebind this device address to the announcing peer
                            connectionManager.addressPeerMap[deviceAddress] = pid
                            connectionManager.noteAnnounceReceived(deviceAddress)
                            Log.d(TAG, "Mapped device $deviceAddress to peer $pid on FIRST-ANNOUNCE for this connection")

                            // Mark as directly connected (upgrades from routed if needed)
                            try { peerManager.setDirectConnection(pid, true) } catch (_: Exception) { }

                            // Initial sync for this newly direct peer
                            try { gossipSyncManager.scheduleInitialSyncToPeer(pid, 1_000) } catch (_: Exception) { }
                        }
                    }
                    // Track for sync
                    try { gossipSyncManager.onPublicPacketSeen(routed.packet) } catch (_: Exception) { }
                }
            }
            
            override fun handleMessage(routed: RoutedPacket) {
                serviceScope.launch { messageHandler.handleMessage(routed) }
                // Track broadcast messages for sync
                try {
                    val pkt = routed.packet
                    val isBroadcast = (pkt.recipientID == null || pkt.recipientID.contentEquals(SpecialRecipients.BROADCAST))
                    if (isBroadcast && pkt.type == MessageType.MESSAGE.value) {
                        gossipSyncManager.onPublicPacketSeen(pkt)
                    }
                } catch (_: Exception) { }
            }
            
            override fun handleLeave(routed: RoutedPacket) {
                serviceScope.launch { messageHandler.handleLeave(routed) }
            }
            
            override fun handleFragment(packet: BitchatPacket): BitchatPacket? {
                // Track broadcast fragments for gossip sync
                try {
                    val isBroadcast = (packet.recipientID == null || packet.recipientID.contentEquals(SpecialRecipients.BROADCAST))
                    if (isBroadcast && packet.type == MessageType.FRAGMENT.value) {
                        gossipSyncManager.onPublicPacketSeen(packet)
                    }
                } catch (_: Exception) { }
                return fragmentManager.handleFragment(packet)
            }
            
            override fun sendAnnouncementToPeer(peerID: String) {
                this@BluetoothMeshService.sendAnnouncementToPeer(peerID)
            }
            
            override fun sendCachedMessages(peerID: String) {
                storeForwardManager.sendCachedMessages(peerID)
            }
            
            override fun relayPacket(routed: RoutedPacket) {
                connectionManager.broadcastPacket(routed)
            }

            override fun handleRequestSync(routed: RoutedPacket) {
                // Decode request and respond with missing packets
                val fromPeer = routed.peerID ?: return
                val req = RequestSyncPacket.decode(routed.packet.payload) ?: return
                gossipSyncManager.handleRequestSync(fromPeer, req)
            }
            
            override fun handleBspSync(routed: RoutedPacket) {
                val peerID = routed.peerID ?: return
                // Route unencrypted BSP_SYNC payload directly to BrambleSyncEngine
                serviceScope.launch {
                    com.seamless.android.sync.BrambleSyncEngine.getInstance(context).handleIncomingData(peerID, routed.packet.payload)
                }
            }
            
            // Private Mesh handlers
            override fun handlePrivateMeshAnnounce(routed: RoutedPacket) {
                val peerID = routed.peerID ?: return
                val payload = routed.packet.payload
                Log.d(TAG, "📡 Received mesh announce from ${peerID}, payload size: ${payload.size}")
                if (payload.size < 9) {
                    Log.w(TAG, "Mesh announce payload too small: ${payload.size}")
                    return
                }
                
                try {
                    // Simple TLV decode: meshId (8 bytes) + name (variable) + memberCount (1 byte)
                    // PRODUCTION FIX: Use lowercase() for consistent hex format across encode/decode
                    val meshId = payload.take(8).toByteArray().toHexString().lowercase()
                    val memberCount = payload[payload.size - 1].toInt() and 0xFF
                    val nameBytes = payload.drop(8).dropLast(1).toByteArray()
                    val meshName = String(nameBytes, Charsets.UTF_8)
                    
                    val hostNickname = peerManager.getPeerNickname(peerID) ?: peerID
                    Log.d(TAG, "🎯 Parsed mesh announce: $meshName (ID: $meshId, host: $hostNickname, members: $memberCount)")
                    
                    PrivateMeshManager.getInstance(context).onMeshAnnounceReceived(
                        meshId = meshId,
                        name = meshName,
                        hostPeerID = peerID,
                        hostNickname = hostNickname,
                        memberCount = memberCount
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse private mesh announce: ${e.message}")
                }
            }
            
            override fun handlePrivateMeshJoinRequest(routed: RoutedPacket) {
                val requesterPeerID = routed.peerID ?: return
                val requesterNickname = peerManager.getPeerNickname(requesterPeerID) ?: requesterPeerID
                val requestId = java.util.UUID.randomUUID().toString()
                
                // Debug: Check if we're the host and if packet was meant for us
                val meshManager = PrivateMeshManager.getInstance(context)
                val recipientID = routed.packet.recipientID?.toHexString()?.lowercase()
                Log.d(TAG, "📥 JOIN_REQUEST received from $requesterNickname ($requesterPeerID)")
                Log.d(TAG, "   Packet addressed to: $recipientID, My ID: $myPeerID")
                Log.d(TAG, "   My state: ${meshManager.state.value}, Am I hosting? ${meshManager.state.value == PrivateMeshState.HOSTING}")
                
                meshManager.onJoinRequestReceived(
                    requestId = requestId,
                    requesterPeerID = requesterPeerID,
                    requesterNickname = requesterNickname
                )
            }
            
            override fun handlePrivateMeshJoinResponse(routed: RoutedPacket) {
                val hostPeerID = routed.peerID ?: return
                val payload = routed.packet.payload
                if (payload.isEmpty()) return
                
                val accepted = payload[0] == 1.toByte()
                if (accepted) {
                    // Decode mesh info: accepted(1) + meshId(8) + name(variable)
                    if (payload.size < 9) return
                    // PRODUCTION FIX: Use lowercase() for consistent hex format
                    val meshId = payload.drop(1).take(8).toByteArray().toHexString().lowercase()
                    val meshName = String(payload.drop(9).toByteArray(), Charsets.UTF_8)
                    
                    // PRODUCTION FIX: Initiate handshake with host to enable messaging
                    val hostPeerID = routed.peerID
                    if (hostPeerID != null && !encryptionService.hasEstablishedSession(hostPeerID)) {
                        Log.d(TAG, "📞 Initiating Noise handshake with mesh host $hostPeerID")
                        messageHandler.delegate?.initiateNoiseHandshake(hostPeerID)
                    }
                    
                    PrivateMeshManager.getInstance(context).onJoinAccepted(meshId, meshName, emptyList())
                } else {
                    PrivateMeshManager.getInstance(context).onJoinRejected()
                }
            }
            
            override fun handlePrivateMeshMemberList(routed: RoutedPacket) {
                // Parse member list and update PrivateMeshManager
                // Format: count(1) + [peerID(8) + nicknameLen(1) + nickname(variable)]*
                val payload = routed.packet.payload
                if (payload.isEmpty()) return
                
                try {
                    val members = mutableListOf<MeshMember>()
                    var offset = 0
                    val count = payload[offset++].toInt() and 0xFF
                    
                    for (i in 0 until count) {
                        if (offset + 9 > payload.size) break
                        val peerIdBytes = payload.drop(offset).take(8).toByteArray()
                        offset += 8
                        val nickLen = payload[offset++].toInt() and 0xFF
                        if (offset + nickLen > payload.size) break
                        val nickname = String(payload.drop(offset).take(nickLen).toByteArray(), Charsets.UTF_8)
                        offset += nickLen
                        
                        members.add(MeshMember(
                            peerID = peerIdBytes.toHexString(),
                            nickname = nickname,
                            isHost = i == 0
                        ))
                    }
                    
                    PrivateMeshManager.getInstance(context).onMemberListUpdated(members)
                    
                    // PRODUCTION FIX: Initiate Noise handshake with ALL mesh members
                    // This ensures we can send/receive messages from everyone in the private mesh
                    serviceScope.launch {
                        for (member in members) {
                            if (member.peerID == myPeerID) continue
                            
                            if (!encryptionService.hasEstablishedSession(member.peerID)) {
                                Log.d(TAG, "📞 Initiating Noise handshake with mesh member ${member.nickname}")
                                messageHandler.delegate?.initiateNoiseHandshake(member.peerID)
                                delay(200) // Small delay between handshakes to avoid flooding
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse member list: ${e.message}")
                }
            }
        }
        
        // PrivateMeshManager delegate for broadcasting mesh packets
        val myNickname = delegate?.getNickname() ?: myPeerID
        PrivateMeshManager.getInstance(context).apply {
            setIdentity(myPeerID, myNickname)
            this.delegate = object : PrivateMeshDelegate {
                override fun onMeshCreated(meshId: String, name: String) {
                    Log.d(TAG, "🌐 Private mesh created: $name ($meshId)")
                }
                
                override fun onMeshClosing(meshId: String) {
                    Log.d(TAG, "🛑 Private mesh closing: $meshId")
                }
                
                override fun onStartDiscovery() {
                    Log.d(TAG, "🔍 Private mesh discovery started")
                }
                
                override fun onJoinRequested(meshId: String, hostPeerID: String) {
                    // Send join request packet to host
                    Log.d(TAG, "📤 Sending JOIN_REQUEST to host: $hostPeerID for mesh: $meshId")
                    serviceScope.launch {
                        val hostIdBytes = hexStringToByteArray(hostPeerID)
                        Log.d(TAG, "   Host ID bytes: ${hostIdBytes.toHexString()}")
                        
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.PRIVATE_MESH_JOIN_REQUEST.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hostIdBytes,
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = meshId.toByteArray(),
                            ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                        )
                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                        Log.d(TAG, "📤 JOIN_REQUEST sent successfully")
                    }
                }
                
                override fun onJoinRequestAccepted(requesterPeerID: String, meshId: String, meshName: String) {
                    // Send acceptance to requester
                    serviceScope.launch {
                        @OptIn(ExperimentalStdlibApi::class)
                        val response = byteArrayOf(1) + meshId.take(16).hexToByteArray() + meshName.toByteArray()
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.PRIVATE_MESH_JOIN_RESPONSE.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hexStringToByteArray(requesterPeerID),
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = response,
                            ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                        )
                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                        
                        // PRODUCTION FIX: Proactively initiate Noise handshake with newly accepted member
                        // This ensures messages can be sent/received immediately after joining
                        if (!encryptionService.hasEstablishedSession(requesterPeerID)) {
                            Log.d(TAG, "📞 Initiating Noise handshake with new mesh member $requesterPeerID")
                            messageHandler.delegate?.initiateNoiseHandshake(requesterPeerID)
                        }
                        
                        // CRITICAL FIX: Send member list directly to new member
                        // The broadcast may not arrive reliably, so we also send a directed copy
                        delay(500) // Small delay to ensure JOIN_RESPONSE is processed first
                        sendMemberListToMember(requesterPeerID)
                    }
                }
                
                override fun onJoinRequestRejected(requesterPeerID: String) {
                    // Send rejection to requester
                    serviceScope.launch {
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.PRIVATE_MESH_JOIN_RESPONSE.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hexStringToByteArray(requesterPeerID),
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = byteArrayOf(0),
                            ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                        )
                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                    }
                }
                
                override fun onMemberListChanged(members: List<MeshMember>) {
                    // Broadcast updated member list to all members
                    Log.d(TAG, "📋 Broadcasting member list (${members.size} members)")
                    
                    serviceScope.launch {
                        try {
                            // Format: count(1) + [peerID(8) + nicknameLen(1) + nickname(variable)]* (max 255 members)
                            val count = members.size.coerceAtMost(255)
                            val buffer = java.io.ByteArrayOutputStream()
                            buffer.write(count)
                            
                            for (i in 0 until count) {
                                val member = members[i]
                                val peerIdBytes = hexStringToByteArray(member.peerID).take(8).toByteArray()
                                val nicknameBytes = member.nickname.toByteArray(Charsets.UTF_8)
                                val nickLen = nicknameBytes.size.coerceAtMost(255)
                                
                                buffer.write(peerIdBytes)     // 8 bytes
                                buffer.write(nickLen)         // 1 byte
                                buffer.write(nicknameBytes, 0, nickLen)
                            }
                            
                            val payload = buffer.toByteArray()
                            
                            val packet = BitchatPacket(
                                version = 1u,
                                type = MessageType.PRIVATE_MESH_MEMBER_LIST.value,
                                senderID = hexStringToByteArray(myPeerID),
                                recipientID = com.seamless.android.protocol.SpecialRecipients.BROADCAST,
                                timestamp = System.currentTimeMillis().toULong(),
                                payload = payload,
                                ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                            )
                            
                            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to broadcast member list: ${e.message}")
                        }
                    }
                }
                
                override fun onMemberKicked(peerID: String) {
                    Log.d(TAG, "🚫 Member kicked: $peerID")
                }
                
                override fun onLeavingMesh(meshId: String) {
                    Log.d(TAG, "👋 Leaving mesh: $meshId")
                }
                
                override fun broadcastMeshAnnounce(meshId: String, name: String, memberCount: Int) {
                    // Broadcast mesh availability
                    serviceScope.launch {
                        Log.d(TAG, "🌐 Broadcasting mesh announce: $name (ID: $meshId, members: $memberCount)")
                        @OptIn(ExperimentalStdlibApi::class)
                        val payload = meshId.take(16).hexToByteArray() + name.toByteArray() + byteArrayOf(memberCount.toByte())
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.PRIVATE_MESH_ANNOUNCE.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = com.seamless.android.protocol.SpecialRecipients.BROADCAST,
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = payload,
                            ttl = 3u // Allow multi-hop discovery
                        )
                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                    }
                }
                
                override fun sendMeshMessage(meshId: String, content: String) {
                    // Broadcast message to all mesh members using NOISE_ENCRYPTED for privacy
                    val meshManager = PrivateMeshManager.getInstance(context)
                    val members = meshManager.members.value
                    
                    serviceScope.launch {
                        // Send to each member (except self)
                        for (member in members) {
                            if (member.peerID == myPeerID) continue
                            
                            try {
                                // Create message payload with sender info - get fresh nickname from outer delegate
                                val senderNickname = this@BluetoothMeshService.delegate?.getNickname() ?: myNickname
                                val payload = buildString {
                                    append(senderNickname)
                                    append("\u0000") // null separator
                                    append(content)
                                }.toByteArray()
                                
                                // Check if we have Noise session with this member
                                if (encryptionService.hasEstablishedSession(member.peerID)) {
                                    // Create NoisePayload for MESH message (not PRIVATE_MESSAGE)
                                    val noisePayload = com.seamless.android.model.NoisePayload(
                                        type = com.seamless.android.model.NoisePayloadType.MESH_MESSAGE,
                                        data = payload
                                    )
                                    
                                    val encrypted = encryptionService.encrypt(noisePayload.encode(), member.peerID)
                                    if (encrypted != null) {
                                        val packet = BitchatPacket(
                                            version = 1u,
                                            type = MessageType.NOISE_ENCRYPTED.value,
                                            senderID = hexStringToByteArray(myPeerID),
                                            recipientID = hexStringToByteArray(member.peerID),
                                            timestamp = System.currentTimeMillis().toULong(),
                                            payload = encrypted,
                                            ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                                        )
                                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                                        Log.d(TAG, "💬 Sent mesh message to ${member.nickname}")
                                    }
                                } else {
                                    // PRODUCTION FIX: Queue mesh message and initiate handshake
                                    Log.d(TAG, "📞 No session with ${member.nickname}, queuing mesh message and initiating handshake")
                                    
                                    val pending = PendingMeshMessage(
                                        meshId = meshId,
                                        content = content,
                                        recipientPeerID = member.peerID
                                    )
                                    synchronized(pendingMeshMessagesQueue) {
                                        val queue = pendingMeshMessagesQueue.getOrPut(member.peerID) { mutableListOf() }
                                        queue.add(pending)
                                        Log.d(TAG, "📥 Queued mesh message for ${member.nickname}, queue size: ${queue.size}")
                                    }
                                    
                                    messageHandler.delegate?.initiateNoiseHandshake(member.peerID)
                                    
                                    // PRODUCTION FIX: Exponential backoff retry for mesh member handshake
                                    val memberPeerID = member.peerID
                                    serviceScope.launch {
                                        repeat(5) { attempt ->
                                            delay(1000L * (1L shl attempt)) // 1s, 2s, 4s, 8s, 16s exponential backoff
                                            if (!encryptionService.hasEstablishedSession(memberPeerID)) {
                                                Log.d(TAG, "🔄 Mesh handshake retry ${attempt + 1}/5 for ${member.nickname} (backoff: ${1L shl attempt}s)")
                                                messageHandler.delegate?.initiateNoiseHandshake(memberPeerID)
                                            } else {
                                                Log.d(TAG, "✅ Session established with ${member.nickname}, flushing messages")
                                                flushPendingMeshMessages(memberPeerID)
                                                return@launch
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send mesh message to ${member.peerID}: ${e.message}")
                            }
                        }
                    }
                }
                
                override fun sendMeshVoiceNote(meshId: String, filePath: String) {
                    // Send voice note to all mesh members using MESH_AUDIO payload type
                    // PRODUCTION FIX: Chunk large files to bypass Noise 64KB encryption limit
                    val meshManager = PrivateMeshManager.getInstance(context)
                    val members = meshManager.members.value
                    
                    serviceScope.launch {
                        Log.d(TAG, "🎤 Sending mesh voice note to ${members.size - 1} members")
                        for (member in members) {
                            if (member.peerID == myPeerID) continue
                            
                            try {
                                val file = java.io.File(filePath)
                                if (!file.exists()) continue
                                
                                val fileData = file.readBytes()
                                val mimeType = "audio/m4a"
                                
                                // PRODUCTION FIX: Chunk large files before encoding
                                val CHUNK_SIZE = 50 * 1024 // 50KB chunks (safe margin below 64KB limit)
                                val totalChunks = if (fileData.size <= CHUNK_SIZE) 1 else (fileData.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                                val transferId = if (totalChunks > 1) java.util.UUID.randomUUID().toString() else ""
                                
                                if (totalChunks > 1) {
                                    Log.d(TAG, "🔀 Chunking ${file.name} (${fileData.size} bytes) into $totalChunks chunks")
                                }
                                
                                for (chunkIndex in 0 until totalChunks) {
                                    val start = chunkIndex * CHUNK_SIZE
                                    val end = minOf(start + CHUNK_SIZE, fileData.size)
                                    val chunkContent = fileData.copyOfRange(start, end)
                                    
                                    val filePacket = com.seamless.android.model.BitchatFilePacket(
                                        fileName = file.name,
                                        fileSize = fileData.size.toLong(),
                                        mimeType = mimeType,
                                        content = chunkContent,
                                        chunkIndex = chunkIndex,
                                        totalChunks = totalChunks,
                                        transferId = transferId
                                    )
                                    val encodedFile = filePacket.encode() ?: continue
                                    
                                    if (encryptionService.hasEstablishedSession(member.peerID)) {
                                        // Use MESH_AUDIO payload type for proper routing
                                        val noisePayload = com.seamless.android.model.NoisePayload(
                                            type = com.seamless.android.model.NoisePayloadType.MESH_AUDIO,
                                            data = encodedFile
                                        )
                                        val encrypted = encryptionService.encrypt(noisePayload.encode(), member.peerID)
                                        if (encrypted != null) {
                                            val packet = BitchatPacket(
                                                version = 1u,
                                                type = MessageType.NOISE_ENCRYPTED.value,
                                                senderID = hexStringToByteArray(myPeerID),
                                                recipientID = hexStringToByteArray(member.peerID),
                                                timestamp = System.currentTimeMillis().toULong(),
                                                payload = encrypted,
                                                ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                                            )
                                            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                                            if (totalChunks > 1) {
                                                Log.d(TAG, "🎤 Sent chunk ${chunkIndex + 1}/$totalChunks to ${member.nickname}")
                                            } else {
                                                Log.d(TAG, "🎤 Sent mesh voice to ${member.nickname}")
                                            }
                                            // Small delay between chunks to prevent overwhelming the receiver
                                            if (totalChunks > 1 && chunkIndex < totalChunks - 1) {
                                                delay(200)
                                            }
                                        }
                                    } else {
                                        // No Noise session: initiate handshake and retry with MESH_AUDIO type
                                        // CRITICAL FIX: Don't use sendFilePrivate() here — it sends as FILE_TRANSFER
                                        // which the receiver won't route to PrivateMeshManager
                                        Log.d(TAG, "📞 No session with ${member.nickname}, initiating handshake for mesh voice")
                                        val handshakeData = encryptionService.initiateHandshake(member.peerID)
                                        if (handshakeData != null) {
                                            val hsPacket = BitchatPacket(1u, MessageType.NOISE_HANDSHAKE.value, hexStringToByteArray(myPeerID), hexStringToByteArray(member.peerID), System.currentTimeMillis().toULong(), handshakeData, ttl = MAX_TTL)
                                            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(hsPacket)))
                                        }
                                        // Retry after handshake completes
                                        delay(3000)
                                        if (encryptionService.hasEstablishedSession(member.peerID)) {
                                            val retryPayload = com.seamless.android.model.NoisePayload(
                                                type = com.seamless.android.model.NoisePayloadType.MESH_AUDIO,
                                                data = encodedFile
                                            )
                                            val encrypted = encryptionService.encrypt(retryPayload.encode(), member.peerID)
                                            if (encrypted != null) {
                                                val pkt = BitchatPacket(1u, MessageType.NOISE_ENCRYPTED.value, hexStringToByteArray(myPeerID), hexStringToByteArray(member.peerID), System.currentTimeMillis().toULong(), encrypted, ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS)
                                                connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(pkt)))
                                                Log.d(TAG, "🎤 Retry: sent mesh voice to ${member.nickname}")
                                            }
                                        } else {
                                            Log.w(TAG, "⚠️ Handshake not complete after retry for ${member.nickname}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send mesh voice note to ${member.peerID}: ${e.message}")
                            }
                        }
                    }
                }
                
                override fun sendMeshImage(meshId: String, filePath: String) {
                    // Send image to all mesh members using MESH_IMAGE payload type
                    // PRODUCTION FIX: Chunk large files to bypass Noise 64KB encryption limit
                    val meshManager = PrivateMeshManager.getInstance(context)
                    val members = meshManager.members.value
                    
                    serviceScope.launch {
                        Log.d(TAG, "🖼️ Sending mesh image to ${members.size - 1} members")
                        for (member in members) {
                            if (member.peerID == myPeerID) continue
                            
                            try {
                                val file = java.io.File(filePath)
                                if (!file.exists()) continue
                                
                                val fileData = file.readBytes()
                                val mimeType = when {
                                    file.name.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
                                    file.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                    file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                    file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                                    else -> "image/jpeg"
                                }
                                
                                // PRODUCTION FIX: Chunk large files before encoding
                                val CHUNK_SIZE = 50 * 1024 // 50KB chunks (safe margin below 64KB limit)
                                val totalChunks = if (fileData.size <= CHUNK_SIZE) 1 else (fileData.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                                val transferId = if (totalChunks > 1) java.util.UUID.randomUUID().toString() else ""
                                
                                if (totalChunks > 1) {
                                    Log.d(TAG, "🔀 Chunking ${file.name} (${fileData.size} bytes) into $totalChunks chunks")
                                }
                                
                                for (chunkIndex in 0 until totalChunks) {
                                    val start = chunkIndex * CHUNK_SIZE
                                    val end = minOf(start + CHUNK_SIZE, fileData.size)
                                    val chunkContent = fileData.copyOfRange(start, end)
                                    
                                    val filePacket = com.seamless.android.model.BitchatFilePacket(
                                        fileName = file.name,
                                        fileSize = fileData.size.toLong(),
                                        mimeType = mimeType,
                                        content = chunkContent,
                                        chunkIndex = chunkIndex,
                                        totalChunks = totalChunks,
                                        transferId = transferId
                                    )
                                    val encodedFile = filePacket.encode() ?: continue
                                    
                                    if (encryptionService.hasEstablishedSession(member.peerID)) {
                                        // Use MESH_IMAGE payload type for proper routing
                                        val noisePayload = com.seamless.android.model.NoisePayload(
                                            type = com.seamless.android.model.NoisePayloadType.MESH_IMAGE,
                                            data = encodedFile
                                        )
                                        val encrypted = encryptionService.encrypt(noisePayload.encode(), member.peerID)
                                        if (encrypted != null) {
                                            val packet = BitchatPacket(
                                                version = 1u,
                                                type = MessageType.NOISE_ENCRYPTED.value,
                                                senderID = hexStringToByteArray(myPeerID),
                                                recipientID = hexStringToByteArray(member.peerID),
                                                timestamp = System.currentTimeMillis().toULong(),
                                                payload = encrypted,
                                                ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                                            )
                                            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                                            if (totalChunks > 1) {
                                                Log.d(TAG, "🖼️ Sent chunk ${chunkIndex + 1}/$totalChunks to ${member.nickname}")
                                            } else {
                                                Log.d(TAG, "🖼️ Sent mesh image to ${member.nickname}")
                                            }
                                            // Small delay between chunks to prevent overwhelming the receiver
                                            if (totalChunks > 1 && chunkIndex < totalChunks - 1) {
                                                delay(200)
                                            }
                                        }
                                    } else {
                                        // No Noise session: initiate handshake and retry with MESH_IMAGE type
                                        // CRITICAL FIX: Don't use sendFilePrivate() here — it sends as FILE_TRANSFER
                                        // which the receiver won't route to PrivateMeshManager
                                        Log.d(TAG, "📞 No session with ${member.nickname}, initiating handshake for mesh image")
                                        val handshakeData = encryptionService.initiateHandshake(member.peerID)
                                        if (handshakeData != null) {
                                            val hsPacket = BitchatPacket(1u, MessageType.NOISE_HANDSHAKE.value, hexStringToByteArray(myPeerID), hexStringToByteArray(member.peerID), System.currentTimeMillis().toULong(), handshakeData, ttl = MAX_TTL)
                                            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(hsPacket)))
                                        }
                                        // Retry after handshake completes
                                        delay(3000)
                                        if (encryptionService.hasEstablishedSession(member.peerID)) {
                                            val retryPayload = com.seamless.android.model.NoisePayload(
                                                type = com.seamless.android.model.NoisePayloadType.MESH_IMAGE,
                                                data = encodedFile
                                            )
                                            val encrypted = encryptionService.encrypt(retryPayload.encode(), member.peerID)
                                            if (encrypted != null) {
                                                val pkt = BitchatPacket(1u, MessageType.NOISE_ENCRYPTED.value, hexStringToByteArray(myPeerID), hexStringToByteArray(member.peerID), System.currentTimeMillis().toULong(), encrypted, ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS)
                                                connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(pkt)))
                                                Log.d(TAG, "🖼️ Retry: sent mesh image to ${member.nickname}")
                                            }
                                        } else {
                                            Log.w(TAG, "⚠️ Handshake not complete after retry for ${member.nickname}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send mesh image to ${member.peerID}: ${e.message}")
                            }
                        }
                    }
                }
                
                override fun sendVoiceStream(chunk: ByteArray) {
                    // Stream voice chunk to all mesh members for real-time walkie-talkie
                    val meshManager = PrivateMeshManager.getInstance(context)
                    val members = meshManager.members.value
                    
                    serviceScope.launch {
                        for (member in members) {
                            if (member.peerID == myPeerID) continue
                            
                            try {
                                // Check if we have Noise session with this member
                                if (encryptionService.hasEstablishedSession(member.peerID)) {
                                    // Create NoisePayload for VOICE_STREAM
                                    val noisePayload = com.seamless.android.model.NoisePayload(
                                        type = com.seamless.android.model.NoisePayloadType.VOICE_STREAM,
                                        data = chunk
                                    )
                                    
                                    val encrypted = encryptionService.encrypt(noisePayload.encode(), member.peerID)
                                    if (encrypted != null) {
                                        val packet = BitchatPacket(
                                            version = 1u,
                                            type = MessageType.NOISE_ENCRYPTED.value,
                                            senderID = hexStringToByteArray(myPeerID),
                                            recipientID = hexStringToByteArray(member.peerID),
                                            timestamp = System.currentTimeMillis().toULong(),
                                            payload = encrypted,
                                            ttl = 1u // Voice streams should not hop
                                        )
                                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                                    }
                                }
                            } catch (e: Exception) {
                                // Don't log every failure for voice streams (too noisy)
                            }
                        }
                    }
                }

                override fun sendHeartbeat(meshId: String) {
                    // Broadcast lightweight heartbeat ping to all mesh members
                    serviceScope.launch {
                        try {
                            val payload = meshId.toByteArray(Charsets.UTF_8)
                            val packet = BitchatPacket(
                                version = 1u,
                                type = MessageType.PRIVATE_MESH_ANNOUNCE.value, // Reuse announce type as heartbeat
                                senderID = hexStringToByteArray(myPeerID),
                                recipientID = com.seamless.android.protocol.SpecialRecipients.BROADCAST,
                                timestamp = System.currentTimeMillis().toULong(),
                                payload = payload,
                                ttl = 2u // Keep heartbeats local
                            )
                            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                            Log.d(TAG, "💓 Heartbeat sent for mesh: $meshId")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send heartbeat: ${e.message}")
                        }
                    }
                }

            }
        }
        
        // BluetoothConnectionManager delegates
        connectionManager.delegate = object : BluetoothConnectionManagerDelegate {
            override fun onPacketReceived(packet: BitchatPacket, peerID: String, device: android.bluetooth.BluetoothDevice?) {
                packetProcessor.processPacket(RoutedPacket(packet, peerID, device?.address))
            }
            
            override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
                // Send initial announcements after services are ready
                serviceScope.launch {
                    delay(200)
                    sendBroadcastAnnounce()
                }
                // Verbose debug: device connected
                try {
                    val addr = device.address
                    val peer = connectionManager.addressPeerMap[addr]
                    val nick = peer?.let { peerManager.getPeerNickname(it) } ?: "unknown"
                    // PRODUCTION FIX: Use safe call instead of force-unwrap
                    val isInbound = connectionManager.isClientConnection(addr) != true
                    com.seamless.android.ui.debug.DebugSettingsManager.getInstance()
                        .logPeerConnection(peer ?: "unknown", nick, addr, isInbound = isInbound)
                } catch (_: Exception) { }
            }

            override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice) {
                val addr = device.address
                // Remove mapping and, if that was the last direct path for the peer, clear direct flag
                val peer = connectionManager.addressPeerMap[addr]
                // ConnectionTracker has already removed the address mapping; be defensive either way
                connectionManager.addressPeerMap.remove(addr)
                if (peer != null) {
                    val stillMapped = connectionManager.addressPeerMap.values.any { it == peer }
                    if (!stillMapped) {
                        // Peer might still be reachable indirectly; mark as not-direct
                        try { peerManager.setDirectConnection(peer, false) } catch (_: Exception) { }
                    }
                    // Verbose debug: device disconnected
                    try {
                        val nick = peerManager.getPeerNickname(peer) ?: "unknown"
                        com.seamless.android.ui.debug.DebugSettingsManager.getInstance()
                            .logPeerDisconnection(peer, nick, addr)
                    } catch (_: Exception) { }
                }
            }
            
            override fun onRSSIUpdated(deviceAddress: String, rssi: Int) {
                // Find the peer ID for this device address and update RSSI in PeerManager
                connectionManager.addressPeerMap[deviceAddress]?.let { peerID ->
                    peerManager.updatePeerRSSI(peerID, rssi)
                }
            }
        }
    }
    
    /**
     * Start the mesh service
     */
    fun startServices() {
        // Prevent double starts (defensive programming)
        if (isActive) {
            Log.w(TAG, "Mesh service already active, ignoring duplicate start request")
            return
        }
        
        Log.i(TAG, "Starting Bluetooth mesh service with peer ID: $myPeerID")
        
        if (connectionManager.startServices()) {
            isActive = true
            
            // Start periodic announcements for peer discovery and connectivity
            sendPeriodicBroadcastAnnounce()
            Log.d(TAG, "Started periodic broadcast announcements (every 30 seconds)")
            // Start periodic syncs
            gossipSyncManager.start()
        } else {
            Log.e(TAG, "Failed to start Bluetooth services")
        }
    }
    
    /**
     * Stop all mesh services
     */
    fun stopServices() {
        if (!isActive) {
            Log.w(TAG, "Mesh service not active, ignoring stop request")
            return
        }
        
        Log.i(TAG, "Stopping Bluetooth mesh service")
        isActive = false
        
        // Send leave announcement
        sendLeaveAnnouncement()
    }
    
    /**
     * Refresh the mesh network - call when app reopens or Bluetooth re-enabled
     * Clears stale peer state and restarts discovery to rejoin mesh
     */
    fun refreshMesh() {
        Log.i(TAG, "🔄 Refreshing mesh network...")
        serviceScope.launch {
            try {
                // Clear stale peers that haven't been seen recently (5 minutes)
                val staleThreshold = System.currentTimeMillis() - (5 * 60 * 1000)
                peerManager.clearInactivePeers(staleThreshold)
                
                // Restart Bluetooth services to rediscover peers
                connectionManager.restartServices()
                
                // PRODUCTION FIX: Wait longer for BLE stack to fully stabilize.
                // The previous 1s delay was often not enough for scanning to discover
                // and connect to a peer before the announce was broadcast.
                delay(2000)
                
                // Send fresh broadcast announce to let peers know we're back
                sendBroadcastAnnounce()
                
                // PRODUCTION FIX: Send additional announces with delays to catch
                // peers that connect at different times during the restart window.
                repeat(2) { i ->
                    delay(3000)
                    if (isActive) {
                        Log.d(TAG, "🔄 Sending refresh announce retry ${i + 1}/2")
                        sendBroadcastAnnounce()
                    }
                }
                
                Log.i(TAG, "✅ Mesh refresh complete - waiting for peer discovery")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Mesh refresh failed: ${e.message}", e)
            }
        }
    }
    
    fun sendMessage(content: String, mentions: List<String> = emptyList(), channel: String? = null) {
        if (content.isEmpty()) return
        
        serviceScope.launch {
            // Increment and get vector clock for causal ordering
            gossipSyncManager.incrementLocalClock()
            val currentClock = gossipSyncManager.getLocalVectorClock()

            // Create BitchatMessage model
            val message = BitchatMessage(
                sender = delegate?.getNickname() ?: "Me",
                content = content,
                senderPeerID = myPeerID,
                timestamp = Date(),
                mentions = mentions.ifEmpty { null },
                channel = channel,
                vectorClock = currentClock
            )

            // Convert to binary payload (with Vector Clock extension)
            // Fallback to raw bytes if serialization fails (shouldn't happen)
            val payload = message.toBinaryPayload() ?: content.toByteArray(Charsets.UTF_8)

            val packet = BitchatPacket(
                version = 1u,
                type = MessageType.MESSAGE.value,
                senderID = hexStringToByteArray(myPeerID),
                recipientID = SpecialRecipients.BROADCAST,
                timestamp = System.currentTimeMillis().toULong(),
                payload = payload,
                signature = null,
                ttl = MAX_TTL
            )

            // Sign the packet before broadcasting
            val signedPacket = signPacketBeforeBroadcast(packet)
            connectionManager.broadcastPacket(RoutedPacket(signedPacket))
            // Track our own broadcast message for sync
            try { gossipSyncManager.onPublicPacketSeen(signedPacket) } catch (_: Exception) { }
        }
    }

    /**
     * Send a file over mesh as a broadcast MESSAGE (public mesh timeline/channels).
     * PRODUCTION FIX: Chunk large files to bypass Noise 64KB encryption limit
     */
    fun sendFileBroadcast(file: com.seamless.android.model.BitchatFilePacket) {
        try {
            Log.d(TAG, "📤 sendFileBroadcast: name=${file.fileName}, size=${file.fileSize}")
            
            // PRODUCTION FIX: Chunk large files before sending
            val CHUNK_SIZE = 40 * 1024 // 40KB chunks (safe margin for BLE MTU)
            val fileContent = file.content
            val totalChunks = if (fileContent.size <= CHUNK_SIZE) 1 else (fileContent.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            val transferId = if (totalChunks > 1) java.util.UUID.randomUUID().toString() else ""
            
            if (totalChunks > 1) {
                Log.d(TAG, "🔀 Chunking ${file.fileName} (${fileContent.size} bytes) into $totalChunks chunks")
            }
            
            serviceScope.launch {
                try {
                    for (chunkIndex in 0 until totalChunks) {
                        val start = chunkIndex * CHUNK_SIZE
                        val end = minOf(start + CHUNK_SIZE, fileContent.size)
                        val chunkContent = fileContent.copyOfRange(start, end)
                        
                        val chunkPacket = com.seamless.android.model.BitchatFilePacket(
                            fileName = file.fileName,
                            fileSize = file.fileSize,
                            mimeType = file.mimeType,
                            content = chunkContent,
                            chunkIndex = chunkIndex,
                            totalChunks = totalChunks,
                            transferId = transferId
                        )
                        
                        val payload = chunkPacket.encode()
                        if (payload == null) {
                            Log.e(TAG, "❌ Failed to encode chunk $chunkIndex in sendFileBroadcast")
                            continue
                        }
                        
                        val packet = BitchatPacket(
                            version = 2u,  // FILE_TRANSFER uses v2 for 4-byte payload length
                            type = MessageType.FILE_TRANSFER.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = SpecialRecipients.BROADCAST,
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = payload,
                            signature = null,
                            ttl = MAX_TTL
                        )
                        val signed = signPacketBeforeBroadcast(packet)
                        val chunkTransferId = sha256Hex(payload)
                        connectionManager.broadcastPacket(RoutedPacket(signed, transferId = chunkTransferId))
                        
                        if (totalChunks > 1) {
                            Log.d(TAG, "📤 Sent chunk ${chunkIndex + 1}/$totalChunks")
                            // Small delay between chunks to prevent overwhelming receiver
                            if (chunkIndex < totalChunks - 1) {
                                delay(200)
                            }
                        }
                    }
                    try { gossipSyncManager.onPublicPacketSeen(BitchatPacket(1u, MessageType.FILE_TRANSFER.value, hexStringToByteArray(myPeerID), SpecialRecipients.BROADCAST, System.currentTimeMillis().toULong(), ByteArray(0), null, MAX_TTL)) } catch (_: Exception) { }
                    Log.d(TAG, "✅ sendFileBroadcast completed successfully (${totalChunks} chunks)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ sendFileBroadcast coroutine failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ sendFileBroadcast failed: ${e.message}", e)
            Log.e(TAG, "❌ File: name=${file.fileName}, size=${file.fileSize}")
        }
    }

    /**
     * Send a file as an encrypted private message using Noise protocol
     * PRODUCTION FIX: Chunk large files to bypass Noise 64KB encryption limit
     */
    fun sendFilePrivate(recipientPeerID: String, file: com.seamless.android.model.BitchatFilePacket) {
        try {
            Log.d(TAG, "📤 sendFilePrivate (ENCRYPTED): to=$recipientPeerID, name=${file.fileName}, size=${file.fileSize}")
            
            // PRODUCTION FIX: Chunk large files before encryption
            val CHUNK_SIZE = 40 * 1024 // 40KB chunks (safe margin below Noise 64KB limit)
            val fileContent = file.content
            val totalChunks = if (fileContent.size <= CHUNK_SIZE) 1 else (fileContent.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            val transferId = if (totalChunks > 1) java.util.UUID.randomUUID().toString() else file.transferId
            
            if (totalChunks > 1) {
                Log.d(TAG, "🔀 Chunking ${file.fileName} (${fileContent.size} bytes) into $totalChunks chunks for encryption")
            }
            
            serviceScope.launch {
                // Check if we have an established Noise session
                if (encryptionService.hasEstablishedSession(recipientPeerID)) {
                    try {
                        for (chunkIndex in 0 until totalChunks) {
                            val start = chunkIndex * CHUNK_SIZE
                            val end = minOf(start + CHUNK_SIZE, fileContent.size)
                            val chunkContent = fileContent.copyOfRange(start, end)
                            
                            val chunkPacket = com.seamless.android.model.BitchatFilePacket(
                                fileName = file.fileName,
                                fileSize = file.fileSize,
                                mimeType = file.mimeType,
                                content = chunkContent,
                                chunkIndex = chunkIndex,
                                totalChunks = totalChunks,
                                transferId = transferId
                            )
                            
                            // Encode the chunk packet as TLV
                            val filePayload = chunkPacket.encode()
                            if (filePayload == null) {
                                Log.e(TAG, "❌ Failed to encode chunk $chunkIndex for private send")
                                continue
                            }
                            
                            // Create NoisePayload wrapper (type byte + file TLV data) - same as iOS
                            val noisePayload = com.seamless.android.model.NoisePayload(
                                type = com.seamless.android.model.NoisePayloadType.FILE_TRANSFER,
                                data = filePayload
                            )
                            
                            // Encrypt the payload using Noise
                            val encrypted = encryptionService.encrypt(noisePayload.encode(), recipientPeerID)
                            if (encrypted == null) {
                                Log.e(TAG, "❌ Failed to encrypt chunk $chunkIndex for $recipientPeerID")
                                continue
                            }
                            
                            // Create NOISE_ENCRYPTED packet
                            val packet = BitchatPacket(
                                version = 1u,
                                type = MessageType.NOISE_ENCRYPTED.value,
                                senderID = hexStringToByteArray(myPeerID),
                                recipientID = hexStringToByteArray(recipientPeerID),
                                timestamp = System.currentTimeMillis().toULong(),
                                payload = encrypted,
                                signature = null,
                                ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                            )
                            
                            // Sign and send the encrypted packet
                            val signed = signPacketBeforeBroadcast(packet)
                            val chunkTransferId = sha256Hex(filePayload)
                            connectionManager.broadcastPacket(RoutedPacket(signed, transferId = chunkTransferId))
                            
                            if (totalChunks > 1) {
                                Log.d(TAG, "📤 Sent encrypted chunk ${chunkIndex + 1}/$totalChunks to $recipientPeerID")
                                // Small delay between chunks to prevent overwhelming receiver
                                if (chunkIndex < totalChunks - 1) {
                                    delay(200)
                                }
                            }
                        }
                        Log.d(TAG, "✅ Sent encrypted file to $recipientPeerID ($totalChunks chunks)")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to encrypt file for $recipientPeerID: ${e.message}", e)
                    }
                } else {
                    // PRODUCTION FIX: Queue file for later delivery after handshake completes
                    Log.w(TAG, "⚠️ No Noise session with $recipientPeerID for file transfer, queuing file and initiating handshake")
                    
                    // Queue the file for later delivery
                    val pending = PendingPrivateFile(
                        filePacket = file,
                        recipientPeerID = recipientPeerID
                    )
                    synchronized(pendingFilesQueue) {
                        val queue = pendingFilesQueue.getOrPut(recipientPeerID) { mutableListOf() }
                        queue.add(pending)
                        Log.d(TAG, "📋 Queued file ${file.fileName} for $recipientPeerID (queue size: ${queue.size})")
                    }
                    
                    // Initiate handshake
                    messageHandler.delegate?.initiateNoiseHandshake(recipientPeerID)
                    
                    // PRODUCTION FIX: Exponential backoff retry for file transfer handshakes
                    serviceScope.launch {
                        repeat(5) { attempt ->
                            delay(1000L * (1L shl attempt)) // 1s, 2s, 4s, 8s, 16s exponential backoff
                            if (!encryptionService.hasEstablishedSession(recipientPeerID)) {
                                Log.d(TAG, "🔄 Retry handshake attempt ${attempt + 1}/5 for $recipientPeerID (backoff: ${1L shl attempt}s)")
                                messageHandler.delegate?.initiateNoiseHandshake(recipientPeerID)
                            } else {
                                Log.d(TAG, "✅ Session established with $recipientPeerID, flushing files")
                                flushPendingFiles(recipientPeerID)
                                return@launch
                            }
                        }
                        // Final attempt to flush in case session came up during delays
                        if (encryptionService.hasEstablishedSession(recipientPeerID)) {
                            flushPendingFiles(recipientPeerID)
                        } else {
                            Log.w(TAG, "⚠️ Could not establish session with $recipientPeerID after 5 retries")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ sendFilePrivate failed: ${e.message}", e)
            Log.e(TAG, "❌ File: to=$recipientPeerID, name=${file.fileName}, size=${file.fileSize}")
        }
    }

    fun cancelFileTransfer(transferId: String): Boolean {
        return connectionManager.cancelTransfer(transferId)
    }

    // Local helper to hash payloads to a stable hex ID for progress mapping
    private fun sha256Hex(bytes: ByteArray): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { bytes.size.toString(16) }
    
    /**
     * Send private message - SIMPLIFIED iOS-compatible version 
     * Uses NoisePayloadType system exactly like iOS SimplifiedBluetoothService
     */
    fun sendPrivateMessage(content: String, recipientPeerID: String, recipientNickname: String, messageID: String? = null) {
        if (content.isEmpty() || recipientPeerID.isEmpty()) return
        if (recipientNickname.isEmpty()) return
        
        serviceScope.launch {
            val finalMessageID = messageID ?: java.util.UUID.randomUUID().toString()
            
            Log.d(TAG, "📨 Sending PM to $recipientPeerID: ${content.take(30)}...")
            
            // Increment and get vector clock for causal ordering
            gossipSyncManager.incrementLocalClock()
            val currentClock = gossipSyncManager.getLocalVectorClock()
            
            // Check if we have a session
            if (messageHandler.delegate?.hasNoiseSession(recipientPeerID) == true) {
                try {
                    // Create PrivateMessagePacket with Vector Clock
                    val pmPacket = com.seamless.android.model.PrivateMessagePacket(
                        messageID = finalMessageID,
                        content = content,
                        vectorClock = currentClock
                    )
                    
                    // PRODUCTION FIX: Defensive null check instead of force unwrap
                    val encodedPmPacket = pmPacket.encode()
                    if (encodedPmPacket == null) {
                        Log.e(TAG, "Failed to encode private message packet for $recipientPeerID")
                        return@launch
                    }
                    
                    // Create NoisePayload
                    val noisePayload = com.seamless.android.model.NoisePayload(
                        type = com.seamless.android.model.NoisePayloadType.PRIVATE_MESSAGE,
                        data = encodedPmPacket
                    )
                    
                    // Encrypt
                    val encryptedPayload = messageHandler.delegate?.encryptForPeer(noisePayload.encode(), recipientPeerID)
                    
                    if (encryptedPayload != null) {
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.NOISE_ENCRYPTED.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hexStringToByteArray(recipientPeerID),
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = encryptedPayload,
                            signature = null,
                            ttl = MAX_TTL
                        )
                        
                        // Send
                        connectionManager.broadcastPacket(RoutedPacket(packet))
                        Log.d(TAG, "📤 Sent encrypted PM to $recipientPeerID")
                        
                        // Also send through local chat UI
                        val sentMessage = BitchatMessage(
                            id = finalMessageID,
                            sender = delegate?.getNickname() ?: "Me",
                            content = content,
                            timestamp = Date(),
                            isPrivate = true,
                            recipientNickname = recipientNickname,
                            senderPeerID = myPeerID,
                            vectorClock = currentClock
                        )
                        delegate?.didReceiveMessage(sentMessage)
                    } else {
                        Log.e(TAG, "Failed to encrypt private message for $recipientPeerID - encryptedPayload is null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to encrypt private message for $recipientPeerID: ${e.message}", e)
                }
            } else {
                // PRODUCTION FIX: Queue message and initiate handshake
                Log.d(TAG, "🤝 No session with $recipientPeerID, queuing message and initiating handshake")
                
                // Queue the message for later delivery
                val pending = PendingPrivateMessage(
                    content = content,
                    recipientPeerID = recipientPeerID,
                    recipientNickname = recipientNickname,
                    messageID = finalMessageID
                )
                synchronized(pendingMessagesQueue) {
                    val queue = pendingMessagesQueue.getOrPut(recipientPeerID) { mutableListOf() }
                    queue.add(pending)
                    Log.d(TAG, "📥 Queued message for $recipientPeerID, queue size: ${queue.size}")
                }
                
                // Initiate handshake
                messageHandler.delegate?.initiateNoiseHandshake(recipientPeerID)
                
                // Schedule retry after a short delay to allow handshake to complete
                serviceScope.launch {
                    kotlinx.coroutines.delay(2000) // Wait 2 seconds for handshake
                    flushPendingMessages(recipientPeerID)
                    flushPendingFiles(recipientPeerID) // Also flush pending files
                }
            }
        }
    }
    
    /**
     * Flush pending messages for a peer after Noise handshake completes
     * PRODUCTION FIX: Retry queued messages that were sent before session was established
     */
    private fun flushPendingMessages(peerID: String, retryCount: Int = 0) {
        val messagesToSend: List<PendingPrivateMessage>
        synchronized(pendingMessagesQueue) {
            messagesToSend = pendingMessagesQueue[peerID]?.toList() ?: emptyList()
            pendingMessagesQueue.remove(peerID)
        }
        
        if (messagesToSend.isEmpty()) return
        
        Log.d(TAG, "🔄 Flushing ${messagesToSend.size} pending messages for $peerID (attempt ${retryCount + 1})")
        
        // Check if we now have a session
        if (messageHandler.delegate?.hasNoiseSession(peerID) == true) {
            for (pending in messagesToSend) {
                // Re-send the message (now that we have a session, it should go through)
                sendPrivateMessage(
                    content = pending.content,
                    recipientPeerID = pending.recipientPeerID,
                    recipientNickname = pending.recipientNickname,
                    messageID = pending.messageID
                )
                Log.d(TAG, "📤 Flushed pending message to ${pending.recipientPeerID}")
            }
        } else if (retryCount < 6) {
            // Retry with limit to prevent infinite coroutine leak
            Log.d(TAG, "⏳ Still no session with $peerID, will retry in 5 seconds (${retryCount + 1}/6)")
            synchronized(pendingMessagesQueue) {
                pendingMessagesQueue[peerID] = messagesToSend.toMutableList()
            }
            serviceScope.launch {
                kotlinx.coroutines.delay(5000)
                flushPendingMessages(peerID, retryCount + 1)
            }
        } else {
            Log.w(TAG, "⚠️ Dropping ${messagesToSend.size} pending messages for $peerID after 6 retries")
        }
    }
    
    /**
     * Flush pending files for a peer after Noise handshake completes
     * PRODUCTION FIX: Retry queued files that were sent before session was established
     */
    private fun flushPendingFiles(peerID: String, retryCount: Int = 0) {
        val filesToSend: List<PendingPrivateFile>
        synchronized(pendingFilesQueue) {
            filesToSend = pendingFilesQueue[peerID]?.toList() ?: emptyList()
            pendingFilesQueue.remove(peerID)
        }
        
        if (filesToSend.isEmpty()) return
        
        Log.d(TAG, "🔄 Flushing ${filesToSend.size} pending files for $peerID (attempt ${retryCount + 1})")
        
        // Check if we now have a session
        if (encryptionService.hasEstablishedSession(peerID)) {
            for (pending in filesToSend) {
                // Re-send the file (now that we have a session, it should go through)
                sendFilePrivate(pending.recipientPeerID, pending.filePacket)
                Log.d(TAG, "📤 Flushed pending file ${pending.filePacket.fileName} to $peerID")
            }
        } else if (retryCount < 6) {
            // Retry with limit to prevent infinite coroutine leak
            Log.d(TAG, "⏳ Still no session with $peerID for files, will retry in 5 seconds (${retryCount + 1}/6)")
            synchronized(pendingFilesQueue) {
                pendingFilesQueue[peerID] = filesToSend.toMutableList()
            }
            serviceScope.launch {
                kotlinx.coroutines.delay(5000)
                flushPendingFiles(peerID, retryCount + 1)
            }
        } else {
            Log.w(TAG, "⚠️ Dropping ${filesToSend.size} pending files for $peerID after 6 retries")
        }
    }
    
    /**
     * Flush pending mesh messages for a peer after Noise handshake completes
     * PRODUCTION FIX: Retry queued mesh messages that were sent before session was established
     */
    private fun flushPendingMeshMessages(peerID: String, retryCount: Int = 0) {
        val messagesToSend: List<PendingMeshMessage>
        synchronized(pendingMeshMessagesQueue) {
            messagesToSend = pendingMeshMessagesQueue[peerID]?.toList() ?: emptyList()
            pendingMeshMessagesQueue.remove(peerID)
        }
        
        if (messagesToSend.isEmpty()) return
        
        Log.d(TAG, "🔄 Flushing ${messagesToSend.size} pending mesh messages for $peerID (attempt ${retryCount + 1})")
        
        // Check if we now have a session
        if (encryptionService.hasEstablishedSession(peerID)) {
            for (pending in messagesToSend) {
                try {
                    // Create message payload with sender info
                    val myNickname = delegate?.getNickname() ?: myPeerID
                    val payload = buildString {
                        append(myNickname)
                        append("\u0000") // null separator
                        append(pending.content)
                    }.toByteArray()
                    
                    // Create NoisePayload for MESH message (NOT PRIVATE_MESSAGE!)
                    val noisePayload = com.seamless.android.model.NoisePayload(
                        type = com.seamless.android.model.NoisePayloadType.MESH_MESSAGE,
                        data = payload
                    )
                    
                    val encrypted = encryptionService.encrypt(noisePayload.encode(), peerID)
                    if (encrypted != null) {
                        val packet = BitchatPacket(
                            version = 1u,
                            type = MessageType.NOISE_ENCRYPTED.value,
                            senderID = hexStringToByteArray(myPeerID),
                            recipientID = hexStringToByteArray(peerID),
                            timestamp = System.currentTimeMillis().toULong(),
                            payload = encrypted,
                            ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
                        )
                        connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
                        Log.d(TAG, "📤 Flushed pending mesh message to $peerID")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to flush mesh message to $peerID: ${e.message}")
                }
            }
        } else if (retryCount < 6) {
            // Retry with limit to prevent infinite coroutine leak
            Log.d(TAG, "⏳ Still no session with $peerID for mesh messages, will retry in 5 seconds (${retryCount + 1}/6)")
            synchronized(pendingMeshMessagesQueue) {
                pendingMeshMessagesQueue[peerID] = messagesToSend.toMutableList()
            }
            serviceScope.launch {
                kotlinx.coroutines.delay(5000)
                flushPendingMeshMessages(peerID, retryCount + 1)
            }
        } else {
            Log.w(TAG, "⚠️ Dropping ${messagesToSend.size} pending mesh messages for $peerID after 6 retries")
        }
    }
    
    /**
     * Send member list directly to a specific member
     * CRITICAL FIX: Ensures new members get the member list even if broadcast doesn't arrive
     */
    private fun sendMemberListToMember(targetPeerID: String) {
        val meshManager = PrivateMeshManager.getInstance(context)
        val members = meshManager.members.value
        
        if (members.isEmpty()) return
        
        try {
            // Format: count(1) + [peerID(8) + nicknameLen(1) + nickname(variable)]*
            val count = members.size.coerceAtMost(255)
            val buffer = java.io.ByteArrayOutputStream()
            buffer.write(count)
            
            for (i in 0 until count) {
                val member = members[i]
                val peerIdBytes = hexStringToByteArray(member.peerID).take(8).toByteArray()
                val nicknameBytes = member.nickname.toByteArray(Charsets.UTF_8)
                val nickLen = nicknameBytes.size.coerceAtMost(255)
                
                buffer.write(peerIdBytes)     // 8 bytes
                buffer.write(nickLen)         // 1 byte
                buffer.write(nicknameBytes, 0, nickLen)
            }
            
            val payload = buffer.toByteArray()
            
            // Send directly to the target member (not broadcast)
            val packet = BitchatPacket(
                version = 1u,
                type = MessageType.PRIVATE_MESH_MEMBER_LIST.value,
                senderID = hexStringToByteArray(myPeerID),
                recipientID = hexStringToByteArray(targetPeerID), // Direct send, not broadcast
                timestamp = System.currentTimeMillis().toULong(),
                payload = payload,
                ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS
            )
            
            connectionManager.broadcastPacket(RoutedPacket(signPacketBeforeBroadcast(packet)))
            Log.d(TAG, "📋 Sent member list (${members.size} members) directly to $targetPeerID")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send member list to $targetPeerID: ${e.message}")
        }
    }
    
    /**
     * Send read receipt for a received private message - NEW NoisePayloadType implementation
     * Uses same encryption approach as iOS SimplifiedBluetoothService
     */
    fun sendReadReceipt(messageID: String, recipientPeerID: String, readerNickname: String) {
        serviceScope.launch {
            Log.d(TAG, "📖 Sending read receipt for message $messageID to $recipientPeerID")
            
            // Route geohash read receipts via MessageRouter instead of here
            val geo = runCatching { com.seamless.android.services.MessageRouter.tryGetInstance() }.getOrNull()
            val isGeoAlias = try {
                val map = com.seamless.android.nostr.GeohashAliasRegistry.snapshot()
                map.containsKey(recipientPeerID)
            } catch (_: Exception) { false }
            if (isGeoAlias && geo != null) {
                geo.sendReadReceipt(com.seamless.android.model.ReadReceipt(messageID), recipientPeerID)
                return@launch
            }
            
            try {
                // Create read receipt payload using NoisePayloadType exactly like iOS
                val readReceiptPayload = com.seamless.android.model.NoisePayload(
                    type = com.seamless.android.model.NoisePayloadType.READ_RECEIPT,
                    data = messageID.toByteArray(Charsets.UTF_8)
                )
                
                // Encrypt the payload
                val encrypted = encryptionService.encrypt(readReceiptPayload.encode(), recipientPeerID)
                
                // Create NOISE_ENCRYPTED packet exactly like iOS
                val packet = BitchatPacket(
                    version = 1u,
                    type = MessageType.NOISE_ENCRYPTED.value,
                    senderID = hexStringToByteArray(myPeerID),
                    recipientID = hexStringToByteArray(recipientPeerID),
                    timestamp = System.currentTimeMillis().toULong(),
                    payload = encrypted,
                    signature = null,
                    ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS // Same TTL as iOS messageTTL
                )
                
                // Sign the packet before broadcasting
                val signedPacket = signPacketBeforeBroadcast(packet)
                connectionManager.broadcastPacket(RoutedPacket(signedPacket))
                Log.d(TAG, "📤 Sent read receipt to $recipientPeerID for message $messageID")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send read receipt to $recipientPeerID: ${e.message}")
            }
        }
    }
    
    /**
     * Send broadcast announce with TLV-encoded identity announcement - exactly like iOS
     */
    fun sendBroadcastAnnounce() {
        Log.d(TAG, "Sending broadcast announce")
        serviceScope.launch {
            val nickname = delegate?.getNickname() ?: myPeerID
            
            // Get the static public key for the announcement
            val staticKey = encryptionService.getStaticPublicKey()
            if (staticKey == null) {
                Log.e(TAG, "No static public key available for announcement")
                return@launch
            }
            
            // Get the signing public key for the announcement
            val signingKey = encryptionService.getSigningPublicKey()
            if (signingKey == null) {
                Log.e(TAG, "No signing public key available for announcement")
                return@launch
            }
            
            // Create iOS-compatible IdentityAnnouncement with TLV encoding
            val announcement = IdentityAnnouncement(nickname, staticKey, signingKey)
            val tlvPayload = announcement.encode()
            if (tlvPayload == null) {
                Log.e(TAG, "Failed to encode announcement as TLV")
                return@launch
            }
            
            val announcePacket = BitchatPacket(
                type = MessageType.ANNOUNCE.value,
                ttl = MAX_TTL,
                senderID = myPeerID,
                payload = tlvPayload
            )
            
            // PRODUCTION FIX: Safely handle null toBinaryDataForSigning
            val binaryDataForSigning = announcePacket.toBinaryDataForSigning()
            val signedPacket = if (binaryDataForSigning != null) {
                encryptionService.signData(binaryDataForSigning)?.let { signature ->
                    announcePacket.copy(signature = signature)
                } ?: announcePacket
            } else {
                Log.w(TAG, "Failed to get binary data for signing announce packet")
                announcePacket
            }
            
            connectionManager.broadcastPacket(RoutedPacket(signedPacket))
            Log.d(TAG, "Sent iOS-compatible signed TLV announce (${tlvPayload.size} bytes)")
            // Track announce for sync
            try { gossipSyncManager.onPublicPacketSeen(signedPacket) } catch (_: Exception) { }
        }
    }
    
    /**
     * Send announcement to specific peer with TLV-encoded identity announcement - exactly like iOS
     */
    fun sendAnnouncementToPeer(peerID: String) {
        if (peerManager.hasAnnouncedToPeer(peerID)) return
        
        val nickname = delegate?.getNickname() ?: myPeerID
        
        // Get the static public key for the announcement
        val staticKey = encryptionService.getStaticPublicKey()
        if (staticKey == null) {
            Log.e(TAG, "No static public key available for peer announcement")
            return
        }
        
        // Get the signing public key for the announcement
        val signingKey = encryptionService.getSigningPublicKey()
        if (signingKey == null) {
            Log.e(TAG, "No signing public key available for peer announcement")
            return
        }
        
        // Create iOS-compatible IdentityAnnouncement with TLV encoding
        val announcement = IdentityAnnouncement(nickname, staticKey, signingKey)
        val tlvPayload = announcement.encode()
        if (tlvPayload == null) {
            Log.e(TAG, "Failed to encode peer announcement as TLV")
            return
        }
        
        val packet = BitchatPacket(
            type = MessageType.ANNOUNCE.value,
            ttl = MAX_TTL,
            senderID = myPeerID,
            payload = tlvPayload
        )
        
        // PRODUCTION FIX: Safely handle null toBinaryDataForSigning
        val binaryDataForSigning = packet.toBinaryDataForSigning()
        val signedPacket = if (binaryDataForSigning != null) {
            encryptionService.signData(binaryDataForSigning)?.let { signature ->
                packet.copy(signature = signature)
            } ?: packet
        } else {
            Log.w(TAG, "Failed to get binary data for signing peer announce packet")
            packet
        }
        
        connectionManager.broadcastPacket(RoutedPacket(signedPacket))
        peerManager.markPeerAsAnnouncedTo(peerID)
        Log.d(TAG, "Sent iOS-compatible signed TLV peer announce to $peerID (${tlvPayload.size} bytes)")

        // Track announce for sync
        try { gossipSyncManager.onPublicPacketSeen(signedPacket) } catch (_: Exception) { }
    }

    /**
     * Send leave announcement
     */
    private fun sendLeaveAnnouncement() {
        val packet = BitchatPacket(
            type = MessageType.LEAVE.value,
            ttl = MAX_TTL,
            senderID = myPeerID,
            payload = byteArrayOf()
        )
        
        // Sign the packet before broadcasting
        val signedPacket = signPacketBeforeBroadcast(packet)
        connectionManager.broadcastPacket(RoutedPacket(signedPacket))
    }
    
    /**
     * Get peer nicknames
     */
    fun getPeerNicknames(): Map<String, String> = peerManager.getAllPeerNicknames()
    
    /**
     * Get peer RSSI values  
     */
    fun getPeerRSSI(): Map<String, Int> = peerManager.getAllPeerRSSI()
    
    /**
     * Check if we have an established Noise session with a peer  
     */
    fun hasEstablishedSession(peerID: String): Boolean {
        return encryptionService.hasEstablishedSession(peerID)
    }
    
    /**
     * Get session state for a peer (for UI state display)
     */
    fun getSessionState(peerID: String): com.seamless.android.noise.NoiseSession.NoiseSessionState {
        return encryptionService.getSessionState(peerID)
    }
    
    
    /**
     * Get peer fingerprint for identity management
     */
    fun getPeerFingerprint(peerID: String): String? {
        return peerManager.getFingerprintForPeer(peerID)
    }

    /**
     * Get peer info for verification purposes
     */
    fun getPeerInfo(peerID: String): PeerInfo? {
        return peerManager.getPeerInfo(peerID)
    }

    /**
     * Update peer information with verification data
     */
    fun updatePeerInfo(
        peerID: String,
        nickname: String,
        noisePublicKey: ByteArray,
        signingPublicKey: ByteArray,
        isVerified: Boolean
    ): Boolean {
        return peerManager.updatePeerInfo(peerID, nickname, noisePublicKey, signingPublicKey, isVerified)
    }
    
    /**
     * Get our identity fingerprint
     */
    fun getIdentityFingerprint(): String {
        return encryptionService.getIdentityFingerprint()
    }
    
    /**
     * Check if encryption icon should be shown for a peer
     */
    fun shouldShowEncryptionIcon(peerID: String): Boolean {
        return encryptionService.hasEstablishedSession(peerID)
    }
    
    /**
     * Get all peers with established encrypted sessions
     */
    fun getEncryptedPeers(): List<String> {
        // SIMPLIFIED: Return empty list for now since we don't have direct access to sessionManager
        // This method is not critical for the session retention fix
        return emptyList()
    }
    
    /**
     * Get device address for a specific peer ID
     */
    fun getDeviceAddressForPeer(peerID: String): String? {
        return connectionManager.addressPeerMap.entries.find { it.value == peerID }?.key
    }
    
    /**
     * Get all device addresses mapped to their peer IDs
     */
    fun getDeviceAddressToPeerMapping(): Map<String, String> {
        return connectionManager.addressPeerMap.toMap()
    }
    
    /**
     * Print device addresses for all connected peers
     */
    fun printDeviceAddressesForPeers(): String {
        return peerManager.getDebugInfoWithDeviceAddresses(connectionManager.addressPeerMap)
    }

    /**
     * Get debug status information
     */
    fun getDebugStatus(): String {
        return buildString {
            appendLine("=== Bluetooth Mesh Service Debug Status ===")
            appendLine("My Peer ID: $myPeerID")
            appendLine()
            appendLine(connectionManager.getDebugInfo())
            appendLine()
            appendLine(peerManager.getDebugInfo(connectionManager.addressPeerMap))
            appendLine()
            appendLine(peerManager.getFingerprintDebugInfo())
            appendLine()
            appendLine(fragmentManager.getDebugInfo())
            appendLine()
            appendLine(securityManager.getDebugInfo())
            appendLine()
            appendLine(storeForwardManager.getDebugInfo())
            appendLine()
            appendLine(messageHandler.getDebugInfo())
            appendLine()
            appendLine(packetProcessor.getDebugInfo())
        }
    }
    
    /**
     * Convert hex string peer ID to binary data (8 bytes) - exactly same as iOS
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val result = ByteArray(8) { 0 } // Initialize with zeros, exactly 8 bytes
        var tempID = hexString
        var index = 0
        
        while (tempID.length >= 2 && index < 8) {
            val hexByte = tempID.substring(0, 2)
            val byte = hexByte.toIntOrNull(16)?.toByte()
            if (byte != null) {
                result[index] = byte
            }
            tempID = tempID.substring(2)
            index++
        }
        
        return result
    }
    
    /**
     * Sign packet before broadcasting using our signing private key
     */
    private fun signPacketBeforeBroadcast(packet: BitchatPacket): BitchatPacket {
        return try {
            // Get the canonical packet data for signing (without signature)
            val packetDataForSigning = packet.toBinaryDataForSigning()
            if (packetDataForSigning == null) {
                Log.w(TAG, "Failed to encode packet type ${packet.type} for signing, sending unsigned")
                return packet
            }
            
            // Sign the packet data using our signing key
            val signature = encryptionService.signData(packetDataForSigning)
            if (signature != null) {
                Log.d(TAG, "✅ Signed packet type ${packet.type} (signature ${signature.size} bytes)")
                packet.copy(signature = signature)
            } else {
                Log.w(TAG, "Failed to sign packet type ${packet.type}, sending unsigned")
                packet
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error signing packet type ${packet.type}: ${e.message}, sending unsigned")
            packet
        }
    }
    
    // MARK: - Panic Mode Support
    
    /**
     * Clear all internal mesh service data (for panic mode)
     */
    fun clearAllInternalData() {
        Log.w(TAG, "🚨 Clearing all mesh service internal data")
        try {
            // Clear all managers
            fragmentManager.clearAllFragments()
            storeForwardManager.clearAllCache()
            securityManager.clearAllData()
            peerManager.clearAllPeers()
            peerManager.clearAllFingerprints()
            Log.d(TAG, "✅ Cleared all mesh service internal data")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing mesh service internal data: ${e.message}")
        }
    }
    
    /**
     * Clear all encryption and cryptographic data (for panic mode)
     */
    fun clearAllEncryptionData() {
        Log.w(TAG, "🚨 Clearing all encryption data")
        try {
            // Clear encryption service persistent identity (includes Ed25519 signing keys)
            encryptionService.clearPersistentIdentity()
            Log.d(TAG, "✅ Cleared all encryption data")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing encryption data: ${e.message}")
        }
    }
    
    /**
     * Check if we have an established Noise session with a peer
     */
    fun hasNoiseSession(peerID: String): Boolean {
        return encryptionService.hasEstablishedSession(peerID)
    }
    
    /**
     * Initiate Noise handshake with a peer for encrypted communication
     */
    fun initiateNoiseHandshake(peerID: String) {
        serviceScope.launch {
            try {
                if (!encryptionService.hasEstablishedSession(peerID)) {
                    Log.d(TAG, "📞 Starting Noise handshake with $peerID")
                    messageHandler.delegate?.initiateNoiseHandshake(peerID)
                } else {
                    Log.d(TAG, "✅ Already have Noise session with $peerID")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initiate handshake: ${e.message}")
            }
        }
    }
}

/**
 * Delegate interface for mesh service callbacks (maintains exact same interface)
 */
interface BluetoothMeshDelegate {
    fun didReceiveMessage(message: BitchatMessage)
    fun didUpdatePeerList(peers: List<String>)
    fun didReceiveChannelLeave(channel: String, fromPeer: String)
    fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String)
    fun didReceiveReadReceipt(messageID: String, recipientPeerID: String)
    fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String?
    fun getNickname(): String?
    fun isFavorite(peerID: String): Boolean
    // registerPeerPublicKey REMOVED - fingerprints now handled centrally in PeerManager
}
