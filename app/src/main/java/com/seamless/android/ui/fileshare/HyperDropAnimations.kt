@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium HyperDrop Animations
 * 
 * 1. Sender: UFO abducts the files and flies off.
 * 2. Receiver: UFO arrives and drops the files.
 */

@Composable
fun HyperDropSenderAnimation(
    onAnimationEnd: () -> Unit,
    progress: Int = 0,
    fileName: String = "",
    fileSize: Long = 0L
) {
    // Density-independent position values
    val density = LocalDensity.current
    val ufoRestY = with(density) { 180.dp.toPx() }
    val filesStartY = with(density) { 480.dp.toPx() }
    val filesTargetY = with(density) { 240.dp.toPx() }
    val beamFullHeight = with(density) { 360.dp.toPx() }

    HyperDropBackground(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onAnimationEnd() }
    ) {
        // Animation State
        var animationStep by remember { mutableIntStateOf(0) }
        
        // --- 1. UFO Entry ---
        val ufoY = remember { Animatable(-200f) } // Start off-screen top
        val ufoScale = remember { Animatable(0.5f) }
        
        // --- 2. Beam ---
        val beamAlpha = remember { Animatable(0f) }
        val beamHeight = remember { Animatable(0f) }
        
        // --- 3. Files (Abduction) ---
        val filesY = remember { Animatable(1000f) } // Start from bottom
        val filesScale = remember { Animatable(1f) }
        val filesAlpha = remember { Animatable(1f) }

        LaunchedEffect(Unit) {
            // STEP 1: Files appear at bottom
            filesY.snapTo(filesStartY)
            delay(200)
            
            // STEP 2: UFO Enters (faster)
            launch {
                ufoY.animateTo(
                    targetValue = ufoRestY,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing)
                )
            }
            launch {
                ufoScale.animateTo(
                    targetValue = 1.2f,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing)
                )
            }
            delay(700)
            
            // STEP 3: Beam Activates (faster)
            launch {
                beamAlpha.animateTo(0.6f, tween(400))
            }
            launch {
                beamHeight.animateTo(beamFullHeight, tween(400))
            }
            delay(450)
            
            // STEP 4: Abduction (Files float up to UFO)
            launch {
                filesY.animateTo(
                    targetValue = filesTargetY,
                    animationSpec = tween(1000, easing = LinearEasing)
                )
            }
            launch {
                filesScale.animateTo(0f, tween(1000, easing = LinearEasing))
            }
            launch {
                filesAlpha.animateTo(0f, tween(800, delayMillis = 200))
            }
            delay(1100)
            
            // STEP 5: Beam Deactivates
            launch {
                beamAlpha.animateTo(0f, tween(300))
            }
            launch {
                beamHeight.animateTo(0f, tween(300))
            }
            delay(300)
            
            // STEP 6: UFO Flies Off (Up and away)
            launch {
                ufoY.animateTo(
                    targetValue = -500f,
                    animationSpec = tween(600, easing = FastOutLinearInEasing)
                )
            }
            launch {
                ufoScale.animateTo(0.2f, tween(600))
            }
            delay(700)
            
            onAnimationEnd()
        }

        // Draw Beam (Background layer)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            if (beamAlpha.value > 0f) {
                drawBeam(
                    centerX = canvasWidth / 2,
                    startY = ufoY.value + 60f, // Start below UFO
                    height = beamHeight.value,
                    alpha = beamAlpha.value
                )
            }
        }
        
        // Draw UFO (Composable Layer)
        UfoGraphic(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = ufoY.value
                    scaleX = ufoScale.value
                    scaleY = ufoScale.value
                }
        )
        
        // Draw Files (Icons)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { filesY.value.toDp() }),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = filesScale.value
                        scaleY = filesScale.value
                        alpha = filesAlpha.value
                    }
            ) {
                 Icon(
                    imageVector = if (fileName.isNotEmpty()) HyperDropUtils.getFileTypeIcon(fileName) else Icons.Filled.Description,
                    contentDescription = null,
                    tint = HyperDropColors.PrimaryCyan,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Sending...",
                    style = HyperDropTypography.TitleMedium.copy(
                        color = HyperDropColors.PrimaryCyan
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // Progress overlay (shows real transfer progress during animation)
        if (fileName.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = HyperDropUtils.formatFileName(fileName) + " \u2022 " + HyperDropUtils.formatFileSize(fileSize),
                    style = HyperDropTypography.BodySmall.copy(
                        color = HyperDropColors.TextTertiary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                HyperDropColors.BorderSubtle,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                HyperDropColors.PrimaryCyan.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (progress > 0) "$progress%" else "Starting...",
                    style = HyperDropTypography.Caption.copy(
                        color = HyperDropColors.PrimaryCyan.copy(alpha = 0.8f)
                    )
                )
            }
        }

        // Tap to skip hint
        Text(
            text = "Tap to skip",
            style = HyperDropTypography.Caption.copy(
                color = HyperDropColors.TextDisabled
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
fun HyperDropReceiverAnimation(
    onAnimationEnd: () -> Unit,
    progress: Int = 0,
    fileName: String = "",
    fileSize: Long = 0L
) {
    // Density-independent position values
    val density = LocalDensity.current
    val ufoRestY = with(density) { 180.dp.toPx() }
    val filesStartY = with(density) { 180.dp.toPx() }
    val filesDropY = with(density) { 480.dp.toPx() }
    val beamFullHeight = with(density) { 300.dp.toPx() }

    HyperDropBackground(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onAnimationEnd() }
    ) {
        // --- 1. UFO Entry ---
        val ufoY = remember { Animatable(-200f) }
        val ufoScale = remember { Animatable(0.5f) }
        
        // --- 2. Beam ---
        val beamAlpha = remember { Animatable(0f) }
        val beamHeight = remember { Animatable(0f) }
        
        // --- 3. Files (Drop) ---
        val filesY = remember { Animatable(filesStartY) } // Start inside UFO
        val filesScale = remember { Animatable(0f) }
        
        LaunchedEffect(Unit) {
            // STEP 1: UFO Arrives
            launch {
                ufoY.animateTo(ufoRestY, tween(1000, easing = FastOutSlowInEasing))
            }
            launch {
                ufoScale.animateTo(1.2f, tween(1000))
            }
            delay(1100)
            
            // STEP 2: Beam
            launch {
                beamAlpha.animateTo(0.6f, tween(400))
            }
            launch {
                beamHeight.animateTo(beamFullHeight, tween(400))
            }
            delay(500)
            
            // STEP 3: Drop File
            filesY.snapTo(filesStartY) // Reset position to UFO
            launch {
                filesScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) // Expand
            }
            launch {
                filesY.animateTo(
                    targetValue = filesDropY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) // Drop with bounce
            }
            delay(1200)
            
            // STEP 4: Beam Off & UFO Leave
            launch {
                beamAlpha.animateTo(0f, tween(300))
            }
            launch {
                beamHeight.animateTo(0f, tween(300))
            }
            delay(300)
            
            launch {
                ufoY.animateTo(-500f, tween(800, easing = FastOutLinearInEasing))
            }
            delay(900)
            
            onAnimationEnd()
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val h = beamHeight.value
            
            if (beamAlpha.value > 0f) {
                drawBeam(
                     centerX = canvasWidth / 2,
                     startY = ufoY.value + 60f,
                     height = h,
                     alpha = beamAlpha.value
                )
            }
        }
        
        // Draw UFO (Composable Layer)
        UfoGraphic(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = ufoY.value
                    scaleX = ufoScale.value
                    scaleY = ufoScale.value
                }
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { filesY.value.toDp() }),
            contentAlignment = Alignment.TopCenter
        ) {
             Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = filesScale.value
                        scaleY = filesScale.value
                    }
            ) {
                 Icon(
                    imageVector = if (fileName.isNotEmpty()) HyperDropUtils.getFileTypeIcon(fileName) else Icons.Filled.Description,
                    contentDescription = null,
                    tint = HyperDropColors.PrimaryCyan,
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "Received!",
                    style = HyperDropTypography.TitleLarge.copy(
                        color = HyperDropColors.TextPrimary
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Progress overlay (shows real transfer progress during animation)
        if (fileName.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = HyperDropUtils.formatFileName(fileName) + " \u2022 " + HyperDropUtils.formatFileSize(fileSize),
                    style = HyperDropTypography.BodySmall.copy(
                        color = HyperDropColors.TextTertiary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                HyperDropColors.BorderSubtle,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                HyperDropColors.PrimaryCyan.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (progress > 0) "$progress%" else "Preparing...",
                    style = HyperDropTypography.Caption.copy(
                        color = HyperDropColors.PrimaryCyan.copy(alpha = 0.8f)
                    )
                )
            }
        }

        // Tap to skip hint
        Text(
            text = "Tap to skip",
            style = HyperDropTypography.Caption.copy(
                color = HyperDropColors.TextDisabled
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

// --- Helper Functions ---

@Composable
fun UfoGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(300.dp, 120.dp)) {
        val ufoColor = Color(0xFFE0E0E0) // Silver
        val domeColor = HyperDropColors.PrimaryCyan.copy(alpha = 0.8f)
        val lightColor = HyperDropColors.BeamCyan
        
        val cx = size.width / 2
        val cy = size.height / 2
        
        // Glow (Largest)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(HyperDropColors.PrimaryCyan.copy(alpha = 0.4f), Color.Transparent),
                radius = size.width * 0.5f
            ),
            topLeft = Offset(cx - size.width/2, cy - size.height/2),
            size = size
        )

        // Saucer body
        val bodyW = size.width * 0.66f
        val bodyH = size.height * 0.5f
        drawOval(
            color = ufoColor,
            topLeft = Offset(cx - bodyW/2, cy - bodyH/2),
            size = Size(bodyW, bodyH)
        )
        
        // Dome
        val domeW = size.width * 0.33f
        val domeH = size.height * 0.6f
        drawArc(
            color = domeColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - domeW/2, cy - bodyH/2 - domeH/2 + 10f),
            size = Size(domeW, domeH)
        )
        
        // Lights
        val lightCount = 5
        val spacing = bodyW / (lightCount + 1)
        for (i in 1..lightCount) {
             val lx = (cx - bodyW/2) + (i * spacing)
             val ly = cy + 10f
             drawCircle(
                 color = lightColor,
                 radius = 6f,
                 center = Offset(lx, ly)
             )
        }
    }
}

fun DrawScope.drawBeam(centerX: Float, startY: Float, height: Float, alpha: Float) {
    val beamColor = HyperDropColors.PrimaryCyan.copy(alpha = alpha)
    val path = Path().apply {
        moveTo(centerX - 20f, startY) // Narrow top
        lineTo(centerX + 20f, startY)
        lineTo(centerX + 150f, startY + height) // Wide bottom
        lineTo(centerX - 150f, startY + height)
        close()
    }
    
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(beamColor, Color.Transparent),
            startY = startY,
            endY = startY + height
        )
    )
}
