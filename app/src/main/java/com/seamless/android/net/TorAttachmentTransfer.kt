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
        const val MAX_ATTACHMENT_BYTES = 1_024 * 1_024
        const val CHUNK_BYTES = 3_840
        const val MAX_PREVIEW_BYTES = 1_200
        const val FOUNTAIN_BLOCK_SIZE = 8
        private const val RECEIPT_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        private const val META_VERSION: Byte = 5
        private const val META_VERSION_V4: Byte = 4
        private const val META_VERSION_V3: Byte = 3
        private const val PREVIOUS_META_VERSION: Byte = 2
        private const val LEGACY_META_VERSION: Byte = 1
        private const val CHUNK_HEADER_BYTES = 16 + 4 + 4
        private const val PARITY_HEADER_BYTES = 16 + 4 + 4 + 4
        private val SUPPORTED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val SUPPORTED_AUDIO_MIME_TYPES = setOf(
            "audio/mp4", "audio/m4a", "audio/aac", "audio/ogg",
            "audio/mpeg", "audio/wav", "audio/3gpp", "audio/webm"
        )
        private val SUPPORTED_DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/json",
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.android.package-archive",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream"
        )
        private val HEX_PUBLIC_KEY = Regex("^[0-9a-fA-F]{64}$")

        fun normalizeSupportedMimeType(rawMime: String): String {
            val clean = rawMime.trim().lowercase().substringBefore(';').trim()
            if (clean.isBlank()) return "application/octet-stream"
            if (clean == "image/jpg") return "image/jpeg"
            if (clean in SUPPORTED_IMAGE_MIME_TYPES ||
                clean in SUPPORTED_AUDIO_MIME_TYPES ||
                clean in SUPPORTED_DOCUMENT_MIME_TYPES
            ) {
                return clean
            }
            if (clean.length in 3..120 && '/' in clean) {
                return "application/octet-stream"
            }
            return "application/octet-stream"
        }

        fun sanitizeFileName(rawName: String): String {
            return rawName.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().take(120)
        }
    }

    data class Meta(
        val transferId: UUID,
        val mimeType: String,
        val totalBytes: Int,
        val chunkCount: Int,
        val senderPublicKey: String = "",
        val groupId: String? = null,
        val groupMessageId: String? = null,
        val chunkSize: Int = CHUNK_BYTES,
        val previewBytes: ByteArray? = null,
        val fileName: String = "",
        val durationMs: Int = 0,
        val viewOnce: Boolean = false
    )

    data class CompletedAttachment(
        val transferId: UUID,
        val path: String,
        val mimeType: String,
        val messageId: UUID,
        val timestamp: Long,
        val senderPublicKey: String,
        val groupId: String? = null,
        val groupMessageId: String? = null,
        val fileName: String = "",
        val durationMs: Int = 0,
        val viewOnce: Boolean = false
    )

    sealed class ChunkResult {
        object Rejected : ChunkResult()
        object AlreadyCompleted : ChunkResult()
        data class Accepted(val completed: CompletedAttachment? = null) : ChunkResult()
    }

    data class ParityDroplet(
        val blockIndex: Int,
        val afterChunkIndex: Int,
        val payload: ByteArray
    )

    private data class IncomingTransfer(
        val sender: String,
        val meta: Meta,
        val messageId: UUID,
        val timestamp: Long,
        val received: BitSet = BitSet(),
        var receivedCount: Int = 0,
        var dirtyChunksSincePersist: Int = 0,
        var raf: RandomAccessFile? = null,
        val createdAtMs: Long = System.currentTimeMillis(),
        @Volatile var lastActivityAtMs: Long = System.currentTimeMillis()
    )

    private val incoming = ConcurrentHashMap<String, IncomingTransfer>()
    private val incomingMetaByTransferId = ConcurrentHashMap<UUID, Meta>()
    private val completedIncoming = ConcurrentHashMap<String, CompletedAttachment>()
    private val transferSenders = ConcurrentHashMap<UUID, String>()
    private val recentCompletedReceipts = ConcurrentHashMap<String, Long>()
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
        groupMessageId: String? = null,
        previewBytes: ByteArray? = null,
        fileName: String = "",
        durationMs: Int = 0,
        viewOnce: Boolean = false
    ): Meta {
        require(totalBytes in 1..MAX_ATTACHMENT_BYTES) { "Attachment must be at most 1 MB" }
        val normalizedMimeType = normalizeSupportedMimeType(mimeType.ifBlank { "application/octet-stream" })
        require(senderPublicKey.isBlank() || senderPublicKey.matches(HEX_PUBLIC_KEY)) {
            "Invalid sender identity"
        }
        require((groupId == null) == (groupMessageId == null)) { "Incomplete group attachment context" }
        val safeFileName = sanitizeFileName(fileName)
        val safePreview = if (viewOnce) null else previewBytes?.takeIf { it.size in 1..MAX_PREVIEW_BYTES }
        return Meta(
            transferId = transferId,
            mimeType = normalizedMimeType,
            totalBytes = totalBytes,
            chunkCount = (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES,
            senderPublicKey = senderPublicKey.lowercase(),
            groupId = groupId,
            groupMessageId = groupMessageId,
            chunkSize = CHUNK_BYTES,
            previewBytes = safePreview,
            fileName = safeFileName,
            durationMs = durationMs.coerceAtLeast(0),
            viewOnce = viewOnce
        )
    }

    fun encodeMeta(meta: Meta): ByteArray {
        val mime = meta.mimeType.toByteArray(Charsets.UTF_8)
        val senderKey = meta.senderPublicKey.toByteArray(Charsets.UTF_8)
        val group = meta.groupId.orEmpty().toByteArray(Charsets.UTF_8)
        val groupMessage = meta.groupMessageId.orEmpty().toByteArray(Charsets.UTF_8)
        val preview = if (meta.viewOnce) {
            ByteArray(0)
        } else {
            meta.previewBytes?.takeIf { it.size <= MAX_PREVIEW_BYTES } ?: ByteArray(0)
        }
        val stride = meta.chunkSize.coerceIn(512, 3_900)
        val rawNameBytes = sanitizeFileName(meta.fileName).toByteArray(Charsets.UTF_8)
        val fileNameBytes = if (rawNameBytes.size > 180) rawNameBytes.copyOf(180) else rawNameBytes
        require(mime.size <= 255) { "Invalid MIME type" }
        require(senderKey.size <= 255) { "Invalid sender identity" }
        require(group.size <= 64 && groupMessage.size <= 64) { "Invalid group attachment context" }
        return ByteBuffer.allocate(
            1 + 16 + 1 + mime.size + 4 + 4 + 1 + senderKey.size +
                1 + group.size + 1 + groupMessage.size + 2 + 2 + preview.size +
                1 + fileNameBytes.size + 4 + 1
        )
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
            .putShort(stride.toShort())
            .putShort(preview.size.toShort())
            .put(preview)
            .put(fileNameBytes.size.toByte())
            .put(fileNameBytes)
            .putInt(meta.durationMs.coerceAtLeast(0))
            .put(if (meta.viewOnce) 1.toByte() else 0.toByte())
            .array()
    }

    fun decodeMeta(data: ByteArray): Meta? = runCatching {
        val buffer = ByteBuffer.wrap(data)
        val version = buffer.get()
        if (version != META_VERSION &&
            version != META_VERSION_V4 &&
            version != META_VERSION_V3 &&
            version != PREVIOUS_META_VERSION &&
            version != LEGACY_META_VERSION
        ) return null
        val id = UUID(buffer.getLong(), buffer.getLong())
        val mimeLength = buffer.get().toInt() and 0xFF
        if (mimeLength == 0 || mimeLength > buffer.remaining()) return null
        val mimeBytes = ByteArray(mimeLength)
        buffer.get(mimeBytes)
        val totalBytes = buffer.getInt()
        val chunkCount = buffer.getInt()
        val mimeType = normalizeSupportedMimeType(String(mimeBytes, Charsets.UTF_8))
        if (totalBytes !in 1..MAX_ATTACHMENT_BYTES) return null
        val senderPublicKey = if (version >= PREVIOUS_META_VERSION) {
            if (buffer.remaining() < 1) return null
            val keyLength = buffer.get().toInt() and 0xFF
            if (keyLength > buffer.remaining()) return null
            String(ByteArray(keyLength).also { buffer.get(it) }, Charsets.UTF_8).lowercase()
        } else {
            ""
        }
        val groupId = if (version >= META_VERSION_V3) readSmallString(buffer) else ""
        val groupMessageId = if (version >= META_VERSION_V3) readSmallString(buffer) else ""
        val chunkStride: Int
        var previewBytes: ByteArray?
        if (version >= META_VERSION_V4) {
            if (buffer.remaining() < 4) return null
            chunkStride = buffer.short.toInt() and 0xFFFF
            val previewLen = buffer.short.toInt() and 0xFFFF
            if (chunkStride !in 512..3_900 || previewLen !in 0..MAX_PREVIEW_BYTES || previewLen > buffer.remaining()) {
                return null
            }
            previewBytes = if (previewLen > 0) ByteArray(previewLen).also { buffer.get(it) } else null
        } else {
            chunkStride = if (chunkCount == (totalBytes + 3_000 - 1) / 3_000) 3_000 else CHUNK_BYTES
            previewBytes = null
        }
        var fileName = ""
        var durationMs = 0
        var viewOnce = false
        if (version == META_VERSION) {
            if (buffer.remaining() < 1 + 4 + 1) return null
            fileName = sanitizeFileName(readSmallString(buffer))
            if (buffer.remaining() < 5) return null
            durationMs = buffer.int.coerceAtLeast(0)
            viewOnce = buffer.get().toInt() != 0
            if (viewOnce) {
                previewBytes = null
            }
        }
        if (chunkCount != (totalBytes + chunkStride - 1) / chunkStride) return null
        if (senderPublicKey.isNotBlank() && !senderPublicKey.matches(HEX_PUBLIC_KEY)) return null
        if ((groupId.isBlank()) != (groupMessageId.isBlank())) return null
        if (buffer.hasRemaining()) return null
        Meta(
            transferId = id,
            mimeType = mimeType,
            totalBytes = totalBytes,
            chunkCount = chunkCount,
            senderPublicKey = senderPublicKey,
            groupId = groupId.ifBlank { null },
            groupMessageId = groupMessageId.ifBlank { null },
            chunkSize = chunkStride,
            previewBytes = previewBytes,
            fileName = fileName,
            durationMs = durationMs,
            viewOnce = viewOnce
        )
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

    /**
     * Generates systematic GF(2) block-XOR parity droplets (`FOUNTAIN_BLOCK_SIZE = 8`)
     * so any single delayed/stalled chunk per block reconstructs immediately in 0 RTTs.
     */
    fun generateFountainParities(transferId: UUID, chunks: List<ByteArray>): List<ParityDroplet> {
        if (chunks.size < FOUNTAIN_BLOCK_SIZE) return emptyList()
        val fullBlocks = chunks.size / FOUNTAIN_BLOCK_SIZE
        val result = ArrayList<ParityDroplet>(fullBlocks)
        for (blockIdx in 0 until fullBlocks) {
            val start = blockIdx * FOUNTAIN_BLOCK_SIZE
            val end = start + FOUNTAIN_BLOCK_SIZE
            var maxLen = 0
            for (i in start until end) {
                if (chunks[i].size > maxLen) maxLen = chunks[i].size
            }
            if (maxLen <= 0 || maxLen > CHUNK_BYTES) continue
            val xorBytes = ByteArray(maxLen)
            for (i in start until end) {
                val c = chunks[i]
                for (j in c.indices) {
                    xorBytes[j] = (xorBytes[j].toInt() xor (c[j].toInt() and 0xFF)).toByte()
                }
            }
            val payload = ByteBuffer.allocate(PARITY_HEADER_BYTES + maxLen)
                .putLong(transferId.mostSignificantBits)
                .putLong(transferId.leastSignificantBits)
                .putInt(blockIdx)
                .putInt(FOUNTAIN_BLOCK_SIZE)
                .putInt(maxLen)
                .put(xorBytes)
                .array()
            result.add(ParityDroplet(blockIdx, end - 1, payload))
        }
        return result
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
        incomingMetaByTransferId[meta.transferId] = meta
        if (meta.senderPublicKey.isNotBlank()) {
            transferSenders[meta.transferId] = meta.senderPublicKey.lowercase()
        }
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
        val stride = transfer.meta.chunkSize.coerceIn(512, 3_900)
        if (length > stride || index !in 0 until transfer.meta.chunkCount) return ChunkResult.Rejected
        val expectedLength = minOf(
            stride,
            transfer.meta.totalBytes - index * stride
        )
        if (length != expectedLength) return ChunkResult.Rejected

        val chunk = ByteArray(length)
        buffer.get(chunk)
        synchronized(transfer) {
            commitChunkLocked(normSender, transferId, transfer, index, stride, chunk)
        }
    }.onFailure { Log.w(TAG, "Rejected attachment chunk: ${it.message}") }
        .getOrElse { ChunkResult.Rejected }

    /**
     * Accepts a Fountain-Warp GF(2) parity droplet and recovers any single missing
     * chunk in `[blockIndex * FOUNTAIN_BLOCK_SIZE, end)` without waiting for retransmission.
     */
    fun acceptParityDroplet(sender: String, data: ByteArray): ChunkResult = runCatching {
        val normSender = canonicalSender(sender)
        if (data.size <= PARITY_HEADER_BYTES) return ChunkResult.Rejected
        val buffer = ByteBuffer.wrap(data)
        val transferId = UUID(buffer.getLong(), buffer.getLong())
        val blockIndex = buffer.getInt()
        val blockCount = buffer.getInt()
        val maxLen = buffer.getInt()
        if (blockCount != FOUNTAIN_BLOCK_SIZE || maxLen !in 1..CHUNK_BYTES || maxLen != buffer.remaining()) {
            return ChunkResult.Rejected
        }
        val key = transferKey(normSender, transferId)
        if (wasRecentlyCompleted(normSender, transferId)) return ChunkResult.AlreadyCompleted
        completedIncoming[key]?.let { return ChunkResult.Accepted(it) }
        val transfer = incoming[key]
            ?: restoreIncoming(normSender, transferId)
            ?: return ChunkResult.Rejected

        val startIdx = blockIndex * FOUNTAIN_BLOCK_SIZE
        val endIdx = startIdx + FOUNTAIN_BLOCK_SIZE
        if (blockIndex < 0 || startIdx >= transfer.meta.chunkCount || endIdx > transfer.meta.chunkCount) {
            return ChunkResult.Rejected
        }

        val xorBytes = ByteArray(maxLen)
        buffer.get(xorBytes)
        val stride = transfer.meta.chunkSize.coerceIn(512, 3_900)

        synchronized(transfer) {
            transfer.lastActivityAtMs = System.currentTimeMillis()
            val finalFile = finalFile(normSender, transferId, transfer.meta.mimeType)
            if (transfer.receivedCount == transfer.meta.chunkCount && finalFile.exists()) {
                return ChunkResult.Accepted(completedAttachment(transfer, transferId, finalFile))
            }
            var missingIdx = -1
            var missingCount = 0
            for (idx in startIdx until endIdx) {
                if (!transfer.received.get(idx)) {
                    missingIdx = idx
                    missingCount++
                }
            }
            if (missingCount != 1 || missingIdx < 0) {
                return ChunkResult.Accepted()
            }

            val expectedMissingLen = minOf(stride, transfer.meta.totalBytes - missingIdx * stride)
            if (expectedMissingLen !in 1..maxLen) return ChunkResult.Rejected

            try {
                val file = transfer.raf ?: RandomAccessFile(tempFile(normSender, transferId), "rw").also {
                    transfer.raf = it
                }
                val scratch = ByteArray(stride)
                for (idx in startIdx until endIdx) {
                    if (idx == missingIdx) continue
                    val presentLen = minOf(stride, transfer.meta.totalBytes - idx * stride)
                    if (presentLen !in 1..maxLen) return ChunkResult.Rejected
                    file.seek(idx.toLong() * stride)
                    file.readFully(scratch, 0, presentLen)
                    for (j in 0 until presentLen) {
                        xorBytes[j] = (xorBytes[j].toInt() xor (scratch[j].toInt() and 0xFF)).toByte()
                    }
                }
            } catch (ioError: Exception) {
                runCatching { transfer.raf?.close() }
                transfer.raf = null
                throw ioError
            }

            val recoveredChunk = xorBytes.copyOfRange(0, expectedMissingLen)
            Log.i(TAG, "🧬 Fountain-Warp recovered missing chunk #$missingIdx for $transferId (0-RTT)")
            commitChunkLocked(normSender, transferId, transfer, missingIdx, stride, recoveredChunk)
        }
    }.onFailure { Log.w(TAG, "Rejected parity droplet: ${it.message}") }
        .getOrElse { ChunkResult.Rejected }

    private fun commitChunkLocked(
        normSender: String,
        transferId: UUID,
        transfer: IncomingTransfer,
        index: Int,
        stride: Int,
        chunk: ByteArray
    ): ChunkResult {
        transfer.lastActivityAtMs = System.currentTimeMillis()
        val finalFile = finalFile(normSender, transferId, transfer.meta.mimeType)
        if (transfer.receivedCount == transfer.meta.chunkCount && finalFile.exists()) {
            return ChunkResult.Accepted(completedAttachment(transfer, transferId, finalFile))
        }
        if (!transfer.received.get(index)) {
            try {
                val file = transfer.raf ?: RandomAccessFile(tempFile(normSender, transferId), "rw").also {
                    transfer.raf = it
                }
                file.seek(index.toLong() * stride)
                file.write(chunk)
            } catch (ioError: Exception) {
                runCatching { transfer.raf?.close() }
                transfer.raf = null
                throw ioError
            }
            transfer.received.set(index)
            transfer.receivedCount++
            val isFinalChunk = transfer.receivedCount == transfer.meta.chunkCount
            if (isFinalChunk) {
                transfer.dirtyChunksSincePersist = 0
                if (!persistIncoming(transfer, synchronous = true)) {
                    transfer.received.clear(index)
                    transfer.receivedCount--
                    return ChunkResult.Rejected
                }
            } else if (++transfer.dirtyChunksSincePersist >= 8) {
                transfer.dirtyChunksSincePersist = 0
                persistIncoming(transfer, synchronous = false)
            }
        }
        if (transfer.receivedCount != transfer.meta.chunkCount) return ChunkResult.Accepted()

        runCatching { transfer.raf?.close() }
        transfer.raf = null
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
        completedIncoming[transferKey(normSender, transferId)] = completed
        return ChunkResult.Accepted(completed)
    }

    fun senderPublicKeyForParity(data: ByteArray): String? =
        if (data.size >= PARITY_HEADER_BYTES) senderPublicKeyForChunk(data) else null

    /**
     * Mailbox frames do not retain the original peer socket address. Persisting a
     * transfer-id-to-identity mapping lets each independently fetched chunk be
     * routed back to the transfer that its authenticated metadata initialized.
     */
    fun senderPublicKeyForChunk(data: ByteArray): String? = runCatching {
        if (data.size < CHUNK_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(data)
        val transferId = UUID(buffer.getLong(), buffer.getLong())
        transferSenders[transferId]?.let { return it }
        val loaded = transferPrefs.getString(transferSenderPrefsKey(transferId), null)
            ?.lowercase()
            ?.takeIf { it.matches(HEX_PUBLIC_KEY) }
        if (loaded != null) {
            transferSenders[transferId] = loaded
        }
        loaded
    }.getOrNull()

    fun peekTransferId(data: ByteArray): UUID? = runCatching {
        if (data.size < CHUNK_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(data)
        UUID(buffer.getLong(), buffer.getLong())
    }.getOrNull()

    fun isFinalChunkPayload(data: ByteArray): Boolean = runCatching {
        if (data.size <= CHUNK_HEADER_BYTES) return false
        val buffer = ByteBuffer.wrap(data)
        val transferId = UUID(buffer.getLong(), buffer.getLong())
        val index = buffer.getInt()
        val meta = incomingMetaByTransferId[transferId] ?: return false
        index == meta.chunkCount - 1
    }.getOrDefault(false)

    /** Closes abandoned file descriptors and removes orphaned `.part` files. */
    fun cleanupStaleTransfers(maxAgeMs: Long = 30 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        val staleEntries = incoming.entries.filter { now - it.value.lastActivityAtMs > maxAgeMs }
        for ((key, transfer) in staleEntries) {
            if (incoming.remove(key, transfer)) {
                synchronized(transfer) {
                    runCatching { transfer.raf?.close() }
                    transfer.raf = null
                }
                incomingMetaByTransferId.remove(transfer.meta.transferId)
                transferSenders.remove(transfer.meta.transferId)
                runCatching { tempFile(transfer.sender, transfer.meta.transferId).delete() }
            }
        }
        if (completedIncoming.size > 256) {
            completedIncoming.keys.take(64).forEach { completedIncoming.remove(it) }
        }
        if (recentCompletedReceipts.size > 1024) {
            recentCompletedReceipts.entries
                .filter { now - it.value > RECEIPT_TTL_MS }
                .forEach { recentCompletedReceipts.remove(it.key) }
        }
    }

    /** Clears resumable transfer state only after the chat row is durable. */
    fun completeIncoming(sender: String, transferId: UUID) {
        val normSender = canonicalSender(sender)
        val key = transferKey(normSender, transferId)
        val now = System.currentTimeMillis()
        completedIncoming.remove(key)
        incomingMetaByTransferId.remove(transferId)
        incoming.remove(key)?.let { stale ->
            synchronized(stale) {
                runCatching { stale.raf?.close() }
                stale.raf = null
            }
        }
        transferSenders.remove(transferId)
        recentCompletedReceipts[receiptPrefsKey(normSender, transferId)] = now
        transferPrefs.edit()
            .remove(transferPrefsKey(normSender, transferId))
            .remove(transferSenderPrefsKey(transferId))
            .putLong(receiptPrefsKey(normSender, transferId), now)
            .commit()
    }

    fun saveOutgoing(transferId: UUID, bytes: ByteArray, mimeType: String): String {
        require(bytes.size in 1..MAX_ATTACHMENT_BYTES) { "Attachment must be at most 1 MB" }
        val file = finalFile("outgoing", transferId, normalizeSupportedMimeType(mimeType))
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun savePreview(transferId: UUID, previewBytes: ByteArray): String? = runCatching {
        if (previewBytes.isEmpty() || previewBytes.size > MAX_PREVIEW_BYTES) return null
        val file = File(attachmentDirectory, "preview_${transferId}.webp")
        file.writeBytes(previewBytes)
        file.absolutePath
    }.getOrNull()

    fun deletePreview(transferId: UUID) {
        runCatching {
            val file = File(attachmentDirectory, "preview_${transferId}.webp")
            if (file.exists()) file.delete()
        }
    }

    fun split(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        val count = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        val chunks = ArrayList<ByteArray>(count)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + CHUNK_BYTES, bytes.size)
            chunks.add(bytes.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

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
        val now = System.currentTimeMillis()
        val cachedAt = recentCompletedReceipts[key]
        if (cachedAt != null) {
            if (now - cachedAt <= RECEIPT_TTL_MS) return true
            recentCompletedReceipts.remove(key)
        }
        val completedAt = transferPrefs.getLong(key, 0L)
        if (completedAt == 0L) return false
        if (now - completedAt <= RECEIPT_TTL_MS) {
            recentCompletedReceipts[key] = completedAt
            return true
        }
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
        groupMessageId = transfer.meta.groupMessageId,
        fileName = transfer.meta.fileName,
        durationMs = transfer.meta.durationMs,
        viewOnce = transfer.meta.viewOnce
    )

    private fun persistIncoming(transfer: IncomingTransfer, synchronous: Boolean = false): Boolean {
        val mime = Base64.encodeToString(transfer.meta.mimeType.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val received = Base64.encodeToString(transfer.received.toByteArray(), Base64.NO_WRAP)
        val senderKey = Base64.encodeToString(transfer.meta.senderPublicKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val fileNameB64 = Base64.encodeToString(transfer.meta.fileName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val value = listOf(
            transfer.messageId.toString(),
            transfer.timestamp.toString(),
            mime,
            transfer.meta.totalBytes.toString(),
            transfer.meta.chunkCount.toString(),
            received,
            senderKey,
            Base64.encodeToString(transfer.meta.groupId.orEmpty().toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            Base64.encodeToString(transfer.meta.groupMessageId.orEmpty().toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            transfer.meta.chunkSize.toString(),
            fileNameB64,
            transfer.meta.durationMs.toString(),
            if (transfer.meta.viewOnce) "1" else "0"
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
        if (parts.size !in 6..13) return null
        val messageId = UUID.fromString(parts[0])
        val timestamp = parts[1].toLong()
        val mime = normalizeSupportedMimeType(String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8))
        val totalBytes = parts[3].toInt()
        val chunkCount = parts[4].toInt()
        val senderPublicKey = parts.getOrNull(6)
            ?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8).lowercase() }
            .orEmpty()
        val groupId = parts.getOrNull(7)?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }.orEmpty()
        val groupMessageId = parts.getOrNull(8)?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }.orEmpty()
        val chunkStride = parts.getOrNull(9)?.toIntOrNull()?.takeIf { it in 512..3_900 }
            ?: if (chunkCount == (totalBytes + 3_000 - 1) / 3_000) 3_000 else CHUNK_BYTES
        val fileName = parts.getOrNull(10)
            ?.let { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }
            ?.let(::sanitizeFileName)
            .orEmpty()
        val durationMs = parts.getOrNull(11)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val viewOnce = parts.getOrNull(12) == "1"
        if (totalBytes !in 1..MAX_ATTACHMENT_BYTES ||
            chunkCount != (totalBytes + chunkStride - 1) / chunkStride ||
            (senderPublicKey.isNotBlank() && !senderPublicKey.matches(HEX_PUBLIC_KEY)) ||
            ((groupId.isBlank()) != (groupMessageId.isBlank()))
        ) return null
        val received = BitSet.valueOf(Base64.decode(parts[5], Base64.NO_WRAP))
        if (received.length() > chunkCount) return null
        val transfer = IncomingTransfer(
            sender = sender,
            meta = Meta(
                transferId = transferId,
                mimeType = mime,
                totalBytes = totalBytes,
                chunkCount = chunkCount,
                senderPublicKey = senderPublicKey,
                groupId = groupId.ifBlank { null },
                groupMessageId = groupMessageId.ifBlank { null },
                chunkSize = chunkStride,
                fileName = fileName,
                durationMs = durationMs,
                viewOnce = viewOnce
            ),
            messageId = messageId,
            timestamp = timestamp,
            received = received,
            receivedCount = received.cardinality()
        )
        if (senderPublicKey.isNotBlank()) {
            transferSenders[transferId] = senderPublicKey
        }
        incomingMetaByTransferId[transferId] = transfer.meta
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
            "image/jpeg" -> "jpg"
            "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/mpeg" -> "mp3"
            "audio/wav" -> "wav"
            "audio/3gpp" -> "3gp"
            "audio/webm" -> "webm"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "text/csv" -> "csv"
            "application/json" -> "json"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/vnd.android.package-archive" -> "apk"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            else -> "bin"
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
