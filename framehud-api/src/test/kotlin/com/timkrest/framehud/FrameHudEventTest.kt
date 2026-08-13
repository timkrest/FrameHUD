package com.timkrest.framehud

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameHudEventTest {

    @Test
    fun `a first frame names the screen and time to display`() {
        val event = FrameHudEvent.FirstFrame(timeToDisplayMs = 123.45f, screen = "MainActivity")
        assertEquals("MainActivity: first frame in 123.4 ms", event.summary)
    }

    @Test
    fun `a finished screen is summed up in one line`() {
        val event = FrameHudEvent.ScreenEnded(stats = stats(), screen = "MainActivity")
        assertEquals("MainActivity: 120 frames in 2.0s, jank 7.5%, p95 18.0 ms, frozen 1", event.summary)
    }

    @Test
    fun `a jank burst carries the diagnosis`() {
        val event = FrameHudEvent.JankBurst(diagnosis = diagnosis(), screen = "Feed", mark = null)
        assertEquals("Feed: jank 25.0%, worst 42.0 ms of 16.7 ms — cpu bound, 19.3 ms per frame", event.summary)
    }

    @Test
    fun `events fired without a bound screen say so`() {
        val event = FrameHudEvent.FrozenFrames(count = 2, screen = null, mark = null)
        assertTrue(event.summary.startsWith("no screen: "))
        assertEquals(
            "Feed: 2 frozen frame(s)",
            FrameHudEvent.FrozenFrames(count = 2, screen = "Feed", mark = null).summary,
        )
    }

    @Test
    fun `an open mark narrows the summary down from the screen to the interaction`() {
        val event = FrameHudEvent.FrozenFrames(count = 2, screen = "Feed", mark = "scroll")
        assertEquals("Feed/scroll: 2 frozen frame(s)", event.summary)

        val unbound = FrameHudEvent.FrozenFrames(count = 2, screen = null, mark = "scroll")
        assertEquals("scroll: 2 frozen frame(s)", unbound.summary)
    }

    @Test
    fun `the measurement context follows the origin in brackets`() {
        val context = mapOf("variant" to "new_checkout", "scenario" to "smoke")
        val event = FrameHudEvent.FrozenFrames(count = 2, screen = "Feed", mark = "scroll", context = context)
        assertEquals("Feed/scroll [variant=new_checkout, scenario=smoke]: 2 frozen frame(s)", event.summary)
    }

    @Test
    fun `summaries keep their dots on a comma locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertTrue(FrameHudEvent.ScreenEnded(stats(), screen = "Feed").summary.contains("2.0s"))
            assertTrue(FrameHudEvent.JankBurst(diagnosis(), "Feed", mark = null).summary.contains("jank 25.0%"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun stats() = SessionStats.EMPTY.copy(
        frames = 120,
        durationMs = 2_000L,
        p95FrameMs = 18f,
        jankPercent = 7.5f,
        frozenFrames = 1,
    )

    private fun diagnosis() = JankDiagnosis(
        cause = JankCause.Stage(stage = PipelineStage.CPU, averageMs = 19.3f),
        severity = JankSeverity.SEVERE,
        jankPercent = 25f,
        worstFrameMs = 42f,
        frameBudgetMs = 16.7f,
    )
}
