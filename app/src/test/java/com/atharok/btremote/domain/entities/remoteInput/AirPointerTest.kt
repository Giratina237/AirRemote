package com.atharok.btremote.domain.entities.remoteInput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Simulated TV: accelerates raw counts like Android's VelocityControl and stops at the screen edges. */
class AirPointerTest {
    private var clock = 1_000L
    private var hostX = 700f
    private var hostY = 300f
    private val raw = ArrayDeque<FloatArray>()
    private var wheel = 0

    private fun hostMove(rx: Float, ry: Float) {
        while (raw.isNotEmpty() && raw.first()[0] < clock - 80) raw.removeFirst()
        raw.addLast(floatArrayOf(clock.toFloat(), rx, ry))
        val speed = hypot(raw.sumOf { it[1].toDouble() }, raw.sumOf { it[2].toDouble() }).toFloat() / 0.08f
        val f = when { speed <= 500f -> 1f; speed >= 3000f -> 3f; else -> 1f + (speed - 500f) / 2500f * 2f }
        hostX = (hostX + rx * f).coerceIn(0f, 1919f)
        hostY = (hostY + ry * f).coerceIn(0f, 1079f)
    }

    private val pointer = AirPointer(now = { clock }, sendRaw = { x, y -> hostMove(x, y) }, sendWheel = { wheel += it })
    private val rad = (Math.PI / 180).toFloat()
    private val level = AirMouseTuning.neutralPitchDeg

    /** Holds the phone at yaw, and pitch relative to the natural pointing angle. */

    /** Holds the phone at (yawDeg, pitchDeg) for [ms] at 200 Hz. */
    private fun hold(yawDeg: Float, pitchDeg: Float, ms: Int) {
        repeat(ms / 5) {
            clock += 5
            pointer.onOrientation(yawDeg * rad, (level + pitchDeg) * rad, clock * 1_000_000L, 1f)
        }
    }

    @Test
    fun neutralIsTheCentreWhereverThePointerWas() {
        hold(30f, 0f, 1500)           // first pointing sample = middle column; level = middle row
        assertEquals(960f, hostX, 3f)
        assertEquals(540f, hostY, 3f)
    }

    @Test
    fun neutralIsTheConfiguredAngle() {
        assertEquals(45f, AirMouseTuning.neutralPitchDeg, 0.01f)
        val p = (AirMouseTuning.neutralPitchDeg - 10f) * rad      // 10 deg lower than neutral
        repeat(300) { clock += 5; pointer.onOrientation(0f, p, clock * 1_000_000L, 1f) }
        assertEquals(1020f, hostY, 25f)
    }

    @Test
    fun holdingThePhoneUprightIsNotNeutral() {
        hold(0f, 80f, 1000)           // looking at the phone screen: nothing happens yet
        assertEquals(700f, hostX, 0.1f)
        hold(20f, 0f, 1500)           // now pointing level at the TV: that's the centre
        assertEquals(960f, hostX, 3f)
        assertEquals(540f, hostY, 3f)
        hold(20f, 5f, 1500)           // 5 deg up = 240 px up
        assertEquals(300f, hostY, 25f)
    }

    @Test
    fun centerButtonUsesTheCurrentAngle() {
        hold(0f, 0f, 1500)
        pointer.recenter(fromButton = true)
        hold(10f, -12f, 1500)
        assertEquals(960f, hostX, 3f)
        assertEquals(540f, hostY, 3f)
    }

    @Test
    fun sameDirectionGivesSameSpotAfterFastSweeps() {
        hold(0f, 0f, 1500)
        // fast sweeps back and forth (host acceleration kicks in)
        for (i in 0 until 6) {
            for (k in 0..40) hold(-15f + 30f * k / 40f, 5f * k / 40f, 5)
            for (k in 0..40) hold(15f - 30f * k / 40f, 5f - 5f * k / 40f, 5)
        }
        hold(10f, -5f, 1500)
        // 40 deg across the screen: 10 deg right = +480 px, 5 deg down = +240 px
        assertEquals(1440f, hostX, 25f)
        assertEquals(780f, hostY, 25f)
        hold(0f, 0f, 1500)
        assertEquals(960f, hostX, 25f)
        assertEquals(540f, hostY, 25f)
    }

    @Test
    fun pointingPastTheEdgeResyncs() {
        hold(0f, 0f, 1500)
        hold(-60f, 0f, 1500)          // way past the left edge
        assertEquals(0f, hostX, 1f)
        hold(0f, 0f, 1500)
        assertEquals(960f, hostX, 3f)
    }

    @Test
    fun clickHoldsThePointerDespiteTheJolt() {
        hold(0f, 0f, 1500)
        pointer.onButtonChange()
        hold(1.5f, -1f, 100)          // the press jolts the phone
        assertEquals(960f, hostX, 3f)
        assertEquals(540f, hostY, 3f)
    }

    @Test
    fun scrollKeepsGoingWhileTiltedAndStopsAtNeutral() {
        hold(0f, 0f, 1500)
        pointer.setScroll(true)
        hold(0f, 0f, 300)
        assertEquals(0, wheel)
        hold(0f, 15f, 2000)            // tilted up and held
        val up = wheel
        assertTrue("scrolls up while held, got $up", up >= 8)
        hold(0f, 0f, 1000)             // back to where it started: nothing more, never backwards
        assertEquals(up, wheel)
        hold(0f, -15f, 1000)
        assertTrue(wheel < up)
        val x = hostX
        pointer.setScroll(false)
        assertTrue(abs(hostX - x) < 1f)
    }
}
