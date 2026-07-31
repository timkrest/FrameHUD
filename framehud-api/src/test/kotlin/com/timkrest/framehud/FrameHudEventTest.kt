package com.timkrest.framehud

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameHudEventTest {

    @Test
    fun `a finished screen is summed up in one line`() {
        val event = FrameHudEvent.ScreenEnded(stats = stats(), screen = "MainActivity")
        assertEquals("MainActivity: 120 frames in 2.0s, jank 7.5%, p95 18.0 ms, frozen 1", event.summary)
    }

    @Test
    fun `a jank burst carries the diagnosis`() {
        val event = FrameHudEvent.JankBurst(diagnosis = diagnosis(), screen = "Feed")
        assertEquals("Feed: jank 25.0%, worst 42.0 ms of 16.7 ms — cpu bound, 19.3 ms per frame", event.summary)
    }

    @Test
    fun `events fired without a bound screen say so`() {
        assertTrue(FrameHudEvent.FrozenFrames(count = 2, screen = null).summary.startsWith("no screen: "))
        assertEquals("Feed: 2 frozen frame(s)", FrameHudEvent.FrozenFrames(count = 2, screen = "Feed").summary)
    }

    @Test
    fun `summaries keep their dots on a comma locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertTrue(FrameHudEvent.ScreenEnded(stats(), screen = "Feed").summary.contains("2.0s"))
            assertTrue(FrameHudEvent.JankBurst(diagnosis(), screen = "Feed").summary.contains("jank 25.0%"))
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
        cause = JankCause.Stage(stage = PipelineStage.CPU, avgMs = 19.3f),
        severity = JankSeverity.SEVERE,
        jankPercent = 25f,
        worstFrameMs = 42f,
        frameBudgetMs = 16.7f,
    )
}
