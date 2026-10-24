@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.onion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.seamless.android.data.TorMessageEntity
import com.seamless.android.data.TorMessageRepository
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorMessagingService
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
    val scope = rememberCoroutineScope()
    
    // Services
    val messagingService = remember { TorMessagingService.getInstance(context) }
    val messageRepository = remember { TorMessageRepository.getInstance(context) }
    val contactManager = remember { OnionContactManager.getInstance(context) }
    val serviceState by messagingService.serviceState.collectAsState()
    val torStatus by messagingService.torStatus.collectAsState()
    val connectedPeers by messagingService.connectedPeersFlow.collectAsState()
    val typingPeers by messagingService.typingPeersFlow.collectAsState()
    val isPeerOnline = connectedPeers.contains(contact.onionAddress)
    val isPeerTyping = typingPeers.contains(contact.onionAddress)
    val contactAddressReady = !contact.requiresRendezvous()
    
    // Messages from database
    val messagesFromDb by messageRepository.getMessagesForContact(contact).collectAsState(initial = emptyList())
    
    // UI state
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var emojiEffectText by remember { mutableStateOf("") }
    var emojiEffectToken by remember { mutableIntStateOf(0) }
    var trustState by remember(contact.id) { mutableStateOf(contact.effectiveTrustState()) }
    var showTrustMenu by remember { mutableStateOf(false) }
    var startupRecoveryAttempted by remember(contact.id) { mutableStateOf(false) }
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
        scope.launch {
            isSending = true
            try {
                val image = withContext(Dispatchers.IO) { prepareImageForTor(context, uri) }
                if (image == null) {
                    Toast.makeText(context, "Could not prepare this image", Toast.LENGTH_SHORT).show()
                } else {
                    messagingService.sendImage(contact.id, image, "image/jpeg").onFailure { error ->
                        Toast.makeText(context, "Failed to queue photo: ${error.message}", Toast.LENGTH_SHORT).show()
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

    DisposableEffect(contact.id) {
        messagingService.setChatVisible(contact.id, true)
        onDispose {
            messagingService.setTyping(contact.id, false)
            messagingService.setChatVisible(contact.id, false)
        }
    }
    
    // Mark messages as read when entering chat
    LaunchedEffect(contact.id) {
        messageRepository.markAsRead(contact)
    }
    
    // Listen for incoming messages — messages are already persisted by TorMessagingService,
    // so this listener only marks them as read (since the user is viewing this chat)
    LaunchedEffect(Unit) {
        messagingService.setMessageListener(object : TorMessagingService.MessageListener {
            override fun onMessageReceived(
                from: OnionContactManager.OnionContact,
                message: TorMessagingService.OnionMessage
            ) {
                if (from.onionAddress == contact.onionAddress) {
                    // Message is already saved by TorMessagingService.handleMessage()
                    // Just mark as read since the user is viewing this chat
                    scope.launch {
                        messageRepository.markAsRead(contact)
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
        if (showTrustMenu) {
            showTrustMenu = false
        } else {
            onNavigateBack()
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
                                    val (peerStatusText, peerStatusColor) = when {
                                        isPeerTyping -> "Typing" to OnionUi.Accent
                                        !contactAddressReady -> "Address needs update" to OnionUi.Warning
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
                                    contact.safetyNumber(),
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
            // Modern glassmorphic input container with elevated shadow
            val inputGlassColor = if (androidx.compose.foundation.isSystemInDarkTheme()) OnionUi.CanvasRaised else Color.White
                
            var isFocused by remember { mutableStateOf(false) }

            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .imePadding() // Lift the input bar above the keyboard
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (isFocused) 8.dp else 4.dp,
                            shape = RoundedCornerShape(18.dp),
                            spotColor = if (isFocused) OnionUi.AccentStrong.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.18f)
                        )
                        .background(
                            color = inputGlassColor,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            brush = if (isFocused) {
                                SolidColor(OnionUi.AccentStrong)
                            } else {
                                SolidColor(if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f))
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it
                            messagingService.setTyping(contact.id, it.isNotBlank())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .onFocusChanged {
                                isFocused = it.isFocused
                                if (!it.isFocused) messagingService.setTyping(contact.id, false)
                            },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF00C6FF)),
                        decorationBox = { innerTextField ->
                            if (messageText.isEmpty()) {
                                Text(
                                    "Message...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { photoPicker.launch("image/*") },
                        enabled = !isSending && trustState != TorContactTrustState.BLOCKED && contactAddressReady,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = "Send photo",
                            tint = if (isSending) Color.White.copy(alpha = 0.35f) else OnionUi.Accent
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            if (trustState == TorContactTrustState.BLOCKED) {
                                Toast.makeText(context, "Contact is blocked", Toast.LENGTH_SHORT).show()
                            } else if (trustState == TorContactTrustState.KEY_CHANGED) {
                                Toast.makeText(context, "Verify this contact before sending", Toast.LENGTH_SHORT).show()
                            } else if (!contactAddressReady) {
                                Toast.makeText(
                                    context,
                                    "Ask them to share again after Tor is ready",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (messageText.isNotBlank() && !isSending) {
                                val text = messageText
                                messageText = ""
                                emojiEffectText = text
                                emojiEffectToken++
                                messagingService.setTyping(contact.id, false)
                                isSending = true
                                
                                scope.launch {
                                    try {
                                        // Use TorMessagingService to send
                                        val result = messagingService.sendMessage(
                                            contactId = contact.id,
                                            text = text
                                        )
                                        
                                        result.onSuccess {
                                            // TorMessagingService owns message persistence and delivery state.
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                "Failed to send: ${error.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Failed to send: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isSending = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(46.dp),
                        containerColor = OnionUi.AccentStrong,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chatBackground)
                .padding(paddingValues)
        ) {
            if (messagesFromDb.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnionUi.Accent.copy(alpha = 0.56f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "End-to-End Encrypted",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Messages are encrypted for this chat.\nTor delivery starts when both contact details are ready.",
                        color = OnionUi.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Status indicator
                    StatusIndicator(serviceState, torStatus, contactAddressReady, isPeerOnline)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messagesFromDb, key = { it.id }) { message ->
                        TorMessageBubble(
                            message = message,
                            contactNickname = contact.nickname
                        )
                    }
                }
            }
            TorEmojiBurst(emojiEffectToken, emojiEffectText)
        }
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

@Composable
private fun TorMessageBubble(
    message: TorMessageEntity,
    contactNickname: String
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
    
    // Delivery route for transparency
    val routeIcon = when (message.deliveryRoute) {
        "MAILBOX" -> "📬"
        "QUEUED" -> "🕒"
        "BLUETOOTH" -> "📶"
        "WIFI_DIRECT" -> "📡"
        else -> "🔗" // DIRECT
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
    val bubbleOutline = if (message.isFromMe) {
        Color.White.copy(alpha = 0.17f)
    } else {
        Color.White.copy(alpha = 0.10f)
    }
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
                .background(
                    brush = bubbleBrush,
                    shape = shape
                )
                .border(1.dp, bubbleOutline, shape)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val attachment = message.attachmentPath?.let(::File)?.takeIf { it.exists() }
                val bitmap = remember(message.attachmentPath) {
                    attachment?.absolutePath?.let(BitmapFactory::decodeFile)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Open photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 276.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { previewPath = attachment?.absolutePath }
                    )
                } else {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = if (isExpressiveEmoji(message.text)) 30.sp else 15.sp,
                        lineHeight = if (isExpressiveEmoji(message.text)) 36.sp else 21.sp
                    )
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
                        "Securing photo for Tor delivery",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(if (isExpressiveEmoji(message.text)) 1.dp else 5.dp))
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    // Delivery route indicator (Session-style transparency)
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
                        // Delivery status icon
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
    val bitmap = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
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
                        bitmap = bitmap.asImageBitmap(),
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

internal fun prepareImageForTor(context: android.content.Context, uri: Uri): ByteArray? {
    val source = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val original = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
    val scale = minOf(1f, 1280f / maxOf(original.width, original.height).toFloat())
    var bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            original,
            (original.width * scale).toInt().coerceAtLeast(1),
            (original.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else original
    try {
        repeat(3) {
            for (quality in intArrayOf(82, 72, 62, 52, 42)) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val bytes = output.toByteArray()
                if (bytes.size <= com.seamless.android.net.TorAttachmentTransfer.MAX_IMAGE_BYTES) return bytes
            }

            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.75f).toInt().coerceAtLeast(1),
                (bitmap.height * 0.75f).toInt().coerceAtLeast(1),
                true
            )
            if (bitmap !== original) bitmap.recycle()
            bitmap = scaled
        }
        return null
    } finally {
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
    }
}

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
