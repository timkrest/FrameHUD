package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.BaselineMetric
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.ThermalLevel
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BaselineThresholdsTest {

    @Test
    fun `a run that matches its baseline passes`() {
        val verdict = BaselineThresholds().verdict(TAG, comparisonOf(baseline = 10f, current = 10f), session())

        assertIs<GateVerdict.Pass>(verdict)
    }

    @Test
    fun `growth past the allowed percentage fails and names both figures`() {
        val verdict = BaselineThresholds().verdict(TAG, comparisonOf(baseline = 10f, current = 12f), session())

        val failed = assertIs<GateVerdict.Fail>(verdict)
        assertContains(failed.message, "p95 10.0 ms → 12.0 ms (+20%) from 4 baseline run(s)")
    }

    @Test
    fun `growth inside the allowed percentage passes`() {
        val verdict = BaselineThresholds().verdict(TAG, comparisonOf(baseline = 10f, current = 10.9f), session())

        assertIs<GateVerdict.Pass>(verdict)
    }

    @Test
    fun `a fraction of a millisecond is noise, however large a share of the baseline it is`() {
        val verdict = BaselineThresholds().verdict(TAG, comparisonOf(baseline = 0.4f, current = 0.8f), session())

        assertIs<GateVerdict.Pass>(verdict)
    }

    @Test
    fun `a metric the baseline reports as zero fails on the absolute change alone`() {
        val verdict = BaselineThresholds().verdict(TAG, comparisonOf(baseline = 0f, current = 4f), session())

        assertIs<GateVerdict.Fail>(verdict)
    }

    @Test
    fun `the failure names the phase that grew the most`() {
        val comparison = comparisonOf(
            baseline = 10f,
            current = 14f,
            baselinePhases = PhaseAverages.of(layout = 4f, draw = 2f),
            currentPhases = PhaseAverages.of(layout = 7f, draw = 3f),
        )

        val failed = assertIs<GateVerdict.Fail>(BaselineThresholds().verdict(TAG, comparison, session()))
        assertContains(failed.message, "Layout grew the most, 4.0 → 7.0 ms")
    }

    @Test
    fun `a regression a confidence issue taints is inconclusive`() {
        val tainted = session(issues = listOf(ConfidenceIssue.ThermalThrottling(ThermalLevel.SEVERE)))

        val verdict = BaselineThresholds().verdict(TAG, comparisonOf(baseline = 10f, current = 12f), tainted)

        val inconclusive = assertIs<GateVerdict.Inconclusive>(verdict)
        assertContains(inconclusive.message, "thermal throttling")
    }

    @Test
    fun `a regression on a metric a noisy run measured cleanly fails in every mode`() {
        val issues = listOf(ConfidenceIssue.RefreshRateChanged(setOf(60, 120)))
        val thresholds = BaselineThresholds(metrics = setOf(BaselineMetric.P95_MS))
        val comparison = comparisonOf(baseline = 10f, current = 12f, currentIssues = issues)

        val verdict = assertIs<GateVerdict.Fail>(thresholds.verdict(TAG, comparison, session(issues)))

        OnInconclusive.entries.forEach { mode ->
            assertFailsWith<AssertionError> { verdict.throwOrWarn(mode) { error("should not warn") } }
        }
    }

    @Test
    fun `a baseline recorded elsewhere is inconclusive rather than a verdict`() {
        val comparison = BaselineComparison.OtherEnvironment(recorded = ENVIRONMENT, current = OTHER_ENVIRONMENT)

        val verdict = BaselineThresholds().verdict(TAG, comparison, session())

        assertContains(assertIs<GateVerdict.Inconclusive>(verdict).message, "Google Pixel 8, API 34")
    }

    @Test
    fun `a baseline that never saw this session is inconclusive`() {
        val verdict = BaselineThresholds().verdict(TAG, BaselineComparison.Compared(emptyList()), session())

        assertContains(assertIs<GateVerdict.Inconclusive>(verdict).message, "no session")
    }

    @Test
    fun `a metric left out of the comparison is inconclusive, even when the rest passed`() {
        val thresholds = BaselineThresholds(metrics = setOf(BaselineMetric.P95_MS, BaselineMetric.JANK_PERCENT))
        val comparison = comparisonOf(baseline = 10f, current = 10f, currentBudgetMs = 8)

        val verdict = thresholds.verdict(TAG, comparison, session())

        assertContains(
            assertIs<GateVerdict.Inconclusive>(verdict).message,
            "jank was judged under another frame budget",
        )
    }

    @Test
    fun `a chosen metric this run measured tainted is inconclusive, even when the rest passed`() {
        val issues = listOf(ConfidenceIssue.RefreshRateChanged(setOf(60, 120)))
        val thresholds =
            BaselineThresholds(metrics = setOf(BaselineMetric.P95_MS, BaselineMetric.LOST_TIME_MS_PER_FRAME))
        val comparison = comparisonOf(baseline = 10f, current = 10f, currentIssues = issues)

        val verdict = thresholds.verdict(TAG, comparison, session(issues))

        assertContains(
            assertIs<GateVerdict.Inconclusive>(verdict).message,
            "refresh rate changed across 60, 120 Hz",
        )
    }

    @Test
    fun `only the chosen metrics are checked`() {
        val thresholds = BaselineThresholds(metrics = setOf(BaselineMetric.JANK_PERCENT))

        assertIs<GateVerdict.Pass>(thresholds.verdict(TAG, comparisonOf(baseline = 10f, current = 20f), session()))
    }

    @Test
    fun `a run with no chosen metric to compare is inconclusive, and the message names each`() {
        val thresholds =
            BaselineThresholds(metrics = setOf(BaselineMetric.JANK_PERCENT, BaselineMetric.LOST_TIME_MS_PER_FRAME))
        val comparison = comparisonOf(baseline = 10f, current = 10f, currentBudgetMs = 8)

        val message = assertIs<GateVerdict.Inconclusive>(thresholds.verdict(TAG, comparison, session())).message

        assertContains(message, "jank was judged under another frame budget")
        assertContains(message, "lost time was judged under another frame budget")
    }

    @Test
    fun `a threshold that cannot judge growth is refused at construction`() {
        assertFailsWith<IllegalArgumentException> { BaselineThresholds(maxRelativeIncreasePercent = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { BaselineThresholds(maxRelativeIncreasePercent = -1f) }
        assertFailsWith<IllegalArgumentException> { BaselineThresholds(metrics = emptySet()) }
    }

    private fun comparisonOf(
        baseline: Float,
        current: Float,
        baselinePhases: PhaseAverages = PhaseAverages.EMPTY,
        currentPhases: PhaseAverages = PhaseAverages.EMPTY,
        currentBudgetMs: Int = BUDGET_MS,
        currentIssues: List<ConfidenceIssue> = emptyList(),
    ): BaselineComparison {
        val recorded = Baseline(
            environment = ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to BaselineEntry.of(
                    stats = statsOf(p95FrameMs = baseline, phases = baselinePhases),
                    frameBudgetMs = BUDGET_MS,
                    runs = 4,
                ),
            ),
        )
        return recorded.compare(
            ENVIRONMENT,
            listOf(
                IntervalReport.of(
                    id = IntervalId.Session,
                    stats = statsOf(p95FrameMs = current, phases = currentPhases)
                        .copy(confidence = MeasurementConfidence(currentIssues)),
                    frameBudgetMs = currentBudgetMs,
                ),
            ),
        )
    }

    private fun statsOf(p95FrameMs: Float, phases: PhaseAverages) =
        IntervalStats.EMPTY.copy(frames = 500, p95FrameMs = p95FrameMs, phases = phases)

    private fun session(issues: List<ConfidenceIssue> = emptyList()) =
        IntervalStats.EMPTY.copy(frames = 500, confidence = MeasurementConfidence(issues))

    private companion object {
        const val TAG = "sample test"

        const val BUDGET_MS = 17

        val ENVIRONMENT = BaselineEnvironment(
            manufacturer = "Google",
            model = "Pixel 8",
            apiLevel = 34,
        )

        val OTHER_ENVIRONMENT = ENVIRONMENT.copy(model = "Pixel 5")
    }
}
