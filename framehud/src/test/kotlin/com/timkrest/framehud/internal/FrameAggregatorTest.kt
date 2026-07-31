package com.timkrest.framehud.internal

import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.PerformanceMetrics
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameAggregatorTest {

    private val clock = TestMetricsClock().apply { elapsedMs = START_MS }
    private val aggregator = FrameAggregator(FrameHudConfig(), clock)

    @Test
    fun `a frame that lands on the deadline is not janky, a nanosecond past it is`() {
        aggregator.addFrame(totalMs = 8.3f, deadlineNs = DEADLINE_60HZ_NS, totalDurationNs = DEADLINE_60HZ_NS)
        assertEquals(0f, aggregator.metrics.value.window.jankPercent, TOLERANCE)

        advancePastThrottle()
        aggregator.addFrame(totalMs = 8.3f, deadlineNs = DEADLINE_60HZ_NS, totalDurationNs = DEADLINE_60HZ_NS + 1)
        assertEquals(50f, aggregator.metrics.value.window.jankPercent, TOLERANCE)
    }

    @Test
    fun `without a deadline the budget follows the refresh rate`() {
        aggregator.addFrame(totalMs = 10f, refreshRateHz = 120f)

        val metrics = aggregator.metrics.value
        assertEquals(120f, metrics.display.refreshRateHz, TOLERANCE)
        assertEquals(8.333f, metrics.display.frameBudgetMs, TOLERANCE)
        assertEquals(100f, metrics.window.jankPercent, TOLERANCE)
    }

    @Test
    fun `a display reporting no refresh rate falls back to the configured one`() {
        aggregator.addFrame(totalMs = 10f, refreshRateHz = FrameAggregator.NO_REFRESH_RATE)

        assertEquals(60f, aggregator.metrics.value.display.refreshRateHz, TOLERANCE)
    }

    @Test
    fun `readings are held back until the throttle interval passes`() {
        aggregator.addFrame(totalMs = 10f)
        clock.elapsedMs += FrameHudConfig.DEFAULT_METRICS_THROTTLE_INTERVAL_MS - 1
        aggregator.addFrame(totalMs = 40f)
        assertEquals(10f, aggregator.metrics.value.phases.total.current, TOLERANCE)

        clock.elapsedMs += 1
        aggregator.addFrame(totalMs = 40f)
        assertEquals(40f, aggregator.metrics.value.phases.total.current, TOLERANCE)
    }

    @Test
    fun `frozen readings stop moving while frames keep being counted`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)
        aggregator.setFrozen(true)

        advancePastThrottle()
        aggregator.addFrame(totalMs = 40f)

        assertEquals(10f, aggregator.metrics.value.phases.total.current, TOLERANCE)
        assertEquals(2, aggregator.sessionStats().frames)
    }

    @Test
    fun `a frame with headroom reports a negative overrun, peak included`() {
        aggregator.addFrame(totalMs = 10f, deadlineNs = DEADLINE_60HZ_NS, totalDurationNs = 10_000_000L)

        val overrun = aggregator.metrics.value.phases.overrun
        assertEquals(-6.667f, overrun.current, TOLERANCE)
        assertEquals(-6.667f, overrun.peak ?: 0f, TOLERANCE)
    }

    @Test
    fun `the tick stops publishing once the last frame has aged out of the window`() {
        aggregator.addFrame(totalMs = 10f)

        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()
        assertEquals(0, aggregator.metrics.value.window.fps)

        // The drain is over, so nothing else reaches the readings until a frame arrives.
        aggregator.addDroppedReports(3)
        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()
        assertEquals(0, aggregator.metrics.value.session.droppedReports)
    }

    @Test
    fun `a fresh frame restarts the drain`() {
        aggregator.addFrame(totalMs = 10f)
        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()
        assertEquals(0, aggregator.metrics.value.window.fps)

        aggregator.addFrame(totalMs = 10f)
        advancePastThrottle()
        aggregator.onTick()
        assertEquals(1, aggregator.metrics.value.window.fps)
    }

    @Test
    fun `dropped reports are counted for the session and for the screen`() {
        aggregator.startCollecting()
        aggregator.addDroppedReports(2)
        aggregator.addFrame(totalMs = 10f)

        assertEquals(2, aggregator.sessionStats().droppedReports)
        assertEquals(2, aggregator.screenStats().droppedReports)
    }

    @Test
    fun `every screen starts over while the session keeps counting`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)
        assertEquals(1, aggregator.screenStats().frames)

        aggregator.startCollecting()
        assertEquals(0, aggregator.screenStats().frames)
        assertEquals(1, aggregator.sessionStats().frames)
    }

    @Test
    fun `resizing the window drops the frames it holds and keeps the session`() {
        aggregator.addFrame(totalMs = 10f)
        advancePastThrottle()
        aggregator.addFrame(totalMs = 12f)

        aggregator.updateConfig(FrameHudConfig(metricsSampleWindowSize = 4))
        advancePastThrottle()
        aggregator.addFrame(totalMs = 14f)

        assertEquals(1, aggregator.metrics.value.window.history.size)
        assertEquals(3, aggregator.sessionStats().frames)
    }

    @Test
    fun `reset empties the readings and the aggregates`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 40f)

        aggregator.reset()

        assertEquals(PerformanceMetrics.EMPTY, aggregator.metrics.value)
        assertEquals(0, aggregator.sessionStats().frames)
        assertNull(aggregator.metrics.value.phases.total.peak)
    }

    private fun advancePastThrottle() {
        clock.elapsedMs += FrameHudConfig.DEFAULT_METRICS_THROTTLE_INTERVAL_MS
    }

    private fun advance(millis: Long) {
        clock.elapsedMs += millis
        clock.nanos += millis * NS_PER_MS_LONG
    }

    private fun FrameAggregator.addFrame(
        totalMs: Float,
        deadlineNs: Long = FrameAggregator.NO_DEADLINE,
        totalDurationNs: Long = (totalMs * NS_PER_MS).toLong(),
        refreshRateHz: Float = 60f,
    ) {
        val durationsMs = FloatArray(FramePhase.entries.size)
        durationsMs[FramePhase.TOTAL.ordinal] = totalMs
        addFrame(
            durationsMs = durationsMs,
            totalDurationNs = totalDurationNs,
            deadlineNs = deadlineNs,
            frameEndNs = clock.nanos,
            refreshRateHz = refreshRateHz,
        )
    }

    private companion object {
        /** Past the throttle interval, so the very first frame publishes like it does on a device. */
        const val START_MS = 10_000L
        const val DEADLINE_60HZ_NS = 16_666_666L
        const val TOLERANCE = 0.001f
        const val NS_PER_MS_LONG = 1_000_000L

        /** Long enough for every frame to fall out of the one-second FPS window. */
        const val PAST_FPS_WINDOW_MS = 1_500L
    }
}
