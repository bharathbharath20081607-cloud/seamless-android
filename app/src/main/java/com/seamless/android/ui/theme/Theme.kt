@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// Obsidian & Optical Light — Achromatic Premium Design System
private val DarkColorScheme = darkColorScheme(
    primary = Color.White,                          // Optical White accent
    onPrimary = Color.Black,
    primaryContainer = SeamlessColors.ObsidianContainer,
    onPrimaryContainer = Color.White,

    secondary = SeamlessColors.TextSecondaryDark,   // Zinc-400
    onSecondary = Color.Black,
    secondaryContainer = SeamlessColors.ObsidianContainer,
    onSecondaryContainer = Color.White,

    tertiary = SeamlessColors.TextTertiaryDark,     // Zinc-500
    onTertiary = Color.White,
    tertiaryContainer = SeamlessColors.ObsidianBright,
    onTertiaryContainer = Color.White,

    error = SeamlessColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFF3B0A0A),
    onErrorContainer = SeamlessColors.Coral,

    background = SeamlessColors.Obsidian,           // True black
    onBackground = Color.White,

    surface = SeamlessColors.ObsidianElevated,      // #101014
    onSurface = Color.White,
    surfaceVariant = SeamlessColors.ObsidianContainer,  // #1A1A20
    onSurfaceVariant = SeamlessColors.TextSecondaryDark,

    outline = SeamlessColors.ObsidianBorder,
    outlineVariant = Color(0xFF3A3A44),

    surfaceContainerLowest = SeamlessColors.Obsidian,
    surfaceContainerLow = SeamlessColors.ObsidianElevated,
    surfaceContainer = SeamlessColors.ObsidianContainer,
    surfaceContainerHigh = SeamlessColors.ObsidianBright,
    surfaceContainerHighest = Color(0xFF30303A),

    scrim = Color.Black,
    inverseSurface = SeamlessColors.OpticalWhite,
    inverseOnSurface = SeamlessColors.TextPrimaryLight,
    inversePrimary = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF09090B),                     // Near-black accent on light
    onPrimary = Color.White,
    primaryContainer = SeamlessColors.OpticalContainer,
    onPrimaryContainer = Color(0xFF09090B),

    secondary = SeamlessColors.TextSecondaryLight,   // Zinc-600
    onSecondary = Color.White,
    secondaryContainer = SeamlessColors.OpticalContainer,
    onSecondaryContainer = Color(0xFF09090B),

    tertiary = SeamlessColors.TextTertiaryLight,     // Zinc-500
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E0E8),
    onTertiaryContainer = Color(0xFF09090B),

    background = SeamlessColors.OpticalLight,        // #F5F5F7
    onBackground = SeamlessColors.TextPrimaryLight,

    surface = SeamlessColors.OpticalWhite,
    onSurface = SeamlessColors.TextPrimaryLight,
    surfaceVariant = SeamlessColors.OpticalContainer,
    onSurfaceVariant = SeamlessColors.TextSecondaryLight,

    surfaceContainerLowest = SeamlessColors.OpticalWhite,
    surfaceContainerLow = Color(0xFFF0F0F4),
    surfaceContainer = SeamlessColors.OpticalContainer,
    surfaceContainerHigh = Color(0xFFE2E2EA),
    surfaceContainerHighest = SeamlessColors.OpticalMuted,

    error = SeamlessColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),

    outline = SeamlessColors.OpticalBorder,
    outlineVariant = SeamlessColors.OpticalMuted,

    scrim = Color.Black,
    inverseSurface = SeamlessColors.ObsidianElevated,
    inverseOnSurface = Color.White,
    inversePrimary = Color.White
)

@Composable
fun SeamlessTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }
    ThemePreferenceManager.isCurrentlyDark = shouldUseDark

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            // Use transparent navigation bar for full edge-to-edge effect
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb() // Ensure transparent status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ),
        content = content
    )
}
