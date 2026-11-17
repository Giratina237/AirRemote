package com.atharok.btremote.domain.entities.remoteInput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AirMouseTest {
    private val reports = mutableListOf<ByteArray>()
    private var clock = 1_000L
    private val sender = MouseReportSender(now = { clock }) { reports.add(it); true }

    private fun movedX() = reports.sumOf { it[1].toInt() }

    @Test
    fun motionRightAfterAClickIsDropped() {
        sender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        clock += 50
        sender.sendMotion(10f, 0f)
        assertEquals(0, movedX())
        clock += MouseReportSender.CLICK_FREEZE_MS
        sender.sendMotion(10f, 0f)
        assertEquals(10, movedX())
    }

    @Test
    fun motionJustBeforeAClickIsUndoneInTheClickReport() {
        sender.sendMotion(6f, 0f)
        clock += 20
        sender.sendMotion(4f, 0f)
        clock += 20
        sender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        assertEquals(0, movedX())                      // pointer back where it was before the jolt
        assertEquals(1, reports.last()[0].toInt())     // and the click is in that same report
    }

    @Test
    fun olderMotionIsNotUndone() {
        sender.sendMotion(10f, 0f)
        clock += MouseReportSender.REWIND_MS + 50
        sender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        assertEquals(10, movedX())
    }

    @Test
    fun pausedMotionIsIgnoredButTouchpadStillWorks() {
        sender.motionPaused = true
        sender.sendMotion(10f, 0f)
        sender.send(MouseAction.NONE, 5f, 0f, 0f)
        assertEquals(5, movedX())
    }

    @Test
    fun motionKeepsAHeldButtonPressed() {
        sender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        clock += 500
        sender.sendMotion(10f, 0f)
        assertEquals(1, reports.last()[0].toInt())     // drag: button stays down while moving
    }

    @Test
    fun filterIgnoresTremorAndMovesOnRealMotion() {
        val f = AirMouseFilter()
        var t = 0L
        var tremor = 0f
        repeat(400) { i ->
            t += 5_000_000L
            tremor += abs(f.step(if (i % 2 == 0) 0.015f else -0.015f, 0.01f, t, 1f).first)
        }
        assertEquals(0f, tremor, 0.001f)
        var moved = 0f
        repeat(200) { t += 5_000_000L; moved += f.step(0.5f, 0f, t, 1f).first }   // 1 s at 0.5 rad/s
        assertTrue("moved $moved", moved in 300f..900f)
    }
}

class AirMouseScrollTest {
    private val reports = mutableListOf<ByteArray>()
    private var clock = 1_000L
    private val sender = MouseReportSender(now = { clock }) { reports.add(it); true }

    @Test
    fun scrollModeTurnsTiltIntoWheelAndKeepsThePointerStill() {
        sender.scrollMode = true
        clock += 500
        repeat(10) { sender.sendMotion(3f, -10f) }      // tilt up
        assertEquals(0, reports.sumOf { it[1].toInt() })
        assertEquals(0, reports.sumOf { it[2].toInt() })
        assertTrue(reports.sumOf { it[3].toInt() } > 0)  // scrolled up
        sender.scrollMode = false
        clock += 500
        reports.clear()
        sender.sendMotion(5f, 0f)
        assertEquals(5, reports.sumOf { it[1].toInt() })
    }
}
