package com.seamless.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import kotlinx.coroutines.delay

/**
 * Connection status types
 */
enum class ConnectionStatus {
    ONLINE,           // Connected to internet (Nostr) + Bluetooth mesh
    MESH_ONLY,        // Bluetooth mesh only, no internet
    TOR_ACTIVE,       // Tor network is connected (global P2P reach)
    OFFLINE,          // No connections
    CONNECTING        // Trying to connect
}

/**
 * Data class for connection state
 */
data class ConnectionState(
    val status: ConnectionStatus,
    val meshPeerCount: Int = 0,
    val isNostrConnected: Boolean = false,
    val isTorEnabled: Boolean = false,
    val torBootstrapPercent: Int = 0
)

/**
 * Connection status bar that shows current connection state
 * Appears at the top of the chat when not fully online
 */
@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, statusText) = when (connectionState.status) {
        ConnectionStatus.ONLINE -> Triple(
            SeamlessCyan.copy(alpha = 0.1f),
            SeamlessCyan,
            "Online • ${connectionState.meshPeerCount} nearby"
        )
        ConnectionStatus.TOR_ACTIVE -> Triple(
            Color(0xFF7C4DFF).copy(alpha = 0.1f),
            Color(0xFF7C4DFF), // Purple for Tor
            if (connectionState.torBootstrapPercent < 100) "Tor Bootstrapping (${connectionState.torBootstrapPercent}%)" else "Tor Active • Global P2P"
        )
        ConnectionStatus.MESH_ONLY -> Triple(
            Color(0xFFFFA726).copy(alpha = 0.1f),
            Color(0xFFFFA726),
            "Mesh only • ${connectionState.meshPeerCount} nearby"
        )
        ConnectionStatus.OFFLINE -> Triple(
            Color(0xFFE53935).copy(alpha = 0.1f),
            Color(0xFFE53935),
            "Offline • Connecting..."
        )
        ConnectionStatus.CONNECTING -> Triple(
            Color(0xFF9E9E9E).copy(alpha = 0.1f),
            Color(0xFF9E9E9E),
            "Connecting..."
        )
    }
    
    // Only show if not fully online with peers
    AnimatedVisibility(
        visible = connectionState.status != ConnectionStatus.ONLINE || connectionState.meshPeerCount == 0,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = backgroundColor,
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Animated status dot
                ConnectionStatusDot(status = connectionState.status)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = statusText,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Animated status indicator dot
 */
@Composable
private fun ConnectionStatusDot(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.TOR_ACTIVE -> Color(0xFF7C4DFF)
        ConnectionStatus.ONLINE -> SeamlessCyan
        ConnectionStatus.MESH_ONLY -> Color(0xFFFFA726)
        ConnectionStatus.OFFLINE -> Color(0xFFE53935)
        ConnectionStatus.CONNECTING -> Color(0xFF9E9E9E)
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Pulsing animation for connecting/offline states
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ConnectionStatus.CONNECTING || status == ConnectionStatus.OFFLINE) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * Compact connection indicator for header
 */
@Composable
fun ConnectionIndicator(
    isOnline: Boolean,
    meshPeerCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isOnline) SeamlessCyan else Color(0xFFFFA726))
        )
        
        if (meshPeerCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$meshPeerCount",
                fontSize = 11.sp,
                color = if (isOnline) SeamlessCyan else Color(0xFFFFA726),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
