@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.nostr

import android.app.Application
import android.util.Log
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.DeliveryStatus
import com.seamless.android.protocol.BitchatPacket
import com.seamless.android.services.SeenMessageStore
import com.seamless.android.ui.ChatState
import com.seamless.android.ui.MeshDelegateHandler
import com.seamless.android.ui.PrivateChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class NostrDirectMessageHandler(
    private val application: Application,
    private val state: ChatState,
    private val privateChatManager: PrivateChatManager,
    private val meshDelegateHandler: MeshDelegateHandler,
    private val scope: CoroutineScope,
    private val repo: GeohashRepository,
    private val dataManager: com.seamless.android.ui.DataManager
) {
    companion object { private const val TAG = "NostrDirectMessageHandler" }

    private val seenStore by lazy { SeenMessageStore.getInstance(application) }

    // File reassembly map
    private val reassemblyMap = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<Int, com.seamless.android.model.BitchatFilePacket>>()

    // Simple event deduplication
    private val processedIds = ArrayDeque<String>()
    private val seen = HashSet<String>()
    private val max = 2000

    private fun dedupe(id: String): Boolean {
        if (seen.contains(id)) return true
        seen.add(id)
        processedIds.addLast(id)
        if (processedIds.size > max) {
            val old = processedIds.removeFirst()
            seen.remove(old)
        }
        return false
    }

    fun onGiftWrap(giftWrap: NostrEvent, geohash: String, identity: NostrIdentity) {
        scope.launch(Dispatchers.Default) {
            try {
                if (dedupe(giftWrap.id)) return@launch

                val messageAge = System.currentTimeMillis() / 1000 - giftWrap.createdAt
                if (messageAge > 173700) return@launch // 48 hours + 15 mins

                val decryptResult = NostrProtocol.decryptPrivateMessage(giftWrap, identity)
                if (decryptResult == null) {
                    Log.w(TAG, "Failed to decrypt Nostr message")
                    return@launch
                }

                val (content, senderPubkey, rumorTimestamp) = decryptResult

                // If sender is blocked for geohash contexts, drop any events from this pubkey
                // Applies to both geohash DMs (geohash != "") and account DMs (geohash == "")
                if (dataManager.isGeohashUserBlocked(senderPubkey)) return@launch
                if (!content.startsWith("bitchat1:")) return@launch

                val base64Content = content.removePrefix("bitchat1:")
                val packetData = base64URLDecode(base64Content) ?: return@launch
                val packet = BitchatPacket.fromBinaryData(packetData) ?: return@launch

                if (packet.type != com.seamless.android.protocol.MessageType.NOISE_ENCRYPTED.value) return@launch

                val noisePayload = com.seamless.android.model.NoisePayload.decode(packet.payload) ?: return@launch
                val messageTimestamp = Date(giftWrap.createdAt * 1000L)
                val convKey = "nostr_${senderPubkey.take(16)}"
                repo.putNostrKeyMapping(convKey, senderPubkey)
                com.seamless.android.nostr.GeohashAliasRegistry.put(convKey, senderPubkey)
                if (geohash.isNotEmpty()) {
                    // Remember which geohash this conversation belongs to so we can subscribe on-demand
                    repo.setConversationGeohash(convKey, geohash)
                    GeohashConversationRegistry.set(convKey, geohash)
                }

                // Ensure sender appears in geohash people list even if they haven't posted publicly yet
                if (geohash.isNotEmpty()) {
                    // Cache a best-effort nickname and mark as participant
                    val cached = repo.getCachedNickname(senderPubkey)
                    if (cached == null) {
                        val base = repo.displayNameForNostrPubkeyUI(senderPubkey).substringBefore("#")
                        repo.cacheNickname(senderPubkey, base)
                    }
                    repo.updateParticipant(geohash, senderPubkey, messageTimestamp)
                }

                val senderNickname = repo.displayNameForNostrPubkeyUI(senderPubkey)

                processNoisePayload(noisePayload, convKey, senderNickname, messageTimestamp, senderPubkey, identity)

            } catch (e: Exception) {
                Log.e(TAG, "onGiftWrap error: ${e.message}")
            }
        }
    }

    private suspend fun processNoisePayload(
        payload: com.seamless.android.model.NoisePayload,
        convKey: String,
        senderNickname: String,
        timestamp: Date,
        senderPubkey: String,
        recipientIdentity: NostrIdentity
    ) {
        when (payload.type) {
            com.seamless.android.model.NoisePayloadType.PRIVATE_MESSAGE -> {
                val pm = com.seamless.android.model.PrivateMessagePacket.decode(payload.data) ?: return
                val existingMessages = state.getPrivateChatsValue()[convKey] ?: emptyList()
                if (existingMessages.any { it.id == pm.messageID }) return

                val message = BitchatMessage(
                    id = pm.messageID,
                    sender = senderNickname,
                    content = pm.content,
                    timestamp = timestamp,
                    isRelay = false,
                    isPrivate = true,
                    recipientNickname = state.getNicknameValue(),
                    senderPeerID = convKey,
                    deliveryStatus = DeliveryStatus.Delivered(to = state.getNicknameValue() ?: "Unknown", at = Date())
                )

                val isViewing = state.getSelectedPrivateChatPeerValue() == convKey
                val suppressUnread = seenStore.hasRead(pm.messageID)

                withContext(Dispatchers.Main) {
                    privateChatManager.handleIncomingPrivateMessage(message, suppressUnread)
                }

                if (!seenStore.hasDelivered(pm.messageID)) {
                    val nostrTransport = NostrTransport.getInstance(application)
                    nostrTransport.sendDeliveryAckGeohash(pm.messageID, senderPubkey, recipientIdentity)
                    seenStore.markDelivered(pm.messageID)
                }

                if (isViewing && !suppressUnread) {
                    val nostrTransport = NostrTransport.getInstance(application)
                    nostrTransport.sendReadReceiptGeohash(pm.messageID, senderPubkey, recipientIdentity)
                    seenStore.markRead(pm.messageID)
                }
            }
            com.seamless.android.model.NoisePayloadType.DELIVERED -> {
                val messageId = String(payload.data, Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    meshDelegateHandler.didReceiveDeliveryAck(messageId, convKey)
                }
            }
            com.seamless.android.model.NoisePayloadType.READ_RECEIPT -> {
                val messageId = String(payload.data, Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    meshDelegateHandler.didReceiveReadReceipt(messageId, convKey)
                }
            }
            com.seamless.android.model.NoisePayloadType.FILE_TRANSFER -> {
                // Properly handle encrypted file transfer
                val file = com.seamless.android.model.BitchatFilePacket.decode(payload.data)
                if (file != null) {
                    val fullFilePacket: com.seamless.android.model.BitchatFilePacket?
                    
                    if (file.totalChunks > 1) {
                        // Chunked file - Handle reassembly
                        val transferId = file.transferId
                        Log.d(TAG, "🧩 Received chunk ${file.chunkIndex + 1}/${file.totalChunks} for ID=${transferId.take(8)}")
                        
                        val chunkMap = reassemblyMap.getOrPut(transferId) { java.util.concurrent.ConcurrentHashMap() }
                        chunkMap[file.chunkIndex] = file
                        
                        if (chunkMap.size == file.totalChunks) {
                            Log.i(TAG, "✅ All chunks received for ID=${transferId.take(8)}. Reassembling...")
                            
                            // Reassemble content
                            val sortedChunks = chunkMap.toSortedMap().values
                            val totalSize = sortedChunks.sumOf { it.content.size }
                            val fullContent = java.nio.ByteBuffer.allocate(totalSize)
                            sortedChunks.forEach { fullContent.put(it.content) }
                            
                            // Create full packet (using metadata from first chunk)
                            fullFilePacket = file.copy(
                                content = fullContent.array(),
                                chunkIndex = 0,
                                totalChunks = 1
                            )
                            reassemblyMap.remove(transferId)
                        } else {
                            // Wait for more chunks
                            Log.d(TAG, "⏳ Waiting for chunks (Have ${chunkMap.size}/${file.totalChunks})")
                            return
                        }
                    } else {
                        // Single packet file
                        fullFilePacket = file
                    }

                    if (fullFilePacket != null) {
                        val rawTransferId = fullFilePacket.transferId.ifBlank { java.util.UUID.randomUUID().toString() }
                        val uniqueMsgId = rawTransferId.uppercase()
                        if (seenStore.hasRead(rawTransferId) || seenStore.hasDelivered(rawTransferId)) {
                            return
                        }
                        val savedPath = com.seamless.android.features.file.FileUtils.saveIncomingFile(application, fullFilePacket)
                        val message = BitchatMessage(
                            id = uniqueMsgId,
                            sender = senderNickname,
                            content = savedPath,
                            type = com.seamless.android.features.file.FileUtils.messageTypeForMime(fullFilePacket.mimeType),
                            timestamp = timestamp,
                            isRelay = false,
                            isPrivate = true,
                            recipientNickname = state.getNicknameValue(),
                            senderPeerID = convKey
                        )
                        Log.d(TAG, "📄 Saved Nostr encrypted incoming file to $savedPath (msgId=$uniqueMsgId)")
                        val viewing = state.getSelectedPrivateChatPeerValue() == convKey
                        withContext(Dispatchers.Main) {
                            privateChatManager.handleIncomingPrivateMessage(message, suppressUnread = viewing)
                        }
                        if (!seenStore.hasDelivered(rawTransferId)) {
                            val nostrTransport = NostrTransport.getInstance(application)
                            nostrTransport.sendDeliveryAckGeohash(rawTransferId, senderPubkey, recipientIdentity)
                            seenStore.markDelivered(rawTransferId)
                        }
                        if (viewing && !seenStore.hasRead(rawTransferId)) {
                            val nostrTransport = NostrTransport.getInstance(application)
                            nostrTransport.sendReadReceiptGeohash(rawTransferId, senderPubkey, recipientIdentity)
                            seenStore.markRead(rawTransferId)
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Failed to decode Nostr file transfer from $convKey")
                }
            }
            com.seamless.android.model.NoisePayloadType.TYPING_INDICATOR -> {
                // Handle typing indicator from Nostr
                val isTyping = payload.data.isNotEmpty() && payload.data[0] == 1.toByte()
                Log.d(TAG, "📝 Nostr typing indicator from $senderNickname: isTyping=$isTyping")
                withContext(Dispatchers.Main) {
                    com.seamless.android.ui.TypingStatusManager.shared.onTypingReceived(convKey, senderNickname, isTyping)
                }
            }
            com.seamless.android.model.NoisePayloadType.MESH_MESSAGE -> {
                // Mesh messages are only used over Bluetooth mesh, not Nostr
                // Ignore if received via Nostr relay
                Log.d(TAG, "Ignoring MESH_MESSAGE over Nostr (only valid on Bluetooth)")
            }
            com.seamless.android.model.NoisePayloadType.MESH_AUDIO -> {
                // Mesh audio is only used over Bluetooth mesh, not Nostr
                Log.d(TAG, "Ignoring MESH_AUDIO over Nostr (only valid on Bluetooth)")
            }
            com.seamless.android.model.NoisePayloadType.MESH_IMAGE -> {
                // Mesh images are only used over Bluetooth mesh, not Nostr
                Log.d(TAG, "Ignoring MESH_IMAGE over Nostr (only valid on Bluetooth)")
            }
            com.seamless.android.model.NoisePayloadType.VOICE_STREAM -> {
                // Walkie-Talkie removed
                Log.d(TAG, "Ignoring VOICE_STREAM over Nostr (Walkie-Talkie removed)")
            }
            com.seamless.android.model.NoisePayloadType.BSP_SYNC -> {
                // BSP sync is handled over Tor/BLE, not Nostr relays
                Log.d(TAG, "Ignoring BSP_SYNC over Nostr (only valid on direct connections)")
            }
        }
    }

    private fun base64URLDecode(input: String): ByteArray? {
        return try {
            val padded = input.replace("-", "+")
                .replace("_", "/")
                .let { str ->
                    val padding = (4 - str.length % 4) % 4
                    str + "=".repeat(padding)
                }
            android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64url: ${e.message}")
            null
        }
    }
}
