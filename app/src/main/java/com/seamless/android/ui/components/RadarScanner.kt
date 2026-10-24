@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    badgeTextColor: Color = MaterialTheme.colorScheme.background
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    // Adapts automatically to Light Mode (dark ink) and Dark Mode / HyperDrop (optical white)
    val accentColor = contentColor
    val contrastBoost = if (accentColor.luminance() < 0.5f) 1.65f else 1.0f

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
                val thinStroke = 0.85.dp.toPx()
                val medStroke = 1.5.dp.toPx()

                // ━━━ 60-TICK CHRONOGRAPH WATCH BEZEL ━━━
                for (tick in 0 until 60) {
                    val angleRad = (tick * 6f - 90f) * PI.toFloat() / 180f
                    val isMajor = tick % 5 == 0
                    val tickLength = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                    val tickAlpha = ((if (isMajor) 0.30f else 0.12f) * contrastBoost).coerceAtMost(0.85f)
                    val startR = maxRadius - tickLength
                    val start = Offset(
                        center.x + cos(angleRad) * startR,
                        center.y + sin(angleRad) * startR
                    )
                    val end = Offset(
                        center.x + cos(angleRad) * maxRadius,
                        center.y + sin(angleRad) * maxRadius
                    )
                    drawLine(
                        color = accentColor.copy(alpha = tickAlpha),
                        start = start,
                        end = end,
                        strokeWidth = if (isMajor) 1.2.dp.toPx() else 0.75.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // ━━━ OUTER SPECULAR RING ━━━
                if (isScanning) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = (neonPulse * 0.12f * contrastBoost).coerceAtMost(0.5f)),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * 1.12f
                        ),
                        radius = maxRadius * 1.08f
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = (neonPulse * 0.45f * contrastBoost).coerceAtMost(0.85f)),
                        radius = maxRadius,
                        style = Stroke(width = medStroke)
                    )
                }

                // ━━━ CONCENTRIC RINGS (Alternating solid hairline & dashed instrument rings) ━━━
                val dashPattern = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                    0f
                )
                val ringRadii = listOf(0.88f, 0.62f, 0.36f)
                ringRadii.forEachIndexed { index, fraction ->
                    val r = maxRadius * fraction
                    drawCircle(
                        color = accentColor.copy(alpha = (0.015f * contrastBoost).coerceAtMost(0.08f)),
                        radius = r
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = ((if (index % 2 == 0) 0.14f else 0.10f) * contrastBoost).coerceAtMost(0.6f)),
                        radius = r,
                        style = Stroke(
                            width = thinStroke,
                            pathEffect = if (index % 2 == 1) dashPattern else null
                        )
                    )
                }

                // ━━━ CROSSHAIRS ━━━
                val crossAlpha = (0.07f * contrastBoost).coerceAtMost(0.3f)
                drawLine(
                    color = accentColor.copy(alpha = crossAlpha),
                    start = Offset(center.x, center.y - maxRadius * 0.88f),
                    end = Offset(center.x, center.y + maxRadius * 0.88f),
                    strokeWidth = thinStroke
                )
                drawLine(
                    color = accentColor.copy(alpha = crossAlpha),
                    start = Offset(center.x - maxRadius * 0.88f, center.y),
                    end = Offset(center.x + maxRadius * 0.88f, center.y),
                    strokeWidth = thinStroke
                )

                if (isScanning) {
                    if (style == RadarStyle.SENDER) {
                        val trailSweepAngle = 60f
                        val trailStartAngle = sweepAngle - 90f - trailSweepAngle

                        clipPath(
                            path = Path().apply {
                                addOval(Rect(center.x - maxRadius, center.y - maxRadius, center.x + maxRadius, center.y + maxRadius))
                            }
                        ) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    0f to Color.Transparent,
                                    0.7f to accentColor.copy(alpha = (0.03f * contrastBoost).coerceAtMost(0.15f)),
                                    1f to accentColor.copy(alpha = (0.18f * contrastBoost).coerceAtMost(0.45f)),
                                    center = center
                                ),
                                startAngle = trailStartAngle,
                                sweepAngle = trailSweepAngle,
                                useCenter = true,
                                topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                                size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                            )
                        }

                        rotate(sweepAngle, pivot = center) {
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.5f),
                                        accentColor.copy(alpha = 0f)
                                    ),
                                    startY = center.y - maxRadius * 0.88f,
                                    endY = center.y
                                ),
                                start = center,
                                end = Offset(center.x, center.y - maxRadius * 0.88f),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = accentColor.copy(alpha = 0.90f),
                                start = center,
                                end = Offset(center.x, center.y - maxRadius * 0.88f),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // ━━━ GOLDEN-ANGLE (137.5°) DISCOVERED DEVICE BLIPS ━━━
                        val visibleBlips = deviceCount.coerceIn(0, 8)
                        for (i in 0 until visibleBlips) {
                            val goldenAngleDeg = (i * 137.5f) % 360f
                            val rad = goldenAngleDeg * PI.toFloat() / 180f
                            val ringFraction = 0.42f + (i % 3) * 0.18f
                            val blipPos = Offset(
                                center.x + cos(rad) * (maxRadius * ringFraction),
                                center.y + sin(rad) * (maxRadius * ringFraction)
                            )
                            // Outer emerald/white bloom
                            drawCircle(
                                color = Color(0xFF30D158).copy(alpha = 0.28f * centerGlow),
                                radius = 10.dp.toPx(),
                                center = blipPos
                            )
                            // Crisp core
                            drawCircle(
                                color = accentColor,
                                radius = 4.dp.toPx(),
                                center = blipPos
                            )
                        }

                        // ━━━ STAGGERED PULSE RINGS ━━━
                        drawCircle(
                            color = accentColor.copy(alpha = pulse1Alpha * 0.35f),
                            radius = maxRadius * 0.88f * pulse1,
                            style = Stroke(width = 1.5f.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor.copy(alpha = pulse2Alpha * 0.25f),
                            radius = maxRadius * 0.88f * pulse2,
                            style = Stroke(width = 1.2f.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor.copy(alpha = pulse3Alpha * 0.18f),
                            radius = maxRadius * 0.88f * pulse3,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    } else {
                        // ━━━ RECEIVER: Inward converging ━━━
                        drawCircle(
                            color = accentColor.copy(alpha = receiverAlpha * 0.45f),
                            radius = maxRadius * 0.88f * receiverScale,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = receiverAlpha * 0.35f),
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
                drawCircle(
                    color = accentColor,
                    radius = 5.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.4f),
                    radius = 10.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.2f.dp.toPx())
                )
            }

            // ── Device count badge ──
            if (deviceCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.25f),
                            radius = size.minDimension / 2f + 4.dp.toPx()
                        )
                        drawCircle(
                            color = accentColor,
                            radius = size.minDimension / 2f
                        )
                    }
                    Text(
                        text = "$deviceCount",
                        color = badgeTextColor,
                        fontSize = 13.sp,
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
            color = contentColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryContentColor,
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
