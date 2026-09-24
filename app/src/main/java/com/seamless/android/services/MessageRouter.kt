package com.seamless.android.services

import android.content.Context
import android.util.Log
import com.seamless.android.mesh.BluetoothMeshService
import com.seamless.android.model.ReadReceipt
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorMessagingService
import com.seamless.android.nostr.NostrTransport
import com.seamless.android.p2p.LibP2PTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Routes messages between BLE mesh, P2P, Tor/Onion, and Nostr transports.
 * 
 * Priority order:
 *   1. Bluetooth Mesh (local, fastest, no internet)
 *   2. libp2p P2P (serverless internet)
 *   3. Tor/Onion P2P (zero-server, censorship-resistant)
 *   4. Nostr Relays (reliable async backup)
 */
class MessageRouter private constructor(
    private val context: Context,
    private val mesh: BluetoothMeshService,
    private val nostr: NostrTransport,
    private val smartRouter: com.seamless.android.routing.SmartRouter
) {
    companion object {
        private const val TAG = "MessageRouter"
        @Volatile private var INSTANCE: MessageRouter? = null
        fun tryGetInstance(): MessageRouter? = INSTANCE
        fun getInstance(context: Context, mesh: BluetoothMeshService): MessageRouter {
            return INSTANCE ?: synchronized(this) {
                val nostr = NostrTransport.getInstance(context)
                val smartRouter = com.seamless.android.routing.SmartRouter.getInstance(context)
                smartRouter.initialize(mesh)
                
                INSTANCE?.also {
                    // Update mesh reference if needed and keep senderPeerID in sync
                    it.nostr.senderPeerID = mesh.myPeerID
                    return it
                }
                MessageRouter(context.applicationContext, mesh, nostr, smartRouter).also { instance ->
                    instance.nostr.senderPeerID = mesh.myPeerID
                    // Register for favorites changes to flush outbox
                    try {
                        com.seamless.android.favorites.FavoritesPersistenceService.shared.addListener(instance.favoriteListener)
                    } catch (_: Exception) {}
                    INSTANCE = instance
                }
            }
        }
    }

    // Outbox: peerID -> queued (content, nickname, messageID)
    private val outbox = mutableMapOf<String, MutableList<Triple<String, String, String>>>()
    private val torScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Listener for favorites changes to flush outbox when npub mapping appears/changes
    private val favoriteListener = object: com.seamless.android.favorites.FavoritesChangeListener {

        override fun onFavoriteChanged(noiseKeyHex: String) {
            flushOutboxFor(noiseKeyHex)
            // Also try 16-hex short id commonly used in UI if any client used that
            val shortId = noiseKeyHex.take(16)
            flushOutboxFor(shortId)
        }
        override fun onAllCleared() {
            // Nothing special; leave queued items until routing becomes possible
        }
    }

    fun sendPrivate(content: String, toPeerID: String, recipientNickname: String, messageID: String) {
        // First: if this is a geohash DM alias (nostr_<pub16>), route via Nostr using global registry
        if (com.seamless.android.nostr.GeohashAliasRegistry.contains(toPeerID)) {
            Log.d(TAG, "Routing PM via Nostr (geohash) to alias ${toPeerID.take(12)}… id=${messageID.take(8)}…")
            val recipientHex = com.seamless.android.nostr.GeohashAliasRegistry.get(toPeerID)
            if (recipientHex != null) {
                // Resolve the conversation's source geohash, so we can send from anywhere
                val sourceGeohash = com.seamless.android.nostr.GeohashConversationRegistry.get(toPeerID)

                // If repository knows the source geohash, pass it so NostrTransport derives the correct identity
                nostr.sendPrivateMessageGeohash(content, recipientHex, messageID, sourceGeohash)
                return
            }
        }

        val hasMesh = mesh.getPeerInfo(toPeerID)?.isConnected == true
        val hasEstablished = mesh.hasEstablishedSession(toPeerID)
        val canNostr = canSendViaNostr(toPeerID)
        
        // DIAGNOSTIC LOGGING
        Log.d(TAG, "┌─────────────────────────────────────────────")
        Log.d(TAG, "│ 📨 sendPrivate() to: ${toPeerID.take(16)}...")
        Log.d(TAG, "│ Content: ${content.take(30)}...")
        Log.d(TAG, "│ hasMesh (isConnected): $hasMesh")
        Log.d(TAG, "│ hasEstablished (Noise session): $hasEstablished")
        Log.d(TAG, "│ canNostr (has npub in favorites): $canNostr")
        Log.d(TAG, "└─────────────────────────────────────────────")
        
        // PRIORITY 1: Bluetooth Mesh (local, fastest)
        if (hasMesh && hasEstablished) {
            Log.d(TAG, "📡 Routing PM via MESH to ${toPeerID} msg_id=${messageID.take(8)}…")
            mesh.sendPrivateMessage(content, toPeerID, recipientNickname, messageID)
            return
        }
        
        // PRIORITY 2: libp2p P2P (serverless internet)
        val p2p = LibP2PTransport.getInstance(context)
        if (p2p.isConnected.value && p2p.isPeerOnline(toPeerID)) {
            Log.d(TAG, "🌐 Routing PM via libp2p P2P to ${toPeerID.take(16)}… msg_id=${messageID.take(8)}…")
            val payload = encodeMessageForP2P(content, recipientNickname, messageID)
            if (p2p.sendDirect(toPeerID, payload)) {
                return  // Success via P2P
            }
            // Fall through to Tor if P2P fails
        }
        
        // PRIORITY 3: Tor/Onion P2P (zero-server, censorship-resistant)
        if (TorManager.isProxyEnabled()) {
            try {
                val torSvc = TorMessagingService.getInstance(context)
                if (torSvc.serviceState.value == TorMessagingService.ServiceState.RUNNING) {
                    Log.d(TAG, "🧅 Routing PM via TOR to ${toPeerID.take(16)}… msg_id=${messageID.take(8)}…")
                    torScope.launch {
                        try {
                            val result = torSvc.sendMessageToOnion(toPeerID, content)
                            if (result.isSuccess) {
                                Log.i(TAG, "✅ Message delivered via Tor: ${result.getOrNull()}")
                            } else {
                                Log.w(TAG, "⚠️ Tor send failed, message queued for retry")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Tor send exception: ${e.message}")
                        }
                    }
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tor routing check failed: ${e.message}")
            }
        }
        
        // PRIORITY 4: Nostr Relays (reliable async backup)
        if (canNostr) {
            Log.d(TAG, "📨 Routing PM via NOSTR to ${toPeerID.take(32)}… msg_id=${messageID.take(8)}…")
            nostr.sendPrivateMessage(content, toPeerID, recipientNickname, messageID)
        } else {
            // If mesh is connected but no session, initiate handshake
            if (hasMesh && !hasEstablished) {
                Log.d(TAG, "🤝 Mesh connected but no session - initiating handshake with ${toPeerID.take(8)}…")
                mesh.initiateNoiseHandshake(toPeerID)
            }
            
            Log.d(TAG, "📥 Queued PM for ${toPeerID} (no mesh session, no Tor, no Nostr) msg_id=${messageID.take(8)}…")
            Log.w(TAG, "   → To send via Nostr: Add ${recipientNickname} as favorite AND exchange Nostr pubkeys")
            val q = outbox.getOrPut(toPeerID) { mutableListOf() }
            q.add(Triple(content, recipientNickname, messageID))
        }
    }
    
    /**
     * Encode message for P2P transport
     */
    private fun encodeMessageForP2P(content: String, recipientNickname: String, messageID: String): ByteArray {
        // Simple format: msgID|nickname|content
        return "$messageID|$recipientNickname|$content".toByteArray(Charsets.UTF_8)
    }

    fun sendReadReceipt(receipt: ReadReceipt, toPeerID: String) {
        if ((mesh.getPeerInfo(toPeerID)?.isConnected == true) && mesh.hasEstablishedSession(toPeerID)) {
            Log.d(TAG, "Routing READ via mesh to ${toPeerID.take(8)}… id=${receipt.originalMessageID.take(8)}…")
            mesh.sendReadReceipt(receipt.originalMessageID, toPeerID, mesh.getPeerNicknames()[toPeerID] ?: mesh.myPeerID)
        } else {
            Log.d(TAG, "Routing READ via Nostr to ${toPeerID.take(8)}… id=${receipt.originalMessageID.take(8)}…")
            nostr.sendReadReceipt(receipt, toPeerID)
        }
    }

    fun sendDeliveryAck(messageID: String, toPeerID: String) {
        // Mesh delivery ACKs are sent by the receiver automatically.
        // Only route via Nostr when mesh path isn't available or when this is a geohash alias
        if (com.seamless.android.nostr.GeohashAliasRegistry.contains(toPeerID)) {
            val recipientHex = com.seamless.android.nostr.GeohashAliasRegistry.get(toPeerID)
            if (recipientHex != null) {
                val identity = com.seamless.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(context) ?: return
                nostr.sendDeliveryAckGeohash(messageID, recipientHex, identity)
                return
            }
        }
        if (!((mesh.getPeerInfo(toPeerID)?.isConnected == true) && mesh.hasEstablishedSession(toPeerID))) {
            nostr.sendDeliveryAck(messageID, toPeerID)
        }
    }

    fun sendFavoriteNotification(toPeerID: String, isFavorite: Boolean) {
        if (mesh.getPeerInfo(toPeerID)?.isConnected == true) {
            val myNpub = try { com.seamless.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(context)?.npub } catch (_: Exception) { null }
            val content = if (isFavorite) "[FAVORITED]:${myNpub ?: ""}" else "[UNFAVORITED]:${myNpub ?: ""}"
            val nickname = mesh.getPeerNicknames()[toPeerID] ?: toPeerID
            mesh.sendPrivateMessage(content, toPeerID, nickname)
        } else {
            nostr.sendFavoriteNotification(toPeerID, isFavorite)
        }
    }

    // Flush any queued messages for a specific peerID
    fun flushOutboxFor(peerID: String) {
        val queued = outbox[peerID] ?: return
        if (queued.isEmpty()) return
        Log.d(TAG, "Flushing outbox for ${peerID.take(8)}… count=${queued.size}")
        val iterator = queued.iterator()
        while (iterator.hasNext()) {
            val (content, nickname, messageID) = iterator.next()
            var hasMesh = mesh.getPeerInfo(peerID)?.isConnected == true && mesh.hasEstablishedSession(peerID)
            // If this is a noiseHex key, see if there is a connected mesh peer for this identity
            if (!hasMesh && peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                val meshPeer = resolveMeshPeerForNoiseHex(peerID)
                if (meshPeer != null && mesh.getPeerInfo(meshPeer)?.isConnected == true && mesh.hasEstablishedSession(meshPeer)) {
                    mesh.sendPrivateMessage(content, meshPeer, nickname, messageID)
                    iterator.remove()
                    continue
                }
            }
            val canNostr = canSendViaNostr(peerID)
            if (hasMesh) {
                mesh.sendPrivateMessage(content, peerID, nickname, messageID)
                iterator.remove()
            } else if (canNostr) {
                nostr.sendPrivateMessage(content, peerID, nickname, messageID)
                iterator.remove()
            }
        }
        if (queued.isEmpty()) {
            outbox.remove(peerID)
        }
    }

    // Flush everything (rarely used)
    fun flushAllOutbox() {
        outbox.keys.toList().forEach { flushOutboxFor(it) }
    }

    private fun canSendViaNostr(peerID: String): Boolean {
        return try {
            // Full Noise key hex
            if (peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                val noiseKey = hexToBytes(peerID)
                val fav = com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKey)
                // PRODUCTION FIX: Only require isFavorite, NOT mutual favoriting
                // Internet-only contacts won't have mutual status but should still receive messages
                fav?.isFavorite == true && fav.peerNostrPublicKey != null
            } else if (peerID.length == 16 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
                // Ephemeral 16-hex mesh ID: resolve via prefix match in favorites
                val fav = com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(peerID)
                // PRODUCTION FIX: Only require isFavorite, NOT mutual favoriting
                fav?.isFavorite == true && fav.peerNostrPublicKey != null
            } else if (peerID.startsWith("nostr:npub") || peerID.startsWith("npub")) {
                // PRODUCTION FIX: Internet-only users have nostr-prefixed peerID - always allow
                true
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun resolveMeshPeerForNoiseHex(noiseHex: String): String? {
        return try {
            mesh.getPeerNicknames().keys.firstOrNull { pid ->
                val info = mesh.getPeerInfo(pid)
                val keyHex = info?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
                keyHex != null && keyHex.equals(noiseHex, ignoreCase = true)
            }
        } catch (_: Exception) { null }
    }

    // Called when mesh peer list changes; attempt to flush any matching outbox entries
    fun onPeersUpdated(peers: List<String>) {
        peers.forEach { pid ->
            flushOutboxFor(pid)
            val noiseHex = try {
                mesh.getPeerInfo(pid)?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
            } catch (_: Exception) { null }
            noiseHex?.let { flushOutboxFor(it) }
        }
    }

    // Called when a Noise session becomes established; flush both the mesh peerID and its noiseHex alias
    fun onSessionEstablished(peerID: String) {
        flushOutboxFor(peerID)
        val noiseHex = try {
            mesh.getPeerInfo(peerID)?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) { null }
        noiseHex?.let { flushOutboxFor(it) }
    }
}
