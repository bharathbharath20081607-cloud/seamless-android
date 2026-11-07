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
import java.io.File
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
    private val publishMutex = Mutex()
    
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
    
    /**
     * Start the Tor daemon
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            Log.d(TAG, "Tor daemon already running")
            return@withContext true
        }
        
        try {
            _status.value = DaemonStatus(state = DaemonState.STARTING)
            
            // Ensure directory exists
            torDirectory.mkdirs()
            
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
            // If PluggableTransportManager detected censorship and has bridge config,
            // write it to the daemon's torrc so the C-Tor binary uses bridges for outbound.
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
                    hsDescriptorUploaded = true
                }
                
                override fun onClockSkewDetected(skewSeconds: Long) {
                    Log.w(TAG, "Clock skew detected: ${skewSeconds}s")
                }
            })
            
            // Store wrapper before start() because Tor can emit CONNECTED from
            // callbacks before start()/enableNetwork() returns.
            torWrapper = wrapper
            isRunning.set(true)

            // Start Tor
            wrapper.start()
            applyIpv6Preference(wrapper)
            
            // Enable networking
            wrapper.enableNetwork(true)
            
            Log.i(TAG, "🚀 Tor daemon started successfully")
            true
            
        } catch (e: Exception) {
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
            if (!waitForDaemonSocks()) {
                val message = "Tor SOCKS listener was not ready for hidden-service publishing"
                Log.w(TAG, message)
                _status.value = _status.value.copy(error = message)
                return@withContext null
            }

            Log.i(TAG, "📡 Publishing hidden service on port $localPort")
            
            // Publish the hidden service
            // virtualPort MUST match the port peers connect to (HIDDEN_SERVICE_PORT = 18888)
            // Previously was 80, which caused ALL connections to fail because peers connect to :18888
            val props = publishWithKeyRecovery(wrapper, localPort, localPort)
            
            hiddenServiceProps = props
            
            // Save private key for persistence
            if (hiddenServicePrivateKey == null) {
                hiddenServicePrivateKey = props.privKey
                savePrivateKey(props.privKey)
            }
            
            val onionAddress = props.onion
            _status.value = _status.value.copy(
                state = DaemonState.CONNECTED,
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

        val message = "Hidden service publish failed after $maxAttempts attempts"
        Log.e(TAG, message)
        _status.value = _status.value.copy(state = DaemonState.ERROR, error = message)
        return null
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
            if (savedKey.isNullOrBlank()) throw e

            Log.w(TAG, "Saved hidden service key failed; rotating onion identity once: ${e.message}")
            hiddenServicePrivateKey = null
            runCatching {
                SecureIdentityStateManager(application).clearSecureValues("tor_hs_private_key")
            }
            wrapper.publishHiddenService(localPort, virtualPort, null)
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
            if (savedKey.isNullOrBlank()) throw e

            Log.w(TAG, "Saved mailbox hidden-service key failed; rotating mailbox identity once: ${e.message}")
            mailboxServicePrivateKey = null
            runCatching {
                SecureIdentityStateManager(application).clearSecureValues("tor_mailbox_hs_private_key")
            }
            wrapper.publishHiddenService(localPort, virtualPort, null)
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
        if (!isRunning.get()) return@withContext
        
        try {
            Log.i(TAG, "Stopping Tor daemon...")
            _status.value = _status.value.copy(state = DaemonState.STOPPING)
            
            // Remove hidden service first
            hiddenServicePublishJob?.cancel()
            hiddenServicePublishJob = null

            hiddenServiceProps?.let { props ->
                try {
                    torWrapper?.removeHiddenService(props.onion)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing hidden service: ${e.message}")
                }
            }
            mailboxServiceProps?.let { props ->
                try {
                    torWrapper?.removeHiddenService(props.onion)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing mailbox hidden service: ${e.message}")
                }
            }
            
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
        try {
            val wrapper = torWrapper
            if (wrapper != null && wrapper.isTorRunning) {
                applyIpv6Preference(wrapper)
                wrapper.enableNetwork(enable)
                Log.i(TAG, "🔌 Tor networking ${if (enable) "enabled" else "disabled"}")
                
                // If re-enabling, force republish hidden service
                if (enable) {
                    scope.launch {
                        delay(3000) // Wait for circuits to rebuild
                        republishHiddenServiceWithRetry()
                    }
                }
            } else {
                Log.d(TAG, "Cannot toggle network — Tor not running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle network: ${e.message}")
        }
    }
    
    /**
     * Republish hidden service with retry logic.
     * Called after Doze exit or network change to ensure we're reachable.
     */
    suspend fun republishHiddenServiceWithRetry(maxAttempts: Int = 3): Boolean {
        if (!isRunning.get()) return false
        
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
        scope.launch { stop() }
        stopHealthMonitoring()
        scope.cancel()
        instance = null
    }
    
    // ===== Bridge Configuration =====

    /**
     * Enables IPv6 guard connections only when the active network has a real
     * global IPv6 default route. Many mobile carriers and home routers hand out
     * IPv6 addresses with broken routing, which makes Tor loop on
     * "No route to host (os error 113)" and stay at 0% bootstrap.
     */
    private fun applyIpv6Preference(wrapper: TorWrapper) {
        val hasIpv6 = hasUsableIpv6Route()
        try {
            wrapper.enableIpv6(hasIpv6)
            Log.i(TAG, "IPv6 guard connections ${if (hasIpv6) "enabled" else "disabled (IPv4 only)"}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply IPv6 preference: ${e.message}")
        }
    }

    private fun hasUsableIpv6Route(): Boolean {
        return try {
            val cm = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val props = cm.getLinkProperties(network) ?: return false
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
            // IPv6-only networks (NAT64/464XLAT) must keep IPv6 on.
            (hasGlobalV6 && hasV6Default) || !hasV4
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Write bridge/PT configuration to daemon's torrc before starting.
     * The C-Tor binary in onionwrapper reads torrc from its data directory.
     */
    private fun configureBridges() {
        try {
            val ptManager = PluggableTransportManager.getInstance(application)
            val bridgeConfig = ptManager.generateBridgeConfig()
            
            if (bridgeConfig.isEmpty()) {
                // No bridges needed — remove any stale torrc override
                val torrcFile = File(torDirectory, "torrc")
                if (torrcFile.exists()) {
                    torrcFile.delete()
                    Log.d(TAG, "Removed stale bridge torrc")
                }
                return
            }
            
            // Write bridge config to torrc in the daemon directory
            val torrcFile = File(torDirectory, "torrc")
            torrcFile.writeText(bridgeConfig)
            Log.i(TAG, "🌉 Bridge config written to torrc (${bridgeConfig.lines().size} lines)")
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to configure bridges (proceeding without): ${e.message}")
        }
    }
    
    // ===== Health Monitoring =====
    
    @Volatile
    private var hsDescriptorUploaded = false
    private var healthJob: Job? = null
    
    /**
     * Periodically check that our hidden service descriptor is uploaded.
     * If it hasn't been uploaded within 2 minutes of CONNECTED state,
     * force a republish.
     */
    private fun startHealthMonitoring() {
        healthJob?.cancel()
        hsDescriptorUploaded = false
        
        healthJob = scope.launch {
            // Wait 2 minutes for initial descriptor upload
            delay(120_000)
            
            while (isActive && isRunning.get()) {
                if (!hsDescriptorUploaded) {
                    Log.w(TAG, "⚠️ HS descriptor not confirmed uploaded — forcing republish")
                    try {
                        republishHiddenServiceWithRetry()
                    } catch (e: Exception) {
                        Log.e(TAG, "Health check republish failed: ${e.message}")
                    }
                }
                
                // Reset flag and check again in 10 minutes
                hsDescriptorUploaded = false
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
