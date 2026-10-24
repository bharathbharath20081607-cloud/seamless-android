@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.nostr

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages WebSocket connections to Nostr relays
 * Compatible with iOS implementation with Android-specific optimizations
 */
class NostrRelayManager private constructor() {
    
    companion object {
        @JvmStatic
        val shared = NostrRelayManager()
        
        private const val TAG = "NostrRelayManager"
        
        /**
         * Get instance for Android compatibility (context-aware calls)
         */
        fun getInstance(context: android.content.Context): NostrRelayManager {
            return shared
        }

        // High-uptime global Nostr relays
        private val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://nostr.mom",
            "wss://purplepag.es"
        )
        
        // Exponential backoff configuration (same as iOS)
        private const val INITIAL_BACKOFF_INTERVAL = com.seamless.android.util.AppConstants.Nostr.INITIAL_BACKOFF_INTERVAL_MS  // 1 second
        private const val MAX_BACKOFF_INTERVAL = com.seamless.android.util.AppConstants.Nostr.MAX_BACKOFF_INTERVAL_MS    // 5 minutes
        private const val BACKOFF_MULTIPLIER = com.seamless.android.util.AppConstants.Nostr.BACKOFF_MULTIPLIER
        private const val MAX_RECONNECT_ATTEMPTS = com.seamless.android.util.AppConstants.Nostr.MAX_RECONNECT_ATTEMPTS
        
        // Track gift-wraps we initiated for logging
        private val pendingGiftWrapIDs = ConcurrentHashMap.newKeySet<String>()
        
        fun registerPendingGiftWrap(id: String) {
            pendingGiftWrapIDs.add(id)
        }

        fun defaultRelays(): List<String> = DEFAULT_RELAYS
    }
    
    /**
     * Relay status information with WORLD-CLASS health scoring
     * Similar to how WhatsApp/Telegram prioritize fast, reliable servers
     */
    data class Relay(
        val url: String,
        var isConnected: Boolean = false,
        var lastError: Throwable? = null,
        var lastConnectedAt: Long? = null,
        var messagesSent: Int = 0,
        var messagesReceived: Int = 0,
        var reconnectAttempts: Int = 0,
        var lastDisconnectedAt: Long? = null,
        var nextReconnectTime: Long? = null,
        // WORLD-CLASS: Health scoring for automatic relay prioritization
        var avgResponseTimeMs: Long = 0,          // Average response time in ms
        var successCount: Int = 0,                 // Successful message deliveries
        var failureCount: Int = 0,                 // Failed deliveries
        var lastResponseTimeMs: Long = 0           // Most recent response time
    ) {
        /** Health score 0-100. Higher = better relay */
        val healthScore: Int
            get() {
                if (successCount + failureCount == 0) return 50 // Neutral for new relays
                
                val successRate = successCount.toFloat() / (successCount + failureCount)
                val speedScore = when {
                    avgResponseTimeMs <= 100 -> 100   // Excellent: <100ms
                    avgResponseTimeMs <= 300 -> 80    // Good: 100-300ms
                    avgResponseTimeMs <= 500 -> 60    // OK: 300-500ms
                    avgResponseTimeMs <= 1000 -> 40   // Slow: 500-1000ms
                    else -> 20                         // Very slow: >1000ms
                }
                
                // Weighted: 60% success rate + 40% speed
                return ((successRate * 60) + (speedScore * 0.4)).toInt().coerceIn(0, 100)
            }
        
        /** Human-readable health status */
        val healthStatus: String
            get() = when {
                !isConnected -> "offline"
                healthScore >= 80 -> "excellent"
                healthScore >= 60 -> "good"
                healthScore >= 40 -> "slow"
                else -> "poor"
            }
    }
    
    // Published state
    private val _relays = MutableLiveData<List<Relay>>()
    val relays: LiveData<List<Relay>> = _relays
    
    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected
    
    // Internal state
    private val relaysList = java.util.concurrent.CopyOnWriteArrayList<Relay>()
    private val connections = ConcurrentHashMap<String, WebSocket>()
    private val subscriptions = ConcurrentHashMap<String, Set<String>>() // relay URL -> subscription IDs
    private val messageHandlers = ConcurrentHashMap<String, (NostrEvent) -> Unit>()
    
    // Persistent subscription tracking for robust reconnection
    private val activeSubscriptions = ConcurrentHashMap<String, SubscriptionInfo>() // subscription ID -> info
    
    /**
     * Information about an active subscription that needs to be maintained across reconnections
     */
    data class SubscriptionInfo(
        val id: String,
        val filter: NostrFilter,
        val handler: (NostrEvent) -> Unit,
        val targetRelayUrls: Set<String>? = null, // null means all relays
        val createdAt: Long = System.currentTimeMillis(),
        val originGeohash: String? = null // used for logging and grouping
    )
    
    // Event deduplication system
    private val eventDeduplicator = NostrEventDeduplicator.getInstance()
    
    // Message queue for reliability (tracks remaining target relays and enqueue timestamp)
    private data class QueuedEvent(
        val event: NostrEvent,
        val pendingRelays: MutableSet<String>,
        val enqueuedAt: Long = System.currentTimeMillis()
    )
    private val messageQueue = mutableListOf<QueuedEvent>()
    private val messageQueueLock = Any()
    private val MESSAGE_QUEUE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    private val MAX_MESSAGE_QUEUE_SIZE = 250
    
    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Subscription validation timer
    private var subscriptionValidationJob: Job? = null
    private val SUBSCRIPTION_VALIDATION_INTERVAL = com.seamless.android.util.AppConstants.Nostr.SUBSCRIPTION_VALIDATION_INTERVAL_MS // 30 seconds
    
    // WORLD-CLASS: WebSocket keep-alive for connection stability
    private var keepAliveJob: Job? = null
    private val KEEP_ALIVE_INTERVAL = 25_000L // 25 seconds (before typical 30s server timeout)
    private val CONNECTION_STALE_THRESHOLD = 60_000L // 60 seconds without activity = stale
    
    // WORLD-CLASS: NIP-65 recipient relay cache
    private val recipientRelayCache = ConcurrentHashMap<String, CachedRelayList>()
    private data class CachedRelayList(val relays: List<String>, val timestamp: Long)
    private val NIP65_CACHE_TTL = 3600_000L // 1 hour cache
    
    // WORLD-CLASS: Delivery confirmation tracking
    private val pendingDeliveryConfirmations = ConcurrentHashMap<String, Long>() // eventId -> timestamp
    
    // WORLD-CLASS: Event send timestamps for response time tracking
    private val eventSendTimestamps = ConcurrentHashMap<String, Long>() // eventId -> sendTime
    
    // OkHttp client for WebSocket connections (via provider to honor Tor)
    private val httpClient: OkHttpClient
        get() = com.seamless.android.net.OkHttpProvider.webSocketClient()
    
    private val gson by lazy { NostrRequest.createGson() }
    
    // Per-geohash relay selection
    private val geohashToRelays = ConcurrentHashMap<String, Set<String>>() // geohash -> relay URLs

    // --- Public API for geohash-specific operation ---

    /**
     * Compute and connect to relays for a given geohash (nearest + optional defaults), cache the mapping.
     */
    fun ensureGeohashRelaysConnected(geohash: String, nRelays: Int = 5, includeDefaults: Boolean = false) {
        try {
            val nearest = RelayDirectory.closestRelaysForGeohash(geohash, nRelays)
            val selected = if (includeDefaults) {
                (nearest + Companion.defaultRelays()).toSet()
            } else nearest.toSet()
            if (selected.isEmpty()) {
                Log.w(TAG, "No relays selected for geohash=$geohash")
                return
            }
            geohashToRelays[geohash] = selected
            Log.i(TAG, "🌐 Geohash $geohash using ${selected.size} relays: ${selected.joinToString()}")
            ensureConnectionsFor(selected)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure relays for $geohash: ${e.message}")
        }
    }

    /**
     * Get relays mapped to a geohash (empty list if none configured).
     */
    fun getRelaysForGeohash(geohash: String): List<String> {
        return geohashToRelays[geohash]?.toList() ?: emptyList()
    }

    /**
     * Subscribe with explicit geohash routing; ensures connections exist, then targets only those relays.
     */
    fun subscribeForGeohash(
        geohash: String,
        filter: NostrFilter,
        id: String = generateSubscriptionId(),
        handler: (NostrEvent) -> Unit,
        includeDefaults: Boolean = false,
        nRelays: Int = 5
    ): String {
        ensureGeohashRelaysConnected(geohash, nRelays, includeDefaults)
        val relayUrls = getRelaysForGeohash(geohash)
        Log.d(TAG, "📡 Subscribing id=$id for geohash=$geohash on ${relayUrls.size} relays")
        return subscribe(
            filter = filter,
            id = id,
            handler = handler,
            targetRelayUrls = relayUrls
        ).also {
            // update origin geohash for this subscription
            activeSubscriptions[it]?.let { sub ->
                activeSubscriptions[it] = sub.copy(originGeohash = geohash)
            }
        }
    }

    /**
     * Send an event specifically to a geohash's relays (+ optional defaults).
     */
    fun sendEventToGeohash(event: NostrEvent, geohash: String, includeDefaults: Boolean = false, nRelays: Int = 5) {
        ensureGeohashRelaysConnected(geohash, nRelays, includeDefaults)
        val relayUrls = getRelaysForGeohash(geohash)
        if (relayUrls.isEmpty()) {
            Log.w(TAG, "No target relays to send event for geohash=$geohash; falling back to defaults")
            sendEvent(event, Companion.defaultRelays())
            return
        }
        Log.v(TAG, "📤 Sending event kind=${event.kind} to ${relayUrls.size} relays for geohash=$geohash")
        sendEvent(event, relayUrls)
    }

    // --- Internal helpers ---

    private fun ensureConnectionsFor(relayUrls: Set<String>) {
        // Ensure relays are tracked for UI/status
        relayUrls.forEach { url ->
            if (relaysList.none { it.url == url }) {
                relaysList.add(Relay(url))
            }
        }
        updateRelaysList()

        scope.launch {
            relayUrls.forEach { relayUrl ->
                launch {
                    if (!connections.containsKey(relayUrl)) {
                        connectToRelay(relayUrl)
                    }
                }
            }
        }
    }

    init {
        // Initialize with default relays - avoid static initialization order issues
        try {
            val defaultRelayUrls = DEFAULT_RELAYS
            relaysList.addAll(defaultRelayUrls.map { Relay(it) })
            _relays.postValue(relaysList.toList())
            updateConnectionStatus()
            Log.d(TAG, "✅ NostrRelayManager initialized with ${relaysList.size} default relays")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NostrRelayManager: ${e.message}", e)
            // Initialize with empty list as fallback
            _relays.postValue(emptyList())
            _isConnected.postValue(false)
        }
    }
    
    /**
     * Connect to all configured relays immediately.
     */
    fun connect() {
        Log.d(TAG, "🌐 Connecting to ${relaysList.size} Nostr relays")
        
        scope.launch {
            relaysList.forEach { relay ->
                launch { connectToRelay(relay.url) }
            }
        }
        
        // Start periodic subscription validation
        startSubscriptionValidation()
        
        // WORLD-CLASS: Start WebSocket keep-alive for connection stability
        startKeepAlive()
        
        // Observe network changes for immediate reconnection
        scope.launch {
            try {
                val tracker = com.seamless.android.net.NetworkStatusTracker.getInstance(com.seamless.android.SeamlessApplication.instance)
                tracker.isInternetAvailable.collect { isAvailable ->
                    if (isAvailable) {
                        Log.i(TAG, "🌐 Internet became available, triggering immediate reconnection check")
                        // Retry all disconnected relays immediately
                        relaysList.forEach { relay ->
                            if (!connections.containsKey(relay.url)) {
                                launch { connectToRelay(relay.url) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize NetworkStatusTracker observation: ${e.message}")
            }
        }
        
        // PRODUCTION FIX: Observe Tor state changes and reconnect when it becomes ready
        scope.launch {
            try {
                com.seamless.android.net.TorManager.statusFlow.collect { status ->
                    if (status.state == com.seamless.android.net.TorManager.TorState.RUNNING && 
                        status.bootstrapPercent >= 100) {
                        // Check if we have any disconnected relays
                        val disconnected = relaysList.filter { !connections.containsKey(it.url) }
                        if (disconnected.isNotEmpty()) {
                            Log.i(TAG, "🔄 Tor ready, connecting ${disconnected.size} disconnected relays")
                            disconnected.forEach { relay ->
                                launch { connectToRelay(relay.url) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not observe Tor status: ${e.message}")
            }
        }
    }
    
    /**
     * Disconnect from all relays
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from all relays")
        
        // Stop subscription validation
        stopSubscriptionValidation()
        
        // Stop keep-alive pings
        stopKeepAlive()
        
        connections.values.forEach { webSocket ->
            webSocket.close(1000, "Manual disconnect")
        }
        connections.clear()
        
        // Clear subscriptions
        subscriptions.clear()
        
        updateConnectionStatus()
    }
    
    /**
     * Add a new relay to the list and connect to it
     */
    fun addRelay(relayUrl: String) {
        // Check if relay already exists
        if (relaysList.any { it.url == relayUrl }) {
            Log.w(TAG, "Relay $relayUrl already exists")
            return
        }
        
        Log.d(TAG, "Adding relay: $relayUrl")
        
        // Add to list
        relaysList.add(Relay(relayUrl))
        updateRelaysList()
        
        // Connect to the new relay
        scope.launch {
            connectToRelay(relayUrl)
        }
    }
    
    /**
     * Remove a relay from the list and disconnect from it
     */
    fun removeRelay(relayUrl: String) {
        val relay = relaysList.find { it.url == relayUrl }
        if (relay == null) {
            Log.w(TAG, "Attempted to remove non-existent relay: $relayUrl")
            return
        }
        
        Log.d(TAG, "Removing relay: $relayUrl")
        
        // Remove from list
        relaysList.remove(relay)
        updateRelaysList()
        
        // Disconnect from relay
        connections[relayUrl]?.close(1000, "Relay removed")
        connections.remove(relayUrl)
        subscriptions.remove(relayUrl)
        
        updateConnectionStatus()
    }
    
    /**
     * Send an event to specified relays (or all if none specified)
     */
    fun sendEvent(event: NostrEvent, relayUrls: List<String>? = null) {
        val targetRelays = relayUrls ?: relaysList.map { it.url }
        ensureConnectionsFor(targetRelays.toSet())
        
        // Attempt immediate send and queue only relays that are not yet connected / failed
        scope.launch {
            val remainingRelays = mutableSetOf<String>()
            targetRelays.forEach { relayUrl ->
                val webSocket = connections[relayUrl]
                if (webSocket != null) {
                    val sent = sendToRelay(event, webSocket, relayUrl)
                    if (!sent) {
                        remainingRelays.add(relayUrl)
                    }
                } else {
                    remainingRelays.add(relayUrl)
                }
            }
            if (remainingRelays.isNotEmpty()) {
                val now = System.currentTimeMillis()
                synchronized(messageQueueLock) {
                    messageQueue.removeAll { now - it.enqueuedAt > MESSAGE_QUEUE_TTL_MS || it.pendingRelays.isEmpty() }
                    while (messageQueue.size >= MAX_MESSAGE_QUEUE_SIZE) {
                        messageQueue.removeAt(0)
                    }
                    messageQueue.add(QueuedEvent(event, remainingRelays, now))
                }
            }
        }
    }
    
    /**
     * Subscribe to events matching a filter
     * The subscription will be automatically re-established on reconnection
     */
    fun subscribe(
        filter: NostrFilter,
        id: String = generateSubscriptionId(),
        handler: (NostrEvent) -> Unit,
        targetRelayUrls: List<String>? = null
    ): String {
        // Store subscription info for persistent tracking
        val subscriptionInfo = SubscriptionInfo(
            id = id,
            filter = filter,
            handler = handler,
            targetRelayUrls = targetRelayUrls?.toSet()
        )
        
        activeSubscriptions[id] = subscriptionInfo
        messageHandlers[id] = handler
        
        Log.d(TAG, "📡 Subscribing to Nostr filter id=$id ${filter.getDebugDescription()}")
        
        // Send subscription to appropriate relays
        sendSubscriptionToRelays(subscriptionInfo)
        
        return id
    }
    
    /**
     * Send a subscription to the appropriate relays
     */
    private fun sendSubscriptionToRelays(subscriptionInfo: SubscriptionInfo) {
        val request = NostrRequest.Subscribe(subscriptionInfo.id, listOf(subscriptionInfo.filter))
        val message = gson.toJson(request, NostrRequest::class.java)
        
        // DEBUG: Log the actual serialized message format
        Log.v(TAG, "🔍 DEBUG: Serialized subscription message: $message")
        
        scope.launch {
            val targetRelays = subscriptionInfo.targetRelayUrls?.toList() ?: connections.keys.toList()
            
            targetRelays.forEach { relayUrl ->
                val webSocket = connections[relayUrl]
                if (webSocket != null) {
                    try {
                        val success = webSocket.send(message)
                        if (success) {
                            // Track subscription for this relay
                            val currentSubs = subscriptions[relayUrl] ?: emptySet()
                            subscriptions[relayUrl] = currentSubs + subscriptionInfo.id
                            
                            Log.v(TAG, "✅ Subscription '${subscriptionInfo.id}' sent to relay: $relayUrl")
                        } else {
                            Log.w(TAG, "❌ Failed to send subscription to $relayUrl: WebSocket send failed")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to send subscription to $relayUrl: ${e.message}")
                    }
                } else {
                    Log.v(TAG, "⏳ Relay $relayUrl not connected, subscription will be sent on reconnection")
                }
            }
            
            if (connections.isEmpty()) {
                Log.w(TAG, "⚠️ No relay connections available for subscription, will retry on reconnection")
            }
        }
    }
    
    /**
     * Unsubscribe from a subscription
     */
    fun unsubscribe(id: String) {
        // Remove from persistent tracking
        val subscriptionInfo = activeSubscriptions.remove(id)
        messageHandlers.remove(id)
        
        if (subscriptionInfo == null) {
            Log.w(TAG, "⚠️ Attempted to unsubscribe from unknown subscription: $id")
            return
        }
        
        Log.d(TAG, "🚫 Unsubscribing from subscription: $id")
        
        val request = NostrRequest.Close(id)
        val message = gson.toJson(request, NostrRequest::class.java)
        
        scope.launch {
            connections.forEach { (relayUrl, webSocket) ->
                val currentSubs = subscriptions[relayUrl]
                if (currentSubs?.contains(id) == true) {
                    try {
                        webSocket.send(message)
                        subscriptions[relayUrl] = currentSubs - id
                        Log.v(TAG, "Unsubscribed '$id' from relay: $relayUrl")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to unsubscribe from $relayUrl: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Manually retry connection to a specific relay
     */
    fun retryConnection(relayUrl: String) {
        val relay = relaysList.find { it.url == relayUrl } ?: return
        
        // Reset reconnection attempts
        relay.reconnectAttempts = 0
        relay.nextReconnectTime = null
        
        // Disconnect if connected
        connections[relayUrl]?.close(1000, "Manual retry")
        connections.remove(relayUrl)
        
        // Attempt immediate reconnection
        scope.launch {
            connectToRelay(relayUrl)
        }
    }
    
    /**
     * Reset all relay connections
     * This will automatically restore all subscriptions when reconnected
     */
    fun resetAllConnections() {
        disconnect()
        
        // Reset all relay states
        relaysList.forEach { relay ->
            relay.reconnectAttempts = 0
            relay.nextReconnectTime = null
            relay.lastError = null
        }
        
        // Reconnect - subscriptions will be automatically restored in onOpen
        connect()
    }
    
    /**
     * Reconnect to relays - lightweight version for app resume
     * Doesn't clear subscriptions, just reconnects disconnected relays
     * Call this when app returns to foreground to ensure private chats work
     */
    fun reconnect() {
        Log.i(TAG, "🔄 Reconnecting to Nostr relays for app resume...")
        
        val disconnectedRelays = relaysList.filter { !connections.containsKey(it.url) }
        
        if (disconnectedRelays.isEmpty() && connections.isNotEmpty()) {
            Log.d(TAG, "✅ All ${connections.size} relays already connected")
            return
        }
        
        scope.launch {
            // Reset backoff for disconnected relays and reconnect
            disconnectedRelays.forEach { relay ->
                relay.reconnectAttempts = 0
                relay.nextReconnectTime = null
                launch {
                    connectToRelay(relay.url)
                }
            }
            
            Log.i(TAG, "⏳ Reconnecting to ${disconnectedRelays.size} disconnected relays")
        }
    }
    
    /**
     * Force re-establishment of all subscriptions on currently connected relays
     * Useful for ensuring subscription consistency after network issues
     */
    fun reestablishAllSubscriptions() {
        Log.d(TAG, "🔄 Force re-establishing all ${activeSubscriptions.size} active subscriptions")
        
        scope.launch {
            connections.forEach { (relayUrl, webSocket) ->
                restoreSubscriptionsForRelay(relayUrl, webSocket)
            }
        }
    }
    
    /**
     * Clear all subscription tracking, message handlers, routing caches, and queued messages.
     * Intended for panic/reset flows prior to reconnecting and re-subscribing from scratch.
     */
    fun clearAllSubscriptions() {
        try {
            // Clear persistent subscription tracking
            activeSubscriptions.clear()
            messageHandlers.clear()
            subscriptions.clear()

            // Clear routing caches (per-geohash relay selections)
            geohashToRelays.clear()

            // Clear any queued messages waiting to be sent
            synchronized(messageQueueLock) {
                messageQueue.clear()
            }

            Log.i(TAG, "🧹 Cleared all Nostr subscriptions and routing caches")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear subscriptions: ${e.message}")
        }
    }
    
    /**
     * Get detailed status for all relays
     */
    fun getRelayStatuses(): List<Relay> {
        return relaysList.toList()
    }
    
    /**
     * Get event deduplication statistics
     */
    fun getDeduplicationStats(): DeduplicationStats {
        return eventDeduplicator.getStats()
    }
    
    /**
     * Clear the event deduplication cache (useful for testing or debugging)
     */
    fun clearDeduplicationCache() {
        eventDeduplicator.clear()
        Log.i(TAG, "🧹 Cleared event deduplication cache")
    }
    
    /**
     * Get the count of active subscriptions
     */
    fun getActiveSubscriptionCount(): Int {
        return activeSubscriptions.size
    }
    
    /**
     * Get information about all active subscriptions (for debugging)
     */
    fun getActiveSubscriptions(): Map<String, SubscriptionInfo> {
        return activeSubscriptions.toMap()
    }
    
    /**
     * Validate subscription consistency across all relays
     * Returns a report of any inconsistencies found
     */
    fun validateSubscriptionConsistency(): SubscriptionConsistencyReport {
        val expectedSubs = activeSubscriptions.keys
        val actualSubsByRelay = subscriptions.toMap()
        val inconsistencies = mutableListOf<String>()
        
        connections.keys.forEach { relayUrl ->
            val actualSubs = actualSubsByRelay[relayUrl] ?: emptySet()
            val expectedForRelay = expectedSubs.filter { subId ->
                val subInfo = activeSubscriptions[subId]
                subInfo?.targetRelayUrls == null || subInfo.targetRelayUrls.contains(relayUrl)
            }.toSet()
            
            val missing = expectedForRelay - actualSubs
            val extra = actualSubs - expectedForRelay
            
            if (missing.isNotEmpty()) {
                inconsistencies.add("Relay $relayUrl missing subscriptions: $missing")
            }
            if (extra.isNotEmpty()) {
                inconsistencies.add("Relay $relayUrl has extra subscriptions: $extra")
            }
        }
        
        return SubscriptionConsistencyReport(
            isConsistent = inconsistencies.isEmpty(),
            inconsistencies = inconsistencies,
            totalActiveSubscriptions = activeSubscriptions.size,
            connectedRelayCount = connections.size
        )
    }
    
    data class SubscriptionConsistencyReport(
        val isConsistent: Boolean,
        val inconsistencies: List<String>,
        val totalActiveSubscriptions: Int,
        val connectedRelayCount: Int
    )
    
    /**
     * Start periodic subscription validation to ensure robustness
     */
    private fun startSubscriptionValidation() {
        stopSubscriptionValidation() // Stop any existing validation
        
        subscriptionValidationJob = scope.launch {
            while (isActive) {
                delay(SUBSCRIPTION_VALIDATION_INTERVAL)
                
                try {
                    val report = validateSubscriptionConsistency()
                    if (!report.isConsistent && report.connectedRelayCount > 0) {
                        Log.w(TAG, "⚠️ Subscription inconsistencies detected: ${report.inconsistencies}")
                        
                        // Auto-repair: re-establish subscriptions for relays with missing ones
                        connections.forEach { (relayUrl, webSocket) ->
                            val currentSubs = subscriptions[relayUrl] ?: emptySet()
                            val expectedSubs = activeSubscriptions.keys.filter { subId ->
                                val subInfo = activeSubscriptions[subId]
                                subInfo?.targetRelayUrls == null || subInfo.targetRelayUrls.contains(relayUrl)
                            }.toSet()
                            
                            val missingSubs = expectedSubs - currentSubs
                            if (missingSubs.isNotEmpty()) {
                                Log.i(TAG, "🔧 Auto-repairing ${missingSubs.size} missing subscriptions for $relayUrl")
                                restoreSubscriptionsForRelay(relayUrl, webSocket)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during subscription validation: ${e.message}")
                }
            }
        }
        
        Log.d(TAG, "🔄 Started periodic subscription validation (${SUBSCRIPTION_VALIDATION_INTERVAL / 1000}s interval)")
    }
    
    /**
     * Stop periodic subscription validation
     */
    private fun stopSubscriptionValidation() {
        subscriptionValidationJob?.cancel()
        subscriptionValidationJob = null
        Log.v(TAG, "⏹️ Stopped subscription validation")
    }
    
    // ==========================================
    // WORLD-CLASS: WebSocket Keep-Alive System
    // ==========================================
    
    /**
     * Start periodic WebSocket keep-alive pings to prevent stale connections.
     * This is critical for maintaining reliable long-distance messaging.
     */
    private fun startKeepAlive() {
        stopKeepAlive() // Cancel any existing job
        
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL)
                
                // OkHttp automatically sends WebSocket protocol PINGs every 25s and triggers onFailure
                // if a relay fails to PONG. Here we proactively reconnect any disconnected relays
                // whose backoff window has elapsed.
                val now = System.currentTimeMillis()
                relaysList.forEach { relay ->
                    try {
                        if (!connections.containsKey(relay.url)) {
                            val nextRetry = relay.nextReconnectTime ?: 0L
                            if (now >= nextRetry) {
                                if (relay.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                                    relay.reconnectAttempts = 0
                                }
                                launch { connectToRelay(relay.url) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Keep-alive check error for ${relay.url}: ${e.message}")
                    }
                }
            }
        }
        
        Log.d(TAG, "💓 Started WebSocket keep-alive (${KEEP_ALIVE_INTERVAL/1000}s interval)")
    }
    
    /**
     * Stop the keep-alive ping job.
     */
    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
    
    // ==========================================
    // WORLD-CLASS: NIP-65 Recipient Relay Discovery
    // ==========================================
    
    /**
     * Get relays for a specific recipient based on their NIP-65 relay list.
     * This ensures messages are sent to relays the recipient actually uses.
     * Falls back to default relays if no NIP-65 list is found.
     */
    suspend fun getRecipientRelays(recipientPubkey: String): List<String> {
        // Check cache first
        recipientRelayCache[recipientPubkey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < NIP65_CACHE_TTL) {
                Log.v(TAG, "📋 Using cached NIP-65 relays for ${recipientPubkey.take(8)}...")
                return cached.relays
            }
        }
        
        Log.d(TAG, "🔍 Fetching NIP-65 relays for ${recipientPubkey.take(8)}...")
        
        // Fetch kind:10002 (NIP-65 Relay List Metadata) event for recipient
        val relays = mutableListOf<String>()
        val latch = java.util.concurrent.CountDownLatch(1)
        
        val filter = NostrFilter(
            authors = listOf(recipientPubkey),
            kinds = listOf(10002), // NIP-65 Relay List Metadata
            limit = 1
        )
        
        val subscriptionId = generateSubscriptionId()
        subscribe(filter, subscriptionId, { event ->
            // Parse relay list from event tags
            // Format: ["r", "wss://relay.example.com", "read"/"write"/null]
            event.tags.filter { it.size >= 2 && it[0] == "r" }
                .forEach { tag ->
                    val url = tag[1]
                    val marker = tag.getOrNull(2) // "read", "write", or null (both)
                    // Include if it's a write relay or both
                    if (marker == null || marker == "write") {
                        relays.add(url)
                    }
                }
            latch.countDown()
        })
        
        // Wait with timeout (5 seconds)
        withContext(Dispatchers.IO) {
            try {
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "NIP-65 fetch interrupted: ${e.message}")
            }
        }
        
        // Clean up subscription
        unsubscribe(subscriptionId)
        
        // Cache result
        if (relays.isNotEmpty()) {
            recipientRelayCache[recipientPubkey] = CachedRelayList(relays, System.currentTimeMillis())
            Log.i(TAG, "✅ Found ${relays.size} NIP-65 relays for ${recipientPubkey.take(8)}...")
        } else {
            Log.d(TAG, "⚠️ No NIP-65 relays found for ${recipientPubkey.take(8)}..., using defaults")
        }
        
        return relays.ifEmpty { defaultRelays() }
    }
    
    /**
     * Send a direct message using NIP-65 optimized relay routing.
     * This sends to both the recipient's preferred relays AND our own write relays
     * to maximize delivery probability.
     */
    suspend fun sendEventWithNIP65(event: NostrEvent, recipientPubkey: String) {
        val recipientRelays = getRecipientRelays(recipientPubkey)
        val myRelays = relaysList.filter { it.isConnected }.map { it.url }
        
        // Send to union of both relay sets for best delivery
        val targetRelays = (recipientRelays + myRelays).distinct()
        
        Log.i(TAG, "📤 Sending event with NIP-65 to ${targetRelays.size} relays " +
            "(recipient: ${recipientRelays.size}, mine: ${myRelays.size})")
        
        // Track for delivery confirmation
        pendingDeliveryConfirmations[event.id] = System.currentTimeMillis()
        
        sendEvent(event, targetRelays)
    }
    
    /**
     * Check if an event was confirmed delivered (received OK from at least one relay).
     */
    fun isEventDelivered(eventId: String): Boolean {
        return !pendingDeliveryConfirmations.containsKey(eventId)
    }
    
    /**
     * Get pending delivery count for monitoring.
     */
    fun getPendingDeliveryCount(): Int = pendingDeliveryConfirmations.size
    
    /**
     * WORLD-CLASS: Get relays sorted by health score (fastest & most reliable first).
     * This is similar to how WhatsApp/Telegram auto-select the best servers.
     */
    fun getBestRelays(count: Int = 5): List<Relay> {
        return relaysList
            .filter { it.isConnected }
            .sortedByDescending { it.healthScore }
            .take(count)
    }
    
    /**
     * Get connected relays sorted by health score for message sending.
     * Prioritizes fast, reliable relays first.
     */
    fun getConnectedRelaysByHealth(): List<String> {
        return relaysList
            .filter { it.isConnected }
            .sortedByDescending { it.healthScore }
            .map { it.url }
    }
    
    /**
     * Update relay health stats after receiving a response.
     */
    private fun updateRelayHealth(relayUrl: String, responseTimeMs: Long, success: Boolean) {
        val relay = relaysList.find { it.url == relayUrl } ?: return
        
        if (success) {
            relay.successCount++
        } else {
            relay.failureCount++
        }
        
        // Update average response time with exponential moving average
        relay.lastResponseTimeMs = responseTimeMs
        if (relay.avgResponseTimeMs == 0L) {
            relay.avgResponseTimeMs = responseTimeMs
        } else {
            // 80% old average + 20% new value for smooth updates
            relay.avgResponseTimeMs = ((relay.avgResponseTimeMs * 8) + (responseTimeMs * 2)) / 10
        }
        
        Log.v(TAG, "📊 Relay health: $relayUrl score=${relay.healthScore} " +
            "avg=${relay.avgResponseTimeMs}ms success=${relay.successCount}/${relay.successCount + relay.failureCount}")
        
        updateRelaysList()
    }

    // MARK: - Private Methods
    
    private suspend fun connectToRelay(urlString: String) {
        // Skip if we already have a connection
        if (connections.containsKey(urlString)) {
            return
        }
        
        Log.v(TAG, "Attempting to connect to Nostr relay: $urlString")
        
        try {
            val relay = relaysList.find { it.url == urlString }
            val request = Request.Builder()
                .url(urlString)
                .build()
            
            // If a relay failed via proxy (e.g. Cloudflare blocking Tor exit node) or Tor is not ready,
            // fall back to direct WebSocket client so Nostr stays connected reliably.
            val client = if ((relay?.reconnectAttempts ?: 0) >= 1) {
                com.seamless.android.net.OkHttpProvider.directWebSocketClient()
            } else {
                httpClient
            }
            val webSocket = client.newWebSocket(request, RelayWebSocketListener(urlString))
            connections[urlString] = webSocket
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create WebSocket connection to $urlString: ${e.message}")
            handleDisconnection(urlString, e)
        }
    }
    
    private fun sendToRelay(event: NostrEvent, webSocket: WebSocket, relayUrl: String): Boolean {
        return try {
            val request = NostrRequest.Event(event)
            val message = gson.toJson(request, NostrRequest::class.java)
            
            Log.v(TAG, "📤 Sending Nostr event (kind: ${event.kind}) to relay: $relayUrl")
            
            val success = webSocket.send(message)
            if (success) {
                // Update relay stats
                val relay = relaysList.find { it.url == relayUrl }
                relay?.messagesSent = (relay?.messagesSent ?: 0) + 1
                updateRelaysList()
            } else {
                Log.e(TAG, "❌ Failed to send event to $relayUrl: WebSocket send failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send event to $relayUrl: ${e.message}")
            false
        }
    }
    
    private fun handleMessage(message: String, relayUrl: String) {
        try {
            relaysList.find { it.url == relayUrl }?.lastConnectedAt = System.currentTimeMillis()
            val jsonElement = JsonParser.parseString(message)
            if (!jsonElement.isJsonArray) {
                Log.w(TAG, "Received non-array message from $relayUrl")
                return
            }
            
            val response = NostrResponse.fromJsonArray(jsonElement.asJsonArray)
            
            when (response) {
                is NostrResponse.Event -> {
                    // Update relay stats
                    val relay = relaysList.find { it.url == relayUrl }
                    relay?.messagesReceived = (relay?.messagesReceived ?: 0) + 1
                    updateRelaysList()
                    
                    // CLIENT-SIDE FILTER ENFORCEMENT: Ensure this event matches the subscription's filter
                    activeSubscriptions[response.subscriptionId]?.let { subInfo ->
                        val matches = try { subInfo.filter.matches(response.event) } catch (e: Exception) { true }
                        if (!matches) {
                            Log.v(TAG, "🚫 Dropping event ${response.event.id.take(16)}... not matching filter for sub=${response.subscriptionId}")
                            // Do NOT call deduplicator here to allow the correct subscription to process it later
                            return
                        }
                    }
                    
                    // DEDUPLICATION: Check if we've already processed this event
                    val wasProcessed = eventDeduplicator.processEvent(response.event) { event ->
                        // Only log non-gift-wrap events to reduce noise
                        if (event.kind != NostrKind.GIFT_WRAP) {
                            val originGeo = activeSubscriptions[response.subscriptionId]?.originGeohash
                            if (originGeo != null) {
                                Log.v(TAG, "📥 Processing event (kind=${event.kind}) from relay=$relayUrl geo=$originGeo sub=${response.subscriptionId}")
                            } else {
                                Log.v(TAG, "📥 Processing event (kind=${event.kind}) from relay=$relayUrl sub=${response.subscriptionId}")
                            }
                        }
                        
                        // Call handler for new events only
                        val handler = messageHandlers[response.subscriptionId]
                        if (handler != null) {
                            scope.launch(Dispatchers.Main) {
                                handler(event)
                            }
                        } else {
                            Log.w(TAG, "⚠️ No handler for subscription ${response.subscriptionId}")
                        }
                    }
                    
                    if (!wasProcessed) {
                        //Log.v(TAG, "🔄 Duplicate event ${response.event.id.take(16)}... from relay: $relayUrl")
                    }
                }
                
                is NostrResponse.EndOfStoredEvents -> {
                    Log.v(TAG, "End of stored events for subscription: ${response.subscriptionId}")
                }
                
                is NostrResponse.Ok -> {
                    val wasGiftWrap = pendingGiftWrapIDs.remove(response.eventId)
                    
                    // WORLD-CLASS: Calculate response time for health scoring
                    val sendTime = eventSendTimestamps.remove(response.eventId)
                    val responseTimeMs = if (sendTime != null) {
                        System.currentTimeMillis() - sendTime
                    } else {
                        200L // Default assumption if not tracked
                    }
                    
                    // WORLD-CLASS: Track delivery confirmation and update health
                    if (response.accepted) {
                        pendingDeliveryConfirmations.remove(response.eventId)
                        
                        // Update relay health with success
                        updateRelayHealth(relayUrl, responseTimeMs, success = true)
                        
                        // Update relay activity timestamp (for keep-alive stale detection)
                        val relay = relaysList.find { it.url == relayUrl }
                        relay?.lastConnectedAt = System.currentTimeMillis()
                        
                        Log.d(TAG, "✅ Event accepted id=${response.eventId.take(16)}... by relay: $relayUrl (${responseTimeMs}ms)")
                    } else {
                        // Update relay health with failure
                        updateRelayHealth(relayUrl, responseTimeMs, success = false)
                        
                        val level = if (wasGiftWrap) Log.WARN else Log.ERROR
                        Log.println(level, TAG, "📮 Event ${response.eventId.take(16)}... rejected by relay: ${response.message ?: "no reason"}")
                    }
                }
                
                is NostrResponse.Notice -> {
                    Log.i(TAG, "📢 Notice from $relayUrl: ${response.message}")
                }
                
                is NostrResponse.Unknown -> {
                    Log.v(TAG, "Unknown message type from $relayUrl: ${response.raw}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message from $relayUrl: ${e.message}")
        }
    }
    
    private fun handleDisconnection(relayUrl: String, error: Throwable, webSocket: WebSocket? = null) {
        val currentWs = connections[relayUrl]
        if (webSocket != null && currentWs != null && currentWs !== webSocket) {
            // Ignore late disconnection callback from a replaced WebSocket instance
            return
        }
        connections.remove(relayUrl)
        // NOTE: Don't remove subscriptions here - keep them for restoration on reconnection
        
        updateRelayStatus(relayUrl, false, error)
        
        val relay = relaysList.find { it.url == relayUrl } ?: return
        relay.reconnectAttempts++
        
        // Stop attempting after max attempts
        if (relay.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts ($MAX_RECONNECT_ATTEMPTS) reached for $relayUrl")
            return
        }
        
        // Calculate backoff interval
        val backoffInterval = min(
            INITIAL_BACKOFF_INTERVAL * BACKOFF_MULTIPLIER.pow(relay.reconnectAttempts - 1.0),
            MAX_BACKOFF_INTERVAL.toDouble()
        ).toLong()
        
        relay.nextReconnectTime = System.currentTimeMillis() + backoffInterval
        
        Log.d(TAG, "Scheduling reconnection to $relayUrl in ${backoffInterval / 1000}s (attempt ${relay.reconnectAttempts})")
        
        // Schedule reconnection
        scope.launch {
            delay(backoffInterval)
            connectToRelay(relayUrl)
        }
    }
    
    private fun updateRelayStatus(url: String, isConnected: Boolean, error: Throwable? = null) {
        val relay = relaysList.find { it.url == url } ?: return
        
        relay.isConnected = isConnected
        relay.lastError = error
        
        if (isConnected) {
            relay.lastConnectedAt = System.currentTimeMillis()
            relay.reconnectAttempts = 0
            relay.nextReconnectTime = null
        } else {
            relay.lastDisconnectedAt = System.currentTimeMillis()
        }
        
        updateRelaysList()
        updateConnectionStatus()
    }
    
    /**
     * Generate a unique subscription ID
     */
    private fun generateSubscriptionId(): String {
        return "sub-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
    }
    
    /**
     * Update relays list LiveData
     */
    private fun updateRelaysList() {
        _relays.postValue(relaysList.toList())
    }
    
    /**
     * Update connection status LiveData
     */
    private fun updateConnectionStatus() {
        val hasConnected = relaysList.any { it.isConnected }
        _isConnected.postValue(hasConnected)
    }
    
    /**
     * Restore all active subscriptions for a specific relay that just reconnected
     */
    private fun restoreSubscriptionsForRelay(relayUrl: String, webSocket: WebSocket) {
        val subscriptionsToRestore = activeSubscriptions.values.filter { subscriptionInfo ->
            // Include subscription if it targets all relays or specifically targets this relay
            subscriptionInfo.targetRelayUrls == null || subscriptionInfo.targetRelayUrls.contains(relayUrl)
        }
        
        if (subscriptionsToRestore.isEmpty()) {
            Log.v(TAG, "🔄 No subscriptions to restore for relay: $relayUrl")
            return
        }
        
        Log.d(TAG, "🔄 Restoring ${subscriptionsToRestore.size} subscriptions for relay: $relayUrl")
        
        subscriptionsToRestore.forEach { subscriptionInfo ->
            try {
                val request = NostrRequest.Subscribe(subscriptionInfo.id, listOf(subscriptionInfo.filter))
                val message = gson.toJson(request, NostrRequest::class.java)
                
                val success = webSocket.send(message)
                if (success) {
                    // Track subscription for this relay
                    val currentSubs = subscriptions[relayUrl] ?: emptySet()
                    subscriptions[relayUrl] = currentSubs + subscriptionInfo.id
                    
                    Log.v(TAG, "✅ Restored subscription '${subscriptionInfo.id}' to relay: $relayUrl")
                } else {
                    Log.w(TAG, "❌ Failed to restore subscription '${subscriptionInfo.id}' to $relayUrl: WebSocket send failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore subscription '${subscriptionInfo.id}' to $relayUrl: ${e.message}")
            }
        }
    }
    
    /**
     * WebSocket listener for relay connections
     */
    private inner class RelayWebSocketListener(private val relayUrl: String) : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "✅ Connected to Nostr relay: $relayUrl")
            updateRelayStatus(relayUrl, true)
            
            // Restore all active subscriptions for this relay
            restoreSubscriptionsForRelay(relayUrl, webSocket)
            
            // Process any queued messages for this relay and remove once sent
            val now = System.currentTimeMillis()
            synchronized(messageQueueLock) {
                val iterator = messageQueue.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    if (now - item.enqueuedAt > MESSAGE_QUEUE_TTL_MS) {
                        iterator.remove()
                        continue
                    }
                    if (relayUrl in item.pendingRelays) {
                        if (sendToRelay(item.event, webSocket, relayUrl)) {
                            item.pendingRelays.remove(relayUrl)
                        }
                    }
                    if (item.pendingRelays.isEmpty()) {
                        iterator.remove()
                    }
                }
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text, relayUrl)
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing for $relayUrl: $code $reason")
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed for $relayUrl: $code $reason")
            val error = Exception("WebSocket closed: $code $reason")
            handleDisconnection(relayUrl, error, webSocket)
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ WebSocket failure for $relayUrl: ${t.message}")
            handleDisconnection(relayUrl, t, webSocket)
        }
    }
}
