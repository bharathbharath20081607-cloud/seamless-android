@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.seamless.android.ui.call.rememberCallHaptics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seamless.android.data.TorMessageEntity
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorMessagingService
import com.seamless.android.net.TorRichChatCodec
import com.seamless.android.service.TorForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * OnionChatScreen - Direct P2P chat with a contact via Tor hidden services
 * 
 * Features:
 * - Message persistence to database
 * - Reliable offline message queuing
 * - End-to-end encryption with Double Ratchet
 * - Delivery status tracking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionChatScreen(
    contact: OnionContactManager.OnionContact,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // Services
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val messageRepository = remember { TorMessageRepository.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val serviceState by messagingService.serviceState.collectAsState()
    val torStatus by messagingService.torStatus.collectAsState()
    val connectedPeers by messagingService.connectedPeersFlow.collectAsState()
    val lanPeers by messagingService.lanPeersFlow.collectAsState()
    val typingPeers by messagingService.typingPeersFlow.collectAsState()
    val isPeerOnline = connectedPeers.contains(contact.onionAddress) || lanPeers.contains(contact.onionAddress)
    val isPeerTyping = typingPeers.contains(contact.onionAddress)
    val contactAddressReady = !contact.requiresRendezvous()
    
    // Messages from database
    val messagesFromDb by messageRepository.getMessagesForContact(contact).collectAsState(initial = emptyList())
    val directReactionsMap by messagingService.directReactionsFlow.collectAsState()
    val directEditedIds by messagingService.directEditedIdsFlow.collectAsState()
    val directDeletedIds by messagingService.directDeletedIdsFlow.collectAsState()
    val directBurnedIds by messagingService.directBurnedIdsFlow.collectAsState()
    val directShredReceipts by messagingService.directShredReceiptsFlow.collectAsState()
    
    // UI state
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var emojiEffectText by remember { mutableStateOf("") }
    var emojiEffectToken by remember { mutableIntStateOf(0) }
    var trustState by remember(contact.id) { mutableStateOf(contact.effectiveTrustState()) }
    var showTrustMenu by remember { mutableStateOf(false) }
    var startupRecoveryAttempted by remember(contact.id) { mutableStateOf(false) }
    var replyingTo by remember(contact.id) { mutableStateOf<TorMessageEntity?>(null) }
    var actionTargetMessage by remember(contact.id) { mutableStateOf<TorMessageEntity?>(null) }
    var editingMessage by remember(contact.id) { mutableStateOf<TorMessageEntity?>(null) }
    var editDraft by remember(contact.id) { mutableStateOf("") }
    var deleteConfirmMessage by remember(contact.id) { mutableStateOf<TorMessageEntity?>(null) }
    var burnOnReadArmed by remember(contact.id) { mutableStateOf(false) }
    var isRecordingVoice by remember(contact.id) { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSearchBar by remember(contact.id) { mutableStateOf(false) }
    var searchQuery by remember(contact.id) { mutableStateOf("") }
    var activeBurnTarget by remember(contact.id) { mutableStateOf<TorMessageEntity?>(null) }
    val callManager = remember { com.seamless.android.call.SeamlessCallManager.getInstance(context) }
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val callMicPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            callManager.startOutgoingCall(contact)
        } else {
            Toast.makeText(context, "Microphone permission is required for voice calls", Toast.LENGTH_SHORT).show()
        }
    }
    val callVideoPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val hasMic = grants[android.Manifest.permission.RECORD_AUDIO] == true ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCam = grants[android.Manifest.permission.CAMERA] == true ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            hasMic && hasCam -> callManager.startOutgoingCall(contact, isVideoCall = true)
            hasMic -> {
                Toast.makeText(context, "Camera permission denied — starting voice call", Toast.LENGTH_SHORT).show()
                callManager.startOutgoingCall(contact, isVideoCall = false)
            }
            else -> Toast.makeText(context, "Microphone & Camera permissions are required for video calls", Toast.LENGTH_SHORT).show()
        }
    }
    val voicePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isRecordingVoice = true
        } else {
            Toast.makeText(context, "Microphone permission is required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }
    val listState = rememberLazyListState()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || isSending) return@rememberLauncherForActivityResult
        if (trustState == TorContactTrustState.BLOCKED || trustState == TorContactTrustState.KEY_CHANGED) {
            Toast.makeText(context, "Verify this contact before sending media", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (!contactAddressReady) {
            Toast.makeText(context, "Ask them to share their ready Tor contact again", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val useViewOnce = burnOnReadArmed
        burnOnReadArmed = false
        scope.launch {
            isSending = true
            try {
                val prepared = withContext(Dispatchers.IO) { prepareOptimizedImageForTor(context, uri) }
                if (prepared == null) {
                    Toast.makeText(context, "Could not prepare this image", Toast.LENGTH_SHORT).show()
                } else {
                    messagingService.sendAttachment(
                        contactId = contact.id,
                        bytes = prepared.bytes,
                        mimeType = prepared.mimeType,
                        viewOnce = useViewOnce,
                        previewBytes = if (useViewOnce) null else prepared.previewBytes
                    ).onFailure { error ->
                        Toast.makeText(context, "Failed to queue photo: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isSending = false
            }
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || isSending) return@rememberLauncherForActivityResult
        if (trustState == TorContactTrustState.BLOCKED || trustState == TorContactTrustState.KEY_CHANGED) {
            Toast.makeText(context, "Verify this contact before sending files", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (!contactAddressReady) {
            Toast.makeText(context, "Ask them to share their ready Tor contact again", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val useViewOnce = burnOnReadArmed
        burnOnReadArmed = false
        scope.launch {
            isSending = true
            try {
                val preparedDoc = withContext(Dispatchers.IO) { prepareDocumentForTor(context, uri) }
                preparedDoc.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Unable to read file", Toast.LENGTH_SHORT).show()
                }.onSuccess { doc ->
                    messagingService.sendAttachment(
                        contactId = contact.id,
                        bytes = doc.bytes,
                        mimeType = doc.mimeType,
                        fileName = doc.fileName,
                        viewOnce = useViewOnce
                    ).onFailure { error ->
                        Toast.makeText(context, "Failed to queue file: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isSending = false
            }
        }
    }
    
    val chatBackground = OnionUi.Canvas
    
    // Start Tor for a fresh app process. If the first daemon startup fails,
    // make one clean delayed retry instead of leaving this chat permanently
    // offline or rapidly restarting a blocked Tor process.
    LaunchedEffect(serviceState) {
        when {
            serviceState == TorMessagingService.ServiceState.STOPPED -> {
                // This call happens from a visible screen, which is the safe
                // Android lifecycle point to acquire the persistent Tor session.
                // The foreground service owns sockets, mailbox hosting, and
                // recovery after the app itself moves to the background.
                TorForegroundService.start(context.applicationContext)
                messagingService.start()
            }
            serviceState == TorMessagingService.ServiceState.ERROR && !startupRecoveryAttempted -> {
                startupRecoveryAttempted = true
                kotlinx.coroutines.delay(5_000L)
                TorForegroundService.start(context.applicationContext)
                messagingService.start()
            }
        }
    }

    // Reopen an existing Tor chat as an active session rather than leaving the
    // prior app process's socket state to decide whether the peer looks online.
    LaunchedEffect(contact.id, serviceState) {
        if (serviceState == TorMessagingService.ServiceState.RUNNING && contactAddressReady) {
            messagingService.refreshPeerConnection(contact.id)
        }
    }

    DisposableEffect(contact.id, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    messagingService.setChatVisible(contact.id, true)
                    scope.launch { messagingService.markContactReadAndSendReceipts(contact) }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    messagingService.setTyping(contact.id, false)
                    messagingService.setChatVisible(contact.id, false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            messagingService.setChatVisible(contact.id, true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            messagingService.setMessageListener(null)
            messagingService.setTyping(contact.id, false)
            messagingService.setChatVisible(contact.id, false)
        }
    }
    
    // Mark messages as read & transmit emerald READ_RECEIPT(0x0B) when entering chat
    LaunchedEffect(contact.id) {
        messagingService.markContactReadAndSendReceipts(contact)
    }
    
    // Listen for incoming messages — mark read and transmit emerald READ_RECEIPT while visible
    LaunchedEffect(Unit) {
        messagingService.setMessageListener(object : TorMessagingService.MessageListener {
            override fun onMessageReceived(
                from: OnionContactManager.OnionContact,
                message: TorMessagingService.OnionMessage
            ) {
                if (from.onionAddress == contact.onionAddress) {
                    scope.launch {
                        messagingService.markContactReadAndSendReceipts(contact)
                    }
                }
            }
            
            override fun onDeliveryStatusChanged(
                messageId: String,
                status: OnionMessageProtocol.DeliveryState
            ) {
                // Delivery state is already updated by TorMessagingService.handleAck()
                // No action needed here — the DB Flow will auto-refresh the UI
            }
            
            override fun onPeerOnline(contact: OnionContactManager.OnionContact) {}
            override fun onPeerOffline(contact: OnionContactManager.OnionContact) {}
        })
    }
    
    // Auto-scroll when new messages arrive
    LaunchedEffect(messagesFromDb.size) {
        if (messagesFromDb.isNotEmpty()) {
            listState.animateScrollToItem(messagesFromDb.lastIndex)
        }
    }
    
    val glassColor = if (androidx.compose.foundation.isSystemInDarkTheme()) 
        OnionUi.CanvasRaised
    else 
        Color.White.copy(alpha = 0.98f)

    // Disappearing messages state
    val privacyManager = com.seamless.android.ui.privacy.PrivacyPreferenceManager
    var disappearingTtl by remember { mutableStateOf(privacyManager.disappearingTtlMs.value) }
    var showTimerMenu by remember { mutableStateOf(false) }
    val ttlOptions = listOf(
        0L to "Off",
        5 * 60_000L to "5 minutes",
        60 * 60_000L to "1 hour",
        24 * 60 * 60_000L to "24 hours",
        7 * 24 * 60 * 60_000L to "7 days"
    )

    // Periodically cleanup expired disappearing messages
    LaunchedEffect(disappearingTtl) {
        if (disappearingTtl > 0) {
            while (true) {
                val cutoff = System.currentTimeMillis() - disappearingTtl
                messageRepository.cleanupOldMessages(cutoff)
                kotlinx.coroutines.delay(30_000L) // Check every 30 seconds
            }
        }
    }

    androidx.activity.compose.BackHandler {
        when {
            activeBurnTarget != null -> activeBurnTarget = null
            isRecordingVoice -> isRecordingVoice = false
            editingMessage != null -> editingMessage = null
            deleteConfirmMessage != null -> deleteConfirmMessage = null
            actionTargetMessage != null -> actionTargetMessage = null
            showSearchBar -> { showSearchBar = false; searchQuery = "" }
            replyingTo != null -> replyingTo = null
            showTrustMenu -> showTrustMenu = false
            else -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 6.dp,
                color = Color.Transparent
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar with per-peer online indicator
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isPeerOnline) OnionUi.Accent else OnionUi.AccentStrong),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.nickname.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // Online dot
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isPeerOnline) Color(0xFF00E676) else Color(0xFF666666)
                                        )
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    contact.nickname,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val transportTier = messagingService.getActiveTransportTier(contact.onionAddress)
                                    val (peerStatusText, peerStatusColor) = when {
                                        isPeerTyping -> "Typing" to OnionUi.Accent
                                        !contactAddressReady -> "Address needs update" to OnionUi.Warning
                                        isPeerOnline && (lanPeers.contains(contact.onionAddress) ||
                                            transportTier == TorMessagingService.ActiveTransportTier.PHANTOM_LAN) ->
                                            "⚡ Phantom-LAN" to OnionUi.Online
                                        isPeerOnline && transportTier == TorMessagingService.ActiveTransportTier.WARP_DUAL_TOR ->
                                            "🚀 Dual-Warp" to OnionUi.Online
                                        isPeerOnline -> "Connected" to OnionUi.Online
                                        serviceState == TorMessagingService.ServiceState.RUNNING &&
                                            !contact.mailboxOnion.isNullOrEmpty() -> "Mailbox ready" to OnionUi.Warning
                                        serviceState == TorMessagingService.ServiceState.RUNNING -> "Tor ready" to OnionUi.Accent
                                        serviceState == TorMessagingService.ServiceState.WAITING_FOR_TOR -> "Connecting ${torStatus.bootstrapPercent}%" to OnionUi.Warning
                                        serviceState == TorMessagingService.ServiceState.STARTING -> "Starting" to OnionUi.Warning
                                        serviceState == TorMessagingService.ServiceState.ERROR -> "Connection error" to OnionUi.Danger
                                        else -> "Offline" to OnionUi.TextSecondary
                                    }
                                    OnionStatusChip(peerStatusText, peerStatusColor)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        val callReady = contactAddressReady && trustState != TorContactTrustState.BLOCKED
                        IconButton(onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) searchQuery = ""
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search messages",
                                tint = if (showSearchBar) OnionUi.Online else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Encrypted P2P + Tor Voice Call Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (callReady) Color(0x1E34D399) else Color.Transparent)
                                .then(
                                    if (callReady) {
                                        Modifier.border(0.9.dp, Color(0x4D34D399), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    when {
                                        trustState == TorContactTrustState.BLOCKED -> {
                                            Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
                                        }
                                        trustState == TorContactTrustState.KEY_CHANGED -> {
                                            Toast.makeText(context, "Verify this contact before calling", Toast.LENGTH_SHORT).show()
                                        }
                                        !contactAddressReady -> {
                                            Toast.makeText(context, "Ask them to share their ready Tor contact first", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {
                                            callHaptics.toggleOn(view)
                                            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasMic) {
                                                callManager.startOutgoingCall(contact)
                                            } else {
                                                callMicPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    }
                                }
                        ) {
                            Icon(
                                Icons.Rounded.Call,
                                contentDescription = "Encrypted Voice Call",
                                tint = if (callReady) {
                                    Color(0xFF34D399)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Encrypted HD P2P + Tor Video Call Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (callReady) Color(0x1E38BDF8) else Color.Transparent)
                                .then(
                                    if (callReady) {
                                        Modifier.border(0.9.dp, Color(0x4D38BDF8), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    when {
                                        trustState == TorContactTrustState.BLOCKED -> {
                                            Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
                                        }
                                        trustState == TorContactTrustState.KEY_CHANGED -> {
                                            Toast.makeText(context, "Verify this contact before calling", Toast.LENGTH_SHORT).show()
                                        }
                                        !contactAddressReady -> {
                                            Toast.makeText(context, "Ask them to share their ready Tor contact first", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {
                                            callHaptics.toggleOn(view)
                                            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.CAMERA
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasMic && hasCam) {
                                                callManager.startOutgoingCall(contact, isVideoCall = true)
                                            } else {
                                                val needed = buildList {
                                                    if (!hasMic) add(android.Manifest.permission.RECORD_AUDIO)
                                                    if (!hasCam) add(android.Manifest.permission.CAMERA)
                                                }.toTypedArray()
                                                callVideoPermLauncher.launch(needed)
                                            }
                                        }
                                    }
                                }
                        ) {
                            Icon(
                                Icons.Rounded.Videocam,
                                contentDescription = "Encrypted HD Video Call",
                                tint = if (callReady) {
                                    Color(0xFF38BDF8)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box {
                            IconButton(onClick = { showTrustMenu = true }) {
                                Icon(
                                    when (trustState) {
                                        TorContactTrustState.VERIFIED -> Icons.Default.VerifiedUser
                                        TorContactTrustState.KEY_CHANGED -> Icons.Default.Report
                                        TorContactTrustState.BLOCKED -> Icons.Default.Block
                                        TorContactTrustState.UNVERIFIED -> Icons.Default.GppMaybe
                                    },
                                    contentDescription = "Contact trust",
                                    tint = when (trustState) {
                                        TorContactTrustState.VERIFIED -> Color(0xFF00D4AA)
                                        TorContactTrustState.KEY_CHANGED -> Color(0xFFFF5252)
                                        TorContactTrustState.BLOCKED -> Color(0xFFFF5252)
                                        TorContactTrustState.UNVERIFIED -> Color(0xFFFFC857)
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = showTrustMenu,
                                onDismissRequest = { showTrustMenu = false }
                            ) {
                                Text(
                                    "Safety Number",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                Text(
                                    remember(contact.id) {
                                        contact.safetyNumber(messagingService.getMyContactInfo().publicKey)
                                    },
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                DropdownMenuItem(
                                    text = { Text("Mark verified") },
                                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                    onClick = {
                                        contactManager.verifyContact(contact.id)
                                        trustState = TorContactTrustState.VERIFIED
                                        showTrustMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Mark unverified") },
                                    leadingIcon = { Icon(Icons.Default.GppMaybe, contentDescription = null) },
                                    onClick = {
                                        contactManager.markUnverified(contact.id)
                                        trustState = TorContactTrustState.UNVERIFIED
                                        showTrustMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Block contact") },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                    onClick = {
                                        contactManager.blockContact(contact.id)
                                        trustState = TorContactTrustState.BLOCKED
                                        showTrustMenu = false
                                    }
                                )
                            }
                        }

                        // Disappearing Messages Timer
                        Box {
                            IconButton(onClick = { showTimerMenu = true }) {
                                Icon(
                                    if (disappearingTtl > 0L) Icons.Default.Timer else Icons.Default.TimerOff,
                                    contentDescription = "Disappearing Messages",
                                    tint = if (disappearingTtl > 0L) Color(0xFF00D4AA) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            DropdownMenu(
                                expanded = showTimerMenu,
                                onDismissRequest = { showTimerMenu = false }
                            ) {
                                Text(
                                    "Disappearing Messages",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                ttlOptions.forEach { (ttl, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(label)
                                                if (ttl == disappearingTtl) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = Color(0xFF00D4AA)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            disappearingTtl = ttl
                                            privacyManager.setDisappearingTtl(context, ttl)
                                            showTimerMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = glassColor
                    )
                )
            }
        },
        bottomBar = {
            // Modern glassmorphic composer with Quoted Reply, Burn-On-Read 🔥 toggle, + Attachment Sheet, and Sonic-Warp Mic
            val inputGlassColor = if (androidx.compose.foundation.isSystemInDarkTheme()) OnionUi.CanvasRaised else Color.White
            var isFocused by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .imePadding()
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                AnimatedVisibility(visible = replyingTo != null) {
                    replyingTo?.let { target ->
                        val preview = remember(target.text, target.attachmentMimeType) {
                            TorRichChatCodec.friendlyPreviewText(target.text, target.attachmentMimeType)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OnionUi.Surface)
                                .border(1.dp, OnionUi.Hairline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(OnionUi.Online)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${if (target.isFromMe) "yourself" else contact.nickname}",
                                    color = OnionUi.Online,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preview,
                                    color = OnionUi.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Close, "Cancel reply", tint = OnionUi.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (isRecordingVoice) {
                    SonicWarpRecorderBar(
                        burnOnReadArmed = burnOnReadArmed,
                        onCancel = { isRecordingVoice = false },
                        onSendVoiceNote = { bytes, durationMs, fileName ->
                            isRecordingVoice = false
                            val useViewOnce = burnOnReadArmed
                            burnOnReadArmed = false
                            scope.launch {
                                isSending = true
                                try {
                                    messagingService.sendAttachment(
                                        contactId = contact.id,
                                        bytes = bytes,
                                        mimeType = "audio/mp4",
                                        fileName = fileName,
                                        durationMs = durationMs,
                                        viewOnce = useViewOnce
                                    ).onFailure { error ->
                                        Toast.makeText(context, "Voice note failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (isFocused) 8.dp else 4.dp,
                                shape = RoundedCornerShape(20.dp),
                                spotColor = if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.35f)
                                else if (isFocused) OnionUi.AccentStrong.copy(alpha = 0.32f)
                                else Color.Black.copy(alpha = 0.18f)
                            )
                            .background(color = inputGlassColor, shape = RoundedCornerShape(20.dp))
                            .border(
                                width = if (burnOnReadArmed || isFocused) 1.5.dp else 1.dp,
                                brush = SolidColor(
                                    when {
                                        burnOnReadArmed -> AegisEmberColor.copy(alpha = 0.85f)
                                        isFocused -> OnionUi.AccentStrong
                                        androidx.compose.foundation.isSystemInDarkTheme() -> Color.White.copy(alpha = 0.15f)
                                        else -> Color.Black.copy(alpha = 0.1f)
                                    }
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                messagingService.speculativePrewarmBulk(contact.id)
                                showAttachmentSheet = true
                            },
                            enabled = !isSending && trustState != TorContactTrustState.BLOCKED && contactAddressReady,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Attachments",
                                tint = if (isSending) Color.White.copy(alpha = 0.35f) else OnionUi.Accent
                            )
                        }

                        IconButton(
                            onClick = { burnOnReadArmed = !burnOnReadArmed },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = "Toggle Burn-On-Read",
                                tint = if (burnOnReadArmed) AegisEmberColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        androidx.compose.foundation.text.BasicTextField(
                            value = messageText,
                            onValueChange = {
                                messageText = it
                                messagingService.setTyping(contact.id, it.isNotBlank())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (!it.isFocused) messagingService.setTyping(contact.id, false)
                                },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(if (burnOnReadArmed) AegisEmberColor else Color(0xFF00C6FF)),
                            decorationBox = { innerTextField ->
                                if (messageText.isEmpty()) {
                                    Text(
                                        if (burnOnReadArmed) "View-Once secret message…" else "Message…",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                        color = if (burnOnReadArmed) AegisEmberColor.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (messageText.isBlank()) {
                            FloatingActionButton(
                                onClick = {
                                    when {
                                        trustState == TorContactTrustState.BLOCKED ->
                                            Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
                                        trustState == TorContactTrustState.KEY_CHANGED ->
                                            Toast.makeText(context, "Verify this contact before recording", Toast.LENGTH_SHORT).show()
                                        !contactAddressReady ->
                                            Toast.makeText(context, "Ask them to share again after Tor is ready", Toast.LENGTH_SHORT).show()
                                        !isSending -> {
                                            messagingService.speculativePrewarmBulk(contact.id)
                                            val hasMic = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasMic) isRecordingVoice = true
                                            else voicePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier.size(44.dp),
                                containerColor = if (burnOnReadArmed) AegisEmberColor else OnionUi.Online,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Mic, contentDescription = "Record voice note", tint = Color.Black)
                                }
                            }
                        } else {
                            FloatingActionButton(
                                onClick = {
                                    if (trustState == TorContactTrustState.BLOCKED) {
                                        Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
                                    } else if (trustState == TorContactTrustState.KEY_CHANGED) {
                                        Toast.makeText(context, "Verify this contact before sending", Toast.LENGTH_SHORT).show()
                                    } else if (!contactAddressReady) {
                                        Toast.makeText(context, "Ask them to share again after Tor is ready", Toast.LENGTH_SHORT).show()
                                    } else if (messageText.isNotBlank() && !isSending) {
                                        val text = messageText
                                        val replyId = replyingTo?.id
                                        val useBurn = burnOnReadArmed
                                        messageText = ""
                                        replyingTo = null
                                        burnOnReadArmed = false
                                        emojiEffectText = if (useBurn) "🔥" else text
                                        emojiEffectToken++
                                        messagingService.setTyping(contact.id, false)
                                        isSending = true
                                        scope.launch {
                                            try {
                                                messagingService.sendMessage(
                                                    contactId = contact.id,
                                                    text = text,
                                                    replyToId = replyId,
                                                    burnOnRead = useBurn
                                                ).onFailure { error ->
                                                    Toast.makeText(context, "Failed to send: ${error.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isSending = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(44.dp),
                                containerColor = if (burnOnReadArmed) AegisEmberColor else OnionUi.AccentStrong,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        val displayedMessages = remember(messagesFromDb, searchQuery) {
            if (searchQuery.isBlank()) messagesFromDb
            else messagesFromDb.filter { msg ->
                val searchable = TorRichChatCodec.friendlyPreviewText(msg.text, msg.attachmentMimeType)
                searchable.contains(searchQuery, ignoreCase = true)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chatBackground)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = showSearchBar) {
                    InChatSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        matchCount = displayedMessages.size,
                        onClose = { showSearchBar = false; searchQuery = "" }
                    )
                }

                if (displayedMessages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = OnionUi.Accent.copy(alpha = 0.56f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "No matching messages" else "End-to-End Encrypted",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "Try another keyword in this encrypted chat."
                            else "Messages, voice notes, 1 MB files & View-Once capsules are E2EE over Tor.",
                            color = OnionUi.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            StatusIndicator(serviceState, torStatus, contactAddressReady, isPeerOnline)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedMessages, key = { it.id }) { message ->
                            TorMessageBubble(
                                message = message,
                                contactNickname = contact.nickname,
                                isEdited = message.id in directEditedIds,
                                isDeleted = message.id in directDeletedIds ||
                                    TorRichChatCodec.parseReply(message.text).body == TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT,
                                isBurned = message.id in directBurnedIds ||
                                    TorRichChatCodec.parseReply(message.text).body == TorRichChatCodec.BURNED_CAPSULE_TEXT,
                                hasShredReceipt = message.id in directShredReceipts,
                                reactions = directReactionsMap[message.id].orEmpty(),
                                onLongPress = { actionTargetMessage = message },
                                onQuotedMessageClick = { targetId ->
                                    val idx = displayedMessages.indexOfFirst { it.id == targetId }
                                    if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                                },
                                onReactionClick = { emoji ->
                                    emojiEffectText = emoji
                                    emojiEffectToken++
                                    scope.launch { messagingService.sendDirectReaction(contact.id, message.id, emoji) }
                                },
                                onOpenBurnCapsule = { activeBurnTarget = message }
                            )
                        }
                    }
                }
            }
            TorEmojiBurst(emojiEffectToken, emojiEffectText)
        }
    }

    if (showAttachmentSheet) {
        OmniAttachmentSheet(
            burnOnReadArmed = burnOnReadArmed,
            onToggleBurnOnRead = { burnOnReadArmed = it },
            onPickPhoto = {
                messagingService.speculativePrewarmBulk(contact.id)
                photoPicker.launch("image/*")
            },
            onStartVoiceNote = {
                messagingService.speculativePrewarmBulk(contact.id)
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMic) isRecordingVoice = true
                else voicePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onPickDocument = {
                messagingService.speculativePrewarmBulk(contact.id)
                documentPicker.launch(arrayOf("*/*"))
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    actionTargetMessage?.let { target ->
        val cleanBody = TorRichChatCodec.parseReply(target.text).body
        val isDeleted = target.id in directDeletedIds || cleanBody == TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT
        val isBurned = target.id in directBurnedIds || cleanBody == TorRichChatCodec.BURNED_CAPSULE_TEXT
        val isViewOnce = TorRichChatCodec.isUnburnedSecretText(target.text) ||
            (TorRichChatCodec.parseAttachmentDescriptor(target.text)?.viewOnce == true)
        val canEdit = target.isFromMe && target.attachmentPath == null && !isDeleted && !isBurned && !isViewOnce
        val canCopy = !isDeleted && !isBurned && !isViewOnce
        DirectMessageActionSheet(
            onDismiss = { actionTargetMessage = null },
            onReact = if (!isDeleted) { emoji ->
                actionTargetMessage = null
                emojiEffectText = emoji
                emojiEffectToken++
                scope.launch { messagingService.sendDirectReaction(contact.id, target.id, emoji) }
            } else null,
            onReply = {
                actionTargetMessage = null
                replyingTo = target
            },
            onCopy = if (canCopy) {
                {
                    actionTargetMessage = null
                    val copyText = TorRichChatCodec.friendlyPreviewText(target.text, target.attachmentMimeType)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Message", copyText))
                    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onEdit = if (canEdit) {
                {
                    actionTargetMessage = null
                    editDraft = cleanBody
                    editingMessage = target
                }
            } else null,
            onDelete = if (!isDeleted && target.isFromMe) {
                {
                    actionTargetMessage = null
                    deleteConfirmMessage = target
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
                            messagingService.editDirectMessage(contact.id, target.id, updatedText)
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
                    "Removes this message on both devices and shreds any local attachment.",
                    color = OnionUi.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmMessage = null
                    scope.launch {
                        messagingService.deleteDirectMessageForEveryone(contact.id, target.id)
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

    activeBurnTarget?.let { target ->
        val burnText = remember(target.text) { TorRichChatCodec.parseBurnText(target.text) }
        val file = target.attachmentPath?.let(::File)?.takeIf { it.exists() }
        AegisBurnViewerDialog(
            senderLabel = contact.nickname,
            secretText = burnText?.secretText,
            attachmentFile = file,
            attachmentMimeType = target.attachmentMimeType,
            ttlSeconds = burnText?.ttlSeconds ?: 12,
            onBurnAndShred = {
                scope.launch { messagingService.consumeAndShredDirectCapsule(contact.id, target.id) }
            },
            onDismiss = { activeBurnTarget = null }
        )
    }
}

@Composable
private fun getStatusText(
    serviceState: TorMessagingService.ServiceState,
    torStatus: TorManager.TorStatus
): String {
    return when (serviceState) {
        TorMessagingService.ServiceState.RUNNING -> "🔒 Secure Connection"
        TorMessagingService.ServiceState.WAITING_FOR_TOR -> "🧅 Connecting to Tor (${torStatus.bootstrapPercent}%)"
        TorMessagingService.ServiceState.STARTING -> "🔄 Starting..."
        TorMessagingService.ServiceState.RENDEZVOUS -> "🤝 Rendezvous..."
        TorMessagingService.ServiceState.ERROR -> "❌ Connection Error"
        TorMessagingService.ServiceState.STOPPED -> "⚫ Offline"
    }
}

@Composable
private fun StatusIndicator(
    serviceState: TorMessagingService.ServiceState,
    torStatus: TorManager.TorStatus,
    contactAddressReady: Boolean,
    isPeerOnline: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A3E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                !contactAddressReady -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFC857),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Contact address missing", color = Color.White, fontSize = 12.sp)
                }
                isPeerOnline -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00D4AA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Direct connection verified", color = Color.White, fontSize = 12.sp)
                }
                serviceState == TorMessagingService.ServiceState.RUNNING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF00D4AA),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Securing direct connection", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                serviceState == TorMessagingService.ServiceState.WAITING_FOR_TOR -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF6366F1),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connecting to Tor network... ${torStatus.bootstrapPercent}%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                serviceState == TorMessagingService.ServiceState.STARTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF6366F1),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Initializing...", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                else -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF5555),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Not connected", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TorMessageBubble(
    message: TorMessageEntity,
    contactNickname: String,
    isEdited: Boolean,
    isDeleted: Boolean,
    isBurned: Boolean,
    hasShredReceipt: Boolean,
    reactions: Map<String, String>,
    onLongPress: () -> Unit,
    onQuotedMessageClick: (String) -> Unit,
    onReactionClick: (String) -> Unit,
    onOpenBurnCapsule: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var previewPath by remember(message.id, message.attachmentPath) { mutableStateOf<String?>(null) }
    val deliveryState = try {
        OnionMessageProtocol.DeliveryState.valueOf(message.deliveryState)
    } catch (e: Exception) {
        OnionMessageProtocol.DeliveryState.PENDING
    }

    val parsed = remember(message.text) { TorRichChatCodec.parseReply(message.text) }
    val burnText = remember(message.text) { TorRichChatCodec.parseBurnText(message.text) }
    val descriptor = remember(message.text, message.attachmentMimeType, message.attachmentPath) {
        TorRichChatCodec.inferDescriptor(message.text, message.attachmentMimeType, message.attachmentPath)
    }
    val attachment = message.attachmentPath?.let(::File)?.takeIf { it.exists() }
    val isStreamingPreview = attachment?.name?.startsWith("preview_") == true
    val isViewOnce = burnText != null || descriptor?.viewOnce == true || isBurned

    val routeIcon = when (message.deliveryRoute) {
        "MAILBOX" -> "📬"
        "QUEUED" -> "🕒"
        "BLUETOOTH" -> "📶"
        "WIFI_DIRECT" -> "📡"
        else -> "🔗"
    }
    val routeLabel = when (message.deliveryRoute) {
        "MAILBOX" -> "via Mailbox"
        "QUEUED" -> "Queued"
        "BLUETOOTH" -> "via Bluetooth"
        "WIFI_DIRECT" -> "via WiFi Direct"
        else -> "Direct"
    }

    val shape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = if (message.isFromMe) 22.dp else 5.dp,
        bottomEnd = if (message.isFromMe) 5.dp else 22.dp
    )
    val bubbleBrush = if (message.isFromMe) {
        Brush.linearGradient(listOf(OnionUi.OutgoingStart, OnionUi.OutgoingEnd))
    } else {
        Brush.linearGradient(listOf(OnionUi.IncomingStart, OnionUi.IncomingEnd))
    }
    val bubbleOutline = if (message.isFromMe) Color.White.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.10f)
    var appeared by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { appeared = true }
    val bubbleAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(180), label = "bubble_alpha")
    val bubbleOffset by animateFloatAsState(if (appeared) 0f else 18f, tween(220), label = "bubble_offset")
    val bubbleScale by animateFloatAsState(if (appeared) 1f else 0.96f, tween(220), label = "bubble_scale")

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 336.dp)
                .graphicsLayer {
                    alpha = bubbleAlpha
                    translationY = bubbleOffset
                    scaleX = bubbleScale
                    scaleY = bubbleScale
                }
                .shadow(
                    elevation = if (message.isFromMe) 6.dp else 2.dp,
                    shape = shape,
                    spotColor = Color.Black.copy(alpha = 0.34f)
                )
                .clip(shape)
                .background(brush = bubbleBrush, shape = shape)
                .border(1.dp, bubbleOutline, shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongPress() }
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (!isDeleted && !parsed.replyToSender.isNullOrBlank()) {
                    val replyId = parsed.replyToId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (message.isFromMe) Color.Black.copy(alpha = 0.25f) else OnionUi.SurfacePressed)
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
                                .background(OnionUi.Online)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = parsed.replyToSender,
                                color = OnionUi.Online,
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

                when {
                    isDeleted -> {
                        Text(
                            text = TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT,
                            color = Color.White.copy(alpha = 0.6f),
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
                        val capsuleReady = burnText != null || (!isStreamingPreview && attachment != null)
                        AegisBurnCapsuleCard(
                            kindLabel = kindLabel,
                            isFromMe = message.isFromMe,
                            isBurned = isBurned,
                            isReady = capsuleReady,
                            hasShredReceipt = hasShredReceipt,
                            onOpenCapsule = onOpenBurnCapsule
                        )
                    }
                    descriptor?.kind == "voice" -> {
                        OnionVoiceNoteCard(
                            filePath = attachment?.absolutePath,
                            durationMsHint = descriptor.durationMs,
                            outgoing = message.isFromMe
                        )
                    }
                    descriptor?.kind == "file" -> {
                        OnionDocumentCard(
                            filePath = attachment?.absolutePath,
                            fileName = descriptor.fileName,
                            totalBytes = descriptor.totalBytes,
                            mimeType = message.attachmentMimeType,
                            outgoing = message.isFromMe
                        )
                    }
                    else -> {
                        val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                            initialValue = null,
                            key1 = attachment?.absolutePath
                        ) {
                            value = attachment?.absolutePath?.let { path ->
                                withContext(Dispatchers.IO) { decodeScaledFileBitmap(path, maxDimensionPx = 640) }
                            }
                        }
                        if (bitmap != null) {
                            Box {
                                Image(
                                    bitmap = bitmap!!,
                                    contentDescription = "Open photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .then(
                                            if (isStreamingPreview) {
                                                Modifier.widthIn(min = 180.dp, max = 276.dp).heightIn(min = 140.dp, max = 300.dp)
                                            } else {
                                                Modifier.widthIn(max = 276.dp).heightIn(max = 300.dp)
                                            }
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (!isStreamingPreview) {
                                                Modifier.clickable { previewPath = attachment?.absolutePath }
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
                        } else {
                            Text(
                                text = parsed.body,
                                color = Color.White,
                                fontSize = if (isExpressiveEmoji(parsed.body)) 30.sp else 15.sp,
                                lineHeight = if (isExpressiveEmoji(parsed.body)) 36.sp else 21.sp
                            )
                        }
                    }
                }

                if (message.attachmentPath != null && message.isFromMe &&
                    deliveryState !in setOf(
                        OnionMessageProtocol.DeliveryState.DELIVERED,
                        OnionMessageProtocol.DeliveryState.READ,
                        OnionMessageProtocol.DeliveryState.FAILED
                    )
                ) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = OnionUi.Accent,
                        trackColor = Color.White.copy(alpha = 0.16f)
                    )
                    Text(
                        "Securing encrypted attachment…",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (!isDeleted && isExpressiveEmoji(parsed.body)) 1.dp else 5.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (isEdited && !isDeleted && !isBurned) {
                        Text(
                            text = "edited · ",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    if (message.isFromMe && message.deliveryRoute != "DIRECT") {
                        Text(
                            text = "$routeIcon ",
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp
                    )

                    if (message.isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val (statusIcon, statusColor) = getDeliveryIcon(deliveryState)
                        Icon(
                            statusIcon,
                            contentDescription = routeLabel,
                            modifier = Modifier.size(14.dp),
                            tint = statusColor
                        )
                    }
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

    previewPath?.let { path ->
        PhotoPreviewDialog(
            file = File(path),
            onDismiss = { previewPath = null },
            onSave = {
                scope.launch {
                    savePhotoToGallery(context, File(path), message.attachmentMimeType ?: "image/jpeg")
                        .onSuccess {
                            Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { error ->
                            Toast.makeText(context, "Could not save photo: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        )
    }
}

@Composable
private fun PhotoPreviewDialog(
    file: File,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = file.absolutePath
    ) {
        value = withContext(Dispatchers.IO) { decodeScaledFileBitmap(file.absolutePath, maxDimensionPx = 2048) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF15191F),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Photo preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview", tint = Color.White)
                }
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Save photo", tint = Color.White)
                }
            }
        }
    }
}

private suspend fun savePhotoToGallery(
    context: android.content.Context,
    source: File,
    mimeType: String
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        require(source.exists()) { "Photo is no longer available" }
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Seamless_${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Seamless")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val destination = resolver.insert(collection, values) ?: error("Media library is unavailable")
        try {
            resolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open saved photo")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(destination, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }
}

internal data class PreparedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val previewBytes: ByteArray? = null
)

internal fun decodeScaledFileBitmap(
    path: String,
    maxDimensionPx: Int = 1280
): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return@runCatching BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    var sampleSize = 1
    var longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / 2 >= maxDimensionPx) {
        sampleSize *= 2
        longest /= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}.getOrNull()

private fun decodeBitmapForTor(context: android.content.Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val decodedP = runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > 1280 && w > 0 && h > 0) {
                    val ratio = 1280f / longest.toFloat()
                    decoder.setTargetSize(
                        (w * ratio).toInt().coerceAtLeast(1),
                        (h * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        }.onFailure {
            Log.d("OnionChatScreen", "ImageDecoder fallback to BitmapFactory for $uri: ${it.message}")
        }.getOrNull()
        if (decodedP != null && decodedP.width > 0 && decodedP.height > 0) {
            return decodedP
        }
    }

    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsOpened = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, boundsOpts)
        true
    } ?: false
    if (!boundsOpened) return null

    val srcWidth = boundsOpts.outWidth
    val srcHeight = boundsOpts.outHeight
    var sampleSize = 1
    if (srcWidth > 0 && srcHeight > 0) {
        var halfMax = maxOf(srcWidth, srcHeight) / 2
        while (halfMax >= 1280) {
            sampleSize *= 2
            halfMax /= 2
        }
    }

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var decoded = if (srcWidth > 0 && srcHeight > 0) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    } else {
        null
    }

    if (decoded == null) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (raw != null && raw.isNotEmpty()) {
            decoded = BitmapFactory.decodeByteArray(
                raw,
                0,
                raw.size,
                if (srcWidth > 0 && srcHeight > 0) decodeOpts else null
            )
        }
    }
    if (decoded == null || decoded.width <= 0 || decoded.height <= 0) return null

    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val rotatedDims = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE -> decoded.height to decoded.width
        else -> decoded.width to decoded.height
    }
    val scale = minOf(1f, 1280f / maxOf(rotatedDims.first, rotatedDims.second).toFloat())
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
    }
    if (scale < 1f) {
        matrix.postScale(scale, scale)
    }

    return if (!matrix.isIdentity) {
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        transformed
    } else {
        decoded
    }
}

internal fun prepareOptimizedImageForTor(context: android.content.Context, uri: Uri): PreparedImage? {
    var bitmap = runCatching { decodeBitmapForTor(context, uri) }
        .onFailure { Log.w("OnionChatScreen", "prepareOptimizedImageForTor failed for $uri: ${it.message}") }
        .getOrNull()
        ?: run {
            Log.w("OnionChatScreen", "prepareOptimizedImageForTor failed for $uri: decode returned null")
            return null
        }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        val soft = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (!bitmap.isRecycled) bitmap.recycle()
        if (soft == null) {
            Log.w("OnionChatScreen", "prepareOptimizedImageForTor failed for $uri: software bitmap copy failed")
            return null
        }
        bitmap = soft
    }

    val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }
    val targetWebpBudgets = intArrayOf(160 * 1024, 220 * 1024, com.seamless.android.net.TorAttachmentTransfer.MAX_IMAGE_BYTES)
    val maxBytes = com.seamless.android.net.TorAttachmentTransfer.MAX_IMAGE_BYTES

    val previewBytes: ByteArray? = runCatching {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val maxEdge = maxOf(srcW, srcH).coerceAtLeast(1)
        val ratio = 48f / maxEdge.toFloat()
        val pw = (srcW * ratio).toInt().coerceIn(8, 48)
        val ph = (srcH * ratio).toInt().coerceIn(8, 48)
        val tiny = Bitmap.createScaledBitmap(bitmap, pw, ph, true)
        try {
            var outBytes: ByteArray? = null
            for (q in intArrayOf(34, 22, 14)) {
                val bos = ByteArrayOutputStream()
                if (tiny.compress(webpFormat, q, bos)) {
                    val candidate = bos.toByteArray()
                    if (candidate.size in 1..1_000) {
                        outBytes = candidate
                        break
                    }
                }
            }
            outBytes
        } finally {
            if (tiny !== bitmap && !tiny.isRecycled) tiny.recycle()
        }
    }.getOrNull()

    try {
        // Primary: WebP quality ramp across 3 scale passes (~3x smaller over Tor than JPEG).
        for (pass in targetWebpBudgets.indices) {
            val cap = targetWebpBudgets[pass]
            for (quality in intArrayOf(78, 66, 54, 42)) {
                val output = ByteArrayOutputStream()
                if (bitmap.compress(webpFormat, quality, output)) {
                    val bytes = output.toByteArray()
                    if (bytes.size in 1..cap) {
                        return PreparedImage(bytes, "image/webp", previewBytes)
                    }
                }
            }
            if (pass < targetWebpBudgets.lastIndex) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.75f).toInt().coerceAtLeast(1),
                    (bitmap.height * 0.75f).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                bitmap = scaled
            }
        }

        // Fallback: JPEG across 2 passes if device WebP codec rejected or exceeded budget.
        repeat(2) { pass ->
            for (quality in intArrayOf(78, 66, 54, 42, 32)) {
                val output = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    val bytes = output.toByteArray()
                    if (bytes.size in 1..maxBytes) {
                        return PreparedImage(bytes, "image/jpeg", previewBytes)
                    }
                }
            }
            if (pass == 0) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.75f).toInt().coerceAtLeast(1),
                    (bitmap.height * 0.75f).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                bitmap = scaled
            }
        }
        Log.w("OnionChatScreen", "prepareOptimizedImageForTor failed for $uri: compressed image exceeded ${maxBytes}B")
        return null
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal fun prepareImageForTor(context: android.content.Context, uri: Uri): ByteArray? =
    prepareOptimizedImageForTor(context, uri)?.bytes

private fun getDeliveryIcon(state: OnionMessageProtocol.DeliveryState): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when (state) {
        OnionMessageProtocol.DeliveryState.PENDING -> Icons.Default.Schedule to Color.White.copy(alpha = 0.5f)
        OnionMessageProtocol.DeliveryState.SENDING -> Icons.Default.Schedule to Color.White.copy(alpha = 0.6f)
        OnionMessageProtocol.DeliveryState.SENT -> Icons.Default.Done to Color.White.copy(alpha = 0.7f)
        OnionMessageProtocol.DeliveryState.DELIVERED -> Icons.Default.DoneAll to Color.White.copy(alpha = 0.8f)
        OnionMessageProtocol.DeliveryState.READ -> Icons.Default.DoneAll to Color(0xFF00D4AA)
        OnionMessageProtocol.DeliveryState.FAILED -> Icons.Default.Error to Color(0xFFFF5555)
    }
}
