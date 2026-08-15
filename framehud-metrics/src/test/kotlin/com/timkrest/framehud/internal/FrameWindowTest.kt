package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class FrameWindowTest {

    private val window = FrameWindow(size = 8)

    private fun addFrame(totalMs: Float, overrunMs: Float = -1f, frameEndNs: Long = 0L) {
        val durations = FloatArray(FramePhase.entries.size)
        durations[FramePhase.TOTAL.ordinal] = totalMs
        window.add(durationsMs = durations, overrunMs = overrunMs, frameEndNs = frameEndNs)
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
    fun `total percentile and worst come from the total ring`() {
        (1..8).forEach { addFrame(totalMs = it.toFloat()) }
        assertEquals(8f, window.worstTotalMs(), TOLERANCE)
        assertEquals(4f, window.totalPercentile(50f), TOLERANCE)
    }

    @Test
    fun `history pairs each frame with the deadline it had, oldest first`() {
        addFrame(totalMs = 1f, overrunMs = -5f)
        addFrame(totalMs = 2f, overrunMs = -4f)
        addFrame(totalMs = 20f, overrunMs = 3f)
        val history = window.history()
        assertEquals(listOf(1f, 2f, 20f), List(history.size, history::totalMsAt))
        assertEquals(listOf(6f, 6f, 17f), List(history.size, history::deadlineMsAt))
    }

    @Test
    fun `clear drops accumulated frames`() {
        addFrame(totalMs = 10f, overrunMs = 1f, frameEndNs = 1L)
        window.clear()
        assertEquals(0f, window.jankPercent(), TOLERANCE)
        assertEquals(0, window.fps(nowNs = 1L))
        assertEquals(0, window.history().size)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
