@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Flagship Spring Physics & Motion Tokens
 * Grounded in Hooke's Law harmonic oscillators and Material 3 Expressive motion.
 */
object SeamlessAnimations {
    // Durations (fallbacks for opacity/color fades)
    const val DURATION_QUICK = 180
    const val DURATION_NORMAL = 260
    const val DURATION_SLOW = 420
    const val DURATION_LONG = 750

    // Easing
    val EasingStandard = FastOutSlowInEasing
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // Common Tween Specs
    val SpecQuick = tween<Float>(DURATION_QUICK, 0, EasingStandard)
    val SpecNormal = tween<Float>(DURATION_NORMAL, 0, EasingStandard)
    val SpecEmphasized = tween<Float>(DURATION_SLOW, 0, EasingEmphasized)

    // Spring Physics Tokens
    val TactilePressSpring = spring<Float>(
        dampingRatio = 0.76f,
        stiffness = 620f
    )
    val ExpressiveMorphSpring = spring<Float>(
        dampingRatio = 0.66f,
        stiffness = 460f
    )
    val SpatialFloatSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 380f
    )
    val SpatialOffsetSpring = spring<IntOffset>(
        dampingRatio = 0.86f,
        stiffness = 380f
    )
}

/**
 * Spatial Screen Choreography for Seamless navigation hubs and drill-downs.
 * Eliminates instant cuts and anchors every surface in 3D/lateral space.
 */
object SeamlessTransitions {
    val HubEnter: EnterTransition =
        fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
        slideInHorizontally(
            animationSpec = SeamlessAnimations.SpatialOffsetSpring,
            initialOffsetX = { fullWidth -> fullWidth / 5 }
        ) +
        scaleIn(
            animationSpec = SeamlessAnimations.SpatialFloatSpring,
            initialScale = 0.94f
        )

    val HubExit: ExitTransition =
        fadeOut(animationSpec = tween(170, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(
            animationSpec = SeamlessAnimations.SpatialOffsetSpring,
            targetOffsetX = { fullWidth -> fullWidth / 6 }
        ) +
        scaleOut(
            animationSpec = SeamlessAnimations.SpatialFloatSpring,
            targetScale = 0.95f
        )

    val DrillDownEnter: EnterTransition =
        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
        slideInHorizontally(
            animationSpec = SeamlessAnimations.SpatialOffsetSpring,
            initialOffsetX = { fullWidth -> (fullWidth * 0.30f).toInt() }
        ) +
        scaleIn(
            animationSpec = SeamlessAnimations.SpatialFloatSpring,
            initialScale = 0.96f
        )

    val DrillDownExit: ExitTransition =
        fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(
            animationSpec = SeamlessAnimations.SpatialOffsetSpring,
            targetOffsetX = { fullWidth -> (fullWidth * 0.28f).toInt() }
        ) +
        scaleOut(
            animationSpec = SeamlessAnimations.SpatialFloatSpring,
            targetScale = 0.96f
        )
}

/**
 * Sub-50ms Tactile Press Compression coupled with calibrated Haptic Feedback.
 * Compresses the element using a high-stiffness spring on touch-down and
 * rebounds organically on release.
 */
fun Modifier.tactilePress(
    scaleDown: Float = 0.965f,
    enabled: Boolean = true,
    enableHaptics: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = SeamlessAnimations.TactilePressSpring,
        label = "tactile_press_scale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && enabled && enableHaptics) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Top-lit Specular Surface Modifier.
 * Simulates an overhead light source catching the top 1dp rim of a brushed
 * obsidian or optical alabaster card.
 */
fun Modifier.specularSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    elevated: Boolean = false
): Modifier = composed {
    val isDark = ThemePreferenceManager.isCurrentlyDark
    val fillBrush = if (isDark) {
        if (elevated) {
            Brush.verticalGradient(listOf(Color(0xFF1B202C), Color(0xFF131720)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF181C26), Color(0xFF11141C)))
        }
    } else {
        if (elevated) {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFAFBFD)))
        }
    }

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFFF2F4F8).copy(alpha = if (elevated) 0.14f else 0.10f),
                Color(0xFFF2F4F8).copy(alpha = 0.03f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0B0F17).copy(alpha = 0.09f),
                Color(0xFF0B0F17).copy(alpha = 0.05f)
            )
        )
    }

    this
        .clip(shape)
        .background(fillBrush, shape)
        .border(1.dp, borderBrush, shape)
}

/**
 * WWDC 2025-2026 Concentric Corner Geometry: preserves equal border thickness around
 * nested capsules/cards (`R_inner = max(R_outer - padding, minRadius)`).
 */
fun concentricCornerRadius(outerRadius: Dp, padding: Dp, minRadius: Dp = 6.dp): Dp =
    (outerRadius - padding).coerceAtLeast(minRadius)

/**
 * WWDC 2025-2026 Liquid Glass Optical Modifier:
 * Pairs depth-calibrated optical fill with a 135-degree directional refractive specular rim
 * and contextual color-spill along the upper-left catchlight.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    tintSpill: Color = Color.Transparent,
    elevated: Boolean = false
): Modifier = composed {
    val isDark = ThemePreferenceManager.isCurrentlyDark
    val fillBrush = if (isDark) {
        Brush.linearGradient(
            colors = if (elevated) {
                listOf(
                    Color(0xFF1E2432).copy(alpha = 0.94f),
                    Color(0xFF131722).copy(alpha = 0.92f)
                )
            } else {
                listOf(
                    Color(0xFF181D29).copy(alpha = 0.92f),
                    Color(0xFF11141C).copy(alpha = 0.90f)
                )
            },
            start = Offset.Zero,
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.96f),
                Color(0xFFF6F8FC).copy(alpha = 0.94f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    val midSpill = if (tintSpill != Color.Transparent) {
        tintSpill.copy(alpha = if (isDark) 0.28f else 0.24f)
    } else if (isDark) {
        Color(0xFFF2F4F8).copy(alpha = 0.08f)
    } else {
        Color(0xFF0B0F17).copy(alpha = 0.08f)
    }

    val specularRim = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFFF2F4F8).copy(alpha = if (elevated) 0.26f else 0.20f),
                midSpill,
                Color(0xFFF2F4F8).copy(alpha = 0.04f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.95f),
                midSpill,
                Color(0xFF0B0F17).copy(alpha = 0.10f)
            )
        },
        start = Offset.Zero,
        end = Offset.Infinite
    )

    this
        .clip(shape)
        .background(fillBrush, shape)
        .border(1.dp, specularRim, shape)
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
    color: Color = SeamlessColors.GlowWhite,
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
            style = Stroke(width = maxRadius.toPx() * (1f - alpha))
        )
    }
}
