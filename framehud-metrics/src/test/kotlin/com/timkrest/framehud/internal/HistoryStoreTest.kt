package com.timkrest.framehud.internal

import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistoryStoreTest {

    @Test
    fun `a run history drops its oldest runs rather than growing past what it can read back`() {
        val runs = List(RUNS) { index ->
            storedRun(runId = "run:$index", recordedAtEpochMs = index + 1L, intervals = crowdedIntervals())
        }

        val written = read(assertNotNull(runs.fittingHistoryJson()).decodeToString()).orEmpty()

        assertTrue(written.size in 1..<RUNS, "kept ${written.size} of $RUNS runs")
        assertEquals(runs.take(written.size), written)
    }

    private fun crowdedIntervals(): List<IntervalReport> =
        listOf(recordedInterval(IntervalId.Session)) +
            List(INTERVALS) { index -> recordedInterval(IntervalId.Mark("mark$index")) }

    private fun read(json: String): List<StoredRun>? = (parseHistory(json) as? Parsed.Read)?.value

    private companion object {
        const val RUNS = 8
        const val INTERVALS = 1_500
    }
}
