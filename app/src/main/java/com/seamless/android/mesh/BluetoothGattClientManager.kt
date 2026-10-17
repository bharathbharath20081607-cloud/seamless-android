@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.mesh

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.seamless.android.protocol.BitchatPacket
import com.seamless.android.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.Job
import com.seamless.android.ui.debug.DebugSettingsManager
import com.seamless.android.ui.debug.DebugScanResult

/**
 * Manages GATT client operations, scanning, and client-side connections
 */
class BluetoothGattClientManager(
    private val context: Context,
    private val connectionScope: CoroutineScope,
    private val connectionTracker: BluetoothConnectionTracker,
    private val permissionManager: BluetoothPermissionManager,
    private val powerManager: PowerManager,
    private val delegate: BluetoothConnectionManagerDelegate?
) {
    
    companion object {
        private const val TAG = "BluetoothGattClientManager"
    }
    
    // Core Bluetooth components
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    /**
     * Public: Connect to a device by MAC address (for debug UI)
     */
    fun connectToAddress(deviceAddress: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        return if (device != null) {
            val rssi = connectionTracker.getBestRSSI(deviceAddress) ?: -50
            connectToDevice(device, rssi)
            true
        } else {
            Log.w(TAG, "connectToAddress: No device for $deviceAddress")
            false
        }
    }

    // Scan management
    private var scanCallback: ScanCallback? = null
    
    // Scan rate limiting to prevent "scanning too frequently" errors
    private var lastScanStartTime = 0L
    private var lastScanStopTime = 0L
    private var isCurrentlyScanning = false
    private val scanRateLimit = 5000L // Minimum 5 seconds between scan start attempts
    
    // RSSI monitoring state
    private var rssiMonitoringJob: Job? = null
    
    // State management
    private var isActive = false
    
    /**
     * Start client manager
     */
    fun start(): Boolean {
        // Respect debug setting
        try {
            if (!com.seamless.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value) {
                Log.i(TAG, "Client start skipped: GATT Client disabled in debug settings")
                return false
            }
        } catch (_: Exception) { }

        if (isActive) {
            Log.d(TAG, "GATT client already active; start is a no-op")
            return true
        }
        if (!permissionManager.hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return false
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        if (bleScanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return false
        }
        
        isActive = true
        
        connectionScope.launch {
            // CRITICAL FIX: Always start scanning immediately on startup.
            // Previously, when duty-cycling was enabled (BALANCED mode = default),
            // startScanning() was never called here, and the duty-cycle's first
            // "ON" signal would fire before the client was ready — so scanning
            // never began. Now we always kick off an initial scan; the duty cycle
            // will naturally stop/restart it on its next OFF/ON transition.
            startScanning()
            
            if (powerManager.shouldUseDutyCycle()) {
                Log.i(TAG, "Using power-aware duty cycling (initial scan already started)")
            }
            
            // Start RSSI monitoring
            startRSSIMonitoring()
        }
        
        return true
    }
    
    /**
     * Stop client manager
     */
    fun stop() {
        if (!isActive) {
            // Idempotent stop
            stopScanning()
            stopRSSIMonitoring()
            Log.i(TAG, "GATT client manager stopped (already inactive)")
            return
        }

        isActive = false
        
        connectionScope.launch {
            // Disconnect all client connections decisively
            try {
                val conns = connectionTracker.getConnectedDevices().values.filter { it.isClient && it.gatt != null }
                conns.forEach { dc ->
                    try { dc.gatt?.disconnect() } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
            
            stopScanning()
            stopRSSIMonitoring()
            Log.i(TAG, "GATT client manager stopped")
        }
    }
    
    /**
     * Handle scan state changes from power manager
     */
    fun onScanStateChanged(shouldScan: Boolean) {
        val enabled = try { com.seamless.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value } catch (_: Exception) { true }
        if (shouldScan && enabled) {
            startScanning()
        } else {
            stopScanning()
        }
    }
    
    /**
     * Start periodic RSSI monitoring for all client connections
     */
    private fun startRSSIMonitoring() {
        rssiMonitoringJob?.cancel()
        rssiMonitoringJob = connectionScope.launch {
            while (isActive) {
                try {
                    // Request RSSI from all client connections
                    val connectedDevices = connectionTracker.getConnectedDevices()
                    connectedDevices.values.filter { it.isClient && it.gatt != null }.forEach { deviceConn ->
                        try {
                            Log.d(TAG, "Requesting RSSI from ${deviceConn.device.address}")
                            deviceConn.gatt?.readRemoteRssi()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to request RSSI from ${deviceConn.device.address}: ${e.message}")
                        }
                    }
                    delay(AppConstants.Mesh.RSSI_UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.w(TAG, "Error in RSSI monitoring: ${e.message}")
                    delay(AppConstants.Mesh.RSSI_UPDATE_INTERVAL_MS)
                }
            }
        }
    }
    
    /**
     * Stop RSSI monitoring
     */
    private fun stopRSSIMonitoring() {
        rssiMonitoringJob?.cancel()
        rssiMonitoringJob = null
    }
    
    /**
     * Start scanning with rate limiting
     */
    @Suppress("DEPRECATION")
    private fun startScanning() {
        // Respect debug setting
        val enabled = try { com.seamless.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value } catch (_: Exception) { true }
        if (!permissionManager.hasBluetoothPermissions() || bleScanner == null || !isActive || !enabled) return
        
        // Rate limit scan starts to prevent "scanning too frequently" errors
        val currentTime = System.currentTimeMillis()
        if (isCurrentlyScanning) {
            Log.d(TAG, "Scan already in progress, skipping start request")
            return
        }
        
        val timeSinceLastStart = currentTime - lastScanStartTime
        if (timeSinceLastStart < scanRateLimit) {
            val remainingWait = scanRateLimit - timeSinceLastStart
            Log.w(TAG, "Scan rate limited: need to wait ${remainingWait}ms before starting scan")
            
            // Schedule delayed scan start
            connectionScope.launch {
                delay(remainingWait)
                if (isActive && !isCurrentlyScanning) {
                    startScanning()
                }
            }
            return
        }
        
        // Use hardware ScanFilter in background so Android 8.1+ allows screen-off scanning,
        // and Broad Spectrum in foreground to catch all advertisement variants.
        val serviceParcelUuid = ParcelUuid(AppConstants.Mesh.Gatt.SERVICE_UUID)
        val scanFilters = if (powerManager.isInBackground()) {
            listOf(ScanFilter.Builder().setServiceUuid(serviceParcelUuid).build())
        } else {
            emptyList<ScanFilter>()
        }
        
        Log.d(TAG, "Starting BLE scan (background=${powerManager.isInBackground()}, filters=${scanFilters.size}) - looking for ${AppConstants.Mesh.Gatt.SERVICE_UUID}")
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Log.d(TAG, "Scan result received: ${result.device.address}")
                handleScanResult(result)
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Log.d(TAG, "Batch scan results received: ${results.size} devices")
                results.forEach { result ->
                    handleScanResult(result)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                isCurrentlyScanning = false
                lastScanStopTime = System.currentTimeMillis()
                
                when (errorCode) {
                    1 -> Log.e(TAG, "SCAN_FAILED_ALREADY_STARTED")
                    2 -> Log.e(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED") 
                    3 -> Log.e(TAG, "SCAN_FAILED_INTERNAL_ERROR")
                    4 -> Log.e(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED")
                    5 -> Log.e(TAG, "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES")
                    6 -> {
                        Log.e(TAG, "SCAN_FAILED_SCANNING_TOO_FREQUENTLY")
                        Log.w(TAG, "Scan failed due to rate limiting - will retry after delay")
                        connectionScope.launch {
                            delay(10000) // Wait 10 seconds before retrying
                            if (isActive) {
                                startScanning()
                            }
                        }
                    }
                    else -> Log.e(TAG, "Unknown scan failure code: $errorCode")
                }
            }
        }
        
        try {
            lastScanStartTime = currentTime
            isCurrentlyScanning = true
            
            bleScanner.startScan(scanFilters, powerManager.getScanSettings(), scanCallback)
            Log.d(TAG, "BLE scan started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan: ${e.message}")
            isCurrentlyScanning = false
        }
    }
    
    /**
     * Stop scanning
     */
    @Suppress("DEPRECATION")
    private fun stopScanning() {
        if (!permissionManager.hasBluetoothPermissions() || bleScanner == null) return
        
        if (isCurrentlyScanning) {
            try {
                scanCallback?.let { 
                    bleScanner.stopScan(it)
                    Log.d(TAG, "BLE scan stopped successfully")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            
            isCurrentlyScanning = false
            lastScanStopTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Handle scan result and initiate connection if appropriate
     */
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val deviceAddress = device.address
        val scanRecord = result.scanRecord
        
        // CRITICAL: Only process devices that advertise our service UUID (in serviceUuids or serviceData)
        val hasOurService = scanRecord?.serviceUuids?.any { it.uuid == AppConstants.Mesh.Gatt.SERVICE_UUID } == true ||
            scanRecord?.serviceData?.keys?.any { it.uuid == AppConstants.Mesh.Gatt.SERVICE_UUID } == true
        if (!hasOurService) {
            return
        }

        // Log.d(TAG, "Received scan result from $deviceAddress - already connected: ${connectionTracker.isDeviceConnected(deviceAddress)}")
        
        // Store RSSI from scan results for later use (especially for server connections)
        connectionTracker.updateScanRSSI(deviceAddress, rssi)

        // Publish scan result to debug UI buffer
        try {
            DebugSettingsManager.getInstance().addScanResult(
                DebugScanResult(
                    deviceName = device.name,
                    deviceAddress = deviceAddress,
                    rssi = rssi,
                    peerID = null // peerID unknown at scan time
                )
            )
        } catch (_: Exception) { }
        
        // Power-aware RSSI filtering
        if (rssi < powerManager.getRSSIThreshold()) {
            Log.d(TAG, "Skipping device $deviceAddress due to weak signal: $rssi < ${powerManager.getRSSIThreshold()}")
            // Even if we skip connecting, still publish scan result to debug UI
            try {
                val pid: String? = null // We don't know peerID until packet exchange
                DebugSettingsManager.getInstance().addScanResult(
                    DebugScanResult(
                        deviceName = device.name,
                        deviceAddress = deviceAddress,
                        rssi = rssi,
                        peerID = pid
                    )
                )
            } catch (_: Exception) { }
            return
        }
        
        // Check if already connected OR already attempting to connect
        if (connectionTracker.isDeviceConnected(deviceAddress)) {
            return
        }
        
        // Check if connection attempt is allowed
        if (!connectionTracker.isConnectionAttemptAllowed(deviceAddress)) {
            Log.d(TAG, "Connection to $deviceAddress not allowed due to recent attempts")
            return
        }
        
        if (connectionTracker.isConnectionLimitReached()) {
            Log.d(TAG, "Connection limit reached (${powerManager.getMaxConnections()})")
            return
        }
        
        // Add pending connection and start connection
        if (connectionTracker.addPendingConnection(deviceAddress)) {
            connectToDevice(device, rssi)
        }
    }
    
    /**
     * Connect to a device as GATT client
     */
    @Suppress("DEPRECATION")
    private fun connectToDevice(device: BluetoothDevice, rssi: Int) {
        if (!permissionManager.hasBluetoothPermissions()) return

        val deviceAddress = device.address
        Log.i(TAG, "Connecting to bitchat device: $deviceAddress")
        
        val gattCallback = object : BluetoothGattCallback() {
            @Volatile
            private var connectionNotified = false

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "Client: Connection state change - Device: $deviceAddress, Status: $status, NewState: $newState")

                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Client: Successfully connected to $deviceAddress. Requesting MTU...")
                    // Request a larger MTU. Must be done before any data transfer.
                    connectionScope.launch {
                        delay(150) // Brief delay improves reliability of MTU request across OEM stacks
                        try {
                            gatt.requestMtu(517)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to request MTU for $deviceAddress: ${e.message}")
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "Client: Disconnected from $deviceAddress with error status $status")
                        if (status == 133 || status == 147) {
                            Log.w(TAG, "Client: Transient GATT connection error ($status) for $deviceAddress - scheduling fast recovery")
                            connectionScope.launch {
                                delay(1500)
                                connectionTracker.removePendingConnection(deviceAddress)
                            }
                        }
                    } else {
                        Log.d(TAG, "Client: Cleanly disconnected from $deviceAddress")
                    }

                    // CRITICAL FIX: Always clean up device connection regardless of status code!
                    // Previously, non-zero status codes (timeout=8, remote terminate=19, error=133)
                    // skipped cleanupDeviceConnection, leaving dead entries in connectedDevices forever!
                    connectionTracker.cleanupDeviceConnection(deviceAddress)

                    // Notify higher layers about device disconnection to update direct flags
                    delegate?.onDeviceDisconnected(gatt.device)

                    connectionScope.launch {
                        delay(300)
                        try {
                            gatt.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing GATT: ${e.message}")
                        }
                    }
                }
            }
            
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val deviceAddress = gatt.device.address
                Log.i(TAG, "Client: MTU changed for $deviceAddress to $mtu with status $status")

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "MTU successfully negotiated for $deviceAddress. Requesting HIGH priority and 2M PHY...")
                    
                    // ** TURBO MODE: Request HIGH priority for lower latency (11ms vs 100ms) **
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        Log.i(TAG, "🚀 Requested HIGH connection priority for $deviceAddress")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to request HIGH priority: ${e.message}")
                    }
                    
                    // ** TURBO MODE: Request 2M PHY for double speed (2Mbps vs 1Mbps) **
                    try {
                        gatt.setPreferredPhy(
                            BluetoothDevice.PHY_LE_2M_MASK,
                            BluetoothDevice.PHY_LE_2M_MASK,
                            BluetoothDevice.PHY_OPTION_NO_PREFERRED
                        )
                        Log.i(TAG, "🚀 Requested 2M PHY for $deviceAddress")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to request 2M PHY: ${e.message}")
                    }
                    
                    // Now that MTU is set, connection is fully ready.
                    val deviceConn = BluetoothConnectionTracker.DeviceConnection(
                        device = gatt.device,
                        gatt = gatt,
                        rssi = rssi,
                        isClient = true
                    )
                    connectionTracker.addDeviceConnection(deviceAddress, deviceConn)
                    
                    // Start service discovery only AFTER MTU is set.
                    gatt.discoverServices()
                } else {
                    Log.w(TAG, "MTU negotiation failed for $deviceAddress with status: $status. Proceeding with default discovery.")
                    val deviceConn = BluetoothConnectionTracker.DeviceConnection(
                        device = gatt.device,
                        gatt = gatt,
                        rssi = rssi,
                        isClient = true
                    )
                    connectionTracker.addDeviceConnection(deviceAddress, deviceConn)
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(AppConstants.Mesh.Gatt.SERVICE_UUID)
                    if (service != null) {
                        val characteristic = service.getCharacteristic(AppConstants.Mesh.Gatt.CHARACTERISTIC_UUID)
                        if (characteristic != null) {
                            connectionTracker.getDeviceConnection(deviceAddress)?.let { deviceConn ->
                                val updatedConn = deviceConn.copy(characteristic = characteristic)
                                connectionTracker.updateDeviceConnection(deviceAddress, updatedConn)
                                Log.d(TAG, "Client: Updated device connection with characteristic for $deviceAddress")
                            }
                            
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(AppConstants.Mesh.Gatt.DESCRIPTOR_UUID)
                            if (descriptor != null) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                }
                                
                                // Fallback in case onDescriptorWrite is delayed or not fired by OEM BLE stack
                                connectionScope.launch {
                                    delay(600)
                                    if (!connectionNotified && connectionTracker.isDeviceConnected(deviceAddress)) {
                                        connectionNotified = true
                                        Log.i(TAG, "Client: Connection setup complete (fallback timer) for $deviceAddress")
                                        delegate?.onDeviceConnected(device)
                                    }
                                }
                            } else {
                                Log.e(TAG, "Client: CCCD descriptor not found for $deviceAddress")
                                gatt.disconnect()
                            }
                        } else {
                            Log.e(TAG, "Client: Required characteristic not found for $deviceAddress")
                            gatt.disconnect()
                        }
                    } else {
                        Log.e(TAG, "Client: Required service not found for $deviceAddress")
                        gatt.disconnect()
                    }
                } else {
                    Log.e(TAG, "Client: Service discovery failed with status $status for $deviceAddress")
                    gatt.disconnect()
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val addr = gatt.device.address
                Log.d(TAG, "Client: onDescriptorWrite for $addr, status=$status")
                if (!connectionNotified) {
                    connectionNotified = true
                    connectionScope.launch {
                        delay(50) // Let BLE stack finish descriptor transaction before initial ANNOUNCE write
                        Log.i(TAG, "Client: Connection setup complete for $addr")
                        delegate?.onDeviceConnected(gatt.device)
                    }
                }
            }

            private fun handleCharacteristicNotification(gatt: BluetoothGatt, value: ByteArray?) {
                try {
                    if (value == null || value.isEmpty()) {
                        Log.w(TAG, "Client: Received empty packet from ${gatt.device.address}")
                        return
                    }
                    Log.i(TAG, "Client: Received packet from ${gatt.device.address}, size: ${value.size} bytes")
                    val packet = BitchatPacket.fromBinaryData(value)
                    if (packet != null) {
                        val peerID = packet.senderID.take(8).toByteArray().joinToString("") { "%02x".format(it) }
                        Log.d(TAG, "Client: Parsed packet type ${packet.type} from $peerID")
                        delegate?.onPacketReceived(packet, peerID, gatt.device)
                    } else {
                        Log.w(TAG, "Client: Failed to parse packet from ${gatt.device.address}, size: ${value.size} bytes")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Client: Exception processing packet from ${gatt.device.address}: ${e.message}", e)
                } catch (t: Throwable) {
                    Log.e(TAG, "Client: Fatal error processing packet: ${t.message}", t)
                }
            }

            // Android 13+ (API 33+) 3-parameter overload — REQUIRED because API 33+ no longer populates characteristic.value
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleCharacteristicNotification(gatt, value)
            }
            
            // Legacy overload for Android 12 and below (API < 33)
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    handleCharacteristicNotification(gatt, characteristic.value)
                }
            }
            
            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                val deviceAddress = gatt.device.address
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Client: RSSI updated for $deviceAddress: $rssi dBm")
                    
                    // Mark link as alive since RSSI read succeeded over the active BLE connection
                    connectionTracker.updateLastPacketReceived(deviceAddress)

                    // Update the connection tracker with new RSSI value
                    connectionTracker.getDeviceConnection(deviceAddress)?.let { deviceConn ->
                        val updatedConn = deviceConn.copy(rssi = rssi, lastPacketReceivedAt = System.currentTimeMillis())
                        connectionTracker.updateDeviceConnection(deviceAddress, updatedConn)
                    }
                    delegate?.onRSSIUpdated(deviceAddress, rssi)
                } else {
                    Log.w(TAG, "Client: Failed to read RSSI for $deviceAddress, status: $status")
                }
            }
            
            // PRODUCTION FIX: Signal write completion latch and detect transient errors
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                val deviceAddress = gatt.device.address
                val isSuccess = status == BluetoothGatt.GATT_SUCCESS
                connectionTracker.onDeviceWriteCompleted(deviceAddress, isSuccess)
                if (isSuccess) {
                    Log.d(TAG, "Client: ✅ Write completed for $deviceAddress")
                } else {
                    Log.e(TAG, "Client: ❌ Write FAILED for $deviceAddress with status $status")
                    if (status == 133 || status == 143) {
                        Log.w(TAG, "Client: Write failure (status $status) - transient BLE buffer congestion, flow control will retry.")
                    }
                }
            }
        }
        
        try {
            Log.d(TAG, "Client: Attempting GATT connection to $deviceAddress with autoConnect=false")
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                Log.e(TAG, "connectGatt returned null for $deviceAddress")
                // PRODUCTION FIX: Remove pending connection so the peer isn't permanently blacklisted
                connectionTracker.removePendingConnection(deviceAddress)
            } else {
                Log.d(TAG, "Client: GATT connection initiated successfully for $deviceAddress")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client: Exception connecting to $deviceAddress: ${e.message}")
            // PRODUCTION FIX: Remove pending connection so the peer isn't permanently blacklisted
            connectionTracker.removePendingConnection(deviceAddress)
        }
    }
    
    /**
     * Restart scanning for power mode changes
     */
    fun restartScanning() {
        // Respect debug setting
        val enabled = try { com.seamless.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value } catch (_: Exception) { true }
        if (!isActive || !enabled) return
        
        connectionScope.launch {
            stopScanning()
            delay(1000) // Extra delay to avoid rate limiting
            
            if (powerManager.shouldUseDutyCycle()) {
                Log.i(TAG, "Switching to duty cycle scanning mode")
                // Duty cycle will handle scanning
            } else {
                Log.i(TAG, "Switching to continuous scanning mode")
                startScanning()
            }
        }
    }
} 
