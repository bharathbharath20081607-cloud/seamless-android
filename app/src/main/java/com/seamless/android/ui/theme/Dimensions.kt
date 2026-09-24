package com.seamless.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive Dimensions System
 * Automatically adapts UI sizing based on screen size
 */

/**
 * Window size classes based on screen width
 */
enum class WindowSizeClass {
    COMPACT,   // < 360dp (small phones)
    MEDIUM,    // 360-600dp (most phones)
    EXPANDED   // > 600dp (large phones, tablets)
}

/**
 * Responsive dimension values that adapt to screen size
 */
data class Dimensions(
    // Padding values
    val screenPadding: Dp,
    val contentPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,
    
    // Component sizes
    val buttonHeight: Dp,
    val iconSize: Dp,
    val avatarSize: Dp,
    val cardCornerRadius: Dp,
    
    // Text scaling factor
    val textScaleFactor: Float
)

/**
 * Compact dimensions for small phones (< 360dp width)
 */
private val CompactDimensions = Dimensions(
    screenPadding = 12.dp,
    contentPadding = 8.dp,
    itemSpacing = 8.dp,
    sectionSpacing = 16.dp,
    buttonHeight = 48.dp,
    iconSize = 20.dp,
    avatarSize = 100.dp,
    cardCornerRadius = 12.dp,
    textScaleFactor = 0.9f
)

/**
 * Medium dimensions for regular phones (360-600dp width)
 */
private val MediumDimensions = Dimensions(
    screenPadding = 20.dp,
    contentPadding = 12.dp,
    itemSpacing = 12.dp,
    sectionSpacing = 20.dp,
    buttonHeight = 52.dp,
    iconSize = 24.dp,
    avatarSize = 120.dp,
    cardCornerRadius = 16.dp,
    textScaleFactor = 1f
)

/**
 * Expanded dimensions for large phones and tablets (> 600dp width)
 */
private val ExpandedDimensions = Dimensions(
    screenPadding = 24.dp,
    contentPadding = 16.dp,
    itemSpacing = 16.dp,
    sectionSpacing = 24.dp,
    buttonHeight = 56.dp,
    iconSize = 28.dp,
    avatarSize = 140.dp,
    cardCornerRadius = 20.dp,
    textScaleFactor = 1.1f
)

/**
 * CompositionLocal for accessing dimensions throughout the UI
 */
val LocalDimensions = compositionLocalOf { MediumDimensions }

/**
 * Get current window size class based on screen width
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp < 360 -> WindowSizeClass.COMPACT
            configuration.screenWidthDp < 600 -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }
    }
}

/**
 * Get responsive dimensions based on current screen size
 */
@Composable
fun rememberDimensions(): Dimensions {
    val windowSizeClass = rememberWindowSizeClass()
    return remember(windowSizeClass) {
        when (windowSizeClass) {
            WindowSizeClass.COMPACT -> CompactDimensions
            WindowSizeClass.MEDIUM -> MediumDimensions
            WindowSizeClass.EXPANDED -> ExpandedDimensions
        }
    }
}

/**
 * Calculate responsive horizontal padding that scales with screen width
 * Uses a percentage of screen width with min/max bounds
 */
@Composable
fun responsiveHorizontalPadding(
    minPadding: Dp = 12.dp,
    maxPadding: Dp = 32.dp,
    percentage: Float = 0.05f // 5% of screen width
): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val calculatedPadding = screenWidth * percentage
    
    return calculatedPadding.coerceIn(minPadding, maxPadding)
}

/**
 * Calculate responsive size that maintains aspect on different screens
 */
@Composable
fun responsiveSize(
    baseSize: Dp,
    minSize: Dp = baseSize * 0.7f,
    maxSize: Dp = baseSize * 1.3f
): Dp {
    val windowSizeClass = rememberWindowSizeClass()
    return when (windowSizeClass) {
        WindowSizeClass.COMPACT -> (baseSize * 0.8f).coerceIn(minSize, maxSize)
        WindowSizeClass.MEDIUM -> baseSize.coerceIn(minSize, maxSize)
        WindowSizeClass.EXPANDED -> (baseSize * 1.15f).coerceIn(minSize, maxSize)
    }
}

/**
 * Get the maximum content width (useful for tablets to prevent overly wide content)
 */
@Composable
fun maxContentWidth(): Dp {
    val configuration = LocalConfiguration.current
    return minOf(configuration.screenWidthDp.dp, 600.dp)
}
