package com.seamless.android.net

import android.content.Context
import android.util.Log
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.BitchatFilePacket
import com.seamless.android.nostr.NostrTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * HybridTransport - Unified transport layer with automatic fallback
 * 
 * Priority order for message delivery:
 * 1. Bluetooth Mesh (fastest, no internet needed, nearby only)
 * 2. Onion P2P (zero-server, long-distance, both must be online)
 * 3. Nostr (public relays, optional fallback for async delivery)
 * 
 * This is THE transport layer for Seamless - best of all worlds:
 * - LOCAL: Bluetooth mesh (offline, instant)
 * - LONG-DISTANCE: Onion P2P via Tor (zero servers, private)
 * - FALLBACK: Nostr (optional, for async delivery)
 */
class HybridTransport private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "HybridTransport"
        
        @Volatile
        private var instance: HybridTransport? = null
        
        fun getInstance(context: Context): HybridTransport {
            return instance ?: synchronized(this) {
                instance ?: HybridTransport(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Transport priority (Briar-inspired tiered connectivity)
    //
    // Tier 1: MESH     — Bluetooth/Wi-Fi Direct (offline, instant, no internet)
    // Tier 2: ONION    — Tor P2P (zero servers, long-distance, both online)
    // Tier 3: MAILBOX  — Store-and-forward (for offline peers)
    // Tier 4: NOSTR    — Public relays (optional async fallback)
    enum class TransportType {
        MESH,       // Bluetooth mesh - nearby, offline
        ONION,      // Tor P2P - long-distance, zero servers
        MAILBOX,    // Store-and-forward for offline peers
        NOSTR       // Public relays - async fallback (optional)
    }
    
    enum class DeliveryStatus {
        PENDING,
        SENT_MESH,
        SENT_ONION,
        SENT_NOSTR,
        DELIVERED,
        FAILED
    }
    
    data class MessageResult(
        val messageId: String,
        val status: DeliveryStatus,
        val transport: TransportType?,
        val error: String? = null
    )
    
    // Settings
    data class HybridSettings(
        val enableMesh: Boolean = true,
        val enableOnion: Boolean = true,
        val enableMailbox: Boolean = true,         // Store-and-forward for offline peers
        val enableNostrFallback: Boolean = false,   // OFF by default (user wants 0 servers)
        val smartTorLifecycle: Boolean = true,       // Only activate Tor when needed
        val meshTimeoutMs: Long = 5_000,
        val onionTimeoutMs: Long = 30_000,
        val mailboxTimeoutMs: Long = 60_000
    )
    
    private var settings = HybridSettings()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Status flow
    private val _status = MutableStateFlow<Map<TransportType, Boolean>>(emptyMap())
    val transportStatus: StateFlow<Map<TransportType, Boolean>> = _status.asStateFlow()
    
    /**
     * Configure transport settings
     */
    fun configure(newSettings: HybridSettings) {
        settings = newSettings
        Log.i(TAG, """
            🔧 HybridTransport configured:
            - Mesh: ${settings.enableMesh}
            - Onion: ${settings.enableOnion}
            - Nostr Fallback: ${settings.enableNostrFallback}
        """.trimIndent())
    }
    
    /**
     * Enable/disable Nostr fallback (public relays)
     * Default is OFF for zero-server mode
     */
    fun setNostrFallbackEnabled(enabled: Boolean) {
        settings = settings.copy(enableNostrFallback = enabled)
        Log.i(TAG, "Nostr fallback ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Send a message using the best available transport
     * Automatically tries fallback transports if primary fails
     */
    suspend fun sendMessage(
        recipientId: String,
        message: BitchatMessage,
        preferredTransport: TransportType? = null
    ): MessageResult {
        
        Log.d(TAG, "📤 Sending message to $recipientId via best transport...")
        
        // Determine transport order based on configuration and preference
        val transportOrder = when (preferredTransport) {
            TransportType.MESH -> listOf(TransportType.MESH, TransportType.ONION, TransportType.NOSTR)
            TransportType.ONION -> listOf(TransportType.ONION, TransportType.MESH, TransportType.NOSTR)
            TransportType.MAILBOX -> listOf(TransportType.MAILBOX, TransportType.ONION, TransportType.MESH, TransportType.NOSTR)
            TransportType.NOSTR -> listOf(TransportType.NOSTR, TransportType.ONION, TransportType.MESH)
            null -> listOf(TransportType.MESH, TransportType.ONION, TransportType.NOSTR)
        }
        
        // Try each transport in order
        for (transport in transportOrder) {
            when (transport) {
                TransportType.MESH -> {
                    if (!settings.enableMesh) continue
                    
                    val result = trySendViaMesh(recipientId, message)
                    if (result != null) return result
                }
                
                TransportType.ONION -> {
                    if (!settings.enableOnion) continue
                    
                    val result = trySendViaOnion(recipientId, message)
                    if (result != null) return result
                }

                TransportType.MAILBOX -> {
                    if (!settings.enableMailbox) continue
                    
                    val result = trySendViaMailbox(recipientId, message)
                    if (result != null) return result
                }
                
                TransportType.NOSTR -> {
                    if (!settings.enableNostrFallback) {
                        Log.d(TAG, "⏭️ Skipping Nostr (disabled for zero-server mode)")
                        continue
                    }
                    
                    val result = trySendViaNostr(recipientId, message)
                    if (result != null) return result
                }
            }
        }
        
        // All transports failed
        Log.e(TAG, "❌ All transports failed for message to $recipientId")
        return MessageResult(
            messageId = message.id,
            status = DeliveryStatus.FAILED,
            transport = null,
            error = "All transports failed"
        )
    }
    
    /**
     * Send a message using tiered transport with mailbox fallback.
     * Enhanced version with 4-tier priority:
     *   1. Mesh (instant, offline)
     *   2. Onion (zero-server, long-distance)
     *   3. Mailbox (store-and-forward for offline peers)
     *   4. Nostr (optional async fallback)
     */
    suspend fun sendMessageTiered(
        recipientId: String,
        message: BitchatMessage,
    ): MessageResult {
        Log.d(TAG, "📤 Sending message via tiered transport...")
        
        val transportOrder = listOf(
            TransportType.MESH,
            TransportType.ONION,
            TransportType.MAILBOX,
            TransportType.NOSTR
        )
        
        for (transport in transportOrder) {
            val result = when (transport) {
                TransportType.MESH -> {
                    if (!settings.enableMesh) null
                    else trySendViaMesh(recipientId, message)
                }
                TransportType.ONION -> {
                    if (!settings.enableOnion) null
                    else trySendViaOnion(recipientId, message)
                }
                TransportType.MAILBOX -> {
                    if (!settings.enableMailbox) null
                    else trySendViaMailbox(recipientId, message)
                }
                TransportType.NOSTR -> {
                    if (!settings.enableNostrFallback) null
                    else trySendViaNostr(recipientId, message)
                }
            }
            if (result != null) return result
        }
        
        return MessageResult(
            messageId = message.id,
            status = DeliveryStatus.FAILED,
            transport = null,
            error = "All tiered transports failed"
        )
    }
    
    /**
     * Send a file using the best available transport
     */
    suspend fun sendFile(
        recipientId: String,
        file: BitchatFilePacket,
        preferredTransport: TransportType? = null
    ): MessageResult {
        
        Log.d(TAG, "📤 Sending file ${file.fileName} to $recipientId...")
        
        // For files, prefer Mesh (fastest) or Onion (zero-server)
        val transportOrder = when (preferredTransport) {
            TransportType.MESH -> listOf(TransportType.MESH, TransportType.ONION)
            TransportType.ONION -> listOf(TransportType.ONION, TransportType.MESH)
            else -> listOf(TransportType.MESH, TransportType.ONION) // Files don't use Nostr
        }
        
        for (transport in transportOrder) {
            when (transport) {
                TransportType.MESH -> {
                    if (!settings.enableMesh) continue
                    
                    val result = trySendFileViaMesh(recipientId, file)
                    if (result != null) return result
                }
                
                TransportType.ONION -> {
                    if (!settings.enableOnion) continue
                    
                    val result = trySendFileViaOnion(recipientId, file)
                    if (result != null) return result
                }

                TransportType.MAILBOX -> {
                    // File transfer via mailbox not yet supported
                    continue
                }
                
                TransportType.NOSTR -> {
                    // Large files not supported over Nostr
                    continue
                }
            }
        }
        
        return MessageResult(
            messageId = file.transferId,
            status = DeliveryStatus.FAILED,
            transport = null,
            error = "All transports failed for file"
        )
    }
    
    // ===== Transport Implementations =====
    
    private suspend fun trySendViaMesh(
        recipientId: String,
        message: BitchatMessage
    ): MessageResult? {
        return try {
            Log.d(TAG, "📡 Trying Bluetooth Mesh...")
            
            val meshService = com.seamless.android.mesh.BluetoothMeshService.getInstance()
            if (meshService == null) {
                Log.d(TAG, "⏭️ Mesh service not initialized, skipping")
                return null
            }

            val peerInfo = meshService.getPeerInfo(recipientId)
            if (peerInfo?.isConnected != true) {
                Log.d(TAG, "⏭️ Peer not connected via mesh, skipping")
                return null
            }

            if (!meshService.hasEstablishedSession(recipientId)) {
                Log.d(TAG, "⏭️ No Noise session with peer, skipping mesh")
                return null
            }

            // Peer is connected and has a Noise session — send via mesh
            withTimeoutOrNull(settings.meshTimeoutMs) {
                meshService.sendPrivateMessage(
                    content = message.content,
                    recipientPeerID = recipientId,
                    recipientNickname = "",
                    messageID = message.id
                )
                true
            } ?: return null

            Log.i(TAG, "✅ Sent via Bluetooth Mesh")
            MessageResult(
                messageId = message.id,
                status = DeliveryStatus.SENT_MESH,
                transport = TransportType.MESH
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Mesh failed: ${e.message}")
            null
        }
    }
    
    private suspend fun trySendViaOnion(
        recipientId: String,
        message: BitchatMessage
    ): MessageResult? {
        return try {
            Log.d(TAG, "🧅 Trying Onion P2P...")
            
            val torService = TorMessagingService.getInstance(context)
            
            // Send via Tor with timeout
            val success = withTimeoutOrNull(settings.onionTimeoutMs) {
                val result = torService.sendMessage(recipientId, message.content)
                result.isSuccess
            }
            
            if (success == true) {
                Log.i(TAG, "✅ Sent via Onion P2P")
                MessageResult(
                    messageId = message.id,
                    status = DeliveryStatus.SENT_ONION,
                    transport = TransportType.ONION
                )
            } else null
            
        } catch (e: Exception) {
            Log.w(TAG, "Onion failed: ${e.message}")
            null
        }
    }
    
    private suspend fun trySendViaNostr(
        recipientId: String,
        message: BitchatMessage
    ): MessageResult? {
        return try {
            Log.d(TAG, "📡 Trying Nostr (fallback)...")
            
            val nostrTransport = NostrTransport.getInstance(context)
            
            // Send via Nostr
            nostrTransport.sendPrivateMessage(
                content = message.content,
                to = recipientId,
                recipientNickname = "",
                messageID = message.id
            )
            
            Log.i(TAG, "✅ Sent via Nostr (async)")
            MessageResult(
                messageId = message.id,
                status = DeliveryStatus.SENT_NOSTR,
                transport = TransportType.NOSTR
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Nostr failed: ${e.message}")
            null
        }
    }
    
    private suspend fun trySendFileViaMesh(
        recipientId: String,
        file: BitchatFilePacket
    ): MessageResult? {
        return try {
            Log.d(TAG, "📡 Trying file via Mesh...")
            
            val meshService = com.seamless.android.mesh.BluetoothMeshService.getInstance()
            if (meshService == null) {
                Log.d(TAG, "⏭️ Mesh service not initialized, skipping file")
                return null
            }

            val peerInfo = meshService.getPeerInfo(recipientId)
            if (peerInfo?.isConnected != true || !meshService.hasEstablishedSession(recipientId)) {
                Log.d(TAG, "⏭️ Peer not reachable via mesh for file, skipping")
                return null
            }

            withTimeoutOrNull(settings.meshTimeoutMs) {
                meshService.sendFilePrivate(recipientId, file)
                true
            } ?: return null

            Log.i(TAG, "✅ Sent file via Bluetooth Mesh")
            MessageResult(
                messageId = file.transferId,
                status = DeliveryStatus.SENT_MESH,
                transport = TransportType.MESH
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Mesh file failed: ${e.message}")
            null
        }
    }
    
    private suspend fun trySendFileViaOnion(
        recipientId: String,
        file: BitchatFilePacket
    ): MessageResult? {
        return try {
            Log.d(TAG, "🧅 Trying file via Onion P2P...")
            
            // TODO: Implement file sending over Onion when ready
            Log.d(TAG, "⏭️ Onion file transfer not yet implemented")
            null
            
        } catch (e: Exception) {
            Log.w(TAG, "Onion file failed: ${e.message}")
            null
        }
    }

    // ===== Mailbox Transport =====

    private suspend fun trySendViaMailbox(
        recipientId: String,
        message: BitchatMessage
    ): MessageResult? {
        return try {
            Log.d(TAG, "📬 Trying Mailbox (store-and-forward)...")
            
            val mailboxClient = MailboxClient.getInstance(context)
            val mailboxes = mailboxClient.getMailboxes()
            
            if (mailboxes.isEmpty()) {
                Log.d(TAG, "⏭️ No mailboxes registered")
                return null
            }
            
            // Encrypt the message for the recipient using EncryptionService
            val encryptedPayload = try {
                val encService = com.seamless.android.crypto.EncryptionService(context)
                encService.encrypt(message.content.toByteArray(), recipientId)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Encryption failed — refusing to send plaintext to mailbox: ${e.message}")
                return null
            }
            
            // Try each registered mailbox
            for (mailboxOnion in mailboxes) {
                val stored = withTimeoutOrNull(settings.mailboxTimeoutMs) {
                    mailboxClient.storeInMailbox(mailboxOnion, recipientId, encryptedPayload)
                }
                
                if (stored == true) {
                    Log.i(TAG, "✅ Stored encrypted message in mailbox for offline delivery")
                    return MessageResult(
                        messageId = message.id,
                        status = DeliveryStatus.SENT_ONION,  // Mailbox is Tor-based
                        transport = TransportType.MAILBOX
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            Log.w(TAG, "Mailbox failed: ${e.message}")
            null
        }
    }

    // ===== Smart Tor Lifecycle (Phase 5: Battery Optimization) =====

    /**
     * Determine whether Tor should be activated.
     *
     * Briar-inspired optimization: don't start Tor if all contacts
     * are reachable via Bluetooth mesh. This saves significant
     * battery on mobile devices.
     *
     * @param contactIds List of all contact IDs we need to communicate with
     * @param meshReachableIds Contacts currently reachable via Bluetooth mesh
     * @return true if Tor should be activated
     */
    fun shouldActivateTor(
        contactIds: Set<String>,
        meshReachableIds: Set<String>
    ): Boolean {
        if (!settings.smartTorLifecycle) return settings.enableOnion
        if (!settings.enableOnion) return false
        
        // If any contact is NOT reachable via mesh, we need Tor
        val unreachable = contactIds - meshReachableIds
        val needTor = unreachable.isNotEmpty()
        
        Log.d(TAG, "📶 Smart Tor: ${contactIds.size} contacts, " +
            "${meshReachableIds.size} mesh-reachable, " +
            "${unreachable.size} unreachable → Tor ${if (needTor) "NEEDED" else "NOT needed"}")
        
        return needTor
    }

    /**
     * Manage Tor lifecycle based on contact reachability.
     * Call this periodically or when mesh connectivity changes.
     * 
     * Actually starts/stops Tor based on whether contacts are reachable
     * via Bluetooth mesh (Briar-inspired battery optimization).
     */
    suspend fun updateTorLifecycle(
        contactIds: Set<String>,
        meshReachableIds: Set<String>
    ) {
        val needTor = shouldActivateTor(contactIds, meshReachableIds)
        val torRunning = TorManager.isProxyEnabled()
        
        if (needTor && !torRunning) {
            Log.i(TAG, "⚡ Activating Tor — unreachable contacts detected")
            TorPreferenceManager.set(context, TorMode.ON)
        } else if (!needTor && torRunning && settings.smartTorLifecycle) {
            // Tor is user-enabled and owned by its foreground service. Do not tear
            // down an onion address or active direct sessions merely because mesh
            // happened to be reachable during this observation.
            Log.d(TAG, "Keeping user-enabled Tor session alive while mesh is reachable")
        }
    }
    
    /**
     * Get current transport availability
     */
    fun getTransportStatus(): Map<TransportType, Boolean> {
        val torState = TorMessagingService.getInstance(context).serviceState.value
        val mailboxClient = MailboxClient.getInstance(context)
        val meshService = com.seamless.android.mesh.BluetoothMeshService.getInstance()
        
        return mapOf(
            TransportType.MESH to (meshService?.isActive == true),
            TransportType.ONION to (torState == TorMessagingService.ServiceState.RUNNING),
            TransportType.MAILBOX to mailboxClient.getMailboxes().isNotEmpty(),
            TransportType.NOSTR to false    // TODO: Check NostrRelayManager status
        )
    }
}
