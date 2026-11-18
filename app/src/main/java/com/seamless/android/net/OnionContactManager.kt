@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.net

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.seamless.android.profile.ProfileManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seamless.android.crypto.EncryptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Onion contacts for zero-server P2P messaging.
 */
class OnionContactManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "OnionContactManager"
        private const val PREFS_NAME = "onion_contacts"
        private const val KEY_CONTACTS = "contacts"
        private const val SEAMLESS_URI_SCHEME = "seamless://"
        private const val UNRESOLVED_ONION = "not-available.onion:18888"

        @Volatile
        private var instance: OnionContactManager? = null

        fun getInstance(context: Context): OnionContactManager {
            return instance ?: synchronized(this) {
                instance ?: OnionContactManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val contacts = ConcurrentHashMap<String, OnionContact>()
    private val _contactsFlow = MutableStateFlow<List<OnionContact>>(emptyList())
    val contactsFlow: StateFlow<List<OnionContact>> = _contactsFlow.asStateFlow()
    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        loadContacts()
    }

    data class OnionContact(
        val id: String,
        val nickname: String,
        val onionAddress: String,
        val publicKey: String,
        val avatarId: String = "aurora",
        val addedAt: Long = System.currentTimeMillis(),
        val isFavorite: Boolean = false,
        val notes: String? = null,
        val needsRendezvous: Boolean = false,
        val mailboxOnion: String? = null,
        val lastSeenOnline: Long = 0L,
        val ephemeralRendezvousId: String? = null,
        val trustState: TorContactTrustState? = TorContactTrustState.UNVERIFIED,
        val verifiedAt: Long = 0L
    ) {
        fun requiresRendezvous(): Boolean {
            val canonicalOnion = canonicalizeOnionEndpoint(onionAddress)
            return needsRendezvous ||
                canonicalOnion.isBlank() ||
                canonicalOnion.equals(UNRESOLVED_ONION, ignoreCase = true) ||
                !isValidOnionEndpoint(canonicalOnion)
        }

        fun toShareableLink(): String {
            val canonicalOnion = canonicalizeOnionEndpoint(onionAddress)
            val builder = Uri.parse(SEAMLESS_URI_SCHEME).buildUpon()
                .authority("add")
                .appendQueryParameter("k", publicKey)
                .appendQueryParameter("n", nickname)
                .appendQueryParameter("a", canonicalAvatarId(avatarId))

            if (!requiresRendezvous()) {
                builder.appendQueryParameter("o", canonicalOnion)
            }
            mailboxOnion?.takeIf { it.isNotBlank() }?.let {
                builder.appendQueryParameter("m", it)
            }
            ephemeralRendezvousId?.takeIf { it.isNotBlank() }?.let {
                builder.appendQueryParameter("r", it)
            }
            return builder.build().toString()
        }

        fun toEphemeralShareLink(): String {
            val rendezvousId = ephemeralRendezvousId ?: UUID.randomUUID().toString()
            return Uri.parse(SEAMLESS_URI_SCHEME).buildUpon()
                .authority("add")
                .appendQueryParameter("k", publicKey)
                .appendQueryParameter("n", nickname)
                .appendQueryParameter("r", rendezvousId)
                .build()
                .toString()
        }

        fun toQrData(): String {
            val name = URLEncoder.encode(nickname, "UTF-8")
            val canonicalOnion = canonicalizeOnionEndpoint(onionAddress)
            // Keep mailbox and avatar in fixed slots. This retains compatibility
            // with old three/four-field QR payloads and prevents an avatar ID
            // from ever being interpreted as a mailbox endpoint.
            val mailbox = mailboxOnion?.takeIf { it.isNotBlank() }?.let {
                URLEncoder.encode(it, "UTF-8")
            }.orEmpty()
            val avatar = canonicalAvatarId(avatarId)
            return if (requiresRendezvous()) {
                "|$publicKey|$name|$mailbox|$avatar"
            } else {
                "${URLEncoder.encode(canonicalOnion, "UTF-8")}|$publicKey|$name|$mailbox|$avatar"
            }
        }

        fun effectiveTrustState(): TorContactTrustState {
            return trustState ?: TorContactTrustState.UNVERIFIED
        }

        fun safetyNumber(myPublicKey: String = ""): String {
            val cleanPeerKey = publicKey.trim().lowercase()
            val cleanMyKey = myPublicKey.trim().lowercase()
            val input = if (cleanMyKey.isNotBlank()) {
                val sorted = listOf(cleanMyKey, cleanPeerKey).sorted()
                "SeamlessSafetyNumber/v1|${sorted[0]}|${sorted[1]}"
            } else {
                "$cleanPeerKey|${canonicalizeOnionEndpoint(onionAddress)}"
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
                .chunked(4)
                .take(12)
                .joinToString(" ")
                .uppercase()
        }

        companion object {
            private val V3_ONION_HOST = Regex("^[a-z2-7]{56}\\.onion$")
            private val AVATAR_IDS = setOf("aurora", "ember", "tide", "orchid", "mint", "copper")

            fun canonicalAvatarId(raw: String?): String = raw
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in AVATAR_IDS }
                ?: "aurora"

            /**
             * Tor P2P endpoints are v3 onion hosts, optionally followed by a
             * valid TCP port. Keeping this check at the contact boundary avoids
             * queuing traffic to look-alike or malformed destinations.
             */
            fun isValidOnionEndpoint(raw: String): Boolean {
                val endpoint = raw.trim().lowercase()
                val host = endpoint.substringBefore(":")
                val portText = endpoint.substringAfter(":", "")
                if (!V3_ONION_HOST.matches(host)) return false
                return portText.isEmpty() || (portText.toIntOrNull() in 1..65_535)
            }

            fun canonicalizeOnionEndpoint(raw: String): String {
                val clean = raw.trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .trimEnd('/')
                val host = clean.substringBefore(":").trim().lowercase()
                if (host.isBlank() || host == UNRESOLVED_ONION.substringBefore(":")) return ""
                val portPart = clean.substringAfter(":", "").trim()
                val port = portPart.toIntOrNull()
                val onionHost = when {
                    host.endsWith(".onion") -> host
                    host.length == 56 && host.all { it in 'a'..'z' || it in '2'..'7' } -> "$host.onion"
                    else -> host
                }
                return if (port != null) "$onionHost:$port" else onionHost
            }

            fun fromShareableLink(uri: String): OnionContact? {
                return try {
                    if (!uri.startsWith(SEAMLESS_URI_SCHEME)) return null
                    val parsed = Uri.parse(uri)
                    val publicKey = parsed.getQueryParameter("k")?.trim().orEmpty()
                    val nickname = parsed.getQueryParameter("n")?.trim().orEmpty()
                    if (publicKey.isBlank() || nickname.isBlank()) return null

                    val onionAddress = canonicalizeOnionEndpoint(parsed.getQueryParameter("o")?.trim().orEmpty())
                    val mailboxOnion = canonicalizeOnionEndpoint(parsed.getQueryParameter("m")?.trim().orEmpty())
                        .takeIf(::isValidOnionEndpoint)
                    val avatarId = canonicalAvatarId(parsed.getQueryParameter("a"))
                    val rendezvousId = parsed.getQueryParameter("r")?.trim()
                    val unresolved = onionAddress.isBlank() ||
                        onionAddress.equals(UNRESOLVED_ONION, ignoreCase = true) ||
                        !isValidOnionEndpoint(onionAddress)

                    OnionContact(
                        id = UUID.randomUUID().toString(),
                        nickname = nickname,
                        onionAddress = if (unresolved) "" else onionAddress,
                        publicKey = publicKey,
                        avatarId = avatarId,
                        needsRendezvous = unresolved,
                        mailboxOnion = mailboxOnion,
                        ephemeralRendezvousId = rendezvousId,
                        trustState = TorContactTrustState.UNVERIFIED
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse contact link: ${e.message}")
                    null
                }
            }

            fun fromQrData(data: String): OnionContact? {
                return try {
                    if (data.startsWith(SEAMLESS_URI_SCHEME)) return fromShareableLink(data)

                    val parts = data.split("|")
                    if (parts.size < 3) return null

                    val onion = canonicalizeOnionEndpoint(URLDecoder.decode(parts[0], "UTF-8").trim())
                    val publicKey = parts[1].trim()
                    val nickname = URLDecoder.decode(parts[2], "UTF-8").trim()
                    val mailbox = parts.getOrNull(3)?.let {
                        canonicalizeOnionEndpoint(URLDecoder.decode(it, "UTF-8").trim())
                    }?.takeIf(::isValidOnionEndpoint)
                    val avatarId = canonicalAvatarId(parts.getOrNull(4))

                    if (publicKey.isBlank() || nickname.isBlank()) return null
                    val unresolved = onion.isBlank() ||
                        onion.equals(UNRESOLVED_ONION, ignoreCase = true) ||
                        !isValidOnionEndpoint(onion)

                    OnionContact(
                        id = UUID.randomUUID().toString(),
                        nickname = nickname,
                        onionAddress = if (unresolved) "" else onion,
                        publicKey = publicKey,
                        avatarId = avatarId,
                        needsRendezvous = unresolved,
                        mailboxOnion = mailbox,
                        trustState = TorContactTrustState.UNVERIFIED
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse QR contact: ${e.message}")
                    null
                }
            }
        }
    }

    fun setMyMailboxOnion(mailboxOnion: String?) {
        val canonical = mailboxOnion
            ?.let { OnionContact.canonicalizeOnionEndpoint(it) }
            ?.takeIf { OnionContact.isValidOnionEndpoint(it) }
        prefs.edit().putString("my_mailbox_onion", canonical).apply()
    }

    fun getMyMailboxOnion(): String? {
        return prefs.getString("my_mailbox_onion", null)
            ?.let { OnionContact.canonicalizeOnionEndpoint(it) }
            ?.takeIf { OnionContact.isValidOnionEndpoint(it) }
    }

    /**
     * Builds the current shareable identity. Defaults to the persisted mailbox
     * hidden service endpoint when not explicitly supplied by the caller.
     */
    fun getMyContactInfo(mailboxOnion: String? = getMyMailboxOnion()): OnionContact {
        val rawOnion = TorHiddenServiceManager.getInstance(context)
            .getOnionAddress()
            .ifBlank {
                (context.applicationContext as? android.app.Application)
                    ?.let { TorDaemonManager.getInstance(it).getOnionAddress() }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$it:${TorHiddenServiceManager.HIDDEN_SERVICE_PORT}" }
                    .orEmpty()
            }
            .ifBlank {
                runCatching {
                    com.seamless.android.identity.SecureIdentityStateManager(context)
                        .getSecureValue("tor_hs_onion_address")
                }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$it:${TorHiddenServiceManager.HIDDEN_SERVICE_PORT}" }
                    .orEmpty()
            }
        val onion = rawOnion
            .let { OnionContact.canonicalizeOnionEndpoint(it) }
            .takeIf { it.isNotBlank() && !it.equals(UNRESOLVED_ONION, ignoreCase = true) }
            .orEmpty()
        val publicKey = getMyPublicKeyHex()
        val avatarId = ProfileManager.getInstance(context).currentUserProfile.value?.avatarStyle ?: "aurora"
        val nickname = context.getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
            .getString("nickname", null)
            ?.takeIf { it.isNotBlank() }
            ?: "anon-${publicKey.take(6).ifBlank { "tor" }}"
        val effectiveMailbox = (mailboxOnion ?: getMyMailboxOnion())
            ?.let { OnionContact.canonicalizeOnionEndpoint(it) }
            ?.takeIf { OnionContact.isValidOnionEndpoint(it) }

        return OnionContact(
            id = "me",
            nickname = nickname,
            onionAddress = onion,
            publicKey = publicKey,
            avatarId = OnionContact.canonicalAvatarId(avatarId),
            needsRendezvous = onion.isBlank(),
            mailboxOnion = effectiveMailbox,
            trustState = TorContactTrustState.VERIFIED,
            verifiedAt = System.currentTimeMillis()
        )
    }

    fun getMyShareableLink(mailboxOnion: String? = getMyMailboxOnion()): String =
        getMyContactInfo(mailboxOnion).toShareableLink()

    fun getMyQrData(mailboxOnion: String? = getMyMailboxOnion()): String =
        getMyContactInfo(mailboxOnion).toQrData()

    fun addContact(contact: OnionContact): Boolean {
        val normalized = normalize(contact)
        if (normalized.publicKey.isBlank() || normalized.nickname.isBlank()) return false

        val samePublicKey = contacts.values.find {
            it.publicKey.equals(normalized.publicKey, ignoreCase = true)
        }
        if (samePublicKey != null) {
            val hasNewOnion = !normalized.requiresRendezvous() &&
                (samePublicKey.requiresRendezvous() ||
                    normalizeOnion(samePublicKey.onionAddress) != normalizeOnion(normalized.onionAddress))
            val hasNewMailbox = !normalized.mailboxOnion.isNullOrBlank() &&
                normalized.mailboxOnion != samePublicKey.mailboxOnion
            val hasNewAvatar = samePublicKey.avatarId != normalized.avatarId
            if (hasNewOnion || hasNewMailbox || hasNewAvatar) {
                contacts[samePublicKey.id] = samePublicKey.copy(
                    nickname = normalized.nickname.ifBlank { samePublicKey.nickname },
                    onionAddress = if (!normalized.requiresRendezvous()) normalized.onionAddress else samePublicKey.onionAddress,
                    needsRendezvous = if (!normalized.requiresRendezvous()) false else samePublicKey.needsRendezvous,
                    mailboxOnion = normalized.mailboxOnion ?: samePublicKey.mailboxOnion,
                    avatarId = normalized.avatarId,
                    ephemeralRendezvousId = normalized.ephemeralRendezvousId ?: samePublicKey.ephemeralRendezvousId,
                    lastSeenOnline = System.currentTimeMillis()
                )
                saveContacts()
                updateFlow()
                Log.i(TAG, "Updated contact ${normalized.nickname} with latest endpoint/mailbox metadata")
                return true
            }
            Log.w(TAG, "Contact with this public key already exists")
            return false
        }

        val sameUnresolvedNickname = contacts.values.find {
            it.requiresRendezvous() &&
                it.nickname.equals(normalized.nickname, ignoreCase = true) &&
                !normalized.requiresRendezvous()
        }
        if (sameUnresolvedNickname != null && false) { // SECURITY: Never overwrite pinned publicKey based on display nickname alone
            contacts[sameUnresolvedNickname.id] = sameUnresolvedNickname.copy(
                nickname = normalized.nickname,
                onionAddress = normalized.onionAddress,
                publicKey = normalized.publicKey,
                needsRendezvous = false,
                mailboxOnion = normalized.mailboxOnion ?: sameUnresolvedNickname.mailboxOnion,
                avatarId = normalized.avatarId,
                ephemeralRendezvousId = normalized.ephemeralRendezvousId ?: sameUnresolvedNickname.ephemeralRendezvousId,
                trustState = TorContactTrustState.UNVERIFIED,
                verifiedAt = 0L
            )
            saveContacts()
            updateFlow()
            Log.i(TAG, "Upgraded unresolved contact ${normalized.nickname} with a real onion address")
            return true
        }

        if (!normalized.requiresRendezvous()) {
            val sameOnion = getContactByOnion(normalized.onionAddress)
            if (sameOnion != null && !sameOnion.publicKey.equals(normalized.publicKey, ignoreCase = true)) {
                Log.w(TAG, "Rejected conflicting public key for existing onion contact ${sameOnion.nickname}")
                return false
            }
            if (sameOnion != null && false) {
                contacts[sameOnion.id] = sameOnion.copy(
                    nickname = normalized.nickname.ifBlank { sameOnion.nickname },
                    publicKey = normalized.publicKey,
                    mailboxOnion = normalized.mailboxOnion ?: sameOnion.mailboxOnion,
                    avatarId = normalized.avatarId,
                    lastSeenOnline = System.currentTimeMillis(),
                    trustState = TorContactTrustState.KEY_CHANGED,
                    verifiedAt = 0L
                )
                saveContacts()
                updateFlow()
                Log.w(TAG, "Key change detected for ${sameOnion.nickname}; updated key and marked unverified")
                return false
            }
        }

        contacts[normalized.id] = normalized
        saveContacts()
        updateFlow()
        Log.i(TAG, "Added onion contact ${normalized.nickname}")
        return true
    }

    fun addContactByPublicKeyOnly(publicKey: String, nickname: String): OnionContact? {
        val contact = OnionContact(
            id = UUID.randomUUID().toString(),
            nickname = nickname.trim().ifBlank { "Unknown" },
            onionAddress = "",
            publicKey = publicKey.trim(),
            needsRendezvous = true,
            trustState = TorContactTrustState.UNVERIFIED
        )
        return if (addContact(contact)) contact else getContactByPublicKey(publicKey)
    }

    fun resolveRendezvousAddress(contactId: String, resolvedOnion: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(
            onionAddress = resolvedOnion,
            needsRendezvous = false,
            lastSeenOnline = System.currentTimeMillis()
        )
        saveContacts()
        updateFlow()
    }

    fun updateMailboxAddress(contactId: String, mailboxOnion: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(mailboxOnion = mailboxOnion)
        saveContacts()
        updateFlow()
    }

    fun updateLastSeen(contactId: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(lastSeenOnline = System.currentTimeMillis())
        saveContacts()
        updateFlow()
    }

    fun verifyContact(contactId: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(
            trustState = TorContactTrustState.VERIFIED,
            verifiedAt = System.currentTimeMillis()
        )
        saveContacts()
        updateFlow()
    }

    fun blockContact(contactId: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(trustState = TorContactTrustState.BLOCKED)
        saveContacts()
        updateFlow()
    }

    fun markUnverified(contactId: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(
            trustState = TorContactTrustState.UNVERIFIED,
            verifiedAt = 0L
        )
        saveContacts()
        updateFlow()
    }

    fun getMailboxContacts(): List<OnionContact> {
        return getAllContacts().filter { !it.mailboxOnion.isNullOrBlank() }
    }

    fun getRendezvousContacts(): List<OnionContact> {
        return getAllContacts().filter { it.requiresRendezvous() }
    }

    fun addContactFromLink(link: String): OnionContact? {
        val contact = OnionContact.fromShareableLink(link) ?: return null
        return if (addContact(contact)) contact else getContactByPublicKey(contact.publicKey)
    }

    fun addContactFromQr(qrData: String): OnionContact? {
        val contact = OnionContact.fromQrData(qrData) ?: return null
        return if (addContact(contact)) contact else getContactByPublicKey(contact.publicKey)
    }

    fun removeContact(contactId: String) {
        contacts.remove(contactId)
        saveContacts()
        updateFlow()
    }

    fun updateContact(contact: OnionContact) {
        contacts[contact.id] = normalize(contact)
        saveContacts()
        updateFlow()
    }

    fun getAllContacts(): List<OnionContact> {
        return contacts.values.sortedByDescending { it.lastSeenOnline.takeIf { seen -> seen > 0L } ?: it.addedAt }
    }

    fun getContact(contactId: String): OnionContact? = contacts[contactId]

    fun getContactByOnion(onionAddress: String): OnionContact? {
        val wanted = normalizeOnion(onionAddress)
        if (wanted.isBlank()) return null
        return contacts.values.find { normalizeOnion(it.onionAddress) == wanted }
    }

    fun getContactByPublicKey(publicKey: String): OnionContact? {
        return contacts.values.find { it.publicKey.equals(publicKey, ignoreCase = true) }
    }

    fun getSafetyNumber(contact: OnionContact): String =
        contact.safetyNumber(getMyContactInfo().publicKey)

    fun toggleFavorite(contactId: String) {
        val contact = contacts[contactId] ?: return
        contacts[contactId] = contact.copy(isFavorite = !contact.isFavorite)
        saveContacts()
        updateFlow()
    }

    fun searchContacts(query: String): List<OnionContact> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return getAllContacts()
        return getAllContacts().filter {
            it.nickname.lowercase().contains(q) ||
                it.onionAddress.lowercase().contains(q) ||
                it.publicKey.lowercase().contains(q)
        }
    }

    private fun loadContacts() {
        try {
            val json = prefs.getString(KEY_CONTACTS, null) ?: return
            val type = object : TypeToken<List<OnionContact>>() {}.type
            val loaded: List<OnionContact> = gson.fromJson(json, type) ?: emptyList()
            contacts.clear()
            loaded.map { normalize(it) }.forEach { contacts[it.id] = it }
            updateFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load onion contacts: ${e.message}")
        }
    }

    private fun saveContacts() {
        try {
            prefs.edit().putString(KEY_CONTACTS, gson.toJson(getAllContacts())).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save onion contacts: ${e.message}")
        }
    }

    private fun updateFlow() {
        _contactsFlow.value = getAllContacts()
    }

    private fun normalize(contact: OnionContact): OnionContact {
        val canonicalOnion = OnionContact.canonicalizeOnionEndpoint(contact.onionAddress)
        val unresolved = canonicalOnion.isBlank() ||
            canonicalOnion.equals(UNRESOLVED_ONION, ignoreCase = true)
        return contact.copy(
            onionAddress = if (unresolved) "" else canonicalOnion,
            publicKey = contact.publicKey.trim(),
            avatarId = OnionContact.canonicalAvatarId(contact.avatarId),
            nickname = contact.nickname.trim().ifBlank { "Unknown" },
            needsRendezvous = unresolved,
            trustState = contact.trustState ?: TorContactTrustState.UNVERIFIED
        )
    }

    private fun normalizeOnion(onionAddress: String): String {
        return OnionContact.canonicalizeOnionEndpoint(onionAddress)
            .lowercase()
            .takeIf { it != UNRESOLVED_ONION }
            .orEmpty()
    }

    private fun getMyPublicKeyHex(): String {
        val encryptionService = EncryptionService(context)
        val key = encryptionService.getStaticPublicKey()
            ?: ByteArray(0)
        return key.joinToString("") { "%02x".format(it) }
    }

    @Suppress("unused")
    private fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
