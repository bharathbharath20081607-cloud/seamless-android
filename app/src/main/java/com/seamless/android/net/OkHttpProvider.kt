@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralized OkHttp provider to ensure all network traffic honors Tor settings.
 */
object OkHttpProvider {
    private val httpClientRef = AtomicReference<OkHttpClient?>(null)
    private val wsClientRef = AtomicReference<OkHttpClient?>(null)
    private val directWsClientRef = AtomicReference<OkHttpClient?>(null)
    private val onionHttpClientRef = AtomicReference<Pair<Int, OkHttpClient>?>(null)

    fun reset() {
        httpClientRef.set(null)
        wsClientRef.set(null)
        onionHttpClientRef.set(null)
    }

    fun httpClient(): OkHttpClient {
        httpClientRef.get()?.let { return it }
        val client = baseBuilderForCurrentProxy()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        httpClientRef.set(client)
        return client
    }

    /**
     * Dedicated HTTP client for v3 `.onion` hidden-service requests (e.g., MailboxClient).
     * Always routes through a local Tor SOCKS5 proxy (never clearnet) and uses generous
     * timeouts suitable for 6-hop Tor rendezvous circuit building.
     */
    fun torOnionHttpClient(context: Context? = null): OkHttpClient {
        val socks = TorManager.currentBestSocksAddress(context)
            ?: TorManager.currentSocksAddress()
            ?: InetSocketAddress("127.0.0.1", TorDaemonManager.HIDDEN_SERVICE_SOCKS_PORT)
        val cached = onionHttpClientRef.get()
        if (cached != null && cached.first == socks.port) {
            return cached.second
        }
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, socks))
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        onionHttpClientRef.set(socks.port to client)
        return client
    }

    fun webSocketClient(): OkHttpClient {
        wsClientRef.get()?.let { return it }
        val client = baseBuilderForCurrentProxy()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Keep open for WebSockets
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS) // Keep-alive
            .retryOnConnectionFailure(true)
            .build()
        wsClientRef.set(client)
        return client
    }

    /**
     * Direct WebSocket client (bypasses SOCKS proxy) used when Tor is still bootstrapping
     * or when a Nostr relay rejects/blocks Tor exit node connections.
     */
    fun directWebSocketClient(): OkHttpClient {
        directWsClientRef.get()?.let { return it }
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        directWsClientRef.set(client)
        return client
    }

    private fun baseBuilderForCurrentProxy(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        val socks: InetSocketAddress? = if (TorManager.isProxyEnabled()) {
            TorManager.currentBestSocksAddress() ?: TorManager.currentSocksAddress()
        } else {
            null
        }
        if (socks != null) {
            val proxy = Proxy(Proxy.Type.SOCKS, socks)
            builder.proxy(proxy)
        }
        return builder
    }
}
