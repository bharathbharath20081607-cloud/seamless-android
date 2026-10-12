package com.seamless.android.ui.onion

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(group: PrivateGroupEntity, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
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
    var showInfo by remember { mutableStateOf(false) }

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
            scope.launch {
                groupService.sendGroupMessage(group.groupId, outgoing)
                    .onFailure { Toast.makeText(context, it.message ?: "Unable to queue message", Toast.LENGTH_SHORT).show() }
            }
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
                Composer(text = text, onTextChange = { if (it.length <= 8_000) text = it }, onSend = send)
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
private fun Composer(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    val canSend = text.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnionUi.CanvasRaised)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
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
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (outgoing) 18.dp else 5.dp,
        bottomEnd = if (outgoing) 5.dp else 18.dp
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (outgoing) OnionUi.AccentStrong else OnionUi.Surface)
                .border(1.dp, OnionUi.Hairline, shape)
                .padding(horizontal = 13.dp, vertical = 8.dp)
        ) {
            if (showName) {
                Text(
                    message.senderNickname,
                    color = senderColor(message.senderOnionAddress),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(message.text, color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(formatChatTime(message.timestamp), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
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
