package com.seamless.android.ui.privacy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages user preferences for privacy features:
 * - App Lock (biometric authentication)
 * - Incognito Mode (screenshot/screen-record blocking)
 * - Disappearing Messages default TTL
 */
object PrivacyPreferenceManager {
    private const val PREFS_NAME = "seamless_privacy_prefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_INCOGNITO_MODE = "incognito_mode"
    private const val KEY_DISAPPEARING_TTL = "disappearing_ttl_ms"

    private val _appLockEnabled = MutableStateFlow(false)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled

    private val _incognitoMode = MutableStateFlow(false)
    val incognitoMode: StateFlow<Boolean> = _incognitoMode

    // Disappearing messages TTL: 0 = off, else milliseconds
    private val _disappearingTtlMs = MutableStateFlow(0L)
    val disappearingTtlMs: StateFlow<Long> = _disappearingTtlMs

    private fun prefs(context: Context): SharedPreferences =
        run {
            val plain = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            try {
                val masterKey = androidx.security.crypto.MasterKey.Builder(context, androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val encrypted = androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    "${PREFS_NAME}_encrypted",
                    masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                if (plain.all.isNotEmpty()) {
                    val editor = encrypted.edit()
                    if (plain.contains(KEY_APP_LOCK_ENABLED)) editor.putBoolean(KEY_APP_LOCK_ENABLED, plain.getBoolean(KEY_APP_LOCK_ENABLED, false))
                    if (plain.contains(KEY_INCOGNITO_MODE)) editor.putBoolean(KEY_INCOGNITO_MODE, plain.getBoolean(KEY_INCOGNITO_MODE, false))
                    if (plain.contains(KEY_DISAPPEARING_TTL)) editor.putLong(KEY_DISAPPEARING_TTL, plain.getLong(KEY_DISAPPEARING_TTL, 0L))
                    editor.apply()
                    plain.edit().clear().apply()
                }
                encrypted
            } catch (_: Exception) {
                plain
            }
        }

    fun init(context: Context) {
        val p = prefs(context)
        _appLockEnabled.value = p.getBoolean(KEY_APP_LOCK_ENABLED, false)
        _incognitoMode.value = p.getBoolean(KEY_INCOGNITO_MODE, false)
        _disappearingTtlMs.value = p.getLong(KEY_DISAPPEARING_TTL, 0L)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _appLockEnabled.value = enabled
    }

    fun setIncognitoMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCOGNITO_MODE, enabled).apply()
        _incognitoMode.value = enabled
    }

    fun setDisappearingTtl(context: Context, ttlMs: Long) {
        prefs(context).edit().putLong(KEY_DISAPPEARING_TTL, ttlMs).apply()
        _disappearingTtlMs.value = ttlMs
    }

    /** User-friendly label for the current TTL */
    fun ttlLabel(ttlMs: Long): String = when {
        ttlMs <= 0L -> "Off"
        ttlMs <= 60_000L -> "${ttlMs / 1000}s"
        ttlMs <= 3_600_000L -> "${ttlMs / 60_000}m"
        ttlMs <= 86_400_000L -> "${ttlMs / 3_600_000}h"
        else -> "${ttlMs / 86_400_000}d"
    }
}
