package com.seamless.android.ui.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Lifecycle-aware low-pass gravity tilt sensor provider for real-time Liquid Glass specular
 * glints and ambient crown depth parallax.
 *
 * Returns a [State]<[Offset]> whose normalized coordinates `[-1f, 1f]` should be read strictly
 * inside draw-phase lambdas (`Canvas`, `drawWithContent`, or `graphicsLayer { }`) so parent
 * composables never recompose on 50 Hz sensor samples.
 */
@Composable
fun rememberTiltLightState(enabled: Boolean = true): State<Offset> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltState = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(context, lifecycleOwner, enabled) {
        if (!enabled) {
            tiltState.value = Offset.Zero
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || gravitySensor == null) {
            return@DisposableEffect onDispose {}
        }

        var smoothX = 0f
        var smoothY = 0f
        var registered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Map phone tilt around comfortable viewing angle (portrait resting tilt ~5.2 m/s^2 on Y).
                val normX = (-event.values[0] / 7.5f).coerceIn(-1f, 1f)
                val normY = ((event.values[1] - 5.2f) / 7.5f).coerceIn(-1f, 1f)
                val alpha = 0.14f
                smoothX += (normX - smoothX) * alpha
                smoothY += (normY - smoothY) * alpha
                tiltState.value = Offset(smoothX, smoothY)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun registerSensor() {
            if (!registered) {
                registered = runCatching {
                    sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
                }.getOrDefault(false)
            }
        }

        fun unregisterSensor() {
            if (registered) {
                runCatching { sensorManager.unregisterListener(listener) }
                registered = false
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerSensor()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerSensor()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> unregisterSensor()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterSensor()
        }
    }

    return tiltState
}
