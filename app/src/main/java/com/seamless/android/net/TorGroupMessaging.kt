package com.seamless.android.net

import android.content.Context
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
 */
class TorGroupMessaging private constructor(context: Context) {

    companion object {
        private const val PREFIX = "SGRP1:"
        private const val VERSION = 1
        private const val MAX_MEMBERS = 32

        @Volatile private var instance: TorGroupMessaging? = null

        fun getInstance(context: Context): TorGroupMessaging = instance ?: synchronized(this) {
            instance ?: TorGroupMessaging(context.applicationContext).also { instance = it }
        }
    }

    enum class InboundResult { NOT_GROUP, HANDLED, REJECTED }

    private data class WireMember(val onionAddress: String, val nickname: String)

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

    suspend fun createGroup(name: String, inviteeIds: Set<String>): Result<PrivateGroupEntity> = runCatching {
        val cleanName = name.trim()
        require(cleanName.length in 1..80) { "Group name must be between 1 and 80 characters" }
        val me = contacts.getMyContactInfo()
        require(!me.requiresRendezvous()) { "Wait for your Tor address before creating a group" }
        val invitees = inviteeIds
            .mapNotNull { contacts.getContact(it) }
            .filter { !it.requiresRendezvous() }
            .filter { it.effectiveTrustState() != TorContactTrustState.BLOCKED && it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED }
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
        val allMembers = listOf(WireMember(me.onionAddress, me.nickname)) + invitees.map {
            WireMember(it.onionAddress, it.nickname)
        }
        allMembers.forEach { member ->
            groupDao.insertMember(GroupMemberEntity(group.groupId, member.onionAddress, member.nickname))
        }

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
        invitees.forEach { contact ->
            TorMessagingService.getInstance(appContext).sendMessageToOnion(
                recipientOnion = contact.onionAddress,
                text = invite,
                completesUiMessage = false
            )
        }
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
        val newContacts = contactIds.mapNotNull { contacts.getContact(it) }
            .filter { it.onionAddress !in currentAddresses && !it.requiresRendezvous() }
            .filter { it.effectiveTrustState() != TorContactTrustState.BLOCKED && it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED }
            .take((MAX_MEMBERS - current.size).coerceAtLeast(0))

        newContacts.forEach { contact ->
            val member = WireMember(contact.onionAddress, contact.nickname)
            groupDao.insertMember(GroupMemberEntity(groupId, member.onionAddress, member.nickname))
            currentAddresses += member.onionAddress
        }
        val allMembers = groupDao.getMembersForGroupNow(groupId).map { WireMember(it.memberOnionAddress, it.memberNickname) }
        newContacts.forEach { contact ->
            TorMessagingService.getInstance(appContext).sendMessageToOnion(
                recipientOnion = contact.onionAddress,
                text = encode(WireEvent(
                    type = "invite",
                    groupId = groupId,
                    groupName = group.name,
                    creatorOnion = me.onionAddress,
                    creatorNickname = me.nickname,
                    members = allMembers
                )),
                completesUiMessage = false
            )
        }
        val existingRecipients = current.filter { it.memberOnionAddress != me.onionAddress }
        newContacts.forEach { contact ->
            val event = encode(WireEvent(
                type = "member_added",
                groupId = groupId,
                creatorOnion = me.onionAddress,
                creatorNickname = me.nickname,
                member = WireMember(contact.onionAddress, contact.nickname)
            ))
            existingRecipients.forEach { recipient ->
                TorMessagingService.getInstance(appContext).sendMessageToOnion(
                    recipientOnion = recipient.memberOnionAddress,
                    text = event,
                    completesUiMessage = false
                )
            }
        }
    }

    suspend fun sendGroupMessage(groupId: String, text: String): Result<String> = runCatching {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty() && cleanText.length <= 8_000) { "Message is empty or too long" }
        val group = requireNotNull(groupDao.getGroup(groupId)) { "Group not found" }
        val me = contacts.getMyContactInfo()
        require(groupDao.getMember(groupId, me.onionAddress) != null) { "You are not a member of this group" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        groupDao.insertGroupMessage(GroupMessageEntity(
            id = id,
            groupId = groupId,
            senderOnionAddress = me.onionAddress,
            senderNickname = me.nickname,
            text = cleanText,
            timestamp = timestamp,
            isFromMe = true,
            deliveryState = "QUEUED"
        ))
        val payload = encode(WireEvent(type = "message", groupId = groupId, messageText = cleanText))
        groupDao.getMembersForGroupNow(groupId)
            .filter { it.memberOnionAddress != me.onionAddress }
            .mapNotNull { member -> contacts.getContactByOnion(member.memberOnionAddress) }
            .forEach { contact ->
                TorMessagingService.getInstance(appContext).sendMessageToOnion(
                    recipientOnion = contact.onionAddress,
                    text = payload,
                    completesUiMessage = false
                )
            }
        id
    }

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
            "message" -> handleGroupMessage(sender, event, messageId, timestamp)
            else -> InboundResult.REJECTED
        }
    }

    private suspend fun handleInvite(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        if (event.creatorOnion != sender.onionAddress || event.groupName.trim().length !in 1..80) {
            return InboundResult.REJECTED
        }
        if (event.members.isEmpty() || event.members.size > MAX_MEMBERS ||
            event.members.none { it.onionAddress == contacts.getMyContactInfo().onionAddress }
        ) return InboundResult.REJECTED
        val existing = groupDao.getGroup(event.groupId)
        if (existing != null && existing.creatorOnionAddress != sender.onionAddress) return InboundResult.REJECTED
        groupDao.insertGroup(PrivateGroupEntity(
            groupId = event.groupId,
            name = event.groupName.trim(),
            creatorOnionAddress = sender.onionAddress,
            creatorNickname = sender.nickname,
            isCreatedByMe = false,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        ))
        event.members.distinctBy { it.onionAddress }.forEach { member ->
            if (member.onionAddress.isNotBlank() && member.nickname.length <= 80) {
                groupDao.insertMember(GroupMemberEntity(event.groupId, member.onionAddress, member.nickname))
            }
        }
        return InboundResult.HANDLED
    }

    private suspend fun handleMemberAdded(sender: OnionContactManager.OnionContact, event: WireEvent): InboundResult {
        val group = groupDao.getGroup(event.groupId) ?: return InboundResult.REJECTED
        val member = event.member ?: return InboundResult.REJECTED
        if (group.creatorOnionAddress != sender.onionAddress || member.onionAddress.isBlank() || member.nickname.length > 80) {
            return InboundResult.REJECTED
        }
        if (groupDao.getMemberCount(event.groupId) >= MAX_MEMBERS &&
            groupDao.getMember(event.groupId, member.onionAddress) == null
        ) return InboundResult.REJECTED
        groupDao.insertMember(GroupMemberEntity(event.groupId, member.onionAddress, member.nickname))
        return InboundResult.HANDLED
    }

    private suspend fun handleGroupMessage(
        sender: OnionContactManager.OnionContact,
        event: WireEvent,
        messageId: String,
        timestamp: Long
    ): InboundResult {
        if (event.messageText.isBlank() || event.messageText.length > 8_000) return InboundResult.REJECTED
        if (groupDao.getGroup(event.groupId) == null || groupDao.getMember(event.groupId, sender.onionAddress) == null) {
            return InboundResult.REJECTED
        }
        groupDao.insertGroupMessage(GroupMessageEntity(
            id = messageId,
            groupId = event.groupId,
            senderOnionAddress = sender.onionAddress,
            senderNickname = sender.nickname,
            text = event.messageText,
            timestamp = timestamp,
            isFromMe = false,
            deliveryState = "DELIVERED"
        ))
        return InboundResult.HANDLED
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
}
