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
    fun `a fallback refresh rate of zero is rejected, since the frame budget divides by it`() {
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(fallbackRefreshRateHz = 0f) }
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(fallbackRefreshRateHz = -60f) }
    }

    @Test
    fun `an infinite fallback refresh rate is rejected, since it divides down to a zero budget`() {
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(fallbackRefreshRateHz = Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { FrameHudConfig(fallbackRefreshRateHz = Float.NaN) }
    }
}
