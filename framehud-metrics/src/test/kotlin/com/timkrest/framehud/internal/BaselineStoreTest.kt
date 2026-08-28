package com.timkrest.framehud.internal

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.PhaseAverages
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaselineStoreTest {

    @Test
    fun `a baseline drops its least measured intervals rather than growing past what it can read back`() {
        val baseline = Baseline(
            environment = RECORDED_ENVIRONMENT,
            entries = buildMap {
                put(IntervalId.Session, entry(runs = RUNS_A_LONG_HISTORY))
                put(IntervalId.Screen(WELL_MEASURED), entry(runs = RUNS_A_LONG_HISTORY))
                repeat(SEEN_ONCE) { index -> put(IntervalId.Screen("product/$index"), entry(runs = 1)) }
            },
        )

        val written = assertNotNull(read(assertNotNull(baseline.fittingJson()).decodeToString()))

        assertTrue(written.entries.size < baseline.entries.size, "nothing was dropped")
        assertEquals(
            setOf(IntervalId.Session, IntervalId.Screen(WELL_MEASURED)),
            written.entries.filterValues { it.runs == RUNS_A_LONG_HISTORY }.keys,
        )
    }

    private fun read(json: String): Baseline? = (parseBaseline(json) as? Parsed.Read)?.value

    private fun entry(runs: Int): BaselineEntry = BaselineEntry.of(
        stats = IntervalStats.EMPTY.copy(frames = 100, p95FrameMs = 10f, phases = PhaseAverages.of(total = 12f)),
        runs = runs,
    )

    private companion object {
        const val WELL_MEASURED = "checkout"
        const val RUNS_A_LONG_HISTORY = 9
        const val SEEN_ONCE = 6_000
    }
}
