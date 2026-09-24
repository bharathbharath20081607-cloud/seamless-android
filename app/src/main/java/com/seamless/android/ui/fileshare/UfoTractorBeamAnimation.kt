package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessColors
import com.seamless.android.ui.theme.SeamlessTeal
import kotlin.math.*
import kotlin.random.Random

/**
 * 🛸 UFO Tractor Beam Animation Component
 * 
 * A cinematic animation where a UFO descends and interacts with file particles.
 * - Sending: Files are beamed UP into the UFO.
 * - Receiving: Files are dropped DOWN from the UFO.
 */
@Composable
fun UfoTransferAnimation(
    modifier: Modifier = Modifier,
    isSending: Boolean,          // True = Beam Up, False = Beam Down
    progress: Float,             // 0f to 100f
    isComplete: Boolean,
    isFailed: Boolean,
    isWaiting: Boolean = false   // Hovering state for receiving
) {
    // Animation States
    // Phase 1: Descend (UFO comes down)
    // Phase 2: Beam (Tractor beam activates, particles move)
    // Phase 3: Ascend (UFO leaves on completion)
    
    val density = LocalDensity.current
    
    // Smooth progress for beam intensity
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "progress"
    )

    // Infinite transition for continuous loops (lights, wobble, particles)
    val infiniteTransition = rememberInfiniteTransition(label = "ufo_loop")
    
    // UFO Hover/Wobble
    val hoverY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hoverY"
    )
    
    val hoverX by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hoverX"
    )

    // Rotating Lights on UFO Rim
    val lightRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lights"
    )
    
    // Beam Pulse
    val beamPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beamPulse"
    )

    // Entry/Exit Animation
    // If complete/failed, fly up. If active, stay down.
    val ufoYOffset by animateFloatAsState(
        targetValue = if (isComplete || isFailed) -300f else 100f, // -300f = offscreen top
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "ufoEntry"
    )
    
    // Beam Extension Animation
    // Beam grows down from UFO when active
    val beamExtension by animateFloatAsState(
        targetValue = if (!isComplete && !isFailed && !isWaiting) 1f else if (isWaiting) 0.3f else 0f,
        animationSpec = tween(1000),
        label = "beamExtension"
    )

    // Helper to generate random particles
    val particles = remember {
        List(15) { generateRandomParticle() }
    }
    
    // Particle Animation Loop
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f, // Just a driver for the canvas draw loop
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val currentUfoY = ufoYOffset + hoverY // Base Y + Hover (ufoYOffset is already in pixels)
        val currentUfoX = centerX + hoverX
        
        // UFO Dimensions
        val ufoWidth = 140.dp.toPx()
        val ufoHeight = 50.dp.toPx()
        val domeWidth = 70.dp.toPx()
        val domeHeight = 40.dp.toPx()
        
        // 1. Draw Tractor Beam (Behind UFO)
        if (beamExtension > 0f) {
            val beamTopWidth = ufoWidth * 0.4f
            val beamBottomWidth = size.width * 0.8f // Wide at bottom
            val beamHeight = size.height - currentUfoY
            val currentBeamHeight = beamHeight * beamExtension
            
            drawTractorBeam(
                topCenter = Offset(currentUfoX, currentUfoY + ufoHeight * 0.5f),
                bottomY = currentUfoY + currentBeamHeight,
                topWidth = beamTopWidth,
                bottomWidth = beamBottomWidth,
                pulseAlpha = beamPulse,
                isSending = isSending
            )
        }
        
        // 2. Draw Particles (Inside Beam)
        if (beamExtension > 0.5f && !isWaiting) {
            particles.forEachIndexed { index, particle ->
                // Animate particle based on system time and its own speed
                // We use the system time (millis) to drive continuous motion without recomposition
                val particleTime = (time * 3.33f) % 1f  // Cycles ~3 times per 10s
                val particleProgress = particleTime
                
                // Offset particle phase so they don't all move together
                val selfPhase = (particle.id * 0.1f) % 1f
                val loopProgress = (particleProgress + selfPhase) % 1f
                
                // Calculate position
                // Sending: Bottom -> Top (1f -> 0f)
                // Receiving: Top -> Bottom (0f -> 1f)
                val rawYProgress = if (isSending) 1f - loopProgress else loopProgress
                
                // Apply easing to make it look like physics (acceleration/deceleration)
                val yProgress = if (isSending) {
                    // Start slow, accelerate up (suction)
                    rawYProgress * rawYProgress 
                } else {
                    // Start slow (gravity), accelerate down
                    rawYProgress * rawYProgress
                }

                val startY = size.height + 50f
                val endY = currentUfoY + ufoHeight
                val currentY = startY + (endY - startY) * (1f - yProgress) // Interpolate
                
                // Horizontal wobble
                val wobble = sin(loopProgress * 10f + particle.id) * 20.dp.toPx()
                val currentX = currentUfoX + wobble + (particle.xOffset * 60.dp.toPx()) // Spread out
                
                // Fade out near UFO (absorbed/spawned)
                val alpha = when {
                    yProgress < 0.1f -> yProgress * 10f // Fade in at bottom (Sending) / top (Receiving)?
                    yProgress > 0.8f -> (1f - yProgress) * 5f // Fade out at top (UFO)
                    else -> 1f
                }
                
                // Only draw if within beam area approximately
                if (alpha > 0f) {
                    drawFileParticle(
                        center = Offset(currentX, currentY),
                        size = particle.size.dp.toPx(),
                        type = particle.type,
                        rotation = particle.rotation + (loopProgress * 360f),
                        alpha = alpha * beamExtension, // Hide if beam is retracted
                        color = if (isSending) SeamlessCyan else SeamlessTeal
                    )
                }
            }
        }

        // 3. Draw UFO
        drawUfo(
            center = Offset(currentUfoX, currentUfoY),
            width = ufoWidth,
            height = ufoHeight,
            domeWidth = domeWidth,
            domeHeight = domeHeight,
            rotation = lightRotation,
            isSending = isSending
        )
    }
}

// --- Drawing Helpers ---

private fun DrawScope.drawUfo(
    center: Offset,
    width: Float,
    height: Float,
    domeWidth: Float,
    domeHeight: Float,
    rotation: Float,
    isSending: Boolean
) {
    // Enhanced outer glow for premium feel
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                (if (isSending) SeamlessCyan else SeamlessTeal).copy(alpha = 0.3f),
                Color.Transparent
            ),
            center = center,
            radius = width * 0.8f
        ),
        topLeft = Offset(center.x - width * 0.8f, center.y - height * 0.8f),
        size = Size(width * 1.6f, height * 1.6f)
    )
    
    // Dome (Glass) - Enhanced with inner glow
    drawArc(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF81D4FA).copy(alpha = 0.95f),
                Color(0xFF4FC3F7).copy(alpha = 0.8f),
                Color(0xFF0277BD).copy(alpha = 0.6f)
            )
        ),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - domeWidth / 2, center.y - domeHeight),
        size = Size(domeWidth, domeHeight * 2),
    )
    
    // Dome inner shine
    drawArc(
        color = Color.White.copy(alpha = 0.4f),
        startAngle = 200f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = Offset(center.x - domeWidth * 0.4f, center.y - domeHeight * 0.8f),
        size = Size(domeWidth * 0.5f, domeHeight * 1.2f),
    )
    
    // Saucer Body (Metal/Dark) - Enhanced gradient
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF546E7A), // Lighter metal
                Color(0xFF455A64), // Metal grey
                Color(0xFF263238), // Darker rim
                Color(0xFF1A1A1A)  // Nearly black edge
            ),
            center = center,
            radius = width / 2
        ),
        topLeft = Offset(center.x - width / 2, center.y - height / 2),
        size = Size(width, height)
    )
    
    // Rim detail ring
    drawOval(
        color = Color(0xFF37474F),
        topLeft = Offset(center.x - width * 0.45f, center.y - height * 0.35f),
        size = Size(width * 0.9f, height * 0.7f),
        style = Stroke(width = 2.dp.toPx())
    )
    
    // Rim Lights - ENHANCED with 12 lights and glow
    val lightCount = 12
    val radiusX = width * 0.45f
    val radiusY = height * 0.45f
    
    for (i in 0 until lightCount) {
        val angleRad = Math.toRadians((rotation + i * (360f / lightCount)).toDouble())
        val lightX = center.x + (radiusX * cos(angleRad)).toFloat()
        val lightY = center.y + (radiusY * sin(angleRad)).toFloat()
        
        // Alternating colors for visual interest
        val lightColor = if (i % 2 == 0) {
            if (isSending) SeamlessCyan else SeamlessTeal
        } else {
            if (isSending) HyperDropColors.AccentBlue else HyperDropColors.SecondaryTeal
        }
        
        // Outer glow (larger)
        drawCircle(
            color = lightColor.copy(alpha = 0.2f),
            radius = 12.dp.toPx(),
            center = Offset(lightX, lightY)
        )
        
        // Mid glow
        drawCircle(
            color = lightColor.copy(alpha = 0.5f),
            radius = 6.dp.toPx(),
            center = Offset(lightX, lightY)
        )
        
        // Core light
        drawCircle(
            color = lightColor,
            radius = 4.dp.toPx(),
            center = Offset(lightX, lightY)
        )
        
        // Center highlight
        drawCircle(
            color = Color.White,
            radius = 1.5.dp.toPx(),
            center = Offset(lightX, lightY)
        )
    }
    
    // Engine Glow (Bottom Center) - ENHANCED with multiple layers
    // Outer engine glow
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                (if (isSending) SeamlessCyan else SeamlessTeal).copy(alpha = 0.4f),
                Color.Transparent
            )
        ),
        topLeft = Offset(center.x - width * 0.35f, center.y + height * 0.05f),
        size = Size(width * 0.7f, height * 0.5f)
    )
    
    // Core engine glow
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                if (isSending) SeamlessCyan else SeamlessTeal,
                Color.Transparent
            )
        ),
        topLeft = Offset(center.x - width * 0.2f, center.y + height * 0.1f),
        size = Size(width * 0.4f, height * 0.25f)
    )
    
    // Inner white core
    drawOval(
        color = Color.White.copy(alpha = 0.9f),
        topLeft = Offset(center.x - width * 0.08f, center.y + height * 0.15f),
        size = Size(width * 0.16f, height * 0.1f)
    )
}

private fun DrawScope.drawTractorBeam(
    topCenter: Offset,
    bottomY: Float,
    topWidth: Float,
    bottomWidth: Float,
    pulseAlpha: Float,
    isSending: Boolean
) {
    val path = Path().apply {
        moveTo(topCenter.x - topWidth / 2, topCenter.y)
        lineTo(topCenter.x + topWidth / 2, topCenter.y)
        lineTo(topCenter.x + bottomWidth / 2, bottomY)
        lineTo(topCenter.x - bottomWidth / 2, bottomY)
        close()
    }
    
    val baseColor = if (isSending) SeamlessCyan else SeamlessTeal // Blue/Cyan vs Green/Teal
    
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.6f * pulseAlpha), // Bright at source
                baseColor.copy(alpha = 0.1f * pulseAlpha), // Fade down
                Color.Transparent                        // Invisible at bottom
            ),
            startY = topCenter.y,
            endY = bottomY
        )
    )
    
    // Add scanlines inside the beam
    val scanlineCount = 8
    for (i in 0 until scanlineCount) {
        val scanY = topCenter.y + ((bottomY - topCenter.y) * ((i.toFloat() / scanlineCount + pulseAlpha) % 1f))
        // Interpolate beam width at this Y position
        val yFraction = ((scanY - topCenter.y) / (bottomY - topCenter.y)).coerceIn(0f, 1f)
        val beamWidthAtY = topWidth + (bottomWidth - topWidth) * yFraction
        drawLine(
            color = baseColor.copy(alpha = 0.15f),
            start = Offset(topCenter.x - beamWidthAtY * 0.4f, scanY),
            end = Offset(topCenter.x + beamWidthAtY * 0.4f, scanY),
            strokeWidth = 1.dp.toPx()
        )
    }
    
    // Add "scanlines" or interference in the beam
    // Simplified: Just some horizontal lines drifting? (Skipping for now to keep clean)
}

private fun DrawScope.drawFileParticle(
    center: Offset,
    size: Float,
    type: ParticleType,
    rotation: Float,
    alpha: Float,
    color: Color
) {
    rotate(rotation, center) {
        when (type) {
            ParticleType.CUBE -> {
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(center.x - size / 2, center.y - size / 2),
                    size = Size(size, size),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Fill
                drawRect(
                    color = color.copy(alpha = alpha * 0.3f),
                    topLeft = Offset(center.x - size / 2, center.y - size / 2),
                    size = Size(size, size)
                )
            }
            ParticleType.DOC -> {
                val path = Path().apply {
                    moveTo(center.x - size / 2, center.y - size / 2)
                    lineTo(center.x + size / 4, center.y - size / 2) // Corner cut start
                    lineTo(center.x + size / 2, center.y - size / 4) // Corner cut end
                    lineTo(center.x + size / 2, center.y + size / 2)
                    lineTo(center.x - size / 2, center.y + size / 2)
                    close()
                }
                drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx()))
                drawPath(path, color.copy(alpha = alpha * 0.2f))
            }
            ParticleType.CIRCLE -> {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size / 2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

// --- Data Models ---

private enum class ParticleType {
    CUBE, DOC, CIRCLE
}

private data class FileParticle(
    val id: Int,
    val type: ParticleType,
    val size: Float, // DP
    val rotation: Float,
    val xOffset: Float // -1f to 1f normalized wobble offset
)

private fun generateRandomParticle(): FileParticle {
    return FileParticle(
        id = Random.nextInt(1000),
        type = ParticleType.entries.toTypedArray().random(),
        size = Random.nextFloat() * 10f + 10f, // 10-20dp
        rotation = Random.nextFloat() * 360f,
        xOffset = Random.nextFloat() * 2f - 1f // -1 to 1
    )
}
