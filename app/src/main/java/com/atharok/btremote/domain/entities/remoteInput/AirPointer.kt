package com.atharok.btremote.domain.entities.remoteInput

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Absolute air pointer: the phone's pointing direction maps to a fixed spot on the TV.
 *
 * The Bluetooth mouse can only send relative movement, so this keeps a model of where the host pointer is
 * and sends the difference to the target. Android (the TV) accelerates fast mouse movement
 * (VelocityControl: x1 below [AirMouseTuning.hostLowThreshold] counts/s, rising to
 * x[AirMouseTuning.hostAcceleration] at [AirMouseTuning.hostHighThreshold]), so each movement is divided by the
 * factor the host will apply, using the same speed estimate. Any remaining error is cleared at the screen
 * edges (host and model both stop there) and by [recenter], which pins the pointer into a corner first.
 *
 * Neutral (centre of the TV) is the pointing direction at the last [recenter].
 */
class AirPointer(
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
    private val sendRaw: (Float, Float) -> Unit,
    private val sendWheel: (Int) -> Unit,
    /** The pen-tablet cursor (absolute). Null = emulate with relative mouse moves. */
    private val pen: PenCursor? = null
) {
    var screenW = 1920f
    var screenH = 1080f

    // model of the host pointer
    var modelX = 0f; private set
    var modelY = 0f; private set

    private var yaw0 = 0f
    private var pitch0 = 0f
    private var needRecenter = true
    private var recenterPitchToo = false   // CENTER button: the current direction (both axes) becomes the middle
    private var settleUntilMs = 0L   // after the corner slam, until the host's speed estimate has forgotten it

    // smoothed target
    private var fx = 0f
    private var fy = 0f
    private var lastSampleNs = 0L
    private val targetHistory = ArrayDeque<FloatArray>()   // [timeMs, x, y]

    // click stabilisation
    private var freezeUntilMs = 0L
    private var latchX = 0f
    private var latchY = 0f

    // host acceleration estimate: raw counts sent recently
    private val rawHistory = ArrayDeque<FloatArray>()      // [timeMs, dx, dy]

    // scroll
    private var scrollMode = false
    private var scrollPitch0 = Float.NaN
    private var scrollYaw0 = 0f
    private var scrollAxis = 0          // pen scroll locked to: 0 none yet, 1 vertical, 2 horizontal
    private var scrollAccumulator = 0f

    private val penMode get() = pen != null && AirMouseTuning.pen

    /**
     * Re-sync. [fromButton] (CENTER): wherever the phone points now becomes the middle of the TV.
     * Otherwise (opening the air mouse): the phone held level (pitch [AirMouseTuning.neutralPitchDeg]) is the
     * middle row, and the middle column is set the first time the phone is held in a pointing posture, not while
     * it's held upright to look at the screen.
     */
    fun recenter(fromButton: Boolean = false) { needRecenter = true; recenterPitchToo = fromButton }

    /** A mouse button went down or up: hold the pointer where it was just before the press jolted the phone. */
    fun onButtonChange() {
        val t = now()
        val past = targetHistory.lastOrNull { it[0] <= t - AirMouseTuning.rewindMs } ?: targetHistory.firstOrNull()
        latchX = past?.get(1) ?: fx
        latchY = past?.get(2) ?: fy
        freezeUntilMs = t + AirMouseTuning.clickFreezeMs
    }

    fun setScroll(on: Boolean) {
        scrollMode = on
        scrollPitch0 = Float.NaN
        scrollAxis = 0
        scrollAccumulator = 0f
        freezeUntilMs = now() + AirMouseTuning.clickFreezeMs
        latchX = if (penMode) fx else modelX; latchY = if (penMode) fy else modelY
    }

    /**
     * @param yaw heading of the phone's pointing axis (rad, + = turning right)
     * @param pitch elevation of the pointing axis (rad, + = up)
     * @param speed user pointer-speed setting (bigger = less turning for the same distance)
     */
    fun onOrientation(yaw: Float, pitch: Float, timestampNs: Long, speed: Float) {
        val t = now()
        val dt = if (lastSampleNs == 0L) 0f else ((timestampNs - lastSampleNs) / 1e9f).coerceIn(0f, 0.05f)
        lastSampleNs = timestampNs

        if (needRecenter) {
            val levelPitch = AirMouseTuning.neutralPitchDeg * PI.toFloat() / 180f
            if (!recenterPitchToo && abs(pitch - levelPitch) > POINTING_POSTURE_RAD) return   // not pointing yet
            needRecenter = false
            yaw0 = yaw
            pitch0 = if (recenterPitchToo) pitch else levelPitch
            targetHistory.clear()
            freezeUntilMs = 0L
            if (penMode) {
                // the TV places the cursor itself: nothing to re-sync, just start in the middle
                fx = screenW / 2f; fy = screenH / 2f
                settleUntilMs = 0L
            } else {
                // pin the host pointer into the top-left corner, wherever it was, then glide to the centre
                repeat(24) { sendRaw(-127f, -127f) }
                rawHistory.clear()
                modelX = 0f; modelY = 0f
                fx = 0f; fy = 0f
                settleUntilMs = t + 2 * SPEED_WINDOW_MS
            }
        }
        if (t < settleUntilMs) return

        val pxPerRad = screenW / (AirMouseTuning.degreesPerScreenWidth / speed.coerceAtLeast(0.1f) * PI.toFloat() / 180f)
        val tx = (screenW / 2f + wrap(yaw - yaw0) * pxPerRad).coerceIn(0f, screenW - 1f)
        val ty = (screenH / 2f - (pitch - pitch0) * pxPerRad).coerceIn(0f, screenH - 1f)

        if (scrollMode) {
            scroll(yaw, pitch, dt)
            return
        }

        // adaptive smoothing (one euro): steady when still, responsive when sweeping
        if (dt > 0f) {
            val v = hypot(tx - fx, ty - fy) / dt.coerceAtLeast(1e-3f)
            val cutoff = AirMouseTuning.pointerMinCutoffHz + AirMouseTuning.pointerBeta * v
            val a = alpha(cutoff, dt)
            fx += a * (tx - fx); fy += a * (ty - fy)
        }
        targetHistory.addLast(floatArrayOf(t.toFloat(), fx, fy))
        while (targetHistory.size > 2 && targetHistory.first()[0] < t - 400) targetHistory.removeFirst()

        val (gx, gy) = if (t < freezeUntilMs) latchX to latchY else fx to fy
        if (penMode) pen!!.moveTo(gx, gy) else moveModelTo(gx, gy, t)
    }

    private fun moveModelTo(gx: Float, gy: Float, t: Long) {
        while (rawHistory.isNotEmpty() && rawHistory.first()[0] < t - SPEED_WINDOW_MS) rawHistory.removeFirst()
        var sx = 0f; var sy = 0f
        for (r in rawHistory) { sx += r[1]; sy += r[2] }
        val dx = gx - modelX
        val dy = gy - modelY
        var rx = 0f; var ry = 0f
        if (abs(dx) >= 0.5f || abs(dy) >= 0.5f) {
            // the host moves raw * f(speed incl. this report); find the share k of the distance to send raw so that
            // k * f(k) = 1 (monotonic in k: bisect), capped at one report's worth
            var lo = 1f / AirMouseTuning.hostAcceleration.coerceAtLeast(1f); var hi = 1f
            repeat(18) {
                val k = (lo + hi) / 2f
                if (k * factorFor(sx + k * dx, sy + k * dy) > 1f) hi = k else lo = k
            }
            rx = dx * lo; ry = dy * lo
            val m = maxOf(abs(rx), abs(ry))
            if (m > MAX_RAW) { rx *= MAX_RAW / m; ry *= MAX_RAW / m }
        }
        // at an edge, keep pushing into it: the host stops there too, which clears any accumulated error
        var px = 0f; var py = 0f
        if (gx <= 0.5f) px = -EDGE_PUSH else if (gx >= screenW - 1.5f) px = EDGE_PUSH
        if (gy <= 0.5f) py = -EDGE_PUSH else if (gy >= screenH - 1.5f) py = EDGE_PUSH
        if (rx == 0f && ry == 0f && px == 0f && py == 0f) return
        val f = factorFor(sx + rx, sy + ry)
        sendRaw(rx + px, ry + py)
        rawHistory.addLast(floatArrayOf(t.toFloat(), rx + px, ry + py))
        modelX = if (px != 0f) gx.coerceIn(0f, screenW - 1f) else (modelX + rx * f).coerceIn(0f, screenW - 1f)
        modelY = if (py != 0f) gy.coerceIn(0f, screenH - 1f) else (modelY + ry * f).coerceIn(0f, screenH - 1f)
    }

    private fun factorFor(rawX: Float, rawY: Float) = hostFactor(hypot(rawX, rawY) / (SPEED_WINDOW_MS / 1000f))

    /** Joystick scroll: the angle when scrolling started is neutral; the further from it, the faster. */
    private fun scroll(yaw: Float, pitch: Float, dt: Float) {
        if (now() < freezeUntilMs) return                       // pressing the button jolts the phone
        if (scrollPitch0.isNaN()) { scrollPitch0 = pitch; scrollYaw0 = yaw; return }
        if (dt <= 0f) return
        val invert = if (AirMouseTuning.invertScroll) -1f else 1f
        if (penMode) {
            val vAngle = pitch - scrollPitch0                   // + = tilted up = scroll up
            val hAngle = wrap(yaw - scrollYaw0)                 // + = turned right = scroll right
            val vEx = (abs(vAngle) - AirMouseTuning.scrollDeadZoneRad).coerceAtLeast(0f)
            val hEx = (abs(hAngle) - AirMouseTuning.scrollDeadZoneRad).coerceAtLeast(0f)
            // one direction at a time: whichever leaves the dead zone first, until back at neutral
            if (vEx == 0f && hEx == 0f) { scrollAxis = 0; return }
            if (scrollAxis == 0) scrollAxis = if (vEx >= hEx) 1 else 2
            // finger-drag: scrolling up / right = dragging down / left
            if (scrollAxis == 1) pen!!.scrollBy(0f, sign(vAngle) * invert * dragSpeed(vEx) * dt)
            else pen!!.scrollBy(-sign(hAngle) * invert * dragSpeed(hEx) * dt, 0f)
            return
        }
        val v = rate(pitch - scrollPitch0) * invert             // + = tilted up = scroll up (notches/s)
        scrollAccumulator += v * dt
        val steps = scrollAccumulator.toInt()
        if (steps != 0) { scrollAccumulator -= steps; sendWheel(steps) }
    }

    private fun dragSpeed(excess: Float) =
        (AirMouseTuning.scrollPxPerRad * excess + AirMouseTuning.scrollPxPerRad2 * excess * excess)
            .coerceAtMost(AirMouseTuning.scrollMaxPxPerSec)

    private fun sign(a: Float) = if (a > 0f) 1f else -1f

    /** Signed notches/s for an angle from neutral: dead zone, then growing gently, capped. */
    private fun rate(angle: Float): Float {
        val excess = (abs(angle) - AirMouseTuning.scrollDeadZoneRad).coerceAtLeast(0f)
        if (excess == 0f) return 0f
        val r = (AirMouseTuning.scrollRate * excess * (1f + AirMouseTuning.scrollCurve * excess))
            .coerceAtMost(AirMouseTuning.scrollMaxRate)
        return if (angle > 0f) r else -r
    }

    private fun hostFactor(rawSpeed: Float): Float {
        val lo = AirMouseTuning.hostLowThreshold
        val hi = AirMouseTuning.hostHighThreshold
        val acc = AirMouseTuning.hostAcceleration
        return when {
            rawSpeed <= lo -> 1f
            rawSpeed >= hi -> acc
            else -> 1f + (rawSpeed - lo) / (hi - lo) * (acc - 1f)
        }
    }

    private fun alpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoffHz)
        return dt / (dt + tau)
    }

    private fun wrap(a: Float): Float {
        var x = a
        while (x > PI) x -= (2 * PI).toFloat()
        while (x < -PI) x += (2 * PI).toFloat()
        return x
    }

    companion object {
        const val SPEED_WINDOW_MS = 80L
        const val MAX_RAW = 120f
        const val EDGE_PUSH = 6f
        val POINTING_POSTURE_RAD = (30 * PI / 180).toFloat()
    }
}
