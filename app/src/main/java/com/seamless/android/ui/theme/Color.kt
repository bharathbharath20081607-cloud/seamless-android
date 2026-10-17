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

    // ── Obsidian Scale (Dark Mode) ──────────────────────────
    val Obsidian          = Color(0xFF000000)  // True black — canvas / background
    val ObsidianElevated  = Color(0xFF101014)  // Surface — cards, sheets
    val ObsidianContainer = Color(0xFF1A1A20)  // Surface container — elevated cards
    val ObsidianBright    = Color(0xFF26262E)  // Highlighted rows, pressed state
    val ObsidianBorder    = Color(0xFF2E2E38)  // Subtle dividers & outlines

    // ── Optical Scale (Light Mode) ──────────────────────────
    val OpticalWhite      = Color(0xFFFFFFFF)  // Pure white
    val OpticalLight      = Color(0xFFF5F5F7)  // Background — light mode canvas
    val OpticalContainer  = Color(0xFFEAEAEF)  // Surface container
    val OpticalMuted      = Color(0xFFD4D4DB)  // Subtle borders, dividers
    val OpticalBorder     = Color(0xFFC0C0C8)  // Outline

    // ── Text ────────────────────────────────────────────────
    val TextPrimaryDark   = Color(0xFFFFFFFF)  // White on dark
    val TextSecondaryDark = Color(0xFFA1A1AA)  // Zinc-400
    val TextTertiaryDark  = Color(0xFF71717A)  // Zinc-500

    val TextPrimaryLight  = Color(0xFF09090B)  // Zinc-950 on light
    val TextSecondaryLight = Color(0xFF52525B) // Zinc-600 (higher contrast in light mode)
    val TextTertiaryLight = Color(0xFF71717A)  // Zinc-500

    // ── Accent: Optical White on Dark / Obsidian on Light ───
    val AccentPrimary: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFFFFFFF) else Color(0xFF09090B)
    val AccentPrimaryLight = Color(0xFF09090B) // Near-black accent on light

    // ── Warm Sparks (Semantic / Sparse Use Only) ────────────
    val Coral             = Color(0xFFFF6B6B)  // Errors, destructive actions
    val Amber             = Color(0xFFFBBF24)  // Warnings, pending states
    val Emerald           = Color(0xFF10B981)  // Success, connected, verified
    val SteelBlue         = Color(0xFF3B82F6)  // Info, links, interactive

    // ── Semantic Aliases ────────────────────────────────────
    val Success = Emerald
    val Warning = Amber
    val Error   = Coral
    val Info    = SteelBlue

    // ── Message Bubbles ─────────────────────────────────────
    val BubbleSelfDark    = Color(0xFFFFFFFF)  // White bubble — "I sent this"
    val BubbleOtherDark   = Color(0xFF1A1A20)  // Obsidian container — others
    val BubbleSelfLight   = Color(0xFF09090B)  // Near-black bubble in light mode
    val BubbleOtherLight  = Color(0xFFEAEAEF)  // Light container — others

    // ── Bubble Text ─────────────────────────────────────────
    val BubbleSelfTextDark  = Color(0xFF000000)  // Black text on white bubble
    val BubbleOtherTextDark = Color(0xFFFFFFFF)  // White text on dark bubble
    val BubbleSelfTextLight = Color(0xFFFFFFFF)  // White text on dark bubble
    val BubbleOtherTextLight = Color(0xFF09090B) // Dark text on light bubble

    // ── Peer Colors (Vibrant and legible on both Dark & Light) ─
    val PeerColors = listOf(
        Color(0xFF2563EB),  // Royal Blue
        Color(0xFF059669),  // Emerald
        Color(0xFFD97706),  // Amber
        Color(0xFFDB2777),  // Rose
        Color(0xFF7C3AED),  // Violet
        Color(0xFFEA580C),  // Orange
        Color(0xFF0284C7),  // Sky
        Color(0xFF16A34A)   // Green
    )

    // ── Glassmorphism ───────────────────────────────────────
    val GlassSurfaceDark: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xCC101014) else Color(0xCCFFFFFF)
    val GlassSurfaceLight = Color(0xCCFFFFFF)  // ~80% white
    val GlassBorderDark: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0x33FFFFFF) else Color(0x26000000)
    val GlassBorderLight  = Color(0x33000000)  // 20% black edge

    // ── Shimmer / Glow ──────────────────────────────────────
    val ShimmerBase: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF1A1A20) else Color(0xFFEAEAEF)
    val ShimmerHighlight: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF26262E) else Color(0xFFF5F5F7)
    val GlowWhite: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0x40FFFFFF) else Color(0x2609090B)
    val GlowSubtle: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0x20FFFFFF) else Color(0x1409090B)

    // ── Gradients (Theme-Adaptive) ──────────────────────────
    val BrandGradientStart: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFFFFFFF) else Color(0xFF09090B)
    val BrandGradientEnd: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFA1A1AA) else Color(0xFF3F3F46)

    val DarkGradientBackgroundStart: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF000000) else OpticalLight
    val DarkGradientBackgroundEnd: Color
        get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF101014) else OpticalWhite

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
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFFFFFFF) else Color(0xFF09090B)
val SeamlessCyan: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFA1A1AA) else Color(0xFF52525B)
val SeamlessPrimaryDark: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFF71717A) else Color(0xFF3F3F46)
val SeamlessPurple: Color
    get() = if (ThemePreferenceManager.isCurrentlyDark) Color(0xFFA1A1AA) else Color(0xFF52525B)
val SeamlessPink        = Color(0xFFFF6B6B)            // Coral
val SeamlessOrange      = Color(0xFFF59E0B)            // Amber

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
