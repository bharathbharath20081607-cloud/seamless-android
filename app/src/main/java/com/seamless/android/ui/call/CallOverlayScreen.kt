package com.seamless.android.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.seamless.android.call.CallSessionInfo
import com.seamless.android.call.CallState
import com.seamless.android.call.CallTransportMode
import com.seamless.android.call.SeamlessCallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val CallBgTop = Color(0xFF060911)
private val CallBgMid = Color(0xFF0B1322)
private val CallBgBottom = Color(0xFF05080F)
private val AccentDirect = Color(0xFF00E5FF)
private val AccentTor = Color(0xFF00E676)
private val AccentStrict = Color(0xFFB388FF)
private val DangerRed = Color(0xFFFF3B5C)
private val MutedRed = Color(0xFFFF5252)
private val WarnAmber = Color(0xFFFFC94D)

private enum class LinkQuality(val label: String, val bars: Int, val color: Color) {
    EXCELLENT("Excellent", 4, Color(0xFF00E676)),
    GOOD("Good", 3, Color(0xFF76FF03)),
    FAIR("Fair", 2, Color(0xFFFFC94D)),
    POOR("Weak", 1, Color(0xFFFF7043)),
    RECONNECTING("Reconnecting", 0, Color(0xFFFF5252))
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
    val callManager = remember { SeamlessCallManager.getInstance(context) }
    val liveSession by callManager.sessionFlow.collectAsState()

    var cachedSession by remember { mutableStateOf<CallSessionInfo?>(null) }
    val activeSession = liveSession
    if (activeSession != null && activeSession.state != CallState.IDLE) {
        cachedSession = activeSession
    }

    val isOverlayVisible = activeSession != null && activeSession.state != CallState.IDLE
    val displaySession = activeSession?.takeIf { it.state != CallState.IDLE } ?: cachedSession ?: return

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (callManager.sessionFlow.value?.state == CallState.INCOMING_RINGING) {
                callManager.acceptIncomingCall()
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for encrypted voice calls",
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
                    val s = callManager.sessionFlow.value
                    if (s != null && s.state != CallState.INCOMING_RINGING && s.state != CallState.ENDED) {
                        callManager.setMinimized(true)
                    }
                },
                onAccept = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        callManager.acceptIncomingCall()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onDecline = { callManager.declineIncomingCall() },
                onHangUp = { callManager.endCall() },
                onToggleMute = { callManager.toggleMute() },
                onToggleSpeaker = { callManager.toggleSpeakerphone() },
                onToggleTorOnly = { callManager.toggleForceTorOnly() },
                onRefreshRoute = {
                    callManager.refreshCallRoute()
                    Toast.makeText(context, "Re-syncing P2P + Tor route…", Toast.LENGTH_SHORT).show()
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
    onHangUp: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
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
        animationSpec = tween(90),
        label = "miniVoice"
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xF20E1420),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        accentColor.copy(alpha = 0.35f + activeVoice * 0.5f),
                        accentColor.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onExpand()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini avatar with live voice ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            val s = 1f + activeVoice * 0.18f
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f))
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C2638))
                        .border(1.5.dp, accentColor, CircleShape)
                ) {
                    Text(
                        text = session.peerNickname.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.peerNickname,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusSub = if (session.state == CallState.ACTIVE) {
                    val transportLabel = when {
                        session.forceTorOnly -> "Tor Strict"
                        session.isMultipathActive && isDirectP2p -> "P2P + Tor"
                        isDirectP2p -> "Direct P2P"
                        else -> "Tor Onion"
                    }
                    "${formatDuration(session.durationSeconds)} • $transportLabel"
                } else {
                    session.statusMessage
                }
                Text(
                    text = statusSub,
                    color = accentColor.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleMute()
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (session.isMuted) MutedRed.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)
                    )
            ) {
                Icon(
                    imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (session.isMuted) "Unmute" else "Mute",
                    tint = if (session.isMuted) MutedRed else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHangUp()
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DangerRed)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End call",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Expand call",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
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
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleTorOnly: () -> Unit,
    onRefreshRoute: () -> Unit
) {
    val canMinimize = session.state != CallState.INCOMING_RINGING && session.state != CallState.ENDED

    BackHandler {
        // Never dismiss a ringing call or the ended summary by accident
        if (canMinimize) onMinimize()
    }

    val primaryAccent by animateColorAsState(
        targetValue = accentFor(session),
        animationSpec = tween(600),
        label = "primaryAccent"
    )
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    val secondaryAccent by animateColorAsState(
        targetValue = if (isDirectP2p) AccentTor else AccentDirect,
        animationSpec = tween(600),
        label = "secondaryAccent"
    )

    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset.value * 0.55f
                val shrink = (dragOffset.value / 2400f).coerceIn(0f, 0.08f)
                scaleX = 1f - shrink
                scaleY = 1f - shrink
            }
            .clip(RoundedCornerShape(if (dragOffset.value > 1f) 28.dp else 0.dp))
            .background(Brush.verticalGradient(listOf(CallBgTop, CallBgMid, CallBgBottom)))
            // Swipe down to minimize; this pointer node also blocks touch leakage
            .pointerInput(canMinimize) {
                val threshold = 140.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (canMinimize && dragOffset.value > threshold) {
                                onMinimize()
                            } else {
                                dragOffset.animateTo(0f, spring(dampingRatio = 0.7f))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                    onVerticalDrag = { change, amount ->
                        if (canMinimize) {
                            change.consume()
                            scope.launch {
                                dragOffset.snapTo((dragOffset.value + amount).coerceAtLeast(0f))
                            }
                        }
                    }
                )
            }
    ) {
        AuroraBackground(
            primaryAccent = primaryAccent,
            secondaryAccent = secondaryAccent,
            isEnded = session.state == CallState.ENDED
        )

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
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
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
                    onMinimize = onMinimize
                )
            }

            // ── Center: orb, identity, status, spectrum, security code, telemetry ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                VoiceReactiveAvatarOrb(
                    peerNickname = session.peerNickname,
                    callState = session.state,
                    primaryAccent = primaryAccent,
                    secondaryAccent = secondaryAccent,
                    callManager = callManager
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = session.peerNickname,
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (session.peerOnion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = shortOnion(session.peerOnion),
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
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
                        Spacer(modifier = Modifier.height(12.dp))
                        SecurityCodeChip(callManager = callManager, primaryAccent = primaryAccent)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                CallTelemetryCard(
                    session = session,
                    callManager = callManager,
                    primaryAccent = primaryAccent
                )
            }

            // ── Bottom controls ──
            when (session.state) {
                CallState.INCOMING_RINGING -> IncomingCallActionRow(
                    onDecline = onDecline,
                    onAccept = onAccept
                )
                CallState.ENDED -> CallEndedPanel(session = session)
                else -> ActiveCallControlDock(
                    session = session,
                    primaryAccent = primaryAccent,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleTorOnly = onToggleTorOnly,
                    onRefreshRoute = onRefreshRoute,
                    onHangUp = onHangUp
                )
            }
        }
    }
}

/**
 * Slow-drifting aurora light blobs tinted by the active route color.
 * Drawn in a single Canvas (draw phase only, no recomposition per frame).
 */
@Composable
private fun AuroraBackground(
    primaryAccent: Color,
    secondaryAccent: Color,
    isEnded: Boolean
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )
    val dim = if (isEnded) 0.45f else 1f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r1 = w * 0.85f
        val c1 = Offset(w * (0.25f + 0.12f * cos(phase)), h * (0.22f + 0.06f * sin(phase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryAccent.copy(alpha = 0.20f * dim), Color.Transparent),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )
        val r2 = w * 0.75f
        val c2 = Offset(w * (0.80f - 0.12f * sin(phase)), h * (0.62f + 0.07f * cos(phase)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryAccent.copy(alpha = 0.13f * dim), Color.Transparent),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )
        val r3 = w * 0.6f
        val c3 = Offset(w * 0.5f, h * (1.02f + 0.03f * sin(phase * 2f)))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7C4DFF).copy(alpha = 0.10f * dim), Color.Transparent),
                center = c3,
                radius = r3
            ),
            radius = r3,
            center = c3
        )
    }
}

@Composable
private fun CallTopBar(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    canMinimize: Boolean,
    onMinimize: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (canMinimize) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMinimize()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize call",
                    tint = Color.White
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(primaryAccent.copy(alpha = 0.13f))
                .border(1.dp, primaryAccent.copy(alpha = 0.42f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = if (isDirectP2p && !session.forceTorOnly) Icons.Default.Speed else Icons.Default.Lock,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = badgeText,
                color = primaryAccent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
        }

        // Live link-quality bars (only meaningful once connected)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        ) {
            if (session.state == CallState.ACTIVE) {
                SignalBars(session = session, callManager = callManager)
            } else {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SignalBars(session: CallSessionInfo, callManager: SeamlessCallManager) {
    val quality = rememberLinkQuality(session, callManager)
    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val barCount = 4
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val barH = size.height * (0.35f + 0.65f * (i + 1) / barCount)
            val active = i < quality.bars
            drawRoundRect(
                color = if (active) quality.color else Color.White.copy(alpha = 0.18f),
                topLeft = Offset(i * (barW + gap), size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 3f, barW / 3f)
            )
        }
    }
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
    val dotsTransition = rememberInfiniteTransition(label = "dots")
    val dotPhase by dotsTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3.99f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "dotPhase"
    )
    val dots = ".".repeat(dotPhase.toInt().coerceIn(0, 3))

    when (session.state) {
        CallState.ACTIVE -> {
            Text(
                text = formatDuration(session.durationSeconds),
                color = primaryAccent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            if (session.isMuted) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MutedRed.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.MicOff, null, tint = MutedRed, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Your microphone is muted", color = MutedRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        CallState.INCOMING_RINGING -> Text(
            text = "Incoming encrypted call$dots",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        CallState.OUTGOING_RINGING -> Text(
            text = "Ringing$dots",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        CallState.OUTGOING_DIALING -> Text(
            text = "Connecting secure circuit$dots",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        CallState.ENDED -> Text(
            text = session.statusMessage.ifBlank { "Call ended" },
            color = DangerRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        CallState.IDLE -> Unit
    }
}

/**
 * Isolated leaf composable collecting 50 Hz voice level flows; the parent never recomposes per frame.
 */
@Composable
private fun VoiceReactiveAvatarOrb(
    peerNickname: String,
    callState: CallState,
    primaryAccent: Color,
    secondaryAccent: Color,
    callManager: SeamlessCallManager
) {
    val localVoice by callManager.localVoiceLevelFlow.collectAsState()
    val remoteVoice by callManager.remoteVoiceLevelFlow.collectAsState()

    val smoothRemote by animateFloatAsState(
        targetValue = remoteVoice.coerceIn(0f, 1f),
        animationSpec = tween(75),
        label = "smoothRemoteOrb"
    )
    val smoothLocal by animateFloatAsState(
        targetValue = localVoice.coerceIn(0f, 1f),
        animationSpec = tween(75),
        label = "smoothLocalOrb"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPulse"
    )
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbSpin"
    )

    val isEnded = callState == CallState.ENDED

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(196.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.29f

            when {
                isEnded -> Unit
                callState == CallState.ACTIVE -> {
                    val combined = max(smoothRemote, smoothLocal)
                    val outer = baseRadius * (1.24f + combined * 0.40f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.10f + smoothRemote * 0.22f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outer * 1.15f
                        ),
                        radius = outer * 1.15f,
                        center = center
                    )
                    drawCircle(
                        color = primaryAccent.copy(alpha = 0.30f + smoothRemote * 0.40f),
                        radius = outer,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    val mid = baseRadius * (1.10f + smoothLocal * 0.24f + smoothRemote * 0.10f)
                    drawCircle(
                        color = secondaryAccent.copy(alpha = 0.12f + smoothLocal * 0.25f),
                        radius = mid,
                        center = center
                    )
                    // Rotating encrypted "orbit" sweep
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, primaryAccent.copy(alpha = 0.75f), Color.Transparent),
                            center = center
                        ),
                        startAngle = spin,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 1.08f, center.y - baseRadius * 1.08f),
                        size = Size(baseRadius * 2.16f, baseRadius * 2.16f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                else -> {
                    // Sonar ripples while dialing / ringing
                    for (k in 0..2) {
                        val p = (ringPulse + k / 3f) % 1f
                        drawCircle(
                            color = primaryAccent.copy(alpha = (1f - p) * 0.30f),
                            radius = baseRadius * (1.05f + p * 0.62f),
                            center = center,
                            style = Stroke(width = (2.5f - p * 1.5f).dp.toPx())
                        )
                    }
                    drawArc(
                        color = primaryAccent.copy(alpha = 0.55f),
                        startAngle = spin * 1.6f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius * 1.08f, center.y - baseRadius * 1.08f),
                        size = Size(baseRadius * 2.16f, baseRadius * 2.16f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        val avatarScale = if (callState == CallState.ACTIVE) 1f + max(smoothRemote, smoothLocal) * 0.05f else 1f
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                    alpha = if (isEnded) 0.6f else 1f
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF1A253A), Color(0xFF2E4060))))
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            if (isEnded) DangerRed else primaryAccent,
                            if (isEnded) DangerRed.copy(alpha = 0.4f) else secondaryAccent
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Text(
                text = peerNickname.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 20-bar symmetric spectrum driven by local & remote voice levels.
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
        animationSpec = tween(70),
        label = "specRemote"
    )
    val smoothLocal by animateFloatAsState(
        targetValue = if (isMuted) 0f else localLevel.coerceIn(0f, 1f),
        animationSpec = tween(70),
        label = "specLocal"
    )

    val phaseTransition = rememberInfiniteTransition(label = "specPhase")
    val wavePhase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(
        modifier = Modifier
            .width(230.dp)
            .height(30.dp)
    ) {
        val barCount = 20
        val spacing = 4.dp.toPx()
        val barWidth = ((size.width - spacing * (barCount - 1)) / barCount).coerceAtLeast(3f)
        val centerIndex = (barCount - 1) / 2f
        val remoteDominant = smoothRemote >= smoothLocal
        val energy = max(smoothRemote, smoothLocal * 0.9f)

        for (i in 0 until barCount) {
            val dist = abs(i - centerIndex) / centerIndex
            val envelope = (1f - dist * 0.7f).coerceIn(0.2f, 1f)
            val harmonic = sin(wavePhase + i * 0.62f) * 0.5f + 0.5f
            val h = (0.14f + energy * envelope * (0.45f + 0.55f * harmonic)).coerceIn(0.12f, 1f)
            val barHeight = size.height * h
            val color = if (remoteDominant) primaryAccent else secondaryAccent
            drawRoundRect(
                color = color.copy(alpha = 0.35f + h * 0.65f),
                topLeft = Offset(i * (barWidth + spacing), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = when {
            smoothRemote > 0.06f && smoothRemote >= smoothLocal -> "Peer speaking"
            smoothLocal > 0.06f -> "You're speaking"
            else -> " "
        },
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 10.sp,
        letterSpacing = 0.8.sp
    )
}

/**
 * Tap-to-expand Short Authentication String. Both callers see the same 4 emojis when no one
 * is intercepting the call; reading them aloud verifies the end-to-end key.
 */
@Composable
private fun SecurityCodeChip(callManager: SeamlessCallManager, primaryAccent: Color) {
    val code by callManager.securityCodeFlow.collectAsState()
    if (code.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, primaryAccent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = !expanded
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = code.joinToString("  "),
                fontSize = 20.sp
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = "Ask your peer to read their 4 emojis.\nIf they match yours, nobody is intercepting this call.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * Glassmorphic telemetry card: route, RTT, buffer, link quality and live TX/RX activity LEDs.
 */
@Composable
private fun CallTelemetryCard(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color
) {
    val jitterMs by callManager.jitterBufferMsFlow.collectAsState()
    val txPulse by callManager.txPacketPulseFlow.collectAsState()
    val rxPulse by callManager.rxPacketPulseFlow.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.state) {
        while (session.state == CallState.ACTIVE) {
            delay(250L)
            now = System.currentTimeMillis()
        }
    }

    val effectiveJitter = if (jitterMs > 0) jitterMs else session.jitterBufferMs

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (session.state != CallState.ACTIVE) {
                Text(
                    text = session.statusMessage.ifBlank { "Negotiating end-to-end encrypted audio…" },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                ActiveTelemetryContent(
                    session = session,
                    callManager = callManager,
                    primaryAccent = primaryAccent,
                    effectiveJitter = effectiveJitter,
                    txPulse = txPulse,
                    rxPulse = rxPulse,
                    now = now
                )
            }
        }
    }
}

@Composable
private fun ActiveTelemetryContent(
    session: CallSessionInfo,
    callManager: SeamlessCallManager,
    primaryAccent: Color,
    effectiveJitter: Int,
    txPulse: Long,
    rxPulse: Long,
    now: Long
) {
    val isDirectP2p = session.transportMode == CallTransportMode.DIRECT_P2P
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val routeLabel = when {
                session.forceTorOnly -> "Tor v3 Hidden Service • IP hidden"
                isDirectP2p && session.isMultipathActive -> "Direct P2P + Tor multipath"
                isDirectP2p -> "Direct P2P UDP"
                else -> "Tor v3 onion circuit"
            }
            Text(
                text = routeLabel,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryStat(
                    label = "LATENCY",
                    value = if (session.rttMs > 0) "${session.rttMs} ms" else "—",
                    valueColor = Color.White
                )
                TelemetryStat(
                    label = "BUFFER",
                    value = "$effectiveJitter ms",
                    valueColor = Color.White
                )
                val quality = rememberLinkQuality(session, callManager)
                TelemetryStat(
                    label = "QUALITY",
                    value = quality.label,
                    valueColor = quality.color
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
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
                    color = if (session.isMuted) MutedRed else AccentTor
                )
                Spacer(modifier = Modifier.width(18.dp))
                ActivityLed(
                    label = if (rxLive) "RX live" else "RX silent",
                    active = rxLive,
                    color = primaryAccent
                )
            }
        }
    }
}

@Composable
private fun TelemetryStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
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
                    .background(if (active) color else Color.White.copy(alpha = 0.25f))
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IncomingCallActionRow(
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "answerHalo")
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "halo"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 26.dp, start = 18.dp, end = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PressableCircleButton(
                size = 74,
                background = DangerRed,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDecline()
                }
            ) {
                Icon(Icons.Default.CallEnd, "Decline call", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Decline", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val base = 37.dp.toPx()
                    for (k in 0..1) {
                        val p = (halo + k * 0.5f) % 1f
                        drawCircle(
                            color = AccentTor.copy(alpha = (1f - p) * 0.38f),
                            radius = base + p * 18.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                Box(modifier = Modifier.graphicsLayer { translationY = -bob * 5.dp.toPx() }) {
                    PressableCircleButton(
                        size = 74,
                        background = AccentTor,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAccept()
                        }
                    ) {
                        Icon(Icons.Default.Call, "Answer call", tint = Color(0xFF05180E), modifier = Modifier.size(32.dp))
                    }
                }
            }
            Text("Answer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CallEndedPanel(session: CallSessionInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 34.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DangerRed.copy(alpha = 0.18f))
                .border(1.dp, DangerRed.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.CallEnd, null, tint = DangerRed, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (session.connectedAtMs != null) {
                "Duration ${formatDuration(session.durationSeconds)}"
            } else {
                "Not connected"
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Session keys wiped from memory",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ActiveCallControlDock(
    session: CallSessionInfo,
    primaryAccent: Color,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleTorOnly: () -> Unit,
    onRefreshRoute: () -> Unit,
    onHangUp: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isActive = session.state == CallState.ACTIVE

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.White.copy(alpha = 0.06f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))
                    ),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlItem(
                    icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (session.isMuted) "Unmute" else "Mute",
                    isActive = session.isMuted,
                    activeColor = MutedRed,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleMute()
                    }
                )
                CallControlItem(
                    icon = if (session.isSpeakerphone) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PhoneInTalk,
                    label = if (session.isSpeakerphone) "Speaker" else "Earpiece",
                    isActive = session.isSpeakerphone,
                    activeColor = primaryAccent,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSpeaker()
                    }
                )
                CallControlItem(
                    icon = Icons.Default.Security,
                    label = if (session.forceTorOnly) "Tor Strict" else "Multipath",
                    isActive = session.forceTorOnly,
                    activeColor = AccentStrict,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTorOnly()
                    }
                )
                CallControlItem(
                    icon = Icons.Default.Refresh,
                    label = "Re-Sync",
                    isActive = false,
                    activeColor = WarnAmber,
                    enabled = isActive,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefreshRoute()
                    }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PressableCircleButton(
                        size = 54,
                        background = DangerRed,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHangUp()
                        }
                    ) {
                        Icon(Icons.Default.CallEnd, "Hang up", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("End", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
                }
            }
        }
        if (isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Swipe down to minimize",
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 10.sp,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun CallControlItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.35f }
    ) {
        PressableCircleButton(
            size = 54,
            background = if (isActive) activeColor.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.09f),
            borderColor = if (isActive) activeColor else Color.White.copy(alpha = 0.14f),
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.78f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

/**
 * Circle button with a spring press-scale for tactile feel.
 */
@Composable
private fun PressableCircleButton(
    size: Int,
    background: Color,
    borderColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "pressScale"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape) else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

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
