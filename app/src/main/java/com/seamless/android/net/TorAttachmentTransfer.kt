@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded, resumable-in-process attachment framing for direct Tor chats.
 * Content stays inside the existing encrypted Tor frame transport; this class
 * only handles chunk metadata and app-private file storage.
 */
class TorAttachmentTransfer(private val context: Context) {

    companion object {
        private const val TAG = "TorAttachmentTransfer"
        // Direct onion circuits are high-latency. A bounded half-megabyte image
        // keeps reliable ACKed transfers practical without turning a chat into a
        // long-lived bulk transfer.
        const val MAX_IMAGE_BYTES = 512 * 1_024
        const val CHUNK_BYTES = 3_000
        private const val RECEIPT_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        private const val META_VERSION: Byte = 3
        private const val PREVIOUS_META_VERSION: Byte = 2
        private const val LEGACY_META_VERSION: Byte = 1
        private const val CHUNK_HEADER_BYTES = 16 + 4 + 4
        private val SUPPORTED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val HEX_PUBLIC_KEY = Regex("^[0-9a-fA-F]{64}$")
    }

    data class Meta(
        val transferId: UUID,
        val mimeType: String,
        val totalBytes: Int,
        val chunkCount: Int,
        val senderPublicKey: String = "",
        val groupId: String? = null,
        val groupMessageId: String? = null
    )

    data class CompletedAttachment(
        val transferId: UUID,
        val path: String,
        val mimeType: String,
        val messageId: UUID,
        val timestamp: Long,
        val senderPublicKey: String,
        val groupId: String? = null,
        val groupMessageId: String? = null
    )

    sealed class ChunkResult {
        object Rejected : ChunkResult()
        object AlreadyCompleted : ChunkResult()
        data class Accepted(val completed: CompletedAttachment? = null) : ChunkResult()
    }

    private data class IncomingTransfer(
        val sender: String,
        val meta: Meta,
        val messageId: UUID,
        val timestamp: Long,
        val received: BitSet = BitSet(),
        var receivedCount: Int = 0
    )

    private val incoming = ConcurrentHashMap<String, IncomingTransfer>()
    private val completedIncoming = ConcurrentHashMap<String, CompletedAttachment>()
    private val attachmentDirectory = File(context.filesDir, "tor_attachments").apply { mkdirs() }
    @Suppress("DEPRECATION")
    private val transferPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tor_attachment_transfers",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun createMeta(
        transferId: UUID,
        mimeType: String,
        totalBytes: Int,
        senderPublicKey: String = "",
        groupId: String? = null,
        groupMessageId: String? = null
    ): Meta {
        require(totalBytes in 1..MAX_IMAGE_BYTES) { "Image must be at most 512 KB" }
        val normalizedMimeType = mimeType.lowercase().ifBlank { "image/jpeg" }
        require(normalizedMimeType in SUPPORTED_IMAGE_MIME_TYPES) { "Unsupported image format" }
        require(senderPublicKey.isBlank() || senderPublicKey.matches(HEX_PUBLIC_KEY)) {
            "Invalid sender identity"
        }
        require((groupId == null) == (groupMessageId == null)) { "Incomplete group attachment context" }
        return Meta(
            transferId = transferId,
            mimeType = normalizedMimeType,
            totalBytes = totalBytes,
            chunkCount = (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES,
            senderPublicKey = senderPublicKey.lowercase(), groupId = groupId, groupMessageId = groupMessageId
        )
    }

    fun encodeMeta(meta: Meta): ByteArray {
        val mime = meta.mimeType.toByteArray(Charsets.UTF_8)
        val senderKey = meta.senderPublicKey.toByteArray(Charsets.UTF_8)
        val group = meta.groupId.orEmpty().toByteArray(Charsets.UTF_8)
        val groupMessage = meta.groupMessageId.orEmpty().toByteArray(Charsets.UTF_8)
        require(mime.size <= 255) { "Invalid MIME type" }
        require(senderKey.size <= 255) { "Invalid sender identity" }
        require(group.size <= 64 && groupMessage.size <= 64) { "Invalid group attachment context" }
        return ByteBuffer.allocate(1 + 16 + 1 + mime.size + 4 + 4 + 1 + senderKey.size + 1 + group.size + 1 + groupMessage.size)
            .put(META_VERSION)
            .putLong(meta.transferId.mostSignificantBits)
            .putLong(meta.transferId.leastSignificantBits)
            .put(mime.size.toByte())
            .put(mime)
            .putInt(meta.totalBytes)
            .putInt(meta.chunkCount)
            .put(senderKey.size.toByte())
            .put(senderKey)
            .put(group.size.toByte()).put(group)
            .put(groupMessage.size.toByte()).put(groupMessage)
            .array()
    }

    fun decodeMeta(data: ByteArray): Meta? = runCatching {
        val buffer = ByteBuffer.wrap(data)
        val version = buffer.get()
        if (version != META_VERSION && version != PREVIOUS_META_VERSION && version != LEGACY_META_VERSION) return null
        val id = UUID(buffer.getLong(), buffer.getLong())
        val mimeLength = buffer.get().toInt() and 0xFF
        if (mimeLength == 0 || mimeLength > buffer.remaining()) return null
        val mimeBytes = ByteArray(mimeLength)
        buffer.get(mimeBytes)
        val totalBytes = buffer.getInt()
        val chunkCount = buffer.getInt()
        val mimeType = String(mimeBytes, Charsets.UTF_8).lowercase()
        val expectedChunkCount = (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES
        if (totalBytes !in 1..MAX_IMAGE_BYTES ||
            chunkCount != expectedChunkCount ||
            mimeType !in SUPPORTED_IMAGE_MIME_TYPES
        ) return null
        val senderPublicKey = if (version >= PREVIOUS_META_VERSION) {
            if (buffer.remaining() < 1) return null
            val keyLength = buffer.get().toInt() and 0xFF
            if (keyLength > buffer.remaining()) return null
            String(ByteArray(keyLength).also { buffer.get(it) }, Charsets.UTF_8).lowercase()
        } else {
            ""
        }
        val groupId = if (version == META_VERSION) readSmallString(buffer) else ""
        val groupMessageId = if (version == META_VERSION) readSmallString(buffer) else ""
        if (senderPublicKey.isNotBlank() && !senderPublicKey.matches(HEX_PUBLIC_KEY)) return null
        if ((groupId.isBlank()) != (groupMessageId.isBlank())) return null
        if (buffer.hasRemaining()) return null
        Meta(id, mimeType, totalBytes, chunkCount, senderPublicKey, groupId.ifBlank { null }, groupMessageId.ifBlank { null })
    }.getOrNull()

    fun encodeChunk(transferId: UUID, index: Int, chunk: ByteArray): ByteArray {
        require(chunk.isNotEmpty() && chunk.size <= CHUNK_BYTES) { "Invalid image chunk" }
        return ByteBuffer.allocate(CHUNK_HEADER_BYTES + chunk.size)
            .putLong(transferId.mostSignificantBits)
            .putLong(transferId.leastSignificantBits)
            .putInt(index)
            .putInt(chunk.size)
            .put(chunk)
            .array()
    }

    private fun canonicalSender(sender: String): String {
        return OnionContactManager.OnionContact.canonicalizeOnionEndpoint(sender)
            .ifBlank { sender.trim().lowercase() }
    }

    fun beginIncoming(sender: String, messageId: UUID, timestamp: Long, meta: Meta): Boolean = runCatching {
        val normSender = canonicalSender(sender)
        val key = transferKey(normSender, meta.transferId)
        val transfer = incoming.putIfAbsent(key, IncomingTransfer(normSender, meta, messageId, timestamp))
            ?: incoming[key]
            ?: return false
        val file = tempFile(normSender, meta.transferId)
        if (!file.exists() || file.length() != meta.totalBytes.toLong()) {
            RandomAccessFile(file, "rw").use { it.setLength(meta.totalBytes.toLong()) }
        }
        persistIncoming(transfer, synchronous = true) && persistTransferSender(meta.transferId, meta.senderPublicKey)
    }.onFailure { Log.w(TAG, "Could not prepare incoming attachment: ${it.message}") }.getOrDefault(false)

    fun acceptChunk(sender: String, data: ByteArray): ChunkResult = runCatching {
        val normSender = canonicalSender(sender)
        if (data.size <= CHUNK_HEADER_BYTES) return ChunkResult.Rejected
        val buffer = ByteBuffer.wrap(data)
        val transferId = UUID(buffer.getLong(), buffer.getLong())
        val index = buffer.getInt()
        val length = buffer.getInt()
        if (length !in 1..CHUNK_BYTES || length != buffer.remaining()) return ChunkResult.Rejected
        val key = transferKey(normSender, transferId)
        if (wasRecentlyCompleted(normSender, transferId)) return ChunkResult.AlreadyCompleted
        completedIncoming[key]?.let { return ChunkResult.Accepted(it) }
        val transfer = incoming[key]
            ?: restoreIncoming(normSender, transferId)
            ?: return ChunkResult.Rejected
        if (index !in 0 until transfer.meta.chunkCount) return ChunkResult.Rejected
        val expectedLength = minOf(
            CHUNK_BYTES,
            transfer.meta.totalBytes - index * CHUNK_BYTES
        )
        if (length != expectedLength) return ChunkResult.Rejected

        val chunk = ByteArray(length)
        buffer.get(chunk)
        synchronized(transfer) {
            val finalFile = finalFile(normSender, transferId, transfer.meta.mimeType)
            if (transfer.receivedCount == transfer.meta.chunkCount && finalFile.exists()) {
                return ChunkResult.Accepted(completedAttachment(transfer, transferId, finalFile))
            }
            if (!transfer.received.get(index)) {
                RandomAccessFile(tempFile(normSender, transferId), "rw").use { file ->
                    file.seek(index.toLong() * CHUNK_BYTES)
                    file.write(chunk)
                }
                transfer.received.set(index)
                transfer.receivedCount++
                val isFinalChunk = transfer.receivedCount == transfer.meta.chunkCount
                if (!persistIncoming(transfer, synchronous = isFinalChunk)) {
                    transfer.received.clear(index)
                    transfer.receivedCount--
                    return ChunkResult.Rejected
                }
            }
            if (transfer.receivedCount != transfer.meta.chunkCount) return ChunkResult.Accepted()

            val temp = tempFile(normSender, transferId)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!temp.renameTo(finalFile)) {
                runCatching {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }.onFailure { return ChunkResult.Rejected }
            }
            val completed = completedAttachment(transfer, transferId, finalFile)
            completedIncoming[key] = completed
            ChunkResult.Accepted(completed)
        }
    }.onFailure { Log.w(TAG, "Rejected attachment chunk: ${it.message}") }
        .getOrElse { ChunkResult.Rejected }

    /**
     * Mailbox frames do not retain the original peer socket address. Persisting a
     * transfer-id-to-identity mapping lets each independently fetched chunk be
     * routed back to the transfer that its authenticated metadata initialized.
     */
    fun senderPublicKeyForChunk(data: ByteArray): String? = runCatching {
        if (data.size < CHUNK_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(data)
        val transferId = UUID(buffer.getLong(), buffer.getLong())
        transferPrefs.getString(transferSenderPrefsKey(transferId), null)
            ?.lowercase()
            ?.takeIf { it.matches(HEX_PUBLIC_KEY) }
    }.getOrNull()

    /** Clears resumable transfer state only after the chat row is durable. */
    fun completeIncoming(sender: String, transferId: UUID) {
        val normSender = canonicalSender(sender)
        val key = transferKey(normSender, transferId)
        completedIncoming.remove(key)
        incoming.remove(key)
        transferPrefs.edit()
            .remove(transferPrefsKey(normSender, transferId))
            .remove(transferSenderPrefsKey(transferId))
            .putLong(receiptPrefsKey(normSender, transferId), System.currentTimeMillis())
            .commit()
    }

    fun saveOutgoing(transferId: UUID, bytes: ByteArray, mimeType: String): String {
        require(bytes.size in 1..MAX_IMAGE_BYTES) { "Image must be at most 512 KB" }
        val file = finalFile("outgoing", transferId, mimeType)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun split(bytes: ByteArray): List<ByteArray> = bytes.asList()
        .chunked(CHUNK_BYTES)
        .map { chunk -> chunk.toByteArray() }

    private fun transferKey(sender: String, transferId: UUID) = "${canonicalSender(sender)}:$transferId"

    private fun transferPrefsKey(sender: String, transferId: UUID): String {
        val bytes = "${canonicalSender(sender)}:$transferId".toByteArray(Charsets.UTF_8)
        return "incoming_${Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)}"
    }

    private fun receiptPrefsKey(sender: String, transferId: UUID): String {
        val bytes = "${canonicalSender(sender)}:$transferId".toByteArray(Charsets.UTF_8)
        return "receipt_${Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)}"
    }

    private fun transferSenderPrefsKey(transferId: UUID) = "sender_${transferId}"

    private fun persistTransferSender(transferId: UUID, senderPublicKey: String): Boolean {
        if (senderPublicKey.isBlank()) return true
        return transferPrefs.edit()
            .putString(transferSenderPrefsKey(transferId), senderPublicKey)
            .commit()
    }

    private fun wasRecentlyCompleted(sender: String, transferId: UUID): Boolean {
        val key = receiptPrefsKey(sender, transferId)
        val completedAt = transferPrefs.getLong(key, 0L)
        if (completedAt == 0L) return false
        if (System.currentTimeMillis() - completedAt <= RECEIPT_TTL_MS) return true
        transferPrefs.edit().remove(key).apply()
        return false
    }

    private fun completedAttachment(
        transfer: IncomingTransfer,
        transferId: UUID,
        file: File
    ) = CompletedAttachment(
        transferId = transferId,
        path = file.absolutePath,
        mimeType = transfer.meta.mimeType,
        messageId = transfer.messageId,
        timestamp = transfer.timestamp,
        senderPublicKey = transfer.meta.senderPublicKey,
        groupId = transfer.meta.groupId,
        groupMessageId = transfer.meta.groupMessageId
    )

    private fun persistIncoming(transfer: IncomingTransfer, synchronous: Boolean = false): Boolean {
        val mime = Base64.encodeToString(transfer.meta.mimeType.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val received = Base64.encodeToString(transfer.received.toByteArray(), Base64.NO_WRAP)
        val senderKey = Base64.encodeToString(transfer.meta.senderPublicKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val value = listOf(
            transfer.messageId.toString(),
            transfer.timestamp.toString(),
            mime,
            transfer.meta.totalBytes.toString(),
            transfer.meta.chunkCount.toString(),
            received,
            senderKey,
            Base64.encodeToString(transfer.meta.groupId.orEmpty().toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            Base64.encodeToString(transfer.meta.groupMessageId.orEmpty().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        ).joinToString("|")
        val editor = transferPrefs.edit()
            .putString(transferPrefsKey(transfer.sender, transfer.meta.transferId), value)
        return if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
    }

    private fun restoreIncoming(sender: String, transferId: UUID): IncomingTransfer? = runCatching {
        val value = transferPrefs.getString(transferPrefsKey(sender, transferId), null) ?: return null
        val parts = value.split('|')
        if (parts.size !in 6..9) return null
        val messageId = UUID.fromString(parts[0])
        val timestamp = parts[1].toLong()
        val mime = String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8).lowercase()
        val totalBytes = parts[3].toInt()
        val chunkCount = parts[4].toInt()
        val senderPublicKey = parts.getOrNull(6)
            ?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8).lowercase() }
            .orEmpty()
        val groupId = parts.getOrNull(7)?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }.orEmpty()
        val groupMessageId = parts.getOrNull(8)?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }.orEmpty()
        if (totalBytes !in 1..MAX_IMAGE_BYTES ||
            chunkCount != (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES ||
            mime !in SUPPORTED_IMAGE_MIME_TYPES ||
            (senderPublicKey.isNotBlank() && !senderPublicKey.matches(HEX_PUBLIC_KEY)) ||
            ((groupId.isBlank()) != (groupMessageId.isBlank()))
        ) return null
        val received = BitSet.valueOf(Base64.decode(parts[5], Base64.NO_WRAP))
        if (received.length() > chunkCount) return null
        val transfer = IncomingTransfer(
            sender = sender,
            meta = Meta(transferId, mime, totalBytes, chunkCount, senderPublicKey, groupId.ifBlank { null }, groupMessageId.ifBlank { null }),
            messageId = messageId,
            timestamp = timestamp,
            received = received,
            receivedCount = received.cardinality()
        )
        val file = tempFile(sender, transferId)
        if (!file.exists() || file.length() != totalBytes.toLong()) {
            RandomAccessFile(file, "rw").use { it.setLength(totalBytes.toLong()) }
        }
        incoming.putIfAbsent(transferKey(sender, transferId), transfer) ?: transfer
    }.onFailure { Log.w(TAG, "Could not restore incoming attachment: ${it.message}") }.getOrNull()

    private fun tempFile(sender: String, transferId: UUID) = File(attachmentDirectory, "${sender.hashCode()}_${transferId}.part")
    private fun finalFile(sender: String, transferId: UUID, mimeType: String): File {
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return File(attachmentDirectory, "${sender.hashCode()}_${transferId}.$extension")
    }

    private fun readSmallString(buffer: ByteBuffer): String {
        if (!buffer.hasRemaining()) return ""
        val length = buffer.get().toInt() and 0xFF
        if (length > buffer.remaining()) throw IllegalArgumentException("Invalid metadata string")
        return String(ByteArray(length).also { buffer.get(it) }, Charsets.UTF_8)
    }
}
