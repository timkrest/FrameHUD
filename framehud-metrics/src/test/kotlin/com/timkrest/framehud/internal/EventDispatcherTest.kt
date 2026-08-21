package com.timkrest.framehud.internal

import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventDispatcherTest {

    private val clock = TestMetricsClock()
    private val slowListenerReports = mutableListOf<Float>()
    private val dispatcher = EventDispatcher(clock = clock, onSlowListener = { slowListenerReports += it })
    private val events = mutableListOf<FrameHudEvent>()
    private val listeners = listOf(FrameHudEventListener { events += it })

    @Test
    fun `a burst is reported once until it clears`() {
        sample(jankPercent = 30f)
        sample(jankPercent = 40f)
        assertEquals(1, events.count { it is FrameHudEvent.JankBurst })

        sample(jankPercent = 0f)
        sample(jankPercent = 30f)
        assertEquals(2, events.count { it is FrameHudEvent.JankBurst })
    }

    @Test
    fun `only newly frozen frames are reported`() {
        sample(frozenFrames = 2)
        sample(frozenFrames = 3)
        val counts = events.filterIsInstance<FrameHudEvent.FrozenFrames>().map { it.count }
        assertEquals(listOf(2, 1), counts)
    }

    @Test
    fun `the first thermal reading stays quiet unless it throttles`() {
        sample(thermal = ThermalStats(level = ThermalLevel.NONE, headroom = null))
        assertTrue(events.isEmpty())

        sample(thermal = ThermalStats(level = ThermalLevel.MODERATE, headroom = null))
        val event = assertIs<FrameHudEvent.ThermalChanged>(events.single())
        assertEquals(ThermalLevel.MODERATE, event.level)
    }

    @Test
    fun `throttling on the first reading is reported`() {
        sample(thermal = ThermalStats(level = ThermalLevel.SEVERE, headroom = null))
        assertIs<FrameHudEvent.ThermalChanged>(events.single())
    }

    @Test
    fun `the sample that starts a burst or freezes frames opens an incident`() {
        assertIs<FrameHudEvent.JankBurst>(sample(jankPercent = 30f, frozenFrames = 1))
        assertNull(sample(jankPercent = 30f))
        assertIs<FrameHudEvent.FrozenFrames>(sample(frozenFrames = 2))
    }

    @Test
    fun `a screen without frames ends without a summary`() {
        dispatcher.onScreenEnded(listeners = listeners, stats = IntervalStats.EMPTY, screen = SCREEN, context = emptyMap())
        assertTrue(events.isEmpty())

        dispatcher.onScreenEnded(listeners, IntervalStats.EMPTY.copy(frames = 12), SCREEN, context = emptyMap())
        assertIs<FrameHudEvent.ScreenEnded>(events.single())
    }

    @Test
    fun `a finished screen does not restart burst tracking`() {
        sample(jankPercent = 30f, frozenFrames = 4)
        dispatcher.onScreenEnded(listeners, IntervalStats.EMPTY.copy(frames = 4), SCREEN, context = emptyMap())
        events.clear()

        sample(jankPercent = 30f, frozenFrames = 4)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a listener at exactly 50 ms is not reported, a millisecond past it is`() {
        val onTimeListener = listOf(FrameHudEventListener { clock.nanos += SLOW_LISTENER_THRESHOLD_NS })
        dispatcher.onScreenEnded(onTimeListener, IntervalStats.EMPTY.copy(frames = 1), SCREEN, context = emptyMap())
        assertTrue(slowListenerReports.isEmpty())

        val slowListener = listOf(FrameHudEventListener { clock.nanos += SLOW_LISTENER_THRESHOLD_NS + NS_PER_MS_LONG })
        dispatcher.onScreenEnded(slowListener, IntervalStats.EMPTY.copy(frames = 1), SCREEN, context = emptyMap())
        assertEquals(1, slowListenerReports.size)
    }

    @Test
    fun `an interaction is reported even when nothing was drawn while it was open`() {
        dispatcher.onMarkEnded(listeners = listeners, stats = IntervalStats.EMPTY, mark = MARK, screen = SCREEN, context = emptyMap())

        val event = assertIs<FrameHudEvent.MarkEnded>(events.single())
        assertEquals(MARK, event.mark)
        assertEquals(SCREEN, event.screen)
    }

    private fun sample(
        jankPercent: Float = 0f,
        frozenFrames: Int = 0,
        thermal: ThermalStats = ThermalStats.EMPTY,
        choreographerTicksPerSecond: Int = 60,
        mark: String? = null,
    ): FrameHudEvent.IncidentTrigger? {
        val metrics = PerformanceMetrics(
            phases = FramePhases(draw = MetricValue(average = 12f)),
            window = FrameWindowStats(jankPercent = jankPercent),
            session = IntervalStats.EMPTY.copy(frames = 100, durationMs = 1_000L, frozenFrames = frozenFrames),
            display = DisplayInfo(refreshRateHz = 60f),
        )
        return dispatcher.onSample(
            listeners = listeners,
            diagnosis = JankDiagnosis.of(
                metrics = metrics,
                memory = MemoryStats.EMPTY,
                thermal = thermal,
                choreographerTicksPerSecond = choreographerTicksPerSecond,
            ),
            frozenFrames = frozenFrames,
            thermalLevel = thermal.level,
            screen = SCREEN,
            mark = mark,
            context = emptyMap(),
        )
    }

    private companion object {
        const val SCREEN = "SampleActivity"
        const val MARK = "scroll"
        const val SLOW_LISTENER_THRESHOLD_NS = 50_000_000L
    }
}
