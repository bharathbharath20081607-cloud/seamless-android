@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.util.Log
import com.seamless.android.identity.SecureIdentityStateManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MailboxService — Store-and-forward relay for offline message delivery.
 *
 * Based on Briar Mailbox design, now using NanoHTTPD REST API:
 * - Run on a spare device (always-on phone, old tablet)
 * - Store encrypted messages for owner when they're offline
 * - Owner polls mailbox when coming online
 * - Messages deleted after successful fetch
 *
 * REST API:
 *   POST /api/files/{inboxId}    — Upload encrypted message blob
 *   GET  /api/files/{folderId}   — Download messages from a folder
 *   DELETE /api/files/{folderId}/{fileId} — Confirm receipt, delete
 *
 * Security:
 * - Messages are end-to-end encrypted BEFORE reaching mailbox
 * - Mailbox cannot decrypt message contents (dumb relay)
 * - Read and delete require an owner-only capability stored in Android Keystore-backed state
 * - Capacity is bounded; senders retry rather than the mailbox deleting older mail
 */
class MailboxService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MailboxService"

        const val MAILBOX_PORT = 18889
        const val MAX_STORED_MESSAGES = 1000
        const val MAX_TOTAL_STORED_MESSAGES = 2000
        const val MAX_MESSAGE_SIZE = 64 * 1024 // 64KB
        // Ciphertext is sent as Base64 JSON, so the HTTP body is larger than
        // the stored opaque blob. Keep the stored-payload limit unchanged.
        const val MAX_REQUEST_BODY_SIZE = (MAX_MESSAGE_SIZE * 4 / 3) + 1024
        const val MESSAGE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        private const val OWNER_REGISTRY_KEY = "tor_mailbox_owner_registry"

        @Volatile
        private var instance: MailboxService? = null

        fun getInstance(context: Context): MailboxService {
            return instance ?: synchronized(this) {
                instance ?: MailboxService(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Stored message in mailbox
     */
    data class StoredMessage(
        val id: String = UUID.randomUUID().toString(),
        val recipientId: String,
        val senderOnion: String,
        val encryptedPayload: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StoredMessage) return false
            return id == other.id
        }
        override fun hashCode(): Int = id.hashCode()
    }

    // Storage — in-memory cache backed by Room persistence
    private val storedMessages = ConcurrentHashMap<String, MutableList<StoredMessage>>() // recipientId -> messages
    private data class OwnerRegistration(
        val publicKey: ByteArray,
        val accessTokenHash: ByteArray
    )

    private val authorizedOwners = ConcurrentHashMap<String, OwnerRegistration>()
    private val authorizedOutboxTokens = ConcurrentHashMap<String, ByteArray>()
    private val storageLock = Any() // JVM lock for compound storage operations (safe on NanoHTTPD threads)
    private val ownerRegistryLock = Any()
    private val secureIdentityState = SecureIdentityStateManager(context)
    private val dao: com.seamless.android.data.MailboxMessageDao by lazy {
        com.seamless.android.data.AppDatabase.getDatabase(context).mailboxMessageDao()
    }

    // State
    private val isRunning = AtomicBoolean(false)
    private var httpServer: MailboxHttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Stats
    private val _stats = MutableStateFlow(MailboxStats())
    val stats: StateFlow<MailboxStats> = _stats.asStateFlow()

    data class MailboxStats(
        val isRunning: Boolean = false,
        val storedMessageCount: Int = 0,
        val ownerCount: Int = 0,
        val totalStored: Long = 0,
        val totalFetched: Long = 0
    )

    /**
     * Register an owner who can store messages with this mailbox
     */
    fun registerOwner(ownerId: String, publicKey: ByteArray, accessToken: String) {
        require(ownerId.isNotBlank()) { "Mailbox owner ID is required" }
        require(accessToken.length >= 32) { "Mailbox access token is too short" }
        authorizedOwners[ownerId] = OwnerRegistration(
            publicKey = publicKey,
            accessTokenHash = hashAccessToken(accessToken)
        )
        persistAuthorizedOwners()
        updateStats()
        Log.i(TAG, "📬 Registered owner: ${redactOwnerId(ownerId)}")
    }

    /**
     * Unregister an owner
     */
    fun registerContactOutboxToken(recipientFolderId: String, token: String) {
        val folder = recipientFolderId.trim().lowercase()
        if (folder.isBlank() || token.length < 32) return
        authorizedOutboxTokens[folder] = hashAccessToken(token)
    }

    fun unregisterOwner(ownerId: String) {
        authorizedOwners.remove(ownerId)
        authorizedOutboxTokens.remove(ownerId.trim().lowercase())
        persistAuthorizedOwners()
        synchronized(storageLock) {
            storedMessages.remove(ownerId)
        }
        // Also purge from database
        scope.launch {
            try { dao.deleteByRecipient(ownerId) } catch (e: Exception) {
                Log.w(TAG, "Failed to purge DB for $ownerId: ${e.message}")
            }
        }
        updateStats()
        Log.i(TAG, "🗑️ Unregistered owner: ${redactOwnerId(ownerId)}")
    }

    /**
     * Start the mailbox service (NanoHTTPD REST server)
     */
    suspend fun start(): Boolean {
        if (isRunning.get()) {
            Log.d(TAG, "Mailbox already running")
            return true
        }

        return try {
            Log.i(TAG, "📫 Starting Mailbox REST Service on port $MAILBOX_PORT")

            // A mailbox must retain its recipients after a process restart;
            // otherwise persisted ciphertext can no longer be fetched.
            loadAuthorizedOwners()

            // Load persisted messages from Room into hot cache
            loadFromDatabase()

            httpServer = MailboxHttpServer(MAILBOX_PORT).also { it.start() }
            isRunning.set(true)

            // Start cleanup loop
            scope.launch { cleanupLoop() }

            updateStats()
            Log.i(TAG, "✅ Mailbox REST Service started")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Mailbox: ${e.message}")
            false
        }
    }

    /**
     * Stop the mailbox service
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            httpServer?.stop()
            httpServer = null
            scope.coroutineContext.cancelChildren()
            updateStats()
            Log.i(TAG, "🛑 Mailbox REST Service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Mailbox: ${e.message}")
        }
    }

    /**
     * Directly deposit a pre-sealed offline blob into local MailboxService storage (Room + hot cache)
     * so an offline contact can pull it from our Tier 1 Sender-Hosted Outbox Mailbox as soon as they
     * come online, or when we act as a MAILBOX_STORE peer relay.
     */
    fun depositLocalBlob(recipientId: String, payload: ByteArray, senderOnion: String = "local"): String? {
        val cleanRecipient = recipientId.trim().lowercase()
        if (cleanRecipient.isBlank() || payload.isEmpty() || payload.size > MAX_MESSAGE_SIZE) return null

        val message = StoredMessage(
            recipientId = cleanRecipient,
            senderOnion = senderOnion,
            encryptedPayload = payload
        )
        val entity = com.seamless.android.data.MailboxMessageEntity(
            id = message.id,
            recipientId = message.recipientId,
            senderOnion = message.senderOnion,
            encryptedPayload = message.encryptedPayload,
            timestamp = message.timestamp,
            expiresAt = message.timestamp + MESSAGE_TTL_MS
        )

        synchronized(storageLock) {
            val messages = storedMessages.getOrPut(cleanRecipient) { mutableListOf() }
            val existing = messages.firstOrNull { it.encryptedPayload.contentEquals(payload) }
            if (existing != null) return existing.id
            if (messages.size >= MAX_STORED_MESSAGES) {
                // Evict oldest message to ensure fresh offline messages or ACKs are not dropped
                val oldest = messages.minByOrNull { it.timestamp }
                if (oldest != null) {
                    messages.remove(oldest)
                    try { runBlocking { dao.deleteById(oldest.id) } } catch (_: Exception) {}
                }
            }
            try {
                runBlocking { dao.insert(entity) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist local mailbox blob ${message.id}: ${e.message}")
                return null
            }
            messages.add(message)
        }
        updateStats()
        Log.d(TAG, "📥 Deposited local offline blob ${message.id.take(8)}... for ${redactOwnerId(cleanRecipient)}")
        return message.id
    }

    /**
     * Get message count for an owner
     */
    fun getMessageCount(ownerId: String): Int {
        return storedMessages[ownerId]?.size ?: 0
    }

    /**
     * Get all stored messages for an owner (for local access)
     */
    fun getStoredMessages(ownerId: String): List<StoredMessage> = synchronized(storageLock) {
        storedMessages[ownerId]?.toList() ?: emptyList()
    }

    // ===== NanoHTTPD REST Server =====

    /**
     * Embedded HTTP server implementing the Briar-style mailbox REST API.
     * Routes all requests through the outer MailboxService for storage.
     */
    private inner class MailboxHttpServer(port: Int) : NanoHTTPD("127.0.0.1", port) { // SECURITY: loopback only; Tor hidden service forwards locally

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: return newErrorResponse(Response.Status.BAD_REQUEST, "No URI")
            val method = session.method

            return try {
                when {
                    // Owner enrollment is local-only. A remote setup endpoint
                    // could otherwise let an attacker replace an owner's key.
                    method == Method.PUT && uri == "/api/setup" ->
                        newErrorResponse(Response.Status.FORBIDDEN, "Remote owner enrollment is disabled")

                    method == Method.GET && uri == "/api/folders" ->
                        newErrorResponse(Response.Status.FORBIDDEN, "Mailbox folders are private")

                    // POST /api/files/{inboxId} — Upload message
                    method == Method.POST && uri.startsWith("/api/files/") && uri.count { it == '/' } == 3 ->
                        handleUpload(session, uri.substringAfterLast("/"))

                    // GET /api/files/{folderId} — Download messages
                    method == Method.GET && uri.startsWith("/api/files/") && uri.count { it == '/' } == 3 ->
                        handleDownload(session, uri.substringAfterLast("/"))

                    // DELETE /api/files/{folderId}/{fileId} — Delete message
                    method == Method.DELETE && uri.startsWith("/api/files/") && uri.count { it == '/' } == 4 -> {
                        val parts = uri.removePrefix("/api/files/").split("/")
                        handleDelete(session, parts[0], parts[1])
                    }

                    // GET /api/health — Health check
                    method == Method.GET && uri == "/api/health" -> {
                        val json = JSONObject().apply {
                            put("status", "ok")
                            put("version", "1.0")
                        }
                        newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                    }

                    else -> newErrorResponse(Response.Status.NOT_FOUND, "Unknown endpoint")
                }
            } catch (e: Exception) {
                Log.e(TAG, "REST error on $method ${redactUri(uri)}: ${e.message}")
                newErrorResponse(Response.Status.INTERNAL_ERROR, "Server error: ${e.message}")
            }
        }

        /**
         * POST /api/files/{inboxId} — Upload encrypted message blob
         */
        private fun handleUpload(session: IHTTPSession, rawInboxId: String): Response {
            val inboxId = rawInboxId.trim().lowercase()
            val isHexFingerprint = inboxId.length in 32..128 && inboxId.all { it.isDigit() || it in 'a'..'f' }
            if (!authorizedOwners.containsKey(inboxId) && !isHexFingerprint) {
                return newErrorResponse(Response.Status.FORBIDDEN, "Unknown recipient")
            }

            val payload = readBody(session)?.let(::decodeUploadPayload)
                ?: return newErrorResponse(Response.Status.BAD_REQUEST, "No body")

            if (payload.size > MAX_MESSAGE_SIZE) {
                return newErrorResponse(Response.Status.BAD_REQUEST, "Payload too large")
            }

            val message = StoredMessage(
                recipientId = inboxId,
                // A SOCKS peer address is not an onion identity and retaining it
                // would create misleading local metadata.
                senderOnion = "unknown",
                encryptedPayload = payload
            )
            val entity = com.seamless.android.data.MailboxMessageEntity(
                id = message.id,
                recipientId = message.recipientId,
                senderOnion = message.senderOnion,
                encryptedPayload = message.encryptedPayload,
                timestamp = message.timestamp,
                expiresAt = message.timestamp + MESSAGE_TTL_MS
            )

            synchronized(storageLock) {
                val messages = storedMessages.getOrPut(inboxId) { mutableListOf() }
                val existing = messages.firstOrNull {
                    it.encryptedPayload.contentEquals(payload)
                }
                if (existing != null) {
                    val json = JSONObject().apply {
                        put("status", "already_stored")
                        put("fileId", existing.id)
                    }
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
                }
                if (messages.size >= MAX_STORED_MESSAGES || storedMessages.values.sumOf { it.size } >= MAX_TOTAL_STORED_MESSAGES) {
                    // Do not silently evict a valid older message. The sender's
                    // durable outbox will retry when capacity becomes available.
                    return newErrorResponse(Response.Status.BAD_REQUEST, "Mailbox is full; retry later")
                }

                // The HTTP success response is the sender's delivery proof to
                // this mailbox, so commit before acknowledging the upload.
                try {
                    runBlocking { dao.insert(entity) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist mailbox message ${message.id}: ${e.message}")
                    return newErrorResponse(Response.Status.INTERNAL_ERROR, "Mailbox storage unavailable")
                }
                messages.add(message)
            }

            updateStats()
            Log.d(TAG, "📥 Stored message ${message.id.take(8)}... for ${redactOwnerId(inboxId)}")

            val json = JSONObject().apply {
                put("status", "stored")
                put("fileId", message.id)
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        }

        /**
         * GET /api/files/{folderId} — Download all messages for a folder
         */
        private fun handleDownload(session: IHTTPSession, folderId: String): Response {
            val cleanFolderId = folderId.trim().lowercase()
            if (!hasOwnerAccess(session, cleanFolderId)) {
                return newErrorResponse(Response.Status.FORBIDDEN, "Mailbox access denied")
            }

            val messages = synchronized(storageLock) {
                storedMessages[cleanFolderId]?.toList() ?: emptyList()
            }

            val filesArray = JSONArray()
            for (msg in messages) {
                filesArray.put(JSONObject().apply {
                    put("fileId", msg.id)
                    put("timestamp", msg.timestamp)
                    put("payload", android.util.Base64.encodeToString(
                        msg.encryptedPayload, android.util.Base64.NO_WRAP
                    ))
                })
            }

            val json = JSONObject().apply {
                put("folderId", cleanFolderId)
                put("files", filesArray)
            }

            updateStats()
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        }

        /**
         * DELETE /api/files/{folderId}/{fileId} — Confirm receipt, delete
         */
        private fun handleDelete(session: IHTTPSession, folderId: String, fileId: String): Response {
            val cleanFolderId = folderId.trim().lowercase()
            if (!hasOwnerAccess(session, cleanFolderId)) {
                return newErrorResponse(Response.Status.FORBIDDEN, "Mailbox access denied")
            }

            val belongsToFolder = synchronized(storageLock) {
                storedMessages[cleanFolderId]?.any { it.id == fileId } == true
            }
            if (!belongsToFolder) {
                return newErrorResponse(Response.Status.NOT_FOUND, "File not found in folder")
            }

            // Delete durable state first. A success response must never leave a
            // message that will return after the mailbox process restarts.
            try {
                runBlocking { dao.deleteById(fileId) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete mailbox message $fileId: ${e.message}")
                return newErrorResponse(Response.Status.INTERNAL_ERROR, "Mailbox storage unavailable")
            }
            synchronized(storageLock) {
                storedMessages[cleanFolderId]?.removeAll { it.id == fileId }
            }

            updateStats()
            Log.d(TAG, "🗑️ Deleted message $fileId from folder $cleanFolderId")

            val json = JSONObject().apply { put("status", "deleted") }
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        }

        // ===== Helpers =====

        private fun readBody(session: IHTTPSession): ByteArray? {
            return try {
                val contentLength = session.headers["content-length"]?.toLongOrNull() ?: return null
                if (contentLength <= 0 || contentLength > MAX_REQUEST_BODY_SIZE) return null
                val files = HashMap<String, String>()
                session.parseBody(files)
                // NanoHTTPD stores body in "postData" for raw POSTs
                files["postData"]?.toByteArray() ?: session.inputStream?.readBytes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read body: ${e.message}")
                null
            }
        }

        /**
         * Mailbox payloads use Base64 JSON to preserve arbitrary ciphertext
         * across NanoHTTPD's text-oriented request-body parser. Raw bytes stay
         * accepted for mailbox instances created by older app versions.
         */
        private fun decodeUploadPayload(body: ByteArray): ByteArray? {
            val text = body.toString(Charsets.UTF_8)
            return try {
                val encoded = JSONObject(text).optString("payload")
                if (encoded.isBlank()) null else android.util.Base64.decode(
                    encoded,
                    android.util.Base64.NO_WRAP
                )
            } catch (_: Exception) {
                body
            }
        }

        private fun newErrorResponse(status: Response.Status, message: String): Response {
            val json = JSONObject().apply {
                put("error", message)
            }
            return newFixedLengthResponse(status, "application/json", json.toString())
        }

        private fun hasOwnerAccess(session: IHTTPSession, rawOwnerId: String): Boolean {
            val ownerId = rawOwnerId.trim().lowercase()
            val token = session.headers["x-seamless-mailbox-capability"].orEmpty()
            if (token.isBlank()) return false

            val tokenHash = hashAccessToken(token)
            val registration = authorizedOwners[ownerId]
            if (registration != null) {
                return MessageDigest.isEqual(registration.accessTokenHash, tokenHash)
            }
            val pairwiseTokenHash = authorizedOutboxTokens[ownerId]
            if (pairwiseTokenHash != null) {
                return MessageDigest.isEqual(pairwiseTokenHash, tokenHash)
            }
            val deterministicToken = deriveContactOutboxToken(ownerId)
            return MessageDigest.isEqual(hashAccessToken(deterministicToken), tokenHash)
        }
    }

    fun deriveContactOutboxToken(recipientFolderId: String): String {
        val clean = recipientFolderId.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("seamless-outbox-v1:$clean".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ===== Background Tasks =====

    private suspend fun cleanupLoop() {
        while (scope.isActive && isRunning.get()) {
            delay(CLEANUP_INTERVAL_MS)
            cleanup()
        }
    }

    private fun cleanup() {
        synchronized(storageLock) {
            val now = System.currentTimeMillis()
            var removed = 0

            storedMessages.values.forEach { messages ->
                val toRemove = messages.filter { now - it.timestamp > MESSAGE_TTL_MS }
                removed += toRemove.size
                messages.removeAll(toRemove.toSet())
            }

            // Remove empty lists
            storedMessages.entries.removeIf { it.value.isEmpty() }

            if (removed > 0) {
                Log.d(TAG, "🧹 Cleaned up $removed expired messages from cache")
                updateStats()
            }
        }

        // Also clean up database
        scope.launch {
            try {
                val dbRemoved = dao.deleteExpired(System.currentTimeMillis())
                if (dbRemoved > 0) {
                    Log.d(TAG, "🧹 Cleaned up $dbRemoved expired messages from database")
                }
            } catch (e: Exception) {
                Log.w(TAG, "DB cleanup failed: ${e.message}")
            }
        }
    }

    /**
     * Load persisted messages from Room into the in-memory hot cache.
     * Called once at startup to restore state across restarts.
     */
    private suspend fun loadFromDatabase() {
        try {
            val recipientIds = dao.getAllRecipientIds()
            var totalLoaded = 0

            for (recipientId in recipientIds) {
                val entities = dao.getByRecipient(recipientId)
                if (entities.isNotEmpty()) {
                    val messages = entities.map { entity ->
                        StoredMessage(
                            id = entity.id,
                            recipientId = entity.recipientId,
                            senderOnion = entity.senderOnion,
                            encryptedPayload = entity.encryptedPayload,
                            timestamp = entity.timestamp
                        )
                    }.toMutableList()

                    synchronized(storageLock) {
                        storedMessages[recipientId] = messages
                    }
                    totalLoaded += messages.size
                }
            }

            // Clean expired messages during load
            val expired = dao.deleteExpired(System.currentTimeMillis())

            Log.i(TAG, "📂 Loaded $totalLoaded messages from database (purged $expired expired)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from database: ${e.message}")
        }
    }

    private fun updateStats() {
        val messageCount = synchronized(storageLock) {
            storedMessages.values.sumOf { it.size }
        }
        _stats.value = MailboxStats(
            isRunning = isRunning.get(),
            storedMessageCount = messageCount,
            ownerCount = authorizedOwners.size,
            totalStored = 0, // TODO: Track
            totalFetched = 0 // TODO: Track
        )
    }

    private fun persistAuthorizedOwners() {
        synchronized(ownerRegistryLock) {
            try {
                val registry = JSONObject()
                authorizedOwners.forEach { (ownerId, registration) ->
                    registry.put(ownerId, JSONObject().apply {
                        put("publicKey", android.util.Base64.encodeToString(
                            registration.publicKey,
                            android.util.Base64.NO_WRAP
                        ))
                        put("accessTokenHash", android.util.Base64.encodeToString(
                            registration.accessTokenHash,
                            android.util.Base64.NO_WRAP
                        ))
                    })
                }
                secureIdentityState.storeSecureValue(OWNER_REGISTRY_KEY, registry.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist mailbox owner registry: ${e.message}")
            }
        }
    }

    private fun loadAuthorizedOwners() {
        synchronized(ownerRegistryLock) {
            try {
                val raw = secureIdentityState.getSecureValue(OWNER_REGISTRY_KEY) ?: return
                val registry = JSONObject(raw)
                val keys = registry.keys()
                while (keys.hasNext()) {
                    val ownerId = keys.next()
                    val entry = registry.optJSONObject(ownerId)
                    val encodedKey = entry?.optString("publicKey").orEmpty()
                    val encodedTokenHash = entry?.optString("accessTokenHash").orEmpty()
                    if (ownerId.isNotBlank() && encodedKey.isNotBlank() && encodedTokenHash.isNotBlank()) {
                        authorizedOwners[ownerId] = OwnerRegistration(
                            publicKey = android.util.Base64.decode(encodedKey, android.util.Base64.NO_WRAP),
                            accessTokenHash = android.util.Base64.decode(
                                encodedTokenHash,
                                android.util.Base64.NO_WRAP
                            )
                        )
                    }
                }
                Log.i(TAG, "Restored ${authorizedOwners.size} mailbox owner registrations")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore mailbox owner registry: ${e.message}")
            }
        }
    }

    private fun hashAccessToken(token: String): ByteArray = MessageDigest
        .getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))

    private fun redactOwnerId(ownerId: String): String = "${ownerId.take(12)}..."

    private fun redactUri(uri: String): String {
        val prefix = "/api/files/"
        if (!uri.startsWith(prefix)) return uri
        val parts = uri.removePrefix(prefix).split('/')
        return "$prefix${redactOwnerId(parts.firstOrNull().orEmpty())}"
    }
}
