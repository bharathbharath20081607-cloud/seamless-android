@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * PRODUCTION: Circuit Breaker pattern for resilient relay/service connections.
 * Based on industry best practices (Netflix Hystrix pattern).
 * 
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failures exceeded threshold, requests fail fast
 * - HALF_OPEN: Testing if service recovered
 * 
 * Usage:
 * ```
 * val breaker = CircuitBreaker.forRelay("wss://relay.example.com")
 * if (breaker.allowRequest()) {
 *     try {
 *         sendToRelay(message)
 *         breaker.recordSuccess()
 *     } catch (e: Exception) {
 *         breaker.recordFailure()
 *     }
 * }
 * ```
 */
class CircuitBreaker private constructor(
    private val name: String,
    private val config: Config = Config()
) {
    companion object {
        private const val TAG = "CircuitBreaker"
        
        // Registry of circuit breakers by name (e.g., relay URL)
        private val breakers = ConcurrentHashMap<String, CircuitBreaker>()
        
        /**
         * Get or create a circuit breaker for a relay.
         */
        fun forRelay(relayUrl: String): CircuitBreaker {
            return breakers.getOrPut(relayUrl) { CircuitBreaker(relayUrl) }
        }
        
        /**
         * Get all circuit breaker states for monitoring.
         */
        fun getAllStates(): Map<String, State> {
            return breakers.mapValues { it.value.state.value }
        }
        
        /**
         * Reset all circuit breakers.
         */
        fun resetAll() {
            breakers.values.forEach { it.reset() }
        }
        
        /**
         * Remove a circuit breaker (e.g., when relay is removed).
         */
        fun remove(name: String) {
            breakers.remove(name)
        }
    }
    
    /**
     * Configuration for circuit breaker behavior.
     */
    data class Config(
        val failureThreshold: Int = 5,           // Failures before opening
        val failureWindowMs: Long = 60_000L,     // Window to count failures (1 min)
        val openDurationMs: Long = 30_000L,      // Time circuit stays open
        val halfOpenMaxRequests: Int = 1,        // Requests to test in half-open
        val successThreshold: Int = 2            // Successes in half-open to close
    )
    
    /**
     * Circuit breaker states.
     */
    enum class State {
        CLOSED,      // Normal operation
        OPEN,        // Failing fast, not allowing requests
        HALF_OPEN    // Testing if service recovered
    }
    
    private val _state = MutableStateFlow(State.CLOSED)
    val state: StateFlow<State> = _state.asStateFlow()
    
    // Failure tracking
    private val failureCount = AtomicInteger(0)
    private val firstFailureTime = AtomicLong(0L)
    
    // Open state tracking
    private val openedAt = AtomicLong(0L)
    
    // Half-open state tracking
    private val halfOpenRequests = AtomicInteger(0)
    private val halfOpenSuccesses = AtomicInteger(0)
    
    /**
     * Check if a request should be allowed.
     */
    fun allowRequest(): Boolean {
        return when (_state.value) {
            State.CLOSED -> true
            State.OPEN -> {
                // Check if open duration has passed
                if (System.currentTimeMillis() - openedAt.get() >= config.openDurationMs) {
                    transitionToHalfOpen()
                    true
                } else {
                    Log.d(TAG, "[$name] Circuit OPEN - request blocked")
                    false
                }
            }
            State.HALF_OPEN -> {
                // Allow limited requests in half-open
                if (halfOpenRequests.incrementAndGet() <= config.halfOpenMaxRequests) {
                    true
                } else {
                    Log.d(TAG, "[$name] Circuit HALF_OPEN - max test requests reached")
                    false
                }
            }
        }
    }
    
    /**
     * Record a successful request.
     */
    fun recordSuccess() {
        when (_state.value) {
            State.CLOSED -> {
                // Reset failure count on success
                failureCount.set(0)
                firstFailureTime.set(0L)
            }
            State.HALF_OPEN -> {
                val successes = halfOpenSuccesses.incrementAndGet()
                if (successes >= config.successThreshold) {
                    transitionToClosed()
                }
            }
            State.OPEN -> {
                // Shouldn't happen, but handle gracefully
                transitionToHalfOpen()
            }
        }
    }
    
    /**
     * Record a failed request.
     */
    fun recordFailure() {
        when (_state.value) {
            State.CLOSED -> {
                val now = System.currentTimeMillis()
                val firstFailure = firstFailureTime.get()
                
                // Reset window if expired
                if (firstFailure > 0 && now - firstFailure > config.failureWindowMs) {
                    failureCount.set(0)
                    firstFailureTime.set(now)
                }
                
                // Set first failure time if not set
                firstFailureTime.compareAndSet(0L, now)
                
                val failures = failureCount.incrementAndGet()
                Log.d(TAG, "[$name] Failure recorded: $failures/${config.failureThreshold}")
                
                if (failures >= config.failureThreshold) {
                    transitionToOpen()
                }
            }
            State.HALF_OPEN -> {
                // Any failure in half-open goes back to open
                transitionToOpen()
            }
            State.OPEN -> {
                // Already open, nothing to do
            }
        }
    }
    
    /**
     * Manually reset the circuit breaker.
     */
    fun reset() {
        _state.value = State.CLOSED
        failureCount.set(0)
        firstFailureTime.set(0L)
        openedAt.set(0L)
        halfOpenRequests.set(0)
        halfOpenSuccesses.set(0)
        Log.i(TAG, "[$name] Circuit breaker RESET")
    }
    
    private fun transitionToOpen() {
        _state.value = State.OPEN
        openedAt.set(System.currentTimeMillis())
        Log.w(TAG, "[$name] Circuit OPENED - blocking requests for ${config.openDurationMs}ms")
    }
    
    private fun transitionToHalfOpen() {
        _state.value = State.HALF_OPEN
        halfOpenRequests.set(0)
        halfOpenSuccesses.set(0)
        Log.i(TAG, "[$name] Circuit HALF_OPEN - testing recovery")
    }
    
    private fun transitionToClosed() {
        _state.value = State.CLOSED
        failureCount.set(0)
        firstFailureTime.set(0L)
        halfOpenRequests.set(0)
        halfOpenSuccesses.set(0)
        Log.i(TAG, "[$name] Circuit CLOSED - service recovered")
    }
    
    /**
     * Get current state info for debugging.
     */
    fun getStateInfo(): StateInfo {
        return StateInfo(
            name = name,
            state = _state.value,
            failureCount = failureCount.get(),
            openedAt = if (openedAt.get() > 0) openedAt.get() else null,
            halfOpenSuccesses = halfOpenSuccesses.get()
        )
    }
    
    data class StateInfo(
        val name: String,
        val state: State,
        val failureCount: Int,
        val openedAt: Long?,
        val halfOpenSuccesses: Int
    )
}

/**
 * Extension: Execute with circuit breaker protection.
 */
suspend fun <T> CircuitBreaker.execute(block: suspend () -> T): T? {
    if (!allowRequest()) return null
    
    return try {
        val result = block()
        recordSuccess()
        result
    } catch (e: Exception) {
        recordFailure()
        throw e
    }
}
