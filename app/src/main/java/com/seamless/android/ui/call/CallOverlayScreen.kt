package com.seamless.android.ui.call

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.seamless.android.call.AudioRoute
import com.seamless.android.call.CallSessionInfo
import com.seamless.android.call.CallState
import com.seamless.android.call.CallTransportMode
import com.seamless.android.call.SeamlessCallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Obsidian & Gemstone Psychological Palette ──
private val CallBgTop = Color(0xFF06080E)
private val CallBgMid = Color(0xFF0A0E17)
private val CallBgBottom = Color(0xFF04060A)

private val GlassSurfaceDark = Color(0xCC0B0F18)
private val GlassPillDark = Color(0xB8090C14)
private val GlassCardSurface = Color(0x14F4F7FC)
private val GlassBorderTop = Color(0x38FFFFFF)
private val GlassBorderBottom = Color(0x0DFFFFFF)

private val TextPlatinum = Color(0xFFF3F6FB)
private val TextSilver = Color(0xFFB4BFCE)
private val TextMutedSlate = Color(0xFF7B879B)

private val AccentDirect = Color(0xFF38BDF8)
private val AccentDirectDeep = Color(0xFF818CF8)
private val AccentTor = Color(0xFF34D399)
private val AccentTorCore = Color(0xFF10B981)
private val AccentStrict = Color(0xFFA78BFA)
private val AccentStrictCore = Color(0xFF7C3AED)

private val EngagedPearl = Color(0xFFF2F5FA)
private val EngagedInk = Color(0xFF0B0E16)
private val DangerCrimsonTop = Color(0xFFFF4D6A)
private val DangerCrimsonBottom = Color(0xFFDC2646)
private val DangerRed = Color(0xFFFB4F68)
private val MutedCoral = Color(0xFFF43F5E)
private val WarnGold = Color(0xFFFBBF24)

private enum class LinkQuality(val label: String, val bars: Int, val color: Color) {
    EXCELLENT("Excellent", 4, Color(0xFF34D399)),
    GOOD("Good", 3, Color(0xFF38BDF8)),
    FAIR("Fair", 2, Color(0xFFFBBF24)),
    POOR("Weak", 1, Color(0xFFFB923C)),
    RECONNECTING("Reconnecting", 0, Color(0xFFFB4F68))
}

/**
 * Global P2P + Tor Voice Call overlay: full-screen incoming/active/ended experience
 * plus the minimized floating call pill.
 *
 * - Caches the last session so exit animations never snap.
 * - Full-screen surface blocks all touches from leaking to screens beneath it.
 * - 50 Hz voice/packet flows are collected only in small leaf composables.
 * - Swipe down anywhere on the call screen to minimize (except while ringing).
 */
@Composable
fun SeamlessCallOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callManager = remember { SeamlessCallManager.getInstance(context) }
    val liveSession by callManager.sessionFlow.collectAsState()

    DisposableEffect(lifecycleOwner, callManager) {
        callManager.videoEngine.setLifecycleOwner(lifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    callManager.videoEngine.setLifecycleOwner(lifecycleOwner)
                    callManager.onAppForegroundStateChanged(true)
                }
                Lifecycle.Event.ON_STOP -> callManager.onAppForegroundStateChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var cachedSession by remember { mutableStateOf<CallSessionInfo?>(null) }
    val activeSession = liveSession
    if (activeSession != null && activeSession.state != CallState.IDLE) {
        cachedSession = activeSession
    }
    LaunchedEffect(activeSession) {
        if (activeSession == null || activeSession.state == CallState.IDLE) {
            delay(250L)
            if (liveSession == null || liveSession?.state == CallState.IDLE) {
                cachedSession = null
            }
        }
    }

    val isOverlayVisible = activeSession != null && activeSession.state != CallState.IDLE
    val displaySession = activeSession?.takeIf { it.state != CallState.IDLE } ?: cachedSession ?: return

    var pendingAnswerWithVideo by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (callManager.sessionFlow.value?.state == CallState.INCOMING_RINGING) {
                callManager.acceptIncomingCall(withVideo = false)
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for encrypted calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val camPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            callManager.toggleLocalVideo(true)
        } else {
            Toast.makeText(
                context,
                "Camera permission is required for encrypted video",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val multiMediaPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasMic = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCam = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            if (callManager.sessionFlow.value?.state == CallState.INCOMING_RINGING) {
                callManager.acceptIncomingCall(withVideo = pendingAnswerWithVideo && hasCam)
                if (pendingAnswerWithVideo && !hasCam) {
                    Toast.makeText(context, "Camera denied — answered with voice only", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for encrypted calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val isMinimized = isOverlayVisible &&
        displaySession.isMinimized &&
        displaySession.state != CallState.INCOMING_RINGING &&
        displaySession.state != CallState.ENDED
    val isFullScreen = isOverlayVisible && !isMinimized

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(30f)
    ) {
        AnimatedVisibility(
            visible = isMinimized,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            MinimizedCallBanner(
                session = displaySession,
                callManager = callManager,
                onExpand = { callManager.setMinimized(false) },
                onToggleMute = { callManager.toggleMute() },
                onToggleVideo = {
                    val hasCam = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCam || displaySession.isLocalVideoEnabled) {
                        callManager.toggleLocalVideo()
                    } else {
                        camPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onHangUp = { callManager.endCall() }
            )
        }

        AnimatedVisibility(
            visible = isFullScreen,
            enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(tween(260)),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenCallView(
                session = displaySession,
                callManager = callManager,
                onMinimize = {
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    val locked = km?.isKeyguardLocked == true
                    val s = callManager.sessionFlow.value
                    if (!locked && s != null && s.state != CallState.INCOMING_RINGING && s.state != CallState.ENDED) {
                        callManager.setMinimized(true)
                    }
                },
                onAccept = { withVideo ->
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCam = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!withVideo) {
                        if (hasMic) {
                            callManager.acceptIncomingCall(withVideo = false)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        if (hasMic && hasCam) {
                            callManager.acceptIncomingCall(withVideo = true)
                        } else {
                            pendingAnswerWithVideo = true
                            val needed = buildList {
                                if (!hasMic) add(Manifest.permission.RECORD_AUDIO)
                                if (!hasCam) add(Manifest.permission.CAMERA)
                            }.toTypedArray()
                            multiMediaPermLauncher.launch(needed)
                        }
                    }
                },
                onDecline = { callManager.declineIncomingCall() },
                onHangUp = { callManager.endCall() },
                onToggleMute = { callManager.toggleMute() },
                onToggleVideo = {
                    val hasCam = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCam || displaySession.isLocalVideoEnabled) {
                        callManager.toggleLocalVideo()
                    } else {
                        camPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onSwitchCamera = { callManager.switchCamera() },
                onSwapVideos = { callManager.toggleSwapVideoViews() },
                onToggleSpeaker = { callManager.toggleSpeakerphone() },
                onToggleTorOnly = { callManager.toggleForceTorOnly() },
                onRefreshRoute = {
                    callManager.refreshCallRoute()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Minimized pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MinimizedCallBanner(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    onExpand: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onHangUp: () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val tiltState = rememberTiltLightState(enabled = true)
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    val accentColor by animateColorAsState(
        targetValue = accentFor(session),
        animationSpec = tween(500),
        label = "miniAccent"
    )

    val remoteLevel by callManager.remoteVoiceLevelFlow.collectAsState()
    val localLevel by callManager.localVoiceLevelFlow.collectAsState()
    val activeVoice by animateFloatAsState(
        targetValue = max(remoteLevel, localLevel).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
        label = "miniVoice"
    )
    val eqTransition = rememberInfiniteTransition(label = "miniEq")
    val eqPhase by eqTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "miniEqPhase"
    )
    val rimShift by eqTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "miniRimShift"
    )

    val shape = RoundedCornerShape(30.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = shape,
            color = Color(0xF505070B),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .shadow(
                    elevation = 22.dp,
                    shape = shape,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = Color.Black
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.14f),
                            (0.20f + rimShift * 0.60f) to Color.White.copy(alpha = 0.36f),
                            1.0f to accentColor.copy(alpha = 0.32f + activeVoice * 0.45f)
                        )
                    ),
                    shape = shape
                )
                .drawWithContent {
                    drawContent()
                    val tilt = tiltState.value
                    val cx = size.width * (0.5f + tilt.x * 0.34f)
                    val cy = size.height * (0.25f + tilt.y * 0.25f)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = size.maxDimension * 0.45f
                        )
                    )
                }
                .clickable {
                    callHaptics.toggleOn(view)
                    onExpand()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Beveled obsidian mini avatar with voice-reactive halo
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                val s = 1f + activeVoice * 0.20f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF1A2232), Color(0xFF0E131E))))
                            .border(
                                width = 1.4.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.35f), accentColor)
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = session.peerNickname.firstOrNull()?.uppercase() ?: "?",
                            color = TextPlatinum,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.peerNickname,
                        color = TextPlatinum,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusSub = if (session.state == CallState.ACTIVE) {
                        val mediaPrefix = if (session.isLocalVideoEnabled || session.isRemoteVideoEnabled) {
                            "HD Video • "
                        } else ""
                        val transportLabel = when {
                            session.forceTorOnly -> "Tor Strict"
                            session.isMultipathActive && isDirectP2p -> "P2P + Tor"
                            isDirectP2p -> "Direct P2P"
                            else -> "Tor Onion"
                        }
                        "$mediaPrefix${formatDuration(session.durationSeconds)} • $transportLabel"
                    } else {
                        session.statusMessage
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (session.state == CallState.ACTIVE) {
                            Canvas(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(width = 15.dp, height = 11.dp)
                            ) {
                                val barW = 2.dp.toPx()
                                val gap = (size.width - barW * 4f) / 3f
                                for (b in 0..3) {
                                    val osc = sin(eqPhase + b * 1.25f) * 0.5f + 0.5f
                                    val frac = (0.28f + activeVoice * 0.72f * osc).coerceIn(0.24f, 1f)
                                    val h = size.height * frac
                                    drawRoundRect(
                                        color = accentColor,
                                        topLeft = Offset(b * (barW + gap), (size.height - h) / 2f),
                                        size = Size(barW, h),
                                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                    )
                                }
                            }
                        }
                        Text(
                            text = statusSub,
                            color = accentColor.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (session.state == CallState.ACTIVE) {
                    val camShapeCorner by animateDpAsState(
                        targetValue = if (session.isLocalVideoEnabled) 12.dp else 18.dp,
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                        label = "miniCamCorner"
                    )
                    val camShape = RoundedCornerShape(camShapeCorner)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(camShape)
                            .background(
                                if (session.isLocalVideoEnabled) EngagedPearl else Color(0x1CFFFFFF)
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (session.isLocalVideoEnabled) Color.Transparent else Color.White.copy(alpha = 0.14f),
                                shape = camShape
                            )
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                if (session.isLocalVideoEnabled) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                                onToggleVideo()
                            }
                    ) {
                        AnimatedCameraSymbol(
                            isEnabled = session.isLocalVideoEnabled,
                            tint = if (session.isLocalVideoEnabled) EngagedInk else TextPlatinum,
                            backingColor = if (session.isLocalVideoEnabled) EngagedPearl else Color(0xFF1A202C),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                val micShapeCorner by animateDpAsState(
                    targetValue = if (session.isMuted) 12.dp else 18.dp,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                    label = "miniMicCorner"
                )
                val micShape = RoundedCornerShape(micShapeCorner)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(micShape)
                        .background(if (session.isMuted) MutedCoral else Color(0x1CFFFFFF))
                        .border(
                            width = 0.8.dp,
                            color = if (session.isMuted) Color.Transparent else Color.White.copy(alpha = 0.14f),
                            shape = micShape
                        )
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            if (session.isMuted) callHaptics.unmuteLive(view) else callHaptics.muteEngaged(view)
                            onToggleMute()
                        }
                ) {
                    AnimatedMicSymbol(
                        isMuted = session.isMuted,
                        tint = if (session.isMuted) Color.White else TextPlatinum,
                        backingColor = if (session.isMuted) MutedCoral else Color(0xFF1A202C),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(DangerCrimsonTop, DangerCrimsonBottom)))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            callHaptics.callEnded()
                            onHangUp()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = "Expand call",
                    tint = TextSilver,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full-screen call view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullScreenCallView(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    onMinimize: () -> Unit,
    onAccept: (withVideo: Boolean) -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSwapVideos: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleTorOnly: () -> Unit,
    onRefreshRoute: () -> Unit
) {
    val context = LocalContext.current
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyguardManager = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    val isKeyguardLocked = keyguardManager?.isKeyguardLocked == true
    val canMinimize = session.state != CallState.INCOMING_RINGING && session.state != CallState.ENDED && !isKeyguardLocked

    BackHandler {
        if (canMinimize) onMinimize()
    }

    val primaryAccent by animateColorAsState(
        targetValue = accentFor(session),
        animationSpec = tween(600),
        label = "primaryAccent"
    )
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    val posterTheme = remember(session.peerOnion, session.peerNickname) {
        contactPosterFor(session.peerOnion.ifBlank { session.peerNickname })
    }
    val secondaryAccent by animateColorAsState(
        targetValue = when {
            session.forceTorOnly -> AccentStrictCore
            isDirectP2p -> AccentDirectDeep
            else -> AccentDirect
        },
        animationSpec = tween(600),
        label = "secondaryAccent"
    )

    val isRemoteVideo = session.state == CallState.ACTIVE && session.isRemoteVideoEnabled
    val isLocalVideo = session.isLocalVideoEnabled && session.state != CallState.ENDED && session.state != CallState.IDLE
    val isSwapped = session.isVideoSwapped && isLocalVideo && isRemoteVideo
    val isPreAnswerSelfBackdrop = isLocalVideo && !isRemoteVideo &&
        (session.state == CallState.OUTGOING_DIALING || session.state == CallState.OUTGOING_RINGING)
    val hasFullScreenVideo = isRemoteVideo || isSwapped || isPreAnswerSelfBackdrop
    val isImmersiveActiveVideo = session.state == CallState.ACTIVE && (isRemoteVideo || isSwapped)
    val showPip = session.state == CallState.ACTIVE && ((isLocalVideo && !isSwapped) || (isRemoteVideo && isSwapped))

    val tiltState = rememberTiltLightState(enabled = session.state != CallState.ENDED && !hasFullScreenVideo)
    var prevCallState by remember(session.callId) { mutableStateOf(session.state) }
    val connectionBloom = remember(session.callId) { Animatable(0f) }
    var endedHapticPlayed by remember(session.callId) { mutableStateOf(false) }
    LaunchedEffect(session.state, session.callId) {
        val wasPreActive = prevCallState == CallState.OUTGOING_DIALING ||
            prevCallState == CallState.OUTGOING_RINGING ||
            prevCallState == CallState.INCOMING_RINGING
        if (wasPreActive && session.state == CallState.ACTIVE) {
            callHaptics.callConnected()
            connectionBloom.snapTo(0f)
            connectionBloom.animateTo(1f, tween(durationMillis = 760, easing = FastOutSlowInEasing))
        } else if (session.state == CallState.ENDED && prevCallState != CallState.ENDED && !endedHapticPlayed) {
            endedHapticPlayed = true
            callHaptics.callEnded()
        }
        prevCallState = session.state
    }

    var hudToastMessage by remember(session.callId) { mutableStateOf<String?>(null) }
    var hudToastToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudToastToken) {
        if (hudToastToken > 0) {
            delay(1_750L)
            hudToastMessage = null
        }
    }
    fun showHudPill(msg: String) {
        hudToastMessage = msg
        hudToastToken++
    }

    val fallbackFirstFrame by callManager.videoEngine.remoteFirstFrameRendered.collectAsState()
    val webrtcFirstFrame by callManager.webrtcVideoEngine.remoteFirstFrame.collectAsState()
    val webrtcConnected by callManager.webrtcVideoEngine.mediaConnected.collectAsState()
    val webrtcStats by callManager.webrtcVideoEngine.stats.collectAsState()
    val isSwitchingCamera by callManager.webrtcVideoEngine.isSwitchingCamera.collectAsState()
    val fallbackResLabel by callManager.videoEngine.remoteResolutionLabel.collectAsState()
    val remoteFirstFrame = webrtcFirstFrame || fallbackFirstFrame
    val hasActiveVideoStream = session.isLocalVideoEnabled || session.isRemoteVideoEnabled
    val remoteResLabel = if (webrtcConnected && !session.forceTorOnly) {
        webrtcStats.qualityLabel
    } else {
        fallbackResLabel
    }
    val activeCodecLabel = when {
        webrtcConnected && webrtcStats.codec.isNotBlank() -> webrtcStats.codec
        webrtcConnected -> "H.264"
        else -> "Tor Video"
    }

    var telemetryExpanded by remember(session.callId) { mutableStateOf(false) }

    var isCropFill by remember(session.callId) { mutableStateOf(true) }
    var isHudVisible by remember(session.callId) { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableIntStateOf(0) }
    val flipRotationY = remember { Animatable(0f) }

    LaunchedEffect(isImmersiveActiveVideo, hudInteractionTick, isHudVisible) {
        if (!isImmersiveActiveVideo) {
            isHudVisible = true
        } else if (isHudVisible) {
            delay(3_800L)
            isHudVisible = false
        }
    }

    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val minThresholdPx = with(density) { 140.dp.toPx() }
    var wasPastMinThreshold by remember { mutableStateOf(false) }
    val dragCornerDp = ((dragOffset.value / minThresholdPx).coerceIn(0f, 1f) * 38f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset.value * 0.55f
                val shrink = (dragOffset.value / 1900f).coerceIn(0f, 0.10f)
                scaleX = 1f - shrink
                scaleY = 1f - shrink
            }
            .clip(RoundedCornerShape(if (dragOffset.value > 1f) dragCornerDp else 0.dp))
            .background(Brush.verticalGradient(listOf(CallBgTop, CallBgMid, CallBgBottom)))
            .pointerInput(isImmersiveActiveVideo, hasFullScreenVideo) {
                detectTapGestures(
                    onTap = {
                        if (isImmersiveActiveVideo) {
                            val nextHud = !isHudVisible
                            if (nextHud) callHaptics.toggleOn(view) else callHaptics.toggleOff(view)
                            isHudVisible = nextHud
                            hudInteractionTick++
                        }
                    },
                    onDoubleTap = {
                        if (isRemoteVideo) {
                            val nextFill = !isCropFill
                            if (nextFill) callHaptics.toggleOn(view) else callHaptics.toggleOff(view)
                            isCropFill = nextFill
                            callManager.videoEngine.setRemoteCropFill(nextFill)
                            showHudPill(if (nextFill) "Fill Screen" else "Fit Frame")
                        }
                    }
                )
            }
            .pointerInput(canMinimize, minThresholdPx) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val shouldMinimize = canMinimize && dragOffset.value > minThresholdPx
                        wasPastMinThreshold = false
                        scope.launch {
                            if (shouldMinimize) {
                                callHaptics.toggleOff(view)
                                onMinimize()
                            } else {
                                dragOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 360f))
                            }
                        }
                    },
                    onDragCancel = {
                        wasPastMinThreshold = false
                        scope.launch { dragOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 360f)) }
                    },
                    onVerticalDrag = { change, amount ->
                        if (canMinimize) {
                            change.consume()
                            val next = (dragOffset.value + amount).coerceAtLeast(0f)
                            val nowPast = next > minThresholdPx
                            if (nowPast != wasPastMinThreshold) {
                                wasPastMinThreshold = nowPast
                                callHaptics.magneticSnap(view)
                            }
                            scope.launch {
                                dragOffset.snapTo(next)
                            }
                        }
                    }
                )
            }
    ) {
        // ── Layer 0 & 1: Aurora (paused during fullscreen video to save GPU/battery) or Full-Screen Video Canvas ──
        if (!hasFullScreenVideo) {
            AuroraBackground(
                primaryAccent = primaryAccent,
                secondaryAccent = secondaryAccent,
                posterAura = posterTheme.aura,
                posterSecondaryAura = posterTheme.secondaryAura,
                tiltState = tiltState,
                isEnded = session.state == CallState.ENDED
            )
        }

        if (isRemoteVideo && !isSwapped) {
            RemoteVideoTextureHost(
                callManager = callManager,
                cropFill = isCropFill,
                modifier = Modifier.fillMaxSize()
            )
            if (!remoteFirstFrame) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x5506080E))
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = GlassPillDark,
                        modifier = Modifier.border(
                            width = 0.9.dp,
                            brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                            shape = RoundedCornerShape(50)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(primaryAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connecting encrypted P2P video…",
                                color = TextPlatinum,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else if (isSwapped) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = flipRotationY.value
                        cameraDistance = 12f * this.density
                    }
            ) {
                LocalCameraPreviewHost(
                    callManager = callManager,
                    lifecycleOwner = lifecycleOwner,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (isPreAnswerSelfBackdrop) {
            LocalCameraPreviewHost(
                callManager = callManager,
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xB806080E),
                                0.45f to Color(0x6606080E),
                                1.0f to Color(0xD904060A)
                            )
                        )
                    )
            )
        }

        // ── Layer 2: Cinematic Neutral Warm-Charcoal Vignette Scrims (Preserves Skin Tones) ──
        AnimatedVisibility(
            visible = hasFullScreenVideo,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(240)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xC808090D), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xD408090D))
                            )
                        )
                )
            }
        }

        // ── Layer 3: Draggable Magnetic Spring PiP Card ──
        AnimatedVisibility(
            visible = showPip,
            enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 360f)) + fadeIn(tween(220)),
            exit = scaleOut(tween(180)) + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            DraggableVideoPipCard(
                session = session,
                callManager = callManager,
                primaryAccent = primaryAccent,
                tiltState = tiltState,
                isHudVisible = isHudVisible,
                isSwapped = isSwapped,
                flipRotationDeg = flipRotationY.value,
                onSwapVideos = {
                    hudInteractionTick++
                    onSwapVideos()
                },
                onSwitchCamera = {
                    if (isSwitchingCamera) return@DraggableVideoPipCard
                    hudInteractionTick++
                    scope.launch {
                        flipRotationY.animateTo(90f, tween(130))
                        onSwitchCamera()
                        flipRotationY.animateTo(180f, tween(150))
                        flipRotationY.snapTo(0f)
                    }
                }
            ) {
                if (isSwapped) {
                    RemoteVideoTextureHost(
                        callManager = callManager,
                        cropFill = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LocalCameraPreviewHost(
                        callManager = callManager,
                        lifecycleOwner = lifecycleOwner,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ── Layer 4A: Compact Top Pill when Video HUD Auto-Hides ──
        AnimatedVisibility(
            visible = isImmersiveActiveVideo && !isHudVisible,
            enter = fadeIn(tween(220)) + slideInVertically { -it },
            exit = fadeOut(tween(180)) + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp)
        ) {
            CompactCollapsedHudPill(
                session = session,
                primaryAccent = primaryAccent,
                resolutionLabel = if (hasActiveVideoStream) {
                    if (session.forceTorOnly || !isDirectP2p) "360p Tor" else remoteResLabel
                } else {
                    "Wideband Voice"
                },
                onTapRestore = {
                    isHudVisible = true
                    hudInteractionTick++
                }
            )
        }

        // ── Layer 4B: Full HUD Overlay ──
        AnimatedVisibility(
            visible = !isImmersiveActiveVideo || isHudVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Top: grab handle + bar ──
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (canMinimize) {
                        val handlePast = dragOffset.value > minThresholdPx
                        val handleWidth by animateDpAsState(
                            targetValue = if (handlePast) 54.dp else 38.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                            label = "grabWidth"
                        )
                        Box(
                            modifier = Modifier
                                .width(handleWidth)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (handlePast) primaryAccent.copy(alpha = 0.85f)
                                    else Color.White.copy(alpha = 0.24f)
                                )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    CallTopBar(
                        session = session,
                        callManager = callManager,
                        primaryAccent = primaryAccent,
                        canMinimize = canMinimize,
                        onMinimize = onMinimize,
                        onToggleDiagnostics = {
                            if (session.state == CallState.ACTIVE) {
                                if (isImmersiveActiveVideo) {
                                    val rttPart = if (session.rttMs > 0) "${session.rttMs} ms" else "measuring"
                                    val routePart = if (session.forceTorOnly) "Tor Strict" else if (isDirectP2p) "Direct P2P" else "Tor Onion"
                                    showHudPill("$routePart • $rttPart • $activeCodecLabel")
                                } else {
                                    telemetryExpanded = !telemetryExpanded
                                }
                            }
                        }
                    )
                }

                // ── Center: Hide center orb/telemetry during full-screen active video so faces remain unobstructed ──
                if (!isImmersiveActiveVideo) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VoiceReactiveAvatarOrb(
                            peerNickname = session.peerNickname,
                            callState = session.state,
                            primaryAccent = primaryAccent,
                            secondaryAccent = secondaryAccent,
                            posterTop = posterTheme.top,
                            posterBottom = posterTheme.bottom,
                            tiltState = tiltState,
                            connectionBloom = connectionBloom.value,
                            callManager = callManager
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = session.peerNickname,
                            color = TextPlatinum,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (session.peerOnion.isNotBlank()) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = shortOnion(session.peerOnion),
                                    color = TextMutedSlate,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        CallStatusHeadline(session = session, primaryAccent = primaryAccent)
                        Spacer(modifier = Modifier.height(12.dp))

                        AnimatedVisibility(
                            visible = session.state == CallState.ACTIVE,
                            enter = fadeIn(tween(300)) + expandVertically(),
                            exit = fadeOut(tween(200)) + shrinkVertically()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LiveCallSpectrumVisualizer(
                                    callManager = callManager,
                                    primaryAccent = primaryAccent,
                                    secondaryAccent = secondaryAccent,
                                    isMuted = session.isMuted
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SecurityCodeChip(callManager = callManager, primaryAccent = primaryAccent)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        CallTelemetryCard(
                            session = session,
                            callManager = callManager,
                            primaryAccent = primaryAccent,
                            expanded = telemetryExpanded,
                            onPoorQualityAutoExpand = { telemetryExpanded = true },
                            onToggleExpand = { telemetryExpanded = !telemetryExpanded },
                            onRefreshRoute = onRefreshRoute
                        )
                    }
                } else {
                    // During active full-screen video: only show compact floating name/timer + SAS badge at top-center
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = session.peerNickname,
                            color = TextPlatinum,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val hudMediaPart = if (hasActiveVideoStream) {
                            val resLabel = if (session.forceTorOnly || !isDirectP2p) "360p Tor" else remoteResLabel
                            "$resLabel • $activeCodecLabel"
                        } else {
                            "Wideband Voice"
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GlassPillDark,
                            modifier = Modifier.border(
                                width = 0.7.dp,
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${formatDuration(session.durationSeconds)} • $hudMediaPart",
                                    color = TextPlatinum,
                                    fontSize = 12.sp,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SecurityCodeChip(callManager = callManager, primaryAccent = primaryAccent)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // ── Bottom controls ──
                when (session.state) {
                    CallState.INCOMING_RINGING -> IncomingCallActionRow(
                        isVideoOffer = session.isVideoCall,
                        onDecline = onDecline,
                        onAccept = onAccept
                    )
                    CallState.ENDED -> CallEndedPanel(session = session)
                    else -> ActiveCallControlDock(
                        session = session,
                        callManager = callManager,
                        primaryAccent = primaryAccent,
                        tiltState = tiltState,
                        isImmersiveVideo = isImmersiveActiveVideo,
                        onToggleMute = { hudInteractionTick++; onToggleMute() },
                        onToggleVideo = { hudInteractionTick++; onToggleVideo() },
                        onSwitchCamera = {
                            if (isSwitchingCamera) return@ActiveCallControlDock
                            hudInteractionTick++
                            scope.launch {
                                flipRotationY.animateTo(90f, tween(130))
                                onSwitchCamera()
                                flipRotationY.animateTo(180f, tween(150))
                                flipRotationY.snapTo(0f)
                            }
                        },
                        onToggleSpeaker = { hudInteractionTick++; onToggleSpeaker() },
                        onToggleTorOnly = { hudInteractionTick++; onToggleTorOnly() },
                        onRefreshRoute = {
                            hudInteractionTick++
                            onRefreshRoute()
                            showHudPill("Re-syncing P2P + Tor route…")
                        },
                        onHangUp = onHangUp
                    )
                }
            }
        }

        // ── Layer 5: Floating Liquid Glass Transient HUD Status Pill ──
        AnimatedVisibility(
            visible = hudToastMessage != null,
            enter = fadeIn(tween(180)) + slideInVertically { -it / 2 } + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(200)) + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 68.dp)
                .zIndex(25f)
        ) {
            val toastText = hudToastMessage.orEmpty()
            Surface(
                shape = RoundedCornerShape(50),
                color = GlassSurfaceDark,
                shadowElevation = 14.dp,
                modifier = Modifier.border(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                    shape = RoundedCornerShape(50)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(primaryAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = toastText,
                        color = TextPlatinum,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalCameraPreviewHost(
    callManager: SeamlessCallManager,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val localTrack by callManager.webrtcVideoEngine.localVideoTrack.collectAsState()
    val localMirror by callManager.webrtcVideoEngine.localMirror.collectAsState()
    val eglContext = remember(callManager) { callManager.webrtcVideoEngine.eglBase.eglBaseContext }
    val rendererRef = remember { mutableStateOf<WebRtcEglTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebRtcEglTextureView(ctx, "local-pip").apply {
                configure(eglContext, mirror = localMirror, cropFill = true)
            }.also { view ->
                rendererRef.value = view
            }
        },
        update = { view ->
            rendererRef.value = view
            view.configure(eglContext, mirror = localMirror, cropFill = true)
        }
    )

    DisposableEffect(localTrack, rendererRef.value) {
        val view = rendererRef.value
        val track = localTrack
        if (view != null && track != null) {
            runCatching { track.addSink(view) }
        }
        onDispose {
            if (view != null && track != null) {
                runCatching { track.removeSink(view) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            rendererRef.value?.releaseRenderer()
            rendererRef.value = null
        }
    }
}

@Composable
private fun RemoteVideoTextureHost(
    callManager: SeamlessCallManager,
    cropFill: Boolean,
    modifier: Modifier = Modifier
) {
    val remoteTrack by callManager.webrtcVideoEngine.remoteVideoTrack.collectAsState()
    val webrtcConnected by callManager.webrtcVideoEngine.mediaConnected.collectAsState()
    val webrtcFirstFrame by callManager.webrtcVideoEngine.remoteFirstFrame.collectAsState()
    val remoteAspect by callManager.webrtcVideoEngine.remoteAspectRatio.collectAsState()
    val remoteBitmap by callManager.videoEngine.remoteFrameBitmap.collectAsState()
    val eglContext = remember(callManager) { callManager.webrtcVideoEngine.eglBase.eglBaseContext }
    val rendererRef = remember { mutableStateOf<WebRtcEglTextureView?>(null) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val eglModifier = if (cropFill) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(remoteAspect.coerceIn(0.4f, 2.5f))
        }
        AndroidView(
            modifier = eglModifier,
            factory = { ctx ->
                WebRtcEglTextureView(ctx, "remote-main").apply {
                    configure(eglContext, mirror = false, cropFill = cropFill)
                }.also { view ->
                    rendererRef.value = view
                }
            },
            update = { view ->
                rendererRef.value = view
                view.configure(eglContext, mirror = false, cropFill = cropFill)
            }
        )
        if (!webrtcConnected || !webrtcFirstFrame) {
            remoteBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Remote peer video",
                    contentScale = if (cropFill) ContentScale.Crop else ContentScale.Fit,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    DisposableEffect(remoteTrack, rendererRef.value) {
        val view = rendererRef.value
        val track = remoteTrack
        if (view != null && track != null) {
            runCatching { track.addSink(view) }
        }
        onDispose {
            if (view != null && track != null) {
                runCatching { track.removeSink(view) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            rendererRef.value?.releaseRenderer()
            rendererRef.value = null
        }
    }
}

/**
 * Hardware-accelerated OpenGL [TextureView] renderer for WebRTC [org.webrtc.VideoTrack]s.
 * Supports safe re-attachment across Compose [movableContentOf] PiP <-> FullScreen swaps
 * without permanently releasing the underlying [org.webrtc.EglRenderer].
 */
private class WebRtcEglTextureView(
    context: android.content.Context,
    rendererName: String
) : TextureView(context), org.webrtc.VideoSink, TextureView.SurfaceTextureListener {

    private val eglRenderer = org.webrtc.EglRenderer(rendererName)
    private val videoLayoutMeasure = org.webrtc.RendererCommon.VideoLayoutMeasure()
    private var sharedContext: org.webrtc.EglBase.Context? = null
    private var isEglInitialized = false
    private var hasEglSurface = false
    private var mirror = false
    private var cropFill = true
    @Volatile private var rotatedFrameWidth = 0
    @Volatile private var rotatedFrameHeight = 0

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun configure(
        eglContext: org.webrtc.EglBase.Context,
        mirror: Boolean,
        cropFill: Boolean
    ) {
        val cropChanged = this.cropFill != cropFill
        this.sharedContext = eglContext
        this.mirror = mirror
        this.cropFill = cropFill
        ensureEglInitialized()
        eglRenderer.setMirror(mirror)
        val scaling = if (cropFill) {
            org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL
        } else {
            org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT
        }
        videoLayoutMeasure.setScalingType(scaling, scaling)
        if (cropChanged) {
            requestLayout()
        }
    }

    private fun ensureEglInitialized() {
        val ctx = sharedContext ?: return
        if (isEglInitialized) return
        runCatching {
            eglRenderer.init(ctx, org.webrtc.EglBase.CONFIG_PLAIN, org.webrtc.GlRectDrawer())
            eglRenderer.setMirror(mirror)
            isEglInitialized = true
        }
    }

    override fun onFrame(frame: org.webrtc.VideoFrame) {
        val w = frame.rotatedWidth
        val h = frame.rotatedHeight
        if (w != rotatedFrameWidth || h != rotatedFrameHeight) {
            rotatedFrameWidth = w
            rotatedFrameHeight = h
            if (!cropFill) {
                post { requestLayout() }
            }
        }
        eglRenderer.onFrame(frame)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!cropFill && rotatedFrameWidth > 0 && rotatedFrameHeight > 0) {
            val size = videoLayoutMeasure.measure(
                widthMeasureSpec,
                heightMeasureSpec,
                rotatedFrameWidth,
                rotatedFrameHeight
            )
            setMeasuredDimension(size.x, size.y)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)
        eglRenderer.setLayoutAspectRatio(w.toFloat() / h.toFloat())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureEglInitialized()
        val st = surfaceTexture
        if (isAvailable && st != null && isEglInitialized && !hasEglSurface) {
            runCatching {
                eglRenderer.createEglSurface(st)
                hasEglSurface = true
            }
        }
    }

    override fun onDetachedFromWindow() {
        if (hasEglSurface) {
            val latch = java.util.concurrent.CountDownLatch(1)
            runCatching {
                eglRenderer.releaseEglSurface { latch.countDown() }
                latch.await(150L, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            hasEglSurface = false
        }
        super.onDetachedFromWindow()
    }

    fun releaseRenderer() {
        if (isEglInitialized) {
            runCatching { eglRenderer.release() }
            isEglInitialized = false
            hasEglSurface = false
        }
    }

    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, width: Int, height: Int) {
        ensureEglInitialized()
        if (isEglInitialized && !hasEglSurface) {
            runCatching {
                eglRenderer.createEglSurface(st)
                hasEglSurface = true
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
        if (hasEglSurface) {
            val latch = java.util.concurrent.CountDownLatch(1)
            runCatching {
                eglRenderer.releaseEglSurface { latch.countDown() }
                latch.await(150L, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            hasEglSurface = false
        }
        return true
    }

    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
}

@Composable
private fun DraggableVideoPipCard(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    tiltState: State<Offset>,
    isHudVisible: Boolean,
    isSwapped: Boolean,
    flipRotationDeg: Float,
    onSwapVideos: () -> Unit,
    onSwitchCamera: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val localLevel by callManager.localVoiceLevelFlow.collectAsState()
    val activeBorderAlpha by animateFloatAsState(
        targetValue = 0.20f + (localLevel * 0.45f).coerceIn(0f, 0.45f),
        animationSpec = tween(90),
        label = "pipBorder"
    )

    var isDragging by remember { mutableStateOf(false) }
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "pipDragScale"
    )

    var expandedSize by remember { mutableStateOf(false) }
    val cardWidthDp by animateDpAsState(if (expandedSize) 136.dp else 110.dp, spring(dampingRatio = 0.8f), label = "pipW")
    val cardHeightDp by animateDpAsState(if (expandedSize) 196.dp else 158.dp, spring(dampingRatio = 0.8f), label = "pipH")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val cardWPx = with(density) { cardWidthDp.toPx() }
        val cardHPx = with(density) { cardHeightDp.toPx() }
        val marginX = with(density) { 16.dp.toPx() }
        val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
        val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }
        val minY = statusTop + with(density) { 82.dp.toPx() }
        val maxY = (maxH - cardHPx - navBottom - with(density) { if (isHudVisible) 220.dp.toPx() else 22.dp.toPx() })
            .coerceAtLeast(minY)

        var snapRight by remember { mutableStateOf(true) }
        var snapBottom by remember { mutableStateOf(true) }
        var initialized by remember { mutableStateOf(false) }

        val targetX = if (snapRight) (maxW - cardWPx - marginX).coerceAtLeast(marginX) else marginX
        val targetY = if (snapBottom) maxY else minY

        val animOffset = remember {
            Animatable(Offset(targetX, targetY), Offset.VectorConverter)
        }

        LaunchedEffect(targetX, targetY) {
            if (!initialized) {
                animOffset.snapTo(Offset(targetX, targetY))
                initialized = true
            } else {
                animOffset.animateTo(
                    targetValue = Offset(targetX, targetY),
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 320f)
                )
            }
        }

        val pipShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .offset { IntOffset(animOffset.value.x.roundToInt(), animOffset.value.y.roundToInt()) }
                .size(width = cardWidthDp, height = cardHeightDp)
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                    rotationY = if (isSwapped) 0f else flipRotationDeg
                    cameraDistance = 12f * this.density
                }
                .shadow(
                    elevation = if (isDragging) 24.dp else 16.dp,
                    shape = pipShape,
                    ambientColor = Color.Black,
                    spotColor = primaryAccent.copy(alpha = 0.25f)
                )
                .clip(pipShape)
                .background(CallBgMid)
                .drawWithContent {
                    drawContent()
                    val tilt = tiltState.value
                    val startX = size.width * (0.25f + tilt.x * 0.45f)
                    val startY = size.height * (0.15f + tilt.y * 0.35f)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(startX, startY),
                            radius = size.maxDimension * 0.65f
                        )
                    )
                }
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.48f),
                            primaryAccent.copy(alpha = activeBorderAlpha),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = pipShape
                )
                .pointerInput(maxW, maxH, minY, maxY, cardWPx, cardHPx) {
                    val velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            velocityTracker.resetTracking()
                            coroutineScope.launch { animOffset.stop() }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val nx = (animOffset.value.x + dragAmount.x).coerceIn(marginX, (maxW - cardWPx - marginX).coerceAtLeast(marginX))
                            val ny = (animOffset.value.y + dragAmount.y).coerceIn(minY, maxY)
                            coroutineScope.launch { animOffset.snapTo(Offset(nx, ny)) }
                        },
                        onDragCancel = {
                            isDragging = false
                            val destX = if (snapRight) (maxW - cardWPx - marginX).coerceAtLeast(marginX) else marginX
                            val destY = if (snapBottom) maxY else minY
                            coroutineScope.launch {
                                animOffset.animateTo(
                                    targetValue = Offset(destX, destY),
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f)
                                )
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val vel = velocityTracker.calculateVelocity()
                            val projectedX = animOffset.value.x + vel.x * 0.14f + cardWPx / 2f
                            val projectedY = animOffset.value.y + vel.y * 0.14f + cardHPx / 2f
                            snapRight = projectedX >= maxW / 2f
                            snapBottom = projectedY >= (minY + maxY + cardHPx) / 2f
                            val destX = if (snapRight) (maxW - cardWPx - marginX).coerceAtLeast(marginX) else marginX
                            val destY = if (snapBottom) maxY else minY
                            callHaptics.magneticSnap(view)
                            coroutineScope.launch {
                                animOffset.animateTo(
                                    targetValue = Offset(destX, destY),
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f)
                                )
                            }
                        }
                    )
                }
                .pointerInput(session.isRemoteVideoEnabled, session.isLocalVideoEnabled) {
                    detectTapGestures(
                        onTap = {
                            if (session.isRemoteVideoEnabled && session.isLocalVideoEnabled) {
                                callHaptics.cameraFlip(view)
                                onSwapVideos()
                            } else {
                                val next = !expandedSize
                                if (next) callHaptics.toggleOn(view) else callHaptics.toggleOff(view)
                                expandedSize = next
                            }
                        },
                        onDoubleTap = {
                            val next = !expandedSize
                            if (next) callHaptics.toggleOn(view) else callHaptics.toggleOff(view)
                            expandedSize = next
                        }
                    )
                }
        ) {
            content()

            // Frosted micro badges auto-fade cleanly with HUD so PiP faces remain edge-to-edge pure
            AnimatedVisibility(
                visible = isHudVisible && !isSwapped && session.isLocalVideoEnabled,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GlassPillDark)
                        .border(0.7.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable {
                            callHaptics.cameraFlip(view)
                            onSwitchCamera()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = "Flip camera",
                        tint = TextPlatinum,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isHudVisible && session.isRemoteVideoEnabled && session.isLocalVideoEnabled,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassPillDark)
                        .border(0.7.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Tap to swap",
                        tint = TextPlatinum.copy(alpha = 0.90f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCollapsedHudPill(
    session: CallSessionInfo,
    primaryAccent: Color,
    resolutionLabel: String,
    onTapRestore: () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val pulseTransition = rememberInfiniteTransition(label = "hudPillPulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hudDotAlpha"
    )

    val pillShape = RoundedCornerShape(50)
    Surface(
        shape = pillShape,
        color = Color(0xCC080B12),
        shadowElevation = 12.dp,
        modifier = Modifier
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f))
                ),
                shape = pillShape
            )
            .clickable {
                callHaptics.toggleOn(view)
                onTapRestore()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(primaryAccent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatDuration(session.durationSeconds),
                color = TextPlatinum,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Text(
                text = " • ",
                color = TextMutedSlate,
                fontSize = 11.5.sp
            )
            Text(
                text = resolutionLabel,
                color = TextSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Volumetric multi-layered aurora light field over OLED obsidian synchronized to
 * 0.1 Hz cardiac-coherence breathing (5s inhale / 5s exhale) and gentle gravity parallax.
 */
@Composable
private fun AuroraBackground(
    primaryAccent: Color,
    secondaryAccent: Color,
    posterAura: Color = Color.Transparent,
    posterSecondaryAura: Color = Color.Transparent,
    tiltState: State<Offset>,
    isEnded: Boolean
) {
    val dim by animateFloatAsState(
        targetValue = if (isEnded) 0.35f else 1f,
        animationSpec = tween(600),
        label = "auroraDim"
    )
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )
    // 0.1 Hz physiological coherence cadence: 5,000ms expand + 5,000ms settle.
    val breath by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraBreath"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tilt = tiltState.value

        // Upper-left jewel key light with subtle device-tilt depth parallax
        val r1 = w * 0.90f
        val c1 = Offset(
            w * (0.24f + 0.10f * cos(phase) + tilt.x * 0.05f),
            h * (0.20f + 0.06f * sin(phase) + tilt.y * 0.04f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryAccent.copy(alpha = 0.16f * dim * breath), Color.Transparent),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )

        // Mid-right harmonic counter-orb
        val counterHue = if (posterSecondaryAura != Color.Transparent) posterSecondaryAura else secondaryAccent
        val r2 = w * 0.82f
        val c2 = Offset(
            w * (0.82f - 0.10f * sin(phase * 0.8f) - tilt.x * 0.04f),
            h * (0.58f + 0.07f * cos(phase * 0.8f) - tilt.y * 0.04f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(counterHue.copy(alpha = 0.13f * dim), Color.Transparent),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )

        // Personal Contact Poster crown zenith bloom
        val zenithColor = if (posterAura != Color.Transparent) posterAura else Color(0xFF3B82F6)
        val r3 = w * 0.78f
        val c3 = Offset(w * (0.50f + 0.07f * sin(phase) + tilt.x * 0.06f), h * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(zenithColor.copy(alpha = 0.12f * dim * breath), Color.Transparent),
                center = c3,
                radius = r3
            ),
            radius = r3,
            center = c3
        )

        // Lower amethyst grounding mist
        val r4 = w * 0.70f
        val c4 = Offset(w * (0.45f - 0.06f * cos(phase)), h * (0.96f + 0.03f * sin(phase * 1.5f)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentStrictCore.copy(alpha = 0.09f * dim), Color.Transparent),
                center = c4,
                radius = r4
            ),
            radius = r4,
            center = c4
        )

        // Peripheral obsidian vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, CallBgBottom.copy(alpha = 0.55f)),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = max(w, h) * 0.78f
            )
        )
    }
}

@Composable
private fun CallTopBar(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    canMinimize: Boolean,
    onMinimize: () -> Unit,
    onToggleDiagnostics: () -> Unit = {}
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    val glassStroke = remember {
        Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (canMinimize) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassPillDark)
                    .border(0.9.dp, glassStroke, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        callHaptics.toggleOff(view)
                        onMinimize()
                    }
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Minimize call",
                    tint = TextPlatinum
                )
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        val badgeText = when {
            session.forceTorOnly -> "TOR STRICT • E2EE"
            session.state != CallState.ACTIVE -> "END-TO-END ENCRYPTED"
            session.isMultipathActive && isDirectP2p -> "P2P + TOR MULTIPATH"
            isDirectP2p -> "DIRECT P2P • E2EE"
            else -> "TOR ONION • E2EE"
        }
        val badgeShape = RoundedCornerShape(50)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = badgeShape,
                    ambientColor = primaryAccent.copy(alpha = 0.20f),
                    spotColor = Color.Black
                )
                .clip(badgeShape)
                .background(GlassPillDark)
                .background(primaryAccent.copy(alpha = 0.10f))
                .border(0.9.dp, primaryAccent.copy(alpha = 0.35f), badgeShape)
                .padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = if (isDirectP2p && !session.forceTorOnly) Icons.Rounded.Speed else Icons.Rounded.Lock,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = badgeText,
                color = TextPlatinum.copy(alpha = 0.94f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
                maxLines = 1
            )
        }

        // Live link-quality indicator (tap to toggle call diagnostics)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassPillDark)
                .border(0.9.dp, glassStroke, CircleShape)
                .then(
                    if (session.state == CallState.ACTIVE) {
                        Modifier.clickable {
                            callHaptics.toggleOn(view)
                            onToggleDiagnostics()
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (session.state == CallState.ACTIVE) {
                SignalBars(session = session, callManager = callManager)
            } else {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = TextSilver,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SignalBars(session: CallSessionInfo, callManager: SeamlessCallManager) {
    val quality = rememberLinkQuality(session, callManager)
    AnimatedSignalBarsSymbol(
        bars = quality.bars,
        activeColor = quality.color,
        isReconnecting = quality == LinkQuality.RECONNECTING,
        modifier = Modifier.size(width = 18.dp, height = 14.dp)
    )
}

/**
 * Derives a live link quality from RTT, jitter buffer depth and inbound packet freshness.
 * Uses a 500 ms ticker scoped to the caller so only the small leaf recomposes.
 */
@Composable
private fun rememberLinkQuality(session: CallSessionInfo, callManager: SeamlessCallManager): LinkQuality {
    val jitterMs by callManager.jitterBufferMsFlow.collectAsState()
    val rxPulse by callManager.rxPacketPulseFlow.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            now = System.currentTimeMillis()
        }
    }

    val connectedFor = session.connectedAtMs?.let { now - it } ?: 0L
    val rxAge = if (rxPulse > 0L) now - rxPulse else Long.MAX_VALUE
    val rtt = session.rttMs
    return when {
        // Remote is silent-suppressed (DTX) up to ~400ms; >5s without any frame means the path stalled
        connectedFor > 6_000L && rxAge > 5_000L -> LinkQuality.RECONNECTING
        rtt in 1..250 && jitterMs <= 160 -> LinkQuality.EXCELLENT
        rtt in 1..600 && jitterMs <= 320 -> LinkQuality.GOOD
        rtt == 0 && jitterMs <= 200 -> LinkQuality.GOOD
        rtt in 1..1200 || jitterMs <= 480 -> LinkQuality.FAIR
        else -> LinkQuality.POOR
    }
}

@Composable
private fun CallStatusHeadline(session: CallSessionInfo, primaryAccent: Color) {
    val transition = rememberInfiniteTransition(label = "statusWave")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val dotPulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "activeDotPulse"
    )

    when (session.state) {
        CallState.ACTIVE -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = dotPulse }
                        .clip(CircleShape)
                        .background(primaryAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDuration(session.durationSeconds),
                    color = TextPlatinum,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(
                        fontFeatureSettings = "tnum",
                        letterSpacing = 0.8.sp
                    )
                )
            }
            AnimatedVisibility(
                visible = session.isMuted,
                enter = scaleIn(initialScale = 0.9f, animationSpec = tween(180)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.9f, animationSpec = tween(150)) + fadeOut(tween(150))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MutedCoral.copy(alpha = 0.16f))
                        .border(0.8.dp, MutedCoral.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.MicOff, null, tint = MutedCoral, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Microphone muted",
                        color = TextPlatinum,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        CallState.INCOMING_RINGING,
        CallState.OUTGOING_RINGING,
        CallState.OUTGOING_DIALING -> {
            val label = when (session.state) {
                CallState.INCOMING_RINGING -> if (session.isVideoCall) "Incoming encrypted HD video" else "Incoming encrypted call"
                CallState.OUTGOING_RINGING -> if (session.isVideoCall) "Video ringing" else "Ringing"
                else -> if (session.isVideoCall) "Connecting encrypted HD video" else "Connecting secure circuit"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = TextPlatinum.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(7.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (idx in 0..2) {
                        val s = (sin(wavePhase - idx * 0.9f) * 0.5f + 0.5f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .graphicsLayer {
                                    alpha = 0.30f + 0.70f * s
                                    val sc = 0.80f + 0.35f * s
                                    scaleX = sc
                                    scaleY = sc
                                }
                                .clip(CircleShape)
                                .background(primaryAccent)
                        )
                    }
                }
            }
        }
        CallState.ENDED -> Text(
            text = session.statusMessage.ifBlank { "Call ended" },
            color = DangerRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        CallState.IDLE -> Unit
    }
}

/**
 * Isolated leaf composable collecting 50 Hz voice level flows; the parent never recomposes per frame.
 * Features a crystal obsidian medallion with real tilt-driven specular highlight, organic 6-lobe
 * fluid harmonic voice halo, luminous orbital comet arc, and 760ms Connection Bloom ripple.
 */
@Composable
private fun VoiceReactiveAvatarOrb(
    peerNickname: String,
    callState: CallState,
    primaryAccent: Color,
    secondaryAccent: Color,
    posterTop: Color = Color(0xFF1A2130),
    posterBottom: Color = Color(0xFF0E131D),
    tiltState: State<Offset>,
    connectionBloom: Float = 0f,
    callManager: SeamlessCallManager
) {
    val localVoice by callManager.localVoiceLevelFlow.collectAsState()
    val remoteVoice by callManager.remoteVoiceLevelFlow.collectAsState()

    val smoothRemote by animateFloatAsState(
        targetValue = remoteVoice.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
        label = "smoothRemoteOrb"
    )
    val smoothLocal by animateFloatAsState(
        targetValue = localVoice.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
        label = "smoothLocalOrb"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPulse"
    )
    // 0.1 Hz cardiac-coherence breathing for soothing pre-connect calm
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.036f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbSpin"
    )
    val fluidPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(3_600, easing = LinearEasing), RepeatMode.Restart),
        label = "orbFluid"
    )
    val fluidPath = remember { Path() }

    val isEnded = callState == CallState.ENDED
    val isActive = callState == CallState.ACTIVE

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(204.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 57.dp.toPx()

            when {
                isEnded -> Unit
                isActive -> {
                    val combined = max(smoothRemote, smoothLocal)
                    // 760ms iridescent answer Connection Bloom expanding wave
                    if (connectionBloom in 0.01f..0.99f) {
                        val bloomRadius = baseRadius * (1.02f + connectionBloom * 1.48f)
                        val bloomAlpha = (1f - connectionBloom) * 0.65f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, primaryAccent.copy(alpha = bloomAlpha * 0.45f), Color.Transparent),
                                center = center,
                                radius = bloomRadius * 1.15f
                            ),
                            radius = bloomRadius * 1.15f,
                            center = center
                        )
                        drawCircle(
                            color = TextPlatinum.copy(alpha = bloomAlpha),
                            radius = bloomRadius,
                            center = center,
                            style = Stroke(width = (3.0f - connectionBloom * 2.0f).dp.toPx())
                        )
                    }

                    // Volumetric base glow
                    val outerRadius = baseRadius * (1.28f + combined * 0.42f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.16f + smoothRemote * 0.24f),
                                secondaryAccent.copy(alpha = 0.06f + combined * 0.09f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius * 1.25f
                        ),
                        radius = outerRadius * 1.25f,
                        center = center
                    )

                    // Organic 6-lobe harmonic fluid aura path driven by voice amplitude
                    val sampleCount = 18
                    fluidPath.reset()
                    var prevX = 0f
                    var prevY = 0f
                    var firstMidX = 0f
                    var firstMidY = 0f
                    for (i in 0..sampleCount) {
                        val idx = i % sampleCount
                        val angle = (idx * (2.0 * PI / sampleCount)).toFloat()
                        val wobble = (0.04f + combined * 0.14f) * (
                            sin(angle * 3f + fluidPhase) * 0.65f +
                                cos(angle * 5f - fluidPhase * 0.7f) * 0.35f
                            )
                        val r = baseRadius * (1.16f + combined * 0.24f + wobble)
                        val px = center.x + r * cos(angle)
                        val py = center.y + r * sin(angle)
                        if (i == 0) {
                            prevX = px
                            prevY = py
                        } else {
                            val midX = (prevX + px) / 2f
                            val midY = (prevY + py) / 2f
                            if (i == 1) {
                                firstMidX = midX
                                firstMidY = midY
                                fluidPath.moveTo(midX, midY)
                            } else {
                                fluidPath.quadraticTo(prevX, prevY, midX, midY)
                            }
                            prevX = px
                            prevY = py
                        }
                    }
                    fluidPath.quadraticTo(prevX, prevY, firstMidX, firstMidY)
                    fluidPath.close()

                    drawPath(
                        path = fluidPath,
                        color = primaryAccent.copy(alpha = 0.11f + combined * 0.16f)
                    )
                    drawPath(
                        path = fluidPath,
                        color = primaryAccent.copy(alpha = 0.24f + combined * 0.34f),
                        style = Stroke(width = 1.1.dp.toPx())
                    )

                    // Hairline orbital sweep + luminous comet tip
                    val orbitR = baseRadius * 1.07f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, primaryAccent.copy(alpha = 0.85f), Color.Transparent),
                            center = center
                        ),
                        startAngle = spin,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(center.x - orbitR, center.y - orbitR),
                        size = Size(orbitR * 2f, orbitR * 2f),
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val tipRad = Math.toRadians((spin + 52.5f).toDouble())
                    val tipOffset = Offset(
                        x = center.x + (orbitR * cos(tipRad)).toFloat(),
                        y = center.y + (orbitR * sin(tipRad)).toFloat()
                    )
                    drawCircle(
                        color = TextPlatinum.copy(alpha = 0.85f),
                        radius = 2.dp.toPx(),
                        center = tipOffset
                    )
                }
                else -> {
                    // 3 silky sonar ripples while dialing / ringing
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryAccent.copy(alpha = 0.16f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.65f
                        ),
                        radius = baseRadius * 1.65f,
                        center = center
                    )
                    for (k in 0..2) {
                        val p = (ringPulse + k / 3f) % 1f
                        drawCircle(
                            color = primaryAccent.copy(alpha = (1f - p) * 0.26f),
                            radius = baseRadius * (1.06f + p * 0.64f),
                            center = center,
                            style = Stroke(width = (1.8f - p * 1.1f).dp.toPx())
                        )
                    }
                    val orbitR = baseRadius * 1.07f
                    drawArc(
                        color = primaryAccent.copy(alpha = 0.55f),
                        startAngle = spin * 1.5f,
                        sweepAngle = 72f,
                        useCenter = false,
                        topLeft = Offset(center.x - orbitR, center.y - orbitR),
                        size = Size(orbitR * 2f, orbitR * 2f),
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        val avatarScale = when {
            isEnded -> 0.96f
            isActive -> 1f + max(smoothRemote, smoothLocal) * 0.055f
            else -> breathScale
        }
        val medallionShape = CircleShape
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(114.dp)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                    alpha = if (isEnded) 0.58f else 1f
                }
                .shadow(
                    elevation = 18.dp,
                    shape = medallionShape,
                    ambientColor = if (isEnded) DangerRed.copy(alpha = 0.2f) else primaryAccent.copy(alpha = 0.25f),
                    spotColor = Color.Black
                )
                .clip(medallionShape)
                .background(Brush.linearGradient(listOf(posterTop, posterBottom)))
                .border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.38f),
                            if (isEnded) DangerRed else primaryAccent,
                            if (isEnded) DangerRed.copy(alpha = 0.35f) else secondaryAccent.copy(alpha = 0.40f)
                        )
                    ),
                    shape = medallionShape
                )
        ) {
            // Draw-phase gravity tilt specular lens sheen inside crystal medallion (zero recomposition)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tilt = tiltState.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(
                            size.width * (0.32f + tilt.x * 0.24f),
                            size.height * (0.26f + tilt.y * 0.24f)
                        ),
                        radius = size.width * 0.65f
                    )
                )
            }
            Text(
                text = peerNickname.firstOrNull()?.uppercase() ?: "?",
                color = TextPlatinum,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 28-bar silk vertical spectrum equalizer with bell-curve edge fade, spring reactivity,
 * and warm champagne-gold shift when the local user speaks vs peer gemstone hue.
 */
@Composable
private fun LiveCallSpectrumVisualizer(
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    secondaryAccent: Color,
    isMuted: Boolean
) {
    val localLevel by callManager.localVoiceLevelFlow.collectAsState()
    val remoteLevel by callManager.remoteVoiceLevelFlow.collectAsState()

    val smoothRemote by animateFloatAsState(
        targetValue = remoteLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f),
        label = "specRemote"
    )
    val smoothLocal by animateFloatAsState(
        targetValue = if (isMuted) 0f else localLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f),
        label = "specLocal"
    )

    val phaseTransition = rememberInfiniteTransition(label = "specPhase")
    val wavePhase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val champagneTop = Color(0xFFF8EFE2)
    val champagneBottom = Color(0xFFE2C799)

    Canvas(
        modifier = Modifier
            .width(248.dp)
            .height(34.dp)
    ) {
        val barCount = 28
        val barWidth = 2.6.dp.toPx()
        val spacing = ((size.width - barWidth * barCount) / (barCount - 1)).coerceAtLeast(2f)
        val remoteDominant = smoothRemote >= smoothLocal
        val energy = max(smoothRemote, smoothLocal * 0.92f)
        val topStop = if (remoteDominant || smoothLocal <= 0.05f) TextPlatinum else champagneTop
        val bottomStop = if (remoteDominant || smoothLocal <= 0.05f) primaryAccent else champagneBottom

        for (i in 0 until barCount) {
            val edgeFade = sin(PI * (i + 0.5) / barCount).toFloat().coerceIn(0.18f, 1f)
            val harmonic = sin(wavePhase + i * 0.50f) * 0.5f + 0.5f
            val idle = 0.16f + 0.08f * harmonic
            val active = idle + energy * edgeFade * (0.40f + 0.60f * harmonic)
            val h = active.coerceIn(0.12f, 1f) * edgeFade
            val barHeight = (size.height * h).coerceAtLeast(barWidth)
            val topY = (size.height - barHeight) / 2f
            val alpha = (0.30f + edgeFade * (0.25f + 0.45f * active)).coerceIn(0.18f, 0.95f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topStop.copy(alpha = alpha * 0.92f),
                        bottomStop.copy(alpha = alpha)
                    ),
                    startY = topY,
                    endY = topY + barHeight
                ),
                topLeft = Offset(i * (barWidth + spacing), topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    val speakerStatus = when {
        smoothRemote > 0.06f && smoothRemote >= smoothLocal -> 1
        smoothLocal > 0.06f -> 2
        else -> 0
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(18.dp)
    ) {
        AnimatedContent(
            targetState = speakerStatus,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
            label = "speakerStatus"
        ) { state ->
            when (state) {
                1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(primaryAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Peer speaking",
                        color = TextSilver,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(champagneBottom)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "You're speaking",
                        color = champagneTop.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                else -> Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

/**
 * Tap-to-expand Short Authentication String with interactive cryptographic match seal.
 */
@Composable
private fun SecurityCodeChip(callManager: SeamlessCallManager, primaryAccent: Color) {
    val code by callManager.securityCodeFlow.collectAsState()
    if (code.isEmpty()) return
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    var isVerifiedMatch by remember(code) { mutableStateOf(false) }
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 20.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f),
        label = "sasCorner"
    )
    val borderAccent by animateColorAsState(
        targetValue = if (isVerifiedMatch) AccentTor.copy(alpha = 0.55f) else primaryAccent.copy(alpha = 0.30f),
        animationSpec = tween(260),
        label = "sasBorder"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 360f))
            .clip(shape)
            .background(if (isVerifiedMatch) AccentTor.copy(alpha = 0.10f) else GlassPillDark)
            .border(0.9.dp, borderAccent, shape)
            .clickable {
                val next = !expanded
                if (next) callHaptics.toggleOn(view) else callHaptics.toggleOff(view)
                expanded = next
            }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isVerifiedMatch) Icons.Rounded.Check else Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = AccentTor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = code.joinToString("  "),
                fontSize = 19.sp,
                letterSpacing = 2.sp
            )
            if (isVerifiedMatch) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verified",
                    color = AccentTor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (expanded) {
            Text(
                text = "Match these 4 emojis with your peer to confirm zero interception.",
                color = TextSilver,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (!isVerifiedMatch) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentTor.copy(alpha = 0.18f))
                        .border(0.8.dp, AccentTor.copy(alpha = 0.50f), RoundedCornerShape(50))
                        .clickable {
                            callHaptics.sasVerified(view)
                            isVerifiedMatch = true
                            expanded = false
                        }
                        .padding(horizontal = 13.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Confirm Match",
                        color = AccentTor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Progressive-disclosure frosted glass telemetry card:
 * - Pre-active states render a concise smoked obsidian whisper pill.
 * - Active state defaults to a single calm 1-line strip; tapping reveals full stats & Re-Sync.
 */
@Composable
private fun CallTelemetryCard(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    expanded: Boolean,
    onPoorQualityAutoExpand: () -> Unit = {},
    onToggleExpand: () -> Unit,
    onRefreshRoute: () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val quality = rememberLinkQuality(session, callManager)
    LaunchedEffect(quality) {
        if (quality == LinkQuality.POOR || quality == LinkQuality.RECONNECTING) {
            onPoorQualityAutoExpand()
        }
    }
    val jitterMs by callManager.jitterBufferMsFlow.collectAsState()
    val txPulse by callManager.txPacketPulseFlow.collectAsState()
    val rxPulse by callManager.rxPacketPulseFlow.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.state, expanded) {
        while (session.state == CallState.ACTIVE && expanded) {
            delay(250L)
            now = System.currentTimeMillis()
        }
    }

    if (session.state != CallState.ACTIVE) {
        val hintText = when {
            session.statusMessage.isBlank() -> "Tor v3 Onion • Ephemeral E2EE"
            session.statusMessage.contains("Ringing", ignoreCase = true) ->
                "Peer notified • End-to-end encrypted"
            else -> session.statusMessage
        }
        val whisperShape = RoundedCornerShape(50)
        Surface(
            shape = whisperShape,
            color = GlassPillDark,
            modifier = Modifier.border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                shape = whisperShape
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(primaryAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = hintText,
                    color = TextSilver,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    val effectiveJitter = if (jitterMs > 0) jitterMs else session.jitterBufferMs
    val cardShape = RoundedCornerShape(22.dp)

    Surface(
        shape = cardShape,
        color = GlassCardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 360f))
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                shape = cardShape
            )
            .clickable {
                if (expanded) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                onToggleExpand()
            }
    ) {
        ActiveTelemetryContent(
            session = session,
            callManager = callManager,
            primaryAccent = primaryAccent,
            quality = quality,
            expanded = expanded,
            effectiveJitter = effectiveJitter,
            txPulse = txPulse,
            rxPulse = rxPulse,
            now = now,
            onRefreshRoute = onRefreshRoute
        )
    }
}

@Composable
private fun ActiveTelemetryContent(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    quality: LinkQuality,
    expanded: Boolean,
    effectiveJitter: Int,
    txPulse: Long,
    rxPulse: Long,
    now: Long,
    onRefreshRoute: () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    val webrtcConnected by callManager.webrtcVideoEngine.mediaConnected.collectAsState()
    val webrtcStats by callManager.webrtcVideoEngine.stats.collectAsState()
    val hasActiveVideoStream = session.isLocalVideoEnabled || session.isRemoteVideoEnabled

    val shortRoute = when {
        session.forceTorOnly -> "Tor Strict"
        webrtcConnected -> webrtcStats.routeLabel.ifBlank { "P2P Direct" }
        isDirectP2p -> "Direct P2P"
        else -> "Tor Onion"
    }
    val rttLabel = if (session.rttMs > 0) "${session.rttMs} ms" else "Measuring"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        // Calm compact strip always visible
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(primaryAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = shortRoute,
                    color = TextPlatinum,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = " • ",
                    color = TextMutedSlate,
                    fontSize = 12.sp
                )
                Text(
                    text = rttLabel,
                    color = TextSilver,
                    fontSize = 12.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1
                )
                Text(
                    text = " • ",
                    color = TextMutedSlate,
                    fontSize = 12.sp
                )
                Text(
                    text = quality.label,
                    color = quality.color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse diagnostics" else "Expand diagnostics",
                tint = TextSilver,
                modifier = Modifier.size(18.dp)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(modifier = Modifier.height(10.dp))

            val routeLabel = when {
                session.forceTorOnly -> "Tor v3 Hidden Service • IP hidden"
                webrtcConnected -> "WebRTC Direct P2P (${webrtcStats.routeLabel.ifBlank { "DTLS-SRTP" }})"
                isDirectP2p && session.isMultipathActive -> "Direct P2P + Tor multipath"
                isDirectP2p -> "Direct P2P UDP"
                else -> "Tor v3 onion circuit"
            }
            val fullRouteLabel = if (hasActiveVideoStream) {
                if (webrtcConnected && !session.forceTorOnly) {
                    val codec = webrtcStats.codec.ifBlank { "H.264" }
                    "$routeLabel • $codec ${webrtcStats.qualityLabel}"
                } else {
                    val res = if (isDirectP2p && !session.forceTorOnly) "720p" else "360p Tor"
                    "$routeLabel • Tor Video $res"
                }
            } else {
                "$routeLabel • Wideband Voice"
            }
            Text(
                text = fullRouteLabel,
                color = TextSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryStat(
                    label = "LATENCY",
                    value = if (session.rttMs > 0) "${session.rttMs} ms" else "—",
                    valueColor = TextPlatinum
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                TelemetryStat(
                    label = "BUFFER",
                    value = "$effectiveJitter ms",
                    valueColor = TextPlatinum
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                TelemetryStat(
                    label = "QUALITY",
                    value = quality.label,
                    valueColor = quality.color
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val txLive = !session.isMuted && txPulse > 0L && now - txPulse < 1_200L
                    val rxLive = rxPulse > 0L && now - rxPulse < 1_200L
                    ActivityLed(
                        label = when {
                            session.isMuted -> "TX muted"
                            txLive -> "TX live"
                            else -> "TX silent"
                        },
                        active = txLive,
                        color = if (session.isMuted) MutedCoral else AccentTor
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    ActivityLed(
                        label = if (rxLive) "RX live" else "RX silent",
                        active = rxLive,
                        color = primaryAccent
                    )
                }

                val resyncShape = RoundedCornerShape(50)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(resyncShape)
                        .background(WarnGold.copy(alpha = 0.14f))
                        .border(0.8.dp, WarnGold.copy(alpha = 0.40f), resyncShape)
                        .clickable {
                            callHaptics.reSyncPulse(view)
                            onRefreshRoute()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = WarnGold, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Re-Sync",
                        color = TextPlatinum,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextMutedSlate,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun ActivityLed(label: String, active: Boolean, color: Color) {
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(180),
        label = "ledGlow"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer { alpha = glow * 0.5f }
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) color else TextMutedSlate.copy(alpha = 0.45f))
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = TextSilver,
            fontSize = 11.sp,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncomingCallActionRow(
    isVideoOffer: Boolean,
    onDecline: () -> Unit,
    onAccept: (withVideo: Boolean) -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "answerHalo")
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "halo"
    )
    val breath by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "answerBreath"
    )

    val declineReveal = remember { Animatable(0f) }
    val voiceReveal = remember { Animatable(0f) }
    val acceptReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val spec = spring<Float>(dampingRatio = 0.72f, stiffness = 340f)
        launch { declineReveal.animateTo(1f, spec) }
        launch { delay(55L); voiceReveal.animateTo(1f, spec) }
        launch { delay(110L); acceptReveal.animateTo(1f, spec) }
    }
    val slideOffsetPx = with(density) { 24.dp.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 26.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = declineReveal.value.coerceIn(0f, 1f)
                translationY = (1f - declineReveal.value) * slideOffsetPx
            }
        ) {
            PressableCircleButton(
                size = if (isVideoOffer) 66 else 74,
                backgroundBrush = Brush.linearGradient(listOf(DangerCrimsonTop, DangerCrimsonBottom)),
                shadowElevation = 14f,
                shadowColor = DangerCrimsonBottom,
                onClick = {
                    callHaptics.callEnded()
                    onDecline()
                }
            ) {
                Icon(Icons.Rounded.CallEnd, "Decline call", tint = Color.White, modifier = Modifier.size(if (isVideoOffer) 28.dp else 32.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Decline", color = TextPlatinum.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        if (isVideoOffer) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = voiceReveal.value.coerceIn(0f, 1f)
                    translationY = (1f - voiceReveal.value) * slideOffsetPx
                }
            ) {
                PressableCircleButton(
                    size = 66,
                    background = Color(0x28FFFFFF),
                    borderBrush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                    onClick = {
                        callHaptics.toggleOn(view)
                        onAccept(false)
                    }
                ) {
                    Icon(Icons.Rounded.PhoneInTalk, "Answer voice only", tint = TextPlatinum, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Voice Only", color = TextPlatinum.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = acceptReveal.value.coerceIn(0f, 1f)
                translationY = (1f - acceptReveal.value) * slideOffsetPx
            }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (isVideoOffer) 98.dp else 110.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val base = if (isVideoOffer) 33.dp.toPx() else 37.dp.toPx()
                    for (k in 0..1) {
                        val p = (halo + k * 0.5f) % 1f
                        drawCircle(
                            color = AccentTor.copy(alpha = (1f - p) * 0.42f),
                            radius = base + p * 17.dp.toPx(),
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                    }
                }
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = breath
                        scaleY = breath
                    }
                ) {
                    PressableCircleButton(
                        size = if (isVideoOffer) 66 else 74,
                        backgroundBrush = Brush.linearGradient(listOf(AccentTor, AccentTorCore)),
                        shadowElevation = 14f,
                        shadowColor = AccentTorCore,
                        onClick = {
                            callHaptics.toggleOn(view)
                            onAccept(isVideoOffer)
                        }
                    ) {
                        Icon(
                            imageVector = if (isVideoOffer) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = if (isVideoOffer) "Accept Video" else "Answer call",
                            tint = EngagedInk,
                            modifier = Modifier.size(if (isVideoOffer) 28.dp else 32.dp)
                        )
                    }
                }
            }
            Text(
                text = if (isVideoOffer) "Accept Video" else "Answer",
                color = TextPlatinum,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CallEndedPanel(session: CallSessionInfo) {
    val panelShape = RoundedCornerShape(24.dp)
    Surface(
        shape = panelShape,
        color = GlassCardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                shape = panelShape
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(DangerRed.copy(alpha = 0.16f))
                    .border(1.dp, DangerRed.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = DangerRed, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (session.connectedAtMs != null) {
                    "Duration ${formatDuration(session.durationSeconds)}"
                } else {
                    "Not connected"
                },
                color = TextPlatinum,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedZeroizedSealSymbol(
                    tint = AccentTor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ephemeral session keys zeroized",
                    color = AccentTor.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReSyncRoutePillLeaf(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    isImmersiveVideo: Boolean,
    onRefreshRoute: () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val quality = rememberLinkQuality(session, callManager)
    val shouldShow = isImmersiveVideo ||
        quality == LinkQuality.FAIR ||
        quality == LinkQuality.POOR ||
        quality == LinkQuality.RECONNECTING
    if (!shouldShow) return
    val resyncShape = RoundedCornerShape(50)
    Surface(
        shape = resyncShape,
        color = GlassPillDark,
        modifier = Modifier
            .border(0.8.dp, WarnGold.copy(alpha = 0.40f), resyncShape)
            .clickable {
                callHaptics.reSyncPulse(view)
                onRefreshRoute()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null, tint = WarnGold, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("Re-Sync Route", color = TextPlatinum, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActiveCallControlDock(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    tiltState: State<Offset>,
    isImmersiveVideo: Boolean,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleTorOnly: () -> Unit,
    onRefreshRoute: () -> Unit,
    onHangUp: () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val audioRoute by callManager.audioRouteFlow.collectAsState()
    val audioRouteIcon = when (audioRoute) {
        AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
        AudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
        AudioRoute.WIRED_HEADSET -> Icons.Rounded.HeadsetMic
        AudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
    }
    val audioRouteLabel = when (audioRoute) {
        AudioRoute.SPEAKER -> "Speaker"
        AudioRoute.BLUETOOTH -> "Bluetooth"
        AudioRoute.WIRED_HEADSET -> "Headset"
        AudioRoute.EARPIECE -> "Earpiece"
    }
    val audioRouteEngaged = audioRoute != AudioRoute.EARPIECE
    val isActive = session.state == CallState.ACTIVE
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    val rimTransition = rememberInfiniteTransition(label = "dockRim")
    val shimmerShift by rimTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerShift"
    )
    val dockBorderBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0.0f to Color.White.copy(alpha = 0.14f),
            (0.20f + shimmerShift * 0.60f) to Color.White.copy(alpha = 0.34f),
            1.0f to primaryAccent.copy(alpha = 0.18f)
        )
    )
    val dockShape = RoundedCornerShape(34.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.80f, stiffness = 340f))
            .padding(bottom = 12.dp)
    ) {
        if (session.isLocalVideoEnabled || isActive) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                if (session.isLocalVideoEnabled) {
                    val camShape = RoundedCornerShape(50)
                    Surface(
                        shape = camShape,
                        color = GlassPillDark,
                        modifier = Modifier
                            .border(
                                width = 0.8.dp,
                                brush = Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                                shape = camShape
                            )
                            .clickable {
                                callHaptics.cameraFlip(view)
                                onSwitchCamera()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Icon(Icons.Rounded.Cameraswitch, null, tint = TextPlatinum, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (session.isFrontCamera) "Front Cam" else "Rear Cam",
                                color = TextPlatinum,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (isActive) {
                    ReSyncRoutePillLeaf(
                        session = session,
                        callManager = callManager,
                        isImmersiveVideo = isImmersiveVideo,
                        onRefreshRoute = onRefreshRoute
                    )
                }
            }
        }

        val tiltSheenModifier = Modifier.drawWithContent {
            drawContent()
            val tilt = tiltState.value
            val cx = size.width * (0.50f + tilt.x * 0.36f)
            val cy = size.height * (0.22f + tilt.y * 0.25f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.maxDimension * 0.48f
                )
            )
        }

        if (!isImmersiveVideo) {
            // ── Normal Voice Call / Video Paused: Apple Phone Flagship Ergonomics ──
            // 4 spacious Fitts's-Law 60dp utility controls in Liquid Glass capsule +
            // strictly isolated 68dp Crimson End Call Orb below (Von Restorff + Gestalt).
            Surface(
                shape = dockShape,
                color = GlassSurfaceDark,
                shadowElevation = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dockShape)
                    .then(tiltSheenModifier)
                    .border(width = 1.dp, brush = dockBorderBrush, shape = dockShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlItem(
                        label = if (session.isMuted) "Unmute" else "Mute",
                        isActive = session.isMuted,
                        activeFill = MutedCoral,
                        activeIconTint = Color.White,
                        activeLabelColor = MutedCoral,
                        buttonSize = 60,
                        onClick = {
                            if (session.isMuted) callHaptics.unmuteLive(view) else callHaptics.muteEngaged(view)
                            onToggleMute()
                        },
                        customIcon = { tint, backing ->
                            AnimatedMicSymbol(
                                isMuted = session.isMuted,
                                tint = tint,
                                backingColor = backing,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    )
                    CallControlItem(
                        label = if (session.isLocalVideoEnabled) "Cam On" else "Camera",
                        isActive = session.isLocalVideoEnabled,
                        activeFill = EngagedPearl,
                        activeIconTint = EngagedInk,
                        activeLabelColor = TextPlatinum,
                        buttonSize = 60,
                        onClick = {
                            if (session.isLocalVideoEnabled) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                            onToggleVideo()
                        },
                        customIcon = { tint, backing ->
                            AnimatedCameraSymbol(
                                isEnabled = session.isLocalVideoEnabled,
                                tint = tint,
                                backingColor = backing,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    )
                    CallControlItem(
                        icon = audioRouteIcon,
                        label = audioRouteLabel,
                        isActive = audioRouteEngaged,
                        activeFill = EngagedPearl,
                        activeIconTint = EngagedInk,
                        activeLabelColor = TextPlatinum,
                        buttonSize = 60,
                        onClick = {
                            if (audioRouteEngaged) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                            onToggleSpeaker()
                        }
                    )
                    CallControlItem(
                        label = if (session.forceTorOnly) "Tor Strict" else "Multipath",
                        isActive = session.forceTorOnly,
                        activeFill = AccentStrictCore,
                        activeBrush = Brush.linearGradient(listOf(AccentStrict, AccentStrictCore)),
                        activeIconTint = Color.White,
                        activeLabelColor = AccentStrict,
                        buttonSize = 60,
                        onClick = {
                            if (session.forceTorOnly) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                            onToggleTorOnly()
                        },
                        customIcon = { tint, _ ->
                            AnimatedShieldCrestSymbol(
                                isTorStrict = session.forceTorOnly || !isDirectP2p,
                                tint = tint,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PressableCircleButton(
                size = 68,
                backgroundBrush = Brush.linearGradient(listOf(DangerCrimsonTop, DangerCrimsonBottom)),
                shadowElevation = 18f,
                shadowColor = DangerCrimsonBottom,
                onClick = {
                    callHaptics.callEnded()
                    onHangUp()
                }
            ) {
                Icon(Icons.Rounded.CallEnd, "Hang up", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        } else {
            // ── Full-Screen HD Video Call: Compact Floating Liquid Glass Pill ──
            Surface(
                shape = dockShape,
                color = GlassSurfaceDark,
                shadowElevation = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dockShape)
                    .then(tiltSheenModifier)
                    .border(width = 1.dp, brush = dockBorderBrush, shape = dockShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlItem(
                        label = if (session.isMuted) "Unmute" else "Mute",
                        isActive = session.isMuted,
                        activeFill = MutedCoral,
                        activeIconTint = Color.White,
                        activeLabelColor = MutedCoral,
                        buttonSize = 52,
                        onClick = {
                            if (session.isMuted) callHaptics.unmuteLive(view) else callHaptics.muteEngaged(view)
                            onToggleMute()
                        },
                        customIcon = { tint, backing ->
                            AnimatedMicSymbol(
                                isMuted = session.isMuted,
                                tint = tint,
                                backingColor = backing,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                    CallControlItem(
                        label = if (session.isLocalVideoEnabled) "Cam On" else "Camera",
                        isActive = session.isLocalVideoEnabled,
                        activeFill = EngagedPearl,
                        activeIconTint = EngagedInk,
                        activeLabelColor = TextPlatinum,
                        buttonSize = 52,
                        onClick = {
                            if (session.isLocalVideoEnabled) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                            onToggleVideo()
                        },
                        customIcon = { tint, backing ->
                            AnimatedCameraSymbol(
                                isEnabled = session.isLocalVideoEnabled,
                                tint = tint,
                                backingColor = backing,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                    CallControlItem(
                        icon = audioRouteIcon,
                        label = audioRouteLabel,
                        isActive = audioRouteEngaged,
                        activeFill = EngagedPearl,
                        activeIconTint = EngagedInk,
                        activeLabelColor = TextPlatinum,
                        buttonSize = 52,
                        onClick = {
                            if (audioRouteEngaged) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                            onToggleSpeaker()
                        }
                    )
                    CallControlItem(
                        label = if (session.forceTorOnly) "Tor Strict" else "Multipath",
                        isActive = session.forceTorOnly,
                        activeFill = AccentStrictCore,
                        activeBrush = Brush.linearGradient(listOf(AccentStrict, AccentStrictCore)),
                        activeIconTint = Color.White,
                        activeLabelColor = AccentStrict,
                        buttonSize = 52,
                        onClick = {
                            if (session.forceTorOnly) callHaptics.toggleOff(view) else callHaptics.toggleOn(view)
                            onToggleTorOnly()
                        },
                        customIcon = { tint, _ ->
                            AnimatedShieldCrestSymbol(
                                isTorStrict = session.forceTorOnly || !isDirectP2p,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )

                    Box(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .width(1.dp)
                            .height(32.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PressableCircleButton(
                            size = 54,
                            backgroundBrush = Brush.linearGradient(listOf(DangerCrimsonTop, DangerCrimsonBottom)),
                            shadowElevation = 10f,
                            shadowColor = DangerCrimsonBottom,
                            onClick = {
                                callHaptics.callEnded()
                                onHangUp()
                            }
                        ) {
                            Icon(Icons.Rounded.CallEnd, "Hang up", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("End", color = TextSilver, fontSize = 11.sp)
                    }
                }
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isImmersiveVideo) "Tap video to hide controls • Drag PiP" else "Swipe down to minimize",
                color = TextMutedSlate,
                fontSize = 10.5.sp,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun CallControlItem(
    icon: ImageVector? = null,
    label: String,
    isActive: Boolean,
    activeFill: Color,
    activeBrush: Brush? = null,
    activeIconTint: Color = EngagedInk,
    activeLabelColor: Color = TextPlatinum,
    buttonSize: Int = 54,
    enabled: Boolean = true,
    onClick: () -> Unit,
    customIcon: (@Composable (tint: Color, backing: Color) -> Unit)? = null
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeFill else Color(0x1CFFFFFF),
        animationSpec = tween(240),
        label = "ctrlBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) activeIconTint else TextPlatinum,
        animationSpec = tween(240),
        label = "ctrlIcon"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isActive) activeLabelColor else TextSilver,
        animationSpec = tween(240),
        label = "ctrlLabel"
    )
    val morphCorner by animateDpAsState(
        targetValue = if (isActive) (buttonSize * 0.34f).dp else (buttonSize / 2f).dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "morphCorner"
    )
    val inactiveRim = remember {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f)))
    }
    val opaqueBacking = if (isActive) activeFill else Color(0xFF1F242E)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.35f }
    ) {
        PressableCircleButton(
            size = buttonSize,
            cornerRadiusDp = morphCorner,
            background = bgColor,
            backgroundBrush = if (isActive) activeBrush else null,
            borderBrush = if (isActive) null else inactiveRim,
            enabled = enabled,
            onClick = onClick
        ) {
            if (customIcon != null) {
                customIcon(iconColor, opaqueBacking)
            } else if (icon != null) {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.78f, animationSpec = tween(180)) + fadeIn(tween(180))) togetherWith
                            (scaleOut(targetScale = 0.78f, animationSpec = tween(150)) + fadeOut(tween(150)))
                    },
                    label = "iconCrossfade"
                ) { currentIcon ->
                    Icon(
                        imageVector = currentIcon,
                        contentDescription = label,
                        tint = iconColor,
                        modifier = Modifier.size((buttonSize * 0.41f).roundToInt().dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Tactile spring button with Material 3 Expressive morphing corners, Liquid Glass
 * refractive touch glow (Doherty Threshold <100ms), press-down LRA micro-tick,
 * and flat or brushed fills.
 */
@Composable
private fun PressableCircleButton(
    size: Int,
    cornerRadiusDp: Dp? = null,
    background: Color = Color.Transparent,
    backgroundBrush: Brush? = null,
    borderBrush: Brush? = null,
    shadowElevation: Float = 0f,
    shadowColor: Color = Color.Black,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val callHaptics = rememberCallHaptics()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) {
            callHaptics.pressDown(view)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 650f),
        label = "pressScale"
    )
    val touchGlow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 80 else 280),
        label = "touchGlow"
    )
    val shape = if (cornerRadiusDp != null) RoundedCornerShape(cornerRadiusDp) else CircleShape

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (shadowElevation > 0f) {
                    Modifier.shadow(
                        elevation = shadowElevation.dp,
                        shape = shape,
                        ambientColor = shadowColor.copy(alpha = 0.4f),
                        spotColor = shadowColor
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (backgroundBrush != null) {
                    Modifier.background(backgroundBrush)
                } else {
                    Modifier.background(background)
                }
            )
            .then(
                if (borderBrush != null) Modifier.border(0.8.dp, borderBrush, shape) else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        if (touchGlow > 0.01f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.26f * touchGlow),
                            Color.Transparent
                        ),
                        center = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = this.size.maxDimension * 0.72f
                    )
                )
            }
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private data class ContactPosterTheme(
    val top: Color,
    val bottom: Color,
    val aura: Color,
    val secondaryAura: Color
)

private val ContactPosterGallery = listOf(
    ContactPosterTheme(Color(0xFF1C2D4A), Color(0xFF0D1524), Color(0xFF38BDF8), Color(0xFF818CF8)), // Midnight Sapphire
    ContactPosterTheme(Color(0xFF12362E), Color(0xFF0A1A17), Color(0xFF34D399), Color(0xFF2DD4BF)), // Deep Emerald
    ContactPosterTheme(Color(0xFF2B1E47), Color(0xFF130E22), Color(0xFFA78BFA), Color(0xFF818CF8)), // Imperial Amethyst
    ContactPosterTheme(Color(0xFF3D1D2C), Color(0xFF1A0D14), Color(0xFFFB7185), Color(0xFFF43F5E)), // Obsidian Rose
    ContactPosterTheme(Color(0xFF38281A), Color(0xFF19120C), Color(0xFFFBBF24), Color(0xFFFB923C)), // Smoked Bronze
    ContactPosterTheme(Color(0xFF13333E), Color(0xFF0A191F), Color(0xFF2DD4BF), Color(0xFF38BDF8))  // Deep Abyssal Teal
)

private fun contactPosterFor(seed: String): ContactPosterTheme {
    val idx = (seed.hashCode() and 0x7FFFFFFF) % ContactPosterGallery.size
    return ContactPosterGallery[idx]
}

private fun accentFor(session: CallSessionInfo): Color = when {
    session.state == CallState.ENDED -> DangerRed
    session.forceTorOnly -> AccentStrict
    session.transportMode == CallTransportMode.DIRECT_P2P -> AccentDirect
    else -> AccentTor
}

private fun shortOnion(onion: String): String {
    val host = onion.substringBefore(':').removeSuffix(".onion")
    return if (host.length > 20) "${host.take(10)}…${host.takeLast(8)}.onion" else onion
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
