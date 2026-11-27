@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.GroupMemberEntity
import com.seamless.android.data.GroupMessageEntity
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.identity.SecureIdentityStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Private Tor groups backed by Chrono-DAG causal Merkle sync, epoch ratcheting,
 * encrypted pairwise delivery, chunked/compressed invites, and multi-admin handover.
 */
class TorGroupMessaging private constructor(context: Context) {

    companion object {
        private const val TAG = "TorGroupMessaging"
        private const val PREFIX = "SGRP1:"
        private const val PREFIX_V2 = "SGRP2:"
        private const val VERSION = 1
        private const val VERSION_V2 = 2
        private const val MAX_MEMBERS = 32
        private const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L
        private const val MAX_WIRE_PAYLOAD_BYTES = 3_500
        private const val MAX_INVITE_PARTS = 8
        private const val MAX_DECOMPRESSED_BYTES = 64 * 1024
        private const val MAX_PENDING_PER_GROUP = 32
        private const val MAX_PENDING_TOTAL = 128
        private const val PENDING_EVENT_TTL_MS = 24 * 60 * 60 * 1000L
        private const val INVITE_PART_TTL_MS = 10 * 60 * 1000L
        private const val MAX_SYNC_BATCH_EVENTS = 24
        private const val SYNC_OFFER_COOLDOWN_MS = 30_000L
        private const val ECHO_SWARM_FANOUT = 2
        private const val MAX_DISAPPEARING_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private const val MIN_DISAPPEARING_TTL_MS = 60_000L

        private const val LEGACY_PREFS_NAME = "tor_group_state"
        private const val SECURE_PREFS_NAME = "tor_group_secure_state"
        private const val ED25519_PRIVATE_KEY_PREF = "ed25519_signing_private_key"
        private const val KEY_DAG_STATE = "chrono_dag_state"
        private const val KEY_PENDING_EVENTS = "pending_events"
        private const val KEY_ED25519_FALLBACK = "chrono_dag_ed25519_priv"

        private const val REPLY_HEADER_PREFIX = "⟦reply:"
        private const val REPLY_HEADER_SUFFIX = "⟧\n"
        const val SYSTEM_SENDER = "system"
        const val DELETED_MESSAGE_TEXT = "This message was deleted"
        private val HEX_PUBLIC_KEY = Regex("^[0-9a-fA-F]{64}$")

        const val STATE_QUEUED = "QUEUED"
        const val STATE_SENT = "SENT"
        const val STATE_DELIVERED = "DELIVERED"
        const val STATE_PARTIAL = "PARTIAL"
        const val STATE_FAILED = "FAILED"

        @Volatile private var instance: TorGroupMessaging? = null

        fun getInstance(context: Context): TorGroupMessaging = instance ?: synchronized(this) {
            instance ?: TorGroupMessaging(context.applicationContext).also { instance = it }
        }

        @androidx.annotation.VisibleForTesting
        internal fun resetForTesting() {
            synchronized(this) {
                instance = null
            }
        }

        data class ParsedGroupMessage(
            val body: String,
            val replyToId: String? = null,
            val replyToSender: String? = null,
            val replyToPreview: String? = null
        )

        fun formatTextWithReply(
            body: String,
            replyToId: String?,
            replyToSender: String?,
            replyToPreview: String?
        ): String {
            if (replyToId.isNullOrBlank() || replyToSender.isNullOrBlank()) return body
            val cleanSender = replyToSender.replace("|", " ").replace("\n", " ").take(40)
            val cleanPreview = (replyToPreview ?: "").replace("|", " ").replace("\n", " ").take(90)
            return "$REPLY_HEADER_PREFIX$replyToId|$cleanSender|$cleanPreview$REPLY_HEADER_SUFFIX$body"
        }

        fun parseMessageContent(rawText: String): ParsedGroupMessage {
            if (!rawText.startsWith(REPLY_HEADER_PREFIX)) {
                return ParsedGroupMessage(body = rawText)
            }
            val endIdx = rawText.indexOf(REPLY_HEADER_SUFFIX)
            if (endIdx <= REPLY_HEADER_PREFIX.length) {
                return ParsedGroupMessage(body = rawText)
            }
            val header = rawText.substring(REPLY_HEADER_PREFIX.length, endIdx)
            val parts = header.split("|", limit = 3)
            val body = rawText.substring(endIdx + REPLY_HEADER_SUFFIX.length)
            return if (parts.size >= 2) {
                ParsedGroupMessage(
                    body = body,
                    replyToId = parts[0].takeIf { it.isNotBlank() },
                    replyToSender = parts[1].takeIf { it.isNotBlank() },
                    replyToPreview = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            } else {
                ParsedGroupMessage(body = rawText)
            }
        }
    }

    enum class InboundResult { NOT_GROUP, HANDLED, DEFERRED, REJECTED }

    internal data class WireMember(
        val onionAddress: String,
        val nickname: String,
        val publicKey: String = "",
        val mailboxOnion: String? = null,
        val avatarId: String = "aurora",
        val signingPubKey: String? = null
    )

    internal data class WireEvent(
        val version: Int = VERSION,
        val type: String,
        val groupId: String,
        val groupName: String = "",
        val creatorOnion: String = "",
        val creatorNickname: String = "",
        val messageText: String = "",
        val groupMessageId: String = "",
        val replyToId: String? = null,
        val replyToSender: String? = null,
        val replyToPreview: String? = null,
        val targetMessageId: String? = null,
        val reactionEmoji: String? = null,
        val member: WireMember? = null,
        val members: List<WireMember> = emptyList(),
        val eventId: String? = null,
        val epoch: Long? = null,
        val eventTimestamp: Long? = null,
        val admins: List<String>? = null,
        val promotedAdminOnion: String? = null,
        val disappearingTtlMs: Long? = null,
        val reactionAction: String? = null,
        val chunkId: String? = null,
        val partIndex: Int? = null,
        val totalParts: Int? = null,
        val authorSigningPubKey: String? = null,
        val dagEvent: ChronoDagGroupSync.SignedDagEvent? = null,
        val syncOffer: ChronoDagGroupSync.SyncOffer? = null,
        val syncRequest: ChronoDagGroupSync.SyncRequest? = null,
        val syncBatch: ChronoDagGroupSync.SyncBatch? = null,
        val relayHops: Int = 0
    )

    private data class DeliveryRecord(
        val expected: MutableSet<String> = mutableSetOf(),
        val acked: MutableSet<String> = mutableSetOf(),
        val failed: MutableSet<String> = mutableSetOf(),
        val pendingIds: MutableMap<String, String> = mutableMapOf()
    )

    private data class ReactionEntry(
        val emoji: String = "",
        val action: String = "add",
        val timestamp: Long = 0L
    )

    private data class PersistedPendingGroupItem(
        val groupId: String = "",
        val senderOnion: String = "",
        val messageId: String = "",
        val timestamp: Long = 0L,
        val payload: String = "",
        val receivedAt: Long = System.currentTimeMillis()
    )

    private data class PendingInvitePartBuffer(
        val senderOnion: String,
        val groupId: String,
        val totalParts: Int,
        val parts: MutableMap<Int, WireEvent> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis()
    )

    private val gson = Gson()
    private val appContext = context.applicationContext
    private val groupDao = AppDatabase.getDatabase(appContext).groupDao()
    private val contacts = OnionContactManager.getInstance(appContext)
    private val notifier = TorMessageNotifier(appContext)
    private val prefsLock = Any()
    private val prefs: SharedPreferences = openSecureGroupPrefs(appContext)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal val chronoDag = ChronoDagGroupSync()
    private val localSigningPrivateKey: ByteArray
    private val localSigningPublicKeyHex: String

    private val pendingGroupEvents = ConcurrentHashMap<String, MutableList<PersistedPendingGroupItem>>()
    private val pendingInviteParts = ConcurrentHashMap<String, PendingInvitePartBuffer>()
    private val reactionStates = ConcurrentHashMap<String, MutableMap<String, ReactionEntry>>()
    private val syncOfferCooldowns = ConcurrentHashMap<String, Long>()
    private val speculativeGroupWarmCooldowns = ConcurrentHashMap<String, Long>()

    private val _deliveryProgressFlow = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val deliveryProgressFlow: StateFlow<Map<String, Pair<Int, Int>>> = _deliveryProgressFlow.asStateFlow()

    private val _lastReadAtFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastReadAtFlow: StateFlow<Map<String, Long>> = _lastReadAtFlow.asStateFlow()

    // messageId -> (canonicalSenderOnion -> emoji)
    private val _reactionsFlow = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val reactionsFlow: StateFlow<Map<String, Map<String, String>>> = _reactionsFlow.asStateFlow()

    private val _groupEpochFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val groupEpochFlow: StateFlow<Map<String, Long>> = _groupEpochFlow.asStateFlow()

    private val _groupAdminsFlow = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val groupAdminsFlow: StateFlow<Map<String, Set<String>>> = _groupAdminsFlow.asStateFlow()

    private val _disappearingTtlFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val disappearingTtlFlow: StateFlow<Map<String, Long>> = _disappearingTtlFlow.asStateFlow()

    private val _editedMessageIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val editedMessageIdsFlow: StateFlow<Set<String>> = _editedMessageIdsFlow.asStateFlow()

    private val _deletedMessageIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val deletedMessageIdsFlow: StateFlow<Set<String>> = _deletedMessageIdsFlow.asStateFlow()

    private val _burnedMessageIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val burnedMessageIdsFlow: StateFlow<Set<String>> = _burnedMessageIdsFlow.asStateFlow()

    private val _shredReceiptsFlow = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val shredReceiptsFlow: StateFlow<Map<String, Set<String>>> = _shredReceiptsFlow.asStateFlow()

    /** Group currently on screen; incoming messages for it do not notify. */
    @Volatile var visibleGroupId: String? = null

    init {
        migrateLegacyPrefsIfNeeded()
        val signingPair = loadOrCreateSigningKeyPair()
        localSigningPrivateKey = signingPair.first
        localSigningPublicKeyHex = signingPair.second
        loadDagState()
        loadSavedReactions()
        loadSavedReadTimestamps()
        loadSavedDeliveryProgress()
        loadSavedMessageFlags()
        loadPendingEvents()
    }

    private fun openSecureGroupPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable (${e.message}); falling back to private prefs")
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyPrefsIfNeeded() {
        runCatching {
            val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val all = legacy.all
            if (all.isEmpty()) return
            synchronized(prefsLock) {
                val editor = prefs.edit()
                all.forEach { (key, value) ->
                    if (!prefs.contains(key)) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Long -> editor.putLong(key, value)
                            is Int -> editor.putInt(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                        }
                    }
                }
                editor.apply()
                legacy.edit().clear().apply()
            }
        }
    }

    private fun loadOrCreateSigningKeyPair(): Pair<ByteArray, String> {
        val loadedFromIdentity = runCatching {
            val secureMgr = SecureIdentityStateManager(appContext)
            val fromRaw = secureMgr.getSecureValue(ED25519_PRIVATE_KEY_PREF)
                ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                ?.takeIf { it.size == 32 }
            fromRaw ?: secureMgr.loadSigningKey()?.first?.takeIf { it.size == 32 }
        }.getOrNull()

        val privBytes = loadedFromIdentity
            ?: prefs.getString(KEY_ED25519_FALLBACK, null)
                ?.let { ChronoDagGroupSync.fromHex(it) }
                ?.takeIf { it.size == 32 }
            ?: ChronoDagGroupSync.generateEd25519KeyPair().first.also { generated ->
                val encoded = android.util.Base64.encodeToString(generated, android.util.Base64.DEFAULT)
                val storedSecurely = runCatching {
                    SecureIdentityStateManager(appContext).storeSecureValue(ED25519_PRIVATE_KEY_PREF, encoded)
                    true
                }.getOrDefault(false)
                if (!storedSecurely) {
                    synchronized(prefsLock) {
                        prefs.edit().putString(KEY_ED25519_FALLBACK, ChronoDagGroupSync.toHex(generated)).apply()
                    }
                }
            }
        val pubHex = ChronoDagGroupSync.toHex(ChronoDagGroupSync.derivePublicKey(privBytes))
        return privBytes to pubHex
    }

    private fun loadDagState() {
        prefs.getString(KEY_DAG_STATE, null)?.let { chronoDag.importStateJson(it) }
        refreshDagUiFlows()
    }

    private fun persistDagStateLocked() {
        prefs.edit().putString(KEY_DAG_STATE, chronoDag.exportStateJson()).apply()
        refreshDagUiFlows()
    }

    private fun refreshDagUiFlows() {
        _groupEpochFlow.value = chronoDag.getAllEpochs()
        _groupAdminsFlow.value = chronoDag.getAllAdmins()
        _disappearingTtlFlow.value = chronoDag.getAllDisappearingTtls()
    }

    // ───────────────────────────── Read state ─────────────────────────────

    fun lastReadAt(groupId: String): Long =
        _lastReadAtFlow.value[groupId] ?: prefs.getLong("read:$groupId", 0L)

    fun markRead(groupId: String) {
        val now = System.currentTimeMillis()
        synchronized(prefsLock) {
            prefs.edit().putLong("read:$groupId", now).apply()
        }
        _lastReadAtFlow.update { it + (groupId to now) }
        notifier.cancelGroup(groupId)
    }

    private fun loadSavedReadTimestamps() {
        runCatching {
            val loaded = mutableMapOf<String, Long>()
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("read:") && value is Long) {
                    loaded[key.removePrefix("read:")] = value
                }
            }
            _lastReadAtFlow.value = loaded
        }
    }

    private fun loadSavedDeliveryProgress() {
        runCatching {
            val progress = mutableMapOf<String, Pair<Int, Int>>()
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("dt:") && value is String) {
                    val msgId = key.removePrefix("dt:")
                    val rec = gson.fromJson(value, DeliveryRecord::class.java)
                    if (rec != null && rec.expected.isNotEmpty() && rec.acked.size < rec.expected.size) {
                        progress[msgId] = rec.acked.size to rec.expected.size
                    }
                } else if (key.startsWith("n:") && value is Int) {
                    val msgId = key.removePrefix("n:")
                    if (!progress.containsKey(msgId) && value > 0) {
                        val acked = prefs.getInt("a:$msgId", 0)
                        if (acked < value) {
                            progress[msgId] = acked to value
                        }
                    }
                }
            }
            _deliveryProgressFlow.value = progress
        }
    }

    private fun loadSavedMessageFlags() {
        runCatching {
            val edited = mutableSetOf<String>()
            val deleted = mutableSetOf<String>()
            val burned = mutableSetOf<String>()
            val shreds = mutableMapOf<String, Set<String>>()
            prefs.all.forEach { (key, value) ->
                when {
                    value == true && key.startsWith("edited:") -> edited.add(key.removePrefix("edited:"))
                    value == true && key.startsWith("deleted:") -> deleted.add(key.removePrefix("deleted:"))
                    value == true && key.startsWith("burned:") -> burned.add(key.removePrefix("burned:"))
                    value is String && key.startsWith("shred:") -> {
                        val set = value.split(',').filter { it.isNotBlank() }.toSet()
                        if (set.isNotEmpty()) shreds[key.removePrefix("shred:")] = set
                    }
                }
            }
            _editedMessageIdsFlow.value = edited
            _deletedMessageIdsFlow.value = deleted
            _burnedMessageIdsFlow.value = burned
            _shredReceiptsFlow.value = shreds
        }
    }

    // ───────────────────────────── Admin & Epoch queries ─────────────────────────────

    suspend fun isAdmin(groupId: String, onionAddress: String): Boolean {
        val clean = canonical(onionAddress)
        if (clean.isBlank()) return false
        val group = groupDao.getGroup(groupId) ?: return false
        if (canonical(group.creatorOnionAddress) == clean) return true
        return chronoDag.isAdmin(groupId, clean)
    }

    suspend fun isLocalAdmin(groupId: String): Boolean {
        val group = groupDao.getGroup(groupId) ?: return false
        if (group.isCreatedByMe) return true
        val meOnion = canonical(contacts.getMyContactInfo().onionAddress)
        if (meOnion.isBlank()) return false
        return canonical(group.creatorOnionAddress) == meOnion || chronoDag.isAdmin(groupId, meOnion)
    }

    data class GroupCallMemberInfo(
        val onionAddress: String,
        val nickname: String,
        val publicKey: String,
        val pinnedSigningPubKey: String? = null
    )

    fun currentEpoch(groupId: String): Long = chronoDag.getEpoch(groupId)

    fun currentDisappearingTtlMs(groupId: String): Long = chronoDag.getDisappearingTtlMs(groupId)

    suspend fun getGroupName(groupId: String): String? = groupDao.getGroup(groupId)?.name

    suspend fun getVerifiedCallMembers(groupId: String): List<GroupCallMemberInfo> {
        val group = groupDao.getGroup(groupId) ?: return emptyList()
        val me = contacts.getMyContactInfo()
        val myCanon = canonical(me.onionAddress)
        return groupDao.getMembersForGroupNow(groupId).map { member ->
            val canon = canonical(member.memberOnionAddress)
            val contact = contacts.getContactByOnion(canon)
            val pubKey = when {
                canon == myCanon -> me.publicKey
                contact != null -> contact.publicKey
                else -> ""
            }
            val pinnedSig = if (canon == myCanon) {
                localSigningPublicKeyHex
            } else {
                chronoDag.getPinnedSigningKey(groupId, canon)
            }
            GroupCallMemberInfo(
                onionAddress = canon,
                nickname = contact?.nickname ?: member.memberNickname,
                publicKey = pubKey,
                pinnedSigningPubKey = pinnedSig
            )
        }
    }

    suspend fun isVerifiedGroupMemberForCall(groupId: String, onionAddress: String): Boolean {
        val clean = canonical(onionAddress)
        if (clean.isBlank()) return false
        if (groupDao.getGroup(groupId) == null) return false
        val contact = contacts.getContactByOnion(clean)
        if (contact != null && !contact.isEligibleForGroupMessaging()) return false
        return isGroupMember(groupId, clean)
    }

    fun signGroupCallPayload(
        groupId: String,
        callId: String,
        epoch: Int,
        senderOnion: String,
        senderTag4: Int,
        rootSeed: ByteArray
    ): Pair<String, String> = com.seamless.android.crypto.AegisGroupCallCrypto.signSenderKeyAnnouncement(
        ed25519PrivateKey = localSigningPrivateKey,
        groupId = groupId,
        callId = callId,
        epoch = epoch,
        senderOnion = senderOnion,
        senderTag4 = senderTag4,
        rootSeed = rootSeed
    )

    suspend fun recordGroupCallTimelineEvent(groupId: String, text: String) {
        if (groupDao.getGroup(groupId) == null) return
        insertSystemBanner(groupId, text)
    }

    /** Chrono-Sense speculative circuit & bulk-lane prewarming for active group members. */
    fun speculativeWarmGroupCircuits(groupId: String, includeBulk: Boolean = false) {
        val now = System.currentTimeMillis()
        val key = "$groupId:$includeBulk"
        if (now - (speculativeGroupWarmCooldowns[key] ?: 0L) < 20_000L) return
        speculativeGroupWarmCooldowns[key] = now
        ioScope.launch {
            val meCanon = canonical(contacts.getMyContactInfo().onionAddress)
            val svc = TorMessagingService.getInstance(appContext)
            groupDao.getMembersForGroupNow(groupId)
                .filter { canonical(it.memberOnionAddress) != meCanon }
                .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
                .filter { it.isEligibleForGroupMessaging() }
                .take(12)
                .forEach { contact ->
                    if (includeBulk) svc.speculativePrewarmBulk(contact.id)
                    else launch { runCatching { svc.refreshPeerConnection(contact.id) } }
                }
        }
    }

    // ───────────────────────────── Commands ─────────────────────────────

    suspend fun createGroup(name: String, inviteeIds: Set<String> = emptySet()): Result<PrivateGroupEntity> = runCatching {
        val cleanName = name.trim()
        require(cleanName.length in 1..80) { "Group name must be between 1 and 80 characters" }
        val me = contacts.getMyContactInfo()
        require(!me.requiresRendezvous()) { "Wait for your Tor address before creating a group" }
        val invitees = inviteeIds
            .mapNotNull { contacts.getContact(it) }
            .filter { it.isEligibleForGroupMessaging() }
            .distinctBy { canonical(it.onionAddress) }
            .take(MAX_MEMBERS - 1)

        val myCanonical = canonical(me.onionAddress)
        val group = PrivateGroupEntity(
            groupId = UUID.randomUUID().toString(),
            name = cleanName,
            creatorOnionAddress = myCanonical,
            creatorNickname = me.nickname,
            isCreatedByMe = true
        )
        groupDao.insertGroup(group)
        val allMembers = listOf(me.toWireMember(signingPubKey = localSigningPublicKeyHex)) +
            invitees.map { it.toWireMember() }
        allMembers.forEach { member ->
            groupDao.insertMember(GroupMemberEntity(group.groupId, canonical(member.onionAddress), member.nickname))
        }
        markRead(group.groupId)

        synchronized(prefsLock) {
            chronoDag.initializeGroup(
                groupId = group.groupId,
                creatorCanonicalOnion = myCanonical,
                initialMembers = allMembers.map { canonical(it.onionAddress) to it.publicKey },
                epoch = 1L,
                admins = setOf(myCanonical),
                disappearingTtlMs = 0L
            )
            chronoDag.pinAuthorSigningKey(group.groupId, myCanonical, localSigningPublicKeyHex)
            persistDagStateLocked()
        }

        val inviteEvent = WireEvent(
            version = VERSION_V2,
            type = "invite",
            groupId = group.groupId,
            groupName = group.name,
            creatorOnion = myCanonical,
            creatorNickname = me.nickname,
            eventId = UUID.randomUUID().toString(),
            epoch = 1L,
            admins = listOf(myCanonical),
            disappearingTtlMs = 0L,
            authorSigningPubKey = localSigningPublicKeyHex,
            members = allMembers
        )
        val payloads = encodeInvitePayloads(inviteEvent)
        invitees.forEach { contact ->
            payloads.forEach { part -> queue(contact.onionAddress, part) }
        }
        group
    }

    suspend fun addMembers(groupId: String, contactIds: Set<String>): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val current = groupDao.getMembersForGroupNow(groupId)
        val myCanonical = canonical(me.onionAddress)
        require(isLocalAdmin(groupId)) {
            "Only a group admin can add members"
        }
        val currentAddresses = current.mapTo(mutableSetOf()) { canonical(it.memberOnionAddress) }
        val currentWireMembers = current.mapNotNull { member ->
            val memberCanon = canonical(member.memberOnionAddress)
            contacts.getContactByOnion(member.memberOnionAddress)?.toWireMember(
                signingPubKey = chronoDag.getPinnedSigningKey(groupId, memberCanon)
            ) ?: me.takeIf { canonical(it.onionAddress) == memberCanon }
                ?.toWireMember(signingPubKey = localSigningPublicKeyHex)
        }
        require(currentWireMembers.size == current.size) {
            "Every group member must have verified contact information before adding members"
        }
        val newContacts = contactIds.mapNotNull { contacts.getContact(it) }
            .filter { canonical(it.onionAddress) !in currentAddresses && it.isEligibleForGroupMessaging() }
            .distinctBy { canonical(it.onionAddress) }
            .take((MAX_MEMBERS - current.size).coerceAtLeast(0))
        require(newContacts.isNotEmpty()) { "No eligible contacts to add" }

        newContacts.forEach { contact ->
            val member = contact.toWireMember()
            groupDao.insertMember(GroupMemberEntity(groupId, canonical(member.onionAddress), member.nickname))
        }
        val allMembers = currentWireMembers + newContacts.map { it.toWireMember() }
        val adminsList: List<String>
        val nextEpoch: Long
        val ttlMs: Long
        synchronized(prefsLock) {
            chronoDag.initializeGroup(
                groupId = groupId,
                creatorCanonicalOnion = canonical(group.creatorOnionAddress),
                initialMembers = currentWireMembers.map { canonical(it.onionAddress) to it.publicKey },
                admins = setOf(canonical(group.creatorOnionAddress))
            )
            nextEpoch = chronoDag.advanceEpoch(
                groupId = groupId,
                activeMembers = allMembers.map { canonical(it.onionAddress) to it.publicKey }
            )
            adminsList = (chronoDag.getAdmins(groupId) + canonical(group.creatorOnionAddress))
                .filter { addr -> allMembers.any { canonical(it.onionAddress) == addr } || addr == myCanonical }
                .distinct()
            ttlMs = chronoDag.getDisappearingTtlMs(groupId)
            persistDagStateLocked()
        }

        val inviteEvent = WireEvent(
            version = VERSION_V2,
            type = "invite",
            groupId = groupId,
            groupName = group.name,
            creatorOnion = canonical(group.creatorOnionAddress),
            creatorNickname = group.creatorNickname,
            eventId = UUID.randomUUID().toString(),
            epoch = nextEpoch,
            admins = adminsList,
            disappearingTtlMs = ttlMs,
            authorSigningPubKey = localSigningPublicKeyHex,
            members = allMembers
        )
        val invitePayloads = encodeInvitePayloads(inviteEvent)
        newContacts.forEach { contact ->
            invitePayloads.forEach { part -> queue(contact.onionAddress, part) }
        }
        val existingRecipients = current.filter { canonical(it.memberOnionAddress) != myCanonical }
        newContacts.forEach { contact ->
            val addEventId = UUID.randomUUID().toString()
            val event = encode(
                WireEvent(
                    version = VERSION_V2,
                    type = "member_added",
                    groupId = groupId,
                    creatorOnion = canonical(group.creatorOnionAddress),
                    creatorNickname = group.creatorNickname,
                    eventId = addEventId,
                    epoch = nextEpoch,
                    admins = adminsList,
                    authorSigningPubKey = localSigningPublicKeyHex,
                    member = contact.toWireMember()
                )
            )
            existingRecipients.forEach { queue(it.memberOnionAddress, event) }
        }
    }

    /** Admin-only: removes a member, ratchets the group epoch, and tells everyone. */
    suspend fun removeMember(groupId: String, memberOnion: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val members = groupDao.getMembersForGroupNow(groupId)
        require(isLocalAdmin(groupId)) {
            "Only a group admin can remove members"
        }
        val target = canonical(memberOnion)
        val myCanonical = canonical(me.onionAddress)
        require(target != myCanonical) { "Use leave to exit your own group" }
        val removed = requireNotNull(members.firstOrNull { canonical(it.memberOnionAddress) == target }) {
            "Member not found"
        }
        groupDao.removeMember(groupId, removed.memberOnionAddress)
        val remainingMembers = members.filter { canonical(it.memberOnionAddress) != target }
        val nextEpoch: Long
        synchronized(prefsLock) {
            chronoDag.removeAdmin(groupId, target)
            nextEpoch = chronoDag.advanceEpoch(
                groupId = groupId,
                activeMembers = remainingMembers.mapNotNull { member ->
                    val canon = canonical(member.memberOnionAddress)
                    val pub = contacts.getContactByOnion(member.memberOnionAddress)?.publicKey
                        ?: me.takeIf { canonical(it.onionAddress) == canon }?.publicKey
                    pub?.let { canon to it }
                }
            )
            persistDagStateLocked()
        }
        val event = encode(
            WireEvent(
                version = VERSION_V2,
                type = "member_removed",
                groupId = groupId,
                creatorOnion = canonical(group.creatorOnionAddress),
                creatorNickname = group.creatorNickname,
                eventId = UUID.randomUUID().toString(),
                epoch = nextEpoch,
                authorSigningPubKey = localSigningPublicKeyHex,
                member = WireMember(removed.memberOnionAddress, removed.memberNickname)
            )
        )
        members.filter { canonical(it.memberOnionAddress) != myCanonical }
            .forEach { queue(it.memberOnionAddress, event) }
        insertSystemBanner(groupId, "${removed.memberNickname} was removed (Epoch #$nextEpoch)")
        com.seamless.android.call.GroupCallManager.getInstance(appContext).onGroupMembershipChanged(groupId)
    }

    /** Admin-only: promotes an existing member to admin so the group has no single point of failure. */
    suspend fun promoteAdmin(groupId: String, memberOnion: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        require(isLocalAdmin(groupId)) { "Only a group admin can promote another admin" }
        val target = canonical(memberOnion)
        val members = groupDao.getMembersForGroupNow(groupId)
        val member = requireNotNull(members.firstOrNull { canonical(it.memberOnionAddress) == target }) {
            "Member not found"
        }
        require(target != canonical(me.onionAddress)) { "You are already an admin" }

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val myCanonical = canonical(me.onionAddress)
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            chronoDag.addAdmin(groupId, target)
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = eventId,
                type = "admin_promote",
                authorCanonicalOnion = myCanonical,
                timestamp = now,
                payloadJson = gson.toJson(mapOf("promotedAdminOnion" to target))
            )
            persistDagStateLocked()
        }
        insertSystemBanner(groupId, "${member.memberNickname} is now a group admin")
        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "admin_promote",
                groupId = groupId,
                creatorOnion = canonical(group.creatorOnionAddress),
                eventId = eventId,
                epoch = chronoDag.getEpoch(groupId),
                eventTimestamp = now,
                promotedAdminOnion = target,
                admins = chronoDag.getAdmins(groupId).toList(),
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv
            )
        )
        members.filter { canonical(it.memberOnionAddress) != myCanonical }
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
            .forEach { queue(it.onionAddress, payload) }
    }

    /** Any member: notify the others (with deterministic admin handover if needed), tombstone & delete locally. */
    suspend fun leaveGroup(groupId: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val members = groupDao.getMembersForGroupNow(groupId)
        val myCanonical = canonical(me.onionAddress)
        val others = members.filter { canonical(it.memberOnionAddress) != myCanonical }
        val currentAdmins = (chronoDag.getAdmins(groupId) + canonical(group.creatorOnionAddress))
            .filter { admin -> members.any { canonical(it.memberOnionAddress) == admin } }
            .toSet()
        val remainingAdmins = currentAdmins - myCanonical
        val successor = if (myCanonical in currentAdmins && remainingAdmins.isEmpty() && others.isNotEmpty()) {
            others.minByOrNull { canonical(it.memberOnionAddress) }?.let { canonical(it.memberOnionAddress) }
        } else {
            null
        }

        val event = encode(
            WireEvent(
                version = VERSION_V2,
                type = "leave",
                groupId = groupId,
                creatorOnion = canonical(group.creatorOnionAddress),
                eventId = UUID.randomUUID().toString(),
                epoch = chronoDag.getEpoch(groupId) + 1L,
                promotedAdminOnion = successor,
                authorSigningPubKey = localSigningPublicKeyHex,
                member = WireMember(me.onionAddress, me.nickname)
            )
        )
        others.forEach { queue(it.memberOnionAddress, event) }
        synchronized(prefsLock) {
            chronoDag.tombstoneGroup(groupId)
            persistDagStateLocked()
        }
        deleteLocal(groupId)
    }

    /** Admin-only rename, broadcast to members. */
    suspend fun renameGroup(groupId: String, newName: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val members = groupDao.getMembersForGroupNow(groupId)
        require(isLocalAdmin(groupId)) {
            "Only a group admin can rename the group"
        }
        val clean = newName.trim()
        require(clean.length in 1..80) { "Group name must be between 1 and 80 characters" }
        groupDao.renameGroup(groupId, clean)
        val myCanonical = canonical(me.onionAddress)
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = eventId,
                type = "rename",
                authorCanonicalOnion = myCanonical,
                timestamp = now,
                payloadJson = gson.toJson(mapOf("groupName" to clean))
            )
            persistDagStateLocked()
        }
        val event = encode(
            WireEvent(
                version = VERSION_V2,
                type = "rename",
                groupId = groupId,
                groupName = clean,
                creatorOnion = canonical(group.creatorOnionAddress),
                eventId = eventId,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv
            )
        )
        members.filter { canonical(it.memberOnionAddress) != myCanonical }
            .forEach { queue(it.memberOnionAddress, event) }
    }

    /** Admin-only disappearing message timer (0 = off, >= 60_000 ms). */
    suspend fun setDisappearingMessages(groupId: String, ttlMs: Long): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        require(isLocalAdmin(groupId)) { "Only a group admin can change disappearing messages" }
        require(ttlMs == 0L || ttlMs in MIN_DISAPPEARING_TTL_MS..MAX_DISAPPEARING_TTL_MS) {
            "Disappearing timer must be Off or between 1 minute and 30 days"
        }
        val myCanonical = canonical(me.onionAddress)
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            chronoDag.setDisappearingTtlMs(groupId, ttlMs)
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = eventId,
                type = "disappearing",
                authorCanonicalOnion = myCanonical,
                timestamp = now,
                payloadJson = gson.toJson(mapOf("disappearingTtlMs" to ttlMs))
            )
            persistDagStateLocked()
        }
        purgeExpiredMessages(groupId)
        insertSystemBanner(groupId, "Disappearing messages set to ${formatTimerLabel(ttlMs)}")
        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "disappearing",
                groupId = groupId,
                creatorOnion = canonical(group.creatorOnionAddress),
                eventId = eventId,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                disappearingTtlMs = ttlMs,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv
            )
        )
        groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonical }
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
            .forEach { queue(it.onionAddress, payload) }
    }

    suspend fun purgeExpiredMessages(groupId: String) {
        val ttl = chronoDag.getDisappearingTtlMs(groupId)
        if (ttl <= 0L) return
        val cutoff = System.currentTimeMillis() - ttl
        if (cutoff <= 0L) return
        runCatching {
            val attachmentPaths = groupDao.getExpiredGroupAttachmentPaths(groupId, cutoff)
            attachmentPaths.forEach { path -> runCatching { File(path).delete() } }
            groupDao.deleteExpiredGroupMessages(groupId, cutoff)
        }
    }

    /** Deletes the local copy only (history + roster). Does not notify anyone. */
    suspend fun deleteLocal(groupId: String) {
        val existingMessages = runCatching { groupDao.getGroupMessages(groupId).first() }.getOrDefault(emptyList())
        existingMessages.forEach { msg ->
            msg.attachmentPath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
        groupDao.deleteMessagesForGroup(groupId)
        groupDao.deleteMembersForGroup(groupId)
        groupDao.deleteGroupById(groupId)
        clearTracking(groupId, existingMessages.map { it.id })
        pendingGroupEvents.remove(groupId)
        synchronized(prefsLock) {
            chronoDag.removeGroupState(groupId)
            prefs.edit().remove("read:$groupId").apply()
            persistPendingEventsLocked()
            persistDagStateLocked()
        }
        _lastReadAtFlow.update { it - groupId }
        notifier.cancelGroup(groupId)
    }

    suspend fun sendGroupMessage(
        groupId: String,
        text: String,
        replyToId: String? = null,
        burnOnRead: Boolean = false,
        burnTtlSeconds: Int = 12
    ): Result<String> = runCatching {
        val cleanText = text.trim()
        val wireBody = if (burnOnRead) {
            TorRichChatCodec.encodeBurnText(cleanText, burnTtlSeconds)
        } else {
            cleanText
        }
        require(cleanText.isNotEmpty() && wireBody.toByteArray(Charsets.UTF_8).size <= 2_400) {
            "Message is empty or too long for secure group delivery"
        }
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val myCanonicalOnion = canonical(me.onionAddress)
        require(isGroupMember(groupId, me.onionAddress)) { "You are no longer a member of this group" }
        purgeExpiredMessages(groupId)

        val replyTarget = replyToId?.let { groupDao.getGroupMessage(it) }?.takeIf { it.groupId == groupId }
        val replySender = replyTarget?.senderNickname
        val replyPreview = replyTarget?.let {
            TorRichChatCodec.friendlyPreviewText(it.text, it.attachmentMimeType).take(90)
        }
        val storedText = formatTextWithReply(wireBody, replyTarget?.id, replySender, replyPreview)

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val localMessage = GroupMessageEntity(
            id = id,
            groupId = groupId,
            senderOnionAddress = me.onionAddress,
            senderNickname = me.nickname,
            text = storedText,
            timestamp = now,
            isFromMe = true,
            deliveryState = STATE_QUEUED
        )
        groupDao.insertGroupMessage(localMessage)

        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            chronoDag.initializeGroup(
                groupId = groupId,
                creatorCanonicalOnion = canonical(group.creatorOnionAddress),
                initialMembers = listOf(myCanonicalOnion to me.publicKey),
                admins = setOf(canonical(group.creatorOnionAddress))
            )
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = id,
                type = "message",
                authorCanonicalOnion = myCanonicalOnion,
                timestamp = now,
                payloadJson = gson.toJson(
                    mapOf(
                        "messageText" to wireBody,
                        "groupMessageId" to id,
                        "replyToId" to replyTarget?.id,
                        "replyToSender" to replySender,
                        "replyToPreview" to replyPreview
                    )
                )
            )
            persistDagStateLocked()
        }

        // Put wireBody (not storedText with duplicate reply header) on wire so 2,400B replies never fail receiver validation
        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "message",
                groupId = groupId,
                messageText = wireBody,
                groupMessageId = id,
                replyToId = replyTarget?.id,
                replyToSender = replySender,
                replyToPreview = replyPreview,
                eventId = id,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv,
                relayHops = 1
            )
        )
        val expectedMembers = groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonicalOnion }
        if (expectedMembers.isEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_SENT)
            return@runCatching id
        }
        val expectedCanonical = expectedMembers.mapTo(mutableSetOf()) { canonical(it.memberOnionAddress) }
        val recipients = expectedMembers
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
        if (recipients.isEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_FAILED)
            error("Verify group members before sending")
        }

        val ineligibleCanonical = expectedCanonical - recipients.map { canonical(it.onionAddress) }.toSet()
        val plannedIds = recipients.associateWith { UUID.randomUUID() }
        val recipientToTransportId = plannedIds.entries.associate { (contact, uuid) ->
            canonical(contact.onionAddress) to uuid.toString()
        }
        trackDelivery(
            groupMessageId = id,
            recipientToTransportId = recipientToTransportId,
            expectedCanonicalOnions = expectedCanonical,
            failedCanonicalOnions = ineligibleCanonical,
            preserveExistingAcked = false
        )

        val fanOutResults = coroutineScope {
            plannedIds.map { (contact, perRecipientId) ->
                async {
                    val ok = TorMessagingService.getInstance(appContext).sendMessageToOnion(
                        recipientOnion = contact.onionAddress,
                        text = payload,
                        messageId = perRecipientId,
                        completesUiMessage = false
                    ).isSuccess
                    Triple(canonical(contact.onionAddress), perRecipientId.toString(), ok)
                }
            }.awaitAll()
        }
        val accepted = mutableListOf<String>()
        val failedEntries = mutableListOf<Pair<String, String>>()
        for ((canonOnion, perRecipientIdStr, ok) in fanOutResults) {
            if (ok) accepted += perRecipientIdStr else failedEntries += canonOnion to perRecipientIdStr
        }
        markFailedRecipients(id, failedEntries)
        if (accepted.isEmpty()) {
            _deliveryProgressFlow.update { it - id }
            clearDeliveryRecord(id)
            groupDao.updateGroupMessageState(id, STATE_FAILED)
            error("Could not queue message for any member")
        }
        if (ineligibleCanonical.isNotEmpty() || failedEntries.isNotEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_PARTIAL)
        }
        id
    }

    /** Sender-only: edits an existing text message and broadcasts the signed update. */
    suspend fun editGroupMessage(groupId: String, messageId: String, newText: String): Result<Unit> = runCatching {
        val cleanText = newText.trim()
        require(cleanText.isNotEmpty() && cleanText.toByteArray(Charsets.UTF_8).size <= 2_400) {
            "Message is empty or too long"
        }
        val existing = requireNotNull(groupDao.getGroupMessage(messageId)) { "Message not found" }
        require(existing.groupId == groupId && existing.isFromMe && existing.attachmentPath == null) {
            "Only your own text messages can be edited"
        }
        val existingBody = parseMessageContent(existing.text).body
        require(
            existing.id !in _deletedMessageIdsFlow.value &&
                existing.id !in _burnedMessageIdsFlow.value &&
                existingBody != DELETED_MESSAGE_TEXT &&
                existingBody != TorRichChatCodec.BURNED_CAPSULE_TEXT &&
                !TorRichChatCodec.isUnburnedSecretText(existing.text)
        ) {
            "This message cannot be edited"
        }
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)

        val parsed = parseMessageContent(existing.text)
        val updatedStored = formatTextWithReply(cleanText, parsed.replyToId, parsed.replyToSender, parsed.replyToPreview)
        groupDao.updateGroupMessageText(messageId, updatedStored)
        synchronized(prefsLock) {
            prefs.edit().putBoolean("edited:$messageId", true).apply()
        }
        _editedMessageIdsFlow.update { it + messageId }

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val myCanonical = canonical(me.onionAddress)
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = eventId,
                type = "edit",
                authorCanonicalOnion = myCanonical,
                timestamp = now,
                payloadJson = gson.toJson(mapOf("targetMessageId" to messageId, "messageText" to cleanText))
            )
            persistDagStateLocked()
        }

        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "edit",
                groupId = groupId,
                targetMessageId = messageId,
                messageText = cleanText,
                eventId = eventId,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv,
                relayHops = 1
            )
        )
        groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonical }
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
            .forEach { queue(it.onionAddress, payload) }
    }

    /** Sender or admin: deletes a message for everyone and broadcasts the signed tombstone. */
    suspend fun deleteGroupMessageForEveryone(groupId: String, messageId: String): Result<Unit> = runCatching {
        val existing = requireNotNull(groupDao.getGroupMessage(messageId)) { "Message not found" }
        require(existing.groupId == groupId && existing.senderOnionAddress != SYSTEM_SENDER) {
            "Message cannot be deleted"
        }
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val myCanonical = canonical(me.onionAddress)
        require(existing.isFromMe || isLocalAdmin(groupId)) {
            "Only the sender or an admin can delete this message"
        }

        TorRichChatCodec.AegisShredder.shredPath(existing.attachmentPath)
        groupDao.markGroupMessageDeleted(messageId, DELETED_MESSAGE_TEXT)
        synchronized(prefsLock) {
            prefs.edit().putBoolean("deleted:$messageId", true).apply()
        }
        _deletedMessageIdsFlow.update { it + messageId }

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = eventId,
                type = "delete",
                authorCanonicalOnion = myCanonical,
                timestamp = now,
                payloadJson = gson.toJson(mapOf("targetMessageId" to messageId))
            )
            persistDagStateLocked()
        }

        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "delete",
                groupId = groupId,
                targetMessageId = messageId,
                eventId = eventId,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv,
                relayHops = 1
            )
        )
        groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonical }
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
            .forEach { queue(it.onionAddress, payload) }
    }

    /** Cryptographically shreds a viewed View-Once group capsule & broadcasts a signed SHRED_RECEIPT. */
    suspend fun consumeAndShredGroupCapsule(groupId: String, messageId: String): Result<Unit> = runCatching {
        val existing = groupDao.getGroupMessage(messageId)
        TorRichChatCodec.AegisShredder.shredPath(existing?.attachmentPath)
        groupDao.markGroupMessageDeleted(messageId, TorRichChatCodec.BURNED_CAPSULE_TEXT)
        synchronized(prefsLock) {
            prefs.edit().putBoolean("burned:$messageId", true).apply()
        }
        _burnedMessageIdsFlow.update { it + messageId }

        val group = groupDao.getGroup(groupId) ?: return@runCatching
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val myCanonical = canonical(me.onionAddress)
        if (!isGroupMember(groupId, me.onionAddress) || existing == null || existing.isFromMe) {
            return@runCatching
        }

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = eventId,
                type = "shred_receipt",
                authorCanonicalOnion = myCanonical,
                timestamp = now,
                payloadJson = gson.toJson(mapOf("targetMessageId" to messageId))
            )
            persistDagStateLocked()
        }

        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "shred_receipt",
                groupId = groupId,
                targetMessageId = messageId,
                eventId = eventId,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv,
                relayHops = 1
            )
        )
        groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonical }
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
            .forEach { queue(it.onionAddress, payload) }
    }

    /**
     * Retries a FAILED or PARTIAL group message or photo ONLY to recipients who
     * have not yet acknowledged it, preserving prior ACK progress.
     */
    suspend fun retryGroupMessage(messageId: String): Result<String> = runCatching {
        val existing = requireNotNull(groupDao.getGroupMessage(messageId)) { "Message not found" }
        val group = requireNotNull(groupDao.getGroup(existing.groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val myCanonicalOnion = canonical(me.onionAddress)
        val expectedMembers = groupDao.getMembersForGroupNow(group.groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonicalOnion }
        if (expectedMembers.isEmpty()) {
            groupDao.updateGroupMessageState(messageId, STATE_SENT)
            return@runCatching messageId
        }

        val expectedCanonical = expectedMembers.mapTo(mutableSetOf()) { canonical(it.memberOnionAddress) }
        val alreadyAcked = synchronized(prefsLock) {
            loadDeliveryRecordLocked(messageId)?.acked?.filter { it in expectedCanonical }?.toSet() ?: emptySet()
        }
        val unackedMembers = expectedMembers.filter { canonical(it.memberOnionAddress) !in alreadyAcked }
        if (unackedMembers.isEmpty()) {
            synchronized(prefsLock) { clearDeliveryRecordLocked(messageId) }
            _deliveryProgressFlow.update { it - messageId }
            groupDao.updateGroupMessageState(messageId, STATE_DELIVERED)
            return@runCatching messageId
        }

        val recipients = unackedMembers
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
        require(recipients.isNotEmpty()) { "Verify unacknowledged group members before retrying" }

        val ineligibleUnacked = unackedMembers.map { canonical(it.memberOnionAddress) }.toSet() -
            recipients.map { canonical(it.onionAddress) }.toSet()

        groupDao.updateGroupMessageState(messageId, if (alreadyAcked.isNotEmpty()) STATE_SENT else STATE_QUEUED)
        val attachmentPath = existing.attachmentPath
        if (!attachmentPath.isNullOrBlank()) {
            val sourceFile = File(attachmentPath).takeIf { it.exists() && it.isFile }
                ?: error("Attachment file no longer available")
            val mime = existing.attachmentMimeType ?: "image/jpeg"
            val desc = TorRichChatCodec.parseAttachmentDescriptor(existing.text)
            val plannedChunkIds = recipients.associateWith { UUID.randomUUID() }
            trackDelivery(
                groupMessageId = messageId,
                recipientToTransportId = plannedChunkIds.entries.associate { (c, id) -> canonical(c.onionAddress) to id.toString() },
                expectedCanonicalOnions = expectedCanonical,
                failedCanonicalOnions = ineligibleUnacked,
                preserveExistingAcked = true
            )
            val photoResults = coroutineScope {
                plannedChunkIds.map { (contact, chunkId) ->
                    async {
                        val sentId = TorMessagingService.getInstance(appContext)
                            .sendAttachmentFromFile(
                                contactId = contact.id,
                                sourceFile = sourceFile,
                                mimeType = mime,
                                fileName = desc?.fileName.orEmpty(),
                                durationMs = desc?.durationMs ?: 0,
                                pageCount = desc?.pageCount ?: 0,
                                sha256Hex = desc?.sha256.orEmpty(),
                                viewOnce = desc?.viewOnce ?: false,
                                groupId = group.groupId,
                                groupMessageId = messageId,
                                finalChunkMessageId = chunkId,
                                keepSourceFile = true
                            )
                            .getOrNull()
                        Triple(canonical(contact.onionAddress), chunkId.toString(), sentId)
                    }
                }.awaitAll()
            }
            val failedEntries = mutableListOf<Pair<String, String>>()
            val acceptedIds = mutableListOf<String>()
            for ((canonOnion, chunkIdStr, sentId) in photoResults) {
                if (sentId == null) failedEntries += canonOnion to chunkIdStr else acceptedIds += sentId
            }
            markFailedRecipients(messageId, failedEntries)
            if (acceptedIds.isEmpty() && alreadyAcked.isEmpty()) {
                _deliveryProgressFlow.update { it - messageId }
                clearDeliveryRecord(messageId)
                groupDao.updateGroupMessageState(messageId, STATE_FAILED)
                error("Could not queue group attachment retry")
            }
            if (ineligibleUnacked.isNotEmpty() || failedEntries.isNotEmpty()) {
                groupDao.updateGroupMessageState(messageId, STATE_PARTIAL)
            }
            return@runCatching messageId
        }

        val parsed = parseMessageContent(existing.text)
        val payload = encode(
            WireEvent(
                version = VERSION_V2,
                type = "message",
                groupId = group.groupId,
                messageText = parsed.body,
                groupMessageId = messageId,
                replyToId = parsed.replyToId,
                replyToSender = parsed.replyToSender,
                replyToPreview = parsed.replyToPreview,
                eventId = messageId,
                epoch = chronoDag.getEpoch(group.groupId),
                eventTimestamp = existing.timestamp,
                authorSigningPubKey = localSigningPublicKeyHex
            )
        )
        val plannedIds = recipients.associateWith { UUID.randomUUID() }
        trackDelivery(
            groupMessageId = messageId,
            recipientToTransportId = plannedIds.entries.associate { (c, id) -> canonical(c.onionAddress) to id.toString() },
            expectedCanonicalOnions = expectedCanonical,
            failedCanonicalOnions = ineligibleUnacked,
            preserveExistingAcked = true
        )
        val textRetryResults = coroutineScope {
            plannedIds.map { (contact, perRecipientId) ->
                async {
                    val ok = TorMessagingService.getInstance(appContext).sendMessageToOnion(
                        recipientOnion = contact.onionAddress,
                        text = payload,
                        messageId = perRecipientId,
                        completesUiMessage = false
                    ).isSuccess
                    Triple(canonical(contact.onionAddress), perRecipientId.toString(), ok)
                }
            }.awaitAll()
        }
        val accepted = mutableListOf<String>()
        val failedEntries = mutableListOf<Pair<String, String>>()
        for ((canonOnion, perRecipientIdStr, ok) in textRetryResults) {
            if (ok) accepted += perRecipientIdStr else failedEntries += canonOnion to perRecipientIdStr
        }
        markFailedRecipients(messageId, failedEntries)
        if (accepted.isEmpty() && alreadyAcked.isEmpty()) {
            _deliveryProgressFlow.update { it - messageId }
            clearDeliveryRecord(messageId)
            groupDao.updateGroupMessageState(messageId, STATE_FAILED)
            error("Could not queue message for any member")
        }
        if (ineligibleUnacked.isNotEmpty() || failedEntries.isNotEmpty()) {
            groupDao.updateGroupMessageState(messageId, STATE_PARTIAL)
        }
        messageId
    }

    /** Broadcasts an encrypted idempotent emoji reaction for a message in the group. */
    suspend fun sendGroupReaction(groupId: String, targetMessageId: String, emoji: String): Result<Unit> = runCatching {
        val cleanEmoji = emoji.trim().take(8)
        require(cleanEmoji.isNotEmpty()) { "Invalid reaction" }
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        val myCanonicalOnion = canonical(me.onionAddress)

        val currentEmoji = _reactionsFlow.value[targetMessageId]?.get(myCanonicalOnion)
        val action = if (currentEmoji == cleanEmoji) "remove" else "add"
        val now = System.currentTimeMillis()
        applyReactionLww(targetMessageId, myCanonicalOnion, cleanEmoji, action, now)

        val reactionEventId = UUID.randomUUID().toString()
        val dagEv: ChronoDagGroupSync.SignedDagEvent
        synchronized(prefsLock) {
            dagEv = chronoDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = localSigningPrivateKey,
                eventId = reactionEventId,
                type = "reaction",
                authorCanonicalOnion = myCanonicalOnion,
                timestamp = now,
                payloadJson = gson.toJson(
                    mapOf(
                        "targetMessageId" to targetMessageId,
                        "reactionEmoji" to cleanEmoji,
                        "reactionAction" to action
                    )
                )
            )
            persistDagStateLocked()
        }

        val event = encode(
            WireEvent(
                version = VERSION_V2,
                type = "reaction",
                groupId = groupId,
                targetMessageId = targetMessageId,
                reactionEmoji = cleanEmoji,
                reactionAction = action,
                eventId = reactionEventId,
                epoch = dagEv.epoch,
                eventTimestamp = now,
                authorSigningPubKey = localSigningPublicKeyHex,
                dagEvent = dagEv,
                relayHops = 1
            )
        )
        groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonicalOnion }
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
            .forEach { contact -> queue(contact.onionAddress, event) }
    }

    suspend fun sendGroupImage(
        groupId: String,
        image: ByteArray,
        mimeType: String,
        previewBytes: ByteArray? = null
    ): Result<String> = sendGroupAttachment(
        groupId = groupId,
        bytes = image,
        mimeType = mimeType,
        previewBytes = previewBytes
    )

    suspend fun sendGroupAttachment(
        groupId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "",
        durationMs: Int = 0,
        viewOnce: Boolean = false,
        previewBytes: ByteArray? = null,
        pageCount: Int = 0
    ): Result<String> {
        val tempFile = File.createTempFile("grp_att_", ".tmp", appContext.cacheDir)
        return try {
            tempFile.writeBytes(bytes)
            sendGroupAttachmentFromFile(
                groupId = groupId,
                sourceFile = tempFile,
                mimeType = mimeType,
                fileName = fileName,
                durationMs = durationMs,
                pageCount = pageCount,
                viewOnce = viewOnce,
                previewBytes = previewBytes
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    suspend fun sendGroupAttachmentFromFile(
        groupId: String,
        sourceFile: File,
        mimeType: String,
        fileName: String = "",
        durationMs: Int = 0,
        pageCount: Int = 0,
        sha256Hex: String = "",
        viewOnce: Boolean = false,
        previewBytes: ByteArray? = null
    ): Result<String> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        ensureLocalMembership(group, me)
        require(isGroupMember(groupId, me.onionAddress)) { "You are no longer a member of this group" }
        purgeExpiredMessages(groupId)
        require(sourceFile.exists() && sourceFile.isFile) { "Attachment file is missing" }
        val fileLen = sourceFile.length()
        require(fileLen in 1L..TorAttachmentTransfer.MAX_ATTACHMENT_BYTES.toLong()) {
            "File exceeds the 50 MB encrypted Tor limit"
        }

        val normalizedMime = TorAttachmentTransfer.normalizeSupportedMimeType(mimeType)
        val cleanName = TorAttachmentTransfer.sanitizeFileName(fileName)
        val id = UUID.randomUUID().toString()
        val dir = File(appContext.filesDir, "tor_group_attachments").apply { mkdirs() }
        val ext = when (normalizedMime) {
            "image/webp" -> "webp"
            "image/png" -> "png"
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
            "text/markdown" -> "md"
            "application/rtf" -> "rtf"
            "application/json" -> "json"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/epub+zip" -> "epub"
            "application/vnd.android.package-archive" -> "apk"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            else -> "bin"
        }
        val local = File(dir, "$id.$ext")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(32_768)
        sourceFile.inputStream().use { input ->
            local.outputStream().use { output ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                    digest.update(buf, 0, read)
                }
            }
        }
        val resolvedSha = sha256Hex.trim().lowercase()
            .takeIf { it.length == 64 }
            ?: digest.digest().joinToString("") { "%02x".format(it) }
        val kind = when {
            normalizedMime.startsWith("audio/") -> "voice"
            normalizedMime.startsWith("image/") -> "photo"
            else -> "file"
        }
        val rowText = if (kind == "photo" && !viewOnce && cleanName.isBlank()) {
            "Photo"
        } else {
            TorRichChatCodec.encodeAttachmentDescriptor(
                TorRichChatCodec.AttachmentDescriptor(
                    kind = kind,
                    fileName = cleanName,
                    totalBytes = fileLen.toInt(),
                    durationMs = durationMs,
                    viewOnce = viewOnce,
                    pageCount = pageCount,
                    sha256 = resolvedSha
                )
            )
        }
        val now = System.currentTimeMillis()
        groupDao.insertGroupMessage(
            GroupMessageEntity(
                id = id,
                groupId = groupId,
                senderOnionAddress = me.onionAddress,
                senderNickname = me.nickname,
                text = rowText,
                timestamp = now,
                isFromMe = true,
                deliveryState = STATE_QUEUED,
                attachmentPath = local.absolutePath,
                attachmentMimeType = normalizedMime
            )
        )

        val myCanonicalOnion = canonical(me.onionAddress)
        val expectedMembers = groupDao.getMembersForGroupNow(groupId)
            .filter { canonical(it.memberOnionAddress) != myCanonicalOnion }
        if (expectedMembers.isEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_SENT)
            return@runCatching id
        }
        val expectedCanonical = expectedMembers.mapTo(mutableSetOf()) { canonical(it.memberOnionAddress) }
        val recipients = expectedMembers
            .mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
            .filter { it.isEligibleForGroupMessaging() }
        if (recipients.isEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_FAILED)
            error("Verify group members before sending media")
        }

        val ineligibleCanonical = expectedCanonical - recipients.map { canonical(it.onionAddress) }.toSet()
        val plannedChunkIds = recipients.associateWith { UUID.randomUUID() }
        trackDelivery(
            groupMessageId = id,
            recipientToTransportId = plannedChunkIds.entries.associate { (c, cid) -> canonical(c.onionAddress) to cid.toString() },
            expectedCanonicalOnions = expectedCanonical,
            failedCanonicalOnions = ineligibleCanonical,
            preserveExistingAcked = false
        )
        val imageResults = coroutineScope {
            plannedChunkIds.map { (contact, chunkId) ->
                async {
                    val sentId = TorMessagingService.getInstance(appContext)
                        .sendAttachmentFromFile(
                            contactId = contact.id,
                            sourceFile = local,
                            mimeType = normalizedMime,
                            fileName = cleanName,
                            durationMs = durationMs,
                            pageCount = pageCount,
                            sha256Hex = resolvedSha,
                            viewOnce = viewOnce,
                            groupId = groupId,
                            groupMessageId = id,
                            finalChunkMessageId = chunkId,
                            previewBytes = if (viewOnce) null else previewBytes,
                            keepSourceFile = true
                        )
                        .getOrNull()
                    Triple(canonical(contact.onionAddress), chunkId.toString(), sentId)
                }
            }.awaitAll()
        }
        val failedEntries = mutableListOf<Pair<String, String>>()
        val transportIds = mutableListOf<String>()
        for ((canonOnion, chunkIdStr, sentId) in imageResults) {
            if (sentId == null) failedEntries += canonOnion to chunkIdStr else transportIds += sentId
        }
        markFailedRecipients(id, failedEntries)
        if (transportIds.isEmpty()) {
            _deliveryProgressFlow.update { it - id }
            clearDeliveryRecord(id)
            groupDao.updateGroupMessageState(id, STATE_FAILED)
            error("Could not queue group attachment")
        }
        if (ineligibleCanonical.isNotEmpty() || failedEntries.isNotEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_PARTIAL)
        }
        id
    }

    suspend fun handleIncomingImagePreview(
        sender: OnionContactManager.OnionContact,
        groupId: String,
        messageId: String,
        timestamp: Long,
        previewPath: String
    ): Boolean = handleIncomingAttachmentPreview(
        sender = sender,
        groupId = groupId,
        messageId = messageId,
        timestamp = timestamp,
        rowText = "Photo",
        previewPath = previewPath,
        mimeType = "image/webp"
    )

    suspend fun handleIncomingAttachmentPreview(
        sender: OnionContactManager.OnionContact,
        groupId: String,
        messageId: String,
        timestamp: Long,
        rowText: String,
        previewPath: String,
        mimeType: String
    ): Boolean {
        if (!sender.isEligibleForGroupMessaging() || chronoDag.isGroupTombstoned(groupId)) {
            return false
        }
        val group = groupDao.getGroup(groupId) ?: return false
        if (!isGroupMember(groupId, sender.onionAddress)) {
            return false
        }
        val now = System.currentTimeMillis()
        val safeTimestamp = minOf(timestamp, now).coerceAtLeast(0L)
        val inserted = groupDao.insertGroupMessageIfAbsent(
            GroupMessageEntity(messageId, groupId, sender.onionAddress, sender.nickname, rowText, safeTimestamp, false, STATE_DELIVERED, previewPath, mimeType)
        )
        if (inserted != -1L && visibleGroupId != groupId &&
            !com.seamless.android.util.AppForegroundTracker.isAppInForeground(appContext)
        ) {
            val label = TorRichChatCodec.friendlyPreviewText(rowText, mimeType)
            notifier.showGroupEvent(groupId, group.name, "${sender.nickname}: $label", senderName = sender.nickname)
        }
        return true
    }

    suspend fun handleIncomingImage(
        sender: OnionContactManager.OnionContact,
        groupId: String,
        messageId: String,
        timestamp: Long,
        path: String,
        mimeType: String
    ): Boolean = handleIncomingAttachment(
        sender = sender,
        groupId = groupId,
        messageId = messageId,
        timestamp = timestamp,
        path = path,
        mimeType = mimeType
    )

    suspend fun handleIncomingAttachment(
        sender: OnionContactManager.OnionContact,
        groupId: String,
        messageId: String,
        timestamp: Long,
        path: String,
        mimeType: String,
        fileName: String = "",
        durationMs: Int = 0,
        viewOnce: Boolean = false,
        pageCount: Int = 0,
        sha256Hex: String = ""
    ): Boolean {
        if (!sender.isEligibleForGroupMessaging() || chronoDag.isGroupTombstoned(groupId)) {
            runCatching { File(path).delete() }
            return false
        }
        val group = groupDao.getGroup(groupId) ?: run {
            runCatching { File(path).delete() }
            return false
        }
        if (!isGroupMember(groupId, sender.onionAddress)) {
            runCatching { File(path).delete() }
            return false
        }
        val normalizedMime = TorAttachmentTransfer.normalizeSupportedMimeType(mimeType)
        val kind = when {
            normalizedMime.startsWith("audio/") -> "voice"
            normalizedMime.startsWith("image/") -> "photo"
            else -> "file"
        }
        val sizeBytes = runCatching { File(path).length().toInt() }.getOrDefault(0)
        val rowText = if (kind == "photo" && !viewOnce && fileName.isBlank()) {
            "Photo"
        } else {
            TorRichChatCodec.encodeAttachmentDescriptor(
                TorRichChatCodec.AttachmentDescriptor(
                    kind = kind,
                    fileName = fileName,
                    totalBytes = sizeBytes,
                    durationMs = durationMs,
                    viewOnce = viewOnce,
                    pageCount = pageCount,
                    sha256 = sha256Hex
                )
            )
        }
        val now = System.currentTimeMillis()
        val safeTimestamp = minOf(timestamp, now).coerceAtLeast(0L)
        val inserted = groupDao.insertGroupMessageIfAbsent(
            GroupMessageEntity(messageId, groupId, sender.onionAddress, sender.nickname, rowText, safeTimestamp, false, STATE_DELIVERED, path, normalizedMime)
        )
        if (inserted == -1L) {
            val existing = groupDao.getGroupMessage(messageId)
            if (existing != null && existing.attachmentPath != path) {
                val wasPreview = TorAttachmentTransfer.isTemporaryAttachmentPath(existing.attachmentPath)
                if (wasPreview) {
                    val oldPreview = existing.attachmentPath
                    groupDao.updateGroupMessageAttachment(messageId, path, normalizedMime)
                    if (rowText != "Photo") {
                        groupDao.updateGroupMessageText(messageId, rowText)
                    }
                    oldPreview?.let { runCatching { File(it).delete() } }
                } else {
                    runCatching { File(path).delete() }
                }
            }
            return true
        }
        if (visibleGroupId != groupId &&
            !com.seamless.android.util.AppForegroundTracker.isAppInForeground(appContext)
        ) {
            val label = TorRichChatCodec.friendlyPreviewText(rowText, normalizedMime)
            notifier.showGroupEvent(groupId, group.name, "${sender.nickname}: $label", senderName = sender.nickname)
        }
        return true
    }

    // ───────────────────── Delivery tracking (ACK driven) ─────────────────────

    private fun loadDeliveryRecordLocked(groupMessageId: String): DeliveryRecord? {
        val raw = prefs.getString("dt:$groupMessageId", null) ?: return null
        return runCatching { gson.fromJson(raw, DeliveryRecord::class.java) }.getOrNull()
    }

    private fun saveDeliveryRecordLocked(groupMessageId: String, record: DeliveryRecord) {
        prefs.edit().putString("dt:$groupMessageId", gson.toJson(record)).apply()
    }

    private fun clearDeliveryRecord(groupMessageId: String) {
        synchronized(prefsLock) { clearDeliveryRecordLocked(groupMessageId) }
    }

    private fun clearDeliveryRecordLocked(groupMessageId: String) {
        val rec = loadDeliveryRecordLocked(groupMessageId)
        val e = prefs.edit()
        rec?.pendingIds?.keys?.forEach { e.remove("p:$it") }
        e.remove("dt:$groupMessageId")
        e.remove("n:$groupMessageId")
        e.remove("a:$groupMessageId")
        e.apply()
    }

    private fun trackDelivery(
        groupMessageId: String,
        recipientToTransportId: Map<String, String>,
        expectedCanonicalOnions: Set<String>,
        failedCanonicalOnions: Set<String>,
        preserveExistingAcked: Boolean = false
    ) {
        val total: Int
        val ackedCount: Int
        synchronized(prefsLock) {
            val prev = if (preserveExistingAcked) loadDeliveryRecordLocked(groupMessageId) else null
            val e = prefs.edit()
            val acked = prev?.acked?.filterTo(mutableSetOf()) { it in expectedCanonicalOnions } ?: mutableSetOf()
            val pending = mutableMapOf<String, String>()
            prev?.pendingIds?.forEach { (oldId, onion) ->
                if (onion in recipientToTransportId.keys || onion in acked || onion !in expectedCanonicalOnions) {
                    e.remove("p:$oldId")
                } else {
                    pending[oldId] = onion
                }
            }
            recipientToTransportId.forEach { (onion, transportId) ->
                if (onion !in acked) {
                    pending[transportId] = onion
                    e.putString("p:$transportId", groupMessageId)
                }
            }
            val failed = ((prev?.failed ?: emptySet()) + failedCanonicalOnions)
                .filter { it in expectedCanonicalOnions && it !in acked && it !in recipientToTransportId.keys }
                .toMutableSet()
            failed.addAll(failedCanonicalOnions.filter { it !in acked })

            val updated = DeliveryRecord(
                expected = expectedCanonicalOnions.toMutableSet(),
                acked = acked,
                failed = failed,
                pendingIds = pending
            )
            e.putString("dt:$groupMessageId", gson.toJson(updated))
            e.apply()
            total = updated.expected.size.coerceAtLeast(1)
            ackedCount = updated.acked.size
        }
        _deliveryProgressFlow.update { current ->
            current + (groupMessageId to (ackedCount to total))
        }
    }

    private fun markFailedRecipients(groupMessageId: String, failedEntries: List<Pair<String, String>>) {
        if (failedEntries.isEmpty()) return
        synchronized(prefsLock) {
            val rec = loadDeliveryRecordLocked(groupMessageId) ?: return
            val e = prefs.edit()
            failedEntries.forEach { (canonOnion, transportId) ->
                rec.pendingIds.remove(transportId)
                e.remove("p:$transportId")
                if (canonOnion !in rec.acked) {
                    rec.failed.add(canonOnion)
                }
            }
            e.putString("dt:$groupMessageId", gson.toJson(rec))
            e.apply()
        }
    }

    private fun clearTracking(groupId: String, messageIds: List<String> = emptyList()) {
        if (messageIds.isEmpty()) return
        synchronized(prefsLock) {
            val e = prefs.edit()
            messageIds.forEach { msgId ->
                loadDeliveryRecordLocked(msgId)?.pendingIds?.keys?.forEach { e.remove("p:$it") }
                e.remove("dt:$msgId")
                e.remove("n:$msgId")
                e.remove("a:$msgId")
                e.remove("react:$msgId")
                e.remove("reactv2:$msgId")
                e.remove("edited:$msgId")
                e.remove("deleted:$msgId")
            }
            e.apply()
        }
        val removeSet = messageIds.toSet()
        reactionStates.keys.removeAll(removeSet)
        synchronized(prefsLock) {
            val e = prefs.edit()
            messageIds.forEach { msgId ->
                e.remove("burned:$msgId")
                e.remove("shred:$msgId")
            }
            e.apply()
        }
        _deliveryProgressFlow.update { it - removeSet }
        _reactionsFlow.update { it - removeSet }
        _editedMessageIdsFlow.update { it - removeSet }
        _deletedMessageIdsFlow.update { it - removeSet }
        _burnedMessageIdsFlow.update { it - removeSet }
        _shredReceiptsFlow.update { it - removeSet }
    }

    /** Transitions a group message from QUEUED to SENT as soon as the first copy leaves. */
    suspend fun onTransportSent(sentMessageId: String) {
        val groupMessageId = synchronized(prefsLock) {
            prefs.getString("p:$sentMessageId", null)
        } ?: return
        val existing = groupDao.getGroupMessage(groupMessageId) ?: return
        if (existing.deliveryState == STATE_QUEUED) {
            groupDao.updateGroupMessageState(groupMessageId, STATE_SENT)
        }
    }

    /** Processes transport ACK per recipient, preserving STATE_PARTIAL until every failed copy recovers. */
    suspend fun onTransportAck(ackedMessageId: String) {
        val groupMessageId: String
        val total: Int
        val ackedCount: Int
        val hasFailed: Boolean
        synchronized(prefsLock) {
            groupMessageId = prefs.getString("p:$ackedMessageId", null) ?: return
            prefs.edit().remove("p:$ackedMessageId").apply()

            val record = loadDeliveryRecordLocked(groupMessageId)
            if (record != null) {
                val recipientOnion = record.pendingIds.remove(ackedMessageId)
                if (recipientOnion != null) {
                    record.acked.add(recipientOnion)
                    record.failed.remove(recipientOnion)
                }
                total = record.expected.size.coerceAtLeast(1)
                ackedCount = record.acked.size
                hasFailed = record.failed.isNotEmpty()
                if (ackedCount >= total) {
                    clearDeliveryRecordLocked(groupMessageId)
                } else {
                    saveDeliveryRecordLocked(groupMessageId, record)
                }
            } else {
                // Legacy fallback for in-flight messages queued before dt: upgrade
                total = prefs.getInt("n:$groupMessageId", 0).coerceAtLeast(1)
                ackedCount = prefs.getInt("a:$groupMessageId", 0) + 1
                hasFailed = false
                if (ackedCount >= total) {
                    prefs.edit().remove("n:$groupMessageId").remove("a:$groupMessageId").apply()
                } else {
                    prefs.edit().putInt("a:$groupMessageId", ackedCount).apply()
                }
            }
        }

        _deliveryProgressFlow.update { current ->
            if (ackedCount >= total) current - groupMessageId
            else current + (groupMessageId to (ackedCount to total))
        }
        val state = when {
            ackedCount >= total -> STATE_DELIVERED
            hasFailed -> STATE_PARTIAL
            else -> STATE_SENT
        }
        val existing = groupDao.getGroupMessage(groupMessageId) ?: return
        if (existing.deliveryState != STATE_DELIVERED) {
            groupDao.updateGroupMessageState(groupMessageId, state)
        }
    }

    /** "2/5 delivered" style progress for the UI, or null if untracked. */
    fun deliveryProgress(groupMessageId: String): Pair<Int, Int>? {
        _deliveryProgressFlow.value[groupMessageId]?.let { return it }
        synchronized(prefsLock) {
            loadDeliveryRecordLocked(groupMessageId)?.let { rec ->
                if (rec.expected.isNotEmpty() && rec.acked.size < rec.expected.size) {
                    return rec.acked.size to rec.expected.size
                }
                return null
            }
            val total = prefs.getInt("n:$groupMessageId", -1)
            if (total <= 0) return null
            return prefs.getInt("a:$groupMessageId", 0) to total
        }
    }

    // ───────────────────────────── Reactions (Onion-keyed LWW) ─────────────────────────────

    private fun applyReactionLww(
        messageId: String,
        senderKey: String,
        emoji: String,
        action: String,
        timestamp: Long
    ) {
        synchronized(prefsLock) {
            val forMsg = reactionStates.getOrPut(messageId) { mutableMapOf() }
            val existing = forMsg[senderKey]
            if (existing != null && timestamp < existing.timestamp) {
                return
            }
            forMsg[senderKey] = ReactionEntry(emoji = emoji, action = action, timestamp = timestamp)
            val activeMap = forMsg
                .filterValues { it.action != "remove" && it.emoji.isNotBlank() }
                .mapValues { it.value.emoji }
            _reactionsFlow.update { all ->
                if (activeMap.isEmpty()) all - messageId else all + (messageId to activeMap)
            }
            val json = JSONObject()
            forMsg.forEach { (k, entry) ->
                json.put(k, JSONObject().apply {
                    put("emoji", entry.emoji)
                    put("action", entry.action)
                    put("timestamp", entry.timestamp)
                })
            }
            prefs.edit().putString("reactv2:$messageId", json.toString()).apply()
        }
    }

    private fun loadSavedReactions() {
        runCatching {
            val loaded = mutableMapOf<String, Map<String, String>>()
            prefs.all.forEach { (key, value) ->
                when {
                    key.startsWith("reactv2:") && value is String -> {
                        val msgId = key.removePrefix("reactv2:")
                        val obj = JSONObject(value)
                        val stateMap = mutableMapOf<String, ReactionEntry>()
                        obj.keys().forEach { senderKey ->
                            val item = obj.optJSONObject(senderKey) ?: return@forEach
                            stateMap[senderKey] = ReactionEntry(
                                emoji = item.optString("emoji"),
                                action = item.optString("action", "add"),
                                timestamp = item.optLong("timestamp", 0L)
                            )
                        }
                        if (stateMap.isNotEmpty()) {
                            reactionStates[msgId] = stateMap
                            val active = stateMap.filterValues { it.action != "remove" && it.emoji.isNotBlank() }
                                .mapValues { it.value.emoji }
                            if (active.isNotEmpty()) loaded[msgId] = active
                        }
                    }
                    key.startsWith("react:") && value is String -> {
                        val msgId = key.removePrefix("react:")
                        if (!prefs.contains("reactv2:$msgId")) {
                            val obj = JSONObject(value)
                            val stateMap = mutableMapOf<String, ReactionEntry>()
                            obj.keys().forEach { k ->
                                val e = obj.optString(k)
                                if (e.isNotBlank()) stateMap[k] = ReactionEntry(e, "add", 0L)
                            }
                            if (stateMap.isNotEmpty()) {
                                reactionStates[msgId] = stateMap
                                loaded[msgId] = stateMap.mapValues { it.value.emoji }
                            }
                        }
                    }
                }
            }
            _reactionsFlow.value = loaded
        }
    }

    // ───────────────────────────── Pending Queue & Invite Part Reassembly ─────────────────────────────

    private fun loadPendingEvents() {
        runCatching {
            val raw = prefs.getString(KEY_PENDING_EVENTS, null) ?: return
            val items = gson.fromJson(raw, Array<PersistedPendingGroupItem>::class.java)?.toList() ?: return
            val now = System.currentTimeMillis()
            items.filter { now - it.receivedAt <= PENDING_EVENT_TTL_MS && it.groupId.isNotBlank() }
                .takeLast(MAX_PENDING_TOTAL)
                .forEach { item ->
                    val list = pendingGroupEvents.getOrPut(item.groupId) {
                        java.util.Collections.synchronizedList(mutableListOf())
                    }
                    synchronized(list) {
                        if (list.size < MAX_PENDING_PER_GROUP && list.none { it.messageId == item.messageId }) {
                            list.add(item)
                        }
                    }
                }
        }
    }

    private fun persistPendingEventsLocked() {
        val now = System.currentTimeMillis()
        val flat = mutableListOf<PersistedPendingGroupItem>()
        pendingGroupEvents.values.forEach { list ->
            synchronized(list) {
                list.removeAll { now - it.receivedAt > PENDING_EVENT_TTL_MS }
                flat.addAll(list)
            }
        }
        val capped = flat.takeLast(MAX_PENDING_TOTAL)
        prefs.edit().putString(KEY_PENDING_EVENTS, gson.toJson(capped)).apply()
    }

    private fun bufferPendingGroupEvent(
        groupId: String,
        sender: OnionContactManager.OnionContact,
        messageId: String,
        timestamp: Long,
        payload: String
    ): InboundResult {
        val now = System.currentTimeMillis()
        val list = pendingGroupEvents.getOrPut(groupId) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(prefsLock) {
            synchronized(list) {
                list.removeAll { now - it.receivedAt > PENDING_EVENT_TTL_MS }
                if (list.none { it.messageId == messageId }) {
                    while (list.size >= MAX_PENDING_PER_GROUP) {
                        list.removeAt(0)
                    }
                    list.add(
                        PersistedPendingGroupItem(
                            groupId = groupId,
                            senderOnion = canonical(sender.onionAddress),
                            messageId = messageId,
                            timestamp = timestamp,
                            payload = payload,
                            receivedAt = now
                        )
                    )
                }
            }
            persistPendingEventsLocked()
        }
        return InboundResult.DEFERRED
    }

    // ───────────────────────────── Inbound Dispatch ─────────────────────────────

    suspend fun handleIncoming(
        sender: OnionContactManager.OnionContact,
        messageId: String,
        timestamp: Long,
        payload: String
    ): InboundResult {
        if (!payload.startsWith(PREFIX) && !payload.startsWith(PREFIX_V2)) return InboundResult.NOT_GROUP
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED

        val event = decode(payload) ?: return InboundResult.REJECTED
        if ((event.version != VERSION && event.version != VERSION_V2) || !isUuid(event.groupId)) {
            return InboundResult.REJECTED
        }

        if (chronoDag.isGroupTombstoned(event.groupId) &&
            event.type != "invite" &&
            event.type != "invite_part"
        ) {
            return InboundResult.REJECTED
        }

        if (event.type != "invite" && event.type != "invite_part" && groupDao.getGroup(event.groupId) == null) {
            return bufferPendingGroupEvent(event.groupId, sender, messageId, timestamp, payload)
        }

        return dispatchGroupEvent(sender, event, messageId, timestamp)
    }

    private suspend fun dispatchGroupEvent(
        sender: OnionContactManager.OnionContact,
        event: WireEvent,
        messageId: String,
        timestamp: Long
    ): InboundResult = when (event.type) {
        "invite" -> handleInvite(sender, event)
        "invite_part" -> handleInvitePart(sender, event)
        "member_added" -> handleMemberAdded(sender, event)
        "member_removed" -> handleMemberRemoved(sender, event)
        "leave" -> handleLeave(sender, event)
        "rename" -> handleRename(sender, event)
        "message" -> handleGroupMessage(sender, event, messageId, timestamp)
        "reaction" -> handleGroupReaction(sender, event, timestamp)
        "edit" -> handleGroupEdit(sender, event)
        "delete" -> handleGroupDelete(sender, event)
        "shred_receipt" -> handleGroupShredReceipt(sender, event)
        "admin_promote" -> handleAdminPromote(sender, event)
        "disappearing" -> handleDisappearingTimer(sender, event)
        "sync_offer" -> handleSyncOffer(sender, event)
        "sync_request" -> handleSyncRequest(sender, event)
        "sync_batch" -> handleSyncBatch(sender, event)
        else -> InboundResult.REJECTED
    }

    private suspend fun handleInvitePart(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        val chunkId = event.chunkId?.takeIf { it.isNotBlank() } ?: return InboundResult.REJECTED
        val partIdx = event.partIndex ?: return InboundResult.REJECTED
        val totalParts = event.totalParts ?: return InboundResult.REJECTED
        if (totalParts !in 2..MAX_INVITE_PARTS || partIdx !in 0 until totalParts) {
            return InboundResult.REJECTED
        }
        val senderCanon = canonical(sender.onionAddress)
        val key = "$senderCanon:${event.groupId}:$chunkId"
        val now = System.currentTimeMillis()
        pendingInviteParts.entries.removeIf { now - it.value.createdAt > INVITE_PART_TTL_MS }

        val buffer = pendingInviteParts.getOrPut(key) {
            if (pendingInviteParts.size >= 32) {
                pendingInviteParts.keys.firstOrNull()?.let { pendingInviteParts.remove(it) }
            }
            PendingInvitePartBuffer(senderCanon, event.groupId, totalParts, createdAt = now)
        }
        val assembled: WireEvent? = synchronized(buffer) {
            if (buffer.totalParts != totalParts || buffer.senderOnion != senderCanon || buffer.groupId != event.groupId) {
                return InboundResult.REJECTED
            }
            buffer.parts[partIdx] = event
            if (buffer.parts.size == totalParts) {
                val mergedMembers = (0 until totalParts).flatMap { idx -> buffer.parts[idx]?.members.orEmpty() }
                event.copy(type = "invite", members = mergedMembers)
            } else {
                null
            }
        }
        if (assembled == null) {
            return InboundResult.HANDLED
        }
        pendingInviteParts.remove(key)
        return handleInvite(sender, assembled)
    }

    private suspend fun handleInvite(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        val canonicalCreator = canonical(event.creatorOnion)
        val canonicalSender = canonical(sender.onionAddress)
        if (event.groupName.trim().length !in 1..80 || canonicalSender.isBlank()) {
            return InboundResult.REJECTED
        }
        val me = contacts.getMyContactInfo()
        val myCanonical = canonical(me.onionAddress)
        val normalizedMembers = event.members.map { member ->
            WireMember(
                onionAddress = canonical(member.onionAddress),
                nickname = member.nickname.trim(),
                publicKey = member.publicKey.trim().lowercase(),
                mailboxOnion = member.mailboxOnion
                    ?.let(OnionContactManager.OnionContact::canonicalizeOnionEndpoint)
                    ?.takeIf { it.isNotBlank() },
                avatarId = OnionContactManager.OnionContact.canonicalAvatarId(member.avatarId),
                signingPubKey = member.signingPubKey?.trim()?.lowercase()?.takeIf { it.matches(HEX_PUBLIC_KEY) }
            )
        }
        val validMembers = normalizedMembers.isNotEmpty() &&
            normalizedMembers.size <= MAX_MEMBERS &&
            normalizedMembers.distinctBy { it.onionAddress }.size == normalizedMembers.size &&
            normalizedMembers.distinctBy { it.publicKey }.size == normalizedMembers.size &&
            normalizedMembers.all { member ->
                member.onionAddress.isNotBlank() &&
                    OnionContactManager.OnionContact.isValidOnionEndpoint(member.onionAddress) &&
                    member.nickname.length in 1..80 &&
                    member.publicKey.matches(HEX_PUBLIC_KEY)
            }
        if (!validMembers || normalizedMembers.none { it.onionAddress == myCanonical }) {
            return InboundResult.REJECTED
        }
        val senderMembership = normalizedMembers.firstOrNull { it.onionAddress == canonicalSender }
        if (senderMembership == null || !senderMembership.publicKey.equals(sender.publicKey, ignoreCase = true)) {
            return InboundResult.REJECTED
        }
        val normalizedAdmins = (event.admins.orEmpty().map { canonical(it) } + canonicalCreator)
            .filter { it.isNotBlank() }
            .toSet()
        val keep = normalizedMembers.map { it.onionAddress }.toSet()
        val authorizedSender = canonicalCreator == canonicalSender ||
            (canonicalSender in normalizedAdmins && canonicalCreator !in keep)
        if (!authorizedSender) {
            return InboundResult.REJECTED
        }

        val existing = groupDao.getGroup(event.groupId)
        if (existing != null) {
            val existingCreator = canonical(existing.creatorOnionAddress)
            val isExistingAdmin = existingCreator == canonicalSender || chronoDag.isAdmin(event.groupId, canonicalSender)
            if (!isExistingAdmin) return InboundResult.REJECTED
        }

        val eventKey = event.eventId ?: "invite:${event.groupId}:${event.epoch ?: 1L}"
        if (existing != null && !chronoDag.markControlEventSeen(eventKey)) {
            return InboundResult.HANDLED
        }

        groupDao.insertGroup(
            PrivateGroupEntity(
                groupId = event.groupId,
                name = event.groupName.trim(),
                creatorOnionAddress = existing?.creatorOnionAddress ?: canonicalCreator,
                creatorNickname = existing?.creatorNickname ?: event.creatorNickname.ifBlank { sender.nickname },
                isCreatedByMe = existing?.isCreatedByMe ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        groupDao.getMembersForGroupNow(event.groupId)
            .filter { canonical(it.memberOnionAddress) !in keep }
            .forEach { groupDao.removeMember(event.groupId, it.memberOnionAddress) }
        normalizedMembers.forEach { member ->
            groupDao.insertMember(GroupMemberEntity(event.groupId, member.onionAddress, member.nickname))
            importGroupPeer(member, me.onionAddress)
        }

        synchronized(prefsLock) {
            chronoDag.untombstoneGroup(event.groupId)
            chronoDag.initializeGroup(
                groupId = event.groupId,
                creatorCanonicalOnion = canonicalCreator,
                initialMembers = normalizedMembers.map { it.onionAddress to it.publicKey },
                epoch = (event.epoch ?: 1L).coerceAtLeast(1L),
                admins = normalizedAdmins,
                disappearingTtlMs = (event.disappearingTtlMs ?: 0L).coerceIn(0L, MAX_DISAPPEARING_TTL_MS)
            )
            event.authorSigningPubKey?.let {
                chronoDag.pinAuthorSigningKey(event.groupId, canonicalSender, it)
            }
            normalizedMembers.forEach { m ->
                m.signingPubKey?.let { chronoDag.pinAuthorSigningKey(event.groupId, m.onionAddress, it) }
            }
            chronoDag.pinAuthorSigningKey(event.groupId, myCanonical, localSigningPublicKeyHex)
            persistDagStateLocked()
        }

        if (existing == null) markRead(event.groupId)
        notifier.showGroupEvent(
            groupId = event.groupId,
            title = event.groupName.trim(),
            body = "${sender.nickname} added you to a private group"
        )

        val buffered = pendingGroupEvents.remove(event.groupId)
        if (!buffered.isNullOrEmpty()) {
            val snapshot = synchronized(buffered) { buffered.sortedBy { it.timestamp } }
            synchronized(prefsLock) { persistPendingEventsLocked() }
            for (item in snapshot) {
                val refreshedSender = contacts.getContactByOnion(item.senderOnion) ?: continue
                if (!refreshedSender.isEligibleForGroupMessaging()) continue
                val bufferedEvent = decode(item.payload) ?: continue
                dispatchGroupEvent(refreshedSender, bufferedEvent, item.messageId, item.timestamp)
            }
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleMemberAdded(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.REJECTED
        val member = event.member ?: return InboundResult.REJECTED
        val senderCanon = canonical(sender.onionAddress)
        if (!isAdmin(event.groupId, senderCanon) ||
            member.onionAddress.isBlank() ||
            member.nickname.trim().length !in 1..80 ||
            !member.publicKey.trim().matches(HEX_PUBLIC_KEY)
        ) {
            return InboundResult.REJECTED
        }
        val normalizedOnion = canonical(member.onionAddress)
        if (groupDao.getMemberCount(event.groupId) >= MAX_MEMBERS &&
            !isGroupMember(event.groupId, normalizedOnion)
        ) return InboundResult.REJECTED
        val normalized = member.copy(
            onionAddress = normalizedOnion,
            nickname = member.nickname.trim(),
            publicKey = member.publicKey.trim().lowercase(),
            mailboxOnion = member.mailboxOnion
                ?.let(OnionContactManager.OnionContact::canonicalizeOnionEndpoint)
                ?.takeIf { it.isNotBlank() },
            avatarId = OnionContactManager.OnionContact.canonicalAvatarId(member.avatarId)
        )
        if (!OnionContactManager.OnionContact.isValidOnionEndpoint(normalized.onionAddress)) {
            return InboundResult.REJECTED
        }
        event.eventId?.let { if (!chronoDag.markControlEventSeen("add:$it")) return InboundResult.HANDLED }
        groupDao.insertMember(GroupMemberEntity(event.groupId, normalized.onionAddress, normalized.nickname))
        importGroupPeer(normalized, contacts.getMyContactInfo().onionAddress)
        val activeMembers = groupDao.getMembersForGroupNow(event.groupId).mapNotNull { m ->
            val c = canonical(m.memberOnionAddress)
            val pk = contacts.getContactByOnion(c)?.publicKey
            pk?.let { c to it }
        }
        synchronized(prefsLock) {
            chronoDag.advanceEpoch(event.groupId, activeMembers, event.epoch)
            normalized.signingPubKey?.let { chronoDag.pinAuthorSigningKey(event.groupId, normalized.onionAddress, it) }
            persistDagStateLocked()
        }
        insertSystemBanner(event.groupId, "${normalized.nickname} joined the group")
        return InboundResult.HANDLED
    }

    private suspend fun handleMemberRemoved(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.HANDLED
        if (!isAdmin(event.groupId, sender.onionAddress)) return InboundResult.REJECTED
        val target = canonical(event.member?.onionAddress ?: return InboundResult.REJECTED)
        event.eventId?.let { if (!chronoDag.markControlEventSeen("rem:$it")) return InboundResult.HANDLED }
        val me = contacts.getMyContactInfo()
        if (target == canonical(me.onionAddress)) {
            notifier.showGroupEvent(event.groupId, group.name, "You were removed from this group")
            synchronized(prefsLock) {
                chronoDag.tombstoneGroup(event.groupId)
                persistDagStateLocked()
            }
            deleteLocal(event.groupId)
        } else {
            val removed = groupDao.getMembersForGroupNow(event.groupId)
                .firstOrNull { canonical(it.memberOnionAddress) == target }
            if (removed != null) {
                groupDao.removeMember(event.groupId, removed.memberOnionAddress)
                val remaining = groupDao.getMembersForGroupNow(event.groupId).mapNotNull { m ->
                    val c = canonical(m.memberOnionAddress)
                    contacts.getContactByOnion(c)?.publicKey?.let { c to it }
                }
                val newEpoch: Long
                synchronized(prefsLock) {
                    chronoDag.removeAdmin(event.groupId, target)
                    newEpoch = chronoDag.advanceEpoch(event.groupId, remaining, event.epoch)
                    persistDagStateLocked()
                }
                insertSystemBanner(event.groupId, "${removed.memberNickname} was removed (Epoch #$newEpoch)")
                com.seamless.android.call.GroupCallManager.getInstance(appContext).onGroupMembershipChanged(event.groupId)
            }
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleLeave(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null) return InboundResult.HANDLED
        event.eventId?.let { if (!chronoDag.markControlEventSeen("leave:$it")) return InboundResult.HANDLED }
        val senderCanonical = canonical(sender.onionAddress)
        val members = groupDao.getMembersForGroupNow(event.groupId)
        val leaving = members.firstOrNull { canonical(it.memberOnionAddress) == senderCanonical }
        if (leaving != null) {
            groupDao.removeMember(event.groupId, leaving.memberOnionAddress)
            val remaining = members.filter { canonical(it.memberOnionAddress) != senderCanonical }
            val wasAdmin = isAdmin(event.groupId, senderCanonical)
            val promoted = event.promotedAdminOnion?.let { canonical(it) }
                ?.takeIf { cand -> wasAdmin && remaining.any { canonical(it.memberOnionAddress) == cand } }
            val newEpoch: Long
            synchronized(prefsLock) {
                chronoDag.removeAdmin(event.groupId, senderCanonical)
                if (promoted != null) {
                    chronoDag.addAdmin(event.groupId, promoted)
                }
                newEpoch = chronoDag.advanceEpoch(
                    event.groupId,
                    remaining.mapNotNull { m ->
                        val c = canonical(m.memberOnionAddress)
                        contacts.getContactByOnion(c)?.publicKey?.let { c to it }
                    },
                    event.epoch
                )
                persistDagStateLocked()
            }
            insertSystemBanner(event.groupId, "${leaving.memberNickname} left the group (Epoch #$newEpoch)")
            if (promoted != null) {
                val successorName = remaining.firstOrNull { canonical(it.memberOnionAddress) == promoted }
                    ?.memberNickname ?: promoted.take(12)
                insertSystemBanner(event.groupId, "$successorName is now a group admin")
            }
            com.seamless.android.call.GroupCallManager.getInstance(appContext).onGroupMembershipChanged(event.groupId)
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleRename(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null) return InboundResult.REJECTED
        if (!isAdmin(event.groupId, sender.onionAddress) || event.groupName.trim().length !in 1..80) {
            return InboundResult.REJECTED
        }
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != canonical(sender.onionAddress)) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                if (!chronoDag.acceptRemoteEvent(dagEv)) return InboundResult.HANDLED
                persistDagStateLocked()
            }
        }
        event.eventId?.let { if (!chronoDag.markControlEventSeen("ren:$it")) return InboundResult.HANDLED }
        val cleanName = event.groupName.trim()
        groupDao.renameGroup(event.groupId, cleanName)
        insertSystemBanner(event.groupId, "${sender.nickname} renamed the group to $cleanName")
        return InboundResult.HANDLED
    }

    private suspend fun handleAdminPromote(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null) return InboundResult.REJECTED
        if (!isAdmin(event.groupId, sender.onionAddress)) return InboundResult.REJECTED
        val promoted = event.promotedAdminOnion?.let { canonical(it) }?.takeIf { it.isNotBlank() }
            ?: return InboundResult.REJECTED
        if (!isGroupMember(event.groupId, promoted)) return InboundResult.REJECTED

        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != canonical(sender.onionAddress)) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                if (!chronoDag.acceptRemoteEvent(dagEv)) return InboundResult.HANDLED
                chronoDag.addAdmin(event.groupId, promoted)
                persistDagStateLocked()
            }
        } ?: run {
            event.eventId?.let { if (!chronoDag.markControlEventSeen("adm:$it")) return InboundResult.HANDLED }
            synchronized(prefsLock) {
                chronoDag.addAdmin(event.groupId, promoted)
                persistDagStateLocked()
            }
        }
        val memberName = groupDao.getMembersForGroupNow(event.groupId)
            .firstOrNull { canonical(it.memberOnionAddress) == promoted }
            ?.memberNickname ?: promoted.take(12)
        insertSystemBanner(event.groupId, "$memberName is now a group admin")
        return InboundResult.HANDLED
    }

    private suspend fun handleDisappearingTimer(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null) return InboundResult.REJECTED
        if (!isAdmin(event.groupId, sender.onionAddress)) return InboundResult.REJECTED
        val ttlMs = event.disappearingTtlMs ?: return InboundResult.REJECTED
        if (ttlMs != 0L && ttlMs !in MIN_DISAPPEARING_TTL_MS..MAX_DISAPPEARING_TTL_MS) {
            return InboundResult.REJECTED
        }
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != canonical(sender.onionAddress)) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                if (!chronoDag.acceptRemoteEvent(dagEv)) return InboundResult.HANDLED
                chronoDag.setDisappearingTtlMs(event.groupId, ttlMs)
                persistDagStateLocked()
            }
        } ?: run {
            event.eventId?.let { if (!chronoDag.markControlEventSeen("ttl:$it")) return InboundResult.HANDLED }
            synchronized(prefsLock) {
                chronoDag.setDisappearingTtlMs(event.groupId, ttlMs)
                persistDagStateLocked()
            }
        }
        purgeExpiredMessages(event.groupId)
        insertSystemBanner(event.groupId, "${sender.nickname} set disappearing messages to ${formatTimerLabel(ttlMs)}")
        return InboundResult.HANDLED
    }

    private suspend fun handleGroupEdit(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        val targetId = event.targetMessageId?.takeIf { isUuid(it) } ?: return InboundResult.REJECTED
        val cleanText = event.messageText.trim()
        if (cleanText.isEmpty() || cleanText.toByteArray(Charsets.UTF_8).size > 2_400) {
            return InboundResult.REJECTED
        }
        if (groupDao.getGroup(event.groupId) == null || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val existing = groupDao.getGroupMessage(targetId) ?: return InboundResult.HANDLED
        if (existing.groupId != event.groupId ||
            canonical(existing.senderOnionAddress) != canonical(sender.onionAddress)
        ) {
            return InboundResult.REJECTED
        }
        val currentBody = parseMessageContent(existing.text).body
        if (existing.id in _deletedMessageIdsFlow.value ||
            existing.id in _burnedMessageIdsFlow.value ||
            currentBody == DELETED_MESSAGE_TEXT ||
            currentBody == TorRichChatCodec.BURNED_CAPSULE_TEXT ||
            TorRichChatCodec.isUnburnedSecretText(existing.text)
        ) {
            return InboundResult.HANDLED
        }
        var newlyAccepted = false
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != canonical(sender.onionAddress)) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                newlyAccepted = chronoDag.acceptRemoteEvent(dagEv)
                persistDagStateLocked()
            }
        }
        val parsed = parseMessageContent(existing.text)
        val updated = formatTextWithReply(cleanText, parsed.replyToId, parsed.replyToSender, parsed.replyToPreview)
        groupDao.updateGroupMessageText(targetId, updated)
        synchronized(prefsLock) {
            prefs.edit().putBoolean("edited:$targetId", true).apply()
        }
        _editedMessageIdsFlow.update { it + targetId }
        if (newlyAccepted) {
            maybeGossipSignedGroupEvent(canonical(sender.onionAddress), event.groupId, event.dagEvent, event.relayHops)
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleGroupDelete(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        val targetId = event.targetMessageId?.takeIf { isUuid(it) } ?: return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val existing = groupDao.getGroupMessage(targetId) ?: return InboundResult.HANDLED
        if (existing.groupId != event.groupId) return InboundResult.REJECTED
        val senderCanon = canonical(sender.onionAddress)
        val isAuthor = canonical(existing.senderOnionAddress) == senderCanon
        if (!isAuthor && !isAdmin(event.groupId, senderCanon)) {
            return InboundResult.REJECTED
        }
        var newlyAccepted = false
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != senderCanon) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                newlyAccepted = chronoDag.acceptRemoteEvent(dagEv)
                persistDagStateLocked()
            }
        }
        TorRichChatCodec.AegisShredder.shredPath(existing.attachmentPath)
        groupDao.markGroupMessageDeleted(targetId, DELETED_MESSAGE_TEXT)
        synchronized(prefsLock) {
            prefs.edit().putBoolean("deleted:$targetId", true).apply()
        }
        _deletedMessageIdsFlow.update { it + targetId }
        if (newlyAccepted) {
            maybeGossipSignedGroupEvent(senderCanon, event.groupId, event.dagEvent, event.relayHops)
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleGroupShredReceipt(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging()) return InboundResult.REJECTED
        val targetId = event.targetMessageId?.takeIf { isUuid(it) } ?: return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val senderCanon = canonical(sender.onionAddress)
        var newlyAccepted = false
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != senderCanon) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                newlyAccepted = chronoDag.acceptRemoteEvent(dagEv)
                persistDagStateLocked()
            }
        }
        recordShredReceipt(targetId, senderCanon)
        if (newlyAccepted) {
            maybeGossipSignedGroupEvent(senderCanon, event.groupId, event.dagEvent, event.relayHops)
        }
        return InboundResult.HANDLED
    }

    private fun recordShredReceipt(targetMessageId: String, senderCanon: String) {
        if (senderCanon.isBlank()) return
        synchronized(prefsLock) {
            val updated = (_shredReceiptsFlow.value[targetMessageId].orEmpty()) + senderCanon
            prefs.edit().putString("shred:$targetMessageId", updated.joinToString(",")).apply()
            _shredReceiptsFlow.update { it + (targetMessageId to updated) }
        }
    }

    private suspend fun handleGroupMessage(
        sender: OnionContactManager.OnionContact,
        event: WireEvent,
        messageId: String,
        timestamp: Long
    ): InboundResult {
        if (event.messageText.isBlank() ||
            event.messageText.toByteArray(Charsets.UTF_8).size > 2_400 ||
            !sender.isEligibleForGroupMessaging()
        ) {
            return InboundResult.REJECTED
        }
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.REJECTED
        if (!isGroupMember(event.groupId, sender.onionAddress)) return InboundResult.REJECTED

        val senderCanon = canonical(sender.onionAddress)
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != senderCanon) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                chronoDag.acceptRemoteEvent(dagEv)
                persistDagStateLocked()
            }
        } ?: event.authorSigningPubKey?.let { keyHex ->
            synchronized(prefsLock) {
                if (!chronoDag.pinAuthorSigningKey(event.groupId, senderCanon, keyHex)) {
                    return InboundResult.REJECTED
                }
                persistDagStateLocked()
            }
        }

        val now = System.currentTimeMillis()
        val rawTs = event.eventTimestamp ?: timestamp
        val safeTimestamp = minOf(rawTs.coerceIn(now - 7 * 86_400_000L, now + MAX_CLOCK_SKEW_MS), now).coerceAtLeast(0L)
        val canonicalId = event.groupMessageId.takeIf { isUuid(it) } ?: messageId

        val cleanBody = parseMessageContent(event.messageText).body
        val replyId = event.replyToId?.takeIf { isUuid(it) }
        val formattedText = if (replyId != null) {
            val localTarget = groupDao.getGroupMessage(replyId)?.takeIf { it.groupId == event.groupId }
            val verifiedSender = localTarget?.senderNickname ?: event.replyToSender?.take(40)
            val verifiedPreview = localTarget?.let { TorRichChatCodec.friendlyPreviewText(it.text, it.attachmentMimeType).take(90) }
                ?: event.replyToPreview?.take(90)
            formatTextWithReply(cleanBody, replyId, verifiedSender, verifiedPreview)
        } else {
            cleanBody
        }

        val inserted = groupDao.insertGroupMessageIfAbsent(
            GroupMessageEntity(
                id = canonicalId,
                groupId = event.groupId,
                senderOnionAddress = sender.onionAddress,
                senderNickname = sender.nickname,
                text = formattedText,
                timestamp = safeTimestamp,
                isFromMe = false,
                deliveryState = STATE_DELIVERED
            )
        )
        purgeExpiredMessages(event.groupId)
        if (inserted != -1L) {
            maybeGossipSignedGroupEvent(senderCanon, event.groupId, event.dagEvent, event.relayHops)
            if (visibleGroupId != event.groupId &&
                !com.seamless.android.util.AppForegroundTracker.isAppInForeground(appContext)
            ) {
                val cleanPreview = TorRichChatCodec.friendlyPreviewText(formattedText).take(140)
                notifier.showGroupEvent(
                    event.groupId,
                    group.name,
                    "${sender.nickname}: $cleanPreview",
                    senderName = sender.nickname
                )
            }
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleGroupReaction(
        sender: OnionContactManager.OnionContact,
        event: WireEvent,
        fallbackTimestamp: Long
    ): InboundResult {
        val targetId = event.targetMessageId?.takeIf { isUuid(it) } ?: return InboundResult.REJECTED
        val emoji = event.reactionEmoji?.trim()?.take(8)?.takeIf { it.isNotEmpty() } ?: return InboundResult.REJECTED
        if (!sender.isEligibleForGroupMessaging() || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val senderCanon = canonical(sender.onionAddress)
        var newlyAccepted = false
        event.dagEvent?.let { dagEv ->
            if (dagEv.groupId != event.groupId || canonical(dagEv.authorOnion) != senderCanon) {
                return InboundResult.REJECTED
            }
            synchronized(prefsLock) {
                if (!chronoDag.acceptRemoteEvent(dagEv)) return InboundResult.HANDLED
                persistDagStateLocked()
                newlyAccepted = true
            }
        }

        val action = event.reactionAction
        if (action == "add" || action == "remove") {
            val ts = event.eventTimestamp ?: fallbackTimestamp
            applyReactionLww(targetId, senderCanon, emoji, action, ts)
        } else {
            // Legacy SGRP1 fallback: apply once per event/message id
            val dedupKey = "r1:$targetId:$senderCanon:$emoji:$fallbackTimestamp"
            if (chronoDag.markControlEventSeen(dedupKey)) {
                val currentlySame = reactionStates[targetId]?.get(senderCanon)?.let {
                    it.action != "remove" && it.emoji == emoji
                } == true
                applyReactionLww(
                    targetId,
                    senderCanon,
                    emoji,
                    if (currentlySame) "remove" else "add",
                    fallbackTimestamp
                )
            }
        }
        if (newlyAccepted) {
            maybeGossipSignedGroupEvent(senderCanon, event.groupId, event.dagEvent, event.relayHops)
        }
        return InboundResult.HANDLED
    }

    /**
     * Echo-Swarm O(log N) 1-hop epidemic gossip: forwards verified Ed25519-signed
     * Chrono-DAG group events to up to [ECHO_SWARM_FANOUT] connected online peers so
     * group chats converge even under asymmetric Tor circuit outages or slow relays.
     */
    private fun maybeGossipSignedGroupEvent(
        senderCanonical: String,
        groupId: String,
        dagEv: ChronoDagGroupSync.SignedDagEvent?,
        relayHops: Int
    ) {
        if (relayHops <= 0 || dagEv == null) return
        ioScope.launch {
            runCatching {
                val myCanonical = canonical(contacts.getMyContactInfo().onionAddress)
                val authorCanonical = canonical(dagEv.authorOnion)
                val circuitMgr = TorCircuitManager.getInstance(appContext)
                val relayTargets = groupDao.getMembersForGroupNow(groupId)
                    .asSequence()
                    .map { canonical(it.memberOnionAddress) }
                    .filter { it.isNotBlank() && it != myCanonical && it != senderCanonical && it != authorCanonical }
                    .distinct()
                    .mapNotNull { contacts.getContactByOnion(it) }
                    .filter { it.isEligibleForGroupMessaging() && circuitMgr.hasActiveConnection(it.onionAddress) }
                    .take(ECHO_SWARM_FANOUT)
                    .toList()
                if (relayTargets.isEmpty()) return@runCatching

                val gossipPayload = encode(
                    WireEvent(
                        version = VERSION_V2,
                        type = "sync_batch",
                        groupId = groupId,
                        syncBatch = ChronoDagGroupSync.SyncBatch(groupId, listOf(dagEv)),
                        authorSigningPubKey = localSigningPublicKeyHex,
                        relayHops = relayHops - 1
                    )
                )
                if (gossipPayload.toByteArray(Charsets.UTF_8).size > MAX_WIRE_PAYLOAD_BYTES) return@runCatching
                val torService = TorMessagingService.getInstance(appContext)
                for (target in relayTargets) {
                    torService.sendMessageToOnion(
                        recipientOnion = target.onionAddress,
                        text = gossipPayload,
                        completesUiMessage = false
                    )
                }
            }
        }
    }

    // ───────────────────────────── Chrono-DAG Peer-Assisted Sync ─────────────────────────────

    /** Offers Chrono-DAG group summaries whenever a known peer comes online. */
    suspend fun onPeerOnline(peerOnion: String) {
        val peerCanon = canonical(peerOnion)
        val contact = contacts.getContactByOnion(peerCanon) ?: return
        if (!contact.isEligibleForGroupMessaging()) return
        val groups = runCatching { groupDao.getAllGroups().first() }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        for (group in groups) {
            if (chronoDag.isGroupTombstoned(group.groupId)) continue
            if (!isGroupMember(group.groupId, peerCanon)) continue
            val cooldownKey = "$peerCanon:${group.groupId}"
            val last = syncOfferCooldowns[cooldownKey] ?: 0L
            if (now - last < SYNC_OFFER_COOLDOWN_MS) continue
            val offer = chronoDag.buildSyncOffer(group.groupId) ?: continue
            syncOfferCooldowns[cooldownKey] = now
            val payload = encode(
                WireEvent(
                    version = VERSION_V2,
                    type = "sync_offer",
                    groupId = group.groupId,
                    epoch = offer.epoch,
                    syncOffer = offer,
                    authorSigningPubKey = localSigningPublicKeyHex
                )
            )
            queue(contact.onionAddress, payload)
        }
    }

    private suspend fun handleSyncOffer(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging() || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val offer = event.syncOffer ?: return InboundResult.REJECTED
        if (offer.groupId != event.groupId) return InboundResult.REJECTED
        val missing = chronoDag.computeMissingEventIds(event.groupId, offer)
        if (missing.isEmpty()) return InboundResult.HANDLED
        val req = encode(
            WireEvent(
                version = VERSION_V2,
                type = "sync_request",
                groupId = event.groupId,
                syncRequest = ChronoDagGroupSync.SyncRequest(event.groupId, missing),
                authorSigningPubKey = localSigningPublicKeyHex
            )
        )
        queue(sender.onionAddress, req)
        return InboundResult.HANDLED
    }

    private suspend fun handleSyncRequest(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging() || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val req = event.syncRequest ?: return InboundResult.REJECTED
        if (req.groupId != event.groupId) return InboundResult.REJECTED
        val batches = chronoDag.buildSyncBatches(event.groupId, req.requestedEventIds)
        batches.forEach { batch ->
            val payload = encode(
                WireEvent(
                    version = VERSION_V2,
                    type = "sync_batch",
                    groupId = event.groupId,
                    syncBatch = batch,
                    authorSigningPubKey = localSigningPublicKeyHex
                )
            )
            if (payload.toByteArray(Charsets.UTF_8).size <= MAX_WIRE_PAYLOAD_BYTES) {
                queue(sender.onionAddress, payload)
            }
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleSyncBatch(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (!sender.isEligibleForGroupMessaging() || !isGroupMember(event.groupId, sender.onionAddress)) {
            return InboundResult.REJECTED
        }
        val batch = event.syncBatch ?: return InboundResult.REJECTED
        if (batch.groupId != event.groupId) return InboundResult.REJECTED
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.REJECTED
        val members = groupDao.getMembersForGroupNow(event.groupId)
        val meCanon = canonical(contacts.getMyContactInfo().onionAddress)

        for (signed in batch.events.take(MAX_SYNC_BATCH_EVENTS)) {
            if (signed.groupId != event.groupId) continue
            val authorCanon = canonical(signed.authorOnion)
            if (authorCanon == meCanon || !isGroupMember(event.groupId, authorCanon)) continue
            val accepted = synchronized(prefsLock) {
                chronoDag.acceptRemoteEvent(signed).also { ok ->
                    if (ok) persistDagStateLocked()
                }
            }
            if (!accepted) continue

            val authorContact = contacts.getContactByOnion(authorCanon)
            val authorName = authorContact?.nickname
                ?: members.firstOrNull { canonical(it.memberOnionAddress) == authorCanon }?.memberNickname
                ?: authorCanon.take(12)
            applySyncedDagEvent(group, authorCanon, authorName, signed)
        }
        return InboundResult.HANDLED
    }

    private suspend fun applySyncedDagEvent(
        group: PrivateGroupEntity,
        authorCanon: String,
        authorName: String,
        signed: ChronoDagGroupSync.SignedDagEvent
    ) {
        val json = runCatching { JSONObject(signed.payloadJson) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        when (signed.type) {
            "message" -> {
                val cleanBody = json.optString("messageText").trim()
                if (cleanBody.isBlank() || cleanBody.toByteArray(Charsets.UTF_8).size > 2_400) return
                val msgId = json.optString("groupMessageId").takeIf { isUuid(it) } ?: signed.eventId
                if (!isUuid(msgId)) return
                val replyId = json.optString("replyToId").takeIf { isUuid(it) }
                val formatted = if (replyId != null) {
                    val localTarget = groupDao.getGroupMessage(replyId)?.takeIf { it.groupId == group.groupId }
                    val rSender = localTarget?.senderNickname ?: json.optString("replyToSender").take(40).takeIf { it.isNotBlank() }
                    val rPreview = localTarget?.let { parseMessageContent(it.text).body.take(90) }
                        ?: json.optString("replyToPreview").take(90).takeIf { it.isNotBlank() }
                    formatTextWithReply(cleanBody, replyId, rSender, rPreview)
                } else {
                    cleanBody
                }
                val safeTs = minOf(signed.timestamp.coerceIn(now - 7 * 86_400_000L, now + MAX_CLOCK_SKEW_MS), now)
                val inserted = groupDao.insertGroupMessageIfAbsent(
                    GroupMessageEntity(
                        id = msgId,
                        groupId = group.groupId,
                        senderOnionAddress = authorCanon,
                        senderNickname = authorName,
                        text = formatted,
                        timestamp = safeTs,
                        isFromMe = false,
                        deliveryState = STATE_DELIVERED
                    )
                )
                if (inserted != -1L && visibleGroupId != group.groupId &&
                    !com.seamless.android.util.AppForegroundTracker.isAppInForeground(appContext)
                ) {
                    val cleanPreview = TorRichChatCodec.friendlyPreviewText(formatted).take(140)
                    notifier.showGroupEvent(group.groupId, group.name, "$authorName: $cleanPreview", senderName = authorName)
                }
            }
            "edit" -> {
                val targetId = json.optString("targetMessageId").takeIf { isUuid(it) } ?: return
                val newText = json.optString("messageText").trim()
                if (newText.isBlank() || newText.toByteArray(Charsets.UTF_8).size > 2_400) return
                val existing = groupDao.getGroupMessage(targetId) ?: return
                if (existing.groupId != group.groupId || canonical(existing.senderOnionAddress) != authorCanon) return
                val currentBody = parseMessageContent(existing.text).body
                if (existing.id in _deletedMessageIdsFlow.value ||
                    existing.id in _burnedMessageIdsFlow.value ||
                    currentBody == DELETED_MESSAGE_TEXT ||
                    currentBody == TorRichChatCodec.BURNED_CAPSULE_TEXT ||
                    TorRichChatCodec.isUnburnedSecretText(existing.text)
                ) return
                val parsed = parseMessageContent(existing.text)
                groupDao.updateGroupMessageText(
                    targetId,
                    formatTextWithReply(newText, parsed.replyToId, parsed.replyToSender, parsed.replyToPreview)
                )
                synchronized(prefsLock) { prefs.edit().putBoolean("edited:$targetId", true).apply() }
                _editedMessageIdsFlow.update { it + targetId }
            }
            "delete" -> {
                val targetId = json.optString("targetMessageId").takeIf { isUuid(it) } ?: return
                val existing = groupDao.getGroupMessage(targetId) ?: return
                if (existing.groupId != group.groupId) return
                if (canonical(existing.senderOnionAddress) != authorCanon && !isAdmin(group.groupId, authorCanon)) return
                TorRichChatCodec.AegisShredder.shredPath(existing.attachmentPath)
                groupDao.markGroupMessageDeleted(targetId, DELETED_MESSAGE_TEXT)
                synchronized(prefsLock) { prefs.edit().putBoolean("deleted:$targetId", true).apply() }
                _deletedMessageIdsFlow.update { it + targetId }
            }
            "shred_receipt" -> {
                val targetId = json.optString("targetMessageId").takeIf { isUuid(it) } ?: return
                recordShredReceipt(targetId, authorCanon)
            }
            "reaction" -> {
                val targetId = json.optString("targetMessageId").takeIf { isUuid(it) } ?: return
                val emoji = json.optString("reactionEmoji").trim().take(8).takeIf { it.isNotEmpty() } ?: return
                val action = json.optString("reactionAction", "add").takeIf { it == "add" || it == "remove" } ?: return
                applyReactionLww(targetId, authorCanon, emoji, action, signed.timestamp)
            }
            "rename" -> {
                if (!isAdmin(group.groupId, authorCanon)) return
                val newName = json.optString("groupName").trim()
                if (newName.length in 1..80) {
                    groupDao.renameGroup(group.groupId, newName)
                }
            }
            "disappearing" -> {
                if (!isAdmin(group.groupId, authorCanon)) return
                val ttlMs = json.optLong("disappearingTtlMs", -1L)
                if (ttlMs == 0L || ttlMs in MIN_DISAPPEARING_TTL_MS..MAX_DISAPPEARING_TTL_MS) {
                    synchronized(prefsLock) {
                        chronoDag.setDisappearingTtlMs(group.groupId, ttlMs)
                        persistDagStateLocked()
                    }
                    purgeExpiredMessages(group.groupId)
                }
            }
        }
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private suspend fun queue(recipientOnion: String, payload: String): Boolean {
        val result = TorMessagingService.getInstance(appContext).sendMessageToOnion(
            recipientOnion = recipientOnion,
            text = payload,
            completesUiMessage = false
        )
        if (result.isFailure) {
            Log.w(TAG, "Failed to queue group control payload (${result.exceptionOrNull()?.message})")
        }
        return result.isSuccess
    }

    internal fun encode(event: WireEvent): String {
        val jsonBytes = gson.toJson(event).toByteArray(Charsets.UTF_8)
        val rawV1 = PREFIX + android.util.Base64.encodeToString(
            jsonBytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val compressed = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { gzip -> gzip.write(jsonBytes) }
            out.toByteArray()
        }
        val rawV2 = PREFIX_V2 + android.util.Base64.encodeToString(
            compressed,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val forceV2 = event.version >= VERSION_V2 ||
            event.type == "invite_part" ||
            event.dagEvent != null ||
            event.syncOffer != null ||
            event.syncRequest != null ||
            event.syncBatch != null ||
            event.reactionAction != null ||
            event.disappearingTtlMs != null ||
            event.promotedAdminOnion != null ||
            event.eventId != null
        return if (forceV2 || rawV2.length < rawV1.length) rawV2 else rawV1
    }

    internal fun encodeInvitePayloads(baseEvent: WireEvent): List<String> {
        val single = encode(baseEvent)
        if (single.toByteArray(Charsets.UTF_8).size <= MAX_WIRE_PAYLOAD_BYTES) {
            return listOf(single)
        }
        val chunkId = UUID.randomUUID().toString()
        val members = baseEvent.members
        val batchSizes = listOf(10, 8, 6, 4, 3, 2, 1)
        for (batchSize in batchSizes) {
            val batches = members.chunked(batchSize)
            if (batches.size > MAX_INVITE_PARTS) continue
            val encodedParts = batches.mapIndexed { idx, slice ->
                encode(
                    baseEvent.copy(
                        version = VERSION_V2,
                        type = "invite_part",
                        chunkId = chunkId,
                        partIndex = idx,
                        totalParts = batches.size,
                        members = slice
                    )
                )
            }
            if (encodedParts.all { it.toByteArray(Charsets.UTF_8).size <= MAX_WIRE_PAYLOAD_BYTES }) {
                return encodedParts
            }
        }
        return listOf(single)
    }

    internal fun decode(payload: String): WireEvent? = runCatching {
        when {
            payload.startsWith(PREFIX_V2) -> {
                val compressed = android.util.Base64.decode(
                    payload.removePrefix(PREFIX_V2),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                )
                if (compressed.size > MAX_DECOMPRESSED_BYTES) return null
                val baos = ByteArrayOutputStream()
                GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                    val buf = ByteArray(1024)
                    var total = 0
                    while (true) {
                        val read = gzip.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_DECOMPRESSED_BYTES) return null
                        baos.write(buf, 0, read)
                    }
                }
                gson.fromJson(String(baos.toByteArray(), Charsets.UTF_8), WireEvent::class.java)
            }
            payload.startsWith(PREFIX) -> {
                val raw = android.util.Base64.decode(
                    payload.removePrefix(PREFIX),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                )
                if (raw.size > MAX_DECOMPRESSED_BYTES) return null
                gson.fromJson(String(raw, Charsets.UTF_8), WireEvent::class.java)
            }
            else -> null
        }
    }.getOrNull()

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value); true }.getOrDefault(false)

    private suspend fun isGroupMember(groupId: String, onionAddress: String): Boolean {
        val expected = canonical(onionAddress)
        return groupDao.getMembersForGroupNow(groupId).any { member ->
            canonical(member.memberOnionAddress) == expected
        }
    }

    /** Repairs older creator groups whose local member row predates endpoint canonicalization. */
    private suspend fun ensureLocalMembership(group: PrivateGroupEntity, me: OnionContactManager.OnionContact) {
        if (!group.isCreatedByMe || isGroupMember(group.groupId, me.onionAddress)) return
        val creator = canonical(group.creatorOnionAddress)
        val local = canonical(me.onionAddress)
        if (local.isBlank()) return
        if (creator != local) {
            groupDao.removeMember(group.groupId, group.creatorOnionAddress)
        }
        groupDao.insertMember(GroupMemberEntity(group.groupId, me.onionAddress, me.nickname))
    }

    private fun OnionContactManager.OnionContact.toWireMember(signingPubKey: String? = null) = WireMember(
        onionAddress = onionAddress,
        nickname = nickname,
        publicKey = publicKey,
        mailboxOnion = mailboxOnion,
        avatarId = avatarId,
        signingPubKey = signingPubKey
    )

    private fun OnionContactManager.OnionContact.isEligibleForGroupMessaging(): Boolean =
        !requiresRendezvous() &&
            effectiveTrustState() != TorContactTrustState.BLOCKED &&
            effectiveTrustState() != TorContactTrustState.KEY_CHANGED

    private fun canonical(endpoint: String): String =
        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(endpoint).ifBlank {
            endpoint.trim().lowercase()
        }

    private fun formatTimerLabel(ttlMs: Long): String = when (ttlMs) {
        0L -> "Off"
        5 * 60_000L -> "5 minutes"
        60 * 60_000L -> "1 hour"
        24 * 60 * 60_000L -> "24 hours"
        7 * 24 * 60 * 60_000L -> "7 days"
        else -> "${ttlMs / 60_000L}m"
    }

    private suspend fun insertSystemBanner(groupId: String, text: String) {
        groupDao.insertGroupMessageIfAbsent(
            GroupMessageEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                senderOnionAddress = SYSTEM_SENDER,
                senderNickname = SYSTEM_SENDER,
                text = text,
                timestamp = System.currentTimeMillis(),
                isFromMe = false,
                deliveryState = STATE_DELIVERED
            )
        )
    }

    private fun importGroupPeer(member: WireMember, myOnionAddress: String) {
        if (canonical(member.onionAddress) == canonical(myOnionAddress)) return
        val wasKnown = contacts.getContactByOnion(member.onionAddress) != null
        contacts.addContact(
            OnionContactManager.OnionContact(
                id = UUID.randomUUID().toString(),
                nickname = member.nickname,
                onionAddress = member.onionAddress,
                publicKey = member.publicKey,
                avatarId = OnionContactManager.OnionContact.canonicalAvatarId(member.avatarId),
                mailboxOnion = member.mailboxOnion
            )
        )
        val saved = contacts.getContactByOnion(member.onionAddress) ?: return
        ioScope.launch {
            runCatching {
                TorMessagingService.getInstance(appContext).registerContactMailboxForPolling(saved)
                if (!wasKnown) {
                    TorMessagingService.getInstance(appContext).refreshPeerConnection(saved.id)
                }
            }
        }
    }
}
