@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.seamless.android.MainActivity
import com.seamless.android.R
import com.seamless.android.net.TorDaemonManager
import com.seamless.android.net.TorHiddenServiceManager
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorMessagingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/**
 * TorForegroundService — Persistent foreground service for Tor networking.
 *
 * Inspired by Briar's dont-kill-me-lib approach, this service:
 *
 * 1. Keeps the Tor daemon alive in the background via startForeground()
 * 2. Holds a partial WakeLock to prevent the CPU from sleeping while Tor
 *    needs to maintain circuits
 * 3. Monitors Doze mode (ACTION_DEVICE_IDLE_MODE_CHANGED) while keeping the
 *    daemon enabled so contacts and queued delivery are not deliberately dropped
 * 4. Monitors network connectivity changes and rebuilds Tor circuits
 *    when connectivity is restored
 * 5. Starts TorMessagingService which manages hidden service, circuit
 *    pool, message queue, BSP sync engine, and BRP rendezvous
 *
 * Lifecycle:
 * - Started by TorManager when user enables Tor (TorMode.ON)
 * - Stopped by TorManager when user disables Tor (TorMode.OFF)
 * - Survives app backgrounding via foreground notification
 * - Auto-restarts via START_STICKY if killed by system
 */
class TorForegroundService : Service() {

    companion object {
        private const val TAG = "TorForegroundService"
        private const val NOTIFICATION_ID = 7799
        private const val CHANNEL_ID = "tor_foreground_channel"
        private const val WAKELOCK_TAG = "seamless:tor_foreground"

        @Volatile
        private var running = false

        fun isRunning(context: Context): Boolean = running

        fun start(context: Context) {
            try {
                val intent = Intent(context, TorForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "🧅 TorForegroundService start requested")
            } catch (e: Exception) {
                Log.w(TAG, "Background foreground-service start deferred by OS (${e.message}); scheduling worker")
                runCatching { TorBackgroundWorker.schedule(context.applicationContext) }
            }
        }
        fun startLegacyUnused(context: Context) {
            val intent = Intent(context, TorForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "🧅 TorForegroundService start requested")
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TorForegroundService::class.java))
            Log.i(TAG, "TorForegroundService stop requested")
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var dozeReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var statusJob: Job? = null
    private var wakeLockRenewalJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Log.i(TAG, "🧅 TorForegroundService created")

        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Connecting to Tor..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Connecting to Tor..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification("Connecting to Tor..."))
            }
        } catch (e: Exception) {
            runCatching { startForeground(NOTIFICATION_ID, createNotification("Connecting to Tor...")) }
        }

        // Acquire partial wake lock to keep CPU running for Tor circuits
        acquireWakeLock()

        // Register Doze mode receiver
        registerDozeReceiver()

        // Register network change callbacks
        registerNetworkCallback()

        // Start the full Tor messaging stack
        startTorMessaging()

        // Schedule TorBackgroundWorker for resilient offline sync
        TorBackgroundWorker.schedule(applicationContext)

        // Observe Tor and hidden service status for notification updates
        observeStatus()

        // Periodically re-acquire WakeLock before the 10-minute timeout expires.
        // This runs independently of state changes to guarantee the CPU stays awake.
        startWakeLockRenewal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY // Auto-restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        Log.i(TAG, "🛑 TorForegroundService destroyed")

        statusJob?.cancel()
        wakeLockRenewalJob?.cancel()

        // Unregister receivers
        unregisterDozeReceiver()
        unregisterNetworkCallback()

        // Release wake lock
        releaseWakeLock()

        // Stop messaging service
        serviceScope.launch {
            try {
                TorMessagingService.getInstance(applicationContext).stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping messaging: ${e.message}")
            }
        }
        serviceScope.cancel()

        super.onDestroy()
    }

    // ===== Tor Messaging Lifecycle =====

    private fun startTorMessaging() {
        serviceScope.launch {
            try {
                Log.i(TAG, "🚀 Starting TorMessagingService from foreground service...")
                TorMessagingService.getInstance(applicationContext).start()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start TorMessagingService: ${e.message}")
                updateNotification("Error: ${e.message?.take(40)}")
            }
        }
    }

    // ===== Doze Mode Handling =====

    /**
     * A foreground-service Tor session must remain enabled through Doze. Android
     * can still defer network access on some devices, but disabling Tor here
     * guarantees every peer is marked offline and forces a slow rebuild later.
     */
    private fun registerDozeReceiver() {
        dozeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pm = context.getSystemService(POWER_SERVICE) as PowerManager
                val isDoze = pm.isDeviceIdleMode

                if (isDoze) {
                    Log.i(TAG, "Device entered Doze; keeping foreground Tor session enabled")
                } else {
                    Log.i(TAG, "⚡ Device exited Doze → re-enabling Tor network")
                    serviceScope.launch {
                        try {
                            TorDaemonManager.getInstance(applicationContext as Application)
                                .enableNetwork(true)

                            // Force republish hidden service so peers can find us
                            delay(2000) // Wait for circuits to rebuild
                            TorHiddenServiceManager.getInstance(applicationContext)
                                .republishHiddenService()
                            TorMessagingService.getInstance(applicationContext)
                                .refreshMailboxHost()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to re-enable network after Doze: ${e.message}")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        registerReceiver(dozeReceiver, filter)
        Log.d(TAG, "Registered Doze mode receiver")
    }

    private fun unregisterDozeReceiver() {
        dozeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        dozeReceiver = null
    }

    // ===== Network Change Handling =====

    /**
     * Monitor connectivity changes. When network is lost, pause operations.
     * When regained, rebuild Tor circuits and republish hidden service.
     */
    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "🌐 Network available → rebuilding Tor circuits")
                serviceScope.launch {
                    try {
                        // Re-enable networking on the daemon
                        TorDaemonManager.getInstance(applicationContext as Application)
                            .enableNetwork(true)

                        // Wait a moment then republish
                        delay(3000)
                        TorHiddenServiceManager.getInstance(applicationContext)
                            .republishHiddenService()
                        TorMessagingService.getInstance(applicationContext)
                            .refreshMailboxHost()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error on network available: ${e.message}")
                    }
                }
            }

            override fun onLost(network: Network) {
                val activeNet = cm.activeNetwork
                val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
                val stillOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (stillOnline) {
                    Log.i(TAG, "📶 Previous network lost, but active internet network remains online — keeping Tor enabled")
                    serviceScope.launch {
                        try {
                            TorDaemonManager.getInstance(applicationContext as Application)
                                .enableNetwork(true)
                        } catch (_: Exception) {}
                    }
                    return
                }
                Log.i(TAG, "📡 All internet networks lost → pausing Tor operations")
                serviceScope.launch {
                    try {
                        TorDaemonManager.getInstance(applicationContext as Application)
                            .enableNetwork(false)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error on network lost: ${e.message}")
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, networkCallback!!)
        Log.d(TAG, "Registered network callback")
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    // ===== WakeLock Management =====

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max, will re-acquire
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    /**
     * Periodically re-acquire WakeLock before the 10-minute timeout expires.
     * The WakeLock is acquired for 10 minutes, so renewing every 9 minutes
     * guarantees continuous CPU wakefulness for Tor circuit maintenance.
     */
    private fun startWakeLockRenewal() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = serviceScope.launch {
            while (isActive) {
                delay(9 * 60 * 1000L) // 9 minutes
                if (wakeLock?.isHeld != true) {
                    Log.d(TAG, "🔄 WakeLock renewal — re-acquiring")
                    acquireWakeLock()
                } else {
                    // Re-acquire to reset the 10-minute timer
                    try {
                        wakeLock?.acquire(10 * 60 * 1000L)
                        Log.d(TAG, "🔄 WakeLock renewed for another 10 minutes")
                    } catch (e: Exception) {
                        Log.w(TAG, "WakeLock renewal failed, re-acquiring: ${e.message}")
                        acquireWakeLock()
                    }
                }
            }
        }
    }

    // ===== Status Observation =====

    private fun observeStatus() {
        statusJob = serviceScope.launch {
            // Combine Tor status and Hidden Service status
            TorManager.statusFlow
                .combine(TorMessagingService.getInstance(applicationContext).serviceState) { tor, svc ->
                    Pair(tor, svc)
                }
                .collect { (torStatus, svcState) ->
                    val text = when {
                        svcState == TorMessagingService.ServiceState.RUNNING -> {
                            val onion = TorDaemonManager.getInstance(
                                applicationContext as Application
                            ).getOnionAddress()
                            if (onion != null) {
                                "🟢 Private messaging active • ${onion.take(16)}…"
                            } else {
                                "🟢 Tor connected • outbound ready"
                            }
                        }
                        svcState == TorMessagingService.ServiceState.WAITING_FOR_TOR ->
                            "⏳ Connecting to Tor… ${torStatus.bootstrapPercent}%"
                        svcState == TorMessagingService.ServiceState.RENDEZVOUS ->
                            "🔑 Peer rendezvous in progress…"
                        svcState == TorMessagingService.ServiceState.STARTING ->
                            "🚀 Starting Tor messaging…"
                        svcState == TorMessagingService.ServiceState.ERROR ->
                            "⚠️ Tor error — retrying…"
                        else -> "Tor stopped"
                    }
                    updateNotification(text)

                    // Re-acquire wake lock periodically while running
                    if (svcState == TorMessagingService.ServiceState.RUNNING) {
                        if (wakeLock?.isHeld != true) {
                            acquireWakeLock()
                        }
                    }
                }
        }
    }

    // ===== Notifications =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tor Network",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Tor running for private peer-to-peer messaging"
                setShowBadge(false)
            }

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Seamless • Tor")
            .setContentText(status)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        try {
            val notification = createNotification(status)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }
}
