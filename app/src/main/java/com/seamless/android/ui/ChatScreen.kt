@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui
import androidx.compose.ui.unit.dp
// [Goose] Bridge file share events to ViewModel via dispatcher is installed in ChatScreen composition

// [Goose] Installing FileShareDispatcher handler in ChatScreen to forward file sends to ViewModel


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.seamless.android.model.BitchatMessage
import com.seamless.android.ui.media.FullScreenImageViewer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seamless.android.ui.events.UiEvent

/**
 * Main ChatScreen - REFACTORED to use component-based architecture
 * This is now a coordinator that orchestrates the following UI components:
 * - ChatHeader: App bar, navigation, peer counter
 * - MessageComponents: Message display and formatting
 * - InputComponents: Message input and command suggestions
 * - SidebarComponents: Navigation drawer with channels and people
 * - AboutSheet: App info and password prompts
 * - ChatUIUtils: Utility functions for formatting and colors
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Launcher for importing chat history
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importChatHistory(it) }
    }

    // Collect UI events for Snackbar feedback
    val uiEvents by viewModel.uiEvents.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(uiEvents) {
        uiEvents?.let { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = when (event.duration) {
                            com.seamless.android.ui.events.SnackbarDuration.SHORT -> SnackbarDuration.Short
                            com.seamless.android.ui.events.SnackbarDuration.LONG -> SnackbarDuration.Long
                            com.seamless.android.ui.events.SnackbarDuration.INDEFINITE -> SnackbarDuration.Indefinite
                        }
                    )
                }
                is UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.retry != null) "Retry" else null,
                        duration = SnackbarDuration.Long
                    )
                }
                UiEvent.MessageSent -> {
                    snackbarHostState.showSnackbar("Message sent", duration = SnackbarDuration.Short)
                }
                is UiEvent.ConnectionEstablished -> {
                    snackbarHostState.showSnackbar("Connected to ${event.peerName}", duration = SnackbarDuration.Short)
                }
                is UiEvent.ConnectionLost -> {
                    snackbarHostState.showSnackbar("${event.peerName} disconnected", duration = SnackbarDuration.Short)
                }
                is UiEvent.JoinedChannel -> {
                    snackbarHostState.showSnackbar("Joined ${event.channelName}", duration = SnackbarDuration.Short)
                }
                is UiEvent.LeftChannel -> {
                    snackbarHostState.showSnackbar("Left ${event.channelName}", duration = SnackbarDuration.Short)
                }
                is UiEvent.FileSent -> {
                    snackbarHostState.showSnackbar("${event.fileName} sent", duration = SnackbarDuration.Short)
                }
                is UiEvent.FileReceived -> {
                    snackbarHostState.showSnackbar("${event.fileName} received", duration = SnackbarDuration.Short)
                }
                UiEvent.CopiedToClipboard -> {
                    snackbarHostState.showSnackbar("Copied to clipboard", duration = SnackbarDuration.Short)
                }
                else -> {}
            }
        }
    }
    
    val messages by viewModel.messages.observeAsState(emptyList())
    val connectedPeers by viewModel.connectedPeers.observeAsState(emptyList())
    val nickname by viewModel.nickname.observeAsState("")
    val selectedPrivatePeer by viewModel.selectedPrivateChatPeer.observeAsState()
    val currentChannel by viewModel.currentChannel.observeAsState()
    val joinedChannels by viewModel.joinedChannels.observeAsState(emptySet())
    val hasUnreadChannels by viewModel.unreadChannelMessages.observeAsState(emptyMap())
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.observeAsState(emptySet())
    val privateChats by viewModel.privateChats.observeAsState(emptyMap())
    val channelMessages by viewModel.channelMessages.observeAsState(emptyMap())
    val showSidebar by viewModel.showSidebar.observeAsState(false)
    val showCommandSuggestions by viewModel.showCommandSuggestions.observeAsState(false)
    val commandSuggestions by viewModel.commandSuggestions.observeAsState(emptyList())
    val showMentionSuggestions by viewModel.showMentionSuggestions.observeAsState(false)
    val mentionSuggestions by viewModel.mentionSuggestions.observeAsState(emptyList())
    val showAppInfo by viewModel.showAppInfo.observeAsState(false)
    
    // Connection status tracking for status bar
    val typingPeers by com.seamless.android.ui.TypingStatusManager.shared.typingPeers.collectAsStateWithLifecycle()
    val isNostrConnected by com.seamless.android.nostr.NostrRelayManager.shared.isConnected.observeAsState(false)

    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showLocationChannelsSheet by remember { mutableStateOf(false) }
    var showLocationNotesSheet by remember { mutableStateOf(false) }
    var showChatsScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showSlideMenu by remember { mutableStateOf(false) }
    var showShareReceiveScreen by remember { mutableStateOf(false) }
    var showReceivedFilesScreen by remember { mutableStateOf(false) }
    var showFilePickerScreen by remember { mutableStateOf(false) }
    var showDeviceDiscoveryScreen by remember { mutableStateOf(false) }
    var showUserSheet by remember { mutableStateOf(false) }
    var selectedUserForSheet by remember { mutableStateOf("") }
    var selectedMessageForSheet by remember { mutableStateOf<BitchatMessage?>(null) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }
    var viewerImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var initialViewerIndex by remember { mutableStateOf(0) }
    var forceScrollToBottom by remember { mutableStateOf(false) }
    var isScrolledUp by remember { mutableStateOf(false) }
    var isHostingMode by remember { mutableStateOf(false) }
    var showPrivateMeshScreen by remember { mutableStateOf(false) }
    var showOnionContactsScreen by remember { mutableStateOf(false) }
    var selectedOnionContact by remember { mutableStateOf<com.seamless.android.net.OnionContactManager.OnionContact?>(null) }
    var selectedOnionGroup by remember { mutableStateOf<com.seamless.android.data.PrivateGroupEntity?>(null) }
    var showNetworkHub by remember { mutableStateOf(false) }
    var profileOpenedFromSettings by remember { mutableStateOf(false) }
    var privateChatOpenedFromChatsScreen by remember { mutableStateOf(false) }
    var onionContactsSelectedTab by remember { mutableStateOf(0) }
    val navContext = androidx.compose.ui.platform.LocalContext.current
    val pendingTorContactId by viewModel.pendingTorContactId.collectAsStateWithLifecycle()
    val pendingTorGroupId by viewModel.pendingTorGroupId.collectAsStateWithLifecycle()

    LaunchedEffect(pendingTorContactId, pendingTorGroupId) {
        val targetContactId = pendingTorContactId
        val targetGroupId = pendingTorGroupId
        if (targetContactId != null) {
            val contact = com.seamless.android.net.OnionContactManager.getInstance(navContext).getContact(targetContactId)
            if (contact != null) {
                selectedOnionGroup = null
                selectedOnionContact = contact
                showOnionContactsScreen = true
            }
            viewModel.consumePendingTorNavigation()
        } else if (targetGroupId != null) {
            val group = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.seamless.android.data.AppDatabase.getDatabase(navContext).groupDao().getGroup(targetGroupId)
            }
            if (group != null) {
                selectedOnionContact = null
                selectedOnionGroup = group
                showOnionContactsScreen = true
            }
            viewModel.consumePendingTorNavigation()
        }
    }
    
    // Emoji Rain Effect state
    val emojiRainState = com.seamless.android.ui.effects.rememberEmojiRainState()
    var lastMessageCount by remember { mutableStateOf(0) }

    // ── Emoji Reaction State ─────────────────────────────────────────
    // Reaction message prefix used to identify reaction messages in the stream
    val REACT_PREFIX = "__REACT__:"
    // Stores reactions per message: messageId -> (emoji -> count)
    val messageReactions = remember { mutableStateMapOf<String, MutableMap<String, Int>>() }
    val processedReactionMsgIds = remember { mutableSetOf<String>() }
    val reactionAnimationState = com.seamless.android.ui.effects.rememberReactionAnimationState()

    // Show password dialog when needed
    LaunchedEffect(showPasswordPrompt) {
        showPasswordDialog = showPasswordPrompt
    }

    val isConnected by viewModel.isConnected.observeAsState(false)
    val passwordPromptChannel by viewModel.passwordPromptChannel.observeAsState(null)

    // Get location channel info for timeline switching
    val selectedLocationChannel by viewModel.selectedLocationChannel.observeAsState()

    // Hierarchical system back navigation across all overlays, sheets, and sub-screens
    val canNavigateBack = showFullScreenImageViewer ||
        showPasswordDialog ||
        showUserSheet ||
        showNetworkHub ||
        showAppInfo ||
        showLocationNotesSheet ||
        showLocationChannelsSheet ||
        selectedOnionContact != null ||
        selectedOnionGroup != null ||
        showOnionContactsScreen ||
        showDeviceDiscoveryScreen ||
        showFilePickerScreen ||
        showShareReceiveScreen ||
        showReceivedFilesScreen ||
        showPrivateMeshScreen ||
        showProfileScreen ||
        showSettingsScreen ||
        showChatsScreen ||
        showSlideMenu ||
        showSidebar ||
        selectedPrivatePeer != null ||
        currentChannel != null ||
        selectedLocationChannel is com.seamless.android.geohash.ChannelID.Location

    androidx.activity.compose.BackHandler(enabled = canNavigateBack) {
        when {
            showFullScreenImageViewer -> showFullScreenImageViewer = false
            showPasswordDialog -> {
                showPasswordDialog = false
                passwordInput = ""
                viewModel.handleBackPressed()
            }
            showUserSheet -> {
                showUserSheet = false
                selectedMessageForSheet = null
            }
            showNetworkHub -> showNetworkHub = false
            showAppInfo -> viewModel.hideAppInfo()
            showLocationNotesSheet -> showLocationNotesSheet = false
            showLocationChannelsSheet -> showLocationChannelsSheet = false
            selectedOnionContact != null -> {
                selectedOnionContact = null
                showOnionContactsScreen = true
            }
            selectedOnionGroup != null -> {
                selectedOnionGroup = null
                showOnionContactsScreen = true
            }
            showOnionContactsScreen -> showOnionContactsScreen = false
            showDeviceDiscoveryScreen -> {
                showDeviceDiscoveryScreen = false
                if (isHostingMode) {
                    showShareReceiveScreen = true
                } else {
                    showFilePickerScreen = true
                }
            }
            showFilePickerScreen -> {
                showFilePickerScreen = false
                showShareReceiveScreen = true
            }
            showShareReceiveScreen -> showShareReceiveScreen = false
            showReceivedFilesScreen -> showReceivedFilesScreen = false
            showPrivateMeshScreen -> showPrivateMeshScreen = false
            showProfileScreen -> {
                showProfileScreen = false
                if (profileOpenedFromSettings) {
                    profileOpenedFromSettings = false
                    showSettingsScreen = true
                }
            }
            showSettingsScreen -> showSettingsScreen = false
            showChatsScreen -> showChatsScreen = false
            showSlideMenu -> showSlideMenu = false
            showSidebar -> viewModel.hideSidebar()
            selectedPrivatePeer != null -> {
                viewModel.endPrivateChat()
                if (privateChatOpenedFromChatsScreen) {
                    privateChatOpenedFromChatsScreen = false
                    showChatsScreen = true
                }
            }
            currentChannel != null -> viewModel.switchToChannel(null)
            selectedLocationChannel is com.seamless.android.geohash.ChannelID.Location -> {
                viewModel.selectLocationChannel(com.seamless.android.geohash.ChannelID.Mesh)
            }
        }
    }

    // Determine what messages to show based on current context (unified timelines)
    val rawMessages = when {
        selectedPrivatePeer != null -> privateChats[selectedPrivatePeer] ?: emptyList()
        currentChannel != null -> channelMessages[currentChannel] ?: emptyList()
        else -> {
            val locationChannel = selectedLocationChannel
            if (locationChannel is com.seamless.android.geohash.ChannelID.Location) {
                val geokey = "geo:${locationChannel.channel.geohash}"
                channelMessages[geokey] ?: emptyList()
            } else {
                messages // Mesh timeline
            }
        }
    }

    // Parse incoming reaction messages, update reactions map with new reference for Compose recomposition,
    // and trigger the contextual reaction animation on receiving devices!
    LaunchedEffect(rawMessages.size, selectedPrivatePeer, currentChannel, selectedLocationChannel) {
        val myPeerId = viewModel.meshServiceInstance?.myPeerID
        val now = System.currentTimeMillis()
        rawMessages.forEach { msg ->
            if (msg.content.startsWith(REACT_PREFIX) && processedReactionMsgIds.add(msg.id)) {
                val parts = msg.content.removePrefix(REACT_PREFIX).split(":")
                if (parts.size >= 2) {
                    val targetMsgId = parts[0]
                    val emoji = parts[1]
                    val isFromMe = (myPeerId != null && msg.senderPeerID.equals(myPeerId, ignoreCase = true)) ||
                        msg.sender.equals(nickname, ignoreCase = true)
                    // Only count remote reactions here because local reactions are already added in onReactionSelected
                    if (!isFromMe) {
                        val updated = LinkedHashMap<String, Int>()
                        messageReactions[targetMsgId]?.let { updated.putAll(it) }
                        updated[emoji] = (updated[emoji] ?: 0) + 1
                        messageReactions[targetMsgId] = updated
                        // Trigger the full-screen reaction animation for fresh incoming reactions
                        if (now - msg.timestamp.time < 20_000L) {
                            reactionAnimationState.trigger(emoji)
                        }
                    }
                }
            }
        }
    }

    // Filter out reaction messages from visible display
    val displayMessages = rawMessages.filter { !it.content.startsWith(REACT_PREFIX) }

    // Determine whether to show media buttons (only hide in geohash location chats)
    val showMediaButtons = when {
        selectedPrivatePeer != null -> true
        currentChannel != null -> true
        else -> selectedLocationChannel !is com.seamless.android.geohash.ChannelID.Location
    }

    // Use WindowInsets to handle keyboard properly
    // Detect new emoji-only messages and trigger emoji rain
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.size > lastMessageCount && lastMessageCount > 0) {
            val newestMessage = displayMessages.lastOrNull()
            if (newestMessage != null && com.seamless.android.ui.effects.isEmojiOnly(newestMessage.content)) {
                emojiRainState.triggerRain(newestMessage.content)
            }
        }
        lastMessageCount = displayMessages.size
    }
    
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            com.seamless.android.ui.theme.SeamlessColors.DarkGradientBackgroundStart,
                            com.seamless.android.ui.theme.SeamlessColors.DarkGradientBackgroundEnd
                        )
                    } else {
                        // Premium gradient background matching the global theme
                        listOf(
                            com.seamless.android.ui.theme.SeamlessColors.LightBackground,
                            com.seamless.android.ui.theme.SeamlessColors.LightBackground
                        )
                    }
                )
            )
    ) {
        // Emoji Rain Effect overlay — rendered on top of everything
        val activeEmoji = emojiRainState.activeEmoji.value
        if (activeEmoji != null) {
            com.seamless.android.ui.effects.EmojiRainEffect(
                emoji = activeEmoji,
                onComplete = { emojiRainState.clear() }
            )
        }
        // Contextual Reaction Animation overlay
        val activeReaction = reactionAnimationState.activeReaction.value
        if (activeReaction != null) {
            com.seamless.android.ui.effects.ReactionAnimationOverlay(
                effect = activeReaction,
                onComplete = { reactionAnimationState.clear() }
            )
        }
        val headerHeight = 42.dp
        
        // Main content area that responds to keyboard/window insets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime) // This handles keyboard insets
                .windowInsetsPadding(WindowInsets.navigationBars) // Add bottom padding when keyboard is not expanded
        ) {
            // Header spacer - creates exact space for the floating header (status bar + compact header)
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(headerHeight)
            )
            
            // Connection status bar - shows when not fully online
            val torStatus by com.seamless.android.net.TorManager.statusFlow.collectAsState()
            val torMode by com.seamless.android.net.TorPreferenceManager.modeFlow.collectAsState()
            
            val connectionStatus = when {
                torStatus.state == com.seamless.android.net.TorManager.TorState.RUNNING -> com.seamless.android.ui.components.ConnectionStatus.TOR_ACTIVE
                connectedPeers.isNotEmpty() && isNostrConnected -> com.seamless.android.ui.components.ConnectionStatus.ONLINE
                connectedPeers.isNotEmpty() -> com.seamless.android.ui.components.ConnectionStatus.MESH_ONLY
                else -> com.seamless.android.ui.components.ConnectionStatus.OFFLINE
            }
            val statusState = com.seamless.android.ui.components.ConnectionState(
                status = connectionStatus,
                meshPeerCount = connectedPeers.size,
                isNostrConnected = isNostrConnected,
                isTorEnabled = torMode == com.seamless.android.net.TorMode.ON,
                torBootstrapPercent = torStatus.bootstrapPercent
            )
            com.seamless.android.ui.components.ConnectionStatusBar(
                connectionState = statusState,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showNetworkHub = !showNetworkHub }
            )
            
            // Dropdown Network Hub anchored below status bar
            Box(modifier = Modifier.fillMaxWidth().zIndex(10f)) {
                com.seamless.android.ui.components.NetworkHubDropdown(
                    expanded = showNetworkHub,
                    onDismiss = { showNetworkHub = false },
                    meshPeerCount = connectedPeers.size,
                    isNostrConnected = isNostrConnected
                )
            }
            
            // Typing indicator for private chats
            if (selectedPrivatePeer != null) {
                val typingNickname = typingPeers[selectedPrivatePeer]
                if (typingNickname != null) {
                    com.seamless.android.ui.components.TypingIndicator(
                        isVisible = true
                    )
                }
            }

            // Messages area - takes up available space, will compress when keyboard appears
            val meshServiceLocal = viewModel.meshServiceInstance
            if (meshServiceLocal != null) {
                MessagesList(
                    messages = displayMessages,
                    currentUserNickname = nickname,
                    meshService = meshServiceLocal,
                    modifier = Modifier.weight(1f),
                    forceScrollToBottom = forceScrollToBottom,
                    onScrolledUpChanged = { isUp -> isScrolledUp = isUp },
                    isPrivateChat = selectedPrivatePeer != null,
                    isChannel = currentChannel != null,
                    channelName = currentChannel,
                    connectedPeersCount = connectedPeers.size,
                    onNicknameClick = { fullSenderName ->
                        // Single click - mention user in text input
                        val currentText = messageText.text
                    
                    // Extract base nickname and hash suffix from full sender name
                    val (baseName, hashSuffix) = splitSuffix(fullSenderName)
                    
                    // Check if we're in a geohash channel to include hash suffix
                    val selectedLocationChannel = viewModel.selectedLocationChannel.value
                    val mentionText = if (selectedLocationChannel is com.seamless.android.geohash.ChannelID.Location && hashSuffix.isNotEmpty()) {
                        // In geohash chat - include the hash suffix from the full display name
                        "@$baseName$hashSuffix"
                    } else {
                        // Regular chat - just the base nickname
                        "@$baseName"
                    }
                    
                    val newText = when {
                        currentText.isEmpty() -> "$mentionText "
                        currentText.endsWith(" ") -> "$currentText$mentionText "
                        else -> "$currentText $mentionText "
                    }
                    
                    messageText = TextFieldValue(
                        text = newText,
                        selection = TextRange(newText.length)
                    )
                },
                onMessageLongPress = { message ->
                    // Message long press - open user action sheet with message context
                    // Extract base nickname from message sender (contains all necessary info)
                    val (baseName, _) = splitSuffix(message.sender)
                    selectedUserForSheet = baseName
                    selectedMessageForSheet = message
                    showUserSheet = true
                },
                onCancelTransfer = { msg ->
                    viewModel.cancelMediaSend(msg.id)
                },
                onImageClick = { currentPath, allImagePaths, initialIndex ->
                    viewerImagePaths = allImagePaths
                    initialViewerIndex = initialIndex
                    showFullScreenImageViewer = true
                },
                onReactionSelected = { message, emoji ->
                    // Add reaction to local state with a fresh map instance so Compose recomposes immediately
                    val updated = LinkedHashMap<String, Int>()
                    messageReactions[message.id]?.let { updated.putAll(it) }
                    updated[emoji] = (updated[emoji] ?: 0) + 1
                    messageReactions[message.id] = updated
                    // Trigger contextual animation
                    reactionAnimationState.trigger(emoji)
                    // Send reaction over mesh so other users see it
                    viewModel.sendMessage("${REACT_PREFIX}${message.id}:${emoji}")
                },
                messageReactions = messageReactions
            )
            } else {
                // Show loading state while mesh service is being initialized
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            // Input area - stays at bottom
        // Bridge file share from lower-level input to ViewModel
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.seamless.android.ui.events.FileShareDispatcher.setHandler { peer, channel, path ->
            viewModel.sendFileNote(peer, channel, path)
        }
    }

    ChatInputSection(
        messageText = messageText,
        onMessageTextChange = { newText: TextFieldValue ->
            messageText = newText
            viewModel.updateCommandSuggestions(newText.text)
            viewModel.updateMentionSuggestions(newText.text)
            
            // Send typing indicator in private chats
            if (selectedPrivatePeer != null && newText.text.isNotEmpty()) {
                com.seamless.android.ui.TypingStatusManager.shared.onUserTyping(selectedPrivatePeer!!)
            }
        },
        onSend = {
            if (messageText.text.trim().isNotEmpty()) {
                viewModel.sendMessage(messageText.text.trim())
                messageText = TextFieldValue("")
                forceScrollToBottom = !forceScrollToBottom // Toggle to trigger scroll
            }
        },
        onSendVoiceNote = { peer, onionOrChannel, path ->
            viewModel.sendVoiceNote(peer, onionOrChannel, path)
        },
        onSendImageNote = { peer, onionOrChannel, path ->
            viewModel.sendImageNote(peer, onionOrChannel, path)
        },
        onSendFileNote = { peer, onionOrChannel, path ->
            viewModel.sendFileNote(peer, onionOrChannel, path)
        },
        showCommandSuggestions = showCommandSuggestions,
        commandSuggestions = commandSuggestions,
        showMentionSuggestions = showMentionSuggestions,
        mentionSuggestions = mentionSuggestions,
        onCommandSuggestionClick = { suggestion: CommandSuggestion ->
            val commandText = viewModel.selectCommandSuggestion(suggestion)
            messageText = TextFieldValue(
                text = commandText,
                selection = TextRange(commandText.length)
            )
        },
        onMentionSuggestionClick = { mention: String ->
            val mentionText = viewModel.selectMentionSuggestion(mention, messageText.text)
            messageText = TextFieldValue(
                text = mentionText,
                selection = TextRange(mentionText.length)
            )
        },
        selectedPrivatePeer = selectedPrivatePeer,
        currentChannel = currentChannel,
        nickname = nickname,
        colorScheme = colorScheme,
        showMediaButtons = showMediaButtons,
        // PRODUCTION FIX: Always show input - hiding it when peers disconnect causes
        // jarring UX. The messaging layer handles offline scenarios gracefully.
        // Previously this would hide on mesh timeline with no connected peers,
        // causing the input to disappear during temporary connection drops.
        isEnabled = true
    )
        }

        // Floating header - positioned absolutely at top, ignores keyboard
        ChatFloatingHeader(
            headerHeight = headerHeight,
            selectedPrivatePeer = selectedPrivatePeer,
            currentChannel = currentChannel,
            nickname = nickname,
            viewModel = viewModel,
            colorScheme = colorScheme,
            onSidebarToggle = { viewModel.showSidebar() },
            onShowAppInfo = { viewModel.showAppInfo() },
            onPanicClear = { viewModel.panicClearAllData() },
            onLocationChannelsClick = { showLocationChannelsSheet = true },
            onLocationNotesClick = { showLocationNotesSheet = true },
            onChatsClick = { showChatsScreen = true },
            onSettingsClick = { showSettingsScreen = true },
            onMenuClick = { showSlideMenu = true },
            onExitPrivateChat = {
                viewModel.endPrivateChat()
                if (privateChatOpenedFromChatsScreen) {
                    privateChatOpenedFromChatsScreen = false
                    showChatsScreen = true
                }
            }
        )

        // Divider under header - positioned after status bar + header height
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .offset(y = headerHeight)
                .zIndex(1f),
            color = colorScheme.outline.copy(alpha = 0.3f)
        )

        val alpha by animateFloatAsState(
            targetValue = if (showSidebar) 0.5f else 0f,
            animationSpec = tween(
                durationMillis = 300,
                easing = EaseOutCubic
            ), label = "overlayAlpha"
        )

        // Only render the background if it's visible
        if (alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
                    .clickable { viewModel.hideSidebar() }
                    .zIndex(1f)
            )
        }

        // Scroll-to-bottom floating button
        AnimatedVisibility(
            visible = isScrolledUp && !showSidebar,
            enter = slideInVertically(
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.SpatialOffsetSpring,
                initialOffsetY = { it / 2 }
            ) + scaleIn(
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.ExpressiveMorphSpring,
                initialScale = 0.8f
            ) + fadeIn(),
            exit = slideOutVertically(
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.SpatialOffsetSpring,
                targetOffsetY = { it / 2 }
            ) + scaleOut(targetScale = 0.8f) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 68.dp)
                .zIndex(1.5f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = colorScheme.primary.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
                    .clickable { forceScrollToBottom = !forceScrollToBottom },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(com.seamless.android.R.string.cd_scroll_to_bottom),
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showSidebar,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.SpatialOffsetSpring
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.SpatialOffsetSpring
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.zIndex(2f)
        ) {
            SidebarOverlay(
                viewModel = viewModel,
                onDismiss = { viewModel.hideSidebar() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Animated Slide-out menu backdrop scrim
        val slideMenuScrimAlpha by animateFloatAsState(
            targetValue = if (showSlideMenu) 0.58f else 0f,
            animationSpec = tween(durationMillis = 240, easing = EaseOutCubic),
            label = "slideMenuScrimAlpha"
        )
        if (slideMenuScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = slideMenuScrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSlideMenu = false }
                    .zIndex(3f)
            )
        }
        
        // Slide-out menu with spring physics
        AnimatedVisibility(
            visible = showSlideMenu,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.SpatialOffsetSpring
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = com.seamless.android.ui.theme.SeamlessAnimations.SpatialOffsetSpring
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.zIndex(4f)
        ) {
            com.seamless.android.ui.navigation.SlideOutMenu(
                isVisible = showSlideMenu,
                onDismiss = { showSlideMenu = false },
                onMenuItemClick = { menuItem ->
                    when (menuItem.action) {
                        com.seamless.android.ui.navigation.MenuAction.NavigateToSettings -> {
                            showSettingsScreen = true
                        }
                        com.seamless.android.ui.navigation.MenuAction.NavigateToProfile -> {
                            profileOpenedFromSettings = false
                            showProfileScreen = true
                        }
                        com.seamless.android.ui.navigation.MenuAction.NavigateToShareThings -> {
                            // First open the Share/Receive hub screen with two buttons
                            showShareReceiveScreen = true
                        }
                        com.seamless.android.ui.navigation.MenuAction.NavigateToReceivedFiles -> {
                            showReceivedFilesScreen = true
                        }
                        com.seamless.android.ui.navigation.MenuAction.NavigateToLocations -> {
                            showLocationChannelsSheet = true
                            showSlideMenu = false
                        }
                        com.seamless.android.ui.navigation.MenuAction.NavigateToMesh -> {
                            showPrivateMeshScreen = true
                        }
                        com.seamless.android.ui.navigation.MenuAction.NavigateToOnionP2P -> {
                            showOnionContactsScreen = true
                        }
                        is com.seamless.android.ui.navigation.MenuAction.NavigateToFutureFeature -> {
                            // Show coming soon message
                            // You can add a Toast or Snackbar here
                        }
                    }
                }
            )
        }
        
        // Snackbar Host for user feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(bottom = 80.dp) // Above the input area
        )
    }

    // Full-screen image viewer - separate from other sheets to allow image browsing without navigation
    // Full-screen image viewer - separate from other sheets to allow image browsing without navigation
    AnimatedVisibility(
        visible = showFullScreenImageViewer,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(300, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(300)),
        exit = scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(250, easing = EaseInCubic)
        ) + fadeOut(animationSpec = tween(250)),
        modifier = Modifier.zIndex(10f) // Ensure it's on top of everything
    ) {
        FullScreenImageViewer(
            imagePaths = viewerImagePaths,
            initialIndex = initialViewerIndex,
            onClose = { showFullScreenImageViewer = false }
        )
    }

    // Dialogs and Sheets
    ChatDialogs(
        showPasswordDialog = showPasswordDialog,
        passwordPromptChannel = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = { passwordInput = it },
        onPasswordConfirm = {
            if (passwordInput.isNotEmpty()) {
                val success = viewModel.joinChannel(passwordPromptChannel!!, passwordInput)
                if (success) {
                    showPasswordDialog = false
                    passwordInput = ""
                }
            }
        },
        onPasswordDismiss = {
            showPasswordDialog = false
            passwordInput = ""
        },
        showAppInfo = showAppInfo,
        onAppInfoDismiss = { viewModel.hideAppInfo() },
        showLocationChannelsSheet = showLocationChannelsSheet,
        onLocationChannelsSheetDismiss = { showLocationChannelsSheet = false },
        showLocationNotesSheet = showLocationNotesSheet,
        onLocationNotesSheetDismiss = { showLocationNotesSheet = false },
        showChatsScreen = showChatsScreen,
        onChatsScreenDismiss = { showChatsScreen = false },
        onChatSelected = { peerId -> 
            privateChatOpenedFromChatsScreen = true
            viewModel.startPrivateChat(peerId)
            showChatsScreen = false
        },
        showSettingsScreen = showSettingsScreen,
        onSettingsScreenDismiss = { showSettingsScreen = false },
        onNavigateToProfile = { 
            profileOpenedFromSettings = true
            showSettingsScreen = false
            showProfileScreen = true 
        },
        showShareReceiveScreen = showShareReceiveScreen,
        onShareReceiveScreenDismiss = { showShareReceiveScreen = false },
        onNavigateToFilePicker = { 
            isHostingMode = false
            showShareReceiveScreen = false
            showFilePickerScreen = true 
        },
        onNavigateToDeviceDiscovery = { 
            isHostingMode = true
            showShareReceiveScreen = false
            showDeviceDiscoveryScreen = true 
        },
        showFilePickerScreen = showFilePickerScreen,
        onFilePickerScreenDismiss = {
            showFilePickerScreen = false
            showShareReceiveScreen = true
        },
        onFileSelected = { filePath ->
            // After file is selected and prepared by FileShareViewModel,
            // navigate to DeviceDiscoveryScreen to select a recipient device
            isHostingMode = false
            showFilePickerScreen = false
            showDeviceDiscoveryScreen = true
        },
        showDeviceDiscoveryScreen = showDeviceDiscoveryScreen,
        onDeviceDiscoveryScreenDismiss = {
            showDeviceDiscoveryScreen = false
            if (isHostingMode) {
                showShareReceiveScreen = true
            } else {
                showFilePickerScreen = true
            }
        },
        onDeviceSelected = { deviceId ->
            // Device connection is handled by FileShareViewModel
            // Keep screen open to show connection status and transfer progress
        },
        showReceivedFilesScreen = showReceivedFilesScreen,
        onReceivedFilesScreenDismiss = { showReceivedFilesScreen = false },
        showUserSheet = showUserSheet,
        onUserSheetDismiss = { 
            showUserSheet = false
            selectedMessageForSheet = null // Reset message when dismissing
        },
        selectedUserForSheet = selectedUserForSheet,
        selectedMessageForSheet = selectedMessageForSheet,
        isHostingMode = isHostingMode,
        onIsHostingModeChange = { isHostingMode = it },
        viewModel = viewModel
    )

    // Profile Screen
    androidx.compose.animation.AnimatedVisibility(
        visible = showProfileScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownExit
    ) {
        com.seamless.android.profile.ui.ProfileScreen(
            onBackClick = {
                showProfileScreen = false
                if (profileOpenedFromSettings) {
                    profileOpenedFromSettings = false
                    showSettingsScreen = true
                }
            }
        )
    }
    
    // Private Mesh Screen
    androidx.compose.animation.AnimatedVisibility(
        visible = showPrivateMeshScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.HubEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.HubExit
    ) {
        com.seamless.android.ui.mesh.PrivateMeshScreen(
            meshService = viewModel.meshServiceInstance,
            onBackClick = { showPrivateMeshScreen = false }
        )
    }
    
    // Onion P2P Contacts Screen - Zero-server messaging via Tor
    androidx.compose.animation.AnimatedVisibility(
        visible = showOnionContactsScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.HubEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.HubExit
    ) {
        com.seamless.android.ui.onion.OnionContactsScreen(
            onNavigateBack = { showOnionContactsScreen = false },
            onContactClick = { contact ->
                // Open P2P chat with this contact
                onionContactsSelectedTab = 0
                selectedOnionContact = contact
                showOnionContactsScreen = false
            },
            onShareMyContact = {
                // Share contact info is handled within the screen
            },
            onGroupClick = { group ->
                onionContactsSelectedTab = 1
                selectedOnionGroup = group
                showOnionContactsScreen = false
            },
            initialTab = onionContactsSelectedTab,
            onTabChanged = { onionContactsSelectedTab = it }
        )
    }
    
    // Cache last active contact/group so exit animations remain non-null
    var cachedOnionContact by remember {
        mutableStateOf<com.seamless.android.net.OnionContactManager.OnionContact?>(null)
    }
    if (selectedOnionContact != null) {
        cachedOnionContact = selectedOnionContact
    }
    var cachedOnionGroup by remember {
        mutableStateOf<com.seamless.android.data.PrivateGroupEntity?>(null)
    }
    if (selectedOnionGroup != null) {
        cachedOnionGroup = selectedOnionGroup
    }

    // Onion P2P Chat Screen - Direct messaging via Tor
    androidx.compose.animation.AnimatedVisibility(
        visible = selectedOnionContact != null,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownExit
    ) {
        cachedOnionContact?.let { contact ->
            com.seamless.android.ui.onion.OnionChatScreen(
                contact = contact,
                onNavigateBack = {
                    selectedOnionContact = null
                    showOnionContactsScreen = true
                }
            )
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = selectedOnionGroup != null,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownExit
    ) {
        cachedOnionGroup?.let { group ->
            com.seamless.android.ui.onion.GroupChatScreen(
                group = group,
                onNavigateBack = {
                    selectedOnionGroup = null
                    showOnionContactsScreen = true
                }
            )
        }
    }
}

@Composable
private fun ChatInputSection(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    showCommandSuggestions: Boolean,
    commandSuggestions: List<CommandSuggestion>,
    showMentionSuggestions: Boolean,
    mentionSuggestions: List<String>,
    onCommandSuggestionClick: (CommandSuggestion) -> Unit,
    onMentionSuggestionClick: (String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    colorScheme: ColorScheme,
    showMediaButtons: Boolean,
    isEnabled: Boolean = true
) {
    // Hide input completely when on mesh timeline with no connected peers (radar showing)
    // This prevents crashes from sending messages when there are no recipients
    AnimatedVisibility(
        visible = isEnabled,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isDark) {
                 // Solid color matching the bottom of the gradient to prevent transparency issues with keyboard
                 com.seamless.android.ui.theme.SeamlessColors.DarkGradientBackgroundEnd
            } else {
                 colorScheme.background
            },
            tonalElevation = 2.dp,
            shadowElevation = 8.dp 
        ) {
            Column {
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
                // Command suggestions box
                if (showCommandSuggestions && commandSuggestions.isNotEmpty()) {
                    CommandSuggestionsBox(
                        suggestions = commandSuggestions,
                        onSuggestionClick = onCommandSuggestionClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
                }
                // Mention suggestions box
                if (showMentionSuggestions && mentionSuggestions.isNotEmpty()) {
                    MentionSuggestionsBox(
                        suggestions = mentionSuggestions,
                        onSuggestionClick = onMentionSuggestionClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
                }
                MessageInput(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    onSend = onSend,
                    onSendVoiceNote = onSendVoiceNote,
                    onSendImageNote = onSendImageNote,
                    onSendFileNote = onSendFileNote,
                    selectedPrivatePeer = selectedPrivatePeer,
                    currentChannel = currentChannel,
                    nickname = nickname,
                    showMediaButtons = showMediaButtons,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatFloatingHeader(
    headerHeight: Dp,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    colorScheme: ColorScheme,
    onSidebarToggle: () -> Unit,
    onShowAppInfo: () -> Unit,
    onPanicClear: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit,
    onChatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onExitPrivateChat: () -> Unit = { viewModel.endPrivateChat() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationManager = remember { com.seamless.android.geohash.LocationChannelManager.getInstance(context) }
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .windowInsetsPadding(WindowInsets.statusBars), // Extend into status bar area
        color = if (isDark) {
            com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceDark
        } else {
            com.seamless.android.ui.theme.SeamlessColors.GlassSurfaceLight
        }
    ) {
        TopAppBar(
            title = {
                ChatHeaderContent(
                    selectedPrivatePeer = selectedPrivatePeer,
                    currentChannel = currentChannel,
                    nickname = nickname,
                    viewModel = viewModel,
                    onBackClick = {
                        when {
                            selectedPrivatePeer != null -> onExitPrivateChat()
                            currentChannel != null -> viewModel.switchToChannel(null)
                        }
                    },
                    onSidebarClick = onSidebarToggle,
                    onTripleClick = onPanicClear,
                    onShowAppInfo = onShowAppInfo,
                    onLocationChannelsClick = onLocationChannelsClick,
                    onLocationNotesClick = {
                        // Ensure location is loaded before showing sheet
                        locationManager.refreshChannels()
                        onLocationNotesClick()
                    },
                    onChatsClick = onChatsClick,
                    onSettingsClick = onSettingsClick,
                    onMenuClick = onMenuClick
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.height(headerHeight) // Ensure compact header height
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDialogs(
    showPasswordDialog: Boolean,
    passwordPromptChannel: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirm: () -> Unit,
    onPasswordDismiss: () -> Unit,
    showAppInfo: Boolean,
    onAppInfoDismiss: () -> Unit,
    showLocationChannelsSheet: Boolean,
    onLocationChannelsSheetDismiss: () -> Unit,
    showLocationNotesSheet: Boolean,
    onLocationNotesSheetDismiss: () -> Unit,
    showChatsScreen: Boolean,
    onChatsScreenDismiss: () -> Unit,
    onChatSelected: (String) -> Unit,
    showSettingsScreen: Boolean,
    onSettingsScreenDismiss: () -> Unit,
    onNavigateToProfile: () -> Unit,
    showShareReceiveScreen: Boolean,
    onShareReceiveScreenDismiss: () -> Unit,
    onNavigateToFilePicker: () -> Unit,
    onNavigateToDeviceDiscovery: () -> Unit,
    showFilePickerScreen: Boolean,
    onFilePickerScreenDismiss: () -> Unit,
    onFileSelected: (String) -> Unit,
    showDeviceDiscoveryScreen: Boolean,
    onDeviceDiscoveryScreenDismiss: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    showReceivedFilesScreen: Boolean,
    onReceivedFilesScreenDismiss: () -> Unit,
    showUserSheet: Boolean,
    onUserSheetDismiss: () -> Unit,
    selectedUserForSheet: String,
    selectedMessageForSheet: BitchatMessage?,
    isHostingMode: Boolean,
    onIsHostingModeChange: (Boolean) -> Unit,
    viewModel: ChatViewModel
) {
    // Password dialog
    PasswordPromptDialog(
        show = showPasswordDialog,
        channelName = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = onPasswordChange,
        onConfirm = onPasswordConfirm,
        onDismiss = onPasswordDismiss
    )

    // About sheet
    var showDebugSheet by remember { mutableStateOf(false) }
    AboutSheet(
        isPresented = showAppInfo,
        onDismiss = onAppInfoDismiss,
        onShowDebug = { showDebugSheet = true }
    )
    if (showDebugSheet) {
        val debugMeshService = viewModel.meshServiceInstance
        if (debugMeshService != null) {
            com.seamless.android.ui.debug.DebugSettingsSheet(
                isPresented = showDebugSheet,
                onDismiss = { showDebugSheet = false },
                meshService = debugMeshService
            )
        }
    }
    
    // Location channels sheet
    if (showLocationChannelsSheet) {
        LocationChannelsSheet(
            isPresented = showLocationChannelsSheet,
            onDismiss = onLocationChannelsSheetDismiss,
            viewModel = viewModel
        )
    }
    
    // Location notes sheet (extracted to separate presenter)
    if (showLocationNotesSheet) {
        LocationNotesSheetPresenter(
            viewModel = viewModel,
            onDismiss = onLocationNotesSheetDismiss
        )
    }
    
    // Chats screen - shows favorited contacts
    androidx.compose.animation.AnimatedVisibility(
        visible = showChatsScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.HubEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.HubExit
    ) {
        ChatsScreen(
            viewModel = viewModel,
            onBackClick = onChatsScreenDismiss,
            onChatSelected = onChatSelected
        )
    }
    
    // Settings screen
    androidx.compose.animation.AnimatedVisibility(
        visible = showSettingsScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.HubEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.HubExit
    ) {
        SettingsScreen(
            onBackClick = onSettingsScreenDismiss,
            meshService = viewModel.meshServiceInstance,
            onProfileClick = onNavigateToProfile
        )
    }
    
    // Share Things screen
    androidx.compose.animation.AnimatedVisibility(
        visible = showShareReceiveScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.HubEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.HubExit
    ) {
        com.seamless.android.ui.fileshare.ShareReceiveScreen(
            onShareThingsClick = onNavigateToFilePicker,
            onReceiveThingsClick = {
                onIsHostingModeChange(true)
                onNavigateToDeviceDiscovery()
            },
            onBackClick = onShareReceiveScreenDismiss
        )
    }
    
    // File Picker screen
    androidx.compose.animation.AnimatedVisibility(
        visible = showFilePickerScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownExit
    ) {
        com.seamless.android.ui.fileshare.FilePickerScreen(
            onFileSelected = { filePath ->
                // Handle file selection and proceed to DeviceDiscoveryScreen
                onIsHostingModeChange(false)
                onFileSelected(filePath)
            },
            onBackClick = onFilePickerScreenDismiss
        )
    }
    
    // Device Discovery screen - handles its own progress screens internally
    androidx.compose.animation.AnimatedVisibility(
        visible = showDeviceDiscoveryScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.DrillDownExit
    ) {
        com.seamless.android.ui.fileshare.DeviceDiscoveryScreen(
            isHosting = isHostingMode,
            onDeviceSelected = { deviceId ->
                // Handle device selection - DeviceDiscoveryScreen handles transfer UI internally
                onDeviceSelected(deviceId)
            },
            onBackClick = onDeviceDiscoveryScreenDismiss
        )
    }
    
    // Received Files screen
    androidx.compose.animation.AnimatedVisibility(
        visible = showReceivedFilesScreen,
        enter = com.seamless.android.ui.theme.SeamlessTransitions.HubEnter,
        exit = com.seamless.android.ui.theme.SeamlessTransitions.HubExit
    ) {
        com.seamless.android.ui.fileshare.ReceivedFilesScreen(
            onBackClick = onReceivedFilesScreenDismiss
        )
    }
    if (showUserSheet) {
        ChatUserSheet(
            isPresented = showUserSheet,
            onDismiss = onUserSheetDismiss,
            targetNickname = selectedUserForSheet,
            selectedMessage = selectedMessageForSheet,
            viewModel = viewModel
        )
    }
}
