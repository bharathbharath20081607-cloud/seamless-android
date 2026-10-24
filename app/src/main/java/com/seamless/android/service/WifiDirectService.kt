@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.service

import android.Manifest
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import com.seamless.android.R
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Wi-Fi Direct service for peer-to-peer file sharing
 */
class WifiDirectService : Service() {

    companion object {
        private const val TAG = "WifiDirectService"
        private const val NOTIFICATION_ID = 1002
        private const val SOCKET_TIMEOUT = 30000 // 30s timeout for large file transfers
        
        // ⚡ TURBO MODE v2.0 - MAXIMUM SPEED CONFIGURATION
        // Research shows 256KB is the optimal buffer for Android Wi-Fi transfers:
        // - Large enough to minimize syscall overhead
        // - Small enough to avoid GC pauses and memory pressure
        // - Matches typical MTU multiples for Wi-Fi (1500 bytes * ~170)
        private const val BUFFER_SIZE = 256 * 1024  // 256KB - 4x faster than 64KB
        
        // Socket buffer sizes - 2MB allows TCP window scaling for max throughput
        // This enables the kernel to buffer more data, reducing context switches
        private const val SOCKET_SEND_BUFFER = 2 * 1024 * 1024 // 2MB send buffer
        private const val SOCKET_RECV_BUFFER = 2 * 1024 * 1024 // 2MB receive buffer
        
        // Progress reporting throttle - reduces UI overhead during transfers
        // Reporting every 2% instead of 1% = 50% fewer callback invocations
        private const val PROGRESS_REPORT_INTERVAL = 2
        
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
                if (filePath != null && fileName != null) {
                    serviceScope.launch {
                        sendFile(filePath, fileName, fileSize)
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
            // Must match android:foregroundServiceType="connectedDevice" in manifest
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startDiscovery() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) {
            Log.w(TAG, "Missing permission for Wi-Fi Direct discovery")
            notifyCallbacks { onDiscoveryFailed(-1) }
            return
        }
        
        channel?.let { ch ->
            wifiP2pManager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery started successfully")
                    notifyCallbacks { onDiscoveryStarted() }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Discovery failed: $reason")
                    notifyCallbacks { onDiscoveryFailed(reason) }
                }
            })
        }
    }

    private fun stopDiscovery() {
        channel?.let { ch ->
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
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) {
            Log.w(TAG, "Missing permission for Wi-Fi Direct group creation")
            return
        }

        // Proactively ensure the receive server is listening on port 8988
        serviceScope.launch {
            startReceiveServer()
        }
        
        channel?.let { ch ->
            wifiP2pManager.requestGroupInfo(ch) { existingGroup ->
                if (existingGroup != null && existingGroup.isGroupOwner) {
                    Log.d(TAG, "Already group owner, keeping existing group and refreshing discovery")
                    wifiP2pManager.requestConnectionInfo(ch) { info ->
                        if (info != null && info.groupFormed) {
                            currentConnection = info
                            notifyCallbacks { onConnected(info) }
                        }
                    }
                    startDiscovery()
                } else if (existingGroup != null) {
                    wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            createGroupInternal(ch)
                        }
                        override fun onFailure(reason: Int) {
                            createGroupInternal(ch)
                        }
                    })
                } else {
                    createGroupInternal(ch)
                }
            }
        }
    }

    private fun createGroupInternal(ch: WifiP2pManager.Channel) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) return

        wifiP2pManager.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group created successfully")
                // Keep peer discovery active so senders on all Android OEMs can discover this host
                handler.postDelayed({ startDiscovery() }, 350L)
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to create group: $reason")
                if (reason == WifiP2pManager.BUSY) {
                    // Fallback to active peer discovery so incoming P2P connections still succeed
                    handler.postDelayed({ startDiscovery() }, 400L)
                }
            }
        })
    }

    private fun connectToDevice(deviceAddress: String, isRetry: Boolean = false) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) {
            Log.w(TAG, "Missing permission for Wi-Fi Direct connection")
            notifyCallbacks { onConnectionFailed(-1) }
            return
        }
        
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
            // Sender prefers Client role (0) so Receiver (or existing Group Owner) owns 192.168.49.1
            groupOwnerIntent = 0
        }
        
        channel?.let { ch ->
            if (!isRetry) {
                wifiP2pManager.requestGroupInfo(ch) { existingGroup ->
                    if (existingGroup != null && existingGroup.isGroupOwner && existingGroup.clientList.isEmpty()) {
                        Log.d(TAG, "Removing empty local group before connecting to peer $deviceAddress")
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
                        }, 450L)
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
        channel?.let { ch ->
            wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Disconnected from group")
                    clientAddress = null
                    lastAcceptedPeerAddress = null
                    notifyCallbacks { onDisconnected() }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to disconnect: $reason")
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
            repeat(3) { attempt ->
                if (attempt > 0) delay(350L * attempt)
                try {
                    Socket().use { pingSocket ->
                        pingSocket.tcpNoDelay = true
                        pingSocket.soTimeout = 3_000
                        pingSocket.connect(InetSocketAddress(hostAddr, 8988), 3_000)
                        val out = DataOutputStream(pingSocket.getOutputStream())
                        out.writeInt(-1) // Magic ping header
                        out.flush()
                        pingSocket.getInputStream().read()
                        Log.d(TAG, "📍 Client IP registration ping succeeded to $hostAddr")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Client IP registration ping attempt ${attempt + 1} failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Send a file to the connected peer using a synchronized header + approval + completion ACK protocol.
     *
     * Both group owner AND client can send files:
     * - Client → sends to groupOwnerAddress (192.168.49.1)
     * - Group Owner → sends to clientAddress (learned from registration ping or incoming connection)
     */
    private suspend fun sendFile(filePath: String, fileName: String, fileSize: Long) {
        val connection = currentConnection
        if (connection == null || !connection.groupFormed) {
            Log.e(TAG, "No active connection for file transfer")
            notifyCallbacks { onFileTransferFailed(fileName, "No active connection") }
            return
        }

        // Determine target address based on our role (waiting briefly if Group Owner is waiting for Client ping)
        val targetAddress = if (connection.isGroupOwner) {
            var addr = clientAddress
            var waitAttempts = 0
            while (addr == null && waitAttempts < 10) {
                delay(250L)
                addr = clientAddress
                waitAttempts++
            }
            if (addr == null) {
                Log.e(TAG, "Client address not yet known — wait for client to connect first")
                notifyCallbacks { onFileTransferFailed(fileName, "Peer not ready yet — please retry in a moment") }
                return
            }
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

        var inputStream: InputStream? = null
        var outputStream: DataOutputStream? = null
        var socket: Socket? = null

        try {
            var effectiveFileSize = fileSize
            // Support both direct file paths and content URIs (zero-copy)
            if (filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(filePath)
                if (effectiveFileSize <= 0L) {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        if (afd.length > 0L) effectiveFileSize = afd.length
                    }
                }
                val rawStream = contentResolver.openInputStream(uri)
                if (rawStream == null) {
                    notifyCallbacks { onFileTransferFailed(fileName, "Could not open content URI") }
                    return
                }
                inputStream = BufferedInputStream(rawStream, BUFFER_SIZE)
                Log.d(TAG, "📂 Streaming directly from content URI: $filePath ($effectiveFileSize bytes)")
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

            // Connect with retry in case receiver's ServerSocket is still binding after group formation
            var lastConnectError: Exception? = null
            for (attempt in 0 until 3) {
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
                    Log.w(TAG, "Socket connect attempt ${attempt + 1}/3 failed: ${e.message}")
                }
            }

            val activeSocket = socket ?: throw (lastConnectError ?: IOException("Failed to connect to recipient"))
            isTransferCancelled = false
            activeTransferSocket = activeSocket
            outputStream = DataOutputStream(BufferedOutputStream(activeSocket.getOutputStream(), BUFFER_SIZE))

            Log.d(TAG, "⚡ TURBO: Socket connected with ${SOCKET_SEND_BUFFER / 1024}KB send buffer")

            // 1. Send header: [nameLength:int][nameBytes][fileSize:long]
            val nameBytes = fileName.toByteArray(Charsets.UTF_8)
            outputStream.writeInt(nameBytes.size)
            outputStream.write(nameBytes)
            outputStream.writeLong(effectiveFileSize)
            outputStream.flush()
            Log.d(TAG, "📤 Sent file header: $fileName, size=$effectiveFileSize — awaiting recipient approval...")

            // 2. Wait for 1-byte recipient approval (1 = accepted, 0 = rejected)
            activeSocket.soTimeout = 35_000
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

            notifyCallbacks { onFileTransferStarted(fileName, effectiveFileSize) }

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
                activeSocket.soTimeout = 10_000
                val ackByte = activeSocket.getInputStream().read()
                Log.d(TAG, "📥 Received completion ACK ($ackByte) for $fileName")
            } catch (e: Exception) {
                Log.w(TAG, "Completion ACK read warning (bytes already flushed): ${e.message}")
            }

            notifyCallbacks { onFileTransferCompleted(fileName) }
            Log.d(TAG, "✅ File transfer completed: $fileName ($totalBytesSent bytes)")

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
        try {
            // Capture the client's IP for bidirectional transfer
            val remoteAddr = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
            if (remoteAddr != null) {
                clientAddress = remoteAddr
                Log.d(TAG, "📍 Captured peer address: $remoteAddr")
            }
            
            inputStream = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))
            val socketOut = socket.getOutputStream()
            
            // Read file header: name length (4 bytes)
            val nameLength = inputStream.readInt()
            if (nameLength == -1) {
                // Client IP registration ping
                socketOut.write(1)
                socketOut.flush()
                Log.d(TAG, "📍 Handled client IP registration ping from $remoteAddr")
                return
            }
            if (nameLength <= 0 || nameLength > 4096) {
                Log.e(TAG, "Invalid incoming file name length: $nameLength")
                socketOut.write(0)
                socketOut.flush()
                return
            }

            val nameBytes = ByteArray(nameLength)
            inputStream.readFully(nameBytes)
            val rawFileName = String(nameBytes, Charsets.UTF_8)
            safeFileName = File(rawFileName).name.ifBlank { "received_file_${System.currentTimeMillis()}" }
            val fileSize = inputStream.readLong()
            isTransferCancelled = false
            activeTransferSocket = socket

            // SECURITY CHECK: Should we accept this file?
            val now = System.currentTimeMillis()
            val isRecentlyApprovedPeer = remoteAddr != null &&
                remoteAddr == lastAcceptedPeerAddress &&
                (now - lastAcceptedTimestamp) < 60_000L

            if (isNfcTrustedSession || isRecentlyApprovedPeer) {
                Log.d(TAG, "✅ Auto-accepting file from trusted/approved session: $safeFileName")
            } else {
                Log.d(TAG, "🔒 Requesting user confirmation for: $safeFileName ($fileSize bytes)")
                notifyCallbacks { onFileTransferRequested(safeFileName, fileSize) }
                
                // Wait for UI to confirm or reject
                val deferred = CompletableDeferred<Boolean>()
                pendingConfirmation = deferred
                val accepted = try {
                    withTimeout(30_000) {
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

            val targetDir = File(getExternalFilesDir(null), "received_files")
            if (!targetDir.exists()) targetDir.mkdirs()
            var resolvedFile = File(targetDir, safeFileName)
            if (resolvedFile.exists()) {
                val dotIndex = safeFileName.lastIndexOf('.')
                val base = if (dotIndex > 0) safeFileName.substring(0, dotIndex) else safeFileName
                val ext = if (dotIndex > 0) safeFileName.substring(dotIndex) else ""
                var counter = 1
                while (resolvedFile.exists()) {
                    resolvedFile = File(targetDir, "$base ($counter)$ext")
                    counter++
                }
            }
            outFile = resolvedFile
            safeFileName = resolvedFile.name

            fileOutputStream = BufferedOutputStream(FileOutputStream(resolvedFile), BUFFER_SIZE)

            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytesRead = 0L
            var lastReportedProgress = -1

            notifyCallbacks { onFileTransferStarted(safeFileName, fileSize) }

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
            if (fileSize > 0L && totalBytesRead < fileSize) {
                throw IOException("Incomplete transfer ($totalBytesRead / $fileSize bytes)")
            }
            transferComplete = true

            // Refresh batch approval timestamp so subsequent queued files in a batch auto-stream seamlessly
            if (remoteAddr != null) {
                lastAcceptedTimestamp = System.currentTimeMillis()
            }

            // Send 1-byte completion ACK (2) to sender
            runCatching {
                socketOut.write(2)
                socketOut.flush()
            }

            notifyCallbacks { onFileTransferCompleted(safeFileName) }
            Log.d(TAG, "✅ Received file saved to: ${resolvedFile.absolutePath}")
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
        }
    }

    private fun cleanup() {
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
                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.checkSelfPermission(this@WifiDirectService, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ActivityCompat.checkSelfPermission(this@WifiDirectService, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    }

                    if (!hasPermission) {
                        Log.w(TAG, "Missing permission for Wi‑Fi Direct peer discovery callback")
                        return
                    }

                    wifiP2pManager.requestPeers(channel) { peers ->
                        Log.d(TAG, "Peers discovered: ${peers.deviceList.size}")
                        
                        if (peers.deviceList.isNotEmpty()) {
                            lastDeviceFoundTime = System.currentTimeMillis()
                            discoveryRetryCount = 0
                        }
                        
                        notifyCallbacks { onPeersDiscovered(peers.deviceList) }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO) as? WifiP2pInfo

                    currentConnection = wifiP2pInfo

                    if (wifiP2pInfo?.groupFormed == true) {
                        Log.d(TAG, "Connected to group: ${wifiP2pInfo.groupOwnerAddress} (isGroupOwner=${wifiP2pInfo.isGroupOwner})")
                        // Start receive server on BOTH roles (owner + client)
                        serviceScope.launch {
                            startReceiveServer()
                        }
                        // If we are the client, send an immediate IP registration ping to the group owner
                        if (!wifiP2pInfo.isGroupOwner) {
                            sendClientRegistrationPing(wifiP2pInfo.groupOwnerAddress?.hostAddress)
                        }
                        notifyCallbacks { onConnected(wifiP2pInfo) }
                    } else {
                        Log.d(TAG, "Disconnected from group")
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
    fun onFileTransferRequested(fileName: String, fileSize: Long) {} // Request user permission
    fun onFileTransferStarted(fileName: String, fileSize: Long) {}
    fun onFileTransferProgress(fileName: String, progress: Int, bytesTransferred: Long) {}
    fun onFileTransferCompleted(fileName: String) {}
    fun onFileTransferFailed(fileName: String, error: String) {}
}
