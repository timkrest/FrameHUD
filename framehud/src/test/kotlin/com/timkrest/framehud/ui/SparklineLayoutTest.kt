package com.timkrest.framehud.ui

import com.timkrest.framehud.FrameHistory
import org.junit.Test
import kotlin.test.assertEquals

class SparklineLayoutTest {

    @Test
    fun `a chart narrower than the window keeps the worst frame of every slice`() {
        val history = historyOf(
            totalsMs = floatArrayOf(10f, 50f, 10f, 10f, 10f, 10f, 10f, 10f),
            deadlinesMs = FloatArray(8) { 16f },
        )
        val frames = worstFramePerSlot(history = history, slotCount = 4)
        assertEquals(listOf(50f, 10f, 10f, 10f), List(frames.size, frames::totalMsAt))
    }

    @Test
    fun `the frame a bar keeps is the one that missed its own deadline by the most`() {
        val history = historyOf(totalsMs = floatArrayOf(40f, 30f), deadlinesMs = floatArrayOf(39f, 10f))
        val frames = worstFramePerSlot(history = history, slotCount = 1)
        assertEquals(30f, frames.totalMsAt(0), TOLERANCE)
        assertEquals(10f, frames.deadlineMsAt(0), TOLERANCE)
    }

    @Test
    fun `the scale doubles in steps so the budget line holds still between readings`() {
        assertEquals(20f, fullHeightFor(peakMs = 12f), TOLERANCE)
        assertEquals(20f, fullHeightFor(peakMs = 20f), TOLERANCE)
        assertEquals(40f, fullHeightFor(peakMs = 21f), TOLERANCE)
        assertEquals(80f, fullHeightFor(peakMs = 45f), TOLERANCE)
    }

    @Test
    fun `the scale stops growing so one stall cannot flatten the rest`() {
        assertEquals(160f, fullHeightFor(peakMs = 5_000f), TOLERANCE)
    }

    private fun historyOf(totalsMs: FloatArray, deadlinesMs: FloatArray): FrameHistory =
        FrameHistory.of(totalsMs = totalsMs, deadlinesMs = deadlinesMs)

    private fun fullHeightFor(peakMs: Float): Float = budgetDoublingCovering(
        frames = historyOf(totalsMs = floatArrayOf(peakMs), deadlinesMs = floatArrayOf(BUDGET_MS)),
        frameBudgetMs = BUDGET_MS,
    )

    private companion object {
        const val BUDGET_MS = 10f
        const val TOLERANCE = 0.0001f
    }
}
