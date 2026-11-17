package com.atharok.btremote.domain.entities.remoteInput

import kotlin.math.roundToInt

/** Keeps sub-pixel movement without sending reports that do not change the mouse state. */
class MouseReportSender(private val sendReport: (ByteArray) -> Boolean) {
    private var buttons: Byte = MouseAction.NONE.byte
    private var pendingX = 0f
    private var pendingY = 0f
    private var pendingWheel = 0f

    fun send(action: MouseAction, x: Float, y: Float, wheel: Float) {
        // All three relative axes use signed 8-bit values in the HID descriptor.
        pendingX = (pendingX + x).coerceIn(-127f, 127f)
        pendingY = (pendingY + y).coerceIn(-127f, 127f)
        pendingWheel = (pendingWheel + wheel).coerceIn(-127f, 127f)
        val dx = pendingX.roundToInt()
        val dy = pendingY.roundToInt()
        val scroll = pendingWheel.roundToInt()
        if (action.byte == buttons && dx == 0 && dy == 0 && scroll == 0) return

        if (sendReport(byteArrayOf(action.byte, dx.toByte(), dy.toByte(), scroll.toByte()))) {
            buttons = action.byte
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
}
