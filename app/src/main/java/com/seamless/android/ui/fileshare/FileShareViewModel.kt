@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.seamless.android.service.WifiDirectService
import com.seamless.android.service.WifiDirectCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * ViewModel for managing file sharing functionality
 */
class FileShareViewModel(application: Application) : AndroidViewModel(application), WifiDirectCallback {

    companion object {
        private const val TAG = "FileShareViewModel"
        // Discovery timeout and retry settings
        private const val DISCOVERY_REFRESH_INTERVAL_MS = 10_000L // Rediscover every 10 seconds
        private const val DISCOVERY_TIMEOUT_MS = 30_000L // Show timeout message after 30 seconds
    }
    
    // Discovery job for automatic refresh
    private var discoveryJob: Job? = null
    
    // Wi-Fi Aware for instant discovery (like AirDrop)
    private var wifiAwareService: com.seamless.android.service.WifiAwareService? = null
    private var isWifiAwareAvailable = false

    // Dual-Mode peer maps so Wi-Fi Direct and Wi-Fi Aware discoveries merge cleanly without overwriting
    private val p2pPeersMap = java.util.concurrent.ConcurrentHashMap<String, WifiP2pDevice>()
    private val awarePeersMap = java.util.concurrent.ConcurrentHashMap<String, String>() // peerId -> peerName

    private fun rebuildDiscoveredDevices() {
        val merged = mutableListOf<WifiP2pDevice>()
        val matchedAwareIds = mutableSetOf<String>()

        // 1. Add all real Wi-Fi Direct peers (with valid MAC addresses)
        for (p2p in p2pPeersMap.values) {
            val cleanP2pName = p2p.deviceName.orEmpty().removePrefix("⚡ ").trim()
            val matchingAwareEntry = awarePeersMap.entries.firstOrNull { (_, awareName) ->
                val cleanAware = awareName.removePrefix("⚡ ").trim()
                cleanP2pName.isNotEmpty() && cleanAware.isNotEmpty() && (
                    cleanP2pName.equals(cleanAware, ignoreCase = true) ||
                        cleanP2pName.contains(cleanAware, ignoreCase = true) ||
                        cleanAware.contains(cleanP2pName, ignoreCase = true)
                    )
            }
            val deviceCopy = WifiP2pDevice(p2p)
            if (matchingAwareEntry != null) {
                matchedAwareIds.add(matchingAwareEntry.key)
                if (!deviceCopy.deviceName.startsWith("⚡")) {
                    deviceCopy.deviceName = "⚡ ${deviceCopy.deviceName}"
                }
            }
            merged.add(deviceCopy)
        }

        // 2. Add any Wi-Fi Aware peers not yet matched to a Wi-Fi Direct MAC
        for ((peerId, peerName) in awarePeersMap.entries) {
            if (peerId in matchedAwareIds) continue
            val synthetic = WifiP2pDevice().apply {
                deviceAddress = peerId
                deviceName = if (peerName.startsWith("⚡")) peerName else "⚡ $peerName"
                status = WifiP2pDevice.AVAILABLE
            }
            merged.add(synthetic)
        }

        _discoveredDevices.postValue(merged)
    }

    private val _isWifiDirectEnabled = MutableLiveData(false)
    val isWifiDirectEnabled: LiveData<Boolean> = _isWifiDirectEnabled

    private val _isDiscovering = MutableLiveData(false)
    val isDiscovering: LiveData<Boolean> = _isDiscovering

    private val _discoveredDevices = MutableLiveData<List<WifiP2pDevice>>(emptyList())
    val discoveredDevices: LiveData<List<WifiP2pDevice>> = _discoveredDevices

    private val _connectionInfo = MutableLiveData<WifiP2pInfo?>()
    val connectionInfo: LiveData<WifiP2pInfo?> = _connectionInfo

    private val _currentTransfer = MutableLiveData<TransferProgress?>()
    val currentTransfer: LiveData<TransferProgress?> = _currentTransfer

    private val _transferHistory = MutableLiveData<List<FileTransfer>>(emptyList())
    val transferHistory: LiveData<List<FileTransfer>> = _transferHistory

    private val _statusMessage = MutableLiveData<String?>(null)
    val statusMessage: LiveData<String?> = _statusMessage

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // Transfer screen state for navigation
    private val _transferScreenState = MutableLiveData(TransferScreenState.NONE)
    val transferScreenState: LiveData<TransferScreenState> = _transferScreenState
    
    // Last transfer result for showing complete/failed state
    private val _lastTransferResult = MutableLiveData<TransferResult?>()
    val lastTransferResult: LiveData<TransferResult?> = _lastTransferResult

    // Incoming file request (for manual confirmations)
    data class IncomingRequest(val fileName: String, val fileSize: Long)
    private val _incomingRequest = MutableLiveData<IncomingRequest?>()
    val incomingRequest: LiveData<IncomingRequest?> = _incomingRequest
    
    // ==================== BATCH FILE TRANSFER SUPPORT ====================
    
    // Single file info for batch
    data class BatchFileInfo(
        val fileName: String,
        val fileSize: Long,
        var progress: Int = 0,
        var bytesTransferred: Long = 0,
        var status: BatchFileStatus = BatchFileStatus.PENDING
    )
    
    enum class BatchFileStatus {
        PENDING,      // Waiting to transfer
        TRANSFERRING, // Currently transferring
        COMPLETED,    // Transfer finished successfully
        FAILED        // Transfer failed
    }
    
    // Incoming batch request (for unified accept/reject)
    data class IncomingBatchRequest(
        val files: List<BatchFileInfo>,
        val senderName: String = "Unknown Device"
    ) {
        val totalSize: Long get() = files.sumOf { it.fileSize }
        val totalFiles: Int get() = files.size
    }
    
    private val _incomingBatchRequest = MutableLiveData<IncomingBatchRequest?>()
    val incomingBatchRequest: LiveData<IncomingBatchRequest?> = _incomingBatchRequest
    
    // Current batch transfer state
    private val _batchTransferFiles = MutableLiveData<List<BatchFileInfo>>(emptyList())
    val batchTransferFiles: LiveData<List<BatchFileInfo>> = _batchTransferFiles
    
    private val _currentBatchFileIndex = MutableLiveData(0)
    val currentBatchFileIndex: LiveData<Int> = _currentBatchFileIndex
    
    private val _isBatchTransferComplete = MutableLiveData(false)
    val isBatchTransferComplete: LiveData<Boolean> = _isBatchTransferComplete
    
    // Batch progress (0f to 1f for UFO animation)
    val batchProgress: Float
        get() {
            val files = _batchTransferFiles.value ?: return 0f
            if (files.isEmpty()) return 0f
            val completedCount = files.count { it.status == BatchFileStatus.COMPLETED }
            val currentFileProgress = files.getOrNull(_currentBatchFileIndex.value ?: 0)
                ?.let { if (it.status == BatchFileStatus.TRANSFERRING) it.progress / 100f else 0f } ?: 0f
            return (completedCount + currentFileProgress) / files.size
        }

    // Speed calculation tracking (inspired by AirDrop/Quick Share UX)
    private var lastBytesTransferred: Long = 0
    private var lastProgressTimestamp: Long = 0
    private var smoothedSpeedBps: Long = 0  // Smoothed for stable display

    private var wifiDirectService: WifiDirectService? = null
    private var isServiceBound: Boolean = false
    
    // ==================== PRODUCTION IMPROVEMENTS ====================
    
    // Connection timeout handling
    private var connectionTimeoutJob: Job? = null
    private val CONNECTION_TIMEOUT_MS = 20_000L // 20 seconds
    private var lastConnectionAttemptDevice: String? = null
    
    // Retry capability for failed transfers
    data class FailedTransfer(
        val filePath: String,
        val fileName: String,
        val fileSize: Long,
        val deviceAddress: String,
        val failureReason: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val _canRetry = MutableLiveData(false)
    val canRetry: LiveData<Boolean> = _canRetry
    private var lastFailedTransfer: FailedTransfer? = null
    
    // Multi-file queue support
    data class QueuedFile(
        val uri: Uri,
        val filePath: String,
        val fileName: String,
        val fileSize: Long
    )
    private val pendingFilesQueue = mutableListOf<QueuedFile>()
    private val _pendingFilesCount = MutableLiveData(0)
    val pendingFilesCount: LiveData<Int> = _pendingFilesCount
    private val _isProcessingQueue = MutableLiveData(false)
    val isProcessingQueue: LiveData<Boolean> = _isProcessingQueue
    private var batchTotalFiles: Int = 1
    private var batchCurrentIndex: Int = 1
    
    // Transfer resume support - save state to SharedPreferences
    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("hyperdrop_state", Context.MODE_PRIVATE)
    }
    
    // ==================== END PRODUCTION IMPROVEMENTS ====================


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? WifiDirectService.LocalBinder)?.getService()
            wifiDirectService = service
            service?.addCallback(this@FileShareViewModel)
            isServiceBound = true
            Log.d(TAG, "Bound to WifiDirectService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            wifiDirectService?.removeCallback(this@FileShareViewModel)
            wifiDirectService = null
            isServiceBound = false
            Log.d(TAG, "WifiDirectService disconnected")
        }
    }

    init {
        // Start Wi-Fi Direct service
        startWifiDirectService()
    }

    private fun startWifiDirectService() {
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // Bind to the service so we can receive WifiDirectCallback updates
        try {
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isServiceBound = bound
            Log.d(TAG, "Wi-Fi Direct service started, bindService result=$bound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to WifiDirectService", e)
        }
    }

    fun startDeviceDiscovery() {
        // Cancel any existing discovery job
        discoveryJob?.cancel()
        
        // Clear stale devices first
        p2pPeersMap.clear()
        awarePeersMap.clear()
        _discoveredDevices.postValue(emptyList())
        _isDiscovering.postValue(true)
        
        val context = getApplication<Application>()
        
        // Start discovery with automatic refresh
        discoveryJob = viewModelScope.launch {
            var totalElapsed = 0L
            var hasShownTimeoutMessage = false
            
            while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                // Start actual discovery
                startDiscoveryInternal(context)
                
                // Wait for refresh interval
                delay(DISCOVERY_REFRESH_INTERVAL_MS)
                totalElapsed += DISCOVERY_REFRESH_INTERVAL_MS
                
                // Show timeout message if no devices found after timeout
                if (totalElapsed >= DISCOVERY_TIMEOUT_MS && 
                    _discoveredDevices.value.isNullOrEmpty() && 
                    !hasShownTimeoutMessage) {
                    Log.w(TAG, "⏱️ Discovery timeout - no devices found after ${totalElapsed/1000}s")
                    _statusMessage.postValue("No devices found yet. Make sure nearby devices are in receive mode.")
                    hasShownTimeoutMessage = true
                }
            }
        }
    }
    
    private fun startDiscoveryInternal(context: Context) {
        // STRATEGY: Simultaneous Discovery (Dual-Mode)
        // We run Wi-Fi Direct AND Wi-Fi Aware (if supported) in parallel.
        
        // 1. Always start Wi-Fi Direct (Base Layer)
        startWifiDirectDiscovery()

        // 2. Also start Wi-Fi Aware if supported (Enhancement Layer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
            com.seamless.android.service.WifiAwareService.isSupported(context)) {
            
            Log.i(TAG, "🚀 Starting Wi-Fi Aware discovery (Dual-Mode)")
            
            if (wifiAwareService == null) {
                wifiAwareService = com.seamless.android.service.WifiAwareService(context)
                wifiAwareService?.setCallback(object : com.seamless.android.service.WifiAwareService.Callback {
                    override fun onDeviceDiscovered(peerId: String, peerName: String, peerHandle: Any) {
                        Log.i(TAG, "🎯 Wi-Fi Aware: Found device: $peerName ($peerId)")
                        awarePeersMap[peerId] = peerName
                        rebuildDiscoveredDevices()
                    }
                    
                    override fun onDeviceLost(peerId: String) {
                        awarePeersMap.remove(peerId)
                        rebuildDiscoveredDevices()
                    }
                    
                    override fun onSessionReady() {
                        Log.i(TAG, "✅ Wi-Fi Aware session ready - starting subscribe")
                        isWifiAwareAvailable = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            wifiAwareService?.startSubscribing()
                        }
                    }
                    
                    override fun onError(message: String) {
                        Log.e(TAG, "Wi-Fi Aware error: $message")
                    }
                    
                    override fun onAwareUnavailable() {
                        Log.w(TAG, "Wi-Fi Aware unavailable")
                        isWifiAwareAvailable = false
                    }
                })
            }
            
            val deviceName = "${com.seamless.android.service.HyperDropIdentity.getAlias(context)} (${com.seamless.android.service.HyperDropIdentity.getDeviceModel()})"
            wifiAwareService?.setDeviceName(deviceName)
            wifiAwareService?.start()
        }
    }
    
    private fun startWifiDirectDiscovery() {
        Log.i(TAG, "🔍 Starting Wi-Fi Direct discovery")
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_START_DISCOVERY
        }
        context.startService(intent)
        _isDiscovering.postValue(true)
    }

    fun startHosting() {
        val context = getApplication<Application>()
        
        // Also advertise via Wi-Fi Aware for instant discovery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
            com.seamless.android.service.WifiAwareService.isSupported(context)) {
            
            Log.i(TAG, "📡 Starting Wi-Fi Aware publishing for hosting")
            
            if (wifiAwareService == null) {
                wifiAwareService = com.seamless.android.service.WifiAwareService(context)
                wifiAwareService?.setCallback(object : com.seamless.android.service.WifiAwareService.Callback {
                    override fun onDeviceDiscovered(peerId: String, peerName: String, peerHandle: Any) {
                        Log.d(TAG, "Received connection from: $peerName")
                    }
                    override fun onDeviceLost(peerId: String) {}
                    override fun onSessionReady() {
                        Log.i(TAG, "✅ Wi-Fi Aware ready - starting publish")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            wifiAwareService?.startPublishing()
                        }
                    }
                    override fun onError(message: String) {
                        Log.e(TAG, "Aware error: $message")
                    }
                    override fun onAwareUnavailable() {
                        Log.w(TAG, "Aware unavailable for hosting, using Wi-Fi Direct only")
                    }
                })
            }
            
            // Set device name for discovery
            val deviceName = "${com.seamless.android.service.HyperDropIdentity.getAlias(context)} (${com.seamless.android.service.HyperDropIdentity.getDeviceModel()})"
            wifiAwareService?.setDeviceName(deviceName)
            wifiAwareService?.start()
        }
        
        // Also start Wi-Fi Direct hosting
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_CREATE_GROUP
        }
        context.startService(intent)
        _statusMessage.value = "Creating group for receiving..."
        _transferScreenState.value = TransferScreenState.WAITING
    }

    fun stopDeviceDiscovery() {
        // Cancel discovery job
        discoveryJob?.cancel()
        discoveryJob = null
        
        // Stop Wi-Fi Aware
        wifiAwareService?.stop()
        
        // Stop Wi-Fi Direct discovery
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_STOP_DISCOVERY
        }
        context.startService(intent)
        _isDiscovering.value = false
    }

    private fun resolveP2pMacAddress(deviceAddressOrAwareId: String): String? {
        if (deviceAddressOrAwareId.contains(":")) {
            return deviceAddressOrAwareId
        }
        val awareName = awarePeersMap[deviceAddressOrAwareId]?.removePrefix("⚡ ")?.trim().orEmpty()
        if (awareName.isNotEmpty()) {
            val matchedByName = p2pPeersMap.values.firstOrNull { p2p ->
                val p2pName = p2p.deviceName.orEmpty().removePrefix("⚡ ").trim()
                p2pName.equals(awareName, ignoreCase = true) ||
                    p2pName.contains(awareName, ignoreCase = true) ||
                    awareName.contains(p2pName, ignoreCase = true)
            }
            if (matchedByName != null) return matchedByName.deviceAddress
        }
        // Fallback: if there is exactly 1 available Wi-Fi Direct peer in range, pair with it
        if (p2pPeersMap.size == 1) {
            return p2pPeersMap.values.first().deviceAddress
        }
        return null
    }

    fun connectToDevice(deviceAddress: String) {
        Log.d(TAG, "📱 Attempting to connect to device: $deviceAddress")
        
        // Ensure service is bound before connecting
        if (!isServiceBound || wifiDirectService == null) {
            Log.w(TAG, "⚠️ WifiDirectService not bound, retrying bind before connect...")
            val context = getApplication<Application>()
            val intent = Intent(context, WifiDirectService::class.java)
            try {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    connectToDevice(deviceAddress)
                }, 300)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind to WifiDirectService", e)
            }
        }

        // If already connected to a formed group, send pending files directly
        if (_connectionInfo.value?.groupFormed == true) {
            if (_pendingFile.value != null) {
                sendPendingFile()
                return
            } else if (pendingFilesQueue.isNotEmpty()) {
                processNextInQueue()
                return
            }
        }

        // Resolve synthetic Wi-Fi Aware ID to a real Wi-Fi Direct MAC address
        if (!deviceAddress.contains(":")) {
            val immediateMac = resolveP2pMacAddress(deviceAddress)
            if (immediateMac != null) {
                performConnect(immediateMac)
                return
            }
            val awareName = awarePeersMap[deviceAddress] ?: "device"
            _statusMessage.postValue("Negotiating high-speed Wi-Fi Direct link with $awareName...")
            startWifiDirectDiscovery()
            viewModelScope.launch {
                var resolvedMac: String? = null
                repeat(15) {
                    delay(300L)
                    resolvedMac = resolveP2pMacAddress(deviceAddress)
                    if (resolvedMac != null) return@repeat
                }
                val finalMac = resolvedMac
                if (finalMac != null) {
                    performConnect(finalMac)
                } else {
                    _errorMessage.postValue("Waiting for $awareName's Wi-Fi Direct channel — tap again in a moment.")
                }
            }
            return
        }
        
        performConnect(deviceAddress)
    }
    
    private fun performConnect(deviceAddress: String) {
        // Cancel any existing timeout job
        connectionTimeoutJob?.cancel()
        lastConnectionAttemptDevice = deviceAddress
        _errorMessage.postValue(null)
        _statusMessage.postValue("Connecting to device...")
        
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_CONNECT
            putExtra(WifiDirectService.EXTRA_DEVICE_ADDRESS, deviceAddress)
        }
        Log.d(TAG, "📤 Sending CONNECT intent for $deviceAddress, serviceBound=$isServiceBound")
        context.startService(intent)
        
        // Start connection timeout
        connectionTimeoutJob = viewModelScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_connectionInfo.value?.groupFormed != true) {
                Log.w(TAG, "⏰ Connection timeout after ${CONNECTION_TIMEOUT_MS/1000}s to $deviceAddress")
                _errorMessage.postValue("Connection timed out. Tap to retry.")
                _statusMessage.postValue("Connection failed - device may be out of range")
            }
        }
    }

    fun cancelActiveTransfer() {
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_CANCEL_TRANSFER
        }
        context.startService(intent)
        clearQueue()
    }

    fun disconnect() {
        connectionTimeoutJob?.cancel()
        clearQueue()
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
    
    // ==================== RETRY FUNCTIONS ====================
    
    /**
     * Retry the last failed transfer
     */
    fun retryLastTransfer() {
        val failed = lastFailedTransfer ?: return
        Log.d(TAG, "🔄 Retrying transfer: ${failed.fileName}")
        
        _canRetry.value = false
        _errorMessage.value = null
        
        // First reconnect, then send
        connectToDevice(failed.deviceAddress)
        
        // Wait for connection, then retry file send
        viewModelScope.launch {
            var attempts = 0
            while (_connectionInfo.value?.groupFormed != true && attempts < 10) {
                delay(1000)
                attempts++
            }
            
            if (_connectionInfo.value?.groupFormed == true) {
                sendFile(failed.filePath, failed.fileName, failed.fileSize)
            } else {
                _errorMessage.postValue("Could not reconnect. Please try again.")
                _canRetry.postValue(true)
            }
        }
    }
    
    /**
     * Clear retry state
     */
    fun clearRetry() {
        lastFailedTransfer = null
        _canRetry.value = false
    }
    
    // ==================== MULTI-FILE QUEUE FUNCTIONS ====================
    
    /**
     * Add multiple files to the send queue
     */
    fun addFilesToQueue(uris: List<Uri>) {
        val context = getApplication<Application>()
        
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = mutableListOf<QueuedFile>()
            for (uri in uris) {
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    var fileName = "file_${System.currentTimeMillis()}"
                    var fileSize = 0L
                    
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (nameIdx >= 0) fileName = it.getString(nameIdx) ?: fileName
                            if (sizeIdx >= 0) fileSize = it.getLong(sizeIdx)
                        }
                    }
                    
                    // Check size limit (HyperDrop 10GB)
                    if (fileSize > com.seamless.android.util.AppConstants.HyperDrop.MAX_FILE_SIZE_BYTES) {
                        Log.e(TAG, "❌ File too large: $fileName ($fileSize bytes)")
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "File too large: $fileName (Max 10GB)"
                        }
                        continue
                    }
                    
                    // ZERO-COPY: Use URI directly for robust large file support
                    resolved.add(QueuedFile(uri, uri.toString(), fileName, fileSize))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add file to queue: ${e.message}")
                }
            }
            
            withContext(Dispatchers.Main) {
                if (_isProcessingQueue.value != true) {
                    batchTotalFiles = (pendingFilesQueue.size + resolved.size).coerceAtLeast(1)
                    batchCurrentIndex = 0
                } else {
                    batchTotalFiles += resolved.size
                }
                pendingFilesQueue.addAll(resolved)
                _pendingFilesCount.value = pendingFilesQueue.size
                Log.d(TAG, "📋 Added ${resolved.size} file(s) to queue (queue size: ${pendingFilesQueue.size})")
                // If already connected to a peer and not currently transferring, start sending immediately
                if (_connectionInfo.value?.groupFormed == true && _isProcessingQueue.value != true && pendingFilesQueue.isNotEmpty()) {
                    processNextInQueue()
                }
            }
        }
    }
    
    /**
     * Process the next file in the queue
     */
    fun processNextInQueue() {
        if (pendingFilesQueue.isEmpty()) {
            _isProcessingQueue.value = false
            batchTotalFiles = 1
            batchCurrentIndex = 1
            _statusMessage.value = "All files sent!"
            return
        }
        
        val next = pendingFilesQueue.removeAt(0)
        batchCurrentIndex = (batchCurrentIndex + 1).coerceAtMost(batchTotalFiles.coerceAtLeast(1))
        _pendingFilesCount.value = pendingFilesQueue.size
        _isProcessingQueue.value = true
        
        Log.d(TAG, "📤 Processing queue (${batchCurrentIndex}/${batchTotalFiles}): ${next.fileName} (${pendingFilesQueue.size} remaining)")
        sendFile(next.filePath, next.fileName, next.fileSize, batchCurrentIndex, batchTotalFiles.coerceAtLeast(1))
    }
    
    /**
     * Clear the file queue
     */
    fun clearQueue() {
        pendingFilesQueue.clear()
        _pendingFilesCount.value = 0
        _isProcessingQueue.value = false
        batchTotalFiles = 1
        batchCurrentIndex = 1
    }
    
    // ==================== END QUEUE FUNCTIONS ====================

    fun sendFile(filePath: String, fileName: String, fileSize: Long, fileIndex: Int = 1, totalFiles: Int = 1) {
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_SEND_FILE
            putExtra(WifiDirectService.EXTRA_FILE_PATH, filePath)
            putExtra(WifiDirectService.EXTRA_FILE_NAME, fileName)
            putExtra(WifiDirectService.EXTRA_FILE_SIZE, fileSize)
        }
        context.startService(intent)

        // Add to transfer history
        val transfer = FileTransfer(
            id = System.currentTimeMillis().toString(),
            fileName = fileName,
            fileSize = fileSize,
            direction = TransferDirection.SENDING,
            status = TransferStatus.IN_PROGRESS,
            timestamp = System.currentTimeMillis(),
            filePath = filePath  // For retry capability
        )
        addTransferToHistory(transfer)
        _lastTransferResult.value = null
        _currentTransfer.value = TransferProgress(
            id = transfer.id,
            fileName = fileName,
            progress = 0,
            bytesTransferred = 0L,
            totalBytes = fileSize,
            fileIndex = fileIndex,
            totalFiles = totalFiles
        )
        // 🚀 Go directly to SENDING to use real-time UFO animation
        _transferScreenState.value = TransferScreenState.SENDING
    }

    fun acceptIncomingTransfer() {
        _incomingRequest.value = null // Clear request
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_ACCEPT_TRANSFER  // FIX: Use constant, not literal string
        }
        context.startService(intent)
    }

    fun rejectIncomingTransfer() {
        _incomingRequest.value = null // Clear request
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_REJECT_TRANSFER  // FIX: Use constant, not literal string
        }
        context.startService(intent)
    }
    
    // ==================== BATCH ACCEPT/REJECT FUNCTIONS ====================
    
    /**
     * Accept entire batch of incoming files with single action
     */
    fun acceptBatchTransfer() {
        val batch = _incomingBatchRequest.value ?: return
        Log.d(TAG, "📥 Accepting batch transfer: ${batch.totalFiles} files, ${batch.totalSize} bytes")
        
        // Initialize batch transfer state
        _batchTransferFiles.value = batch.files.toList()
        _currentBatchFileIndex.value = 0
        _isBatchTransferComplete.value = false
        
        // Clear the request
        _incomingBatchRequest.value = null
        _incomingRequest.value = null
        
        // Accept via service
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_ACCEPT_TRANSFER
        }
        context.startService(intent)
        
        // Transition to receiving state
        _transferScreenState.value = TransferScreenState.RECEIVING
    }
    
    /**
     * Reject entire batch of incoming files with single action
     */
    fun rejectBatchTransfer() {
        Log.d(TAG, "❌ Rejecting batch transfer")
        
        // Clear all batch state
        _incomingBatchRequest.value = null
        _incomingRequest.value = null
        _batchTransferFiles.value = emptyList()
        _currentBatchFileIndex.value = 0
        
        // Reject via service
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_REJECT_TRANSFER
        }
        context.startService(intent)
    }
    
    /**
     * Add file to incoming batch request (called by service for each file in batch)
     */
    fun addToBatchRequest(fileName: String, fileSize: Long, senderName: String = "Unknown Device") {
        val currentBatch = _incomingBatchRequest.value
        val newFile = BatchFileInfo(fileName, fileSize)
        
        if (currentBatch != null) {
            // Add to existing batch
            val updatedFiles = currentBatch.files + newFile
            _incomingBatchRequest.value = currentBatch.copy(files = updatedFiles, senderName = senderName)
        } else {
            // Create new batch
            _incomingBatchRequest.value = IncomingBatchRequest(listOf(newFile), senderName)
        }
        
        Log.d(TAG, "📋 Added to batch request: $fileName (batch size: ${_incomingBatchRequest.value?.files?.size})")
    }
    
    /**
     * Update batch file progress during transfer
     */
    fun updateBatchFileProgress(fileName: String, progress: Int, bytesTransferred: Long) {
        val files = _batchTransferFiles.value?.toMutableList() ?: return
        val index = files.indexOfFirst { it.fileName == fileName && it.status != BatchFileStatus.COMPLETED }
        
        if (index >= 0) {
            files[index] = files[index].copy(
                progress = progress,
                bytesTransferred = bytesTransferred,
                status = BatchFileStatus.TRANSFERRING
            )
            _batchTransferFiles.value = files
            _currentBatchFileIndex.value = index
        }
    }
    
    /**
     * Mark a batch file as completed
     */
    fun markBatchFileCompleted(fileName: String) {
        val files = _batchTransferFiles.value?.toMutableList() ?: return
        val index = files.indexOfFirst { it.fileName == fileName && it.status == BatchFileStatus.TRANSFERRING }
        
        if (index >= 0) {
            files[index] = files[index].copy(
                progress = 100,
                status = BatchFileStatus.COMPLETED
            )
            _batchTransferFiles.value = files
            
            // Check if all files completed
            val allCompleted = files.all { it.status == BatchFileStatus.COMPLETED }
            if (allCompleted) {
                Log.d(TAG, "🎉 Batch transfer complete! All ${files.size} files received.")
                _isBatchTransferComplete.value = true
            }
        }
    }
    
    /**
     * Mark a batch file as failed
     */
    fun markBatchFileFailed(fileName: String) {
        val files = _batchTransferFiles.value?.toMutableList() ?: return
        val index = files.indexOfFirst { it.fileName == fileName && it.status == BatchFileStatus.TRANSFERRING }
        
        if (index >= 0) {
            files[index] = files[index].copy(status = BatchFileStatus.FAILED)
            _batchTransferFiles.value = files
        }
    }
    
    /**
     * Reset batch transfer state
     */
    fun resetBatchTransfer() {
        _batchTransferFiles.value = emptyList()
        _currentBatchFileIndex.value = 0
        _isBatchTransferComplete.value = false
        _incomingBatchRequest.value = null
    }

    /**
     * Called when the "UFO Abduction/Arrival" animation completes.
     * Transitions to the actual progress screen.
     */
    fun onAnimationCompleted() {
        val currentState = _transferScreenState.value
        if (currentState == TransferScreenState.ANIMATING_SEND) {
            _transferScreenState.value = TransferScreenState.SENDING
        } else if (currentState == TransferScreenState.ANIMATING_RECEIVE) {
            _transferScreenState.value = TransferScreenState.RECEIVING
        }
    }

    /**
     * Convenience helper to send a file chosen from the system picker.
     * Resolves the content Uri into a temporary file we control, then
     * delegates to the existing sendFile() pipeline so progress/history
     * handling stays in one place.
     */
    private val _pendingFile = MutableLiveData<PendingFile?>()
    val pendingFile: LiveData<PendingFile?> = _pendingFile

    /**
     * Prepares a file for sending by copying it to a cache file and storing its metadata.
     * Does NOT send immediately. Call sendPendingFile() after connection.
     */
    fun prepareFileForSending(uri: Uri) {
        val appContext = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = appContext.contentResolver

                var displayName = "shared_file"
                var reportedSize: Long = 0L

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex) ?: displayName
                        }
                        if (sizeIndex != -1) {
                            reportedSize = cursor.getLong(sizeIndex)
                        }
                    }
                }

                // ZERO-COPY: Use URI directly for huge files (3GB+)
                // This prevents "out of space" errors and crash-inducing IO delays
                val effectiveSize = if (reportedSize > 0L) reportedSize else 0L

                withContext(Dispatchers.Main) {
                    _pendingFile.value = PendingFile(
                        filePath = uri.toString(), 
                        fileName = displayName,
                        fileSize = effectiveSize
                    )
                    Log.d(TAG, "File prepared for sending (zero-copy): $displayName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare file from URI: $uri", e)
            }
        }
    }

    fun sendPendingFile() {
        val pending = _pendingFile.value ?: return
        sendFile(pending.filePath, pending.fileName, pending.fileSize)
        _pendingFile.value = null // Clear after sending
    }

    private fun addTransferToHistory(transfer: FileTransfer) {
        val currentHistory = _transferHistory.value ?: emptyList()
        _transferHistory.value = currentHistory + transfer
    }

    // Wi-Fi Direct callback implementations
    override fun onWifiDirectStateChanged(enabled: Boolean) {
        _isWifiDirectEnabled.value = enabled
        Log.d(TAG, "Wi-Fi Direct enabled: $enabled")
    }

    override fun onDiscoveryFailed(reason: Int) {
        _isDiscovering.value = false
        _errorMessage.value = when (reason) {
            -1 -> "Discovery failed: missing Nearby devices/location permission. Check app permissions."
            android.net.wifi.p2p.WifiP2pManager.BUSY -> "System is busy. Please toggle Wi-Fi off and on, then try again."
            android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device."
            else -> "Device discovery failed (code $reason)."
        }
        Log.e(TAG, "Device discovery failed: $reason")
    }

    override fun onDiscoveryStarted() {
        _isDiscovering.value = true
        _statusMessage.value = "Scanning for nearby devices..."
        _errorMessage.value = null
        Log.d(TAG, "Device discovery started")
    }

    override fun onDiscoveryStopped() {
        _isDiscovering.value = false
        _statusMessage.value = "Scan complete."
        Log.d(TAG, "Device discovery stopped")
    }

    override fun onPeersDiscovered(peers: Collection<WifiP2pDevice>) {
        p2pPeersMap.clear()
        for (peer in peers) {
            if (!peer.deviceAddress.isNullOrBlank()) {
                p2pPeersMap[peer.deviceAddress] = peer
            }
        }
        rebuildDiscoveredDevices()
        Log.d(TAG, "Discovered ${peers.size} Wi-Fi Direct devices (total merged: ${_discoveredDevices.value?.size ?: 0})")
    }

    override fun onConnectionFailed(reason: Int) {
        connectionTimeoutJob?.cancel()
        val reasonText = when (reason) {
            -1 -> "Missing Nearby Wi-Fi / Location permission."
            android.net.wifi.p2p.WifiP2pManager.BUSY -> "Wi-Fi Direct is busy — please tap again."
            android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device."
            else -> "Could not connect to peer (code $reason)."
        }
        _errorMessage.value = reasonText
        _statusMessage.value = reasonText
        Log.e(TAG, "Wi-Fi Direct connection failed: $reasonText")
    }

    override fun onConnected(connectionInfo: WifiP2pInfo) {
        connectionTimeoutJob?.cancel()
        _connectionInfo.value = connectionInfo
        _statusMessage.value = "Connected via Wi‑Fi Direct (${if (connectionInfo.isGroupOwner) "Group owner" else "Client"})."
        _errorMessage.value = null
        Log.d(TAG, "Connected to group: ${connectionInfo.groupOwnerAddress} (isGroupOwner=${connectionInfo.isGroupOwner})")
        
        // Auto-send pending file or queue on BOTH Client and Group Owner roles
        val hasPendingWork = _pendingFile.value != null || pendingFilesQueue.isNotEmpty()
        if (hasPendingWork) {
            viewModelScope.launch {
                // If we became Group Owner, wait briefly for Client's IP registration ping
                if (connectionInfo.isGroupOwner) {
                    delay(650L)
                }
                if (_pendingFile.value != null) {
                    Log.d(TAG, "📤 Auto-sending pending file after connection established")
                    sendPendingFile()
                } else if (pendingFilesQueue.isNotEmpty()) {
                    Log.d(TAG, "📤 Auto-sending from queue after connection established (${pendingFilesQueue.size} files)")
                    processNextInQueue()
                }
            }
        }
    }

    override fun onDisconnected() {
        connectionTimeoutJob?.cancel()
        _connectionInfo.value = null
        _currentTransfer.value = null
        _statusMessage.value = "Disconnected from Wi‑Fi Direct group."
        Log.d(TAG, "Disconnected from group")
    }

    override fun onThisDeviceChanged(device: WifiP2pDevice) {} // Default
    
    override fun onFileTransferRequested(fileName: String, fileSize: Long) {
        Log.d(TAG, "Incoming file request: $fileName")
        _incomingRequest.value = IncomingRequest(fileName, fileSize)
        _statusMessage.value = "Incoming file request: $fileName"
        
        // Also add to batch request for multi-file support
        addToBatchRequest(fileName, fileSize)
    }

    override fun onFileTransferStarted(fileName: String, fileSize: Long) {
        Log.d(TAG, "File transfer started: $fileName ($fileSize bytes)")
        
        // Reset speed tracking for new transfer
        lastBytesTransferred = 0
        lastProgressTimestamp = System.currentTimeMillis()
        smoothedSpeedBps = 0
        
        // Check if this transfer already exists (outgoing) or needs to be added (incoming)
        val currentHistory = _transferHistory.value ?: emptyList()
        val existingTransfer = currentHistory.find {
            it.fileName == fileName &&
                it.status == TransferStatus.IN_PROGRESS &&
                it.direction == TransferDirection.SENDING
        }
        val isReceiver = existingTransfer == null

        _statusMessage.value = if (isReceiver) "Receiving $fileName..." else "Sending $fileName..."
        _errorMessage.value = null
        _lastTransferResult.value = null
        
        // Transition directly to SENDING / RECEIVING (real-time UFO progress screens)
        val targetState = if (isReceiver) TransferScreenState.RECEIVING else TransferScreenState.SENDING
        _transferScreenState.value = targetState

        if (existingTransfer != null) {
            // Update existing outgoing transfer
            val updatedHistory = currentHistory.map { transfer ->
                if (transfer.id == existingTransfer.id) {
                    transfer.copy(status = TransferStatus.IN_PROGRESS, fileSize = fileSize)
                } else {
                    transfer
                }
            }
            _transferHistory.value = updatedHistory
            val curIdx = _currentTransfer.value?.fileIndex ?: batchCurrentIndex.coerceAtLeast(1)
            val curTotal = _currentTransfer.value?.totalFiles ?: batchTotalFiles.coerceAtLeast(1)
            _currentTransfer.value = TransferProgress(
                id = existingTransfer.id,
                fileName = fileName,
                progress = 0,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                fileIndex = curIdx,
                totalFiles = curTotal
            )
        } else {
            // New incoming transfer
            val newTransfer = FileTransfer(
                id = System.currentTimeMillis().toString(),
                fileName = fileName,
                fileSize = fileSize,
                direction = TransferDirection.RECEIVING,
                status = TransferStatus.IN_PROGRESS,
                timestamp = System.currentTimeMillis()
            )
            addTransferToHistory(newTransfer)
            val batchFiles = _batchTransferFiles.value.orEmpty()
            val recvIndex = (_currentBatchFileIndex.value ?: 0) + 1
            val recvTotal = batchFiles.size.coerceAtLeast(1)
            _currentTransfer.value = TransferProgress(
                id = newTransfer.id,
                fileName = fileName,
                progress = 0,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                fileIndex = recvIndex,
                totalFiles = recvTotal
            )
        }
    }

    override fun onFileTransferProgress(fileName: String, progress: Int, bytesTransferred: Long) {
        _currentTransfer.value?.let { current ->
            if (current.fileName == fileName) {
                // Calculate transfer speed (like AirDrop/Quick Share show progress)
                val now = System.currentTimeMillis()
                val timeDelta = now - lastProgressTimestamp
                val bytesDelta = bytesTransferred - lastBytesTransferred
                
                var speedBps = smoothedSpeedBps
                var etaSeconds = -1
                
                if (timeDelta > 100 && bytesDelta > 0) { // Update speed every 100ms+
                    val instantSpeed = (bytesDelta * 1000) / timeDelta  // bytes per second
                    
                    // Exponential smoothing for stable speed display
                    speedBps = if (smoothedSpeedBps == 0L) {
                        instantSpeed
                    } else {
                        ((smoothedSpeedBps * 7) + (instantSpeed * 3)) / 10  // 70/30 smoothing
                    }
                    smoothedSpeedBps = speedBps
                    
                    // Calculate ETA
                    val remainingBytes = current.totalBytes - bytesTransferred
                    if (speedBps > 0 && remainingBytes > 0) {
                        etaSeconds = (remainingBytes / speedBps).toInt()
                    } else if (remainingBytes <= 0) {
                        etaSeconds = 0
                    }
                    
                    lastBytesTransferred = bytesTransferred
                    lastProgressTimestamp = now
                }
                
                _currentTransfer.value = current.copy(
                    progress = progress,
                    bytesTransferred = bytesTransferred,
                    speedBytesPerSec = speedBps,
                    estimatedSecondsRemaining = etaSeconds
                )
            }
        }
        
        // Also update batch file progress
        updateBatchFileProgress(fileName, progress, bytesTransferred)
    }

    override fun onFileTransferCompleted(fileName: String) {
        Log.d(TAG, "File transfer completed: $fileName")

        // Update transfer history
        val currentHistory = _transferHistory.value ?: emptyList()
        var transferDirection = TransferDirection.SENDING // Default
        var fileSize = 0L
        
        val updatedHistory = currentHistory.map { transfer ->
            if (transfer.fileName == fileName && transfer.status == TransferStatus.IN_PROGRESS) {
                transferDirection = transfer.direction
                fileSize = transfer.fileSize
                transfer.copy(status = TransferStatus.COMPLETED)
            } else {
                transfer
            }
        }
        _transferHistory.value = updatedHistory

        // Set last transfer result for UI display
        _lastTransferResult.value = TransferResult(
            fileName = fileName,
            fileSize = fileSize,
            direction = transferDirection,
            success = true,
            errorMessage = null
        )
        
        // Keep screen state so UI can show completion, don't reset to NONE
        // UI will reset when user dismisses the screen
        
        _currentTransfer.value = _currentTransfer.value?.copy(progress = 100)
        
        val action = if (transferDirection == TransferDirection.RECEIVING) "received" else "sent"
        _statusMessage.value = "$fileName $action successfully."
        
        // PRODUCTION FIX: Process next file in queue if available
        if (pendingFilesQueue.isNotEmpty() && transferDirection == TransferDirection.SENDING) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                processNextInQueue()
            }, 350) // Small delay before next transfer
        } else if (transferDirection == TransferDirection.SENDING) {
            _isProcessingQueue.value = false
        }
        
        // Also mark batch file as completed
        markBatchFileCompleted(fileName)
    }

    override fun onFileTransferFailed(fileName: String, error: String) {
        Log.e(TAG, "File transfer failed: $fileName - $error")

        // Update transfer history
        val currentHistory = _transferHistory.value ?: emptyList()
        var transferDirection = TransferDirection.SENDING
        var fileSize = 0L
        var filePath = ""
        
        val updatedHistory = currentHistory.map { transfer ->
            if (transfer.fileName == fileName && transfer.status != TransferStatus.FAILED) {
                transferDirection = transfer.direction
                fileSize = transfer.fileSize
                filePath = transfer.filePath ?: ""
                transfer.copy(status = TransferStatus.FAILED, errorMessage = error)
            } else {
                transfer
            }
        }
        _transferHistory.value = updatedHistory

        // Set last transfer result for UI display
        _lastTransferResult.value = TransferResult(
            fileName = fileName,
            fileSize = fileSize,
            direction = transferDirection,
            success = false,
            errorMessage = error
        )
        
        // PRODUCTION FIX: Enable retry for failed outgoing transfers
        if (transferDirection == TransferDirection.SENDING && filePath.isNotBlank()) {
            lastFailedTransfer = FailedTransfer(
                filePath = filePath,
                fileName = fileName,
                fileSize = fileSize,
                deviceAddress = lastConnectionAttemptDevice ?: "",
                failureReason = error
            )
            _canRetry.postValue(true)
            Log.d(TAG, "🔄 Retry enabled for failed transfer: $fileName")
        }
        
        // Keep screen state so UI can show failure, don't reset to NONE
        
        _errorMessage.value = error
    }
    
    /**
     * Reset transfer screen state (called when user dismisses progress screen)
     */
    fun dismissTransferScreen() {
        _transferScreenState.value = TransferScreenState.NONE
        _lastTransferResult.value = null
        _currentTransfer.value = null
        _errorMessage.value = null
    }
    
    // Note: onLaunchAnimationComplete and onLandingAnimationComplete were removed
    // as the Rocket animation screens have been deleted in favor of the UFO animation.

    override fun onCleared() {
        super.onCleared()
        val context = getApplication<Application>()
        try {
            if (isServiceBound) {
                wifiDirectService?.removeCallback(this)
                context.unbindService(serviceConnection)
                isServiceBound = false
                Log.d(TAG, "Unbound from WifiDirectService in onCleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding from WifiDirectService", e)
        }
        wifiDirectService = null
    }
}

/**
 * Transfer progress with speed and ETA tracking
 * Inspired by AirDrop and Quick Share transfer displays
 */
data class TransferProgress(
    val id: String,
    val fileName: String,
    val progress: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0,        // Transfer speed in bytes/second
    val estimatedSecondsRemaining: Int = -1, // ETA in seconds, -1 = unknown
    val fileIndex: Int = 1,                 // Current file index in batch (1-based)
    val totalFiles: Int = 1                 // Total files in batch
) {
    /**
     * Format speed for display (e.g., "12.5 MB/s")
     */
    fun formatSpeed(): String {
        if (speedBytesPerSec <= 0) return ""
        
        val kbps = speedBytesPerSec / 1024.0
        val mbps = kbps / 1024.0
        
        return when {
            mbps >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB/s", mbps)
            kbps >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB/s", kbps)
            else -> "$speedBytesPerSec B/s"
        }
    }
    
    /**
     * Format ETA for display (e.g., "~30s remaining" or "~2 min remaining")
     */
    fun formatEta(): String {
        if (estimatedSecondsRemaining < 0) return ""
        if (estimatedSecondsRemaining == 0) return "Almost done"
        
        return when {
            estimatedSecondsRemaining < 60 -> "~${estimatedSecondsRemaining}s remaining"
            estimatedSecondsRemaining < 3600 -> {
                val minutes = estimatedSecondsRemaining / 60
                "~${minutes} min remaining"
            }
            else -> {
                val hours = estimatedSecondsRemaining / 3600
                val minutes = (estimatedSecondsRemaining % 3600) / 60
                "~${hours}h ${minutes}m remaining"
            }
        }
    }
}

data class FileTransfer(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val timestamp: Long,
    val errorMessage: String? = null,
    val filePath: String? = null  // For retry capability
)

enum class TransferDirection {
    SENDING,
    RECEIVING
}

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class PendingFile(
    val filePath: String,
    val fileName: String,
    val fileSize: Long
)

/**
 * State for tracking which transfer screen should be shown
 */
enum class TransferScreenState {
    NONE,               // No transfer screen
    ANIMATING_SEND,     // UFO Abduction animation playing
    ANIMATING_RECEIVE,  // UFO Arrival animation playing
    SENDING,            // Show send progress screen
    RECEIVING,          // Show receive progress screen
    WAITING             // Waiting for sender to connect (receiver hosted)
}

/**
 * Result of a completed transfer for showing final state
 */
data class TransferResult(
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val success: Boolean,
    val errorMessage: String? = null
)
