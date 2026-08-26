package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameAggregatorTest {

    private val clock = TestMetricsClock().apply { elapsedMs = FIRST_SAMPLE_TIME_MS }
    private val aggregator = FrameAggregator(FrameHudConfig(), clock, isEmulator = false)

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
        aggregator.addFrame(totalMs = 10f, refreshRateHz = UNKNOWN_REFRESH_RATE_HZ)

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
    fun `a frozen panel holds its published reading, tracks the live one, and releases it on thaw`() {
        aggregator.addFrame(totalMs = 10f)
        aggregator.setFrozen(true)

        advancePastThrottle()
        aggregator.addFrame(totalMs = 40f)

        assertEquals(10f, aggregator.metrics.value.phases.total.current, TOLERANCE)
        assertEquals(40f, aggregator.liveMetrics.phases.total.current, TOLERANCE)

        aggregator.setFrozen(false)

        assertEquals(40f, aggregator.metrics.value.phases.total.current, TOLERANCE)
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
    }

    @Test
    fun `dropped reports alone do not restart publishing once the tick has drained`() {
        aggregator.addFrame(totalMs = 10f)
        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()

        aggregator.addDroppedReports(screen = aggregator.screenName, count = 3)
        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()

        assertEquals(0, aggregator.metrics.value.session.droppedReports, "a drained tick published without a frame")
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
        aggregator.addDroppedReports(screen = aggregator.screenName, count = 2)
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
    fun `restarting the screen hands back its stats and starts the next one empty`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)
        advancePastThrottle()
        aggregator.addFrame(totalMs = 40f)

        val ended = aggregator.restartScreen()

        assertEquals(2, ended.frames)
        assertEquals(0, aggregator.screenStats().frames)
        assertEquals(2, aggregator.sessionStats().frames)
    }

    @Test
    fun `a restarted screen inherits the standing throttle and starts clean after a cool sample`() {
        aggregator.startCollecting()
        aggregator.addThermalLevel(ThermalLevel.SEVERE)
        aggregator.addFrame(totalMs = 10f)

        aggregator.restartScreen()
        assertTrue(hasThermalIssue(aggregator.screenStats()))

        aggregator.addThermalLevel(ThermalLevel.NONE)
        aggregator.restartScreen()
        assertFalse(hasThermalIssue(aggregator.screenStats()))
        assertTrue(hasThermalIssue(aggregator.sessionStats()))
    }

    @Test
    fun `a mark inherits the standing throttle and starts clean after a cool sample`() {
        aggregator.startCollecting()
        aggregator.addThermalLevel(ThermalLevel.SEVERE)

        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)
        assertTrue(hasThermalIssue(assertNotNull(aggregator.endMark())))

        aggregator.addThermalLevel(ThermalLevel.NONE)
        aggregator.beginMark("scroll")
        advancePastThrottle()
        aggregator.addFrame(totalMs = 10f)
        assertFalse(hasThermalIssue(assertNotNull(aggregator.endMark())))
    }

    @Test
    fun `a screen started after a gap in collection does not inherit the pre-gap throttle`() {
        aggregator.startCollecting()
        aggregator.addThermalLevel(ThermalLevel.SEVERE)
        aggregator.stopCollecting()

        aggregator.startCollecting()

        assertFalse(hasThermalIssue(aggregator.screenStats()))
    }

    @Test
    fun `reset keeps the standing environment for the next session`() {
        aggregator.startCollecting()
        aggregator.addThermalLevel(ThermalLevel.SEVERE)
        aggregator.addBattery(BatterySample(powerSaveMode = true, levelPercent = 10))

        aggregator.reset()

        val issues = aggregator.sessionStats().confidence.issues
        assertTrue(issues.any { it is ConfidenceIssue.ThermalThrottling })
        assertTrue(issues.any { it is ConfidenceIssue.LowBattery })
    }

    @Test
    fun `an export reads fresh metrics without waiting for the throttle`() {
        aggregator.addFrame(totalMs = 10f)
        aggregator.addFrame(totalMs = 40f)
        assertEquals(10f, aggregator.metrics.value.phases.total.current, TOLERANCE)

        assertEquals(40f, aggregator.refreshMetricsIgnoringThrottle().phases.total.current, TOLERANCE)
    }

    @Test
    fun `the worst frames survive screen restarts and clear on reset`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)
        aggregator.restartScreen()
        advancePastThrottle()
        aggregator.addFrame(totalMs = 800f)

        assertEquals(listOf(800f, 10f), aggregator.worstFrames().map { it.totalMs })

        aggregator.reset()
        assertEquals(emptyList(), aggregator.worstFrames())
    }

    @Test
    fun `a mark covers the frames drawn while it was open and nothing before it`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)

        aggregator.beginMark("scroll")
        advancePastThrottle()
        aggregator.addFrame(totalMs = 40f)
        advancePastThrottle()
        aggregator.addFrame(totalMs = 40f)

        val stats = assertNotNull(aggregator.endMark())
        assertEquals(2, stats.frames)
        assertEquals(100f, stats.jankPercent, TOLERANCE)
        assertEquals(3, aggregator.sessionStats().frames)
    }

    @Test
    fun `beginning a mark twice reports only the frames after the second one`() {
        aggregator.startCollecting()
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)

        aggregator.beginMark("scroll")
        advancePastThrottle()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(1, assertNotNull(aggregator.endMark()).frames)
    }

    @Test
    fun `resizing the window drops the frames it holds and keeps the session`() {
        aggregator.addFrame(totalMs = 10f)
        advancePastThrottle()
        aggregator.addFrame(totalMs = 12f)

        aggregator.updateConfig(FrameHudConfig(metricsSampleWindowFrames = 4))
        advancePastThrottle()
        aggregator.addFrame(totalMs = 14f)

        assertEquals(1, aggregator.metrics.value.window.history.size)
        assertEquals(3, aggregator.sessionStats().frames)
    }

    @Test
    fun `a still screen stops publishing but keeps the live session clock the diagnosis divides by`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)
        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()
        val drained = aggregator.liveMetrics.session.durationMs

        advance(PAST_FPS_WINDOW_MS)
        aggregator.onTick()

        assertEquals(drained + PAST_FPS_WINDOW_MS, aggregator.liveMetrics.session.durationMs)
        assertEquals(drained, aggregator.metrics.value.session.durationMs, "a still screen published again")
    }

    @Test
    fun `resizing the window keeps the peaks, which count since the reset and not since the window`() {
        aggregator.addFrame(totalMs = 40f)

        aggregator.updateConfig(FrameHudConfig(metricsSampleWindowFrames = 4))

        assertEquals(40f, aggregator.metrics.value.phases.total.peak ?: 0f, TOLERANCE)
    }

    @Test
    fun `resizing the window publishes the empty one instead of the readings it dropped`() {
        aggregator.addFrame(totalMs = 10f)

        aggregator.updateConfig(FrameHudConfig(metricsSampleWindowFrames = 4))

        assertEquals(0, aggregator.metrics.value.window.history.size)
        assertEquals(0, aggregator.metrics.value.window.fps)
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

    @Test
    fun `a reset keeps the display the run measured, so the budget it leaves behind matches it`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f, refreshRateHz = 120f)

        aggregator.reset()

        val metrics = aggregator.metrics.value
        assertEquals(120f, metrics.display.refreshRateHz, TOLERANCE)
        assertEquals(metrics.display.frameBudgetMs, metrics.window.frameBudgetMs, TOLERANCE)
    }

    @Test
    fun `a second visit to a screen adds to the interval the first one started`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)
        aggregator.restartScreen("Checkout")
        aggregator.addFrame(totalMs = 10f)
        aggregator.restartScreen("Home")
        aggregator.addFrame(totalMs = 10f)

        assertEquals(2, aggregator.interval(IntervalId.Screen("Home")).frames)
        assertEquals(1, aggregator.interval(IntervalId.Screen("Checkout")).frames)
        assertEquals(3, aggregator.interval(IntervalId.Session).frames)
    }

    @Test
    fun `a jank streak does not continue across separate visits to a screen`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 40f)
        aggregator.restartScreen("Checkout")
        aggregator.addFrame(totalMs = 10f)
        aggregator.restartScreen("Home")
        aggregator.addFrame(totalMs = 40f)

        assertEquals(1, aggregator.interval(IntervalId.Screen("Home")).maxJankStreak)
    }

    @Test
    fun `a mark collects every stretch it covered under one name`() {
        aggregator.startCollecting("Home")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)
        aggregator.endMark()
        aggregator.addFrame(totalMs = 10f)
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)

        assertEquals(2, aggregator.interval(IntervalId.Mark("scroll")).frames)
    }

    @Test
    fun `a frame another window drew counts there and in the session, not on the screen in view`() {
        aggregator.startCollecting("Home")
        aggregator.beginWindow("promo")
        aggregator.addFrame(totalMs = 10f)
        aggregator.addFrame(totalMs = 40f, screen = "promo")

        assertEquals(1, aggregator.interval(IntervalId.Screen("Home")).frames)
        assertEquals(1, aggregator.interval(IntervalId.Screen("promo")).frames)
        assertEquals(2, aggregator.interval(IntervalId.Session).frames)
        assertEquals(1, aggregator.screenStats().frames)
    }

    @Test
    fun `a frame another window drew stays out of the incident window`() {
        aggregator.startCollecting("Home")
        aggregator.beginWindow("promo")
        aggregator.addFrame(totalMs = 10f)
        aggregator.armIncident(
            trigger = FrameHudEvent.FrozenFrames(count = 1, screen = "Home", mark = null),
            readings = IncidentReadings(
                memory = MemoryStats.EMPTY,
                thermal = ThermalStats.EMPTY,
                process = ProcessStats.EMPTY,
                battery = BatterySample.UNKNOWN,
                counters = emptyList(),
                mainThreadBlock = MainThreadBlock.NONE,
            ),
        )
        aggregator.addFrame(totalMs = 400f, screen = "promo")
        aggregator.addFrame(totalMs = 20f)

        val frames = aggregator.incidents().single().worst.frames
        assertContentEquals(listOf(10f, 20f), List(frames.size) { frames.totalMsAt(it) })
    }

    @Test
    fun `a frame another window drew stays out of the rolling window`() {
        aggregator.startCollecting("Home")
        aggregator.beginWindow("promo")
        aggregator.addFrame(totalMs = 10f)
        aggregator.addFrame(totalMs = 400f, screen = "promo")
        advancePastThrottle()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(2, aggregator.metrics.value.window.history.size)
        assertEquals(10f, aggregator.metrics.value.window.worstFrameMs, TOLERANCE)
    }

    @Test
    fun `a mark leaves out what another window drew while it ran`() {
        aggregator.startCollecting("Home")
        aggregator.beginWindow("promo")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)
        aggregator.addFrame(totalMs = 10f, screen = "promo")

        assertEquals(1, aggregator.interval(IntervalId.Mark("scroll")).frames)
    }

    @Test
    fun `a window counts the time it was measured, not the time the screen was`() {
        aggregator.startCollecting("Home")
        advance(100)
        aggregator.beginWindow("promo")
        advance(400)
        aggregator.addFrame(totalMs = 10f, screen = "promo")
        aggregator.endWindow("promo")
        advance(100)

        assertEquals(400L, aggregator.interval(IntervalId.Screen("promo")).durationMs)
        assertEquals(600L, aggregator.interval(IntervalId.Screen("Home")).durationMs)
    }

    @Test
    fun `a forgotten window keeps what it drew and counts nothing after`() {
        aggregator.startCollecting("Home")
        aggregator.beginWindow("promo")
        aggregator.addFrame(totalMs = 10f, screen = "promo")
        aggregator.endWindow("promo")
        aggregator.addFrame(totalMs = 10f, screen = "promo")

        assertEquals(1, aggregator.interval(IntervalId.Screen("promo")).frames)
        assertEquals(2, aggregator.interval(IntervalId.Session).frames)
    }

    @Test
    fun `a window named like the screen in view cannot end that screen`() {
        aggregator.startCollecting("Home")
        aggregator.endWindow("Home")
        aggregator.addFrame(totalMs = 10f)

        assertEquals(1, aggregator.interval(IntervalId.Screen("Home")).frames)
    }

    @Test
    fun `a window measured across the background skips the time the app spent there`() {
        aggregator.startCollecting("Home")
        aggregator.beginWindow("promo")
        aggregator.addFrame(totalMs = 10f, screen = "promo")
        aggregator.stopCollecting()
        advance(1_000)
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f, screen = "promo")

        assertEquals(2, aggregator.interval(IntervalId.Screen("promo")).frames)
        assertEquals(0L, aggregator.interval(IntervalId.Screen("promo")).durationMs)
    }

    @Test
    fun `frames drawn while no mark is set belong to no mark`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)

        assertTrue(aggregator.intervals().none { it.id is IntervalId.Mark })
    }

    @Test
    fun `a reset drops the intervals and keeps collecting the screen in view`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)
        aggregator.restartScreen("Checkout")
        aggregator.addFrame(totalMs = 10f)

        aggregator.reset()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(listOf(IntervalId.Session, IntervalId.Screen("Checkout")), aggregator.intervals().map { it.id })
        assertEquals(1, aggregator.interval(IntervalId.Screen("Checkout")).frames)
    }

    @Test
    fun `an unnamed screen has no interval of its own`() {
        aggregator.startCollecting()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(listOf(IntervalId.Session), aggregator.intervals().map { it.id })
    }

    @Test
    fun `the budget key follows the deadline that judged the frames, not the reported rate`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 5f, deadlineNs = DEADLINE_60HZ_NS / 2, refreshRateHz = UNKNOWN_REFRESH_RATE_HZ)

        assertEquals(8, aggregator.intervals().first { it.id == IntervalId.Session }.frameBudgetMs)
    }

    @Test
    fun `an interval carries the budget that judged nearly all its frames`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f, refreshRateHz = 60f)
        aggregator.restartScreen("Checkout")
        aggregator.addFrame(totalMs = 10f, refreshRateHz = 120f)

        val budgets = aggregator.intervals().associate { it.id to it.frameBudgetMs }
        assertEquals(17, budgets[IntervalId.Screen("Home")])
        assertEquals(8, budgets[IntervalId.Screen("Checkout")])
        assertNull(budgets[IntervalId.Session])
    }

    @Test
    fun `a screen with a budget of its own is judged by it while the session keeps the display's`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 8)
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)

        val home = aggregator.interval(IntervalId.Screen("Home"))
        assertEquals(100f, home.jankPercent, TOLERANCE)
        assertEquals(2f, home.lostTimeMs, TOLERANCE)
        assertEquals(0f, aggregator.interval(IntervalId.Session).jankPercent, TOLERANCE)
    }

    @Test
    fun `the rolling window is judged by the budget of the screen drawing it`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 8)
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)

        val window = aggregator.metrics.value.window
        assertEquals(8f, window.frameBudgetMs, TOLERANCE)
        assertEquals(100f, window.jankPercent, TOLERANCE)
    }

    @Test
    fun `a mark judges its frames by its own budget, not by the one its screen carries`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 20, IntervalId.Mark("scroll") to 8)
        aggregator.startCollecting("Home")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)

        assertEquals(100f, aggregator.interval(IntervalId.Mark("scroll")).jankPercent, TOLERANCE)
        assertEquals(0f, aggregator.interval(IntervalId.Screen("Home")).jankPercent, TOLERANCE)
        assertEquals(8f, aggregator.metrics.value.window.frameBudgetMs, TOLERANCE)
    }

    @Test
    fun `the rolling window keeps the budget its frames were judged by until newer ones arrive`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 20, IntervalId.Mark("scroll") to 8)
        aggregator.startCollecting("Home")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)
        aggregator.endMark()

        assertEquals(8f, aggregator.refreshMetricsIgnoringThrottle().window.frameBudgetMs, TOLERANCE)

        advancePastThrottle()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(20f, aggregator.metrics.value.window.frameBudgetMs, TOLERANCE)
    }

    @Test
    fun `a budget set while a screen draws judges the frames that follow it`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)
        aggregator.updateConfig(FrameHudConfig(frameBudgetsMs = mapOf(IntervalId.Screen("Home") to 8)))
        advancePastThrottle()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(50f, aggregator.interval(IntervalId.Screen("Home")).jankPercent, TOLERANCE)
        assertNull(aggregator.intervals().first { it.id == IntervalId.Screen("Home") }.frameBudgetMs)
        assertEquals(8f, aggregator.metrics.value.window.frameBudgetMs, TOLERANCE)
    }

    @Test
    fun `a session budget judges the screens that carry none of their own`() {
        val aggregator = aggregatorBudgeting(IntervalId.Session to 8)
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)

        assertEquals(100f, aggregator.interval(IntervalId.Session).jankPercent, TOLERANCE)
        assertEquals(100f, aggregator.interval(IntervalId.Screen("Home")).jankPercent, TOLERANCE)
    }

    @Test
    fun `a mark without a budget of its own is judged by the screen it runs on`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 8)
        aggregator.startCollecting("Home")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 10f)

        assertEquals(100f, aggregator.interval(IntervalId.Mark("scroll")).jankPercent, TOLERANCE)
        assertEquals(8, aggregator.intervals().first { it.id == IntervalId.Mark("scroll") }.frameBudgetMs)
    }

    @Test
    fun `a mark that ran under two budgets names neither`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 8)
        aggregator.startCollecting("Home")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 5f)
        aggregator.endMark()
        aggregator.restartScreen("Checkout")
        aggregator.beginMark("scroll")
        aggregator.addFrame(totalMs = 5f)

        assertNull(aggregator.intervals().first { it.id == IntervalId.Mark("scroll") }.frameBudgetMs)
    }

    @Test
    fun `a reset leaves behind the budget the next frame will be judged by`() {
        val aggregator = aggregatorBudgeting(IntervalId.Screen("Home") to 8)
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)

        aggregator.reset()

        assertEquals(8f, aggregator.metrics.value.window.frameBudgetMs, TOLERANCE)
        assertEquals(0, aggregator.metrics.value.window.history.size)
    }

    @Test
    fun `screens past the tracked limit are dropped rather than remembered`() {
        aggregator.startCollecting("Home")
        repeat(40) { index ->
            aggregator.restartScreen("screen-$index")
            aggregator.addFrame(totalMs = 10f)
        }

        val screens = aggregator.intervals().filter { it.id is IntervalId.Screen }
        assertEquals(32, screens.size)
        assertEquals(40, aggregator.interval(IntervalId.Session).frames)
    }

    @Test
    fun `reset starts tracking the active screen after the name limit dropped it`() {
        aggregator.startCollecting("Home")
        repeat(32) { index -> aggregator.restartScreen("screen-$index") }

        aggregator.reset()
        aggregator.addFrame(totalMs = 10f)

        assertEquals(1, aggregator.interval(IntervalId.Screen("screen-31")).frames)
    }

    @Test
    fun `a reset while nothing is collecting leaves the interval stopped`() {
        aggregator.startCollecting("Home")
        aggregator.addFrame(totalMs = 10f)
        aggregator.stopCollecting()

        aggregator.reset()
        advance(1_000)

        assertEquals(0L, aggregator.interval(IntervalId.Screen("Home")).durationMs)
    }

    private fun aggregatorBudgeting(vararg budgets: Pair<IntervalId, Int>) =
        FrameAggregator(FrameHudConfig(frameBudgetsMs = budgets.toMap()), clock, isEmulator = false)

    private fun FrameAggregator.interval(id: IntervalId): IntervalStats =
        assertNotNull(intervals().firstOrNull { it.id == id }, "no interval for $id").stats

    private fun hasThermalIssue(stats: IntervalStats): Boolean = stats.confidence.issues.any { it is ConfidenceIssue.ThermalThrottling }

    private fun advancePastThrottle() {
        clock.elapsedMs += FrameHudConfig.DEFAULT_METRICS_THROTTLE_INTERVAL_MS
    }

    private fun advance(millis: Long) {
        clock.elapsedMs += millis
        clock.nanos += millis * NS_PER_MS_LONG
    }

    private fun FrameAggregator.addFrame(
        totalMs: Float,
        screen: String? = screenName,
        deadlineNs: Long = NO_DEADLINE_NS,
        totalDurationNs: Long = (totalMs * NS_PER_MS).toLong(),
        refreshRateHz: Float = 60f,
    ) {
        addFrame(
            screen = screen,
            durationsMs = phaseDurationsMs(totalMs),
            totalDurationNs = totalDurationNs,
            deadlineNs = deadlineNs,
            frameEndNs = clock.nanos,
            refreshRateHz = refreshRateHz,
        )
    }

    private companion object {
        const val FIRST_SAMPLE_TIME_MS = FrameHudConfig.DEFAULT_METRICS_THROTTLE_INTERVAL_MS
        const val DEADLINE_60HZ_NS = 16_666_666L
        const val TOLERANCE = 0.001f

        const val PAST_FPS_WINDOW_MS = 1_500L
    }
}
