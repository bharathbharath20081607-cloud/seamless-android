package com.seamless.android.p2p

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DirectP2PTransport - Lightweight serverless P2P messaging
 * 
 * Uses WebRTC-style signaling via existing Nostr relays for connection setup,
 * then establishes direct WebSocket connections between peers.
 * 
 * ARCHITECTURE:
 * 1. Peers exchange connection info via Nostr (one-time, encrypted)
 * 2. Direct WebSocket connections established between peers
 * 3. All messages flow P2P without relays
 * 
 * This achieves "serverless" by only using relays for initial handshake,
 * not for actual message delivery.
 */
class LibP2PTransport(private val context: Context) {
    
    companion object {
        private const val TAG = "DirectP2PTransport"
        private const val PING_INTERVAL_MS = 30_000L
        private const val CONNECTION_TIMEOUT_MS = 10_000L
        
        @Volatile
        private var INSTANCE: LibP2PTransport? = null
        
        fun getInstance(context: Context): LibP2PTransport {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LibP2PTransport(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)  // No timeout for WebSocket
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Active peer connections (peerId -> WebSocket)
    private val peerConnections = ConcurrentHashMap<String, WebSocket>()
    
    // Peer endpoints discovered via Nostr (peerId -> endpoint URL)
    private val peerEndpoints = ConcurrentHashMap<String, String>()
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount
    
    // Message handlers
    private val messageHandlers = mutableListOf<(P2PMessage) -> Unit>()
    
    data class P2PMessage(
        val fromPeerId: String,
        val payload: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Start the P2P transport
     */
    fun start() {
        _isConnected.value = true
        Log.i(TAG, "🚀 DirectP2P transport started")
        
        // Start connection maintenance
        startConnectionMaintenance()
    }
    
    /**
     * Stop the P2P transport
     */
    fun stop() {
        peerConnections.values.forEach { it.close(1000, "Shutdown") }
        peerConnections.clear()
        _isConnected.value = false
        Log.i(TAG, "DirectP2P transport stopped")
    }
    
    /**
     * Register a peer's endpoint for direct connection
     * Called when we receive peer info via Nostr signaling
     */
    fun registerPeerEndpoint(peerId: String, endpoint: String) {
        peerEndpoints[peerId] = endpoint
        Log.d(TAG, "Registered endpoint for ${peerId.take(16)}...")
    }
    
    /**
     * Check if a peer is online (has active connection)
     */
    fun isPeerOnline(peerId: String): Boolean {
        return peerConnections.containsKey(peerId)
    }
    
    /**
     * Send direct message to a peer
     * Returns true if sent successfully
     */
    fun sendDirect(peerId: String, payload: ByteArray): Boolean {
        // Check for existing connection
        val existingSocket = peerConnections[peerId]
        if (existingSocket != null) {
            return try {
                existingSocket.send(okio.ByteString.of(*payload))
                Log.d(TAG, "📤 Sent direct message to ${peerId.take(16)}...")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send: ${e.message}")
                peerConnections.remove(peerId)
                false
            }
        }
        
        // Try to establish connection
        val endpoint = peerEndpoints[peerId]
        if (endpoint != null) {
            return connectToPeer(peerId, endpoint, payload)
        }
        
        Log.d(TAG, "No endpoint for ${peerId.take(16)}... - falling back to Nostr")
        return false
    }
    
    /**
     * Connect to a peer and send message
     */
    private fun connectToPeer(peerId: String, endpoint: String, payload: ByteArray): Boolean {
        return try {
            val request = Request.Builder()
                .url(endpoint)
                .build()
            
            val socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "🔗 Connected to peer ${peerId.take(16)}...")
                    peerConnections[peerId] = webSocket
                    _peerCount.value = peerConnections.size
                    
                    // Send the pending message
                    webSocket.send(okio.ByteString.of(*payload))
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    val msg = P2PMessage(peerId, bytes.toByteArray())
                    messageHandlers.forEach { it(msg) }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = P2PMessage(peerId, text.toByteArray())
                    messageHandlers.forEach { it(msg) }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Connection failed to ${peerId.take(16)}...: ${t.message}")
                    peerConnections.remove(peerId)
                    _peerCount.value = peerConnections.size
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    peerConnections.remove(peerId)
                    _peerCount.value = peerConnections.size
                }
            })
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to peer: ${e.message}")
            false
        }
    }
    
    /**
     * Maintain connections and clean up stale ones
     */
    private fun startConnectionMaintenance() {
        scope.launch {
            while (isActive) {
                delay(60_000)  // Every minute
                
                // Clean up stale connections
                val stale = peerConnections.entries.filter { (_, socket) ->
                    try {
                        socket.send("ping")
                        false
                    } catch (e: Exception) {
                        true
                    }
                }
                stale.forEach { (peerId, _) ->
                    peerConnections.remove(peerId)
                }
                _peerCount.value = peerConnections.size
            }
        }
    }
    
    /**
     * Register a message handler
     */
    fun onMessage(handler: (P2PMessage) -> Unit) {
        messageHandlers.add(handler)
    }
    
    /**
     * Remove a message handler
     */
    fun removeMessageHandler(handler: (P2PMessage) -> Unit) {
        messageHandlers.remove(handler)
    }
    
    /**
     * Shutdown
     */
    fun shutdown() {
        stop()
        scope.cancel()
    }
}
