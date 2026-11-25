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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
        // framing alignment is never lost. Kept at 15s (above KEEPALIVE_INTERVAL_MS)
        // so dead sockets unblock promptly.
        const val READ_TIMEOUT_MS = 15_000
        const val KEEPALIVE_INTERVAL_MS = 10_000L    // Keep an established peer session warm
        const val CIRCUIT_HEALTH_CHECK_MS = 15_000L  // Health/liveness sweep
        // A healthy peer pings every KEEPALIVE_INTERVAL_MS and answers our pings, so
        // an established connection that has *received nothing* for this long is a
        // dead circuit even though the local SOCKS socket still looks open.
        const val DEAD_CONNECTION_MS = 90_000L
        // An existing connection that received frames within this window is considered
        // healthy when a simultaneous dial or incoming socket arrives.
        private const val ACTIVE_CONNECTION_HEALTHY_MS = 45_000L
        private const val SOCKET_BUFFER_BYTES = 65_536
        private const val STREAM_BUFFER_BYTES = 65_536
        private const val HEDGE_STAGGER_MS = 3_200L
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
        private const val BULK_IDLE_TIMEOUT_MS = 180_000L
        private const val BULK_DIAL_COOLDOWN_MS = 45_000L

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
        @Volatile var socket: Socket? = null,
        @Volatile var input: DataInputStream? = null,
        @Volatile var output: DataOutputStream? = null,
        @Volatile var state: ConnectionState = ConnectionState.DISCONNECTED,
        val connectedAt: Long = System.currentTimeMillis(),
        @Volatile var lastActivity: Long = 0L,
        @Volatile var lastReceivedAt: Long = 0L,
        @Volatile var hasReceivedFrame: Boolean = false,
        var reconnectAttempts: Int = 0,
        var lastError: String? = null,
        val isIncoming: Boolean = false,
        val isLanBypass: Boolean = false
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    // Active connections
    private val connections = ConcurrentHashMap<String, PeerConnection>()

    // Pending scheduled reconnect jobs per peer so timers never stack or outlive disconnect()
    private val reconnectJobs = ConcurrentHashMap<String, Job>()

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
    private val hedgeIsolationCounter = java.util.concurrent.atomic.AtomicLong(1L)

    // Secondary Warp-Lane (Lane 1: BULK_MEDIA) independent Tor circuits per peer
    private val bulkConnections = ConcurrentHashMap<String, PeerConnection>()
    private val bulkWriteLocks = ConcurrentHashMap<String, Mutex>()
    private val bulkConnectionLocks = ConcurrentHashMap<String, Mutex>()
    private val bulkDials = ConcurrentHashMap<String, Deferred<PeerConnection?>>()
    private val bulkDialCooldownUntil = ConcurrentHashMap<String, Long>()
    @Volatile
    var bulkHelloFrameProvider: ((String) -> ByteArray?)? = null

    /** Optional local LAN bypass endpoint resolver supplied by PhantomLanBridge. */
    @Volatile
    var lanEndpointResolver: ((String) -> InetSocketAddress?)? = null

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

    private fun isLanSocket(socket: Socket): Boolean {
        val addr = socket.inetAddress ?: return false
        return !addr.isLoopbackAddress && (addr.isSiteLocalAddress || addr.isLinkLocalAddress)
    }

    private suspend fun tryOpenLanSocket(canonicalKey: String): Socket? {
        val lanAddr = runCatching { lanEndpointResolver?.invoke(canonicalKey) }.getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            val sock = Socket()
            try {
                sock.connect(lanAddr, 350)
                configureSocket(sock)
                sock
            } catch (_: Exception) {
                runCatching { sock.close() }
                null
            }
        }
    }

    fun isLanDirectConnection(onionAddress: String): Boolean =
        activeConnection(canonicalizePeerKey(onionAddress))?.isLanBypass == true

    fun getConnectedLanPeers(): List<String> =
        connections.entries
            .filter { activeConnectionFromMap(connections, it.key)?.isLanBypass == true }
            .map { it.key }

    suspend fun upgradeToLanIfAvailable(onionAddress: String): Boolean {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        if (activeConnection(canonicalKey)?.isLanBypass == true) return true
        val lanSock = tryOpenLanSocket(canonicalKey) ?: return false
        return registerOutbound(canonicalKey, lanSock, isLanBypass = true) != null
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
        return awaitDialOrIncoming(canonicalKey, startDial(canonicalKey))
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
        return withTimeoutOrNull(budgetMs) { awaitDialOrIncoming(canonicalKey, dial) }
    }

    private suspend fun awaitDialOrIncoming(
        canonicalKey: String,
        dial: Deferred<PeerConnection?>
    ): PeerConnection? = coroutineScope {
        activeConnection(canonicalKey)?.let { existing ->
            existing.lastActivity = System.currentTimeMillis()
            return@coroutineScope existing
        }

        val winner = CompletableDeferred<PeerConnection?>()

        val incomingJob = launch(start = CoroutineStart.UNDISPATCHED) {
            _connectionEvents
                .filter { it == canonicalKey }
                .mapNotNull { activeConnection(canonicalKey) }
                .first()
                .let { conn ->
                    conn.lastActivity = System.currentTimeMillis()
                    winner.complete(conn)
                }
        }

        activeConnection(canonicalKey)?.let { existing ->
            existing.lastActivity = System.currentTimeMillis()
            incomingJob.cancel()
            return@coroutineScope existing
        }

        val dialObserverJob = launch {
            val result = dial.await()
            val resolved = result ?: activeConnection(canonicalKey)
            winner.complete(resolved)
        }

        try {
            winner.await()
        } finally {
            incomingJob.cancel()
            dialObserverJob.cancel()
        }
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
    private suspend fun writeFrameToConnection(
        connection: PeerConnection,
        writeLock: Mutex,
        data: ByteArray
    ): Boolean = writeLock.withLock {
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

    private suspend fun writeFramesBurstToConnection(
        connection: PeerConnection,
        writeLock: Mutex,
        frames: List<ByteArray>
    ): Boolean = writeLock.withLock {
        withContext(Dispatchers.IO) {
            connection.output?.let { out ->
                for (frame in frames) {
                    out.writeInt(frame.size)
                    out.write(frame)
                }
                out.flush()
                connection.lastActivity = System.currentTimeMillis()
                true
            } ?: false
        }
    }

    suspend fun sendFramesBurstIfConnected(onionAddress: String, frames: List<ByteArray>): Boolean {
        if (frames.isEmpty()) return true
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = activeConnection(canonicalKey) ?: return false
        val writeLock = writeLocks.getOrPut(canonicalKey) { Mutex() }
        return try {
            writeFramesBurstToConnection(connection, writeLock, frames)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Opportunistic burst skipped for $canonicalKey: ${e.message}")
            false
        }
    }

    suspend fun sendFramesBurst(onionAddress: String, frames: List<ByteArray>): Boolean {
        if (frames.isEmpty()) return true
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val writeLock = writeLocks.getOrPut(canonicalKey) { Mutex() }

        var connection = getConnection(canonicalKey) ?: return false
        repeat(2) {
            try {
                if (writeFramesBurstToConnection(connection, writeLock, frames)) return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send burst to $canonicalKey: ${e.message}")
                handleConnectionFailure(canonicalKey, connection, e.message ?: "Burst send failed")
            }
            val next = activeConnection(canonicalKey)
            if (next == null || next === connection) return false
            connection = next
        }
        return false
    }

    /**
     * Write directly onto an already-connected peer circuit without dialing.
     * Used for opportunistic PING/PONG/TYPING frames so offline peers never trigger dials.
     */
    suspend fun sendDataIfConnected(onionAddress: String, data: ByteArray): Boolean {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = activeConnection(canonicalKey) ?: return false
        val writeLock = writeLocks.getOrPut(canonicalKey) { Mutex() }
        return try {
            writeFrameToConnection(connection, writeLock, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Opportunistic send skipped for $canonicalKey: ${e.message}")
            false
        }
    }

    suspend fun sendData(onionAddress: String, data: ByteArray): Boolean {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        // Per-peer write lock prevents interleaved writes (e.g. ACK + queued message)
        val writeLock = writeLocks.getOrPut(canonicalKey) { Mutex() }

        var connection = getConnection(canonicalKey) ?: return false
        repeat(2) {
            try {
                val written = writeFrameToConnection(connection, writeLock, data)
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
                    connection.hasReceivedFrame = true
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

    fun getConnectionConnectedAt(onionAddress: String): Long {
        return activeConnection(canonicalizePeerKey(onionAddress))?.connectedAt ?: 0L
    }

    /**
     * A non-blocking liveness check for receipt delivery. Never start a fresh
     * 60-second onion dial solely to acknowledge a mailbox item.
     */
    fun hasActiveConnection(onionAddress: String): Boolean =
        activeConnection(canonicalizePeerKey(onionAddress)) != null

    /**
     * Recycles an existing connection if it has not received any inbound frames within
     * [silentThresholdMs]. Used by voice call setup and mid-call watchdogs to avoid
     * pouring real-time call signals/audio into a half-open SOCKS socket.
     */
    suspend fun recycleIfSilent(onionAddress: String, silentThresholdMs: Long): Boolean = mutex.withLock {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = connections[canonicalKey] ?: return@withLock false
        val now = System.currentTimeMillis()
        if (now - connection.lastReceivedAt <= silentThresholdMs) {
            return@withLock false
        }
        Log.w(TAG, "♻️ Recycling silent connection to $canonicalKey (idle ${now - connection.lastReceivedAt}ms)")
        connections.remove(canonicalKey)
        closeConnection(connection)
        updateStateFlow()
        true
    }

    /**
     * Disconnect from a peer
     */
    suspend fun disconnect(onionAddress: String) = mutex.withLock {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        reconnectJobs.remove(canonicalKey)?.cancel()
        dials.remove(canonicalKey)?.cancel()
        bulkDials.remove(canonicalKey)?.cancel()
        bulkDialCooldownUntil.remove(canonicalKey)
        val connection = connections.remove(canonicalKey)
        connection?.let { closeConnection(it) }
        val bulkConnection = bulkConnections.remove(canonicalKey)
        bulkConnection?.let { closeConnection(it) }
        updateStateFlow()
        Log.d(TAG, "Disconnected from $canonicalKey")
    }

    /**
     * Disconnect all peers
     */
    suspend fun disconnectAll() = mutex.withLock {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        dials.values.forEach { it.cancel() }
        dials.clear()
        bulkDials.values.forEach { it.cancel() }
        bulkDials.clear()
        bulkDialCooldownUntil.clear()
        for ((address, connection) in connections) {
            try {
                closeConnection(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Error closing connection to $address: ${e.message}")
            }
        }
        connections.clear()
        for ((address, bulkConnection) in bulkConnections) {
            try {
                closeConnection(bulkConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error closing bulk connection to $address: ${e.message}")
            }
        }
        bulkConnections.clear()
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
        preferIncoming: Boolean? = null
    ): Boolean {
        val canonicalKey = canonicalizePeerKey(peerIdentifier)
        val connectionLock = connectionLocks.getOrPut(canonicalKey) { Mutex() }
        return connectionLock.withLock {
            mutex.withLock {
                try {
                    val now = System.currentTimeMillis()
                    val incomingIsLan = isLanSocket(socket)
                    val resolvedPreferIncoming = preferIncoming ?: preferIncoming(canonicalKey)
                    val active = activeConnection(canonicalKey)
                    if (active != null) {
                        // Keep our own outbound circuit only if a simultaneous dial race
                        // is happening right now (received traffic, or fresh outbound
                        // handshake within 20s) and the deterministic tie-break says
                        // outbound wins. Always allow a 1.5ms LAN socket to upgrade a slow Tor circuit.
                        val activeHealthy = active.hasReceivedFrame &&
                            (now - active.lastReceivedAt) <= ACTIVE_CONNECTION_HEALTHY_MS
                        val freshOutboundHandshake = !active.hasReceivedFrame &&
                            (now - active.connectedAt) <= 20_000L
                        val keepActive = when {
                            incomingIsLan && !active.isLanBypass -> false
                            !incomingIsLan && active.isLanBypass && (activeHealthy || freshOutboundHandshake) -> true
                            else -> !active.isIncoming &&
                                (activeHealthy || freshOutboundHandshake) &&
                                !resolvedPreferIncoming
                        }
                        if (keepActive) {
                            dials.remove(canonicalKey)?.cancel()
                            try { socket.close() } catch (_: Exception) {}
                            return@withLock false
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
                        connectedAt = now,
                        lastActivity = now,
                        lastReceivedAt = now,
                        hasReceivedFrame = true,
                        reconnectAttempts = 0,
                        isIncoming = true,
                        isLanBypass = incomingIsLan
                    )

                    connections[canonicalKey] = connection
                    reconnectJobs.remove(canonicalKey)?.cancel()
                    dials.remove(canonicalKey)?.cancel()
                    updateStateFlow()
                    _connectionEvents.tryEmit(canonicalKey)
                    Log.i(TAG, "📥 Incoming connection registered for $canonicalKey")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register incoming connection: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}
                    false
                }
            }
        }
    }

    /**
     * Make-before-break reconnect: dials a fresh circuit in the background while keeping
     * the existing connection usable until the replacement socket is ready. If the dial
     * fails, any still-open existing connection is left untouched.
     */
    suspend fun warmReconnect(onionAddress: String): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        dials[canonicalKey]?.takeIf { it.isActive }?.let { inFlight ->
            return withTimeoutOrNull(20_000L) { inFlight.await() }
        }
        val existingActive = activeConnection(canonicalKey)
        val previousConnId = existingActive?.connectionId
        val previousReceivedAt = existingActive?.lastReceivedAt ?: 0L
        val warmDial = synchronized(dials) {
            dials[canonicalKey]?.takeIf { it.isActive }?.let { return@synchronized it }
            val deferred = scope.async {
                val socket = try {
                    connectSocketWithRetry(canonicalKey, maxAttempts = 2)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Warm reconnect dial failed for $canonicalKey: ${e.message}")
                    null
                } ?: return@async null

                val connectionLock = connectionLocks.getOrPut(canonicalKey) { Mutex() }
                connectionLock.withLock {
                    mutex.withLock {
                        val now = System.currentTimeMillis()
                        val current = activeConnection(canonicalKey)
                        if (current != null &&
                            current.socket?.isClosed == false &&
                            current.socket?.isConnected == true &&
                            current.hasReceivedFrame &&
                            (now - current.lastReceivedAt) <= 15_000L &&
                            (current.connectionId != previousConnId || current.lastReceivedAt > previousReceivedAt)
                        ) {
                            try { socket.close() } catch (_: Exception) {}
                            return@async current
                        }
                        current?.let { closeConnection(it) }
                        val established = createOutboundConnection(canonicalKey, socket, now)
                        connections[canonicalKey] = established
                        updateStateFlow()
                        _connectionEvents.tryEmit(canonicalKey)
                        Log.i(TAG, "♻️ Warm-reconnected circuit to $canonicalKey")
                        established
                    }
                }
            }
            dials[canonicalKey] = deferred
            deferred.invokeOnCompletion { dials.remove(canonicalKey, deferred) }
            deferred
        }
        return withTimeoutOrNull(30_000L) { warmDial.await() }
    }

    // ========== Private Methods ==========

    private suspend fun openSocksSocket(
        canonicalKey: String,
        torProxy: InetSocketAddress,
        onSocketCreated: (Socket) -> Unit = {}
    ): Socket = withContext(Dispatchers.IO) {
        val parts = canonicalKey.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 18888
        val created = Socket(Proxy(Proxy.Type.SOCKS, torProxy))
        onSocketCreated(created)
        created.connect(InetSocketAddress.createUnresolved(host, port), CONNECT_TIMEOUT_MS)
        configureSocket(created)
        created
    }

    private suspend fun connectSocketWithRetry(
        canonicalKey: String,
        maxAttempts: Int = 2
    ): Socket? {
        val torProxy = TorManager.currentBestSocksAddress(context) ?: return null
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            var pendingSocket: Socket? = null
            try {
                return openSocksSocket(canonicalKey, torProxy) { pendingSocket = it }
            } catch (e: CancellationException) {
                try { pendingSocket?.close() } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                try { pendingSocket?.close() } catch (_: Exception) {}
                lastError = e
                if (attempt + 1 < maxAttempts) {
                    delay(500L)
                }
            }
        }
        lastError?.let { throw it }
        return null
    }

    /**
     * Dials a peer and registers the resulting socket. Runs in this manager's
     * scope (see [startDial]); the blocking connect holds no lock, so incoming
     * connections from the same peer are never delayed by an outbound dial.
     */
    private suspend fun openIsolatedSocksSocket(
        canonicalKey: String,
        torProxy: InetSocketAddress,
        isolationToken: String?,
        onSocketCreated: (Socket) -> Unit = {}
    ): Socket {
        if (isolationToken.isNullOrBlank()) {
            return openSocksSocket(canonicalKey, torProxy, onSocketCreated)
        }
        return withContext(Dispatchers.IO) {
            val parts = canonicalKey.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 18888
            val sock = Socket()
            onSocketCreated(sock)
            try {
                sock.connect(torProxy, 5_000)
                sock.soTimeout = CONNECT_TIMEOUT_MS
                sock.tcpNoDelay = true
                val ins = sock.getInputStream()
                val outs = sock.getOutputStream()

                // SOCKS5 greeting supporting No-Auth (0x00) and Username/Password (0x02) for IsolateSOCKSAuth
                outs.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
                outs.flush()
                val greetingResp = readExact(ins, 2)
                if (greetingResp[0] != 0x05.toByte()) {
                    throw IOException("Invalid SOCKS5 version: ${greetingResp[0]}")
                }
                when (greetingResp[1].toInt() and 0xFF) {
                    0x02 -> {
                        val user = "s-iso".toByteArray(Charsets.US_ASCII)
                        val pass = isolationToken.take(64).toByteArray(Charsets.US_ASCII)
                        val authPkt = ByteArray(3 + user.size + pass.size)
                        authPkt[0] = 0x01
                        authPkt[1] = user.size.toByte()
                        System.arraycopy(user, 0, authPkt, 2, user.size)
                        authPkt[2 + user.size] = pass.size.toByte()
                        System.arraycopy(pass, 0, authPkt, 3 + user.size, pass.size)
                        outs.write(authPkt)
                        outs.flush()
                        val authResp = readExact(ins, 2)
                        if (authResp[1] != 0x00.toByte()) {
                            throw IOException("SOCKS5 RFC1929 auth rejected: ${authResp[1]}")
                        }
                    }
                    0x00 -> Unit
                    else -> throw IOException("SOCKS5 method rejected: ${greetingResp[1]}")
                }

                // SOCKS5 CONNECT with DOMAINNAME (0x03) so Tor resolves the .onion privately
                val hostBytes = host.toByteArray(Charsets.US_ASCII)
                require(hostBytes.size in 1..255) { "Invalid host length for SOCKS5" }
                val req = ByteArray(7 + hostBytes.size)
                req[0] = 0x05
                req[1] = 0x01 // CONNECT
                req[2] = 0x00
                req[3] = 0x03 // DOMAINNAME
                req[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
                req[5 + hostBytes.size] = ((port ushr 8) and 0xFF).toByte()
                req[6 + hostBytes.size] = (port and 0xFF).toByte()
                outs.write(req)
                outs.flush()

                val replyHdr = readExact(ins, 4)
                val rVer = replyHdr[0].toInt() and 0xFF
                val rep = replyHdr[1].toInt() and 0xFF
                val atyp = replyHdr[3].toInt() and 0xFF
                if (rVer != 0x05 || rep != 0x00) {
                    throw IOException("Tor SOCKS5 connect failed (rep=$rep)")
                }
                when (atyp) {
                    0x01 -> readExact(ins, 6)
                    0x04 -> readExact(ins, 18)
                    0x03 -> {
                        val dLen = ins.read()
                        if (dLen < 0) throw EOFException("Truncated SOCKS5 domain reply")
                        readExact(ins, dLen + 2)
                    }
                    else -> throw IOException("Unknown SOCKS5 ATYP: $atyp")
                }

                configureSocket(sock)
                sock
            } catch (e: Exception) {
                runCatching { sock.close() }
                throw e
            }
        }
    }

    private fun readExact(ins: java.io.InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = ins.read(buf, offset, count - offset)
            if (n < 0) throw EOFException("Unexpected EOF during SOCKS5 handshake")
            offset += n
        }
        return buf
    }

    /**
     * Conflux Iso-Hedge: race two stream-isolated Tor SOCKS5 rendezvous circuits after 3.2s
     * so C-Tor's IsolateSOCKSAuth forces an independent intro + rendezvous path instead of
     * inheriting a stalled guard/intro circuit for 60s.
     */
    private suspend fun openSocksSocketHedged(
        canonicalKey: String,
        torProxy: InetSocketAddress
    ): Socket = coroutineScope {
        val winner = CompletableDeferred<Socket>()
        val pending1 = AtomicReference<Socket?>(null)
        val pending2 = AtomicReference<Socket?>(null)
        val errors = ConcurrentHashMap<Int, Throwable>()
        val finishedCount = AtomicInteger(0)
        val startSecondDial = CompletableDeferred<Unit>()

        suspend fun runDialAttempt(slot: Int, isolationToken: String, pendingRef: AtomicReference<Socket?>) {
            try {
                val sock = openIsolatedSocksSocket(canonicalKey, torProxy, isolationToken) { pendingRef.set(it) }
                if (!winner.complete(sock)) {
                    runCatching { sock.close() }
                }
            } catch (ce: CancellationException) {
                runCatching { pendingRef.get()?.close() }
                throw ce
            } catch (e: Throwable) {
                runCatching { pendingRef.get()?.close() }
                errors[slot] = e
                if (slot == 1) startSecondDial.complete(Unit)
                if (finishedCount.incrementAndGet() >= 2) {
                    winner.completeExceptionally(errors[2] ?: errors[1] ?: e)
                }
            }
        }

        val job1 = launch { runDialAttempt(1, "pri-${canonicalKey.take(20)}", pending1) }
        val job2 = launch {
            withTimeoutOrNull(HEDGE_STAGGER_MS) { startSecondDial.await() }
            if (!isActive || winner.isCompleted) {
                if (finishedCount.incrementAndGet() >= 2 && !winner.isCompleted) {
                    winner.completeExceptionally(errors[1] ?: IOException("Hedged SOCKS dial cancelled"))
                }
                return@launch
            }
            val hdgToken = "hdg-${hedgeIsolationCounter.incrementAndGet()}"
            runDialAttempt(2, hdgToken, pending2)
        }

        try {
            val chosen = winner.await()
            job1.cancel()
            job2.cancel()
            pending1.get()?.takeIf { it !== chosen }?.let { runCatching { it.close() } }
            pending2.get()?.takeIf { it !== chosen }?.let { runCatching { it.close() } }
            chosen
        } catch (e: Throwable) {
            job1.cancel()
            job2.cancel()
            runCatching { pending1.get()?.close() }
            runCatching { pending2.get()?.close() }
            throw e
        }
    }

    private suspend fun dialAndRegister(canonicalKey: String): PeerConnection? {
        // An incoming connection may have arrived while this dial was queued.
        val (existingConnected, placeholder) = mutex.withLock {
            val active = activeConnection(canonicalKey)
            if (active != null) {
                active to null
            } else {
                val p = connections.getOrPut(canonicalKey) { PeerConnection(canonicalKey) }
                if (p.state == ConnectionState.CONNECTED &&
                    p.socket?.isConnected == true &&
                    p.socket?.isClosed == false
                ) {
                    p to null
                } else {
                    p.state = ConnectionState.CONNECTING
                    updateStateFlow()
                    null to p
                }
            }
        }
        if (existingConnected != null) return existingConnected
        placeholder!!

        // Phantom-LAN: attempt direct encrypted local Wi-Fi bypass BEFORE Tor SOCKS5
        tryOpenLanSocket(canonicalKey)?.let { lanSocket ->
            return registerOutbound(canonicalKey, lanSocket, isLanBypass = true)
        }

        val torProxy = TorManager.currentBestSocksAddress(context)
        if (torProxy == null) {
            Log.e(TAG, "Tor proxy not available")
            placeholder.lastError = "Tor proxy not available"
            scheduleReconnect(canonicalKey, placeholder, "Tor proxy not available")
            return null
        }

        return try {
            val socket = openSocksSocketHedged(canonicalKey, torProxy)
            registerOutbound(canonicalKey, socket, isLanBypass = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val existing = activeConnection(canonicalKey)
            if (existing != null) {
                Log.d(TAG, "Outbound dial to $canonicalKey failed after incoming circuit established (${e.message}); keeping active circuit")
                existing
            } else {
                Log.e(TAG, "Failed to connect to $canonicalKey: ${e.message}")
                placeholder.lastError = e.message
                scheduleReconnect(canonicalKey, placeholder, e.message ?: "Connect failed")
                null
            }
        }
    }

    private fun createOutboundConnection(
        canonicalKey: String,
        socket: Socket,
        now: Long = System.currentTimeMillis(),
        isLanBypass: Boolean = false
    ): PeerConnection = PeerConnection(
        onionAddress = canonicalKey,
        socket = socket,
        input = DataInputStream(BufferedInputStream(socket.inputStream, STREAM_BUFFER_BYTES)),
        output = DataOutputStream(BufferedOutputStream(socket.outputStream, STREAM_BUFFER_BYTES)),
        state = ConnectionState.CONNECTED,
        connectedAt = now,
        lastActivity = now,
        lastReceivedAt = now,
        hasReceivedFrame = false,
        reconnectAttempts = 0,
        isIncoming = false,
        isLanBypass = isLanBypass
    )

    private suspend fun registerOutbound(
        canonicalKey: String,
        socket: Socket,
        isLanBypass: Boolean = false
    ): PeerConnection? {
        val connectionLock = connectionLocks.getOrPut(canonicalKey) { Mutex() }
        return connectionLock.withLock {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val active = activeConnection(canonicalKey)
                val preferIncoming = preferIncoming(canonicalKey)
                // Symmetric tie-break matching addIncomingConnection:
                // only preserve `active` if it is healthy (has received a frame recently)
                // AND either `active` is our own outbound or `preferIncoming` picks incoming.
                // A direct Phantom-LAN socket always upgrades a multi-hop Tor circuit.
                val activeHealthy = active != null &&
                    active.socket?.isClosed == false &&
                    active.socket?.isConnected == true &&
                    active.hasReceivedFrame &&
                    (now - active.lastReceivedAt) <= ACTIVE_CONNECTION_HEALTHY_MS
                val keepActive = when {
                    active == null -> false
                    isLanBypass && !active.isLanBypass -> false
                    !isLanBypass && active.isLanBypass && activeHealthy -> true
                    else -> activeHealthy && (!active.isIncoming || preferIncoming)
                }
                if (keepActive && active != null) {
                    try { socket.close() } catch (_: Exception) {}
                    _connectionStates.value = _connectionStates.value + (canonicalKey to ConnectionState.CONNECTED)
                    return@withLock active
                }
                active?.let { closeConnection(it) }
                val established = createOutboundConnection(canonicalKey, socket, now, isLanBypass = isLanBypass)
                connections[canonicalKey] = established
                reconnectJobs.remove(canonicalKey)?.cancel()
                updateStateFlow()
                _connectionEvents.tryEmit(canonicalKey)
                Log.i(TAG, if (isLanBypass) "⚡ Connected Phantom-LAN to $canonicalKey" else "✅ Connected to $canonicalKey")
                established
            }
        }
    }

    private fun activeConnectionFromMap(
        map: ConcurrentHashMap<String, PeerConnection>,
        onionAddress: String
    ): PeerConnection? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val existing = map[canonicalKey] ?: return null
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

    private fun activeConnection(onionAddress: String): PeerConnection? =
        activeConnectionFromMap(connections, onionAddress)

    private fun activeBulkConnection(onionAddress: String): PeerConnection? =
        activeConnectionFromMap(bulkConnections, onionAddress)

    fun hasActiveBulkConnection(onionAddress: String): Boolean =
        activeBulkConnection(canonicalizePeerKey(onionAddress)) != null

    fun getActiveBulkConnectionId(onionAddress: String): String? =
        activeBulkConnection(canonicalizePeerKey(onionAddress))?.connectionId

    fun getConnectedBulkPeers(): List<String> =
        bulkConnections.entries
            .filter { it.value.state == ConnectionState.CONNECTED }
            .map { it.key }

    fun ensureBulkConnectionAsync(onionAddress: String) {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        if (canonicalKey.isBlank() || canonicalKey.startsWith("pubkey:") || canonicalKey.startsWith("rendezvous:")) {
            return
        }
        if (activeBulkConnection(canonicalKey) != null) return
        val now = System.currentTimeMillis()
        if (now < (bulkDialCooldownUntil[canonicalKey] ?: 0L)) return
        bulkDials[canonicalKey]?.takeIf { it.isActive }?.let { return }
        synchronized(bulkDials) {
            bulkDials[canonicalKey]?.takeIf { it.isActive }?.let { return }
            if (activeBulkConnection(canonicalKey) != null) return
            val dial = scope.async { dialAndRegisterBulk(canonicalKey) }
            bulkDials[canonicalKey] = dial
            dial.invokeOnCompletion { bulkDials.remove(canonicalKey, dial) }
        }
    }

    private suspend fun dialAndRegisterBulk(canonicalKey: String): PeerConnection? {
        activeBulkConnection(canonicalKey)?.let { return it }
        val helloBytes = runCatching { bulkHelloFrameProvider?.invoke(canonicalKey) }.getOrNull()
        if (helloBytes == null || helloBytes.isEmpty()) {
            return null
        }
        var sock: Socket? = null
        return try {
            val lanSock = tryOpenLanSocket(canonicalKey)
            val useLan = lanSock != null
            sock = lanSock ?: run {
                val torProxy = TorManager.currentBestSocksAddress(context) ?: return null
                openIsolatedSocksSocket(canonicalKey, torProxy, "bulk-${canonicalKey.take(18)}")
            }
            val now = System.currentTimeMillis()
            val established = createOutboundConnection(canonicalKey, sock, now, isLanBypass = useLan)
            withContext(Dispatchers.IO) {
                val out = established.output ?: throw IOException("Missing bulk output stream")
                out.writeInt(helloBytes.size)
                out.write(helloBytes)
                out.flush()
            }
            val connectionLock = bulkConnectionLocks.getOrPut(canonicalKey) { Mutex() }
            connectionLock.withLock {
                mutex.withLock {
                    val existing = activeBulkConnection(canonicalKey)
                    val existingHealthy = existing != null &&
                        existing.hasReceivedFrame &&
                        (now - existing.lastReceivedAt) <= ACTIVE_CONNECTION_HEALTHY_MS
                    val keepExisting = when {
                        existing == null -> false
                        useLan && !existing.isLanBypass -> false
                        !useLan && existing.isLanBypass && existingHealthy -> true
                        else -> existing.isIncoming && existingHealthy && preferIncoming(canonicalKey)
                    }
                    if (keepExisting && existing != null) {
                        closeConnection(established)
                        return@withLock existing
                    }
                    existing?.let { closeConnection(it) }
                    bulkConnections[canonicalKey] = established
                    bulkDialCooldownUntil.remove(canonicalKey)
                    _connectionEvents.tryEmit(canonicalKey)
                    Log.i(TAG, if (useLan) "⚡ Connected Phantom-LAN BULK for $canonicalKey" else "⚡ Connected Warp-Lane BULK circuit for $canonicalKey")
                    established
                }
            }
        } catch (ce: CancellationException) {
            sock?.let { runCatching { it.close() } }
            throw ce
        } catch (e: Exception) {
            sock?.let { runCatching { it.close() } }
            bulkDialCooldownUntil[canonicalKey] = System.currentTimeMillis() + BULK_DIAL_COOLDOWN_MS
            Log.d(TAG, "Bulk circuit dial failed for $canonicalKey: ${e.message}")
            null
        }
    }

    suspend fun addIncomingBulkConnection(peerIdentifier: String, socket: Socket): Boolean {
        val canonicalKey = canonicalizePeerKey(peerIdentifier)
        val connectionLock = bulkConnectionLocks.getOrPut(canonicalKey) { Mutex() }
        return connectionLock.withLock {
            mutex.withLock {
                try {
                    val now = System.currentTimeMillis()
                    val incomingIsLan = isLanSocket(socket)
                    val active = activeBulkConnection(canonicalKey)
                    if (active != null) {
                        val activeHealthy = active.hasReceivedFrame &&
                            (now - active.lastReceivedAt) <= ACTIVE_CONNECTION_HEALTHY_MS
                        val freshOutboundHandshake = !active.hasReceivedFrame &&
                            (now - active.connectedAt) <= 20_000L
                        val keepActive = when {
                            incomingIsLan && !active.isLanBypass -> false
                            !incomingIsLan && active.isLanBypass && (activeHealthy || freshOutboundHandshake) -> true
                            else -> !active.isIncoming &&
                                (activeHealthy || freshOutboundHandshake) &&
                                !preferIncoming(canonicalKey)
                        }
                        if (keepActive) {
                            bulkDials.remove(canonicalKey)?.cancel()
                            try { socket.close() } catch (_: Exception) {}
                            return@withLock false
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
                        connectedAt = now,
                        lastActivity = now,
                        lastReceivedAt = now,
                        hasReceivedFrame = true,
                        reconnectAttempts = 0,
                        isIncoming = true,
                        isLanBypass = incomingIsLan
                    )
                    bulkConnections[canonicalKey] = connection
                    bulkDials.remove(canonicalKey)?.cancel()
                    bulkDialCooldownUntil.remove(canonicalKey)
                    _connectionEvents.tryEmit(canonicalKey)
                    Log.i(TAG, "📥 Incoming Warp-Lane BULK connection registered for $canonicalKey")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register incoming bulk connection: ${e.message}")
                    try { socket.close() } catch (_: Exception) {}
                    false
                }
            }
        }
    }

    suspend fun receiveBulkData(onionAddress: String, expectedConnectionId: String? = null): ByteArray? {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = bulkConnections[canonicalKey] ?: return null
        if (expectedConnectionId != null && connection.connectionId != expectedConnectionId) return null

        return try {
            withContext(Dispatchers.IO) {
                val input = connection.input ?: return@withContext null
                val data = readFrame(input)
                if (data != null) {
                    val now = System.currentTimeMillis()
                    connection.lastActivity = now
                    connection.lastReceivedAt = now
                    connection.hasReceivedFrame = true
                }
                data
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Bulk receive closed for $canonicalKey: ${e.message}")
            mutex.withLock {
                if (bulkConnections[canonicalKey]?.connectionId == connection.connectionId) {
                    bulkConnections.remove(canonicalKey)
                    closeConnection(connection)
                }
            }
            null
        }
    }

    suspend fun sendBulkDataIfConnected(onionAddress: String, data: ByteArray): Boolean {
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val connection = activeBulkConnection(canonicalKey) ?: return false
        val writeLock = bulkWriteLocks.getOrPut(canonicalKey) { Mutex() }
        return try {
            writeFrameToConnection(connection, writeLock, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Bulk single send failed for $canonicalKey: ${e.message}")
            mutex.withLock {
                if (bulkConnections[canonicalKey]?.connectionId == connection.connectionId) {
                    bulkConnections.remove(canonicalKey)
                    closeConnection(connection)
                }
            }
            false
        }
    }

    suspend fun sendBulkFramesBurst(
        onionAddress: String,
        frames: List<ByteArray>,
        allowPrimaryFallback: Boolean = true
    ): Boolean {
        if (frames.isEmpty()) return true
        val canonicalKey = canonicalizePeerKey(onionAddress)
        val bulk = activeBulkConnection(canonicalKey)
        if (bulk != null) {
            val writeLock = bulkWriteLocks.getOrPut(canonicalKey) { Mutex() }
            try {
                if (writeFramesBurstToConnection(bulk, writeLock, frames)) return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Bulk burst failed for $canonicalKey, failing over: ${e.message}")
                mutex.withLock {
                    if (bulkConnections[canonicalKey]?.connectionId == bulk.connectionId) {
                        bulkConnections.remove(canonicalKey)
                        closeConnection(bulk)
                    }
                }
            }
        }
        ensureBulkConnectionAsync(canonicalKey)
        return if (allowPrimaryFallback) sendFramesBurst(canonicalKey, frames) else false
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
            reconnectJobs.remove(canonicalKey)?.cancel()
            connection.state = ConnectionState.FAILED
            updateStateFlow()
            Log.w(TAG, "Connection to $canonicalKey reached $MAX_RECONNECT_ATTEMPTS reconnect failures; waiting for network/send wake-up")
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
        reconnectJobs.remove(canonicalKey)?.cancel()
        reconnectJobs[canonicalKey] = scope.launch {
            delay(delay)
            reconnectJobs.remove(canonicalKey)
            if (isNetworkAvailable.get() || lanEndpointResolver?.invoke(canonicalKey) != null) {
                getConnection(canonicalKey)
            }
        }
    }

    private fun detachConnectionHandles(connection: PeerConnection): Triple<Socket?, DataInputStream?, DataOutputStream?> {
        val sock = connection.socket
        val inp = connection.input
        val out = connection.output
        connection.socket = null
        connection.input = null
        connection.output = null
        return Triple(sock, inp, out)
    }

    private fun closeDetachedHandles(handles: Triple<Socket?, DataInputStream?, DataOutputStream?>) {
        val (sock, inp, out) = handles
        // Close socket FIRST so blocked reads/writes unblock immediately instead of stalling on stream locks.
        try { sock?.close() } catch (_: Exception) {}
        try { inp?.close() } catch (_: Exception) {}
        try { out?.close() } catch (_: Exception) {}
    }

    private fun closeConnection(connection: PeerConnection) {
        closeDetachedHandles(detachConnectionHandles(connection))
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
            val deadBulk = mutableListOf<Pair<String, PeerConnection>>()
            for ((address, bulkConn) in bulkConnections) {
                val socket = bulkConn.socket
                val socketGone = socket == null || socket.isClosed || !socket.isConnected ||
                    socket.isInputShutdown || socket.isOutputShutdown
                val idleMs = now - maxOf(bulkConn.lastActivity, bulkConn.lastReceivedAt)
                if (socketGone || idleMs > BULK_IDLE_TIMEOUT_MS) {
                    deadBulk += address to bulkConn
                }
            }
            deadBulk.forEach { (address, bulkConn) ->
                bulkConnections.remove(address, bulkConn)
                closeConnection(bulkConn)
                bulkConn.state = ConnectionState.DISCONNECTED
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
                    val handlesToClose = mutableListOf<Triple<Socket?, DataInputStream?, DataOutputStream?>>()
                    val peersToReconnect = mutex.withLock {
                        val keys = connections.keys.toList()
                        if (previousHandle != 0L) {
                            for ((_, connection) in connections) {
                                handlesToClose.add(detachConnectionHandles(connection))
                                connection.state = ConnectionState.DISCONNECTED
                                connection.reconnectAttempts = 0
                            }
                            for ((_, bulkConn) in bulkConnections) {
                                handlesToClose.add(detachConnectionHandles(bulkConn))
                                bulkConn.state = ConnectionState.DISCONNECTED
                            }
                            bulkConnections.clear()
                            bulkDialCooldownUntil.clear()
                            updateStateFlow()
                        }
                        keys
                    }
                    handlesToClose.forEach { closeDetachedHandles(it) }
                    if (previousHandle != 0L && peersToReconnect.isNotEmpty()) {
                        // Allow TorDaemonManager handover reset to bounce DisableNetwork cleanly before redialing.
                        delay(450L)
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
                    val handlesToClose = mutableListOf<Triple<Socket?, DataInputStream?, DataOutputStream?>>()
                    mutex.withLock {
                        for ((_, connection) in connections) {
                            handlesToClose.add(detachConnectionHandles(connection))
                            connection.state = ConnectionState.DISCONNECTED
                        }
                        for ((_, bulkConn) in bulkConnections) {
                            handlesToClose.add(detachConnectionHandles(bulkConn))
                            bulkConn.state = ConnectionState.DISCONNECTED
                        }
                        bulkConnections.clear()
                        updateStateFlow()
                    }
                    handlesToClose.forEach { closeDetachedHandles(it) }
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
