package com.seamless.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Seamless Premium Color Palette
 * Designed for high contrast, vibrancy, and depth.
 */

// Primary Brand Colors - Electric Teal & Cyan
val SeamlessTeal = Color(0xFF00E5FF)     // High-energy Cyan
val SeamlessCyan = Color(0xFF00B0FF)     // Deep Electric Blue
val SeamlessPrimaryDark = Color(0xFF0081CB) // Darker shade for depth

// Secondary/Accent Colors
val SeamlessPurple = Color(0xFFD500F9)   // Electric Purple for accents
val SeamlessPink = Color(0xFFFF4081)     // Vibrant Pink
val SeamlessOrange = Color(0xFFFF9100)   // Energetic Orange

// Semantic Colors
object SeamlessColors {
    // Status
    val Success = Color(0xFF00E676)      // Bright Green
    val Warning = Color(0xFFFFC400)      // Amber
    val Error = Color(0xFFFF1744)        // Vivid Red
    val Info = Color(0xFF2979FF)         // Bright Blue

    // Dark Theme Surfaces (Deep Blue-Grey scale)
    val DarkBackground = Color(0xFF050B14)       // Almost black, hint of blue
    val DarkSurface = Color(0xFF0F1724)          // Deep blue-grey
    val DarkSurfaceContainer = Color(0xFF1A2634) // Lighter container
    val DarkSurfaceBright = Color(0xFF253242)    // Highlighted surface
    val DarkOutline = Color(0xFF2F3E50)          // Subtle border

    // Light Theme Surfaces (Clean, crisp)
    val LightBackground = Color(0xFFF5F7FA)      // Very light cool grey
    val LightSurface = Color(0xFFFFFFFF)         // Pure white
    val LightSurfaceContainer = Color(0xFFEEF2F6) // Slight grey container
    val LightOutline = Color(0xFFE0E6EB)         // Subtle border

    // Text Colors
    val TextPrimaryDark = Color(0xFFFFFFFF)
    val TextSecondaryDark = Color(0xFF94A3B8)    // Slate grey
    val TextTertiaryDark = Color(0xFF64748B)

    val TextPrimaryLight = Color(0xFF0F1724)     // Deep blue-black
    val TextSecondaryLight = Color(0xFF64748B)   // Slate grey
    val TextTertiaryLight = Color(0xFF94A3B8)

    // Message Bubbles (Gradients will be handled in UI components, these are fallbacks)
    val BubbleSelfDark = Color(0xFF00B0FF)       // Electric Blue
    val BubbleOtherDark = Color(0xFF1A2634)      // Surface Container
    val BubbleSelfLight = Color(0xFFE1F5FE)      // Very light blue
    val BubbleOtherLight = Color(0xFFF1F5F9)     // Light grey

    // Peer Colors (Vibrant, Neon-ish for Dark Mode)
    val PeerColors = listOf(
        Color(0xFF00E676), // Green
        Color(0xFF2979FF), // Blue
        Color(0xFFFF9100), // Orange
        Color(0xFFD500F9), // Purple
        Color(0xFF00E5FF), // Cyan
        Color(0xFFFF1744), // Red
        Color(0xFFFFEA00), // Yellow
        Color(0xFFF50057)  // Pink
    )

    // Premium Gradients
    val BrandGradientStart = Color(0xFF00E5FF)
    val BrandGradientEnd = Color(0xFF2979FF)
    
    val DarkGradientBackgroundStart = Color(0xFF050B14)
    val DarkGradientBackgroundEnd = Color(0xFF0F1724)
    
    // Glassmorphism Colors (with alpha)
    val GlassSurfaceDark = Color(0xCC0F1724) // ~80% opacity for blur
    val GlassSurfaceLight = Color(0xCCFFFFFF)
    
    val GlassBorderDark = Color(0x33FFFFFF) // 20% white for border
    val GlassBorderLight = Color(0x33FFFFFF)

    // Animation & Effect Colors
    val ShimmerBase = Color(0xFF1A2634)
    val ShimmerHighlight = Color(0xFF253242)
    val GlowCyan = Color(0x6600E5FF) // Translucent cyan for glow
    val GlowPink = Color(0x66D500F9) // Translucent purple for glow
}

// Helper function to generate consistent color from string
fun generateColorFromString(input: String): Color {
    val hash = input.hashCode()
    val index = kotlin.math.abs(hash) % SeamlessColors.PeerColors.size
    return SeamlessColors.PeerColors[index]
}

// Helper function to get contrasting text color
fun getContrastingTextColor(backgroundColor: Color): Color {
    val luminance = (0.299 * backgroundColor.red + 
                     0.587 * backgroundColor.green + 
                     0.114 * backgroundColor.blue)
    return if (luminance > 0.5) Color.Black else Color.White
}
