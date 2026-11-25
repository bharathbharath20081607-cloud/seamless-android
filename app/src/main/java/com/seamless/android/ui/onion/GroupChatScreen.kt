@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
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
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorGroupMessaging
import com.seamless.android.net.TorMessagingService
import com.seamless.android.net.TorRichChatCodec
import com.seamless.android.service.TorForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QuickReactionEmojis = listOf("❤️", "👍", "🔥", "😂", "😮", "🙏")

private val DisappearingOptions = listOf(
    0L to "Off",
    5 * 60_000L to "5 minutes",
    60 * 60_000L to "1 hour",
    24 * 60 * 60_000L to "24 hours",
    7 * 24 * 60 * 60_000L to "7 days"
)

private fun formatTimerShort(ttlMs: Long): String = when (ttlMs) {
    0L -> "Off"
    5 * 60_000L -> "5m"
    60 * 60_000L -> "1h"
    24 * 60 * 60_000L -> "24h"
    7 * 24 * 60 * 60_000L -> "7d"
    else -> "${ttlMs / 60_000L}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(group: PrivateGroupEntity, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val groupDao = remember { AppDatabase.getDatabase(context).groupDao() }
    val groupService = remember { TorGroupMessaging.getInstance(context) }
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val allGroups by remember { groupDao.getAllGroups() }.collectAsState(initial = listOf(group))
    val messages by remember(group.groupId) { groupDao.getGroupMessages(group.groupId) }
        .collectAsState(initial = emptyList())
    val members by remember(group.groupId) { groupDao.getMembersForGroup(group.groupId) }
        .collectAsState(initial = emptyList())
    val connectedPeers by messagingService.connectedPeersFlow.collectAsState()
    val deliveryProgressMap by groupService.deliveryProgressFlow.collectAsState()
    val reactionsMap by groupService.reactionsFlow.collectAsState()
    val editedIds by groupService.editedMessageIdsFlow.collectAsState()
    val deletedIds by groupService.deletedMessageIdsFlow.collectAsState()
    val burnedIds by groupService.burnedMessageIdsFlow.collectAsState()
    val shredReceiptsMap by groupService.shredReceiptsFlow.collectAsState()
    val groupEpochs by groupService.groupEpochFlow.collectAsState()
    val groupAdminsMap by groupService.groupAdminsFlow.collectAsState()
    val disappearingTtls by groupService.disappearingTtlFlow.collectAsState()

    val listState = rememberLazyListState()
    var text by remember(group.groupId) { mutableStateOf("") }
    var replyingTo by remember(group.groupId) { mutableStateOf<GroupMessageEntity?>(null) }
    var editingMessage by remember(group.groupId) { mutableStateOf<GroupMessageEntity?>(null) }
    var editDraft by remember(group.groupId) { mutableStateOf("") }
    var deleteConfirmMessage by remember(group.groupId) { mutableStateOf<GroupMessageEntity?>(null) }
    var actionTargetMessage by remember(group.groupId) { mutableStateOf<GroupMessageEntity?>(null) }
    var fullscreenPhotoPath by remember(group.groupId) { mutableStateOf<String?>(null) }
    var isSendingMedia by remember(group.groupId) { mutableStateOf(false) }
    var emojiEffectText by remember(group.groupId) { mutableStateOf("") }
    var emojiEffectToken by remember(group.groupId) { mutableIntStateOf(0) }
    var showInfo by remember(group.groupId) { mutableStateOf(false) }
    var burnOnReadArmed by remember(group.groupId) { mutableStateOf(false) }
    var isRecordingVoice by remember(group.groupId) { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember(group.groupId) { mutableStateOf(false) }
    var searchQuery by remember(group.groupId) { mutableStateOf("") }
    var activeBurnTarget by remember(group.groupId) { mutableStateOf<GroupMessageEntity?>(null) }

    val myCanonicalOnion = remember {
        OnionContactManager.OnionContact.canonicalizeOnionEndpoint(contactManager.getMyContactInfo().onionAddress)
    }

    // Live copy: reflects renames; a missing row means we left or were removed.
    val live = allGroups.firstOrNull { it.groupId == group.groupId }
    var everSeen by remember(group.groupId) { mutableStateOf(false) }
    LaunchedEffect(live) {
        if (live != null) everSeen = true
        else if (everSeen) onNavigateBack()
    }
    val current = live ?: group

    val isLocalAdmin = remember(current, groupAdminsMap, myCanonicalOnion) {
        val creatorCanon = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(current.creatorOnionAddress)
        current.isCreatedByMe ||
            (myCanonicalOnion.isNotBlank() &&
                (creatorCanon == myCanonicalOnion || myCanonicalOnion in groupAdminsMap[current.groupId].orEmpty()))
    }
    val currentEpoch = groupEpochs[current.groupId] ?: groupService.currentEpoch(current.groupId)
    val currentTtlMs = disappearingTtls[current.groupId] ?: groupService.currentDisappearingTtlMs(current.groupId)

    val onlineMemberCount = remember(members, connectedPeers) {
        members.count { member ->
            connectedPeers.any { peer ->
                OnionContactManager.OnionContact.canonicalizeOnionEndpoint(peer) ==
                    OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
            }
        }
    }

    val voicePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) isRecordingVoice = true
        else Toast.makeText(context, "Microphone permission is required for voice notes", Toast.LENGTH_SHORT).show()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || isSendingMedia) return@rememberLauncherForActivityResult
        val useViewOnce = burnOnReadArmed
        burnOnReadArmed = false
        scope.launch {
            isSendingMedia = true
            try {
                val prepared = withContext(Dispatchers.IO) { prepareOptimizedImageForTor(context, uri) }
                if (prepared == null) Toast.makeText(context, "Could not prepare this image", Toast.LENGTH_SHORT).show()
                else groupService.sendGroupAttachment(
                    groupId = group.groupId,
                    bytes = prepared.bytes,
                    mimeType = prepared.mimeType,
                    viewOnce = useViewOnce,
                    previewBytes = if (useViewOnce) null else prepared.previewBytes
                ).onFailure { Toast.makeText(context, it.message ?: "Unable to queue photo", Toast.LENGTH_SHORT).show() }
            } finally { isSendingMedia = false }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || isSendingMedia) return@rememberLauncherForActivityResult
        val useViewOnce = burnOnReadArmed
        burnOnReadArmed = false
        scope.launch {
            isSendingMedia = true
            try {
                val preparedDoc = withContext(Dispatchers.IO) { prepareDocumentForTor(context, uri) }
                preparedDoc.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Unable to read file", Toast.LENGTH_SHORT).show()
                }.onSuccess { doc ->
                    groupService.sendGroupAttachment(
                        groupId = group.groupId,
                        bytes = doc.bytes,
                        mimeType = doc.mimeType,
                        fileName = doc.fileName,
                        viewOnce = useViewOnce
                    ).onFailure { Toast.makeText(context, it.message ?: "Unable to queue file", Toast.LENGTH_SHORT).show() }
                }
            } finally { isSendingMedia = false }
        }
    }

    // Ensure Tor services run on cold-start deep links and purge expired disappearing messages
    LaunchedEffect(group.groupId) {
        if (messagingService.serviceState.value == TorMessagingService.ServiceState.STOPPED) {
            TorForegroundService.start(context.applicationContext)
            messagingService.start()
        }
        groupService.purgeExpiredMessages(group.groupId)
    }

    // Warm up peer circuits when opening the group chat
    LaunchedEffect(group.groupId, members.size) {
        members.forEach { member ->
            val memberOnion = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
            if (memberOnion != myCanonicalOnion) {
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
                Lifecycle.Event.ON_RESUME -> {
                    groupService.visibleGroupId = group.groupId
                    groupService.markRead(group.groupId)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> if (groupService.visibleGroupId == group.groupId) {
                    groupService.visibleGroupId = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            groupService.visibleGroupId = group.groupId
            groupService.markRead(group.groupId)
        }
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
            val useBurn = burnOnReadArmed
            text = ""
            replyingTo = null
            burnOnReadArmed = false
            emojiEffectText = if (useBurn) "🔥" else outgoing
            emojiEffectToken++
            scope.launch {
                groupService.sendGroupMessage(
                    groupId = group.groupId,
                    text = outgoing,
                    replyToId = replyId,
                    burnOnRead = useBurn
                ).onFailure { Toast.makeText(context, it.message ?: "Unable to queue message", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    androidx.activity.compose.BackHandler {
        when {
            activeBurnTarget != null -> activeBurnTarget = null
            isRecordingVoice -> isRecordingVoice = false
            fullscreenPhotoPath != null -> fullscreenPhotoPath = null
            editingMessage != null -> editingMessage = null
            deleteConfirmMessage != null -> deleteConfirmMessage = null
            actionTargetMessage != null -> actionTargetMessage = null
            showSearchBar -> { showSearchBar = false; searchQuery = "" }
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
                                    color = OnionUi.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, null, tint = OnionUi.Online, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val onlineLabel = if (onlineMemberCount > 0) " · $onlineMemberCount online" else ""
                                    val timerLabel = if (currentTtlMs > 0L) " · ⏱ ${formatTimerShort(currentTtlMs)}" else ""
                                    Text(
                                        "${members.size} member${if (members.size == 1) "" else "s"}$onlineLabel$timerLabel · E2EE",
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnionUi.TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) searchQuery = ""
                        }) {
                            Icon(
                                Icons.Default.Search,
                                "Search messages",
                                tint = if (showSearchBar) OnionUi.Online else OnionUi.TextPrimary
                            )
                        }
                        if (isLocalAdmin) {
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Default.GroupAdd, "Add members", tint = OnionUi.TextPrimary)
                            }
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Group info", tint = OnionUi.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = OnionUi.CanvasRaised)
                )
            },
            bottomBar = {
                Composer(
                    text = text,
                    onTextChange = {
                        if (it.length <= 2_200) {
                            if (text.isBlank() && it.isNotBlank()) {
                                groupService.speculativeWarmGroupCircuits(group.groupId, includeBulk = false)
                            }
                            text = it
                        }
                    },
                    onSend = send,
                    onOpenSheet = {
                        groupService.speculativeWarmGroupCircuits(group.groupId, includeBulk = true)
                        showAttachmentSheet = true
                    },
                    onStartVoiceNote = {
                        groupService.speculativeWarmGroupCircuits(group.groupId, includeBulk = true)
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) isRecordingVoice = true
                        else voicePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    burnOnReadArmed = burnOnReadArmed,
                    onToggleBurnOnRead = { burnOnReadArmed = !burnOnReadArmed },
                    isRecordingVoice = isRecordingVoice,
                    onCancelVoiceNote = { isRecordingVoice = false },
                    onSendVoiceNote = { bytes, durationMs, fileName ->
                        isRecordingVoice = false
                        val useViewOnce = burnOnReadArmed
                        burnOnReadArmed = false
                        scope.launch {
                            isSendingMedia = true
                            try {
                                groupService.sendGroupAttachment(
                                    groupId = group.groupId,
                                    bytes = bytes,
                                    mimeType = "audio/mp4",
                                    fileName = fileName,
                                    durationMs = durationMs,
                                    viewOnce = useViewOnce
                                ).onFailure {
                                    Toast.makeText(context, it.message ?: "Unable to queue voice note", Toast.LENGTH_SHORT).show()
                                }
                            } finally { isSendingMedia = false }
                        }
                    },
                    mediaEnabled = !isSendingMedia,
                    isSendingMedia = isSendingMedia,
                    replyingTo = replyingTo,
                    onCancelReply = { replyingTo = null }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            val displayedMessages = remember(messages, searchQuery) {
                if (searchQuery.isBlank()) messages
                else messages.filter { msg ->
                    val clean = TorRichChatCodec.friendlyPreviewText(msg.text, msg.attachmentMimeType)
                    clean.contains(searchQuery, ignoreCase = true)
                }
            }
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = showSearchBar) {
                        InChatSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            matchCount = displayedMessages.size,
                            onClose = { showSearchBar = false; searchQuery = "" }
                        )
                    }
                    if (displayedMessages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                GroupAvatar(current.name, size = 76.dp, onlineCount = onlineMemberCount)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "No matching messages" else "Say hello to ${current.name}",
                                    color = OnionUi.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "Try another keyword in this Chrono-DAG group."
                                    else "Messages, voice notes, 1 MB files & View-Once capsules are encrypted for each member via Chrono-DAG over Tor.",
                                    color = OnionUi.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                                if (searchQuery.isBlank()) {
                                    Spacer(Modifier.height(14.dp))
                                    OnionStatusChip("Chrono-DAG · Epoch #$currentEpoch · Zero servers", OnionUi.Online)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(displayedMessages.size, key = { displayedMessages[it].id }) { index ->
                                val message = displayedMessages[index]
                                val previous = displayedMessages.getOrNull(index - 1)
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
                                    val cleanMsgBody = TorGroupMessaging.parseMessageContent(message.text).body
                                    GroupMessageBubble(
                                        message = message,
                                        showName = showName,
                                        isEdited = message.id in editedIds,
                                        isDeleted = message.id in deletedIds ||
                                            cleanMsgBody == TorGroupMessaging.DELETED_MESSAGE_TEXT,
                                        isBurned = message.id in burnedIds ||
                                            cleanMsgBody == TorRichChatCodec.BURNED_CAPSULE_TEXT,
                                        groupShredCount = shredReceiptsMap[message.id]?.size ?: 0,
                                        deliveryProgress = deliveryProgressMap[message.id] ?: groupService.deliveryProgress(message.id),
                                        reactions = reactionsMap[message.id].orEmpty(),
                                        onLongPress = { actionTargetMessage = message },
                                        onQuotedMessageClick = { targetId ->
                                            val idx = displayedMessages.indexOfFirst { it.id == targetId }
                                            if (idx >= 0) {
                                                scope.launch { listState.animateScrollToItem(idx + 1) }
                                            }
                                        },
                                        onPhotoClick = { path -> fullscreenPhotoPath = path },
                                        onOpenBurnCapsule = { activeBurnTarget = message },
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
                }
                TorEmojiBurst(emojiEffectToken, emojiEffectText)
            }
        }
    }

    if (showAttachmentSheet) {
        OmniAttachmentSheet(
            burnOnReadArmed = burnOnReadArmed,
            onToggleBurnOnRead = { burnOnReadArmed = it },
            onPickPhoto = {
                groupService.speculativeWarmGroupCircuits(group.groupId, includeBulk = true)
                photoPicker.launch("image/*")
            },
            onStartVoiceNote = {
                groupService.speculativeWarmGroupCircuits(group.groupId, includeBulk = true)
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMic) isRecordingVoice = true
                else voicePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onPickDocument = {
                groupService.speculativeWarmGroupCircuits(group.groupId, includeBulk = true)
                documentPicker.launch(arrayOf("*/*"))
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    activeBurnTarget?.let { target ->
        val burnText = remember(target.text) { TorRichChatCodec.parseBurnText(target.text) }
        val file = target.attachmentPath?.let { java.io.File(it) }?.takeIf { it.exists() }
        AegisBurnViewerDialog(
            senderLabel = target.senderNickname,
            secretText = burnText?.secretText,
            attachmentFile = file,
            attachmentMimeType = target.attachmentMimeType,
            ttlSeconds = burnText?.ttlSeconds ?: 12,
            onBurnAndShred = {
                scope.launch { groupService.consumeAndShredGroupCapsule(group.groupId, target.id) }
            },
            onDismiss = { activeBurnTarget = null }
        )
    }

    actionTargetMessage?.let { target ->
        val cleanBody = TorGroupMessaging.parseMessageContent(target.text).body
        val isDeleted = target.id in deletedIds || cleanBody == TorGroupMessaging.DELETED_MESSAGE_TEXT
        val isBurned = target.id in burnedIds || cleanBody == TorRichChatCodec.BURNED_CAPSULE_TEXT
        val isViewOnce = TorRichChatCodec.isUnburnedSecretText(target.text) ||
            (TorRichChatCodec.parseAttachmentDescriptor(target.text)?.viewOnce == true)
        val canEdit = target.isFromMe && target.attachmentPath == null && !isDeleted && !isBurned && !isViewOnce
        val canDelete = !isDeleted && (target.isFromMe || isLocalAdmin)
        val canCopy = !isDeleted && !isBurned && !isViewOnce
        MessageActionSheet(
            onDismiss = { actionTargetMessage = null },
            onReact = if (!isDeleted) { emoji ->
                actionTargetMessage = null
                emojiEffectText = emoji
                emojiEffectToken++
                scope.launch { groupService.sendGroupReaction(group.groupId, target.id, emoji) }
            } else null,
            onReply = {
                actionTargetMessage = null
                replyingTo = target
            },
            onCopy = if (canCopy) {
                {
                    actionTargetMessage = null
                    val clean = TorRichChatCodec.friendlyPreviewText(target.text, target.attachmentMimeType)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Group message", clean))
                    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onEdit = if (canEdit) {
                {
                    actionTargetMessage = null
                    editDraft = TorGroupMessaging.parseMessageContent(target.text).body
                    editingMessage = target
                }
            } else null,
            onDelete = if (canDelete) {
                {
                    actionTargetMessage = null
                    deleteConfirmMessage = target
                }
            } else null,
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

    editingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            containerColor = OnionUi.Surface,
            title = { Text("Edit message", color = OnionUi.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { if (it.length <= 2_200) editDraft = it },
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = onionFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedText = editDraft
                        editingMessage = null
                        scope.launch {
                            groupService.editGroupMessage(group.groupId, target.id, updatedText)
                                .onFailure { Toast.makeText(context, it.message ?: "Unable to edit", Toast.LENGTH_SHORT).show() }
                        }
                    },
                    enabled = editDraft.isNotBlank()
                ) { Text("Save", color = OnionUi.Online, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel", color = OnionUi.TextSecondary)
                }
            }
        )
    }

    deleteConfirmMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmMessage = null },
            containerColor = OnionUi.Surface,
            title = { Text("Delete for everyone?", color = OnionUi.TextPrimary) },
            text = {
                Text(
                    "This removes the message content across members' devices via a signed Chrono-DAG tombstone.",
                    color = OnionUi.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmMessage = null
                    scope.launch {
                        groupService.deleteGroupMessageForEveryone(group.groupId, target.id)
                            .onFailure { Toast.makeText(context, it.message ?: "Unable to delete", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("Delete", color = OnionUi.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmMessage = null }) {
                    Text("Cancel", color = OnionUi.TextSecondary)
                }
            }
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
            isLocalAdmin = isLocalAdmin,
            adminsSet = groupAdminsMap[current.groupId].orEmpty(),
            currentEpoch = currentEpoch,
            currentDisappearingTtlMs = currentTtlMs,
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
    onOpenSheet: () -> Unit,
    onStartVoiceNote: () -> Unit,
    burnOnReadArmed: Boolean,
    onToggleBurnOnRead: () -> Unit,
    isRecordingVoice: Boolean,
    onCancelVoiceNote: () -> Unit,
    onSendVoiceNote: (bytes: ByteArray, durationMs: Int, fileName: String) -> Unit,
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
                val cleanBody = remember(target.text, target.attachmentMimeType) {
                    TorRichChatCodec.friendlyPreviewText(target.text, target.attachmentMimeType)
                }
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

        if (isRecordingVoice) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                SonicWarpRecorderBar(
                    burnOnReadArmed = burnOnReadArmed,
                    onCancel = onCancelVoiceNote,
                    onSendVoiceNote = onSendVoiceNote
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
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
                        IconButton(onClick = onOpenSheet, enabled = mediaEnabled, modifier = Modifier.size(42.dp)) {
                            Icon(
                                Icons.Default.Add,
                                "Attachments",
                                tint = if (mediaEnabled) OnionUi.Online else OnionUi.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.2f) else OnionUi.SurfacePressed)
                        .then(
                            if (burnOnReadArmed) Modifier.border(1.dp, AegisEmberColor.copy(alpha = 0.7f), CircleShape)
                            else Modifier
                        )
                        .clickable { onToggleBurnOnRead() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        "Toggle Burn-On-Read",
                        tint = if (burnOnReadArmed) AegisEmberColor else OnionUi.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            1.dp,
                            if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.75f) else OnionUi.Hairline,
                            RoundedCornerShape(24.dp)
                        ),
                    placeholder = {
                        Text(
                            if (burnOnReadArmed) "View-Once group secret…" else "Encrypted group message…",
                            color = if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.8f) else OnionUi.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = OnionUi.Surface,
                        unfocusedContainerColor = OnionUi.Surface,
                        focusedTextColor = OnionUi.TextPrimary,
                        unfocusedTextColor = OnionUi.TextPrimary,
                        cursorColor = OnionUi.TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(8.dp))
                if (canSend) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (burnOnReadArmed) AegisEmberColor else OnionUi.Accent)
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Send",
                            tint = if (burnOnReadArmed) Color.White else OnionUi.Canvas,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (burnOnReadArmed) AegisEmberColor else OnionUi.Online)
                            .clickable(enabled = mediaEnabled, onClick = onStartVoiceNote),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            "Record voice note",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupMessageBubble(
    message: GroupMessageEntity,
    showName: Boolean,
    isEdited: Boolean,
    isDeleted: Boolean,
    isBurned: Boolean,
    groupShredCount: Int,
    deliveryProgress: Pair<Int, Int>?,
    reactions: Map<String, String>,
    onLongPress: () -> Unit,
    onQuotedMessageClick: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    onOpenBurnCapsule: () -> Unit,
    onReactionClick: (String) -> Unit,
    onRetryClick: () -> Unit
) {
    val outgoing = message.isFromMe
    val parsed = remember(message.text) { TorGroupMessaging.parseMessageContent(message.text) }
    val burnText = remember(message.text) { TorRichChatCodec.parseBurnText(message.text) }
    val descriptor = remember(message.text, message.attachmentMimeType, message.attachmentPath) {
        TorRichChatCodec.inferDescriptor(message.text, message.attachmentMimeType, message.attachmentPath)
    }
    val isViewOnce = burnText != null || descriptor?.viewOnce == true || isBurned
    val primaryTextColor = if (outgoing) Color.White else OnionUi.TextPrimary
    val secondaryTextColor = if (outgoing) Color.White.copy(alpha = 0.56f) else OnionUi.TextSecondary
    val quoteBg = if (outgoing) Color.Black.copy(alpha = 0.25f) else OnionUi.SurfacePressed
    val quotePreviewColor = if (outgoing) Color.White.copy(alpha = 0.72f) else OnionUi.TextSecondary

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
                    onClick = {},
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

            if (!isDeleted && !parsed.replyToSender.isNullOrBlank()) {
                val replyId = parsed.replyToId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(quoteBg)
                        .then(
                            if (replyId != null) Modifier.clickable { onQuotedMessageClick(replyId) }
                            else Modifier
                        )
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
                                color = quotePreviewColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when {
                isDeleted -> {
                    Text(
                        TorGroupMessaging.DELETED_MESSAGE_TEXT,
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
                isViewOnce -> {
                    val kindLabel = when {
                        descriptor?.kind == "voice" -> "Voice Note"
                        descriptor?.kind == "photo" -> "Photo"
                        descriptor?.kind == "file" -> "File"
                        else -> "Secret Note"
                    }
                    val attFile = message.attachmentPath?.let { java.io.File(it) }?.takeIf { it.exists() }
                    val isPreview = attFile?.name?.startsWith("preview_") == true
                    val ready = burnText != null || (!isPreview && attFile != null)
                    AegisBurnCapsuleCard(
                        kindLabel = kindLabel,
                        isFromMe = outgoing,
                        isBurned = isBurned,
                        isReady = ready,
                        hasShredReceipt = groupShredCount > 0,
                        groupShredCount = groupShredCount,
                        onOpenCapsule = onOpenBurnCapsule
                    )
                }
                descriptor?.kind == "voice" -> {
                    OnionVoiceNoteCard(
                        filePath = message.attachmentPath,
                        durationMsHint = descriptor.durationMs,
                        outgoing = outgoing
                    )
                }
                descriptor?.kind == "file" -> {
                    OnionDocumentCard(
                        filePath = message.attachmentPath,
                        fileName = descriptor.fileName,
                        totalBytes = descriptor.totalBytes,
                        mimeType = message.attachmentMimeType,
                        outgoing = outgoing
                    )
                }
                else -> {
                    if (message.attachmentPath == null || parsed.body != "Photo") {
                        Text(
                            parsed.body,
                            color = primaryTextColor,
                            fontSize = if (isExpressiveEmoji(parsed.body)) 30.sp else 15.sp,
                            lineHeight = if (isExpressiveEmoji(parsed.body)) 36.sp else 21.sp
                        )
                    }

                    message.attachmentPath?.let { path ->
                        val decodedState by produceState<Pair<Boolean, ImageBitmap?>>(
                            initialValue = false to null,
                            key1 = path
                        ) {
                            val bmp = withContext(Dispatchers.IO) {
                                decodeScaledFileBitmap(path, maxDimensionPx = 640)
                            }
                            value = true to bmp
                        }
                        val (finished, bitmap) = decodedState
                        val isStreamingPreview = path.contains("preview_")
                        if (bitmap != null) {
                            if (parsed.body != "Photo") Spacer(Modifier.height(8.dp))
                            Box {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Group photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (!isStreamingPreview) {
                                                Modifier.clickable { onPhotoClick(path) }
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                                if (isStreamingPreview) {
                                    Text(
                                        text = "⚡ HD streaming…",
                                        color = OnionUi.Accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.Black.copy(alpha = 0.62f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        } else if (finished) {
                            if (parsed.body != "Photo") Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(quoteBg)
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Photo, null, tint = secondaryTextColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Photo unavailable", color = secondaryTextColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (!isDeleted && !isBurned && isExpressiveEmoji(parsed.body)) 2.dp else 5.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEdited && !isDeleted && !isBurned) {
                    Text(
                        "edited · ",
                        color = secondaryTextColor,
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
                Text(
                    formatChatTime(message.timestamp),
                    color = secondaryTextColor,
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
                                    color = OnionUi.TextPrimary,
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
    onDismiss: () -> Unit,
    onReact: ((String) -> Unit)?,
    onReply: () -> Unit,
    onCopy: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onRetry: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.CanvasRaised,
        contentColor = OnionUi.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            if (onReact != null) {
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Reply", Icons.AutoMirrored.Filled.Reply, Modifier.weight(1f), onClick = onReply)
                if (onCopy != null) {
                    ActionPill("Copy", Icons.Default.ContentCopy, Modifier.weight(1f), onClick = onCopy)
                }
            }
            if (onEdit != null || onDelete != null || onRetry != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onEdit != null) {
                        ActionPill("Edit", Icons.Default.Edit, Modifier.weight(1f), onClick = onEdit)
                    }
                    if (onDelete != null) {
                        ActionPill("Delete", Icons.Default.Delete, Modifier.weight(1f), tint = OnionUi.Danger, onClick = onDelete)
                    }
                    if (onRetry != null) {
                        ActionPill("Retry", Icons.Default.Refresh, Modifier.weight(1f), onClick = onRetry)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun FullscreenPhotoDialog(path: String, onDismiss: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { decodeScaledFileBitmap(path, maxDimensionPx = 2048) }
    }
    val loadedBitmap = bitmap
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
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap,
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
    isLocalAdmin: Boolean,
    adminsSet: Set<String>,
    currentEpoch: Long,
    currentDisappearingTtlMs: Long,
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
    var showTimerDialog by remember { mutableStateOf(false) }
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
        contentColor = OnionUi.TextPrimary
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GroupAvatar(group.name, size = 76.dp, onlineCount = onlineCount)
                Spacer(Modifier.height(12.dp))
                Text(group.name, color = OnionUi.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${members.size} member${if (members.size == 1) "" else "s"} · created by ${if (group.isCreatedByMe) "you" else group.creatorNickname}",
                    color = OnionUi.TextSecondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                val timerBadge = if (currentDisappearingTtlMs > 0L) " · ⏱ ${formatTimerShort(currentDisappearingTtlMs)}" else ""
                OnionStatusChip(
                    label = if (onlineCount > 0) "$onlineCount online · Chrono-DAG Epoch #$currentEpoch$timerBadge"
                    else "Chrono-DAG Epoch #$currentEpoch$timerBadge · Pairwise E2EE",
                    color = OnionUi.Online
                )
            }
            Spacer(Modifier.height(18.dp))

            if (isLocalAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionPill("Add", Icons.Default.GroupAdd, Modifier.weight(1f)) { showAdd = true }
                    ActionPill("Rename", Icons.Default.Edit, Modifier.weight(1f)) { showRename = true }
                    ActionPill("Timer", Icons.Default.Timer, Modifier.weight(1f)) { showTimerDialog = true }
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
                val creatorCanon = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(group.creatorOnionAddress)
                val sortedMembers = members.sortedByDescending {
                    val mCanon = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it.memberOnionAddress)
                    when {
                        mCanon == creatorCanon -> 2
                        mCanon in adminsSet -> 1
                        else -> 0
                    }
                }
                items(sortedMembers.size, key = { sortedMembers[it].memberOnionAddress }) { idx ->
                    val member = sortedMembers[idx]
                    val canonicalMember = OnionContactManager.OnionContact.canonicalizeOnionEndpoint(member.memberOnionAddress)
                    val isMe = canonicalMember == myOnion
                    val isCreator = canonicalMember == creatorCanon
                    val isMemberAdmin = isCreator || canonicalMember in adminsSet
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
                                    color = OnionUi.TextPrimary,
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
                                color = OnionUi.Canvas,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(OnionUi.Accent)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        } else if (isMemberAdmin) {
                            Text(
                                "Admin",
                                color = OnionUi.Online,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(OnionUi.Online.copy(alpha = 0.14f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                        if (isLocalAdmin && !isMe && !isCreator) {
                            if (!isMemberAdmin) {
                                IconButton(onClick = {
                                    scope.launch {
                                        service.promoteAdmin(group.groupId, member.memberOnionAddress)
                                            .onSuccess { toast("${member.memberNickname} promoted to admin") }
                                            .onFailure { toast(it.message ?: "Unable to promote") }
                                    }
                                }) {
                                    Icon(Icons.Default.Shield, "Make admin", tint = OnionUi.Online)
                                }
                            }
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
        val remainingSlots = (32 - members.size).coerceAtLeast(0)
        val candidates = contactManager.getAllContacts().filter {
            OnionContactManager.OnionContact.canonicalizeOnionEndpoint(it.onionAddress) !in memberAddresses &&
                !it.requiresRendezvous() &&
                it.effectiveTrustState() != TorContactTrustState.BLOCKED &&
                it.effectiveTrustState() != TorContactTrustState.KEY_CHANGED
        }
        ModalBottomSheet(
            onDismissRequest = { showAdd = false; selected = emptySet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = OnionUi.CanvasRaised,
            contentColor = OnionUi.TextPrimary
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Add members ($remainingSlots slots left)", color = OnionUi.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (candidates.isEmpty() || remainingSlots <= 0) {
                    Text(
                        if (remainingSlots <= 0) "This group is already at the 32-member limit."
                        else "No eligible verified Tor contacts available to add.",
                        color = OnionUi.TextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    ContactPickerList(
                        candidates = candidates,
                        selected = selected,
                        onToggle = { id ->
                            selected = if (id in selected) selected - id
                            else if (selected.size < remainingSlots) selected + id else selected
                        },
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
                            containerColor = OnionUi.Accent,
                            contentColor = OnionUi.Canvas,
                            disabledContainerColor = OnionUi.TextPrimary.copy(alpha = 0.14f),
                            disabledContentColor = OnionUi.TextPrimary.copy(alpha = 0.4f)
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
            title = { Text("Rename group", color = OnionUi.TextPrimary) },
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
                }) { Text("Save", color = OnionUi.Online, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel", color = OnionUi.TextSecondary) } }
        )
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = OnionUi.Surface,
            title = { Text("Disappearing messages", color = OnionUi.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisappearingOptions.forEach { (ttl, label) ->
                        val active = currentDisappearingTtlMs == ttl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) OnionUi.Online.copy(alpha = 0.16f) else OnionUi.SurfacePressed)
                                .border(1.dp, if (active) OnionUi.Online else OnionUi.Hairline, RoundedCornerShape(12.dp))
                                .clickable {
                                    showTimerDialog = false
                                    scope.launch {
                                        service.setDisappearingMessages(group.groupId, ttl)
                                            .onSuccess { toast("Disappearing timer: $label") }
                                            .onFailure { toast(it.message ?: "Unable to update timer") }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                color = if (active) OnionUi.Online else OnionUi.TextPrimary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (active) {
                                Icon(Icons.Default.Done, null, tint = OnionUi.Online, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("Close", color = OnionUi.TextSecondary)
                }
            }
        )
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            containerColor = OnionUi.Surface,
            title = { Text("Remove ${member.memberNickname}?", color = OnionUi.TextPrimary) },
            text = { Text("They will lose access to new messages, the group epoch key will rotate, and they will be told they were removed.", color = OnionUi.TextSecondary) },
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
            title = { Text("Leave group?", color = OnionUi.TextPrimary) },
            text = {
                Text(
                    if (isLocalAdmin) "Admin permissions will automatically transfer to another member if others remain. Chat history on this device is deleted."
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
    tint: Color = OnionUi.TextPrimary,
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
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
