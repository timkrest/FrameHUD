package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class LatencyHistogramTest {

    @Test
    fun `empty histogram returns zero`() {
        assertEquals(0f, LatencyHistogram().percentile(50f), TOLERANCE)
    }

    @Test
    fun `percentile returns the fine bucket upper bound`() {
        val histogram = LatencyHistogram()
        repeat(99) { histogram.add(10f) }
        histogram.add(100f)
        assertEquals(10.25f, histogram.percentile(50f), TOLERANCE)
    }

    @Test
    fun `tail percentile is clamped to the observed max`() {
        val histogram = LatencyHistogram()
        repeat(99) { histogram.add(10f) }
        histogram.add(100f)
        assertEquals(100f, histogram.percentile(100f), TOLERANCE)
    }

    @Test
    fun `boundary value lands in the first tail bucket`() {
        val histogram = LatencyHistogram()
        histogram.add(64f)
        assertEquals(64f, histogram.percentile(99f), TOLERANCE)
    }

    @Test
    fun `a frame past the last bucket still counts toward the percentiles`() {
        val histogram = LatencyHistogram()
        repeat(9) { histogram.add(10f) }
        histogram.add(OUTSIDE_HISTOGRAM_RANGE_MS)

        assertEquals(10, histogram.count)
        assertEquals(10.25f, histogram.percentile(50f), TOLERANCE)
        assertEquals(OUTSIDE_HISTOGRAM_RANGE_MS, histogram.percentile(100f), TOLERANCE)
    }

    @Test
    fun `clear empties every bucket`() {
        val histogram = LatencyHistogram()
        listOf(5f, 80f).forEach(histogram::add)
        histogram.clear()
        assertEquals(0, histogram.count)
        assertEquals(0f, histogram.percentile(50f), TOLERANCE)
        assertEquals(0f, histogram.percentile(99f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f

        const val OUTSIDE_HISTOGRAM_RANGE_MS = 5_000_000f
    }
}
