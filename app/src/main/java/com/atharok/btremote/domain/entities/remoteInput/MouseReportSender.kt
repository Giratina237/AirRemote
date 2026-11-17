package com.atharok.btremote.domain.entities.remoteInput

import kotlin.math.roundToInt

class MouseReportSender(
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
    private val sendReport: (ByteArray) -> Boolean
) {
    private var buttons: Byte = MouseAction.NONE.byte
    private var pendingX = 0f
    private var pendingY = 0f
    private var pendingWheel = 0f

    // ---- Air mouse click stabilisation ----
    // Pressing a button (on screen, on the touchpad or a volume key) jolts the phone, and the gyroscope
    // turns that jolt into pointer movement, so the click lands next to the target. To prevent that:
    //  - motion from the air mouse is dropped for CLICK_FREEZE_MS after every button change,
    //  - when a button goes down, the air-mouse motion of the last REWIND_MS is undone first,
    //  - while motionPaused is set (a finger on the touchpad), air-mouse motion is ignored.
    private var lastButtonChangeMs = -CLICK_FREEZE_MS
    private val recentMotion = ArrayDeque<Motion>()
    var motionPaused: Boolean = false
        set(value) {
            field = value
            recentMotion.clear()
        }

    private class Motion(val timeMs: Long, val x: Float, val y: Float)

    fun send(action: MouseAction, x: Float, y: Float, wheel: Float) {
        if (action.byte != buttons) {
            val t = now()
            if (action != MouseAction.NONE && buttons == MouseAction.NONE.byte) rewindRecentMotion(t)
            lastButtonChangeMs = t
            recentMotion.clear()
        }
        sendRaw(action.byte, x, y, wheel)
    }

    /** Pointer movement from the air mouse (gyroscope). */
    fun sendMotion(x: Float, y: Float) {
        if (motionPaused) return
        val t = now()
        if (t - lastButtonChangeMs < AirMouseTuning.clickFreezeMs) return
        recentMotion.addLast(Motion(t, x, y))
        while (recentMotion.isNotEmpty() && recentMotion.first().timeMs < t - AirMouseTuning.rewindMs) recentMotion.removeFirst()
        sendRaw(buttons, x, y, 0f)
    }

    private fun rewindRecentMotion(t: Long) {
        var x = 0f
        var y = 0f
        for (m in recentMotion) if (m.timeMs >= t - AirMouseTuning.rewindMs) { x += m.x; y += m.y }
        // Folded into the same report as the button press: the host moves the pointer back, then clicks.
        pendingX -= x
        pendingY -= y
    }

    private fun sendRaw(buttonsByte: Byte, x: Float, y: Float, wheel: Float) {
        // All three relative axes use signed 8-bit values in the HID descriptor.
        pendingX = (pendingX + x).coerceIn(-127f, 127f)
        pendingY = (pendingY + y).coerceIn(-127f, 127f)
        pendingWheel = (pendingWheel + wheel).coerceIn(-127f, 127f)
        val dx = pendingX.roundToInt()
        val dy = pendingY.roundToInt()
        val scroll = pendingWheel.roundToInt()
        if (buttonsByte == buttons && dx == 0 && dy == 0 && scroll == 0) return

        if (sendReport(byteArrayOf(buttonsByte, dx.toByte(), dy.toByte(), scroll.toByte()))) {
            buttons = buttonsByte
            pendingX -= dx
            pendingY -= dy
            pendingWheel -= scroll
        } else {
            // Do not replay movement collected while the host was unavailable.
            pendingX = 0f
            pendingY = 0f
            pendingWheel = 0f
        }
    }

    companion object {
        const val CLICK_FREEZE_MS = 160L
        const val REWIND_MS = 70L
    }
}
