package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class WorstFramesTest {

    @Test
    fun `keeps only the slowest frames, worst first`() {
        val frames = WorstFrames(capacity = 3)
        frames.add(totalMs = 10f, endNs = 1L)
        frames.add(totalMs = 40f, endNs = 2L)
        frames.add(totalMs = 20f, endNs = 3L)
        frames.add(totalMs = 30f, endNs = 4L)
        frames.add(totalMs = 5f, endNs = 5L)

        assertEquals(listOf(40f, 30f, 20f), frames.snapshot().map { it.totalMs })
        assertEquals(listOf(2L, 4L, 3L), frames.snapshot().map { it.endNs })
    }

    @Test
    fun `clearing forgets every frame`() {
        val frames = WorstFrames(capacity = 3)
        frames.add(totalMs = 10f, endNs = 1L)

        frames.clear()

        assertEquals(emptyList(), frames.snapshot())
    }
}
