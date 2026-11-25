@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TorHiddenServiceManager - Production-ready Tor Hidden Service implementation
 * 
 * This manager handles:
 * - Starting the Tor daemon via TorDaemonManager
 * - Publishing hidden services (making us reachable via .onion address)
 * - Listening for incoming connections on the hidden service port
 * - Integration with existing Arti SOCKS proxy for outbound
 * 
 * Architecture:
 * - TorDaemonManager wraps Briar's onionwrapper for hidden service hosting (inbound)
 * - Arti (Rust) handles SOCKS proxy for outbound connections (fast, modern)
 * 
 * Based on Briar/Cwtch approach: each device is its own .onion address
 */
class TorHiddenServiceManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TorHiddenService"
        
        // Hidden service configuration
        const val HIDDEN_SERVICE_PORT = 18888
        const val HIDDEN_SERVICE_DIR = "hidden_service"
        
        // Legacy - kept for compatibility but now using TorDaemonManager ports
        const val CONTROL_PORT = 9051
        const val SOCKS_PORT = 9050
        
        @Volatile
        private var instance: TorHiddenServiceManager? = null
        
        fun getInstance(context: Context): TorHiddenServiceManager {
            return instance ?: synchronized(this) {
                instance ?: TorHiddenServiceManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // State
    enum class HiddenServiceState {
        STOPPED,
        STARTING,
        WAITING_FOR_TOR,
        RUNNING,
        ERROR
    }
    
    data class HiddenServiceStatus(
        val state: HiddenServiceState = HiddenServiceState.STOPPED,
        val onionAddress: String = "",
        val torConnected: Boolean = false,
        val error: String? = null
    )
    
    private val _status = MutableStateFlow(HiddenServiceStatus())
    val statusFlow: StateFlow<HiddenServiceStatus> = _status.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var daemonStatusJob: Job? = null
    
    // TorDaemonManager handles the actual Tor daemon
    private var torDaemonManager: TorDaemonManager? = null
    
    // Connection listener
    interface ConnectionListener {
        fun onIncomingConnection(clientSocket: java.net.Socket)
    }
    
    private var connectionListener: ConnectionListener? = null

    init {
        val savedOnion = runCatching {
            com.seamless.android.identity.SecureIdentityStateManager(context)
                .getSecureValue("tor_hs_onion_address")
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        if (savedOnion != null) {
            val endpoint = toOnionEndpoint(savedOnion)
            if (endpoint.isNotBlank()) {
                _status.value = HiddenServiceStatus(
                    state = HiddenServiceState.WAITING_FOR_TOR,
                    onionAddress = endpoint,
                    torConnected = false
                )
            }
        }
    }
    
    fun setConnectionListener(listener: ConnectionListener?) {
        connectionListener = listener
    }
    
    /**
     * Start the hidden service
     * 
     * This now properly starts the Tor daemon and publishes a real hidden service
     * that other devices can connect to.
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            Log.d(TAG, "Hidden service already running")
            return@withContext true
        }
        
        try {
            _status.value = _status.value.copy(
                state = HiddenServiceState.STARTING,
                error = null
            )
            isRunning.set(true)
            
            // Start the local server socket first to accept incoming connections
            startServer()
            
            // Get the TorDaemonManager instance
            val application = context.applicationContext as Application
            val daemonManager = TorDaemonManager.getInstance(application)
            torDaemonManager = daemonManager
            
            // Start observing daemon status
            daemonStatusJob = scope.launch {
                daemonManager.statusFlow.collect { daemonStatus ->
                    val endpoint = daemonStatus.onionAddress
                        ?.let(::toOnionEndpoint)
                        .orEmpty()
                        .ifBlank { _status.value.onionAddress }
                    when (daemonStatus.state) {
                        TorDaemonManager.DaemonState.CONNECTED -> {
                            // Tor is connected - update our status with onion address
                            if (endpoint.isNotBlank()) {
                                _status.value = HiddenServiceStatus(
                                    state = HiddenServiceState.RUNNING,
                                    onionAddress = endpoint,
                                    torConnected = true
                                )
                                Log.i(TAG, "🧅 Hidden service running: $endpoint")
                            } else {
                                _status.value = _status.value.copy(
                                    state = HiddenServiceState.WAITING_FOR_TOR,
                                    torConnected = true,
                                    error = daemonStatus.error
                                )
                            }
                        }
                        TorDaemonManager.DaemonState.BOOTSTRAPPING,
                        TorDaemonManager.DaemonState.STARTING -> {
                            _status.value = _status.value.copy(
                                state = HiddenServiceState.WAITING_FOR_TOR,
                                onionAddress = endpoint,
                                torConnected = false
                            )
                        }
                        TorDaemonManager.DaemonState.ERROR -> {
                            _status.value = _status.value.copy(
                                state = HiddenServiceState.ERROR,
                                error = daemonStatus.error ?: "Tor daemon error"
                            )
                        }
                        else -> {
                            if (endpoint.isNotBlank() && _status.value.onionAddress.isBlank()) {
                                _status.value = _status.value.copy(onionAddress = endpoint)
                            }
                        }
                    }
                }
            }
            
            // Start the Tor daemon
            Log.i(TAG, "🚀 Starting Tor daemon for hidden service...")
            _status.value = _status.value.copy(state = HiddenServiceState.WAITING_FOR_TOR)
            
            val started = daemonManager.start()
            if (!started) {
                val daemonError = daemonManager.statusFlow.value.error ?: "Failed to start Tor daemon"
                Log.e(TAG, "Failed to start Tor daemon: $daemonError")
                isRunning.set(false)
                daemonStatusJob?.cancel()
                daemonStatusJob = null
                serverJob?.cancel()
                try {
                    serverSocket?.close()
                } catch (_: Exception) {
                }
                _status.value = HiddenServiceStatus(
                    state = HiddenServiceState.ERROR,
                    error = daemonError
                )
                return@withContext false
            }

            Log.i(TAG, "✅ Hidden service startup initiated - waiting for Tor connection...")
            true
            
        } catch (e: Exception) {
            isRunning.set(false)
            serverJob?.cancel()
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
            Log.e(TAG, "Failed to start hidden service: ${e.message}", e)
            _status.value = HiddenServiceStatus(
                state = HiddenServiceState.ERROR,
                error = e.message
            )
            false
        }
    }
    
    /**
     * Stop the hidden service
     */
    fun stop() {
        if (!isRunning.get()) return
        
        isRunning.set(false)
        
        // Cancel status observation
        daemonStatusJob?.cancel()
        daemonStatusJob = null
        
        // Cancel server job
        serverJob?.cancel()
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        
        // Stop Tor daemon (capture reference BEFORE clearing torDaemonManager)
        val daemonToStop = torDaemonManager
        serverSocket = null
        torDaemonManager = null
        scope.launch {
            daemonToStop?.stop()
        }
        _status.value = HiddenServiceStatus(state = HiddenServiceState.STOPPED)
        Log.i(TAG, "Hidden service stopped")
    }
    
    /**
     * Get the current onion address
     */
    fun getOnionAddress(): String {
        return _status.value.onionAddress
    }

    private fun toOnionEndpoint(rawOnion: String): String {
        val clean = rawOnion.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val host = clean.substringBefore(":").trim()
        if (host.isBlank()) return ""
        val port = clean.substringAfter(":", HIDDEN_SERVICE_PORT.toString())
            .toIntOrNull()
            ?: HIDDEN_SERVICE_PORT
        val onionHost = if (host.endsWith(".onion", ignoreCase = true)) host else "$host.onion"
        return "$onionHost:$port"
    }
    
    /**
     * Check if running
     */
    fun isRunning(): Boolean = isRunning.get()
    
    // ========== Private Methods ==========
    
    /**
     * Start the local server to handle incoming connections
     */
    private fun startServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(java.net.InetSocketAddress("127.0.0.1", HIDDEN_SERVICE_PORT))
        }
        
        serverJob = scope.launch {
            Log.i(TAG, "🔌 Hidden service server listening on port $HIDDEN_SERVICE_PORT")
            var rebindDelayMs = 500L

            while (isActive && isRunning.get()) {
                try {
                    val clientSocket = withContext(Dispatchers.IO) {
                        serverSocket?.accept()
                    }
                    rebindDelayMs = 500L

                    clientSocket?.let { socket ->
                        Log.d(TAG, "📥 Incoming connection from ${socket.remoteSocketAddress}")
                        val listener = connectionListener
                        if (listener != null) {
                            listener.onIncomingConnection(socket)
                        } else {
                            runCatching { socket.close() }
                        }
                    }

                } catch (e: SocketException) {
                    if (!isRunning.get() || !isActive) break
                    // A single socket error used to end the loop for good, leaving
                    // the app silently unreachable. Rebind and keep accepting.
                    Log.e(TAG, "Server socket error: ${e.message} — rebinding in ${rebindDelayMs}ms")
                    delay(rebindDelayMs)
                    rebindDelayMs = (rebindDelayMs * 2).coerceAtMost(10_000L)
                    try {
                        serverSocket?.close()
                    } catch (_: Exception) {
                    }
                    try {
                        serverSocket = ServerSocket().apply {
                            reuseAddress = true
                            bind(java.net.InetSocketAddress("127.0.0.1", HIDDEN_SERVICE_PORT))
                        }
                    } catch (bindError: Exception) {
                        Log.e(TAG, "Failed to rebind server socket: ${bindError.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error accepting connection: ${e.message}")
                    delay(500L)
                }
            }
        }
    }
    
    
    /**
     * Force republish hidden service descriptor.
     * Called by TorForegroundService when:
     * - Device exits Doze mode
     * - Network connectivity is restored
     * - Hidden service descriptor upload fails
     *
     * This ensures peers can always find us after interruptions.
     */
    suspend fun republishHiddenService() {
        if (!isRunning.get()) return
        
        Log.i(TAG, "🔄 Republishing hidden service...")
        
        val daemon = torDaemonManager ?: return
        if (!daemon.isConnected()) {
            Log.w(TAG, "Cannot republish — Tor daemon not connected")
            return
        }
        
        try {
            // Re-publish with retry
            val success = daemon.republishHiddenServiceWithRetry()
            if (!success) {
                _status.value = HiddenServiceStatus(
                    state = HiddenServiceState.ERROR,
                    torConnected = true,
                    error = daemon.statusFlow.value.error ?: "Hidden service publish failed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to republish hidden service: ${e.message}")
            _status.value = HiddenServiceStatus(
                state = HiddenServiceState.ERROR,
                torConnected = true,
                error = e.message ?: "Hidden service publish failed"
            )
        }
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
