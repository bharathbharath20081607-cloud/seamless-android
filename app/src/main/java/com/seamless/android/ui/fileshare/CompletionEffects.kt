@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
 * Apple Pay / Stripe-style animated stroke checkmark with outer ring sweep,
 * soft emerald bloom, and PathMeasure stroke drawing.
 */
@Composable
fun SuccessCheckmark(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val haptic = LocalHapticFeedback.current
    val ringSweep = remember { Animatable(0f) }
    val fillAlpha = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }
    val containerScale = remember { Animatable(0.88f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            containerScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
            )
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            // 0-420ms: Outer ring sweeps 0 -> 360 deg
            ringSweep.animateTo(
                targetValue = 1f,
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(260)
            // 260-520ms: Soft emerald fill + radial bloom expands
            fillAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(360)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // 360-660ms: PathMeasure draws the 2-segment checkmark
            checkProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "emerald_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = containerScale.value
                scaleY = containerScale.value
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val maxR = size.minDimension / 2f
            val ringR = maxR * 0.72f
            val emerald = HyperDropColors.SuccessGreen

            // Radial emerald bloom
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        emerald.copy(alpha = 0.24f * fillAlpha.value * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR
            )

            // Soft emerald inner fill
            drawCircle(
                color = emerald.copy(alpha = 0.14f * fillAlpha.value),
                radius = ringR,
                center = center
            )

            // Sweeping outer ring (0 -> 360 degrees)
            drawArc(
                color = emerald,
                startAngle = -90f,
                sweepAngle = 360f * ringSweep.value,
                useCenter = false,
                topLeft = Offset(center.x - ringR, center.y - ringR),
                size = androidx.compose.ui.geometry.Size(ringR * 2f, ringR * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            // PathMeasure 2-segment checkmark stroke
            if (checkProgress.value > 0f) {
                val fullCheckPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x - ringR * 0.38f, center.y + ringR * 0.02f)
                    lineTo(center.x - ringR * 0.08f, center.y + ringR * 0.30f)
                    lineTo(center.x + ringR * 0.42f, center.y - ringR * 0.26f)
                }
                val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
                pathMeasure.setPath(fullCheckPath, false)
                val segmentPath = androidx.compose.ui.graphics.Path()
                pathMeasure.getSegment(
                    0f,
                    pathMeasure.length * checkProgress.value,
                    segmentPath,
                    true
                )
                drawPath(
                    path = segmentPath,
                    color = Color.White,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
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
