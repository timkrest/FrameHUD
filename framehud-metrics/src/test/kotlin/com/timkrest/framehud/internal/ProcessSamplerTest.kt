package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcessSamplerTest {

    private val clock = TestMetricsClock()
    private val probe = FakeProcessProbe()
    private val sampler = ProcessSampler(clock, probe)

    @Test
    fun `the first sample has nothing to measure the cpu against`() {
        probe.cpuTimeMs = 100L

        assertNull(sampler.sample().cpuPercent)
    }

    @Test
    fun `cpu reads as the share of one core used since the previous sample`() {
        sampler.sample()
        clock.elapsedMs += 1_000L
        probe.cpuTimeMs = 420L

        assertEquals(42f, sampler.sample().cpuPercent)
    }

    @Test
    fun `peaks hold the highest reading so far`() {
        probe.pssMb = 210
        sampler.sample()
        probe.pssMb = 180

        val stats = sampler.sample()
        assertEquals(180, stats.pssMb)
        assertEquals(210, stats.peakPssMb)
    }

    @Test
    fun `a figure the platform will not report stays null, peak included`() {
        val stats = sampler.sample()

        assertNull(stats.openFiles)
        assertNull(stats.peakOpenFiles)
    }

    @Test
    fun `resetting forgets the peaks and what the cpu was measured against`() {
        probe.pssMb = 210
        sampler.sample()
        clock.elapsedMs += 1_000L
        probe.cpuTimeMs = 420L
        sampler.sample()

        sampler.reset()
        probe.pssMb = 180
        val stats = sampler.sample()

        assertEquals(180, stats.peakPssMb)
        assertNull(stats.cpuPercent)
    }

    private class FakeProcessProbe : ProcessProbe {
        var cpuTimeMs: Long? = 0L
        var pssMb: Int? = null
        var threads: Int? = null
        var openFiles: Int? = null

        override fun cpuTimeMs(): Long? = cpuTimeMs
        override fun pssMb(): Int? = pssMb
        override fun threads(): Int? = threads
        override fun openFiles(): Int? = openFiles
    }
}
