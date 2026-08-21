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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class MarkEventsTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val deliveryThreads = CopyOnWriteArrayList<String>()
    private val listener = FrameHudEventListener {
        deliveryThreads += Thread.currentThread().name
        events += it
    }

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
    fun aMarkLeftOpenEndsWithTheScreenItRanOn() {
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.renderFrames()
            runOnMain { FrameHud.mark = MARK }
            scenario.renderFrames()
        }

        val ended = awaitMarkEnded()
        assertEquals(MARK, ended.mark)
        assertEquals(ReportingProbeActivity::class.java.simpleName, ended.screen)
        assertTrue(ended.stats.frames > 0, "the mark reported no frames")

        val intervals = events.filter { it is FrameHudEvent.MarkEnded || it is FrameHudEvent.ScreenEnded }
        assertIs<FrameHudEvent.MarkEnded>(intervals.first(), "the screen was summed up before the mark inside it")

        val metricsThread = FrameHud.config.metricsThreadName
        assertEquals(listOf(metricsThread), deliveryThreads.distinct(), "an event skipped the metrics thread")
    }

    private fun awaitMarkEnded(): FrameHudEvent.MarkEnded {
        val deadlineMs = SystemClock.elapsedRealtime() + EVENT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            events.filterIsInstance<FrameHudEvent.MarkEnded>().firstOrNull()?.let { return it }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail("Expected a finished mark, saw ${events.map { it.summary }}")
    }

    private companion object {
        const val MARK = "checkout"
        const val EVENT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
