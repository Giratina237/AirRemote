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
        assertEquals(30f, AirMouseTuning.neutralPitchDeg, 0.01f)
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

/** Pen-tablet mode: the TV positions the cursor from absolute coordinates. */
class AirPointerPenTest {
    private var clock = 1_000L
    private val pens = mutableListOf<Triple<Float, Float, Boolean>>()
    private val times = mutableListOf<Long>()
    private var relative = 0
    private val cursor = PenCursor(now = { clock }) { x, y, tip, _ -> pens.add(Triple(x, y, tip)); times.add(clock) }
    private val pointer = AirPointer(now = { clock }, sendRaw = { _, _ -> relative++ }, sendWheel = {}, pen = cursor)
    private val rad = (Math.PI / 180).toFloat()
    private val level = AirMouseTuning.neutralPitchDeg

    private fun hold(yawDeg: Float, pitchDeg: Float, ms: Int) = repeat(ms / 5) {
        clock += 5
        pointer.onOrientation(yawDeg * rad, (level + pitchDeg) * rad, clock * 1_000_000L, 1f)
        cursor.tick()
    }

    @Test
    fun absoluteCoordinatesNoRelativeMoves() {
        hold(0f, 0f, 500)
        for (i in 0 until 10) { hold(15f, 5f, 100); hold(-15f, -5f, 100) }
        hold(10f, -5f, 1500)
        val (x, y, _) = pens.last()
        assertEquals(1440f, x, 2f)
        assertEquals(780f, y, 2f)
        assertEquals(0, relative)
    }

    @Test
    fun clickPressesThePenAtTheSteadySpot() {
        hold(0f, 0f, 1500)
        pointer.onButtonChange()
        cursor.setTip(true)
        hold(1.5f, -1f, 100)                       // jolt
        assertTrue(pens.last().third)
        assertEquals(960f, pens.last().first, 3f)
        assertEquals(540f, pens.last().second, 3f)
        cursor.setTip(false)
        assertTrue(!pens.last().third)
    }

    @Test
    fun scrollDragsSmoothlyAndLiftsWithoutFling() {
        hold(0f, 0f, 1500)
        pointer.setScroll(true)
        hold(0f, 0f, 300)
        assertTrue(pens.none { it.third })         // no tilt: no press (no accidental long-press)
        hold(0f, 15f, 1500)                        // tilt up: drag down (content scrolls up), tip held
        val down = pens.filter { it.third }
        assertTrue(down.size > 50)
        assertTrue("moves in small steps", pens.zipWithNext().filter { (a, b) -> a.third && b.third }.all { (a, b) -> abs(b.second - a.second) < 30f })
        assertTrue(down.last().second > down.first().second)
        hold(0f, 0f, 400)                          // back to neutral: holds still, then lifts
        val lift = pens.indexOfLast { it.third }
        assertTrue(lift < pens.size - 1)
        // the pen is still (no reports) for a while before it lifts, so the list sees zero speed: no fling
        assertTrue("still before lifting", times[lift + 1] - times[lift] >= PenCursor.IDLE_MS)
        assertEquals(pens[lift].second, pens[lift + 1].second, 0.01f)
        pointer.setScroll(false)
        hold(0f, 0f, 300)
        assertEquals(960f, pens.last().first, 3f)  // cursor back where it was
        assertEquals(540f, pens.last().second, 3f)
    }

    @Test
    fun turningRightScrollsSideways() {
        hold(0f, 0f, 1500)
        pointer.setScroll(true)
        hold(0f, 0f, 300)
        hold(15f, 0f, 1000)                        // turned right: drag left (content scrolls right)
        val down = pens.filter { it.third }
        assertTrue(down.size > 30)
        assertTrue(down.last().first < down.first().first)
        assertEquals(down.first().second, down.last().second, 1f)   // no vertical drift
    }

    @Test
    fun sidewaysScrollIgnoresWristTilt() {
        hold(0f, 0f, 1500)
        pointer.setScroll(true)
        hold(0f, 0f, 300)
        hold(15f, 0f, 200)                         // turn right first...
        hold(15f, 9f, 1000)                        // ...the wrist tilts too: still only sideways
        val down = pens.filter { it.third }
        assertEquals(down.first().second, down.last().second, 0.5f)
    }

    private fun dragSpeed(tiltDeg: Float): Float {
        pens.clear(); times.clear()
        pointer.setScroll(false); hold(0f, 0f, 400)
        pointer.setScroll(true); hold(0f, 0f, 300)
        hold(0f, tiltDeg, 120)                      // within one stroke
        val d = pens.filter { it.third }
        return (d.last().second - d.first().second) / ((times[pens.lastIndexOf(d.last())] - times[pens.indexOf(d.first())]) / 1000f)
    }

    @Test
    fun scrollSpeedGrowsWithTilt() {
        hold(0f, 0f, 1500)
        val slow = dragSpeed(10f)
        val fast = dragSpeed(30f)
        assertTrue("slow $slow fast $fast", slow > 100f && fast > 3.5f * slow && fast > 1500f)
    }
}

/** Scroll button in D-pad mode: arrow presses, faster the further it's tilted. */
class AirPointerDpadTest {
    private var clock = 1_000L
    private val keys = mutableListOf<Pair<Long, Int>>()
    private val cursor = PenCursor(now = { clock }) { _, _, _, _ -> }
    private val pointer = AirPointer(now = { clock }, sendRaw = { _, _ -> }, sendWheel = {}, pen = cursor,
        sendDpad = { keys.add(clock to it) })
    private val rad = (Math.PI / 180).toFloat()
    private val level = AirMouseTuning.neutralPitchDeg

    private fun hold(yawDeg: Float, pitchDeg: Float, ms: Int) = repeat(ms / 5) {
        clock += 5
        pointer.onOrientation(yawDeg * rad, (level + pitchDeg) * rad, clock * 1_000_000L, 1f)
    }

    @org.junit.After fun reset() { AirMouseTuning.scrollDpad = false }

    @Test
    fun tiltSendsRepeatingArrowsOneAxis() {
        AirMouseTuning.scrollDpad = true
        hold(0f, 0f, 1500)
        pointer.setScroll(true)
        hold(0f, 0f, 300)
        assertTrue(keys.isEmpty())
        hold(0f, -6f, 1000)                         // small wobble: nothing
        assertTrue(keys.isEmpty())
        hold(0f, -12f, 1000)                        // tilt down a bit: one press, a pause, then slow repeats
        val slow = keys.size
        assertTrue("slow $slow", keys.all { it.second == 1 } && slow in 2..4)
        assertTrue(keys.size < 2 || keys[1].first - keys[0].first >= 450)
        keys.clear()
        hold(0f, -45f, 1000)                        // much further: faster
        assertTrue("fast ${keys.size}", keys.all { it.second == 1 } && keys.size >= 6)
        keys.clear()
        hold(0f, 0f, 300); hold(20f, 8f, 1000)       // back to neutral, then turn right (wrist tilt ignored)
        assertTrue(keys.isNotEmpty() && keys.all { it.second == 3 })
    }
}
