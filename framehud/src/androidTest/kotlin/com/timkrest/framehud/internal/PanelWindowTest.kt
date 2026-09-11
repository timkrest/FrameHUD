// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.ui.dragHandle
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PanelWindowTest {

    private val relayouts = mutableListOf<Pair<Int, Int>>()

    private val shownView = AtomicReference<View>()

    @Test
    fun aPanelFollowsTheFingerOnceTheDragBegan() {
        onShownPanel { window, panel ->
            val dragging = panel.dragFrom(window)

            panel.touch(MotionEvent.ACTION_MOVE, x = GRABBED_X - TRAVEL, y = GRABBED_Y + SLOP_ROOM + TRAVEL)

            assertEquals(
                PanelPosition(x = dragging.x + TRAVEL.toInt(), y = dragging.y + TRAVEL.toInt()),
                window.position,
            )
        }
    }

    @Test
    fun aFingerThatStoppedLeavesThePanelWhereItIs() {
        onShownPanel { window, panel ->
            val dragging = panel.dragFrom(window)

            repeat(MOVES) { panel.touch(MotionEvent.ACTION_MOVE, x = GRABBED_X, y = GRABBED_Y + SLOP_ROOM) }

            assertEquals(dragging, window.position)
        }
    }

    @Test
    fun everyMoveWithinOneFrameCostsASingleRelayout() {
        onShownPanel { window, panel ->
            panel.dragFrom(window)
            repeat(MOVES) { step ->
                panel.touch(MotionEvent.ACTION_MOVE, x = GRABBED_X - step, y = GRABBED_Y + SLOP_ROOM + step)
            }
        }

        assertEquals(listOf(WRAPPED), relayouts)
    }

    private fun View.dragFrom(window: PanelWindow): PanelPosition {
        touch(MotionEvent.ACTION_DOWN, x = GRABBED_X, y = GRABBED_Y)
        touch(MotionEvent.ACTION_MOVE, x = GRABBED_X, y = GRABBED_Y + SLOP_ROOM)
        return window.position
    }

    private fun View.touch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(DOWN_TIME, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun onShownPanel(block: (window: PanelWindow, panel: View) -> Unit) {
        ActivityScenario.launch(Activity::class.java).use { scenario ->
            val panel = AtomicReference<PanelWindow>()
            scenario.onActivity { activity ->
                val window = PanelWindow(
                    context = spyingOnLayouts(activity),
                    mode = PanelWindowMode.APP,
                    startPosition = START_POSITION,
                    content = { drag -> Box(Modifier.size(PANEL_SIDE).dragHandle(drag)) },
                )
                panel.set(window)
                window.show()
            }
            val view = awaitLaidOutPanel()
            try {
                scenario.onActivity { block(panel.get(), view) }
                view.awaitFrame()
            } finally {
                scenario.onActivity { panel.get().dismiss() }
            }
        }
    }

    private fun View.awaitFrame() {
        val drawn = CountDownLatch(1)
        postOnAnimation { drawn.countDown() }
        assertTrue(drawn.await(LAYOUT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "no frame came")
    }

    private fun awaitLaidOutPanel(): View {
        val deadline = SystemClock.uptimeMillis() + LAYOUT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            shownView.get()?.takeIf { it.width > 0 }?.let {
                relayouts.clear()
                return it
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("the panel never reached the window")
    }

    private fun spyingOnLayouts(activity: Activity): Context = object : ContextWrapper(activity) {
        private val spied = requireNotNull(activity.getSystemService(WindowManager::class.java))

        private val spy = object : WindowManager by spied {
            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                shownView.set(view)
                spied.addView(view, params)
            }

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
        const val GRABBED_X = 100f
        const val GRABBED_Y = 100f
        const val SLOP_ROOM = 120f
        const val TRAVEL = 60f
        const val MOVES = 20
        const val LAYOUT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 20L
        const val DOWN_TIME = 0L

        val PANEL_SIDE = 200.dp
        val START_POSITION = PanelPosition(x = 0, y = 0)
        val WRAPPED = ViewGroup.LayoutParams.WRAP_CONTENT to ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
