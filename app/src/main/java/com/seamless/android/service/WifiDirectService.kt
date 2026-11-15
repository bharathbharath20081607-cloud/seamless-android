@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.seamless.android.R
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Wi-Fi Direct service for peer-to-peer file sharing (HyperDrop)
 */
class WifiDirectService : Service() {

    companion object {
        private const val TAG = "WifiDirectService"
        private const val NOTIFICATION_ID = 1002
        private const val SOCKET_TIMEOUT = 30000 // 30s timeout for large file transfers

        // ⚡ TURBO MODE v2.0 - MAXIMUM SPEED CONFIGURATION
        private const val BUFFER_SIZE = 256 * 1024  // 256KB optimal Wi-Fi buffer
        private const val SOCKET_SEND_BUFFER = 2 * 1024 * 1024 // 2MB send buffer
        private const val SOCKET_RECV_BUFFER = 2 * 1024 * 1024 // 2MB receive buffer
        private const val PROGRESS_REPORT_INTERVAL = 2

        private const val HEADER_MAGIC_PING = -1
        private const val HEADER_MAGIC_V2 = -2

        private const val CHANNEL_ID = "wifi_direct_channel"

        // Action constants for intent-based control
        const val ACTION_START_DISCOVERY = "com.seamless.android.action.START_DISCOVERY"
        const val ACTION_STOP_DISCOVERY = "com.seamless.android.action.STOP_DISCOVERY"
        const val ACTION_CREATE_GROUP = "com.seamless.android.action.CREATE_GROUP"
        const val ACTION_CONNECT = "com.seamless.android.action.CONNECT"
        const val ACTION_DISCONNECT = "com.seamless.android.action.DISCONNECT"
        const val ACTION_SEND_FILE = "com.seamless.android.action.SEND_FILE"
        const val ACTION_SET_NFC_TRUSTED = "com.seamless.android.action.SET_NFC_TRUSTED"
        const val ACTION_ACCEPT_TRANSFER = "com.seamless.android.action.ACCEPT_TRANSFER"
        const val ACTION_REJECT_TRANSFER = "com.seamless.android.action.REJECT_TRANSFER"
        const val ACTION_CANCEL_TRANSFER = "com.seamless.android.action.CANCEL_TRANSFER"

        // Extra constants for intent data
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_FILE_SIZE = "extra_file_size"
        const val EXTRA_FILE_INDEX = "extra_file_index"
        const val EXTRA_TOTAL_FILES = "extra_total_files"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_NFC_TRUSTED = "extra_nfc_trusted"
    }

    // Service binder for binding to this service
    inner class LocalBinder : Binder() {
        fun getService(): WifiDirectService = this@WifiDirectService
    }

    private val binder = LocalBinder()

    // Wi-Fi Direct components
    private lateinit var wifiP2pManager: WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WiFiDirectBroadcastReceiver? = null

    // State tracking
    private var currentConnection: WifiP2pInfo? = null
    private var serverSocket: ServerSocket? = null
    private var isNfcTrustedSession = false
    private var lastDeviceFoundTime = 0L
    private var discoveryRetryCount = 0
    private var isLocalServiceRegistered = false
    private var isLocalServiceRegistrationInFlight = false

    // Map discovered peer MAC addresses to friendly HyperDrop aliases via DNS-SD
    private val dnsSdAliases = ConcurrentHashMap<String, String>()
    // Service discovery can return an app peer before it appears in the generic peer list.
    // Keep the generic list for address bookkeeping, but only expose verified
    // HyperDrop services to the file-sharing UI.
    private val p2pPeersByAddress = ConcurrentHashMap<String, WifiP2pDevice>()
    private val servicePeersByAddress = ConcurrentHashMap<String, WifiP2pDevice>()
    private var dnsSdServiceRequest: WifiP2pDnsSdServiceRequest? = null
    private var isServiceRequestRegistrationInFlight = false
    private val connectedClientMacs = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var hasConnectedPeerInGroup: Boolean = false

    // Track the client's IP address (discovered from incoming connections)
    // This enables the group owner to send files TO the client
    @Volatile private var clientAddress: String? = null
    @Volatile private var isServerRunning: Boolean = false
    @Volatile private var lastAcceptedPeerAddress: String? = null
    @Volatile private var lastAcceptedTimestamp: Long = 0L
    @Volatile private var activeTransferSocket: Socket? = null
    @Volatile private var isTransferCancelled: Boolean = false

    // Pending confirmation for manual file transfer approval
    private var pendingConfirmation: CompletableDeferred<Boolean>? = null

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks for UI updates
    private val callbacks = mutableListOf<WifiDirectCallback>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WifiDirectService onCreate")

        createNotificationChannel()
        startForegroundServiceCompat()

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        // Proactively start the TCP receive server on port 8988
        serviceScope.launch {
            startReceiveServer()
        }

        // Register broadcast receiver
        receiver = WiFiDirectBroadcastReceiver()
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, intentFilter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_DISCOVERY -> startDiscovery()
            ACTION_STOP_DISCOVERY -> stopDiscovery()
            ACTION_CREATE_GROUP -> createGroup()
            ACTION_CONNECT -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (deviceAddress != null) {
                    connectToDevice(deviceAddress)
                }
            }
            ACTION_DISCONNECT -> disconnect()
            ACTION_SEND_FILE -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
                val fileIndex = intent.getIntExtra(EXTRA_FILE_INDEX, 1).coerceAtLeast(1)
                val totalFiles = intent.getIntExtra(EXTRA_TOTAL_FILES, 1).coerceAtLeast(1)
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME)
                    ?: "${HyperDropIdentity.getAlias(this)} (${HyperDropIdentity.getDeviceModel()})"
                if (filePath != null && fileName != null) {
                    serviceScope.launch {
                        sendFile(filePath, fileName, fileSize, fileIndex, totalFiles, senderName)
                    }
                }
            }
            ACTION_SET_NFC_TRUSTED -> {
                isNfcTrustedSession = intent.getBooleanExtra(EXTRA_NFC_TRUSTED, false)
                Log.d(TAG, "NFC trusted session set to: $isNfcTrustedSession")
            }
            ACTION_ACCEPT_TRANSFER -> {
                pendingConfirmation?.complete(true)
            }
            ACTION_REJECT_TRANSFER -> {
                pendingConfirmation?.complete(false)
            }
            ACTION_CANCEL_TRANSFER -> {
                cancelActiveTransfer()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WifiDirectService onDestroy")

        cleanup()
        serviceScope.cancel()

        try {
            receiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    /**
     * Check whether there is an active P2P group with an actual connected peer.
     */
    fun hasActivePeerConnection(deviceAddress: String? = null): Boolean {
        val conn = currentConnection ?: return false
        if (!conn.groupFormed) return false
        if (!conn.isGroupOwner) {
            // We are a Client connected to a Group Owner
            return true
        }
        // We are Group Owner — only truly connected if a client has joined or registered its IP
        val hasClient = hasConnectedPeerInGroup || clientAddress != null || connectedClientMacs.isNotEmpty()
        if (!hasClient) return false
        if (deviceAddress != null && connectedClientMacs.isNotEmpty()) {
            return connectedClientMacs.any { it.equals(deviceAddress, ignoreCase = true) } || clientAddress != null
        }
        return true
    }

    private fun hasWifiDirectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Android still requires the system Location toggle for Wi-Fi Direct peer and
     * service discovery. This is separate from the runtime Nearby devices grant.
     */
    private fun isLocationModeEnabled(): Boolean {
        return try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled == true
            } else {
                Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) !=
                    Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to determine Location mode", e)
            false
        }
    }

    private fun publishDiscoveredPeers() {
        val peers = LinkedHashMap<String, WifiP2pDevice>()
        servicePeersByAddress.forEach { (address, peer) ->
            val copy = WifiP2pDevice(peer)
            val alias = address?.let { dnsSdAliases[it] }
            if (!alias.isNullOrBlank()) copy.deviceName = alias
            peers[address] = copy
        }
        notifyCallbacks { onPeersDiscovered(peers.values) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wi-Fi Direct Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for Wi-Fi Direct file sharing"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wi-Fi Direct")
            .setContentText("Ready for file sharing")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Register a DNS-SD local service and response listeners so HyperDrop peers
     * can exchange friendly aliases and stay discoverable in P2P Listen state.
     */
    private fun ensureDnsSdConfigured(ch: WifiP2pManager.Channel): Boolean {
        if (!hasWifiDirectPermission()) return false
        try {
            val alias = HyperDropIdentity.getAlias(this)
            val model = HyperDropIdentity.getDeviceModel()
            val visibility = HyperDropIdentity.getVisibilityMode(this)

            wifiP2pManager.setDnsSdResponseListeners(
                ch,
                { instanceName, registrationType, srcDevice ->
                    // Identity is confirmed by the TXT record below. Some OEMs
                    // are strict about DNS-SD instance-name matching, so do not
                    // discard a response before its record arrives.
                    Log.d(TAG, "DNS-SD service response: $instanceName.$registrationType from ${srcDevice?.deviceAddress}")
                },
                { _, record, srcDevice ->
                    val addr = srcDevice?.deviceAddress?.lowercase(Locale.US) ?: return@setDnsSdResponseListeners
                    val peerAlias = record["alias"]
                    val peerModel = record["model"]
                    if (!peerAlias.isNullOrBlank()) {
                        val display = if (!peerModel.isNullOrBlank()) {
                            "⚡ $peerAlias ($peerModel)"
                        } else {
                            "⚡ $peerAlias"
                        }
                        dnsSdAliases[addr] = display
                        servicePeersByAddress[addr] = WifiP2pDevice(srcDevice).apply {
                            deviceName = display
                        }
                        publishDiscoveredPeers()
                        Log.d(TAG, "🎯 DNS-SD resolved peer $addr -> $display")
                    }
                }
            )

            if (!isLocalServiceRegistered && visibility != HyperDropIdentity.VisibilityMode.HIDDEN) {
                if (isLocalServiceRegistrationInFlight) return false
                isLocalServiceRegistrationInFlight = true
                val record = mapOf(
                    "alias" to alias,
                    "model" to model,
                    "port" to "8988"
                )
                val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("_hyperdrop", "_tcp", record)
                wifiP2pManager.addLocalService(ch, serviceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        isLocalServiceRegistrationInFlight = false
                        isLocalServiceRegistered = true
                        Log.d(TAG, "✅ Registered HyperDrop DNS-SD local service: $alias ($model)")
                        // The Wi-Fi P2P framework processes one operation at a time.
                        // Begin discovery only after registration has completed.
                        handler.post { startDiscovery(retryAttempt = 1) }
                    }
                    override fun onFailure(reason: Int) {
                        isLocalServiceRegistrationInFlight = false
                        Log.w(TAG, "DNS-SD addLocalService failed: $reason")
                        if (reason == WifiP2pManager.BUSY) {
                            handler.postDelayed({ startDiscovery(retryAttempt = 1) }, 650L)
                        } else {
                            notifyCallbacks { onDiscoveryFailed(reason) }
                        }
                    }
                })
                return false
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "DNS-SD setup warning: ${e.message}")
            notifyCallbacks { onDiscoveryFailed(WifiP2pManager.ERROR) }
            return false
        }
    }

    private fun startDiscovery(retryAttempt: Int = 0) {
        if (!hasWifiDirectPermission()) {
            Log.w(TAG, "Missing permission for Wi-Fi Direct discovery")
            notifyCallbacks { onDiscoveryFailed(-1) }
            return
        }
        if (!isLocationModeEnabled()) {
            Log.w(TAG, "Location mode is disabled; Android blocks Wi-Fi Direct discovery")
            notifyCallbacks { onDiscoveryFailed(-2) }
            return
        }

        serviceScope.launch {
            startReceiveServer()
        }

        channel?.let { ch ->
            if (!ensureDnsSdConfigured(ch)) return

            // If we are sitting in a stale empty autonomous group (0 clients), remove it first
            // so the Wi-Fi P2P radio can scan social channels 1, 6, and 11 properly.
            if (retryAttempt == 0) {
                wifiP2pManager.requestGroupInfo(ch) { existingGroup ->
                    if (existingGroup != null && existingGroup.isGroupOwner && existingGroup.clientList.isEmpty()) {
                        Log.d(TAG, "Removing stale empty P2P group before starting discovery")
                        hasConnectedPeerInGroup = false
                        connectedClientMacs.clear()
                        wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                handler.postDelayed({ startDiscovery(retryAttempt = 1) }, 250L)
                            }
                            override fun onFailure(reason: Int) {
                                startDiscovery(retryAttempt = 1)
                            }
                        })
                    } else {
                        startHyperDropServiceDiscovery(ch, retryAttempt)
                    }
                }
                return
            }

            startHyperDropServiceDiscovery(ch, retryAttempt)
        }
    }

    /**
     * Discover only the registered HyperDrop service. Calling discoverPeers() and
     * discoverServices() back-to-back makes the framework cancel one scan with
     * BUSY on many phones, which was the source of intermittent invisible peers.
     */
    private fun startHyperDropServiceDiscovery(ch: WifiP2pManager.Channel, retryAttempt: Int) {
        if (!hasWifiDirectPermission()) return

        val request = dnsSdServiceRequest
        if (request == null) {
            if (isServiceRequestRegistrationInFlight) return
            isServiceRequestRegistrationInFlight = true
            // Ask for DNS-SD records broadly, then admit only records containing
            // HyperDrop's identity fields. This avoids OEM-specific failures with
            // a narrowly formatted instance-name query.
            val newRequest = WifiP2pDnsSdServiceRequest.newInstance()
            wifiP2pManager.addServiceRequest(ch, newRequest, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    isServiceRequestRegistrationInFlight = false
                    dnsSdServiceRequest = newRequest
                    discoverHyperDropServices(ch, retryAttempt)
                }

                override fun onFailure(reason: Int) {
                    isServiceRequestRegistrationInFlight = false
                    Log.w(TAG, "Could not register HyperDrop service request: $reason")
                    if (reason == WifiP2pManager.BUSY && retryAttempt < 3) {
                        handler.postDelayed({ startHyperDropServiceDiscovery(ch, retryAttempt + 1) }, 550L * (retryAttempt + 1))
                    } else {
                        notifyCallbacks { onDiscoveryFailed(reason) }
                    }
                }
            })
        } else {
            discoverHyperDropServices(ch, retryAttempt)
        }
    }

    private fun discoverHyperDropServices(ch: WifiP2pManager.Channel, retryAttempt: Int) {
        wifiP2pManager.discoverServices(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "HyperDrop service discovery started successfully")
                notifyCallbacks { onDiscoveryStarted() }
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "HyperDrop service discovery failed: $reason (retryAttempt=$retryAttempt)")
                if (reason == WifiP2pManager.BUSY && retryAttempt < 3) {
                    handler.postDelayed({
                        discoverHyperDropServices(ch, retryAttempt + 1)
                    }, 550L * (retryAttempt + 1))
                } else {
                    notifyCallbacks { onDiscoveryFailed(reason) }
                }
            }
        })
    }

    private fun stopDiscovery() {
        channel?.let { ch ->
            dnsSdServiceRequest?.let { request ->
                wifiP2pManager.removeServiceRequest(ch, request, null)
            }
            dnsSdServiceRequest = null
            isServiceRequestRegistrationInFlight = false
            servicePeersByAddress.clear()
            wifiP2pManager.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery stopped")
                    notifyCallbacks { onDiscoveryStopped() }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to stop discovery: $reason")
                }
            })
        }
    }

    private fun createGroup() {
        if (!hasWifiDirectPermission()) {
            Log.w(TAG, "Missing permission for Wi-Fi Direct hosting")
            notifyCallbacks { onDiscoveryFailed(-1) }
            return
        }
        if (!isLocationModeEnabled()) {
            Log.w(TAG, "Location mode is disabled; Android blocks Wi-Fi Direct hosting discovery")
            notifyCallbacks { onDiscoveryFailed(-2) }
            return
        }

        // Proactively ensure the receive server is listening on port 8988
        serviceScope.launch {
            startReceiveServer()
        }

        channel?.let { ch ->
            if (!ensureDnsSdConfigured(ch)) return
            wifiP2pManager.requestGroupInfo(ch) { existingGroup ->
                if (existingGroup != null && (!existingGroup.isGroupOwner || existingGroup.clientList.isNotEmpty())) {
                    Log.d(TAG, "Already in active peer group, keeping existing group and refreshing discovery")
                    hasConnectedPeerInGroup = true
                    connectedClientMacs.clear()
                    existingGroup.clientList?.forEach { client ->
                        if (!client.deviceAddress.isNullOrBlank()) {
                            connectedClientMacs.add(client.deviceAddress)
                        }
                    }
                    wifiP2pManager.requestConnectionInfo(ch) { info ->
                        if (info != null && info.groupFormed) {
                            currentConnection = info
                            notifyCallbacks { onConnected(info) }
                        }
                    }
                    startHyperDropServiceDiscovery(ch, retryAttempt = 1)
                } else if (existingGroup != null) {
                    // Remove empty solo group so this receiver enters standard P2P Listen+Search mode
                    // on social channels (1, 6, 11) and is 100% discoverable by all Android phones.
                    hasConnectedPeerInGroup = false
                    connectedClientMacs.clear()
                    wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            handler.postDelayed({ startHyperDropServiceDiscovery(ch, retryAttempt = 1) }, 250L)
                        }
                        override fun onFailure(reason: Int) {
                            startHyperDropServiceDiscovery(ch, retryAttempt = 1)
                        }
                    })
                } else {
                    startHyperDropServiceDiscovery(ch, retryAttempt = 1)
                }
            }
        }
    }

    private fun connectToDevice(deviceAddress: String, isRetry: Boolean = false) {
        if (!hasWifiDirectPermission()) {
            Log.w(TAG, "Missing permission for Wi-Fi Direct connection")
            notifyCallbacks { onConnectionFailed(-1) }
            return
        }

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
            // Sender prefers Client role (0) so Receiver owns 192.168.49.1
            groupOwnerIntent = 0
        }

        channel?.let { ch ->
            if (!isRetry) {
                wifiP2pManager.requestGroupInfo(ch) { existingGroup ->
                    if (existingGroup != null && existingGroup.isGroupOwner && existingGroup.clientList.isEmpty()) {
                        Log.d(TAG, "Removing empty local group before connecting to peer $deviceAddress")
                        hasConnectedPeerInGroup = false
                        connectedClientMacs.clear()
                        wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                handler.postDelayed({ connectToDevice(deviceAddress, isRetry = true) }, 250L)
                            }
                            override fun onFailure(reason: Int) {
                                connectToDevice(deviceAddress, isRetry = true)
                            }
                        })
                    } else {
                        connectToDevice(deviceAddress, isRetry = true)
                    }
                }
                return
            }
            wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection initiated to: $deviceAddress")
                    notifyCallbacks { onConnectionInitiated(deviceAddress) }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Connection failed: $reason (isRetry=$isRetry)")
                    if (reason == WifiP2pManager.BUSY) {
                        handler.postDelayed({
                            wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    notifyCallbacks { onConnectionInitiated(deviceAddress) }
                                }
                                override fun onFailure(r: Int) {
                                    notifyCallbacks { onConnectionFailed(r) }
                                }
                            })
                        }, 550L)
                    } else {
                        notifyCallbacks { onConnectionFailed(reason) }
                    }
                }
            })
        }
    }

    private fun cancelActiveTransfer() {
        isTransferCancelled = true
        pendingConfirmation?.complete(false)
        try {
            activeTransferSocket?.close()
        } catch (_: Exception) { }
        activeTransferSocket = null
    }

    private fun disconnect() {
        cancelActiveTransfer()
        hasConnectedPeerInGroup = false
        connectedClientMacs.clear()
        channel?.let { ch ->
            wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Disconnected from group")
                    isNfcTrustedSession = false
                    clientAddress = null
                    lastAcceptedPeerAddress = null
                    notifyCallbacks { onDisconnected() }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to disconnect: $reason")
                    isNfcTrustedSession = false
                    clientAddress = null
                    lastAcceptedPeerAddress = null
                    notifyCallbacks { onDisconnected() }
                }
            })
        }
    }

    /**
     * Send a lightweight registration ping from Client to Group Owner immediately upon joining
     * the P2P group so the Group Owner learns the Client's IP address for bidirectional transfers.
     */
    private fun sendClientRegistrationPing(groupOwnerHostAddress: String?) {
        val hostAddr = groupOwnerHostAddress ?: return
        serviceScope.launch {
            repeat(8) { attempt ->
                if (attempt > 0) delay(300L * attempt)
                try {
                    Socket().use { pingSocket ->
                        pingSocket.tcpNoDelay = true
                        pingSocket.soTimeout = 3_000
                        pingSocket.connect(InetSocketAddress(hostAddr, 8988), 3_000)
                        val out = DataOutputStream(pingSocket.getOutputStream())
                        out.writeInt(HEADER_MAGIC_PING)
                        out.flush()
                        pingSocket.getInputStream().read()
                        Log.d(TAG, "📍 Client IP registration ping succeeded to $hostAddr (attempt ${attempt + 1})")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Client IP registration ping attempt ${attempt + 1}/8 failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Fallback ARP lookup on Group Owner in case the Client's registration ping was delayed.
     */
    private fun resolveClientAddressFromArp(): String? {
        return try {
            val arpFile = File("/proc/net/arp")
            if (!arpFile.exists()) return null
            arpFile.useLines { lines ->
                for (line in lines.drop(1)) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (ip.startsWith("192.168.49.") && ip != "192.168.49.1" && mac != "00:00:00:00:00:00") {
                            Log.d(TAG, "📍 Resolved client IP from ARP table: $ip ($mac)")
                            return@useLines ip
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun acquireWifiLock(tagSuffix: String): WifiManager.WifiLock? {
        return runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wm?.createWifiLock(mode, "seamless:hyperdrop_$tagSuffix")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun acquireWakeLock(tagSuffix: String): PowerManager.WakeLock? {
        return runCatching {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "seamless:hyperdrop_wake_$tagSuffix")?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // Max 10 minutes
            }
        }.getOrNull()
    }

    /**
     * Send a file to the connected peer using a synchronized V2 header + approval + completion ACK protocol.
     */
    private suspend fun sendFile(
        filePath: String,
        fileName: String,
        fileSize: Long,
        fileIndex: Int = 1,
        totalFiles: Int = 1,
        senderName: String = "Nearby Device"
    ) {
        val connection = currentConnection
        if (connection == null || !connection.groupFormed) {
            Log.e(TAG, "No active connection for file transfer")
            notifyCallbacks { onFileTransferFailed(fileName, "No active connection") }
            return
        }

        // Determine target address based on our role
        val targetAddress = if (connection.isGroupOwner) {
            var addr = clientAddress ?: resolveClientAddressFromArp()
            var waitAttempts = 0
            while (addr == null && waitAttempts < 24) {
                delay(250L)
                addr = clientAddress ?: resolveClientAddressFromArp()
                waitAttempts++
            }
            if (addr == null) {
                Log.e(TAG, "Client address not yet known — wait for client to connect first")
                notifyCallbacks { onFileTransferFailed(fileName, "Peer not ready yet — please tap Retry") }
                return
            }
            clientAddress = addr
            Log.d(TAG, "Group owner sending to client at $addr")
            addr
        } else {
            connection.groupOwnerAddress?.hostAddress
        }

        if (targetAddress == null) {
            Log.e(TAG, "No target address available")
            notifyCallbacks { onFileTransferFailed(fileName, "No target address") }
            return
        }

        val wifiLock = acquireWifiLock("tx")
        val wakeLock = acquireWakeLock("tx")
        var inputStream: InputStream? = null
        var outputStream: DataOutputStream? = null
        var socket: Socket? = null
        var tempStagedFile: File? = null

        try {
            var effectiveFileSize = fileSize
            if (filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(filePath)
                if (effectiveFileSize <= 0L) {
                    runCatching {
                        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            if (pfd.statSize > 0L) effectiveFileSize = pfd.statSize
                        }
                    }
                }
                if (effectiveFileSize <= 0L) {
                    runCatching {
                        contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                            if (afd.length > 0L) effectiveFileSize = afd.length
                        }
                    }
                }
                val rawStream = contentResolver.openInputStream(uri)
                if (rawStream == null) {
                    notifyCallbacks { onFileTransferFailed(fileName, "Could not open selected file") }
                    return
                }
                // Fallback for virtual/cloud ContentProviders that do not report file size upfront
                if (effectiveFileSize <= 0L) {
                    val staged = File(cacheDir, "hyperdrop_stage_${System.currentTimeMillis()}_${File(fileName).name}")
                    rawStream.use { input ->
                        FileOutputStream(staged).use { output ->
                            input.copyTo(output, BUFFER_SIZE)
                        }
                    }
                    tempStagedFile = staged
                    effectiveFileSize = staged.length()
                    inputStream = BufferedInputStream(FileInputStream(staged), BUFFER_SIZE)
                    Log.d(TAG, "📂 Staged unknown-length content URI: $filePath ($effectiveFileSize bytes)")
                } else {
                    inputStream = BufferedInputStream(rawStream, BUFFER_SIZE)
                    Log.d(TAG, "📂 Streaming directly from content URI: $filePath ($effectiveFileSize bytes)")
                }
            } else {
                val file = File(filePath)
                if (!file.exists()) {
                    notifyCallbacks { onFileTransferFailed(fileName, "File not found") }
                    return
                }
                if (effectiveFileSize <= 0L) {
                    effectiveFileSize = file.length()
                }
                inputStream = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
                Log.d(TAG, "📂 Streaming from file: $filePath ($effectiveFileSize bytes)")
            }

            if (effectiveFileSize <= 0L) {
                notifyCallbacks { onFileTransferFailed(fileName, "Cannot send empty (0 byte) file") }
                return
            }

            // Connect with retry in case receiver's ServerSocket is still binding after group formation
            var lastConnectError: Exception? = null
            for (attempt in 0 until 5) {
                if (attempt > 0) delay(350L * attempt)
                try {
                    val candidate = Socket()
                    candidate.tcpNoDelay = true
                    candidate.sendBufferSize = SOCKET_SEND_BUFFER
                    candidate.receiveBufferSize = SOCKET_RECV_BUFFER
                    candidate.soTimeout = SOCKET_TIMEOUT
                    candidate.connect(InetSocketAddress(targetAddress, 8988), 8_000)
                    socket = candidate
                    break
                } catch (e: Exception) {
                    lastConnectError = e
                    Log.w(TAG, "Socket connect attempt ${attempt + 1}/5 failed: ${e.message}")
                }
            }

            val activeSocket = socket ?: throw (lastConnectError ?: IOException("Failed to connect to recipient"))
            isTransferCancelled = false
            activeTransferSocket = activeSocket
            outputStream = DataOutputStream(BufferedOutputStream(activeSocket.getOutputStream(), BUFFER_SIZE))

            Log.d(TAG, "⚡ TURBO: Socket connected with ${SOCKET_SEND_BUFFER / 1024}KB send buffer")

            // 1. Send V2 header: [HEADER_MAGIC_V2:int][nameLength:int][nameBytes][fileSize:long][fileIndex:int][totalFiles:int][senderLen:int][senderBytes]
            val nameBytes = fileName.toByteArray(Charsets.UTF_8)
            val senderBytes = senderName.toByteArray(Charsets.UTF_8)
            outputStream.writeInt(HEADER_MAGIC_V2)
            outputStream.writeInt(nameBytes.size)
            outputStream.write(nameBytes)
            outputStream.writeLong(effectiveFileSize)
            outputStream.writeInt(fileIndex)
            outputStream.writeInt(totalFiles)
            outputStream.writeInt(senderBytes.size)
            outputStream.write(senderBytes)
            outputStream.flush()
            Log.d(TAG, "📤 Sent V2 file header: $fileName ($fileIndex/$totalFiles), size=$effectiveFileSize — awaiting recipient approval...")

            // 2. Wait for 1-byte recipient approval (1 = accepted, 0 = rejected)
            activeSocket.soTimeout = 65_000
            val approvalByte = activeSocket.getInputStream().read()
            if (approvalByte != 1) {
                val reason = if (approvalByte == 0) "Transfer declined by recipient" else "Recipient did not respond in time"
                Log.w(TAG, "❌ Transfer not approved ($approvalByte): $reason")
                notifyCallbacks { onFileTransferFailed(fileName, reason) }
                return
            }
            activeSocket.soTimeout = SOCKET_TIMEOUT

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesSent = 0L
            var lastReportedProgress = -1

            notifyCallbacks { onFileTransferStarted(fileName, effectiveFileSize, fileIndex, totalFiles) }

            // 3. Stream file payload
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isTransferCancelled) {
                    throw IOException("Transfer cancelled")
                }
                outputStream.write(buffer, 0, bytesRead)
                totalBytesSent += bytesRead

                val safeTotalBytes = if (effectiveFileSize > 0L) effectiveFileSize else maxOf(totalBytesSent, 1L)
                val progress = ((totalBytesSent * 100) / safeTotalBytes).toInt().coerceIn(0, 100)

                if (progress - lastReportedProgress >= PROGRESS_REPORT_INTERVAL || progress >= 100) {
                    lastReportedProgress = progress
                    notifyCallbacks { onFileTransferProgress(fileName, progress, totalBytesSent) }
                }
            }

            outputStream.flush()

            // 4. Wait for 1-byte completion ACK (2) confirming recipient flushed file to disk
            try {
                activeSocket.soTimeout = 12_000
                val ackByte = activeSocket.getInputStream().read()
                Log.d(TAG, "📥 Received completion ACK ($ackByte) for $fileName")
            } catch (e: Exception) {
                Log.w(TAG, "Completion ACK read warning (bytes already flushed): ${e.message}")
            }

            notifyCallbacks { onFileTransferCompleted(fileName, fileIndex, totalFiles) }
            Log.d(TAG, "✅ File transfer completed: $fileName ($totalBytesSent bytes, $fileIndex/$totalFiles)")

        } catch (e: Exception) {
            Log.e(TAG, "Error during file transfer", e)
            notifyCallbacks { onFileTransferFailed(fileName, e.message ?: "Transfer failed") }
        } finally {
            if (activeTransferSocket === socket) {
                activeTransferSocket = null
            }
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
            runCatching { tempStagedFile?.delete() }
            runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    /**
     * Start a resilient file receiver server on port 8988.
     */
    private suspend fun startReceiveServer() {
        if (isServerRunning && serverSocket != null && serverSocket?.isClosed == false) {
            return
        }
        isServerRunning = true
        try {
            if (serverSocket == null || serverSocket?.isClosed == true) {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    receiveBufferSize = SOCKET_RECV_BUFFER
                    bind(InetSocketAddress(8988))
                }
                Log.d(TAG, "⚡ TURBO: File receive server started on port 8988 with ${SOCKET_RECV_BUFFER / 1024}KB buffer")
            }

            while (serverSocket?.isClosed == false) {
                val clientSocket = try {
                    serverSocket?.accept()
                } catch (e: IOException) {
                    if (serverSocket?.isClosed == false) {
                        Log.e(TAG, "Error accepting incoming file connection", e)
                    }
                    break
                }
                clientSocket?.let { socket ->
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = SOCKET_RECV_BUFFER
                    socket.sendBufferSize = SOCKET_SEND_BUFFER
                    socket.soTimeout = SOCKET_TIMEOUT

                    serviceScope.launch {
                        handleIncomingFile(socket)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in receive server", e)
        } finally {
            isServerRunning = false
        }
    }

    private suspend fun handleIncomingFile(socket: Socket) {
        var inputStream: DataInputStream? = null
        var fileOutputStream: OutputStream? = null
        var safeFileName = "unknown"
        var outFile: File? = null
        var transferComplete = false
        val wifiLock = acquireWifiLock("rx")
        val wakeLock = acquireWakeLock("rx")

        try {
            // Capture the client's IP for bidirectional transfer
            val remoteAddr = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
            if (remoteAddr != null) {
                clientAddress = remoteAddr
                hasConnectedPeerInGroup = true
                Log.d(TAG, "📍 Captured peer address: $remoteAddr")
            }

            inputStream = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))
            val socketOut = socket.getOutputStream()

            // Read first integer (either HEADER_MAGIC_PING, HEADER_MAGIC_V2, or legacy nameLength)
            val firstInt = inputStream.readInt()
            if (firstInt == HEADER_MAGIC_PING) {
                socketOut.write(1)
                socketOut.flush()
                Log.d(TAG, "📍 Handled client IP registration ping from $remoteAddr")
                val conn = currentConnection
                if (conn != null && conn.groupFormed && conn.isGroupOwner) {
                    notifyCallbacks { onConnected(conn) }
                }
                return
            }

            val nameLength: Int
            val rawFileName: String
            val fileSize: Long
            var fileIndex = 1
            var totalFiles = 1
            var senderName = "Nearby Device"

            if (firstInt == HEADER_MAGIC_V2) {
                nameLength = inputStream.readInt()
                if (nameLength <= 0 || nameLength > 4096) {
                    Log.e(TAG, "Invalid incoming V2 file name length: $nameLength")
                    socketOut.write(0)
                    socketOut.flush()
                    return
                }
                val nameBytes = ByteArray(nameLength)
                inputStream.readFully(nameBytes)
                rawFileName = String(nameBytes, Charsets.UTF_8)
                fileSize = inputStream.readLong()
                fileIndex = inputStream.readInt().coerceAtLeast(1)
                totalFiles = inputStream.readInt().coerceAtLeast(1)
                val senderLen = inputStream.readInt()
                if (senderLen in 1..512) {
                    val senderBytes = ByteArray(senderLen)
                    inputStream.readFully(senderBytes)
                    senderName = String(senderBytes, Charsets.UTF_8)
                }
            } else {
                nameLength = firstInt
                if (nameLength <= 0 || nameLength > 4096) {
                    Log.e(TAG, "Invalid incoming file name length: $nameLength")
                    socketOut.write(0)
                    socketOut.flush()
                    return
                }
                val nameBytes = ByteArray(nameLength)
                inputStream.readFully(nameBytes)
                rawFileName = String(nameBytes, Charsets.UTF_8)
                fileSize = inputStream.readLong()
            }

            val baseSafeName = File(rawFileName).name.ifBlank { "received_file_${System.currentTimeMillis()}" }
            if (fileSize <= 0L || fileSize > 10L * 1024 * 1024 * 1024) {
                Log.e(TAG, "Invalid incoming file size: $fileSize")
                socketOut.write(0)
                socketOut.flush()
                return
            }

            // Resolve target output path upfront so safeFileName stays consistent across all callbacks
            val targetDir = File(getExternalFilesDir(null), "received_files")
            if (!targetDir.exists()) targetDir.mkdirs()
            var resolvedFile = File(targetDir, baseSafeName)
            if (resolvedFile.exists()) {
                val dotIndex = baseSafeName.lastIndexOf('.')
                val base = if (dotIndex > 0) baseSafeName.substring(0, dotIndex) else baseSafeName
                val ext = if (dotIndex > 0) baseSafeName.substring(dotIndex) else ""
                var counter = 1
                while (resolvedFile.exists()) {
                    resolvedFile = File(targetDir, "$base ($counter)$ext")
                    counter++
                }
            }
            outFile = resolvedFile
            safeFileName = resolvedFile.name

            isTransferCancelled = false
            activeTransferSocket = socket

            // SECURITY CHECK: Should we accept this file?
            val now = System.currentTimeMillis()
            val isRecentlyApprovedPeer = remoteAddr != null &&
                remoteAddr == lastAcceptedPeerAddress &&
                (now - lastAcceptedTimestamp) < 120_000L

            if (isNfcTrustedSession || isRecentlyApprovedPeer) {
                Log.d(TAG, "✅ Auto-accepting file from trusted/approved session: $safeFileName ($fileIndex/$totalFiles)")
                isNfcTrustedSession = false
            } else {
                Log.d(TAG, "🔒 Requesting user confirmation for: $safeFileName ($fileSize bytes, $fileIndex/$totalFiles) from $senderName")
                notifyCallbacks {
                    onFileTransferRequested(safeFileName, fileSize, fileIndex, totalFiles, senderName)
                }

                // Wait for UI to confirm or reject
                val deferred = CompletableDeferred<Boolean>()
                pendingConfirmation = deferred
                val accepted = try {
                    withTimeout(60_000) {
                        deferred.await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Confirmation timed out")
                    false
                } finally {
                    if (pendingConfirmation === deferred) {
                        pendingConfirmation = null
                    }
                }

                if (!accepted) {
                    Log.w(TAG, "❌ File transfer rejected by user or timeout: $safeFileName")
                    runCatching {
                        socketOut.write(0)
                        socketOut.flush()
                    }
                    socket.close()
                    return
                }
                if (remoteAddr != null) {
                    lastAcceptedPeerAddress = remoteAddr
                    lastAcceptedTimestamp = System.currentTimeMillis()
                }
                Log.d(TAG, "✅ File transfer manually accepted by user")
            }

            // Signal approval (1) to sender so it begins streaming payload
            socketOut.write(1)
            socketOut.flush()

            fileOutputStream = BufferedOutputStream(FileOutputStream(resolvedFile), BUFFER_SIZE)

            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytesRead = 0L
            var lastReportedProgress = -1

            notifyCallbacks { onFileTransferStarted(safeFileName, fileSize, fileIndex, totalFiles) }

            while (totalBytesRead < fileSize) {
                if (isTransferCancelled) {
                    throw IOException("Transfer cancelled")
                }
                val bytesToRead = minOf(buffer.size.toLong(), fileSize - totalBytesRead).toInt()
                val bytesRead = inputStream.read(buffer, 0, bytesToRead)
                if (bytesRead == -1) break
                fileOutputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val progress = if (fileSize > 0L) ((totalBytesRead * 100) / fileSize).toInt().coerceIn(0, 100) else 100
                if (progress - lastReportedProgress >= PROGRESS_REPORT_INTERVAL || progress >= 100) {
                    lastReportedProgress = progress
                    notifyCallbacks { onFileTransferProgress(safeFileName, progress, totalBytesRead) }
                }
            }

            fileOutputStream.flush()
            fileOutputStream.close()
            fileOutputStream = null

            if (fileSize > 0L && totalBytesRead < fileSize) {
                throw IOException("Incomplete transfer ($totalBytesRead / $fileSize bytes)")
            }
            transferComplete = true

            // Ensure extensionless files receive their real extension (e.g. .mp4, .jpg) via magic-byte inspection
            val finalFile = com.seamless.android.ui.fileshare.HyperDropUtils.ensureFileExtension(resolvedFile)
            resolvedFile = finalFile
            outFile = finalFile
            safeFileName = finalFile.name

            // Refresh batch approval timestamp so subsequent queued files in a batch auto-stream seamlessly
            if (remoteAddr != null) {
                lastAcceptedTimestamp = System.currentTimeMillis()
            }

            // Send 1-byte completion ACK (2) to sender immediately
            runCatching {
                socketOut.write(2)
                socketOut.flush()
            }

            // Also publish received file to public Downloads/Seamless in background so it is visible in Gallery/Files
            serviceScope.launch {
                exportToPublicDownloads(resolvedFile)
            }

            notifyCallbacks { onFileTransferCompleted(safeFileName, fileIndex, totalFiles) }
            Log.d(TAG, "✅ Received file saved to: ${resolvedFile.absolutePath} ($fileIndex/$totalFiles)")
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving file", e)
            notifyCallbacks { onFileTransferFailed(safeFileName, e.message ?: "Receive failed") }
        } finally {
            if (activeTransferSocket === socket) {
                activeTransferSocket = null
            }
            try {
                inputStream?.close()
                fileOutputStream?.close()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing receive streams", e)
            }
            if (!transferComplete) {
                runCatching { outFile?.takeIf { it.exists() }?.delete() }
            }
            runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    /**
     * Automatically export a received file to public Downloads/Seamless so the user
     * can access it immediately in their system Gallery, Files, and Downloads apps.
     */
    private fun exportToPublicDownloads(sourceFile: File) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return
        try {
            val mime = com.seamless.android.ui.fileshare.HyperDropUtils.resolveMimeType(sourceFile)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Seamless")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out, BUFFER_SIZE)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Log.d(TAG, "📂 Auto-exported received file to Downloads/Seamless: ${sourceFile.name}")
                }
            } else {
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Seamless"
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mime),
                    null
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-export to public Downloads note: ${e.message}")
        }
    }

    private fun cleanup() {
        channel?.let { ch ->
            dnsSdServiceRequest?.let { request ->
                wifiP2pManager.removeServiceRequest(ch, request, null)
            }
            wifiP2pManager.clearLocalServices(ch, null)
        }
        dnsSdServiceRequest = null
        isServiceRequestRegistrationInFlight = false
        servicePeersByAddress.clear()
        p2pPeersByAddress.clear()
        try {
            serverSocket?.close()
            serverSocket = null
            isServerRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }

    private fun notifyCallbacks(block: WifiDirectCallback.() -> Unit) {
        handler.post {
            callbacks.forEach { callback ->
                try {
                    block(callback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying callback", e)
                }
            }
        }
    }

    fun addCallback(callback: WifiDirectCallback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    fun removeCallback(callback: WifiDirectCallback) {
        callbacks.remove(callback)
    }

    // Broadcast receiver for Wi-Fi Direct events
    private inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "Wi-Fi Direct state changed: $isEnabled")
                    notifyCallbacks { onWifiDirectStateChanged(isEnabled) }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!hasWifiDirectPermission()) {
                        Log.w(TAG, "Missing permission for Wi‑Fi Direct peer discovery callback")
                        return
                    }

                    wifiP2pManager.requestPeers(channel) { peers ->
                        Log.d(TAG, "Peers discovered: ${peers.deviceList.size}")

                        if (peers.deviceList.isNotEmpty()) {
                            lastDeviceFoundTime = System.currentTimeMillis()
                            discoveryRetryCount = 0
                        }

                        p2pPeersByAddress.clear()
                        peers.deviceList.forEach { rawPeer ->
                            val copy = WifiP2pDevice(rawPeer)
                            val addr = copy.deviceAddress?.lowercase(Locale.US)
                            if (!addr.isNullOrBlank()) p2pPeersByAddress[addr] = copy
                        }
                        publishDiscoveredPeers()
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO) as? WifiP2pInfo
                    @Suppress("DEPRECATION")
                    val wifiP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP) as? WifiP2pGroup

                    currentConnection = wifiP2pInfo

                    if (wifiP2pInfo?.groupFormed == true) {
                        Log.d(TAG, "P2P group formed: ${wifiP2pInfo.groupOwnerAddress} (isGroupOwner=${wifiP2pInfo.isGroupOwner})")
                        // Start receive server on BOTH roles (owner + client)
                        serviceScope.launch {
                            startReceiveServer()
                        }
                        if (!wifiP2pInfo.isGroupOwner) {
                            // We are Client — we are definitely connected to the Group Owner
                            hasConnectedPeerInGroup = true
                            sendClientRegistrationPing(wifiP2pInfo.groupOwnerAddress?.hostAddress)
                            notifyCallbacks { onConnected(wifiP2pInfo) }
                        } else {
                            // We are Group Owner — verify whether an actual Client is connected
                            val directClients = wifiP2pGroup?.clientList.orEmpty()
                            if (directClients.isNotEmpty()) {
                                hasConnectedPeerInGroup = true
                                connectedClientMacs.clear()
                                directClients.forEach { c ->
                                    if (!c.deviceAddress.isNullOrBlank()) {
                                        connectedClientMacs.add(c.deviceAddress)
                                    }
                                }
                                notifyCallbacks { onConnected(wifiP2pInfo) }
                            } else {
                                val ch = channel
                                if (ch != null && hasWifiDirectPermission()) {
                                    wifiP2pManager.requestGroupInfo(ch) { groupInfo ->
                                        val clients = groupInfo?.clientList.orEmpty()
                                        if (clients.isNotEmpty() || clientAddress != null) {
                                            hasConnectedPeerInGroup = true
                                            connectedClientMacs.clear()
                                            clients.forEach { c ->
                                                if (!c.deviceAddress.isNullOrBlank()) {
                                                    connectedClientMacs.add(c.deviceAddress)
                                                }
                                            }
                                            notifyCallbacks { onConnected(wifiP2pInfo) }
                                        } else {
                                            hasConnectedPeerInGroup = false
                                            Log.d(TAG, "Autonomous group has 0 clients — waiting for peer connection before firing onConnected")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Disconnected from group")
                        isNfcTrustedSession = false
                        hasConnectedPeerInGroup = false
                        connectedClientMacs.clear()
                        clientAddress = null
                        lastAcceptedPeerAddress = null
                        notifyCallbacks { onDisconnected() }
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as? WifiP2pDevice
                    device?.let {
                        Log.d(TAG, "This device changed: ${it.deviceName}")
                        notifyCallbacks { onThisDeviceChanged(it) }
                    }
                }
            }
        }
    }
}

/**
 * Callback interface for Wi-Fi Direct events
 */
interface WifiDirectCallback {
    fun onWifiDirectStateChanged(enabled: Boolean) {}
    fun onDiscoveryStarted() {}
    fun onDiscoveryFailed(reason: Int) {}
    fun onDiscoveryStopped() {}
    fun onPeersDiscovered(peers: Collection<WifiP2pDevice>) {}
    fun onConnectionInitiated(deviceAddress: String) {}
    fun onConnectionFailed(reason: Int) {}
    fun onConnected(connectionInfo: WifiP2pInfo) {}
    fun onDisconnected() {}
    fun onThisDeviceChanged(device: WifiP2pDevice) {}
    fun onFileTransferRequested(
        fileName: String,
        fileSize: Long,
        fileIndex: Int = 1,
        totalFiles: Int = 1,
        senderName: String = "Nearby Device"
    ) {}
    fun onFileTransferStarted(
        fileName: String,
        fileSize: Long,
        fileIndex: Int = 1,
        totalFiles: Int = 1
    ) {}
    fun onFileTransferProgress(fileName: String, progress: Int, bytesTransferred: Long) {}
    fun onFileTransferCompleted(
        fileName: String,
        fileIndex: Int = 1,
        totalFiles: Int = 1
    ) {}
    fun onFileTransferFailed(fileName: String, error: String) {}
}
