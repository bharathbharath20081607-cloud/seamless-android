package com.seamless.android.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager as AndroidPowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seamless.android.MainActivity
import com.seamless.android.R
import com.seamless.android.mesh.BluetoothMeshService
import com.seamless.android.mesh.BluetoothMeshDelegate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that owns and manages the mesh network.
 * This service keeps the mesh active even when the UI is not visible,
 * enabling true long-distance message relay.
 */
class MeshForegroundService : Service() {
    
    companion object {
        private const val TAG = "MeshForegroundService"
        private const val CHANNEL_ID = "mesh_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREF_BACKGROUND_ENABLED = "background_mesh_enabled"
        
        private const val PREFS_NAME = "seamless_prefs"
        
        fun isBackgroundEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_BACKGROUND_ENABLED, false)
        }
        
        fun setBackgroundEnabled(context: Context, enabled: Boolean) {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_BACKGROUND_ENABLED, enabled).apply()
            
            if (enabled) {
                start(context)
            } else {
                stop(context)
            }
        }
        
        fun start(context: Context) {
            if (!isBackgroundEnabled(context)) return
            
            try {
                val intent = Intent(context, MeshForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Starting foreground service")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
                e.printStackTrace()
            }
        }
        
        fun stop(context: Context) {
            try {
                val intent = Intent(context, MeshForegroundService::class.java)
                context.stopService(intent)
                Log.d(TAG, "Stopping foreground service")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop foreground service", e)
                e.printStackTrace()
            }
        }
    }
    
    private val binder = LocalBinder()
    private var meshService: BluetoothMeshService? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Track connected activities/delegates
    private val connectedDelegates = ConcurrentHashMap<String, BluetoothMeshDelegate>()
    private var primaryDelegate: BluetoothMeshDelegate? = null
    
    // Message queue for reliability
    private val pendingMessages = mutableListOf<PendingMessage>()
    
    // State flow for peer count updates
    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()
    
    // Health monitoring
    private var lastHealthCheck = System.currentTimeMillis()
    private var healthCheckJob: Job? = null
    
    // Track app foreground/background state to avoid duplicate notifications
    @Volatile
    private var isAppInBackground = true

    // ===== Doze Mode Handling (Phase 5: Battery Optimization) =====
    private var wakeLock: AndroidPowerManager.WakeLock? = null
    private var isDozeMode = false
    private var torLifecycleJob: Job? = null
    
    data class PendingMessage(
        val content: String,
        val recipient: String?,
        val timestamp: Long,
        val retryCount: Int = 0
    )
    
    inner class LocalBinder : Binder() {
        fun getService(): MeshForegroundService = this@MeshForegroundService
        
        fun getMeshService(): BluetoothMeshService? = meshService
        
        fun setAppBackgroundState(inBackground: Boolean) {
            this@MeshForegroundService.setAppBackgroundState(inBackground)
        }
        
        fun setDelegate(id: String, delegate: BluetoothMeshDelegate, isPrimary: Boolean = false) {
            connectedDelegates[id] = delegate
            if (isPrimary) {
                primaryDelegate = delegate
                meshService?.delegate = delegate
            }
            Log.d(TAG, "Delegate connected: $id (primary: $isPrimary)")
        }
        
        fun removeDelegate(id: String) {
            connectedDelegates.remove(id)
            if (primaryDelegate == connectedDelegates[id]) {
                primaryDelegate = connectedDelegates.values.firstOrNull()
                meshService?.delegate = primaryDelegate
            }
            Log.d(TAG, "Delegate disconnected: $id")
        }
    }
    
    fun setAppBackgroundState(inBackground: Boolean) {
        isAppInBackground = inBackground
        notificationManager?.setAppBackgroundState(inBackground)
        Log.d(TAG, "App background state updated: $inBackground")
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        try {
            createNotificationChannel()
            startForegroundWithNotification()
            initializeMeshService()
            startHealthMonitoring()
            startNostrMonitoring()
            registerDozeReceiver()
            startTorLifecycleMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create service", e)
            e.printStackTrace()
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: action=${intent?.action}")
        
        // Handle stop action from notification
        if (intent?.action == "STOP_SERVICE") {
            // Update the preference to reflect the service is off
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_BACKGROUND_ENABLED, false).apply()
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Ensure mesh service is running
        if (!isRunning) {
            isRunning = true
            if (meshService == null) {
                initializeMeshService()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service onUnbind")
        // Return true to receive onRebind calls
        return true
    }
    
    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "Service onRebind")
        super.onRebind(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        isRunning = false
        healthCheckJob?.cancel()
        torLifecycleJob?.cancel()
        serviceScope.cancel()
        unregisterDozeReceiver()
        releaseWakeLock()
        
        // Stop mesh services properly
        try {
            meshService?.stopServices()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mesh services", e)
        }
        meshService = null
        BluetoothMeshService.setInstance(null)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Network Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the mesh network active for message relay"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // Intent to open the app when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            pendingIntentFlags
        )
        
        // Action to stop the service
        val stopIntent = Intent(this, MeshForegroundService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            pendingIntentFlags
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seamless Mesh Active")
            .setContentText("Relaying messages in the background")
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_close, // You'll need to add this icon
                "Stop",
                stopPendingIntent
            )
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun initializeMeshService() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Initializing mesh service")
                
                // Create the mesh service instance
                if (meshService == null) {
                    meshService = BluetoothMeshService(applicationContext)
                    BluetoothMeshService.setInstance(meshService)
                    
                    // Set the primary delegate if available
                    primaryDelegate?.let {
                        meshService?.delegate = it
                    }
                    
                    // Start mesh services
                    meshService?.startServices()
                    Log.d(TAG, "Mesh services started successfully")
                    

                    // Monitor peer count changes
                    monitorPeerCount()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize mesh service", e)
                e.printStackTrace()
                
                // Retry after delay
                delay(5000)
                if (isRunning) {
                    initializeMeshService()
                }
            }
        }
    }
    
    private fun monitorPeerCount() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    val peerCount = meshService?.getPeerNicknames()?.size ?: 0
                    _peerCount.value = peerCount
                    updateNotification(peerCount)
                    delay(5000) // Update every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring peer count", e)
                }
            }
        }
    }
    
    private fun startHealthMonitoring() {
        healthCheckJob = serviceScope.launch {
            while (isRunning) {
                try {
                    delay(30000) // Check every 30 seconds
                    
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastCheck = currentTime - lastHealthCheck
                    
                    // Check if mesh service is still healthy
                    if (meshService == null) {
                        Log.w(TAG, "Mesh service is null, reinitializing")
                        initializeMeshService()
                    } else {
                        // Verify mesh service is responsive
                        val isHealthy = meshService?.connectionManager != null
                        if (!isHealthy) {
                            Log.w(TAG, "Mesh service unhealthy, restarting")
                            meshService?.stopServices()
                            meshService = null
                            initializeMeshService()
                        }
                    }
                    
                    lastHealthCheck = currentTime
                    
                    // Process pending messages
                    processPendingMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Health check failed", e)
                }
            }
        }
    }
    
    private fun processPendingMessages() {
        if (pendingMessages.isEmpty()) return
        
        serviceScope.launch {
            val messagesToProcess = pendingMessages.toList()
            pendingMessages.clear()
            
            for (message in messagesToProcess) {
                try {
                    // Attempt to send the message
                    if (message.retryCount < 3) {
                        // Add retry logic here when mesh service supports it
                        Log.d(TAG, "Processing pending message: ${message.content}")
                    } else {
                        Log.w(TAG, "Message exceeded retry limit, dropping")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process message", e)
                    // Re-queue with increased retry count
                    pendingMessages.add(message.copy(retryCount = message.retryCount + 1))
                }
            }
        }
    }
    
    fun updateNotification(connectedPeers: Int) {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            pendingIntentFlags
        )
        
        // Action to stop the service
        val stopIntent = Intent(this, MeshForegroundService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            pendingIntentFlags
        )
        
        val statusText = when {
            connectedPeers == 0 -> "Searching for peers..."
            connectedPeers == 1 -> "Connected to 1 peer"
            else -> "Connected to $connectedPeers peers"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seamless Mesh Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_close,
                "Stop",
                stopPendingIntent
            )
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // --- Nostr Integration for Real-Time Background Notifications ---

    private var nostrManager: com.seamless.android.nostr.NostrRelayManager? = null
    private var notificationManager: com.seamless.android.ui.NotificationManager? = null

    private fun startNostrMonitoring() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting Nostr background monitoring")
                
                // Initialize NotificationManager
                if (notificationManager == null) {
                    notificationManager = com.seamless.android.ui.NotificationManager(
                        applicationContext,
                        androidx.core.app.NotificationManagerCompat.from(applicationContext),
                        com.seamless.android.util.NotificationIntervalManager()
                    )
                    // Tell it we are in background so it always notifies
                    notificationManager?.setAppBackgroundState(true)
                }

                // Initialize NostrRelayManager
                nostrManager = com.seamless.android.nostr.NostrRelayManager.getInstance(applicationContext)
                nostrManager?.connect()

                // Get our identity
                val identity = com.seamless.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(applicationContext)
                if (identity != null) {
                    Log.d(TAG, "Subscribing to DMs for ${identity.npub}")
                    
                    // Subscribe to Gift Wraps (Kind 1059) - NIP-44/17
                    val giftWrapFilter = com.seamless.android.nostr.NostrFilter.giftWrapsFor(
                        pubkey = identity.publicKeyHex,
                        since = System.currentTimeMillis()
                    )
                    
                    nostrManager?.subscribe(
                        filter = giftWrapFilter,
                        id = "background_dm_giftwrap",
                        handler = { event ->
                            handleBackgroundNostrEvent(event)
                        }
                    )

                    // Subscribe to Legacy DMs (Kind 4) - NIP-04
                    val dmFilter = com.seamless.android.nostr.NostrFilter.Builder()
                        .kinds(4)
                        .tagP(identity.publicKeyHex)
                        .since(System.currentTimeMillis())
                        .build()
                    
                    nostrManager?.subscribe(
                        filter = dmFilter,
                        id = "background_dm_nip04",
                        handler = { event ->
                            handleBackgroundNostrEvent(event)
                        }
                    )
                } else {
                    Log.w(TAG, "No Nostr identity found, skipping background subscription")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Nostr monitoring", e)
            }
        }
    }

    private fun handleBackgroundNostrEvent(event: com.seamless.android.nostr.NostrEvent) {
        // Skip if app is in foreground - let UI handle it to avoid duplicate notifications
        if (!isAppInBackground) {
            Log.v(TAG, "Skipping background Nostr event - app is in foreground")
            return
        }
        
        Log.d(TAG, "Received background Nostr event kind=${event.kind}")
        
        serviceScope.launch(Dispatchers.Default) {
            try {
                val identity = com.seamless.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(applicationContext)
                if (identity == null) {
                    Log.w(TAG, "No identity for decryption")
                    return@launch
                }
                
                var messageContent = "New Encrypted Message"
                var senderPubkey = event.pubkey
                var senderNickname = "Nostr User"
                
                // Decrypt based on event kind
                if (event.kind == 1059) {
                    // Gift Wrap (NIP-17)
                    val decrypted = com.seamless.android.nostr.NostrProtocol.decryptPrivateMessage(event, identity)
                    if (decrypted != null) {
                        val (content, pubkey, _) = decrypted
                        senderPubkey = pubkey
                        
                        // Try to extract actual message from bitchat payload
                        if (content.startsWith("bitchat1:")) {
                            val extractedContent = extractBitchatMessage(content)
                            if (extractedContent != null) {
                                messageContent = extractedContent
                            }
                        } else {
                            messageContent = content
                        }
                        
                        // Try to get a readable sender name
                        senderNickname = "Nostr ${senderPubkey.take(8)}..."
                    }
                } else if (event.kind == 4) {
                    // Legacy DM (NIP-04) - show generic for now
                    senderNickname = "Nostr ${senderPubkey.take(8)}..."
                }
                
                withContext(Dispatchers.Main) {
                    notificationManager?.showPrivateMessageNotification(
                        senderPeerID = senderPubkey,
                        senderNickname = senderNickname,
                        messageContent = messageContent
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing background Nostr event", e)
                // Fallback to generic notification
                withContext(Dispatchers.Main) {
                    notificationManager?.showPrivateMessageNotification(
                        senderPeerID = event.pubkey,
                        senderNickname = "Nostr User",
                        messageContent = "New Encrypted Message"
                    )
                }
            }
        }
    }
    
    private fun extractBitchatMessage(content: String): String? {
        return try {
            val base64Content = content.removePrefix("bitchat1:")
            val padded = base64Content.replace("-", "+")
                .replace("_", "/")
                .let { str ->
                    val padding = (4 - str.length % 4) % 4
                    str + "=".repeat(padding)
                }
            val packetData = android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
            val packet = com.seamless.android.protocol.BitchatPacket.fromBinaryData(packetData) ?: return null
            
            if (packet.type != com.seamless.android.protocol.MessageType.NOISE_ENCRYPTED.value) return null
            
            val noisePayload = com.seamless.android.model.NoisePayload.decode(packet.payload) ?: return null
            
            if (noisePayload.type == com.seamless.android.model.NoisePayloadType.PRIVATE_MESSAGE) {
                val pm = com.seamless.android.model.PrivateMessagePacket.decode(noisePayload.data)
                pm?.content
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract bitchat message: ${e.message}")
            null
        }
    }

    // ===== Doze Mode Handling (Phase 5: Battery Optimization) =====

    /**
     * BroadcastReceiver for Doze mode transitions.
     *
     * When Android enters Doze mode:
     * - Network access is suspended
     * - BLE scanning is throttled
     * - Alarms are deferred
     * - JobScheduler is paused
     *
     * Our response:
     * - Reduce BLE scanning to absolute minimum
     * - Acquire WakeLock only for critical pending syncs
     * - Pause Tor (it can't use network anyway)
     * - Queue messages for delivery when Doze lifts
     */
    private val dozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AndroidPowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
                val pm = getSystemService(Context.POWER_SERVICE) as? AndroidPowerManager
                val inDoze = pm?.isDeviceIdleMode == true
                handleDozeTransition(inDoze)
            }
        }
    }

    private fun registerDozeReceiver() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = IntentFilter(AndroidPowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                registerReceiver(dozeReceiver, filter)
                Log.d(TAG, "🔋 Doze mode receiver registered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Doze receiver: ${e.message}")
        }
    }

    private fun unregisterDozeReceiver() {
        try {
            unregisterReceiver(dozeReceiver)
        } catch (e: Exception) {
            // May not have been registered
        }
    }

    /**
     * Handle Doze mode transitions.
     */
    private fun handleDozeTransition(entering: Boolean) {
        isDozeMode = entering

        if (entering) {
            Log.i(TAG, "💤 Entering Doze mode — reducing operations")

            // Reduce mesh scanning to ultra-low power via PowerManager
            try {
                val meshPowerMgr = com.seamless.android.mesh.PowerManager(applicationContext)
                meshPowerMgr.setAppBackgroundState(true)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to adjust mesh power: ${e.message}")
            }

            // Update notification to show Doze state
            updateNotificationForDoze(true)

        } else {
            Log.i(TAG, "⚡ Exiting Doze mode — resuming operations")

            // Resume normal scanning
            try {
                val meshPowerMgr = com.seamless.android.mesh.PowerManager(applicationContext)
                meshPowerMgr.setAppBackgroundState(isAppInBackground)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resume mesh power: ${e.message}")
            }

            // Trigger immediate sync of pending messages
            serviceScope.launch {
                processPendingMessages()
            }

            updateNotificationForDoze(false)
        }
    }

    /**
     * Acquire a partial WakeLock for critical sync operations.
     * Used when BSP sync is in progress and we can't afford the CPU sleeping.
     * Automatically releases after 30 seconds.
     */
    fun acquireSyncWakeLock(tag: String = "sync", timeoutMs: Long = 30_000) {
        try {
            if (wakeLock?.isHeld == true) return

            val pm = getSystemService(Context.POWER_SERVICE) as? AndroidPowerManager
            wakeLock = pm?.newWakeLock(
                AndroidPowerManager.PARTIAL_WAKE_LOCK,
                "seamless:$tag"
            )?.apply {
                acquire(timeoutMs)
            }
            Log.d(TAG, "🔒 WakeLock acquired for $tag (${timeoutMs}ms timeout)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    /**
     * Release the WakeLock after sync completes.
     */
    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "🔓 WakeLock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock: ${e.message}")
        }
    }

    private fun updateNotificationForDoze(entering: Boolean) {
        val peerCount = _peerCount.value
        val statusText = when {
            entering -> "Doze mode — reduced scanning"
            peerCount == 0 -> "Searching for peers..."
            peerCount == 1 -> "Connected to 1 peer"
            else -> "Connected to $peerCount peers"
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )

        val stopIntent = Intent(this, MeshForegroundService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (entering) "Seamless — Low Power" else "Seamless Mesh Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    // ===== Smart Tor Lifecycle (Phase 5) =====

    /**
     * Periodically check if Tor is needed based on contact reachability.
     * If all contacts are reachable via mesh, Tor is deactivated to save battery.
     */
    private fun startTorLifecycleMonitoring() {
        torLifecycleJob = serviceScope.launch {
            while (isRunning) {
                try {
                    delay(30_000) // Check every 30 seconds

                    val hybridTransport = com.seamless.android.net.HybridTransport.getInstance(applicationContext)

                    // Get all contacts we want to communicate with
                    val allContacts = try {
                        val tms = com.seamless.android.net.TorMessagingService.getInstance(applicationContext)
                        tms.getContacts().map { it.id }.toSet()
                    } catch (e: Exception) {
                        emptySet()
                    }

                    // Get contacts reachable via mesh
                    val meshReachable = try {
                        meshService?.getPeerNicknames()?.keys?.toSet() ?: emptySet()
                    } catch (e: Exception) {
                        emptySet()
                    }

                    // Let HybridTransport decide if Tor is needed
                    hybridTransport.updateTorLifecycle(allContacts, meshReachable)

                } catch (e: Exception) {
                    Log.e(TAG, "Tor lifecycle check failed: ${e.message}")
                }
            }
        }
    }
}
