@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.components
import androidx.compose.ui.unit.Dp

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density

// ─────────────────────────────────────────────────────────────────────────────
// Premium Message Bubble — Obsidian & Optical Light Edition
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
    
    // ── Press-to-scale spring animation (weighty 0.84f damping) ──────
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 400f
        ),
        label = "bubbleScale"
    )
    
    val backgroundModifier = if (isSelf) {
        // ── SENT BUBBLE ─────────────────────────────────────────────
        // High-contrast smoked Obsidian glass pill (#1F1F27 -> #17171E) with White@22% specular border
        Modifier
            .shadow(
                elevation = 2.dp, 
                shape = bubbleShape, 
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.2f)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF1F1F27),
                            Color(0xFF17171E)
                        )
                    } else {
                        listOf(
                            Color(0xFF22222A),
                            Color(0xFF18181F)
                        )
                    }
                ),
                shape = bubbleShape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = bubbleShape
            )
    } else {
        // ── RECEIVED BUBBLE ─────────────────────────────────────────
        // Machined dark glass (#121217) with 1.dp White@10% border in dark mode
        Modifier
            .shadow(
                elevation = 1.dp,
                shape = bubbleShape,
                spotColor = Color.Black.copy(alpha = 0.2f),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            )
            .background(
                color = if (isDark) Color(0xFF121217) else colorScheme.surfaceVariant.copy(alpha = 0.85f),
                shape = bubbleShape
            )
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                shape = bubbleShape
            )
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
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        isPressed = anyPressed
                    }
                }
            }
            .then(backgroundModifier)
            .padding(
                start = if (!isSelf && isLastInGroup) 16.dp else 14.dp, 
                end = if (isSelf && isLastInGroup) 16.dp else 14.dp, 
                top = 9.dp, 
                bottom = 8.dp
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
