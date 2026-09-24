package com.seamless.android.mesh

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seamless.android.data.MessageRepository
import com.seamless.android.model.BitchatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Sneakernet operations (exporting/importing messages via file)
 */
class SneakernetManager(
    private val context: Context,
    private val repository: MessageRepository
) {
    private val gson = Gson()

    /**
     * Export all messages to a JSON file in the app's cache directory
     * Returns the File object if successful, null otherwise
     */
    /**
     * Export all messages to a JSON file in the app's cache directory using streaming
     * Returns the File object if successful, null otherwise
     */
    suspend fun exportMessages(): File? = withContext(Dispatchers.IO) {
        try {
            // Use a cursor or paging if the repository supports it, but for now we stream the list
            // Assuming getAllMessagesSync returns a List, we still load it into memory here.
            // Ideally, the repository should return a Cursor or Flow to stream from DB.
            // However, to fix the JSON OOM, we can at least stream the JSON writing.
            // If the list itself is too big for memory, we need a repository change (out of scope for now).
            // We will assume the list fits in memory but the JSON string construction was the bottleneck.
            val messages = repository.getAllMessagesSync()
            if (messages.isEmpty()) return@withContext null

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "seamless_chat_export_$timestamp.json"
            val file = File(context.cacheDir, fileName)

            com.google.gson.stream.JsonWriter(FileWriter(file)).use { writer ->
                writer.setIndent("  ")
                writer.beginArray()
                messages.forEach { message ->
                    gson.toJson(message, BitchatMessage::class.java, writer)
                }
                writer.endArray()
            }
            
            Log.d(TAG, "Exported ${messages.size} messages to ${file.absolutePath}")
            return@withContext file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export messages", e)
            return@withContext null
        }
    }

    /**
     * Import messages from a JSON file URI using streaming
     * Returns the number of messages imported
     */
    suspend fun importMessages(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext 0
            val reader = InputStreamReader(inputStream)
            
            var importedCount = 0
            com.google.gson.stream.JsonReader(reader).use { jsonReader ->
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val message = gson.fromJson<BitchatMessage>(jsonReader, BitchatMessage::class.java)
                    repository.insertMessage(message)
                    importedCount++
                }
                jsonReader.endArray()
            }
            
            Log.d(TAG, "Imported $importedCount messages from URI")
            return@withContext importedCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import messages", e)
            return@withContext 0
        }
    }

    companion object {
        private const val TAG = "SneakernetManager"
    }
}
