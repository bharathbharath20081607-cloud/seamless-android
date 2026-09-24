package com.seamless.android.net

import android.util.Log
import android.content.Context
import com.seamless.android.crypto.EncryptionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * MailboxClient — Connects to mailbox servers via Tor to fetch offline messages.
 *
 * Now uses OkHttp REST calls routed through Tor SOCKS5 proxy via OkHttpProvider.
 *
 * REST API (matches MailboxService):
 *   POST /api/files/{inboxId}    — Upload encrypted message blob to a folder
 *   GET  /api/files/{folderId}   — Download messages from a folder
 *   DELETE /api/files/{folderId}/{fileId} — Confirm receipt, delete from mailbox
 *
 * All requests are routed through Tor SOCKS5 proxy using OkHttpProvider.httpClient().
 */
class MailboxClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MailboxClient"

        // Foreground Tor sessions should pick up an offline text message quickly.
        // Android background scheduling remains best-effort and must not promise
        // instant delivery while the process is stopped.
        const val POLL_INTERVAL_MS = 15_000L

        @Volatile
        private var instance: MailboxClient? = null

        fun getInstance(context: Context): MailboxClient {
            return instance ?: synchronized(this) {
                instance ?: MailboxClient(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Registered mailbox
     */
    data class RegisteredMailbox(
        val onionAddress: String,
        val ownerPublicKey: ByteArray,
        val accessToken: String? = null,
        val isOwnedByClient: Boolean = false,
        var lastPollTime: Long = 0,
        var isActive: Boolean = true
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RegisteredMailbox) return false
            return onionAddress == other.onionAddress
        }
        override fun hashCode(): Int = onionAddress.hashCode()
    }

    /**
     * Fetched message from mailbox
     */
    data class FetchedMessage(
        val id: String,
        val mailboxOnion: String,
        val encryptedPayload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FetchedMessage) return false
            return id == other.id
        }
        override fun hashCode(): Int = id.hashCode()
    }

    // Registered mailboxes
    private val mailboxes = mutableListOf<RegisteredMailbox>()
    private val mutex = Mutex()

    // Polling state
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null

    // Message callback
    interface MessageCallback {
        /** Returns only the message IDs safely committed by the client. */
        suspend fun onMessagesReceived(messages: List<FetchedMessage>): Set<String>
    }

    private var messageCallback: MessageCallback? = null

    // Our identity
    private var encryptionService: EncryptionService? = null
    private var myOwnerId: String = ""

    /**
     * Initialize with encryption service
     */
    fun initialize(encryptionService: EncryptionService) {
        this.encryptionService = encryptionService
        this.myOwnerId = encryptionService.getIdentityFingerprint()
    }

    /**
     * Check if this client has been initialized with an encryption service
     */
    fun isInitialized(): Boolean = encryptionService != null

    /**
     * Set callback for received messages
     */
    fun setMessageCallback(callback: MessageCallback?) {
        messageCallback = callback
    }

    // ===== OkHttp client (routed through Tor SOCKS5) =====

    private val httpClient: OkHttpClient
        get() = OkHttpProvider.httpClient()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Build base URL for a mailbox (http over Tor SOCKS5)
     */
    private fun baseUrl(mailboxOnion: String): String {
        val parts = mailboxOnion.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: MailboxService.MAILBOX_PORT
        return "http://$host:$port"
    }

    // ===== Registration =====

    /**
     * Adds the user's already-authorized mailbox to the polling set without
     * sending a setup request through Tor. This is used when the mailbox is
     * hosted locally and authorization was registered directly by the owner.
     */
    suspend fun registerLocalMailbox(mailboxOnion: String, accessToken: String): Boolean = mutex.withLock {
        if (encryptionService == null) {
            Log.e(TAG, "Cannot register local mailbox - MailboxClient not initialized")
            return@withLock false
        }

        mailboxes.removeAll { it.isOwnedByClient && it.onionAddress != mailboxOnion }
        if (mailboxes.any { it.onionAddress == mailboxOnion }) return@withLock true

        mailboxes.add(
            RegisteredMailbox(
                onionAddress = mailboxOnion,
                ownerPublicKey = ByteArray(0),
                accessToken = accessToken,
                isOwnedByClient = true
            )
        )
        Log.i(TAG, "Registered local mailbox for polling: ${redactEndpoint(mailboxOnion)}")
        true
    }

    /**
     * Unregister a mailbox
     */
    suspend fun unregisterMailbox(mailboxOnion: String) = mutex.withLock {
        mailboxes.removeAll { it.onionAddress == mailboxOnion }
        Log.i(TAG, "🗑️ Unregistered mailbox: ${redactEndpoint(mailboxOnion)}")
    }

    /**
     * Get list of registered mailboxes
     */
    fun getMailboxes(): List<String> = mailboxes.map { it.onionAddress }

    // ===== Polling =====

    /**
     * Start automatic polling
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return

        pollJob = scope.launch {
            while (isActive) {
                try {
                    pollAllMailboxes()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }

        Log.i(TAG, "📤 Started mailbox polling (REST)")
    }

    /**
     * Stop automatic polling
     */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        Log.i(TAG, "🛑 Stopped mailbox polling")
    }

    /**
     * Poll all registered mailboxes via GET /api/files/{folderId}
     */
    suspend fun pollAllMailboxes(): List<FetchedMessage> {
        val allMessages = mutableListOf<FetchedMessage>()
        // Registration can change when Tor recreates the local hidden service.
        // Use one locked snapshot so the poller never observes a half-mutated list.
        val registeredMailboxes = mutex.withLock { mailboxes.toList() }

        for (mailbox in registeredMailboxes) {
            if (!mailbox.isActive) continue

            try {
                val messages = pollMailbox(mailbox.onionAddress, mailbox.accessToken)
                allMessages.addAll(messages)

                mutex.withLock {
                    mailbox.lastPollTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to poll ${redactEndpoint(mailbox.onionAddress)}: ${e.message}")
            }
        }

        if (allMessages.isNotEmpty()) {
            // Deliver to callback FIRST, then confirm receipt on server.
            // This prevents message loss if the app crashes between fetch and processing.
            val committedIds = messageCallback?.onMessagesReceived(allMessages).orEmpty()

            // Now safe to confirm receipt — delete from mailbox server
            for (msg in allMessages) {
                if (msg.id !in committedIds) continue
                try {
                    deleteFromMailbox(msg.mailboxOnion, msg.id, registeredMailboxes.firstOrNull {
                        it.onionAddress == msg.mailboxOnion
                    }?.accessToken)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to confirm receipt for ${msg.id}: ${e.message}")
                }
            }
        }

        return allMessages
    }

    /**
     * Poll a specific mailbox via REST.
     * GET /api/files/{myOwnerId} to retrieve pending messages.
     */
    suspend fun pollMailbox(mailboxOnion: String, accessToken: String? = null): List<FetchedMessage> {
        Log.d(TAG, "📤 Polling mailbox via REST: ${redactEndpoint(mailboxOnion)}")

        val messages = mutableListOf<FetchedMessage>()

        try {
            val request = Request.Builder()
                .url("${baseUrl(mailboxOnion)}/api/files/$myOwnerId")
                .get()
                .apply {
                    accessToken?.takeIf { it.isNotBlank() }?.let {
                        header("X-Seamless-Mailbox-Capability", it)
                    }
                }
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            response.use {
                if (!it.isSuccessful) {
                    Log.e(TAG, "Poll failed: ${it.code}")
                    return messages
                }

                val body = it.body?.string() ?: return messages
                val json = JSONObject(body)
                val files = json.optJSONArray("files") ?: return messages

                Log.d(TAG, "📥 Mailbox has ${files.length()} messages")

                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val fileId = file.getString("fileId")
                    val payloadBase64 = file.getString("payload")
                    val payload = android.util.Base64.decode(payloadBase64, android.util.Base64.NO_WRAP)

                    messages.add(FetchedMessage(
                        id = fileId,
                        mailboxOnion = mailboxOnion,
                        encryptedPayload = payload
                    ))
                }
            }

            // NOTE: Deletion is now handled by pollAllMailboxes() AFTER callback processing.
            // This prevents message loss if the app crashes between fetch and processing.

        } catch (e: Exception) {
            Log.e(TAG, "Error polling mailbox: ${e.message}")
        }

        return messages
    }

    // ===== Store =====

    /**
     * Store a message in a mailbox for another user.
     * POST /api/files/{recipientId} with encrypted payload.
     */
    suspend fun storeInMailbox(
        mailboxOnion: String,
        recipientId: String,
        encryptedPayload: ByteArray
    ): Boolean {
        Log.d(TAG, "📥 Storing message in mailbox via REST: ${redactEndpoint(mailboxOnion)}")

        return try {
            // NanoHTTPD's generic form parser is text-oriented. Wrap opaque
            // encrypted bytes in Base64 JSON so a mailbox can never alter the
            // ciphertext through charset conversion before we retrieve it.
            val body = JSONObject().apply {
                put(
                    "payload",
                    android.util.Base64.encodeToString(
                        encryptedPayload,
                        android.util.Base64.NO_WRAP
                    )
                )
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("${baseUrl(mailboxOnion)}/api/files/$recipientId")
                .post(body)
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            response.use {
                if (it.isSuccessful) {
                    Log.i(TAG, "✅ Message stored in mailbox via REST")
                    true
                } else {
                    Log.e(TAG, "Failed to store message: ${it.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error storing in mailbox: ${e.message}")
            false
        }
    }

    // ===== Delete =====

    /**
     * Delete a message from mailbox after successful receipt.
     * DELETE /api/files/{folderId}/{fileId}
     */
    private suspend fun deleteFromMailbox(mailboxOnion: String, messageId: String, accessToken: String?) {
        try {
            val request = Request.Builder()
                .url("${baseUrl(mailboxOnion)}/api/files/$myOwnerId/$messageId")
                .delete()
                .apply {
                    accessToken?.takeIf { it.isNotBlank() }?.let {
                        header("X-Seamless-Mailbox-Capability", it)
                    }
                }
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            response.use {
                if (!it.isSuccessful) {
                    throw IllegalStateException("Mailbox delete failed: HTTP ${it.code}")
                }
            }

            Log.d(TAG, "🗑️ Deleted message $messageId from mailbox")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from mailbox: ${e.message}")
        }
    }

    private fun redactEndpoint(endpoint: String): String = endpoint
        .substringBefore(':')
        .take(12)
        .plus("...")
}
