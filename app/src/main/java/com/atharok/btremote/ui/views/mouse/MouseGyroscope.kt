package com.atharok.btremote.ui.views.mouse

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.atharok.btremote.domain.entities.remoteInput.AirMouseTuning
import com.atharok.btremote.domain.entities.remoteInput.AirMouseFilter
import com.atharok.btremote.presentation.viewmodel.GyroscopeSensorViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Air mouse: every gyroscope sample goes through [AirMouseFilter] (smoothing, dead zone, acceleration)
 * and straight to the HID sender, without going through recomposition.
 */
@Composable
fun MouseGyroscope(
    mouseSpeed: Float,
    sendMouseMotion: (Float, Float) -> Unit,
    sendMouseTilt: (Float, Float) -> Unit = { _, _ -> },
    sendMouseOrientation: ((Float, Float, Long, Float) -> Unit)? = null,
    recenter: () -> Unit = {},
    gyroscopeSensorViewModel: GyroscopeSensorViewModel = koinViewModel()
) {
    val filter = remember { AirMouseFilter() }
    val tuningDir = LocalContext.current.getExternalFilesDir(null)
    val currentSpeed = rememberUpdatedState(mouseSpeed)
    val currentSend = rememberUpdatedState(sendMouseMotion)
    val currentTilt = rememberUpdatedState(sendMouseTilt)
    val currentOrientation = rememberUpdatedState(sendMouseOrientation)
    val lastNs = remember { longArrayOf(0L) }

    DisposableEffect(Unit) {
        filter.reset()
        lastNs[0] = 0L
        gyroscopeSensorViewModel.setOnSample { x, y, z, timestampNs ->
            AirMouseTuning.reloadIfChanged(tuningDir, timestampNs / 1_000_000L)
            val (rateX, rateY) = screenRates(x, y, z, gyroscopeSensorViewModel.getDisplayRotation())
            val dt = if (lastNs[0] == 0L) 0f else ((timestampNs - lastNs[0]) / 1e9f).coerceIn(0f, 0.05f)
            lastNs[0] = timestampNs
            if (AirMouseTuning.absolute && currentOrientation.value != null) return@setOnSample
            currentTilt.value(rateY, dt)
            val (dx, dy) = filter.step(rateX, rateY, timestampNs, currentSpeed.value)
            if (dx != 0f || dy != 0f) currentSend.value(dx, dy)
        }
        gyroscopeSensorViewModel.setOnOrientation { yaw, pitch, timestampNs ->
            if (AirMouseTuning.absolute) currentOrientation.value?.invoke(yaw, pitch, timestampNs, currentSpeed.value)
        }
        recenter()
        gyroscopeSensorViewModel.startListening()
        onDispose {
            gyroscopeSensorViewModel.stopListening()
            gyroscopeSensorViewModel.setOnSample(null)
            gyroscopeSensorViewModel.setOnOrientation(null)
        }
    }
}

/** Device gyro axes -> (horizontal, vertical) pointer rates for the current screen rotation. */
private fun screenRates(x: Float, y: Float, z: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    Surface.ROTATION_0 -> -z to -x
    Surface.ROTATION_180 -> -z to x
    Surface.ROTATION_270 -> -z to -y
    Surface.ROTATION_90 -> -z to y
    else -> 0f to 0f
}
