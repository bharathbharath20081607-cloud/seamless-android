@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Pluggable Transport Manager
 *
 * Manages transport selection and configuration for censorship circumvention.
 * Coordinates between CensorshipDetector (what transport to use) and
 * TorManager (how to configure Arti with bridges/PTs).
 *
 * Supported transports:
 * - VANILLA:   Direct Tor (no bridges)
 * - OBFS4:     Look-like-nothing — binary bundled as lyrebird
 * - MEEK_LITE: Domain fronting via Azure/CDN
 * - SNOWFLAKE: WebRTC proxies via Snowflake broker
 *
 * Architecture:
 * ```
 * CensorshipDetector → PluggableTransportManager → TorManager
 *   (detect)              (configure)                (apply)
 * ```
 *
 * Based on Briar's pluggable transport integration:
 * https://code.briarproject.org/briar/briar/-/tree/master/briar-android/src/main/java/org/briarproject/briar/android/navdrawer
 */
class PluggableTransportManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PluggableTransportMgr"
        private const val PREFS_NAME = "pluggable_transport"
        private const val KEY_MANUAL_STRATEGY = "manual_strategy"
        private const val KEY_AUTO_ENABLED = "auto_detection_enabled"
        private const val KEY_CUSTOM_BRIDGES = "custom_bridges"

        // Default obfs4 bridges (from Tor Browser's built-in bridges)
        // These are public bridges that are regularly refreshed
        private val DEFAULT_OBFS4_BRIDGES = listOf(
            "obfs4 192.95.36.142:443 CDF2E852BF539B82BD10E27E9115A31734E378C2 cert=qUVQ0srL1JI/vO6V6m/24anYXiJD3QP2HgTAKQQALpAX4MhbkqFvaeeTetNRtUTFWFJ3mA iat-mode=0",
            "obfs4 38.229.1.78:80 C8CBDB2464FC9804A69105437531027A2D03B3EE cert=Hmyfd2ev46gGY7NoVxA9ngrPF2zCZtzskRTzoWXbxNkzeVnGFPWmrTtILRyqCTjHR+s9dg iat-mode=0",
            "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo7krje64BztaofUza2FT1TKYIw iat-mode=0",
        )

        // Meek bridge (Azure CDN)
        private val DEFAULT_MEEK_BRIDGES = listOf(
            "meek_lite 192.0.2.18:80 BE776A53492E1E044A26F17306E1BC46A55A1625 url=https://meek.azureedge.net/ front=ajax.aspnetcdn.com"
        )

        // WebTunnel HTTPS bridges (camouflage as normal HTTPS web requests)
        private val DEFAULT_WEBTUNNEL_BRIDGES = listOf(
            "webtunnel [2001:db8:3398:b43a:8c38:5b64:48ef:90aa]:443 935D418A13825285C3A2067847E4D3E1E55A029A url=https://wt.bridges.torproject.org/ ver=0.0.1"
        )

        // Snowflake configuration (via bundled liblyrebird.so — avoid defunct stun.ekiga.net)
        private val DEFAULT_SNOWFLAKE_BRIDGES = listOf(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://snowflake-broker.torproject.net.global.prod.fastly.net/ front=cdn.sstatic.net ice=stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478 utls-imitate=hellorandomizedalpn"
        )

        @Volatile
        private var instance: PluggableTransportManager? = null

        fun getInstance(context: Context): PluggableTransportManager {
            return instance ?: synchronized(this) {
                instance ?: PluggableTransportManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Transport configuration ready for TorManager
     */
    data class TransportConfig(
        val strategy: CensorshipDetector.TransportStrategy,
        val bridges: List<String>,
        val ptBinaryPath: String?,        // Path to lyrebird binary (for obfs4)
        val extraArgs: Map<String, String> = emptyMap()
    )

    private val censorshipDetector = CensorshipDetector.getInstance(context)
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _currentStrategy = MutableStateFlow(CensorshipDetector.TransportStrategy.VANILLA)
    val currentStrategy: StateFlow<CensorshipDetector.TransportStrategy> = _currentStrategy.asStateFlow()

    private val _transportConfig = MutableStateFlow<TransportConfig?>(null)
    val transportConfig: StateFlow<TransportConfig?> = _transportConfig.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Whether auto-detection is enabled (vs manual selection)
     */
    var autoDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply() }

    /**
     * Initialize and detect the best transport.
     * Call this before starting Tor.
     */
    suspend fun initialize(): TransportConfig {
        val strategy = if (autoDetectionEnabled) {
            val result = censorshipDetector.detectBestTransport()
            result.strategy
        } else {
            getManualStrategy()
        }

        return applyStrategy(strategy)
    }

    /**
     * Manually set the transport strategy (disables auto-detection).
     */
    suspend fun setManualStrategy(strategy: CensorshipDetector.TransportStrategy): TransportConfig {
        autoDetectionEnabled = false
        prefs.edit().putString(KEY_MANUAL_STRATEGY, strategy.name).apply()
        return applyStrategy(strategy)
    }

    /**
     * Force auto-detection and apply the result.
     */
    suspend fun autoDetectAndApply(): TransportConfig {
        autoDetectionEnabled = true
        val result = censorshipDetector.forceRedetect()
        return applyStrategy(result.strategy)
    }

    fun setCustomBridges(lines: List<String>) {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(16)
        prefs.edit().putString(KEY_CUSTOM_BRIDGES, cleaned.joinToString("\n")).apply()
        val current = _currentStrategy.value
        if (cleaned.isNotEmpty() || current != CensorshipDetector.TransportStrategy.VANILLA) {
            applyStrategy(current)
        }
    }

    fun getCustomBridges(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_BRIDGES, null).orEmpty()
        return raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    /**
     * Apply a transport strategy and generate configuration for TorManager.
     */
    private fun applyStrategy(strategy: CensorshipDetector.TransportStrategy): TransportConfig {
        _currentStrategy.value = strategy
        val customBridges = getCustomBridges()
        val lyrebirdPath = findPtBinary("lyrebird") ?: findPtBinary("obfs4proxy")

        val config = when (strategy) {
            CensorshipDetector.TransportStrategy.VANILLA -> {
                if (customBridges.isNotEmpty()) {
                    TransportConfig(
                        strategy = strategy,
                        bridges = customBridges,
                        ptBinaryPath = lyrebirdPath
                    )
                } else {
                    TransportConfig(
                        strategy = strategy,
                        bridges = emptyList(),
                        ptBinaryPath = null
                    )
                }
            }

            CensorshipDetector.TransportStrategy.OBFS4 -> {
                TransportConfig(
                    strategy = strategy,
                    bridges = customBridges.ifEmpty { DEFAULT_OBFS4_BRIDGES },
                    ptBinaryPath = lyrebirdPath,
                    extraArgs = mapOf("TunnelMode" to "obfs4")
                )
            }

            CensorshipDetector.TransportStrategy.WEBTUNNEL -> {
                TransportConfig(
                    strategy = strategy,
                    bridges = customBridges.ifEmpty { DEFAULT_WEBTUNNEL_BRIDGES },
                    ptBinaryPath = lyrebirdPath,
                    extraArgs = mapOf("TunnelMode" to "webtunnel")
                )
            }

            CensorshipDetector.TransportStrategy.MEEK_LITE -> {
                TransportConfig(
                    strategy = strategy,
                    bridges = customBridges.ifEmpty { DEFAULT_MEEK_BRIDGES },
                    ptBinaryPath = lyrebirdPath,
                    extraArgs = mapOf("TunnelMode" to "meek_lite")
                )
            }

            CensorshipDetector.TransportStrategy.SNOWFLAKE -> {
                val snowflakePath = lyrebirdPath ?: findPtBinary("snowflake-client")
                TransportConfig(
                    strategy = strategy,
                    bridges = customBridges.ifEmpty { DEFAULT_SNOWFLAKE_BRIDGES },
                    ptBinaryPath = snowflakePath,
                    extraArgs = mapOf(
                        "TunnelMode" to "snowflake",
                        "BrokerURL" to "https://snowflake-broker.torproject.net.global.prod.fastly.net/",
                        "FrontDomains" to "cdn.sstatic.net",
                        "ICEAddresses" to "stun:stun.l.google.com:19302"
                    )
                )
            }
        }

        _transportConfig.value = config
        Log.i(TAG, "🔧 Transport strategy: ${strategy.name} (${config.bridges.size} bridges)")

        return config
    }

    /**
     * Find a pluggable transport binary in the app's native libs or assets.
     */
    private fun findPtBinary(name: String): String? {
        // Check native libs directory
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val candidates = listOf(
            "$nativeLibDir/lib${name}.so",
            "$nativeLibDir/$name",
            "${context.filesDir}/pt/$name",
            "${context.filesDir}/pt/lib${name}.so"
        )

        for (path in candidates) {
            val file = java.io.File(path)
            if (file.exists() && file.canExecute()) {
                Log.d(TAG, "Found PT binary: $path")
                return path
            }
        }

        Log.w(TAG, "⚠️ PT binary '$name' not found (transports requiring it may not work)")
        return null
    }

    /**
     * Get the manually selected strategy.
     */
    private fun getManualStrategy(): CensorshipDetector.TransportStrategy {
        val name = prefs.getString(KEY_MANUAL_STRATEGY, null)
        return try {
            if (name != null) CensorshipDetector.TransportStrategy.valueOf(name)
            else CensorshipDetector.TransportStrategy.VANILLA
        } catch (e: Exception) {
            CensorshipDetector.TransportStrategy.VANILLA
        }
    }

    /**
     * Generate a torrc-compatible bridge configuration string.
     * This can be passed to Tor/Arti as bridge configuration.
     */
    fun generateBridgeConfig(): String {
        val config = _transportConfig.value ?: return ""
        if (config.bridges.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("UseBridges 1")

        // Add bridge lines
        for (bridge in config.bridges) {
            sb.appendLine("Bridge $bridge")
        }

        // Add client transport plugin if needed
        if (config.ptBinaryPath != null) {
            val transport = when (config.strategy) {
                CensorshipDetector.TransportStrategy.OBFS4 -> "obfs4"
                CensorshipDetector.TransportStrategy.WEBTUNNEL -> "webtunnel"
                CensorshipDetector.TransportStrategy.MEEK_LITE -> "meek_lite"
                CensorshipDetector.TransportStrategy.SNOWFLAKE -> "snowflake"
                else -> return sb.toString()
            }
            sb.appendLine("ClientTransportPlugin $transport exec ${config.ptBinaryPath}")
        }

        return sb.toString()
    }

    /**
     * Get available transport strategies with their status.
     */
    fun getAvailableStrategies(): List<StrategyInfo> {
        val lyrebirdAvailable = (findPtBinary("lyrebird") ?: findPtBinary("obfs4proxy")) != null
        return CensorshipDetector.TransportStrategy.values().map { strategy ->
            val available = when (strategy) {
                CensorshipDetector.TransportStrategy.VANILLA -> true
                CensorshipDetector.TransportStrategy.OBFS4 -> lyrebirdAvailable
                CensorshipDetector.TransportStrategy.WEBTUNNEL -> lyrebirdAvailable
                CensorshipDetector.TransportStrategy.MEEK_LITE -> lyrebirdAvailable
                CensorshipDetector.TransportStrategy.SNOWFLAKE -> lyrebirdAvailable || findPtBinary("snowflake-client") != null
            }

            StrategyInfo(
                strategy = strategy,
                available = available,
                active = strategy == _currentStrategy.value,
                description = censorshipDetector.getStrategyDescription(strategy)
            )
        }
    }

    data class StrategyInfo(
        val strategy: CensorshipDetector.TransportStrategy,
        val available: Boolean,
        val active: Boolean,
        val description: String
    )
}
