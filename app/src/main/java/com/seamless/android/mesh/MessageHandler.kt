@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.mesh

import android.util.Log
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.IdentityAnnouncement
import com.seamless.android.model.RoutedPacket
import com.seamless.android.protocol.BitchatPacket
import com.seamless.android.protocol.MessageType
import com.seamless.android.util.toHexString
import com.seamless.android.features.file.IncomingTransferAssembler
import kotlinx.coroutines.*
import java.util.*

/**
 * Handles processing of different message types
 * Extracted from BluetoothMeshService for better separation of concerns
 */
class MessageHandler(private val myPeerID: String, private val appContext: android.content.Context) {
    
    companion object {
        private const val TAG = "MessageHandler"
    }
    
    // PRODUCTION FIX: Transfer assembler for chunked media files
    private val transferAssembler = IncomingTransferAssembler(appContext)
    
    // Delegate for callbacks
    var delegate: MessageHandlerDelegate? = null
    
    // Reference to PacketProcessor for recursive packet handling
    var packetProcessor: PacketProcessor? = null
    
    // Coroutines
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Handle Noise encrypted transport message - SIMPLIFIED iOS-compatible version
     * Uses NoisePayloadType system exactly like iOS SimplifiedBluetoothService
     */
    suspend fun handleNoiseEncrypted(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "Processing Noise encrypted message from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own messages
        if (peerID == myPeerID) return
        
        // Check if this message is for us
        val recipientID = packet.recipientID?.toHexString()
        if (recipientID == null || !recipientID.equals(myPeerID, ignoreCase = true)) {
            Log.d(TAG, "🔐 Encrypted message not for me (for $recipientID, I am $myPeerID)")
            return
        }
        
        // PRODUCTION FIX: Wrap entire handler in try-catch to prevent crashes
        try {
            // Decrypt the message using the Noise service
            val decryptedData = delegate?.decryptFromPeer(packet.payload, peerID)
            if (decryptedData == null) {
                Log.w(TAG, "Failed to decrypt Noise message from $peerID - initiating handshake recovery")
                delegate?.initiateNoiseHandshake(peerID)
                return
            }
            
            if (decryptedData.isEmpty()) {
                Log.w(TAG, "Decrypted data is empty from $peerID")
                return
            }
            
            // NEW: Use NoisePayload system exactly like iOS
            val noisePayload = com.seamless.android.model.NoisePayload.decode(decryptedData)
            if (noisePayload == null) {
                Log.w(TAG, "Failed to parse NoisePayload from $peerID")
                return
            }
            
            Log.d(TAG, "🔓 Decrypted NoisePayload type ${noisePayload.type} from $peerID")
            
            when (noisePayload.type) {
                com.seamless.android.model.NoisePayloadType.PRIVATE_MESSAGE -> {
                    // Decode TLV private message exactly like iOS
                    val privateMessage = com.seamless.android.model.PrivateMessagePacket.decode(noisePayload.data)
                    if (privateMessage != null) {
                        Log.d(TAG, "🔓 Decrypted TLV PM from $peerID: ${privateMessage.content.take(30)}...")

                        // Handle favorite/unfavorite notifications embedded as PMs
                        val pmContent = privateMessage.content
                        if (pmContent.startsWith("[FAVORITED]") || pmContent.startsWith("[UNFAVORITED]")) {
                            handleFavoriteNotificationFromMesh(pmContent, peerID)
                            // Acknowledge delivery for UX parity
                            sendDeliveryAck(privateMessage.messageID, peerID)
                            return
                        }
                        
                        // Create BitchatMessage - preserve source packet timestamp
                        val message = BitchatMessage(
                            id = privateMessage.messageID,
                            sender = delegate?.getPeerNickname(peerID) ?: "Unknown",
                            content = privateMessage.content,
                            timestamp = java.util.Date(packet.timestamp.toLong()),
                            isRelay = false,
                            originalSender = null,
                            isPrivate = true,
                            recipientNickname = delegate?.getMyNickname(),
                            senderPeerID = peerID,
                            mentions = null, // TODO: Parse mentions if needed
                            vectorClock = privateMessage.vectorClock
                        )
                        
                        // Update local vector clock with received clock for causal ordering
                        if (privateMessage.vectorClock != null) {
                            delegate?.updateVectorClock(privateMessage.vectorClock)
                        }
                        
                        // Notify delegate
                        delegate?.onMessageReceived(message)
                        
                        // Send delivery ACK exactly like iOS
                        sendDeliveryAck(privateMessage.messageID, peerID)
                    }
                }
                
                com.seamless.android.model.NoisePayloadType.FILE_TRANSFER -> {
                    // Handle encrypted file transfer; generate unique message ID
                    // PRODUCTION FIX: Use IncomingTransferAssembler for chunked files (just like MESH_AUDIO/IMAGE)
                    Log.d(TAG, "📥 FILE_TRANSFER payload received from $peerID, size=${noisePayload.data.size} bytes")
                    
                    val file = try {
                        com.seamless.android.model.BitchatFilePacket.decode(noisePayload.data)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception decoding BitchatFilePacket from $peerID: ${e.message}")
                        null
                    }
                    
                    if (file != null) {
                        Log.d(TAG, "🔓 Decrypted encrypted file from $peerID: name='${file.fileName}', size=${file.fileSize}, mime='${file.mimeType}', chunk=${file.chunkIndex+1}/${file.totalChunks}")
                        
                        // PRODUCTION FIX: Use IncomingTransferAssembler for chunked files
                        // handleChunk returns the complete file path when all chunks are received, null otherwise
                        val savedPath = transferAssembler.handleChunk(file)
                        
                        if (savedPath != null) {
                            val uniqueMsgId = file.transferId.ifBlank { java.util.UUID.randomUUID().toString() }.uppercase()
                            Log.d(TAG, "📄 Saved complete incoming file to $savedPath (msgId=$uniqueMsgId)")
                            
                            val message = BitchatMessage(
                                id = uniqueMsgId,
                                sender = delegate?.getPeerNickname(peerID) ?: "Unknown",
                                content = savedPath,
                                type = com.seamless.android.features.file.FileUtils.messageTypeForMime(file.mimeType),
                                timestamp = java.util.Date(packet.timestamp.toLong()),
                                isRelay = false,
                                isPrivate = true,
                                recipientNickname = delegate?.getMyNickname(),
                                senderPeerID = peerID
                            )

                            delegate?.onMessageReceived(message)

                            // Send delivery ACK with deterministic message ID
                            sendDeliveryAck(uniqueMsgId, peerID)
                        }
                        // If savedPath is null, we're waiting for more chunks - no message yet
                    } else {
                        Log.e(TAG, "❌ Failed to decode Nostr file transfer from $peerID. Payload size: ${noisePayload.data.size}. Data: ${noisePayload.data.take(16).toByteArray().toHexString()}...")
                    }
                }
                
                com.seamless.android.model.NoisePayloadType.DELIVERED -> {
                    // Handle delivery ACK exactly like iOS
                    val messageID = String(noisePayload.data, Charsets.UTF_8)
                    Log.d(TAG, "📬 Delivery ACK received from $peerID for message $messageID")
                    
                    // Simplified: Call delegate with messageID and peerID directly
                    delegate?.onDeliveryAckReceived(messageID, peerID)
                }
                
                com.seamless.android.model.NoisePayloadType.READ_RECEIPT -> {
                    // Handle read receipt exactly like iOS
                    val messageID = String(noisePayload.data, Charsets.UTF_8)
                    Log.d(TAG, "👁️ Read receipt received from $peerID for message $messageID")
                    
                    // Simplified: Call delegate with messageID and peerID directly
                    delegate?.onReadReceiptReceived(messageID, peerID)
                }
                
                com.seamless.android.model.NoisePayloadType.TYPING_INDICATOR -> {
                    // Handle typing indicator
                    val isTyping = noisePayload.data.isNotEmpty() && noisePayload.data[0] == 1.toByte()
                    val nickname = delegate?.getPeerNickname(peerID) ?: "Unknown"
                    Log.d(TAG, "📝 Typing indicator from $peerID: isTyping=$isTyping")
                    
                    // Notify TypingStatusManager
                    com.seamless.android.ui.TypingStatusManager.shared.onTypingReceived(peerID, nickname, isTyping)
                }
                
                com.seamless.android.model.NoisePayloadType.MESH_MESSAGE -> {
                    // Handle Private Mesh message - route to PrivateMeshManager
                    // Payload format: [senderNickname]\0[content] OR [senderNickname]\0[messageId]\0[content]
                    val payloadStr = String(noisePayload.data, Charsets.UTF_8)
                    val parts = payloadStr.split("\u0000", limit = 3)
                    val senderNickname = if (parts.isNotEmpty()) parts[0] else delegate?.getPeerNickname(peerID) ?: "Unknown"
                    val msgId: String?
                    val content: String
                    if (parts.size == 3) {
                        msgId = parts[1].ifBlank { null }
                        content = parts[2]
                    } else {
                        msgId = null
                        content = if (parts.size >= 2) parts[1] else payloadStr
                    }
                    
                    Log.d(TAG, "🏠 Mesh message from $senderNickname: ${content.take(30)}...")
                    
                    // Route to PrivateMeshManager - NOT the regular chat
                    PrivateMeshManager.getInstance(appContext).onMessageReceived(
                        senderPeerID = peerID,
                        senderNickname = senderNickname,
                        content = content,
                        messageId = msgId
                    )
                }
                
                com.seamless.android.model.NoisePayloadType.MESH_AUDIO -> {
                    // Handle Private Mesh audio/voice note - route to PrivateMeshManager
                    // PRODUCTION FIX: Use IncomingTransferAssembler for chunked files
                    val file = try {
                        com.seamless.android.model.BitchatFilePacket.decode(noisePayload.data)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception decoding mesh audio from $peerID: ${e.message}")
                        null
                    }
                    
                    if (file != null) {
                        // Handle chunk and get complete file path (null if waiting for more chunks)
                        val savedPath = transferAssembler.handleChunk(file)
                        
                        if (savedPath != null) {
                            Log.d(TAG, "🎤 Received complete mesh voice note from $peerID: ${file.fileName}")
                            val senderNickname = delegate?.getPeerNickname(peerID) ?: "Unknown"
                            PrivateMeshManager.getInstance(appContext).onMediaReceived(
                                senderPeerID = peerID,
                                senderNickname = senderNickname,
                                filePath = savedPath,
                                type = PrivateMeshMessageType.AUDIO,
                                messageId = file.transferId.ifBlank { null }
                            )
                            file.transferId.takeIf { it.isNotBlank() }?.let {
                                sendDeliveryAck(it.uppercase(), peerID)
                            }
                        }
                    }
                }
                
                com.seamless.android.model.NoisePayloadType.MESH_IMAGE -> {
                    // Handle Private Mesh image - route to PrivateMeshManager
                    // PRODUCTION FIX: Use IncomingTransferAssembler for chunked files
                    val file = try {
                        com.seamless.android.model.BitchatFilePacket.decode(noisePayload.data)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception decoding mesh image from $peerID: ${e.message}")
                        null
                    }
                    
                    if (file != null) {
                        // Handle chunk and get complete file path (null if waiting for more chunks)
                        val savedPath = transferAssembler.handleChunk(file)
                        
                        if (savedPath != null) {
                            Log.d(TAG, "🖼️ Received complete mesh image from $peerID: ${file.fileName}")
                            val senderNickname = delegate?.getPeerNickname(peerID) ?: "Unknown"
                            PrivateMeshManager.getInstance(appContext).onMediaReceived(
                                senderPeerID = peerID,
                                senderNickname = senderNickname,
                                filePath = savedPath,
                                type = PrivateMeshMessageType.IMAGE,
                                messageId = file.transferId.ifBlank { null }
                            )
                            file.transferId.takeIf { it.isNotBlank() }?.let {
                                sendDeliveryAck(it.uppercase(), peerID)
                            }
                        }
                    }
                }
                
                com.seamless.android.model.NoisePayloadType.VOICE_STREAM -> {
                    // Walkie-Talkie removed
                }
                
                com.seamless.android.model.NoisePayloadType.BSP_SYNC -> {
                    // Route decrypted BSP_SYNC payload to BrambleSyncEngine
                    com.seamless.android.sync.BrambleSyncEngine.getInstance(appContext).handleIncomingData(peerID, noisePayload.data)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Noise encrypted message from $peerID: ${e.message}", e)
            // PRODUCTION FIX: Don't rethrow - silently fail to prevent crash
        } catch (t: Throwable) {
            // Catch any other throwable including OutOfMemoryError, etc.
            Log.e(TAG, "Fatal error processing Noise encrypted message from $peerID: ${t.message}", t)
        }
    }
    
    /**
     * Send delivery ACK for a received private message - exactly like iOS
     */
    private suspend fun sendDeliveryAck(messageID: String, senderPeerID: String) {
        try {
            // Create ACK payload: [type byte] + [message ID] - exactly like iOS
            val ackPayload = com.seamless.android.model.NoisePayload(
                type = com.seamless.android.model.NoisePayloadType.DELIVERED,
                data = messageID.toByteArray(Charsets.UTF_8)
            )
            
            // Encrypt the payload
            val encryptedPayload = delegate?.encryptForPeer(ackPayload.encode(), senderPeerID)
            if (encryptedPayload == null) {
                Log.w(TAG, "Failed to encrypt delivery ACK for $senderPeerID")
                return
            }
            
            // Create NOISE_ENCRYPTED packet exactly like iOS
                val packet = BitchatPacket(
                    version = 1u,
                    type = MessageType.NOISE_ENCRYPTED.value,
                    senderID = hexStringToByteArray(myPeerID),
                    recipientID = hexStringToByteArray(senderPeerID),
                    timestamp = System.currentTimeMillis().toULong(),
                    payload = encryptedPayload,
                    signature = null,
                    ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS // Same TTL as iOS messageTTL
                )
            
            delegate?.sendPacket(packet)
            Log.d(TAG, "📤 Sent delivery ACK to $senderPeerID for message $messageID")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send delivery ACK to $senderPeerID: ${e.message}")
        }
    }
    
    /**
     * Handle announce message with TLV decoding and signature verification - exactly like iOS
     */
    suspend fun handleAnnounce(routed: RoutedPacket): Boolean {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"

        if (peerID == myPeerID) return false

        // Ignore stale announcements older than STALE_PEER_TIMEOUT
        val now = System.currentTimeMillis()
        val age = now - packet.timestamp.toLong()
        if (kotlin.math.abs(age) > 86_400_000L) {
            Log.w(TAG, "Ignoring stale ANNOUNCE from ${peerID.take(8)} (age=${age}ms > ${com.seamless.android.util.AppConstants.Mesh.STALE_PEER_TIMEOUT_MS}ms)")
            return false
        }
        
        // Try to decode as iOS-compatible IdentityAnnouncement with TLV format
        val announcement = IdentityAnnouncement.decode(packet.payload)
        if (announcement == null) {
            Log.w(TAG, "Failed to decode announce from $peerID as iOS-compatible TLV format")
            return false
        }
        
        // Verify packet signature using the announced signing public key
        var verified = false
        if (packet.signature != null) {
            // PRODUCTION FIX: Safely handle null values instead of force-unwrap
            val signatureData = packet.signature
            val binaryDataForSigning = packet.toBinaryDataForSigning()
            if (signatureData != null && binaryDataForSigning != null) {
                verified = delegate?.verifyEd25519Signature(signatureData, binaryDataForSigning, announcement.signingPublicKey) ?: false
            }
            if (!verified) {
                Log.w(TAG, "⚠️ Signature verification for announce failed ${peerID.take(8)}")
            }
        }

        // Check for existing peer with different noise public key
        val existingPeer = delegate?.getPeerInfo(peerID)
        
        // PRODUCTION FIX: Use safe null check instead of force-unwrap
        val existingNoiseKey = existingPeer?.noisePublicKey
        if (existingPeer != null && existingNoiseKey != null && !existingNoiseKey.contentEquals(announcement.noisePublicKey)) {
            Log.w(TAG, "⚠️ Announce key mismatch for ${peerID.take(8)}... — keeping unverified")
            verified = false
        }

        // Require verified announce; ignore otherwise (no backward compatibility)
        if (!verified) {
            Log.w(TAG, "❌ Ignoring unverified announce from ${peerID.take(8)}...")
            return false
        }
        
        // Successfully decoded TLV format exactly like iOS
        Log.d(TAG, "✅ Verified announce from $peerID: nickname=${announcement.nickname}, " +
                "noisePublicKey=${announcement.noisePublicKey.joinToString("") { "%02x".format(it) }.take(16)}..., " +
                "signingPublicKey=${announcement.signingPublicKey.joinToString("") { "%02x".format(it) }.take(16)}...")
        
        // Extract nickname and public keys from TLV data
        val nickname = announcement.nickname
        val noisePublicKey = announcement.noisePublicKey
        val signingPublicKey = announcement.signingPublicKey
        
        // Update peer info with verification status through new method
        val isFirstAnnounce = delegate?.updatePeerInfo(
            peerID = peerID,
            nickname = nickname,
            noisePublicKey = noisePublicKey,
            signingPublicKey = signingPublicKey,
            isVerified = true
        ) ?: false

        // Update peer ID binding with noise public key for identity management
        delegate?.updatePeerIDBinding(
            newPeerID = peerID,
            nickname = nickname,
            publicKey = noisePublicKey,
            previousPeerID = null
        )
        
        Log.d(TAG, "✅ Processed verified TLV announce: stored identity for $peerID")
        return isFirstAnnounce
    }
    
    /**
     * Handle Noise handshake - SIMPLIFIED iOS-compatible version
     * Single handshake type (0x10) with response determined by payload analysis
     */
    suspend fun handleNoiseHandshake(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "Processing Noise handshake from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own handshake messages
        if (peerID == myPeerID) return
        
        // Check if handshake is addressed to us
        val recipientID = packet.recipientID?.toHexString()
        if (recipientID == null || !recipientID.equals(myPeerID, ignoreCase = true)) {
            Log.d(TAG, "Handshake not for me (for $recipientID, I am $myPeerID)")
            return
        }
        
        try {
            // Process handshake message through delegate (simplified approach)
            val response = delegate?.processNoiseHandshakeMessage(packet.payload, peerID)
            
            if (response != null) {
                Log.d(TAG, "Generated handshake response for $peerID (${response.size} bytes)")
                
                // Send response using same packet type (simplified iOS approach)
                val responsePacket = BitchatPacket(
                    version = 1u,
                    type = MessageType.NOISE_HANDSHAKE.value,
                    senderID = hexStringToByteArray(myPeerID),
                    recipientID = hexStringToByteArray(peerID),
                    timestamp = System.currentTimeMillis().toULong(),
                    payload = response,
                    signature = null,
                    ttl = com.seamless.android.util.AppConstants.MESSAGE_TTL_HOPS // Same TTL as iOS
                )
                
                delegate?.sendPacket(responsePacket)
                Log.d(TAG, "📤 Sent handshake response to $peerID")
            }
            
            // Check if session is now established
            val hasSession = delegate?.hasNoiseSession(peerID) ?: false
            if (hasSession) {
                Log.d(TAG, "✅ Noise session established with $peerID")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process Noise handshake from $peerID: ${e.message}")
        }
    }
    
    /**
     * Handle broadcast or private message
     */
    suspend fun handleMessage(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        if (peerID == myPeerID) return
        val senderNickname = delegate?.getPeerNickname(peerID)
        if (senderNickname != null) {
            Log.d(TAG, "Received message from $senderNickname")
            delegate?.updatePeerNickname(peerID, senderNickname)
        }
        
        val recipientID = packet.recipientID?.takeIf { !it.contentEquals(delegate?.getBroadcastRecipient()) }
        
        if (recipientID == null) {
            // BROADCAST MESSAGE
            handleBroadcastMessage(routed)
        } else if (recipientID.toHexString() == myPeerID) {
            // PRIVATE MESSAGE FOR US
            handlePrivateMessage(packet, peerID)
        }
        // Message relay is now handled by centralized PacketRelayManager
    }
    
    /**
     * Handle broadcast message with verification enforcement
     */
    private suspend fun handleBroadcastMessage(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        // Enforce: accept public messages from verified peers or packets carrying an Ed25519 signature,
        // and proactively request an ANNOUNCE if we missed the peer's initial announcement.
        val peerInfo = delegate?.getPeerInfo(peerID)
        if (peerInfo?.signingPublicKey != null && packet.signature != null && delegate?.verifySignature(packet, peerID) == false) {
            Log.w(TAG, "🚫 Dropping public message with invalid Ed25519 signature from ${peerID.take(8)}...")
            return
        }
        if (peerInfo == null || !peerInfo.isVerifiedNickname) {
            delegate?.sendAnnouncementToPeer(peerID)
            if (packet.signature == null) {
                Log.w(TAG, "🚫 Dropping unsigned public message from unverified/unknown peer ${peerID.take(8)}...")
                return
            }
            Log.d(TAG, "⚠️ Accepting signed public packet from ${peerID.take(8)} while requesting ANNOUNCE")
        }
        
        try {
            // Try file packet first (voice, image, etc.) and log outcome for FILE_TRANSFER
            // PRODUCTION FIX: Use IncomingTransferAssembler for chunked files
            val isFileTransfer = com.seamless.android.protocol.MessageType.fromValue(packet.type) == com.seamless.android.protocol.MessageType.FILE_TRANSFER
            val file = com.seamless.android.model.BitchatFilePacket.decode(packet.payload)
            if (file != null) {
                if (isFileTransfer) {
                    Log.d(TAG, "📥 FILE_TRANSFER decode success (broadcast): name='${file.fileName}', size=${file.fileSize}, mime='${file.mimeType}', chunk=${file.chunkIndex+1}/${file.totalChunks}, from=${peerID.take(8)}")
                }
                // Use handleChunk to properly assemble multi-chunk files
                val savedPath = transferAssembler.handleChunk(file)
                if (savedPath != null) {
                    val message = BitchatMessage(
                        id = file.transferId.ifBlank { java.util.UUID.randomUUID().toString() }.uppercase(),
                        sender = delegate?.getPeerNickname(peerID) ?: "unknown",
                        content = savedPath,
                        type = com.seamless.android.features.file.FileUtils.messageTypeForMime(file.mimeType),
                        senderPeerID = peerID,
                        timestamp = Date(packet.timestamp.toLong())
                    )
                    Log.d(TAG, "📄 Saved complete incoming file to $savedPath")
                    delegate?.onMessageReceived(message)
                }
                // If savedPath is null, we're waiting for more chunks
                return
            } else if (isFileTransfer) {
                Log.w(TAG, "⚠️ FILE_TRANSFER decode failed (broadcast) from ${peerID.take(8)} payloadSize=${packet.payload.size}")
            }

            // Try to parse as binary-encoded BitchatMessage first (has TLV structure with message ID, content, vector clock)
            val parsedMessage = BitchatMessage.fromBinaryPayload(packet.payload)
            if (parsedMessage != null) {
                if ((peerInfo == null || !peerInfo.isVerifiedNickname) &&
                    packet.signature != null &&
                    parsedMessage.sender.isNotBlank() &&
                    parsedMessage.sender != peerID
                ) {
                    delegate?.addOrUpdatePeer(peerID, parsedMessage.sender)
                }
                // Successfully parsed binary message format
                val message = parsedMessage.copy(
                    sender = delegate?.getPeerNickname(peerID) ?: parsedMessage.sender,
                    senderPeerID = peerID,
                    timestamp = Date(packet.timestamp.toLong())
                )
                Log.d(TAG, "📨 Parsed binary message: '${message.content.take(50)}...' from ${peerID.take(8)}")
                delegate?.onMessageReceived(message)
                return
            }
            
            // Final fallback: treat as plain text (for legacy/simple messages)
            val textContent = String(packet.payload, Charsets.UTF_8)
            
            // Sanity check: if it looks like corrupted binary data, skip it
            if (textContent.contains("\u0000") || textContent.length < 2) {
                Log.w(TAG, "⚠️ Skipping corrupted/binary message from ${peerID.take(8)}")
                return
            }
            
            val message = BitchatMessage(
                sender = delegate?.getPeerNickname(peerID) ?: "unknown",
                content = textContent,
                senderPeerID = peerID,
                timestamp = Date(packet.timestamp.toLong())
            )
            delegate?.onMessageReceived(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process broadcast message from $peerID: ${e.message}", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error processing broadcast message from $peerID: ${t.message}", t)
        }
    }
    
    /**
     * Handle (decrypted) private message addressed to us
     */
    private suspend fun handlePrivateMessage(packet: BitchatPacket, peerID: String) {
        try {
            // PRODUCTION FIX: Safely verify signature without force-unwrap
            if (packet.signature != null && delegate?.verifySignature(packet, peerID) != true) {
                Log.w(TAG, "Invalid signature for private message from $peerID")
                return
            }

            // Try file packet first (voice, image, etc.) and log outcome for FILE_TRANSFER
            // PRODUCTION FIX: Use IncomingTransferAssembler for chunked files
            val isFileTransfer = com.seamless.android.protocol.MessageType.fromValue(packet.type) == com.seamless.android.protocol.MessageType.FILE_TRANSFER
            val file = com.seamless.android.model.BitchatFilePacket.decode(packet.payload)
            if (file != null) {
                if (isFileTransfer) {
                    Log.d(TAG, "📥 FILE_TRANSFER decode success (private): name='${file.fileName}', size=${file.fileSize}, mime='${file.mimeType}', chunk=${file.chunkIndex+1}/${file.totalChunks}, from=${peerID.take(8)}")
                }
                // Use handleChunk to properly assemble multi-chunk files
                val savedPath = transferAssembler.handleChunk(file)
                if (savedPath != null) {
                    val message = BitchatMessage(
                        id = file.transferId.ifBlank { java.util.UUID.randomUUID().toString() }.uppercase(),
                        sender = delegate?.getPeerNickname(peerID) ?: "unknown",
                        content = savedPath,
                        type = com.seamless.android.features.file.FileUtils.messageTypeForMime(file.mimeType),
                        senderPeerID = peerID,
                        timestamp = Date(packet.timestamp.toLong()),
                        isPrivate = true,
                        recipientNickname = delegate?.getMyNickname()
                    )
                    Log.d(TAG, "📄 Saved complete incoming file to $savedPath")
                    delegate?.onMessageReceived(message)
                }
                // If savedPath is null, we're waiting for more chunks
                return
            } else if (isFileTransfer) {
                Log.w(TAG, "⚠️ FILE_TRANSFER decode failed (private) from ${peerID.take(8)} payloadSize=${packet.payload.size}")
            }

            // Try to parse as binary-encoded BitchatMessage first
            val parsedMessage = BitchatMessage.fromBinaryPayload(packet.payload)
            if (parsedMessage != null) {
                val message = parsedMessage.copy(
                    sender = delegate?.getPeerNickname(peerID) ?: parsedMessage.sender,
                    senderPeerID = peerID,
                    timestamp = Date(packet.timestamp.toLong()),
                    isPrivate = true,
                    recipientNickname = delegate?.getMyNickname()
                )
                Log.d(TAG, "📨 Parsed private binary message: '${message.content.take(50)}...' from ${peerID.take(8)}")
                delegate?.onMessageReceived(message)
                return
            }
            
            // Final fallback: plain text
            val textContent = String(packet.payload, Charsets.UTF_8)
            
            // Sanity check: skip corrupted binary data
            if (textContent.contains("\u0000") || textContent.length < 2) {
                Log.w(TAG, "⚠️ Skipping corrupted/binary private message from ${peerID.take(8)}")
                return
            }
            
            val message = BitchatMessage(
                sender = delegate?.getPeerNickname(peerID) ?: "unknown",
                content = textContent,
                senderPeerID = peerID,
                timestamp = Date(packet.timestamp.toLong()),
                isPrivate = true,
                recipientNickname = delegate?.getMyNickname()
            )
            delegate?.onMessageReceived(message)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process private message from $peerID: ${e.message}", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error processing private message from $peerID: ${t.message}", t)
        }
    }

    
    
    /**
     * Handle leave message
     */
    suspend fun handleLeave(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        val content = String(packet.payload, Charsets.UTF_8)
        
        val peerInfo = delegate?.getPeerInfo(peerID)
        if (peerInfo?.signingPublicKey != null && packet.signature != null && delegate?.verifySignature(packet, peerID) == false) {
            Log.w(TAG, "🚫 Ignoring forged LEAVE packet for ${peerID.take(8)}...")
            return
        }
        if (content.startsWith("#")) {
            // Channel leave
            delegate?.onChannelLeave(content, peerID)
        } else {
            // Peer disconnect
            delegate?.removePeer(peerID)
        }
        
        // Leave message relay is now handled by centralized PacketRelayManager
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Message Handler Debug Info ===")
            appendLine("Handler Scope Active: ${handlerScope.isActive}")
            appendLine("My Peer ID: $myPeerID")
        }
    }
    
    /**
     * Convert hex string peer ID to binary data (8 bytes) - same as iOS implementation
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val result = ByteArray(8) { 0 } // Initialize with zeros, exactly 8 bytes
        var tempID = hexString
        var index = 0
        
        while (tempID.length >= 2 && index < 8) {
            val hexByte = tempID.substring(0, 2)
            val byte = hexByte.toIntOrNull(16)?.toByte()
            if (byte != null) {
                result[index] = byte
            }
            tempID = tempID.substring(2)
            index++
        }
        
        return result
    }

    /**
     * Shutdown the handler
     */
    fun shutdown() {
        handlerScope.cancel()
    }
    
    // ... (rest of existing methods)


    /**
     * Handle favorite/unfavorite notification received over mesh as a private message.
     * Content format: "[FAVORITED]:npub..." or "[UNFAVORITED]:npub..."
     */
    private fun handleFavoriteNotificationFromMesh(content: String, fromPeerID: String) {
        try {
            val isFavorite = content.startsWith("[FAVORITED]")
            val npub = content.substringAfter(":", "").trim().takeIf { it.startsWith("npub1") }

            // Update mutual favorite status in persistence
            // Resolve full Noise key if available via delegate peer info
            val peerInfo = delegate?.getPeerInfo(fromPeerID)
            val noiseKey = peerInfo?.noisePublicKey
            if (noiseKey != null) {
                com.seamless.android.favorites.FavoritesPersistenceService.shared.updatePeerFavoritedUs(noiseKey, isFavorite)
                if (npub != null) {
                    // Index by noise key and current mesh peerID for fast Nostr routing
                    com.seamless.android.favorites.FavoritesPersistenceService.shared.updateNostrPublicKey(noiseKey, npub)
                    com.seamless.android.favorites.FavoritesPersistenceService.shared.updateNostrPublicKeyForPeerID(fromPeerID, npub)
                }

                // Determine iOS-style guidance text
                val rel = com.seamless.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKey)
                val guidance = if (isFavorite) {
                    if (rel?.isFavorite == true) {
                        " — mutual! You can continue DMs via Nostr when out of mesh."
                    } else {
                        " — favorite back to continue DMs later."
                    }
                } else {
                    ". DMs over Nostr will pause unless you both favorite again."
                }

                // Emit system message via delegate callback
                val action = if (isFavorite) "favorited" else "unfavorited"
                val sys = com.seamless.android.model.BitchatMessage(
                    sender = "system",
                    content = "${peerInfo.nickname} $action you$guidance",
                    timestamp = java.util.Date(),
                    isRelay = false
                )
                delegate?.onMessageReceived(sys)
            }
        } catch (_: Exception) {
            // Best-effort; ignore errors
        }
    }
}

/**
 * Delegate interface for message handler callbacks
 */
interface MessageHandlerDelegate {
    // Peer management
    fun addOrUpdatePeer(peerID: String, nickname: String): Boolean
    fun removePeer(peerID: String)
    fun updatePeerNickname(peerID: String, nickname: String)
    fun getPeerNickname(peerID: String): String?
    fun getNetworkSize(): Int
    fun getMyNickname(): String?
    fun getPeerInfo(peerID: String): PeerInfo?
    fun updatePeerInfo(peerID: String, nickname: String, noisePublicKey: ByteArray, signingPublicKey: ByteArray, isVerified: Boolean): Boolean
    
    // Packet operations
    fun sendPacket(packet: BitchatPacket)
    fun relayPacket(routed: RoutedPacket)
    fun getBroadcastRecipient(): ByteArray
    fun sendAnnouncementToPeer(peerID: String)
    
    // Cryptographic operations
    fun verifySignature(packet: BitchatPacket, peerID: String): Boolean
    fun encryptForPeer(data: ByteArray, recipientPeerID: String): ByteArray?
    fun decryptFromPeer(encryptedData: ByteArray, senderPeerID: String): ByteArray?
    fun verifyEd25519Signature(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean
    
    // Noise protocol operations
    fun hasNoiseSession(peerID: String): Boolean
    fun initiateNoiseHandshake(peerID: String)
    fun processNoiseHandshakeMessage(payload: ByteArray, peerID: String): ByteArray?
    fun updatePeerIDBinding(newPeerID: String, nickname: String,
                           publicKey: ByteArray, previousPeerID: String?)
    
    // Message operations
    fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String?

    // Vector Clock sync
    fun updateVectorClock(clock: Map<String, Long>)
    
    // Callbacks
    fun onMessageReceived(message: BitchatMessage)
    fun onChannelLeave(channel: String, fromPeer: String)
    fun onDeliveryAckReceived(messageID: String, peerID: String)
    fun onReadReceiptReceived(messageID: String, peerID: String)
}
