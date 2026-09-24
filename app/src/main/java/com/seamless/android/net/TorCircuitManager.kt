package com.seamless.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Tor circuit connections for reliable messaging
 * 
 * Features:
 * - Connection pooling per peer
 * - Automatic reconnection on circuit failure
 * - Network change handling (WiFi ↔ mobile)
 * - Circuit health monitoring
 * - Connection cleanup
 */
class TorCircuitManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TorCircuitManager"
        
        // Connection timeouts
        const val CONNECT_TIMEOUT_MS = 60_000       // 60 seconds for Tor circuits
        const val READ_TIMEOUT_MS = 5_000           // 5 seconds read timeout for highly responsive connection polling
        const val KEEPALIVE_INTERVAL_MS = 15_000L    // Keep an established peer session warm
        const val CIRCUIT_HEALTH_CHECK_MS = 60_000L  // Health check every minute
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_BASE_DELAY_MS = 2_000L
        
        @Volatile
        private var instance: TorCircuitManager? = null
        
        fun getInstance(context: Context): TorCircuitManager {
            return instance ?: synchronized(this) {
                instance ?: TorCircuitManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Connection state for a peer
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        FAILED
    }
    
    /**
     * Peer connection info
     */
    data class PeerConnection(
        val onionAddress: String,
        val connectionId: String = UUID.randomUUID().toString(),
        var socket: Socket? = null,
        var input: DataInputStream? = null,
        var output: DataOutputStream? = null,
        var state: ConnectionState = ConnectionState.DISCONNECTED,
        var lastActivity: Long = 0L,
        var reconnectAttempts: Int = 0,
        var lastError: String? = null
    )
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    
    // Active connections
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    
    // Per-peer write locks to prevent interleaved writes on same socket
    private val writeLocks = ConcurrentHashMap<String, Mutex>()

    // A peer can be reached by both an outbound circuit and an incoming onion
    // connection at the same time. Serialising that lifecycle prevents the two
    // paths from replacing each other's socket while a frame is in flight.
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()
    
    // Connection state flows
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()
    
    // Network state
    private val isNetworkAvailable = AtomicBoolean(false)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Health check job
    private var healthCheckJob: Job? = null
    
    init {
        registerNetworkCallback()
        startHealthCheck()
    }
    
    /**
     * Get or establish a connection to a peer.
     * NOTE: Does NOT hold the class-level mutex during the blocking socket.connect()
     * because that can take up to 60 seconds over Tor.
     */
    suspend fun getConnection(onionAddress: String): PeerConnection? {
        val connectionLock = connectionLocks.getOrPut(onionAddress) { Mutex() }
        return connectionLock.withLock {
            val existing = mutex.withLock {
                activeConnection(onionAddress)?.also { connection ->
                    connection.lastActivity = System.currentTimeMillis()
                }
            }
            if (existing != null) return@withLock existing

            // The per-peer lock stays held while connecting, but the class-wide
            // mutex does not. Other peers remain fully responsive.
            connectToPeer(onionAddress)
        }
    }
    
    /**
     * Send data through an established connection
     */
    suspend fun sendData(onionAddress: String, data: ByteArray): Boolean {
        val connection = getConnection(onionAddress) ?: return false
        
        // Per-peer write lock prevents interleaved writes (e.g. ACK + queued message)
        val writeLock = writeLocks.getOrPut(onionAddress) { Mutex() }
        
        return try {
            writeLock.withLock {
                withContext(Dispatchers.IO) {
                    connection.output?.let { out ->
                        out.writeInt(data.size)
                        out.write(data)
                        out.flush()
                        connection.lastActivity = System.currentTimeMillis()
                        true
                    } ?: false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to $onionAddress: ${e.message}")
            handleConnectionFailure(onionAddress, connection, e.message ?: "Send failed")
            false
        }
    }
    
    /**
     * Receive data from a connection
     */
    suspend fun receiveData(onionAddress: String, expectedConnectionId: String? = null): ByteArray? {
        val connection = connections[onionAddress] ?: return null
        if (expectedConnectionId != null && connection.connectionId != expectedConnectionId) return null
        
        return try {
            withContext(Dispatchers.IO) {
                connection.input?.let { input ->
                    val size = input.readInt()
                    if (size <= 0 || size > OnionMessageProtocol.FRAME_SIZE * 2) {
                        Log.e(TAG, "Invalid data size from $onionAddress: $size")
                        return@withContext null
                    }
                    val data = ByteArray(size)
                    input.readFully(data)
                    connection.lastActivity = System.currentTimeMillis()
                    data
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            // Expected normal behavior when connection is idle. Do not close the socket!
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data from $onionAddress: ${e.message}")
            handleConnectionFailure(onionAddress, connection, e.message ?: "Receive failed")
            null
        }
    }

    fun getActiveConnectionId(onionAddress: String): String? {
        return activeConnection(onionAddress)?.connectionId
    }

    /**
     * A non-blocking liveness check for receipt delivery. Never start a fresh
     * 60-second onion dial solely to acknowledge a mailbox item.
     */
    fun hasActiveConnection(onionAddress: String): Boolean =
        activeConnection(onionAddress) != null
    
    /**
     * Disconnect from a peer
     */
    suspend fun disconnect(onionAddress: String) = mutex.withLock {
        val connection = connections.remove(onionAddress)
        connection?.let { closeConnection(it) }
        updateStateFlow()
        Log.d(TAG, "Disconnected from $onionAddress")
    }
    
    /**
     * Disconnect all peers
     */
    suspend fun disconnectAll() = mutex.withLock {
        for ((address, connection) in connections) {
            try {
                closeConnection(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error closing connection to $address: ${e.message}")
            }
        }
        connections.clear()
        updateStateFlow()
        Log.d(TAG, "Disconnected all peers")
    }
    
    /**
     * Get connection state for a peer
     */
    fun getConnectionState(onionAddress: String): ConnectionState {
        return connections[onionAddress]?.state ?: ConnectionState.DISCONNECTED
    }
    
    /**
     * Get all connected peers
     */
    fun getConnectedPeers(): List<String> {
        return connections.entries
            .filter { it.value.state == ConnectionState.CONNECTED }
            .map { it.key }
    }
    
    /**
     * Register an incoming connection from TorHiddenServiceManager.
     * This wires incoming Tor hidden service connections into the connection pool
     * so they can be used by sendData()/receiveData() just like outbound connections.
     * 
     * The peerIdentifier is typically the peer's onion address, sent as a handshake
     * in the first message frame after connection.
     */
    suspend fun addIncomingConnection(
        peerIdentifier: String,
        socket: Socket,
        preferIncoming: Boolean
    ) {
        val connectionLock = connectionLocks.getOrPut(peerIdentifier) { Mutex() }
        connectionLock.withLock {
            mutex.withLock {
                try {
                    val active = activeConnection(peerIdentifier)
                    if (active != null && !preferIncoming) {
                        try { socket.close() } catch (_: Exception) {}
                        return@withLock
                    }
                    if (active != null) {
                        closeConnection(active)
                    }

                    socket.soTimeout = READ_TIMEOUT_MS
                    val connection = PeerConnection(
                        onionAddress = peerIdentifier,
                        socket = socket,
                        input = DataInputStream(socket.inputStream),
                        output = DataOutputStream(socket.outputStream),
                        state = ConnectionState.CONNECTED,
                        lastActivity = System.currentTimeMillis(),
                        reconnectAttempts = 0
                    )

                    connections[peerIdentifier] = connection
                    updateStateFlow()
                    Log.i(TAG, "📥 Incoming connection registered for $peerIdentifier")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register incoming connection: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }
    }
    
    // ========== Private Methods ==========
    
    private suspend fun connectToPeer(onionAddress: String): PeerConnection? {
        val torProxy = TorManager.currentBestSocksAddress(context)
        if (torProxy == null) {
            Log.e(TAG, "Tor proxy not available")
            return null
        }
        
        val connection = connections.getOrPut(onionAddress) {
            PeerConnection(onionAddress)
        }
        
        connection.state = ConnectionState.CONNECTING
        updateStateFlow()
        
        return withContext(Dispatchers.IO) {
            var pendingSocket: Socket? = null
            try {
                val parts = onionAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 18888
                
                val proxy = Proxy(Proxy.Type.SOCKS, torProxy)
                val socket = Socket(proxy)
                pendingSocket = socket
                
                socket.connect(InetSocketAddress.createUnresolved(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                
                connection.socket = socket
                connection.input = DataInputStream(socket.inputStream)
                connection.output = DataOutputStream(socket.outputStream)
                connection.state = ConnectionState.CONNECTED
                connection.lastActivity = System.currentTimeMillis()
                connection.reconnectAttempts = 0
                pendingSocket = null
                
                updateStateFlow()
                Log.i(TAG, "✅ Connected to $onionAddress")
                
                connection
                
            } catch (e: Exception) {
                try { pendingSocket?.close() } catch (_: Exception) {}
                Log.e(TAG, "Failed to connect to $onionAddress: ${e.message}")
                connection.lastError = e.message
                scheduleReconnect(onionAddress, connection, e.message ?: "Connect failed")
                null
            }
        }
    }

    private fun activeConnection(onionAddress: String): PeerConnection? {
        val existing = connections[onionAddress] ?: return null
        val socket = existing.socket
        return existing.takeIf {
            it.state == ConnectionState.CONNECTED &&
                socket?.isConnected == true &&
                !socket.isClosed &&
                it.input != null &&
                it.output != null
        }
    }
    
    private suspend fun handleConnectionFailure(
        onionAddress: String,
        failedConnection: PeerConnection,
        reason: String
    ) {
        mutex.withLock {
            val connection = connections[onionAddress] ?: return
            if (connection.connectionId != failedConnection.connectionId) return
            if (connection.state == ConnectionState.RECONNECTING) return
            closeConnection(connection)
            
            scheduleReconnect(onionAddress, connection, reason)
        }
    }

    /** Schedules every reconnect step, including a failed initial dial. */
    private fun scheduleReconnect(onionAddress: String, connection: PeerConnection, reason: String) {
        if (connection.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            connection.state = ConnectionState.FAILED
            connection.lastError = reason
            updateStateFlow()
            Log.e(TAG, "Connection to $onionAddress failed after $MAX_RECONNECT_ATTEMPTS attempts")
            return
        }

        connection.reconnectAttempts++
        connection.state = ConnectionState.RECONNECTING
        connection.lastError = reason
        updateStateFlow()

        val delay = RECONNECT_BASE_DELAY_MS * (1 shl (connection.reconnectAttempts - 1))
        Log.w(TAG, "Reconnecting to $onionAddress in ${delay}ms (attempt ${connection.reconnectAttempts})")
        scope.launch {
            delay(delay)
            getConnection(onionAddress)
        }
    }
    
    private fun closeConnection(connection: PeerConnection) {
        try {
            connection.input?.close()
            connection.output?.close()
            connection.socket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        connection.socket = null
        connection.input = null
        connection.output = null
    }
    
    private fun updateStateFlow() {
        _connectionStates.value = connections.mapValues { it.value.state }
    }
    
    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(CIRCUIT_HEALTH_CHECK_MS)
                checkAllConnections()
            }
        }
    }
    
    private suspend fun checkAllConnections() = mutex.withLock {
        for ((address, connection) in connections) {
            if (connection.state != ConnectionState.CONNECTED) continue
            
            // Check if the underlying socket is still alive
            val socket = connection.socket
            if (socket == null || socket.isClosed || !socket.isConnected) {
                Log.d(TAG, "Connection to $address is dead (socket closed/null), cleaning up")
                closeConnection(connection)
                connection.state = ConnectionState.DISCONNECTED
            }
        }
        
        updateStateFlow()
    }
    
    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                isNetworkAvailable.set(true)
                // Android network changes invalidate onion circuits. Rebuild the
                // direct sessions we already know about immediately instead of
                // waiting for a later health-check or a user action.
                scope.launch {
                    connections.keys.toList().forEach { onionAddress ->
                        getConnection(onionAddress)
                    }
                }
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                isNetworkAvailable.set(false)
                
                // Mark all connections as disconnected
                scope.launch {
                    mutex.withLock {
                        for ((_, connection) in connections) {
                            closeConnection(connection)
                            connection.state = ConnectionState.DISCONNECTED
                        }
                        updateStateFlow()
                    }
                }
            }
            
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                Log.d(TAG, "Network changed: WiFi=$hasWifi, Cellular=$hasCellular")
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        
        // Check initial state using modern API
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        isNetworkAvailable.set(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        healthCheckJob?.cancel()
        scope.launch { disconnectAll() }
        
        networkCallback?.let {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
