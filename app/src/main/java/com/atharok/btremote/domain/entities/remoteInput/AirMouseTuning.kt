package com.atharok.btremote.domain.entities.remoteInput

import org.json.JSONObject
import java.io.File

/**
 * Live-tunable air mouse parameters. Read from <external files dir>/airmouse.json, re-checked about once a
 * second while the air mouse runs, so values can be changed with `adb push` without reinstalling.
 * Keys (all optional): pixelsPerRadian, acceleration, deadZone, minCutoffHz, beta, clickFreezeMs, rewindMs.
 */
object AirMouseTuning {
    @Volatile var pixelsPerRadian = 1200f
    @Volatile var acceleration = AirMouseFilter.ACCELERATION
    @Volatile var deadZone = AirMouseFilter.DEAD_ZONE
    @Volatile var minCutoffHz = AirMouseFilter.MIN_CUTOFF_HZ
    @Volatile var beta = AirMouseFilter.BETA
    @Volatile var clickFreezeMs = MouseReportSender.CLICK_FREEZE_MS
    @Volatile var rewindMs = MouseReportSender.REWIND_MS

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
            android.util.Log.i("AirMouse", "tuning: ppr=$pixelsPerRadian accel=$acceleration dead=$deadZone cutoff=$minCutoffHz beta=$beta freeze=$clickFreezeMs rewind=$rewindMs")
        } catch (e: Exception) {
            android.util.Log.w("AirMouse", "airmouse.json: $e")
        }
    }

    const val FILE_NAME = "airmouse.json"
}
