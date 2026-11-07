@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

/**
 * HyperDrop Identity Manager
 * 
 * Inspired by AirDrop and Quick Share:
 * - Generates persistent device fingerprint for identification
 * - Creates fun, memorable device aliases (like "Swift Dolphin")
 * - Manages device visibility settings
 * 
 * Similar to how AirDrop shows contact names and Quick Share shows device names,
 * HyperDrop uses quirky aliases for a friendly, memorable experience.
 */
object HyperDropIdentity {
    private const val PREFS_NAME = "hyperdrop_identity"
    private const val KEY_FINGERPRINT = "device_fingerprint"
    private const val KEY_ALIAS = "device_alias"
    private const val KEY_VISIBILITY = "visibility_mode"
    private const val KEY_VISIBILITY_EXPIRY = "visibility_expiry"
    
    // Fun adjectives for device names (inspired by LocalSend's approach)
    private val adjectives = listOf(
        "Swift", "Cosmic", "Electric", "Mystic", "Neon", "Turbo", "Hyper",
        "Blazing", "Crystal", "Dynamic", "Ethereal", "Fierce", "Galactic",
        "Infinite", "Jade", "Kinetic", "Lunar", "Mighty", "Noble", "Omega",
        "Prism", "Quantum", "Radiant", "Stellar", "Thunder", "Ultra", "Vivid",
        "Warp", "Zenith", "Atomic", "Brilliant", "Charged", "Dazzling"
    )
    
    // Cool nouns for device names
    private val nouns = listOf(
        "Dolphin", "Tiger", "Phoenix", "Dragon", "Falcon", "Wolf", "Panther",
        "Eagle", "Hawk", "Lion", "Shark", "Cobra", "Jaguar", "Viper",
        "Thunder", "Storm", "Blaze", "Frost", "Shadow", "Spark", "Flash",
        "Nova", "Comet", "Orbit", "Pulse", "Wave", "Bolt", "Surge",
        "Nebula", "Quasar", "Cipher", "Vector", "Matrix", "Prism"
    )
    
    /**
     * Visibility modes inspired by AirDrop and Quick Share
     */
    enum class VisibilityMode {
        HIDDEN,              // Not discoverable (like AirDrop "Receiving Off")
        CONTACTS_ONLY,       // Only known contacts (like AirDrop "Contacts Only")
        EVERYONE_10_MIN,     // Visible for 10 minutes, then reverts (like AirDrop)
        EVERYONE             // Always visible (like Quick Share "Everyone")
    }
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize with application context
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get or generate a persistent device fingerprint
     * Used for device identification and authentication
     */
    fun getFingerprint(context: Context): String {
        init(context)
        
        var fingerprint = prefs?.getString(KEY_FINGERPRINT, null)
        if (fingerprint == null) {
            // Generate new fingerprint from UUID + device info
            // Avoid deprecated Build.SERIAL - use Android ID instead
            val uuid = UUID.randomUUID().toString()
            val androidId = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            val deviceInfo = "${Build.MANUFACTURER}:${Build.MODEL}:$androidId"
            val combined = "$uuid:$deviceInfo"
            
            // SHA-256 hash for consistent length
            fingerprint = sha256(combined)
            
            prefs?.edit()?.putString(KEY_FINGERPRINT, fingerprint)?.apply()
        }
        
        return fingerprint
    }
    
    /**
     * Get or generate a fun device alias
     * Regenerate with regenerateAlias() if user wants a new one
     */
    fun getAlias(context: Context): String {
        init(context)
        
        var alias = prefs?.getString(KEY_ALIAS, null)
        if (alias == null) {
            alias = generateAlias()
            prefs?.edit()?.putString(KEY_ALIAS, alias)?.apply()
        }
        
        return alias
    }
    
    /**
     * Generate a new random alias
     */
    fun regenerateAlias(context: Context): String {
        init(context)
        
        val alias = generateAlias()
        prefs?.edit()?.putString(KEY_ALIAS, alias)?.apply()
        
        return alias
    }
    
    /**
     * Get device model for display (like Quick Share shows "Pixel 8")
     */
    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        
        // Avoid duplicating manufacturer in model name
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }
    
    /**
     * Get current visibility mode
     */
    fun getVisibilityMode(context: Context): VisibilityMode {
        init(context)
        
        // Check if temporary visibility has expired
        val expiry = prefs?.getLong(KEY_VISIBILITY_EXPIRY, 0L) ?: 0L
        if (expiry > 0 && System.currentTimeMillis() > expiry) {
            // Expired - revert to CONTACTS_ONLY (like AirDrop behavior)
            setVisibilityMode(context, VisibilityMode.CONTACTS_ONLY)
            return VisibilityMode.CONTACTS_ONLY
        }
        
        val modeOrdinal = prefs?.getInt(KEY_VISIBILITY, VisibilityMode.EVERYONE.ordinal)
            ?: VisibilityMode.EVERYONE.ordinal
        
        return VisibilityMode.entries.getOrElse(modeOrdinal) { VisibilityMode.EVERYONE }
    }
    
    /**
     * Set visibility mode
     */
    fun setVisibilityMode(context: Context, mode: VisibilityMode) {
        init(context)
        
        val expiry = if (mode == VisibilityMode.EVERYONE_10_MIN) {
            System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        } else {
            0L
        }
        
        prefs?.edit()
            ?.putInt(KEY_VISIBILITY, mode.ordinal)
            ?.putLong(KEY_VISIBILITY_EXPIRY, expiry)
            ?.apply()
    }
    
    /**
     * Get remaining time for temporary visibility (in seconds)
     * Returns -1 if not in temporary mode
     */
    fun getVisibilityTimeRemaining(context: Context): Int {
        init(context)
        
        val expiry = prefs?.getLong(KEY_VISIBILITY_EXPIRY, 0L) ?: 0L
        if (expiry <= 0) return -1
        
        val remaining = (expiry - System.currentTimeMillis()) / 1000
        return if (remaining > 0) remaining.toInt() else 0
    }
    
    /**
     * Check if device should be discoverable to a specific fingerprint
     * Used for "Contacts Only" mode
     */
    fun isDiscoverableTo(context: Context, senderFingerprint: String, isKnownContact: Boolean): Boolean {
        return when (getVisibilityMode(context)) {
            VisibilityMode.HIDDEN -> false
            VisibilityMode.CONTACTS_ONLY -> isKnownContact
            VisibilityMode.EVERYONE_10_MIN -> true
            VisibilityMode.EVERYONE -> true
        }
    }
    
    private fun generateAlias(): String {
        val adjective = adjectives.random()
        val noun = nouns.random()
        return "$adjective $noun"
    }
    
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
