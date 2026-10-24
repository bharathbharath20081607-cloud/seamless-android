@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.GroupMemberEntity
import com.seamless.android.data.GroupMessageEntity
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorGroupMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(group: PrivateGroupEntity, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val groupDao = remember { AppDatabase.getDatabase(context).groupDao() }
    val groupService = remember { TorGroupMessaging.getInstance(context) }
    val allGroups by remember { groupDao.getAllGroups() }.collectAsState(initial = listOf(group))
    val messages by remember(group.groupId) { groupDao.getGroupMessages(group.groupId) }
        .collectAsState(initial = emptyList())
    val members by remember(group.groupId) { groupDao.getMembersForGroup(group.groupId) }
        .collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var isSendingMedia by remember { mutableStateOf(false) }
    var emojiEffectText by remember { mutableStateOf("") }
    var emojiEffectToken by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
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

    DisposableEffect(group.groupId) {
        groupService.visibleGroupId = group.groupId
        groupService.markRead(group.groupId)
        onDispose {
            groupService.markRead(group.groupId)
            if (groupService.visibleGroupId == group.groupId) groupService.visibleGroupId = null
        }
    }
    // A composable can remain in memory after the app goes to background. Clear
    // the visible marker then, so mailbox-delivered group messages still raise
    // a system notification while the user is away from the app.
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
            text = ""
            emojiEffectText = outgoing
            emojiEffectToken++
            scope.launch {
                groupService.sendGroupMessage(group.groupId, outgoing)
                    .onFailure { Toast.makeText(context, it.message ?: "Unable to queue message", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    androidx.activity.compose.BackHandler {
        if (showInfo) {
            showInfo = false
        } else {
            onNavigateBack()
        }
    }

    OnionPage {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showInfo = true }) {
                            GroupAvatar(current.name, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(current.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, null, tint = OnionUi.Online, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${members.size} member${if (members.size == 1) "" else "s"} · end-to-end over Tor",
                                        color = OnionUi.TextSecondary, fontSize = 11.sp, maxLines = 1
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
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Group info", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = OnionUi.CanvasRaised)
                )
            },
            bottomBar = {
                Composer(text = text, onTextChange = { if (it.length <= 8_000) text = it }, onSend = send,
                    onPhoto = { photoPicker.launch("image/*") }, mediaEnabled = !isSendingMedia)
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        GroupAvatar(current.name, size = 72.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Say hello to ${current.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Messages are encrypted separately for each member and delivered over Tor, even when someone is offline.",
                            color = OnionUi.TextSecondary, textAlign = TextAlign.Center, fontSize = 13.sp
                        )
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
                        val showName = !message.isFromMe &&
                            (previous == null || previous.isFromMe || previous.senderOnionAddress != message.senderOnionAddress)
                        GroupMessageBubble(message, showName)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
            TorEmojiBurst(emojiEffectToken, emojiEffectText)
        }
    }

    if (showInfo) {
        GroupInfoSheet(
            group = current,
            members = members,
            onDismiss = { showInfo = false },
            onLeft = { showInfo = false; onNavigateBack() }
        )
    }
}

@Composable
private fun Composer(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onPhoto: () -> Unit, mediaEnabled: Boolean) {
    val canSend = text.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnionUi.CanvasRaised)
            .border(1.dp, OnionUi.Hairline)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(OnionUi.SurfacePressed), contentAlignment = Alignment.Center) {
            IconButton(onClick = onPhoto, enabled = mediaEnabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Photo, "Send photo", tint = if (mediaEnabled) OnionUi.Online else OnionUi.TextMuted, modifier = Modifier.size(20.dp))
            }
        }
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, OnionUi.Hairline, RoundedCornerShape(24.dp)),
            placeholder = { Text("Message group", color = OnionUi.TextMuted) },
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
                Icons.AutoMirrored.Filled.Send, "Send",
                tint = if (canSend) Color.Black else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun GroupMessageBubble(message: GroupMessageEntity, showName: Boolean) {
    val outgoing = message.isFromMe
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 1.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .graphicsLayer { this.alpha = alpha; this.translationY = translateY; scaleX = scale; scaleY = scale }
                .shadow(if (outgoing) 6.dp else 2.dp, shape, spotColor = Color.Black.copy(alpha = 0.32f))
                .clip(shape)
                .background(bubbleBrush, shape)
                .border(1.dp, if (outgoing) Color.White.copy(alpha = 0.17f) else OnionUi.Hairline, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (showName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape).background(senderColor(message.senderOnionAddress))
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
            Text(
                message.text,
                color = Color.White,
                fontSize = if (isExpressiveEmoji(message.text)) 30.sp else 15.sp,
                lineHeight = if (isExpressiveEmoji(message.text)) 36.sp else 21.sp
            )
            message.attachmentPath?.let { path ->
                val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                if (bitmap != null) {
                    Spacer(Modifier.height(8.dp))
                    Image(bitmap, "Group photo", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)))
                }
            }
            Spacer(Modifier.height(if (isExpressiveEmoji(message.text)) 1.dp else 5.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(formatChatTime(message.timestamp), color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp)
                if (outgoing) {
                    Spacer(Modifier.width(4.dp))
                    DeliveryMark(message.deliveryState)
                }
            }
        }
    }
}

@Composable
private fun DeliveryMark(state: String) {
    val (icon, tint) = when (state) {
        TorGroupMessaging.STATE_DELIVERED -> Icons.Default.DoneAll to OnionUi.Online
        TorGroupMessaging.STATE_SENT -> Icons.Default.Done to Color.White.copy(alpha = 0.7f)
        TorGroupMessaging.STATE_PARTIAL -> Icons.Default.WarningAmber to OnionUi.Warning
        TorGroupMessaging.STATE_FAILED -> Icons.Default.ErrorOutline to OnionUi.Danger
        else -> Icons.Default.Schedule to Color.White.copy(alpha = 0.5f)
    }
    Icon(icon, contentDescription = state.lowercase(), tint = tint, modifier = Modifier.size(13.dp))
}

// ───────────────────────── Group info ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    group: PrivateGroupEntity,
    members: List<GroupMemberEntity>,
    onDismiss: () -> Unit,
    onLeft: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { TorGroupMessaging.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val myOnion = remember { contactManager.getMyContactInfo().onionAddress }

    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberEntity?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var renameText by remember(group.name) { mutableStateOf(group.name) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.CanvasRaised,
        contentColor = Color.White
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GroupAvatar(group.name, size = 76.dp)
                Spacer(Modifier.height(12.dp))
                Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "${members.size} member${if (members.size == 1) "" else "s"} · created by ${if (group.isCreatedByMe) "you" else group.creatorNickname}",
                    color = OnionUi.TextSecondary, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(18.dp))

            if (group.isCreatedByMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionPill("Add members", Icons.Default.GroupAdd, Modifier.weight(1f)) { showAdd = true }
                    ActionPill("Rename", Icons.Default.Info, Modifier.weight(1f)) { showRename = true }
                }
                Spacer(Modifier.height(14.dp))
            }

            Text("MEMBERS", color = OnionUi.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.height((members.size.coerceIn(1, 5) * 62).dp)) {
                items(members.sortedByDescending { it.memberOnionAddress == group.creatorOnionAddress }, key = { it.memberOnionAddress }) { member ->
                    val isMe = member.memberOnionAddress == myOnion
                    val isCreator = member.memberOnionAddress == group.creatorOnionAddress
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GroupAvatar(member.memberNickname, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                member.memberNickname + if (isMe) " (you)" else "",
                                color = Color.White, fontWeight = FontWeight.SemiBold
                            )
                            Text(member.memberOnionAddress.take(18) + "…", color = OnionUi.TextMuted, fontSize = 12.sp)
                        }
                        if (isCreator) {
                            Text(
                                "Creator", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 9.dp, vertical = 3.dp)
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
                colors = ButtonDefaults.buttonColors(containerColor = OnionUi.Danger.copy(alpha = 0.14f), contentColor = OnionUi.Danger),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Leave group", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showAdd) {
        val memberAddresses = members.map { it.memberOnionAddress }.toSet()
        val candidates = contactManager.getAllContacts().filter { it.onionAddress !in memberAddresses && !it.requiresRendezvous() }
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
                    Text("Everyone in your Tor contacts is already in this group.", color = OnionUi.TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    ContactPickerList(candidates, selected, { id -> selected = if (id in selected) selected - id else selected + id }, Modifier.height(300.dp))
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
                            containerColor = Color.White, contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.18f), disabledContentColor = Color.White.copy(alpha = 0.4f)
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
private fun ActionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
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
