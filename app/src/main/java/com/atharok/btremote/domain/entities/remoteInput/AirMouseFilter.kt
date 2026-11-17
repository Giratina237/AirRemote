package com.atharok.btremote.domain.entities.remoteInput

import kotlin.math.PI
import kotlin.math.hypot

/**
 * Turns gyroscope angular rates into pointer movement for the air mouse.
 *
 * - Adaptive smoothing (a "one euro" style low-pass on the angular rate): heavy smoothing when the hand
 *   is nearly still (kills jitter), light smoothing when it moves fast (no lag on big sweeps).
 * - Soft dead zone: hand tremor below [DEAD_ZONE] rad/s never moves the pointer; between 1x and 3x the
 *   dead zone the gain ramps in, so there's no visible "jump" when movement starts.
 * - Mild acceleration: slow wrist motions are precise, quick flicks travel across the screen.
 * - Integrates rate x real sensor time step, so the pointer speed doesn't depend on the sensor rate.
 */
class AirMouseFilter {

    private var lastTimestampNs = 0L
    private var smoothX = 0f
    private var smoothY = 0f

    fun reset() {
        lastTimestampNs = 0L
        smoothX = 0f
        smoothY = 0f
    }

    /**
     * @param rateX angular rate that should move the pointer horizontally (rad/s, + = right)
     * @param rateY angular rate that should move the pointer vertically (rad/s, + = down)
     * @param timestampNs sensor event timestamp
     * @param speed user pointer speed setting (1 = default)
     * @return pointer movement in HID units (pixels)
     */
    fun step(rateX: Float, rateY: Float, timestampNs: Long, speed: Float): Pair<Float, Float> {
        val dt = if (lastTimestampNs == 0L) 0f else ((timestampNs - lastTimestampNs) / 1e9f).coerceIn(0f, MAX_DT)
        lastTimestampNs = timestampNs
        if (dt <= 0f) return 0f to 0f

        val rawMagnitude = hypot(rateX, rateY)
        val cutoffHz = MIN_CUTOFF_HZ + BETA * rawMagnitude
        val alpha = alpha(cutoffHz, dt)
        smoothX += alpha * (rateX - smoothX)
        smoothY += alpha * (rateY - smoothY)

        val magnitude = hypot(smoothX, smoothY)
        val keep = ((magnitude - DEAD_ZONE) / (2f * DEAD_ZONE)).coerceIn(0f, 1f)
        if (keep == 0f) return 0f to 0f

        val gain = PIXELS_PER_RADIAN * speed * (1f + ACCELERATION * magnitude) * keep
        return smoothX * gain * dt to smoothY * gain * dt
    }

    private fun alpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoffHz)
        return dt / (dt + tau)
    }

    companion object {
        const val DEAD_ZONE = 0.025f          // rad/s (~1.4 deg/s): typical resting hand tremor
        const val MIN_CUTOFF_HZ = 2.5f        // smoothing when nearly still
        const val BETA = 12f                  // how quickly smoothing lets go as speed rises
        const val PIXELS_PER_RADIAN = 900f    // base speed (speed setting 1)
        const val ACCELERATION = 0.6f         // extra gain per rad/s
        const val MAX_DT = 0.05f              // ignore gaps (sensor paused) longer than 50 ms
    }
}
