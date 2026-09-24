package com.seamless.android.nostr

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

/**
 * NIP-65 Relay List Registry
 * 
 * Manages user relay preferences for the Gossip/Outbox model.
 * Each user can publish their preferred relays (kind 10002 event)
 * and this registry caches that information for intelligent routing.
 * 
 * PRODUCTION FEATURE: Ensures messages are delivered to the relays
 * where the recipient is actually listening, not just random defaults.
 */
data class RelayList(
    val readRelays: List<String>,   // Where user RECEIVES messages (their inbox)
    val writeRelays: List<String>,  // Where user PUBLISHES (their outbox)
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Get all unique relays (both read and write)
     */
    fun allRelays(): Set<String> = (readRelays + writeRelays).toSet()
    
    companion object {
        val EMPTY = RelayList(emptyList(), emptyList())
    }
}

class Nip65RelayRegistry private constructor(private val context: Context) {

    companion object {
        private const val TAG = "Nip65RelayRegistry"
        private const val PREFS_NAME = "nip65_relay_registry"
        private const val KEY_RELAY_LISTS = "relay_lists"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        
        // Default relays to use when no NIP-65 data is available
        // These are high-quality, reliable relays
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://relay.primal.net",
            "wss://relay.nostr.band",
            "wss://nos.lol",
            "wss://relay.snort.social",
            "wss://nostr.wine",
            "wss://purplepag.es",
            "wss://relay.nostr.bg"
        )

        @Volatile
        private var INSTANCE: Nip65RelayRegistry? = null

        val shared: Nip65RelayRegistry
            get() = INSTANCE ?: throw IllegalStateException("Nip65RelayRegistry not initialized")

        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = Nip65RelayRegistry(context.applicationContext)
                    }
                }
            }
        }
        
        /**
         * Try to get the shared instance, or null if not initialized
         */
        fun tryGetInstance(): Nip65RelayRegistry? = INSTANCE
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // In-memory cache: npub -> RelayList
    private val relayCache = mutableMapOf<String, RelayList>()
    
    // PRODUCTION: Relay health tracking for intelligent routing
    private val relayHealth = mutableMapOf<String, RelayHealthStats>()
    
    /**
     * Relay health statistics for intelligent routing
     */
    data class RelayHealthStats(
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var totalLatencyMs: Long = 0,
        var lastSuccessTime: Long = 0,
        var lastFailureTime: Long = 0
    ) {
        val successRate: Float
            get() = if (successCount + failureCount > 0) {
                successCount.toFloat() / (successCount + failureCount)
            } else 0.5f // Default 50% for unknown relays
        
        val avgLatencyMs: Long
            get() = if (successCount > 0) totalLatencyMs / successCount else 1000L
        
        /**
         * Performance score (higher = better)
         * Weighs success rate heavily, with latency as secondary factor
         */
        val performanceScore: Float
            get() = successRate * 100 - (avgLatencyMs / 100f).coerceAtMost(50f)
    }
    
    /**
     * Record successful message send to relay
     */
    fun recordSuccess(relayUrl: String, latencyMs: Long) {
        val stats = relayHealth.getOrPut(relayUrl) { RelayHealthStats() }
        stats.successCount++
        stats.totalLatencyMs += latencyMs
        stats.lastSuccessTime = System.currentTimeMillis()
        Log.v(TAG, "✅ Relay $relayUrl: success (${stats.successRate * 100}% rate, ${stats.avgLatencyMs}ms avg)")
    }
    
    /**
     * Record failed message send to relay
     */
    fun recordFailure(relayUrl: String) {
        val stats = relayHealth.getOrPut(relayUrl) { RelayHealthStats() }
        stats.failureCount++
        stats.lastFailureTime = System.currentTimeMillis()
        Log.w(TAG, "❌ Relay $relayUrl: failure (${stats.successRate * 100}% rate)")
    }
    
    /**
     * Get relays sorted by health/performance (best first)
     */
    fun sortByHealth(relays: Collection<String>): List<String> {
        return relays.sortedByDescending { url ->
            relayHealth[url]?.performanceScore ?: 50f // Default score for unknown
        }
    }
    
    /**
     * Get health stats for debugging
     */
    fun getHealthStats(): Map<String, RelayHealthStats> = relayHealth.toMap()
    
    init {
        loadFromDisk()
    }

    /**
     * Parse a NIP-65 event (kind 10002) and store the relay list
     */
    fun parseAndStore(event: NostrEvent): Boolean {
        if (event.kind != NostrKind.RELAY_LIST_METADATA) {
            return false
        }
        
        try {
            val readRelays = mutableListOf<String>()
            val writeRelays = mutableListOf<String>()
            
            // Parse "r" tags: ["r", "wss://relay.url", "read"|"write"|""]
            for (tag in event.tags) {
                if (tag.size >= 2 && tag[0] == "r") {
                    val relayUrl = tag[1]
                    val marker = if (tag.size >= 3) tag[2] else ""
                    
                    when (marker.lowercase()) {
                        "read" -> readRelays.add(relayUrl)
                        "write" -> writeRelays.add(relayUrl)
                        "" -> {
                            // No marker = both read and write
                            readRelays.add(relayUrl)
                            writeRelays.add(relayUrl)
                        }
                    }
                }
            }
            
            if (readRelays.isEmpty() && writeRelays.isEmpty()) {
                Log.w(TAG, "NIP-65 event has no relays: ${event.id.take(16)}...")
                return false
            }
            
            val npub = event.pubkey
            val relayList = RelayList(readRelays, writeRelays)
            
            relayCache[npub] = relayList
            saveToDisk()
            
            Log.i(TAG, "✅ Stored NIP-65 for $npub: ${readRelays.size} read, ${writeRelays.size} write")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse NIP-65 event: ${e.message}")
            return false
        }
    }

    /**
     * Get the relays where we should SEND messages TO a user (their inbox/read relays)
     * Returns defaulted list if no NIP-65 data is available
     */
    fun getInboxRelays(npubHex: String): List<String> {
        val cached = relayCache[npubHex]
        
        // If we have cached data and it's not too old, use it
        if (cached != null && !isExpired(cached)) {
            return cached.readRelays.ifEmpty { DEFAULT_RELAYS.take(4) }
        }
        
        // No data or expired - use defaults
        return DEFAULT_RELAYS.take(4)
    }

    /**
     * Get relays to publish our own events (our outbox/write relays)
     * For now returns our configured relays
     */
    fun getOutboxRelays(): List<String> {
        // Could be enhanced to use user's own NIP-65 settings
        return DEFAULT_RELAYS.take(4)
    }

    /**
     * Get combined relays for sending a DM (our outbox + their inbox + ALL defaults)
     * PRODUCTION FIX: Always include ALL default relays to ensure message delivery
     * even when recipient's NIP-65 data is unavailable or incomplete
     */
    fun getDeliveryRelays(recipientNpubHex: String): Set<String> {
        val ourOutbox = getOutboxRelays()
        val theirInbox = getInboxRelays(recipientNpubHex)
        
        // CRITICAL FIX: Always include ALL default relays to maximize delivery probability
        // This ensures messages reach recipients regardless of their NIP-65 configuration
        val allDefaults = DEFAULT_RELAYS.toSet()
        
        val combined = (ourOutbox + theirInbox + allDefaults).toSet()
        Log.i(TAG, "📡 Delivery relays for ${recipientNpubHex.take(16)}...: ${combined.size} total (includes ${allDefaults.size} defaults)")
        
        return combined
    }

    /**
     * GPS-ENHANCED: Get delivery relays prioritizing geographically close relays
     * Uses RelayDirectory to find relays near the sender's location for lower latency
     * 
     * @param recipientNpubHex The recipient's public key in hex
     * @param senderGeohash Optional geohash of sender's location (4-6 chars precision)
     * @return Set of relay URLs optimized for speed
     */
    fun getGeoEnhancedDeliveryRelays(recipientNpubHex: String, senderGeohash: String?): Set<String> {
        val baseRelays = getDeliveryRelays(recipientNpubHex).toMutableSet()
        
        // Add geographically close relays for lower latency
        if (!senderGeohash.isNullOrBlank() && senderGeohash.length >= 4) {
            try {
                val geoRelays = RelayDirectory.closestRelaysForGeohash(senderGeohash, 3)
                if (geoRelays.isNotEmpty()) {
                    baseRelays.addAll(geoRelays)
                    Log.d(TAG, "📍 Added ${geoRelays.size} geo-close relays for ${senderGeohash.take(4)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get geo relays: ${e.message}")
            }
        }
        
        Log.d(TAG, "📡 Geo-enhanced delivery: ${baseRelays.size} relays total")
        return baseRelays
    }

    /**
     * Request a fresh NIP-65 lookup for a user
     * Triggers a subscription to fetch their kind 10002 event
     */
    fun requestRelayListRefresh(npubHex: String) {
        scope.launch {
            try {
                val filter = NostrFilter(
                    authors = listOf(npubHex),
                    kinds = listOf(NostrKind.RELAY_LIST_METADATA),
                    limit = 1
                )
                
                NostrRelayManager.shared.subscribe(
                    filter = filter,
                    id = "nip65-refresh-${npubHex.take(8)}",
                    handler = { event ->
                        parseAndStore(event)
                    }
                )
                
                // Auto-unsubscribe after 10 seconds
                delay(10000)
                NostrRelayManager.shared.unsubscribe("nip65-refresh-${npubHex.take(8)}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request NIP-65 refresh: ${e.message}")
            }
        }
    }

    /**
     * Check if we have relay data for a user
     */
    fun hasRelayData(npubHex: String): Boolean {
        val cached = relayCache[npubHex]
        return cached != null && !isExpired(cached)
    }

    /**
     * Clear all cached data
     */
    fun clear() {
        relayCache.clear()
        prefs.edit().remove(KEY_RELAY_LISTS).apply()
        Log.i(TAG, "🧹 Cleared all NIP-65 cache")
    }

    private fun isExpired(relayList: RelayList): Boolean {
        return System.currentTimeMillis() - relayList.lastUpdated > CACHE_DURATION_MS
    }

    private fun loadFromDisk() {
        try {
            val json = prefs.getString(KEY_RELAY_LISTS, null) ?: return
            val type = object : TypeToken<Map<String, RelayList>>() {}.type
            val loaded: Map<String, RelayList> = gson.fromJson(json, type)
            relayCache.putAll(loaded)
            Log.d(TAG, "📥 Loaded ${relayCache.size} NIP-65 entries from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load NIP-65 cache: ${e.message}")
        }
    }

    private fun saveToDisk() {
        try {
            val json = gson.toJson(relayCache)
            prefs.edit().putString(KEY_RELAY_LISTS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save NIP-65 cache: ${e.message}")
        }
    }
}
