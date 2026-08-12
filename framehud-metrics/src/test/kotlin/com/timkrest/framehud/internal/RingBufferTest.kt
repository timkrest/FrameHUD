package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RingBufferTest {

    @Test
    fun `empty buffer returns zeros`() {
        val buffer = RingBuffer(capacity = 4)
        assertEquals(0f, buffer.average(), TOLERANCE)
        assertEquals(0f, buffer.last(), TOLERANCE)
        assertEquals(0f, buffer.windowMax(), TOLERANCE)
        assertEquals(0f, buffer.percentile(50f), TOLERANCE)
        assertEquals(0, buffer.snapshot().size)
        assertNull(buffer.peak)
    }

    @Test
    fun `average uses only the current window after wrap`() {
        val buffer = RingBuffer(capacity = 4)
        listOf(1f, 2f, 3f, 4f, 5f, 6f).forEach(buffer::add)
        assertEquals((3f + 4f + 5f + 6f) / 4f, buffer.average(), TOLERANCE)
    }

    @Test
    fun `last returns the newest value`() {
        val buffer = RingBuffer(capacity = 3)
        listOf(1f, 2f, 3f, 4f).forEach(buffer::add)
        assertEquals(4f, buffer.last(), TOLERANCE)
    }

    @Test
    fun `the window max is bounded by the window while the peak survives eviction`() {
        val buffer = RingBuffer(capacity = 2)
        buffer.add(10f)
        buffer.add(1f)
        buffer.add(2f)
        assertEquals(2f, buffer.windowMax(), TOLERANCE)
        assertEquals(10f, buffer.peak)
    }

    @Test
    fun `a peak stays the highest sample even when every sample is negative`() {
        val buffer = RingBuffer(capacity = 4)
        listOf(-8f, -2f, -5f).forEach(buffer::add)
        assertEquals(-2f, buffer.peak)
        assertEquals(-2f, buffer.windowMax(), TOLERANCE)
        assertEquals(-5f, buffer.percentile(50f), TOLERANCE)
    }

    @Test
    fun `the window is read from the oldest sample after wrapping`() {
        val buffer = RingBuffer(capacity = 3)
        listOf(5f, 4f, 3f, 2f, 1f).forEach(buffer::add)
        assertEquals(listOf(3f, 2f, 1f), buffer.snapshot().toList())
        assertEquals(3f, buffer.windowMax(), TOLERANCE)
        assertEquals(1f, buffer.percentile(0f), TOLERANCE)
        assertEquals(2f, buffer.percentile(50f), TOLERANCE)
    }

    @Test
    fun `percentile uses nearest rank`() {
        val buffer = RingBuffer(capacity = 10)
        (1..10).forEach { buffer.add(it.toFloat()) }
        assertEquals(5f, buffer.percentile(50f), TOLERANCE)
        assertEquals(10f, buffer.percentile(95f), TOLERANCE)
        assertEquals(1f, buffer.percentile(0f), TOLERANCE)
        assertEquals(10f, buffer.percentile(100f), TOLERANCE)
    }

    @Test
    fun `percentile ignores values the window has evicted`() {
        val buffer = RingBuffer(capacity = 4)
        listOf(100f, 200f, 300f, 400f).forEach(buffer::add)
        assertEquals(200f, buffer.percentile(50f), TOLERANCE)

        listOf(1f, 2f).forEach(buffer::add)
        assertEquals(2f, buffer.percentile(50f), TOLERANCE)
    }

    @Test
    fun `snapshot preserves insertion order after wrap`() {
        val buffer = RingBuffer(capacity = 3)
        listOf(1f, 2f, 3f, 4f).forEach(buffer::add)
        assertEquals(listOf(2f, 3f, 4f), buffer.snapshot().toList())
    }

    @Test
    fun `clear resets the window and the peak`() {
        val buffer = RingBuffer(capacity = 3)
        buffer.add(10f)
        buffer.clear()
        assertEquals(0, buffer.size)
        assertEquals(0f, buffer.average(), TOLERANCE)
        assertNull(buffer.peak)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
