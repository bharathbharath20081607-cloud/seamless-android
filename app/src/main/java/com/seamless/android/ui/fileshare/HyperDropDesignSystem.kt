package com.seamless.android.ui.fileshare

import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessTeal
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================
//  🎨 HYPERDROP DESIGN SYSTEM 2.0
//  Premium design tokens, shared components, and the Cyber-Fluid
//  visual language for the entire HyperDrop feature.
// =====================================================================

// ======================== COLOR TOKENS ========================

/**
 * Centralized color palette for HyperDrop.
 * ALL HyperDrop files should reference these instead of hardcoded Color() values.
 */
object HyperDropColors {
    // --- Backgrounds ---
    val DeepSpaceBlack = Color(0xFF050510)
    val SpaceNavy = Color(0xFF0A1025)
    val SpaceBlue = Color(0xFF101835)
    val DarkNavy = Color(0xFF0A0E21)

    // --- Surfaces ---
    val CardSurface = Color(0xFF1E293B)
    val CardSurfaceDark = Color(0xFF1A2236)
    val CardSurfaceLight = Color(0xFF263548)

    // --- Accent ---
    val PrimaryCyan = SeamlessCyan
    val SecondaryTeal = SeamlessTeal
    val AccentBlue = Color(0xFF00D4FF)
    val BeamCyan = Color(0xFF00FFCC)
    val GlowPurple = Color(0xFF7C3AED)

    // --- Semantic ---
    val SuccessGreen = Color(0xFF00E676)
    val ErrorRed = Color(0xFFFF5252)
    val WarningAmber = Color(0xFFFFD93D)
    val InfoBlue = Color(0xFF42A5F5)

    // --- Text ---
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.7f)
    val TextTertiary = Color.White.copy(alpha = 0.5f)
    val TextDisabled = Color.White.copy(alpha = 0.3f)

    // --- Borders & Dividers ---
    val BorderSubtle = Color.White.copy(alpha = 0.08f)
    val BorderDefault = Color.White.copy(alpha = 0.12f)
    val BorderFocus = SeamlessCyan.copy(alpha = 0.5f)

    // --- Visibility States ---
    val VisibilityHidden = Color(0xFFFF6B6B)
    val VisibilityContacts = Color(0xFFFFD93D)
    val VisibilityEveryone = Color(0xFF00E676)

    // --- Gradients (as color lists) ---
    val BackgroundGradient = listOf(DeepSpaceBlack, SpaceNavy, SpaceBlue)
    val CardGlowGradient = listOf(PrimaryCyan.copy(alpha = 0.3f), Color.Transparent)
    val BeamGradient = listOf(BeamCyan, Color.Transparent)
    val SuccessGradient = listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.7f))
    val ErrorGradient = listOf(ErrorRed, ErrorRed.copy(alpha = 0.7f))
}

// ======================== TYPOGRAPHY TOKENS ========================

/**
 * Text style presets for consistent typography across HyperDrop.
 */
object HyperDropTypography {
    val DisplayLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = HyperDropColors.TextPrimary
    )
    val DisplayMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
        color = HyperDropColors.TextPrimary
    )
    val TitleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = HyperDropColors.TextPrimary
    )
    val TitleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = HyperDropColors.TextPrimary
    )
    val BodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = HyperDropColors.TextSecondary
    )
    val BodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = HyperDropColors.TextSecondary
    )
    val BodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = HyperDropColors.TextTertiary
    )
    val LabelBold = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = HyperDropColors.TextPrimary
    )
    val Caption = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = HyperDropColors.TextTertiary
    )
    val Percentage = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = HyperDropColors.PrimaryCyan
    )
}

// ======================== SPACING TOKENS ========================

object HyperDropSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screenPadding = 20.dp
    val cardPadding = 20.dp
    val cardCorner = 24.dp
    val iconCircleSize = 52.dp
    val iconSize = 28.dp
}

// ======================== MOTION TOKENS ========================

object HyperDropMotion {
    // Durations
    const val INSTANT = 150
    const val FAST = 250
    const val MEDIUM = 400
    const val SLOW = 600
    const val DRAMATIC = 1000

    // Standard easing curves
    val StandardEasing = FastOutSlowInEasing
    val EnterEasing = FastOutSlowInEasing
    val ExitEasing = FastOutLinearInEasing
    val LinearEasing = androidx.compose.animation.core.LinearEasing

    // Spring specs for bouncy interactions
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
}

// ======================== BACKGROUNDS ========================

/**
 * Global animated background with starfield, nebula layers, and occasional shooting stars.
 * Used across ALL HyperDrop screens for visual consistency.
 */
@Composable
fun HyperDropBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundGradient = Brush.verticalGradient(
        colors = HyperDropColors.BackgroundGradient
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Layer 1: Nebula glow blobs
        NebulaLayer(intensity)

        // Layer 2: Starfield particles
        StarfieldEffect(intensity)

        // Layer 3: Occasional shooting star
        ShootingStarEffect(intensity)

        // Content overlay
        content()
    }
}

@Composable
private fun NebulaLayer(intensity: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "nebula")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula_drift"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Top-right nebula blob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    HyperDropColors.GlowPurple.copy(alpha = 0.06f * intensity),
                    Color.Transparent
                ),
                center = Offset(size.width * (0.8f + drift * 0.1f), size.height * 0.15f),
                radius = size.width * 0.5f
            )
        )

        // Bottom-left nebula blob
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    HyperDropColors.PrimaryCyan.copy(alpha = 0.04f * intensity),
                    Color.Transparent
                ),
                center = Offset(size.width * (0.2f - drift * 0.05f), size.height * 0.75f),
                radius = size.width * 0.45f
            )
        )
    }
}

@Composable
private fun StarfieldEffect(intensity: Float) {
    val stars = remember {
        List(60) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                blinkSpeed = Random.nextFloat() * 2f + 0.5f,
                alpha = Random.nextFloat() * 0.5f + 0.2f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starfield")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            val pulse = sin(time * star.blinkSpeed * 6.28f + star.x * 10f) * 0.5f + 0.5f
            val currentAlpha = (star.alpha * pulse * intensity).coerceIn(0f, 1f)

            val driftY = (star.y - time * 0.05f) % 1f
            val yPos = if (driftY < 0) driftY + 1f else driftY

            drawCircle(
                color = HyperDropColors.PrimaryCyan.copy(alpha = currentAlpha),
                radius = star.size,
                center = Offset(star.x * size.width, yPos * size.height)
            )
        }
    }
}

@Composable
private fun ShootingStarEffect(intensity: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "shooting_star")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shoot"
    )

    // Only show the shooting star during a brief window of the animation cycle
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (progress in 0.4f..0.55f && intensity > 0.5f) {
            val t = ((progress - 0.4f) / 0.15f).coerceIn(0f, 1f)
            val startX = size.width * 0.15f
            val startY = size.height * 0.1f
            val endX = size.width * 0.75f
            val endY = size.height * 0.35f

            val currentX = startX + (endX - startX) * t
            val currentY = startY + (endY - startY) * t
            val tailLength = 80f

            // Trail gradient
            val trailAlpha = (1f - t) * 0.6f * intensity
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = trailAlpha)
                    ),
                    start = Offset(currentX - tailLength, currentY - tailLength * 0.4f),
                    end = Offset(currentX, currentY)
                ),
                start = Offset(currentX - tailLength, currentY - tailLength * 0.4f),
                end = Offset(currentX, currentY),
                strokeWidth = 2f
            )
            // Head glow
            drawCircle(
                color = Color.White.copy(alpha = trailAlpha),
                radius = 3f,
                center = Offset(currentX, currentY)
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val blinkSpeed: Float,
    val alpha: Float
)

// ======================== CARDS ========================

/**
 * Premium glass card with animated shimmer border and glassmorphism blur.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(HyperDropSpacing.cardCorner),
    backgroundColor: Color = HyperDropColors.CardSurface.copy(alpha = 0.4f),
    borderColor: Color = HyperDropColors.BorderDefault,
    showShimmerBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val mod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    // Glassmorphism blur — API 31+
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(10.dp)
    } else {
        Modifier
    }

    // Animated shimmer border
    val shimmerBorderMod = if (showShimmerBorder) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer_border")
        val shimmerAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_angle"
        )
        Modifier.border(
            width = 1.dp,
            brush = Brush.sweepGradient(
                colors = listOf(
                    HyperDropColors.PrimaryCyan.copy(alpha = 0.5f),
                    Color.Transparent,
                    HyperDropColors.GlowPurple.copy(alpha = 0.3f),
                    Color.Transparent,
                    HyperDropColors.PrimaryCyan.copy(alpha = 0.5f)
                )
            ),
            shape = shape as RoundedCornerShape
        )
    } else {
        Modifier
    }

    Surface(
        modifier = mod.then(shimmerBorderMod),
        shape = shape,
        color = Color.Transparent,
        border = if (!showShimmerBorder) {
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        } else null
    ) {
        Box {
            // Blurred background layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(blurModifier)
                    .background(backgroundColor)
            )
            // Content layer
            Column(
                modifier = Modifier.padding(HyperDropSpacing.cardPadding),
                content = content
            )
        }
    }
}

// ======================== REUSABLE COMPONENTS ========================

/**
 * Reusable icon-in-circle component used throughout HyperDrop.
 */
@Composable
fun HyperDropIconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = HyperDropSpacing.iconCircleSize,
    iconSize: Dp = HyperDropSpacing.iconSize,
    tint: Color = HyperDropColors.PrimaryCyan,
    backgroundColor: Color = HyperDropColors.PrimaryCyan.copy(alpha = 0.12f),
    showGlow: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (showGlow) {
                    Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.25f),
                            backgroundColor
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(backgroundColor, backgroundColor)
                    )
                }
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Premium action button with glow effect and haptic feedback.
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = HyperDropColors.PrimaryCyan,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    hapticEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val infiniteTransition = rememberInfiniteTransition(label = "btn_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow behind button
        if (enabled && !isLoading) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = glowAlpha * 0.25f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.maxDimension * 0.7f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                )
            }
        }

        Button(
            onClick = {
                if (hapticEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.Black,
                disabledContainerColor = color.copy(alpha = 0.3f),
                disabledContentColor = Color.Black.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            interactionSource = interactionSource
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = HyperDropTypography.LabelBold.copy(color = Color.Black)
                )
            }
        }
    }
}

// ======================== PROGRESS ========================

/**
 * Premium circular progress ring with liquid fill and pulsing glow.
 */
@Composable
fun FluidProgressRing(
    progress: Float,
    modifier: Modifier = Modifier.size(120.dp),
    color: Color = HyperDropColors.PrimaryCyan,
    strokeWidth: Dp = 8.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(HyperDropMotion.MEDIUM, easing = HyperDropMotion.StandardEasing),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "fluid")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val sw = strokeWidth.toPx()

        // Track
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = radius - sw / 2,
            style = Stroke(width = sw)
        )

        // Glow arc (wider, behind)
        if (animatedProgress > 0f) {
            drawArc(
                color = color.copy(alpha = 0.15f * glowPulse),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = sw + 8.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(sw / 2 - 4.dp.toPx(), sw / 2 - 4.dp.toPx()),
                size = Size(size.width - sw + 8.dp.toPx(), size.height - sw + 8.dp.toPx())
            )
        }

        // Liquid fill
        val waveHeight = 8.dp.toPx()
        val fillLevel = size.height * (1f - animatedProgress)

        val liquidPath = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width, size.height)
            lineTo(size.width, fillLevel)
            var x = size.width
            while (x >= 0) {
                val waveY = fillLevel + sin((x / size.width * 10f) + wavePhase) * waveHeight
                lineTo(x, waveY)
                x -= 8f
            }
            close()
        }

        clipPath(
            path = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
        ) {
            drawPath(
                path = liquidPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.6f),
                        color.copy(alpha = 0.3f)
                    )
                )
            )
        }

        // Main progress arc
        if (animatedProgress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}
