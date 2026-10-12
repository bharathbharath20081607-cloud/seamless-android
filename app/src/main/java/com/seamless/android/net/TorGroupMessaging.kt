package com.seamless.android.net

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.GroupMemberEntity
import com.seamless.android.data.GroupMessageEntity
import com.seamless.android.data.PrivateGroupEntity
import java.util.UUID

/**
 * Small private groups built from the same encrypted pairwise Tor envelopes as
 * one-to-one chats. Each member receives its own queued copy, so direct Tor and
 * mailbox recovery work without a central group server or a shared relay key.
 *
 * Authorization model: the creator is the only authority for roster changes
 * (invite, add, remove, rename). Any member may leave. Every inbound event is
 * checked against the authenticated pairwise sender, never against fields the
 * sender supplies about itself.
 */
class TorGroupMessaging private constructor(context: Context) {

    companion object {
        private const val PREFIX = "SGRP1:"
        private const val VERSION = 1
        private const val MAX_MEMBERS = 32
        private const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L
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
    }

    enum class InboundResult { NOT_GROUP, HANDLED, REJECTED }

    /**
     * A group uses pairwise encrypted Tor envelopes rather than a shared group
     * key. Each roster entry therefore carries the public contact material
     * required to encrypt to every member after a creator-authenticated invite.
     */
    private data class WireMember(
        val onionAddress: String,
        val nickname: String,
        val publicKey: String = "",
        val mailboxOnion: String? = null
    )

    private data class WireEvent(
        val version: Int = VERSION,
        val type: String,
        val groupId: String,
        val groupName: String = "",
        val creatorOnion: String = "",
        val creatorNickname: String = "",
        val messageText: String = "",
        val member: WireMember? = null,
        val members: List<WireMember> = emptyList()
    )

    private val gson = Gson()
    private val groupDao = AppDatabase.getDatabase(context).groupDao()
    private val contacts = OnionContactManager.getInstance(context)
    private val appContext = context.applicationContext
    private val notifier = TorMessageNotifier(appContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("tor_group_state", Context.MODE_PRIVATE)
    private val prefsLock = Any()

    /** Group currently on screen; incoming messages for it do not notify. */
    @Volatile var visibleGroupId: String? = null

    // ───────────────────────────── Read state ─────────────────────────────

    fun lastReadAt(groupId: String): Long = prefs.getLong("read:$groupId", 0L)

    fun markRead(groupId: String) {
        prefs.edit().putLong("read:$groupId", System.currentTimeMillis()).apply()
    }

    // ───────────────────────────── Commands ─────────────────────────────

    suspend fun createGroup(name: String, inviteeIds: Set<String>): Result<PrivateGroupEntity> = runCatching {
        val cleanName = name.trim()
        require(cleanName.length in 1..80) { "Group name must be between 1 and 80 characters" }
        val me = contacts.getMyContactInfo()
        require(!me.requiresRendezvous()) { "Wait for your Tor address before creating a group" }
        val invitees = inviteeIds
            .mapNotNull { contacts.getContact(it) }
            .filter { it.isEligibleForGroupMessaging() }
            .distinctBy { it.onionAddress }
            .take(MAX_MEMBERS - 1)

        val group = PrivateGroupEntity(
            groupId = UUID.randomUUID().toString(),
            name = cleanName,
            creatorOnionAddress = me.onionAddress,
            creatorNickname = me.nickname,
            isCreatedByMe = true
        )
        groupDao.insertGroup(group)
        val allMembers = listOf(me.toWireMember()) + invitees.map { it.toWireMember() }
        allMembers.forEach { member ->
            groupDao.insertMember(GroupMemberEntity(group.groupId, member.onionAddress, member.nickname))
        }
        markRead(group.groupId)

        val invite = encode(
            WireEvent(
                type = "invite",
                groupId = group.groupId,
                groupName = group.name,
                creatorOnion = me.onionAddress,
                creatorNickname = me.nickname,
                members = allMembers
            )
        )
        invitees.forEach { contact -> queue(contact.onionAddress, invite) }
        group
    }

    suspend fun addMembers(groupId: String, contactIds: Set<String>): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        require(group.isCreatedByMe && group.creatorOnionAddress == me.onionAddress) {
            "Only the group creator can add members"
        }
        val current = groupDao.getMembersForGroupNow(groupId)
        val currentAddresses = current.mapTo(mutableSetOf()) { it.memberOnionAddress }
        val currentWireMembers = current.mapNotNull { member ->
            contacts.getContactByOnion(member.memberOnionAddress)?.toWireMember()
                ?: me.takeIf { it.onionAddress == member.memberOnionAddress }?.toWireMember()
        }
        require(currentWireMembers.size == current.size) {
            "Every group member must have verified contact information before adding members"
        }
        val newContacts = contactIds.mapNotNull { contacts.getContact(it) }
            .filter { it.onionAddress !in currentAddresses && it.isEligibleForGroupMessaging() }
            .distinctBy { it.onionAddress }
            .take((MAX_MEMBERS - current.size).coerceAtLeast(0))
        require(newContacts.isNotEmpty()) { "No eligible contacts to add" }

        newContacts.forEach { contact ->
            val member = contact.toWireMember()
            groupDao.insertMember(GroupMemberEntity(groupId, member.onionAddress, member.nickname))
        }
        val allMembers = currentWireMembers + newContacts.map { it.toWireMember() }
        val invite = encode(
            WireEvent(
                type = "invite",
                groupId = groupId,
                groupName = group.name,
                creatorOnion = me.onionAddress,
                creatorNickname = me.nickname,
                members = allMembers
            )
        )
        newContacts.forEach { contact -> queue(contact.onionAddress, invite) }
        val existingRecipients = current.filter { it.memberOnionAddress != me.onionAddress }
        newContacts.forEach { contact ->
            val event = encode(
                WireEvent(
                    type = "member_added",
                    groupId = groupId,
                    creatorOnion = me.onionAddress,
                    creatorNickname = me.nickname,
                    member = contact.toWireMember()
                )
            )
            existingRecipients.forEach { queue(it.memberOnionAddress, event) }
        }
    }

    /** Creator-only: removes a member and tells everyone (including the removed member). */
    suspend fun removeMember(groupId: String, memberOnion: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        require(group.isCreatedByMe && group.creatorOnionAddress == me.onionAddress) {
            "Only the group creator can remove members"
        }
        val target = memberOnion.trim().lowercase()
        require(target != me.onionAddress.lowercase()) { "Use leave to exit your own group" }
        val members = groupDao.getMembersForGroupNow(groupId)
        val removed = requireNotNull(members.firstOrNull { it.memberOnionAddress.lowercase() == target }) {
            "Member not found"
        }
        groupDao.removeMember(groupId, removed.memberOnionAddress)
        val event = encode(
            WireEvent(
                type = "member_removed",
                groupId = groupId,
                creatorOnion = me.onionAddress,
                creatorNickname = me.nickname,
                member = WireMember(removed.memberOnionAddress, removed.memberNickname)
            )
        )
        // Remaining members first, then the removed member so they clean up locally.
        members.filter { it.memberOnionAddress != me.onionAddress }
            .forEach { queue(it.memberOnionAddress, event) }
    }

    /** Any member: notify the others, then delete the group locally. */
    suspend fun leaveGroup(groupId: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        val members = groupDao.getMembersForGroupNow(groupId)
        val event = encode(
            WireEvent(
                type = "leave",
                groupId = groupId,
                creatorOnion = group.creatorOnionAddress,
                member = WireMember(me.onionAddress, me.nickname)
            )
        )
        members.filter { it.memberOnionAddress != me.onionAddress }
            .forEach { queue(it.memberOnionAddress, event) }
        deleteLocal(groupId)
    }

    /** Creator-only rename, broadcast to members. */
    suspend fun renameGroup(groupId: String, newName: String): Result<Unit> = runCatching {
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        require(group.isCreatedByMe && group.creatorOnionAddress == me.onionAddress) {
            "Only the group creator can rename the group"
        }
        val clean = newName.trim()
        require(clean.length in 1..80) { "Group name must be between 1 and 80 characters" }
        groupDao.renameGroup(groupId, clean)
        val event = encode(WireEvent(type = "rename", groupId = groupId, groupName = clean, creatorOnion = me.onionAddress))
        groupDao.getMembersForGroupNow(groupId)
            .filter { it.memberOnionAddress != me.onionAddress }
            .forEach { queue(it.memberOnionAddress, event) }
    }

    /** Deletes the local copy only (history + roster). Does not notify anyone. */
    suspend fun deleteLocal(groupId: String) {
        groupDao.deleteMessagesForGroup(groupId)
        groupDao.deleteMembersForGroup(groupId)
        groupDao.deleteGroupById(groupId)
        clearTracking(groupId)
        prefs.edit().remove("read:$groupId").apply()
    }

    suspend fun sendGroupMessage(groupId: String, text: String): Result<String> = runCatching {
        val cleanText = text.trim()
        // Group events are JSON then Base64-encoded before the pairwise sealed
        // envelope is applied. This limit guarantees the final encrypted event
        // fits in the fixed-size Tor transport frame.
        require(cleanText.isNotEmpty() && cleanText.toByteArray(Charsets.UTF_8).size <= 2_400) {
            "Message is empty or too long for secure group delivery"
        }
        requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        require(groupDao.getMember(groupId, me.onionAddress) != null) { "You are not a member of this group" }
        val id = UUID.randomUUID().toString()
        val localMessage = GroupMessageEntity(
            id = id,
            groupId = groupId,
            senderOnionAddress = me.onionAddress,
            senderNickname = me.nickname,
            text = cleanText,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            deliveryState = STATE_QUEUED
        )
        groupDao.insertGroupMessage(localMessage)
        val payload = encode(WireEvent(type = "message", groupId = groupId, messageText = cleanText))
        val expectedRecipients = groupDao.getMembersForGroupNow(groupId)
            .filter { it.memberOnionAddress != me.onionAddress }
        val recipients = expectedRecipients.mapNotNull { contacts.getContactByOnion(it.memberOnionAddress) }
        if (recipients.size != expectedRecipients.size || recipients.any { !it.isEligibleForGroupMessaging() }) {
            groupDao.updateGroupMessageState(id, STATE_FAILED)
            error("Verify every group member before sending")
        }
        if (recipients.isEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_SENT)
            return@runCatching id
        }

        // One ACK-tracked pairwise copy per member; the group message is
        // DELIVERED only when every accepted copy has been acknowledged.
        val accepted = mutableListOf<String>()
        var failed = 0
        recipients.forEach { contact ->
            val perRecipientId = UUID.randomUUID()
            val ok = TorMessagingService.getInstance(appContext).sendMessageToOnion(
                recipientOnion = contact.onionAddress,
                text = payload,
                messageId = perRecipientId,
                completesUiMessage = false
            ).isSuccess
            if (ok) accepted += perRecipientId.toString() else failed++
        }
        if (accepted.isEmpty()) {
            groupDao.updateGroupMessageState(id, STATE_FAILED)
            error("Could not queue message for any member")
        }
        trackDelivery(id, accepted)
        if (failed > 0) {
            groupDao.updateGroupMessageState(id, STATE_PARTIAL)
            error("Message queued for ${accepted.size} of ${recipients.size} members")
        }
        id
    }

    // ───────────────────── Delivery tracking (ACK driven) ─────────────────────

    private fun trackDelivery(groupMessageId: String, perRecipientIds: List<String>) {
        synchronized(prefsLock) {
            val e = prefs.edit()
            perRecipientIds.forEach { e.putString("p:$it", groupMessageId) }
            e.putInt("n:$groupMessageId", perRecipientIds.size)
            e.putInt("a:$groupMessageId", 0)
            e.apply()
        }
    }

    private fun clearTracking(groupId: String) {
        // Tracking keys are tiny and keyed by message id; stale ones are inert
        // because the group message row they update no longer exists.
    }

    /**
     * Called by [TorMessagingService] for every transport ACK. Cheap no-op when the
     * acknowledged id does not belong to a group message.
     */
    suspend fun onTransportAck(ackedMessageId: String) {
        val groupMessageId: String
        val total: Int
        val acked: Int
        synchronized(prefsLock) {
            groupMessageId = prefs.getString("p:$ackedMessageId", null) ?: return
            total = prefs.getInt("n:$groupMessageId", 0)
            acked = prefs.getInt("a:$groupMessageId", 0) + 1
            prefs.edit().remove("p:$ackedMessageId").putInt("a:$groupMessageId", acked).apply()
            if (total in 1..acked) {
                prefs.edit().remove("n:$groupMessageId").remove("a:$groupMessageId").apply()
            }
        }
        val state = if (total in 1..acked) STATE_DELIVERED else STATE_SENT
        val existing = groupDao.getGroupMessage(groupMessageId) ?: return
        // Never downgrade (e.g. DELIVERED -> SENT) if ACKs arrive out of order.
        if (existing.deliveryState != STATE_DELIVERED) {
            groupDao.updateGroupMessageState(groupMessageId, state)
        }
    }

    /** "2/5 delivered" style progress for the UI, or null if untracked. */
    fun deliveryProgress(groupMessageId: String): Pair<Int, Int>? {
        val total = prefs.getInt("n:$groupMessageId", -1)
        if (total < 0) return null
        return prefs.getInt("a:$groupMessageId", 0) to total
    }

    // ───────────────────────────── Inbound ─────────────────────────────

    suspend fun handleIncoming(
        sender: OnionContactManager.OnionContact,
        messageId: String,
        timestamp: Long,
        payload: String
    ): InboundResult {
        if (!payload.startsWith(PREFIX)) return InboundResult.NOT_GROUP
        val event = decode(payload) ?: return InboundResult.REJECTED
        if (event.version != VERSION || !isUuid(event.groupId)) return InboundResult.REJECTED
        return when (event.type) {
            "invite" -> handleInvite(sender, event)
            "member_added" -> handleMemberAdded(sender, event)
            "member_removed" -> handleMemberRemoved(sender, event)
            "leave" -> handleLeave(sender, event)
            "rename" -> handleRename(sender, event)
            "message" -> handleGroupMessage(sender, event, messageId, timestamp)
            else -> InboundResult.REJECTED
        }
    }

    private suspend fun handleInvite(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (event.creatorOnion != sender.onionAddress || event.groupName.trim().length !in 1..80) {
            return InboundResult.REJECTED
        }
        val me = contacts.getMyContactInfo()
        val normalizedMembers = event.members.map { member ->
            WireMember(
                onionAddress = member.onionAddress.trim().lowercase(),
                nickname = member.nickname.trim(),
                publicKey = member.publicKey.trim().lowercase(),
                mailboxOnion = member.mailboxOnion
                    ?.let(OnionContactManager.OnionContact::canonicalizeOnionEndpoint)
                    ?.takeIf { it.isNotBlank() }
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
        if (!validMembers || normalizedMembers.none { it.onionAddress == me.onionAddress.lowercase() }) {
            return InboundResult.REJECTED
        }
        // The authenticated sender must be the creator and part of the
        // membership list before the invite becomes durable local state.
        val creatorMembership = normalizedMembers.firstOrNull {
            it.onionAddress == sender.onionAddress.lowercase()
        }
        if (creatorMembership == null || !creatorMembership.publicKey.equals(sender.publicKey, ignoreCase = true)) {
            return InboundResult.REJECTED
        }
        val existing = groupDao.getGroup(event.groupId)
        if (existing != null && existing.creatorOnionAddress != sender.onionAddress) return InboundResult.REJECTED
        groupDao.insertGroup(
            PrivateGroupEntity(
                groupId = event.groupId,
                name = event.groupName.trim(),
                creatorOnionAddress = sender.onionAddress,
                creatorNickname = sender.nickname,
                isCreatedByMe = false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        // An invite is the authoritative roster: drop anyone no longer in it.
        val keep = normalizedMembers.map { it.onionAddress }.toSet()
        groupDao.getMembersForGroupNow(event.groupId)
            .filter { it.memberOnionAddress.lowercase() !in keep }
            .forEach { groupDao.removeMember(event.groupId, it.memberOnionAddress) }
        normalizedMembers.forEach { member ->
            groupDao.insertMember(GroupMemberEntity(event.groupId, member.onionAddress, member.nickname))
            importGroupPeer(member, me.onionAddress)
        }
        if (existing == null) markRead(event.groupId)
        notifier.showGroupEvent(
            groupId = event.groupId,
            title = event.groupName.trim(),
            body = "${sender.nickname} added you to a private group"
        )
        return InboundResult.HANDLED
    }

    private suspend fun handleMemberAdded(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.REJECTED
        val member = event.member ?: return InboundResult.REJECTED
        if (group.creatorOnionAddress != sender.onionAddress ||
            member.onionAddress.isBlank() ||
            member.nickname.trim().length !in 1..80 ||
            !member.publicKey.trim().matches(HEX_PUBLIC_KEY)
        ) {
            return InboundResult.REJECTED
        }
        if (groupDao.getMemberCount(event.groupId) >= MAX_MEMBERS &&
            groupDao.getMember(event.groupId, member.onionAddress) == null
        ) return InboundResult.REJECTED
        val normalized = member.copy(
            onionAddress = member.onionAddress.trim().lowercase(),
            nickname = member.nickname.trim(),
            publicKey = member.publicKey.trim().lowercase(),
            mailboxOnion = member.mailboxOnion
                ?.let(OnionContactManager.OnionContact::canonicalizeOnionEndpoint)
                ?.takeIf { it.isNotBlank() }
        )
        if (!OnionContactManager.OnionContact.isValidOnionEndpoint(normalized.onionAddress)) {
            return InboundResult.REJECTED
        }
        groupDao.insertMember(GroupMemberEntity(event.groupId, normalized.onionAddress, normalized.nickname))
        importGroupPeer(normalized, contacts.getMyContactInfo().onionAddress)
        return InboundResult.HANDLED
    }

    private suspend fun handleMemberRemoved(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.HANDLED // already gone: idempotent
        if (group.creatorOnionAddress != sender.onionAddress) return InboundResult.REJECTED
        val target = event.member?.onionAddress?.trim()?.lowercase() ?: return InboundResult.REJECTED
        val me = contacts.getMyContactInfo()
        if (target == me.onionAddress.lowercase()) {
            // We were removed: wipe the group locally.
            notifier.showGroupEvent(event.groupId, group.name, "You were removed from this group")
            deleteLocal(event.groupId)
        } else {
            groupDao.getMembersForGroupNow(event.groupId)
                .firstOrNull { it.memberOnionAddress.lowercase() == target }
                ?.let { groupDao.removeMember(event.groupId, it.memberOnionAddress) }
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleLeave(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (groupDao.getGroup(event.groupId) == null) return InboundResult.HANDLED
        // Only the authenticated sender can remove itself.
        groupDao.getMember(event.groupId, sender.onionAddress)?.let {
            groupDao.removeMember(event.groupId, it.memberOnionAddress)
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleRename(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.REJECTED
        if (group.creatorOnionAddress != sender.onionAddress || event.groupName.trim().length !in 1..80) {
            return InboundResult.REJECTED
        }
        groupDao.renameGroup(event.groupId, event.groupName.trim())
        return InboundResult.HANDLED
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
        if (groupDao.getMember(event.groupId, sender.onionAddress) == null) return InboundResult.REJECTED
        // A wrong sender clock must not reorder or pin messages to the future.
        val now = System.currentTimeMillis()
        val safeTimestamp = minOf(timestamp, now).coerceAtLeast(0L)
        val inserted = groupDao.insertGroupMessageIfAbsent(
            GroupMessageEntity(
                id = messageId,
                groupId = event.groupId,
                senderOnionAddress = sender.onionAddress,
                senderNickname = sender.nickname,
                text = event.messageText,
                timestamp = safeTimestamp,
                isFromMe = false,
                deliveryState = STATE_DELIVERED
            )
        )
        if (inserted != -1L && visibleGroupId != event.groupId) {
            notifier.showGroupEvent(event.groupId, group.name, "${sender.nickname}: ${event.messageText.take(140)}")
        }
        return InboundResult.HANDLED
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private suspend fun queue(recipientOnion: String, payload: String) {
        TorMessagingService.getInstance(appContext).sendMessageToOnion(
            recipientOnion = recipientOnion,
            text = payload,
            completesUiMessage = false
        )
    }

    private fun encode(event: WireEvent): String = PREFIX + android.util.Base64.encodeToString(
        gson.toJson(event).toByteArray(Charsets.UTF_8),
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
    )

    private fun decode(payload: String): WireEvent? = runCatching {
        val raw = android.util.Base64.decode(payload.removePrefix(PREFIX), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        gson.fromJson(String(raw, Charsets.UTF_8), WireEvent::class.java)
    }.getOrNull()

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value); true }.getOrDefault(false)

    private fun OnionContactManager.OnionContact.toWireMember() = WireMember(
        onionAddress = onionAddress,
        nickname = nickname,
        publicKey = publicKey,
        mailboxOnion = mailboxOnion
    )

    private fun OnionContactManager.OnionContact.isEligibleForGroupMessaging(): Boolean =
        !requiresRendezvous() &&
            effectiveTrustState() != TorContactTrustState.BLOCKED &&
            effectiveTrustState() != TorContactTrustState.KEY_CHANGED

    private fun importGroupPeer(member: WireMember, myOnionAddress: String) {
        if (member.onionAddress == myOnionAddress.lowercase()) return
        contacts.addContact(
            OnionContactManager.OnionContact(
                id = UUID.randomUUID().toString(),
                nickname = member.nickname,
                onionAddress = member.onionAddress,
                publicKey = member.publicKey,
                mailboxOnion = member.mailboxOnion
            )
        )
    }
}
