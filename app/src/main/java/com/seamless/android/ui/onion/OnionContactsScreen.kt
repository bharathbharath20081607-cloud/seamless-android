package com.seamless.android.ui.onion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.seamless.android.data.TorMessageEntity
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorHiddenServiceManager
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorMessagingService
import com.seamless.android.net.TorGroupMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OnionContactsScreen - Displays P2P contacts for zero-server messaging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionContactsScreen(
    onNavigateBack: () -> Unit,
    onContactClick: (OnionContactManager.OnionContact) -> Unit,
    onShareMyContact: () -> Unit,
    onGroupClick: (PrivateGroupEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val hiddenServiceManager = remember { TorHiddenServiceManager.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val messageRepository = remember { TorMessageRepository.getInstance(context) }

    val contacts by contactManager.contactsFlow.collectAsState()
    val serviceState by messagingService.serviceState.collectAsState()
    val serviceError by messagingService.lastError.collectAsState()
    val torStatus by messagingService.torStatus.collectAsState()
    val connectedPeers by messagingService.connectedPeersFlow.collectAsState()
    val hiddenStatus by hiddenServiceManager.statusFlow.collectAsState()
    val recentMessages by messageRepository.getRecentConversations().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQrInput by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (serviceState == TorMessagingService.ServiceState.STOPPED) {
            messagingService.start()
        }
    }

    OnionPage {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tor P2P Contacts", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            getServiceStatusText(serviceState, torStatus, hiddenStatus, serviceError),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showShareSheet = true
                        onShareMyContact()
                    }) {
                        Icon(Icons.Default.Share, "Share my contact", tint = OnionUi.Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OnionUi.CanvasRaised)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 1) showCreateGroup = true else showAddDialog = true
                },
                containerColor = OnionUi.Accent,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = if (selectedTab == 1) "Create group" else "Add contact")
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = OnionUi.CanvasRaised,
                contentColor = OnionUi.Accent
            ) {
                listOf("Contacts", "Groups").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.PersonAdd
                                    else -> Icons.Default.Groups
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (contacts.isEmpty()) {
                        EmptyContactsView(
                            serviceState = serviceState,
                            errorText = serviceError ?: hiddenStatus.error,
                            onShareMyContact = { showShareSheet = true },
                            onRetry = { scope.launch { messagingService.start() } }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(contacts, key = { it.id }) { contact ->
                                val lastMessage = recentMessages.firstOrNull {
                                    it.contactId == contact.id || it.contactOnionAddress == contact.onionAddress
                                }
                                ContactCard(
                                    contact = contact,
                                    lastMessage = lastMessage,
                                    isOnline = connectedPeers.contains(contact.onionAddress),
                                    onClick = { onContactClick(contact) }
                                )
                            }
                        }
                    }
                }
                1 -> PrivateGroupsTab(onGroupClick)
            }
        }
    }
    }

    if (showShareSheet) {
        ShareContactSheet(onDismiss = { showShareSheet = false })
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onPasteQr = {
                showAddDialog = false
                showQrInput = true
            },
            onAdd = { value ->
                scope.launch {
                    val contact = messagingService.addContact(value)
                    Toast.makeText(
                        context,
                        if (contact != null) "Contact added" else "Invalid contact data",
                        Toast.LENGTH_SHORT
                    ).show()
                    showAddDialog = false
                }
            }
        )
    }

    if (showQrInput) {
        QrScannerScreen(
            onScanResult = { value ->
                scope.launch {
                    val contact = messagingService.addContact(value)
                    Toast.makeText(
                        context,
                        if (contact != null) "Contact added" else "Invalid QR data",
                        Toast.LENGTH_SHORT
                    ).show()
                    showQrInput = false
                }
            },
            onCancel = { showQrInput = false }
        )
    }

    if (showCreateGroup) {
        CreateGroupSheet(
            contacts = contacts,
            onDismiss = { showCreateGroup = false },
            onCreate = { name, invitees ->
                if (name.isBlank()) return@CreateGroupSheet
                showCreateGroup = false
                scope.launch {
                    TorGroupMessaging.getInstance(context).createGroup(name, invitees)
                        .onSuccess { onGroupClick(it) }
                        .onFailure {
                            Toast.makeText(
                                context,
                                it.message ?: "Unable to create group",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        )
    }
}

@Composable
private fun EmptyContactsView(
    serviceState: TorMessagingService.ServiceState,
    errorText: String?,
    onShareMyContact: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(72.dp), tint = OnionUi.Accent)
        Spacer(Modifier.height(18.dp))
        Text("No onion contacts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (serviceState == TorMessagingService.ServiceState.ERROR) {
                "Tor is not ready yet. ${errorText?.take(96) ?: "Tap retry after checking network access."}"
            } else {
                "Share your contact after Tor publishes your onion address, or add someone else's link."
            },
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))
        if (serviceState == TorMessagingService.ServiceState.ERROR) {
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B6CFF))) {
                Icon(Icons.Default.HourglassTop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retry Tor", color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
        }
        Button(onClick = onShareMyContact, colors = ButtonDefaults.buttonColors(containerColor = OnionUi.Accent)) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share My Contact", color = Color.Black)
        }
    }
}

@Composable
private fun ContactCard(
    contact: OnionContactManager.OnionContact,
    lastMessage: TorMessageEntity?,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = OnionUi.Corner,
        colors = CardDefaults.cardColors(containerColor = OnionUi.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnionAvatar(contact.nickname, isOnline)

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.nickname,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    TrustBadge(contact.effectiveTrustState())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        contact.requiresRendezvous() -> "Address pending. Re-scan after Tor is ready."
                        lastMessage != null -> lastMessage.text
                        else -> contact.onionAddress.take(18) + "..."
                    },
                    color = OnionUi.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrustBadge(state: TorContactTrustState) {
    val (text, color, _) = when (state) {
        TorContactTrustState.VERIFIED -> Triple("Verified", OnionUi.Accent, Icons.Default.VerifiedUser)
        TorContactTrustState.KEY_CHANGED -> Triple("Key changed", Color(0xFFFF5252), Icons.Default.Report)
        TorContactTrustState.BLOCKED -> Triple("Blocked", Color(0xFFFF5252), Icons.Default.Report)
        TorContactTrustState.UNVERIFIED -> Triple("Unverified", Color(0xFFFFC857), Icons.Default.HourglassTop)
    }
    OnionStatusChip(text, color)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContactSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val hiddenServiceManager = remember { TorHiddenServiceManager.getInstance(context) }
    val hiddenStatus by hiddenServiceManager.statusFlow.collectAsState()
    val myContact = remember(hiddenStatus.onionAddress) { contactManager.getMyContactInfo() }
    val hasRealOnion = !myContact.requiresRendezvous()
    val publishError = hiddenStatus.error?.takeIf { it.isNotBlank() }
    val shareData = remember(myContact) { myContact.toShareableLink() }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(shareData, hasRealOnion) {
        qrBitmap = if (hasRealOnion) {
            withContext(Dispatchers.Default) { generateQRCodeBitmap(shareData, 512) }
        } else {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnionUi.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Share Tor Contact", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Card(
                shape = OnionUi.Corner,
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    if (hasRealOnion && qrBitmap != null) {
                        Image(qrBitmap!!.asImageBitmap(), contentDescription = "Contact QR")
                    } else if (publishError != null) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Report, null, tint = Color(0xFFFF5252), modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Tor publish error", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                publishError.take(110),
                                color = Color.Black.copy(alpha = 0.72f),
                                fontSize = 11.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = OnionUi.Accent)
                            Spacer(Modifier.height(12.dp))
                            Text("Publishing onion address", color = Color.Black, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    hasRealOnion -> myContact.onionAddress
                    publishError != null -> "Tor could not publish your onion address. Retry after checking network/Tor access."
                    else -> "Wait until Tor shows your onion address, then share."
                },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = hasRealOnion,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Seamless Tor contact", shareData))
                        Toast.makeText(context, "Contact link copied", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnionUi.Accent)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy", color = Color.Black)
                }
                Button(
                    enabled = hasRealOnion,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shareData)
                        context.startActivity(Intent.createChooser(intent, "Share contact"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C6FF))
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share", color = Color.Black)
                }
            }

            if (!hasRealOnion) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            messagingService.start()
                            hiddenServiceManager.republishHiddenService()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B6CFF))
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Tor Publish", color = Color.White)
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun generateQRCodeBitmap(data: String, size: Int): Bitmap {
    val bitMatrix: BitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

private fun getServiceStatusText(
    serviceState: TorMessagingService.ServiceState,
    torStatus: TorManager.TorStatus,
    hiddenStatus: TorHiddenServiceManager.HiddenServiceStatus,
    serviceError: String?
): String {
    val myOnion = hiddenStatus.onionAddress
    return when (serviceState) {
        TorMessagingService.ServiceState.RUNNING -> {
            when {
                hiddenStatus.state == TorHiddenServiceManager.HiddenServiceState.ERROR ->
                    "Tor error - ${(hiddenStatus.error ?: "hidden service").take(48)}"
                myOnion.isBlank() -> "Online - publishing onion address"
                else -> "Online - ${myOnion.take(18)}..."
            }
        }
        TorMessagingService.ServiceState.WAITING_FOR_TOR -> {
            val percent = maxOf(torStatus.bootstrapPercent, if (hiddenStatus.torConnected) 100 else 0)
            "Connecting to Tor $percent%"
        }
        TorMessagingService.ServiceState.STARTING -> "Starting Tor"
        TorMessagingService.ServiceState.RENDEZVOUS -> "Resolving contact"
        TorMessagingService.ServiceState.ERROR ->
            "Tor error - ${(serviceError ?: hiddenStatus.error ?: torStatus.lastLogLine.ifBlank { "tap retry" }).take(48)}"
        TorMessagingService.ServiceState.STOPPED -> "Offline"
    }
}

@Composable
private fun QrScannerScreen(
    onScanResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add Tor Contact") },
        text = {
            Column {
                Text("Paste the QR text or Seamless contact link.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Contact QR/link") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onScanResult(value.trim()) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

private fun processQRCode(imageProxy: ImageProxy, onResult: (String) -> Unit) {
    try {
        val buffer = imageProxy.planes.firstOrNull()?.buffer ?: return
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val width = imageProxy.width
        val height = imageProxy.height
        val pixels = IntArray(width * height) { index ->
            val luminance = bytes.getOrNull(index)?.toInt()?.and(0xFF) ?: 0
            android.graphics.Color.rgb(luminance, luminance, luminance)
        }
        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = MultiFormatReader().decode(bitmap)
        onResult(result.text)
    } catch (_: Exception) {
    } finally {
        imageProxy.close()
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onPasteQr: () -> Unit,
    onAdd: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tor Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Contact link") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onPasteQr) {
                    Icon(Icons.Default.QrCode, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste QR text")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onAdd(value.trim()) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(32.dp))
    }
}
