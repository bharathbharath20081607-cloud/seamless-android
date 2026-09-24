package com.seamless.android.noise

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * SeamlessRatchetManager - Manages SeamlessRatchet sessions for all peers
 * 
 * This integrates with the existing NoiseEncryptionService:
 * 1. Noise handshake establishes initial shared secret
 * 2. SeamlessRatchet takes over for message encryption with per-message keys
 * 3. Both work together for ultimate security
 */
class SeamlessRatchetManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SeamlessRatchetManager"
        
        @Volatile
        private var INSTANCE: SeamlessRatchetManager? = null
        
        fun getInstance(context: Context): SeamlessRatchetManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SeamlessRatchetManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    // Active ratchet sessions by fingerprint (not peer ID - fingerprint is stable)
    private val sessions = ConcurrentHashMap<String, SeamlessRatchet>()
    
    // Our identity key pair (loaded from NoiseEncryptionService)
    private var localKeyPair: SeamlessRatchet.KeyPair? = null
    
    /**
     * Initialize with our identity key pair
     */
    fun initialize(privateKey: ByteArray, publicKey: ByteArray) {
        localKeyPair = SeamlessRatchet.KeyPair(privateKey.clone(), publicKey.clone())
        Log.i(TAG, "SeamlessRatchetManager initialized")
    }
    
    /**
     * Create or get ratchet session for a peer
     * Called after Noise handshake completes
     */
    fun getOrCreateSession(fingerprint: String, remotePublicKey: ByteArray): SeamlessRatchet {
        return sessions.getOrPut(fingerprint) {
            val keyPair = localKeyPair 
                ?: throw IllegalStateException("SeamlessRatchetManager not initialized")
            
            Log.d(TAG, "Creating new SeamlessRatchet session for ${fingerprint.take(16)}...")
            SeamlessRatchet(keyPair, remotePublicKey)
        }
    }
    
    /**
     * Encrypt message with forward secrecy
     * Each message gets a unique key that is immediately discarded
     */
    fun encrypt(plaintext: ByteArray, fingerprint: String): ByteArray? {
        val session = sessions[fingerprint]
        if (session == null) {
            Log.w(TAG, "No session for $fingerprint - Noise handshake required first")
            return null
        }
        
        return try {
            session.encrypt(plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            null
        }
    }
    
    /**
     * Decrypt message with forward secrecy
     */
    fun decrypt(ciphertext: ByteArray, fingerprint: String): ByteArray? {
        val session = sessions[fingerprint]
        if (session == null) {
            Log.w(TAG, "No session for $fingerprint")
            return null
        }
        
        return try {
            session.decrypt(ciphertext)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security violation: ${e.message}")
            // Session may be compromised - destroy it
            removeSession(fingerprint)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }
    
    /**
     * Check if we have a ratchet session for a peer
     */
    fun hasSession(fingerprint: String): Boolean = sessions.containsKey(fingerprint)
    
    /**
     * Remove and destroy a session (zeroizes all keys)
     */
    fun removeSession(fingerprint: String) {
        sessions.remove(fingerprint)?.destroy()
        Log.d(TAG, "Removed session for ${fingerprint.take(16)}...")
    }
    
    /**
     * Destroy all sessions (called on logout/panic)
     */
    fun destroyAll() {
        sessions.values.forEach { it.destroy() }
        sessions.clear()
        localKeyPair?.privateKey?.fill(0)
        localKeyPair = null
        Log.i(TAG, "All sessions destroyed")
    }
    
    /**
     * Get number of active sessions
     */
    fun activeSessionCount(): Int = sessions.size
}
