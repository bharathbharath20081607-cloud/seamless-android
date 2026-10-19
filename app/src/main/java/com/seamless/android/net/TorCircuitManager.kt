@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Tor circuit connections for reliable messaging
 *
 * Features:
 * - One socket per peer, with every socket represented by its own
 *   [PeerConnection] instance (so a stale reader can never act on, or tear
 *   down, a newer connection)
 * - Deterministic tie-break when both peers dial each other simultaneously
 * - Background dials that outlive a caller's wait budget (a slow onion
 *   rendezvous is never cancelled half-way)
 * - Dead-circuit watchdog: SOCKS sockets keep accepting writes after the
 *   Tor circuit behind them has died, so liveness is judged by *received*
 *   frames, not by writes
 * - Frame-safe reads that cannot lose alignment on an idle timeout
 * - Default-network aware recovery (WiFi <-> mobile) without reacting to
 *   unrelated secondary networks
 */
class TorCircuitManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TorCircuitManager"

        // Connection timeouts
        const val CONNECT_TIMEOUT_MS = 60_000       // 60 seconds for Tor circuits
        // Idle read timeout. A timeout before the first byte of a frame is
        // harmless; a timeout inside a frame is retried (see readFrame) so
        // framing alignment is never lost.
        const val READ_TIMEOUT_MS = 60_000
        const val KEEPALIVE_INTERVAL_MS = 15_000L    // Keep an established peer session warm
        const val CIRCUIT_HEALTH_CHECK_MS = 15_000L  // Health/liveness sweep
        // A healthy peer pings every KEEPALIVE_INTERVAL_MS and answers our pings, so
        // an established connection that has *received nothing* for this long is a
        // dead circuit even though the local SOCKS socket still looks open.
        const val DEAD_CONNECTION_MS = 55_000L
        // An existing connection that is still receiving traffic within this window
        // is considered healthy when a duplicate connection arrives.
        private const val STALE_ACTIVE_GRACE_MS = 50_000L
        private const val SOCKET_BUFFER_BYTES = 65_536
        private const val STREAM_BUFFER_BYTES = 16_384
        // Mid-frame stall tolerance: this many consecutive read timeouts inside a
        // single frame are tolerated before the connection is declared broken.
        private const val MAX_MID_FRAME_STALLS = 3
        private const val MAX_PARALLEL_REDIAL = 4
        // Onion-service failures are often transient introduction-point or
        // rendezvous failures. Keep recovering in the background instead of
        // giving up after a short burst of retries.
        const val MAX_RECONNECT_ATTEMPTS = 20
        const val RECONNECT_BASE_DELAY_MS = 2_000L
        const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val SLOW_RECONNECT_DELAY_MS = 300_000L

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
     * Peer connection info. Each established socket gets its own instance
     * (and therefore its own [connectionId]).
     */
    data class PeerConnection(
        val onionAddress: String,
        val connectionId: String = UUID.randomUUID().toString(),
        var socket: Socket? = null,
        var input: DataInputStream? = null,
        var output: DataOutputStream? = null,
        @Volatile var state: ConnectionState = ConnectionState.DISCONNECTED,
        @Volatile var lastActivity: Long = 0L,
        @Volatile var lastReceivedAt: Long = 0L,
        var reconnectAttempts: Int = 0,
        var lastError: String? = null,
        val isIncoming: Boolean = false
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    // Active connections
    private val connections = ConcurrentHashMap<String, PeerConnection>()

    // Per-peer write locks to prevent interleaved writes on same socket
    private val writeLocks = ConcurrentHashMap<String, Mutex>()

    // A peer can be reached by both an outbound circuit and an incoming onion
    // connection at the same time. Serialising the *registration* of those
    // sockets (never the dial itself) prevents the two paths from replacing
    // each other's socket while a frame is in flight.
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()

    // At most one in-flight dial per peer. Dials run in this manager's scope so
    // a caller that stops waiting never cancels a slow onion rendezvous.
    private val dials = ConcurrentHashMap<String, Deferred<PeerConnection?>>()

    // Emits a peer key whenever a connection (in or out) becomes usable.
    private val _connectionEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val connectionEvents: SharedFlow<String> = _connectionEvents.asSharedFlow()

    /**
     * Decides which socket survives when both peers dial each other at once.
     * Must be deterministic and symmetric (set by TorMessagingService).
     */
    @Volatile
    var preferIncomingResolver: ((String) -> Boolean)? = null

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

    private fun preferIncoming(peerKey: String): Boolean =
        runCatching { preferIncomingResolver?.invoke(peerKey) }.getOrNull() ?: false

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
     * Get or establish a connection to a peer. Waits for as long as the dial
     * takes (bounded by [CONNECT_TIMEOUT_MS]).
     */
    suspend fun getConnection(onionAddress: String): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        activeConnection(canonicalKey)?.let { connection ->
            connection.lastActivity = System.currentTimeMillis()
            return connection
        }
        return startDial(canonicalKey).await()
    }

    /**
     * Like [getConnection] but stops *waiting* after [budgetMs]. The dial itself
     * keeps running in the background so a slow cold rendezvous still completes
     * and the next delivery cycle finds a ready circuit.
     */
    suspend fun getConnectionWithin(onionAddress: String, budgetMs: Long): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        activeConnection(canonicalKey)?.let { connection ->
            connection.lastActivity = System.currentTimeMillis()
            return connection
        }
        val dial = startDial(canonicalKey)
        return withTimeoutOrNull(budgetMs) { dial.await() }
    }

    private fun startDial(canonicalKey: String): Deferred<PeerConnection?> {
        dials[canonicalKey]?.takeIf { it.isActive }?.let { return it }
        synchronized(dials) {
            dials[canonicalKey]?.takeIf { it.isActive }?.let { return it }
            val dial = scope.async { dialAndRegister(canonicalKey) }
            dials[canonicalKey] = dial
            dial.invokeOnCompletion { dials.remove(canonicalKey, dial) }
            return dial
        }
    }

    /**
     * Send data through an established connection
     */
    suspend fun sendData(onionAddress: String, data: ByteArray): Boolean {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        // Per-peer write lock prevents interleaved writes (e.g. ACK + queued message)
        val writeLock = writeLocks.getOrPut(canonicalKey) { Mutex() }

        var connection = getConnection(canonicalKey) ?: return false
        repeat(2) {
            try {
                val written = writeLock.withLock {
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
                if (written) return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send data to $canonicalKey: ${e.message}")
                handleConnectionFailure(canonicalKey, connection, e.message ?: "Send failed")
            }
            // The connection was replaced or closed under us. Retry once on the
            // current healthy connection, but never start a fresh multi-second
            // dial from inside a send.
            val next = activeConnection(canonicalKey)
            if (next == null || next === connection) return false
            connection = next
        }
        return false
    }

    /**
     * Receive one frame. Returns null on an idle timeout (nothing consumed) or
     * when the connection was replaced; throws-and-handles real failures.
     */
    suspend fun receiveData(onionAddress: String, expectedConnectionId: String? = null): ByteArray? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = connections[canonicalKey] ?: return null
        if (expectedConnectionId != null && connection.connectionId != expectedConnectionId) return null

        return try {
            withContext(Dispatchers.IO) {
                val input = connection.input ?: return@withContext null
                val data = readFrame(input)
                if (data != null) {
                    val now = System.currentTimeMillis()
                    connection.lastActivity = now
                    connection.lastReceivedAt = now
                }
                data
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data from $canonicalKey: ${e.message}")
            handleConnectionFailure(canonicalKey, connection, e.message ?: "Receive failed")
            null
        }
    }

    /**
     * Reads one length-prefixed frame without ever losing alignment.
     * A timeout before the first byte consumed nothing and returns null. Once a
     * frame has started, timeouts are retried a bounded number of times and then
     * fail the connection (so the stream is re-established, never mis-framed).
     */
    private fun readFrame(input: DataInputStream): ByteArray? {
        val first = try {
            input.read()
        } catch (e: SocketTimeoutException) {
            return null
        }
        if (first < 0) throw EOFException("Peer closed the connection")

        val header = ByteArray(4)
        header[0] = first.toByte()
        readFullyResilient(input, header, 1, 3)
        val size = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        if (size <= 0 || size > OnionMessageProtocol.FRAME_SIZE * 2) {
            // A bad length means the stream is out of sync; resync by reconnecting.
            throw IOException("Invalid frame size: $size")
        }
        val data = ByteArray(size)
        readFullyResilient(input, data, 0, size)
        return data
    }

    private fun readFullyResilient(input: DataInputStream, buffer: ByteArray, offset: Int, length: Int) {
        var read = 0
        var stalls = 0
        while (read < length) {
            try {
                val n = input.read(buffer, offset + read, length - read)
                if (n < 0) throw EOFException("Peer closed the connection mid-frame")
                read += n
                stalls = 0
            } catch (e: SocketTimeoutException) {
                if (++stalls >= MAX_MID_FRAME_STALLS) throw e
            }
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
                    if (active != null) {
                        // Keep our own outbound circuit only if it is demonstrably
                        // alive (still *receiving*) and the deterministic tie-break
                        // says outbound wins. Every other duplicate replaces the old
                        // socket: a peer that redials usually saw the old one die.
                        val activeHealthy = (now - active.lastReceivedAt) <= STALE_ACTIVE_GRACE_MS
                        val keepActive = !active.isIncoming && activeHealthy && !preferIncoming
                        if (keepActive) {
                            try { socket.close() } catch (_: Exception) {}
                            return@withLock
                        }
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
                        lastReceivedAt = now,
                        reconnectAttempts = 0,
                        isIncoming = true
                    )

                    connections[canonicalKey] = connection
                    updateStateFlow()
                    _connectionEvents.tryEmit(canonicalKey)
                    Log.i(TAG, "📥 Incoming connection registered for $canonicalKey")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register incoming connection: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }
    }

    // ========== Private Methods ==========

    /**
     * Dials a peer and registers the resulting socket. Runs in this manager's
     * scope (see [startDial]); the blocking connect holds no lock, so incoming
     * connections from the same peer are never delayed by an outbound dial.
     */
    private suspend fun dialAndRegister(canonicalKey: String): PeerConnection? {
        val torProxy = TorManager.currentBestSocksAddress(context)
        if (torProxy == null) {
            Log.e(TAG, "Tor proxy not available")
            return null
        }
        // An incoming connection may have arrived while this dial was queued.
        activeConnection(canonicalKey)?.let { return it }

        val placeholder = connections.getOrPut(canonicalKey) { PeerConnection(canonicalKey) }
        placeholder.state = ConnectionState.CONNECTING
        updateStateFlow()

        var pendingSocket: Socket? = null
        return try {
            val socket = withContext(Dispatchers.IO) {
                val parts = canonicalKey.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 18888
                val created = Socket(Proxy(Proxy.Type.SOCKS, torProxy))
                pendingSocket = created
                created.connect(InetSocketAddress.createUnresolved(host, port), CONNECT_TIMEOUT_MS)
                created
            }
            configureSocket(socket)
            registerOutbound(canonicalKey, socket)
        } catch (e: CancellationException) {
            try { pendingSocket?.close() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            try { pendingSocket?.close() } catch (_: Exception) {}
            Log.e(TAG, "Failed to connect to $canonicalKey: ${e.message}")
            placeholder.lastError = e.message
            scheduleReconnect(canonicalKey, placeholder, e.message ?: "Connect failed")
            null
        }
    }

    private suspend fun registerOutbound(canonicalKey: String, socket: Socket): PeerConnection? {
        val connectionLock = connectionLocks.getOrPut(canonicalKey) { Mutex() }
        return connectionLock.withLock {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val active = activeConnection(canonicalKey)
                // If the peer reached us while we were dialing, the symmetric
                // tie-break decides which of the two sockets survives.
                val keepActive = active != null && (
                    !active.isIncoming ||
                        (preferIncoming(canonicalKey) && (now - active.lastReceivedAt) <= STALE_ACTIVE_GRACE_MS)
                    )
                if (keepActive) {
                    try { socket.close() } catch (_: Exception) {}
                    active
                } else {
                    if (active != null) closeConnection(active)
                    val established = PeerConnection(
                        onionAddress = canonicalKey,
                        socket = socket,
                        input = DataInputStream(BufferedInputStream(socket.inputStream, STREAM_BUFFER_BYTES)),
                        output = DataOutputStream(BufferedOutputStream(socket.outputStream, STREAM_BUFFER_BYTES)),
                        state = ConnectionState.CONNECTED,
                        lastActivity = now,
                        lastReceivedAt = now,
                        reconnectAttempts = 0,
                        isIncoming = false
                    )
                    connections[canonicalKey] = established
                    updateStateFlow()
                    _connectionEvents.tryEmit(canonicalKey)
                    Log.i(TAG, "✅ Connected to $canonicalKey")
                    established
                }
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
            // Ignore failures reported by a reader/writer of an already-replaced socket.
            if (connection.connectionId != failedConnection.connectionId) return
            if (connection.state == ConnectionState.RECONNECTING) return
            closeConnection(connection)

            scheduleReconnect(canonicalKey, connection, reason)
        }
    }

    /** Schedules every reconnect step, including a failed initial dial. */
    private fun scheduleReconnect(onionAddress: String, connection: PeerConnection, reason: String) {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        connection.lastError = reason

        if (connection.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            // Never give up on a contact permanently: drop to a slow background
            // retry instead of leaving the peer unreachable until the app restarts.
            connection.state = ConnectionState.FAILED
            updateStateFlow()
            Log.e(TAG, "Connection to $canonicalKey failed after $MAX_RECONNECT_ATTEMPTS attempts; retrying slowly")
            scope.launch {
                delay(SLOW_RECONNECT_DELAY_MS)
                connection.reconnectAttempts = MAX_RECONNECT_ATTEMPTS / 2
                getConnection(canonicalKey)
            }
            return
        }

        connection.reconnectAttempts++
        connection.state = ConnectionState.RECONNECTING
        updateStateFlow()

        val exponent = (connection.reconnectAttempts - 1).coerceAtMost(5)
        val base = (RECONNECT_BASE_DELAY_MS * (1L shl exponent)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        // Jitter keeps many contacts from redialing in lock-step after a network flap.
        val delay = base + (Math.random() * (base / 4)).toLong()
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
                try {
                    checkAllConnections()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Health check failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Liveness sweep. Writes to a Tor SOCKS socket keep succeeding after the
     * circuit behind it died, so a connection is considered dead when its socket
     * is gone *or* it has received nothing for [DEAD_CONNECTION_MS] (several
     * missed keepalives). Dead connections are closed and redialed immediately
     * instead of silently swallowing messages until an ACK retry notices.
     */
    private suspend fun checkAllConnections() {
        val now = System.currentTimeMillis()
        val dead = mutex.withLock {
            val found = mutableListOf<Pair<String, PeerConnection>>()
            for ((address, connection) in connections) {
                if (connection.state != ConnectionState.CONNECTED) continue
                val socket = connection.socket
                val socketGone = socket == null || socket.isClosed || !socket.isConnected ||
                    socket.isInputShutdown || socket.isOutputShutdown
                val silent = now - connection.lastReceivedAt > DEAD_CONNECTION_MS
                if (socketGone || silent) {
                    Log.w(
                        TAG,
                        "Connection to $address is dead (" +
                            (if (socketGone) "socket closed" else "no frames for ${now - connection.lastReceivedAt}ms") +
                            "), recycling"
                    )
                    found += address to connection
                }
            }
            found.forEach { (_, connection) ->
                closeConnection(connection)
                connection.state = ConnectionState.DISCONNECTED
            }
            updateStateFlow()
            found
        }
        dead.forEach { (address, connection) ->
            scheduleReconnect(address, connection, "Connection went silent")
        }
    }

    private fun redial(peers: List<String>) {
        if (peers.isEmpty()) return
        val gate = Semaphore(MAX_PARALLEL_REDIAL)
        peers.forEach { peer ->
            scope.launch {
                gate.withPermit {
                    try {
                        getConnection(peer)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // The *default* network callback fires only for the network Android is
        // actually routing through. A general network callback also fires for
        // every secondary network (mobile data while on WiFi, VPN, ...), and
        // tearing down healthy circuits for those caused repeated drops.
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val handle = network.networkHandle
                val previousHandle = lastKnownNetworkHandle
                lastKnownNetworkHandle = handle
                isNetworkAvailable.set(true)
                // Same default network reported again: nothing changed.
                if (previousHandle == handle) return

                Log.d(TAG, "Default network available (handle=$handle, previous=$previousHandle)")
                // A different default network invalidates onion circuits built on
                // the old one. Rebuild sessions with bounded concurrency instead
                // of dialing every contact at once.
                scope.launch {
                    val peersToReconnect = mutex.withLock {
                        val keys = connections.keys.toList()
                        if (previousHandle != 0L) {
                            for ((_, connection) in connections) {
                                closeConnection(connection)
                                connection.state = ConnectionState.DISCONNECTED
                                connection.reconnectAttempts = 0
                            }
                            updateStateFlow()
                        }
                        keys
                    }
                    redial(peersToReconnect)
                }
            }

            override fun onLost(network: Network) {
                // Ignore a network we already moved away from (handover).
                if (network.networkHandle != lastKnownNetworkHandle) {
                    Log.d(TAG, "Previous network lost during handover — keeping circuits")
                    return
                }
                Log.d(TAG, "Default network lost")
                isNetworkAvailable.set(false)
                // Reset so the next available network always triggers a redial,
                // even if it is the same Network object returning.
                lastKnownNetworkHandle = 0L
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

        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)

        // Check initial state using modern API
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        isNetworkAvailable.set(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        lastKnownNetworkHandle = network?.networkHandle ?: 0L
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
