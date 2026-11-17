package com.atharok.btremote.ui.views.mouse

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    gyroscopeSensorViewModel: GyroscopeSensorViewModel = koinViewModel()
) {
    val filter = remember { AirMouseFilter() }
    val currentSpeed = rememberUpdatedState(mouseSpeed)
    val currentSend = rememberUpdatedState(sendMouseMotion)

    DisposableEffect(Unit) {
        filter.reset()
        gyroscopeSensorViewModel.setOnSample { x, y, z, timestampNs ->
            val (rateX, rateY) = screenRates(x, y, z, gyroscopeSensorViewModel.getDisplayRotation())
            val (dx, dy) = filter.step(rateX, rateY, timestampNs, currentSpeed.value)
            if (dx != 0f || dy != 0f) currentSend.value(dx, dy)
        }
        gyroscopeSensorViewModel.startListening()
        onDispose {
            gyroscopeSensorViewModel.stopListening()
            gyroscopeSensorViewModel.setOnSample(null)
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
