package com.seamless.android.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seamless.android.net.TorMessageNotifier

/**
 * Diagnostics and 1-tap system-settings helpers for Tor P2P background reliability
 * and Android 13–16 / HyperOS / OEM notification delivery.
 */
object TorReliabilityHelper {

    private val OEM_AUTOSTART_COMPONENTS = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
    )

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)

    fun isTorChannelAudible(context: Context): Boolean {
        if (!hasNotificationPermission(context) || !areNotificationsEnabled(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
        if (nm.getNotificationChannel(TorMessageNotifier.CHANNEL_ID) == null) {
            TorMessageNotifier(context.applicationContext).ensureChannelsCreated()
        }
        val channel = nm.getNotificationChannel(TorMessageNotifier.CHANNEL_ID) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    fun isOemBackgroundRestrictedDevice(): Boolean {
        val mfg = Build.MANUFACTURER.lowercase()
        return mfg.contains("xiaomi") ||
            mfg.contains("redmi") ||
            mfg.contains("poco") ||
            mfg.contains("oppo") ||
            mfg.contains("realme") ||
            mfg.contains("oneplus") ||
            mfg.contains("vivo") ||
            mfg.contains("iqoo") ||
            mfg.contains("huawei") ||
            mfg.contains("honor")
    }

    fun openTorNotificationSettings(context: Context) {
        TorMessageNotifier(context.applicationContext).ensureChannelsCreated()
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && areNotificationsEnabled(context) && hasNotificationPermission(context)) {
                add(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, TorMessageNotifier.CHANNEL_ID)
                    }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
            add(appDetailsIntent(context))
        }
        launchFirstResolvable(context, intents)
    }

    @SuppressLint("BatteryLife")
    fun openBackgroundReliabilitySettings(context: Context) {
        val intents = buildList {
            if (!isBatteryOptimizationIgnored(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
            for (component in OEM_AUTOSTART_COMPONENTS) {
                add(Intent().apply { this.component = component })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            add(appDetailsIntent(context))
        }
        launchFirstResolvable(context, intents)
    }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    private fun launchFirstResolvable(context: Context, intents: List<Intent>): Boolean {
        val pm = context.packageManager
        for (intent in intents) {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = runCatching { pm.resolveActivity(intent, 0) != null }.getOrDefault(false)
            if (!resolved) continue
            val launched = runCatching { context.startActivity(intent); true }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }
}
