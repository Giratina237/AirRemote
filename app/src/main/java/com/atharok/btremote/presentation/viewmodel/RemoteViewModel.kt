package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.MOUSE_REPORT_ID
import com.atharok.btremote.common.utils.PEN_MAX
import com.atharok.btremote.common.utils.PEN_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_REPORT_ID
import com.atharok.btremote.domain.entities.remoteInput.AirMouseTuning
import com.atharok.btremote.domain.entities.remoteInput.AirPointer
import kotlinx.coroutines.delay
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.PenCursor
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.MouseReportSender
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.domain.usecases.RemoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RemoteViewModel(
    private val useCase: RemoteUseCase
): ViewModel() {

    // ---- Settings ----

    val remoteSettingsFlow: Flow<RemoteSettings> = useCase.remoteSettingsFlow

    // ---- Connection ----

    fun disconnectDevice(): Boolean = useCase.disconnectDevice()

    // ---- Send ----

    fun sendReport(id: Int, bytes: ByteArray): Boolean {
        return useCase.sendReport(id, bytes)
    }

    // Remote
    val sendRemoteReport: (ByteArray) -> Unit = { bytes -> sendReport(REMOTE_REPORT_ID, bytes) }

    // Mouse
    // The TV sees a pen tablet, not a mouse (a mouse on the same device would turn the pen into an unused
    // touchpad): mouse reports from the touchpad / buttons are translated into pen-cursor actions.
    private val penCursor = PenCursor(sendPen = { x, y, tip, inRange -> sendPenReport(x, y, tip, inRange) })
    private var mouseButtons = 0
    private val mouseReportSender = MouseReportSender { mouseToPen(it) }

    private fun mouseToPen(report: ByteArray): Boolean {
        val buttons = report[0].toInt()
        val changed = buttons xor mouseButtons
        mouseButtons = buttons
        if (report[1].toInt() != 0 || report[2].toInt() != 0) penCursor.moveBy(report[1].toFloat(), report[2].toFloat())
        if (changed and 0x02 != 0) {   // right button = Back
            sendReport(REMOTE_REPORT_ID, if (buttons and 0x02 != 0) byteArrayOf(0x24, 0x02) else REMOTE_INPUT_NONE)
        }
        if (changed and 0x01 != 0) penCursor.setTip(buttons and 0x01 != 0)
        if (report[3].toInt() != 0) penCursor.scrollBy(report[3] * AirMouseTuning.scrollPxPerNotch / 4f)
        return true
    }

    init {
        viewModelScope.launch {
            while (true) { delay(16); penCursor.tick() }
        }
    }
    val sendMouseReport: (MouseAction, Float, Float, Float) -> Unit = { input, x, y, wheel ->
        mouseReportSender.send(input, x, y, wheel)
    }

    // Air mouse (gyroscope): click-stabilised motion, paused while a finger is on the touchpad
    val sendMouseMotion: (Float, Float) -> Unit = { x, y -> mouseReportSender.sendMotion(x, y) }
    val setMouseMotionPaused: (Boolean) -> Unit = { mouseReportSender.motionPaused = it }
    private val airPointer = AirPointer(
        sendRaw = { x, y -> mouseReportSender.sendPointerRaw(x, y) },
        sendWheel = { mouseReportSender.sendWheel(it) },
        pen = penCursor
    ).also { p -> mouseReportSender.onButtonChange = { p.onButtonChange() } }

    private fun sendPenReport(x: Float, y: Float, tip: Boolean, inRange: Boolean) {
        val px = (x / (penCursor.screenW - 1f) * PEN_MAX).toInt().coerceIn(0, PEN_MAX)
        val py = (y / (penCursor.screenH - 1f) * PEN_MAX).toInt().coerceIn(0, PEN_MAX)
        val flags = (if (tip) 1 else 0) or (if (inRange) 2 else 0)
        sendReport(PEN_REPORT_ID, byteArrayOf(flags.toByte(), px.toByte(), (px shr 8).toByte(), py.toByte(), (py shr 8).toByte()))
    }
    val setMouseScrollMode: (Boolean) -> Unit = {
        if (AirMouseTuning.absolute) airPointer.setScroll(it) else mouseReportSender.scrollMode = it
    }
    val sendMouseOrientation: (Float, Float, Long, Float) -> Unit = { yaw, pitch, t, speed -> airPointer.onOrientation(yaw, pitch, t, speed) }
    val recenterAirPointer: (Boolean) -> Unit = { fromButton -> airPointer.recenter(fromButton) }
    val sendMouseTilt: (Float, Float) -> Unit = { rateY, dt -> mouseReportSender.sendTilt(rateY, dt) }

    // Keyboard
    val sendKeyboardReport: (ByteArray) -> Unit = { bytes -> sendReport(KEYBOARD_REPORT_ID, bytes) }

    // Text (Keyboard)
    private var sendTextJob: Job? = null
    val sendTextReport: (String, VirtualKeyboardLayout, Boolean) -> Unit = { text, virtualKeyboardLayout, shouldSendEnter ->
        if(sendTextJob?.isActive != true) {
            sendTextJob = viewModelScope.launch(Dispatchers.Default) {
                useCase.sendTextReport(text, virtualKeyboardLayout, shouldSendEnter)
            }
        }
    }
}
