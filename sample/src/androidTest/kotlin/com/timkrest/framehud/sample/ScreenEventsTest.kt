package com.timkrest.framehud.sample

import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class ScreenEventsTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val listener = FrameHudEventListener { events += it }

    @get:Rule
    val config = FrameHudConfigRule { it.copy(eventListeners = it.eventListeners + listener) }

    @Before
    fun resetCollector() {
        clearScreenNameAndContext()
        FrameHud.reset()
    }

    @After
    fun clearScreenNameAndContext() {
        runOnMain {
            FrameHud.screen = null
            FrameHud.context = emptyMap()
        }
    }

    @Test
    fun framesAreCollectedWhileAnActivityIsResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(RENDER_MS)

            val stats = assertNotNull(FrameHud.awaitSessionStats(STATS_TIMEOUT_MS), "nothing was collecting")
            assertTrue(stats.frames > 0, "no frames reached the collector")
        }
    }

    @Test
    fun theFirstFrameCarriesTheScreenAndTimeToDisplay() {
        assumeFirstFramesAreReported()
        ActivityScenario.launch(MainActivity::class.java).use {
            val firstFrame = awaitEvents<FrameHudEvent.FirstFrame>(count = 1).single()
            assertEquals(MainActivity::class.java.simpleName, firstFrame.screen)
            assertTrue(firstFrame.timeToDisplayMs > 0f, "first frame took ${firstFrame.timeToDisplayMs} ms")
        }
    }

    @Test
    fun returningToAScreenDoesNotMeasureItAgain() {
        assumeFirstFramesAreReported()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(RENDER_MS)
            awaitEvents<FrameHudEvent.FirstFrame>(count = 1)

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.renderFrames(RENDER_MS)
        }

        awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(1, events.filterIsInstance<FrameHudEvent.FirstFrame>().size, "the screen was measured twice")
    }

    @Test
    fun eachScreenSummaryCarriesTheScreenThatEnded() {
        ActivityScenario.launch(MainActivity::class.java).use { main ->
            main.renderFrames(RENDER_MS)

            ActivityScenario.launch(DetailsActivity::class.java).use { details ->
                details.renderFrames(RENDER_MS)
            }
        }

        val ended = awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(screens, ended.map { it.screen })
        assertTrue(ended.all { it.stats.frames > 0 }, "a summary reported no frames")
    }

    @Test
    fun namingScreensSplitsStatsWithoutTouchingTheWindow() {
        runOnMain { FrameHud.screen = "cart" }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(RENDER_MS)
            runOnMain { FrameHud.screen = "checkout" }
            scenario.renderFrames(RENDER_MS)
            runOnMain { FrameHud.screen = null }
            scenario.renderFrames(RENDER_MS)
        }

        val ended = awaitEvents<FrameHudEvent.ScreenEnded>(count = 3)
        assertEquals(listOf("cart", "checkout", MainActivity::class.java.simpleName), ended.map { it.screen })
        assertTrue(ended.all { it.stats.frames > 0 }, "a named screen reported no frames")
    }

    @Test
    fun theFirstFrameCarriesTheScreenNameSetBeforeLaunch() {
        assumeFirstFramesAreReported()
        runOnMain { FrameHud.screen = "home" }
        ActivityScenario.launch(MainActivity::class.java).use {
            val firstFrame = awaitEvents<FrameHudEvent.FirstFrame>(count = 1).single()
            assertEquals("home", firstFrame.screen)
        }
    }

    @Test
    fun aScreenSummaryCarriesTheMeasurementContext() {
        runOnMain { FrameHud.context = mapOf("variant" to "b") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(RENDER_MS)
        }

        val ended = awaitEvents<FrameHudEvent.ScreenEnded>(count = 1).single()
        assertEquals(mapOf("variant" to "b"), ended.context)
    }

    @Test
    fun renamingTheScreenEndsTheActiveMark() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(RENDER_MS)
            runOnMain { FrameHud.mark = "scroll" }
            scenario.renderFrames(RENDER_MS)
            runOnMain { FrameHud.screen = "cart" }

            val ended = awaitEvents<FrameHudEvent.MarkEnded>(count = 1).single()
            assertEquals("scroll", ended.mark)
            assertEquals(MainActivity::class.java.simpleName, ended.screen)
            assertNull(FrameHud.mark, "the mark survived a screen change")
        }
    }

    private fun assumeFirstFramesAreReported() {
        assumeTrue(
            "the platform flags no first frame below API 29",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        )
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
        val screens = listOf<String?>(
            MainActivity::class.java.simpleName,
            DetailsActivity::class.java.simpleName,
        )
        const val RENDER_MS = 700L
        const val STATS_TIMEOUT_MS = 2_000L
        const val EVENT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
