@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Flagship Message Bubble — High-Contrast Luminance & Top-Lit Specular Physics
// Engineered for instant Figure-Ground segregation (Von Restorff isolation)
// across both Obsidian Dark and Optical Alabaster Light modes.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MessageBubble(
    isSelf: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val bubbleShape = remember(isSelf, isFirstInGroup, isLastInGroup) {
        CyberBubbleShape(isSelf, isFirstInGroup, isLastInGroup)
    }

    // ── Tactile Press-to-Scale Spring Physics ───────────────────────
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.972f else 1f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 520f
        ),
        label = "bubbleScale"
    )

    val backgroundModifier = if (isSelf) {
        // ── SENT BUBBLE (Precision Sapphire — Apple/Google/Signal Figure-Ground Hierarchy) ──
        Modifier
            .shadow(
                elevation = if (isDark) 3.dp else 4.dp,
                shape = bubbleShape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.20f),
                ambientColor = Color.Black.copy(alpha = 0.16f)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF163568),
                            Color(0xFF10274F)
                        )
                    } else {
                        listOf(
                            Color(0xFF1D58D8),
                            Color(0xFF1746B0)
                        )
                    }
                ),
                shape = bubbleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF60A5FA).copy(alpha = 0.26f),
                            Color(0xFF60A5FA).copy(alpha = 0.06f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color(0xFF1E3A8A).copy(alpha = 0.18f)
                        )
                    }
                ),
                shape = bubbleShape
            )
    } else {
        // ── RECEIVED BUBBLE (Machined Space Graphite / Pure Studio White) ──
        Modifier
            .shadow(
                elevation = if (isDark) 1.dp else 2.dp,
                shape = bubbleShape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.09f),
                ambientColor = Color.Black.copy(alpha = 0.07f)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF181C26),
                            Color(0xFF131720)
                        )
                    } else {
                        listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF8FAFC)
                        )
                    }
                ),
                shape = bubbleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFFF2F4F8).copy(alpha = 0.10f),
                            Color(0xFFF2F4F8).copy(alpha = 0.03f)
                        )
                    } else {
                        listOf(
                            Color(0xFF0B0F17).copy(alpha = 0.09f),
                            Color(0xFF0B0F17).copy(alpha = 0.05f)
                        )
                    }
                ),
                shape = bubbleShape
            )
    }

    val bubbleContentColor = if (isSelf) {
        Color(0xFFF5F8FF)
    } else {
        colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        isPressed = anyPressed
                    }
                }
            }
            .then(backgroundModifier)
            .padding(
                start = if (!isSelf && isLastInGroup) 16.dp else 14.dp,
                end = if (isSelf && isLastInGroup) 16.dp else 14.dp,
                top = 10.dp,
                bottom = 8.dp
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides bubbleContentColor) {
            content()
        }
    }
}

/**
 * Modern Chat Bubble Shape — Continuous Bezier Geometry
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

        val largeRadius = 20.dp.toPx(density)
        val mediumRadius = 14.dp.toPx(density)
        val smallRadius = 6.dp.toPx(density)
        val tailWidth = 6.dp.toPx(density)
        val tailHeight = 10.dp.toPx(density)

        val w = size.width
        val h = size.height

        val topLeadingRadius = if (isSelf) largeRadius else if (isFirstInGroup) largeRadius else smallRadius
        val topTrailingRadius = if (isSelf) { if (isFirstInGroup) largeRadius else smallRadius } else largeRadius
        val bottomLeadingRadius = if (isSelf) largeRadius else if (isLastInGroup) smallRadius else mediumRadius
        val bottomTrailingRadius = if (isSelf) { if (isLastInGroup) smallRadius else mediumRadius } else largeRadius

        if (isSelf) {
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

            if (isLastInGroup) {
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
