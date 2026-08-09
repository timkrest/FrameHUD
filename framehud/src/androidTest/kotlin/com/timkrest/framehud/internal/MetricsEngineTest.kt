package com.timkrest.framehud.internal

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MetricsEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val threadName = "framehud-engine-${NEXT_ID.getAndIncrement()}"

    private val events = CopyOnWriteArrayList<FrameHudEvent>()

    private val deliveryThreads = CopyOnWriteArrayList<String>()

    private var config = FrameHudConfig(
        metricsThreadName = threadName,
        eventListeners = listOf(
            FrameHudEventListener {
                deliveryThreads += Thread.currentThread().name
                events += it
            },
        ),
    )

    private val engine = MetricsEngine(config = { config })

    @After
    fun stopEngine() {
        onMainThread { engine.stop() }
    }

    @Test
    fun thereAreNoSessionStatsBeforeTheEngineStarts() {
        assertNull(engine.awaitSessionStats(TIMEOUT_MS), "an engine that never started answered")
    }

    @Test
    fun theAggregatesStayReadableAfterStop() {
        onMainThread { engine.start(context) }
        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "nothing was collecting")

        onMainThread { engine.stop() }

        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "the aggregates went away with the last activity")
    }

    @Test
    fun aMarkBegunBeforeTheFirstScreenSurvivesTheFocusSwapThatStartsCollection() {
        onMainThread { engine.setMark(MARK) }
        onMainThread { engine.unbindWindow() }
        onMainThread { engine.start(context) }
        onMainThread { engine.setMark(null) }
        awaitMetricsThread()

        val ended = assertIs<FrameHudEvent.MarkEnded>(events.single())
        assertEquals(MARK, ended.mark)
    }

    @Test
    fun settingTheSameMarkAgainLeavesTheIntervalRunning() {
        onMainThread { engine.start(context) }
        onMainThread { engine.setMark(MARK) }
        onMainThread { engine.setMark(MARK) }
        awaitMetricsThread()

        assertTrue(events.isEmpty(), "the interval was cut in two: $events")
    }

    @Test
    fun aMarkEndedBeforeTheStartStillReachesListenersOnTheMetricsThread() {
        onMainThread { engine.setMark(MARK) }
        onMainThread { engine.setMark(null) }
        awaitMetricsThread()

        assertIs<FrameHudEvent.MarkEnded>(events.single())
        assertEquals(listOf(threadName), deliveryThreads.distinct(), "an event reached a listener off the metrics thread")
    }

    @Test
    fun anEngineConfiguredBeforeTheStartRunsNoMetricsThread() {
        engine.reset()
        onMainThread { engine.applyConfig(config) }

        assertEquals(0, threadsNamed(threadName), "a metrics thread was started before the panel")

        onMainThread { engine.start(context) }
        awaitMetricsThread()

        assertEquals(1, threadsNamed(threadName), "the engine is running more than one metrics thread")
    }

    @Test
    fun renamingTheMetricsThreadRetiresTheOldOne() {
        onMainThread { engine.start(context) }
        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "nothing was collecting")

        config = config.copy(metricsThreadName = "$threadName-renamed")
        onMainThread { engine.applyConfig(config) }

        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "the renamed thread answers nothing")
        assertTrue(awaitThreadGone(threadName), "the retired metrics thread is still running")
    }

    private fun onMainThread(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun awaitMetricsThread() {
        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "the metrics thread never answered")
    }

    private fun threadsNamed(name: String): Int = Thread.getAllStackTraces().keys.count { it.name == name }

    private fun awaitThreadGone(name: String): Boolean {
        val deadlineMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (Thread.getAllStackTraces().keys.none { it.name == name }) return true
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private companion object {
        val NEXT_ID = AtomicInteger()

        const val MARK = "scroll"
        const val TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
