package com.seamless.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.seamless.android.net.TorMode
import com.seamless.android.net.TorPreferenceManager
import com.seamless.android.service.TorForegroundService

/**
 * BootCompletedReceiver - Auto-restart Tor after device reboot.
 *
 * If the user had Tor enabled before the device was rebooted,
 * this receiver starts the TorForegroundService which bootstraps
 * the full Tor stack (Arti SOCKS, C-Tor daemon, hidden service,
 * messaging service, BSP sync engine).
 *
 * Registered in AndroidManifest.xml with RECEIVE_BOOT_COMPLETED.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "📱 Device boot completed — checking Tor state")

        try {
            TorPreferenceManager.init(context)
            val savedMode = TorPreferenceManager.get(context)

            if (savedMode == TorMode.ON) {
                Log.i(TAG, "🧅 Tor was enabled before reboot — auto-starting TorForegroundService")
                TorForegroundService.start(context)
            } else {
                Log.d(TAG, "Tor was disabled — skipping auto-start")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check/restore Tor state: ${e.message}")
        }
    }
}
