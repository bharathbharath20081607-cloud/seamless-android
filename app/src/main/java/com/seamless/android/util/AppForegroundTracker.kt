package com.seamless.android.util

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide OS-truth foreground tracker backed by ActivityLifecycleCallbacks.
 * Prevents background-only system notifications across Bluetooth Mesh, Nostr,
 * Geohash, Tor P2P, and Tor Groups whenever the user has the app open and unlocked.
 */
object AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    private val initialized = AtomicBoolean(false)
    private val startedCount = AtomicInteger(0)
    private val foregroundListeners = CopyOnWriteArrayList<() -> Unit>()

    fun init(application: Application) {
        if (initialized.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    fun addOnEnterForegroundListener(listener: () -> Unit) {
        foregroundListeners.add(listener)
    }

    fun isAppInForeground(context: Context? = null): Boolean {
        if (startedCount.get() <= 0) return false
        if (context != null) {
            val locked = runCatching {
                (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
            }.getOrDefault(false)
            if (locked) return false
        }
        return true
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedCount.incrementAndGet() == 1) {
            for (listener in foregroundListeners) {
                runCatching { listener() }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount.updateAndGet { maxOf(0, it - 1) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
