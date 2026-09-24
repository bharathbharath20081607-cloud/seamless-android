package com.seamless.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.seamless.android.net.TorManager
import com.seamless.android.net.TorMode
import com.seamless.android.net.TorPreferenceManager
import com.seamless.android.net.TorHiddenServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * P2P Network Hub - a unified "at-a-glance" status panel for all transport layers.
 * Shown as a dropdown from the main ChatScreen top bar.
 * 
 * Displays:
 * - Mesh peer count & status
 * - Tor bootstrap progress & circuit status
 * - Bridge request button when Tor stalls (censorship UX)
 * - Nostr relay connectivity
 */
@Composable
fun NetworkHubDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    meshPeerCount: Int,
    isNostrConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val torStatus by TorManager.statusFlow.collectAsState()
    val torMode by TorPreferenceManager.modeFlow.collectAsState()
    val hiddenServiceManager = remember(context) { TorHiddenServiceManager.getInstance(context) }
    val hiddenServiceStatus by hiddenServiceManager.statusFlow.collectAsState()
    val torReachable = hiddenServiceStatus.state == TorHiddenServiceManager.HiddenServiceState.RUNNING &&
        hiddenServiceStatus.onionAddress.isNotBlank()
    
    // Detect if Tor is stalled (bootstrapping for too long)
    var torStallDetected by remember { mutableStateOf(false) }
    LaunchedEffect(torStatus.state, torStatus.bootstrapPercent) {
        if (torStatus.state == TorManager.TorState.BOOTSTRAPPING || 
            torStatus.state == TorManager.TorState.STARTING) {
            kotlinx.coroutines.delay(20_000L) // 20s threshold
            if (torStatus.state == TorManager.TorState.BOOTSTRAPPING || 
                torStatus.state == TorManager.TorState.STARTING) {
                torStallDetected = true
            }
        } else {
            torStallDetected = false
        }
    }
    
    AnimatedVisibility(
        visible = expanded,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(
            animationSpec = tween(250, easing = EaseOutCubic)
        ),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(
            animationSpec = tween(200, easing = EaseInCubic)
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Network Hub",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                
                // === Mesh Transport ===
                TransportRow(
                    emoji = "📡",
                    label = "Bluetooth Mesh",
                    statusText = if (meshPeerCount > 0) "$meshPeerCount nearby" else "Scanning...",
                    isActive = meshPeerCount > 0,
                    color = Color(0xFF00C851)
                )
                
                // === Tor Transport ===
                TransportRow(
                    emoji = "🧅",
                    label = "Tor Network",
                    statusText = when {
                        torReachable -> "Onion service ready"
                        else -> when (torStatus.state) {
                        TorManager.TorState.RUNNING -> "Connected"
                        TorManager.TorState.BOOTSTRAPPING -> "Bootstrapping ${torStatus.bootstrapPercent}%"
                        TorManager.TorState.STARTING -> "Starting..."
                        TorManager.TorState.STOPPING -> "Stopping..."
                        TorManager.TorState.ERROR -> "Error"
                        TorManager.TorState.OFF -> if (torMode == TorMode.ON) "Waiting..." else "Disabled"
                        }
                    },
                    isActive = torReachable || torStatus.state == TorManager.TorState.RUNNING,
                    color = Color(0xFF7C4DFF),
                    isLoading = !torReachable && (
                        torStatus.state == TorManager.TorState.BOOTSTRAPPING ||
                            torStatus.state == TorManager.TorState.STARTING
                        ),
                    progress = if (torStatus.state == TorManager.TorState.BOOTSTRAPPING) 
                        torStatus.bootstrapPercent / 100f else null
                )
                
                // === Censorship Detection: Bridge Request ===
                if (torStallDetected && torMode == TorMode.ON) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFA726).copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Trigger pluggable transport bridge request
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val ptManager = com.seamless.android.net.PluggableTransportManager.getInstance(context)
                                            ptManager.autoDetectAndApply()
                                            
                                            // Tell TorManager to restart with new bridges
                                            TorManager.restartToApplyBridges(context)
                                        } catch (e: Exception) { 
                                            Log.e("NetworkHub", "Failed to get bridges", e)
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Tor connection stalled",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFA726)
                                )
                                Text(
                                    "Tap to request Tor bridges (obfs4)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // === Nostr Relay ===
                TransportRow(
                    emoji = "🌐",
                    label = "Nostr Relay",
                    statusText = if (isNostrConnected) "Connected" else "Disconnected",
                    isActive = isNostrConnected,
                    color = Color(0xFF29B6F6)
                )
                
                // === Enable Tor toggle if currently disabled ===
                if (torMode != TorMode.ON) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                TorPreferenceManager.set(context, TorMode.ON)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Enable Tor for global P2P reach",
                            fontSize = 13.sp,
                            color = Color(0xFF7C4DFF),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single transport row in the Network Hub
 */
@Composable
private fun TransportRow(
    emoji: String,
    label: String,
    statusText: String,
    isActive: Boolean,
    color: Color,
    isLoading: Boolean = false,
    progress: Float? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji icon
        Text(emoji, fontSize = 18.sp)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .width(60.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = color,
                            trackColor = color.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            color = color,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                Text(
                    statusText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) color else Color.Gray.copy(alpha = 0.4f))
        )
    }
}
