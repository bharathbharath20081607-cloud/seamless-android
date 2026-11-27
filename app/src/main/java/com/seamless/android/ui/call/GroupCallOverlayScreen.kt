package com.seamless.android.ui.call

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.seamless.android.call.CallTransportMode
import com.seamless.android.call.GroupCallManager
import com.seamless.android.call.GroupCallParticipant
import com.seamless.android.call.GroupCallReactionEvent
import com.seamless.android.call.GroupCallSessionInfo
import com.seamless.android.call.GroupCallState
import com.seamless.android.call.GroupStageLayoutMode
import com.seamless.android.call.GroupTopologyMode
import com.seamless.android.net.TorGroupMessaging
import com.seamless.android.ui.theme.PerceptualColorScience
import com.seamless.android.ui.theme.liquidGlass
import com.seamless.android.ui.theme.tactilePress
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private val GrpCanvasTop = Color(0xFF06080E)
private val GrpCanvasBottom = Color(0xFF04060A)
private val GrpTileBase = Color(0xFF111520)
private val GrpTileElevated = Color(0xFF171C2A)
private val GrpTextPrimary = Color(0xFFF3F6FB)
private val GrpTextSecondary = Color(0xFFB4BFCE)
private val GrpEmerald = PerceptualColorScience.oklchDisplayP3(0.76f, 0.16f, 160f)
private val GrpSapphire = PerceptualColorScience.oklchDisplayP3(0.73f, 0.145f, 255f)
private val GrpStrictViolet = PerceptualColorScience.oklchDisplayP3(0.70f, 0.16f, 292f)
private val GrpHandCyan = PerceptualColorScience.oklchDisplayP3(0.79f, 0.13f, 210f)
private val GrpDanger = Color(0xFFFB4F68)

private val StageReactions = listOf("👍", "❤️", "🔥", "👏", "😂", "🎉")

/**
 * Global Group Voice & Video Call overlay (`Aegis Swarm Conference™ v2`):
 * - Incoming ringing screen with permission handling
 * - Spotlight / Equal-Grid Conference Stage with 3-bar Display P3 voice equalizers
 * - Push-to-Talk ("Hold Mute to Speak"), Live Emoji Reactions, Per-Peer 0–200% Volume Boost
 * - Draggable Floating Liquid-Glass pill & 4-Emoji Verification / Route Boost modal
 */
@Composable
fun SeamlessGroupCallOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { GroupCallManager.getInstance(context) }
    val liveSession by manager.sessionFlow.collectAsState()
    val videoBitmaps by manager.participantVideoBitmapsFlow.collectAsState()
    val reactionEvents by manager.reactionEventsFlow.collectAsState()

    DisposableEffect(lifecycleOwner, manager) {
        manager.localVideoEngine.setLifecycleOwner(lifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    manager.localVideoEngine.setLifecycleOwner(lifecycleOwner)
                    manager.onAppForegroundStateChanged(true)
                }
                Lifecycle.Event.ON_STOP -> manager.onAppForegroundStateChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var cached by remember { mutableStateOf<GroupCallSessionInfo?>(null) }
    if (liveSession != null && liveSession?.state != GroupCallState.IDLE) {
        cached = liveSession
    }
    LaunchedEffect(liveSession) {
        if (liveSession == null || liveSession?.state == GroupCallState.IDLE) {
            delay(240L)
            if (liveSession == null || liveSession?.state == GroupCallState.IDLE) {
                cached = null
            }
        }
    }

    val visible = liveSession != null && liveSession?.state != GroupCallState.IDLE
    val display = liveSession?.takeIf { it.state != GroupCallState.IDLE } ?: cached ?: return

    var pendingJoinWithVideo by remember { mutableStateOf(false) }

    val multiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasMic = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCam = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            Toast.makeText(context, "Microphone permission is required for group calls", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (manager.sessionFlow.value?.state == GroupCallState.INCOMING_RINGING) {
            manager.acceptIncomingGroupCall(withVideo = pendingJoinWithVideo && hasCam)
        } else if (pendingJoinWithVideo && hasCam) {
            manager.toggleLocalVideo(true)
        }
    }

    val camPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) manager.toggleLocalVideo(true)
        else Toast.makeText(context, "Camera permission is required for group video", Toast.LENGTH_SHORT).show()
    }

    val isMinimized = visible &&
        display.isMinimized &&
        display.state != GroupCallState.INCOMING_RINGING &&
        display.state != GroupCallState.ENDED
    val isFullScreen = visible && !isMinimized

    var pillOffsetX by remember { mutableFloatStateOf(0f) }
    var pillOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(32f)
    ) {
        AnimatedVisibility(
            visible = isMinimized,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .offset { IntOffset(pillOffsetX.roundToInt(), pillOffsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pillOffsetX = (pillOffsetX + dragAmount.x).coerceIn(-120f, 120f)
                        pillOffsetY = (pillOffsetY + dragAmount.y).coerceIn(0f, 950f)
                    }
                }
        ) {
            MinimizedGroupCallPill(
                session = display,
                onExpand = { manager.setMinimized(false) },
                onToggleMute = { manager.toggleMute() },
                onLeave = { manager.leaveCall() }
            )
        }

        AnimatedVisibility(
            visible = isFullScreen,
            enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(tween(250)),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (display.state == GroupCallState.INCOMING_RINGING) {
                IncomingGroupCallScreen(
                    session = display,
                    onDecline = { manager.declineIncomingGroupCall() },
                    onJoin = { withVideo ->
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic && (!withVideo || hasCam)) {
                            manager.acceptIncomingGroupCall(withVideo)
                        } else {
                            pendingJoinWithVideo = withVideo
                            val needed = buildList {
                                if (!hasMic) add(Manifest.permission.RECORD_AUDIO)
                                if (withVideo && !hasCam) add(Manifest.permission.CAMERA)
                            }.toTypedArray()
                            multiPermLauncher.launch(needed)
                        }
                    }
                )
            } else {
                ActiveGroupConferenceScreen(
                    session = display,
                    manager = manager,
                    videoBitmaps = videoBitmaps,
                    reactionEvents = reactionEvents,
                    onMinimize = {
                        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km?.isKeyguardLocked != true && display.state == GroupCallState.ACTIVE) {
                            manager.setMinimized(true)
                        }
                    },
                    onToggleCamera = {
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam || display.isLocalVideoEnabled) manager.toggleLocalVideo()
                        else camPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }
}

@Composable
private fun IncomingGroupCallScreen(
    session: GroupCallSessionInfo,
    onDecline: () -> Unit,
    onJoin: (Boolean) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "incoming_group_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GrpCanvasTop, GrpTileBase, GrpCanvasBottom)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .liquidGlass(RoundedCornerShape(50), tintSpill = GrpEmerald)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Lock, null, tint = GrpEmerald, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "End-to-End Encrypted Group Call",
                    color = GrpTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(152.dp)
                            .graphicsLayer { scaleX = pulse; scaleY = pulse }
                            .clip(CircleShape)
                            .background(GrpEmerald.copy(alpha = 0.14f))
                    )
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GrpSapphire.copy(0.35f), GrpEmerald.copy(0.28f))))
                            .border(1.5.dp, GrpEmerald.copy(0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = session.groupName.trim().split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                            .joinToString("")
                            .ifBlank { "G" }
                        Text(initials, color = GrpTextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    session.groupName,
                    color = GrpTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    session.statusMessage.ifBlank { "Incoming group call" },
                    color = GrpTextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(GrpDanger)
                            .tactilePress { onDecline() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Decline", color = GrpTextSecondary, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(GrpEmerald)
                            .tactilePress { onJoin(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Call, "Join Voice", tint = Color(0xFF04140D), modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Join Voice", color = GrpTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(GrpSapphire)
                            .tactilePress { onJoin(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Videocam, "Join Video", tint = Color(0xFF04101C), modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Join Video", color = GrpTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActiveGroupConferenceScreen(
    session: GroupCallSessionInfo,
    manager: GroupCallManager,
    videoBitmaps: Map<String, Bitmap>,
    reactionEvents: List<GroupCallReactionEvent>,
    onMinimize: () -> Unit,
    onToggleCamera: () -> Unit
) {
    val context = LocalContext.current
    var showSecuritySheet by remember { mutableStateOf(false) }
    var showAdminLeaveDialog by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var inspectedPeerOnion by remember { mutableStateOf<String?>(null) }
    var isLocalAdmin by remember(session.groupId) { mutableStateOf(false) }

    LaunchedEffect(session.groupId) {
        isLocalAdmin = runCatching {
            TorGroupMessaging.getInstance(context).isLocalAdmin(session.groupId)
        }.getOrDefault(false)
    }

    BackHandler(enabled = session.state == GroupCallState.ACTIVE) {
        onMinimize()
    }

    val spotlightTarget = remember(
        session.participants,
        session.pinnedSpeakerOnion,
        session.activeSpeakerOnion,
        session.layoutMode,
        session.isLocalVideoEnabled,
        videoBitmaps
    ) {
        val pinned = session.pinnedSpeakerOnion?.let { pin ->
            session.participants.firstOrNull { it.onionAddress == pin }
        }
        if (session.layoutMode == GroupStageLayoutMode.EQUAL_GRID) {
            pinned
        } else {
            val remoteActiveVideo = session.activeSpeakerOnion?.let { active ->
                session.participants.firstOrNull {
                    !it.isLocal && it.onionAddress == active &&
                        (it.isVideoEnabled || videoBitmaps[it.onionAddress] != null)
                }
            }
            val anyRemoteVideo = session.participants.firstOrNull {
                !it.isLocal && (it.isVideoEnabled || videoBitmaps[it.onionAddress] != null)
            }
            val remoteActiveSpeaker = if (session.participants.size >= 3) {
                session.activeSpeakerOnion?.let { active ->
                    session.participants.firstOrNull { !it.isLocal && it.onionAddress == active }
                }
            } else null
            pinned ?: remoteActiveVideo ?: anyRemoteVideo ?: remoteActiveSpeaker
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GrpCanvasTop, GrpTileBase, GrpCanvasBottom)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Glass Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .size(40.dp)
                        .liquidGlass(CircleShape)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = GrpTextPrimary)
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.groupName,
                        color = GrpTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${session.participants.size} in call · ${formatGroupDuration(session.durationSeconds)}",
                        color = GrpTextSecondary,
                        fontSize = 12.sp
                    )
                }

                if (session.securityEmojis.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .liquidGlass(RoundedCornerShape(50), tintSpill = GrpEmerald)
                            .tactilePress { showSecuritySheet = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.securityEmojis.joinToString(""), fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                }

                IconButton(
                    onClick = { manager.toggleStageLayoutMode() },
                    modifier = Modifier
                        .size(40.dp)
                        .liquidGlass(
                            CircleShape,
                            tintSpill = if (session.layoutMode == GroupStageLayoutMode.EQUAL_GRID) GrpSapphire else Color.Transparent
                        )
                ) {
                    Icon(
                        if (session.layoutMode == GroupStageLayoutMode.EQUAL_GRID) Icons.Rounded.ViewModule else Icons.Rounded.GridView,
                        "Switch Layout",
                        tint = if (session.layoutMode == GroupStageLayoutMode.EQUAL_GRID) GrpSapphire else GrpTextPrimary
                    )
                }
                Spacer(Modifier.width(6.dp))

                if (session.isLocalVideoEnabled) {
                    IconButton(
                        onClick = { manager.switchCamera() },
                        modifier = Modifier
                            .size(40.dp)
                            .liquidGlass(CircleShape)
                    ) {
                        Icon(Icons.Rounded.Cameraswitch, "Switch Camera", tint = GrpTextPrimary)
                    }
                    Spacer(Modifier.width(6.dp))
                }

                IconButton(
                    onClick = { manager.toggleForceTorOnly() },
                    modifier = Modifier
                        .size(40.dp)
                        .liquidGlass(
                            CircleShape,
                            tintSpill = if (session.forceTorOnly) GrpStrictViolet else GrpEmerald
                        )
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        "Strict Tor Toggle",
                        tint = if (session.forceTorOnly) GrpStrictViolet else GrpEmerald
                    )
                }
            }

            // Live Topology, RTT & Encryption Status Pill (Tappable for Security/Route Sheet)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GrpTileElevated.copy(alpha = 0.88f),
                    modifier = Modifier.clickable { showSecuritySheet = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = GrpEmerald, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        val rttTag = if (session.medianRttMs > 0) " · ${session.medianRttMs}ms" else ""
                        Text(
                            session.statusMessage.ifBlank { "Encrypted Swarm" } + rttTag,
                            color = GrpTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Stage: 2-Peer Floating PiP OR Multi-Peer Spotlight OR Bento Grid + Floating Reaction Bursts
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                val anyVideoInTwoPeer = session.participants.size == 2 &&
                    session.layoutMode == GroupStageLayoutMode.AUTO_SPOTLIGHT &&
                    (session.isLocalVideoEnabled || session.participants.any {
                        !it.isLocal && (it.isVideoEnabled || videoBitmaps[it.onionAddress] != null)
                    })

                if (anyVideoInTwoPeer) {
                    val primary = spotlightTarget
                        ?: session.participants.firstOrNull { !it.isLocal }
                        ?: session.participants.first()
                    val secondary = session.participants.firstOrNull { it.onionAddress != primary.onionAddress }
                    Box(modifier = Modifier.fillMaxSize()) {
                        GroupParticipantTile(
                            participant = primary,
                            session = session,
                            manager = manager,
                            remoteBitmap = videoBitmaps[primary.onionAddress],
                            isSpotlight = true,
                            onInspectPeer = { if (!primary.isLocal) inspectedPeerOnion = primary.onionAddress },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (secondary != null) {
                            GroupParticipantTile(
                                participant = secondary,
                                session = session,
                                manager = manager,
                                remoteBitmap = videoBitmaps[secondary.onionAddress],
                                isSpotlight = false,
                                onInspectPeer = { if (!secondary.isLocal) inspectedPeerOnion = secondary.onionAddress },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 10.dp, bottom = if (reactionEvents.isNotEmpty()) 58.dp else 12.dp)
                                    .width(108.dp)
                                    .height(152.dp)
                                    .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.55f))
                            )
                        }
                    }
                } else if (spotlightTarget != null && session.participants.size >= 2) {
                    val others = session.participants.filter { it.onionAddress != spotlightTarget.onionAddress }
                    Column(modifier = Modifier.fillMaxSize()) {
                        GroupParticipantTile(
                            participant = spotlightTarget,
                            session = session,
                            manager = manager,
                            remoteBitmap = videoBitmaps[spotlightTarget.onionAddress],
                            isSpotlight = true,
                            onInspectPeer = { if (!spotlightTarget.isLocal) inspectedPeerOnion = spotlightTarget.onionAddress },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(118.dp)
                        ) {
                            items(others, key = { it.onionAddress }) { p ->
                                GroupParticipantTile(
                                    participant = p,
                                    session = session,
                                    manager = manager,
                                    remoteBitmap = videoBitmaps[p.onionAddress],
                                    isSpotlight = false,
                                    onInspectPeer = { if (!p.isLocal) inspectedPeerOnion = p.onionAddress },
                                    modifier = Modifier
                                        .width(104.dp)
                                        .height(116.dp)
                                )
                            }
                        }
                    }
                } else {
                    val columns = when {
                        session.participants.size <= 1 -> 1
                        session.participants.size <= 6 -> 2
                        else -> 3
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(session.participants, key = { it.onionAddress }) { p ->
                            GroupParticipantTile(
                                participant = p,
                                session = session,
                                manager = manager,
                                remoteBitmap = videoBitmaps[p.onionAddress],
                                isSpotlight = false,
                                onInspectPeer = { if (!p.isLocal) inspectedPeerOnion = p.onionAddress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (session.participants.size <= 2) 1.05f else 0.88f)
                            )
                        }
                    }
                }

                // Live Rising Floating Reaction Bursts
                if (reactionEvents.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        reactionEvents.takeLast(4).forEach { ev ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xCC121723)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(ev.emoji, fontSize = 15.sp)
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        ev.senderNickname,
                                        color = GrpTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Floating Emoji Reaction Tray + PTT Hint
            AnimatedVisibility(
                visible = showReactionPicker,
                enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically { it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .liquidGlass(RoundedCornerShape(50), elevated = true)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StageReactions.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .tactilePress {
                                    manager.sendCallReaction(emoji)
                                    showReactionPicker = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }

            if (session.isPushToTalkActive) {
                Text(
                    "Push-to-Talk Live — release to mute",
                    color = GrpHandCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            // Bottom M3 Expressive Liquid-Glass Control Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .liquidGlass(RoundedCornerShape(30.dp), elevated = true)
                    .padding(horizontal = 8.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute with Press-and-Hold Push-to-Talk!
                val micActive = session.isMuted || session.isPushToTalkActive
                val micColor = when {
                    session.isPushToTalkActive -> GrpHandCyan
                    session.isMuted -> GrpTextPrimary
                    else -> Color.White.copy(alpha = 0.11f)
                }
                val micCorner by animateDpAsState(
                    targetValue = if (micActive) 18.dp else 26.dp,
                    animationSpec = spring(),
                    label = "ptt_corner"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(micCorner))
                            .background(micColor)
                            .pointerInput(session.isMuted) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    if (!manager.sessionFlow.value?.isMuted.let { it == true }) {
                                        // Wait for release and toggle mute on clean tap
                                        val up = withTimeoutOrNull(4_000L) {
                                            do {
                                                val ev = awaitPointerEvent()
                                            } while (ev.changes.any { it.pressed })
                                            true
                                        }
                                        if (up == true) manager.toggleMute()
                                    } else {
                                        var activatedPtt = false
                                        val quickUp = withTimeoutOrNull(180L) {
                                            do {
                                                val ev = awaitPointerEvent()
                                            } while (ev.changes.any { it.pressed })
                                            true
                                        }
                                        if (quickUp == true) {
                                            manager.toggleMute()
                                        } else {
                                            activatedPtt = true
                                            manager.setPushToTalkPressed(true)
                                            do {
                                                val ev = awaitPointerEvent()
                                            } while (ev.changes.any { it.pressed })
                                            if (activatedPtt) manager.setPushToTalkPressed(false)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = "Mute or Hold for PTT",
                            tint = if (micActive) Color(0xFF0A0D14) else GrpTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            session.isPushToTalkActive -> "PTT Live"
                            session.isMuted -> "Hold PTT"
                            else -> "Mute"
                        },
                        color = if (session.isPushToTalkActive) GrpHandCyan else GrpTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                val routeIcon = when (session.audioRoute) {
                    AudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                    AudioRoute.WIRED_HEADSET -> Icons.Rounded.HeadsetMic
                    AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                    AudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
                }
                val routeLabel = when (session.audioRoute) {
                    AudioRoute.BLUETOOTH -> "Bluetooth"
                    AudioRoute.WIRED_HEADSET -> "Headset"
                    AudioRoute.SPEAKER -> "Speaker"
                    AudioRoute.EARPIECE -> "Earpiece"
                }
                ConferenceDockButton(
                    icon = routeIcon,
                    label = routeLabel,
                    active = session.audioRoute == AudioRoute.SPEAKER || session.audioRoute == AudioRoute.BLUETOOTH,
                    activeColor = GrpSapphire,
                    activeIconTint = Color(0xFF06101B),
                    onClick = { manager.cycleAudioRoute() }
                )

                ConferenceDockButton(
                    icon = if (session.isLocalVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label = if (session.isLocalVideoEnabled) "Cam On" else "Video",
                    active = session.isLocalVideoEnabled,
                    activeColor = GrpTextPrimary,
                    activeIconTint = Color(0xFF0A0D14),
                    onClick = onToggleCamera
                )

                ConferenceDockButton(
                    icon = Icons.Rounded.PanTool,
                    label = "Hand",
                    active = session.isHandRaised,
                    activeColor = GrpHandCyan,
                    activeIconTint = Color(0xFF051319),
                    onClick = { manager.toggleHandRaised() }
                )

                ConferenceDockButton(
                    icon = Icons.Rounded.EmojiEmotions,
                    label = "React",
                    active = showReactionPicker,
                    activeColor = GrpEmerald,
                    activeIconTint = Color(0xFF05160D),
                    onClick = { showReactionPicker = !showReactionPicker }
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(GrpDanger)
                            .tactilePress {
                                if (isLocalAdmin) showAdminLeaveDialog = true
                                else manager.leaveCall()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.CallEnd, "Leave Call", tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Leave", color = GrpDanger, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Per-Participant Audio Boost (0-200%) & Connection Sheet
    val inspectedPeer = inspectedPeerOnion?.let { onion ->
        session.participants.firstOrNull { it.onionAddress == onion }
    }
    if (inspectedPeer != null) {
        val pct = (inspectedPeer.localVolumeGain * 100f).roundToInt()
        AlertDialog(
            onDismissRequest = { inspectedPeerOnion = null },
            containerColor = Color(0xFF141925),
            title = {
                Text(inspectedPeer.nickname, color = GrpTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (inspectedPeer.transportMode == CallTransportMode.DIRECT_P2P) {
                            "Direct P2P Encrypted · ${inspectedPeer.rttMs.coerceAtLeast(1)}ms"
                        } else {
                            "Tor Onion Circuit · IP Hidden"
                        },
                        color = if (inspectedPeer.transportMode == CallTransportMode.DIRECT_P2P) GrpSapphire else GrpEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Participant Volume", color = GrpTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$pct%",
                            color = if (pct > 100) GrpEmerald else GrpTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = inspectedPeer.localVolumeGain,
                        onValueChange = { g -> manager.setParticipantVolume(inspectedPeer.onionAddress, g) },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = GrpEmerald,
                            activeTrackColor = GrpEmerald,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { manager.setParticipantVolume(inspectedPeer.onionAddress, 1.0f) }) {
                            Text("Reset 100%", color = GrpTextSecondary)
                        }
                        TextButton(onClick = {
                            manager.pinSpeaker(inspectedPeer.onionAddress)
                            inspectedPeerOnion = null
                        }) {
                            Text(
                                if (session.pinnedSpeakerOnion == inspectedPeer.onionAddress) "Unpin Stage" else "Pin to Stage",
                                color = GrpSapphire,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedPeerOnion = null }) {
                    Text("Done", color = GrpEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSecuritySheet) {
        AlertDialog(
            onDismissRequest = { showSecuritySheet = false },
            containerColor = Color(0xFF141925),
            title = {
                Text("Conference Verification", color = GrpTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Verify these emojis match across all members in the call. Media keys automatically rotate whenever someone joins or leaves (Room Epoch #${session.epoch}).",
                        color = GrpTextSecondary,
                        fontSize = 13.sp
                    )
                    Surface(
                        color = Color(0xFF0D111A),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            session.securityEmojis.joinToString("  "),
                            fontSize = 26.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        "Topology: ${if (session.topologyMode == GroupTopologyMode.DIRECT_MESH) "Direct P2P Mesh" else "Zero-Knowledge Swarm Relay"}",
                        color = GrpEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Direct Links: ${session.directPeersCount}  ·  Tor Onion Links: ${session.torPeersCount}" +
                            if (session.medianRttMs > 0) "  ·  Median RTT: ${session.medianRttMs}ms" else "",
                        color = GrpTextSecondary,
                        fontSize = 11.sp
                    )
                    session.participants.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = if (p.hasSenderKeyForEpoch) GrpEmerald else GrpTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                if (p.isLocal) "${p.nickname} (You)" else p.nickname,
                                color = GrpTextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (!session.forceTorOnly) {
                        Button(
                            onClick = {
                                manager.refreshRoutes()
                                Toast.makeText(context, "Boosting Direct P2P + Tor paths…", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GrpSapphire.copy(alpha = 0.2f),
                                contentColor = GrpSapphire
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Boost Direct Routes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecuritySheet = false }) {
                    Text("Done", color = GrpEmerald, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAdminLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showAdminLeaveDialog = false },
            containerColor = Color(0xFF141925),
            title = { Text("Leave Group Call?", color = GrpTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Leave only yourself so others can continue, or end the conference room for everyone.",
                    color = GrpTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdminLeaveDialog = false
                    manager.endCallForAll()
                }) {
                    Text("End for All", color = GrpDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdminLeaveDialog = false
                    manager.leaveCall()
                }) {
                    Text("Leave Only Me", color = GrpTextPrimary)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupParticipantTile(
    participant: GroupCallParticipant,
    session: GroupCallSessionInfo,
    manager: GroupCallManager,
    remoteBitmap: Bitmap?,
    isSpotlight: Boolean,
    onInspectPeer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSpeaking = !participant.isMuted && participant.voiceLevel > 0.08f
    val speakingGlow by animateFloatAsState(
        targetValue = if (isSpeaking) participant.voiceLevel.coerceIn(0.25f, 1f) else 0f,
        animationSpec = tween(140),
        label = "speaking_glow"
    )
    val isPinned = session.pinnedSpeakerOnion == participant.onionAddress
    val borderColor by animateColorAsState(
        targetValue = when {
            speakingGlow > 0.1f -> GrpEmerald.copy(alpha = 0.45f + speakingGlow * 0.5f)
            isPinned -> GrpSapphire.copy(alpha = 0.75f)
            else -> Color.White.copy(alpha = 0.10f)
        },
        animationSpec = tween(160),
        label = "tile_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (speakingGlow > 0.1f || isPinned) 2.2.dp else 1.dp,
        animationSpec = spring(),
        label = "tile_border_dp"
    )
    val recentReaction = participant.lastReactionEmoji?.takeIf {
        System.currentTimeMillis() - participant.lastReactionAtMs <= 4_200L
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(GrpTileElevated)
            .border(borderWidth, borderColor, RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = { manager.pinSpeaker(participant.onionAddress) },
                onLongClick = { if (!participant.isLocal) onInspectPeer() }
            )
    ) {
        if (participant.isLocal && session.isLocalVideoEnabled) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        manager.localVideoEngine.bindLocalPreview(lifecycleOwner, this)
                    }
                },
                update = { pv -> manager.localVideoEngine.bindLocalPreview(lifecycleOwner, pv) },
                onRelease = { pv -> manager.localVideoEngine.clearLocalPreviewIfMatches(pv) },
                modifier = Modifier.fillMaxSize()
            )
        } else if (!participant.isLocal && participant.isVideoEnabled && remoteBitmap != null && !remoteBitmap.isRecycled) {
            Image(
                bitmap = remoteBitmap.asImageBitmap(),
                contentDescription = "${participant.nickname} video",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = participant.nickname.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val hue = ((participant.senderTag4 and 0x7FFFFFFF) % 10) * 32f + 160f
            val avatarTint = PerceptualColorScience.oklchDisplayP3(0.58f, 0.15f, hue % 360f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(avatarTint.copy(alpha = 0.22f), GrpTileBase))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSpotlight) 86.dp else 54.dp)
                        .clip(CircleShape)
                        .background(avatarTint.copy(alpha = 0.24f))
                        .border(1.5.dp, avatarTint.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initial,
                        color = GrpTextPrimary,
                        fontSize = if (isSpotlight) 32.sp else 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top badges (Reaction / Hand Raised / Volume Gain / Pin / Route / Tune)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (recentReaction != null) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0xCC06080E)) {
                        Text(
                            recentReaction,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (participant.isHandRaised) {
                    Surface(shape = RoundedCornerShape(50), color = GrpHandCyan) {
                        Text(
                            "✋",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!participant.isLocal && participant.localVolumeGain != 1.0f) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0xAA06080E)) {
                        Text(
                            "${(participant.localVolumeGain * 100f).roundToInt()}%",
                            color = GrpEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) {
                    Icon(Icons.Rounded.PushPin, "Pinned", tint = GrpSapphire, modifier = Modifier.size(16.dp))
                } else if (!participant.isLocal) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0x9906080E)) {
                        Text(
                            if (participant.transportMode == CallTransportMode.DIRECT_P2P && participant.rttMs > 0) {
                                "${participant.rttMs}ms"
                            } else {
                                "Tor"
                            },
                            color = if (participant.transportMode == CallTransportMode.DIRECT_P2P) GrpSapphire else GrpEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0x9906080E))
                            .clickable { onInspectPeer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Tune, "Volume", tint = GrpTextSecondary, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Bottom Nameplate + Live Voice Equalizer / Mute Icon
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC04060A))))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (participant.isLocal) "You" else participant.nickname,
                color = GrpTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (participant.isMuted) {
                Icon(Icons.Rounded.MicOff, "Muted", tint = GrpDanger, modifier = Modifier.size(14.dp))
            } else if (isSpeaking) {
                VoiceEqualizerBars(level = participant.voiceLevel)
            }
        }
    }
}

@Composable
private fun VoiceEqualizerBars(level: Float, modifier: Modifier = Modifier) {
    val norm = level.coerceIn(0.15f, 1f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(5.dp + 7.dp * (norm * 0.7f), 6.dp + 10.dp * norm, 5.dp + 6.dp * (norm * 0.85f))
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GrpEmerald)
            )
        }
    }
}

@Composable
private fun ConferenceDockButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    activeIconTint: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor else Color.White.copy(alpha = 0.11f),
        animationSpec = tween(180),
        label = "dock_bg"
    )
    val corner by animateDpAsState(
        targetValue = if (active) 18.dp else 26.dp,
        animationSpec = spring(),
        label = "dock_corner"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(corner))
                .background(bgColor)
                .tactilePress { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) activeIconTint else GrpTextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) GrpTextPrimary else GrpTextSecondary,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MinimizedGroupCallPill(
    session: GroupCallSessionInfo,
    onExpand: () -> Unit,
    onToggleMute: () -> Unit,
    onLeave: () -> Unit
) {
    val activeSpeakerName = remember(session.activeSpeakerOnion, session.participants) {
        session.activeSpeakerOnion?.let { onion ->
            session.participants.firstOrNull { it.onionAddress == onion }?.let {
                if (it.isLocal) "You speaking" else "${it.nickname} speaking"
            }
        }
    }
    val speakingLvl = session.participants.maxOfOrNull { if (it.isMuted) 0f else it.voiceLevel } ?: 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(26.dp), tintSpill = GrpEmerald, elevated = true)
            .clickable { onExpand() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (speakingLvl > 0.08f) {
            VoiceEqualizerBars(level = speakingLvl)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(GrpEmerald)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${session.groupName} · ${session.participants.size} in call",
                color = GrpTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(formatGroupDuration(session.durationSeconds))
                    if (activeSpeakerName != null) append(" · $activeSpeakerName")
                },
                color = GrpEmerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleMute, modifier = Modifier.size(34.dp)) {
            Icon(
                if (session.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                "Mute",
                tint = if (session.isMuted) GrpDanger else GrpTextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onExpand, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.OpenInFull, "Expand", tint = GrpTextPrimary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onLeave, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.CallEnd, "Leave", tint = GrpDanger, modifier = Modifier.size(18.dp))
        }
    }
}

private fun formatGroupDuration(sec: Long): String {
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    val s = sec % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
