package com.timkrest.framehud.internal

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.BaselineTrust
import com.timkrest.framehud.BudgetCandidate
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MeasuredMetric
import com.timkrest.framehud.PhaseAverages
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BaselineJsonTest {

    @Test
    fun `a baseline survives a round trip`() {
        val baseline = Baseline(
            environment = RECORDED_ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to entry(p95FrameMs = 12.5f),
                IntervalId.Screen("product/{id}") to entry(p95FrameMs = 20f),
                IntervalId.Mark("scroll") to entry(p95FrameMs = 30f),
            ),
        )

        assertEquals(baseline, read(baseline.toJson()))
    }

    @Test
    fun `the runs behind each figure survive a round trip`() {
        val baseline = sessionBaseline(
            entry(
                p95FrameMs = 10f,
                runs = 3,
                trust = BaselineTrust(
                    cleanRuns = mapOf(MeasuredMetric.P95 to 0, MeasuredMetric.P99 to 2),
                    gpuRuns = 1,
                ),
            ),
        )

        assertEquals(baseline, read(baseline.toJson()))
    }

    @Test
    fun `the frame budget and the candidate budget survive a round trip`() {
        val baseline = sessionBaseline(candidateEntry())

        assertEquals(baseline, read(baseline.toJson()))
    }

    @Test
    fun `a missing GPU average stays missing`() {
        val baseline = sessionBaseline()

        assertNull(read(baseline.toJson())?.entries?.getValue(IntervalId.Session)?.phases?.gpu)
    }

    @Test
    fun `a file written by another schema is kept for the build that reads it`() {
        val json = sessionBaseline().toJson()
            .replace(""""schema":$BASELINE_SCHEMA_VERSION""", """"schema":${BASELINE_SCHEMA_VERSION + 1}""")

        assertIs<Parsed.Unreadable>(parseBaseline(json))
    }

    @Test
    fun `a file naming no schema is refused`() {
        assertIs<Parsed.Rejected>(parseBaseline("""{"intervals":[]}"""))
    }

    @Test
    fun `a file without an environment is refused`() {
        assertIs<Parsed.Rejected>(parseBaseline("""{"schema":$BASELINE_SCHEMA_VERSION,"intervals":[]}"""))
    }

    @Test
    fun `an interval this build cannot name is left out`() {
        val json = Baseline(
            environment = RECORDED_ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to entry(p95FrameMs = 10f),
                IntervalId.Mark("fling") to entry(p95FrameMs = 20f),
            ),
        ).toJson().replace(""""interval":"mark:fling"""", """"interval":"gesture:fling"""")

        assertEquals(setOf(IntervalId.Session), read(json)?.entries?.keys)
    }

    @Test
    fun `an entry missing a figure rejects the file rather than reading zeros`() {
        val json = sessionBaseline().toJson().replace(""""jankPercent":""", """"wasJankPercent":""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a run count below one rejects the file`() {
        val json = sessionBaseline().toJson().replace(""""runs":1""", """"runs":-1""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `an invalid clean-run count rejects the file rather than counting every run clean`() {
        val json = sessionBaseline().toJson().replace(""""JANK_PERCENT":0""", """"JANK_PERCENT":-1""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `clean runs of the wrong shape reject the file rather than counting every run clean`() {
        val json = sessionBaseline().toJson()
            .replace(""""cleanRuns":{"JANK_PERCENT":0,"LOST_TIME":0}""", """"cleanRuns":7""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a phase the entry does not carry rejects the file`() {
        val json = sessionBaseline().toJson().replace(""""layoutMs":""", """"wasLayoutMs":""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `more clean runs than runs rejects the file`() {
        val json = sessionBaseline().toJson().replace(""""JANK_PERCENT":0""", """"JANK_PERCENT":2""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a run count between whole numbers rejects the file rather than rounding`() {
        val json = sessionBaseline().toJson().replace(""""runs":1""", """"runs":1.5""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a figure past what a float holds rejects the file rather than reading infinity`() {
        val json = sessionBaseline().toJson().replace(""""p95FrameMs":10.0""", """"p95FrameMs":1e400""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a negative phase average rejects the file`() {
        val json = sessionBaseline().toJson().replace(""""layoutMs":4.0""", """"layoutMs":-4.0""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a candidate budget that already replaced the budget rejects the file`() {
        val json = sessionBaseline(candidateEntry()).toJson()
            .replace(""""budgetMs":8,"runs":2""", """"budgetMs":8,"runs":3""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a candidate for the budget already in effect rejects the file`() {
        val json = sessionBaseline(candidateEntry()).toJson().replace(""""budgetMs":8""", """"budgetMs":17""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a candidate backed by more runs than the entry holds rejects the file`() {
        val json = sessionBaseline(candidateEntry()).toJson().replace(""""runs":3""", """"runs":1""")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `the same interval twice rejects the file rather than keeping one of them`() {
        val one = sessionBaseline().toJson()
        val session = one.substringAfter(""""intervals":[""").substringBeforeLast("]")
        val json = one.replace(session, "$session,$session")

        assertIs<Parsed.Rejected>(parseBaseline(json))
    }

    @Test
    fun `text that is not JSON is refused`() {
        assertIs<Parsed.Rejected>(parseBaseline("not json"))
    }

    private fun read(json: String): Baseline? = (parseBaseline(json) as? Parsed.Read)?.value

    private fun sessionBaseline(entry: BaselineEntry = entry(p95FrameMs = 10f)): Baseline =
        Baseline(RECORDED_ENVIRONMENT, mapOf(IntervalId.Session to entry))

    private fun candidateEntry(): BaselineEntry = entry(
        p95FrameMs = 10f,
        runs = 3,
        frameBudgetMs = 17,
        trust = BaselineTrust(candidateBudget = BudgetCandidate(budgetMs = 8, runs = 2)),
    )

    private fun entry(
        p95FrameMs: Float,
        runs: Int = 1,
        frameBudgetMs: Int? = null,
        trust: BaselineTrust? = null,
    ): BaselineEntry {
        val measured = BaselineEntry.of(
            stats = IntervalStats.EMPTY.copy(
                frames = 100,
                p95FrameMs = p95FrameMs,
                lostTimeMs = 25f,
                frozenFrames = 1,
                phases = PhaseAverages.of(layout = 4f, draw = 2f, total = 12f),
            ),
            frameBudgetMs = frameBudgetMs,
            runs = runs,
        )
        if (trust == null) return measured
        return BaselineEntry.restored(
            runs = measured.runs,
            p50FrameMs = measured.p50FrameMs,
            p95FrameMs = measured.p95FrameMs,
            p99FrameMs = measured.p99FrameMs,
            jankPercent = measured.jankPercent,
            lostTimeMsPerFrame = measured.lostTimeMsPerFrame,
            frozenPercent = measured.frozenPercent,
            phases = measured.phases,
            frameBudgetMs = measured.frameBudgetMs,
            trust = trust,
        )
    }
}
