package com.seamless.android.ui.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom SF-Symbols-grade microphone symbol with animated diagonal slash tracing and
 * an optical negative-space knockout halo beneath the slash so the silhouette stays
 * unmistakable against both frosted glass and solid accents.
 */
@Composable
fun AnimatedMicSymbol(
    isMuted: Boolean,
    tint: Color,
    backingColor: Color,
    modifier: Modifier = Modifier
) {
    val slashProgress by animateFloatAsState(
        targetValue = if (isMuted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
        label = "micSlash"
    )
    val microScale by animateFloatAsState(
        targetValue = if (isMuted) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 500f),
        label = "micScale"
    )

    Box(modifier = modifier.graphicsLayer {
        scaleX = microScale
        scaleY = microScale
    }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strokeW = (w * 0.088f).coerceAtLeast(1.8f)

            // Microphone capsule body
            val capW = w * 0.27f
            val capH = h * 0.45f
            val capLeft = (w - capW) / 2f
            val capTop = h * 0.12f
            drawRoundRect(
                color = tint,
                topLeft = Offset(capLeft, capTop),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(capW / 2f, capW / 2f)
            )

            // Cradle pickup arc
            val arcW = w * 0.52f
            val arcH = h * 0.44f
            drawArc(
                color = tint,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset((w - arcW) / 2f, capTop + capH * 0.30f),
                size = Size(arcW, arcH),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Stand stem + base
            val stemTop = capTop + capH * 0.30f + arcH
            val stemBottom = h * 0.87f
            drawLine(
                color = tint,
                start = Offset(w / 2f, stemTop),
                end = Offset(w / 2f, stemBottom),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.34f, stemBottom),
                end = Offset(w * 0.66f, stemBottom),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Diagonal slash with optical knockout gap
            if (slashProgress > 0.01f) {
                val p = slashProgress.coerceIn(0f, 1f)
                val sx = w * 0.20f
                val sy = h * 0.20f
                val ex = sx + (w * 0.60f) * p
                val ey = sy + (h * 0.60f) * p
                drawLine(
                    color = backingColor,
                    start = Offset(sx - strokeW * 0.35f, sy - strokeW * 0.35f),
                    end = Offset(ex + strokeW * 0.35f, ey + strokeW * 0.35f),
                    strokeWidth = strokeW * 2.15f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = strokeW * 1.05f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Custom rounded cinema camera symbol with live lens ring and animated slash tracing
 * when video is turned off.
 */
@Composable
fun AnimatedCameraSymbol(
    isEnabled: Boolean,
    tint: Color,
    backingColor: Color,
    modifier: Modifier = Modifier
) {
    val slashProgress by animateFloatAsState(
        targetValue = if (isEnabled) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
        label = "camSlash"
    )
    val lensGlow by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0f,
        animationSpec = tween(220),
        label = "camLens"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.088f).coerceAtLeast(1.8f)

        // Camera body
        val bodyLeft = w * 0.11f
        val bodyTop = h * 0.25f
        val bodyW = w * 0.52f
        val bodyH = h * 0.50f
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.11f, w * 0.11f)
        )

        // Lens trapezoid
        val wingPath = Path().apply {
            moveTo(bodyLeft + bodyW * 0.94f, h * 0.42f)
            lineTo(w * 0.89f, h * 0.29f)
            lineTo(w * 0.89f, h * 0.71f)
            lineTo(bodyLeft + bodyW * 0.94f, h * 0.58f)
            close()
        }
        drawPath(path = wingPath, color = tint)

        // Subtle cutout shutter ring when live
        if (lensGlow > 0.05f) {
            drawCircle(
                color = backingColor.copy(alpha = 0.32f * lensGlow),
                radius = w * 0.08f,
                center = Offset(bodyLeft + bodyW * 0.48f, h * 0.50f)
            )
        }

        // Diagonal negation slash with optical knockout clearing
        if (slashProgress > 0.01f) {
            val p = slashProgress.coerceIn(0f, 1f)
            val sx = w * 0.18f
            val sy = h * 0.18f
            val ex = sx + (w * 0.64f) * p
            val ey = sy + (h * 0.64f) * p
            drawLine(
                color = backingColor,
                start = Offset(sx - strokeW * 0.35f, sy - strokeW * 0.35f),
                end = Offset(ex + strokeW * 0.35f, ey + strokeW * 0.35f),
                strokeWidth = strokeW * 2.15f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = strokeW * 1.05f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Cryptographic shield crest that illuminates an organic orbital inner ring in Tor Strict mode
 * and holds dual-route geometry otherwise.
 */
@Composable
fun AnimatedShieldCrestSymbol(
    isTorStrict: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val strictProgress by animateFloatAsState(
        targetValue = if (isTorStrict) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "shieldStrict"
    )
    val transition = rememberInfiniteTransition(label = "shieldOrbit")
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shieldSpin"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.086f).coerceAtLeast(1.8f)

        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.84f, h * 0.25f)
            quadraticTo(w * 0.84f, h * 0.66f, w * 0.50f, h * 0.88f)
            quadraticTo(w * 0.16f, h * 0.66f, w * 0.16f, h * 0.25f)
            close()
        }
        drawPath(
            path = shield,
            color = tint.copy(alpha = 0.14f + 0.18f * strictProgress)
        )
        drawPath(
            path = shield,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Inner onion ring (strict mode) vs dual-path chevron
        val cx = w * 0.50f
        val cy = h * 0.50f
        if (strictProgress > 0.25f) {
            val r = w * 0.14f
            drawCircle(
                color = tint.copy(alpha = strictProgress),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 0.85f)
            )
            val rad = orbitAngle * (PI / 180.0)
            drawCircle(
                color = tint.copy(alpha = strictProgress),
                radius = strokeW * 0.7f,
                center = Offset(cx + (r * cos(rad)).toFloat(), cy + (r * sin(rad)).toFloat())
            )
        } else {
            val check = Path().apply {
                moveTo(w * 0.38f, h * 0.50f)
                lineTo(w * 0.47f, h * 0.59f)
                lineTo(w * 0.64f, h * 0.41f)
            }
            drawPath(
                path = check,
                color = tint.copy(alpha = 1f - strictProgress * 0.6f),
                style = Stroke(width = strokeW * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * Animated cryptographic zeroization seal for CallEndedPanel that draws the shield
 * contour followed by the verification checkmark over 560ms on composition entry.
 */
@Composable
fun AnimatedZeroizedSealSymbol(
    tint: Color,
    modifier: Modifier = Modifier
) {
    var started by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { started = 1f }
    val drawProgress by animateFloatAsState(
        targetValue = started,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "zeroizedSeal"
    )
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.11f).coerceAtLeast(1.6f)

        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.84f, h * 0.25f)
            quadraticTo(w * 0.84f, h * 0.65f, w * 0.50f, h * 0.88f)
            quadraticTo(w * 0.16f, h * 0.65f, w * 0.16f, h * 0.25f)
            close()
        }
        val segShield = Path()
        pathMeasure.setPath(shield, false)
        val shieldFrac = (drawProgress * 1.45f).coerceIn(0f, 1f)
        pathMeasure.getSegment(0f, pathMeasure.length * shieldFrac, segShield, true)
        drawPath(
            path = segShield,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val checkFrac = ((drawProgress - 0.38f) / 0.62f).coerceIn(0f, 1f)
        if (checkFrac > 0.01f) {
            val check = Path().apply {
                moveTo(w * 0.35f, h * 0.51f)
                lineTo(w * 0.46f, h * 0.62f)
                lineTo(w * 0.66f, h * 0.40f)
            }
            val segCheck = Path()
            pathMeasure.setPath(check, false)
            pathMeasure.getSegment(0f, pathMeasure.length * checkFrac, segCheck, true)
            drawPath(
                path = segCheck,
                color = tint,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * Spring-damped 4-bar link strength symbol whose bar heights breathe subtly during reconnecting.
 */
@Composable
fun AnimatedSignalBarsSymbol(
    bars: Int,
    activeColor: Color,
    isReconnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "signalPulse")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "signalWave"
    )
    val smoothLevel by animateFloatAsState(
        targetValue = bars.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        label = "signalBarsLevel"
    )

    Canvas(modifier = modifier) {
        val barCount = 4
        val gap = (size.width * 0.11f).coerceAtLeast(1.5f)
        val barW = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val fillFrac = ((smoothLevel - i).coerceIn(0f, 1f))
            val pulseAlpha = if (isReconnecting) (0.22f + 0.55f * wave) else (0.18f + 0.82f * fillFrac)
            val targetH = size.height * (0.34f + 0.66f * (i + 1) / barCount)
            val barH = targetH * (0.76f + 0.24f * fillFrac)
            drawRoundRect(
                color = if (fillFrac > 0.1f || isReconnecting) activeColor.copy(alpha = pulseAlpha)
                else Color.White.copy(alpha = 0.18f),
                topLeft = Offset(i * (barW + gap), size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}
