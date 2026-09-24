package com.seamless.android

import android.app.Application
import com.seamless.android.nostr.RelayDirectory
import com.seamless.android.ui.theme.ThemePreferenceManager
import com.seamless.android.net.TorManager
import kotlinx.coroutines.launch

/**
 * Main application class for seamless Android
 */
class SeamlessApplication : Application() {
    
    companion object {
        lateinit var instance: SeamlessApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // PRODUCTION FIX: Install global uncaught exception handler to log crashes
        installGlobalExceptionHandler()
        
        // Initialize Tor first so any early network goes over Tor
        try { TorManager.init(this) } catch (e: Exception) { android.util.Log.e("SeamlessApp", "Tor init failed", e) }

        // Initialize theme preference
        ThemePreferenceManager.init(this)

        // Initialize debug preference manager (persists debug toggles)
        try { com.seamless.android.ui.debug.DebugPreferenceManager.init(this) } catch (e: Exception) { android.util.Log.e("SeamlessApp", "DebugPreference init failed", e) }

        // Ensure core mesh roles are enabled by default for production use
        // This prevents users from accidentally disabling GATT Server/Client via debug UI
        try {
            val dbg = com.seamless.android.ui.debug.DebugSettingsManager.getInstance()
            dbg.setGattServerEnabled(true)
            dbg.setGattClientEnabled(true)
            dbg.setPacketRelayEnabled(true)
        } catch (e: Exception) { android.util.Log.e("SeamlessApp", "DebugSettings defaults failed", e) }

        // TorManager already initialized above
        
        // PRODUCTION FIX: Offload heavy startup tasks to a background coroutine to prevent ANR.
        // This includes database operations, parsing assets, and decrypting keys.
        val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        appScope.launch {
            // Initialize relay directory (loads assets/nostr_relays.csv)
            try {
                RelayDirectory.initialize(this@SeamlessApplication)
                android.util.Log.d("SeamlessApp", "✅ Relay directory initialized")
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "RelayDirectory init failed", e)
            }

            // Initialize LocationNotesManager dependencies early so sheet subscriptions can start immediately
            try {
                com.seamless.android.nostr.LocationNotesInitializer.initialize(this@SeamlessApplication)
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "LocationNotes init failed", e)
            }

            // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
            try {
                com.seamless.android.favorites.FavoritesPersistenceService.initialize(this@SeamlessApplication)
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "FavoritesPersistence init failed", e)
            }

            // PRODUCTION: Initialize NIP-65 relay registry for Gossip model (intelligent routing)
            try {
                com.seamless.android.nostr.Nip65RelayRegistry.initialize(this@SeamlessApplication)
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "Nip65RelayRegistry init failed", e)
            }

            // Warm up Nostr identity to ensure npub is available for favorite notifications
            try {
                com.seamless.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this@SeamlessApplication)
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "NostrIdentity warmup failed", e)
            }

            // PRODUCTION FIX: Initialize Nostr relay connections for long-distance messaging
            // This is REQUIRED for private messages to work over the internet!
            try {
                com.seamless.android.nostr.NostrClient.getInstance(this@SeamlessApplication).initialize()
                android.util.Log.d("SeamlessApp", "✅ Nostr client initialized - relays connecting")
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "NostrClient init failed", e)
            }

            // PRODUCTION FIX: Clear mesh timeline on app restart (fresh start for new nearby people)
            // This preserves private chats and channel messages
            try {
                val db = com.seamless.android.data.AppDatabase.getDatabase(this@SeamlessApplication)
                val repository = com.seamless.android.data.MessageRepository(db.messageDao())
                repository.clearMeshMessages()
                android.util.Log.d("SeamlessApp", "✅ Cleared mesh timeline messages on startup")
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "Failed to clear mesh messages", e)
            }
        }
        
        // WORLD-CLASS: Schedule background message retry worker for guaranteed delivery
        try {
            com.seamless.android.worker.MessageRetryWorker.schedule(this)
        } catch (e: Exception) {
            android.util.Log.e("SeamlessApp", "Failed to schedule message retry worker", e)
        }
        
        // PRODUCTION: Schedule Tor background sync worker for censorship-resistant offline delivery
        try {
            com.seamless.android.service.TorBackgroundWorker.schedule(this)
        } catch (e: Exception) {
            android.util.Log.e("SeamlessApp", "Failed to schedule Tor sync worker", e)
        }
        
        // SEAMLESS PROTOCOL: Initialize unified long-distance messaging transport
        // Combines Nostr + Tor + GPS routing + offline queue for ultimate messaging
        appScope.launch {
            try {
                com.seamless.android.net.SeamlessTransport.getInstance(this@SeamlessApplication).initialize()
                android.util.Log.d("SeamlessApp", "✅ SeamlessTransport initialized")
            } catch (e: Exception) {
                android.util.Log.e("SeamlessApp", "SeamlessTransport init error", e)
            }
        }
    }
    
    /**
     * PRODUCTION FIX: Install the enhanced global exception handler
     * Features: crash recovery, error logging, safe execution helpers
     */
    private fun installGlobalExceptionHandler() {
        com.seamless.android.core.GlobalExceptionHandler.install(this)
    }
}
