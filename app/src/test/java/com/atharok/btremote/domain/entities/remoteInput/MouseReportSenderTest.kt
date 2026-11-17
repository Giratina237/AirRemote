package com.atharok.btremote.domain.entities.remoteInput

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseReportSenderTest {
    private val reports = mutableListOf<ByteArray>()
    private val sender = MouseReportSender { reports.add(it); true }

    @Test
    fun stationaryEventsDoNotSendReports() {
        repeat(100) { sender.send(MouseAction.NONE, 0f, 0f, 0f) }
        assertTrue(reports.isEmpty())
    }

    @Test
    fun smallMovementsAccumulateInsteadOfBeingLost() {
        repeat(10) { sender.send(MouseAction.NONE, 0.2f, -0.2f, 0f) }
        assertEquals(2, reports.sumOf { it[1].toInt() })
        assertEquals(-2, reports.sumOf { it[2].toInt() })
        assertEquals(2, reports.size)
    }

    @Test
    fun oppositeFractionalMovementsCancel() {
        sender.send(MouseAction.NONE, 0.2f, -0.2f, 0f)
        sender.send(MouseAction.NONE, -0.2f, 0.2f, 0f)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun stationaryClicksAndReleasesAreBothSent() {
        sender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        sender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        sender.send(MouseAction.NONE, 0f, 0f, 0f)
        assertEquals(2, reports.size)
        assertArrayEquals(byteArrayOf(1, 0, 0, 0), reports[0])
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), reports[1])
    }

    @Test
    fun movingWhileDraggingPreservesHeldButton() {
        sender.send(MouseAction.MOUSE_CLICK_RIGHT, 0f, 0f, 0f)
        sender.send(MouseAction.MOUSE_CLICK_RIGHT, 5f, -3f, 0f)
        sender.send(MouseAction.NONE, 0f, 0f, 0f)
        assertArrayEquals(byteArrayOf(2, 5, -3, 0), reports[1])
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), reports[2])
    }

    @Test
    fun scrollDeltasDoNotNeedAnEmptyResetReport() {
        sender.send(MouseAction.NONE, 0f, 0f, 1f)
        sender.send(MouseAction.NONE, 0f, 0f, 0f)
        sender.send(MouseAction.NONE, 0f, 0f, 1f)
        assertEquals(2, reports.size)
        reports.forEach { assertArrayEquals(byteArrayOf(0, 0, 0, 1), it) }
    }

    @Test
    fun allAxesStayInsideTheSignedHidRange() {
        sender.send(MouseAction.NONE, 1000f, -1000f, 300f)
        assertArrayEquals(byteArrayOf(0, 127, -127, 127), reports.single())
        sender.send(MouseAction.NONE, 0f, 0f, 0f)
        assertEquals(1, reports.size)
    }

    @Test
    fun failedMovementIsNotReplayedLater() {
        var success = false
        val failingSender = MouseReportSender { reports.add(it); success }
        failingSender.send(MouseAction.NONE, 10.2f, 4.2f, 0f)
        success = true
        failingSender.send(MouseAction.NONE, 0.4f, 0.4f, 0f)
        assertEquals(1, reports.size)
    }

    @Test
    fun failedButtonReleaseCanBeRetried() {
        var success = true
        val failingSender = MouseReportSender { reports.add(it); success }
        failingSender.send(MouseAction.MOUSE_CLICK_LEFT, 0f, 0f, 0f)
        success = false
        failingSender.send(MouseAction.NONE, 0f, 0f, 0f)
        success = true
        failingSender.send(MouseAction.NONE, 0f, 0f, 0f)
        assertEquals(3, reports.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), reports.last())
    }
}
