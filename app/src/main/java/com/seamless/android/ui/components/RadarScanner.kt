package com.seamless.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan
import com.seamless.android.ui.theme.SeamlessColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class RadarStyle {
    SENDER,   // Outward pulsing, scanning sweep
    RECEIVER  // Inward converging, listening pulse
}

/**
 * Premium Radar Scanner — AirDrop-beating visual design
 * Features: gradient sweep arm with glow trail, crosshairs, multiple
 * staggered pulse rings, outer neon border, center phone icon glow,
 * and device count badge.
 */
@Composable
fun RadarScanner(
    isScanning: Boolean,
    title: String,
    subtitle: String,
    style: RadarStyle = RadarStyle.SENDER,
    deviceCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    // Luxury Monochrome Instrument: Optical White with surgical status semantics
    val accentColor = Color.White

    // ── Main sweep rotation (360° every 3s) ──
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // ── Pulse ring 1 ──
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1Alpha"
    )

    // ── Pulse ring 2 (staggered 800ms) ──
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val pulse2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2Alpha"
    )

    // ── Pulse ring 3 (staggered 1600ms) ──
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse3"
    )
    val pulse3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse3Alpha"
    )

    // ── Outer neon border pulse ──
    val neonPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonPulse"
    )

    // ── Center glow breathing ──
    val centerGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centerGlow"
    )

    // ── Receiver inward ring ──
    val receiverScale by infiniteTransition.animateFloat(
        initialValue = 1.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "receiverScale"
    )
    val receiverAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "receiverAlpha"
    )

    // Spark particle positions (static, drawn along sweep trail)
    val sparks = remember {
        List(12) {
            SparkParticle(
                angleOffset = it * 30f + (it * 7f % 15f),
                radiusFraction = 0.3f + (it * 0.05f % 0.65f),
                size = 1.5f + (it % 3) * 0.8f
            )
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
                val maxRadius = size.minDimension / 2f
                val thinStroke = 1.dp.toPx()
                val medStroke = 2.dp.toPx()

                // ━━━ OUTER NEON GLOW RING ━━━
                if (isScanning) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = neonPulse * 0.15f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * 1.15f
                        ),
                        radius = maxRadius * 1.1f
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = neonPulse * 0.6f),
                        radius = maxRadius,
                        style = Stroke(width = medStroke)
                    )
                }

                // ━━━ CONCENTRIC RINGS (3 rings with subtle fill) ━━━
                val ringRadii = listOf(1f, 0.66f, 0.33f)
                ringRadii.forEach { fraction ->
                    val r = maxRadius * fraction
                    // Very subtle fill between rings
                    drawCircle(
                        color = accentColor.copy(alpha = 0.02f),
                        radius = r
                    )
                    // Ring stroke
                    drawCircle(
                        color = accentColor.copy(alpha = 0.12f),
                        radius = r,
                        style = Stroke(width = thinStroke)
                    )
                }

                // ━━━ CROSSHAIRS ━━━
                val crossAlpha = 0.08f
                // Vertical
                drawLine(
                    color = accentColor.copy(alpha = crossAlpha),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = thinStroke
                )
                // Horizontal
                drawLine(
                    color = accentColor.copy(alpha = crossAlpha),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = thinStroke
                )

                if (isScanning) {
                    if (style == RadarStyle.SENDER) {
                        // ━━━ GRADIENT SWEEP ARM with GLOW TRAIL ━━━
                        // The trailing glow is a 60° arc behind the sweep line
                        val trailSweepAngle = 60f
                        val trailStartAngle = sweepAngle - 90f - trailSweepAngle

                        // Clip to circle
                        clipPath(
                            path = Path().apply {
                                addOval(Rect(center.x - maxRadius, center.y - maxRadius, center.x + maxRadius, center.y + maxRadius))
                            }
                        ) {
                            // Gradient trail arc (fading from bright to transparent)
                            drawArc(
                                brush = Brush.sweepGradient(
                                    0f to Color.Transparent,
                                    0.7f to accentColor.copy(alpha = 0.03f),
                                    1f to accentColor.copy(alpha = 0.2f),
                                    center = center
                                ),
                                startAngle = trailStartAngle,
                                sweepAngle = trailSweepAngle,
                                useCenter = true,
                                topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                                size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                            )
                        }

                        // ━━━ SWEEP LINE (bright, glowing) ━━━
                        rotate(sweepAngle, pivot = center) {
                            // Glow behind the line
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.6f),
                                        accentColor.copy(alpha = 0f)
                                    ),
                                    startY = center.y - maxRadius,
                                    endY = center.y
                                ),
                                start = center,
                                end = Offset(center.x, center.y - maxRadius),
                                strokeWidth = 6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            // Sharp bright line on top
                            drawLine(
                                color = accentColor.copy(alpha = 0.95f),
                                start = center,
                                end = Offset(center.x, center.y - maxRadius),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // ━━━ SPARK PARTICLES along sweep trail ━━━
                        sparks.forEach { spark ->
                            val sparkAngle = sweepAngle - spark.angleOffset
                            val sparkAngleRad = sparkAngle * PI.toFloat() / 180f
                            val sparkRadius = maxRadius * spark.radiusFraction
                            val sparkX = center.x + cos(sparkAngleRad) * sparkRadius
                            val sparkY = center.y + sin(sparkAngleRad) * sparkRadius
                            val distFromSweep = spark.angleOffset / 60f // 0..1 normalized
                            val sparkAlpha = (1f - distFromSweep).coerceIn(0f, 1f) * 0.7f

                            drawCircle(
                                color = accentColor.copy(alpha = sparkAlpha),
                                radius = spark.size,
                                center = Offset(sparkX, sparkY)
                            )
                        }

                        // ━━━ STAGGERED PULSE RINGS ━━━
                        drawCircle(
                            color = accentColor.copy(alpha = pulse1Alpha * 0.4f),
                            radius = maxRadius * pulse1,
                            style = Stroke(width = 2.5f.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor.copy(alpha = pulse2Alpha * 0.3f),
                            radius = maxRadius * pulse2,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor.copy(alpha = pulse3Alpha * 0.2f),
                            radius = maxRadius * pulse3,
                            style = Stroke(width = 1.5f.dp.toPx())
                        )
                    } else {
                        // ━━━ RECEIVER: Inward converging ━━━
                        drawCircle(
                            color = accentColor.copy(alpha = receiverAlpha * 0.5f),
                            radius = maxRadius * receiverScale,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        // Pulsing center glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = receiverAlpha * 0.4f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = maxRadius * 0.35f
                            ),
                            radius = maxRadius * 0.35f
                        )
                    }
                }

                // ━━━ CENTER GLOW + DOT ━━━
                // Soft radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isScanning) centerGlow * 0.25f else 0.1f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius * 0.22f
                    ),
                    radius = maxRadius * 0.22f
                )
                // Bright center dot
                drawCircle(
                    color = accentColor,
                    radius = 5.dp.toPx(),
                    center = center
                )
                // Outer ring around center dot
                drawCircle(
                    color = accentColor.copy(alpha = 0.4f),
                    radius = 10.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5f.dp.toPx())
                )
            }

            // ── Device count badge ──
            if (deviceCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .then(
                            Modifier
                                .padding(0.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Badge background
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor,
                                    accentColor.copy(alpha = 0.7f)
                                )
                            ),
                            radius = size.minDimension / 2f
                        )
                        // Outer glow
                        drawCircle(
                            color = accentColor.copy(alpha = 0.3f),
                            radius = size.minDimension / 2f + 4.dp.toPx()
                        )
                    }
                    Text(
                        text = "$deviceCount",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                letterSpacing = (-0.2).sp
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB0B0B8),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private data class SparkParticle(
    val angleOffset: Float,   // Degrees behind the sweep line
    val radiusFraction: Float, // 0..1 distance from center
    val size: Float           // Pixel radius
)
