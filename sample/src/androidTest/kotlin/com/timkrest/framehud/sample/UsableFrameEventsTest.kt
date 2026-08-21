package com.timkrest.framehud.sample

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class UsableFrameEventsTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val listener = FrameHudEventListener { events += it }

    @get:Rule
    val config = FrameHudConfigRule { it.copy(eventListeners = it.eventListeners + listener) }

    @Before
    fun resetCollector() {
        clearScreenName()
        FrameHud.reset()
    }

    @After
    fun clearScreenName() {
        runOnMain { FrameHud.screen = null }
    }

    @Test
    fun theLaunchReportsUsableThroughTheFullyDrawnReporter() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames()

            val usable = awaitEvents<FrameHudEvent.UsableFrame>(count = 1).single()
            assertEquals(MainActivity::class.java.simpleName, usable.screen)
            assertTrue(usable.timeToUsableMs > 0f, "usable in ${usable.timeToUsableMs} ms")
        }
    }

    @Test
    fun reportUsableEndsTheMeasurementOnTheNextFrame() {
        ActivityScenario.launch(DetailsActivity::class.java).use { scenario ->
            scenario.renderFrames()
            FrameHud.reportUsable()
            scenario.renderFrames()

            val usable = awaitEvents<FrameHudEvent.UsableFrame>(count = 1).single()
            assertEquals(DetailsActivity::class.java.simpleName, usable.screen)
        }
    }

    @Test
    fun aScreenMeasuresUsableOnce() {
        ActivityScenario.launch(DetailsActivity::class.java).use { scenario ->
            scenario.renderFrames()
            FrameHud.reportUsable()
            scenario.renderFrames()
            awaitEvents<FrameHudEvent.UsableFrame>(count = 1)

            FrameHud.reportUsable()
            scenario.renderFrames()
        }

        awaitEvents<FrameHudEvent.ScreenEnded>(count = 1)
        assertEquals(1, events.filterIsInstance<FrameHudEvent.UsableFrame>().size, "the screen measured usable twice")
    }

    @Test
    fun aRenamedScreenMeasuresUsableAgain() {
        ActivityScenario.launch(DetailsActivity::class.java).use { scenario ->
            scenario.renderFrames()
            FrameHud.reportUsable()
            scenario.renderFrames()
            awaitEvents<FrameHudEvent.UsableFrame>(count = 1)

            runOnMain { FrameHud.screen = "checkout" }
            FrameHud.reportUsable()
            scenario.renderFrames()

            val usable = awaitEvents<FrameHudEvent.UsableFrame>(count = 2).last()
            assertEquals("checkout", usable.screen)
        }
    }

    @Test
    fun aLaunchReportAfterARenameDoesNotEndTheRenamedScreen() {
        ActivityScenario.launch(DetailsActivity::class.java).use { scenario ->
            scenario.renderFrames()
            runOnMain { FrameHud.screen = "checkout" }
            scenario.onActivity { it.reportFullyDrawn() }
            scenario.renderFrames()
            assertTrue(
                events.filterIsInstance<FrameHudEvent.UsableFrame>().isEmpty(),
                "the launch report ended a renamed screen",
            )

            FrameHud.reportUsable()
            scenario.renderFrames()

            val usable = awaitEvents<FrameHudEvent.UsableFrame>(count = 1).single()
            assertEquals("checkout", usable.screen)
        }
    }

    private inline fun <reified T : FrameHudEvent> awaitEvents(count: Int): List<T> {
        val deadlineMs = SystemClock.elapsedRealtime() + EVENT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val matching = events.filterIsInstance<T>()
            if (matching.size >= count) return matching.take(count)
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail("Expected $count ${T::class.java.simpleName} events, saw ${events.map { it.summary }}")
    }

    private companion object {
        const val EVENT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
