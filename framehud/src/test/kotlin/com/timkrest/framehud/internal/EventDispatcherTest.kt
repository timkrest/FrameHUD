package com.timkrest.framehud.internal

import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventDispatcherTest {

    private val dispatcher = EventDispatcher()
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
    fun `a screen without frames ends without a summary`() {
        dispatcher.onScreenEnded(listeners = listeners, stats = SessionStats.EMPTY, screen = SCREEN)
        assertTrue(events.isEmpty())

        dispatcher.onScreenEnded(listeners, SessionStats.EMPTY.copy(frames = 12), SCREEN)
        assertIs<FrameHudEvent.ScreenEnded>(events.single())
    }

    @Test
    fun `a finished screen does not restart burst tracking`() {
        sample(jankPercent = 30f, frozenFrames = 4)
        dispatcher.onScreenEnded(listeners, SessionStats.EMPTY.copy(frames = 4), SCREEN)
        events.clear()

        sample(jankPercent = 30f, frozenFrames = 4)
        assertTrue(events.isEmpty())
    }

    private fun sample(
        jankPercent: Float = 0f,
        frozenFrames: Int = 0,
        thermal: ThermalStats = ThermalStats.EMPTY,
        vsyncRate: Int = 60,
    ) {
        dispatcher.onSample(
            listeners = listeners,
            metrics = PerformanceMetrics(
                phases = FramePhases(draw = MetricValue(average = 12f)),
                window = FrameWindowStats(jankPercent = jankPercent),
                session = SessionStats.EMPTY.copy(frames = 100, durationMs = 1_000L, frozenFrames = frozenFrames),
                display = DisplayInfo(refreshRateHz = 60f),
            ),
            memory = MemoryStats.EMPTY,
            thermal = thermal,
            vsyncRate = vsyncRate,
            screen = SCREEN,
        )
    }

    private companion object {
        const val SCREEN = "SampleActivity"
    }
}
