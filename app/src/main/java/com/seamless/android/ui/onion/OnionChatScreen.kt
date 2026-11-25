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
import com.seamless.android.ui.theme.tactilePress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

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

    fun launchCallWithChecks(isVideo: Boolean) {
        when {
            trustState == TorContactTrustState.BLOCKED ->
                Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
            trustState == TorContactTrustState.KEY_CHANGED ->
                Toast.makeText(context, "Verify this contact before calling", Toast.LENGTH_SHORT).show()
            !contactAddressReady ->
                Toast.makeText(context, "Ask them to share their ready contact first", Toast.LENGTH_SHORT).show()
            else -> {
                callHaptics.toggleOn(view)
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!isVideo) {
                    if (hasMic) callManager.startOutgoingCall(contact)
                    else callMicPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    val hasCam = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic && hasCam) {
                        callManager.startOutgoingCall(contact, isVideoCall = true)
                    } else {
                        val needed = buildList {
                            if (!hasMic) add(Manifest.permission.RECORD_AUDIO)
                            if (!hasCam) add(Manifest.permission.CAMERA)
                        }.toTypedArray()
                        callVideoPermLauncher.launch(needed)
                    }
                }
            }
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
                Lifecycle.Event.ON_PAUSE -> {
                    messagingService.setTyping(contact.id, false)
                }
                Lifecycle.Event.ON_STOP -> {
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
    
    val glassColor = OnionUi.CanvasRaised

    // Disappearing messages state
    val privacyManager = com.seamless.android.ui.privacy.PrivacyPreferenceManager
    var disappearingTtl by remember { mutableStateOf(privacyManager.disappearingTtlMs.value) }
    val ttlOptions = listOf(
        0L to "Off",
        5 * 60_000L to "5m",
        60 * 60_000L to "1h",
        24 * 60 * 60_000L to "24h",
        7 * 24 * 60 * 60_000L to "7d"
    )
    val timerShortLabel = ttlOptions.firstOrNull { it.first == disappearingTtl }?.second

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
            Column(modifier = Modifier.background(glassColor)) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTrustMenu = true }
                        ) {
                            OnionAvatar(
                                label = contact.nickname,
                                online = isPeerOnline,
                                avatarId = contact.avatarId,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = contact.nickname,
                                        color = OnionUi.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        imageVector = when (trustState) {
                                            TorContactTrustState.VERIFIED -> Icons.Default.VerifiedUser
                                            TorContactTrustState.KEY_CHANGED, TorContactTrustState.BLOCKED -> Icons.Default.Report
                                            TorContactTrustState.UNVERIFIED -> Icons.Default.Shield
                                        },
                                        contentDescription = "Trust status",
                                        tint = when (trustState) {
                                            TorContactTrustState.VERIFIED -> OnionUi.Online
                                            TorContactTrustState.KEY_CHANGED, TorContactTrustState.BLOCKED -> OnionUi.Danger
                                            TorContactTrustState.UNVERIFIED -> OnionUi.TextMuted
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val transportTier = messagingService.getActiveTransportTier(contact.onionAddress)
                                    val (peerStatusText, peerStatusColor) = when {
                                        isPeerTyping -> "Typing…" to OnionUi.Online
                                        !contactAddressReady -> "Address needs update" to OnionUi.Warning
                                        isPeerOnline && (lanPeers.contains(contact.onionAddress) ||
                                            transportTier == TorMessagingService.ActiveTransportTier.PHANTOM_LAN) ->
                                            "Direct LAN · Encrypted" to OnionUi.Online
                                        isPeerOnline && transportTier == TorMessagingService.ActiveTransportTier.WARP_DUAL_TOR ->
                                            "Dual-Circuit · Encrypted" to OnionUi.Online
                                        isPeerOnline -> "Online · Encrypted" to OnionUi.Online
                                        serviceState == TorMessagingService.ServiceState.RUNNING &&
                                            !contact.mailboxOnion.isNullOrEmpty() -> "Mailbox ready" to OnionUi.TextSecondary
                                        serviceState == TorMessagingService.ServiceState.RUNNING -> "Tor ready" to OnionUi.TextSecondary
                                        serviceState == TorMessagingService.ServiceState.WAITING_FOR_TOR -> "Connecting ${torStatus.bootstrapPercent}%" to OnionUi.Warning
                                        serviceState == TorMessagingService.ServiceState.STARTING -> "Starting…" to OnionUi.Warning
                                        serviceState == TorMessagingService.ServiceState.ERROR -> "Connection error" to OnionUi.Danger
                                        else -> "Offline" to OnionUi.TextMuted
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(peerStatusColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    val timerSuffix = if (disappearingTtl > 0L && timerShortLabel != null) " · ⏱ $timerShortLabel" else ""
                                    Text(
                                        text = peerStatusText + timerSuffix,
                                        color = if (isPeerTyping || isPeerOnline) peerStatusColor else OnionUi.TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = OnionUi.TextPrimary
                            )
                        }
                    },
                    actions = {
                        val callReady = contactAddressReady && trustState != TorContactTrustState.BLOCKED
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (callReady) OnionUi.Online.copy(alpha = 0.14f) else Color.Transparent)
                                .then(
                                    if (callReady) Modifier.border(0.9.dp, OnionUi.Online.copy(alpha = 0.30f), CircleShape)
                                    else Modifier
                                )
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    launchCallWithChecks(isVideo = false)
                                }
                        ) {
                            Icon(
                                Icons.Rounded.Call,
                                contentDescription = "Encrypted Voice Call",
                                tint = if (callReady) OnionUi.Online else OnionUi.TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (callReady) OnionUi.Info.copy(alpha = 0.14f) else Color.Transparent)
                                .then(
                                    if (callReady) Modifier.border(0.9.dp, OnionUi.Info.copy(alpha = 0.30f), CircleShape)
                                    else Modifier
                                )
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    launchCallWithChecks(isVideo = true)
                                }
                        ) {
                            Icon(
                                Icons.Rounded.Videocam,
                                contentDescription = "Encrypted Video Call",
                                tint = if (callReady) OnionUi.Info else OnionUi.TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(onClick = { showTrustMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Contact details and security",
                                tint = OnionUi.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                HorizontalDivider(color = OnionUi.Hairline, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            val replyPreview = replyingTo?.let { target ->
                TorRichChatCodec.friendlyPreviewText(target.text, target.attachmentMimeType)
            }
            SovereignChatComposer(
                modifier = Modifier.imePadding(),
                value = messageText,
                onValueChange = {
                    messageText = it
                    messagingService.setTyping(contact.id, it.isNotBlank())
                },
                placeholder = "Encrypted message…",
                burnPlaceholder = "View-Once secret message…",
                canSend = messageText.isNotBlank(),
                enabled = trustState != TorContactTrustState.BLOCKED && contactAddressReady,
                mediaEnabled = !isSending,
                isSending = isSending,
                burnOnReadArmed = burnOnReadArmed,
                onToggleBurnOnRead = { burnOnReadArmed = !burnOnReadArmed },
                isRecordingVoice = isRecordingVoice,
                onStartVoiceNote = {
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
                onCancelVoiceNote = { isRecordingVoice = false },
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
                },
                onOpenAttachments = {
                    when {
                        trustState == TorContactTrustState.BLOCKED ->
                            Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
                        trustState == TorContactTrustState.KEY_CHANGED ->
                            Toast.makeText(context, "Verify this contact before sending", Toast.LENGTH_SHORT).show()
                        !contactAddressReady ->
                            Toast.makeText(context, "Ask them to share their ready contact first", Toast.LENGTH_SHORT).show()
                        !isSending -> {
                            messagingService.speculativePrewarmBulk(contact.id)
                            showAttachmentSheet = true
                        }
                    }
                },
                onSendText = {
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
                replySenderLabel = replyingTo?.let { if (it.isFromMe) "yourself" else contact.nickname },
                replyPreviewText = replyPreview,
                replyAccentColor = OnionUi.AccentBlue,
                onCancelReply = { replyingTo = null }
            )
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
        val isAtBottom by remember(displayedMessages.size) {
            derivedStateOf {
                val lastIdx = displayedMessages.lastIndex
                if (lastIdx < 0) true
                else {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: lastIdx
                    lastVisible >= lastIdx - 1
                }
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
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(OnionUi.Surface)
                                .border(1.dp, OnionUi.Hairline, RoundedCornerShape(24.dp))
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OnionAvatar(
                                label = contact.nickname,
                                online = isPeerOnline,
                                avatarId = contact.avatarId,
                                size = 68.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching messages"
                                else "Encrypted chat with ${contact.nickname}",
                                color = OnionUi.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Try another keyword in this encrypted conversation."
                                else "Messages, voice notes, files, and View-Once notes travel peer-to-peer over Tor — zero central servers.",
                                color = OnionUi.TextSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                StatusIndicator(serviceState, torStatus, contactAddressReady, isPeerOnline)
                            }
                        }
                    }
                } else {
                    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(displayedMessages.size, key = { displayedMessages[it].id }) { index ->
                            val message = displayedMessages[index]
                            val previous = displayedMessages.getOrNull(index - 1)
                            val next = displayedMessages.getOrNull(index + 1)
                            val showDateHeader = previous == null || !isSameCalendarDay(previous.timestamp, message.timestamp)
                            if (showDateHeader) {
                                DateHeaderPill(formatDateHeader(message.timestamp))
                            }

                            val cleanBody = remember(message.text) { TorRichChatCodec.parseReply(message.text).body }
                            val isMsgDeleted = message.id in directDeletedIds ||
                                cleanBody == TorRichChatCodec.DELETED_DIRECT_MESSAGE_TEXT
                            val isMsgBurned = message.id in directBurnedIds ||
                                cleanBody == TorRichChatCodec.BURNED_CAPSULE_TEXT
                            val callEvent = remember(message.id, cleanBody, message.attachmentPath, isMsgDeleted, isMsgBurned) {
                                if (message.attachmentPath == null && !isMsgDeleted && !isMsgBurned) {
                                    parseCallEventText(cleanBody)
                                } else null
                            }

                            if (callEvent != null) {
                                Spacer(modifier = Modifier.height(if (showDateHeader) 2.dp else 6.dp))
                                CallEventChatCard(
                                    event = callEvent,
                                    timestampText = timeFmt.format(Date(message.timestamp)),
                                    isFromMe = message.isFromMe,
                                    onCallBack = { isVideo -> launchCallWithChecks(isVideo = isVideo) }
                                )
                            } else {
                                fun isBubbleClusterable(neighbor: TorMessageEntity?): Boolean {
                                    if (neighbor == null) return false
                                    if (neighbor.isFromMe != message.isFromMe) return false
                                    if (!isSameCalendarDay(neighbor.timestamp, message.timestamp)) return false
                                    if (abs(message.timestamp - neighbor.timestamp) > 180_000L) return false
                                    val neighborBody = TorRichChatCodec.parseReply(neighbor.text).body
                                    return neighbor.attachmentPath != null || parseCallEventText(neighborBody) == null
                                }
                                val isFirstInCluster = showDateHeader || !isBubbleClusterable(previous)
                                val isLastInCluster = next == null ||
                                    !isSameCalendarDay(message.timestamp, next.timestamp) ||
                                    !isBubbleClusterable(next)
                                if (!showDateHeader && isFirstInCluster) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                TorMessageBubble(
                                    message = message,
                                    contactNickname = contact.nickname,
                                    isFirstInCluster = isFirstInCluster,
                                    isLastInCluster = isLastInCluster,
                                    isEdited = message.id in directEditedIds,
                                    isDeleted = isMsgDeleted,
                                    isBurned = isMsgBurned,
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
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = displayedMessages.isNotEmpty() && !isAtBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 14.dp)
            ) {
                JumpToLatestButton(
                    onClick = {
                        if (displayedMessages.isNotEmpty()) {
                            scope.launch { listState.animateScrollToItem(displayedMessages.lastIndex) }
                        }
                    }
                )
            }

            TorEmojiBurst(emojiEffectToken, emojiEffectText)
        }
    }

    if (showTrustMenu) {
        ContactSecuritySheet(
            contact = contact,
            isPeerOnline = isPeerOnline,
            trustState = trustState,
            safetyNumber = remember(contact.id) {
                contact.safetyNumber(messagingService.getMyContactInfo().publicKey)
            },
            disappearingTtl = disappearingTtl,
            ttlOptions = ttlOptions,
            onSearchMessages = {
                showTrustMenu = false
                showSearchBar = true
            },
            onSelectTtl = { ttl ->
                disappearingTtl = ttl
                privacyManager.setDisappearingTtl(context, ttl)
            },
            onVerify = {
                contactManager.verifyContact(contact.id)
                trustState = TorContactTrustState.VERIFIED
            },
            onUnverify = {
                contactManager.markUnverified(contact.id)
                trustState = TorContactTrustState.UNVERIFIED
            },
            onToggleBlock = {
                if (trustState == TorContactTrustState.BLOCKED) {
                    contactManager.markUnverified(contact.id)
                    trustState = TorContactTrustState.UNVERIFIED
                } else {
                    contactManager.blockContact(contact.id)
                    trustState = TorContactTrustState.BLOCKED
                }
            },
            onDismiss = { showTrustMenu = false }
        )
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OnionUi.SurfacePressed)
            .border(1.dp, OnionUi.Hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            !contactAddressReady -> {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = OnionUi.Warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text("Contact address missing", color = OnionUi.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            isPeerOnline -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = OnionUi.Online,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text("Direct connection verified", color = OnionUi.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            serviceState == TorMessagingService.ServiceState.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = OnionUi.Online,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text("Securing direct connection", color = OnionUi.TextSecondary, fontSize = 12.sp)
            }
            serviceState == TorMessagingService.ServiceState.WAITING_FOR_TOR -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = OnionUi.Warning,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    "Connecting to Tor network (${torStatus.bootstrapPercent}%)",
                    color = OnionUi.TextSecondary,
                    fontSize = 12.sp
                )
            }
            serviceState == TorMessagingService.ServiceState.STARTING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = OnionUi.Warning,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text("Initializing Tor…", color = OnionUi.TextSecondary, fontSize = 12.sp)
            }
            else -> {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = OnionUi.Danger,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text("Offline", color = OnionUi.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TorMessageBubble(
    message: TorMessageEntity,
    contactNickname: String,
    isFirstInCluster: Boolean = true,
    isLastInCluster: Boolean = true,
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

    val primaryTextColor = if (message.isFromMe) OnionUi.OutgoingText else OnionUi.IncomingText
    val secondaryTextColor = if (message.isFromMe) OnionUi.OutgoingSubtle else OnionUi.IncomingSubtle
    val quoteBg = if (message.isFromMe) Color.Black.copy(alpha = 0.24f) else OnionUi.SurfacePressed

    val topStart = if (!message.isFromMe && !isFirstInCluster) 6.dp else 20.dp
    val topEnd = if (message.isFromMe && !isFirstInCluster) 6.dp else 20.dp
    val bottomStart = if (message.isFromMe) 20.dp else if (!isLastInCluster) 6.dp else 4.dp
    val bottomEnd = if (!message.isFromMe) 20.dp else if (!isLastInCluster) 6.dp else 4.dp
    val shape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd
    )

    val bubbleBrush = if (message.isFromMe) {
        Brush.linearGradient(listOf(OnionUi.OutgoingStart, OnionUi.OutgoingEnd))
    } else {
        Brush.linearGradient(listOf(OnionUi.IncomingStart, OnionUi.IncomingEnd))
    }
    val bubbleOutline = if (message.isFromMe) OnionUi.OutgoingBorder else OnionUi.IncomingBorder
    var appeared by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { appeared = true }
    val bubbleAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(160), label = "bubble_alpha")
    val bubbleOffset by animateFloatAsState(if (appeared) 0f else 12f, tween(200), label = "bubble_offset")

    val isPhotoOrAttachmentPending = message.attachmentPath != null && message.isFromMe &&
        deliveryState !in setOf(
            OnionMessageProtocol.DeliveryState.DELIVERED,
            OnionMessageProtocol.DeliveryState.READ,
            OnionMessageProtocol.DeliveryState.FAILED
        )

    @Composable
    fun TimestampCluster() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isEdited && !isDeleted && !isBurned) {
                Text(
                    text = "edited · ",
                    color = secondaryTextColor,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                )
            }
            if (message.isFromMe && message.deliveryRoute == "MAILBOX" && deliveryState != OnionMessageProtocol.DeliveryState.PENDING) {
                Text(text = "📬 ", fontSize = 10.sp)
            }
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                color = secondaryTextColor,
                fontSize = 10.5.sp
            )
            if (message.isFromMe) {
                Spacer(modifier = Modifier.width(3.dp))
                val (statusIcon, statusColor) = getDeliveryIcon(deliveryState, secondaryTextColor)
                Icon(
                    statusIcon,
                    contentDescription = deliveryState.name.lowercase(),
                    modifier = Modifier.size(13.dp),
                    tint = statusColor
                )
            }
        }
    }

    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = attachment?.absolutePath
    ) {
        value = if (!isDeleted && !isViewOnce && descriptor?.kind != "voice" && descriptor?.kind != "file") {
            attachment?.absolutePath?.let { path ->
                withContext(Dispatchers.IO) { decodeScaledFileBitmap(path, maxDimensionPx = 640) }
            }
        } else null
    }

    val isShortInlineText = !isDeleted &&
        !isViewOnce &&
        descriptor == null &&
        bitmap == null &&
        !isPhotoOrAttachmentPending &&
        parsed.replyToSender.isNullOrBlank() &&
        !isExpressiveEmoji(parsed.body) &&
        parsed.body.length <= 28 &&
        '\n' !in parsed.body

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 324.dp)
                .graphicsLayer {
                    alpha = bubbleAlpha
                    translationY = bubbleOffset
                }
                .shadow(
                    elevation = if (message.isFromMe) 4.dp else 1.dp,
                    shape = shape,
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(shape)
                .background(brush = bubbleBrush, shape = shape)
                .border(1.dp, bubbleOutline, shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongPress() }
                )
        ) {
            if (isShortInlineText) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = parsed.body,
                        color = primaryTextColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.padding(bottom = 1.dp)) {
                        TimestampCluster()
                    }
                }
            } else {
                val hPad = if (bitmap != null) 8.dp else 13.dp
                val vPad = if (bitmap != null) 8.dp else 10.dp
                Column(
                    modifier = Modifier.padding(horizontal = hPad, vertical = vPad)
                ) {
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
                            val quoteAccent = if (message.isFromMe) Color(0xFF93C5FD) else OnionUi.AccentBlue
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(quoteAccent)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = parsed.replyToSender,
                                    color = quoteAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!parsed.replyToPreview.isNullOrBlank()) {
                                    Text(
                                        text = parsed.replyToPreview,
                                        color = secondaryTextColor,
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
                        bitmap != null -> {
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
                                        .clip(RoundedCornerShape(14.dp))
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
                                        text = "Loading HD…",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = parsed.body,
                                color = primaryTextColor,
                                fontSize = if (isExpressiveEmoji(parsed.body)) 30.sp else 15.sp,
                                lineHeight = if (isExpressiveEmoji(parsed.body)) 36.sp else 21.sp
                            )
                        }
                    }

                    if (isPhotoOrAttachmentPending) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                            color = if (message.isFromMe) Color.White else OnionUi.Online,
                            trackColor = primaryTextColor.copy(alpha = 0.16f)
                        )
                        Text(
                            "Sending encrypted attachment…",
                            color = secondaryTextColor,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (!isDeleted && isExpressiveEmoji(parsed.body)) 1.dp else 4.dp))

                    Box(modifier = Modifier.align(Alignment.End)) {
                        TimestampCluster()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ContactSecuritySheet(
    contact: OnionContactManager.OnionContact,
    isPeerOnline: Boolean,
    trustState: TorContactTrustState,
    safetyNumber: String,
    disappearingTtl: Long,
    ttlOptions: List<Pair<Long, String>>,
    onSearchMessages: () -> Unit,
    onSelectTtl: (Long) -> Unit,
    onVerify: () -> Unit,
    onUnverify: () -> Unit,
    onToggleBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnionAvatar(
                    label = contact.nickname,
                    online = isPeerOnline,
                    avatarId = contact.avatarId,
                    size = 56.dp
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = contact.nickname,
                        color = OnionUi.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    OnionStatusChip(
                        label = when (trustState) {
                            TorContactTrustState.VERIFIED -> "Verified contact"
                            TorContactTrustState.KEY_CHANGED -> "Key changed · Verify"
                            TorContactTrustState.BLOCKED -> "Blocked"
                            TorContactTrustState.UNVERIFIED -> "Unverified · Check safety number"
                        },
                        color = when (trustState) {
                            TorContactTrustState.VERIFIED -> OnionUi.Online
                            TorContactTrustState.KEY_CHANGED, TorContactTrustState.BLOCKED -> OnionUi.Danger
                            TorContactTrustState.UNVERIFIED -> OnionUi.Warning
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OnionUi.Surface)
                    .border(1.dp, OnionUi.Hairline, RoundedCornerShape(14.dp))
                    .tactilePress(scaleDown = 0.98f, onClick = onSearchMessages)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = OnionUi.AccentBlue, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(12.dp))
                Text("Search in conversation", color = OnionUi.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OnionUi.Surface)
                    .border(1.dp, OnionUi.Hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = OnionUi.AccentBlue, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Disappearing messages", color = OnionUi.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ttlOptions.forEach { (ttl, label) ->
                        val active = ttl == disappearingTtl
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (active) Brush.verticalGradient(listOf(OnionUi.PrimaryStart, OnionUi.PrimaryEnd))
                                    else Brush.verticalGradient(listOf(OnionUi.SurfacePressed, OnionUi.SurfacePressed))
                                )
                                .border(
                                    1.dp,
                                    if (active) OnionUi.PrimaryRim else OnionUi.Hairline,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { onSelectTtl(ttl) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (active) OnionUi.OnPrimary else OnionUi.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OnionUi.Surface)
                    .border(1.dp, OnionUi.Hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            null,
                            tint = if (trustState == TorContactTrustState.VERIFIED) OnionUi.Online else OnionUi.Warning,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Safety Number", color = OnionUi.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Safety Number", safetyNumber))
                            Toast.makeText(context, "Safety number copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy safety number", tint = OnionUi.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = safetyNumber,
                    color = OnionUi.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = if (trustState == TorContactTrustState.VERIFIED) onUnverify else onVerify,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (trustState == TorContactTrustState.VERIFIED) OnionUi.SurfacePressed else OnionUi.PrimaryStart,
                        contentColor = if (trustState == TorContactTrustState.VERIFIED) OnionUi.TextPrimary else OnionUi.OnPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(
                        if (trustState == TorContactTrustState.VERIFIED) "Mark as Unverified" else "Mark Verified",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            val isBlocked = trustState == TorContactTrustState.BLOCKED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isBlocked) OnionUi.Surface else OnionUi.Danger.copy(alpha = 0.10f))
                    .border(
                        1.dp,
                        if (isBlocked) OnionUi.Hairline else OnionUi.Danger.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .tactilePress(scaleDown = 0.98f, onClick = onToggleBlock)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Block, null, tint = if (isBlocked) OnionUi.TextPrimary else OnionUi.Danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBlocked) "Unblock Contact" else "Block Contact",
                    color = if (isBlocked) OnionUi.TextPrimary else OnionUi.Danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(22.dp))
        }
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

private fun getDeliveryIcon(
    state: OnionMessageProtocol.DeliveryState,
    mutedTint: Color
): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when (state) {
        OnionMessageProtocol.DeliveryState.PENDING -> Icons.Default.Schedule to mutedTint
        OnionMessageProtocol.DeliveryState.SENDING -> Icons.Default.Schedule to mutedTint
        OnionMessageProtocol.DeliveryState.SENT -> Icons.Default.Done to mutedTint
        OnionMessageProtocol.DeliveryState.DELIVERED -> Icons.Default.DoneAll to mutedTint
        OnionMessageProtocol.DeliveryState.READ -> Icons.Default.DoneAll to Color(0xFF34D399)
        OnionMessageProtocol.DeliveryState.FAILED -> Icons.Default.Error to OnionUi.Danger
    }
}
