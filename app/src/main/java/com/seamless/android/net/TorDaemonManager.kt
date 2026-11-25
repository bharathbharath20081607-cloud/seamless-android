@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
// TorDaemonManager — C-Tor daemon lifecycle manager
package com.seamless.android.net

import android.app.Application
import android.util.Log
import com.seamless.android.identity.SecureIdentityStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManagerFactory
import org.briarproject.onionwrapper.AndroidTorWrapper
import org.briarproject.onionwrapper.TorWrapper
import org.briarproject.onionwrapper.TorWrapper.HiddenServiceProperties
import org.briarproject.onionwrapper.TorWrapper.Observer
import org.briarproject.onionwrapper.TorWrapper.TorState
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TorDaemonManager - Manages embedded Tor daemon for hidden service support
 * 
 * This is the key component that makes Tor messaging work like Cwtch/Briar.
 * It wraps Briar's AndroidTorWrapper to:
 * - Start/stop the embedded Tor daemon
 * - Publish hidden services (making us reachable via .onion address)
 * - Handle Tor state changes (bootstrapping, connected, etc.)
 * 
 * Architecture:
 * - Uses onionwrapper for hidden service hosting (inbound connections)
 * - Works alongside Arti SOCKS proxy for outbound connections
 */
class TorDaemonManager private constructor(private val application: Application) {
    
    companion object {
        private const val TAG = "TorDaemonManager"
        
        // Tor ports - must not conflict with Arti
        const val HIDDEN_SERVICE_SOCKS_PORT = 19050  // Different from Arti's 9150
        const val CONTROL_PORT = 19051
        private const val SOCKS_READY_TIMEOUT_MS = 30_000L
        
        // Architecture mappings for Tor binaries
        private val ARCH_MAP = mapOf(
            "arm64-v8a" to "arm64_pie",
            "armeabi-v7a" to "arm_pie",
            "x86_64" to "x86_64_pie",
            "x86" to "x86_pie"
        )

        private val STRICT_V3_ONION_ID = Regex("^[a-z2-7]{56}$")
        private val BOOTSTRAP_CACHE_FILES = listOf(
            "cached-microdesc-consensus",
            "cached-microdescs",
            "cached-microdescs.new",
            "cached-certs",
            "unverified-microdesc-consensus"
        )

        internal fun extractV3OnionId(endpoint: String): String? {
            val raw = endpoint.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")
                .lowercase()
                .removeSuffix(".onion")
            return if (STRICT_V3_ONION_ID.matches(raw)) raw else null
        }

        @Volatile
        private var instance: TorDaemonManager? = null
        
        fun getInstance(application: Application): TorDaemonManager {
            return instance ?: synchronized(this) {
                instance ?: TorDaemonManager(application).also { instance = it }
            }
        }
    }
    
    // Tor daemon state
    enum class DaemonState {
        NOT_STARTED,
        STARTING,
        BOOTSTRAPPING,
        CONNECTED,
        STOPPING,
        STOPPED,
        ERROR
    }
    
    data class DaemonStatus(
        val state: DaemonState = DaemonState.NOT_STARTED,
        val bootstrapPercent: Int = 0,
        val onionAddress: String? = null,
        val error: String? = null
    )
    
    private val _status = MutableStateFlow(DaemonStatus())
    val statusFlow: StateFlow<DaemonStatus> = _status.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val startStopMutex = Mutex()
    private val publishMutex = Mutex()
    private val handoverMutex = Mutex()
    private val uploadedOnions = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var consecutiveBootstrapFailures = 0
    @Volatile
    private var reachedConnectedThisStart = false

    private fun normalizeOnionKey(onion: String): String =
        onion.trim().lowercase().removeSuffix(".onion")
    
    // Executors for TorWrapper
    private val ioExecutor: Executor = Executors.newCachedThreadPool()
    private val eventExecutor: Executor = Executors.newSingleThreadExecutor()
    
    // Tor wrapper instance
    private var torWrapper: TorWrapper? = null
    
    // Hidden service info
    private var hiddenServiceProps: HiddenServiceProperties? = null
    private var hiddenServicePrivateKey: String? = null
    private var mailboxServiceProps: HiddenServiceProperties? = null
    private var mailboxServicePrivateKey: String? = null
    private var hiddenServicePublishJob: Job? = null
    
    // Tor data directory
    private val torDirectory by lazy { File(application.filesDir, "tor_daemon") }

    init {
        loadSavedOnionAddress()
    }
    
    private fun clearCorruptBootstrapCacheIfNeeded() {
        if (consecutiveBootstrapFailures < 2) return
        var deleted = 0
        val dirs = listOf(File(torDirectory, ".tor"), torDirectory)
        for (dir in dirs) {
            if (!dir.exists()) continue
            for (name in BOOTSTRAP_CACHE_FILES) {
                val f = File(dir, name)
                if (f.exists() && runCatching { f.delete() }.getOrDefault(false)) {
                    deleted++
                }
            }
        }
        consecutiveBootstrapFailures = 0
        Log.w(TAG, "🧹 Purged $deleted cached Tor consensus/microdescriptor file(s) after repeated bootstrap failures")
    }

    /**
     * Start the Tor daemon
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        startStopMutex.withLock {
        if (isRunning.get()) {
            Log.d(TAG, "Tor daemon already running")
            return@withContext true
        }
        
        try {
            reachedConnectedThisStart = false
            _status.value = _status.value.copy(state = DaemonState.STARTING, error = null)
            
            // Ensure directory exists and clear corrupt consensus caches after repeated failures
            torDirectory.mkdirs()
            clearCorruptBootstrapCacheIfNeeded()
            
            // Get architecture
            val architecture = getArchitecture()
            if (architecture == null) {
                Log.e(TAG, "Unsupported architecture")
                _status.value = DaemonStatus(
                    state = DaemonState.ERROR,
                    error = "Unsupported device architecture"
                )
                return@withContext false
            }
            
            Log.i(TAG, "🧅 Starting Tor daemon (arch: $architecture)")
            
            // Load saved private key if exists
            loadPrivateKey()
            loadMailboxPrivateKey()
            
            // ━━━ Configure Bridges / Pluggable Transports ━━━
            val ptManager = PluggableTransportManager.getInstance(application)
            val ptConfig = ptManager.transportConfig.value ?: runCatching { ptManager.initialize() }.getOrNull()
            configureBridges()
            
            // AndroidTorWrapper creates its own process wake lock and requires
            // a real manager here; passing null fails during wrapper creation.
            val wakeLockManager = AndroidWakeLockManagerFactory.createAndroidWakeLockManager(application)
            val wrapper = AndroidTorWrapper(
                application,
                wakeLockManager,
                ioExecutor,
                eventExecutor,
                architecture,
                torDirectory,
                HIDDEN_SERVICE_SOCKS_PORT,
                CONTROL_PORT
            )
            
            // Set up observer for state changes
            wrapper.setObserver(object : Observer {
                override fun onState(torState: TorState) {
                    Log.d(TAG, "Tor state: $torState")
                    when (torState) {
                        TorState.NOT_STARTED -> {
                            _status.value = _status.value.copy(state = DaemonState.NOT_STARTED)
                        }
                        TorState.STARTING -> {
                            _status.value = _status.value.copy(state = DaemonState.STARTING)
                        }
                        TorState.STARTED -> {
                            _status.value = _status.value.copy(state = DaemonState.BOOTSTRAPPING)
                        }
                        TorState.CONNECTING -> {
                            _status.value = _status.value.copy(state = DaemonState.BOOTSTRAPPING)
                        }
                        TorState.CONNECTED -> {
                            Log.i(TAG, "✅ Tor connected to network!")
                            reachedConnectedThisStart = true
                            consecutiveBootstrapFailures = 0
                            _status.value = _status.value.copy(
                                state = DaemonState.CONNECTED,
                                bootstrapPercent = 100
                            )
                            // Publish hidden service now that we're connected
                            scheduleHiddenServicePublish("connected")
                            // Start health monitoring
                            startHealthMonitoring()
                        }
                        TorState.STOPPING -> {
                            _status.value = _status.value.copy(state = DaemonState.STOPPING)
                        }
                        TorState.STOPPED -> {
                            if (!reachedConnectedThisStart) {
                                consecutiveBootstrapFailures++
                            }
                            _status.value = _status.value.copy(state = DaemonState.STOPPED)
                            isRunning.set(false)
                            stopHealthMonitoring()
                        }
                        else -> {
                            // DISABLED or other states - treat as not started
                            Log.d(TAG, "Tor state (other): $torState")
                            _status.value = _status.value.copy(state = DaemonState.NOT_STARTED)
                        }
                    }
                }
                
                override fun onBootstrapPercentage(percentage: Int) {
                    Log.d(TAG, "Bootstrap: $percentage%")
                    _status.value = _status.value.copy(bootstrapPercent = percentage)
                }
                
                override fun onHsDescriptorUpload(onion: String) {
                    Log.i(TAG, "🧅 Hidden service descriptor uploaded: ${onion.take(10)}...")
                    val normalized = normalizeOnionKey(onion)
                    if (normalized.isNotEmpty()) {
                        uploadedOnions.add(normalized)
                    } else {
                        hiddenServiceProps?.onion?.let { uploadedOnions.add(normalizeOnionKey(it)) }
                    }
                }
                
                override fun onClockSkewDetected(skewSeconds: Long) {
                    Log.w(TAG, "Clock skew detected: ${skewSeconds}s")
                }
            })
            
            // Store wrapper before start() because Tor can emit CONNECTED from
            // callbacks before start()/enableNetwork() returns.
            torWrapper = wrapper
            isRunning.set(true)

            if (ptConfig != null && ptConfig.bridges.isNotEmpty()) {
                runCatching { wrapper.enableBridges(ptConfig.bridges) }
                    .onFailure { Log.w(TAG, "enableBridges failed: ${it.message}") }
            } else {
                runCatching { wrapper.disableBridges() }
            }

            // Start Tor
            wrapper.start()
            applyMobileTorConfig()
            applyIpv6Preference(wrapper)

            // Pre-register ADD_ONION on the local control socket immediately so the
            // user's .onion address, QR code, and share link are ready without waiting
            // for full network bootstrap.
            scheduleHiddenServicePublish("daemon-started")
            
            // Enable networking
            wrapper.enableNetwork(true)
            
            Log.i(TAG, "🚀 Tor daemon started successfully")
            true
            
        } catch (e: Exception) {
            consecutiveBootstrapFailures++
            val errorMessage = "${e::class.java.simpleName}: ${e.message ?: "unknown error"}"
            Log.e(TAG, "Failed to start Tor daemon: $errorMessage", e)
            isRunning.set(false)
            torWrapper = null
            _status.value = DaemonStatus(
                state = DaemonState.ERROR,
                error = errorMessage
            )
            false
        }
        }
    }
    
    /**
     * Publish hidden service on specified local port
     */
    suspend fun publishHiddenService(localPort: Int): String? = withContext(Dispatchers.IO) {
        publishMutex.withLock {
        val wrapper = torWrapper
        if (wrapper == null || !wrapper.isTorRunning) {
            val message = "Cannot publish hidden service - Tor daemon is not running"
            Log.e(TAG, message)
            _status.value = _status.value.copy(error = message)
            return@withContext null
        }
        
        try {
            val isBootstrapped = _status.value.bootstrapPercent >= 100 ||
                wrapper.getTorState() == TorState.CONNECTED
            val resolvedState = if (isBootstrapped) {
                DaemonState.CONNECTED
            } else {
                _status.value.state.takeIf { it != DaemonState.NOT_STARTED } ?: DaemonState.BOOTSTRAPPING
            }

            val existingProps = hiddenServiceProps
            if (existingProps != null && existingProps.onion.isNotBlank()) {
                saveOnionAddress(existingProps.onion)
                _status.value = _status.value.copy(
                    state = resolvedState,
                    onionAddress = existingProps.onion,
                    error = null
                )
                Log.d(TAG, "♻️ Reusing active hidden service: ${existingProps.onion.take(16)}...onion")
                return@withContext existingProps.onion
            }

            Log.i(TAG, "📡 Publishing hidden service on port $localPort (bootstrapped=$isBootstrapped)")
            
            // Publish the hidden service via ADD_ONION over the local control connection.
            // virtualPort MUST match the port peers connect to (HIDDEN_SERVICE_PORT = 18888)
            val props = publishWithKeyRecovery(wrapper, localPort, localPort)
            
            hiddenServiceProps = props
            
            // Save private key and onion address for instant 0ms availability on reopen
            if (hiddenServicePrivateKey == null) {
                hiddenServicePrivateKey = props.privKey
                savePrivateKey(props.privKey)
            }
            
            val onionAddress = props.onion
            saveOnionAddress(onionAddress)
            _status.value = _status.value.copy(
                state = resolvedState,
                onionAddress = onionAddress,
                error = null
            )
            
            Log.i(TAG, "✅ Hidden service published: ${onionAddress.take(16)}...onion")
            
            onionAddress
            
        } catch (e: Exception) {
            val message = "${e::class.java.simpleName}: ${e.message ?: "unknown publish error"}"
            Log.e(TAG, "Failed to publish hidden service: $message", e)
            _status.value = _status.value.copy(error = "Hidden service publish failed: $message")
            null
        }
        }
    }
    
    /**
     * Publish a custom hidden service with a specific local port, virtual port, and private key blob.
     * Returns the onion address if successful.
     */
    suspend fun publishCustomHiddenService(
        localPort: Int,
        virtualPort: Int,
        privateKeyBlob: String
    ): String? = withContext(Dispatchers.IO) {
        val wrapper = torWrapper
        if (wrapper == null || !wrapper.isTorRunning) {
            Log.e(TAG, "Cannot publish custom hidden service - Tor not running")
            return@withContext null
        }
        
        try {
            Log.i(TAG, "📡 Publishing custom hidden service: localPort=$localPort, virtualPort=$virtualPort")
            val props = wrapper.publishHiddenService(localPort, virtualPort, privateKeyBlob)
            val onionAddress = props.onion
            Log.i(TAG, "✅ Custom hidden service published: ${onionAddress.take(16)}...onion")
            onionAddress
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish custom hidden service: ${e.message}", e)
            null
        }
    }

    /**
     * Remove a custom hidden service by its onion address.
     */
    suspend fun removeCustomHiddenService(onionAddress: String) = withContext(Dispatchers.IO) {
        val wrapper = torWrapper
        if (wrapper == null) {
            Log.e(TAG, "Cannot remove custom hidden service - Tor not running")
            return@withContext
        }
        
        try {
            Log.i(TAG, "📡 Removing custom hidden service: $onionAddress")
            wrapper.removeHiddenService(onionAddress)
            Log.i(TAG, "✅ Custom hidden service removed: $onionAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove custom hidden service: ${e.message}", e)
        }
    }
    
    /**
     * Internal method to publish hidden service after Tor connects
     */
    private suspend fun publishHiddenServiceInternal(): String? {
        // Use the standard hidden service port from TorHiddenServiceManager
        val port = TorHiddenServiceManager.HIDDEN_SERVICE_PORT
        return publishHiddenService(port)
    }

    /**
     * Publishes the mailbox HTTP server behind its own persistent onion service.
     * A single onion service cannot be mapped to two independent local ports by
     * OnionWrapper, so the chat listener and mailbox listener intentionally use
     * separate onion identities.
     */
    suspend fun publishMailboxHiddenService(localPort: Int = MailboxService.MAILBOX_PORT): String? =
        withContext(Dispatchers.IO) {
            publishMutex.withLock {
                val wrapper = torWrapper
                if (wrapper == null || !wrapper.isTorRunning) {
                    Log.w(TAG, "Cannot publish mailbox hidden service - Tor daemon is not running")
                    return@withContext null
                }

                val existingProps = mailboxServiceProps
                if (existingProps != null && existingProps.onion.isNotBlank()) {
                    Log.d(TAG, "♻️ Reusing active mailbox hidden service: ${existingProps.onion.take(16)}...onion")
                    return@withContext existingProps.onion
                }

                try {
                    if (!waitForDaemonSocks()) {
                        Log.w(TAG, "Cannot publish mailbox hidden service - Tor SOCKS listener is not ready")
                        return@withContext null
                    }
                    val props = publishMailboxWithKeyRecovery(wrapper, localPort, localPort)
                    mailboxServiceProps = props
                    if (mailboxServicePrivateKey == null) {
                        mailboxServicePrivateKey = props.privKey
                        saveMailboxPrivateKey(props.privKey)
                    }
                    Log.i(TAG, "Mailbox hidden service published: ${props.onion.take(16)}...onion")
                    props.onion
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish mailbox hidden service: ${e.message}", e)
                    null
                }
            }
        }

    private fun scheduleHiddenServicePublish(reason: String) {
        hiddenServicePublishJob?.cancel()
        hiddenServicePublishJob = scope.launch {
            publishHiddenServiceWithRetryInternal(reason = reason, maxAttempts = 6)
        }
    }

    private suspend fun publishHiddenServiceWithRetryInternal(
        reason: String,
        maxAttempts: Int
    ): String? {
        for (attempt in 1..maxAttempts) {
            val onion = publishHiddenServiceInternal()
            if (!onion.isNullOrBlank()) {
                Log.i(TAG, "✅ Hidden service published after $reason (attempt $attempt)")
                return onion
            }

            if (attempt < maxAttempts) {
                val delayMs = (2_000L * attempt).coerceAtMost(15_000L)
                Log.w(TAG, "Hidden service publish attempt $attempt/$maxAttempts failed; retrying in ${delayMs}ms")
                delay(delayMs)
            }
        }

        if (!reachedConnectedThisStart) {
            consecutiveBootstrapFailures++
        }
        val message = "Hidden service publish failed after $maxAttempts attempts"
        Log.e(TAG, message)
        _status.value = _status.value.copy(state = DaemonState.ERROR, error = message)
        return null
    }

    private fun isCollisionError(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("collision") || msg.contains("already")
    }

    private fun publishWithKeyRecovery(
        wrapper: TorWrapper,
        localPort: Int,
        virtualPort: Int
    ): HiddenServiceProperties {
        val savedKey = hiddenServicePrivateKey
        return try {
            wrapper.publishHiddenService(localPort, virtualPort, savedKey)
        } catch (e: Exception) {
            if (!savedKey.isNullOrBlank()) {
                Log.w(TAG, "Preserving permanent onion private key after publish error: ${e.message}")
            }
            throw e
        }
    }

    private fun publishMailboxWithKeyRecovery(
        wrapper: TorWrapper,
        localPort: Int,
        virtualPort: Int
    ): HiddenServiceProperties {
        val savedKey = mailboxServicePrivateKey
        return try {
            wrapper.publishHiddenService(localPort, virtualPort, savedKey)
        } catch (e: Exception) {
            if (!savedKey.isNullOrBlank()) {
                Log.w(TAG, "Preserving permanent mailbox private key after publish error: ${e.message}")
            }
            throw e
        }
    }

    private suspend fun waitForDaemonSocks(timeoutMs: Long = SOCKS_READY_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(
                        java.net.InetSocketAddress("127.0.0.1", HIDDEN_SERVICE_SOCKS_PORT),
                        500
                    )
                }
                return true
            } catch (_: Exception) {
                delay(500)
            }
        }
        return false
    }
    
    /**
     * Stop the Tor daemon
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        startStopMutex.withLock {
        if (!isRunning.get()) return@withContext
        
        try {
            Log.i(TAG, "Stopping Tor daemon...")
            _status.value = _status.value.copy(state = DaemonState.STOPPING)
            
            // Remove hidden service first
            hiddenServicePublishJob?.cancel()
            hiddenServicePublishJob = null

            hiddenServiceProps?.let { props ->
                uploadedOnions.remove(normalizeOnionKey(props.onion))
                try {
                    torWrapper?.removeHiddenService(props.onion)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing hidden service: ${e.message}")
                }
            }
            mailboxServiceProps?.let { props ->
                uploadedOnions.remove(normalizeOnionKey(props.onion))
                try {
                    torWrapper?.removeHiddenService(props.onion)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing mailbox hidden service: ${e.message}")
                }
            }
            hiddenServiceProps = null
            mailboxServiceProps = null
            uploadedOnions.clear()
            
            // Stop Tor
            torWrapper?.stop()
            torWrapper = null
            
            isRunning.set(false)
            _status.value = DaemonStatus(state = DaemonState.STOPPED)
            
            Log.i(TAG, "Tor daemon stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Tor daemon: ${e.message}", e)
        }
        }
    }
    
    /**
     * Check if Tor is running and connected
     */
    fun isConnected(): Boolean {
        return torWrapper?.isTorRunning == true && 
               _status.value.state == DaemonState.CONNECTED
    }
    
    /**
     * Get current onion address
     */
    fun getOnionAddress(): String? {
        return _status.value.onionAddress
    }
    
    /**
     * Get architecture string for Tor binaries
     */
    private fun getArchitecture(): String? {
        val supportedAbis = android.os.Build.SUPPORTED_ABIS
        for (abi in supportedAbis) {
            ARCH_MAP[abi]?.let { return it }
        }
        return null
    }
    
    /**
     * Save private key for persistent onion address — encrypted in secure storage
     */
    private fun savePrivateKey(privateKey: String) {
        try {
            val stateManager = SecureIdentityStateManager(application)
            stateManager.storeSecureValue("tor_hs_private_key", privateKey)
            // Delete any old plaintext key file
            val oldKeyFile = File(torDirectory, "hs_ed25519_private_key")
            if (oldKeyFile.exists()) oldKeyFile.delete()
            Log.d(TAG, "Hidden service private key saved to secure storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save private key: ${e.message}")
        }
    }
    
    /**
     * Load private key for persistent onion address — from secure storage with migration
     */
    private fun saveOnionAddress(onionAddress: String) {
        val clean = onionAddress.trim().removeSuffix(".onion")
        if (clean.isBlank()) return
        runCatching {
            SecureIdentityStateManager(application).storeSecureValue("tor_hs_onion_address", clean)
        }.onFailure { e ->
            Log.w(TAG, "Failed to save onion address: ${e.message}")
        }
    }

    private fun loadSavedOnionAddress() {
        val savedOnion = runCatching {
            SecureIdentityStateManager(application).getSecureValue("tor_hs_onion_address")
        }.getOrNull()?.trim()?.removeSuffix(".onion")?.takeIf { it.isNotBlank() } ?: return
        _status.value = _status.value.copy(onionAddress = savedOnion)
    }

    private fun loadPrivateKey() {
        try {
            val stateManager = SecureIdentityStateManager(application)
            // Try secure storage first
            val secureKey = stateManager.getSecureValue("tor_hs_private_key")
            if (secureKey != null) {
                hiddenServicePrivateKey = secureKey
                Log.d(TAG, "Hidden service private key loaded from secure storage")
                return
            }
            // Migrate from plaintext file
            val keyFile = File(torDirectory, "hs_ed25519_private_key")
            if (keyFile.exists()) {
                val plainKey = keyFile.readText()
                hiddenServicePrivateKey = plainKey
                // Migrate to secure storage and wipe file
                stateManager.storeSecureValue("tor_hs_private_key", plainKey)
                keyFile.delete()
                Log.d(TAG, "Migrated hidden service key to secure storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load private key: ${e.message}")
        }
    }
    
    /**
     * Toggle Tor's networking on/off (Doze mode support).
     * Called by TorForegroundService when device enters/exits Doze.
     *
     * When Doze enters: disable networking → Tor stops trying to use network
     * When Doze exits: enable networking → Tor rebuilds guard connections
     */
    fun enableNetwork(enable: Boolean) {
        val wrapper = torWrapper
        if (wrapper == null || !wrapper.isTorRunning) {
            Log.d(TAG, "Cannot toggle network — Tor not running")
            return
        }
        scope.launch {
            try {
                if (enable) {
                    applyIpv6Preference(wrapper)
                    wrapper.enableNetwork(true)
                    applyMobileTorConfig()
                    Log.i(TAG, "🔌 Tor networking enabled")
                    delay(3000) // Wait for circuits to rebuild
                    republishHiddenServiceWithRetry()
                } else {
                    wrapper.enableNetwork(false)
                    Log.i(TAG, "🔌 Tor networking disabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle network: ${e.message}")
            }
        }
    }

    /**
     * Forces C-Tor to tear down stalled OR sockets after a Wi-Fi <-> Cellular interface
     * handover by bouncing DisableNetwork cleanly (since AbstractTorWrapper.enableNetwork(true)
     * is a no-op when already true), re-applying dual-stack/mobile SETCONF tuning, and signaling NEWNYM.
     */
    suspend fun resetNetworkForHandover() = withContext(Dispatchers.IO) {
        val wrapper = torWrapper ?: return@withContext
        if (!wrapper.isTorRunning) return@withContext
        handoverMutex.withLock {
            try {
                Log.i(TAG, "🔄 Resetting Tor circuits for network handover")
                runCatching { wrapper.enableNetwork(false) }
                delay(250)
                applyIpv6Preference(wrapper)
                wrapper.enableNetwork(true)
                applyMobileTorConfig()
                sendControlCommands(listOf("SIGNAL NEWNYM"))
                uploadedOnions.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Handover Tor reset failed: ${e.message}")
            }
        }
        delay(2000)
        republishHiddenServiceWithRetry()
    }
    
    /**
     * Republish hidden service with retry logic.
     * Called after Doze exit or network change to ensure we're reachable.
     */
    suspend fun republishHiddenServiceWithRetry(maxAttempts: Int = 3): Boolean {
        if (!isRunning.get() || _status.value.state != DaemonState.CONNECTED) return false

        val alreadyUploaded = publishMutex.withLock {
            val existing = hiddenServiceProps
            if (existing != null && existing.onion.isNotBlank() && torWrapper?.isTorRunning == true) {
                val normalized = normalizeOnionKey(existing.onion)
                if (uploadedOnions.contains(normalized)) {
                    Log.d(TAG, "✅ Hidden service ${existing.onion.take(16)}...onion descriptor already uploaded — skipping republish")
                    true
                } else {
                    Log.i(TAG, "♻️ Removing un-uploaded hidden service ${existing.onion.take(16)}...onion before republish")
                    uploadedOnions.remove(normalized)
                    runCatching { torWrapper?.removeHiddenService(existing.onion) }
                    hiddenServiceProps = null
                    false
                }
            } else {
                false
            }
        }
        if (alreadyUploaded) return true

        for (attempt in 1..maxAttempts) {
            val onion = try {
                publishHiddenServiceInternal()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to republish hidden service (attempt $attempt/$maxAttempts): ${e.message}")
                null
            }

            if (!onion.isNullOrBlank()) {
                Log.i(TAG, "✅ Hidden service republished (attempt $attempt)")
                return true
            }

            if (attempt < maxAttempts) {
                delay(5000L * attempt)
            }
        }
        val message = "Failed to republish hidden service after $maxAttempts attempts"
        Log.e(TAG, "❌ $message")
        _status.value = _status.value.copy(state = DaemonState.ERROR, error = message)
        return false
    }

    private fun saveMailboxPrivateKey(privateKey: String) {
        try {
            SecureIdentityStateManager(application)
                .storeSecureValue("tor_mailbox_hs_private_key", privateKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save mailbox hidden-service key: ${e.message}")
        }
    }

    private fun loadMailboxPrivateKey() {
        try {
            mailboxServicePrivateKey = SecureIdentityStateManager(application)
                .getSecureValue("tor_mailbox_hs_private_key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mailbox hidden-service key: ${e.message}")
        }
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        stopHealthMonitoring()
        instance = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                stop()
            } finally {
                scope.cancel()
            }
        }
    }
    
    // ===== Control Port Tuning & Dual-Stack Configuration =====

    private fun readControlReply(reader: BufferedReader): Pair<Boolean, List<String>> {
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: return false to lines
            lines.add(line)
            if (line.length >= 4 && line[3] == ' ') {
                return line.startsWith("250 ") to lines
            }
            if (line.length >= 3 && (line[0] == '4' || line[0] == '5') && (line.length == 3 || line[3] == ' ')) {
                return false to lines
            }
        }
    }

    private fun hexEncode(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hexChars[v ushr 4]
            out[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(out)
    }

    private fun findControlCookieFile(protocolInfoLines: List<String>): File? {
        val cookieMatch = Regex("""COOKIEFILE="([^"]+)"""").find(protocolInfoLines.joinToString("\n"))
        val candidates = buildList {
            cookieMatch?.groupValues?.getOrNull(1)?.let { rawPath ->
                add(File(rawPath.replace("\\\\", "\\").replace("\\\"", "\"")))
            }
            add(File(torDirectory, ".tor/control_auth_cookie"))
            add(File(torDirectory, "control_auth_cookie"))
            add(File(torDirectory, "control.authcookie"))
        }
        return candidates.firstOrNull { it.exists() && it.canRead() && it.length() > 0L }
    }

    private fun sendControlCommands(commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", CONTROL_PORT), 1500)
                socket.soTimeout = 1500
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)

                writer.write("PROTOCOLINFO 1\r\n")
                writer.flush()
                val (infoOk, infoLines) = readControlReply(reader)
                val infoJoined = infoLines.joinToString(" ")
                val cookieFile = findControlCookieFile(infoLines)
                val authLine = when {
                    cookieFile != null -> "AUTHENTICATE ${hexEncode(cookieFile.readBytes())}\r\n"
                    infoOk && infoJoined.contains("NULL") -> "AUTHENTICATE\r\n"
                    else -> "AUTHENTICATE\r\n"
                }
                writer.write(authLine)
                writer.flush()
                val (authOk, authLines) = readControlReply(reader)
                if (!authOk) {
                    Log.w(TAG, "Tor control auth failed: ${authLines.lastOrNull()}")
                    return false
                }

                var allOk = true
                for (cmd in commands) {
                    writer.write("$cmd\r\n")
                    writer.flush()
                    val (cmdOk, cmdLines) = readControlReply(reader)
                    if (!cmdOk) {
                        allOk = false
                        Log.w(TAG, "Tor control '$cmd' returned: ${cmdLines.lastOrNull()}")
                    }
                }
                runCatching {
                    writer.write("QUIT\r\n")
                    writer.flush()
                }
                allOk
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tor control command failed: ${e.message}")
            false
        }
    }

    /**
     * Re-applies mobile-tuned C-Tor parameters over the authenticated ControlPort
     * because onionwrapper's wrapper.start() overwrites torrc on disk.
     */
    private fun applyMobileTorConfig() {
        val ok = sendControlCommands(
            listOf(
                "SETCONF KeepalivePeriod=30 NewCircuitPeriod=30 MaxCircuitDirtiness=600 LearnCircuitBuildTimeout=1 NumEntryGuards=2 SocksTimeout=45 CircuitStreamTimeout=20 ConnectionPadding=auto UseMicrodescriptors=1"
            )
        )
        runCatching {
            sendControlCommands(
                listOf("SETCONF HiddenServicePoWDefensesEnabled=1 HiddenServiceNumIntroductionPoints=5")
            )
        }
        runCatching {
            sendControlCommands(
                listOf("SETCONF ConfluxEnabled=1 ConfluxClientUX=high_throughput")
            )
        }
        if (ok) {
            Log.i(TAG, "⚙️ Applied mobile C-Tor 0.4.9 + Conflux tuning via batched ControlPort SETCONF")
        }
    }

    /**
     * Proactively trigger v3 hidden service descriptor fetches (`HSFETCH`) for active
     * contacts and mailbox endpoints right after Tor connects or recovers from a handover,
     * cutting first-message latency on cold starts.
     */
    suspend fun prefetchOnionDescriptors(endpoints: Collection<String>): Int = withContext(Dispatchers.IO) {
        if (_status.value.state != DaemonState.CONNECTED) return@withContext 0
        val onionIds = endpoints
            .asSequence()
            .mapNotNull { extractV3OnionId(it) }
            .distinct()
            .take(24)
            .toList()
        if (onionIds.isEmpty()) return@withContext 0
        val ok = sendControlCommands(onionIds.map { "HSFETCH $it" })
        if (ok) {
            Log.i(TAG, "🔍 Prefetched ${onionIds.size} v3 onion descriptor(s) via HSFETCH")
            onionIds.size
        } else {
            0
        }
    }

    /**
     * Applies true dual-stack (ClientUseIPv4=1 ClientUseIPv6=1), IPv6-only, or IPv4-only routing.
     * Note: onionwrapper's enableIpv6(true) sets ClientUseIPv4=0, which breaks dual-stack phones
     * that lack global IPv6 reachability; calling SETCONF directly preserves IPv4 + IPv6 together.
     */
    private fun applyIpv6Preference(wrapper: TorWrapper) {
        val (hasV4, hasV6) = detectNetworkAddressAvailability()
        try {
            when {
                !hasV4 && hasV6 -> {
                    wrapper.enableIpv6(true)
                    sendControlCommands(listOf("SETCONF ClientUseIPv4=0 ClientUseIPv6=1"))
                    Log.i(TAG, "IPv6-only mode enabled (ClientUseIPv4=0 ClientUseIPv6=1)")
                }
                hasV4 && hasV6 -> {
                    wrapper.enableIpv6(false)
                    sendControlCommands(listOf("SETCONF ClientUseIPv4=1 ClientUseIPv6=1"))
                    Log.i(TAG, "Dual-stack mode enabled (ClientUseIPv4=1 ClientUseIPv6=1)")
                }
                else -> {
                    wrapper.enableIpv6(false)
                    sendControlCommands(listOf("SETCONF ClientUseIPv4=1 ClientUseIPv6=0"))
                    Log.i(TAG, "IPv4-only mode enabled (ClientUseIPv4=1 ClientUseIPv6=0)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply IPv6 preference: ${e.message}")
        }
    }

    private fun detectNetworkAddressAvailability(): Pair<Boolean, Boolean> {
        return try {
            val cm = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return true to false
            val network = cm.activeNetwork ?: return true to false
            val props = cm.getLinkProperties(network) ?: return true to false
            val hasGlobalV6 = props.linkAddresses.any { la ->
                val addr = la.address
                addr is java.net.Inet6Address && !addr.isLinkLocalAddress &&
                    !addr.isSiteLocalAddress && !addr.isLoopbackAddress &&
                    (addr.address[0].toInt() and 0xFE) != 0xFC // exclude ULA fc00::/7
            }
            val hasV6Default = props.routes.any { route ->
                route.destination.address is java.net.Inet6Address &&
                    route.destination.prefixLength == 0
            }
            val hasV4 = props.linkAddresses.any { it.address is java.net.Inet4Address }
            val hasUsableV6 = (hasGlobalV6 && hasV6Default) || !hasV4
            hasV4 to hasUsableV6
        } catch (e: Exception) {
            true to false
        }
    }
    
    /**
     * Write bridge/PT configuration to daemon's torrc before starting.
     * The C-Tor binary in onionwrapper reads torrc from its data directory.
     */
    private fun configureBridges() {
        try {
            val ptManager = PluggableTransportManager.getInstance(application)
            val bridgeLines = ptManager.generateBridgeConfig().trim()

            // Always apply mobile-tuned low-latency torrc settings so C-Tor keeps
            // guards and hidden-service rendezvous circuits warm across carrier/NAT
            // transitions, and append pluggable transport bridges when enabled.
            val baseTorrc = buildString {
                appendLine("ClientOnly 1")
                appendLine("KeepalivePeriod 30")
                appendLine("NewCircuitPeriod 15")
                appendLine("MaxCircuitDirtiness 600")
                appendLine("CircuitBuildTimeout 25")
                appendLine("LearnCircuitBuildTimeout 1")
                appendLine("NumEntryGuards 2")
                appendLine("SocksTimeout 30")
                appendLine("CircuitStreamTimeout 20")
                appendLine("ConfluxEnabled 1")
                appendLine("ConfluxClientUX high_throughput")
                if (bridgeLines.isNotEmpty()) {
                    appendLine(bridgeLines)
                }
            }

            val torrcFile = File(torDirectory, "torrc")
            torrcFile.parentFile?.mkdirs()
            torrcFile.writeText(baseTorrc)
            Log.i(TAG, "⚙️ Wrote mobile-tuned torrc (bridges=${bridgeLines.isNotEmpty()})")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to configure bridges (proceeding without): ${e.message}")
        }
    }
    
    // ===== Health Monitoring =====
    
    private var healthJob: Job? = null
    
    /**
     * Periodically verify that our active hidden service descriptor has uploaded.
     * Once confirmed via onHsDescriptorUpload(), C-Tor refreshes descriptors automatically;
     * do NOT clear the confirmation map every 10 minutes (which triggered destructive DEL_ONION).
     */
    private fun startHealthMonitoring() {
        healthJob?.cancel()
        
        healthJob = scope.launch {
            // Wait 2 minutes for initial descriptor upload
            delay(120_000)
            
            while (isActive && isRunning.get()) {
                val currentOnion = hiddenServiceProps?.onion?.let(::normalizeOnionKey)
                val isUploaded = !currentOnion.isNullOrEmpty() && uploadedOnions.contains(currentOnion)
                if (!isUploaded) {
                    Log.w(TAG, "⚠️ HS descriptor not confirmed uploaded — forcing republish")
                    try {
                        republishHiddenServiceWithRetry()
                    } catch (e: Exception) {
                        Log.e(TAG, "Health check republish failed: ${e.message}")
                    }
                }
                delay(600_000)
            }
        }
    }
    
    private fun stopHealthMonitoring() {
        healthJob?.cancel()
        hiddenServicePublishJob?.cancel()
        hiddenServicePublishJob = null
        healthJob = null
    }
}
