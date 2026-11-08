@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.seamless.android.service.HyperDropIdentity
import com.seamless.android.service.WifiDirectCallback
import com.seamless.android.service.WifiDirectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing HyperDrop file sharing functionality
 */
class FileShareViewModel(application: Application) : AndroidViewModel(application), WifiDirectCallback {

    companion object {
        private const val TAG = "FileShareViewModel"
        private const val DISCOVERY_REFRESH_INTERVAL_MS = 10_000L // Rediscover every 10 seconds
        private const val DISCOVERY_TIMEOUT_MS = 30_000L // Show timeout message after 30 seconds
    }

    // Discovery job for automatic refresh
    private var discoveryJob: Job? = null
    private var filePreparationJob: Job? = null

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
                val awareDisplay = matchingAwareEntry.value
                deviceCopy.deviceName = if (awareDisplay.startsWith("⚡")) awareDisplay else "⚡ $awareDisplay"
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
    data class IncomingRequest(
        val fileName: String,
        val fileSize: Long,
        val senderName: String = "Nearby Device",
        val fileIndex: Int = 1,
        val totalFiles: Int = 1
    )
    private val _incomingRequest = MutableLiveData<IncomingRequest?>()
    val incomingRequest: LiveData<IncomingRequest?> = _incomingRequest

    // ==================== BATCH FILE TRANSFER SUPPORT ====================

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

    data class IncomingBatchRequest(
        val files: List<BatchFileInfo>,
        val senderName: String = "Nearby Device"
    ) {
        val totalSize: Long get() = files.sumOf { it.fileSize }
        val totalFiles: Int get() = files.size
    }

    private val _incomingBatchRequest = MutableLiveData<IncomingBatchRequest?>()
    val incomingBatchRequest: LiveData<IncomingBatchRequest?> = _incomingBatchRequest

    private val _batchTransferFiles = MutableLiveData<List<BatchFileInfo>>(emptyList())
    val batchTransferFiles: LiveData<List<BatchFileInfo>> = _batchTransferFiles

    private val _currentBatchFileIndex = MutableLiveData(0)
    val currentBatchFileIndex: LiveData<Int> = _currentBatchFileIndex

    private val _isBatchTransferComplete = MutableLiveData(false)
    val isBatchTransferComplete: LiveData<Boolean> = _isBatchTransferComplete

    val batchProgress: Float
        get() {
            val files = _batchTransferFiles.value ?: return 0f
            if (files.isEmpty()) return 0f
            val completedCount = files.count { it.status == BatchFileStatus.COMPLETED }
            val currentFileProgress = files.getOrNull(_currentBatchFileIndex.value ?: 0)
                ?.let { if (it.status == BatchFileStatus.TRANSFERRING) it.progress / 100f else 0f } ?: 0f
            return (completedCount + currentFileProgress) / files.size
        }

    // Speed calculation tracking
    private var lastBytesTransferred: Long = 0
    private var lastProgressTimestamp: Long = 0
    private var smoothedSpeedBps: Long = 0

    private var wifiDirectService: WifiDirectService? = null
    private var isServiceBound: Boolean = false

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

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("hyperdrop_state", Context.MODE_PRIVATE)
    }

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
        try {
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isServiceBound = bound
            Log.d(TAG, "Wi-Fi Direct service started, bindService result=$bound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to WifiDirectService", e)
        }
    }

    fun startDeviceDiscovery() {
        discoveryJob?.cancel()

        p2pPeersMap.clear()
        awarePeersMap.clear()
        _discoveredDevices.postValue(emptyList())
        _isDiscovering.postValue(true)

        val context = getApplication<Application>()

        discoveryJob = viewModelScope.launch {
            var totalElapsed = 0L
            var hasShownTimeoutMessage = false

            while (coroutineContext[Job]?.isActive == true) {
                startDiscoveryInternal(context)

                delay(DISCOVERY_REFRESH_INTERVAL_MS)
                totalElapsed += DISCOVERY_REFRESH_INTERVAL_MS

                if (totalElapsed >= DISCOVERY_TIMEOUT_MS &&
                    _discoveredDevices.value.isNullOrEmpty() &&
                    !hasShownTimeoutMessage
                ) {
                    Log.w(TAG, "⏱️ Discovery timeout - no devices found after ${totalElapsed / 1000}s")
                    _statusMessage.postValue("No devices found yet. Make sure nearby devices are in Receive mode.")
                    hasShownTimeoutMessage = true
                }
            }
        }
    }

    private fun startDiscoveryInternal(context: Context) {
        startWifiDirectDiscovery()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            com.seamless.android.service.WifiAwareService.isSupported(context)
        ) {
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

            val deviceName = "${HyperDropIdentity.getAlias(context)} (${HyperDropIdentity.getDeviceModel()})"
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

        // If user had visibility set to HIDDEN, automatically enable EVERYONE when they explicitly enter Receive mode
        if (HyperDropIdentity.getVisibilityMode(context) == HyperDropIdentity.VisibilityMode.HIDDEN) {
            HyperDropIdentity.setVisibilityMode(context, HyperDropIdentity.VisibilityMode.EVERYONE)
        }

        discoveryJob?.cancel()
        _errorMessage.value = null
        _statusMessage.value = "Visible to nearby devices — waiting for sender..."
        // Stay on DeviceDiscoveryScreen (Radar + This Device card + Incoming Request bottom sheet)
        // until an active transfer actually starts!
        if (_transferScreenState.value == TransferScreenState.WAITING) {
            _transferScreenState.value = TransferScreenState.NONE
        }

        discoveryJob = viewModelScope.launch {
            while (coroutineContext[Job]?.isActive == true) {
                startHostingInternal(context)
                delay(DISCOVERY_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun startHostingInternal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            com.seamless.android.service.WifiAwareService.isSupported(context)
        ) {
            if (wifiAwareService == null) {
                wifiAwareService = com.seamless.android.service.WifiAwareService(context)
                wifiAwareService?.setCallback(object : com.seamless.android.service.WifiAwareService.Callback {
                    override fun onDeviceDiscovered(peerId: String, peerName: String, peerHandle: Any) {
                        Log.d(TAG, "Received connection from: $peerName")
                        awarePeersMap[peerId] = peerName
                        rebuildDiscoveredDevices()
                    }
                    override fun onDeviceLost(peerId: String) {
                        awarePeersMap.remove(peerId)
                        rebuildDiscoveredDevices()
                    }
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

            val deviceName = "${HyperDropIdentity.getAlias(context)} (${HyperDropIdentity.getDeviceModel()})"
            wifiAwareService?.setDeviceName(deviceName)
            wifiAwareService?.start()
        }

        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_CREATE_GROUP
        }
        context.startService(intent)
        _isDiscovering.postValue(true)
    }

    fun stopDeviceDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        wifiAwareService?.stop()

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
        if (p2pPeersMap.size == 1) {
            return p2pPeersMap.values.first().deviceAddress
        }
        return null
    }

    fun connectToDevice(deviceAddress: String) {
        Log.d(TAG, "📱 Attempting to connect to device: $deviceAddress")

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

        val resolvedTargetMac = resolveP2pMacAddress(deviceAddress)
        val isPeerAlreadyConnected = resolvedTargetMac != null && (
            p2pPeersMap[resolvedTargetMac]?.status == WifiP2pDevice.CONNECTED ||
                wifiDirectService?.hasActivePeerConnection(resolvedTargetMac) == true
            )

        // Only skip P2P connection setup if we are genuinely connected to this peer
        if (_connectionInfo.value?.groupFormed == true && isPeerAlreadyConnected) {
            viewModelScope.launch {
                filePreparationJob?.join()
                if (_pendingFile.value != null) {
                    sendPendingFile()
                } else if (pendingFilesQueue.isNotEmpty()) {
                    processNextInQueue()
                }
            }
            return
        }

        // Resolve synthetic Wi-Fi Aware ID to a real Wi-Fi Direct MAC address
        if (!deviceAddress.contains(":")) {
            if (resolvedTargetMac != null) {
                performConnect(resolvedTargetMac)
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

        connectionTimeoutJob = viewModelScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_connectionInfo.value?.groupFormed != true || wifiDirectService?.hasActivePeerConnection() != true) {
                Log.w(TAG, "⏰ Connection timeout after ${CONNECTION_TIMEOUT_MS / 1000}s to $deviceAddress")
                _errorMessage.postValue("Connection timed out. Tap device to retry.")
                _statusMessage.postValue("Connection failed - ensure receiver has HyperDrop Receive open")
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

    fun retryLastTransfer() {
        val failed = lastFailedTransfer ?: return
        Log.d(TAG, "🔄 Retrying transfer: ${failed.fileName}")

        _canRetry.value = false
        _errorMessage.value = null

        if (wifiDirectService?.hasActivePeerConnection() == true) {
            sendFile(failed.filePath, failed.fileName, failed.fileSize)
            return
        }

        connectToDevice(failed.deviceAddress)

        viewModelScope.launch {
            var attempts = 0
            while (wifiDirectService?.hasActivePeerConnection() != true && attempts < 12) {
                delay(1000)
                attempts++
            }

            if (wifiDirectService?.hasActivePeerConnection() == true) {
                sendFile(failed.filePath, failed.fileName, failed.fileSize)
            } else {
                _errorMessage.postValue("Could not reconnect. Please try again.")
                _canRetry.postValue(true)
            }
        }
    }

    fun clearRetry() {
        lastFailedTransfer = null
        _canRetry.value = false
    }

    // ==================== MULTI-FILE QUEUE FUNCTIONS ====================

    private fun resolveUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var fileName = "file_${System.currentTimeMillis()}"
        var fileSize = 0L
        val resolver = context.contentResolver

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        fileName = cursor.getString(nameIdx) ?: fileName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        if (fileSize <= 0L) {
            runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize > 0L) fileSize = pfd.statSize
                }
            }
        }

        if (fileSize <= 0L) {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) fileSize = afd.length
                }
            }
        }

        // Ensure fileName has a valid extension so received videos/images are recognized by media players
        val dotIndex = fileName.lastIndexOf('.')
        val existingExt = if (dotIndex > 0) fileName.substring(dotIndex + 1) else ""
        if (existingExt.isBlank() || HyperDropUtils.mimeTypeFromExtension(existingExt) == null) {
            var resolvedExt = runCatching {
                HyperDropUtils.extensionFromMimeType(resolver.getType(uri))
            }.getOrNull()

            if (resolvedExt.isNullOrBlank()) {
                resolvedExt = runCatching {
                    val header = ByteArray(64)
                    val read = resolver.openInputStream(uri)?.use { it.read(header) } ?: 0
                    if (read >= 4) HyperDropUtils.detectExtensionAndMimeFromBytes(header, read)?.first else null
                }.getOrNull()
            }

            if (!resolvedExt.isNullOrBlank()) {
                fileName = "${fileName.trimEnd('.')}.$resolvedExt"
            }
        }

        return fileName to fileSize
    }

    fun addFilesToQueue(uris: List<Uri>) {
        val context = getApplication<Application>()

        filePreparationJob = viewModelScope.launch(Dispatchers.IO) {
            val resolved = mutableListOf<QueuedFile>()
            for (uri in uris) {
                try {
                    val (fileName, fileSize) = resolveUriMetadata(context, uri)

                    if (fileSize > com.seamless.android.util.AppConstants.HyperDrop.MAX_FILE_SIZE_BYTES) {
                        Log.e(TAG, "❌ File too large: $fileName ($fileSize bytes)")
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "File too large: $fileName (Max 10GB)"
                        }
                        continue
                    }

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

                if (wifiDirectService?.hasActivePeerConnection() == true &&
                    _isProcessingQueue.value != true &&
                    pendingFilesQueue.isNotEmpty()
                ) {
                    processNextInQueue()
                }
            }
        }
    }

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

        Log.d(TAG, "📤 Processing queue ($batchCurrentIndex/$batchTotalFiles): ${next.fileName} (${pendingFilesQueue.size} remaining)")
        sendFile(next.filePath, next.fileName, next.fileSize, batchCurrentIndex, batchTotalFiles.coerceAtLeast(1))
    }

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
        val senderName = "${HyperDropIdentity.getAlias(context)} (${HyperDropIdentity.getDeviceModel()})"
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_SEND_FILE
            putExtra(WifiDirectService.EXTRA_FILE_PATH, filePath)
            putExtra(WifiDirectService.EXTRA_FILE_NAME, fileName)
            putExtra(WifiDirectService.EXTRA_FILE_SIZE, fileSize)
            putExtra(WifiDirectService.EXTRA_FILE_INDEX, fileIndex)
            putExtra(WifiDirectService.EXTRA_TOTAL_FILES, totalFiles)
            putExtra(WifiDirectService.EXTRA_SENDER_NAME, senderName)
        }
        context.startService(intent)

        val transfer = FileTransfer(
            id = System.currentTimeMillis().toString(),
            fileName = fileName,
            fileSize = fileSize,
            direction = TransferDirection.SENDING,
            status = TransferStatus.IN_PROGRESS,
            timestamp = System.currentTimeMillis(),
            filePath = filePath
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
        _transferScreenState.value = TransferScreenState.SENDING
    }

    fun acceptIncomingTransfer() {
        _incomingRequest.value = null
        _incomingBatchRequest.value = null
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_ACCEPT_TRANSFER
        }
        context.startService(intent)
    }

    fun rejectIncomingTransfer() {
        _incomingRequest.value = null
        _incomingBatchRequest.value = null
        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_REJECT_TRANSFER
        }
        context.startService(intent)
    }

    // ==================== BATCH ACCEPT/REJECT FUNCTIONS ====================

    fun acceptBatchTransfer() {
        val batch = _incomingBatchRequest.value ?: return
        Log.d(TAG, "📥 Accepting batch transfer: ${batch.totalFiles} files, ${batch.totalSize} bytes")

        _batchTransferFiles.value = batch.files.toList()
        _currentBatchFileIndex.value = 0
        _isBatchTransferComplete.value = false

        _incomingBatchRequest.value = null
        _incomingRequest.value = null

        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_ACCEPT_TRANSFER
        }
        context.startService(intent)

        _transferScreenState.value = TransferScreenState.RECEIVING
    }

    fun rejectBatchTransfer() {
        Log.d(TAG, "❌ Rejecting batch transfer")

        _incomingBatchRequest.value = null
        _incomingRequest.value = null
        _batchTransferFiles.value = emptyList()
        _currentBatchFileIndex.value = 0

        val context = getApplication<Application>()
        val intent = Intent(context, WifiDirectService::class.java).apply {
            action = WifiDirectService.ACTION_REJECT_TRANSFER
        }
        context.startService(intent)
    }

    fun addToBatchRequest(fileName: String, fileSize: Long, senderName: String = "Nearby Device", resetExisting: Boolean = false) {
        val currentBatch = if (resetExisting) null else _incomingBatchRequest.value
        val newFile = BatchFileInfo(fileName, fileSize)

        if (currentBatch != null) {
            val updatedFiles = currentBatch.files + newFile
            _incomingBatchRequest.value = currentBatch.copy(files = updatedFiles, senderName = senderName)
        } else {
            _incomingBatchRequest.value = IncomingBatchRequest(listOf(newFile), senderName)
        }

        Log.d(TAG, "📋 Added to batch request: $fileName (batch size: ${_incomingBatchRequest.value?.files?.size})")
    }

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

    fun markBatchFileCompleted(fileName: String) {
        val files = _batchTransferFiles.value?.toMutableList() ?: return
        val index = files.indexOfFirst {
            it.status == BatchFileStatus.TRANSFERRING &&
                (it.fileName == fileName || fileName.startsWith(it.fileName + "."))
        }

        if (index >= 0) {
            files[index] = files[index].copy(
                fileName = fileName,
                progress = 100,
                status = BatchFileStatus.COMPLETED
            )
            _batchTransferFiles.value = files

            val allCompleted = files.all { it.status == BatchFileStatus.COMPLETED }
            if (allCompleted) {
                Log.d(TAG, "🎉 Batch transfer complete! All ${files.size} files received.")
                _isBatchTransferComplete.value = true
            }
        }
    }

    fun markBatchFileFailed(fileName: String) {
        val files = _batchTransferFiles.value?.toMutableList() ?: return
        val index = files.indexOfFirst { it.fileName == fileName && it.status == BatchFileStatus.TRANSFERRING }

        if (index >= 0) {
            files[index] = files[index].copy(status = BatchFileStatus.FAILED)
            _batchTransferFiles.value = files
        }
    }

    fun resetBatchTransfer() {
        _batchTransferFiles.value = emptyList()
        _currentBatchFileIndex.value = 0
        _isBatchTransferComplete.value = false
        _incomingBatchRequest.value = null
    }

    fun onAnimationCompleted() {
        val currentState = _transferScreenState.value
        if (currentState == TransferScreenState.ANIMATING_SEND) {
            _transferScreenState.value = TransferScreenState.SENDING
        } else if (currentState == TransferScreenState.ANIMATING_RECEIVE) {
            _transferScreenState.value = TransferScreenState.RECEIVING
        }
    }

    private val _pendingFile = MutableLiveData<PendingFile?>()
    val pendingFile: LiveData<PendingFile?> = _pendingFile

    fun prepareFileForSending(uri: Uri) {
        val appContext = getApplication<Application>()
        filePreparationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val (displayName, reportedSize) = resolveUriMetadata(appContext, uri)
                val effectiveSize = if (reportedSize > 0L) reportedSize else 0L

                withContext(Dispatchers.Main) {
                    _pendingFile.value = PendingFile(
                        filePath = uri.toString(),
                        fileName = displayName,
                        fileSize = effectiveSize
                    )
                    Log.d(TAG, "File prepared for sending (zero-copy): $displayName ($effectiveSize bytes)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare file from URI: $uri", e)
            }
        }
    }

    fun sendPendingFile() {
        val pending = _pendingFile.value ?: return
        sendFile(pending.filePath, pending.fileName, pending.fileSize)
        _pendingFile.value = null
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
            android.net.wifi.p2p.WifiP2pManager.BUSY -> "System is busy. Retrying scan..."
            android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device."
            else -> "Device discovery failed (code $reason)."
        }
        Log.e(TAG, "Device discovery failed: $reason")
    }

    override fun onDiscoveryStarted() {
        _isDiscovering.value = true
        _errorMessage.value = null
        Log.d(TAG, "Device discovery started")
    }

    override fun onDiscoveryStopped() {
        _isDiscovering.value = false
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

        viewModelScope.launch {
            filePreparationJob?.join()
            val hasPendingWork = _pendingFile.value != null || pendingFilesQueue.isNotEmpty()
            if (hasPendingWork && _isProcessingQueue.value != true) {
                if (connectionInfo.isGroupOwner) {
                    delay(450L)
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

    override fun onThisDeviceChanged(device: WifiP2pDevice) {}

    override fun onFileTransferRequested(
        fileName: String,
        fileSize: Long,
        fileIndex: Int,
        totalFiles: Int,
        senderName: String
    ) {
        Log.d(TAG, "Incoming file request: $fileName ($fileIndex/$totalFiles) from $senderName")
        _incomingRequest.value = IncomingRequest(
            fileName = fileName,
            fileSize = fileSize,
            senderName = senderName,
            fileIndex = fileIndex,
            totalFiles = totalFiles
        )
        _statusMessage.value = "$senderName wants to send $fileName"

        addToBatchRequest(fileName, fileSize, senderName, resetExisting = (fileIndex <= 1))
    }

    override fun onFileTransferStarted(
        fileName: String,
        fileSize: Long,
        fileIndex: Int,
        totalFiles: Int
    ) {
        Log.d(TAG, "File transfer started: $fileName ($fileSize bytes, $fileIndex/$totalFiles)")

        lastBytesTransferred = 0
        lastProgressTimestamp = System.currentTimeMillis()
        smoothedSpeedBps = 0

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
        _incomingRequest.value = null

        val targetState = if (isReceiver) TransferScreenState.RECEIVING else TransferScreenState.SENDING
        _transferScreenState.value = targetState

        if (existingTransfer != null) {
            val updatedHistory = currentHistory.map { transfer ->
                if (transfer.id == existingTransfer.id) {
                    transfer.copy(status = TransferStatus.IN_PROGRESS, fileSize = fileSize)
                } else {
                    transfer
                }
            }
            _transferHistory.value = updatedHistory
            val curIdx = if (fileIndex > 0) fileIndex else (_currentTransfer.value?.fileIndex ?: batchCurrentIndex.coerceAtLeast(1))
            val curTotal = if (totalFiles > 1) totalFiles else (_currentTransfer.value?.totalFiles ?: batchTotalFiles.coerceAtLeast(1))
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
            val newTransfer = FileTransfer(
                id = System.currentTimeMillis().toString(),
                fileName = fileName,
                fileSize = fileSize,
                direction = TransferDirection.RECEIVING,
                status = TransferStatus.IN_PROGRESS,
                timestamp = System.currentTimeMillis()
            )
            addTransferToHistory(newTransfer)
            _currentTransfer.value = TransferProgress(
                id = newTransfer.id,
                fileName = fileName,
                progress = 0,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                fileIndex = fileIndex.coerceAtLeast(1),
                totalFiles = totalFiles.coerceAtLeast(1)
            )
        }
    }

    override fun onFileTransferProgress(fileName: String, progress: Int, bytesTransferred: Long) {
        _currentTransfer.value?.let { current ->
            if (current.fileName == fileName) {
                val now = System.currentTimeMillis()
                val timeDelta = now - lastProgressTimestamp
                val bytesDelta = bytesTransferred - lastBytesTransferred

                var speedBps = smoothedSpeedBps
                var etaSeconds = -1

                if (timeDelta > 100 && bytesDelta > 0) {
                    val instantSpeed = (bytesDelta * 1000) / timeDelta

                    speedBps = if (smoothedSpeedBps == 0L) {
                        instantSpeed
                    } else {
                        ((smoothedSpeedBps * 7) + (instantSpeed * 3)) / 10
                    }
                    smoothedSpeedBps = speedBps

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

        updateBatchFileProgress(fileName, progress, bytesTransferred)
    }

    override fun onFileTransferCompleted(fileName: String, fileIndex: Int, totalFiles: Int) {
        Log.d(TAG, "File transfer completed: $fileName ($fileIndex/$totalFiles)")

        val currentHistory = _transferHistory.value ?: emptyList()
        var transferDirection = TransferDirection.SENDING
        var fileSize = 0L

        val updatedHistory = currentHistory.map { transfer ->
            if (transfer.status == TransferStatus.IN_PROGRESS &&
                (transfer.fileName == fileName || fileName.startsWith(transfer.fileName + "."))
            ) {
                transferDirection = transfer.direction
                fileSize = transfer.fileSize
                transfer.copy(fileName = fileName, status = TransferStatus.COMPLETED)
            } else {
                transfer
            }
        }
        _transferHistory.value = updatedHistory

        _currentTransfer.value = _currentTransfer.value?.copy(fileName = fileName, progress = 100)
        markBatchFileCompleted(fileName)

        if (transferDirection == TransferDirection.SENDING) {
            if (pendingFilesQueue.isNotEmpty()) {
                _statusMessage.value = "Sent $fileName ($fileIndex/$totalFiles) — sending next..."
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    processNextInQueue()
                }, 250)
            } else {
                _isProcessingQueue.value = false
                _lastTransferResult.value = TransferResult(
                    fileName = fileName,
                    fileSize = fileSize,
                    direction = transferDirection,
                    success = true,
                    errorMessage = null
                )
                _statusMessage.value = "$fileName sent successfully."
            }
        } else {
            // Receiver side: only show final completion screen when the last file in the batch finishes
            if (fileIndex >= totalFiles) {
                _lastTransferResult.value = TransferResult(
                    fileName = fileName,
                    fileSize = fileSize,
                    direction = transferDirection,
                    success = true,
                    errorMessage = null
                )
                _statusMessage.value = if (totalFiles > 1) {
                    "All $totalFiles files received! Saved to Downloads/Seamless."
                } else {
                    "$fileName received! Saved to Downloads/Seamless."
                }
            } else {
                _statusMessage.value = "Received $fileName ($fileIndex/$totalFiles)..."
            }
        }
    }

    override fun onFileTransferFailed(fileName: String, error: String) {
        Log.e(TAG, "File transfer failed: $fileName - $error")

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

        _lastTransferResult.value = TransferResult(
            fileName = fileName,
            fileSize = fileSize,
            direction = transferDirection,
            success = false,
            errorMessage = error
        )

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

        markBatchFileFailed(fileName)
        _errorMessage.value = error
    }

    fun dismissTransferScreen() {
        _transferScreenState.value = TransferScreenState.NONE
        _lastTransferResult.value = null
        _currentTransfer.value = null
        _errorMessage.value = null
    }

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

data class TransferProgress(
    val id: String,
    val fileName: String,
    val progress: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0,
    val estimatedSecondsRemaining: Int = -1,
    val fileIndex: Int = 1,
    val totalFiles: Int = 1
) {
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
    val filePath: String? = null
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

enum class TransferScreenState {
    NONE,
    ANIMATING_SEND,
    ANIMATING_RECEIVE,
    SENDING,
    RECEIVING,
    WAITING
}

data class TransferResult(
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val success: Boolean,
    val errorMessage: String? = null
)
