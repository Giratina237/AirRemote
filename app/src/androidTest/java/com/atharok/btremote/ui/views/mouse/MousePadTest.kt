package com.atharok.btremote.ui.views.mouse

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MousePadTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<MouseAction>()
    private val movements = mutableListOf<Offset>()

    private fun showPad() {
        compose.setContent {
            MousePad(
                mouseSpeed = 1f,
                scrollSpeed = 1f,
                sendMouseAction = { actions.add(it) },
                sendMousePosition = { x, y -> movements.add(Offset(x, y)) },
                sendMouseScroll = {},
                shape = RectangleShape,
                modifier = Modifier.size(200.dp).testTag("mousePad")
            )
        }
    }

    @Test
    fun firstSingleFingerTapClicksAndReleases() {
        showPad()
        compose.onNodeWithTag("mousePad").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(MouseAction.MOUSE_CLICK_LEFT, MouseAction.NONE), actions)
        }
    }

    @Test
    fun successiveGesturesEachStartANewTap() {
        showPad()
        repeat(2) {
            compose.onNodeWithTag("mousePad").performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(
                listOf(MouseAction.MOUSE_CLICK_LEFT, MouseAction.NONE,
                    MouseAction.MOUSE_CLICK_LEFT, MouseAction.NONE),
                actions
            )
        }
    }

    @Test
    fun swipingMovesWithoutClicking() {
        showPad()
        compose.onNodeWithTag("mousePad").performTouchInput {
            swipe(Offset(20f, 100f), Offset(180f, 100f), durationMillis = 80)
        }
        compose.runOnIdle {
            assertTrue(actions.isEmpty())
            assertTrue(movements.sumOf { it.x.toDouble() } > 100)
        }
    }
}
