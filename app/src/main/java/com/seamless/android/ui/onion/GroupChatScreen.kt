package com.seamless.android.ui.onion

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.GroupMessageEntity
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorGroupMessaging
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(group: PrivateGroupEntity, onNavigateBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val groupDao = remember { AppDatabase.getDatabase(context).groupDao() }
    val groupService = remember { TorGroupMessaging.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val messages by groupDao.getGroupMessages(group.groupId).collectAsState(initial = emptyList())
    val members by groupDao.getMembersForGroup(group.groupId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var showAddMembers by remember { mutableStateOf(false) }
    var selectedContacts by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    OnionPage {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${members.size} members / encrypted over Tor", color = OnionUi.TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (group.isCreatedByMe) {
                        IconButton(onClick = { showAddMembers = true }) {
                            Icon(Icons.Default.GroupAdd, "Add members", tint = OnionUi.Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OnionUi.CanvasRaised)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(OnionUi.CanvasRaised).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 8_000) text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message group") },
                    singleLine = false,
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = {
                            val outgoing = text.trim()
                            if (outgoing.isNotEmpty()) {
                                text = ""
                                scope.launch {
                                    groupService.sendGroupMessage(group.groupId, outgoing)
                                        .onFailure { Toast.makeText(context, it.message ?: "Unable to queue message", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }) { Icon(Icons.Default.Send, "Send", tint = OnionUi.Accent) }
                    }
                )
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Messages stay in this private group.", color = OnionUi.TextSecondary, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages, key = { it.id }) { message -> GroupMessageBubble(message) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
    }

    if (showAddMembers) {
        val memberAddresses = members.map { it.memberOnionAddress }.toSet()
        val candidates = contactManager.getAllContacts().filter { it.onionAddress !in memberAddresses && !it.requiresRendezvous() }
        AlertDialog(
            onDismissRequest = { showAddMembers = false },
            title = { Text("Add members") },
            text = {
                if (candidates.isEmpty()) Text("All available Tor contacts are already in this group.")
                else LazyColumn {
                    items(candidates, key = { it.id }) { contact ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = contact.id in selectedContacts,
                                onCheckedChange = { checked ->
                                    selectedContacts = if (checked) selectedContacts + contact.id else selectedContacts - contact.id
                                }
                            )
                            Text(contact.nickname)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val invitees = selectedContacts
                    selectedContacts = emptySet()
                    showAddMembers = false
                    scope.launch {
                        groupService.addMembers(group.groupId, invitees)
                            .onFailure { Toast.makeText(context, it.message ?: "Unable to add members", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddMembers = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupMessageBubble(message: GroupMessageEntity) {
    val outgoing = message.isFromMe
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.background(
                if (outgoing) OnionUi.AccentStrong else OnionUi.Surface,
                OnionUi.Corner
            ).padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            if (!outgoing) Text(message.senderNickname, color = OnionUi.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(message.text, color = Color.White)
            Spacer(Modifier.height(3.dp))
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 10.sp
            )
        }
    }
}
