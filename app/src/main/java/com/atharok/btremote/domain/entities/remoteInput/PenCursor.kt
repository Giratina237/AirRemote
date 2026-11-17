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
    private var strokeX = 0f            // pen position during the stroke
    private var strokeY = 0f
    private var settleUntilMs = 0L
    private var lastScrollMs = 0L
    private var lastSentX = Float.NaN
    private var lastSentY = Float.NaN
    private var lastSentTip = false

    val scrolling get() = stroke != Stroke.NONE

    // auto-hide: hand tremor keeps the pointer "moving", so the TV would never fade it on its own
    private var hidden = false
    private var anchorX = Float.NaN
    private var anchorY = Float.NaN
    private var stillSinceMs = now()

    fun moveTo(px: Float, py: Float) {
        x = px.coerceIn(0f, screenW - 1f)
        y = py.coerceIn(0f, screenH - 1f)
        val moved = anchorX.isNaN() || hypot(x - anchorX, y - anchorY) > (if (hidden) WAKE_PX else STILL_PX)
        if (moved) { anchorX = x; anchorY = y; stillSinceMs = now() }
        if (hidden) {
            if (!moved) return                      // stay hidden through tremor
            hidden = false
            if (stroke == Stroke.NONE) emit(force = true)
            return
        }
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
        hidden = false; stillSinceMs = now()
        tip = down
        emit(force = true)
    }

    /**
     * Continuous scroll by dragging with the pen: [dx], [dy] pixels of finger-drag
     * (+dy = content moves down, i.e. scrolling up; +dx = content moves right, i.e. scrolling left).
     */
    fun scrollBy(dx: Float, dy: Float) {
        if (tip || (dx == 0f && dy == 0f)) return
        val t = now()
        lastScrollMs = t
        hidden = false; stillSinceMs = t
        when (stroke) {
            Stroke.NONE -> {
                // start a stroke where there's room to drag in this direction
                strokeX = when { dx > 0f -> screenW * 0.25f; dx < 0f -> screenW * 0.75f; else -> x.coerceIn(screenW * 0.1f, screenW * 0.9f) }
                strokeY = when { dy > 0f -> screenH * 0.25f; dy < 0f -> screenH * 0.75f; else -> y.coerceIn(screenH * 0.1f, screenH * 0.9f) }
                stroke = Stroke.DRAG
                sendRaw(strokeX, strokeY, false)      // hover to the start first
                sendRaw(strokeX, strokeY, true)
            }
            Stroke.DRAG -> {
                strokeX += dx; strokeY += dy
                sendRaw(strokeX, strokeY, true)
                if (strokeX < screenW * 0.05f || strokeX > screenW * 0.95f ||
                    strokeY < screenH * 0.08f || strokeY > screenH * 0.92f) {
                    stroke = Stroke.SETTLE; settleUntilMs = t + SETTLE_MS
                }
            }
            Stroke.SETTLE -> tick()
        }
    }

    fun scrollBy(dy: Float) = scrollBy(0f, dy)

    /** Call regularly: lifts the pen once scrolling has stopped (after holding still, so there's no fling). */
    fun tick() {
        val t = now()
        when (stroke) {
            Stroke.DRAG -> if (t - lastScrollMs > IDLE_MS) { stroke = Stroke.SETTLE; settleUntilMs = t + SETTLE_MS }
            Stroke.SETTLE -> if (t >= settleUntilMs) finishStrokeNow()
            Stroke.NONE -> {
                val hideMs = AirMouseTuning.cursorHideMs
                if (!hidden && !tip && hideMs > 0f && t - stillSinceMs > hideMs) {
                    hidden = true
                    sendPen(x, y, false, false)     // pen out of range: the TV fades its cursor
                }
            }
        }
    }

    private fun finishStrokeNow() {
        sendRaw(strokeX, strokeY, false)      // lift where it is
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
        const val STILL_PX = 8f         // closer than this counts as still (tremor)
        const val WAKE_PX = 12f         // move this far to bring a hidden cursor back
        const val SETTLE_MS = 60L       // hold still before lifting (the list's fling tracker sees zero speed)
    }
}
