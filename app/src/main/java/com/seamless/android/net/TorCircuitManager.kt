@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
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
        // A Tor stream can pause for several seconds while a rendezvous circuit
        // is rebuilt. Timing out midway through DataInputStream.readFully() loses
        // framing alignment and manifests as an intermittent lost chat/photo.
        // Keep the deadline generous; an idle timeout still returns null and the
        // dedicated read loop remains cancellable on its IO dispatcher.
        const val READ_TIMEOUT_MS = 60_000
        const val KEEPALIVE_INTERVAL_MS = 15_000L    // Keep an established peer session warm
        const val CIRCUIT_HEALTH_CHECK_MS = 45_000L  // Health check every 45 seconds
        private const val STALE_ACTIVE_GRACE_MS = 10_000L // Prefer fresh inbound socket if existing idle > 10s
        private const val SOCKET_BUFFER_BYTES = 65_536
        private const val STREAM_BUFFER_BYTES = 16_384
        // Onion-service failures are often transient introduction-point or
        // rendezvous failures. Keep recovering in the background instead of
        // giving up after a short burst of retries.
        const val MAX_RECONNECT_ATTEMPTS = 20
        const val RECONNECT_BASE_DELAY_MS = 2_000L
        const val MAX_RECONNECT_DELAY_MS = 60_000L
        
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
    @Volatile private var lastKnownNetworkHandle: Long = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Health check job
    private var healthCheckJob: Job? = null
    
    init {
        registerNetworkCallback()
        startHealthCheck()
    }

    private fun canonicalizePeerKey(peerIdentifier: String): String {
        val trimmed = peerIdentifier.trim()
        if (trimmed.startsWith("pubkey:", ignoreCase = true)) {
            return trimmed.lowercase()
        }
        val canonical = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(trimmed)
        return canonical.ifBlank { trimmed.lowercase() }
    }

    private fun configureSocket(socket: Socket) {
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.sendBufferSize = SOCKET_BUFFER_BYTES
            socket.receiveBufferSize = SOCKET_BUFFER_BYTES
        } catch (_: Exception) {
        }
    }
    
    /**
     * Get or establish a connection to a peer.
     * NOTE: Does NOT hold the class-level mutex during the blocking socket.connect()
     * because that can take up to 60 seconds over Tor.
     */
    suspend fun getConnection(onionAddress: String): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connectionLock = connectionLocks.getOrPut(canonicalKey) { Mutex() }
        return connectionLock.withLock {
            val existing = mutex.withLock {
                activeConnection(canonicalKey)?.also { connection ->
                    connection.lastActivity = System.currentTimeMillis()
                }
            }
            if (existing != null) return@withLock existing

            // The per-peer lock stays held while connecting, but the class-wide
            // mutex does not. Other peers remain fully responsive.
            connectToPeer(canonicalKey)
        }
    }
    
    /**
     * Send data through an established connection
     */
    suspend fun sendData(onionAddress: String, data: ByteArray): Boolean {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = getConnection(canonicalKey) ?: return false
        
        // Per-peer write lock prevents interleaved writes (e.g. ACK + queued message)
        val writeLock = writeLocks.getOrPut(canonicalKey) { Mutex() }
        
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
            Log.e(TAG, "Failed to send data to $canonicalKey: ${e.message}")
            handleConnectionFailure(canonicalKey, connection, e.message ?: "Send failed")
            false
        }
    }
    
    /**
     * Receive data from a connection
     */
    suspend fun receiveData(onionAddress: String, expectedConnectionId: String? = null): ByteArray? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = connections[canonicalKey] ?: return null
        if (expectedConnectionId != null && connection.connectionId != expectedConnectionId) return null
        
        return try {
            withContext(Dispatchers.IO) {
                connection.input?.let { input ->
                    val size = input.readInt()
                    if (size <= 0 || size > OnionMessageProtocol.FRAME_SIZE * 2) {
                        Log.e(TAG, "Invalid data size from $canonicalKey: $size")
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
            Log.e(TAG, "Failed to receive data from $canonicalKey: ${e.message}")
            handleConnectionFailure(canonicalKey, connection, e.message ?: "Receive failed")
            null
        }
    }

    fun getActiveConnectionId(onionAddress: String): String? {
        return activeConnection(canonicalizePeerKey(onionAddress))?.connectionId
    }

    /**
     * A non-blocking liveness check for receipt delivery. Never start a fresh
     * 60-second onion dial solely to acknowledge a mailbox item.
     */
    fun hasActiveConnection(onionAddress: String): Boolean =
        activeConnection(canonicalizePeerKey(onionAddress)) != null
    
    /**
     * Disconnect from a peer
     */
    suspend fun disconnect(onionAddress: String) = mutex.withLock {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = connections.remove(canonicalKey)
        connection?.let { closeConnection(it) }
        updateStateFlow()
        Log.d(TAG, "Disconnected from $canonicalKey")
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
        return connections[canonicalizePeerKey(onionAddress)]?.state ?: ConnectionState.DISCONNECTED
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
        val canonicalKey = canonicalizePeerKey(peerIdentifier)
        val connectionLock = connectionLocks.getOrPut(canonicalKey) { Mutex() }
        connectionLock.withLock {
            mutex.withLock {
                try {
                    val now = System.currentTimeMillis()
                    val active = activeConnection(canonicalKey)
                    val isActiveFresh = active != null && (now - active.lastActivity) <= STALE_ACTIVE_GRACE_MS
                    if (active != null && isActiveFresh && !preferIncoming) {
                        try { socket.close() } catch (_: Exception) {}
                        return@withLock
                    }
                    if (active != null) {
                        closeConnection(active)
                    }

                    configureSocket(socket)
                    val connection = PeerConnection(
                        onionAddress = canonicalKey,
                        socket = socket,
                        input = DataInputStream(BufferedInputStream(socket.inputStream, STREAM_BUFFER_BYTES)),
                        output = DataOutputStream(BufferedOutputStream(socket.outputStream, STREAM_BUFFER_BYTES)),
                        state = ConnectionState.CONNECTED,
                        lastActivity = now,
                        reconnectAttempts = 0
                    )

                    connections[canonicalKey] = connection
                    updateStateFlow()
                    Log.i(TAG, "📥 Incoming connection registered for $canonicalKey")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register incoming connection: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }
    }
    
    // ========== Private Methods ==========
    
    private suspend fun connectToPeer(onionAddress: String): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val torProxy = TorManager.currentBestSocksAddress(context)
        if (torProxy == null) {
            Log.e(TAG, "Tor proxy not available")
            return null
        }
        
        val connection = connections.getOrPut(canonicalKey) {
            PeerConnection(canonicalKey)
        }
        
        connection.state = ConnectionState.CONNECTING
        updateStateFlow()
        
        return withContext(Dispatchers.IO) {
            var pendingSocket: Socket? = null
            try {
                val parts = canonicalKey.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 18888
                
                val proxy = Proxy(Proxy.Type.SOCKS, torProxy)
                val socket = Socket(proxy)
                pendingSocket = socket
                
                socket.connect(InetSocketAddress.createUnresolved(host, port), CONNECT_TIMEOUT_MS)
                configureSocket(socket)
                
                connection.socket = socket
                connection.input = DataInputStream(BufferedInputStream(socket.inputStream, STREAM_BUFFER_BYTES))
                connection.output = DataOutputStream(BufferedOutputStream(socket.outputStream, STREAM_BUFFER_BYTES))
                connection.state = ConnectionState.CONNECTED
                connection.lastActivity = System.currentTimeMillis()
                connection.reconnectAttempts = 0
                pendingSocket = null
                
                updateStateFlow()
                Log.i(TAG, "✅ Connected to $canonicalKey")
                
                connection
                
            } catch (e: Exception) {
                try { pendingSocket?.close() } catch (_: Exception) {}
                Log.e(TAG, "Failed to connect to $canonicalKey: ${e.message}")
                connection.lastError = e.message
                scheduleReconnect(canonicalKey, connection, e.message ?: "Connect failed")
                null
            }
        }
    }

    private fun activeConnection(onionAddress: String): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val existing = connections[canonicalKey] ?: return null
        val socket = existing.socket
        return existing.takeIf {
            it.state == ConnectionState.CONNECTED &&
                socket?.isConnected == true &&
                !socket.isClosed &&
                !socket.isInputShutdown &&
                !socket.isOutputShutdown &&
                it.input != null &&
                it.output != null
        }
    }
    
    private suspend fun handleConnectionFailure(
        onionAddress: String,
        failedConnection: PeerConnection,
        reason: String
    ) {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        mutex.withLock {
            val connection = connections[canonicalKey] ?: return
            if (connection.connectionId != failedConnection.connectionId) return
            if (connection.state == ConnectionState.RECONNECTING) return
            closeConnection(connection)
            
            scheduleReconnect(canonicalKey, connection, reason)
        }
    }

    /** Schedules every reconnect step, including a failed initial dial. */
    private fun scheduleReconnect(onionAddress: String, connection: PeerConnection, reason: String) {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        if (connection.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            connection.state = ConnectionState.FAILED
            connection.lastError = reason
            updateStateFlow()
            Log.e(TAG, "Connection to $canonicalKey failed after $MAX_RECONNECT_ATTEMPTS attempts")
            return
        }

        connection.reconnectAttempts++
        connection.state = ConnectionState.RECONNECTING
        connection.lastError = reason
        updateStateFlow()

        val exponent = (connection.reconnectAttempts - 1).coerceAtMost(5)
        val delay = (RECONNECT_BASE_DELAY_MS * (1L shl exponent))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        Log.w(TAG, "Reconnecting to $canonicalKey in ${delay}ms (attempt ${connection.reconnectAttempts})")
        scope.launch {
            delay(delay)
            getConnection(canonicalKey)
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
            if (socket == null || socket.isClosed || !socket.isConnected || socket.isInputShutdown || socket.isOutputShutdown) {
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
                val handle = network.networkHandle
                val previousHandle = lastKnownNetworkHandle
                lastKnownNetworkHandle = handle
                Log.d(TAG, "Network available (handle=$handle)")
                isNetworkAvailable.set(true)
                // Android network changes invalidate onion circuits. Close stale
                // sockets from the previous network and rebuild direct sessions
                // concurrently instead of sequentially blocking for 60s per peer.
                scope.launch {
                    val peersToReconnect = mutex.withLock {
                        val keys = connections.keys.toList()
                        if (previousHandle != 0L && previousHandle != handle) {
                            for ((_, connection) in connections) {
                                closeConnection(connection)
                                connection.state = ConnectionState.DISCONNECTED
                                connection.reconnectAttempts = 0
                            }
                            updateStateFlow()
                        }
                        keys
                    }
                    peersToReconnect.forEach { onionAddress ->
                        launch { getConnection(onionAddress) }
                    }
                }
            }
            
            override fun onLost(network: Network) {
                val activeNet = connectivityManager.activeNetwork
                val caps = activeNet?.let { connectivityManager.getNetworkCapabilities(it) }
                val stillOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (stillOnline || (lastKnownNetworkHandle != 0L && network.networkHandle != lastKnownNetworkHandle)) {
                    Log.d(TAG, "Previous network lost during handover, active network remains online — preserving circuits")
                    isNetworkAvailable.set(true)
                    return
                }
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
