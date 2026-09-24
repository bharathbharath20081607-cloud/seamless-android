package com.seamless.android.crypto

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * RatchetSessionManager - Manages Double Ratchet sessions for all contacts
 * 
 * Responsibilities:
 * - Store and retrieve ratchet sessions per contact
 * - Persist sessions encrypted using Android Keystore
 * - Handle session initialization after Noise handshake
 * - Provide thread-safe access to sessions
 */
class RatchetSessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RatchetSessionManager"
        private const val PREFS_NAME = "seamless_ratchet_sessions"
        private const val KEY_SESSION_IDS = "session_ids"
        
        @Volatile
        private var instance: RatchetSessionManager? = null
        
        fun getInstance(context: Context): RatchetSessionManager {
            return instance ?: synchronized(this) {
                instance ?: RatchetSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val gson = Gson()
    private val mutex = Mutex()
    
    // In-memory cache of active sessions
    private val sessions = ConcurrentHashMap<String, DoubleRatchet>()
    
    // Encrypted SharedPreferences for session persistence
    @Suppress("DEPRECATION")
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    init {
        loadAllSessions()
    }
    
    /**
     * Initialize a new session as the initiator (after Noise handshake)
     */
    suspend fun initSessionAsInitiator(
        contactId: String,
        sharedSecret: ByteArray,
        remoteDHPublicKey: ByteArray
    ): DoubleRatchet = mutex.withLock {
        Log.i(TAG, "🔐 Initializing ratchet session as initiator for: $contactId")
        
        val ratchet = DoubleRatchet.initAsInitiator(sharedSecret, remoteDHPublicKey)
        sessions[contactId] = ratchet
        persistSession(contactId, ratchet)
        
        ratchet
    }
    
    /**
     * Initialize a new session as the responder (after Noise handshake)
     */
    suspend fun initSessionAsResponder(
        contactId: String,
        sharedSecret: ByteArray,
        ourDHKeyPair: X25519KeyPair
    ): DoubleRatchet = mutex.withLock {
        Log.i(TAG, "🔐 Initializing ratchet session as responder for: $contactId")
        
        val ratchet = DoubleRatchet.initAsResponder(sharedSecret, ourDHKeyPair)
        sessions[contactId] = ratchet
        persistSession(contactId, ratchet)
        
        ratchet
    }
    
    /**
     * Get existing session for contact
     */
    fun getSession(contactId: String): DoubleRatchet? {
        return sessions[contactId]
    }
    
    /**
     * Check if session exists for contact
     */
    fun hasSession(contactId: String): Boolean {
        return sessions.containsKey(contactId)
    }
    
    /**
     * Encrypt a message using the contact's ratchet session
     */
    suspend fun encrypt(contactId: String, plaintext: ByteArray): RatchetMessage = mutex.withLock {
        val ratchet = sessions[contactId] 
            ?: throw IllegalStateException("No ratchet session for contact: $contactId")
        
        val message = ratchet.encrypt(plaintext)
        
        // Persist updated state
        persistSession(contactId, ratchet)
        
        message
    }
    
    /**
     * Decrypt a message using the contact's ratchet session
     */
    suspend fun decrypt(contactId: String, message: RatchetMessage): ByteArray = mutex.withLock {
        val ratchet = sessions[contactId]
            ?: throw IllegalStateException("No ratchet session for contact: $contactId")
        
        val plaintext = ratchet.decrypt(message)
        
        // Persist updated state (ratchet may have advanced)
        persistSession(contactId, ratchet)
        
        plaintext
    }
    
    /**
     * Get the current DH public key for a session (for handshake exchange)
     */
    fun getPublicKey(contactId: String): ByteArray? {
        return sessions[contactId]?.getPublicKey()
    }
    
    /**
     * Delete session for contact
     */
    suspend fun deleteSession(contactId: String) = mutex.withLock {
        sessions.remove(contactId)
        encryptedPrefs.edit().remove("session_$contactId").apply()
        
        // Update session IDs list
        val sessionIds = getSessionIds().toMutableSet()
        sessionIds.remove(contactId)
        saveSessionIds(sessionIds)
        
        Log.i(TAG, "🗑️ Deleted ratchet session for: $contactId")
    }
    
    /**
     * Delete all sessions
     */
    suspend fun deleteAllSessions() = mutex.withLock {
        sessions.clear()
        encryptedPrefs.edit().clear().apply()
        Log.i(TAG, "🗑️ Deleted all ratchet sessions")
    }
    
    /**
     * Get count of active sessions
     */
    fun getSessionCount(): Int = sessions.size
    
    /**
     * Persist session to encrypted storage
     */
    private fun persistSession(contactId: String, ratchet: DoubleRatchet) {
        try {
            val state = ratchet.exportState()
            val serialized = serializeState(state)
            
            encryptedPrefs.edit()
                .putString("session_$contactId", serialized)
                .apply()
            
            // Track session ID
            val sessionIds = getSessionIds().toMutableSet()
            sessionIds.add(contactId)
            saveSessionIds(sessionIds)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist session for $contactId: ${e.message}")
        }
    }
    
    /**
     * Load all sessions from encrypted storage
     */
    private fun loadAllSessions() {
        try {
            val sessionIds = getSessionIds()
            Log.d(TAG, "📂 Loading ${sessionIds.size} ratchet sessions...")
            
            for (contactId in sessionIds) {
                try {
                    val serialized = encryptedPrefs.getString("session_$contactId", null)
                    if (serialized != null) {
                        val state = deserializeState(serialized)
                        val ratchet = DoubleRatchet.restore(state)
                        sessions[contactId] = ratchet
                        Log.d(TAG, "✅ Loaded session for: $contactId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load session for $contactId: ${e.message}")
                }
            }
            
            Log.i(TAG, "📦 Loaded ${sessions.size} ratchet sessions")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sessions: ${e.message}")
        }
    }
    
    /**
     * Get list of stored session IDs
     */
    private fun getSessionIds(): Set<String> {
        val json = encryptedPrefs.getString(KEY_SESSION_IDS, "[]")
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Save list of session IDs
     */
    private fun saveSessionIds(ids: Set<String>) {
        val json = gson.toJson(ids)
        encryptedPrefs.edit().putString(KEY_SESSION_IDS, json).apply()
    }
    
    /**
     * Serialize ratchet state to JSON
     */
    private fun serializeState(state: RatchetState): String {
        // Convert byte arrays to Base64 for JSON serialization
        val serializable = SerializableRatchetState(
            dhPrivateKey = android.util.Base64.encodeToString(state.dhPrivateKey, android.util.Base64.NO_WRAP),
            dhPublicKey = android.util.Base64.encodeToString(state.dhPublicKey, android.util.Base64.NO_WRAP),
            remoteDHPublicKey = state.remoteDHPublicKey?.let { 
                android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) 
            },
            rootKey = android.util.Base64.encodeToString(state.rootKey, android.util.Base64.NO_WRAP),
            sendingChainKey = state.sendingChainKey?.let { 
                android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) 
            },
            receivingChainKey = state.receivingChainKey?.let { 
                android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) 
            },
            sendMessageNumber = state.sendMessageNumber,
            receiveMessageNumber = state.receiveMessageNumber,
            previousSendingChainLength = state.previousSendingChainLength,
            skippedMessageKeys = state.skippedMessageKeys.map { (key, value) ->
                SerializableSkippedKey(
                    dhPublicKey = android.util.Base64.encodeToString(key.first, android.util.Base64.NO_WRAP),
                    messageNumber = key.second,
                    messageKey = android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)
                )
            }
        )
        return gson.toJson(serializable)
    }
    
    /**
     * Deserialize ratchet state from JSON
     */
    private fun deserializeState(json: String): RatchetState {
        val serializable = gson.fromJson(json, SerializableRatchetState::class.java)
        
        return RatchetState(
            dhPrivateKey = android.util.Base64.decode(serializable.dhPrivateKey, android.util.Base64.NO_WRAP),
            dhPublicKey = android.util.Base64.decode(serializable.dhPublicKey, android.util.Base64.NO_WRAP),
            remoteDHPublicKey = serializable.remoteDHPublicKey?.let { 
                android.util.Base64.decode(it, android.util.Base64.NO_WRAP) 
            },
            rootKey = android.util.Base64.decode(serializable.rootKey, android.util.Base64.NO_WRAP),
            sendingChainKey = serializable.sendingChainKey?.let { 
                android.util.Base64.decode(it, android.util.Base64.NO_WRAP) 
            },
            receivingChainKey = serializable.receivingChainKey?.let { 
                android.util.Base64.decode(it, android.util.Base64.NO_WRAP) 
            },
            sendMessageNumber = serializable.sendMessageNumber,
            receiveMessageNumber = serializable.receiveMessageNumber,
            previousSendingChainLength = serializable.previousSendingChainLength,
            skippedMessageKeys = serializable.skippedMessageKeys.associate { skipped ->
                Pair(
                    android.util.Base64.decode(skipped.dhPublicKey, android.util.Base64.NO_WRAP),
                    skipped.messageNumber
                ) to android.util.Base64.decode(skipped.messageKey, android.util.Base64.NO_WRAP)
            }
        )
    }
}

/**
 * JSON-serializable version of RatchetState
 */
private data class SerializableRatchetState(
    val dhPrivateKey: String,
    val dhPublicKey: String,
    val remoteDHPublicKey: String?,
    val rootKey: String,
    val sendingChainKey: String?,
    val receivingChainKey: String?,
    val sendMessageNumber: Int,
    val receiveMessageNumber: Int,
    val previousSendingChainLength: Int,
    val skippedMessageKeys: List<SerializableSkippedKey>
)

private data class SerializableSkippedKey(
    val dhPublicKey: String,
    val messageNumber: Int,
    val messageKey: String
)
