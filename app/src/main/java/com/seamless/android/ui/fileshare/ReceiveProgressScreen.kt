@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen receiving progress screen for HyperDrop file transfers.
 * Shows premium UI with animated progress, file info, and accept/reject options.
 * 
 * Inspired by AirDrop and Quick Share transfer displays with speed and ETA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveProgressScreen(
    fileName: String?,
    fileSize: Long,
    progress: Int,
    bytesTransferred: Long,
    isWaitingForTransfer: Boolean,
    isReceiving: Boolean,
    isComplete: Boolean,
    isFailed: Boolean,
    errorMessage: String? = null,
    showAcceptReject: Boolean = false,
    speedBytesPerSec: Long = 0,           // Transfer speed (AirDrop/Quick Share inspired)
    estimatedSecondsRemaining: Int = -1,   // ETA in seconds
    fileIndex: Int = 1,                    // Current file in batch
    totalFiles: Int = 1,                   // Total files in batch
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onOpenFile: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "receiveProgress")

    // Pulse animation for waiting state
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
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
                        text = when {
                            isComplete -> "Transfer Complete"
                            isFailed -> "Transfer Failed"
                            isReceiving -> "Receiving File"
                            else -> "Waiting for Sender"
                        },
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
                // Animated UFO Tractor Beam (Receiving Mode)
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
                            isSending = false, // Beam Down
                            progress = progress.toFloat(),
                            isComplete = isComplete,
                            isFailed = isFailed,
                            isWaiting = isWaitingForTransfer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Circular progress section (when receiving or complete)
                if (isReceiving || isComplete) {
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
                }

                // Status message for waiting state
                if (isWaitingForTransfer && !showAcceptReject && !isComplete && !isFailed) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(HyperDropSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Waiting for sender to connect...",
                                style = HyperDropTypography.TitleMedium.copy(
                                    color = HyperDropColors.PrimaryCyan
                                )
                            )
                            Spacer(modifier = Modifier.height(HyperDropSpacing.sm))
                            Text(
                                text = "Make sure the sender has selected this device",
                                style = HyperDropTypography.BodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // File info card (when receiving or complete)
                if (fileName != null && (isReceiving || isComplete || isFailed)) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(HyperDropSpacing.xl),
                            verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.md)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    if (isReceiving && !isComplete && !isFailed && speedBytesPerSec > 0) {
                                        val speedText = HyperDropUtils.formatSpeed(speedBytesPerSec)
                                        val etaText = HyperDropUtils.formatEta(estimatedSecondsRemaining)
                                        Text(
                                            text = if (etaText.isNotEmpty()) "$speedText • $etaText" else speedText,
                                            style = HyperDropTypography.Caption.copy(
                                                color = HyperDropColors.AccentBlue.copy(alpha = 0.8f)
                                            )
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = when {
                                            isComplete -> "Complete"
                                            isFailed -> "Failed"
                                            else -> "Receiving..."
                                        },
                                        color = when {
                                            isComplete -> HyperDropColors.SuccessGreen
                                            isFailed -> HyperDropColors.ErrorRed
                                            else -> HyperDropColors.AccentBlue
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
                    }
                }
                
                // Accept/Reject dialog for incoming requests
                if (showAcceptReject && fileName != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HyperDropColors.CardSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(HyperDropSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(HyperDropSpacing.lg)
                        ) {
                            Text(
                                text = "Incoming File Request",
                                style = HyperDropTypography.TitleLarge.copy(
                                    color = HyperDropColors.PrimaryCyan
                                )
                            )
                            Text(
                                text = "Do you want to receive this file?",
                                style = HyperDropTypography.BodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(HyperDropSpacing.lg)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PremiumButton(
                                        text = "Decline",
                                        onClick = onReject,
                                        color = HyperDropColors.ErrorRed
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    PremiumButton(
                                        text = "Accept",
                                        onClick = onAccept,
                                        color = HyperDropColors.SuccessGreen
                                    )
                                }
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
                    if (!isComplete && !showAcceptReject) {
                        PremiumButton(
                            text = if (isWaitingForTransfer) "Stop Waiting" else "Cancel",
                            onClick = onCancel,
                            color = HyperDropColors.ErrorRed
                        )
                    }
                    
                    if (isComplete) {
                        if (onOpenFile != null) {
                            PremiumButton(
                                text = "Open File",
                                onClick = onOpenFile,
                                icon = Icons.Filled.FolderOpen,
                                color = HyperDropColors.PrimaryCyan
                            )
                        }
                        PremiumButton(
                            text = "Done",
                            onClick = onDone,
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


