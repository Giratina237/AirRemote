package com.atharok.btremote.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GyroscopeSensor(
    private val sensorManager: SensorManager,
    displayManager: DisplayManager
): SensorEventListener {

    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotation: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)

    /** Pointing direction of the phone's top edge: (yaw rad, + = right; pitch rad, + = up; timestamp ns). */
    var onOrientation: ((Float, Float, Long) -> Unit)? = null
    private val display: Display? = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    private val _gyroscopePositionsState: MutableStateFlow<Triple<Float, Float, Float>> = MutableStateFlow(Triple(0f, 0f, 0f))
    val gyroscopePositionsState: StateFlow<Triple<Float, Float, Float>> = _gyroscopePositionsState.asStateFlow()

    /**
     * Called for every gyroscope sample (x, y, z in rad/s, timestamp in ns), on the main thread.
     * The air mouse uses this instead of the StateFlow: a StateFlow collected by Compose coalesces
     * samples, which drops movement and makes the pointer stutter.
     */
    var onSample: ((Float, Float, Float, Long) -> Unit)? = null

    fun startListening() {
        gyroscope?.also {
            sensorManager.registerListener(this, it, SAMPLING_PERIOD_US)
        }
        rotation?.also {
            sensorManager.registerListener(this, it, SAMPLING_PERIOD_US)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun getDisplayRotation(): Int = display?.rotation ?: Surface.ROTATION_0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            val cb = onOrientation ?: return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            // world direction of the device +Y axis (top edge) = second column of R
            val vx = rotationMatrix[1]; val vy = rotationMatrix[4]; val vz = rotationMatrix[7]
            val yaw = kotlin.math.atan2(vx, vy)
            val pitch = kotlin.math.asin(vz.coerceIn(-1f, 1f))
            cb(yaw, pitch, event.timestamp)
            return
        }
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            onSample?.invoke(event.values[0], event.values[1], event.values[2], event.timestamp)
            _gyroscopePositionsState.value = Triple(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val SAMPLING_PERIOD_US = 5000   // 200 Hz; the report sender batches sub-pixel motion
    }
}
