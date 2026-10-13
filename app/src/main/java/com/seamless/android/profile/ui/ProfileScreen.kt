package com.seamless.android.profile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.seamless.android.profile.ProfileManager
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessPink
import com.seamless.android.ui.theme.SeamlessPurple
import com.seamless.android.ui.theme.SeamlessTeal
import com.seamless.android.ui.theme.rememberDimensions
import com.seamless.android.ui.theme.safeAreaPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Ultra-Premium Profile Screen
// Inspired by Apple · Google · Meta · Instagram
// Features: Canvas particle background, breathing avatar glow, staggered
//           spring entrances, glassmorphic cards, animated gradient shimmer,
//           and fluid mode morphing.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    isInitialSetup: Boolean = false,
    onSetupComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileManager = remember { ProfileManager.getInstance(context) }
    val colorScheme = MaterialTheme.colorScheme

    // ── Core State ───────────────────────────────────────────────────────
    var isEditing by remember { mutableStateOf(isInitialSetup) }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarStyle by remember { mutableStateOf("aurora") }
    var isSaving by remember { mutableStateOf(false) }

    // ── Staggered Entrance Visibility Flags ──────────────────────────────
    var showAvatar by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profile = profileManager.currentUserProfile.value
        if (profile != null) {
            displayName = profile.displayName
            bio = profile.bio
            avatarStyle = profile.avatarStyle
            profile.avatarUri?.let { avatarUri = Uri.parse(it) }
        }
        // Staggered entrance cascade
        delay(150)
        showAvatar = true
        delay(250)
        showContent = true
        delay(200)
        showCards = true
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) avatarUri = uri
    }

    // ── Infinite Animations ──────────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "global_inf")

    // Avatar ring rotation
    val ringRotation by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "ring_rot"
    )
    // Avatar breathing glow (outer halo)
    val breatheScale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe_scale"
    )
    val breatheAlpha by inf.animateFloat(
        initialValue = 0.45f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe_alpha"
    )
    // Background floating orb offset
    val orbPhase by inf.animateFloat(
        initialValue = 0f, targetValue = 6.2832f, // 2π
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "orb_phase"
    )
    // Button shimmer
    val shimmerX by inf.animateFloat(
        initialValue = -500f, targetValue = 1500f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "shimmer_x"
    )

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) (if (isInitialSetup) "Create Profile" else "Edit Profile") else "Profile",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                    )
                },
                navigationIcon = {
                    if (!isInitialSetup) {
                        IconButton(onClick = {
                            if (isEditing) isEditing = false else onBackClick()
                        }) {
                            Icon(
                                imageVector = if (isEditing) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (!isEditing && !isInitialSetup) {
                        val editBtnInteraction = remember { MutableInteractionSource() }
                        val editBtnPressed by editBtnInteraction.collectIsPressedAsState()
                        val editBtnScale by animateFloatAsState(
                            targetValue = if (editBtnPressed) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "edit_btn_scale"
                        )
                        Surface(
                            onClick = { isEditing = true },
                            interactionSource = editBtnInteraction,
                            shape = RoundedCornerShape(20.dp),
                            color = SeamlessCyan.copy(alpha = 0.12f),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .scale(editBtnScale)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Edit, "Edit",
                                    tint = SeamlessCyan, modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Edit", style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold, color = SeamlessCyan
                                    )
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Layer 0: Animated Background ─────────────────────────────
            // Canvas-drawn floating orbs + gradient wash
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val w = size.width
                val h = size.height

                // Floating gradient orbs that drift slowly
                drawFloatingOrb(
                    center = Offset(
                        w * 0.2f + cos(orbPhase) * w * 0.08f,
                        h * 0.12f + sin(orbPhase * 0.7f) * h * 0.04f
                    ),
                    radius = w * 0.32f,
                    color = SeamlessCyan.copy(alpha = 0.07f)
                )
                drawFloatingOrb(
                    center = Offset(
                        w * 0.85f + sin(orbPhase * 1.3f) * w * 0.06f,
                        h * 0.08f + cos(orbPhase * 0.5f) * h * 0.03f
                    ),
                    radius = w * 0.28f,
                    color = SeamlessPurple.copy(alpha = 0.06f)
                )
                drawFloatingOrb(
                    center = Offset(
                        w * 0.5f + cos(orbPhase * 0.9f) * w * 0.1f,
                        h * 0.35f + sin(orbPhase * 0.6f) * h * 0.05f
                    ),
                    radius = w * 0.22f,
                    color = SeamlessPink.copy(alpha = 0.04f)
                )
            }

            // ── Main Scrollable Content ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .safeAreaPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Welcome Header (initial setup only) ──────────────────
                if (isInitialSetup) {
                    AnimatedVisibility(
                        visible = showContent,
                        enter = fadeIn(tween(600)) + slideInVertically(
                            initialOffsetY = { -30 },
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "Welcome to Seamless",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                            Text(
                                text = "Set up your identity to start chatting",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colorScheme.onSurfaceVariant
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Avatar Section ───────────────────────────────────────
                AnimatedVisibility(
                    visible = showAvatar,
                    enter = fadeIn(tween(500)) + scaleIn(
                        initialScale = 0.7f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
                    )
                ) {
                    val avatarInteraction = remember { MutableInteractionSource() }
                    val avatarPressed by avatarInteraction.collectIsPressedAsState()
                    val avatarScale by animateFloatAsState(
                        targetValue = if (avatarPressed && isEditing) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "av_s"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(170.dp)
                            .scale(avatarScale)
                            .clickable(
                                interactionSource = avatarInteraction,
                                indication = null,
                                enabled = isEditing
                            ) { imagePickerLauncher.launch("image/*") }
                    ) {
                        // Breathing outer halo glow
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .scale(breatheScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            SeamlessCyan.copy(alpha = breatheAlpha),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Rotating gradient ring (Instagram stories)
                        Box(
                            modifier = Modifier
                                .size(155.dp)
                                .rotate(ringRotation)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        colors = listOf(
                                            SeamlessCyan,
                                            Color(0xFF6366F1), // Indigo
                                            SeamlessPurple,
                                            SeamlessPink,
                                            Color(0xFFFF6B6B), // Coral
                                            SeamlessTeal,
                                            SeamlessCyan
                                        )
                                    )
                                )
                        )

                        // Background gap ring
                        Box(
                            modifier = Modifier
                                .size(148.dp)
                                .clip(CircleShape)
                                .background(colorScheme.background)
                        )

                        // Actual avatar image / placeholder
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                        ) {
                            if (avatarUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(avatarUri),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                                    colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        "Default Avatar",
                                        modifier = Modifier.size(64.dp),
                                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        // Camera badge (edit mode)
                        if (isEditing) {
                            Surface(
                                shape = CircleShape,
                                color = SeamlessCyan,
                                border = BorderStroke(3.dp, colorScheme.background),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-4).dp, y = (-4).dp)
                                    .size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CameraAlt, "Change Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Content: Edit vs View Mode ───────────────────────────
                AnimatedContent(
                    targetState = isEditing,
                    transitionSpec = {
                        (fadeIn(tween(400, delayMillis = 100)) +
                                slideInVertically(
                                    initialOffsetY = { 60 },
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
                                )) togetherWith
                                (fadeOut(tween(250)) + slideOutVertically(
                                    targetOffsetY = { -40 },
                                    animationSpec = tween(250)
                                ))
                    },
                    label = "mode_switch"
                ) { editing ->
                    if (editing) {
                        EditModeContent(
                            displayName = displayName,
                            onDisplayNameChange = { if (it.length <= 30) displayName = it },
                            bio = bio,
                            onBioChange = { if (it.length <= 100) bio = it },
                            avatarStyle = avatarStyle,
                            onAvatarStyleChange = { avatarStyle = it },
                            isSaving = isSaving,
                            isInitialSetup = isInitialSetup,
                            shimmerX = shimmerX,
                            onSave = {
                                if (displayName.isNotBlank() && !isSaving) {
                                    isSaving = true
                                    scope.launch {
                                        var finalUri = avatarUri
                                        val currentProfile = profileManager.currentUserProfile.value
                                        if (avatarUri != null && avatarUri.toString() != currentProfile?.avatarUri) {
                                            val savedUri = profileManager.saveAvatarImage(avatarUri!!)
                                            if (savedUri != null) finalUri = savedUri
                                        }
                                        profileManager.saveProfile(displayName, finalUri, bio, avatarStyle)
                                        isSaving = false
                                        isEditing = false
                                        if (isInitialSetup) onSetupComplete()
                                    }
                                }
                            }
                        )
                    } else {
                        ViewModeContent(
                            displayName = displayName,
                            bio = bio,
                            showCards = showCards,
                            showContent = showContent
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// EDIT MODE
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun EditModeContent(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    avatarStyle: String,
    onAvatarStyleChange: (String) -> Unit,
    isSaving: Boolean,
    isInitialSetup: Boolean,
    shimmerX: Float,
    onSave: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // Staggered field entrance
    var showField1 by remember { mutableStateOf(false) }
    var showField2 by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        showField1 = true
        delay(180)
        showField2 = true
        delay(180)
        showButton = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Display Name ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = showField1,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { 50 },
                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow)
            )
        ) {
            PremiumTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = "Display Name",
                placeholder = "Your nickname",
                icon = Icons.Default.Person,
                accentColor = SeamlessCyan,
                maxLength = 30,
                singleLine = true
            )
        }

        // ── Bio ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showField2,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { 50 },
                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow)
            )
        ) {
            PremiumTextField(
                value = bio,
                onValueChange = onBioChange,
                label = "Bio",
                placeholder = "Tell the mesh about yourself...",
                icon = Icons.Default.AutoAwesome,
                accentColor = SeamlessPurple,
                maxLength = 100,
                singleLine = false,
                maxLines = 3
            )
        }

        AnimatedVisibility(
            visible = showField2,
            enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 40 })
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Contact avatar",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Shared with new Tor contacts without sending your profile photo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("aurora", "ember", "tide", "orchid", "mint", "copper").forEach { style ->
                        FilterChip(
                            selected = avatarStyle == style,
                            onClick = { onAvatarStyleChange(style) },
                            label = { Text(style.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Save / Get Started Button ────────────────────────────────
        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { 60 },
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
            )
        ) {
            val btnInteraction = remember { MutableInteractionSource() }
            val btnPressed by btnInteraction.collectIsPressedAsState()
            val btnScale by animateFloatAsState(
                targetValue = if (btnPressed) 0.95f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "btn_s"
            )
            val isValid = displayName.isNotBlank() && !isSaving

            Button(
                onClick = onSave,
                enabled = isValid,
                interactionSource = btnInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .scale(btnScale),
                shape = RoundedCornerShape(29.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (isValid) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        SeamlessCyan,
                                        Color(0xFF6366F1),
                                        SeamlessPurple,
                                        SeamlessPink
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            },
                            shape = RoundedCornerShape(29.dp)
                        )
                        // Shimmer overlay
                        .drawBehind {
                            if (isValid) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.18f),
                                            Color.Transparent
                                        ),
                                        startX = shimmerX - 200f,
                                        endX = shimmerX + 200f
                                    )
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White, strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isInitialSetup) "Get Started" else "Save Changes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isValid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    letterSpacing = 0.3.sp
                                )
                            )
                            if (isValid) {
                                Icon(
                                    Icons.Default.ArrowForward, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// VIEW MODE
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ViewModeContent(
    displayName: String,
    bio: String,
    showCards: Boolean,
    showContent: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Name + Bio ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(500)) + slideInVertically(
                initialOffsetY = { 30 },
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = displayName.ifEmpty { "Anonymous" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        shadow = Shadow(
                            color = SeamlessCyan.copy(alpha = 0.15f),
                            blurRadius = 16f
                        )
                    )
                )
                if (bio.isNotBlank()) {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = colorScheme.onBackground.copy(alpha = 0.65f),
                            lineHeight = 24.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        // ── Info Cards (Glassmorphic) ────────────────────────────────
        AnimatedVisibility(
            visible = showCards,
            enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(
                initialOffsetY = { 50 },
                animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow)
            )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassInfoCard(
                    icon = Icons.Default.WifiTethering,
                    title = "Mesh Status",
                    value = "Online",
                    accentColor = SeamlessColors.Success,
                    subtitle = "Connected to local mesh network"
                )
                GlassInfoCard(
                    icon = Icons.Default.Shield,
                    title = "Identity",
                    value = "Encrypted",
                    accentColor = SeamlessCyan,
                    subtitle = "End-to-end encrypted local identity"
                )
                GlassInfoCard(
                    icon = Icons.Default.SignalCellularAlt,
                    title = "Network",
                    value = "P2P",
                    accentColor = SeamlessPurple,
                    subtitle = "Peer-to-peer mesh via Bluetooth"
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// REUSABLE PREMIUM COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Premium text field with glassmorphic container, accent glow, and character counter.
 */
@Composable
private fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    accentColor: Color,
    maxLength: Int,
    singleLine: Boolean,
    maxLines: Int = 1
) {
    val colorScheme = MaterialTheme.colorScheme
    val isFocused = remember { mutableStateOf(false) }
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused.value) 1f else 0.25f,
        animationSpec = tween(300),
        label = "border_alpha"
    )
    val containerAlpha by animateFloatAsState(
        targetValue = if (isFocused.value) 0.12f else 0.06f,
        animationSpec = tween(300),
        label = "container_alpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                icon, null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )
        }

        // Text field in a glass container
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = accentColor.copy(alpha = containerAlpha),
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = borderAlpha)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(it)
                        isFocused.value = true
                    },
                    placeholder = {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                    },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else maxLines,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = accentColor
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )

                // Character counter bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${value.length}/$maxLength",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (value.length >= maxLength)
                                SeamlessPink
                            else
                                colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

/**
 * Glassmorphic info card for profile stats (view mode).
 */
@Composable
private fun GlassInfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon container with accent glow
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                )
            }

            // Value badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ── Canvas Helpers ───────────────────────────────────────────────────────────

private fun DrawScope.drawFloatingOrb(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
