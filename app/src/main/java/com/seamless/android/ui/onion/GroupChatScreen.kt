@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.GroupMemberEntity
import com.seamless.android.data.GroupMessageEntity
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorGroupMessaging
import com.seamless.android.net.TorMessagingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QuickReactionEmojis = listOf("❤️", "👍", "🔥", "😂", "😮", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(group: PrivateGroupEntity, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val groupDao = remember { AppDatabase.getDatabase(context).groupDao() }
    val groupService = remember { TorGroupMessaging.getInstance(context) }
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val allGroups by remember { groupDao.getAllGroups() }.collectAsState(initial = listOf(group))
    val messages by remember(group.groupId) { groupDao.getGroupMessages(group.groupId) }
        .collectAsState(initial = emptyList())
    val members by remember(group.groupId) { groupDao.getMembersForGroup(group.groupId) }
        .collectAsState(initial = emptyList())
    val connectedPeers by messagingService.connectedPeersFlow.collectAsState()
    val deliveryProgressMap by groupService.deliveryProgressFlow.collectAsState()
    val reactionsMap by groupService.reactionsFlow.collectAsState()

    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<GroupMessageEntity?>(null) }
    var actionTargetMessage by remember { mutableStateOf<GroupMessageEntity?>(null) }
    var fullscreenPhotoPath by remember { mutableStateOf<String?>(null) }
    var isSendingMedia by remember { mutableStateOf(false) }
    var emojiEffectText by remember { mutableStateOf("") }
    var emojiEffectToken by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }

    val onlineMemberCount = remember(members, connectedPeers) {
        members.count { member ->
            connectedPeers.any { peer ->
                OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peer) ==
                    OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || isSendingMedia) return@rememberLauncherForActivityResult
        scope.launch {
            isSendingMedia = true
            try {
                val image = withContext(Dispatchers.IO) { prepareImageForTor(context, uri) }
                if (image == null) Toast.makeText(context, "Could not prepare this image", Toast.LENGTH_SHORT).show()
                else groupService.sendGroupImage(group.groupId, image, "image/jpeg")
                    .onFailure { Toast.makeText(context, it.message ?: "Unable to queue photo", Toast.LENGTH_SHORT).show() }
            } finally { isSendingMedia = false }
        }
    }

    // Live copy: reflects renames; a missing row means we left or were removed.
    val live = allGroups.firstOrNull { it.groupId == group.groupId }
    var everSeen by remember { mutableStateOf(false) }
    LaunchedEffect(live) {
        if (live != null) everSeen = true
        else if (everSeen) onNavigateBack()
    }
    val current = live ?: group

    // Warm up peer circuits when opening the group chat
    LaunchedEffect(group.groupId, members.size) {
        val contactManager = OnionContactManager.getInstance(context)
        val myOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(
            contactManager.getMyContactInfo().onionAddress
        )
        members.forEach { member ->
            val memberOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
            if (memberOnion != myOnion) {
                contactManager.getContactByOnion(member.memberOnionAddress)?.let { contact ->
                    launch(Dispatchers.IO) {
                        runCatching { messagingService.refreshPeerConnection(contact.id) }
                    }
                }
            }
        }
    }

    DisposableEffect(group.groupId) {
        groupService.visibleGroupId = group.groupId
        groupService.markRead(group.groupId)
        onDispose {
            groupService.markRead(group.groupId)
            if (groupService.visibleGroupId == group.groupId) groupService.visibleGroupId = null
        }
    }
    DisposableEffect(group.groupId, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> groupService.visibleGroupId = group.groupId
                Lifecycle.Event.ON_STOP -> if (groupService.visibleGroupId == group.groupId) {
                    groupService.visibleGroupId = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            groupService.markRead(group.groupId)
        }
    }

    val send: () -> Unit = {
        val outgoing = text.trim()
        if (outgoing.isNotEmpty()) {
            val replyId = replyingTo?.id
            text = ""
            replyingTo = null
            emojiEffectText = outgoing
            emojiEffectToken++
            scope.launch {
                groupService.sendGroupMessage(group.groupId, outgoing, replyToId = replyId)
                    .onFailure { Toast.makeText(context, it.message ?: "Unable to queue message", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    androidx.activity.compose.BackHandler {
        when {
            fullscreenPhotoPath != null -> fullscreenPhotoPath = null
            actionTargetMessage != null -> actionTargetMessage = null
            showInfo -> showInfo = false
            replyingTo != null -> replyingTo = null
            else -> onNavigateBack()
        }
    }

    OnionPage {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showInfo = true }
                        ) {
                            GroupAvatar(current.name, size = 40.dp, onlineCount = onlineMemberCount)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    current.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, null, tint = OnionUi.Online, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val onlineLabel = if (onlineMemberCount > 0) " · $onlineMemberCount online" else ""
                                    Text(
                                        "${members.size} member${if (members.size == 1) "" else "s"}$onlineLabel · E2EE over Tor",
                                        color = if (onlineMemberCount > 0) OnionUi.Online else OnionUi.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (current.isCreatedByMe) {
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Default.GroupAdd, "Add members", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Group info", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = OnionUi.CanvasRaised)
                )
            },
            bottomBar = {
                Composer(
                    text = text,
                    onTextChange = { if (it.length <= 2_200) text = it },
                    onSend = send,
                    onPhoto = { photoPicker.launch("image/*") },
                    mediaEnabled = !isSendingMedia,
                    isSendingMedia = isSendingMedia,
                    replyingTo = replyingTo,
                    onCancelReply = { replyingTo = null }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        GroupAvatar(current.name, size = 76.dp, onlineCount = onlineMemberCount)
                        Spacer(Modifier.height(16.dp))
                        Text("Say hello to ${current.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Every message & photo is encrypted separately for each member and delivered peer-to-peer over Tor — even when members are offline.",
                            color = OnionUi.TextSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        OnionStatusChip("Zero central servers · Pairwise Noise E2EE", OnionUi.Online)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(messages.size, key = { messages[it].id }) { index ->
                        val message = messages[index]
                        val previous = messages.getOrNull(index - 1)
                        val showDateHeader = previous == null || !isSameCalendarDay(previous.timestamp, message.timestamp)
                        if (showDateHeader) {
                            DateHeaderPill(formatDateHeader(message.timestamp))
                        }
                        if (message.senderOnionAddress == TorGroupMessaging.SYSTEM_SENDER) {
                            SystemEventBanner(message.text)
                        } else {
                            val showName = !message.isFromMe &&
                                (previous == null || previous.isFromMe ||
                                    previous.senderOnionAddress != message.senderOnionAddress || showDateHeader)
                            GroupMessageBubble(
                                message = message,
                                showName = showName,
                                deliveryProgress = deliveryProgressMap[message.id] ?: groupService.deliveryProgress(message.id),
                                reactions = reactionsMap[message.id].orEmpty(),
                                onLongPress = { actionTargetMessage = message },
                                onReplyClick = { replyingTo = message },
                                onPhotoClick = { path -> fullscreenPhotoPath = path },
                                onReactionClick = { emoji ->
                                    emojiEffectText = emoji
                                    emojiEffectToken++
                                    scope.launch { groupService.sendGroupReaction(group.groupId, message.id, emoji) }
                                },
                                onRetryClick = {
                                    scope.launch {
                                        groupService.retryGroupMessage(message.id)
                                            .onSuccess { Toast.makeText(context, "Retrying over Tor…", Toast.LENGTH_SHORT).show() }
                                            .onFailure { Toast.makeText(context, it.message ?: "Retry failed", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
            TorEmojiBurst(emojiEffectToken, emojiEffectText)
        }
    }

    actionTargetMessage?.let { target ->
        MessageActionSheet(
            message = target,
            onDismiss = { actionTargetMessage = null },
            onReact = { emoji ->
                actionTargetMessage = null
                emojiEffectText = emoji
                emojiEffectToken++
                scope.launch { groupService.sendGroupReaction(group.groupId, target.id, emoji) }
            },
            onReply = {
                actionTargetMessage = null
                replyingTo = target
            },
            onCopy = {
                actionTargetMessage = null
                val clean = TorGroupMessaging.parseMessageContent(target.text).body
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Group message", clean))
                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
            },
            onRetry = if (target.isFromMe && (target.deliveryState == TorGroupMessaging.STATE_FAILED || target.deliveryState == TorGroupMessaging.STATE_PARTIAL)) {
                {
                    actionTargetMessage = null
                    scope.launch {
                        groupService.retryGroupMessage(target.id)
                            .onSuccess { Toast.makeText(context, "Retrying over Tor…", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "Retry failed", Toast.LENGTH_SHORT).show() }
                    }
                }
            } else null
        )
    }

    fullscreenPhotoPath?.let { path ->
        FullscreenPhotoDialog(path = path, onDismiss = { fullscreenPhotoPath = null })
    }

    if (showInfo) {
        GroupInfoSheet(
            group = current,
            members = members,
            connectedPeers = connectedPeers,
            onDismiss = { showInfo = false },
            onLeft = { showInfo = false; onNavigateBack() }
        )
    }
}

@Composable
private fun DateHeaderPill(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = OnionUi.Surface.copy(alpha = 0.88f),
            modifier = Modifier.border(1.dp, OnionUi.Hairline, RoundedCornerShape(999.dp))
        ) {
            Text(
                text = label,
                color = OnionUi.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SystemEventBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = OnionUi.Surface.copy(alpha = 0.65f),
            modifier = Modifier.border(1.dp, OnionUi.Hairline, RoundedCornerShape(12.dp))
        ) {
            Text(
                text = text,
                color = OnionUi.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhoto: () -> Unit,
    mediaEnabled: Boolean,
    isSendingMedia: Boolean,
    replyingTo: GroupMessageEntity?,
    onCancelReply: () -> Unit
) {
    val canSend = text.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnionUi.CanvasRaised)
            .border(1.dp, OnionUi.Hairline)
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(visible = replyingTo != null) {
            replyingTo?.let { target ->
                val cleanBody = remember(target.text) { TorGroupMessaging.parseMessageContent(target.text).body }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OnionUi.Surface)
                        .border(1.dp, OnionUi.Hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(senderColor(target.senderOnionAddress))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${if (target.isFromMe) "yourself" else target.senderNickname}",
                            color = senderColor(target.senderOnionAddress),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = cleanBody,
                            color = OnionUi.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Cancel reply", tint = OnionUi.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(OnionUi.SurfacePressed),
                contentAlignment = Alignment.Center
            ) {
                if (isSendingMedia) {
                    CircularProgressIndicator(
                        color = OnionUi.Online,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    IconButton(onClick = onPhoto, enabled = mediaEnabled, modifier = Modifier.size(42.dp)) {
                        Icon(
                            Icons.Default.Photo,
                            "Send photo",
                            tint = if (mediaEnabled) OnionUi.Online else OnionUi.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, OnionUi.Hairline, RoundedCornerShape(24.dp)),
                placeholder = { Text("Encrypted group message…", color = OnionUi.TextMuted) },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = OnionUi.Surface,
                    unfocusedContainerColor = OnionUi.Surface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Color.White else Color.White.copy(alpha = 0.14f))
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "Send",
                    tint = if (canSend) Color.Black else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupMessageBubble(
    message: GroupMessageEntity,
    showName: Boolean,
    deliveryProgress: Pair<Int, Int>?,
    reactions: Map<String, String>,
    onLongPress: () -> Unit,
    onReplyClick: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onReactionClick: (String) -> Unit,
    onRetryClick: () -> Unit
) {
    val outgoing = message.isFromMe
    val parsed = remember(message.text) { TorGroupMessaging.parseMessageContent(message.text) }
    val shape = RoundedCornerShape(
        topStart = 22.dp, topEnd = 22.dp,
        bottomStart = if (outgoing) 22.dp else 5.dp,
        bottomEnd = if (outgoing) 5.dp else 22.dp
    )
    val bubbleBrush = if (outgoing) {
        Brush.linearGradient(listOf(OnionUi.OutgoingStart, OnionUi.OutgoingEnd))
    } else {
        Brush.linearGradient(listOf(OnionUi.IncomingStart, OnionUi.IncomingEnd))
    }
    var appeared by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { appeared = true }
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(180), label = "group_bubble_alpha")
    val translateY by animateFloatAsState(if (appeared) 0f else 16f, tween(220), label = "group_bubble_offset")
    val scale by animateFloatAsState(if (appeared) 1f else 0.96f, tween(220), label = "group_bubble_scale")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 320.dp)
                .graphicsLayer { this.alpha = alpha; this.translationY = translateY; scaleX = scale; scaleY = scale }
                .shadow(if (outgoing) 6.dp else 2.dp, shape, spotColor = Color.Black.copy(alpha = 0.32f))
                .clip(shape)
                .background(bubbleBrush, shape)
                .border(1.dp, if (outgoing) Color.White.copy(alpha = 0.17f) else OnionUi.Hairline, shape)
                .combinedClickable(
                    onClick = { onLongPress() },
                    onLongClick = { onLongPress() }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (showName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(senderColor(message.senderOnionAddress))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        message.senderNickname,
                        color = senderColor(message.senderOnionAddress),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(5.dp))
            }

            if (!parsed.replyToSender.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(senderColor(parsed.replyToSender))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = parsed.replyToSender,
                            color = senderColor(parsed.replyToSender),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!parsed.replyToPreview.isNullOrBlank()) {
                            Text(
                                text = parsed.replyToPreview,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (message.attachmentPath == null || parsed.body != "Photo") {
                Text(
                    parsed.body,
                    color = Color.White,
                    fontSize = if (isExpressiveEmoji(parsed.body)) 30.sp else 15.sp,
                    lineHeight = if (isExpressiveEmoji(parsed.body)) 36.sp else 21.sp
                )
            }

            message.attachmentPath?.let { path ->
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = path) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                    }
                }
                if (bitmap != null) {
                    if (parsed.body != "Photo") Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Group photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhotoClick(path) }
                    )
                }
            }

            Spacer(Modifier.height(if (isExpressiveEmoji(parsed.body)) 2.dp else 5.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatChatTime(message.timestamp),
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 10.sp
                )
                if (outgoing) {
                    Spacer(Modifier.width(5.dp))
                    DeliveryMark(
                        state = message.deliveryState,
                        progress = deliveryProgress,
                        onRetryClick = onRetryClick
                    )
                }
            }
        }

        if (reactions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val grouped = remember(reactions) {
                reactions.entries.groupBy({ it.value }, { it.key })
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (emoji, senders) ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = OnionUi.SurfacePressed,
                        modifier = Modifier
                            .border(1.dp, OnionUi.Hairline, RoundedCornerShape(999.dp))
                            .clickable { onReactionClick(emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 12.sp)
                            if (senders.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    senders.size.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryMark(
    state: String,
    progress: Pair<Int, Int>?,
    onRetryClick: () -> Unit
) {
    val (icon, tint) = when (state) {
        TorGroupMessaging.STATE_DELIVERED -> Icons.Default.DoneAll to OnionUi.Online
        TorGroupMessaging.STATE_SENT -> Icons.Default.Done to Color.White.copy(alpha = 0.75f)
        TorGroupMessaging.STATE_PARTIAL -> Icons.Default.WarningAmber to OnionUi.Warning
        TorGroupMessaging.STATE_FAILED -> Icons.Default.ErrorOutline to OnionUi.Danger
        else -> Icons.Default.Schedule to Color.White.copy(alpha = 0.55f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (progress != null && progress.second > 0 && state != TorGroupMessaging.STATE_DELIVERED) {
            Text(
                text = "${progress.first}/${progress.second}",
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(3.dp))
        }
        Icon(icon, contentDescription = state.lowercase(), tint = tint, modifier = Modifier.size(13.dp))
        if (state == TorGroupMessaging.STATE_FAILED || state == TorGroupMessaging.STATE_PARTIAL) {
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.18f))
                    .clickable(onClick = onRetryClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Refresh, null, tint = tint, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Retry", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: GroupMessageEntity,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onRetry: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.CanvasRaised,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OnionUi.Surface)
                    .border(1.dp, OnionUi.Hairline, RoundedCornerShape(18.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickReactionEmojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(OnionUi.SurfacePressed)
                            .clickable { onReact(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 21.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Reply", Icons.AutoMirrored.Filled.Reply, Modifier.weight(1f), onClick = onReply)
                ActionPill("Copy", Icons.Default.ContentCopy, Modifier.weight(1f), onClick = onCopy)
                if (onRetry != null) {
                    ActionPill("Retry", Icons.Default.Refresh, Modifier.weight(1f), onClick = onRetry)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun FullscreenPhotoDialog(path: String, onDismiss: () -> Unit) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Full photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

// ───────────────────────── Group info ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    group: PrivateGroupEntity,
    members: List<GroupMemberEntity>,
    connectedPeers: Set<String>,
    onDismiss: () -> Unit,
    onLeft: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { TorGroupMessaging.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val myOnion = remember {
        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(contactManager.getMyContactInfo().onionAddress)
    }

    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberEntity?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var renameText by remember(group.name) { mutableStateOf(group.name) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val onlineCount = remember(members, connectedPeers) {
        members.count { m ->
            connectedPeers.any { p ->
                OnionContactManager.OnionContact.canonicalizeOnionEndpoint(p) ==
                    OnionContactManager.OnionContact.canonicalizeOnionEndpoint(m.memberOnionAddress)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.CanvasRaised,
        contentColor = Color.White
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GroupAvatar(group.name, size = 76.dp, onlineCount = onlineCount)
                Spacer(Modifier.height(12.dp))
                Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${members.size} member${if (members.size == 1) "" else "s"} · created by ${if (group.isCreatedByMe) "you" else group.creatorNickname}",
                    color = OnionUi.TextSecondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                OnionStatusChip(
                    label = if (onlineCount > 0) "$onlineCount online on Tor · Pairwise E2EE" else "Pairwise E2EE over Tor",
                    color = OnionUi.Online
                )
            }
            Spacer(Modifier.height(18.dp))

            if (group.isCreatedByMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionPill("Add members", Icons.Default.GroupAdd, Modifier.weight(1f)) { showAdd = true }
                    ActionPill("Rename", Icons.Default.Edit, Modifier.weight(1f)) { showRename = true }
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                "MEMBERS (${members.size}/32)",
                color = OnionUi.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.height((members.size.coerceIn(1, 5) * 64).dp)) {
                val sortedMembers = members.sortedByDescending {
                    OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it.memberOnionAddress) ==
                        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(group.creatorOnionAddress)
                }
                items(sortedMembers.size, key = { sortedMembers[it].memberOnionAddress }) { idx ->
                    val member = sortedMembers[idx]
                    val canonicalMember = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
                    val isMe = canonicalMember == myOnion
                    val isCreator = canonicalMember ==
                        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(group.creatorOnionAddress)
                    val isOnline = isMe || connectedPeers.any {
                        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it) == canonicalMember
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GroupAvatar(member.memberNickname, size = 42.dp, onlineCount = if (isOnline) 1 else 0)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    member.memberNickname + if (isMe) " (you)" else "",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isOnline && !isMe) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Online",
                                        color = OnionUi.Online,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                member.memberOnionAddress.take(22) + "…",
                                color = OnionUi.TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Onion address", member.memberOnionAddress))
                                    toast("Copied .onion address")
                                }
                            )
                        }
                        if (isCreator) {
                            Text(
                                "Creator",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        } else if (group.isCreatedByMe && !isMe) {
                            IconButton(onClick = { memberToRemove = member }) {
                                Icon(Icons.Default.PersonRemove, "Remove ${member.memberNickname}", tint = OnionUi.Danger)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { showLeave = true },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnionUi.Danger.copy(alpha = 0.14f),
                    contentColor = OnionUi.Danger
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Leave group", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showAdd) {
        val memberAddresses = members.map {
            OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it.memberOnionAddress)
        }.toSet()
        val candidates = contactManager.getAllContacts().filter {
            OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it.onionAddress) !in memberAddresses &&
                !it.requiresRendezvous()
        }
        ModalBottomSheet(
            onDismissRequest = { showAdd = false; selected = emptySet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = OnionUi.CanvasRaised,
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Add members", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (candidates.isEmpty()) {
                    Text(
                        "Everyone in your Tor contacts is already in this group.",
                        color = OnionUi.TextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    ContactPickerList(
                        candidates = candidates,
                        selected = selected,
                        onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                        modifier = Modifier.height(300.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val invitees = selected
                            selected = emptySet(); showAdd = false
                            scope.launch {
                                service.addMembers(group.groupId, invitees)
                                    .onSuccess { toast("Invitations queued over Tor") }
                                    .onFailure { toast(it.message ?: "Unable to add members") }
                            }
                        },
                        enabled = selected.isNotEmpty(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.18f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Add ${selected.size.takeIf { it > 0 } ?: ""}".trim(), fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = OnionUi.Surface,
            title = { Text("Rename group", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { if (it.length <= 80) renameText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = onionFieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText
                    showRename = false
                    scope.launch {
                        service.renameGroup(group.groupId, name).onFailure { toast(it.message ?: "Unable to rename") }
                    }
                }) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel", color = OnionUi.TextSecondary) } }
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            containerColor = OnionUi.Surface,
            title = { Text("Remove ${member.memberNickname}?", color = Color.White) },
            text = { Text("They will lose access to new messages and be told they were removed.", color = OnionUi.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    memberToRemove = null
                    scope.launch {
                        service.removeMember(group.groupId, member.memberOnionAddress)
                            .onFailure { toast(it.message ?: "Unable to remove member") }
                    }
                }) { Text("Remove", color = OnionUi.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("Cancel", color = OnionUi.TextSecondary) } }
        )
    }

    if (showLeave) {
        AlertDialog(
            onDismissRequest = { showLeave = false },
            containerColor = OnionUi.Surface,
            title = { Text("Leave group?", color = Color.White) },
            text = {
                Text(
                    if (group.isCreatedByMe) "You created this group. Nobody will be able to add or remove members after you leave. Chat history on this device is deleted."
                    else "Other members are notified and this group's history on this device is deleted.",
                    color = OnionUi.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeave = false
                    scope.launch {
                        service.leaveGroup(group.groupId)
                            .onSuccess { onLeft() }
                            .onFailure { toast(it.message ?: "Unable to leave group") }
                    }
                }) { Text("Leave", color = OnionUi.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLeave = false }) { Text("Cancel", color = OnionUi.TextSecondary) } }
        )
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(OnionUi.Surface)
            .border(1.dp, OnionUi.Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
