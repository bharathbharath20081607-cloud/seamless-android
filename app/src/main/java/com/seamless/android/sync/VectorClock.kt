@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.sync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Vector Clock implementation for causal ordering.
 * Maps PeerID -> Counter.
 */
data class VectorClock(
    val clock: Map<String, Long> = emptyMap()
) {
    /**
     * Increment the clock for a specific peer (usually self).
     */
    fun increment(peerID: String): VectorClock {
        val current = clock[peerID] ?: 0L
        return copy(clock = clock + (peerID to current + 1))
    }

    /**
     * Merge with another vector clock (taking max of each entry).
     */
    fun merge(other: VectorClock): VectorClock {
        val merged = clock.toMutableMap()
        other.clock.forEach { (peer, time) ->
            val current = merged[peer] ?: 0L
            if (time > current) {
                merged[peer] = time
            }
        }
        return VectorClock(merged)
    }

    /**
     * Check if this clock happened before or is concurrent with another.
     * Returns true if all entries in this clock are <= other clock,
     * and at least one is <.
     */
    fun isBefore(other: VectorClock): Boolean {
        var hasSmaller = false
        val allKeys = clock.keys + other.clock.keys
        
        for (key in allKeys) {
            val myTime = clock[key] ?: 0L
            val otherTime = other.clock[key] ?: 0L
            
            if (myTime > otherTime) return false
            if (myTime < otherTime) hasSmaller = true
        }
        return hasSmaller
    }

    /**
     * Check if this clock is concurrent with another (neither happened before the other).
     */
    fun isConcurrent(other: VectorClock): Boolean {
        return !isBefore(other) && !other.isBefore(this) && this != other
    }

    fun toJson(): String {
        return Gson().toJson(clock)
    }

    companion object {
        fun fromJson(json: String): VectorClock {
            return try {
                val type = object : TypeToken<Map<String, Long>>() {}.type
                val map: Map<String, Long> = Gson().fromJson(json, type)
                VectorClock(map)
            } catch (e: Exception) {
                VectorClock()
            }
        }
    }
}
