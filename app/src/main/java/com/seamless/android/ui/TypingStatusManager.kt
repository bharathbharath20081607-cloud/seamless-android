@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages typing status for private chats
 * Handles sending typing indicators and tracking received typing status
 */
class TypingStatusManager {
    
    companion object {
        private const val TAG = "TypingStatusManager"
        private const val TYPING_TIMEOUT_MS = 3000L // Stop showing "typing" after 3 seconds
        private const val TYPING_SEND_THROTTLE_MS = 2000L // Don't send more than once per 2 seconds
        
        val shared = TypingStatusManager()
    }
    
    // Who is currently typing (peerID -> nickname)
    private val _typingPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingPeers: StateFlow<Map<String, String>> = _typingPeers.asStateFlow()
    
    // Track when we last sent our typing status
    private var lastTypingSentTime = 0L
    
    // Coroutine scope for timeout handling
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Timeout jobs for each peer
    private val timeoutJobs = ConcurrentHashMap<String, Job>()
    
    // Delegate for sending typing status
    var delegate: TypingStatusDelegate? = null
    
    /**
     * Called when user starts typing in a private chat
     */
    fun onUserTyping(recipientPeerID: String) {
        val now = System.currentTimeMillis()
        
        // Throttle: don't send too frequently
        if (now - lastTypingSentTime < TYPING_SEND_THROTTLE_MS) {
            return
        }
        
        lastTypingSentTime = now
        
        // Send typing indicator to the recipient
        delegate?.sendTypingStatus(recipientPeerID, isTyping = true)
        Log.d(TAG, "📝 Sent typing indicator to $recipientPeerID")
    }
    
    /**
     * Called when user stops typing or sends message
     */
    fun onUserStoppedTyping(recipientPeerID: String) {
        delegate?.sendTypingStatus(recipientPeerID, isTyping = false)
        Log.d(TAG, "✓ Sent stop typing to $recipientPeerID")
    }
    
    /**
     * Called when we receive a typing indicator from a peer
     */
    fun onTypingReceived(peerID: String, nickname: String, isTyping: Boolean) {
        if (isTyping) {
            Log.d(TAG, "📝 $nickname is typing...")
            
            // Add to typing peers
            _typingPeers.value = _typingPeers.value.toMutableMap().apply {
                put(peerID, nickname)
            }
            
            // Cancel existing timeout for this peer
            timeoutJobs[peerID]?.cancel()
            
            // Set timeout to auto-remove
            timeoutJobs[peerID] = scope.launch {
                delay(TYPING_TIMEOUT_MS)
                removeTypingPeer(peerID)
            }
        } else {
            removeTypingPeer(peerID)
        }
    }
    
    /**
     * Check if a specific peer is currently typing
     */
    fun isTyping(peerID: String): Boolean {
        return _typingPeers.value.containsKey(peerID)
    }
    
    /**
     * Get nickname of typing peer
     */
    fun getTypingNickname(peerID: String): String? {
        return _typingPeers.value[peerID]
    }
    
    private fun removeTypingPeer(peerID: String) {
        _typingPeers.value = _typingPeers.value.toMutableMap().apply {
            remove(peerID)
        }
        timeoutJobs.remove(peerID)?.cancel()
    }
    
    /**
     * Clear typing status for a peer (e.g., when they disconnect)
     */
    fun clearTypingForPeer(peerID: String) {
        removeTypingPeer(peerID)
    }
    
    /**
     * Clear all typing status
     */
    fun clearAll() {
        _typingPeers.value = emptyMap()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
    }
}

/**
 * Delegate for sending typing status packets
 */
interface TypingStatusDelegate {
    fun sendTypingStatus(recipientPeerID: String, isTyping: Boolean)
}
