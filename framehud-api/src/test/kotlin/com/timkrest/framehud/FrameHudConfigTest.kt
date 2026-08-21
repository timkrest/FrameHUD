package com.timkrest.framehud

import org.junit.Test
import kotlin.test.assertFailsWith

class FrameHudConfigTest {

    @Test
    fun `a window of no frames is rejected where it is written, not clamped where it is read`() {
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(metricsSampleWindowFrames = 0) }
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(metricsSampleWindowFrames = -1) }
    }

    @Test
    fun `a negative throttle is rejected, zero means every reading`() {
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(metricsThrottleIntervalMs = -1L) }
        FrameHudConfig(metricsThrottleIntervalMs = 0L)
    }

    @Test
    fun `a frame budget no frame can fit into is rejected`() {
        listOf(0, -8).forEach { budget ->
            assertFailsWith<IllegalArgumentException>("accepted $budget ms") {
                FrameHudConfig(frameBudgetsMs = mapOf(IntervalId.Screen("feed") to budget))
            }
        }
    }

    @Test
    fun `a fallback refresh rate the frame budget cannot divide by is rejected`() {
        listOf(0f, -60f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN).forEach { rate ->
            assertFailsWith<IllegalArgumentException>("accepted $rate Hz") {
                FrameHudConfig(fallbackRefreshRateHz = rate)
            }
        }
    }
}
