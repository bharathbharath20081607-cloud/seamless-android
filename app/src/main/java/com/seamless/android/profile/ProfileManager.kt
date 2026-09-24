package com.seamless.android.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Data model for the local user's profile.
 */
data class UserProfile(
    val displayName: String,
    val avatarUri: String?, // String URI or path to internal storage
    val bio: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Manages secure storage and retrieval of the user's profile.
 * Uses EncryptedSharedPreferences to keep profile data private.
 */
class ProfileManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ProfileManager"
        private const val PREFS_NAME = "bitchat_profile"
        private const val FALLBACK_PREFS_NAME = "bitchat_profile_fallback"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_BIO = "bio"
        private const val KEY_UPDATED_AT = "updated_at"

        @Volatile
        private var INSTANCE: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: android.content.SharedPreferences
    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    init {
        // Try to use EncryptedSharedPreferences, fall back to plain if it fails
        @Suppress("DEPRECATION")
        prefs = try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ EncryptedSharedPreferences failed, using fallback: ${e.message}")
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
        
        // Load initial state
        loadProfile()
    }

    private fun loadProfile() {
        val name = prefs.getString(KEY_DISPLAY_NAME, null)
        if (name != null) {
            val avatar = prefs.getString(KEY_AVATAR_URI, null)
            val bio = prefs.getString(KEY_BIO, "") ?: ""
            val updated = prefs.getLong(KEY_UPDATED_AT, 0L)
            
            _currentUserProfile.value = UserProfile(name, avatar, bio, updated)
            Log.d(TAG, "Loaded profile: $name")
        } else {
            _currentUserProfile.value = null
            Log.d(TAG, "No profile found")
        }
    }

    /**
     * Save or update the user profile.
     */
    fun saveProfile(displayName: String, avatarUri: Uri?, bio: String = "") {
        val uriString = avatarUri?.toString()
        val now = System.currentTimeMillis()

        prefs.edit().apply {
            putString(KEY_DISPLAY_NAME, displayName)
            if (uriString != null) {
                putString(KEY_AVATAR_URI, uriString)
            } else {
                remove(KEY_AVATAR_URI)
            }
            putString(KEY_BIO, bio)
            putLong(KEY_UPDATED_AT, now)
            apply()
        }

        _currentUserProfile.value = UserProfile(displayName, uriString, bio, now)
        Log.d(TAG, "Saved profile: $displayName")
    }

    /**
     * Copies a selected image to internal app storage to ensure it persists securely.
     * Returns the Uri of the saved internal file.
     */
    fun saveAvatarImage(sourceUri: Uri): Uri? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null
            
            // Create secure directory for avatars if needed
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdir()
            
            // Create new file with UUID
            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            val destFile = File(avatarDir, fileName)
            
            // Copy stream
            destFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            // Return file URI
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar image", e)
            null
        }
    }

    /**
     * Clear profile data (e.g., for panic mode).
     */
    fun clearProfile() {
        prefs.edit().clear().apply()
        _currentUserProfile.value = null
        
        // Also clear stored avatars
        try {
            val avatarDir = File(context.filesDir, "avatars")
            if (avatarDir.exists()) {
                avatarDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear avatar files", e)
        }
        
        Log.d(TAG, "Profile cleared")
    }
    
    /**
     * Check if a profile is set up.
     */
    fun hasProfile(): Boolean {
        return _currentUserProfile.value != null
    }
}
