@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.graphics.Bitmap
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.platform.LocalView
import com.seamless.android.ui.call.rememberCallHaptics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.seamless.android.service.TorForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * OnionContactsScreen - Displays P2P contacts for zero-server messaging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionContactsScreen(
    onNavigateBack: () -> Unit,
    onContactClick: (OnionContactManager.OnionContact) -> Unit,
    onShareMyContact: () -> Unit,
    onGroupClick: (PrivateGroupEntity) -> Unit,
    initialTab: Int = 0,
    onTabChanged: (Int) -> Unit = {}
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
    val activeGroupRooms by com.seamless.android.call.GroupCallManager.getInstance(context)
        .activeRoomsByGroupFlow.collectAsState()

    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQrInput by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var bannerDismissed by remember { mutableStateOf(false) }
    var notifHealthy by remember { mutableStateOf(com.seamless.android.util.TorReliabilityHelper.isTorChannelAudible(context)) }
    var batteryHealthy by remember { mutableStateOf(com.seamless.android.util.TorReliabilityHelper.isBatteryOptimizationIgnored(context)) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifHealthy = com.seamless.android.util.TorReliabilityHelper.isTorChannelAudible(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifHealthy = com.seamless.android.util.TorReliabilityHelper.isTorChannelAudible(context)
                batteryHealthy = com.seamless.android.util.TorReliabilityHelper.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.activity.compose.BackHandler {
        when {
            showQrInput -> {
                showQrInput = false
                showAddDialog = true
            }
            showAddDialog -> showAddDialog = false
            showShareSheet -> showShareSheet = false
            showCreateGroup -> showCreateGroup = false
            else -> onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !com.seamless.android.util.TorReliabilityHelper.hasNotificationPermission(context)
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (serviceState == TorMessagingService.ServiceState.STOPPED) {
            TorForegroundService.start(context.applicationContext)
            messagingService.start()
        }
    }

    LaunchedEffect(serviceState) {
        if (serviceState == TorMessagingService.ServiceState.RUNNING) {
            messagingService.prewarmActiveContacts()
        }
    }

    OnionPage {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tor P2P Contacts", color = OnionUi.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            getServiceStatusText(serviceState, torStatus, hiddenStatus, serviceError),
                            color = OnionUi.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnionUi.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showShareSheet = true
                        onShareMyContact()
                    }) {
                        Icon(Icons.Default.Share, "Share my contact", tint = OnionUi.TextPrimary)
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
                containerColor = OnionUi.PrimaryStart,
                contentColor = OnionUi.OnPrimary
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
                        onClick = {
                            selectedTab = index
                            onTabChanged(index)
                        },
                        text = {
                            if (index == 1 && activeGroupRooms.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label)
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(OnionUi.Online.copy(alpha = 0.20f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            "${activeGroupRooms.size} Live",
                                            color = OnionUi.Online,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Text(label)
                            }
                        },
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

            if (!bannerDismissed && (!notifHealthy || !batteryHealthy)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = OnionUi.CanvasRaised,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        OnionUi.Accent.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = OnionUi.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!notifHealthy && !batteryHealthy) {
                                        "Enable alerts & background sync"
                                    } else if (!notifHealthy) {
                                        "Tor message alerts are muted"
                                    } else {
                                        "Keep Tor alive in background"
                                    },
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = OnionUi.TextPrimary
                                )
                                Text(
                                    text = if (!notifHealthy) {
                                        "Allow sound & lockscreen banners so new Tor messages never arrive silently."
                                    } else {
                                        "Disable battery restrictions to receive Tor messages & calls reliably."
                                    },
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = OnionUi.TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { bannerDismissed = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = OnionUi.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!notifHealthy) {
                                TextButton(
                                    onClick = {
                                        val needsRuntimePerm =
                                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                                androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.POST_NOTIFICATIONS
                                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (needsRuntimePerm) {
                                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            com.seamless.android.util.TorReliabilityHelper
                                                .openTorNotificationSettings(context)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Enable Alerts",
                                        color = OnionUi.Accent,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            if (!batteryHealthy) {
                                TextButton(
                                    onClick = {
                                        com.seamless.android.util.TorReliabilityHelper
                                            .openBackgroundReliabilitySettings(context)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Allow Background",
                                        color = OnionUi.Accent,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
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
            onCancel = {
                showQrInput = false
                showAddDialog = true
            }
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
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(64.dp), tint = OnionUi.Online)
        Spacer(Modifier.height(16.dp))
        Text("No onion contacts yet", color = OnionUi.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (serviceState == TorMessagingService.ServiceState.ERROR) {
                "Tor is not ready yet. ${errorText?.take(96) ?: "Tap retry after checking network access."}"
            } else {
                "Share your contact once Tor is ready, or add a friend's invite link or QR."
            },
            color = OnionUi.TextSecondary,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (serviceState == TorMessagingService.ServiceState.ERROR) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = OnionUi.Accent, contentColor = OnionUi.AccentContent)
            ) {
                Icon(Icons.Default.HourglassTop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retry Tor")
            }
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = onShareMyContact,
            colors = ButtonDefaults.buttonColors(containerColor = OnionUi.PrimaryStart, contentColor = OnionUi.OnPrimary)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share My Contact", fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val callManager = remember { com.seamless.android.call.SeamlessCallManager.getInstance(context) }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            callManager.startOutgoingCall(contact)
        } else {
            Toast.makeText(context, "Microphone permission is required for voice calls", Toast.LENGTH_SHORT).show()
        }
    }
    val videoPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasMic = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCam = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            hasMic && hasCam -> callManager.startOutgoingCall(contact, isVideoCall = true)
            hasMic -> {
                Toast.makeText(context, "Camera permission denied — starting voice call", Toast.LENGTH_SHORT).show()
                callManager.startOutgoingCall(contact, isVideoCall = false)
            }
            else -> Toast.makeText(context, "Microphone and camera permissions are required for video calls", Toast.LENGTH_SHORT).show()
        }
    }
    val trust = contact.effectiveTrustState()
    val hasUnread = lastMessage != null && !lastMessage.isFromMe && lastMessage.readAt == null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = OnionUi.Corner,
        colors = CardDefaults.cardColors(containerColor = OnionUi.Surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hasUnread) OnionUi.AccentBlue.copy(alpha = 0.45f) else OnionUi.Hairline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnionAvatar(contact.nickname, isOnline, contact.avatarId, size = 50.dp)

            Spacer(Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        contact.nickname,
                        color = OnionUi.TextPrimary,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(5.dp))
                    TrustBadgeIcon(trust)
                    Spacer(Modifier.weight(1f))
                    lastMessage?.let { msg ->
                        Text(
                            text = formatChatTime(msg.timestamp),
                            color = if (hasUnread) OnionUi.AccentBlue else OnionUi.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewText = when {
                        contact.requiresRendezvous() -> "Address pending — re-scan when ready"
                        lastMessage != null -> {
                            val prefix = if (lastMessage.isFromMe) "You: " else ""
                            prefix + com.seamless.android.net.TorRichChatCodec.friendlyPreviewText(
                                lastMessage.text,
                                lastMessage.attachmentMimeType
                            )
                        }
                        else -> contact.onionAddress.take(18) + "…"
                    }
                    Text(
                        text = previewText,
                        color = if (hasUnread) OnionUi.TextPrimary else OnionUi.TextSecondary,
                        fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasUnread) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(OnionUi.AccentBlue)
                        )
                    }
                }
            }

            if (!contact.requiresRendezvous() && trust != TorContactTrustState.BLOCKED) {
                Spacer(Modifier.width(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OnionUi.Online.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(OnionUi.Online.copy(alpha = 0.42f), OnionUi.Online.copy(alpha = 0.12f)),
                                    start = Offset.Zero,
                                    end = Offset.Infinite
                                ),
                                CircleShape
                            )
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                callHaptics.toggleOn(view)
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    callManager.startOutgoingCall(contact)
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                    ) {
                        Icon(
                            Icons.Rounded.Call,
                            contentDescription = "Voice Call ${contact.nickname}",
                            tint = OnionUi.Online,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(OnionUi.Info.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(OnionUi.Info.copy(alpha = 0.42f), OnionUi.Info.copy(alpha = 0.12f)),
                                    start = Offset.Zero,
                                    end = Offset.Infinite
                                ),
                                CircleShape
                            )
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                callHaptics.toggleOn(view)
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasMic && hasCam) {
                                    callManager.startOutgoingCall(contact, isVideoCall = true)
                                } else {
                                    val needed = buildList {
                                        if (!hasMic) add(Manifest.permission.RECORD_AUDIO)
                                        if (!hasCam) add(Manifest.permission.CAMERA)
                                    }.toTypedArray()
                                    videoPermLauncher.launch(needed)
                                }
                            }
                    ) {
                        Icon(
                            Icons.Rounded.Videocam,
                            contentDescription = "Video Call ${contact.nickname}",
                            tint = OnionUi.Info,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustBadgeIcon(state: TorContactTrustState) {
    when (state) {
        TorContactTrustState.VERIFIED -> Icon(
            Icons.Default.VerifiedUser,
            contentDescription = "Verified",
            tint = OnionUi.Online,
            modifier = Modifier.size(15.dp)
        )
        TorContactTrustState.KEY_CHANGED, TorContactTrustState.BLOCKED -> Icon(
            Icons.Default.Report,
            contentDescription = state.name,
            tint = OnionUi.Danger,
            modifier = Modifier.size(15.dp)
        )
        TorContactTrustState.UNVERIFIED -> Icon(
            Icons.Default.HourglassTop,
            contentDescription = "Unverified",
            tint = OnionUi.Warning,
            modifier = Modifier.size(14.dp)
        )
    }
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
            Text("Share Tor Contact", color = OnionUi.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                    publishError != null -> "Tor could not publish your onion address. Retry after checking network access."
                    else -> "Wait until Tor shows your onion address, then share."
                },
                color = OnionUi.TextSecondary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = OnionUi.Online, contentColor = Color(0xFF04130E))
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy", fontWeight = FontWeight.Bold)
                }
                Button(
                    enabled = hasRealOnion,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shareData)
                        context.startActivity(Intent.createChooser(intent, "Share contact"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnionUi.SurfacePressed, contentColor = OnionUi.TextPrimary)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share", fontWeight = FontWeight.SemiBold)
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var manualEntry by remember { mutableStateOf(false) }
    var manualValue by remember { mutableStateOf("") }
    var consumed by remember { mutableStateOf(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(previewView, hasCameraPermission) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(analyzerExecutor) { image ->
                    processQRCode(image) { value ->
                        if (!consumed) {
                            consumed = true
                            ContextCompat.getMainExecutor(context).execute { onScanResult(value) }
                        }
                    }
                }
            }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        } catch (_: Exception) {
            // The manual field remains available if another camera client owns the lens.
        }
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = OnionUi.Corner, color = OnionUi.Surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Scan Tor Contact", color = OnionUi.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))
                if (hasCameraPermission && !manualEntry) {
                    Box(Modifier.size(280.dp).clip(OnionUi.Corner).background(Color.Black)) {
                        AndroidView(
                            factory = { PreviewView(it).also { preview -> previewView = preview } },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "Point the camera at a Seamless contact QR",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        if (hasCameraPermission) "Paste a contact link instead." else "Camera permission is needed to scan. You can still paste a contact link.",
                        color = OnionUi.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualValue,
                        onValueChange = { manualValue = it },
                        label = { Text("Contact QR/link") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { manualEntry = !manualEntry }) {
                        Text(if (manualEntry) "Use camera" else "Paste link")
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    if (manualEntry) {
                        Button(onClick = { if (manualValue.isNotBlank()) onScanResult(manualValue.trim()) }) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}

private fun processQRCode(imageProxy: ImageProxy, onResult: (String) -> Unit) {
    try {
        val plane = imageProxy.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val bytes = ByteArray(imageProxy.width * imageProxy.height)
        val rowData = ByteArray(plane.rowStride)
        for (row in 0 until imageProxy.height) {
            buffer.position(row * plane.rowStride)
            buffer.get(rowData, 0, minOf(plane.rowStride, buffer.remaining()))
            for (column in 0 until imageProxy.width) {
                bytes[row * imageProxy.width + column] = rowData[column * plane.pixelStride]
            }
        }
        val width = imageProxy.width
        val height = imageProxy.height
        val source = com.google.zxing.PlanarYUVLuminanceSource(
            bytes, width, height, 0, 0, width, height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = MultiFormatReader().apply {
            setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }.decode(bitmap)
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
                    Text("Scan QR code")
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
