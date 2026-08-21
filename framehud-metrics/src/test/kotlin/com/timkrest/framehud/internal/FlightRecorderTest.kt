package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class FlightRecorderTest {

    private val clock = TestMetricsClock()

    private val asked = mutableListOf<String>()

    private val recorder = FlightRecorder(clock) { asked += it }

    @Test
    fun `the trace is asked to retain what it holds`() {
        recorder.retainTrace("framehud_incident")

        assertEquals(listOf("framehud_incident"), asked)
    }

    @Test
    fun `a burst of incidents asks once, not once each`() {
        repeat(20) {
            clock.elapsedMs += 100
            recorder.retainTrace("framehud_incident")
        }

        assertEquals(1, asked.size)
        assertEquals(1, recorder.timesAsked)
    }

    @Test
    fun `an incident long after the last one asks again`() {
        recorder.retainTrace("framehud_incident")
        clock.elapsedMs += 5_000

        recorder.retainTrace("framehud_incident")

        assertEquals(2, asked.size)
        assertEquals(2, recorder.timesAsked)
    }

    @Test
    fun `a reset lets the next incident ask straight away`() {
        recorder.retainTrace("framehud_incident")

        recorder.reset()
        recorder.retainTrace("framehud_incident")

        assertEquals(2, asked.size)
        assertEquals(1, recorder.timesAsked)
    }
}
