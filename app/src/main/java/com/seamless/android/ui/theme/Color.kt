@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Seamless — Obsidian & Optical Light Design System
 *
 * Philosophy: True-black canvas (#000000) with Optical White (#FFFFFF) as the
 * sole accent. Depth comes from luminance layering, not hue. Every surface is
 * a shade of obsidian; every accent is a shade of light.
 *
 * Neuroaesthetic rationale:
 *   • High-contrast achromatic palette reduces cognitive load.
 *   • Zinc-grey secondary text leverages Weber's law for comfortable reading.
 *   • Sparse warm accents (Coral, Amber) trigger salience without distraction.
 */

// ─────────────────────────────────────────────
//  Core Palette Tokens
// ─────────────────────────────────────────────

object SeamlessColors {

    // ── Space Graphite Titanium Scale (Dark Mode — Anti-Halation & Anti-Smear) ──
    val Obsidian          = Color(0xFF090B10)  // Space Graphite Titanium — zero AMOLED black smear
    val ObsidianElevated  = Color(0xFF11141C)  // Surface — cards, sheets
    val ObsidianContainer = Color(0xFF181C26)  // Surface container — elevated cards
    val ObsidianBright    = Color(0xFF222734)  // Highlighted rows, pressed state
    val ObsidianBorder    = Color(0xFF2D3446)  // Subtle dividers & outlines

    // ── Studio Optical Scale (Light Mode — Clean Neutral Hierarchy) ─────────
    val OpticalWhite      = Color(0xFFFFFFFF)  // Pure optical card surface
    val OpticalLight      = Color(0xFFF5F6F8)  // Studio systemGroupedBackground canvas
    val OpticalContainer  = Color(0xFFEBEEF3)  // Machined optical surface container
    val OpticalMuted      = Color(0xFFDCE1EA)  // Subtle borders, dividers
    val OpticalBorder     = Color(0xFFC7D0DE)  // Outline

    // ── Text (APCA Polarity Calibrated: Lc -93.2 Dark / Lc +97.1 Light) ──────
    val TextPrimaryDark   = Color(0xFFF2F4F8)  // Optical Platinum on dark (anti-halation)
    val TextSecondaryDark = Color(0xFF98A1B3)  // Precision Cool Slate
    val TextTertiaryDark  = Color(0xFF667085)  // Muted Graphite

    val TextPrimaryLight  = Color(0xFF0B0F17)  // Deep Carbon Ink on light
    val TextSecondaryLight = Color(0xFF475467) // Slate Ink
    val TextTertiaryLight = Color(0xFF667085)  // Muted Graphite Ink

    // ── Accent: Optical Platinum on Dark / Carbon Ink on Light ──────────────
    val AccentPrimary: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFF2F4F8) else Color(0xFF0B0F17)
    val AccentPrimaryLight = Color(0xFF0B0F17) // Carbon ink accent on light

    // ── Apple HIG & M3 HCT System Semantics (Orthogonal Pop-Out Channels) ───
    val SystemBlue: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF2B6EF6) else Color(0xFF1D58D8)
    val Coral: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFF9544B) else Color(0xFFDC2626)
    val Amber: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val Emerald: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF2ED158) else Color(0xFF15803D)
    val SteelBlue: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    // ── Semantic Aliases ────────────────────────────────────
    val Success: Color get() = Emerald
    val Warning: Color get() = Amber
    val Error: Color   get() = Coral
    val Info: Color    get() = SteelBlue

    // ── Message Bubbles (Precision Sapphire Self vs Machined Graphite / White) ──
    val BubbleSelfDark    = Color(0xFF163568)  // Deep Precision Sapphire — "I sent this"
    val BubbleOtherDark   = Color(0xFF181C26)  // Machined Graphite — others
    val BubbleSelfLight   = Color(0xFF1D58D8)  // Precision System Cobalt — "I sent this"
    val BubbleOtherLight  = Color(0xFFFFFFFF)  // Pure Optical White — others

    // ── Bubble Text ─────────────────────────────────────────
    val BubbleSelfTextDark  = Color(0xFFF5F8FF)  // Crisp Optical Blue-White on Sapphire
    val BubbleOtherTextDark = Color(0xFFF2F4F8)  // Optical Platinum on Machined Graphite
    val BubbleSelfTextLight = Color(0xFFFFFFFF)  // Pure White on System Cobalt
    val BubbleOtherTextLight = Color(0xFF0B0F17) // Carbon Ink on Pure Optical White

    // ── Peer Colors (M3 HCT Iso-Luminant: T76 Dark / T42 Light) ─────────────
    private val PeerColorsDark = listOf(
        Color(0xFF60A5FA),  // System Blue
        Color(0xFF34D399),  // System Emerald
        Color(0xFF818CF8),  // System Indigo
        Color(0xFFFBBF24),  // System Amber
        Color(0xFFFB7185),  // System Rose
        Color(0xFF22D3EE),  // System Cyan
        Color(0xFFA78BFA),  // System Violet
        Color(0xFF4ADE80)   // System Mint
    )
    private val PeerColorsLight = listOf(
        Color(0xFF1D4ED8),  // Precision Cobalt
        Color(0xFF047857),  // Deep Emerald
        Color(0xFF4338CA),  // Deep Indigo
        Color(0xFFB45309),  // Precision Amber
        Color(0xFFBE123C),  // Crimson Rose
        Color(0xFF0E7490),  // Deep Cyan
        Color(0xFF6D28D9),  // Deep Violet
        Color(0xFF15803D)   // Forest Emerald
    )
    val PeerColors: List<Color>
        get() = if (ThemePreferenceManager.isCurrentlyDark) PeerColorsDark else PeerColorsLight

    // ── Glassmorphism ───────────────────────────────────────
    val GlassSurfaceDark: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xCC11141C) else Color(0xCCFFFFFF)
    val GlassSurfaceLight = Color(0xCCFFFFFF)  // ~80% white
    val GlassBorderDark: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0x28F2F4F8) else Color(0x1E0B0F17)
    val GlassBorderLight  = Color(0x1E0B0F17)  // Subtle carbon edge

    // ── Shimmer / Glow ──────────────────────────────────────
    val ShimmerBase: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF181C26) else Color(0xFFEBEEF3)
    val ShimmerHighlight: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF222734) else Color(0xFFF5F6F8)
    val GlowWhite: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0x38F2F4F8) else Color(0x220B0F17)
    val GlowSubtle: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0x1CF2F4F8) else Color(0x120B0F17)

    // ── Gradients (Theme-Adaptive) ──────────────────────────
    val BrandGradientStart: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFF2F4F8) else Color(0xFF0B0F17)
    val BrandGradientEnd: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF98A1B3) else Color(0xFF475467)

    val DarkGradientBackgroundStart: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF090B10) else OpticalLight
    val DarkGradientBackgroundEnd: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF11141C) else OpticalWhite

    // ── Legacy Compat (Theme-Adaptive so screens using Dark* work in Light Mode) ─
    val DarkBackground: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Obsidian else OpticalLight
    val DarkSurface: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) ObsidianElevated else OpticalWhite
    val DarkSurfaceContainer: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) ObsidianContainer else OpticalContainer
    val DarkSurfaceBright: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) ObsidianBright else OpticalMuted
    val DarkOutline: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) ObsidianBorder else OpticalBorder

    val LightBackground       = OpticalLight
    val LightSurface          = OpticalWhite
    val LightSurfaceContainer = OpticalContainer
    val LightOutline          = OpticalBorder
}

// ─────────────────────────────────────────────
//  Theme-Adaptive Top-Level Aliases
//
//  Referenced across 30+ UI files. In Dark Mode, SeamlessTeal is Optical White
//  (#FFFFFF); in Light Mode, it automatically switches to Obsidian (#09090B)
//  so icons, borders, text, waveforms, and badges never turn white-on-white!
// ─────────────────────────────────────────────

val SeamlessTeal: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFF2F4F8) else Color(0xFF0B0F17)
val SeamlessCyan: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF98A1B3) else Color(0xFF475467)
val SeamlessPrimaryDark: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF667085) else Color(0xFF475467)
val SeamlessPurple: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF98A1B3) else Color(0xFF475467)
val SeamlessPink: Color
    get() = SeamlessColors.Coral
val SeamlessOrange: Color
    get() = SeamlessColors.Amber

// ─────────────────────────────────────────────
//  Utility Functions
// ─────────────────────────────────────────────

/** Generate a consistent peer color from a string identifier. */
fun generateColorFromString(input: String): Color {
    val hash = input.hashCode()
    val index = kotlin.math.abs(hash) % SeamlessColors.PeerColors.size
    return SeamlessColors.PeerColors[index]
}

/** Return Black or White text depending on background luminance. */
fun getContrastingTextColor(backgroundColor: Color): Color {
    val luminance = (0.299 * backgroundColor.red +
                     0.587 * backgroundColor.green +
                     0.114 * backgroundColor.blue)
    return if (luminance > 0.5) Color.Black else Color.White
}
