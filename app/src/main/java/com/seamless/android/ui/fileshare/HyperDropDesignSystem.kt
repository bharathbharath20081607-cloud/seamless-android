@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.fileshare
import androidx.compose.ui.unit.dp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// =====================================================================
//  🎨 HYPERDROP DESIGN SYSTEM 2.0
//  Premium design tokens, shared components, and the Cyber-Fluid
//  visual language for the entire HyperDrop feature.
// =====================================================================

// ======================== COLOR TOKENS ========================

/**
 * Centralized color palette for HyperDrop.
 * ALL HyperDrop files should reference these instead of hardcoded Color() values.
 */
object HyperDropColors {
    // --- Canvas & Atmosphere (Obsidian Luxe) ---
    val TrueBlack = Color(0xFF000000)               // 100% Pure OLED Black
    val ObsidianCanvas = Color(0xFF070709)          // Deep luxury atmospheric obsidian
    val DeepSpaceBlack = TrueBlack                  // Backward compatibility alias
    val SpaceNavy = ObsidianCanvas                  // Backward compatibility alias
    val SpaceBlue = Color(0xFF0C0C10)               // Deep dark elevation
    val DarkNavy = ObsidianCanvas

    // --- Surfaces (Tonal Elevation) ---
    val CardSurface = Color(0xFF101014)             // Primary dark titanium card
    val CardSurfaceDark = Color(0xFF0A0A0E)         // Deep sunken card container
    val CardSurfaceLight = Color(0xFF16161D)        // Raised titanium surface
    val CardSurfaceElevated = Color(0xFF1E1E28)     // Active / pressed card state
    val GlassSurface = Color.White.copy(alpha = 0.04f) // Translucent liquid glass

    // --- Optical Whites & Monochromes ---
    val PrimaryCyan = Color(0xFFFFFFFF)             // 100% Pure Optical White
    val SecondaryTeal = Color(0xFFB0B0B8)           // Apple Titanium Gray
    val AccentBlue = Color(0xFFFFFFFF)              // Pure Optical White
    val BeamCyan = Color(0xFFFFFFFF)                // Diamond White
    val GlowPurple = Color.White.copy(alpha = 0.12f)// Ethereal lunar luminescence

    // --- Semantic Accents (Apple HIG Precision) ---
    val SuccessGreen = Color(0xFF30D158)            // Apple System Emerald (Live / Connected)
    val ErrorRed = Color(0xFFFF453A)                // Apple System Coral (Error / Decline)
    val WarningAmber = Color(0xFFFF9F0A)            // Apple System Amber (Warning / Pending)
    val InfoBlue = Color(0xFFE5E5EA)                // Lunar Silver

    // --- Typography & Text ---
    val TextPrimary = Color(0xFFFFFFFF)             // 100% Crisp Optical White
    val TextSecondary = Color(0xFFB0B0B8)           // Titanium Secondary (zero glare)
    val TextTertiary = Color(0xFF6B6B75)            // Muted secondary meta
    val TextDisabled = Color.White.copy(alpha = 0.22f)

    // --- Specular Hairline Borders (Apple Milled Glass) ---
    val BorderSubtle = Color.White.copy(alpha = 0.08f) // 1px resting hairline
    val BorderDefault = Color.White.copy(alpha = 0.12f)// Standard hairline
    val BorderSpecular = Color.White.copy(alpha = 0.18f)// Light-catching active edge
    val BorderFocus = Color.White.copy(alpha = 0.35f)  // Focused stroke

    // --- Visibility States ---
    val VisibilityHidden = Color(0xFFFF453A)
    val VisibilityContacts = Color(0xFFFF9F0A)
    val VisibilityEveryone = Color(0xFF30D158)

    // --- Gradients (as color lists) ---
    val BackgroundGradient = listOf(TrueBlack, ObsidianCanvas, Color(0xFF0C0C10))
    val CardGlowGradient = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
    val BeamGradient = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
    val SuccessGradient = listOf(SuccessGreen, SuccessGreen.copy(alpha = 0.7f))
    val ErrorGradient = listOf(ErrorRed, ErrorRed.copy(alpha = 0.7f))
}

// ======================== TYPOGRAPHY TOKENS ========================

/**
 * Text style presets for consistent typography across HyperDrop.
 */
object HyperDropTypography {
    val DisplayLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = HyperDropColors.TextPrimary
    )
    val DisplayMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
        color = HyperDropColors.TextPrimary
    )
    val TitleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = HyperDropColors.TextPrimary
    )
    val TitleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = HyperDropColors.TextPrimary
    )
    val BodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = HyperDropColors.TextSecondary
    )
    val BodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = HyperDropColors.TextSecondary
    )
    val BodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = HyperDropColors.TextTertiary
    )
    val LabelBold = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = HyperDropColors.TextPrimary
    )
    val Caption = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = HyperDropColors.TextTertiary
    )
    val Percentage = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = HyperDropColors.PrimaryCyan
    )
}

// ======================== SPACING TOKENS ========================

object HyperDropSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screenPadding = 20.dp
    val cardPadding = 20.dp
    val cardCorner = 24.dp
    val iconCircleSize = 52.dp
    val iconSize = 28.dp
}

// ======================== MOTION & PHYSICS TOKENS ========================

object HyperDropMotion {
    // Durations
    const val INSTANT = 150
    const val FAST = 250
    const val MEDIUM = 400
    const val SLOW = 600
    const val DRAMATIC = 1000

    // Standard easing curves
    val StandardEasing = FastOutSlowInEasing
    val EnterEasing = FastOutSlowInEasing
    val ExitEasing = FastOutLinearInEasing
    val LinearEasing = androidx.compose.animation.core.LinearEasing

    // ── Apple WWDC Fluid Spring Physics ──
    // Tactile spring for buttons, cards, touch compression (damping 0.84, stiffness 400)
    val TactileSpring = spring<Float>(
        dampingRatio = 0.84f,
        stiffness = 400f
    )

    // Critically damped spring for state changes & zero-bounce transitions (damping 1.0)
    val CriticallyDamped = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f
    )

    // Bouncy spring for playful / celebratory moments
    val BouncySpring = spring<Float>(
        dampingRatio = 0.84f,
        stiffness = 400f
    )

    // Choreographed entrance cascade interval
    const val STAGGER_INTERVAL_MS = 45L
}

// ======================== LUXURY MODIFIERS ========================

/**
 * Obsidian layered glass surface modifier:
 * Vertical top-lit luminance gradient (#16161D -> #0E0E12) + 1.dp diagonal specular hairline border.
 */
fun Modifier.luxeGlass(
    cornerRadius: Dp = HyperDropSpacing.cardCorner,
    topAlpha: Float = 0.18f,
    bottomAlpha: Float = 0.03f
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF16161D).copy(alpha = 0.92f),
                    Color(0xFF0E0E12).copy(alpha = 0.92f)
                )
            ),
            shape = shape
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = topAlpha),
                    Color.White.copy(alpha = bottomAlpha)
                )
            ),
            shape = shape
        )
}

/**
 * Tactile spring compression modifier (0.97f scale with 0.84f damping, 400f stiffness + subtle haptic).
 */
@Composable
fun Modifier.luxePress(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = HyperDropMotion.TactileSpring,
        label = "luxe_press_scale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * Staggered entrance cascade modifier (45ms-55ms intervals with 0.84f spring physics).
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelayMs: Int = 55
): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * baseDelayMs).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "stagger_alpha_$index"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = HyperDropMotion.TactileSpring,
        label = "stagger_offset_$index"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY
    }
}

// ======================== LIQUID TRANSITIONS ========================

/**
 * Standardized liquid spring sheet and spatial transitions across HyperDrop.
 * Modeled on Apple VisionOS and iOS modal presentation physics.
 */
object HyperDropTransitions {
    val ScreenEnter: EnterTransition = slideInVertically(
        initialOffsetY = { (it * 0.12f).toInt() },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
    ) + fadeIn(
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
    ) + scaleIn(
        initialScale = 0.96f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
    )

    val ScreenExit: ExitTransition = slideOutVertically(
        targetOffsetY = { (it * 0.12f).toInt() },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
    ) + fadeOut(
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
    ) + scaleOut(
        targetScale = 0.96f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
    )

    val ModalEnter: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
    ) + fadeIn()

    val ModalExit: ExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 400f)
    ) + fadeOut()
}

// ======================== BACKGROUNDS ========================

/**
 * Luxury atmospheric background with breathing ambient luminescence.
 * Replaces noisy cartoon stars with deep OLED obsidian stillness and subtle radial light.
 */
@Composable
fun HyperDropBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundGradient = Brush.verticalGradient(
        colors = HyperDropColors.BackgroundGradient
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Ambient breathing radial light layer
        AmbientLuminescenceLayer(intensity)

        // Content overlay
        content()
    }
}

@Composable
private fun AmbientLuminescenceLayer(intensity: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_luma")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Top-center gentle ambient glow (VisionOS-style top lighting)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.035f * breath * intensity),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, -size.height * 0.05f),
                radius = size.width * 0.85f
            )
        )

        // Bottom subtle secondary ambient bounce
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.015f * breath * intensity),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 1.05f),
                radius = size.width * 0.70f
            )
        )
    }
}

// ======================== CARDS ========================

/**
 * Premium glass card with tactile spring press, continuous squircle, and hairline border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(HyperDropSpacing.cardCorner),
    backgroundColor: Color = HyperDropColors.CardSurface.copy(alpha = 0.90f),
    borderColor: Color = HyperDropColors.BorderSubtle,
    showShimmerBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1.0f,
        animationSpec = HyperDropMotion.TactileSpring,
        label = "card_press_scale"
    )

    val mod = if (onClick != null) {
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    } else {
        modifier
    }

    val borderBrush = if (showShimmerBorder) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.14f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                borderColor,
                Color.White.copy(alpha = 0.03f)
            )
        )
    }

    Surface(
        modifier = mod.border(width = 1.dp, brush = borderBrush, shape = shape),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF16161D).copy(alpha = 0.92f),
                        backgroundColor
                    )
                )
            )
        ) {
            // 1.dp inner top specular highlight line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(HyperDropSpacing.cardPadding),
                content = content
            )
        }
    }
}

// ======================== REUSABLE COMPONENTS ========================

/**
 * Reusable live status pill (e.g., "Scanning", "Visible", "Connected") with breathing dot.
 */
@Composable
fun StatusPill(
    label: String,
    color: Color = HyperDropColors.SuccessGreen,
    isPulsing: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pill")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = if (isPulsing) 0.45f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF121217).copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = dotAlpha))
        )
        Text(
            text = label,
            style = HyperDropTypography.Caption.copy(
                color = HyperDropColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        )
    }
}

/**
 * Luxury monogram avatar with machined titanium ring and optional corner badge.
 */
@Composable
fun MonogramAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isActive: Boolean = false,
    badgeIcon: ImageVector? = null
) {
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF20202A),
                            Color(0xFF121218)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = if (isActive) {
                            listOf(HyperDropColors.SuccessGreen, HyperDropColors.SuccessGreen.copy(alpha = 0.3f))
                        } else {
                            listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.06f))
                        }
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = HyperDropTypography.TitleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            )
        }

        if (badgeIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0E))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = if (isActive) HyperDropColors.SuccessGreen else Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Reusable icon-in-circle component: obsidian glass circle with hairline border.
 */
@Composable
fun HyperDropIconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = HyperDropSpacing.iconCircleSize,
    iconSize: Dp = HyperDropSpacing.iconSize,
    tint: Color = HyperDropColors.TextPrimary,
    backgroundColor: Color = HyperDropColors.CardSurfaceLight,
    showGlow: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, HyperDropColors.BorderSubtle, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * World-class action button: solid optical white pill button with pitch-black text,
 * Apple-grade tactile spring compression, and micro-haptic feedback.
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Color.White,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    hapticEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1.0f,
        animationSpec = HyperDropMotion.TactileSpring,
        label = "btn_press_scale"
    )

    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                if (hapticEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.Black,
                disabledContainerColor = color.copy(alpha = 0.25f),
                disabledContentColor = Color.Black.copy(alpha = 0.4f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 15.dp),
            interactionSource = interactionSource
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = HyperDropTypography.LabelBold.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                )
            }
        }
    }
}

// ======================== PROGRESS ========================

/**
 * Premium circular progress ring with liquid fill and pulsing glow.
 */
@Composable
fun FluidProgressRing(
    progress: Float,
    modifier: Modifier = Modifier.size(120.dp),
    color: Color = HyperDropColors.PrimaryCyan,
    strokeWidth: Dp = 8.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(HyperDropMotion.MEDIUM, easing = HyperDropMotion.StandardEasing),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "fluid")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val sw = strokeWidth.toPx()

        // Track
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = radius - sw / 2,
            style = Stroke(width = sw)
        )

        // Glow arc (wider, behind)
        if (animatedProgress > 0f) {
            drawArc(
                color = color.copy(alpha = 0.15f * glowPulse),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = sw + 8.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(sw / 2 - 4.dp.toPx(), sw / 2 - 4.dp.toPx()),
                size = Size(size.width - sw + 8.dp.toPx(), size.height - sw + 8.dp.toPx())
            )
        }

        // Liquid fill
        val waveHeight = 8.dp.toPx()
        val fillLevel = size.height * (1f - animatedProgress)

        val liquidPath = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width, size.height)
            lineTo(size.width, fillLevel)
            var x = size.width
            while (x >= 0) {
                val waveY = fillLevel + sin((x / size.width * 10f) + wavePhase) * waveHeight
                lineTo(x, waveY)
                x -= 8f
            }
            close()
        }

        clipPath(
            path = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
        ) {
            drawPath(
                path = liquidPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.6f),
                        color.copy(alpha = 0.3f)
                    )
                )
            )
        }

        // Main progress arc
        if (animatedProgress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}
