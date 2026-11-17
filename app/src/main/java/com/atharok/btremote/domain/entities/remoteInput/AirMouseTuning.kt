package com.atharok.btremote.domain.entities.remoteInput

import org.json.JSONObject
import java.io.File

/**
 * Live-tunable air mouse parameters. Read from <external files dir>/airmouse.json, re-checked about once a
 * second while the air mouse runs, so values can be changed with `adb push` without reinstalling.
 * Keys (all optional): pixelsPerRadian, acceleration, deadZone, minCutoffHz, beta, clickFreezeMs, rewindMs,
 * scrollDeadZoneRad, scrollRate, scrollCurve, scrollMaxRate, invertScroll.
 */
object AirMouseTuning {
    @Volatile var pixelsPerRadian = 1200f
    @Volatile var acceleration = AirMouseFilter.ACCELERATION
    @Volatile var deadZone = AirMouseFilter.DEAD_ZONE
    @Volatile var minCutoffHz = AirMouseFilter.MIN_CUTOFF_HZ
    @Volatile var beta = AirMouseFilter.BETA
    @Volatile var clickFreezeMs = MouseReportSender.CLICK_FREEZE_MS
    @Volatile var rewindMs = MouseReportSender.REWIND_MS
    @Volatile var scrollDeadZoneRad = 0.07f    // ~4 deg of tilt from neutral before scrolling starts
    @Volatile var scrollRate = 18f             // wheel notches per second per radian past the dead zone
    @Volatile var scrollCurve = 1.5f           // extra speed-up further out
    @Volatile var scrollMaxRate = 20f          // cap (x scrollPxPerNotch = fastest drag-scroll, px/s)
    @Volatile var invertScroll = false
    // absolute pointer
    @Volatile var absolute = true
    @Volatile var scrollPxPerNotch = 60f        // pen drag-scroll: pixels per second per notch/s
    // pen drag-scroll speed (px/s) for an angle past the dead zone e (rad): scrollPxPerRad*e + scrollPxPerRad2*e^2
    @Volatile var scrollPxPerRad = 1500f
    @Volatile var scrollPxPerRad2 = 8000f
    @Volatile var scrollMaxPxPerSec = 4000f
    // scroll button as a D-pad: arrow presses repeating faster with the angle (works in any UI)
    @Volatile var scrollDpad = false
    @Volatile var dpadMinRate = 3f              // presses/s just past the dead zone
    @Volatile var dpadMaxRate = 12f             // presses/s at dpadFullRad past it
    @Volatile var dpadFullRad = 0.35f
    @Volatile var pen = true                     // absolute via the pen-tablet device (false: emulate with relative mouse moves)
    @Volatile var neutralPitchDeg = 30f         // phone angle that points at the middle row (0 = level, + = tilted up)
    @Volatile var degreesPerScreenWidth = 40f   // how far to turn the phone to cross the TV
    @Volatile var pointerMinCutoffHz = 1.2f     // smoothing when still (lower = steadier)
    @Volatile var pointerBeta = 0.004f          // how quickly smoothing lets go as it moves (per px/s)
    // the TV's mouse acceleration (dumpsys input: PointerVelocityControlParameters)
    @Volatile var hostLowThreshold = 500f
    @Volatile var hostHighThreshold = 3000f
    @Volatile var hostAcceleration = 3f

    private var lastModified = -1L
    private var lastCheckMs = 0L

    fun reloadIfChanged(dir: File?, nowMs: Long) {
        if (dir == null || nowMs - lastCheckMs < 1000L) return
        lastCheckMs = nowMs
        val f = File(dir, FILE_NAME)
        val m = if (f.exists()) f.lastModified() else 0L
        if (m == lastModified) return
        lastModified = m
        if (m == 0L) return
        try {
            val j = JSONObject(f.readText())
            pixelsPerRadian = j.optDouble("pixelsPerRadian", pixelsPerRadian.toDouble()).toFloat()
            acceleration = j.optDouble("acceleration", acceleration.toDouble()).toFloat()
            deadZone = j.optDouble("deadZone", deadZone.toDouble()).toFloat()
            minCutoffHz = j.optDouble("minCutoffHz", minCutoffHz.toDouble()).toFloat()
            beta = j.optDouble("beta", beta.toDouble()).toFloat()
            clickFreezeMs = j.optLong("clickFreezeMs", clickFreezeMs)
            rewindMs = j.optLong("rewindMs", rewindMs)
            scrollDeadZoneRad = j.optDouble("scrollDeadZoneRad", scrollDeadZoneRad.toDouble()).toFloat()
            scrollRate = j.optDouble("scrollRate", scrollRate.toDouble()).toFloat()
            scrollCurve = j.optDouble("scrollCurve", scrollCurve.toDouble()).toFloat()
            scrollMaxRate = j.optDouble("scrollMaxRate", scrollMaxRate.toDouble()).toFloat()
            invertScroll = j.optBoolean("invertScroll", invertScroll)
            absolute = j.optBoolean("absolute", absolute)
            pen = j.optBoolean("pen", pen)
            if (j.has("scrollDpad")) scrollDpad = j.optBoolean("scrollDpad", scrollDpad)
            dpadMinRate = j.optDouble("dpadMinRate", dpadMinRate.toDouble()).toFloat()
            dpadMaxRate = j.optDouble("dpadMaxRate", dpadMaxRate.toDouble()).toFloat()
            dpadFullRad = j.optDouble("dpadFullRad", dpadFullRad.toDouble()).toFloat()
            scrollPxPerRad = j.optDouble("scrollPxPerRad", scrollPxPerRad.toDouble()).toFloat()
            scrollPxPerRad2 = j.optDouble("scrollPxPerRad2", scrollPxPerRad2.toDouble()).toFloat()
            scrollMaxPxPerSec = j.optDouble("scrollMaxPxPerSec", scrollMaxPxPerSec.toDouble()).toFloat()
            scrollPxPerNotch = j.optDouble("scrollPxPerNotch", scrollPxPerNotch.toDouble()).toFloat()
            neutralPitchDeg = j.optDouble("neutralPitchDeg", neutralPitchDeg.toDouble()).toFloat()
            degreesPerScreenWidth = j.optDouble("degreesPerScreenWidth", degreesPerScreenWidth.toDouble()).toFloat()
            pointerMinCutoffHz = j.optDouble("pointerMinCutoffHz", pointerMinCutoffHz.toDouble()).toFloat()
            pointerBeta = j.optDouble("pointerBeta", pointerBeta.toDouble()).toFloat()
            hostLowThreshold = j.optDouble("hostLowThreshold", hostLowThreshold.toDouble()).toFloat()
            hostHighThreshold = j.optDouble("hostHighThreshold", hostHighThreshold.toDouble()).toFloat()
            hostAcceleration = j.optDouble("hostAcceleration", hostAcceleration.toDouble()).toFloat()
            android.util.Log.i("AirMouse", "tuning: ppr=$pixelsPerRadian accel=$acceleration dead=$deadZone cutoff=$minCutoffHz beta=$beta freeze=$clickFreezeMs rewind=$rewindMs")
        } catch (e: Exception) {
            android.util.Log.w("AirMouse", "airmouse.json: $e")
        }
    }

    const val FILE_NAME = "airmouse.json"
}
