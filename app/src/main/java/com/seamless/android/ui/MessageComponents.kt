package com.seamless.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
 

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import com.seamless.android.ui.theme.SeamlessAnimations
import android.content.Intent
import android.net.Uri
import com.seamless.android.model.BitchatMessage
import com.seamless.android.model.DeliveryStatus
import com.seamless.android.model.DeliveryTransport
import com.seamless.android.mesh.BluetoothMeshService
import java.text.SimpleDateFormat
import java.util.*
import com.seamless.android.ui.media.VoiceNotePlayer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Error
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.seamless.android.ui.media.FileMessageItem
import com.seamless.android.model.BitchatMessageType
import com.seamless.android.R
import androidx.compose.ui.res.stringResource
import com.seamless.android.ui.components.MessageBubble


// VoiceNotePlayer moved to com.seamless.android.ui.media.VoiceNotePlayer

/**
 * Message display components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

@Composable
fun MessagesList(
    messages: List<BitchatMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: ((Boolean) -> Unit)? = null,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((BitchatMessage) -> Unit)? = null,
    onCancelTransfer: ((BitchatMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null,
    onReactionSelected: ((BitchatMessage, String) -> Unit)? = null,
    messageReactions: Map<String, Map<String, Int>> = emptyMap(),
    isPrivateChat: Boolean = false,
    isChannel: Boolean = false,
    channelName: String? = null,
    connectedPeersCount: Int = 0
) {
    val listState = rememberLazyListState()
    
    // Track if this is the first time messages are being loaded
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }
    
    // Smart scroll: auto-scroll to bottom for initial load, then only when user is at or near the bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            
            // With reverseLayout=true and reversed data, index 0 is the latest message at the bottom
            val isFirstLoad = !hasScrolledToInitialPosition
            val isNearLatest = firstVisibleIndex <= 2
            
            if (isFirstLoad || isNearLatest) {
                listState.animateScrollToItem(0)
                if (isFirstLoad) {
                    hasScrolledToInitialPosition = true
                }
            }
        }
    }
    
    // Track whether user has scrolled away from the latest messages
    val isAtLatest by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            firstVisibleIndex <= 2
        }
    }
    LaunchedEffect(isAtLatest) {
        onScrolledUpChanged?.invoke(!isAtLatest)
    }
    
    // Force scroll to bottom when requested (e.g., when user sends a message)
    LaunchedEffect(forceScrollToBottom) {
        if (messages.isNotEmpty()) {
            // With reverseLayout=true and reversed data, latest is at index 0
            listState.animateScrollToItem(0)
        }
    }
    
    // Show empty state when no messages
    if (messages.isEmpty()) {
        MessageListEmptyState(
            isSearchingPeers = connectedPeersCount == 0,
            hasPeers = connectedPeersCount > 0,
            isPrivateChat = isPrivateChat,
            isChannel = isChannel,
            channelName = channelName,
            modifier = modifier
        )
    } else {
        val reversedMessages = messages.asReversed()

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier,
            reverseLayout = true
        ) {
            itemsIndexed(
                items = reversedMessages,
                key = { _, msg -> msg.id }
            ) { index, message ->
                // Calculate grouping based on reversed list (index 0 is latest/bottom)
                val newerMessage = if (index > 0) reversedMessages[index - 1] else null
                val olderMessage = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null
                
                // First in group = Top of bubble = Older message is different
                val isFirstInGroup = olderMessage == null || olderMessage.sender != message.sender
                
                // Last in group = Bottom of bubble = Newer message is different
                val isLastInGroup = newerMessage == null || newerMessage.sender != message.sender

                // Insert a date header when the day changes (or for the first message)
                // Note: olderMessage is the "previous" message in chronological order
                val showDateHeader = olderMessage == null || !isSameDay(message.timestamp, olderMessage.timestamp)

                if (showDateHeader) {
                    MessageDateHeader(
                        date = message.timestamp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                MessageItem(
                    message = message,
                    messages = messages,
                    currentUserNickname = currentUserNickname,
                    meshService = meshService,
                    isFirstInGroup = isFirstInGroup,
                    isLastInGroup = isLastInGroup,
                    onNicknameClick = onNicknameClick,
                    onMessageLongPress = onMessageLongPress,
                    onCancelTransfer = onCancelTransfer,
                    onImageClick = onImageClick,
                    onReactionSelected = onReactionSelected,
                    reactions = messageReactions[message.id] ?: emptyMap()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: BitchatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    messages: List<BitchatMessage> = emptyList(),
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((BitchatMessage) -> Unit)? = null,
    onCancelTransfer: ((BitchatMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null,
    onReactionSelected: ((BitchatMessage, String) -> Unit)? = null,
    reactions: Map<String, Int> = emptyMap()
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isOwnMessage = message.sender == currentUserNickname || 
                       message.senderPeerID == meshService.myPeerID

    // Emoji reaction picker state
    var showReactionPicker by remember { mutableStateOf(false) }

    // ── Premium Entrance Animation ─────────────────────────────────
    // Sent messages fly in from right, received from left, both with
    // scale bounce and fade — inspired by iMessage/WhatsApp.
    val alpha = remember { Animatable(0f) }
    val slideX = remember { Animatable(if (isOwnMessage) 80f else -80f) }
    val slideY = remember { Animatable(30f) }
    val entryScale = remember { Animatable(0.85f) }
    
    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            )
        }
        launch {
            slideX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.65f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            slideY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            entryScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Adaptive vertical spacing: tight for same-sender groups, relaxed between senders
    val topPad = if (isFirstInGroup) 5.dp else 1.dp
    val bottomPad = if (isLastInGroup) 5.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = topPad, bottom = bottomPad)
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationX = slideX.value
                this.translationY = slideY.value
                this.scaleX = entryScale.value
                this.scaleY = entryScale.value
            },
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        // Avatar for other users (left side) - only show if last in group
        if (!isOwnMessage) {
            if (isLastInGroup) {
                MessageAvatar(
                    sender = message.sender,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.Bottom) // Align to bottom of bubble group
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp)) // 32dp avatar + 8dp padding
            }
        }
        
        // Message bubble
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Sender name header - only show if first in group and not own message
            if (!isOwnMessage && isFirstInGroup) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = com.seamless.android.ui.theme.generateColorFromString(message.sender),
                    modifier = Modifier
                        .padding(start = 12.dp, bottom = 2.dp)
                        .clickable { onNicknameClick?.invoke(message.sender) }
                )
            }
            
            // Emoji Reaction Picker — appears above the bubble on long-press
            if (showReactionPicker) {
                com.seamless.android.ui.components.EmojiReactionPicker(
                    isVisible = true,
                    onEmojiSelected = { emoji ->
                        showReactionPicker = false
                        onReactionSelected?.invoke(message, emoji)
                    },
                    onDismiss = { showReactionPicker = false }
                )
            }

            MessageBubble(
                isSelf = isOwnMessage,
                isFirstInGroup = isFirstInGroup,
                isLastInGroup = isLastInGroup
            ) {
                Box {
                    // Create a custom layout that combines selectable text with clickable nickname areas
                    MessageTextWithClickableNicknames(
                        message = message,
                        messages = messages,
                        currentUserNickname = currentUserNickname,
                        meshService = meshService,
                        colorScheme = colorScheme,
                        timeFormatter = timeFormatter,
                        onNicknameClick = onNicknameClick,
                        onMessageLongPress = { msg ->
                            // Show emoji reaction picker on long-press (only picker, no bottom sheet)
                            showReactionPicker = true
                        },
                        onCancelTransfer = onCancelTransfer,
                        onImageClick = onImageClick,
                        modifier = Modifier
                    )
                    
                    // Status indicator for own messages
                    if (isOwnMessage) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Transport transparency indicator
                            TransportIndicator(message.deliveryTransport)
                            Text(
                                text = timeFormatter.format(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                letterSpacing = 0.3.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            message.deliveryStatus?.let { status ->
                                MessageStatusIcon(status)
                            }
                        }
                    }
                }
            }
            
            // Reaction badges below the bubble
            com.seamless.android.ui.components.ReactionBadges(
                reactions = reactions,
                isSelf = isOwnMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MessageAvatar(
    sender: String,
    modifier: Modifier = Modifier
) {
    val avatarColor = com.seamless.android.ui.theme.generateColorFromString(sender)
    
    // Clean avatar — single colored circle with white initial (Google Messages style)
    Box(
        modifier = modifier
            .size(32.dp)
            .background(
                color = avatarColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = sender.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MessageStatusIcon(status: DeliveryStatus) {
    val (icon, tint) = when (status) {
        is DeliveryStatus.Sending -> Icons.Outlined.Schedule to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        is DeliveryStatus.Sent -> Icons.Outlined.Done to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        is DeliveryStatus.Delivered -> Icons.Outlined.DoneAll to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        is DeliveryStatus.Read -> Icons.Outlined.DoneAll to com.seamless.android.ui.theme.SeamlessColors.Info
        is DeliveryStatus.Failed -> Icons.Outlined.Error to com.seamless.android.ui.theme.SeamlessColors.Error
        is DeliveryStatus.PartiallyDelivered -> Icons.Outlined.Done to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    
    Icon(
        imageVector = icon,
        contentDescription = status.getDisplayText(),
        modifier = Modifier.size(12.dp),
        tint = tint
    )
}

/**
 * Transport Transparency Indicator - shows which network layer delivered the message.
 * Critical for high-risk users to verify their privacy level per-message.
 */
@Composable
fun TransportIndicator(transport: DeliveryTransport) {
    if (transport == DeliveryTransport.UNKNOWN) return // Don't show for legacy messages
    
    val (emoji, description, color) = when (transport) {
        DeliveryTransport.MESH -> Triple("📡", "Sent via Bluetooth Mesh", Color(0xFF00C851))
        DeliveryTransport.TOR -> Triple("🧅", "Sent via Tor", Color(0xFF7C4DFF))
        DeliveryTransport.MAILBOX -> Triple("📬", "Queued in Mailbox", Color(0xFFFFA726))
        DeliveryTransport.NOSTR -> Triple("🌐", "Sent via Nostr Relay", Color(0xFF29B6F6))
        DeliveryTransport.UNKNOWN -> Triple("", "", Color.Transparent)
    }
    
    Text(
        text = emoji,
        fontSize = 9.sp,
        modifier = Modifier.padding(end = 3.dp),
        color = color
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
    private fun MessageTextWithClickableNicknames(
        message: BitchatMessage,
        messages: List<BitchatMessage>,
        currentUserNickname: String,
        meshService: BluetoothMeshService,
        colorScheme: ColorScheme,
        timeFormatter: SimpleDateFormat,
        onNicknameClick: ((String) -> Unit)?,
        onMessageLongPress: ((BitchatMessage) -> Unit)?,
        onCancelTransfer: ((BitchatMessage) -> Unit)?,
        onImageClick: ((String, List<String>, Int) -> Unit)?,
        modifier: Modifier = Modifier
    ) {
    // Image special rendering
    if (message.type == BitchatMessageType.Image) {
        com.seamless.android.ui.media.ImageMessageItem(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            onImageClick = onImageClick,
            modifier = modifier
        )
        return
    }

    // Voice note special rendering
    if (message.type == BitchatMessageType.Audio) {
        com.seamless.android.ui.media.AudioMessageItem(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            modifier = modifier
        )
        return
    }

    // File special rendering
    if (message.type == BitchatMessageType.File) {
        val path = message.content.trim()
        // Derive sending progress if applicable
        val (overrideProgress, _) = when (val st = message.deliveryStatus) {
            is com.seamless.android.model.DeliveryStatus.PartiallyDelivered -> {
                if (st.total > 0 && st.reached < st.total) {
                    (st.reached.toFloat() / st.total.toFloat()) to Color(0xFF1E88E5) // blue while sending
                } else null to null
            }
            else -> null to null
        }
        Column(modifier = modifier.fillMaxWidth()) {
            // Header: nickname + timestamp line above the file, identical styling to text messages
            val headerText = formatMessageHeaderAnnotatedString(
                message = message,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter
            )
            val haptic = LocalHapticFeedback.current
            var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = headerText,
                fontFamily = FontFamily.Default,
                color = colorScheme.onSurface,
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(onTap = { pos ->
                        val layout = headerLayout ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(pos)
                        val ann = headerText.getStringAnnotations("nickname_click", offset, offset)
                        if (ann.isNotEmpty() && onNicknameClick != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNicknameClick.invoke(ann.first().item)
                        }
                    }, onLongPress = { onMessageLongPress?.invoke(message) })
                },
                onTextLayout = { headerLayout = it }
            )

            // PRODUCTION FIX: Don't load file bytes into memory - causes OOM crash for 4GB+ files
            // Only need metadata (name, size, mimeType) for display - content is never used in UI
            val packet = try {
                val file = java.io.File(path)
                if (file.exists()) {
                    // Create metadata-only packet for display (no file content loaded)
                    com.seamless.android.model.BitchatFilePacket(
                        fileName = file.name,
                        fileSize = file.length(),
                        mimeType = com.seamless.android.features.file.FileUtils.getMimeTypeFromExtension(file.name),
                        content = ByteArray(0) // Empty - content not needed for display
                    )
                } else null
            } catch (e: Exception) {
                null
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (packet != null) {
                        if (overrideProgress != null) {
                            // Show sending animation while in-flight
                            com.seamless.android.ui.media.FileSendingAnimation(
                                fileName = packet.fileName,
                                progress = overrideProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Static file display with open/save dialog
                            FileMessageItem(
                                packet = packet,
                                onFileClick = {
                                    // handled inside FileMessageItem via dialog
                                }
                            )
                        }

                        // Cancel button overlay during sending
                        val showCancel = message.sender == currentUserNickname && (message.deliveryStatus is DeliveryStatus.PartiallyDelivered)
                        if (showCancel) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                                    .clickable { onCancelTransfer?.invoke(message) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Text(text = stringResource(R.string.file_unavailable), fontFamily = FontFamily.Default, color = Color.Gray)
                    }
                }
            }
        }
        return
    }

    // Check if this message should be animated during PoW mining
    val shouldAnimate = shouldAnimateMessage(message.id)
    
    // If animation is needed, use the matrix animation component for content only
    if (shouldAnimate) {
        // Display message with matrix animation for content
        MessageWithMatrixAnimation(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onImageClick = onImageClick,
            modifier = modifier
        )
    } else {
        // Normal message display
        val annotatedText = formatMessageAsAnnotatedString(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter
        )
        
        // Check if this message was sent by self to avoid click interactions on own nickname
        val isSelf = message.senderPeerID == meshService.myPeerID || 
                     message.sender == currentUserNickname ||
                     message.sender.startsWith("$currentUserNickname#")
        
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = annotatedText,
            modifier = modifier.pointerInput(message) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position)
                        // Nickname click only when not self
                        if (!isSelf && onNicknameClick != null) {
                            val nicknameAnnotations = annotatedText.getStringAnnotations(
                                tag = "nickname_click",
                                start = offset,
                                end = offset
                            )
                            if (nicknameAnnotations.isNotEmpty()) {
                                val nickname = nicknameAnnotations.first().item
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNicknameClick.invoke(nickname)
                                return@detectTapGestures
                            }
                        }
                        // Geohash teleport (all messages)
                        val geohashAnnotations = annotatedText.getStringAnnotations(
                            tag = "geohash_click",
                            start = offset,
                            end = offset
                        )
                        if (geohashAnnotations.isNotEmpty()) {
                            val geohash = geohashAnnotations.first().item
                            try {
                                val locationManager = com.seamless.android.geohash.LocationChannelManager.getInstance(
                                    context
                                )
                                val level = when (geohash.length) {
                                    in 0..2 -> com.seamless.android.geohash.GeohashChannelLevel.REGION
                                    in 3..4 -> com.seamless.android.geohash.GeohashChannelLevel.PROVINCE
                                    5 -> com.seamless.android.geohash.GeohashChannelLevel.CITY
                                    6 -> com.seamless.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                                    else -> com.seamless.android.geohash.GeohashChannelLevel.BLOCK
                                }
                                val channel = com.seamless.android.geohash.GeohashChannel(level, geohash.lowercase())
                                locationManager.setTeleported(true)
                                locationManager.select(com.seamless.android.geohash.ChannelID.Location(channel))
                            } catch (_: Exception) { }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                        // URL open (all messages)
                        val urlAnnotations = annotatedText.getStringAnnotations(
                            tag = "url_click",
                            start = offset,
                            end = offset
                        )
                        if (urlAnnotations.isNotEmpty()) {
                            val raw = urlAnnotations.first().item
                            val resolved = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) raw else "https://$raw"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolved))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMessageLongPress?.invoke(message)
                    }
                )
            },
            fontFamily = FontFamily.Default,
            softWrap = true,
            overflow = TextOverflow.Visible,
            style = androidx.compose.ui.text.TextStyle(
                color = colorScheme.onSurface
            ),
            onTextLayout = { result -> textLayoutResult = result }
        )
    }
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    val colorScheme = MaterialTheme.colorScheme
    
    when (status) {
        is DeliveryStatus.Sending -> {
            Text(
                text = stringResource(R.string.status_sending),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        is DeliveryStatus.Sent -> {
            // Use a subtle hollow marker for Sent; single check is reserved for Delivered (iOS parity)
            Text(
                text = stringResource(R.string.status_pending),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        is DeliveryStatus.Delivered -> {
            // Single check for Delivered (matches iOS expectations)
            Text(
                text = stringResource(R.string.status_sent),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        is DeliveryStatus.Read -> {
            Text(
                text = stringResource(R.string.status_delivered),
                fontSize = 10.sp,
                color = Color(0xFF007AFF), // Blue
                fontWeight = FontWeight.Bold
            )
        }
        is DeliveryStatus.Failed -> {
            Text(
                text = stringResource(R.string.status_failed),
                fontSize = 10.sp,
                color = Color.Red.copy(alpha = 0.8f)
            )
        }
        is DeliveryStatus.PartiallyDelivered -> {
            // Show a single subdued check without numeric label
            Text(
                text = stringResource(R.string.status_sent),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun MessageDateHeader(
    date: Date,
    modifier: Modifier = Modifier
) {
    val label = remember(date) { formatDateLabel(date) }
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = colorScheme.surfaceVariant.copy(alpha = 0.85f),
            tonalElevation = 2.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun isSameDay(d1: Date, d2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = d1 }
    val cal2 = Calendar.getInstance().apply { time = d2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateLabel(date: Date): String {
    val today = Date()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isToday = isSameDay(date, today)

    val targetCal = Calendar.getInstance().apply { time = date }
    val isYesterday = targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

    return when {
        isToday -> "Today"
        isYesterday -> "Yesterday"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    }
}
