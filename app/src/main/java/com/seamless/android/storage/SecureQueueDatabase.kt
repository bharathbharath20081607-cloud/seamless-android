package com.seamless.android.storage

import android.content.Context
import android.util.Log
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.seamless.android.net.OnionMessageProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.security.SecureRandom
import java.util.UUID

/**
 * SecureQueueDatabase - Encrypted-at-rest message queue using SQLCipher
 * 
 * Features:
 * - AES-256-CBC encryption via SQLCipher
 * - Key derived from Android Keystore
 * - Transactional outbox pattern for reliable delivery
 * - Automatic cleanup of old messages
 * 
 * Tables:
 * - pending_messages: Messages waiting to be sent
 * - delivery_status: Tracking sent/delivered/read states
 * - sessions: Ratchet session metadata (actual keys in RatchetSessionManager)
 */
class SecureQueueDatabase private constructor(context: Context) {
    
    companion object {
        private const val TAG = "SecureQueueDatabase"
        private const val DATABASE_NAME = "seamless_queue.db"
        private const val DATABASE_VERSION = 1
        
        // TTL for messages
        const val MESSAGE_TTL_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L     // 1 hour
        private const val KEY_PREFS_NAME = "seamless_queue_key_store"
        private const val KEY_DB_KEY = "queue_db_key"
        
        @Volatile
        private var instance: SecureQueueDatabase? = null
        
        fun getInstance(context: Context): SecureQueueDatabase {
            return instance ?: synchronized(this) {
                instance ?: SecureQueueDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    
    // Database key derived from Android Keystore
    private val dbKey: ByteArray by lazy { deriveDbKey(context) }
    
    // SQLCipher database helper
    private val dbHelper: QueueDbHelper by lazy { QueueDbHelper(context, dbKey) }
    
    // Delivery status updates
    private val _deliveryUpdates = MutableSharedFlow<DeliveryUpdate>(replay = 0)
    val deliveryUpdates: SharedFlow<DeliveryUpdate> = _deliveryUpdates.asSharedFlow()
    
    init {
        // Initialize SQLCipher
        System.loadLibrary("sqlcipher")
        startCleanupLoop()
    }
    
    /**
     * Pending message model
     */
    data class QueuedMessage(
        val id: String = UUID.randomUUID().toString(),
        val recipientOnion: String,
        val encryptedPayload: ByteArray,
        var state: OnionMessageProtocol.DeliveryState = OnionMessageProtocol.DeliveryState.PENDING,
        var retryCount: Int = 0,
        var nextRetryAt: Long = System.currentTimeMillis(),
        val createdAt: Long = System.currentTimeMillis(),
        var lastAttemptAt: Long = 0,
        val sequenceNumber: Long = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QueuedMessage) return false
            return id == other.id
        }
        override fun hashCode(): Int = id.hashCode()
    }
    
    /**
     * Delivery update event
     */
    data class DeliveryUpdate(
        val messageId: String,
        val newState: OnionMessageProtocol.DeliveryState,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Enqueue a message for delivery
     */
    suspend fun enqueue(
        recipientOnion: String,
        frame: OnionMessageProtocol.OnionFrame
    ): String = mutex.withLock {
        val db = dbHelper.writableDatabase
        val messageId = frame.messageId.toString()
        val encoded = frame.encode()
        
        db.beginTransaction()
        try {
            // Get next sequence number for recipient
            val seqCursor = db.rawQuery(
                "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM pending_messages WHERE recipient_onion = ?",
                arrayOf(recipientOnion)
            )
            val sequenceNumber = if (seqCursor.moveToFirst()) seqCursor.getLong(0) else 1L
            seqCursor.close()
            
            // Insert message
            db.execSQL(
                """INSERT INTO pending_messages 
                   (id, recipient_onion, encrypted_payload, state, retry_count, next_retry_at, created_at, sequence_number)
                   VALUES (?, ?, ?, ?, 0, ?, ?, ?)""",
                arrayOf(
                    messageId,
                    recipientOnion,
                    encoded,
                    OnionMessageProtocol.DeliveryState.PENDING.ordinal,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    sequenceNumber
                )
            )
            
            // Track delivery status
            db.execSQL(
                """INSERT INTO delivery_status (message_id, state)
                   VALUES (?, ?)""",
                arrayOf(messageId, OnionMessageProtocol.DeliveryState.PENDING.ordinal)
            )
            
            db.setTransactionSuccessful()
            Log.d(TAG, "📬 Enqueued message $messageId for $recipientOnion (seq: $sequenceNumber)")
            
        } finally {
            db.endTransaction()
        }
        
        messageId
    }
    
    /**
     * Get pending messages ready for delivery
     */
    suspend fun getPendingMessages(
        recipientOnion: String? = null,
        limit: Int = 100
    ): List<QueuedMessage> = mutex.withLock {
        val db = dbHelper.readableDatabase
        val now = System.currentTimeMillis()
        
        val query = if (recipientOnion != null) {
            """SELECT * FROM pending_messages 
               WHERE recipient_onion = ? AND state = ? AND next_retry_at <= ?
               ORDER BY sequence_number ASC LIMIT ?"""
        } else {
            """SELECT * FROM pending_messages 
               WHERE state = ? AND next_retry_at <= ?
               ORDER BY recipient_onion, sequence_number ASC LIMIT ?"""
        }
        
        val args = if (recipientOnion != null) {
            arrayOf(recipientOnion, OnionMessageProtocol.DeliveryState.PENDING.ordinal.toString(), now.toString(), limit.toString())
        } else {
            arrayOf(OnionMessageProtocol.DeliveryState.PENDING.ordinal.toString(), now.toString(), limit.toString())
        }
        
        val cursor = db.rawQuery(query, args)
        val messages = mutableListOf<QueuedMessage>()
        
        while (cursor.moveToNext()) {
            messages.add(cursorToMessage(cursor))
        }
        cursor.close()
        
        messages
    }
    
    /**
     * Mark message as sent (awaiting ACK)
     */
    suspend fun markSent(messageId: String) = mutex.withLock {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        
        db.execSQL(
            """UPDATE pending_messages SET state = ?, last_attempt_at = ? WHERE id = ?""",
            arrayOf(OnionMessageProtocol.DeliveryState.SENT.ordinal, now, messageId)
        )
        
        db.execSQL(
            """UPDATE delivery_status SET state = ?, sent_at = ? WHERE message_id = ?""",
            arrayOf(OnionMessageProtocol.DeliveryState.SENT.ordinal, now, messageId)
        )
        
        emitUpdate(messageId, OnionMessageProtocol.DeliveryState.SENT)
    }
    
    /**
     * Mark message as delivered (ACK received)
     */
    suspend fun markDelivered(messageId: String) = mutex.withLock {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        
        db.beginTransaction()
        try {
            // Update delivery status
            db.execSQL(
                """UPDATE delivery_status SET state = ?, delivered_at = ? WHERE message_id = ?""",
                arrayOf(OnionMessageProtocol.DeliveryState.DELIVERED.ordinal, now, messageId)
            )
            
            // Remove from pending queue (delivered successfully)
            db.execSQL("DELETE FROM pending_messages WHERE id = ?", arrayOf(messageId))
            
            db.setTransactionSuccessful()
            Log.i(TAG, "✅ Message $messageId delivered")
            
        } finally {
            db.endTransaction()
        }
        
        emitUpdate(messageId, OnionMessageProtocol.DeliveryState.DELIVERED)
    }
    
    /**
     * Mark message as read
     */
    suspend fun markRead(messageId: String) = mutex.withLock {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        
        db.execSQL(
            """UPDATE delivery_status SET state = ?, read_at = ? WHERE message_id = ?""",
            arrayOf(OnionMessageProtocol.DeliveryState.READ.ordinal, now, messageId)
        )
        
        emitUpdate(messageId, OnionMessageProtocol.DeliveryState.READ)
    }
    
    /**
     * Handle send failure with exponential backoff
     */
    suspend fun handleSendFailure(messageId: String, reason: String) = mutex.withLock {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        
        // Get current retry count
        val cursor = db.rawQuery(
            "SELECT retry_count FROM pending_messages WHERE id = ?",
            arrayOf(messageId)
        )
        
        if (!cursor.moveToFirst()) {
            cursor.close()
            return@withLock
        }
        
        val retryCount = cursor.getInt(0) + 1
        cursor.close()
        
        if (retryCount >= 50) { // MAX_RETRY_ATTEMPTS
            // Mark as failed
            db.execSQL(
                """UPDATE pending_messages SET state = ? WHERE id = ?""",
                arrayOf(OnionMessageProtocol.DeliveryState.FAILED.ordinal, messageId)
            )
            
            db.execSQL(
                """UPDATE delivery_status SET state = ?, fail_reason = ? WHERE message_id = ?""",
                arrayOf(OnionMessageProtocol.DeliveryState.FAILED.ordinal, reason, messageId)
            )
            
            emitUpdate(messageId, OnionMessageProtocol.DeliveryState.FAILED)
            Log.e(TAG, "❌ Message $messageId failed after $retryCount attempts: $reason")
            
        } else {
            // Calculate exponential backoff with jitter
            val baseDelay = 5000L
            val maxDelay = 300000L
            val delay = minOf(baseDelay * (1 shl retryCount) + (Math.random() * 1000).toLong(), maxDelay)
            val nextRetry = now + delay
            
            db.execSQL(
                """UPDATE pending_messages 
                   SET retry_count = ?, next_retry_at = ?, last_attempt_at = ?, state = ?
                   WHERE id = ?""",
                arrayOf(retryCount, nextRetry, now, OnionMessageProtocol.DeliveryState.PENDING.ordinal, messageId)
            )
            
            Log.w(TAG, "🔄 Message $messageId retry $retryCount/50 in ${delay}ms")
        }
    }
    
    /**
     * Get delivery status for a message
     */
    suspend fun getDeliveryStatus(messageId: String): OnionMessageProtocol.DeliveryState? = mutex.withLock {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT state FROM delivery_status WHERE message_id = ?",
            arrayOf(messageId)
        )
        
        val state = if (cursor.moveToFirst()) {
            OnionMessageProtocol.DeliveryState.entries[cursor.getInt(0)]
        } else {
            null
        }
        cursor.close()
        state
    }
    
    /**
     * Get stats
     */
    suspend fun getStats(): QueueStats = mutex.withLock {
        val db = dbHelper.readableDatabase
        
        val cursor = db.rawQuery(
            """SELECT state, COUNT(*) FROM pending_messages GROUP BY state""",
            null
        )
        
        var pending = 0
        var sending = 0
        var failed = 0
        
        while (cursor.moveToNext()) {
            val state = OnionMessageProtocol.DeliveryState.entries[cursor.getInt(0)]
            val count = cursor.getInt(1)
            when (state) {
                OnionMessageProtocol.DeliveryState.PENDING -> pending = count
                OnionMessageProtocol.DeliveryState.SENDING, 
                OnionMessageProtocol.DeliveryState.SENT -> sending += count
                OnionMessageProtocol.DeliveryState.FAILED -> failed = count
                else -> {}
            }
        }
        cursor.close()
        
        // Get recipient count
        val recipientCursor = db.rawQuery(
            "SELECT COUNT(DISTINCT recipient_onion) FROM pending_messages", null
        )
        val recipientCount = if (recipientCursor.moveToFirst()) recipientCursor.getInt(0) else 0
        recipientCursor.close()
        
        QueueStats(pending, sending, failed, recipientCount)
    }
    
    data class QueueStats(
        val pendingCount: Int,
        val sendingCount: Int,
        val failedCount: Int,
        val recipientCount: Int
    )
    
    /**
     * Clear all messages
     */
    suspend fun clearAll() = mutex.withLock {
        val db = dbHelper.writableDatabase
        db.execSQL("DELETE FROM pending_messages")
        db.execSQL("DELETE FROM delivery_status")
        Log.i(TAG, "🗑️ Cleared all queued messages")
    }
    
    /**
     * Close database
     */
    fun close() {
        scope.cancel()
        dbHelper.close()
    }
    
    // ========== Private Helpers ==========
    
    @Suppress("DEPRECATION")
    private fun deriveDbKey(context: Context): ByteArray {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                KEY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            prefs.getString(KEY_DB_KEY, null)?.let { encoded ->
                val decoded = Base64.decode(encoded, Base64.NO_WRAP)
                if (decoded.size == 32) return decoded
                Log.w(TAG, "Invalid stored queue DB key length, rotating key")
            }

            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            prefs.edit()
                .putString(KEY_DB_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
                .apply()
            key
        } catch (e: Exception) {
            Log.e(TAG, "Secure queue key storage unavailable: ${e.message}")
            throw IllegalStateException("Secure queue key storage unavailable", e)
        }
    }
    
    private fun cursorToMessage(cursor: android.database.Cursor): QueuedMessage {
        return QueuedMessage(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            recipientOnion = cursor.getString(cursor.getColumnIndexOrThrow("recipient_onion")),
            encryptedPayload = cursor.getBlob(cursor.getColumnIndexOrThrow("encrypted_payload")),
            state = OnionMessageProtocol.DeliveryState.entries[cursor.getInt(cursor.getColumnIndexOrThrow("state"))],
            retryCount = cursor.getInt(cursor.getColumnIndexOrThrow("retry_count")),
            nextRetryAt = cursor.getLong(cursor.getColumnIndexOrThrow("next_retry_at")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            lastAttemptAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_attempt_at")),
            sequenceNumber = cursor.getLong(cursor.getColumnIndexOrThrow("sequence_number"))
        )
    }
    
    private suspend fun emitUpdate(messageId: String, state: OnionMessageProtocol.DeliveryState) {
        _deliveryUpdates.emit(DeliveryUpdate(messageId, state))
    }
    
    private fun startCleanupLoop() {
        scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                cleanup()
            }
        }
    }
    
    private suspend fun cleanup() = mutex.withLock {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        val cutoff = now - MESSAGE_TTL_MS
        
        // Delete old messages
        val deleted = db.rawQuery(
            "SELECT COUNT(*) FROM pending_messages WHERE created_at < ?",
            arrayOf(cutoff.toString())
        )
        val count = if (deleted.moveToFirst()) deleted.getInt(0) else 0
        deleted.close()
        
        if (count > 0) {
            db.execSQL("DELETE FROM pending_messages WHERE created_at < ?", arrayOf(cutoff))
            Log.d(TAG, "🧹 Cleaned up $count old messages")
        }
        
        // Delete old delivery status entries
        db.execSQL(
            """DELETE FROM delivery_status 
               WHERE message_id NOT IN (SELECT id FROM pending_messages)
               AND COALESCE(read_at, delivered_at, sent_at, 0) < ?""",
            arrayOf(cutoff)
        )
    }
    
    /**
     * SQLCipher database helper
     */
    private class QueueDbHelper(
        context: Context,
        private val dbKey: ByteArray
    ) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        dbKey,
        null,
        DATABASE_VERSION,
        0,
        null,
        null,
        true
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE pending_messages (
                    id TEXT PRIMARY KEY,
                    recipient_onion TEXT NOT NULL,
                    encrypted_payload BLOB NOT NULL,
                    state INTEGER DEFAULT 0,
                    retry_count INTEGER DEFAULT 0,
                    next_retry_at INTEGER,
                    created_at INTEGER NOT NULL,
                    last_attempt_at INTEGER DEFAULT 0,
                    sequence_number INTEGER NOT NULL
                )
            """)
            
            db.execSQL("""
                CREATE INDEX idx_pending_recipient ON pending_messages(recipient_onion, state, next_retry_at)
            """)
            
            db.execSQL("""
                CREATE TABLE delivery_status (
                    message_id TEXT PRIMARY KEY,
                    state INTEGER NOT NULL,
                    sent_at INTEGER,
                    delivered_at INTEGER,
                    read_at INTEGER,
                    fail_reason TEXT
                )
            """)
            
            Log.i(TAG, "✅ Created encrypted queue database")
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle future migrations
        }
    }
}
