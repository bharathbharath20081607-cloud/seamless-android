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

// Refined color schemes with better visual hierarchy AND Electric Vibrancy
private val DarkColorScheme = darkColorScheme(
    primary = SeamlessTeal, // Electric Cyan
    onPrimary = Color.Black,
    primaryContainer = SeamlessPrimaryDark,
    onPrimaryContainer = Color.White,
    
    secondary = SeamlessPurple, // Electric Purple
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A148C),
    onSecondaryContainer = Color(0xFFE1BEE7),
    
    tertiary = SeamlessPink, // Hot Pink
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF880E4F),
    onTertiaryContainer = Color(0xFFF8BBD0),
    
    error = SeamlessColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = SeamlessColors.DarkBackground, // Deep Blue-Black
    onBackground = SeamlessColors.TextPrimaryDark,
    
    surface = SeamlessColors.DarkSurface,
    onSurface = SeamlessColors.TextPrimaryDark,
    surfaceVariant = SeamlessColors.DarkSurfaceContainer,
    onSurfaceVariant = SeamlessColors.TextSecondaryDark,
    
    outline = SeamlessColors.DarkOutline,
    outlineVariant = Color(0xFF444746),
    
    scrim = Color.Black,
    inverseSurface = SeamlessColors.LightSurface,
    inverseOnSurface = SeamlessColors.TextPrimaryLight,
    inversePrimary = SeamlessPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = SeamlessPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = SeamlessTeal,
    onPrimaryContainer = Color.Black,
    
    secondary = SeamlessPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E5F5),
    onSecondaryContainer = Color(0xFF4A148C),
    
    tertiary = SeamlessPink,
    onTertiary = Color.White,
    
    background = SeamlessColors.LightBackground,
    onBackground = SeamlessColors.TextPrimaryLight,
    
    surface = SeamlessColors.LightSurface,
    onSurface = SeamlessColors.TextPrimaryLight,
    surfaceVariant = SeamlessColors.LightSurfaceContainer,
    onSurfaceVariant = SeamlessColors.TextSecondaryLight,
    
    error = SeamlessColors.Error,
    onError = Color.White,
    
    outline = SeamlessColors.LightOutline,
    outlineVariant = Color(0xFFC4C7C5)
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
