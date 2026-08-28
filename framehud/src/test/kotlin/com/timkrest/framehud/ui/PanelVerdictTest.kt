package com.timkrest.framehud.ui

import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.MetricValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PanelVerdictTest {

    @Test
    fun `a healthy window has nothing to point at`() {
        assertEquals(PanelVerdict.Ok, verdict(jankPercent = 4.9f, drawMs = 20f))
    }

    @Test
    fun `the slowest phase is blamed`() {
        val attention = assertIs<PanelVerdict.Attention>(verdict(jankPercent = 10f, drawMs = 9f))
        assertEquals(LABEL_DRAW, attention.phaseLabel)
        assertEquals(9f, attention.phaseAvgMs, TOLERANCE)
    }

    @Test
    fun `delay is blamed too, though it belongs to no stage`() {
        val attention = assertIs<PanelVerdict.Attention>(
            verdict(jankPercent = 10f, drawMs = 4f, unknownDelayMs = 11f),
        )
        assertEquals(LABEL_DELAY, attention.phaseLabel)
    }

    @Test
    fun `the render thread is blamed when it is the slowest`() {
        val attention = assertIs<PanelVerdict.Attention>(verdict(jankPercent = 10f, drawMs = 4f, swapMs = 12f))
        assertEquals(LABEL_SWAP, attention.phaseLabel)
    }

    @Test
    fun `on an emulator the host-rendered phases are left out of the blame`() {
        val attention = assertIs<PanelVerdict.Attention>(
            verdict(jankPercent = 10f, drawMs = 4f, swapMs = 12f, isEmulator = true),
        )
        assertEquals(LABEL_DRAW, attention.phaseLabel)
    }

    private fun verdict(
        jankPercent: Float,
        drawMs: Float = 0f,
        unknownDelayMs: Float = 0f,
        swapMs: Float = 0f,
        isEmulator: Boolean = false,
    ): PanelVerdict = panelVerdict(
        phases = FramePhases.of(
            unknownDelay = MetricValue.of(average = unknownDelayMs),
            draw = MetricValue.of(average = drawMs),
            swapBuffers = MetricValue.of(average = swapMs),
        ),
        jankPercent = jankPercent,
        isEmulator = isEmulator,
    )

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
