package com.seamless.android.ui.fileshare

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * NFC Tap-to-Share UI Component
 * 
 * Displays AirDrop-style ripple animation that encourages users to tap phones together.
 * Based on iOS 17 AirDrop and Google Quick Share UI patterns.
 */
@Composable
fun NfcTapToShareOverlay(
    isActive: Boolean,
    onDismiss: () -> Unit,
    transferState: NfcTransferState = NfcTransferState.WAITING
) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "nfc_ripple")
    
    // Multiple ripple animations at different phases for wave effect
    val ripple1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    
    val ripple2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )
    
    val ripple3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, delayMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple3"
    )
    
    // Phone icon pulse
    val phonePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phonePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        // Ripple waves (AirDrop-style)
        Canvas(modifier = Modifier.size(300.dp)) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val maxRadius = size.width / 2

            // Draw multiple ripples
            listOf(ripple1, ripple2, ripple3).forEach { rippleProgress ->
                val radius = maxRadius * rippleProgress
                val alpha = (1f - rippleProgress) * 0.6f
                
                drawCircle(
                    color = HyperDropColors.PrimaryCyan.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            
            // Inner glow circle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HyperDropColors.PrimaryCyan.copy(alpha = 0.3f),
                        HyperDropColors.PrimaryCyan.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.4f
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius * 0.4f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Animated phone icons showing tap gesture
            Box(
                modifier = Modifier.scale(phonePulse),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    HyperDropColors.PrimaryCyan.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                
                // NFC Icon
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = "NFC",
                    tint = HyperDropColors.PrimaryCyan,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Status text based on transfer state with crossfade transition
            Crossfade(
                targetState = transferState,
                animationSpec = tween(HyperDropMotion.FAST),
                label = "nfc_state_crossfade"
            ) { state ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state) {
                        NfcTransferState.WAITING -> {
                            Text(
                                text = "Tap phones together",
                                style = HyperDropTypography.DisplayMedium,
                                color = HyperDropColors.TextPrimary
                            )
                            Text(
                                text = "Hold your phone close to the other device",
                                style = HyperDropTypography.BodyMedium,
                                color = HyperDropColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                        NfcTransferState.CONNECTING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = HyperDropColors.PrimaryCyan,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Connecting...",
                                style = HyperDropTypography.DisplayMedium,
                                color = HyperDropColors.PrimaryCyan
                            )
                        }
                        NfcTransferState.TRANSFERRING -> {
                            Icon(
                                imageVector = Icons.Filled.CellTower,
                                contentDescription = "Transferring",
                                tint = HyperDropColors.PrimaryCyan,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Transferring...",
                                style = HyperDropTypography.DisplayMedium,
                                color = HyperDropColors.PrimaryCyan
                            )
                            Text(
                                text = "Keep devices close until complete",
                                style = HyperDropTypography.BodyMedium,
                                color = HyperDropColors.TextSecondary
                            )
                        }
                        NfcTransferState.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = HyperDropColors.SuccessGreen,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Transfer Complete!",
                                style = HyperDropTypography.DisplayMedium,
                                color = HyperDropColors.SuccessGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cancel button
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = HyperDropColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        }
    }
}

enum class NfcTransferState {
    WAITING,
    CONNECTING,
    TRANSFERRING,
    SUCCESS
}

/**
 * Quick Share style Send/Receive tab selector
 * Full-screen layout with distinct Send and Receive sections
 */
@Composable
fun QuickShareStyleTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Send", "Receive")
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = MaterialTheme.shapes.large,
        color = HyperDropColors.CardSurface.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                
                Surface(
                    modifier = Modifier
                        .weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) HyperDropColors.PrimaryCyan else Color.Transparent,
                    onClick = { onTabSelected(index) }
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (isSelected) Color.Black else HyperDropColors.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Device card in grid layout (Quick Share style)
 */
@Composable
fun DeviceGridCard(
    deviceName: String,
    deviceType: DeviceType,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(200),
        label = "cardScale"
    )

    Surface(
        modifier = Modifier
            .size(120.dp)
            .scale(scale),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) HyperDropColors.PrimaryCyan.copy(alpha = 0.2f) else HyperDropColors.CardSurface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, HyperDropColors.PrimaryCyan) else null,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Device icon with glow
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = HyperDropColors.PrimaryCyan.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (deviceType) {
                        DeviceType.PHONE -> Icons.Filled.PhoneAndroid
                        DeviceType.TABLET -> Icons.Filled.Tablet
                        DeviceType.COMPUTER -> Icons.Filled.Computer
                    },
                    contentDescription = deviceName,
                    tint = HyperDropColors.PrimaryCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = deviceName,
                style = HyperDropTypography.BodySmall.copy(color = HyperDropColors.TextPrimary),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

enum class DeviceType {
    PHONE, TABLET, COMPUTER
}

/**
 * Material Design 3 style circular progress indicator for file transfer
 * Based on Quick Share's updated progress animation
 */
@Composable
fun MaterialTransferProgress(
    progress: Float, // 0f to 1f
    fileName: String,
    transferredBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "transferProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HyperDropColors.CardSurface,
                        HyperDropColors.SpaceNavy
                    )
                ),
                shape = MaterialTheme.shapes.large
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular progress (Material 3 style)
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(120.dp),
                strokeWidth = 8.dp,
                color = HyperDropColors.PrimaryCyan,
                trackColor = HyperDropColors.CardSurfaceLight,
                strokeCap = StrokeCap.Round
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = HyperDropTypography.Percentage
                )
                Text(
                    text = HyperDropUtils.formatFileSize(transferredBytes),
                    style = HyperDropTypography.BodySmall.copy(
                        color = HyperDropColors.TextTertiary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // File name
        Text(
            text = fileName,
            style = HyperDropTypography.TitleMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "${HyperDropUtils.formatFileSize(transferredBytes)} / ${HyperDropUtils.formatFileSize(totalBytes)}",
            style = HyperDropTypography.BodyMedium.copy(
                color = HyperDropColors.TextTertiary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Cancel button
        TextButton(onClick = onCancel) {
            Text(
                text = "Cancel",
                color = HyperDropColors.ErrorRed,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
