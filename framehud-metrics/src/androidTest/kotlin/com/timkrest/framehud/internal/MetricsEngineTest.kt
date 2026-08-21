package com.timkrest.framehud.internal

import android.app.Dialog
import android.content.Context
import android.os.SystemClock
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.BlankActivity
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.await
import com.timkrest.framehud.awaitFrames
import com.timkrest.framehud.postFrames
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
    fun thereAreNoStatsBeforeTheEngineStarts() {
        assertNull(await { engine.sessionStats() }, "an engine that never started answered")
    }

    @Test
    fun theAggregatesStayReadableAfterStop() {
        onMainThread { engine.start(context) }
        assertNotNull(await { engine.sessionStats() }, "nothing was collecting")

        onMainThread { engine.stop() }

        assertNotNull(await { engine.sessionStats() }, "the aggregates went away with the last activity")
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
    fun onceTheScreenIsGoneItNamesTheNumbersItLeftBehindAndNothingElse() {
        onMainThread { engine.start(context) }
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.onActivity { engine.bindWindow(it.window, screen = SCREEN, start = null) }
            onMainThread { engine.unbindWindow() }
        }
        onMainThread { engine.setMark(MARK) }
        onMainThread { engine.setMark(null) }
        awaitMetricsThread()

        val export = assertNotNull(await { engine.exportStats() }, "the metrics thread never answered")
        assertEquals(SCREEN, export.screenName, "the export lost the name of the screen its numbers came from")
        val ended = events.filterIsInstance<FrameHudEvent.MarkEnded>().single()
        assertNull(ended.screen, "the mark was named after a screen that was already gone")
    }

    @Test
    fun aScreenTheAppNamedItselfIsNoLongerActiveOnceItsWindowIsGone() {
        onMainThread { engine.start(context) }
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            onMainThread { engine.setScreen(SCREEN) }
            scenario.onActivity { engine.bindWindow(it.window, screen = "BlankActivity", start = null) }
            onMainThread { engine.unbindWindow() }
        }
        onMainThread { engine.setMark(MARK) }
        onMainThread { engine.setMark(null) }
        awaitMetricsThread()

        val ended = events.filterIsInstance<FrameHudEvent.MarkEnded>().single()
        assertNull(ended.screen, "a screen the app named outlived the window it was named for")
        assertEquals(SCREEN, engine.screenOverride, "the name the app set has to hold until it sets another")
    }

    @Test
    fun aWindowTheAppHandsOverIsMeasuredAsAScreenOfItsOwn() {
        onMainThread { engine.start(context) }
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.onActivity { engine.bindWindow(it.window, screen = SCREEN, start = null) }
            lateinit var dialog: Dialog
            lateinit var drawn: CountDownLatch
            scenario.onActivity { activity ->
                dialog = Dialog(activity).apply { show() }
                val window = assertNotNull(dialog.window, "the dialog has no window to measure")
                engine.measureWindow(window, WINDOW)
                drawn = window.postFrames(DRAWN_FRAMES)
            }
            awaitFrames(drawn, DRAWN_FRAMES)
            awaitMetricsThread()
            val intervals = assertNotNull(await { engine.intervals() }, "the metrics thread never answered")
            onMainThread { dialog.dismiss() }

            val measured = assertNotNull(
                intervals.firstOrNull { it.id == IntervalId.Screen(WINDOW) },
                "the window the app handed over is missing from $intervals",
            )
            assertTrue(measured.stats.frames > 0, "the window the app handed over drew nothing FrameHud saw")
        }
    }

    @Test
    fun aWindowTheAppTakesBackStopsCounting() {
        onMainThread { engine.start(context) }
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.onActivity { engine.bindWindow(it.window, screen = SCREEN, start = null) }
            lateinit var window: Window
            lateinit var dialog: Dialog
            lateinit var drawn: CountDownLatch
            scenario.onActivity { activity ->
                dialog = Dialog(activity).apply { show() }
                window = assertNotNull(dialog.window, "the dialog has no window to measure")
                engine.measureWindow(window, WINDOW)
                drawn = window.postFrames(DRAWN_FRAMES)
            }
            awaitFrames(drawn, DRAWN_FRAMES)
            onMainThread { engine.forgetWindow(window) }
            awaitMetricsThread()
            val measured = framesOn(IntervalId.Screen(WINDOW))
            assertTrue(measured > 0, "the window the app handed over drew nothing FrameHud saw")

            scenario.onActivity { drawn = window.postFrames(DRAWN_FRAMES) }
            awaitFrames(drawn, DRAWN_FRAMES)
            awaitMetricsThread()
            onMainThread { dialog.dismiss() }

            assertEquals(measured, framesOn(IntervalId.Screen(WINDOW)), "a window the app took back kept counting")
        }
    }

    @Test
    fun whatTheAppWritesToACounterReachesTheReadingsThePanelCollects() {
        engine.counter(COUNTER).set(7)
        onMainThread { engine.start(context) }
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.onActivity { engine.bindWindow(it.window, screen = SCREEN, start = null) }

            val readings = await { engine.counters.first { it.isNotEmpty() } }

            onMainThread { engine.unbindWindow() }
            assertEquals(listOf(CounterReading(COUNTER, value = 7, peakSinceReset = 7)), readings)
        }
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
        assertNotNull(await { engine.sessionStats() }, "nothing was collecting")

        config = config.copy(metricsThreadName = "$threadName-renamed")
        onMainThread { engine.applyConfig(config) }

        assertNotNull(await { engine.sessionStats() }, "the renamed thread answers nothing")
        assertTrue(awaitThreadGone(threadName), "the retired metrics thread is still running")
    }

    private fun framesOn(id: IntervalId): Int {
        val intervals = assertNotNull(await { engine.intervals() }, "the metrics thread never answered")
        return assertNotNull(intervals.firstOrNull { it.id == id }, "$id is missing from $intervals").stats.frames
    }

    @Test
    fun aFailureOfItsOwnReachesListenersOnceForEachCallItFailedIn() {
        onMainThread { engine.start(context) }

        engine.onInternalFailure(FAILED_AT, NoSuchMethodError())
        engine.onInternalFailure(FAILED_AT, NoSuchMethodError())
        engine.onInternalFailure(FAILED_AT_ANOTHER_CALL, IllegalStateException())
        awaitMetricsThread()

        val failures = events.filterIsInstance<FrameHudEvent.InternalFailure>()
        assertEquals(listOf(FAILED_AT, FAILED_AT_ANOTHER_CALL), failures.map { it.what })
    }

    @Test
    fun aResetLetsTheSameFailureBeHeardAgain() {
        onMainThread { engine.start(context) }
        engine.onInternalFailure(FAILED_AT, NoSuchMethodError())
        awaitMetricsThread()

        engine.reset()
        engine.onInternalFailure(FAILED_AT, NoSuchMethodError())
        awaitMetricsThread()

        assertEquals(2, events.filterIsInstance<FrameHudEvent.InternalFailure>().size, "$events")
    }

    private fun onMainThread(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun awaitMetricsThread() {
        assertNotNull(await { engine.sessionStats() }, "the metrics thread never answered")
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
        const val SCREEN = "checkout"
        const val WINDOW = "checkout/promo"
        const val COUNTER = "decode queue"
        const val DRAWN_FRAMES = 20
        const val FAILED_AT = "reading frame metrics"
        const val FAILED_AT_ANOTHER_CALL = "sampling the main thread"
        const val TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
