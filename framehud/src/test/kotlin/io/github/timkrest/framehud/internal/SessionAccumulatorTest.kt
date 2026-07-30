package io.github.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class SessionAccumulatorTest {

    @Test
    fun `jank percent is the share of janky frames`() {
        val session = SessionAccumulator()
        session.addFrame(totalMs = 10f, isJanky = true)
        session.addFrame(totalMs = 10f, isJanky = false)
        session.addFrame(totalMs = 10f, isJanky = true)
        session.addFrame(totalMs = 10f, isJanky = false)
        assertEquals(50f, session.stats().jankPercent, TOLERANCE)
    }

    @Test
    fun `frozen frames are counted above 700 ms`() {
        val session = SessionAccumulator()
        session.addFrame(totalMs = 699f, isJanky = true)
        session.addFrame(totalMs = 701f, isJanky = true)
        assertEquals(1, session.stats().frozenFrames)
    }

    @Test
    fun `max jank streak tracks the longest run`() {
        val session = SessionAccumulator()
        listOf(true, true, false, true).forEach { session.addFrame(totalMs = 10f, isJanky = it) }
        assertEquals(2, session.stats().maxJankStreak)
    }

    @Test
    fun `percentiles come from the frame histogram clamped to the observed max`() {
        val session = SessionAccumulator()
        repeat(99) { session.addFrame(totalMs = 10f, isJanky = false) }
        session.addFrame(totalMs = 100f, isJanky = true)
        assertEquals(100, session.stats().frames)
        assertEquals(10.25f, session.stats().p50FrameMs, TOLERANCE)
    }

    @Test
    fun `duration is zero when collection never started`() {
        val session = SessionAccumulator()
        session.addFrame(totalMs = 10f, isJanky = false)
        assertEquals(0L, session.stats().durationMs)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
