package com.timkrest.framehud.ui

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals

class PanelColorsTest {

    @Test
    fun `fps is judged against the refresh rate, not a fixed sixty`() {
        assertEquals(TextGood, fpsColor(fps = 60, refreshRateHz = 60f))
        assertEquals(TextWarning, fpsColor(fps = 60, refreshRateHz = 120f))
        assertEquals(TextNormal, fpsColor(fps = 100, refreshRateHz = 120f))
        assertEquals(TextGood, fpsColor(fps = 118, refreshRateHz = 120f))
        assertEquals(TextHeader, fpsColor(fps = 0, refreshRateHz = 120f))
    }

    @Test
    fun `overrun reacts to any missed deadline, not only to a whole budget lost`() {
        assertEquals(TextGood, overrunColor(overrunMs = -4f))
        assertEquals(TextGood, overrunColor(overrunMs = 0f))
        assertEquals(TextCaution, overrunColor(overrunMs = 4f))
        assertEquals(TextWarning, overrunColor(overrunMs = 20f))
    }

    @Test
    fun `a phase row only turns amber when it is the one to look at`() {
        assertEquals(TextNormal, phaseColor(valueMs = 4f, isAttention = false))
        assertEquals(TextCaution, phaseColor(valueMs = 4f, isAttention = true))
        assertEquals(TextWarning, phaseColor(valueMs = 20f, isAttention = true))
    }

    private fun overrunColor(overrunMs: Float): Color = metricRowColor(
        valueMs = overrunMs,
        frameBudgetMs = FRAME_BUDGET_MS,
        kind = MetricRowKind.OVERRUN,
        isAttention = false,
    )

    private fun phaseColor(valueMs: Float, isAttention: Boolean): Color = metricRowColor(
        valueMs = valueMs,
        frameBudgetMs = FRAME_BUDGET_MS,
        kind = MetricRowKind.PHASE,
        isAttention = isAttention,
    )

    private companion object {
        const val FRAME_BUDGET_MS = 16.7f
    }
}
