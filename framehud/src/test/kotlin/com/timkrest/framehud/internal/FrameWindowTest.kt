package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class FrameWindowTest {

    private val window = FrameWindow(size = 8)

    private fun addFrame(
        totalMs: Float,
        isJanky: Boolean = false,
        frameEndNs: Long = 0L,
    ) {
        val durations = FloatArray(FramePhase.entries.size)
        durations[FramePhase.TOTAL.ordinal] = totalMs
        window.add(durationsMs = durations, isJanky = isJanky, overrunMs = 0f, frameEndNs = frameEndNs)
    }

    @Test
    fun `fps counts frames completed within the last second`() {
        val second = 1_000_000_000L
        addFrame(totalMs = 10f, frameEndNs = second / 2)
        addFrame(totalMs = 10f, frameEndNs = second)
        addFrame(totalMs = 10f, frameEndNs = second * 2)
        assertEquals(2, window.fps(nowNs = second * 2))
        assertEquals(1, window.fps(nowNs = second * 3))
        assertEquals(0, window.fps(nowNs = second * 4))
    }

    @Test
    fun `jank percent reflects the window share`() {
        addFrame(totalMs = 10f, isJanky = true)
        addFrame(totalMs = 10f)
        addFrame(totalMs = 10f)
        addFrame(totalMs = 10f, isJanky = true)
        assertEquals(50f, window.jankPercent(), TOLERANCE)
    }

    @Test
    fun `total percentile and worst come from the total ring`() {
        (1..8).forEach { addFrame(totalMs = it.toFloat()) }
        assertEquals(8f, window.worstTotalMs(), TOLERANCE)
        assertEquals(4f, window.totalPercentile(50f), TOLERANCE)
    }

    @Test
    fun `history is ordered oldest to newest`() {
        listOf(1f, 2f, 3f).forEach { addFrame(totalMs = it) }
        assertEquals(listOf(1f, 2f, 3f), window.totalHistory().toList())
    }

    @Test
    fun `clear drops accumulated frames`() {
        addFrame(totalMs = 10f, isJanky = true, frameEndNs = 1L)
        window.clear()
        assertEquals(0f, window.jankPercent(), TOLERANCE)
        assertEquals(0, window.fps(nowNs = 1L))
        assertEquals(0, window.totalHistory().size)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
