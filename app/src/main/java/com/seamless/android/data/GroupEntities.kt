package com.seamless.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for Private Groups (Briar-style).
 * Only the creator can invite new members.
 */
@Entity(
    tableName = "private_groups",
    indices = [Index(value = ["createdAt"])]
)
data class PrivateGroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val creatorOnionAddress: String,
    val creatorNickname: String,
    val isCreatedByMe: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Group membership mapping.
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "memberOnionAddress"],
    indices = [Index(value = ["groupId"])]
)
data class GroupMemberEntity(
    val groupId: String,
    val memberOnionAddress: String,
    val memberNickname: String,
    val joinedAt: Long = System.currentTimeMillis()
)

/**
 * Messages within private groups.
 */
@Entity(
    tableName = "group_messages",
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["timestamp"])
    ]
)
data class GroupMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val senderOnionAddress: String,
    val senderNickname: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val deliveryState: String = "SENT"
)

/**
 * Database entity for Forums (Briar-style).
 * Any subscriber can invite their own contacts.
 */
@Entity(
    tableName = "forums",
    indices = [Index(value = ["createdAt"])]
)
data class ForumEntity(
    @PrimaryKey val forumId: String,
    val name: String,
    val creatorOnionAddress: String,
    val creatorNickname: String,
    val isCreatedByMe: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Forum messages with threading (parentMessageId for replies).
 */
@Entity(
    tableName = "forum_messages",
    indices = [
        Index(value = ["forumId"]),
        Index(value = ["parentMessageId"]),
        Index(value = ["timestamp"])
    ]
)
data class ForumMessageEntity(
    @PrimaryKey val id: String,
    val forumId: String,
    val parentMessageId: String? = null,   // null = top-level post
    val senderOnionAddress: String,
    val senderNickname: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean
)
