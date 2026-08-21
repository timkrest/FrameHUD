package com.timkrest.framehud.internal

import com.timkrest.framehud.CounterReading
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CounterRegistryTest {

    private val registry = CounterRegistry()

    @Test
    fun `the same name answers with the counter that already carries the value`() {
        registry.counter("decode queue").add(3)
        registry.counter("decode queue").add(4)
        registry.sample()

        assertEquals(listOf(CounterReading("decode queue", value = 7, peakSinceReset = 7)), registry.liveCounters)
    }

    @Test
    fun `a value that falls back leaves the peak where it reached`() {
        val queue = registry.counter("decode queue")
        queue.set(31)
        queue.set(4)
        registry.sample()

        val reading = registry.liveCounters.single()
        assertEquals(4, reading.value)
        assertEquals(31, reading.peakSinceReset)
    }

    @Test
    fun `a reset drops the peak to what the counter reads now`() {
        val queue = registry.counter("decode queue")
        queue.set(31)
        queue.set(4)

        registry.reset()

        assertEquals(4, registry.liveCounters.single().peakSinceReset)
    }

    @Test
    fun `counters past the tracked limit are dropped rather than remembered`() {
        repeat(20) { registry.counter("counter-$it").add(1) }
        registry.sample()

        val readings = registry.liveCounters
        assertEquals(16, readings.size)
        assertNull(readings.firstOrNull { it.name == "counter-16" })
    }

    @Test
    fun `a name a trace could not tell apart is rejected where the app writes it`() {
        assertFailsWith<IllegalArgumentException> { registry.counter("q".repeat(MAX_TRACE_NAME_LENGTH + 1)) }
    }
}
