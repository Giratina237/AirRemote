package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.ui.components.customButtons.SurfaceButton

/**
 * Air mouse screen (gyroscope mode): the phone is the pointer.
 *  - CLICK: press = left button down, release = up, so holding it drags.
 *  - SCROLL: while held, the pointer stays put and tilting the phone up / down scrolls.
 *  - BACK: right mouse button (Android treats it as Back).
 */
@Composable
fun AirMouseLayout(
    sendMouseAction: (MouseAction) -> Unit,
    setScrollMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { setScrollMode(false); sendMouseAction(MouseAction.NONE) } }
    val gap = 8.dp
    val shape = RoundedCornerShape(24.dp)

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        AirButton(
            label = "CLICK",
            hint = "hold to drag",
            onDown = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sendMouseAction(MouseAction.MOUSE_CLICK_LEFT) },
            onUp = { sendMouseAction(MouseAction.NONE) },
            shape = shape,
            big = true,
            modifier = Modifier.fillMaxWidth().weight(0.55f)
        )
        Row(modifier = Modifier.fillMaxWidth().weight(0.45f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            AirButton(
                label = "SCROLL",
                hint = "hold + tilt",
                onDown = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); setScrollMode(true) },
                onUp = { setScrollMode(false) },
                shape = shape,
                modifier = Modifier.weight(0.62f).fillMaxSize()
            )
            AirButton(
                label = "BACK",
                hint = null,
                onDown = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sendMouseAction(MouseAction.MOUSE_CLICK_RIGHT) },
                onUp = { sendMouseAction(MouseAction.NONE) },
                shape = shape,
                modifier = Modifier.weight(0.38f).fillMaxSize()
            )
        }
    }
}

@Composable
private fun AirButton(
    label: String,
    hint: String?,
    onDown: () -> Unit,
    onUp: () -> Unit,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    big: Boolean = false
) {
    SurfaceButton(touchDown = onDown, touchUp = onUp, modifier = modifier, shape = shape) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (hint != null) Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
