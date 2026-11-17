package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.customButtons.SurfaceButton
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Air mouse screen (gyroscope mode), laid out for one right hand. The thumb pivots around the lower-right of the
 * screen, so the main controls form a quarter circle there: CLICK in the corner (the vertex), BACK / SCROLL /
 * CENTER as a ring around it. Media and volume keys above, Home and Menu below.
 *  - CLICK: press = pen down, release = up, so holding it drags.
 *  - SCROLL: tap to turn scroll mode on (tap again, CLICK or BACK to leave it), or hold it and release.
 *    In scroll mode tilting up / down scrolls vertically and turning left / right scrolls sideways; the further
 *    from where it started, the faster.
 *  - CENTER: point at the middle of the TV and tap.
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
    val gap = 8.dp
    val shape = RoundedCornerShape(24.dp)

    var scrollOn by remember { mutableStateOf(false) }
    val scrollDownAt = remember { longArrayOf(0L) }
    val ignoreScrollUp = remember { booleanArrayOf(false) }
    fun scroll(on: Boolean) { scrollOn = on; setScrollMode(on) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSetScroll = rememberUpdatedState(setScrollMode)
    val currentMouse = rememberUpdatedState(sendMouseAction)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) scrollOn = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentSetScroll.value(false)
            currentMouse.value(MouseAction.NONE)
        }
    }

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

    val buttons = listOf(
        // vertex
        RadialButton("CLICK", "hold to drag",
            onDown = { tick(); if (scrollOn) scroll(false); sendMouseAction(MouseAction.MOUSE_CLICK_LEFT) },
            onUp = { sendMouseAction(MouseAction.NONE) }),
        // ring, from the right edge round to the bottom
        RadialButton("BACK", null,
            onDown = { tick(); if (scrollOn) scroll(false); sendRemoteKey(RemoteButtonProperties.BackButton.input) },
            onUp = { sendRemoteKey(REMOTE_INPUT_NONE) }),
        RadialButton("SCROLL", if (scrollOn) "on · tap to stop" else "tap or hold",
            onDown = {
                tick()
                if (scrollOn) { scroll(false); ignoreScrollUp[0] = true }
                else { scroll(true); scrollDownAt[0] = System.currentTimeMillis(); ignoreScrollUp[0] = false }
            },
            onUp = {
                // held (not tapped): scrolling ends on release
                if (!ignoreScrollUp[0] && System.currentTimeMillis() - scrollDownAt[0] > HOLD_MS) scroll(false)
            },
            active = scrollOn),
        RadialButton("CENTER", "point at middle",
            onDown = { tick(); recenter() },
            onUp = {})
    )

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(0.17f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(RemoteButtonProperties.RewindButton)
            Key(RemoteButtonProperties.PlayPauseButton, 1.4f)
            Key(RemoteButtonProperties.ForwardButton)
        }
        Row(Modifier.fillMaxWidth().weight(0.17f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(RemoteButtonProperties.VolumeDownButton)
            Key(RemoteButtonProperties.VolumeUpButton)
            Key(RemoteButtonProperties.VolumeMuteButton)
        }
        RadialPad(buttons = buttons, modifier = Modifier.fillMaxWidth().weight(0.46f))
        Row(Modifier.fillMaxWidth().weight(0.20f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(RemoteButtonProperties.HomeButton)
            Key(RemoteButtonProperties.MenuButton)
        }
    }
}

private const val HOLD_MS = 350L
private const val INNER = 0.46f    // vertex disc radius / ring radius

private class RadialButton(
    val label: String,
    val hint: String?,
    val onDown: () -> Unit,
    val onUp: () -> Unit,
    val active: Boolean = false
)

/**
 * Quarter circle anchored at the bottom-right corner: buttons[0] is the vertex (inner quarter disc), the rest
 * split the outer ring into equal sectors, from the right edge (top) round to the bottom edge (left).
 */
@Composable
private fun RadialPad(buttons: List<RadialButton>, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pressed by remember { mutableIntStateOf(-1) }
    val current = rememberUpdatedState(buttons)
    val ringCount = buttons.size - 1

    val idle = MaterialTheme.colorScheme.surfaceContainerHigh
    val down = MaterialTheme.colorScheme.secondaryContainer
    val on = MaterialTheme.colorScheme.primaryContainer
    val gapColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.toPx() }

    fun radius(w: Float, h: Float) = min(w, h)
    fun hit(p: Offset, s: IntSize): Int {
        val vx = s.width.toFloat(); val vy = s.height.toFloat()
        val r = hypot(vx - p.x, vy - p.y)
        val big = radius(vx, vy)
        if (r <= big * INNER) return 0
        if (r > big * 1.08f) return -1
        // angle from the left (0) up to straight up (90 degrees)
        val a = Math.toDegrees(atan2((vy - p.y).toDouble(), (vx - p.x).toDouble())).toFloat().coerceIn(0f, 90f)
        val sector = ((90f - a) / (90f / ringCount)).toInt().coerceIn(0, ringCount - 1)   // 0 = top (right edge)
        return 1 + sector
    }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val index = hit(first.position, size)
                    if (index < 0) return@awaitEachGesture
                    val button = current.value[index]
                    pressed = index
                    button.onDown()
                    first.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    pressed = -1
                    button.onUp()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val big = radius(this.size.width, this.size.height)
            val vertex = Offset(this.size.width, this.size.height)
            val tl = Offset(vertex.x - big, vertex.y - big)
            val sweep = 90f / ringCount
            // ring sectors (Compose angles: 180 = left, 270 = up); sector 0 at the top
            for (i in 0 until ringCount) {
                val color = when {
                    pressed == 1 + i -> down
                    current.value[1 + i].active -> on
                    else -> idle
                }
                drawArc(color, startAngle = 270f - sweep * (i + 1), sweepAngle = sweep, useCenter = true,
                    topLeft = tl, size = Size(2 * big, 2 * big))
            }
            // gaps between sectors
            for (i in 1 until ringCount) {
                val a = ((270f - sweep * i) * PI / 180f).toFloat()
                drawLine(gapColor, vertex, Offset(vertex.x + big * cos(a), vertex.y + big * sin(a)), strokeWidth = gapPx)
            }
            // vertex: gap ring, then the disc
            val inner = big * INNER
            drawCircle(gapColor, radius = inner + gapPx / 2f, center = vertex)
            drawCircle(if (pressed == 0) down else idle, radius = inner - gapPx / 2f, center = vertex)
            drawCircle(gapColor, radius = big, center = vertex, style = Stroke(width = gapPx / 2f))
        }

        if (size != IntSize.Zero) {
            val big = radius(size.width.toFloat(), size.height.toFloat())
            val sweep = 90.0 / ringCount
            fun at(r: Float, deg: Double): IntOffset {
                val a = deg * PI / 180.0
                return IntOffset((size.width + r * cos(a)).roundToInt(), (size.height + r * sin(a)).roundToInt())
            }
            val labelW = with(density) { 120.dp.roundToPx() }
            val labelH = with(density) { 56.dp.roundToPx() }
            for (i in buttons.indices) {
                val c = if (i == 0) at(big * INNER * 0.52f, 225.0)
                        else at(big * (INNER + 1f) / 2f, 270.0 - sweep * (i - 0.5))
                Box(
                    Modifier
                        .offset { IntOffset(c.x - labelW / 2, c.y - labelH / 2) }
                        .size(with(density) { labelW.toDp() }, with(density) { labelH.toDp() }),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = buttons[i].label,
                            style = if (i == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        buttons[i].hint?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
