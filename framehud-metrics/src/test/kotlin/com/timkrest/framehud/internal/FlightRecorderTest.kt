package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    }

    @Test
    fun `an incident long after the last one asks again`() {
        recorder.retainTrace("framehud_incident")
        clock.elapsedMs += 5_000

        recorder.retainTrace("framehud_incident")

        assertEquals(2, asked.size)
        assertEquals(2, recorder.recordingFor(null)?.timesAsked)
    }

    @Test
    fun `a reset lets the next incident ask straight away`() {
        recorder.retainTrace("framehud_incident")

        recorder.reset()
        recorder.retainTrace("framehud_incident")

        assertEquals(2, asked.size)
        assertEquals(1, recorder.recordingFor(null)?.timesAsked)
    }

    @Test
    fun `another trace is asked at once rather than waiting out the one before it`() {
        recorder.retainTrace("framehud_incident")

        recorder.retainTrace("framehud_scroll")

        assertEquals(listOf("framehud_incident", "framehud_scroll"), asked)
    }

    @Test
    fun `a report counts the asks against the trace they were made to`() {
        recorder.retainTrace("framehud_incident")
        recorder.retainTrace("framehud_scroll")

        val recording = recorder.recordingFor("framehud_incident")

        assertEquals("framehud_scroll", recording?.trigger)
        assertEquals(1, recording?.timesAsked)
    }

    @Test
    fun `a trace asked again after another one keeps what it was asked before`() {
        recorder.retainTrace("framehud_incident")
        recorder.retainTrace("framehud_scroll")

        recorder.retainTrace("framehud_incident")

        val recording = recorder.recordingFor(null)
        assertEquals("framehud_incident", recording?.trigger)
        assertEquals(2, recording?.timesAsked)
    }

    @Test
    fun `a trigger switched off after an incident still says the trace was asked`() {
        recorder.retainTrace("framehud_incident")

        val recording = recorder.recordingFor(null)

        assertEquals("framehud_incident", recording?.trigger)
        assertEquals(1, recording?.timesAsked)
    }

    @Test
    fun `a trigger nothing has reached yet is named with nothing to its account`() {
        val recording = recorder.recordingFor("framehud_incident")

        assertEquals("framehud_incident", recording?.trigger)
        assertEquals(0, recording?.timesAsked)
    }

    @Test
    fun `a run with no trigger and no ask records nothing`() {
        assertNull(recorder.recordingFor(null))
    }
}
