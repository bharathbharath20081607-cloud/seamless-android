package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🎉 Premium Completion Effects for HyperDrop
 * 
 * - Confetti burst animation
 * - Success checkmark with glow
 * - Particle explosion effect
 */

// Luxury Shimmer palette: Optical White, Lunar Silver, and Apple System Emerald
private val confettiColors = listOf(
    HyperDropColors.SuccessGreen,
    Color.White,
    Color(0xFFE5E5EA),
    HyperDropColors.SuccessGreen.copy(alpha = 0.7f),
    Color.White.copy(alpha = 0.8f),
)

/**
 * Confetti burst animation overlay
 */
@Composable
fun ConfettiBurst(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val particles = remember { 
        List(60) { 
            ConfettiParticle(
                x = 0.5f,
                y = 0.5f,
                velocityX = (Random.nextFloat() - 0.5f) * 2f,
                velocityY = Random.nextFloat() * -1.5f - 0.5f,
                color = confettiColors.random(),
                size = Random.nextFloat() * 8f + 4f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 20f
            )
        }
    }
    
    val confettiProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        confettiProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(3000, easing = LinearEasing)
        )
    }
    val time = confettiProgress.value
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val gravity = 0.02f
        
        particles.forEach { particle ->
            // Physics simulation using infiniteTransition time value
            val t = time * 3f // Speed up
            val x = size.width * (particle.x + particle.velocityX * t)
            val y = size.height * (particle.y + particle.velocityY * t + 0.5f * gravity * t * t)
            
            // Fade out as particles fall
            val alpha = (1f - time).coerceIn(0f, 1f)
            
            if (y < size.height && alpha > 0f) {
                val rotation = particle.rotation + particle.rotationSpeed * t * 360f
                val pieceWidth = particle.size
                val pieceHeight = particle.size * 0.6f
                val pivotOffset = Offset(x, y)
                
                // Apply rotation transform so confetti visually spins
                rotate(degrees = rotation, pivot = pivotOffset) {
                    drawRect(
                        color = particle.color.copy(alpha = alpha),
                        topLeft = Offset(x - pieceWidth / 2, y - pieceHeight / 2),
                        size = androidx.compose.ui.geometry.Size(pieceWidth, pieceHeight)
                    )
                }
            }
        }
    }
}

/**
 * Success checkmark with scale-in and glow effect
 */
@Composable
fun SuccessCheckmark(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var animationTriggered by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(100)
            animationTriggered = true
        }
    }
    
    // Haptic feedback when checkmark appears
    LaunchedEffect(Unit) {
        delay(200) // Wait for scale-in animation to start
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // Glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    if (isVisible) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            // Glow background
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(
                    color = HyperDropColors.SuccessGreen.copy(alpha = glowAlpha * 0.3f),
                    radius = size.minDimension / 2
                )
                drawCircle(
                    color = HyperDropColors.SuccessGreen.copy(alpha = glowAlpha * 0.5f),
                    radius = size.minDimension / 3
                )
            }
            
            // Checkmark icon
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Success",
                tint = HyperDropColors.SuccessGreen,
                modifier = Modifier.size(80.dp)
            )
        }
    }
}

/**
 * Particle explosion effect (burst of small dots)
 */
@Composable
fun ParticleBurst(
    isActive: Boolean,
    color: Color = HyperDropColors.SuccessGreen,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val particles = remember {
        List(30) {
            val angle = Random.nextFloat() * 360f
            val speed = Random.nextFloat() * 0.8f + 0.2f
            BurstParticle(
                angle = angle,
                speed = speed,
                size = Random.nextFloat() * 6f + 2f
            )
        }
    }
    
    // Use Animatable + LaunchedEffect to guarantee burst plays from 0→1
    val burstProgress = remember { Animatable(0f) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.minDimension / 2
        val progress = burstProgress.value
        
        particles.forEach { particle ->
            val radians = Math.toRadians(particle.angle.toDouble())
            val distance = maxRadius * particle.speed * progress
            
            val x = centerX + (distance * cos(radians)).toFloat()
            val y = centerY + (distance * sin(radians)).toFloat()
            
            // Fade out as particles expand
            val alpha = (1f - progress).coerceIn(0f, 1f)
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = particle.size * (1f - progress * 0.5f),
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Animated circular progress ring with glowing trail
 */
@Composable
fun GlowingProgressRing(
    progress: Float, // 0f to 100f
    modifier: Modifier = Modifier,
    color: Color = HyperDropColors.PrimaryCyan,
    trackColor: Color = Color.White.copy(alpha = 0.1f),
    strokeWidth: Float = 12f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    // Glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )
    
    Canvas(modifier = modifier) {
        val sweepAngle = (animatedProgress / 100f) * 360f
        val startAngle = -90f
        
        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
        
        // Glow layer
        drawArc(
            color = color.copy(alpha = 0.3f * glowIntensity),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth + 8f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
        
        // Main progress
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

/**
 * Shimmer effect overlay for cards during active transfer
 */
@Composable
fun ShimmerOverlay(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val shimmerWidth = size.width * 0.3f
        val shimmerStart = size.width * shimmerX
        
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                startX = shimmerStart,
                endX = shimmerStart + shimmerWidth
            )
        )
    }
}

// Data classes for particles
private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float
)

private data class BurstParticle(
    val angle: Float,
    val speed: Float,
    val size: Float
)
