package com.seamless.android.mesh

/**
 * Data models for Private Mesh feature
 */

/**
 * Represents a discovered private mesh that can be joined
 */
data class DiscoveredMesh(
    val meshId: String,
    val name: String,
    val hostPeerID: String,
    val hostNickname: String,
    val memberCount: Int,
    val rssi: Int = Int.MIN_VALUE,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Represents a member of a private mesh
 */
data class MeshMember(
    val peerID: String,
    val nickname: String,
    val isHost: Boolean,
    val joinedAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Represents a pending join request from another user
 */
data class JoinRequest(
    val requestId: String,
    val requesterPeerID: String,
    val requesterNickname: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * State of the private mesh
 */
enum class PrivateMeshState {
    IDLE,           // Not in any private mesh
    HOSTING,        // Hosting a private mesh
    JOINING,        // Requesting to join a mesh
    CONNECTED       // Connected to a private mesh as member
}

/**
 * Represents a message in the private mesh chat
 */
data class PrivateMeshMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderPeerID: String,
    val senderNickname: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val type: PrivateMeshMessageType = PrivateMeshMessageType.TEXT
)

/**
 * Type of private mesh message
 */
enum class PrivateMeshMessageType {
    TEXT,
    AUDIO,
    IMAGE
}

