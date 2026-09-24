package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessTeal

/**
 * Batch file receive progress screen for HyperDrop.
 * Shows ALL files in a list with sequential progress and unified accept/reject.
 * UFO stays until entire batch is complete, then flies away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchReceiveProgressScreen(
    files: List<FileShareViewModel.BatchFileInfo>,
    currentFileIndex: Int,
    isBatchComplete: Boolean,
    isFailed: Boolean,
    senderName: String = "Unknown Device",
    showAcceptReject: Boolean = false,
    errorMessage: String? = null,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    // Calculate overall batch progress for UFO
    val batchProgress = remember(files) {
        if (files.isEmpty()) 0f
        else {
            val completedCount = files.count { it.status == FileShareViewModel.BatchFileStatus.COMPLETED }
            val currentProgress = files.getOrNull(currentFileIndex)
                ?.let { if (it.status == FileShareViewModel.BatchFileStatus.TRANSFERRING) it.progress / 100f else 0f } ?: 0f
            (completedCount + currentProgress) / files.size
        }
    }

    // 5a: Animate the batch progress bar
    val animatedBatchProgress by animateFloatAsState(
        targetValue = batchProgress,
        animationSpec = tween(HyperDropMotion.MEDIUM, easing = HyperDropMotion.StandardEasing),
        label = "batchProgress"
    )
    
    val totalSize = files.sumOf { it.fileSize }
    val totalTransferred = files.sumOf { it.bytesTransferred }
    val isReceiving = files.any { it.status == FileShareViewModel.BatchFileStatus.TRANSFERRING }

    // Haptic feedback on batch completion
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(isBatchComplete) {
        if (isBatchComplete) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when {
                                isBatchComplete -> "Transfer Complete"
                                isFailed -> "Transfer Failed"
                                showAcceptReject -> "Incoming Files"
                                isReceiving -> "Receiving Files"
                                else -> "Waiting..."
                            },
                            color = HyperDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (files.isNotEmpty()) {
                            Text(
                                text = HyperDropUtils.formatBatchSummary(files.size, totalSize),
                                style = HyperDropTypography.Caption
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (isBatchComplete || isFailed) onDone else onCancel) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = HyperDropSpacing.lg)
            ) {
                // UFO Animation - stays until batch complete
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    if (isBatchComplete) {
                        // Confetti on completion
                        ConfettiBurst(
                            isActive = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        SuccessCheckmark(
                            isVisible = true,
                            modifier = Modifier.size(100.dp)
                        )
                    } else {
                        // Enhanced UFO with batch progress
                        UfoTransferAnimation(
                            isSending = false,
                            progress = batchProgress * 100f,
                            isComplete = isBatchComplete,
                            isFailed = isFailed,
                            isWaiting = showAcceptReject,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // ============ ACCEPT/REJECT FOR ENTIRE BATCH ============
                if (showAcceptReject && files.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HyperDropSpacing.lg),
                        shape = RoundedCornerShape(HyperDropSpacing.xl),
                        colors = CardDefaults.cardColors(
                            containerColor = HyperDropColors.CardSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(HyperDropSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.md)
                        ) {
                            // Sender info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = HyperDropColors.PrimaryCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(HyperDropSpacing.sm))
                                Text(
                                    text = senderName,
                                    style = HyperDropTypography.TitleMedium
                                )
                            }
                            
                            Text(
                                text = "wants to send you ${files.size} files",
                                style = HyperDropTypography.BodyMedium
                            )
                            
                            Spacer(modifier = Modifier.height(HyperDropSpacing.sm))
                            
                            // Accept/Reject buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(HyperDropSpacing.md)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PremiumButton(
                                        text = "Decline",
                                        onClick = onReject,
                                        icon = Icons.Filled.Close,
                                        color = HyperDropColors.ErrorRed
                                    )
                                }
                                
                                Box(modifier = Modifier.weight(1f)) {
                                    PremiumButton(
                                        text = "Accept All",
                                        onClick = onAccept,
                                        icon = Icons.Filled.Check,
                                        color = HyperDropColors.SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
                
                // ============ FILE LIST WITH PROGRESS ============
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(HyperDropSpacing.xl),
                    colors = CardDefaults.cardColors(
                        containerColor = HyperDropColors.CardSurface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(HyperDropSpacing.md)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = HyperDropSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Files",
                                style = HyperDropTypography.TitleMedium
                            )
                            Text(
                                text = "${files.count { it.status == FileShareViewModel.BatchFileStatus.COMPLETED }}/${files.size} complete",
                                style = HyperDropTypography.Caption.copy(
                                    color = HyperDropColors.PrimaryCyan
                                )
                            )
                        }
                        
                        HorizontalDivider(color = HyperDropColors.BorderSubtle)
                        
                        // File list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.sm),
                            contentPadding = PaddingValues(vertical = HyperDropSpacing.sm)
                        ) {
                            itemsIndexed(files) { index, file ->
                                BatchFileItemRow(
                                    file = file,
                                    isCurrentFile = index == currentFileIndex && 
                                            file.status == FileShareViewModel.BatchFileStatus.TRANSFERRING,
                                    index = index
                                )
                            }
                        }
                    }
                }
                
                // ============ OVERALL PROGRESS BAR ============
                if (isReceiving || isBatchComplete) {
                    Spacer(modifier = Modifier.height(HyperDropSpacing.lg))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HyperDropColors.CardSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(HyperDropSpacing.lg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Overall Progress",
                                    style = HyperDropTypography.Caption
                                )
                                Text(
                                    text = "${(animatedBatchProgress * 100).toInt()}%",
                                    style = HyperDropTypography.Caption.copy(
                                        color = HyperDropColors.PrimaryCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(HyperDropSpacing.sm))
                            
                            // Progress bar — uses animatedBatchProgress
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = animatedBatchProgress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    HyperDropColors.PrimaryCyan,
                                                    HyperDropColors.SecondaryTeal
                                                )
                                            )
                                        )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(HyperDropSpacing.xs))
                            
                            Text(
                                text = "${HyperDropUtils.formatFileSize(totalTransferred)} / ${HyperDropUtils.formatFileSize(totalSize)}",
                                style = HyperDropTypography.Caption
                            )
                        }
                    }
                }
                
                // ============ ACTION BUTTONS ============
                Spacer(modifier = Modifier.height(HyperDropSpacing.lg))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = HyperDropSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.md)
                ) {
                    if (!isBatchComplete && !showAcceptReject) {
                        PremiumButton(
                            text = "Cancel",
                            onClick = onCancel,
                            color = HyperDropColors.ErrorRed
                        )
                    }
                    
                    if (isBatchComplete) {
                        PremiumButton(
                            text = "Done",
                            onClick = onDone,
                            icon = Icons.Filled.Check,
                            color = HyperDropColors.SuccessGreen
                        )
                    }
                    
                    if (isFailed) {
                        PremiumButton(
                            text = "Close",
                            onClick = onDone,
                            color = HyperDropColors.PrimaryCyan
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual file row in the batch list with stagger animation
 */
@Composable
private fun BatchFileItemRow(
    file: FileShareViewModel.BatchFileInfo,
    isCurrentFile: Boolean,
    index: Int = 0
) {
    val statusColor = when (file.status) {
        FileShareViewModel.BatchFileStatus.COMPLETED -> HyperDropColors.SuccessGreen
        FileShareViewModel.BatchFileStatus.TRANSFERRING -> HyperDropColors.PrimaryCyan
        FileShareViewModel.BatchFileStatus.FAILED -> HyperDropColors.ErrorRed
        FileShareViewModel.BatchFileStatus.PENDING -> HyperDropColors.TextDisabled
    }
    
    val statusIcon = when (file.status) {
        FileShareViewModel.BatchFileStatus.COMPLETED -> Icons.Filled.CheckCircle
        FileShareViewModel.BatchFileStatus.TRANSFERRING -> Icons.Filled.Downloading
        FileShareViewModel.BatchFileStatus.FAILED -> Icons.Filled.Error
        FileShareViewModel.BatchFileStatus.PENDING -> Icons.Filled.Schedule
    }

    // 5b: Stagger animation for file list items
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = HyperDropMotion.MEDIUM,
            delayMillis = index * 50, // Stagger by 50ms per item
            easing = HyperDropMotion.StandardEasing
        ),
        label = "item_scale"
    )
    
    // Pulse animation for current file
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrentFile) HyperDropColors.PrimaryCyan.copy(alpha = 0.1f * pulseAlpha)
                else Color.Transparent
            )
            .padding(HyperDropSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 5c: File type icon instead of generic icon
        HyperDropIconCircle(
            icon = HyperDropUtils.getFileTypeIcon(file.fileName),
            size = 40.dp,
            iconSize = 22.dp,
            tint = statusColor,
            backgroundColor = statusColor.copy(alpha = 0.15f)
        )
        
        Spacer(modifier = Modifier.width(HyperDropSpacing.md))
        
        // File info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = HyperDropUtils.formatFileName(file.fileName),
                color = HyperDropColors.TextPrimary,
                fontWeight = if (isCurrentFile) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = HyperDropUtils.formatFileSize(file.fileSize),
                    style = HyperDropTypography.Caption
                )
                
                if (file.status == FileShareViewModel.BatchFileStatus.TRANSFERRING) {
                    Text(
                        text = " • ${file.progress}%",
                        style = HyperDropTypography.Caption.copy(
                            color = HyperDropColors.PrimaryCyan,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            
            // Mini progress bar for current file
            if (file.status == FileShareViewModel.BatchFileStatus.TRANSFERRING) {
                Spacer(modifier = Modifier.height(HyperDropSpacing.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = file.progress / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(HyperDropColors.PrimaryCyan)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(HyperDropSpacing.sm))
        
        // Status icon
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(24.dp)
        )
    }
}


