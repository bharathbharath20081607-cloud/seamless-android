package com.seamless.android.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.core.content.getSystemService
import kotlin.math.sqrt

object DeviceUtils {

    /**
     * Determines if the current device is a tablet based on screen size and density.
     * Uses multiple criteria to accurately detect tablets vs phones.
     */
    fun isTablet(context: Context): Boolean {
        val windowManager = context.getSystemService<WindowManager>()
        
        val diagonalInches: Double
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            // Modern API for Android 11+
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val density = context.resources.displayMetrics
            
            val widthInches = bounds.width() / density.xdpi
            val heightInches = bounds.height() / density.ydpi
            diagonalInches = sqrt((widthInches * widthInches) + (heightInches * heightInches).toDouble())
        } else {
            // Fallback for older APIs
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            
            val widthInches = displayMetrics.widthPixels / displayMetrics.xdpi
            val heightInches = displayMetrics.heightPixels / displayMetrics.ydpi
            diagonalInches = sqrt((widthInches * widthInches) + (heightInches * heightInches).toDouble())
        }

        // Check if device has tablet configuration
        val configuration = context.resources.configuration
        val isLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val isXLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE

        // A device is considered a tablet if:
        // 1. Screen diagonal is 7 inches or larger, OR
        // 2. Configuration indicates large or xlarge screen, OR
        // 3. Smallest width is 600dp or more (sw600dp)
        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp

        return diagonalInches >= 7.0 || isLargeScreen || isXLargeScreen || smallestWidthDp >= 600
    }
}

