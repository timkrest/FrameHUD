package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimitTest {

    private val clock = TestMetricsClock()
    private val limit = RateLimit(clock, INTERVAL_MS)

    @Test
    fun `a fresh limit takes at once`() {
        assertTrue(limit.tryTake())
    }

    @Test
    fun `a take before the interval is refused without putting the next one off`() {
        limit.tryTake()

        clock.elapsedMs += INTERVAL_MS - 1
        assertFalse(limit.tryTake())

        clock.elapsedMs += 1
        assertTrue(limit.tryTake())
    }

    @Test
    fun `a cleared limit takes at once again`() {
        limit.tryTake()

        limit.clear()

        assertTrue(limit.tryTake())
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
    }
}
