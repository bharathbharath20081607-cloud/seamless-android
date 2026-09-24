package com.seamless.android.features.file

import android.content.Context
import android.util.Log
import com.seamless.android.model.BitchatFilePacket
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages re-assembly of chunked file transfers.
 * Handles incoming chunks based on transferId and reconstructs the complete file.
 */
class IncomingTransferAssembler(private val context: Context) {
    
    companion object {
        private const val TAG = "IncomingTransferAssembler"
        private const val CHUNK_TIMEOUT_MS = 60_000L // 60 seconds to receive all chunks
    }
    
    // Track ongoing transfers: transferId -> TransferState
    private val activeTransfers = ConcurrentHashMap<String, TransferState>()
    
    /**
     * State for an ongoing multi-chunk transfer
     */
    private data class TransferState(
        val transferId: String,
        val totalChunks: Int,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
        val startTime: Long = System.currentTimeMillis()
    ) {
        fun isComplete(): Boolean = chunks.size == totalChunks
        fun isExpired(): Boolean = System.currentTimeMillis() - startTime > CHUNK_TIMEOUT_MS
    }
    
    /**
     * Handle an incoming file chunk.
     * Returns the complete file path if all chunks have been received, null otherwise.
     */
    fun handleChunk(packet: BitchatFilePacket): String? {
        // Clean up expired transfers
        cleanupExpiredTransfers()
        
        // Single-chunk file (no assembly needed)
        if (packet.totalChunks <= 1) {
            Log.d(TAG, "📦 Single-chunk file received: ${packet.fileName}")
            return FileUtils.saveIncomingFile(context, packet)
        }
        
        val transferId = packet.transferId
        Log.d(TAG, "📦 Received chunk ${packet.chunkIndex + 1}/${packet.totalChunks} for transfer $transferId")
        
        // Get or create transfer state
        val state = activeTransfers.getOrPut(transferId) {
            TransferState(
                transferId = transferId,
                totalChunks = packet.totalChunks,
                fileName = packet.fileName,
                fileSize = packet.fileSize,
                mimeType = packet.mimeType
            )
        }
        
        // Validate chunk consistency
        if (state.totalChunks != packet.totalChunks) {
            Log.e(TAG, "❌ Chunk count mismatch for $transferId: expected ${state.totalChunks}, got ${packet.totalChunks}")
            activeTransfers.remove(transferId)
            return null
        }
        
        // Store chunk content
        state.chunks[packet.chunkIndex] = packet.content
        
        // Check if transfer is complete
        if (state.isComplete()) {
            Log.d(TAG, "✅ All chunks received for $transferId, assembling file...")
            return assembleAndSave(state)
        }
        
        Log.d(TAG, "⏳ Waiting for more chunks: ${state.chunks.size}/${state.totalChunks}")
        return null
    }
    
    /**
     * Assemble all chunks into a complete file and save it
     */
    private fun assembleAndSave(state: TransferState): String? {
        return try {
            // Reconstruct file content by concatenating chunks in order
            val totalSize = state.chunks.values.sumOf { it.size }
            val completeContent = ByteArray(totalSize)
            var offset = 0
            
            for (i in 0 until state.totalChunks) {
                val chunk = state.chunks[i]
                if (chunk == null) {
                    Log.e(TAG, "❌ Missing chunk $i for ${state.transferId}")
                    activeTransfers.remove(state.transferId)
                    return null
                }
                chunk.copyInto(completeContent, offset)
                offset += chunk.size
            }
            
            Log.d(TAG, "✅ Assembled ${completeContent.size} bytes from ${state.totalChunks} chunks")
            
            // Create complete file packet
            val completePacket = BitchatFilePacket(
                fileName = state.fileName,
                fileSize = state.fileSize,
                mimeType = state.mimeType,
                content = completeContent,
                chunkIndex = 0,
                totalChunks = 1,
                transferId = state.transferId
            )
            
            // Save assembled file
            val filePath = FileUtils.saveIncomingFile(context, completePacket)
            
            // Clean up transfer state
            activeTransfers.remove(state.transferId)
            
            Log.d(TAG, "💾 Saved complete file: $filePath")
            filePath
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to assemble file for ${state.transferId}: ${e.message}", e)
            activeTransfers.remove(state.transferId)
            null
        }
    }
    
    /**
     * Remove transfers that have expired
     */
    private fun cleanupExpiredTransfers() {
        val expired = activeTransfers.filter { it.value.isExpired() }
        expired.forEach { (transferId, state) ->
            Log.w(TAG, "⏰ Transfer $transferId expired (${state.chunks.size}/${state.totalChunks} chunks received)")
            activeTransfers.remove(transferId)
        }
    }
    
    /**
     * Get debug info about active transfers
     */
    fun getDebugInfo(): String = buildString {
        appendLine("=== IncomingTransferAssembler ===")
        appendLine("Active transfers: ${activeTransfers.size}")
        activeTransfers.forEach { (id, state) ->
            appendLine("  $id: ${state.chunks.size}/${state.totalChunks} chunks (${state.fileName})")
        }
    }
}
