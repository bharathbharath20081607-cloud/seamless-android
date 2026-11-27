package com.seamless

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.OnionMessageQueue
import com.seamless.android.net.TorAttachmentTransfer
import com.seamless.android.net.TorRichChatCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class TorPdfAndLargeTransferTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val senderPubHex = "a".repeat(64)

    @Test
    fun metaV6_roundTrips50MbPageCountAndSha256_andRejectsOverLimit() {
        assertEquals(50 * 1024 * 1024, TorAttachmentTransfer.MAX_ATTACHMENT_BYTES)

        val transfer = TorAttachmentTransfer(context)
        val transferId = UUID.randomUUID()
        val sha = "0123456789abcdef".repeat(4)
        val preview = byteArrayOf(1, 2, 3, 4, 5)
        val meta = transfer.createMeta(
            transferId = transferId,
            mimeType = "application/pdf",
            totalBytes = TorAttachmentTransfer.MAX_ATTACHMENT_BYTES,
            senderPublicKey = senderPubHex,
            previewBytes = preview,
            fileName = "Audit_Report_2026.pdf",
            pageCount = 42,
            sha256Hex = sha
        )
        val encoded = transfer.encodeMeta(meta)
        val decoded = transfer.decodeMeta(encoded)
        assertNotNull(decoded)
        assertEquals(transferId, decoded!!.transferId)
        assertEquals("application/pdf", decoded.mimeType)
        assertEquals(TorAttachmentTransfer.MAX_ATTACHMENT_BYTES, decoded.totalBytes)
        assertEquals("Audit_Report_2026.pdf", decoded.fileName)
        assertEquals(42, decoded.pageCount)
        assertEquals(sha, decoded.sha256Hex)
        assertArrayEquals(preview, decoded.previewBytes)

        var threw = false
        try {
            transfer.createMeta(
                transferId = UUID.randomUUID(),
                mimeType = "application/pdf",
                totalBytes = TorAttachmentTransfer.MAX_ATTACHMENT_BYTES + 1
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected >50 MB attachment to be rejected", threw)
    }

    @Test
    fun stageOutgoingFromStream_computesStreamingSha256_andRejectsOverLimit() {
        val transfer = TorAttachmentTransfer(context)
        val payload = ByteArray(96 * 1024) { ((it * 31) xor 0x5A).toByte() }
        val expectedSha = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }

        val transferId = UUID.randomUUID()
        val staged = transfer.stageOutgoingFromStream(
            transferId = transferId,
            input = ByteArrayInputStream(payload),
            mimeType = "application/pdf"
        )
        val stagedFile = File(staged.path)
        try {
            assertTrue(stagedFile.exists())
            assertEquals(payload.size, staged.totalBytes)
            assertEquals("application/pdf", staged.normalizedMime)
            assertEquals(expectedSha, staged.sha256Hex)
        } finally {
            stagedFile.delete()
        }

        val cappedBytes = ByteArray(1025) { 7 }
        var rejected = false
        val overId = UUID.randomUUID()
        try {
            transfer.stageOutgoingFromStream(
                transferId = overId,
                input = ByteArrayInputStream(cappedBytes),
                mimeType = "application/pdf",
                maxBytes = 1024
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected stream exceeding maxBytes to throw", rejected)
    }

    @Test
    fun diskBackedChunksAndFountainParity_recoverDroppedChunkAndVerifySha256() {
        val transfer = TorAttachmentTransfer(context)
        val senderOnion = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef12.onion:18888"
        val totalSize = TorAttachmentTransfer.CHUNK_BYTES * TorAttachmentTransfer.FOUNTAIN_BLOCK_SIZE * 2 + 777
        val originalBytes = ByteArray(totalSize) { ((it * 17 + 13) and 0xFF).toByte() }
        val shaHex = MessageDigest.getInstance("SHA-256")
            .digest(originalBytes)
            .joinToString("") { "%02x".format(it) }

        val srcId = UUID.randomUUID()
        val staged = transfer.stageOutgoingFromStream(
            transferId = srcId,
            input = ByteArrayInputStream(originalBytes),
            mimeType = "application/pdf"
        )
        val srcFile = File(staged.path)
        try {
            val meta = transfer.createMeta(
                transferId = srcId,
                mimeType = "application/pdf",
                totalBytes = staged.totalBytes,
                senderPublicKey = senderPubHex,
                fileName = "Blueprint.pdf",
                pageCount = 12,
                sha256Hex = shaHex
            )
            assertTrue(transfer.beginIncoming(senderOnion, UUID.randomUUID(), System.currentTimeMillis(), meta))

            // Feed all chunks EXCEPT chunk #3 (which sits in Fountain block 0)
            for (idx in 0 until meta.chunkCount) {
                if (idx == 3) continue
                val chunkPayload = transfer.readChunkPayloadFromFile(srcFile, srcId, idx, totalBytes = staged.totalBytes)
                val res = transfer.acceptChunk(senderOnion, chunkPayload)
                assertTrue(res is TorAttachmentTransfer.ChunkResult.Accepted)
                assertNull((res as TorAttachmentTransfer.ChunkResult.Accepted).completed)
            }

            // Feed Fountain-Warp block 0 parity droplet to recover chunk #3 in 0 RTT and verify whole-file SHA-256
            val parity0 = transfer.readParityPayloadFromFile(srcFile, srcId, blockIndex = 0, totalBytes = staged.totalBytes)
            val finalRes = transfer.acceptParityDroplet(senderOnion, parity0)
            assertTrue(finalRes is TorAttachmentTransfer.ChunkResult.Accepted)
            val completed = (finalRes as TorAttachmentTransfer.ChunkResult.Accepted).completed
            assertNotNull(completed)
            assertEquals(12, completed!!.pageCount)
            assertEquals(shaHex, completed.sha256Hex)
            val outFile = File(completed.path)
            assertArrayEquals(originalBytes, outFile.readBytes())
            transfer.completeIncoming(senderOnion, srcId)
            outFile.delete()

            // Verify tampered SHA-256 rejects completion
            val badId = UUID.randomUUID()
            val badMeta = transfer.createMeta(
                transferId = badId,
                mimeType = "application/pdf",
                totalBytes = staged.totalBytes,
                senderPublicKey = senderPubHex,
                fileName = "Tampered.pdf",
                pageCount = 12,
                sha256Hex = "f".repeat(64)
            )
            assertTrue(transfer.beginIncoming(senderOnion, UUID.randomUUID(), System.currentTimeMillis(), badMeta))
            var lastRes: TorAttachmentTransfer.ChunkResult? = null
            for (idx in 0 until badMeta.chunkCount) {
                val chunkPayload = transfer.readChunkPayloadFromFile(srcFile, badId, idx, totalBytes = staged.totalBytes)
                lastRes = transfer.acceptChunk(senderOnion, chunkPayload)
            }
            assertTrue("Mismatched SHA-256 must reject final chunk", lastRes is TorAttachmentTransfer.ChunkResult.Rejected)
        } finally {
            srcFile.delete()
        }
    }

    @Test
    fun lazyFileQueue_persistsEncryptedSnapshotAndMaterializesFramesWithoutHeapBloat() = runBlocking {
        val queue = OnionMessageQueue.getInstance(context)
        queue.clearAll()
        val transfer = TorAttachmentTransfer(context)
        val payload = ByteArray(16_384) { (it * 5).toByte() }
        val transferId = UUID.randomUUID()
        val staged = transfer.stageOutgoingFromStream(
            transferId = transferId,
            input = ByteArrayInputStream(payload),
            mimeType = "application/pdf"
        )
        val srcFile = File(staged.path)
        try {
            val recipient = "peer23456789012345678901234567890123456789012345678901234.onion:18888"
            val uiMsgId = UUID.randomUUID().toString()
            val chunkCount = (staged.totalBytes + TorAttachmentTransfer.CHUNK_BYTES - 1) / TorAttachmentTransfer.CHUNK_BYTES
            val specs = (0 until chunkCount).map { idx ->
                OnionMessageQueue.BatchFrameSpec(
                    uiMessageId = uiMsgId,
                    completesUiMessage = idx == chunkCount - 1,
                    lazyId = UUID.randomUUID(),
                    lazySpec = OnionMessageQueue.LazyFileFrameSpec(
                        frameTypeCode = OnionMessageProtocol.FrameType.FILE_CHUNK.code,
                        sequence = idx.toLong(),
                        timestamp = System.currentTimeMillis(),
                        transferId = transferId.toString(),
                        sourcePath = staged.path,
                        indexOrBlock = idx,
                        chunkStride = TorAttachmentTransfer.CHUNK_BYTES,
                        totalBytes = staged.totalBytes
                    )
                )
            }
            val queuedIds = queue.enqueueBatch(recipient, specs)
            assertEquals(chunkCount, queuedIds.size)

            val firstQueued = queue.getQueuedMessage(queuedIds.first())
            assertNotNull(firstQueued)
            assertTrue("Lazy queued frame must keep heap frameData empty", firstQueued!!.frameData.isEmpty())

            val wireBytes = firstQueued.materializeFrameData(transfer)
            val decodedFrame = OnionMessageProtocol.OnionFrame.decode(wireBytes)
            assertNotNull(decodedFrame)
            assertEquals(OnionMessageProtocol.FrameType.FILE_CHUNK, decodedFrame!!.frameType)
            assertEquals(queuedIds.first(), decodedFrame.messageId.toString())

            queue.flush()
            val snapshotFile = File(context.filesDir, "onion_queue_v2.enc")
            assertTrue("Expected encrypted queue snapshot file", snapshotFile.exists() && snapshotFile.length() > 32L)
            assertFalse(snapshotFile.readText(Charsets.UTF_8).contains(recipient))
        } finally {
            queue.clearAll()
            srcFile.delete()
        }
    }

    @Test
    fun richChatCodec_encodesPdfPageCountSha256AndPageCitations() {
        val sha = "9f8a4c1d".repeat(8)
        val encoded = TorRichChatCodec.encodeAttachmentDescriptor(
            TorRichChatCodec.AttachmentDescriptor(
                kind = "file",
                fileName = "Quarterly_Brief.pdf",
                totalBytes = 14_600_000,
                viewOnce = false,
                pageCount = 18,
                sha256 = sha
            )
        )
        val parsed = TorRichChatCodec.parseAttachmentDescriptor(encoded)
        assertNotNull(parsed)
        assertEquals("file", parsed!!.kind)
        assertEquals("Quarterly_Brief.pdf", parsed.fileName)
        assertEquals(14_600_000, parsed.totalBytes)
        assertEquals(18, parsed.pageCount)
        assertEquals(sha, parsed.sha256)
        assertEquals("📄 Quarterly_Brief.pdf (18 pages)", TorRichChatCodec.friendlyPreviewText(encoded, "application/pdf"))

        val formatted = TorRichChatCodec.formatPdfPageCitation(7, "Check the key rotation diagram")
        assertEquals("📄 Page 7 — Check the key rotation diagram", formatted)
        val cite = TorRichChatCodec.parsePdfPageCitation(formatted)
        assertNotNull(cite)
        assertEquals(7, cite!!.pageNumber)
        assertEquals("Check the key rotation diagram", cite.note)
    }
}
