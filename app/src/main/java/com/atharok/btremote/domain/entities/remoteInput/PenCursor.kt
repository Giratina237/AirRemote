package com.atharok.btremote.domain.entities.remoteInput

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The TV cursor, driven through the HID pen-tablet device: we always know exactly where it is.
 *
 * - [moveTo]: absolute position (the gyro air pointer).
 * - [moveBy]: relative movement (touchpad, relative air mouse), with the same mild acceleration the TV
 *   applies to a real mouse so the touchpad feels unchanged.
 * - [setTip]: left button = pen tip (press / drag / long press).
 * - [scrollBy]: smooth scrolling by dragging with the tip down, the way a finger scrolls a list. Long scrolls are
 *   split into strokes: before lifting, the pen holds still briefly so the lift doesn't fling the list.
 *
 * All calls on one thread; [tick] must be called regularly (finishes strokes when scrolling stops).
 */
class PenCursor(
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
    /** x, y in screen pixels, tip, in range */
    private val sendPen: (Float, Float, Boolean, Boolean) -> Unit
) {
    var screenW = 1920f
    var screenH = 1080f

    var x = 960f; private set
    var y = 540f; private set
    private var tip = false

    // relative acceleration
    private val recent = ArrayDeque<FloatArray>()   // [timeMs, dx, dy]

    // drag scrolling
    private enum class Stroke { NONE, DRAG, SETTLE }
    private var stroke = Stroke.NONE
    private var strokeY = 0f            // pen y during the stroke (x stays at the cursor)
    private var settleUntilMs = 0L
    private var lastScrollMs = 0L
    private var lastSentX = Float.NaN
    private var lastSentY = Float.NaN
    private var lastSentTip = false

    val scrolling get() = stroke != Stroke.NONE

    fun moveTo(px: Float, py: Float) {
        x = px.coerceIn(0f, screenW - 1f)
        y = py.coerceIn(0f, screenH - 1f)
        if (stroke == Stroke.NONE) emit()
    }

    fun moveBy(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val t = now()
        while (recent.isNotEmpty() && recent.first()[0] < t - SPEED_WINDOW_MS) recent.removeFirst()
        recent.addLast(floatArrayOf(t.toFloat(), dx, dy))
        var sx = 0f; var sy = 0f
        for (r in recent) { sx += r[1]; sy += r[2] }
        val f = accel(hypot(sx, sy) / (SPEED_WINDOW_MS / 1000f))
        moveTo(x + dx * f, y + dy * f)
    }

    fun setTip(down: Boolean) {
        if (stroke != Stroke.NONE) finishStrokeNow()
        tip = down
        emit(force = true)
    }

    /** Continuous scroll: [dy] pixels of finger-drag (+ = content moves down, i.e. scrolling up). */
    fun scrollBy(dy: Float) {
        if (tip || dy == 0f) return
        val t = now()
        lastScrollMs = t
        when (stroke) {
            Stroke.NONE -> {
                // start a stroke where there's room to drag in this direction
                strokeY = if (dy > 0f) screenH * 0.25f else screenH * 0.75f
                stroke = Stroke.DRAG
                sendRaw(x, strokeY, false)      // hover to the start first
                sendRaw(x, strokeY, true)
            }
            Stroke.DRAG -> {
                strokeY += dy
                sendRaw(x, strokeY, true)
                if (strokeY < screenH * 0.08f || strokeY > screenH * 0.92f) {
                    stroke = Stroke.SETTLE; settleUntilMs = t + SETTLE_MS
                }
            }
            Stroke.SETTLE -> tick()
        }
    }

    /** Call regularly: lifts the pen once scrolling has stopped (after holding still, so there's no fling). */
    fun tick() {
        val t = now()
        when (stroke) {
            Stroke.DRAG -> if (t - lastScrollMs > IDLE_MS) { stroke = Stroke.SETTLE; settleUntilMs = t + SETTLE_MS }
            Stroke.SETTLE -> if (t >= settleUntilMs) finishStrokeNow()
            Stroke.NONE -> {}
        }
    }

    private fun finishStrokeNow() {
        sendRaw(x, strokeY, false)      // lift where it is
        stroke = Stroke.NONE
        emit(force = true)              // and hover back to the cursor
    }

    private fun emit(force: Boolean = false) {
        if (!force && abs(x - lastSentX) < 0.25f && abs(y - lastSentY) < 0.25f && tip == lastSentTip) return
        sendRaw(x, y, tip)
    }

    private fun sendRaw(px: Float, py: Float, down: Boolean) {
        lastSentX = px; lastSentY = py; lastSentTip = down
        sendPen(px.coerceIn(0f, screenW - 1f), py.coerceIn(0f, screenH - 1f), down, true)
    }

    private fun accel(speed: Float): Float {
        val lo = AirMouseTuning.hostLowThreshold
        val hi = AirMouseTuning.hostHighThreshold
        val acc = AirMouseTuning.hostAcceleration
        return when {
            speed <= lo -> 1f
            speed >= hi -> acc
            else -> 1f + (speed - lo) / (hi - lo) * (acc - 1f)
        }
    }

    companion object {
        const val SPEED_WINDOW_MS = 80L
        const val IDLE_MS = 120L        // no scroll input this long = scrolling stopped
        const val SETTLE_MS = 80L       // hold still before lifting (the list's fling tracker sees zero speed)
    }
}
