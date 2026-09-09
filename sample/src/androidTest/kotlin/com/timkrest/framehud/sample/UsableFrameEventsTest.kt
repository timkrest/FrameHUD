// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.instrumentation.FrameHudResetRule
import com.timkrest.framehud.shared.drawFrames
import com.timkrest.framehud.shared.runOnMain
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UsableFrameEventsTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val listener = FrameHudEventListener { events += it }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(FrameHudResetRule())
        .around(FrameHudConfigRule { it.copy(eventListeners = it.eventListeners + listener) })

    @Test
    fun theLaunchReportsUsableThroughTheFullyDrawnReporter() {
        assumeTrue(
            "a window below API 26 drops the draw listener the reporter waits for",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
        )

        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)

            val usable = events.awaitEvents<FrameHudEvent.UsableFrame>(count = 1).single()
            assertEquals(ReportingProbeActivity::class.java.simpleName, usable.screen)
            assertTrue(usable.timeToUsableMs > 0f, "usable in ${usable.timeToUsableMs} ms")
        }
    }

    @Test
    fun reportUsableEndsTheMeasurementOnTheNextFrame() {
        ActivityScenario.launch(SilentProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            FrameHud.reportUsable()
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)

            val usable = events.awaitEvents<FrameHudEvent.UsableFrame>(count = 1).single()
            assertEquals(SilentProbeActivity::class.java.simpleName, usable.screen)
        }
    }

    @Test
    fun aScreenMeasuresUsableOnce() {
        ActivityScenario.launch(SilentProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            FrameHud.reportUsable()
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            events.awaitEvents<FrameHudEvent.UsableFrame>(count = 1)

            FrameHud.reportUsable()
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
        }

        events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 1)
        assertEquals(1, events.filterIsInstance<FrameHudEvent.UsableFrame>().size, "the screen measured usable twice")
    }

    @Test
    fun aRenamedScreenMeasuresUsableAgain() {
        ActivityScenario.launch(SilentProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            FrameHud.reportUsable()
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            events.awaitEvents<FrameHudEvent.UsableFrame>(count = 1)

            runOnMain { FrameHud.screen = "checkout" }
            FrameHud.reportUsable()
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)

            val usable = events.awaitEvents<FrameHudEvent.UsableFrame>(count = 2).last()
            assertEquals("checkout", usable.screen)
        }
    }

    @Test
    fun aLaunchReportAfterARenameDoesNotEndTheRenamedScreen() {
        ActivityScenario.launch(SilentProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            runOnMain { FrameHud.screen = "checkout" }
            scenario.onActivity { it.reportFullyDrawn() }
            scenario.renderCollectedFrames()
            assertTrue(
                events.filterIsInstance<FrameHudEvent.UsableFrame>().isEmpty(),
                "the launch report ended a renamed screen",
            )

            FrameHud.reportUsable()
            scenario.renderCollectedFrames()

            val usable = events.awaitEvents<FrameHudEvent.UsableFrame>(count = 1).single()
            assertEquals("checkout", usable.screen)
        }
    }
}
