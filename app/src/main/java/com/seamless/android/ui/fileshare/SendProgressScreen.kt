package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.R
import com.seamless.android.ui.theme.SeamlessCyan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen sending progress screen for HyperDrop file transfers.
 * Shows premium UI with animated progress, file info, and cancel option.
 * 
 * Inspired by AirDrop and Quick Share transfer displays with speed and ETA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendProgressScreen(
    fileName: String,
    fileSize: Long,
    progress: Int,
    bytesTransferred: Long,
    isComplete: Boolean,
    isFailed: Boolean,
    errorMessage: String? = null,
    speedBytesPerSec: Long = 0,           // Transfer speed (AirDrop/Quick Share inspired)
    estimatedSecondsRemaining: Int = -1,   // ETA in seconds
    fileIndex: Int = 1,                    // Current file in batch
    totalFiles: Int = 1,                   // Total files in batch
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sendProgress")

    // Pulse animation for the icon
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Smooth animated progress for the FluidProgressRing
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "progressAnimation"
    )

    // Haptic feedback on completion
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(isComplete) {
        if (isComplete) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isComplete) "Transfer Complete" else if (isFailed) "Transfer Failed" else "Sending File",
                        color = HyperDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (isComplete || isFailed) onDone else onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = HyperDropColors.PrimaryCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        HyperDropBackground {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.lg),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(HyperDropSpacing.lg)
                    .verticalScroll(rememberScrollState())
            ) {
                // Animated UFO Tractor Beam
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp) // Responsive area for the beam
                ) {
                    // Confetti burst on completion
                    if (isComplete) {
                        ConfettiBurst(
                            isActive = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    if (isComplete) {
                        // Success checkmark
                        SuccessCheckmark(
                            isVisible = true,
                            modifier = Modifier.size(120.dp)
                        )
                    } else {
                        UfoTransferAnimation(
                            isSending = true,
                            progress = progress.toFloat(),
                            isComplete = isComplete,
                            isFailed = isFailed,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Circular progress section
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = HyperDropSpacing.lg)
                ) {
                    FluidProgressRing(
                        progress = animatedProgress,
                        modifier = Modifier.size(140.dp),
                        color = SeamlessCyan
                    )
                    // Percentage inside the ring
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = HyperDropTypography.Percentage
                        )
                        Text(
                            text = HyperDropUtils.formatFileSize(bytesTransferred),
                            style = HyperDropTypography.Caption
                        )
                    }
                }

                // File info card
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Shimmer overlay during active transfer
                    if (!isComplete && !isFailed) {
                        ShimmerOverlay(
                            isActive = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // File icon and name row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HyperDropIconCircle(
                            icon = HyperDropUtils.getFileTypeIcon(fileName),
                            tint = HyperDropUtils.getFileTypeColor(fileName),
                            backgroundColor = HyperDropUtils.getFileTypeColor(fileName).copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.width(HyperDropSpacing.lg))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = HyperDropUtils.formatFileName(fileName),
                                style = HyperDropTypography.TitleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = HyperDropUtils.formatFileSize(fileSize),
                                style = HyperDropTypography.BodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(HyperDropSpacing.md))
                    
                    // Transfer stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${HyperDropUtils.formatFileSize(bytesTransferred)} / ${HyperDropUtils.formatFileSize(fileSize)}",
                                style = HyperDropTypography.BodySmall
                            )
                            // Speed and ETA display (AirDrop/Quick Share inspired)
                            if (!isComplete && !isFailed && speedBytesPerSec > 0) {
                                val speedText = HyperDropUtils.formatSpeed(speedBytesPerSec)
                                val etaText = HyperDropUtils.formatEta(estimatedSecondsRemaining)
                                Text(
                                    text = if (etaText.isNotEmpty()) "$speedText • $etaText" else speedText,
                                    style = HyperDropTypography.Caption.copy(
                                        color = HyperDropColors.PrimaryCyan.copy(alpha = 0.8f)
                                    )
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = when {
                                    isComplete -> "Complete"
                                    isFailed -> "Failed"
                                    else -> "Sending..."
                                },
                                color = when {
                                    isComplete -> HyperDropColors.SuccessGreen
                                    isFailed -> HyperDropColors.ErrorRed
                                    else -> HyperDropColors.PrimaryCyan
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Show file index for batch transfers
                            if (totalFiles > 1) {
                                Text(
                                    text = "File $fileIndex of $totalFiles",
                                    style = HyperDropTypography.Caption
                                )
                            }
                        }
                    }
                }
                
                // Error message (if failed)
                if (isFailed && errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HyperDropColors.ErrorRed.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            color = HyperDropColors.ErrorRed,
                            modifier = Modifier.padding(HyperDropSpacing.lg),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(HyperDropSpacing.md))
                
                // Action buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.md)
                ) {
                    if (!isComplete) {
                        PremiumButton(
                            text = "Cancel",
                            onClick = onCancel,
                            color = HyperDropColors.ErrorRed
                        )
                    }
                    
                    if (isFailed && onRetry != null) {
                        PremiumButton(
                            text = "Retry",
                            onClick = onRetry,
                            icon = Icons.Filled.Refresh,
                            color = HyperDropColors.PrimaryCyan
                        )
                    }
                    
                    if (isComplete || isFailed) {
                        PremiumButton(
                            text = if (isComplete) "Done" else "Close",
                            onClick = onDone,
                            color = if (isComplete) HyperDropColors.SuccessGreen else HyperDropColors.PrimaryCyan
                        )
                    }
                }
            }
        }
    }
}


