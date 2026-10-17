@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Bolt
import com.seamless.android.R
import com.seamless.android.service.HyperDropIdentity
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sin

/**
 * HyperDrop - Premium file sharing main screen
 * Provides a visually stunning interface for send/receive file options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiveScreen(
    onShareThingsClick: () -> Unit,
    onReceiveThingsClick: () -> Unit,
    onBackClick: () -> Unit
) {


        // Global Animated Background
        HyperDropBackground {
            // Content
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

            // Top bar
            HyperDropTopBar(onBackClick = onBackClick)
            
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo/Icon section
                HyperDropLogo()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tagline
                Text(
                    text = stringResource(R.string.share_receive_subtitle),
                    style = HyperDropTypography.BodyLarge,
                    color = HyperDropColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(48.dp))

                // Send Button
                HyperDropActionCard(
                    title = stringResource(R.string.share_things),
                    subtitle = stringResource(R.string.share_things_desc),
                    icon = Icons.Filled.Share,
                    primaryColor = HyperDropColors.TextPrimary,
                    secondaryColor = HyperDropColors.TextSecondary,
                    onClick = onShareThingsClick
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Receive Button
                HyperDropActionCard(
                    title = stringResource(R.string.receive_things),
                    subtitle = stringResource(R.string.receive_things_desc),
                    icon = Icons.Filled.Download,
                    primaryColor = HyperDropColors.TextPrimary,
                    secondaryColor = HyperDropColors.TextSecondary,
                    onClick = onReceiveThingsClick
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Speed indicator
                SpeedIndicator()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Device Identity Card (AirDrop/Quick Share inspired)
                DeviceIdentityChip()
            }
        }
    }
}

@Composable
private fun HyperDropTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            @Suppress("DEPRECATION")
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = HyperDropColors.TextPrimary
            )
        }
    }
}

@Composable
private fun HyperDropLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_animation")

    val haloBreath by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_breath"
    )

    val bezelRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bezel_rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(128.dp)
    ) {
        // Precision machined watch-bezel medallion with 36 micro-ticks
        Canvas(
            modifier = Modifier.size(128.dp)
        ) {
            val center = this.center
            val outerRadius = size.minDimension / 2f

            // Breathing radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = haloBreath),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius
            )

            // Outer specular hairline ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.35f)
                    ),
                    center = center
                ),
                radius = outerRadius - 2.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            // 36 precision micro-ticks around inner bezel
            val tickOuterR = outerRadius - 6.dp.toPx()
            for (i in 0 until 36) {
                val angleDeg = i * 10f + bezelRotation * 0.1f
                val rad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 3 == 0
                val tickLen = if (isMajor) 6.dp.toPx() else 3.dp.toPx()
                val tickAlpha = if (isMajor) 0.32f else 0.14f
                val startX = center.x + (tickOuterR - tickLen) * kotlin.math.cos(rad).toFloat()
                val startY = center.y + (tickOuterR - tickLen) * kotlin.math.sin(rad).toFloat()
                val endX = center.x + tickOuterR * kotlin.math.cos(rad).toFloat()
                val endY = center.y + tickOuterR * kotlin.math.sin(rad).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = tickAlpha),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 1.2.dp.toPx() else 0.8.dp.toPx()
                )
            }
        }

        // Static machined obsidian center medallion
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A22),
                            Color(0xFF0E0E13)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun HyperDropActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        showShimmerBorder = false
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HyperDropIconCircle(
                icon = icon,
                tint = primaryColor,
                backgroundColor = Color(0xFF1A1A22),
                size = 50.dp,
                iconSize = 24.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = HyperDropTypography.TitleLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = HyperDropTypography.BodyMedium.copy(
                        color = HyperDropColors.TextTertiary
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = primaryColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "speed_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF101015).copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Bolt,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Wi-Fi Direct High-Speed Pipe",
            style = HyperDropTypography.Caption.copy(
                color = HyperDropColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = Modifier.width(10.dp))

        // 5-bar sinusoidal wave visualizer
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(14.dp)
        ) {
            for (i in 0 until 5) {
                val wave = ((sin(phase + i * 0.85f) + 1f) / 2f).coerceIn(0.25f, 1f)
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight(wave)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.45f + 0.45f * wave))
                )
            }
        }
    }
}



/**
 * Device Identity Chip - Shows the user's quirky alias and visibility status
 * Inspired by AirDrop and Quick Share's device visibility display
 * Now interactive: tap to cycle through visibility modes
 */
@Composable
private fun DeviceIdentityChip() {
    val context = LocalContext.current
    val deviceAlias = remember { HyperDropIdentity.getAlias(context) }
    var visibilityMode by remember { mutableStateOf(HyperDropIdentity.getVisibilityMode(context)) }

    // Scale animation on tap
    var tapped by remember { mutableStateOf(false) }
    val chipScale by animateFloatAsState(
        targetValue = if (tapped) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 400f
        ),
        label = "chip_scale",
        finishedListener = { tapped = false }
    )

    // TODO: localize visibility text strings
    val visibilityText = when (visibilityMode) {
        HyperDropIdentity.VisibilityMode.HIDDEN -> "Hidden"
        HyperDropIdentity.VisibilityMode.CONTACTS_ONLY -> "Contacts Only"
        HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN -> "Everyone (10 min)"
        HyperDropIdentity.VisibilityMode.EVERYONE -> "Everyone"
    }
    
    val visibilityColor = when (visibilityMode) {
        HyperDropIdentity.VisibilityMode.HIDDEN -> HyperDropColors.VisibilityHidden
        HyperDropIdentity.VisibilityMode.CONTACTS_ONLY -> HyperDropColors.VisibilityContacts
        HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN -> HyperDropColors.VisibilityEveryone
        HyperDropIdentity.VisibilityMode.EVERYONE -> HyperDropColors.VisibilityEveryone
    }

    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = Modifier
            .scale(chipScale)
            .clip(RoundedCornerShape(24.dp))
            .background(HyperDropColors.CardSurface.copy(alpha = 0.85f))
            .border(1.dp, HyperDropColors.BorderSubtle, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tapped = true
                // Cycle: HIDDEN → CONTACTS_ONLY → EVERYONE_10_MIN → EVERYONE → HIDDEN
                val nextMode = when (visibilityMode) {
                    HyperDropIdentity.VisibilityMode.HIDDEN -> HyperDropIdentity.VisibilityMode.CONTACTS_ONLY
                    HyperDropIdentity.VisibilityMode.CONTACTS_ONLY -> HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN
                    HyperDropIdentity.VisibilityMode.EVERYONE_10_MIN -> HyperDropIdentity.VisibilityMode.EVERYONE
                    HyperDropIdentity.VisibilityMode.EVERYONE -> HyperDropIdentity.VisibilityMode.HIDDEN
                }
                HyperDropIdentity.setVisibilityMode(context, nextMode)
                visibilityMode = nextMode
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Device alias
        Text(
            text = deviceAlias,
            style = HyperDropTypography.TitleMedium
        )
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(HyperDropColors.TextDisabled)
        )
        
        // Visibility indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(visibilityColor, CircleShape)
            )
            Text(
                text = visibilityText,
                style = HyperDropTypography.BodySmall.copy(
                    color = visibilityColor.copy(alpha = 0.9f)
                )
            )
        }
    }
}
