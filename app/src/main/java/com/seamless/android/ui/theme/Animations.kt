package com.seamless.android.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard animation specs for consistent feel across the app
 */
object SeamlessAnimations {
    // Durations
    const val DURATION_QUICK = 200
    const val DURATION_NORMAL = 300
    const val DURATION_SLOW = 500
    const val DURATION_LONG = 800

    // Easing
    val EasingStandard = FastOutSlowInEasing
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) // Material 3 style
    
    // Common Specs
    val SpecQuick = tween<Float>(DURATION_QUICK, 0, EasingStandard)
    val SpecNormal = tween<Float>(DURATION_NORMAL, 0, EasingStandard)
    val SpecEmphasized = tween<Float>(DURATION_SLOW, 0, EasingEmphasized)
}

/**
 * Modifier that applies a shimmering effect to the component.
 * Useful for loading states.
 */
fun Modifier.shimmerEffect(
    shimmerColor: Color = SeamlessColors.ShimmerHighlight,
    durationMillis: Int = 1500
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            shimmerColor.copy(alpha = 0.5f),
            Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    drawBehind {
        drawRect(brush = brush)
    }
}

/**
 * Modifier that adds a subtle pulsing glow effect
 */
fun Modifier.pulsingGlow(
    color: Color = SeamlessColors.GlowCyan,
    maxRadius: Dp = 10.dp,
    durationMillis: Int = 2000
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "glow")
    
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_alpha"
    )
    
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_scale"
    )

    this.drawBehind {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = size.maxDimension / 2 * scale,
            style = Stroke(width = maxRadius.toPx() * (1f - alpha)) // Fade out stroke
        )
    }
}
