@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.mesh

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.mesh.*
import com.seamless.android.ui.MessagesList
import com.seamless.android.ui.MessageInput

// Premium Mesh Design Tokens — Obsidian & Optical Light
private object MeshColors {
    // Optical Monochrome
    val PrimaryCyan = Color(0xFFFFFFFF)              // Pure Optical White
    val PrimaryCyanDim = Color.White.copy(alpha = 0.12f)
    // Private mesh - Titanium & Lunar Light
    val PrivateViolet = Color(0xFFFFFFFF)            // Optical White
    val PrivateMagenta = Color(0xFFE5E5EA)           // Lunar Silver
    val PrivateDarkBg = Color(0xFF070709)            // Deep Obsidian
    val PrivateCardBg = Color(0xFF101014)            // Titanium Card Surface
    // Shared
    val GlassWhite = Color.White.copy(alpha = 0.04f) // Translucent glass fill
    val GlassBorder = Color.White.copy(alpha = 0.08f)// 1px hairline border
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFB0B0B8)            // Titanium secondary
    val TextMuted = Color(0xFF6B6B75)                // Muted tertiary
    val SuccessGreen = Color(0xFF30D158)             // Apple System Emerald
    val ErrorRed = Color(0xFFFF453A)                 // Apple System Coral
    val DarkBg = Color(0xFF000000)                   // True OLED Black
    val SpaceNavy = Color(0xFF070709)                // Deep Obsidian
}

// Legacy aliases mapped to design tokens
private val PrivateRoomPurple = MeshColors.PrivateViolet
private val PrivateRoomMagenta = MeshColors.PrivateMagenta
private val PrivateRoomDarkBg = MeshColors.PrivateDarkBg
private val PrivateRoomCardBg = MeshColors.PrivateCardBg

/**
 * Glassmorphic card with optional shimmer border effect
 */
@Composable
private fun MeshGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = MeshColors.PrivateViolet,
    showShimmer: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "shimmer_offset"
    )

    Box(
        modifier = modifier
            .then(
                if (showShimmer) {
                    Modifier.drawBehind {
                        val brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                glowColor.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            start = Offset(size.width * shimmerOffset, 0f),
                            end = Offset(size.width * (shimmerOffset + 1f), size.height)
                        )
                        drawRoundRect(
                            brush = brush,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                } else Modifier
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MeshColors.GlassWhite,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MeshColors.GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }
}

/**
 * Main Private Mesh screen with options to create or join a mesh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateMeshScreen(
    meshService: BluetoothMeshService?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val meshManager = remember { PrivateMeshManager.getInstance(context) }
    
    val state by meshManager.state.collectAsState()
    val currentMeshName by meshManager.currentMeshName.collectAsState()
    val members by meshManager.members.collectAsState()
    val pendingRequests by meshManager.pendingRequests.collectAsState()
    val discoveredMeshes by meshManager.discoveredMeshes.collectAsState()
    val isDiscovering by meshManager.isDiscovering.collectAsState()
    
    // Track which screen to show
    var showCreateScreen by remember { mutableStateOf(false) }
    var showJoinScreen by remember { mutableStateOf(false) }
    var showRequestsDialog by remember { mutableStateOf(false) }
    
    // Collect messages for chat
    val messages by meshManager.messages.collectAsState()
    
    val colorScheme = MaterialTheme.colorScheme
    
    // Use distinct purple theme when inside a private room
    // Obsidian Luxe background gradient
    val isInRoom = state == PrivateMeshState.HOSTING || state == PrivateMeshState.CONNECTED
    val backgroundGradient = if (isInRoom) {
        // Deep obsidian gradient for private room with subtle titanium elevation
        Brush.verticalGradient(
            colors = listOf(
                MeshColors.PrivateDarkBg,
                Color(0xFF0F0F14),
                MeshColors.DarkBg
            )
        )
    } else {
        // Deep obsidian canvas for menu/joining states
        Brush.verticalGradient(
            colors = listOf(
                MeshColors.SpaceNavy,
                MeshColors.DarkBg,
                Color(0xFF0A0A0E)
            )
        )
    }
    val accentColor = if (isInRoom) MeshColors.SuccessGreen else MeshColors.PrimaryCyan
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .imePadding() // Fix: Push content up when keyboard appears
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (state) {
                                PrivateMeshState.HOSTING -> "Hosting: $currentMeshName"
                                PrivateMeshState.CONNECTED -> "Connected: $currentMeshName"
                                PrivateMeshState.JOINING -> "Joining..."
                                else -> "Private Mesh"
                            },
                            fontWeight = FontWeight.Bold,
                            color = MeshColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            when {
                                showCreateScreen -> showCreateScreen = false
                                showJoinScreen -> {
                                    meshManager.stopDiscovery()
                                    showJoinScreen = false
                                }
                                else -> onBackClick()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MeshColors.TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Show Requests button only when hosting and there are pending requests
                        if (state == PrivateMeshState.HOSTING) {
                            Box {
                                IconButton(onClick = { showRequestsDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Requests",
                                        tint = if (pendingRequests.isNotEmpty()) accentColor else MeshColors.TextSecondary
                                    )
                                }
                                // Badge for pending request count
                                if (pendingRequests.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .size(18.dp),
                                        shape = CircleShape,
                                        color = accentColor
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = pendingRequests.size.toString(),
                                                color = Color.Black,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            val currentViewKey = when {
                showCreateScreen -> "CREATE"
                showJoinScreen -> "JOIN"
                state == PrivateMeshState.HOSTING -> "HOSTING"
                state == PrivateMeshState.CONNECTED -> "CONNECTED"
                state == PrivateMeshState.JOINING -> "JOINING"
                else -> "MENU"
            }
            AnimatedContent(
                targetState = currentViewKey,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(180))
                        )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                label = "private_mesh_state_transition"
            ) { viewKey ->
                when (viewKey) {
                    "CREATE" -> {
                        CreateMeshContent(
                            meshManager = meshManager,
                            onMeshCreated = { showCreateScreen = false }
                        )
                    }
                    "JOIN" -> {
                        JoinMeshContent(
                            meshManager = meshManager,
                            discoveredMeshes = discoveredMeshes,
                            isDiscovering = isDiscovering,
                            onJoinRequested = { showJoinScreen = false }
                        )
                    }
                    "HOSTING" -> {
                        HostingContent(
                            meshManager = meshManager,
                            meshService = meshService,
                            members = members,
                            messages = messages,
                            meshName = currentMeshName ?: ""
                        )
                    }
                    "CONNECTED" -> {
                        ConnectedContent(
                            meshManager = meshManager,
                            meshService = meshService,
                            members = members,
                            messages = messages,
                            meshName = currentMeshName ?: ""
                        )
                    }
                    "JOINING" -> {
                        JoiningContent()
                    }
                    else -> {
                        MainMenuContent(
                            onCreateClick = { showCreateScreen = true },
                            onJoinClick = {
                                meshManager.startDiscovery()
                                showJoinScreen = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Requests Slide-Up Panel
    if (showRequestsDialog) {
        RequestsDialog(
            pendingRequests = pendingRequests,
            onAccept = { requestId -> meshManager.acceptJoinRequest(requestId) },
            onReject = { requestId -> meshManager.rejectJoinRequest(requestId) },
            onDismiss = { showRequestsDialog = false }
        )
    }
}

/**
 * Main menu with Create and Join options - Premium glassmorphic design
 */
@Composable
private fun MainMenuContent(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    // Pulse animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "menu_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val outerRingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_ring_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated glowing icon with pulse (GPU-accelerated via graphicsLayer)
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MeshColors.PrimaryCyan.copy(alpha = outerRingAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Inner circle
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MeshColors.PrimaryCyan.copy(alpha = glowAlpha),
                                MeshColors.PrimaryCyan.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .drawBehind {
                        drawCircle(
                            color = MeshColors.PrimaryCyan.copy(alpha = 0.4f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Hub,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MeshColors.PrimaryCyan
                )
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        
        Text(
            text = "Private Mesh",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MeshColors.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Create a private group or join an existing one.\nOnly approved members can participate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MeshColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Create Mesh - Glassmorphic Action Card
        MeshGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = MeshColors.PrimaryCyan,
            showShimmer = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreateClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MeshColors.PrimaryCyan.copy(alpha = 0.3f),
                                    MeshColors.PrivateViolet.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MeshColors.PrimaryCyan
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Create Mesh",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeshColors.TextPrimary
                    )
                    Text(
                        text = "Host a new private group",
                        fontSize = 12.sp,
                        color = MeshColors.TextMuted
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MeshColors.TextMuted
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Join Mesh - Glassmorphic Action Card
        MeshGlassCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = MeshColors.PrivateViolet,
            showShimmer = false
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJoinClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MeshColors.PrivateViolet.copy(alpha = 0.3f),
                                    MeshColors.PrivateMagenta.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MeshColors.PrivateViolet
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Join Mesh",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeshColors.TextPrimary
                    )
                    Text(
                        text = "Find and join nearby groups",
                        fontSize = 12.sp,
                        color = MeshColors.TextMuted
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MeshColors.TextMuted
                )
            }
        }
    }
}

/**
 * Create mesh screen content
 */
@Composable
private fun CreateMeshContent(
    meshManager: PrivateMeshManager,
    onMeshCreated: () -> Unit
) {
    var meshName by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Create Private Mesh",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MeshColors.TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Give your mesh a name so others can find it",
            style = MaterialTheme.typography.bodyMedium,
            color = MeshColors.TextSecondary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = meshName,
            onValueChange = { meshName = it.take(20) },
            label = { Text("Mesh Name") },
            placeholder = { Text("e.g., Friends Group") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (meshName.isNotBlank()) {
                        meshManager.createMesh(meshName.trim())
                        onMeshCreated()
                    }
                }
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (meshName.isNotBlank()) {
                    meshManager.createMesh(meshName.trim())
                    onMeshCreated()
                }
            },
            enabled = meshName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MeshColors.PrimaryCyan,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Start Hosting",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }
    }
}

/**
 * Join mesh screen with holographic sonar animation and discovered meshes
 */
@Composable
private fun JoinMeshContent(
    meshManager: PrivateMeshManager,
    discoveredMeshes: List<DiscoveredMesh>,
    isDiscovering: Boolean,
    onJoinRequested: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Holographic sonar animation
        if (isDiscovering) {
            HolographicSonar()
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (discoveredMeshes.isEmpty()) "Searching for nearby meshes..." else "Found ${discoveredMeshes.size} mesh${if (discoveredMeshes.size != 1) "es" else ""}",
            style = MaterialTheme.typography.bodyLarge,
            color = MeshColors.TextSecondary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // List of discovered meshes
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(discoveredMeshes) { mesh ->
                DiscoveredMeshCard(
                    mesh = mesh,
                    onClick = {
                        meshManager.requestToJoin(mesh.meshId)
                        onJoinRequested()
                    }
                )
            }
        }
    }
}

/**
 * Premium holographic sonar animation with rotating sweep, concentric rings, and pulsing dots
 */
@Composable
private fun HolographicSonar() {
    val infiniteTransition = rememberInfiniteTransition(label = "sonar")

    // Sweep rotation (full 360° over 3 seconds)
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "sweep_angle"
    )

    // Ring pulse animations (3 rings with staggered timing)
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring1"
    )
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring1s"
    )

    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring2"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring2s"
    )

    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring3"
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring3s"
    )

    // Pulsing dots (simulate detected signals)
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1a"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2a"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, delayMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ), label = "dot3a"
    )

    val cyanColor = MeshColors.PrimaryCyan
    val violetColor = MeshColors.PrivateViolet

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Static concentric grid rings (subtle)
            for (i in 1..4) {
                val r = maxRadius * (i / 4f)
                drawCircle(
                    color = cyanColor.copy(alpha = 0.08f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 0.8f)
                )
            }

            // Pulsing expanding rings
            val rings = listOf(
                ring1Scale to ring1Alpha,
                ring2Scale to ring2Alpha,
                ring3Scale to ring3Alpha
            )
            for ((scale, alpha) in rings) {
                val r = maxRadius * scale
                drawCircle(
                    color = cyanColor.copy(alpha = alpha * 0.4f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // Rotating sweep line (pie-slice gradient)
            rotate(sweepAngle, pivot = center) {
                val sweepBrush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.05f to cyanColor.copy(alpha = 0.25f),
                    0.12f to cyanColor.copy(alpha = 0.08f),
                    0.15f to Color.Transparent,
                    1f to Color.Transparent
                )
                drawCircle(
                    brush = sweepBrush,
                    radius = maxRadius,
                    center = center
                )
                // Bright leading edge line
                drawLine(
                    color = cyanColor.copy(alpha = 0.6f),
                    start = center,
                    end = Offset(center.x, center.y - maxRadius),
                    strokeWidth = 1.5f
                )
            }

            // Pulsing signal dots at pseudo-random positions
            val dots = listOf(
                Triple(0.55f, 0.78f, dot1Alpha),  // angle ~45° from center
                Triple(0.72f, 0.35f, dot2Alpha),  // angle ~150°
                Triple(0.30f, 0.42f, dot3Alpha)   // angle ~260°
            )
            for ((xFrac, yFrac, alpha) in dots) {
                val dotPos = Offset(size.width * xFrac, size.height * yFrac)
                // Glow
                drawCircle(
                    color = violetColor.copy(alpha = alpha * 0.3f),
                    radius = 8f,
                    center = dotPos
                )
                // Core dot
                drawCircle(
                    color = cyanColor.copy(alpha = alpha * 0.9f),
                    radius = 3.5f,
                    center = dotPos
                )
            }
        }

        // Center icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MeshColors.PrimaryCyan.copy(alpha = 0.25f),
                            MeshColors.PrimaryCyan.copy(alpha = 0.08f)
                        )
                    )
                )
                .drawBehind {
                    drawCircle(
                        color = cyanColor.copy(alpha = 0.5f),
                        style = Stroke(width = 1.2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Hub,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MeshColors.PrimaryCyan
            )
        }
    }
}

/**
 * Card for a discovered mesh - glassmorphic style
 */
@Composable
private fun DiscoveredMeshCard(
    mesh: DiscoveredMesh,
    onClick: () -> Unit
) {
    MeshGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        glowColor = MeshColors.PrimaryCyan,
        showShimmer = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mesh icon with gradient
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MeshColors.PrimaryCyan.copy(alpha = 0.2f),
                                MeshColors.PrivateViolet.copy(alpha = 0.15f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Hub,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MeshColors.PrimaryCyan
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mesh.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshColors.TextPrimary
                )
                Text(
                    text = "Host: ${mesh.hostNickname} • ${mesh.memberCount} member${if (mesh.memberCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshColors.TextSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MeshColors.TextMuted
            )
        }
    }
}

/**
 * Content when hosting a mesh - includes chat area
 */
@Composable
private fun HostingContent(
    meshManager: PrivateMeshManager,
    meshService: BluetoothMeshService?,
    members: List<MeshMember>,
    messages: List<PrivateMeshMessage>,
    meshName: String
) {
    // Get my nickname from members list (host is self when hosting)
    val myNickname = members.find { it.isHost }?.nickname ?: "Me"
    // Only show media buttons if we have a mesh service to handle sending
    val showMediaButtons = meshService != null
    // We can't access ChatViewModel easily here for commands/mentions, so keep suggestions empty for now
    // or we could refactor to pass them down if needed.
    
    var messageText by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var showMembers by remember { mutableStateOf(false) }
    val reactPrefix = "__REACT__:"
    val messageReactions = remember { mutableStateMapOf<String, MutableMap<String, Int>>() }
    val processedReactionMsgIds = remember { mutableSetOf<String>() }
    val reactionAnimationState = com.seamless.android.ui.effects.rememberReactionAnimationState()

    LaunchedEffect(messages.size) {
        val now = System.currentTimeMillis()
        messages.forEach { msg ->
            if (msg.content.startsWith(reactPrefix) && processedReactionMsgIds.add(msg.id)) {
                val parts = msg.content.removePrefix(reactPrefix).split(":")
                if (parts.size >= 2 && !msg.isFromMe) {
                    val targetMsgId = parts[0]
                    val emoji = parts[1]
                    val updated = LinkedHashMap<String, Int>()
                    messageReactions[targetMsgId]?.let { updated.putAll(it) }
                    updated[emoji] = (updated[emoji] ?: 0) + 1
                    messageReactions[targetMsgId] = updated
                    if (now - msg.timestamp < 20_000L) {
                        reactionAnimationState.trigger(emoji)
                    }
                }
            }
        }
    }
    val displayMessages = remember(messages) {
        messages.filter { !it.content.startsWith(reactPrefix) }.map { it.toBitchatMessage() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val activeReaction = reactionAnimationState.activeReaction.value
        if (activeReaction != null) {
            com.seamless.android.ui.effects.ReactionAnimationOverlay(
                effect = activeReaction,
                onComplete = { reactionAnimationState.clear() }
            )
        }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Compact collapsible header - shows mesh info, expands to show members
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMembers = !showMembers },
            color = MeshColors.PrivateCardBg
        ) {
            Column {
                // Compact header row - always visible
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small room icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MeshColors.PrivateViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MeetingRoom,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MeshColors.PrivateViolet
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    // Mesh name and member count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meshName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeshColors.TextPrimary
                        )
                        Text(
                            text = "${members.size} member${if (members.size != 1) "s" else ""} • Hosting",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshColors.PrivateViolet.copy(alpha = 0.8f)
                        )
                    }
                    
                    // Animated expand/collapse icon
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (showMembers) 180f else 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "chevron_rotation"
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (showMembers) "Collapse" else "Expand",
                        modifier = Modifier.rotate(rotationAngle),
                        tint = MeshColors.TextSecondary
                    )
                }
                
                // Expanded content with smooth animation
                AnimatedVisibility(
                    visible = showMembers,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 400f
                        )
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 400f
                        )
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MeshColors.PrivateViolet.copy(alpha = 0.3f))
                        
                        // Members list
                        members.forEach { member ->
                            MemberCard(
                                member = member,
                                isHost = true,
                                onKick = if (!member.isHost) {
                                    { meshManager.kickMember(member.peerID) }
                                } else null
                            )
                        }
                        
                        // Stop hosting button
                        OutlinedButton(
                            onClick = { meshManager.stopHosting() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MeshColors.ErrorRed
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeshColors.ErrorRed.copy(alpha = 0.5f))
                        ) {
                            Text("Stop Hosting")
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = MeshColors.GlassBorder)

        
        if (meshService != null) {
            MessagesList(
                messages = displayMessages,
                currentUserNickname = myNickname,
                meshService = meshService,
                modifier = Modifier.weight(1f),
                isPrivateChat = true,
                connectedPeersCount = members.size,
                onNicknameClick = { nickname ->
                    val currentText = messageText.text
                    val mentionText = "@$nickname "
                    val newText = if (currentText.isEmpty()) mentionText else "$currentText $mentionText"
                    messageText = androidx.compose.ui.text.input.TextFieldValue(
                        text = newText,
                        selection = androidx.compose.ui.text.TextRange(newText.length)
                    )
                },
                onReactionSelected = { message, emoji ->
                    processedReactionMsgIds.add("local:${message.id}:$emoji")
                    val existing = LinkedHashMap(messageReactions[message.id] ?: emptyMap())
                    existing[emoji] = (existing[emoji] ?: 0) + 1
                    messageReactions[message.id] = existing
                    reactionAnimationState.trigger(emoji)
                    meshManager.sendMessage("$reactPrefix${message.id}:$emoji")
                },
                messageReactions = messageReactions
            )
        } else {
            Box(Modifier.weight(1f))
        }
        
        // Message input
        MessageInput(
            value = messageText,
            onValueChange = { messageText = it },
            onSend = {
                if (messageText.text.isNotBlank()) {
                    meshManager.sendMessage(messageText.text.trim())
                    messageText = androidx.compose.ui.text.input.TextFieldValue("")
                }
            },
            onSendVoiceNote = { _, _, filePath ->
                meshManager.sendVoiceNote(filePath)
            },
            onSendImageNote = { _, _, filePath ->
                meshManager.sendImage(filePath)
            },
            onSendFileNote = { _, _, _ -> },
            selectedPrivatePeer = null,
            currentChannel = null,
            nickname = myNickname,
            showMediaButtons = true
        )
    }
    }
}

/**
 * Slide-up bottom sheet for join requests - replaces AlertDialog
 */
@Composable
private fun RequestsDialog(
    pendingRequests: List<JoinRequest>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Scrim overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Slide-up panel
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 400f
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // Block clicks from reaching scrim
                    .padding(top = 80.dp),
                color = MeshColors.PrivateCardBg,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MeshColors.GlassBorder
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Handle bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MeshColors.TextMuted)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MeshColors.PrivateViolet,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Join Requests",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MeshColors.TextPrimary
                        )
                        if (pendingRequests.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MeshColors.PrivateViolet.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = pendingRequests.size.toString(),
                                    color = MeshColors.PrivateViolet,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (pendingRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No pending requests",
                                color = MeshColors.TextMuted,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            items(pendingRequests) { request ->
                                JoinRequestCard(
                                    request = request,
                                    onAccept = { onAccept(request.requestId) },
                                    onReject = { onReject(request.requestId) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Done button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            "Done",
                            color = MeshColors.PrivateViolet,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Join request card with glassmorphic style and glowing action buttons
 */
@Composable
private fun JoinRequestCard(
    request: JoinRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    MeshGlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeshColors.PrimaryCyan,
        showShimmer = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with gradient
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MeshColors.PrimaryCyan.copy(alpha = 0.25f),
                                MeshColors.PrivateViolet.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = request.requesterNickname.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MeshColors.PrimaryCyan
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.requesterNickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshColors.TextPrimary
                )
                Text(
                    text = "Wants to join",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshColors.TextSecondary
                )
            }
            
            // Reject button - glowing red
            IconButton(
                onClick = onReject
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MeshColors.ErrorRed.copy(alpha = 0.15f))
                        .drawBehind {
                            drawCircle(
                                color = MeshColors.ErrorRed.copy(alpha = 0.4f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Reject",
                        tint = MeshColors.ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // Accept button - glowing green
            IconButton(
                onClick = onAccept
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MeshColors.SuccessGreen.copy(alpha = 0.15f))
                        .drawBehind {
                            drawCircle(
                                color = MeshColors.SuccessGreen.copy(alpha = 0.4f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Accept",
                        tint = MeshColors.SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Member card with glassmorphic styling, gradient avatar, and shimmer host badge
 */
@Composable
private fun MemberCard(
    member: MeshMember,
    isHost: Boolean,
    onKick: (() -> Unit)?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "member_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing)
        ),
        label = "host_badge_shimmer"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MeshColors.GlassWhite,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeshColors.GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gradient avatar circle with glow
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (member.isHost) {
                            Brush.linearGradient(
                                colors = listOf(
                                    MeshColors.PrimaryCyan.copy(alpha = 0.3f),
                                    MeshColors.PrivateViolet.copy(alpha = 0.3f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    MeshColors.PrivateViolet.copy(alpha = 0.2f),
                                    MeshColors.PrivateMagenta.copy(alpha = 0.15f)
                                )
                            )
                        }
                    )
                    .drawBehind {
                        if (member.isHost) {
                            drawCircle(
                                color = MeshColors.PrimaryCyan.copy(alpha = 0.5f),
                                style = Stroke(width = 1.2f)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (member.isHost) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Host",
                        modifier = Modifier.size(20.dp),
                        tint = MeshColors.PrimaryCyan
                    )
                } else {
                    Text(
                        text = member.nickname.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MeshColors.PrivateViolet
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.nickname,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MeshColors.TextPrimary
                    )
                    if (member.isHost) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Premium host badge with shimmer effect
                        Box(
                            modifier = Modifier
                                .drawBehind {
                                    val brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MeshColors.PrimaryCyan.copy(alpha = 0.4f),
                                            Color.Transparent
                                        ),
                                        start = Offset(size.width * shimmerOffset, 0f),
                                        end = Offset(size.width * (shimmerOffset + 1f), size.height)
                                    )
                                    drawRoundRect(
                                        brush = brush,
                                        cornerRadius = CornerRadius(4.dp.toPx()),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                        ) {
                            Surface(
                                color = MeshColors.PrimaryCyan.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "HOST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeshColors.PrimaryCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            if (onKick != null && isHost && !member.isHost) {
                IconButton(
                    onClick = onKick
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MeshColors.ErrorRed.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonRemove,
                            contentDescription = "Remove member",
                            modifier = Modifier.size(16.dp),
                            tint = MeshColors.ErrorRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content when connected to a mesh as member - includes chat area
 */
@Composable
private fun ConnectedContent(
    meshManager: PrivateMeshManager,
    meshService: BluetoothMeshService?,
    members: List<MeshMember>,
    messages: List<PrivateMeshMessage>,
    meshName: String
) {
    // Get my nickname - for connected members, find their entry in the list
    // When connected, we're the one who is NOT the host, so find based on that
    val myNickname = members.firstOrNull { !it.isHost }?.nickname ?: "Me"
    var messageText by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var showMembers by remember { mutableStateOf(false) }
    val reactPrefix = "__REACT__:"
    val messageReactions = remember { mutableStateMapOf<String, MutableMap<String, Int>>() }
    val processedReactionMsgIds = remember { mutableSetOf<String>() }
    val reactionAnimationState = com.seamless.android.ui.effects.rememberReactionAnimationState()

    LaunchedEffect(messages.size) {
        val now = System.currentTimeMillis()
        messages.forEach { msg ->
            if (msg.content.startsWith(reactPrefix) && processedReactionMsgIds.add(msg.id)) {
                val parts = msg.content.removePrefix(reactPrefix).split(":")
                if (parts.size >= 2 && !msg.isFromMe) {
                    val targetMsgId = parts[0]
                    val emoji = parts[1]
                    val updated = LinkedHashMap<String, Int>()
                    messageReactions[targetMsgId]?.let { updated.putAll(it) }
                    updated[emoji] = (updated[emoji] ?: 0) + 1
                    messageReactions[targetMsgId] = updated
                    if (now - msg.timestamp < 20_000L) {
                        reactionAnimationState.trigger(emoji)
                    }
                }
            }
        }
    }
    val displayMessages = remember(messages) {
        messages.filter { !it.content.startsWith(reactPrefix) }.map { it.toBitchatMessage() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val activeReaction = reactionAnimationState.activeReaction.value
        if (activeReaction != null) {
            com.seamless.android.ui.effects.ReactionAnimationOverlay(
                effect = activeReaction,
                onComplete = { reactionAnimationState.clear() }
            )
        }
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Compact collapsible header - shows mesh info, expands to show members
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMembers = !showMembers },
            color = MeshColors.PrivateCardBg
        ) {
            Column {
                // Compact header row - always visible
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small room icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MeshColors.PrivateViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MeetingRoom,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MeshColors.PrivateViolet
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    // Mesh name and member count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meshName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeshColors.TextPrimary
                        )
                        Text(
                            text = "${members.size} member${if (members.size != 1) "s" else ""} • Connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshColors.PrivateViolet.copy(alpha = 0.8f)
                        )
                    }
                    
                    // Animated expand/collapse icon
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (showMembers) 180f else 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "chevron_rotation"
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (showMembers) "Collapse" else "Expand",
                        modifier = Modifier.rotate(rotationAngle),
                        tint = MeshColors.TextSecondary
                    )
                }
                
                // Expanded content with smooth animation
                AnimatedVisibility(
                    visible = showMembers,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 400f
                        )
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 400f
                        )
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MeshColors.PrivateViolet.copy(alpha = 0.3f))
                        
                        // Members list
                        members.forEach { member ->
                            MemberCard(
                                member = member,
                                isHost = false,
                                onKick = null
                            )
                        }
                        
                        // Leave button
                        OutlinedButton(
                            onClick = { meshManager.leaveMesh() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MeshColors.ErrorRed
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeshColors.ErrorRed.copy(alpha = 0.5f))
                        ) {
                            Text("Leave Mesh")
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = MeshColors.GlassBorder)

        
        if (meshService != null) {
            MessagesList(
                messages = displayMessages,
                currentUserNickname = myNickname,
                meshService = meshService,
                modifier = Modifier.weight(1f),
                isPrivateChat = true,
                connectedPeersCount = members.size,
                onNicknameClick = { nickname ->
                    val currentText = messageText.text
                    val mentionText = "@$nickname "
                    val newText = if (currentText.isEmpty()) mentionText else "$currentText $mentionText"
                    messageText = androidx.compose.ui.text.input.TextFieldValue(
                        text = newText,
                        selection = androidx.compose.ui.text.TextRange(newText.length)
                    )
                },
                onReactionSelected = { message, emoji ->
                    processedReactionMsgIds.add("local:${message.id}:$emoji")
                    val existing = LinkedHashMap(messageReactions[message.id] ?: emptyMap())
                    existing[emoji] = (existing[emoji] ?: 0) + 1
                    messageReactions[message.id] = existing
                    reactionAnimationState.trigger(emoji)
                    meshManager.sendMessage("$reactPrefix${message.id}:$emoji")
                },
                messageReactions = messageReactions
            )
        } else {
            Box(Modifier.weight(1f))
        }
        
        // Message input
        MessageInput(
            value = messageText,
            onValueChange = { messageText = it },
            onSend = {
                if (messageText.text.isNotBlank()) {
                    meshManager.sendMessage(messageText.text.trim())
                    messageText = androidx.compose.ui.text.input.TextFieldValue("")
                }
            },
            onSendVoiceNote = { _, _, filePath ->
                meshManager.sendVoiceNote(filePath)
            },
            onSendImageNote = { _, _, filePath ->
                meshManager.sendImage(filePath)
            },
            onSendFileNote = { _, _, _ -> },
            selectedPrivatePeer = null,
            currentChannel = null,
            nickname = myNickname,
            showMediaButtons = true
        )
    }
    }
}

// Extension to bridge data models
private fun PrivateMeshMessage.toBitchatMessage(): com.seamless.android.model.BitchatMessage {
    // Map PrivateMeshMessageType to BitchatMessageType
    val messageType = when (this.type) {
        com.seamless.android.mesh.PrivateMeshMessageType.AUDIO -> com.seamless.android.model.BitchatMessageType.Audio
        com.seamless.android.mesh.PrivateMeshMessageType.IMAGE -> com.seamless.android.model.BitchatMessageType.Image
        else -> com.seamless.android.model.BitchatMessageType.Message
    }
    
    return com.seamless.android.model.BitchatMessage(
        id = this.id,
        sender = this.senderNickname,
        content = this.content,
        timestamp = java.util.Date(this.timestamp),
        senderPeerID = this.senderPeerID,
        isPrivate = true, // Treat as private for UI purposes
        isRelay = false, // Not relevant for private mesh messages
        originalSender = null,
        recipientNickname = null,
        mentions = null,
        channel = null,
        type = messageType,
        // Since we don't track delivery status in PrivateMeshMessage, assume delivered/sent
        deliveryStatus = if (this.isFromMe) com.seamless.android.model.DeliveryStatus.Sent else com.seamless.android.model.DeliveryStatus.Read(by = "me", at = java.util.Date(this.timestamp))
    )
}




/**
 * Holographic joining content with pulsing rings and animated waiting text
 */
@Composable
private fun JoiningContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "joining")

    // Multiple pulsing ring animations
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "jr1s"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "jr1a"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "jr2s"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "jr2a"
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "jr3s"
    )
    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "jr3a"
    )

    // Floating bob animation for text
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // Animated dots cycle (0, 1, 2, 3 dots)
    val dotCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )
    val dotCount = dotCycle.toInt().coerceIn(0, 3)
    val dots = ".".repeat(dotCount)

    val violetColor = MeshColors.PrivateViolet
    val cyanColor = MeshColors.PrimaryCyan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Holographic pulsing rings
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f

                // Ring 1
                drawCircle(
                    color = violetColor.copy(alpha = ring1Alpha * 0.5f),
                    radius = maxRadius * ring1Scale,
                    center = center,
                    style = Stroke(width = 2f)
                )
                // Ring 2
                drawCircle(
                    color = cyanColor.copy(alpha = ring2Alpha * 0.4f),
                    radius = maxRadius * ring2Scale,
                    center = center,
                    style = Stroke(width = 2f)
                )
                // Ring 3
                drawCircle(
                    color = violetColor.copy(alpha = ring3Alpha * 0.3f),
                    radius = maxRadius * ring3Scale,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // Center icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                violetColor.copy(alpha = 0.25f),
                                cyanColor.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .drawBehind {
                        drawCircle(
                            color = violetColor.copy(alpha = 0.5f),
                            style = Stroke(width = 1.2f)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = violetColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Floating text with bob animation
        Text(
            text = "Waiting for host approval$dots",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MeshColors.TextPrimary,
            modifier = Modifier.offset(y = bobOffset.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "The host needs to accept your request",
            style = MaterialTheme.typography.bodyMedium,
            color = MeshColors.TextSecondary,
            modifier = Modifier.offset(y = (bobOffset * 0.5f).dp)
        )
    }
}
