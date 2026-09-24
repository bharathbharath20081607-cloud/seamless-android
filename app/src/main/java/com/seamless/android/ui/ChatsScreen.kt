package com.seamless.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.R
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.BitchatMessageType
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessTeal
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern chat list screen showing all favorited contacts
 * Provides quick access to DM conversations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onChatSelected: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val favoritePeers by viewModel.favoritePeers.observeAsState(emptySet())
    val peerNicknames by viewModel.peerNicknames.observeAsState(emptyMap())
    val peerSessionStates by viewModel.peerSessionStates.observeAsState(emptyMap())
    val privateChats by viewModel.privateChats.observeAsState(emptyMap())
    val unreadPrivateMessages by viewModel.unreadPrivateMessages.observeAsState(emptySet())
    val connectedPeers by viewModel.connectedPeers.observeAsState(emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colorScheme.background)
        ) {
            if (favoritePeers.isEmpty()) {
                // Empty state
                EmptyChatListState(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Chat list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = favoritePeers.toList().sortedByDescending { peerId ->
                            // Sort by most recent message timestamp (newest first)
                            privateChats[peerId]?.lastOrNull()?.timestamp?.time ?: 0L
                        },
                        key = { it }
                    ) { peerId ->
                        // PRODUCTION FIX: Resolve nickname with robust fallback chain
                        val resolvedNickname = peerNicknames[peerId] 
                            ?: try {
                                // Use new helper that handles mesh peerIDs AND internet-only nostr:npub peerIDs
                                com.seamless.android.favorites.FavoritesPersistenceService.shared.findNicknameForPeerID(peerId)
                            } catch (_: Exception) { null }
                        
                        ChatListItem(
                            peerId = peerId,
                            nickname = resolvedNickname,
                            isOnline = connectedPeers.contains(peerId),
                            sessionState = peerSessionStates[peerId],
                            lastMessage = privateChats[peerId]?.lastOrNull(),
                            unreadCount = if (unreadPrivateMessages.contains(peerId)) {
                                // For now just show indicator, not actual count
                                1
                            } else 0,
                            onChatClick = { onChatSelected(peerId) },
                            myPeerID = viewModel.meshServiceInstance?.myPeerID ?: ""
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    peerId: String,
    nickname: String?,
    isOnline: Boolean,
    sessionState: String?,
    lastMessage: BitchatMessage?,
    unreadCount: Int,
    onChatClick: () -> Unit,
    myPeerID: String = ""
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box(
                modifier = Modifier.size(52.dp)
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            if (isOnline) SeamlessCyan.copy(alpha = 0.2f)
                            else colorScheme.surface
                        )
                        .border(
                            width = 2.dp,
                            color = if (isOnline) SeamlessCyan.copy(alpha = 0.5f)
                            else colorScheme.outline.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (nickname ?: peerId).take(2).uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) SeamlessCyan else colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Online indicator dot
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colorScheme.background)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Chat info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Name and time row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nickname ?: peerId.take(12),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                        color = colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Timestamp
                    lastMessage?.let { msg ->
                        Text(
                            text = formatMessageTime(msg.timestamp.time),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unreadCount > 0) SeamlessCyan
                            else colorScheme.onBackground.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Message preview and unread badge row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastMessage?.let { msg ->
                            when {
                                msg.type == BitchatMessageType.Audio -> "🎤 Voice message"
                                msg.type == BitchatMessageType.Image -> "📷 Photo"
                                msg.type == BitchatMessageType.File -> "📎 File"
                                msg.sender == myPeerID -> "You: ${msg.content}"
                                else -> msg.content
                            }
                        } ?: "Tap to start chatting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unreadCount > 0) colorScheme.onBackground
                        else colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Unread badge
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(SeamlessCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatListState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Forum,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Chats Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Start a chat by adding people to your favorites.\nTap the star icon next to someone's name in the network list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Format timestamp for chat list display
 */
private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
