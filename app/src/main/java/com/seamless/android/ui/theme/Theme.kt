@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

// Space Graphite Titanium & Studio Optical — Apple HIG & M3 HCT Precision System
private val DarkColorScheme = darkColorScheme(
    primary = SeamlessColors.TextPrimaryDark,       // Optical Platinum accent (anti-halation)
    onPrimary = SeamlessColors.Obsidian,
    primaryContainer = SeamlessColors.ObsidianContainer,
    onPrimaryContainer = SeamlessColors.TextPrimaryDark,

    secondary = SeamlessColors.TextSecondaryDark,   // Cool Slate
    onSecondary = SeamlessColors.Obsidian,
    secondaryContainer = SeamlessColors.ObsidianContainer,
    onSecondaryContainer = SeamlessColors.TextPrimaryDark,

    tertiary = SeamlessColors.TextTertiaryDark,     // Graphite
    onTertiary = SeamlessColors.TextPrimaryDark,
    tertiaryContainer = SeamlessColors.ObsidianBright,
    onTertiaryContainer = SeamlessColors.TextPrimaryDark,

    error = Color(0xFFF9544B),                      // Precision System Red (anti-chromostereopsis)
    onError = Color.White,
    errorContainer = Color(0xFF351314),
    onErrorContainer = Color(0xFFFCA5A5),

    background = SeamlessColors.Obsidian,           // Space Graphite (#090B10) prevents OLED smear
    onBackground = SeamlessColors.TextPrimaryDark,  // Optical Platinum (#F2F4F8, APCA Lc -93.2)

    surface = SeamlessColors.ObsidianElevated,      // #11141C
    onSurface = SeamlessColors.TextPrimaryDark,
    surfaceVariant = SeamlessColors.ObsidianContainer,  // #181C26
    onSurfaceVariant = SeamlessColors.TextSecondaryDark,

    outline = SeamlessColors.ObsidianBorder,
    outlineVariant = Color(0xFF343C50),

    surfaceContainerLowest = SeamlessColors.Obsidian,
    surfaceContainerLow = SeamlessColors.ObsidianElevated,
    surfaceContainer = SeamlessColors.ObsidianContainer,
    surfaceContainerHigh = SeamlessColors.ObsidianBright,
    surfaceContainerHighest = Color(0xFF282E3E),

    scrim = Color(0xFF040508),
    inverseSurface = SeamlessColors.OpticalWhite,
    inverseOnSurface = SeamlessColors.TextPrimaryLight,
    inversePrimary = SeamlessColors.Obsidian
)

private val LightColorScheme = lightColorScheme(
    primary = SeamlessColors.TextPrimaryLight,      // Deep Carbon Ink on light
    onPrimary = SeamlessColors.OpticalWhite,
    primaryContainer = SeamlessColors.OpticalContainer,
    onPrimaryContainer = SeamlessColors.TextPrimaryLight,

    secondary = SeamlessColors.TextSecondaryLight,  // Slate Ink
    onSecondary = SeamlessColors.OpticalWhite,
    secondaryContainer = SeamlessColors.OpticalContainer,
    onSecondaryContainer = SeamlessColors.TextPrimaryLight,

    tertiary = SeamlessColors.TextTertiaryLight,    // Graphite Ink
    onTertiary = SeamlessColors.OpticalWhite,
    tertiaryContainer = Color(0xFFE2E6EE),
    onTertiaryContainer = SeamlessColors.TextPrimaryLight,

    background = SeamlessColors.OpticalLight,       // Studio Optical Neutral (#F5F6F8)
    onBackground = SeamlessColors.TextPrimaryLight,

    surface = SeamlessColors.OpticalWhite,          // Pure Optical White
    onSurface = SeamlessColors.TextPrimaryLight,
    surfaceVariant = SeamlessColors.OpticalContainer,
    onSurfaceVariant = SeamlessColors.TextSecondaryLight,

    surfaceContainerLowest = SeamlessColors.OpticalWhite,
    surfaceContainerLow = Color(0xFFF0F2F6),
    surfaceContainer = SeamlessColors.OpticalContainer,
    surfaceContainerHigh = Color(0xFFE4E8F0),
    surfaceContainerHighest = SeamlessColors.OpticalMuted,

    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),

    outline = SeamlessColors.OpticalBorder,
    outlineVariant = SeamlessColors.OpticalMuted,

    scrim = Color(0xFF090B10),
    inverseSurface = SeamlessColors.ObsidianElevated,
    inverseOnSurface = SeamlessColors.TextPrimaryDark,
    inversePrimary = SeamlessColors.TextPrimaryDark
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                window.context.resources.configuration.isScreenWideColorGamut
            ) {
                window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
