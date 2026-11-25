package com.seamless.android.ui.call

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Multi-tier tactile haptic signature engine calibrated for Seamless calls.
 *
 * Uses Android LRA [VibrationEffect.Composition] primitives on API 30/31+ with
 * cached capability probing at construction (zero Binder IPC on touch), falls
 * back to amplitude-shaped waveform bursts on API 26–29, and triggers semantic
 * [HapticFeedbackConstants] on the host [View] as a universal anchor while
 * honoring system touch-haptic preferences and [VibrationAttributes.USAGE_TOUCH].
 */
class CallHapticsEngine(context: Context) {

    private val appContext: Context = context.applicationContext

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true
    private val hasAmplitude: Boolean = hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        runCatching { vibrator?.hasAmplitudeControl() == true }.getOrDefault(false)

    private val hasClick: Boolean
    private val hasTick: Boolean
    private val hasRise: Boolean
    private val hasFall: Boolean
    private val hasLowTick: Boolean
    private val hasThud: Boolean
    private val hasSpin: Boolean

    init {
        var click = false
        var tick = false
        var rise = false
        var fall = false
        var lowTick = false
        var thud = false
        var spin = false

        if (hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val rSupport = vibrator?.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
                )
                if (rSupport != null && rSupport.size == 4) {
                    click = rSupport[0]
                    tick = rSupport[1]
                    rise = rSupport[2]
                    fall = rSupport[3]
                }
            }
        }
        if (hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val sSupport = vibrator?.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_SPIN
                )
                if (sSupport != null && sSupport.size == 3) {
                    lowTick = sSupport[0]
                    thud = sSupport[1]
                    spin = sSupport[2]
                }
            }
        }

        hasClick = click
        hasTick = tick
        hasRise = rise
        hasFall = fall
        hasLowTick = lowTick
        hasThud = thud
        hasSpin = spin
    }

    /** Subtle micro-tick right at finger touch-down (under Doherty threshold). */
    fun pressDown(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            when {
                hasLowTick -> addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.28f)
                hasTick -> addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.22f)
                else -> return@playComposition false
            }
            true
        }
        if (!played) {
            performViewFeedback(view, HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Crisp rising mechanical detent when engaging Camera, Speaker, Tor Strict, or expanding HUD. */
    fun toggleOn(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            if (hasRise && hasClick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.42f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.82f, 18)
                true
            } else if (hasClick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.78f)
                true
            } else {
                false
            }
        }
        if (!played) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                performViewFeedback(view, HapticFeedbackConstants.TOGGLE_ON)
            } else if (!playPredefinedOrWaveform(
                    predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1,
                    timings = longArrayOf(0L, 12L),
                    amplitudes = intArrayOf(0, 175)
                )
            ) {
                performViewFeedback(view, HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    /** Soft settling release when disengaging a tool or collapsing panels. */
    fun toggleOff(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            if (hasLowTick && hasFall) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.50f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.32f, 14)
                true
            } else if (hasLowTick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.42f)
                true
            } else if (hasTick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f)
                true
            } else {
                false
            }
        }
        if (!played) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                performViewFeedback(view, HapticFeedbackConstants.TOGGLE_OFF)
            } else if (!playPredefinedOrWaveform(
                    predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1,
                    timings = longArrayOf(0L, 9L),
                    amplitudes = intArrayOf(0, 95)
                )
            ) {
                performViewFeedback(view, HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    /** Distinct downward double privacy lock when muting the microphone (eyes-free differentiation). */
    fun muteEngaged(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            if (hasClick && hasLowTick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.78f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.45f, 52)
                true
            } else if (hasClick && hasFall) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.42f, 52)
                true
            } else {
                false
            }
        }
        if (!played && !playPredefinedOrWaveform(
                predefinedId = -1,
                timings = longArrayOf(0L, 14L, 50L, 10L),
                amplitudes = intArrayOf(0, 180, 0, 90)
            )
        ) {
            performViewFeedback(view, HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Bright upward double open when unmuting the microphone. */
    fun unmuteLive(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            if (hasLowTick && hasClick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.38f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.84f, 38)
                true
            } else if (hasRise && hasClick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.40f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.84f, 38)
                true
            } else {
                false
            }
        }
        if (!played && !playPredefinedOrWaveform(
                predefinedId = -1,
                timings = longArrayOf(0L, 10L, 38L, 15L),
                amplitudes = intArrayOf(0, 95, 0, 195)
            )
        ) {
            performViewFeedback(view, HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Warm 3-step arrival bloom when an encrypted call transitions into ACTIVE media. */
    fun callConnected() {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            when {
                hasRise && hasClick && hasLowTick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.52f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.90f, 32)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.48f, 62)
                    true
                }
                hasClick && hasTick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f, 44)
                    true
                }
                else -> false
            }
        }
        if (!played) {
            playPredefinedOrWaveform(
                predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_DOUBLE_CLICK else -1,
                timings = longArrayOf(0L, 18L, 42L, 24L),
                amplitudes = intArrayOf(0, 125, 0, 220)
            )
        }
    }

    /** Weighty dignified closure signature on Hang Up / Decline / Call Ended. */
    fun callEnded() {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            when {
                hasThud && hasFall -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.85f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.38f, 68)
                    true
                }
                hasThud -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.82f)
                    true
                }
                hasClick && hasFall -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.82f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.40f, 64)
                    true
                }
                else -> false
            }
        }
        if (!played) {
            playPredefinedOrWaveform(
                predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_HEAVY_CLICK else -1,
                timings = longArrayOf(0L, 28L, 58L, 18L),
                amplitudes = intArrayOf(0, 210, 0, 85)
            )
        }
    }

    /** Magnetic detent lock on PiP corner snap or crossing swipe-down-to-minimize threshold. */
    fun magneticSnap(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            when {
                hasThud -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.52f)
                    true
                }
                hasClick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.62f)
                    true
                }
                else -> false
            }
        }
        if (!played) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                performViewFeedback(view, HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
            } else if (!playPredefinedOrWaveform(
                    predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_HEAVY_CLICK else -1,
                    timings = longArrayOf(0L, 14L),
                    amplitudes = intArrayOf(0, 155)
                )
            ) {
                performViewFeedback(view, HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /** Mechanical lens twist + click detent on camera switch or PiP view swap. */
    fun cameraFlip(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            when {
                hasSpin && hasTick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.42f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.65f, 26)
                    true
                }
                hasLowTick && hasClick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.35f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.68f, 32)
                    true
                }
                else -> false
            }
        }
        if (!played) {
            performViewFeedback(view, HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Double cryptographic seal when confirming SAS emojis match. */
    fun sasVerified(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            if (hasClick) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.72f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 64)
                true
            } else {
                false
            }
        }
        if (!played) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                performViewFeedback(view, HapticFeedbackConstants.CONFIRM)
            } else {
                playPredefinedOrWaveform(
                    predefinedId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_DOUBLE_CLICK else -1,
                    timings = longArrayOf(0L, 14L, 58L, 20L),
                    amplitudes = intArrayOf(0, 165, 0, 235)
                )
            }
        }
    }

    /** Twin quiet telemetry ticks when re-syncing the Tor / Direct P2P route. */
    fun reSyncPulse(view: View? = null) {
        if (!touchHapticsAllowed()) return
        val played = playComposition {
            when {
                hasLowTick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.48f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.48f, 76)
                    true
                }
                hasTick -> {
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f)
                    addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f, 76)
                    true
                }
                else -> false
            }
        }
        if (!played) {
            performViewFeedback(view, HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun touchHapticsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // VibrationAttributes.USAGE_TOUCH enforces system touch-haptics settings natively on API 33+.
            return true
        }
        return runCatching {
            @Suppress("DEPRECATION")
            Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        }.getOrDefault(true)
    }

    private inline fun playComposition(build: VibrationEffect.Composition.() -> Boolean): Boolean {
        if (!hasVibrator || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val vib = vibrator ?: return false
        val comp = VibrationEffect.startComposition()
        if (!comp.build()) return false
        return runCatching {
            vibrateEffect(vib, comp.compose())
            true
        }.getOrDefault(false)
    }

    private fun playPredefinedOrWaveform(
        predefinedId: Int,
        timings: LongArray,
        amplitudes: IntArray
    ): Boolean {
        if (!hasVibrator) return false
        val vib = vibrator ?: return false
        return runCatching {
            val effect = when {
                predefinedId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    VibrationEffect.createPredefined(predefinedId)
                hasAmplitude && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    VibrationEffect.createWaveform(timings, -1)
                else -> return false
            }
            vibrateEffect(vib, effect)
            true
        }.getOrDefault(false)
    }

    private fun vibrateEffect(vib: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            vib.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(effect)
        }
    }

    private fun performViewFeedback(view: View?, constant: Int) {
        if (view != null) {
            runCatching { view.performHapticFeedback(constant) }
        }
    }
}

@Composable
fun rememberCallHaptics(): CallHapticsEngine {
    val context = LocalContext.current
    return remember(context.applicationContext) { CallHapticsEngine(context) }
}
