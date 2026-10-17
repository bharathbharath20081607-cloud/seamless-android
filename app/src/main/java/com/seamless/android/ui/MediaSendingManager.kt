@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui

import android.util.Log
import com.seamless.android.mesh.BluetoothMeshService
import com.seamless.android.model.BitchatFilePacket
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.BitchatMessageType
import java.util.Date
import java.security.MessageDigest

/**
 * Handles media file sending operations (voice notes, images, generic files)
 * Separated from ChatViewModel for better separation of concerns
 */
class MediaSendingManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private var meshService: BluetoothMeshService?
) {
    companion object {
        private const val TAG = "MediaSendingManager"
        private const val MAX_FILE_SIZE = com.seamless.android.util.AppConstants.Media.MAX_FILE_SIZE_BYTES // 50MB limit
    }
    
    // Update the mesh service when it becomes available
    fun updateMeshService(service: BluetoothMeshService?) {
        meshService = service
    }

    // Track in-flight transfer progress: transferId -> messageId and reverse
    private val transferMessageMap = mutableMapOf<String, String>()
    private val messageTransferMap = mutableMapOf<String, String>()

    /**
     * Send a voice note (audio file)
     */
    fun sendVoiceNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        try {
            val file = java.io.File(filePath)
            if (!file.exists() || file.length() <= 0L) {
                Log.e(TAG, "❌ Voice note file does not exist or is empty: $filePath")
                return
            }
            Log.d(TAG, "📁 Voice note exists: size=${file.length()} bytes, name=${file.name}")
            
            if (file.length() > MAX_FILE_SIZE) {
                Log.e(TAG, "❌ File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
                return
            }

            val detectedMime = com.seamless.android.features.file.FileUtils.getMimeTypeFromExtension(file.name)
            val mimeType = if (detectedMime.startsWith("audio/")) detectedMime else "audio/mp4"

            val filePacket = BitchatFilePacket(
                fileName = file.name,
                fileSize = file.length(),
                mimeType = mimeType,
                content = file.readBytes(),
                transferId = java.util.UUID.randomUUID().toString()
            )

            if (toPeerIDOrNull != null) {
                sendPrivateFile(toPeerIDOrNull, filePacket, filePath, BitchatMessageType.Audio)
            } else {
                sendPublicFile(channelOrNull, filePacket, filePath, BitchatMessageType.Audio)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send voice note: ${e.message}")
        }
    }

    /**
     * Send an image file
     */
    fun sendImageNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        try {
            Log.d(TAG, "🔄 Starting image send: $filePath")
            val file = java.io.File(filePath)
            if (!file.exists() || file.length() <= 0L) {
                Log.e(TAG, "❌ Image file does not exist or is empty: $filePath")
                return
            }
            Log.d(TAG, "📁 File exists: size=${file.length()} bytes, name=${file.name}")
            
            if (file.length() > MAX_FILE_SIZE) {
                Log.e(TAG, "❌ File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
                return
            }

            val detectedMime = com.seamless.android.features.file.FileUtils.getMimeTypeFromExtension(file.name)
            val mimeType = if (detectedMime.startsWith("image/")) detectedMime else "image/jpeg"

            val filePacket = BitchatFilePacket(
                fileName = file.name,
                fileSize = file.length(),
                mimeType = mimeType,
                content = file.readBytes(),
                transferId = java.util.UUID.randomUUID().toString()
            )

            if (toPeerIDOrNull != null) {
                sendPrivateFile(toPeerIDOrNull, filePacket, filePath, BitchatMessageType.Image)
            } else {
                sendPublicFile(channelOrNull, filePacket, filePath, BitchatMessageType.Image)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Image send failed completely", e)
            Log.e(TAG, "❌ Image path: $filePath")
            Log.e(TAG, "❌ Error details: ${e.message}")
            Log.e(TAG, "❌ Error type: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Send a generic file
     */
    fun sendFileNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        try {
            Log.d(TAG, "🔄 Starting file send: $filePath")
            val file = java.io.File(filePath)
            if (!file.exists() || file.length() <= 0L) {
                Log.e(TAG, "❌ File does not exist or is empty: $filePath")
                return
            }
            Log.d(TAG, "📁 File exists: size=${file.length()} bytes, name=${file.name}")
            
            if (file.length() > MAX_FILE_SIZE) {
                Log.e(TAG, "❌ File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
                return
            }

            // Use the real MIME type based on extension; fallback to octet-stream
            val mimeType = try { 
                com.seamless.android.features.file.FileUtils.getMimeTypeFromExtension(file.name) 
            } catch (_: Exception) { 
                "application/octet-stream" 
            }
            Log.d(TAG, "🏷️ MIME type: $mimeType")

            // Try to preserve the original file name if our copier prefixed it earlier
            val originalName = run {
                val name = file.name
                val base = name.substringBeforeLast('.')
                val ext = name.substringAfterLast('.', "").let { if (it.isNotBlank()) ".${it}" else "" }
                val stripped = Regex("^send_\\d+_(.+)$").matchEntire(base)?.groupValues?.getOrNull(1) ?: base
                stripped + ext
            }
            Log.d(TAG, "📝 Original filename: $originalName")

            val filePacket = BitchatFilePacket(
                fileName = originalName,
                fileSize = file.length(),
                mimeType = mimeType,
                content = file.readBytes(),
                transferId = java.util.UUID.randomUUID().toString()
            )
            Log.d(TAG, "📦 Created file packet successfully")

            val messageType = when {
                mimeType.lowercase().startsWith("image/") -> BitchatMessageType.Image
                mimeType.lowercase().startsWith("audio/") -> BitchatMessageType.Audio
                else -> BitchatMessageType.File
            }

            if (toPeerIDOrNull != null) {
                sendPrivateFile(toPeerIDOrNull, filePacket, filePath, messageType)
            } else {
                sendPublicFile(channelOrNull, filePacket, filePath, messageType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: File send failed completely", e)
            Log.e(TAG, "❌ File path: $filePath")
            Log.e(TAG, "❌ Error details: ${e.message}")
            Log.e(TAG, "❌ Error type: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Send a file privately (encrypted)
     */
    private fun sendPrivateFile(
        toPeerID: String,
        filePacket: BitchatFilePacket,
        filePath: String,
        messageType: BitchatMessageType
    ) {
        val payload = filePacket.encode()
        if (payload == null) {
            Log.e(TAG, "❌ Failed to encode file packet for private send")
            return
        }
        Log.d(TAG, "🔒 Encoded private packet: ${payload.size} bytes")

        val payloadHashId = sha256Hex(payload)
        val canonicalTransferId = filePacket.transferId.ifBlank { payloadHashId }
        val contentHash = sha256Hex(filePacket.content)

        Log.d(TAG, "📤 FILE_TRANSFER send (private): name='${filePacket.fileName}', size=${filePacket.fileSize}, mime='${filePacket.mimeType}', sha256=$contentHash, to=${toPeerID.take(8)} transferId=${canonicalTransferId.take(16)}…")

        val msg = BitchatMessage(
            id = java.util.UUID.randomUUID().toString().uppercase(), // Generate unique ID for each message
            sender = state.getNicknameValue() ?: "me",
            content = filePath,
            type = messageType,
            timestamp = Date(),
            isRelay = false,
            isPrivate = true,
            recipientNickname = try { meshService?.getPeerNicknames()?.get(toPeerID) } catch (_: Exception) { null },
            senderPeerID = meshService?.myPeerID ?: ""
        )
        
        messageManager.addPrivateMessage(toPeerID, msg)
        
        synchronized(transferMessageMap) {
            transferMessageMap[canonicalTransferId] = msg.id
            transferMessageMap[payloadHashId] = msg.id
            messageTransferMap[msg.id] = canonicalTransferId
        }
        
        // Seed progress so delivery icons render for media
        messageManager.updateMessageDeliveryStatus(
            msg.id,
            com.seamless.android.model.DeliveryStatus.PartiallyDelivered(0, 100)
        )
        
        // PRODUCTION FIX: Try Bluetooth first, but also send via Nostr for long-distance
        // Check if peer is on local mesh vs long-distance
        val isPeerOnMesh = meshService?.getPeerNicknames()?.containsKey(toPeerID) == true
        
        if (isPeerOnMesh) {
            Log.d(TAG, "📤 Calling meshService.sendFilePrivate to $toPeerID (local mesh)")
            meshService?.sendFilePrivate(toPeerID, filePacket.copy(transferId = canonicalTransferId))
        } else {
            // Long-distance: use Nostr transport
            Log.d(TAG, "📤 Sending file via Nostr to $toPeerID (long-distance)")
            try {
                val context = com.seamless.android.SeamlessApplication.instance
                com.seamless.android.nostr.NostrTransport.getInstance(context).sendFilePrivate(filePacket, toPeerID)
                messageManager.updateMessageDeliveryStatus(
                    msg.id,
                    com.seamless.android.model.DeliveryStatus.Delivered(to = toPeerID, at = Date())
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Nostr file send failed: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ File send completed successfully")
    }

    /**
     * Send a file publicly (broadcast or channel)
     */
    private fun sendPublicFile(
        channelOrNull: String?,
        filePacket: BitchatFilePacket,
        filePath: String,
        messageType: BitchatMessageType
    ) {
        val payload = filePacket.encode()
        if (payload == null) {
            Log.e(TAG, "❌ Failed to encode file packet for broadcast send")
            return
        }
        Log.d(TAG, "🔓 Encoded broadcast packet: ${payload.size} bytes")
        
        val payloadHashId = sha256Hex(payload)
        val canonicalTransferId = filePacket.transferId.ifBlank { payloadHashId }
        val contentHash = sha256Hex(filePacket.content)
        
        Log.d(TAG, "📤 FILE_TRANSFER send (broadcast): name='${filePacket.fileName}', size=${filePacket.fileSize}, mime='${filePacket.mimeType}', sha256=$contentHash, transferId=${canonicalTransferId.take(16)}…")

        val myPeerID = meshService?.myPeerID ?: ""
        val message = BitchatMessage(
            id = java.util.UUID.randomUUID().toString().uppercase(), // Generate unique ID for each message
            sender = state.getNicknameValue() ?: myPeerID,
            content = filePath,
            type = messageType,
            timestamp = Date(),
            isRelay = false,
            senderPeerID = myPeerID,
            channel = channelOrNull
        )
        
        if (!channelOrNull.isNullOrBlank()) {
            channelManager.addChannelMessage(channelOrNull, message, myPeerID)
        } else {
            messageManager.addMessage(message)
        }
        
        synchronized(transferMessageMap) {
            transferMessageMap[canonicalTransferId] = message.id
            transferMessageMap[payloadHashId] = message.id
            messageTransferMap[message.id] = canonicalTransferId
        }
        
        // Seed progress so animations start immediately
        messageManager.updateMessageDeliveryStatus(
            message.id,
            com.seamless.android.model.DeliveryStatus.PartiallyDelivered(0, 100)
        )
        
        Log.d(TAG, "📤 Calling meshService.sendFileBroadcast")
        meshService?.sendFileBroadcast(filePacket.copy(transferId = canonicalTransferId))
        Log.d(TAG, "✅ File broadcast completed successfully")
    }

    /**
     * Cancel a media transfer by message ID
     */
    fun cancelMediaSend(messageId: String) {
        val transferId = synchronized(transferMessageMap) { messageTransferMap[messageId] }
        if (transferId != null) {
            val cancelled = meshService?.cancelFileTransfer(transferId) ?: false
            if (cancelled) {
                // Try to remove cached local file for this message (if any)
                runCatching { findMessagePathById(messageId)?.let { java.io.File(it).delete() } }

                // Remove the message from chat upon explicit cancel
                messageManager.removeMessageById(messageId)
                synchronized(transferMessageMap) {
                    transferMessageMap.remove(transferId)
                    messageTransferMap.remove(messageId)
                }
            }
        }
    }

    private fun findMessagePathById(messageId: String): String? {
        // Search main timeline
        state.getMessagesValue().firstOrNull { it.id == messageId }?.content?.let { return it }
        // Search private chats
        state.getPrivateChatsValue().values.forEach { list ->
            list.firstOrNull { it.id == messageId }?.content?.let { return it }
        }
        // Search channel messages
        state.getChannelMessagesValue().values.forEach { list ->
            list.firstOrNull { it.id == messageId }?.content?.let { return it }
        }
        return null
    }

    /**
     * Update progress for a transfer
     */
    fun updateTransferProgress(transferId: String, messageId: String) {
        synchronized(transferMessageMap) {
            transferMessageMap[transferId] = messageId
            messageTransferMap[messageId] = transferId
        }
    }

    /**
     * Handle transfer progress events
     */
    fun handleTransferProgressEvent(evt: com.seamless.android.mesh.TransferProgressEvent) {
        val msgId = synchronized(transferMessageMap) { transferMessageMap[evt.transferId] }
        if (msgId != null) {
            if (evt.completed) {
                messageManager.updateMessageDeliveryStatus(
                    msgId,
                    com.seamless.android.model.DeliveryStatus.Delivered(to = "mesh", at = java.util.Date())
                )
                synchronized(transferMessageMap) {
                    val msgIdRemoved = transferMessageMap.remove(evt.transferId)
                    if (msgIdRemoved != null) messageTransferMap.remove(msgIdRemoved)
                }
            } else {
                messageManager.updateMessageDeliveryStatus(
                    msgId,
                    com.seamless.android.model.DeliveryStatus.PartiallyDelivered(evt.sent, evt.total)
                )
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        bytes.size.toString(16)
    }
}
