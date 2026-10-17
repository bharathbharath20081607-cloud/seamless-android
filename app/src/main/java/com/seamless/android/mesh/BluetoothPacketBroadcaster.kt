@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.mesh

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.os.Build
import android.util.Log
import com.seamless.android.protocol.SpecialRecipients
import com.seamless.android.model.RoutedPacket
import com.seamless.android.protocol.MessageType
import com.seamless.android.util.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.actor

/**
 * Handles packet broadcasting to connected devices using actor pattern for serialization
 * 
 * In Bluetooth Low Energy (BLE):
 *
 * Peripheral (server):
 * Advertises.
 * Accepts connections.
 * Hosts a GATT server.
 * Remote devices read/write/subscribe to characteristics.
 *
 *  Central (client):
 * Scans.
 * Initiates connections.
 * Hosts a GATT client.
 * Reads/writes to the peripheral’s characteristics.
 */
class BluetoothPacketBroadcaster(
    private val connectionScope: CoroutineScope,
    private val connectionTracker: BluetoothConnectionTracker,
    private val fragmentManager: FragmentManager?
) {
    
    companion object {
        private const val TAG = "BluetoothPacketBroadcaster"
        private const val CLEANUP_DELAY = com.seamless.android.util.AppConstants.Mesh.BROADCAST_CLEANUP_DELAY_MS
    }

    // Optional nickname resolver injected by higher layer (peerID -> nickname?)
    private var nicknameResolver: ((String) -> String?)? = null

    fun setNicknameResolver(resolver: (String) -> String?) {
        nicknameResolver = resolver
    }
    
    /**
     * Debug logging helper - can be easily removed/disabled for production
     */
    private fun logPacketRelay(
        typeName: String,
        senderPeerID: String,
        senderNick: String?,
        incomingPeer: String?,
        incomingAddr: String?,
        toPeer: String?,
        toDeviceAddress: String,
        ttl: UByte
    ) {
        try {
            val fromNick = incomingPeer?.let { nicknameResolver?.invoke(it) }
            val toNick = toPeer?.let { nicknameResolver?.invoke(it) }
            val isRelay = (incomingAddr != null || incomingPeer != null)
            
            com.seamless.android.ui.debug.DebugSettingsManager.getInstance().logPacketRelayDetailed(
                packetType = typeName,
                senderPeerID = senderPeerID,
                senderNickname = senderNick,
                fromPeerID = incomingPeer,
                fromNickname = fromNick,
                fromDeviceAddress = incomingAddr,
                toPeerID = toPeer,
                toNickname = toNick,
                toDeviceAddress = toDeviceAddress,
                ttl = ttl,
                isRelay = isRelay
            )
        } catch (_: Exception) { 
            // Silently ignore debug logging failures
        }
    }
    
    // Data class to hold broadcast request information
    private data class BroadcastRequest(
        val routed: RoutedPacket,
        val gattServer: BluetoothGattServer?,
        val characteristic: BluetoothGattCharacteristic?,
        val targetPeerID: String? = null
    )
    
    // Actor scope for the broadcaster
    private val broadcasterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transferJobs = ConcurrentHashMap<String, Job>()
    private val fragmentMutex = kotlinx.coroutines.sync.Mutex()
    
    // SERIALIZATION: Actor to serialize all broadcast operations
    @OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val broadcasterActor = broadcasterScope.actor<BroadcastRequest>(
        capacity = Channel.UNLIMITED
    ) {
        Log.d(TAG, "🎭 Created packet broadcaster actor")
        try {
            for (request in channel) {
                broadcastSinglePacketInternal(
                    request.routed,
                    request.gattServer,
                    request.characteristic,
                    request.targetPeerID
                )
            }
        } finally {
            Log.d(TAG, "🎭 Packet broadcaster actor terminated")
        }
    }
    
    fun broadcastPacket(
        routed: RoutedPacket,
        gattServer: BluetoothGattServer?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        val packet = routed.packet
        val isFile = packet.type == MessageType.FILE_TRANSFER.value
        if (isFile) {
            Log.d(TAG, "📤 Broadcasting FILE_TRANSFER: ${packet.payload.size} bytes")
        }
        // Prefer caller-provided transferId (e.g., for encrypted media), else derive for FILE_TRANSFER
        val transferId = routed.transferId ?: (if (isFile) sha256Hex(packet.payload) else null)
        // Check if we need to fragment
        if (fragmentManager != null) {
            val fragments = try {
                fragmentManager.createFragments(packet)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fragment creation failed: ${e.message}", e)
                if (isFile) {
                    Log.e(TAG, "❌ File fragmentation failed for ${packet.payload.size} byte file")
                }
                return
            }
            if (fragments.size > 1) {
                if (isFile) {
                    Log.d(TAG, "🔀 File needs ${fragments.size} fragments")
                }
                Log.d(TAG, "Fragmenting packet into ${fragments.size} fragments")
                if (transferId != null) {
                    TransferProgressManager.start(transferId, fragments.size)
                }
                val job = connectionScope.launch {
                    fragmentMutex.lock()
                    try {
                        var sent = 0
                        fragments.forEach { fragment ->
                            if (!isActive) return@launch
                            // If cancelled, stop sending remaining fragments
                            if (transferId != null && transferJobs[transferId]?.isCancelled == true) return@launch
                            broadcastSinglePacket(RoutedPacket(fragment, transferId = transferId), gattServer, characteristic)
                            // Per-device GATT callback latches now pace hardware writes; 40ms keeps actor channel smooth
                            delay(40)
                            if (transferId != null) {
                                sent += 1
                                TransferProgressManager.progress(transferId, sent, fragments.size)
                                if (sent == fragments.size) TransferProgressManager.complete(transferId, fragments.size)
                            }
                        }
                    } finally {
                        fragmentMutex.unlock()
                    }
                }
                if (transferId != null) {
                    transferJobs[transferId] = job
                    job.invokeOnCompletion { transferJobs.remove(transferId) }
                }
                return
            }
        }
        
        // Send single packet if no fragmentation needed
        if (transferId != null) {
            TransferProgressManager.start(transferId, 1)
        }
        broadcastSinglePacket(routed, gattServer, characteristic)
        if (transferId != null) {
            TransferProgressManager.progress(transferId, 1, 1)
            TransferProgressManager.complete(transferId, 1)
        }
    }

    fun cancelTransfer(transferId: String): Boolean {
        val job = transferJobs.remove(transferId) ?: return false
        job.cancel()
        return true
    }

    /**
     * Send a packet to a specific peer only, without broadcasting.
     * Returns true if a direct path was found and used.
     */
    fun sendPacketToPeer(
        routed: RoutedPacket,
        targetPeerID: String,
        gattServer: BluetoothGattServer?,
        characteristic: BluetoothGattCharacteristic?
    ): Boolean {
        val packet = routed.packet
        // Normalize to lowercase for map lookup (addressPeerMap stores lowercase)
        val normalizedTargetPeerID = targetPeerID.lowercase()

        // Verify a direct connection (server subscription or client connection) exists
        val hasServerTarget = connectionTracker.getSubscribedDevices()
            .any { connectionTracker.addressPeerMap[it.address] == normalizedTargetPeerID }
        val hasClientTarget = connectionTracker.getConnectedDevices().values
            .any {
                connectionTracker.addressPeerMap[it.device.address] == normalizedTargetPeerID &&
                    it.isClient && it.gatt != null && it.characteristic != null
            }

        if (!hasServerTarget && !hasClientTarget) {
            return false
        }

        val isFile = packet.type == MessageType.FILE_TRANSFER.value
        if (isFile) {
            Log.d(TAG, "📤 Sending direct FILE_TRANSFER to $normalizedTargetPeerID: ${packet.payload.size} bytes")
        }
        val transferId = routed.transferId ?: (if (isFile) sha256Hex(packet.payload) else null)

        // Fragment large direct packets so they never exceed negotiated BLE MTU
        if (fragmentManager != null) {
            val fragments = try {
                fragmentManager.createFragments(packet)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Direct fragment creation failed: ${e.message}", e)
                return false
            }
            if (fragments.size > 1) {
                if (transferId != null) {
                    TransferProgressManager.start(transferId, fragments.size)
                }
                val job = connectionScope.launch {
                    fragmentMutex.lock()
                    try {
                        var sent = 0
                        fragments.forEach { fragment ->
                            if (!isActive) return@launch
                            if (transferId != null && transferJobs[transferId]?.isCancelled == true) return@launch
                            broadcastSinglePacket(
                                RoutedPacket(fragment, transferId = transferId),
                                gattServer,
                                characteristic,
                                normalizedTargetPeerID
                            )
                            delay(40)
                            if (transferId != null) {
                                sent += 1
                                TransferProgressManager.progress(transferId, sent, fragments.size)
                                if (sent == fragments.size) TransferProgressManager.complete(transferId, fragments.size)
                            }
                        }
                    } finally {
                        fragmentMutex.unlock()
                    }
                }
                if (transferId != null) {
                    transferJobs[transferId] = job
                    job.invokeOnCompletion { transferJobs.remove(transferId) }
                }
                return true
            }
        }

        if (transferId != null) {
            TransferProgressManager.start(transferId, 1)
        }
        broadcastSinglePacket(routed, gattServer, characteristic, normalizedTargetPeerID)
        if (transferId != null) {
            TransferProgressManager.progress(transferId, 1, 1)
            TransferProgressManager.complete(transferId, 1)
        }
        return true
    }

    private fun sha256Hex(bytes: ByteArray): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { bytes.size.toString(16) }

    
    /**
     * Public entry point for broadcasting - submits request to actor for serialization
     */
    fun broadcastSinglePacket(
        routed: RoutedPacket,
        gattServer: BluetoothGattServer?,
        characteristic: BluetoothGattCharacteristic?,
        targetPeerID: String? = null
    ) {
        // Submit broadcast request to actor for serialization
        broadcasterScope.launch {
            try {
                broadcasterActor.send(BroadcastRequest(routed, gattServer, characteristic, targetPeerID))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send broadcast request to actor: ${e.message}")
                // Fallback to direct processing if actor fails
                broadcastSinglePacketInternal(routed, gattServer, characteristic, targetPeerID)
            }
        }
    }
    
    /**
     * Internal broadcast implementation - runs in serialized actor context
     */
    private suspend fun broadcastSinglePacketInternal(
        routed: RoutedPacket,
        gattServer: BluetoothGattServer?,
        characteristic: BluetoothGattCharacteristic?,
        explicitTargetPeerID: String? = null
    ) {
        val packet = routed.packet
        val data = packet.toBinaryData() ?: return
        val typeName = MessageType.fromValue(packet.type)?.name ?: packet.type.toString()
        val senderPeerID = (routed.peerID ?: packet.senderID.toHexString()).lowercase()
        val incomingAddr = routed.relayAddress
        val incomingPeer = incomingAddr?.let { connectionTracker.addressPeerMap[it] }
        val senderNick = senderPeerID.let { pid -> nicknameResolver?.invoke(pid) }

        // Direct target resolution (either explicit via sendPacketToPeer or via packet.recipientID)
        val directRecipientID = explicitTargetPeerID?.lowercase()
            ?: if (packet.recipientID != null && !packet.recipientID.contentEquals(SpecialRecipients.BROADCAST)) {
                packet.recipientID.toHexString().lowercase()
            } else {
                null
            }

        if (!directRecipientID.isNullOrEmpty()) {
            // Try to find the recipient in server connections (subscribedDevices)
            val targetDevice = connectionTracker.getSubscribedDevices()
                .firstOrNull { connectionTracker.addressPeerMap[it.address] == directRecipientID }
            
            // If found, send directly
            if (targetDevice != null) {
                Log.d(TAG, "Send packet type ${packet.type} directly to target device for recipient $directRecipientID: ${targetDevice.address}")
                if (notifyDevice(targetDevice, data, gattServer, characteristic)) {
                    val toPeer = connectionTracker.addressPeerMap[targetDevice.address]
                    logPacketRelay(typeName, senderPeerID, senderNick, incomingPeer, incomingAddr, toPeer, targetDevice.address, packet.ttl)
                    return  // Sent, no need to continue
                }
            }

            // Try to find the recipient in client connections (connectedDevices) - MUST filter for active client GATT connections
            val targetDeviceConn = connectionTracker.getConnectedDevices().values
                .firstOrNull {
                    connectionTracker.addressPeerMap[it.device.address] == directRecipientID &&
                        it.isClient && it.gatt != null && it.characteristic != null
                }
            
            // If found, send directly
            if (targetDeviceConn != null) {
                Log.d(TAG, "Send packet type ${packet.type} directly to target client connection for recipient $directRecipientID: ${targetDeviceConn.device.address}")
                if (writeToDeviceConn(targetDeviceConn, data)) {
                    val toPeer = connectionTracker.addressPeerMap[targetDeviceConn.device.address]
                    logPacketRelay(typeName, senderPeerID, senderNick, incomingPeer, incomingAddr, toPeer, targetDeviceConn.device.address, packet.ttl)
                    return  // Sent, no need to continue
                }
            }

            // If caller explicitly requested direct-only delivery, do not fall through to broadcast
            if (explicitTargetPeerID != null) {
                return
            }
        }

        // Else, continue with broadcasting to all devices
        val subscribedDevices = connectionTracker.getSubscribedDevices()
        val connectedDevices = connectionTracker.getConnectedDevices()
        
        Log.i(TAG, "Broadcasting packet type ${packet.type} to ${subscribedDevices.size} server + ${connectedDevices.size} client connections")

        val senderID = packet.senderID.toHexString().lowercase()
        
        // Send to server connections (devices connected to our GATT server)
        for (device in subscribedDevices) {
            if (device.address == routed.relayAddress) {
                Log.d(TAG, "Skipping broadcast to client back to relayer: ${device.address}")
                continue
            }
            if (connectionTracker.addressPeerMap[device.address] == senderID) {
                Log.d(TAG, "Skipping broadcast to client back to sender: ${device.address}")
                continue
            }
            val sent = notifyDevice(device, data, gattServer, characteristic)
            if (sent) {
                val toPeer = connectionTracker.addressPeerMap[device.address]
                logPacketRelay(typeName, senderPeerID, senderNick, incomingPeer, incomingAddr, toPeer, device.address, packet.ttl)
            }
        }
        
        // Send to client connections (GATT servers we are connected to)
        for (deviceConn in connectedDevices.values) {
            if (deviceConn.isClient && deviceConn.gatt != null && deviceConn.characteristic != null) {
                if (deviceConn.device.address == routed.relayAddress) {
                    Log.d(TAG, "Skipping broadcast to server back to relayer: ${deviceConn.device.address}")
                    continue
                }
                if (connectionTracker.addressPeerMap[deviceConn.device.address] == senderID) {
                    Log.d(TAG, "Skipping broadcast to server back to sender: ${deviceConn.device.address}")
                    continue
                }
                val sent = writeToDeviceConn(deviceConn, data)
                if (sent) {
                    val toPeer = connectionTracker.addressPeerMap[deviceConn.device.address]
                    logPacketRelay(typeName, senderPeerID, senderNick, incomingPeer, incomingAddr, toPeer, deviceConn.device.address, packet.ttl)
                }
            }
        }
    }
    
    /**
     * Send data to a single device (server->client) with per-device notification latch and retry
     */
    private suspend fun notifyDevice(
        device: BluetoothDevice, 
        data: ByteArray,
        gattServer: BluetoothGattServer?,
        characteristic: BluetoothGattCharacteristic?
    ): Boolean {
        val char = characteristic ?: return false
        val server = gattServer ?: return false
        val address = device.address
        return try {
            var initiated = false
            val retryDelays = longArrayOf(0L, 15L, 30L, 60L)
            for (delayMs in retryDelays) {
                if (delayMs > 0L) delay(delayMs)
                connectionTracker.prepareDeviceNotify(address)
                initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, char, false, data) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    char.value = data
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, char, false)
                }
                if (initiated) break
                connectionTracker.onDeviceNotifyCompleted(address, false)
            }
            if (initiated) {
                connectionTracker.awaitDeviceNotify(address, 450L)
            }
            initiated
        } catch (e: Exception) {
            Log.w(TAG, "Error sending to server connection $address: ${e.message}")
            connectionTracker.onDeviceNotifyCompleted(address, false)
            connectionScope.launch {
                delay(CLEANUP_DELAY)
                connectionTracker.removeSubscribedDevice(device)
                connectionTracker.addressPeerMap.remove(address)
            }
            false
        }
    }

    /**
     * Send data to a single device (client->server) with per-device write latch and retry
     */
    private suspend fun writeToDeviceConn(
        deviceConn: BluetoothConnectionTracker.DeviceConnection, 
        data: ByteArray
    ): Boolean {
        val char = deviceConn.characteristic ?: return false
        val gatt = deviceConn.gatt ?: return false
        val address = deviceConn.device.address
        return try {
            var initiated = false
            val retryDelays = longArrayOf(0L, 15L, 30L, 60L)
            for (delayMs in retryDelays) {
                if (delayMs > 0L) delay(delayMs)
                connectionTracker.prepareDeviceWrite(address)
                initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        char,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    char.value = data
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
                if (initiated) break
                connectionTracker.onDeviceWriteCompleted(address, false)
            }
            if (initiated) {
                connectionTracker.awaitDeviceWrite(address, 550L)
            }
            initiated
        } catch (e: Exception) {
            Log.w(TAG, "Error sending to client connection $address: ${e.message}")
            connectionTracker.onDeviceWriteCompleted(address, false)
            connectionScope.launch {
                delay(CLEANUP_DELAY)
                connectionTracker.cleanupDeviceConnection(address)
            }
            false
        }
    }
    
    /**
     * Get debug information
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Packet Broadcaster Debug Info ===")
            appendLine("Broadcaster Scope Active: ${broadcasterScope.isActive}")
            appendLine("Actor Channel Closed: ${broadcasterActor.isClosedForSend}")
            appendLine("Connection Scope Active: ${connectionScope.isActive}")
        }
    }
    
    /**
     * Shutdown the broadcaster actor gracefully
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down BluetoothPacketBroadcaster actor")
        
        // Close the actor gracefully
        broadcasterActor.close()
        
        // Cancel the broadcaster scope
        broadcasterScope.cancel()
        
        Log.d(TAG, "BluetoothPacketBroadcaster shutdown complete")
    }
} 
