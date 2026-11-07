package com.seamless.android.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BitchatFilePacket: TLV-encoded file transfer payload for BLE mesh.
 * TLVs:
 *  - 0x01: filename (UTF-8)
 *  - 0x02: file size (8 bytes, UInt64)
 *  - 0x03: mime type (UTF-8)
 *  - 0x04: content (bytes) — may appear multiple times for large files
 *
 * Length field for TLV is 2 bytes (UInt16, big-endian) for all TLVs.
 * For large files, CONTENT is chunked into multiple TLVs of up to 65535 bytes each.
 *
 * Note: The outer BitchatPacket uses version 2 (4-byte payload length), so this
 * TLV payload can exceed 64 KiB even though each TLV value is limited to 65535 bytes.
 * Transport-level fragmentation then splits the final packet for BLE MTU.
 */
data class BitchatFilePacket(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val content: ByteArray,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val transferId: String = java.util.UUID.randomUUID().toString()
) {
    private enum class TLVType(val v: UByte) {
        FILE_NAME(0x01u), FILE_SIZE(0x02u), MIME_TYPE(0x03u), CONTENT(0x04u),
        CHUNK_INDEX(0x05u), TOTAL_CHUNKS(0x06u), TRANSFER_ID(0x07u);
        companion object { fun from(value: UByte) = values().find { it.v == value } }
    }

    fun encode(): ByteArray? {
        try {
            android.util.Log.d("BitchatFilePacket", "🔄 Encoding: name=$fileName, size=$fileSize, chunk=$chunkIndex/$totalChunks, id=${transferId.take(8)}")
            val nameBytes = fileName.toByteArray(Charsets.UTF_8)
            val mimeBytes = mimeType.toByteArray(Charsets.UTF_8)
            val idBytes = transferId.toByteArray(Charsets.UTF_8)
            
            // Validate bounds
            if (nameBytes.size > 0xFFFF || mimeBytes.size > 0xFFFF || idBytes.size > 0xFFFF) {
                android.util.Log.e("BitchatFilePacket", "❌ TLV field too large")
                return null
            }
            
            val sizeFieldLen = 4 
            val contentLenFieldLen = 4
            val chunkFieldLen = 4

            // Calculate capacity
            val includeTransferId = idBytes.isNotEmpty()
            var capacity = (1 + 2 + nameBytes.size) + (1 + 2 + sizeFieldLen) + (1 + 2 + mimeBytes.size) + (1 + contentLenFieldLen + content.size)
            if (totalChunks > 1) {
                capacity += (1 + 2 + chunkFieldLen) * 2 // chunkIndex + totalChunks
            }
            if (includeTransferId) {
                capacity += (1 + 2 + idBytes.size) // transferId
            }

            val buf = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN)

            // FILE_NAME
            buf.put(TLVType.FILE_NAME.v.toByte())
            buf.putShort(nameBytes.size.toShort())
            buf.put(nameBytes)

            // FILE_SIZE
            buf.put(TLVType.FILE_SIZE.v.toByte())
            buf.putShort(sizeFieldLen.toShort())
            buf.putInt(fileSize.toInt())

            // MIME_TYPE
            buf.put(TLVType.MIME_TYPE.v.toByte())
            buf.putShort(mimeBytes.size.toShort())
            buf.put(mimeBytes)
            
            // CHUNKS & ID
            if (totalChunks > 1) {
                buf.put(TLVType.CHUNK_INDEX.v.toByte())
                buf.putShort(chunkFieldLen.toShort())
                buf.putInt(chunkIndex)
                
                buf.put(TLVType.TOTAL_CHUNKS.v.toByte())
                buf.putShort(chunkFieldLen.toShort())
                buf.putInt(totalChunks)
            }
            if (includeTransferId) {
                buf.put(TLVType.TRANSFER_ID.v.toByte())
                buf.putShort(idBytes.size.toShort())
                buf.put(idBytes)
            }

            // CONTENT
            buf.put(TLVType.CONTENT.v.toByte())
            buf.putInt(content.size)
            buf.put(content)

            val result = buf.array()
            // android.util.Log.d("BitchatFilePacket", "✅ Encoded successfully: ${result.size} bytes total")
            return result
        } catch (e: Exception) {
            android.util.Log.e("BitchatFilePacket", "❌ Encoding failed: ${e.message}", e)
            return null
        }
    }

    companion object {
        fun decode(data: ByteArray): BitchatFilePacket? {
            // android.util.Log.d("BitchatFilePacket", "🔄 Decoding ${data.size} bytes")
            try {
                var off = 0
                var name: String? = null
                var size: Long? = null
                var mime: String? = null
                var contentBytes: ByteArray? = null
                var chunkIdx = 0
                var totalChunks = 1
                var transferId: String = java.util.UUID.randomUUID().toString()

                while (off + 3 <= data.size) {
                    val tagVal = data[off].toUByte()
                    val t = TLVType.from(tagVal)
                    off += 1
                    
                    val len: Int
                    if (t == TLVType.CONTENT) {
                        if (off + 4 > data.size) break
                        len = ((data[off].toInt() and 0xFF) shl 24) or ((data[off + 1].toInt() and 0xFF) shl 16) or ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)
                        off += 4
                    } else {
                        if (off + 2 > data.size) break
                        len = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
                        off += 2
                    }

                    if (len < 0 || off + len > data.size) break
                    val value = data.copyOfRange(off, off + len)
                    off += len

                    when (t) {
                        TLVType.FILE_NAME -> name = String(value, Charsets.UTF_8)
                        TLVType.FILE_SIZE -> {
                             val bb = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
                             if (len == 4) size = bb.int.toLong() and 0xFFFFFFFFL
                             else if (len == 8) size = bb.long
                        }
                        TLVType.MIME_TYPE -> mime = String(value, Charsets.UTF_8)
                        TLVType.CONTENT -> {
                            if (contentBytes == null) contentBytes = value else contentBytes = (contentBytes!! + value)
                        }
                        TLVType.CHUNK_INDEX -> {
                             val bb = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
                             chunkIdx = bb.int
                        }
                        TLVType.TOTAL_CHUNKS -> {
                             val bb = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
                             totalChunks = bb.int
                        }
                        TLVType.TRANSFER_ID -> {
                             transferId = String(value, Charsets.UTF_8)
                        }
                        null -> { } // Skip unknown
                    }
                }
                val n = name ?: return null
                val c = contentBytes ?: return null
                val s = size ?: c.size.toLong()
                val m = mime ?: "application/octet-stream"
                // if (totalChunks > 1) android.util.Log.d("BitchatFilePacket", "🧩 Decoded Chunk $chunkIdx/$totalChunks ID=${transferId.take(8)}")
                val result = BitchatFilePacket(n, s, m, c, chunkIdx, totalChunks, transferId)
                return result
            } catch (e: Exception) {
                android.util.Log.e("BitchatFilePacket", "❌ Decoding failed: ${e.message}", e)
                return null
            }
        }
    }
}

