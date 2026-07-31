package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class FrameTimestampsTest {

    @Test
    fun `countSince counts timestamps at or after the cutoff`() {
        val timestamps = FrameTimestamps(capacity = 8)
        listOf(100L, 200L, 300L).forEach(timestamps::add)
        assertEquals(2, timestamps.countSince(150L))
        assertEquals(3, timestamps.countSince(100L))
        assertEquals(0, timestamps.countSince(301L))
    }

    @Test
    fun `wrap evicts the oldest timestamps`() {
        val timestamps = FrameTimestamps(capacity = 3)
        listOf(1L, 2L, 3L, 4L).forEach(timestamps::add)
        assertEquals(3, timestamps.countSince(2L))
        assertEquals(2, timestamps.countSince(3L))
    }
}
