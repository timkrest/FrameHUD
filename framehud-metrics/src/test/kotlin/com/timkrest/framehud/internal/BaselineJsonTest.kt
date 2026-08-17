package com.timkrest.framehud.internal

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.BaselineTrust
import com.timkrest.framehud.BudgetCandidate
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.MeasuredMetric
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.SessionStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BaselineJsonTest {

    @Test
    fun `a baseline survives a round trip`() {
        val baseline = Baseline(
            environment = ENVIRONMENT,
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
        val entry = entry(p95FrameMs = 10f).copy(
            runs = 3,
            trust = BaselineTrust(
                cleanRuns = mapOf(MeasuredMetric.P95 to 0, MeasuredMetric.P99 to 2),
                gpuRuns = 1,
            ),
        )
        val baseline = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry))

        assertEquals(baseline, read(baseline.toJson()))
    }

    @Test
    fun `the frame budget and the candidate budget survive a round trip`() {
        val entry = entry(p95FrameMs = 10f).copy(
            runs = 3,
            frameBudgetMs = 17,
            trust = BaselineTrust(candidateBudget = BudgetCandidate(budgetMs = 8, runs = 2)),
        )
        val baseline = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry))

        assertEquals(baseline, read(baseline.toJson()))
    }

    @Test
    fun `a missing GPU average stays missing`() {
        val baseline = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))

        assertNull(read(baseline.toJson())?.entries?.getValue(IntervalId.Session)?.phases?.gpu)
    }

    @Test
    fun `a file written by another schema is refused`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""schema":$BASELINE_SCHEMA_VERSION""", """"schema":${BASELINE_SCHEMA_VERSION + 1}""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a file without an environment is refused`() {
        assertIs<ParsedBaseline.Rejected>(parseBaseline("""{"schema":$BASELINE_SCHEMA_VERSION,"intervals":[]}"""))
    }

    @Test
    fun `an interval this build cannot name is left out`() {
        val json = Baseline(
            environment = ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to entry(p95FrameMs = 10f),
                IntervalId.Mark("fling") to entry(p95FrameMs = 20f),
            ),
        ).toJson().replace(""""interval":"mark:fling"""", """"interval":"gesture:fling"""")

        assertEquals(setOf(IntervalId.Session), read(json)?.entries?.keys)
    }

    @Test
    fun `an entry missing a figure rejects the file rather than reading zeros`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""jankPercent":""", """"wasJankPercent":""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a run count below one rejects the file`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""runs":1""", """"runs":-1""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `an invalid clean-run count rejects the file rather than counting every run clean`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""JANK_PERCENT":0""", """"JANK_PERCENT":-1""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `clean runs of the wrong shape reject the file rather than counting every run clean`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""cleanRuns":{"JANK_PERCENT":0,"LOST_TIME":0}""", """"cleanRuns":7""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a phase the entry does not carry rejects the file`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""layoutMs":""", """"wasLayoutMs":""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `more clean runs than runs rejects the file`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""JANK_PERCENT":0""", """"JANK_PERCENT":2""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a run count between whole numbers rejects the file rather than rounding`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""runs":1""", """"runs":1.5""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a figure past what a float holds rejects the file rather than reading infinity`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""p95FrameMs":10.0""", """"p95FrameMs":1e400""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a negative phase average rejects the file`() {
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry(p95FrameMs = 10f)))
            .toJson()
            .replace(""""layoutMs":4.0""", """"layoutMs":-4.0""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a candidate budget that already replaced the budget rejects the file`() {
        val entry = entry(p95FrameMs = 10f).copy(
            runs = 3,
            frameBudgetMs = 17,
            trust = BaselineTrust(candidateBudget = BudgetCandidate(budgetMs = 8, runs = 2)),
        )
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry))
            .toJson()
            .replace(""""budgetMs":8,"runs":2""", """"budgetMs":8,"runs":3""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a candidate for the budget already in effect rejects the file`() {
        val entry = entry(p95FrameMs = 10f).copy(
            runs = 3,
            frameBudgetMs = 17,
            trust = BaselineTrust(candidateBudget = BudgetCandidate(budgetMs = 8, runs = 2)),
        )
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry))
            .toJson()
            .replace(""""budgetMs":8""", """"budgetMs":17""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `a candidate backed by more runs than the entry holds rejects the file`() {
        val entry = entry(p95FrameMs = 10f).copy(
            runs = 3,
            frameBudgetMs = 17,
            trust = BaselineTrust(candidateBudget = BudgetCandidate(budgetMs = 8, runs = 2)),
        )
        val json = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry))
            .toJson()
            .replace(""""runs":3""", """"runs":1""")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `the same interval twice rejects the file rather than keeping one of them`() {
        val entry = entry(p95FrameMs = 10f)
        val one = Baseline(ENVIRONMENT, mapOf(IntervalId.Session to entry)).toJson()
        val session = one.substringAfter(""""intervals":[""").substringBeforeLast("]")
        val json = one.replace(session, "$session,$session")

        assertIs<ParsedBaseline.Rejected>(parseBaseline(json))
    }

    @Test
    fun `text that is not JSON is refused`() {
        assertIs<ParsedBaseline.Rejected>(parseBaseline("not json"))
    }

    private fun read(json: String): Baseline? = (parseBaseline(json) as? ParsedBaseline.Read)?.baseline

    private fun entry(p95FrameMs: Float) = BaselineEntry.of(
        SessionStats.EMPTY.copy(
            frames = 100,
            p95FrameMs = p95FrameMs,
            lostTimeMs = 25f,
            frozenFrames = 1,
            phases = PhaseAverages(layout = 4f, draw = 2f, total = 12f),
        ),
    )

    private companion object {
        val ENVIRONMENT = BaselineEnvironment(
            manufacturer = "Google",
            model = "Pixel 8",
            apiLevel = 34,
        )
    }
}
