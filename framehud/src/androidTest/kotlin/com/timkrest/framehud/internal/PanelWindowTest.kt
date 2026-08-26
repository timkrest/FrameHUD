package com.timkrest.framehud.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.ui.PanelDrag
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PanelWindowTest {

    private val relayouts = mutableListOf<Pair<Int, Int>>()

    private val handedDrag = AtomicReference<PanelDrag>()

    @Test
    fun aDragLaysTheWindowOutOnceAtEachEndOfTheGesture() {
        onShownPanel { _, drag ->
            drag.grab(Offset(x = GRABBED_X, y = GRABBED_Y))
            repeat(MOVES) { move ->
                drag.moveTo(Offset(x = GRABBED_X - move, y = GRABBED_Y + move))
            }
            assertEquals(listOf(FULL_SCREEN), relayouts, "moves in flight")

            drag.release()
            assertEquals(listOf(FULL_SCREEN, WRAPPED), relayouts, "the gesture over")
        }
    }

    @Test
    fun aPanelKeepsWhereTheFingerLeftIt() {
        onShownPanel { window, drag ->
            val before = window.position

            drag.grab(Offset(x = GRABBED_X, y = GRABBED_Y))
            drag.moveTo(Offset(x = GRABBED_X - TRAVEL, y = GRABBED_Y + TRAVEL))
            drag.release()

            assertEquals(
                PanelPosition(x = before.x + TRAVEL.toInt(), y = before.y + TRAVEL.toInt()),
                window.position,
            )
        }
    }

    private fun onShownPanel(block: (window: PanelWindow, drag: PanelDrag) -> Unit) {
        ActivityScenario.launch(Activity::class.java).use { scenario ->
            val panel = AtomicReference<PanelWindow>()
            scenario.onActivity { activity ->
                val window = PanelWindow(
                    context = spyingOnLayouts(activity),
                    mode = PanelWindowMode.APP,
                    startPosition = null,
                    content = { drag -> handedDrag.set(drag) },
                )
                panel.set(window)
                window.show()
            }
            val drag = awaitComposedContent()
            try {
                scenario.onActivity { block(panel.get(), drag) }
            } finally {
                scenario.onActivity { panel.get().dismiss() }
            }
        }
    }

    private fun awaitComposedContent(): PanelDrag {
        val deadline = SystemClock.uptimeMillis() + COMPOSE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            handedDrag.get()?.let { return it }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("the panel never composed its content")
    }

    private fun spyingOnLayouts(activity: Activity): Context = object : ContextWrapper(activity) {
        private val spied = requireNotNull(activity.getSystemService(WindowManager::class.java))

        private val spy = object : WindowManager by spied {
            override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
                relayouts += params.width to params.height
                spied.updateViewLayout(view, params)
            }

            @RequiresApi(Build.VERSION_CODES.R)
            override fun getCurrentWindowMetrics(): WindowMetrics = spied.currentWindowMetrics
        }

        override fun getSystemService(name: String): Any? =
            if (name == WINDOW_SERVICE) spy else super.getSystemService(name)
    }

    private companion object {
        const val GRABBED_X = 400f
        const val GRABBED_Y = 400f
        const val TRAVEL = 60f
        const val MOVES = 20
        const val COMPOSE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 20L

        val FULL_SCREEN = ViewGroup.LayoutParams.MATCH_PARENT to ViewGroup.LayoutParams.MATCH_PARENT
        val WRAPPED = ViewGroup.LayoutParams.WRAP_CONTENT to ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
