// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.instrumentation.FrameHudResetRule
import com.timkrest.framehud.shared.await
import com.timkrest.framehud.shared.drawFrames
import com.timkrest.framehud.shared.runOnMain
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ScreenEventsTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val listener = FrameHudEventListener { events += it }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(FrameHudResetRule())
        .around(FrameHudConfigRule { it.copy(eventListeners = it.eventListeners + listener) })

    @Test
    fun framesAreCollectedWhileAnActivityIsResumed() {
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)

            val stats = await { FrameHud.sessionStats() }
            assertTrue(stats.frames > 0, "no frames reached the collector")
        }
    }

    @Test
    fun theFirstFrameCarriesTheScreenAndTimeToDisplay() {
        assumeFirstFramesAreReported()
        ActivityScenario.launch(ReportingProbeActivity::class.java).use {
            val firstFrame = events.awaitEvents<FrameHudEvent.FirstFrame>(count = 1).single()
            assertEquals(ReportingProbeActivity::class.java.simpleName, firstFrame.screen)
            assertTrue(firstFrame.timeToDisplayMs > 0f, "first frame took ${firstFrame.timeToDisplayMs} ms")
        }
    }

    @Test
    fun returningToAScreenDoesNotMeasureItAgain() {
        assumeFirstFramesAreReported()
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assumeTrue("the device dropped FrameMetrics reports", ended.all { it.stats.droppedReports == 0 })
        val firstFrames = events.filterIsInstance<FrameHudEvent.FirstFrame>()
        assertEquals(1, firstFrames.size, "first frames among ${events.map { it.summary }}")
    }

    @Test
    fun eachScreenSummaryCarriesTheScreenThatEnded() {
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { first ->
            first.drawFrames(FRAMES_FOR_AN_EVENT)

            ActivityScenario.launch(SilentProbeActivity::class.java).use { second ->
                second.drawFrames(FRAMES_FOR_AN_EVENT)
            }
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(screens, ended.map { it.screen })
        assertTrue(ended.all { it.stats.frames > 0 }, "a summary reported no frames")
    }

    @Test
    fun namingScreensSplitsStatsWithoutTouchingTheWindow() {
        runOnMain { FrameHud.screen = "cart" }
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            runOnMain { FrameHud.screen = "checkout" }
            scenario.renderCollectedFrames()
            runOnMain { FrameHud.screen = null }
            scenario.renderCollectedFrames()
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 3)
        assertEquals(listOf("cart", "checkout", ReportingProbeActivity::class.java.simpleName), ended.map { it.screen })
        assertTrue(ended.all { it.stats.frames > 0 }, "a named screen reported no frames")
    }

    @Test
    fun theFirstFrameCarriesTheScreenNameSetBeforeLaunch() {
        assumeFirstFramesAreReported()
        runOnMain { FrameHud.screen = "home" }
        ActivityScenario.launch(ReportingProbeActivity::class.java).use {
            val firstFrame = events.awaitEvents<FrameHudEvent.FirstFrame>(count = 1).single()
            assertEquals("home", firstFrame.screen)
        }
    }

    @Test
    fun aScreenSummaryCarriesTheMeasurementContext() {
        runOnMain { FrameHud.context = mapOf("variant" to "b") }
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 1).single()
        assertEquals(mapOf("variant" to "b"), ended.context)
    }

    @Test
    fun renamingTheScreenEndsTheActiveMark() {
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            runOnMain { FrameHud.mark = "scroll" }
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            runOnMain { FrameHud.screen = "cart" }

            val ended = events.awaitEvents<FrameHudEvent.MarkEnded>(count = 1).single()
            assertEquals("scroll", ended.mark)
            assertEquals(ReportingProbeActivity::class.java.simpleName, ended.screen)
            assertNull(FrameHud.mark, "the mark survived a screen change")
        }
    }

    private fun assumeFirstFramesAreReported() {
        assumeTrue(
            "the platform flags no first frame below API 29",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        )
    }

    private companion object {
        val screens = listOf<String?>(
            ReportingProbeActivity::class.java.simpleName,
            SilentProbeActivity::class.java.simpleName,
        )
    }
}
