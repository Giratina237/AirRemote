package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.MOUSE_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_REPORT_ID
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
    private val mouseReportSender = MouseReportSender { sendReport(MOUSE_REPORT_ID, it) }
    val sendMouseReport: (MouseAction, Float, Float, Float) -> Unit = { input, x, y, wheel ->
        mouseReportSender.send(input, x, y, wheel)
    }

    // Air mouse (gyroscope): click-stabilised motion, paused while a finger is on the touchpad
    val sendMouseMotion: (Float, Float) -> Unit = { x, y -> mouseReportSender.sendMotion(x, y) }
    val setMouseMotionPaused: (Boolean) -> Unit = { mouseReportSender.motionPaused = it }

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
