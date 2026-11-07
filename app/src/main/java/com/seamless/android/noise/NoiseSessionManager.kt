@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.noise

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * SIMPLIFIED Noise session manager - focuses on core functionality only
 */
class NoiseSessionManager(
    private val localStaticPrivateKey: ByteArray,
    private val localStaticPublicKey: ByteArray
) {
    
    companion object {
        private const val TAG = "NoiseSessionManager"
    }
    
    private val sessions = ConcurrentHashMap<String, NoiseSession>()
    private val localPeerID: String = try {
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(localStaticPublicKey)
            .take(8)
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }
    
    // Callbacks
    var onSessionEstablished: ((String, ByteArray) -> Unit)? = null
    var onSessionFailed: ((String, Throwable) -> Unit)? = null
    
    // MARK: - Simple Session Management

    /**
     * Add new session for a peer
     */
    fun addSession(peerID: String, session: NoiseSession) {
        sessions[peerID] = session
        Log.d(TAG, "Added new session for $peerID")
    }

    /**
     * Get existing session for a peer
     */
    fun getSession(peerID: String): NoiseSession? {
        val session = sessions[peerID]
        return session
    }
    
    /**
     * Remove session for a peer
     */
    fun removeSession(peerID: String) {
        sessions[peerID]?.destroy()
        sessions.remove(peerID)
        Log.d(TAG, "Removed session for $peerID")
    }
    
    /**
     * SIMPLIFIED: Initiate handshake - no tie breaker, just start
     */
    fun initiateHandshake(peerID: String): ByteArray {
        Log.d(TAG, "initiateHandshake($peerID)")

        // Remove any existing session first
        val existing = getSession(peerID)
        if (existing != null && existing.isHandshaking() && (System.currentTimeMillis() - existing.getCreationTime() < 4000L)) { Log.d(TAG, "Handshake already in progress with $peerID (<4s old), skipping duplicate initiation"); throw IllegalStateException("Handshake already in progress with $peerID") }
        removeSession(peerID)
        
        // Create new session as initiator
        val session = NoiseSession(
            peerID = peerID,
            isInitiator = true,
            localStaticPrivateKey = localStaticPrivateKey,
            localStaticPublicKey = localStaticPublicKey
        )
        Log.d(TAG, "Storing new INITIATOR session for $peerID")
        addSession(peerID, session)
        
        try {
            val handshakeData = session.startHandshake()
            Log.d(TAG, "Started handshake with $peerID as INITIATOR")
            return handshakeData
        } catch (e: Exception) {
            sessions.remove(peerID)
            throw e
        }
    }
    
    /**
     * Handle incoming handshake message
     */
    fun processHandshakeMessage(peerID: String, message: ByteArray): ByteArray? {
        Log.d(TAG, "processHandshakeMessage($peerID, ${message.size} bytes)")
        
        try {
            var session = getSession(peerID)
            
            // If no session exists, or if we have an unestablished session and receive a fresh 32-byte Msg 1
            // (simultaneous handshake glare), create a new responder session to accept the peer's initiation.
            if (message.size == 32 && session != null && session.isHandshaking() && localPeerID.isNotEmpty() && localPeerID.lowercase() < peerID.lowercase()) { Log.d(TAG, "⚡ Glare tie-breaker won by local ($localPeerID < $peerID), keeping INITIATOR"); return null }
            if (session != null && session.isEstablished() && message.size != 32) { Log.d(TAG, "Ignoring late handshake msg (${message.size}B) for established session $peerID"); return null }
            if (session == null && message.size != 32) { Log.w(TAG, "Ignoring non-initial handshake msg (${message.size}B) with no session for $peerID"); return null }
            if (session == null || message.size == 32) {
                Log.d(TAG, "Creating new RESPONDER session for $peerID (glare=${session != null})")
                session = NoiseSession(
                    peerID = peerID,
                    isInitiator = false,
                    localStaticPrivateKey = localStaticPrivateKey,
                    localStaticPublicKey = localStaticPublicKey
                )
                addSession(peerID, session)
            }
            
            // Process handshake message
            val response = session.processHandshakeMessage(message)
            
            // Check if session is established
            if (session.isEstablished()) {
                Log.d(TAG, "✅ Session ESTABLISHED with $peerID")
                val remoteStaticKey = session.getRemoteStaticPublicKey()
                if (remoteStaticKey != null) {
                    onSessionEstablished?.invoke(peerID, remoteStaticKey)
                }
            }
            
            return response
            
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed with $peerID: ${e.message}")
            sessions.remove(peerID)
            onSessionFailed?.invoke(peerID, e)
            throw e
        }
    }
    
    /**
     * SIMPLIFIED: Encrypt data
     * PRODUCTION FIX: Returns null instead of throwing to prevent crashes
     */
    fun encrypt(data: ByteArray, peerID: String): ByteArray? {
        val session = getSession(peerID)
        if (session == null) {
            Log.w(TAG, "No session found for $peerID when trying to encrypt")
            return null
        }
        if (!session.isEstablished()) {
            Log.w(TAG, "Session not established with $peerID when trying to encrypt")
            return null
        }
        return try {
            session.encrypt(data)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed for $peerID: ${e.message}", e)
            null
        }
    }
    
    /**
     * SIMPLIFIED: Decrypt data
     * PRODUCTION FIX: Returns null instead of throwing to prevent crashes
     */
    fun decrypt(encryptedData: ByteArray, peerID: String): ByteArray? {
        val session = getSession(peerID)
        if (session == null) {
            Log.e(TAG, "No session found for $peerID when trying to decrypt")
            return null
        }
        if (!session.isEstablished()) {
            Log.e(TAG, "Session not established with $peerID when trying to decrypt")
            return null
        }
        return try {
            session.decrypt(encryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for $peerID: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if session is established with peer
     */
    fun hasEstablishedSession(peerID: String): Boolean {
        val hasSession = getSession(peerID)?.isEstablished() ?: false
        Log.d(TAG, "hasEstablishedSession($peerID): $hasSession")
        return hasSession
    }
    
    /**
     * Get session state for a peer (for UI state display)
     */
    fun getSessionState(peerID: String): NoiseSession.NoiseSessionState {
        return getSession(peerID)?.getState() ?: NoiseSession.NoiseSessionState.Uninitialized
    }
    
    /**
     * Get remote static public key for a peer (if session established)
     */
    fun getRemoteStaticKey(peerID: String): ByteArray? {
        return getSession(peerID)?.getRemoteStaticPublicKey()
    }
    
    /**
     * Get handshake hash for channel binding (if session established)
     */
    fun getHandshakeHash(peerID: String): ByteArray? {
        return getSession(peerID)?.getHandshakeHash()
    }
    
    /**
     * Get sessions that need rekeying based on time or message count
     */
    fun getSessionsNeedingRekey(): List<String> {
        return sessions.entries
            .filter { (_, session) -> 
                session.isEstablished() && session.needsRekey()
            }
            .map { it.key }
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): String = buildString {
        appendLine("=== Noise Session Manager Debug ===")
        appendLine("Active sessions: ${sessions.size}")
        appendLine("")
        
        if (sessions.isNotEmpty()) {
            appendLine("Sessions:")
            sessions.forEach { (peerID, session) ->
                appendLine("  $peerID: ${session.getState()}")
            }
        }
    }
    
    /**
     * Shutdown manager and clean up all sessions
     */
    fun shutdown() {
        sessions.values.forEach { it.destroy() }
        sessions.clear()
        Log.d(TAG, "Noise session manager shut down")
    }
}

/**
 * Session-related errors
 */
sealed class NoiseSessionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object SessionNotFound : NoiseSessionError("Session not found")
    object SessionNotEstablished : NoiseSessionError("Session not established")
    object InvalidState : NoiseSessionError("Session in invalid state")
    object HandshakeFailed : NoiseSessionError("Handshake failed")
    object AlreadyEstablished : NoiseSessionError("Session already established")
}
