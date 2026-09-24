package com.seamless.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.theme.SeamlessCyan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.drawscope.Fill
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Premium Message Bubble — Apple iMessage · WhatsApp · Google Messages Fusion
//
// Features:
//  • Elegant gradient for sent messages (brand cyan → deep blue)
//  • Clean surface-variant for received messages (adapts to light/dark theme)
//  • Subtle top-highlight shine for depth (8% opacity, not harsh)
//  • Spring-physics press-to-shrink micro-interaction
//  • Smooth bezier tails on grouped message endings
//  • Soft colored shadows matching bubble tint
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MessageBubble(
    isSelf: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bubbleShape = remember(isSelf, isFirstInGroup, isLastInGroup) {
        CyberBubbleShape(isSelf, isFirstInGroup, isLastInGroup)
    }
    
    // ── Press-to-scale spring animation ──────────────────────────────
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubbleScale"
    )
    
    val backgroundModifier = if (isSelf) {
        // ── SENT BUBBLE ─────────────────────────────────────────────
        // Clean gradient: brand cyan → deep blue (iMessage-inspired)
        Modifier
            .shadow(
                elevation = 3.dp, 
                shape = bubbleShape, 
                spotColor = Color(0xFF0072FF).copy(alpha = 0.25f),
                ambientColor = Color(0xFF00C6FF).copy(alpha = 0.15f)
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00C6FF), // Bright cyan
                        Color(0xFF0072FF)  // Deep blue
                    )
                ),
                shape = bubbleShape
            )
            // Subtle top shine (8% — elegant, not harsh)
            .then(
                Modifier.background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = 50f
                    ),
                    shape = bubbleShape
                )
            )
    } else {
        // ── RECEIVED BUBBLE ─────────────────────────────────────────
        // Clean surface that adapts to light/dark theme (Google Messages style)
        Modifier
            .shadow(
                elevation = 1.dp,
                shape = bubbleShape,
                spotColor = Color.Black.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.08f)
            )
            .background(
                color = colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = bubbleShape
            )
            // Very subtle glass edge for depth
            .then(
                Modifier.background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 30f
                    ),
                    shape = bubbleShape
                )
            )
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
            .then(backgroundModifier)
            .padding(
                start = if (!isSelf && isLastInGroup) 16.dp else 14.dp, 
                end = if (isSelf && isLastInGroup) 16.dp else 14.dp, 
                top = 10.dp, 
                bottom = 10.dp
            )
    ) {
        content()
    }
}

/**
 * Modern Chat Bubble Shape — iMessage/WhatsApp-inspired
 * 
 * Features:
 * - Smooth 20dp corner radii (slightly softer than before)
 * - Refined bezier tails (6dp wide, 10dp tall — less aggressive)
 * - Clean asymmetric grouping: 4dp corners for consecutive same-sender messages
 */
private class CyberBubbleShape(
    private val isSelf: Boolean,
    private val isFirstInGroup: Boolean,
    private val isLastInGroup: Boolean
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        
        // Refined radii — softer feel
        val largeRadius = 20.dp.toPx(density)
        val mediumRadius = 14.dp.toPx(density) 
        val smallRadius = 5.dp.toPx(density)
        val tailWidth = 6.dp.toPx(density)
        val tailHeight = 10.dp.toPx(density)

        val w = size.width
        val h = size.height
        
        // Corner radii based on position in group
        val topLeadingRadius = if (isSelf) largeRadius else if (isFirstInGroup) largeRadius else smallRadius
        val topTrailingRadius = if (isSelf) { if (isFirstInGroup) largeRadius else smallRadius } else largeRadius
        val bottomLeadingRadius = if (isSelf) largeRadius else if (isLastInGroup) smallRadius else mediumRadius
        val bottomTrailingRadius = if (isSelf) { if (isLastInGroup) smallRadius else mediumRadius } else largeRadius

        if (isSelf) {
            // SELF — right side, tail bottom-right
            path.moveTo(topLeadingRadius, 0f)
            path.lineTo(w - topTrailingRadius, 0f)
            
            // Top-right corner
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    w - 2 * topTrailingRadius, 0f, 
                    w, 2 * topTrailingRadius
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            
            if (isLastInGroup) {
                // Smooth bezier tail
                path.lineTo(w, h - tailHeight)
                path.cubicTo(
                    w, h - tailHeight * 0.3f,
                    w + tailWidth * 0.5f, h,
                    w + tailWidth, h + tailHeight * 0.3f
                )
                path.cubicTo(
                    w, h + tailHeight * 0.1f,
                    w - mediumRadius, h,
                    w - largeRadius, h
                )
            } else {
                path.lineTo(w, h - bottomTrailingRadius)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        w - 2 * bottomTrailingRadius, h - 2 * bottomTrailingRadius,
                        w, h
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
            }
            
            path.lineTo(bottomLeadingRadius, h)
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    0f, h - 2 * bottomLeadingRadius,
                    2 * bottomLeadingRadius, h
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            
            path.lineTo(0f, topLeadingRadius)
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    0f, 0f,
                    2 * topLeadingRadius, 2 * topLeadingRadius
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
        } else {
            // RECEIVED — left side, tail bottom-left
            path.moveTo(topLeadingRadius, 0f)
            path.lineTo(w - topTrailingRadius, 0f)
            
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    w - 2 * topTrailingRadius, 0f,
                    w, 2 * topTrailingRadius
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            
            path.lineTo(w, h - bottomTrailingRadius)
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    w - 2 * bottomTrailingRadius, h - 2 * bottomTrailingRadius,
                    w, h
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            
            if (isLastInGroup) {
                path.lineTo(largeRadius, h)
                path.cubicTo(
                    mediumRadius, h,
                    0f, h + tailHeight * 0.1f,
                    -tailWidth, h + tailHeight * 0.3f
                )
                path.cubicTo(
                    -tailWidth * 0.5f, h,
                    0f, h - tailHeight * 0.3f,
                    0f, h - tailHeight
                )
            } else {
                path.lineTo(bottomLeadingRadius, h)
                path.arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        0f, h - 2 * bottomLeadingRadius,
                        2 * bottomLeadingRadius, h
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
            }
            
            path.lineTo(0f, topLeadingRadius)
            path.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    0f, 0f,
                    2 * topLeadingRadius, 2 * topLeadingRadius
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
        }
        
        path.close()
        return Outline.Generic(path)
    }
}

private fun Dp.toPx(density: Density): Float = value * density.density
