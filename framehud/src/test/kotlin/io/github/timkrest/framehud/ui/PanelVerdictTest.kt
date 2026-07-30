package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.PerformanceMetrics
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PanelVerdictTest {

    @Test
    fun `a healthy window has nothing to point at`() {
        assertEquals(PanelVerdict.Ok, panelVerdict(metrics(jankPercent = 4.9f, drawMs = 20f)))
    }

    @Test
    fun `the slowest phase is blamed`() {
        val verdict = assertIs<PanelVerdict.Attention>(panelVerdict(metrics(jankPercent = 10f, drawMs = 9f)))
        assertEquals(LABEL_DRAW, verdict.phaseLabel)
        assertEquals(9f, verdict.phaseAvgMs, TOLERANCE)
    }

    @Test
    fun `delay is blamed too, though it belongs to no stage`() {
        val verdict = assertIs<PanelVerdict.Attention>(
            panelVerdict(metrics(jankPercent = 10f, drawMs = 4f, unknownDelayMs = 11f)),
        )
        assertEquals(LABEL_DELAY, verdict.phaseLabel)
    }

    private fun metrics(
        jankPercent: Float,
        drawMs: Float = 0f,
        unknownDelayMs: Float = 0f,
    ) = PerformanceMetrics(
        unknownDelay = MetricValue(average = unknownDelayMs),
        draw = MetricValue(average = drawMs),
        windowJankPercent = jankPercent,
        frameBudgetMs = 16.7f,
    )

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
