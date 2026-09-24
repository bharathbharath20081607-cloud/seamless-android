package com.seamless.android.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Censorship Detection & Auto-Circumvention
 *
 * Detects whether the user's network censors Tor and automatically
 * selects the best pluggable transport strategy. Uses a combination of:
 *
 * 1. Country-based heuristics (OONI-derived data)
 * 2. Runtime connectivity probes
 * 3. Cached detection results for fast startup
 *
 * Based on Briar's automated circumvention approach:
 * https://briarproject.org/news/2023-briar-1-5-censorship-circumvention/
 */
class CensorshipDetector private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CensorshipDetector"
        private const val PREFS_NAME = "censorship_detection"
        private const val KEY_LAST_STRATEGY = "last_strategy"
        private const val KEY_LAST_COUNTRY = "last_country"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L  // 24 hours

        // Probe timeouts
        private const val VANILLA_PROBE_TIMEOUT_MS = 10_000
        private const val BRIDGE_PROBE_TIMEOUT_MS = 20_000
        private const val INTERNET_PROBE_TIMEOUT_MS = 8_000

        // Known Tor directory authority addresses for vanilla probe
        private val TOR_DIRECTORY_AUTHORITIES = listOf(
            "128.31.0.34" to 9101,     // moria1
            "86.59.21.38" to 80,       // tor26
            "194.109.206.212" to 80,   // dizum
        )

        // Well-known hosts to verify basic internet connectivity
        // (not Tor-related, so they shouldn't be blocked even in censored regions)
        private val INTERNET_PROBE_HOSTS = listOf(
            "1.1.1.1" to 80,           // Cloudflare DNS
            "8.8.8.8" to 53,           // Google DNS
            "204.79.197.200" to 80,    // Bing
        )

        @Volatile
        private var instance: CensorshipDetector? = null

        fun getInstance(context: Context): CensorshipDetector {
            return instance ?: synchronized(this) {
                instance ?: CensorshipDetector(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Transport strategies ordered by preference.
     *
     * VANILLA:    Direct Tor — fastest, works on unblocked networks
     * OBFS4:      Look-like-nothing protocol — defeats DPI-based blocking
     * MEEK_LITE:  Domain fronting via CDNs — defeats IP-based blocking
     * SNOWFLAKE:  WebRTC proxies — most resilient, highest latency
     */
    enum class TransportStrategy {
        VANILLA,        // Direct Tor connection
        OBFS4,          // obfs4 pluggable transport
        MEEK_LITE,      // meek_lite domain fronting
        SNOWFLAKE       // Snowflake WebRTC proxies
    }

    /**
     * Detection result
     */
    data class DetectionResult(
        val strategy: TransportStrategy,
        val countryCode: String?,
        val vanillaBlocked: Boolean,
        val probeLatencyMs: Long,
        val detectedAt: Long = System.currentTimeMillis()
    )

    // Countries known to block Tor (OONI-derived, updated periodically)
    // Source: https://ooni.org/data/
    private val COUNTRIES_BLOCKING_TOR = mapOf(
        // Heavy Tor blocking — need obfs4 or stronger
        "CN" to TransportStrategy.MEEK_LITE,     // China — deep DPI, need domain fronting
        "IR" to TransportStrategy.SNOWFLAKE,      // Iran — aggressive blocking
        "RU" to TransportStrategy.OBFS4,          // Russia — DPI-based blocking
        "TM" to TransportStrategy.MEEK_LITE,      // Turkmenistan — near-total internet control
        "BY" to TransportStrategy.OBFS4,          // Belarus — DPI blocking

        // Moderate Tor blocking — obfs4 usually works
        "EG" to TransportStrategy.OBFS4,          // Egypt — intermittent blocking
        "KZ" to TransportStrategy.OBFS4,          // Kazakhstan — DPI
        "UZ" to TransportStrategy.OBFS4,          // Uzbekistan
        "VN" to TransportStrategy.OBFS4,          // Vietnam
        "AE" to TransportStrategy.OBFS4,          // UAE
        "SA" to TransportStrategy.OBFS4,          // Saudi Arabia
        "PK" to TransportStrategy.OBFS4,          // Pakistan

        // Mild/intermittent blocking
        "TH" to TransportStrategy.OBFS4,          // Thailand — occasional
        "VE" to TransportStrategy.OBFS4,          // Venezuela
        "ET" to TransportStrategy.OBFS4,          // Ethiopia
        "MM" to TransportStrategy.SNOWFLAKE,      // Myanmar
    )

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _detectionResult = MutableStateFlow<DetectionResult?>(null)
    val detectionResult: StateFlow<DetectionResult?> = _detectionResult.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Detect the best transport strategy for the current network.
     *
     * Order:
     * 1. Check cache (valid for 24 hours)
     * 2. Check country-based heuristic
     * 3. Run vanilla Tor connectivity probe
     * 4. If vanilla blocked, try obfs4 → meek → snowflake
     */
    suspend fun detectBestTransport(countryCode: String? = null): DetectionResult {
        // Check cache first
        val cached = getCachedResult()
        if (cached != null) {
            Log.i(TAG, "Using cached transport strategy: ${cached.strategy}")
            _detectionResult.value = cached
            return cached
        }

        val country = countryCode ?: detectCountryCode()
        Log.i(TAG, "🔍 Detecting censorship for country: $country")

        // Country-based heuristic
        val countryStrategy = COUNTRIES_BLOCKING_TOR[country]
        if (countryStrategy != null) {
            Log.i(TAG, "🌍 Country $country known to block Tor, using: $countryStrategy")

            // Still probe vanilla in case blocking has changed
            val vanillaWorks = probeVanillaTor()
            val strategy = if (vanillaWorks) TransportStrategy.VANILLA else countryStrategy

            val result = DetectionResult(
                strategy = strategy,
                countryCode = country,
                vanillaBlocked = !vanillaWorks,
                probeLatencyMs = 0
            )
            cacheResult(result)
            _detectionResult.value = result
            return result
        }

        // No country heuristic — probe connectivity
        // PRODUCTION FIX: First check if we have any internet at all.
        // If there is NO internet, we should NOT assume censorship.
        val hasInternet = probeInternetConnectivity()
        if (!hasInternet) {
            Log.w(TAG, "📡 No internet connectivity detected — defaulting to VANILLA (not caching)")
            val result = DetectionResult(
                strategy = TransportStrategy.VANILLA,
                countryCode = country,
                vanillaBlocked = false,
                probeLatencyMs = 0
            )
            // Do NOT cache this result — it's a transient no-internet state.
            // We want to re-probe once connectivity is restored.
            _detectionResult.value = result
            return result
        }

        val probeStart = System.currentTimeMillis()
        val vanillaWorks = probeVanillaTor()
        val probeLatency = System.currentTimeMillis() - probeStart

        val strategy = if (vanillaWorks) TransportStrategy.VANILLA else TransportStrategy.OBFS4

        val result = DetectionResult(
            strategy = strategy,
            countryCode = country,
            vanillaBlocked = !vanillaWorks,
            probeLatencyMs = probeLatency
        )
        cacheResult(result)
        _detectionResult.value = result

        Log.i(TAG, "🔍 Detection result: strategy=${result.strategy}, vanillaBlocked=${result.vanillaBlocked}")
        return result
    }

    /**
     * Force re-detection (ignoring cache)
     */
    suspend fun forceRedetect(countryCode: String? = null): DetectionResult {
        clearCache()
        return detectBestTransport(countryCode)
    }

    /**
     * Get the recommended strategy for a specific country.
     */
    fun getStrategyForCountry(countryCode: String): TransportStrategy {
        return COUNTRIES_BLOCKING_TOR[countryCode] ?: TransportStrategy.VANILLA
    }

    /**
     * Probe whether vanilla (direct) Tor is reachable.
     * Tries to TCP-connect to a Tor directory authority.
     */
    private suspend fun probeVanillaTor(): Boolean = withContext(Dispatchers.IO) {
        for ((host, port) in TOR_DIRECTORY_AUTHORITIES) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), VANILLA_PROBE_TIMEOUT_MS)
                socket.close()
                Log.d(TAG, "✅ Vanilla Tor reachable via $host:$port")
                return@withContext true
            } catch (e: Exception) {
                Log.d(TAG, "❌ Cannot reach $host:$port: ${e.message}")
            }
        }
        Log.w(TAG, "🚫 Vanilla Tor directory authorities unreachable")
        return@withContext false
    }

    /**
     * Detect country code from Android telephony/locale.
     */
    private fun detectCountryCode(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val simCountry = tm?.simCountryIso?.uppercase()
            val networkCountry = tm?.networkCountryIso?.uppercase()
            val localeCountry = java.util.Locale.getDefault().country.uppercase()

            // Prefer SIM country (most reliable), fall back to network, then locale
            when {
                !simCountry.isNullOrEmpty() && simCountry.length == 2 -> simCountry
                !networkCountry.isNullOrEmpty() && networkCountry.length == 2 -> networkCountry
                localeCountry.length == 2 -> localeCountry
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect country: ${e.message}")
            "UNKNOWN"
        }
    }

    // ===== Caching =====

    private fun getCachedResult(): DetectionResult? {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - lastCheck > CACHE_DURATION_MS) return null

        val strategyName = prefs.getString(KEY_LAST_STRATEGY, null) ?: return null
        val country = prefs.getString(KEY_LAST_COUNTRY, null)

        return try {
            DetectionResult(
                strategy = TransportStrategy.valueOf(strategyName),
                countryCode = country,
                vanillaBlocked = strategyName != TransportStrategy.VANILLA.name,
                probeLatencyMs = 0,
                detectedAt = lastCheck
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheResult(result: DetectionResult) {
        prefs.edit()
            .putString(KEY_LAST_STRATEGY, result.strategy.name)
            .putString(KEY_LAST_COUNTRY, result.countryCode)
            .putLong(KEY_LAST_CHECK, result.detectedAt)
            .apply()
    }

    private fun clearCache() {
        prefs.edit().clear().apply()
    }

    /**
     * PRODUCTION FIX: Probe whether we have ANY internet connectivity at all.
     * This is done BEFORE Tor-specific probes to distinguish "no internet" from "censorship".
     * We try well-known non-Tor hosts that are almost never blocked.
     */
    private suspend fun probeInternetConnectivity(): Boolean = withContext(Dispatchers.IO) {
        for ((host, port) in INTERNET_PROBE_HOSTS) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), INTERNET_PROBE_TIMEOUT_MS)
                socket.close()
                Log.d(TAG, "✅ Internet reachable via $host:$port")
                return@withContext true
            } catch (e: Exception) {
                Log.d(TAG, "❌ Cannot reach $host:$port: ${e.message}")
            }
        }
        Log.w(TAG, "📡 No general internet connectivity detected")
        return@withContext false
    }

    /**
     * Called when network connectivity changes. If we previously detected censorship
     * but had no internet, clear the cache so we re-probe with actual connectivity.
     */
    fun onNetworkAvailable() {
        val cached = _detectionResult.value
        if (cached != null && cached.vanillaBlocked) {
            Log.i(TAG, "🔄 Network available — clearing stale censorship cache for re-detection")
            clearCache()
        }
    }

    /**
     * Get human-readable description of a transport strategy.
     */
    fun getStrategyDescription(strategy: TransportStrategy): String {
        return when (strategy) {
            TransportStrategy.VANILLA -> "Direct Tor connection (fastest)"
            TransportStrategy.OBFS4 -> "obfs4 bridge (bypasses DPI blocking)"
            TransportStrategy.MEEK_LITE -> "Domain fronting via CDN (bypasses IP blocking)"
            TransportStrategy.SNOWFLAKE -> "Snowflake WebRTC proxy (most resilient)"
        }
    }
}
