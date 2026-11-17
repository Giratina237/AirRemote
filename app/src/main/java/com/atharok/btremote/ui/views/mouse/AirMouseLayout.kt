package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.customButtons.SurfaceButton

/**
 * Air mouse screen (gyroscope mode), laid out for one right hand: the phone is the pointer and the thumb
 * rests on the middle-right of the screen.
 *  - Thumb zone (lower-right sweep): CLICK on the right (press = pen down, release = up, so holding it drags),
 *    BACK under it, SCROLL bottom-left (hold, tilt up / down to keep scrolling). CENTER top-left, the one
 *    corner of the band the thumb doesn't reach comfortably.
 *  - Media and volume keys along the top, Home and Menu at the bottom (used less).
 */
@Composable
fun AirMouseLayout(
    sendMouseAction: (MouseAction) -> Unit,
    setScrollMode: (Boolean) -> Unit,
    recenter: () -> Unit = {},
    sendRemoteKey: (ByteArray) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    DisposableEffect(Unit) { onDispose { setScrollMode(false); sendMouseAction(MouseAction.NONE) } }
    val gap = 8.dp
    val shape = RoundedCornerShape(24.dp)

    @Composable
    fun RowScope.Key(button: RemoteButtonProperties, weight: Float = 1f) {
        SurfaceButton(
            touchDown = { tick(); sendRemoteKey(button.input) },
            touchUp = { sendRemoteKey(REMOTE_INPUT_NONE) },
            modifier = Modifier.weight(weight).fillMaxHeight(),
            shape = shape
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(button.icon, contentDescription = stringResource(button.stringRes), modifier = Modifier.size(32.dp))
            }
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(0.18f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(RemoteButtonProperties.RewindButton)
            Key(RemoteButtonProperties.PlayPauseButton, 1.4f)
            Key(RemoteButtonProperties.ForwardButton)
        }
        Row(Modifier.fillMaxWidth().weight(0.18f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(RemoteButtonProperties.VolumeDownButton)
            Key(RemoteButtonProperties.VolumeUpButton)
            Key(RemoteButtonProperties.VolumeMuteButton)
        }
        // thumb zone: a right thumb sweeps the lower-right of this band (not its top-left corner)
        Row(Modifier.fillMaxWidth().weight(0.41f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(0.46f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                AirButton(
                    label = "CENTER",
                    hint = "point at the middle",
                    onDown = { tick(); recenter() },
                    onUp = {},
                    shape = shape,
                    modifier = Modifier.fillMaxWidth().weight(0.5f)
                )
                AirButton(
                    label = "SCROLL",
                    hint = "hold + tilt",
                    onDown = { tick(); setScrollMode(true) },
                    onUp = { setScrollMode(false) },
                    shape = shape,
                    modifier = Modifier.fillMaxWidth().weight(0.5f)
                )
            }
            Column(Modifier.weight(0.54f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                AirButton(
                    label = "CLICK",
                    hint = "hold to drag",
                    onDown = { tick(); sendMouseAction(MouseAction.MOUSE_CLICK_LEFT) },
                    onUp = { sendMouseAction(MouseAction.NONE) },
                    shape = shape,
                    big = true,
                    modifier = Modifier.fillMaxWidth().weight(0.64f)
                )
                AirButton(
                    label = "BACK",
                    hint = null,
                    onDown = { tick(); sendRemoteKey(RemoteButtonProperties.BackButton.input) },
                    onUp = { sendRemoteKey(REMOTE_INPUT_NONE) },
                    shape = shape,
                    modifier = Modifier.fillMaxWidth().weight(0.36f)
                )
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.23f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(RemoteButtonProperties.HomeButton)
            Key(RemoteButtonProperties.MenuButton)
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
