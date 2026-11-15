@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.app.Application
import android.content.Context
import android.util.Log
import com.seamless.android.service.TorForegroundService
import info.guardianproject.arti.ArtiLogListener
import info.guardianproject.arti.ArtiProxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages embedded Tor lifecycle & provides SOCKS proxy address.
 * Coordinates both the embedded Tor daemon (TorDaemonManager on port 19050, which hosts
 * v3 .onion hidden services and supports Pluggable Transports) and Arti (Tor in Rust)
 * so that Tor bootstrap never deadlocks on single-stack IPv4/IPv6 mobile networks.
 */
object TorManager {
    private const val TAG = "TorManager"
    private const val DEFAULT_SOCKS_PORT = com.seamless.android.util.AppConstants.Tor.DEFAULT_SOCKS_PORT
    private const val RESTART_DELAY_MS = com.seamless.android.util.AppConstants.Tor.RESTART_DELAY_MS
    private const val INACTIVITY_TIMEOUT_MS = com.seamless.android.util.AppConstants.Tor.INACTIVITY_TIMEOUT_MS
    private const val MAX_RETRY_ATTEMPTS = com.seamless.android.util.AppConstants.Tor.MAX_RETRY_ATTEMPTS
    private const val STOP_TIMEOUT_MS = com.seamless.android.util.AppConstants.Tor.STOP_TIMEOUT_MS

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var initialized = false
    @Volatile private var socksAddr: InetSocketAddress? = null
    private val artiProxyRef = AtomicReference<ArtiProxy?>(null)
    @Volatile private var lastMode: TorMode = TorMode.OFF
    private val applyMutex = Mutex()
    @Volatile private var desiredMode: TorMode = TorMode.OFF
    @Volatile private var currentSocksPort: Int = DEFAULT_SOCKS_PORT
    @Volatile private var lastLogTime = AtomicLong(0L)
    @Volatile private var lastProgressTime = AtomicLong(0L)
    @Volatile private var retryAttempts = 0
    @Volatile private var bindRetryAttempts = 0
    private var inactivityJob: Job? = null
    private var retryJob: Job? = null
    private var daemonObserverJob: Job? = null
    private var daemonFallbackJob: Job? = null
    private var currentApplication: Application? = null

    // Pluggable Transport integration for censorship circumvention
    @Volatile private var ptManager: PluggableTransportManager? = null
    @Volatile private var activeTransportStrategy: CensorshipDetector.TransportStrategy = CensorshipDetector.TransportStrategy.VANILLA

    private enum class LifecycleState { STOPPED, STARTING, RUNNING, STOPPING }
    @Volatile private var lifecycleState: LifecycleState = LifecycleState.STOPPED

    enum class TorState { OFF, STARTING, BOOTSTRAPPING, RUNNING, STOPPING, ERROR }

    data class TorStatus(
        val mode: TorMode = TorMode.OFF,
        val running: Boolean = false,
        val bootstrapPercent: Int = 0,
        val lastLogLine: String = "",
        val state: TorState = TorState.OFF
    )

    private val _status = MutableStateFlow(TorStatus())
    val statusFlow: StateFlow<TorStatus> = _status.asStateFlow()

    private val stateChangeDeferred = AtomicReference<CompletableDeferred<TorState>?>(null)

    fun isProxyEnabled(): Boolean {
        val s = _status.value
        if (s.mode != TorMode.OFF && s.running && s.bootstrapPercent >= 100 && socksAddr != null && s.state == TorState.RUNNING) {
            return true
        }
        val app = currentApplication
        if (s.mode != TorMode.OFF && app != null) {
            val daemonState = runCatching {
                TorDaemonManager.getInstance(app).statusFlow.value.state
            }.getOrNull()
            if (daemonState == TorDaemonManager.DaemonState.CONNECTED) {
                return true
            }
        }
        return false
    }

    /**
     * Restart Tor to apply new Pluggable Transport Bridges.
     * Often used when the Tor connection stalls to circumvent censorship.
     */
    fun restartToApplyBridges(context: Context) {
        val app = context.applicationContext as Application
        appScope.launch {
            Log.i(TAG, "Restarting Tor to apply new bridges...")
            applyMode(app, TorMode.OFF)
            delay(1000)
            applyMode(app, TorMode.ON)
        }
    }

    fun init(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            currentApplication = application
            TorPreferenceManager.init(application)

            // Initialize Pluggable Transport Manager
            ptManager = PluggableTransportManager.getInstance(application)
            ensureDaemonObserver(application)

            val savedMode = TorPreferenceManager.get(application)
            if (savedMode == TorMode.ON) {
                if (currentSocksPort < DEFAULT_SOCKS_PORT) {
                    currentSocksPort = DEFAULT_SOCKS_PORT
                }
                desiredMode = savedMode
            }
            appScope.launch {
                applyMode(application, savedMode)
            }

            // Observe changes
            appScope.launch {
                TorPreferenceManager.modeFlow.collect { mode ->
                    applyMode(application, mode)
                }
            }
        }
    }

    private fun ensureDaemonObserver(application: Application) {
        if (daemonObserverJob?.isActive == true) return
        daemonObserverJob = appScope.launch {
            val daemonManager = runCatching { TorDaemonManager.getInstance(application) }.getOrNull()
                ?: return@launch
            daemonManager.statusFlow.collect { daemonStatus ->
                if (desiredMode != TorMode.ON && _status.value.mode != TorMode.ON) {
                    return@collect
                }
                when (daemonStatus.state) {
                    TorDaemonManager.DaemonState.STARTING,
                    TorDaemonManager.DaemonState.BOOTSTRAPPING -> {
                        val progress = daemonStatus.bootstrapPercent.coerceIn(0, 99)
                        lastProgressTime.set(System.currentTimeMillis())
                        val current = _status.value
                        if (current.state != TorState.RUNNING || current.bootstrapPercent < 100) {
                            val nextPercent = maxOf(current.bootstrapPercent, progress)
                            val logMsg = if (progress > 0) {
                                "Bootstrapping Tor network ($progress%)..."
                            } else {
                                current.lastLogLine.ifBlank { "Starting Tor daemon..." }
                            }
                            _status.value = current.copy(
                                mode = TorMode.ON,
                                running = true,
                                bootstrapPercent = nextPercent,
                                state = if (nextPercent > 0) TorState.BOOTSTRAPPING else TorState.STARTING,
                                lastLogLine = logMsg
                            )
                        }
                    }
                    TorDaemonManager.DaemonState.CONNECTED -> {
                        lastProgressTime.set(System.currentTimeMillis())
                        val daemonSocks = InetSocketAddress("127.0.0.1", TorDaemonManager.HIDDEN_SERVICE_SOCKS_PORT)
                        val wasReady = _status.value.state == TorState.RUNNING && _status.value.bootstrapPercent >= 100
                        socksAddr = daemonSocks
                        lifecycleState = LifecycleState.RUNNING
                        daemonFallbackJob?.cancel()
                        retryAttempts = 0
                        bindRetryAttempts = 0
                        _status.value = _status.value.copy(
                            mode = TorMode.ON,
                            running = true,
                            bootstrapPercent = 100,
                            state = TorState.RUNNING,
                            lastLogLine = "Connected to Tor network (100%)"
                        )
                        completeWaitersIf(TorState.RUNNING)
                        // Stop redundant Arti retries/warnings once the primary C-Tor daemon is 100% connected
                        stopInactivityMonitoring()
                        stopRetryMonitoring()
                        if (artiProxyRef.get() != null) {
                            Log.i(TAG, "TorDaemonManager connected on port ${TorDaemonManager.HIDDEN_SERVICE_SOCKS_PORT}; stopping redundant Arti instance")
                            stopArtiInternal()
                        }
                        if (!wasReady) {
                            try { OkHttpProvider.reset() } catch (_: Throwable) { }
                            try { com.seamless.android.nostr.NostrRelayManager.shared.reconnect() } catch (_: Throwable) { }
                        }
                    }
                    TorDaemonManager.DaemonState.ERROR -> {
                        val err = daemonStatus.error
                        if (!err.isNullOrBlank() && _status.value.state != TorState.RUNNING) {
                            _status.value = _status.value.copy(
                                lastLogLine = err
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun currentSocksAddress(): InetSocketAddress? {
        return currentBestSocksAddress(currentApplication) ?: socksAddr
    }

    fun currentBestSocksAddress(context: Context? = null): InetSocketAddress? {
        val status = _status.value
        val primary = socksAddr

        // Direct P2P traffic and .onion mailbox calls must use the Tor daemon that
        // published our onion service whenever it is connected.
        val app = (context?.applicationContext as? Application) ?: currentApplication
        if (app != null) {
            val daemonStatus = runCatching {
                TorDaemonManager.getInstance(app).statusFlow.value
            }.getOrNull()
            if (daemonStatus?.state == TorDaemonManager.DaemonState.CONNECTED) {
                return InetSocketAddress("127.0.0.1", TorDaemonManager.HIDDEN_SERVICE_SOCKS_PORT)
            }
        }

        if (status.mode != TorMode.OFF &&
            status.running &&
            status.bootstrapPercent >= 100 &&
            status.state == TorState.RUNNING &&
            primary != null
        ) {
            return primary
        }

        return if (status.mode != TorMode.OFF && status.running && primary != null) primary else null
    }

    suspend fun applyMode(application: Application, mode: TorMode) {
        currentApplication = application
        ensureDaemonObserver(application)
        applyMutex.withLock {
            try {
                desiredMode = mode
                lastMode = mode
                val s = _status.value
                if (mode == s.mode && mode != TorMode.OFF && (lifecycleState == LifecycleState.STARTING || lifecycleState == LifecycleState.RUNNING)) {
                    Log.i(TAG, "applyMode: already in progress/running mode=$mode, state=$lifecycleState; skip")
                    return
                }
                when (mode) {
                    TorMode.OFF -> {
                        Log.i(TAG, "applyMode: OFF -> stopping tor")
                        daemonFallbackJob?.cancel()
                        daemonFallbackJob = null
                        lifecycleState = LifecycleState.STOPPING
                        _status.value = _status.value.copy(mode = TorMode.OFF, running = false, bootstrapPercent = 0, state = TorState.STOPPING)

                        // Stop TorForegroundService (which stops TorMessagingService, hidden service, Doze/network handling)
                        TorForegroundService.stop(application)

                        stopArti() // non-suspending immediate request
                        waitForStateTransition(target = TorState.OFF, timeoutMs = STOP_TIMEOUT_MS)
                        socksAddr = null
                        _status.value = _status.value.copy(mode = TorMode.OFF, running = false, bootstrapPercent = 0, state = TorState.OFF, lastLogLine = "")
                        currentSocksPort = DEFAULT_SOCKS_PORT
                        bindRetryAttempts = 0
                        lifecycleState = LifecycleState.STOPPED
                        try {
                            OkHttpProvider.reset()
                            com.seamless.android.nostr.NostrRelayManager.shared.reconnect()
                        } catch (_: Throwable) { }
                    }
                    TorMode.ON -> {
                        Log.i(TAG, "applyMode: ON -> starting TorForegroundService & Tor engines")
                        if (currentSocksPort < DEFAULT_SOCKS_PORT) {
                            currentSocksPort = DEFAULT_SOCKS_PORT
                        }
                        bindRetryAttempts = 0
                        lifecycleState = LifecycleState.STARTING
                        socksAddr = null
                        _status.value = _status.value.copy(
                            mode = TorMode.ON,
                            running = true,
                            bootstrapPercent = 0,
                            state = TorState.STARTING,
                            lastLogLine = "Starting Tor..."
                        )
                        try { OkHttpProvider.reset() } catch (_: Throwable) { }

                        // CRITICAL FIX: Start TorForegroundService immediately so TorDaemonManager
                        // (Briar C-Tor on port 19050) and TorMessagingService bootstrap in parallel
                        // instead of deadlocking behind Arti when IPv6 routes fail (os error 113).
                        try {
                            TorForegroundService.start(application)
                            Log.i(TAG, "🧅 TorForegroundService started immediately on TorMode.ON")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not start TorForegroundService immediately: ${e.message}")
                        }

                        // Check if TorDaemonManager is already connected
                        val daemonAlreadyConnected = runCatching {
                            TorDaemonManager.getInstance(application).statusFlow.value.state == TorDaemonManager.DaemonState.CONNECTED
                        }.getOrDefault(false)

                        if (daemonAlreadyConnected) {
                            socksAddr = InetSocketAddress("127.0.0.1", TorDaemonManager.HIDDEN_SERVICE_SOCKS_PORT)
                            lifecycleState = LifecycleState.RUNNING
                            _status.value = _status.value.copy(
                                mode = TorMode.ON,
                                running = true,
                                bootstrapPercent = 100,
                                state = TorState.RUNNING,
                                lastLogLine = "Connected to Tor network (100%)"
                            )
                            try { OkHttpProvider.reset() } catch (_: Throwable) { }
                            try { com.seamless.android.nostr.NostrRelayManager.shared.reconnect() } catch (_: Throwable) { }
                        } else {
                            // Prefer the primary C-Tor daemon (port 19050, which also hosts our .onion)
                            // and only start Arti (port 9150) as a fallback if C-Tor errors or fails
                            // to connect within 45s. Starting both simultaneously causes CPU/network
                            // contention and mid-session cutover drops when C-Tor later stops Arti.
                            daemonFallbackJob?.cancel()
                            daemonFallbackJob = appScope.launch {
                                val daemonManager = TorDaemonManager.getInstance(application)
                                val terminalDaemonState = withTimeoutOrNull(45_000L) {
                                    daemonManager.statusFlow.first {
                                        it.state == TorDaemonManager.DaemonState.CONNECTED ||
                                            it.state == TorDaemonManager.DaemonState.ERROR
                                    }
                                }?.state

                                if (desiredMode != TorMode.ON ||
                                    terminalDaemonState == TorDaemonManager.DaemonState.CONNECTED ||
                                    daemonManager.statusFlow.value.state == TorDaemonManager.DaemonState.CONNECTED
                                ) {
                                    return@launch
                                }

                                Log.w(TAG, "TorDaemonManager not connected after fallback wait (state=$terminalDaemonState); starting Arti fallback")
                                startArti(application, useDelay = false)
                                waitUntilBootstrapped()
                                if (_status.value.running && desiredMode == TorMode.ON) {
                                    if (socksAddr == null) {
                                        socksAddr = currentBestSocksAddress(application)
                                            ?: InetSocketAddress("127.0.0.1", currentSocksPort)
                                    }
                                    Log.i(TAG, "Tor ON: proxy set to $socksAddr")
                                    OkHttpProvider.reset()
                                    try { com.seamless.android.nostr.NostrRelayManager.shared.reconnect() } catch (_: Throwable) { }
                                    try { TorForegroundService.start(application) } catch (_: Throwable) { }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply Tor mode: ${e.message}")
            }
        }
    }

    private suspend fun startArti(application: Application, useDelay: Boolean = false) {
        try {
            // If C-Tor daemon is already connected, no need to spin up a second Tor engine
            val daemonConnected = runCatching {
                TorDaemonManager.getInstance(application).statusFlow.value.state == TorDaemonManager.DaemonState.CONNECTED
            }.getOrDefault(false)
            if (daemonConnected) {
                Log.i(TAG, "TorDaemonManager already connected; skipping redundant Arti startup")
                socksAddr = InetSocketAddress("127.0.0.1", TorDaemonManager.HIDDEN_SERVICE_SOCKS_PORT)
                _status.value = _status.value.copy(
                    mode = TorMode.ON,
                    running = true,
                    bootstrapPercent = 100,
                    state = TorState.RUNNING,
                    lastLogLine = "Connected to Tor network (100%)"
                )
                lifecycleState = LifecycleState.RUNNING
                return
            }

            // Ensure any previous instance is fully stopped before starting a new one
            stopArtiAndWait()

            Log.i(TAG, "Starting Arti on port $currentSocksPort…")
            if (useDelay) {
                delay(RESTART_DELAY_MS)
            }

            // ━━━ Censorship Detection & Pluggable Transport Selection ━━━
            ptManager?.let { pt ->
                try {
                    val config = pt.initialize()
                    activeTransportStrategy = config.strategy

                    if (config.strategy != CensorshipDetector.TransportStrategy.VANILLA) {
                        Log.i(TAG, "🌍 Censorship detected — using ${config.strategy.name} transport")
                        Log.i(TAG, "🌉 Bridges: ${config.bridges.size} configured")

                        val bridgeConfig = pt.generateBridgeConfig()
                        if (bridgeConfig.isNotEmpty()) {
                            Log.d(TAG, "Bridge config:\n$bridgeConfig")
                        }
                    } else {
                        Log.i(TAG, "✅ No censorship detected — using direct Tor")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Censorship detection failed, defaulting to vanilla: ${e.message}")
                    activeTransportStrategy = CensorshipDetector.TransportStrategy.VANILLA
                }
            }

            val logListener = ArtiLogListener { logLine ->
                val text = logLine ?: return@ArtiLogListener
                val s = text.toString()
                Log.i(TAG, "arti: $s")
                lastLogTime.set(System.currentTimeMillis())

                // Filter out noisy single-stack IPv6/unreachable relay warnings (os error 113 / 101)
                // so they don't overwrite real bootstrap progress or trick inactivity monitoring.
                val isTransientRouteWarning = s.contains("No route to host", ignoreCase = true) ||
                    s.contains("os error 113", ignoreCase = true) ||
                    s.contains("Network is unreachable", ignoreCase = true) ||
                    s.contains("os error 101", ignoreCase = true)

                if (!isTransientRouteWarning) {
                    lastProgressTime.set(System.currentTimeMillis())
                    if (_status.value.bootstrapPercent < 100) {
                        _status.value = _status.value.copy(lastLogLine = s)
                    }
                } else if (_status.value.lastLogLine.isBlank()) {
                    _status.value = _status.value.copy(lastLogLine = "Connecting to Tor relays (IPv4 fallback active)...")
                }

                handleArtiLogLine(s)
            }

            val proxy = ArtiProxy.Builder(application)
                .setSocksPort(currentSocksPort)
                .setDnsPort(currentSocksPort + 1)
                .setLogListener(logListener)
                .build()

            artiProxyRef.set(proxy)
            proxy.start()
            val now = System.currentTimeMillis()
            lastLogTime.set(now)
            lastProgressTime.set(now)

            if (_status.value.state != TorState.RUNNING) {
                _status.value = _status.value.copy(running = true, state = TorState.STARTING)
            }
            lifecycleState = LifecycleState.RUNNING
            startInactivityMonitoring()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Arti on port $currentSocksPort: ${e.message}")
            val daemonConnected = runCatching {
                TorDaemonManager.getInstance(application).statusFlow.value.state == TorDaemonManager.DaemonState.CONNECTED
            }.getOrDefault(false)
            if (daemonConnected) {
                return
            }
            _status.value = _status.value.copy(state = TorState.ERROR)

            val isBindError = isBindError(e)
            if (isBindError && bindRetryAttempts < MAX_RETRY_ATTEMPTS) {
                bindRetryAttempts++
                currentSocksPort++
                Log.w(TAG, "Port bind failed (attempt $bindRetryAttempts/$MAX_RETRY_ATTEMPTS), retrying with port $currentSocksPort")
                socksAddr = InetSocketAddress("127.0.0.1", currentSocksPort)
                try { OkHttpProvider.reset() } catch (_: Throwable) { }
                try { com.seamless.android.nostr.NostrRelayManager.shared.resetAllConnections() } catch (_: Throwable) { }
                startArti(application, useDelay = false)
            } else if (isBindError) {
                Log.e(TAG, "Max bind retry attempts reached ($MAX_RETRY_ATTEMPTS), giving up on Arti")
                lifecycleState = LifecycleState.STOPPED
                _status.value = _status.value.copy(running = false, bootstrapPercent = 0, state = TorState.ERROR)
            } else {
                scheduleRetry(application)
            }
        }
    }

    private fun isBindError(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("bind") ||
               message.contains("address already in use") ||
               message.contains("port") && message.contains("use") ||
               message.contains("permission denied") && message.contains("port") ||
               message.contains("could not bind")
    }

    private fun stopArtiInternal() {
        try {
            val proxy = artiProxyRef.getAndSet(null)
            if (proxy != null) {
                Log.i(TAG, "Stopping Arti…")
                try { proxy.stop() } catch (_: Throwable) {}
            }
            stopInactivityMonitoring()
            stopRetryMonitoring()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Arti: ${e.message}")
        }
    }

    private fun stopArti() {
        stopArtiInternal()
        socksAddr = null
        _status.value = _status.value.copy(running = false, bootstrapPercent = 0, state = TorState.STOPPING)
    }

    private suspend fun stopArtiAndWait(timeoutMs: Long = STOP_TIMEOUT_MS) {
        stopArtiInternal()
        waitForStateTransition(target = TorState.OFF, timeoutMs = timeoutMs)
        delay(200)
    }

    private suspend fun restartArti(application: Application) {
        val daemonConnected = runCatching {
            TorDaemonManager.getInstance(application).statusFlow.value.state == TorDaemonManager.DaemonState.CONNECTED
        }.getOrDefault(false)
        if (daemonConnected) {
            Log.i(TAG, "Skipping Arti restart because TorDaemonManager is already CONNECTED")
            stopArtiInternal()
            return
        }
        Log.i(TAG, "Restarting Arti (keeping SOCKS proxy enabled)...")
        stopArtiAndWait()
        delay(RESTART_DELAY_MS)
        startArti(application, useDelay = false)
    }

    private fun startInactivityMonitoring() {
        inactivityJob?.cancel()
        val timeoutMs = INACTIVITY_TIMEOUT_MS.coerceAtLeast(45_000L)
        inactivityJob = appScope.launch {
            while (true) {
                delay(timeoutMs)
                val currentTime = System.currentTimeMillis()
                val lastActivity = lastProgressTime.get().takeIf { it > 0L } ?: lastLogTime.get()
                val timeSinceLastActivity = currentTime - lastActivity

                if (timeSinceLastActivity > timeoutMs) {
                    val currentMode = _status.value.mode
                    if (currentMode == TorMode.ON) {
                        val bootstrapPercent = _status.value.bootstrapPercent
                        if (bootstrapPercent < 100) {
                            Log.w(TAG, "Bootstrap stall detected (${timeSinceLastActivity}ms without progress), restarting Arti")
                            currentApplication?.let { app ->
                                appScope.launch {
                                    restartArti(app)
                                }
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stopInactivityMonitoring() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    private fun scheduleRetry(application: Application) {
        retryJob?.cancel()
        if (retryAttempts < MAX_RETRY_ATTEMPTS) {
            retryAttempts++
            val delayMs = (1000L * (1 shl retryAttempts)).coerceAtMost(30000L)
            Log.w(TAG, "Scheduling Arti retry attempt $retryAttempts in ${delayMs}ms")
            retryJob = appScope.launch {
                delay(delayMs)
                val currentMode = _status.value.mode
                if (currentMode == TorMode.ON) {
                    Log.i(TAG, "Retrying Arti start (attempt $retryAttempts)")
                    restartArti(application)
                }
            }
        } else {
            Log.e(TAG, "Max retry attempts reached, giving up on Arti connection")
        }
    }

    private fun stopRetryMonitoring() {
        retryJob?.cancel()
        retryJob = null
    }

    private suspend fun waitUntilBootstrapped() {
        val current = _status.value
        if (!current.running) return
        if (current.bootstrapPercent >= 100 && current.state == TorState.RUNNING) return
        while (true) {
            val s = statusFlow.first { (it.bootstrapPercent >= 100 && it.state == TorState.RUNNING) || !it.running || it.state == TorState.ERROR }
            if (!s.running || s.state == TorState.ERROR) return
            if (s.bootstrapPercent >= 100 && s.state == TorState.RUNNING) return
        }
    }

    private fun handleArtiLogLine(s: String) {
        when {
            s.contains("AMEx: state changed to Initialized", ignoreCase = true) -> {
                if (_status.value.state != TorState.RUNNING) {
                    _status.value = _status.value.copy(state = TorState.STARTING)
                    completeWaitersIf(TorState.STARTING)
                }
            }
            s.contains("AMEx: state changed to Starting", ignoreCase = true) -> {
                if (_status.value.state != TorState.RUNNING) {
                    _status.value = _status.value.copy(state = TorState.STARTING)
                    completeWaitersIf(TorState.STARTING)
                }
            }
            s.contains("Listening on", ignoreCase = true) && s.contains(currentSocksPort.toString()) -> {
                if (_status.value.state != TorState.RUNNING) {
                    _status.value = _status.value.copy(state = TorState.BOOTSTRAPPING, running = true)
                    Log.i(TAG, "✅ SOCKS proxy listening — bootstrapping in progress")
                }
            }
            s.contains("Sufficiently bootstrapped", ignoreCase = true) -> {
                if (socksAddr == null) {
                    socksAddr = InetSocketAddress("127.0.0.1", currentSocksPort)
                }
                _status.value = _status.value.copy(
                    bootstrapPercent = 100,
                    state = TorState.RUNNING,
                    running = true,
                    lastLogLine = "Connected to Tor network (100%)"
                )
                retryAttempts = 0
                bindRetryAttempts = 0
                completeWaitersIf(TorState.RUNNING)
                stopInactivityMonitoring()
            }
            s.contains("We have found that guard [scrubbed] is usable.", ignoreCase = true) -> {
                if (socksAddr == null) {
                    socksAddr = InetSocketAddress("127.0.0.1", currentSocksPort)
                }
                _status.value = _status.value.copy(
                    state = TorState.RUNNING,
                    bootstrapPercent = 100,
                    running = true,
                    lastLogLine = "Connected to Tor network (100%)"
                )
                completeWaitersIf(TorState.RUNNING)
                stopInactivityMonitoring()
            }
            s.contains("AMEx: state changed to Stopping", ignoreCase = true) -> {
                if (desiredMode == TorMode.OFF) {
                    _status.value = _status.value.copy(state = TorState.STOPPING, running = false)
                }
            }
            s.contains("AMEx: state changed to Stopped", ignoreCase = true) -> {
                if (desiredMode == TorMode.OFF) {
                    _status.value = _status.value.copy(state = TorState.OFF, running = false, bootstrapPercent = 0)
                }
                completeWaitersIf(TorState.OFF)
            }
            s.contains("Another process has the lock on our state files", ignoreCase = true) -> {
                if (_status.value.state != TorState.RUNNING) {
                    _status.value = _status.value.copy(state = TorState.ERROR)
                }
            }
        }
    }

    private fun completeWaitersIf(state: TorState) {
        stateChangeDeferred.getAndSet(null)?.let { def ->
            def.complete(state)
        }
    }

    private suspend fun waitForStateTransition(target: TorState, timeoutMs: Long): TorState? {
        val def = CompletableDeferred<TorState>()
        stateChangeDeferred.getAndSet(def)?.cancel()
        return withTimeoutOrNull(timeoutMs) {
            val cur = _status.value.state
            if (cur == target) return@withTimeoutOrNull cur
            def.await()
        }
    }

    // Visible for instrumentation tests to validate installation
    fun installResourcesForTest(application: Application): Boolean { return true }

    // ===== Pluggable Transport / Censorship API =====

    fun getTransportStrategy(): CensorshipDetector.TransportStrategy {
        return activeTransportStrategy
    }

    fun getCensorshipInfo(): CensorshipDetector.DetectionResult? {
        val app = currentApplication ?: return null
        return CensorshipDetector.getInstance(app).detectionResult.value
    }

    suspend fun forceRedetectCensorship() {
        val app = currentApplication ?: return
        ptManager?.let { pt ->
            val config = pt.autoDetectAndApply()
            activeTransportStrategy = config.strategy
            Log.i(TAG, "🔍 Re-detected censorship: strategy=${config.strategy.name}")

            if (_status.value.running) {
                Log.i(TAG, "🔄 Restarting Tor with new transport: ${config.strategy.name}")
                applyMode(app, TorMode.OFF)
                delay(RESTART_DELAY_MS)
                applyMode(app, TorMode.ON)
            }
        }
    }

    fun getAvailableStrategies(): List<PluggableTransportManager.StrategyInfo> {
        return ptManager?.getAvailableStrategies() ?: emptyList()
    }
}
