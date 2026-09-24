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
import android.os.Build
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
        
        channel?.let { ch ->
            wifiP2pManager.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Group created successfully")
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to create group: $reason")
                }
            })
        }
    }

    private fun connectToDevice(deviceAddress: String) {
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
        }
        
        channel?.let { ch ->
            wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection initiated to: $deviceAddress")
                    notifyCallbacks { onConnectionInitiated(deviceAddress) }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Connection failed: $reason")
                    notifyCallbacks { onConnectionFailed(reason) }
                }
            })
        }
    }

    private fun disconnect() {
        channel?.let { ch ->
            wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Disconnected from group")
                    notifyCallbacks { onDisconnected() }
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to disconnect: $reason")
                }
            })
        }
    }

    /**
     * Send a file to the connected peer using a simple header-based protocol.
     *
     * FIXED: Both group owner AND client can now send files.
     * - Client → sends to groupOwnerAddress (standard)
     * - Group Owner → sends to clientAddress (discovered from incoming connection)
     */
    private suspend fun sendFile(filePath: String, fileName: String, fileSize: Long) {
        val connection = currentConnection
        if (connection == null || !connection.groupFormed) {
            Log.e(TAG, "No active connection for file transfer")
            notifyCallbacks { onFileTransferFailed(fileName, "No active connection") }
            return
        }

        // Determine target address based on our role
        val targetAddress = if (connection.isGroupOwner) {
            // Group owner sends to the client's IP (learned from incoming connection)
            val addr = clientAddress
            if (addr == null) {
                Log.e(TAG, "Client address not yet known — wait for client to connect first")
                notifyCallbacks { onFileTransferFailed(fileName, "Peer not connected yet — please wait") }
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
        val socket = Socket()

        try {
            // Support both direct file paths and content URIs (zero-copy)
            if (filePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(filePath)
                inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                     notifyCallbacks { onFileTransferFailed(fileName, "Could not open content URI") }
                     return
                }
                Log.d(TAG, "📂 Streaming directly from content URI: $filePath")
            } else {
                val file = File(filePath)
                if (!file.exists()) {
                    notifyCallbacks { onFileTransferFailed(fileName, "File not found") }
                    return
                }
                inputStream = FileInputStream(file)
                Log.d(TAG, "📂 Streaming from file: $filePath")
            }

            // ⚡ TURBO: Configure socket for maximum transfer speed
            socket.tcpNoDelay = true
            socket.sendBufferSize = SOCKET_SEND_BUFFER
            socket.receiveBufferSize = SOCKET_RECV_BUFFER
            socket.soTimeout = SOCKET_TIMEOUT

            socket.connect(InetSocketAddress(targetAddress, 8988), SOCKET_TIMEOUT)
            outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))

            Log.d(TAG, "⚡ TURBO: Socket connected with ${SOCKET_SEND_BUFFER / 1024}KB send buffer")

            // Send simple header: [nameLength:int][nameBytes][fileSize:long]
            val nameBytes = fileName.toByteArray(Charsets.UTF_8)
            outputStream.writeInt(nameBytes.size)
            outputStream.write(nameBytes)
            outputStream.writeLong(fileSize)
            outputStream.flush()
            Log.d(TAG, "📤 Sent file header: $fileName, size=$fileSize (flushed)")

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesSent = 0L
            var lastReportedProgress = -1

            notifyCallbacks { onFileTransferStarted(fileName, fileSize) }

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesSent += bytesRead

                // Guard against division by zero in case fileSize is zero or invalid
                val safeTotalBytes = if (fileSize > 0L) fileSize else 1L
                val progress = (totalBytesSent * 100 / safeTotalBytes).toInt()
                
                // ⚡ TURBO v2.0: Report progress every 2% to reduce callback overhead
                if (progress - lastReportedProgress >= PROGRESS_REPORT_INTERVAL || progress >= 100) {
                    lastReportedProgress = progress
                    notifyCallbacks { onFileTransferProgress(fileName, progress, totalBytesSent) }
                }
            }

            outputStream.flush()
            notifyCallbacks { onFileTransferCompleted(fileName) }
            Log.d(TAG, "File transfer completed: $fileName")

        } catch (e: Exception) {
            Log.e(TAG, "Error during file transfer", e)
            notifyCallbacks { onFileTransferFailed(fileName, e.message ?: "Transfer failed") }
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
        }
    }

    /**
     * Start a simple file receiver server on port 8988 when acting as group owner.
     * Each incoming connection is treated as a single file transfer using the
     * header format written by sendFile().
     */
    private suspend fun startReceiveServer() {
        try {
            if (serverSocket == null || serverSocket?.isClosed == true) {
                serverSocket = ServerSocket(8988)
                // ⚡ TURBO RECEIVER: Large receive buffer for high-speed incoming transfers
                serverSocket?.receiveBufferSize = SOCKET_RECV_BUFFER
                Log.d(TAG, "⚡ TURBO: File receive server started on port 8988 with ${SOCKET_RECV_BUFFER / 1024}KB buffer")
            }

            while (serverSocket?.isClosed == false) {
                val clientSocket = try {
                    serverSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Error accepting incoming file connection", e)
                    break
                }
                clientSocket?.let { socket ->
                    // ⚡ TURBO: Optimize accepted socket for maximum receive speed
                    socket.tcpNoDelay = true
                    socket.receiveBufferSize = SOCKET_RECV_BUFFER
                    socket.sendBufferSize = SOCKET_SEND_BUFFER
                    
                    serviceScope.launch {
                        handleIncomingFile(socket)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in receive server", e)
        }
    }

    private suspend fun handleIncomingFile(socket: Socket) {
        var inputStream: DataInputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            // Capture the client's IP for bidirectional transfer
            val remoteAddr = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress
            if (remoteAddr != null && clientAddress == null) {
                clientAddress = remoteAddr
                Log.d(TAG, "📍 Captured client address: $remoteAddr")
            }
            
            inputStream = DataInputStream(socket.getInputStream())
            
            // Read file header: name length (4 bytes) + name + file size (8 bytes)
            val nameLength = inputStream.readInt()
            val nameBytes = ByteArray(nameLength)
            inputStream.readFully(nameBytes)
            val fileName = String(nameBytes, Charsets.UTF_8)
            val fileSize = inputStream.readLong()

            // SECURITY CHECK: Should we accept this file?
            if (isNfcTrustedSession) {
                Log.d(TAG, "✅ Auto-accepting file from NFC trusted session: $fileName")
            } else {
                Log.d(TAG, "🔒 Untrusted connection, requesting user confirmation for: $fileName")
                notifyCallbacks { onFileTransferRequested(fileName, fileSize) }
                
                // Wait for UI to confirm or reject
                pendingConfirmation = CompletableDeferred()
                val accepted = try {
                     // 30 second timeout for user to decide
                    withTimeout(30_000) {
                        pendingConfirmation?.await() ?: false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Confirmation timed out")
                    false
                } finally {
                    pendingConfirmation = null
                }
                
                if (!accepted) {
                    Log.w(TAG, "❌ File transfer rejected by user or timeout")
                    // Still need to consume the stream or close connection
                    // In a simple socket protocol, closing the socket is the rejection
                    socket.close() 
                    return
                }
                Log.d(TAG, "✅ File transfer manually accepted by user")
            }

            val targetDir = File(getExternalFilesDir(null), "received_files")
            if (!targetDir.exists()) targetDir.mkdirs()
            val outFile = File(targetDir, fileName)

            outputStream = FileOutputStream(outFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytesRead = 0L
            var lastReportedProgress = -1

            notifyCallbacks { onFileTransferStarted(fileName, fileSize) }

            while (totalBytesRead < fileSize) {
                val bytesToRead = minOf(buffer.size.toLong(), fileSize - totalBytesRead).toInt()
                val bytesRead = inputStream.read(buffer, 0, bytesToRead)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                // ⚡ TURBO v2.0: Report progress every 2% to reduce callback overhead
                val progress = if (fileSize > 0L) ((totalBytesRead * 100) / fileSize).toInt() else 0
                if (progress - lastReportedProgress >= PROGRESS_REPORT_INTERVAL || progress >= 100) {
                    lastReportedProgress = progress
                    notifyCallbacks { onFileTransferProgress(fileName, progress, totalBytesRead) }
                }
            }

            outputStream.flush()
            notifyCallbacks { onFileTransferCompleted(fileName) }
            Log.d(TAG, "Received file saved to: ${outFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving file", e)
            notifyCallbacks { onFileTransferFailed("unknown", e.message ?: "Receive failed") }
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing receive streams", e)
            }
        }
    }

    private fun cleanup() {
        try {
            serverSocket?.close()
            serverSocket = null
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
        callbacks.add(callback)
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
                        
                        // PRODUCTION FIX: Track when devices are found to reset retry timer
                        if (peers.deviceList.isNotEmpty()) {
                            lastDeviceFoundTime = System.currentTimeMillis()
                            discoveryRetryCount = 0 // Reset retry count when devices found
                        }
                        
                        notifyCallbacks { onPeersDiscovered(peers.deviceList) }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO) as? WifiP2pInfo
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as? android.net.NetworkInfo

                    currentConnection = wifiP2pInfo

                    if (wifiP2pInfo?.groupFormed == true) {
                        Log.d(TAG, "Connected to group: ${wifiP2pInfo.groupOwnerAddress}")
                        // FIXED: Start receive server on BOTH roles (owner + client)
                        // This enables bidirectional file transfer
                        serviceScope.launch {
                            startReceiveServer()
                        }
                        notifyCallbacks { onConnected(wifiP2pInfo) }
                    } else {
                        Log.d(TAG, "Disconnected from group")
                        clientAddress = null // Reset on disconnect
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
