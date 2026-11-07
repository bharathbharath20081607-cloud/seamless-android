@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.util

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * PRODUCTION: Exponential Backoff retry utility for robust network operations.
 * Based on WhatsApp/Signal patterns for intelligent retry delays.
 * 
 * Usage:
 * ```
 * val result = ExponentialBackoff.retry {
 *     // Your operation that might fail
 *     sendMessage(content)
 * }
 * ```
 */
object ExponentialBackoff {
    
    /**
     * Default configuration for most operations
     */
    const val DEFAULT_INITIAL_DELAY_MS = 1000L
    const val DEFAULT_MAX_DELAY_MS = 60000L
    const val DEFAULT_MULTIPLIER = 2.0
    const val DEFAULT_MAX_RETRIES = 5
    
    /**
     * Fast retry configuration for time-sensitive operations
     */
    const val FAST_INITIAL_DELAY_MS = 500L
    const val FAST_MAX_DELAY_MS = 8000L
    const val FAST_MAX_RETRIES = 3
    
    /**
     * Slow retry configuration for background operations (battery-friendly)
     */
    const val SLOW_INITIAL_DELAY_MS = 5000L
    const val SLOW_MAX_DELAY_MS = 300000L // 5 minutes
    const val SLOW_MAX_RETRIES = 10
    
    /**
     * Calculate the delay for a given attempt number.
     * 
     * @param attempt The current attempt number (0-indexed)
     * @param initialDelayMs The initial delay in milliseconds
     * @param maxDelayMs The maximum delay cap in milliseconds
     * @param multiplier The multiplier for exponential growth (default 2.0)
     * @param jitterFactor Random jitter factor (0.0-1.0) to prevent thundering herd
     * @return The calculated delay in milliseconds
     */
    fun calculateDelay(
        attempt: Int,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        multiplier: Double = DEFAULT_MULTIPLIER,
        jitterFactor: Double = 0.1
    ): Long {
        val exponentialDelay = initialDelayMs * multiplier.pow(attempt.toDouble())
        val cappedDelay = min(exponentialDelay.toLong(), maxDelayMs)
        
        // Add random jitter to prevent thundering herd problem
        val jitter = (cappedDelay * jitterFactor * Math.random()).toLong()
        return cappedDelay + jitter
    }
    
    /**
     * Execute a block with exponential backoff retry.
     * 
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay between retries
     * @param maxDelayMs Maximum delay cap
     * @param multiplier Delay multiplier for each retry
     * @param shouldRetry Predicate to determine if retry should be attempted for given exception
     * @param onRetry Callback invoked before each retry with attempt number and exception
     * @param block The suspend function to execute
     * @return Result of the block execution
     * @throws Exception if all retries are exhausted
     */
    suspend fun <T> retry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        multiplier: Double = DEFAULT_MULTIPLIER,
        shouldRetry: (Exception) -> Boolean = { true },
        onRetry: (attempt: Int, exception: Exception) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                
                if (attempt >= maxRetries || !shouldRetry(e)) {
                    throw e
                }
                
                onRetry(attempt, e)
                
                val delayMs = calculateDelay(attempt, initialDelayMs, maxDelayMs, multiplier)
                delay(delayMs)
            }
        }
        
        throw lastException ?: IllegalStateException("Retry exhausted without exception")
    }
    
    /**
     * Execute a block with fast retry (for time-sensitive operations).
     */
    suspend fun <T> retryFast(
        shouldRetry: (Exception) -> Boolean = { true },
        onRetry: (attempt: Int, exception: Exception) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T = retry(
        maxRetries = FAST_MAX_RETRIES,
        initialDelayMs = FAST_INITIAL_DELAY_MS,
        maxDelayMs = FAST_MAX_DELAY_MS,
        shouldRetry = shouldRetry,
        onRetry = onRetry,
        block = block
    )
    
    /**
     * Execute a block with slow retry (for background/battery-friendly operations).
     */
    suspend fun <T> retrySlow(
        shouldRetry: (Exception) -> Boolean = { true },
        onRetry: (attempt: Int, exception: Exception) -> Unit = { _, _ -> },
        block: suspend () -> T
    ): T = retry(
        maxRetries = SLOW_MAX_RETRIES,
        initialDelayMs = SLOW_INITIAL_DELAY_MS,
        maxDelayMs = SLOW_MAX_DELAY_MS,
        shouldRetry = shouldRetry,
        onRetry = onRetry,
        block = block
    )
    
    /**
     * Try once with no retry - useful for transitioning legacy code.
     */
    suspend fun <T> tryOnce(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Extension function: Retry with exponential backoff on any suspend function.
 */
suspend fun <T> (suspend () -> T).withRetry(
    maxRetries: Int = ExponentialBackoff.DEFAULT_MAX_RETRIES,
    onRetry: (attempt: Int, exception: Exception) -> Unit = { _, _ -> }
): T = ExponentialBackoff.retry(maxRetries = maxRetries, onRetry = onRetry, block = this)
