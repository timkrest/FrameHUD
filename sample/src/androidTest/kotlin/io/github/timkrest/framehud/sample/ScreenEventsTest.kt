package io.github.timkrest.framehud.sample

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.timkrest.framehud.FrameHud
import io.github.timkrest.framehud.FrameHudEvent
import io.github.timkrest.framehud.FrameHudEventListener
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        FrameHud.reset()
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
    fun eachScreenSummaryCarriesTheScreenThatEnded() {
        ActivityScenario.launch(MainActivity::class.java).use { main ->
            main.renderFrames(RENDER_MS)

            ActivityScenario.launch(DetailsActivity::class.java).use { details ->
                details.renderFrames(RENDER_MS)
            }
        }

        val ended = awaitScreenEnded(count = 2)
        val expected = listOf<String?>(
            MainActivity::class.java.simpleName,
            DetailsActivity::class.java.simpleName,
        )
        assertEquals(expected, ended.map { it.screen })
        assertTrue(ended.all { it.stats.frames > 0 }, "a summary reported no frames")
    }

    private fun awaitScreenEnded(count: Int): List<FrameHudEvent.ScreenEnded> {
        val deadlineMs = SystemClock.elapsedRealtime() + EVENT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val ended = events.filterIsInstance<FrameHudEvent.ScreenEnded>()
            if (ended.size >= count) return ended.take(count)
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail("Expected $count screen summaries, saw ${events.map { it.summary }}")
    }

    private companion object {
        const val RENDER_MS = 700L
        const val STATS_TIMEOUT_MS = 2_000L
        const val EVENT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
