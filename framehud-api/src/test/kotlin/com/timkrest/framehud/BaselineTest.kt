package com.timkrest.framehud

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineTest {

    @Test
    fun `an entry spreads lost time and frozen frames over every frame`() {
        val entry = BaselineEntry.of(
            IntervalStats.EMPTY.copy(frames = 200, lostTimeMs = 50f, frozenFrames = 2),
        )

        assertEquals(0.25f, entry.lostTimeMsPerFrame)
        assertEquals(1f, entry.frozenPercent)
    }

    @Test
    fun `an interval with no frames reports zero rather than dividing by them`() {
        val entry = BaselineEntry.of(IntervalStats.EMPTY.copy(lostTimeMs = 4f, frozenFrames = 1))

        assertEquals(0f, entry.lostTimeMsPerFrame)
        assertEquals(0f, entry.frozenPercent)
    }

    @Test
    fun `a second run moves the baseline halfway`() {
        val baseline = baselineOf(p95FrameMs = 10f)

        val updated = baseline.updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 20f)))

        val session = updated.entries.getValue(IntervalId.Session)
        assertEquals(15f, session.p95FrameMs)
        assertEquals(2, session.runs)
    }

    @Test
    fun `a long-lived baseline still follows the app`() {
        val baseline = baselineOf(p95FrameMs = 10f, runs = 50)

        val updated = baseline.updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 20f)))

        assertEquals(11f, updated.entries.getValue(IntervalId.Session).p95FrameMs)
    }

    @Test
    fun `another environment records the baseline from scratch`() {
        val baseline = baselineOf(p95FrameMs = 10f, runs = 4)

        val updated = baseline.updatedWith(OTHER_ENVIRONMENT, listOf(sessionOf(p95FrameMs = 20f)))

        val session = updated.entries.getValue(IntervalId.Session)
        assertEquals(20f, session.p95FrameMs)
        assertEquals(1, session.runs)
        assertEquals(OTHER_ENVIRONMENT, updated.environment)
    }

    @Test
    fun `an interval this run never reached keeps its baseline`() {
        val baseline = Baseline(
            environment = ENVIRONMENT,
            entries = mapOf(IntervalId.Screen("Checkout") to BaselineEntry.of(statsOf(p95FrameMs = 30f))),
        )

        val updated = baseline.updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))

        assertEquals(30f, updated.entries.getValue(IntervalId.Screen("Checkout")).p95FrameMs)
        assertEquals(10f, updated.entries.getValue(IntervalId.Session).p95FrameMs)
    }

    @Test
    fun `a baseline from another device compares nothing`() {
        val comparison = baselineOf(p95FrameMs = 10f).compare(OTHER_ENVIRONMENT, listOf(sessionOf(p95FrameMs = 20f)))

        val other = assertIs<BaselineComparison.OtherEnvironment>(comparison)
        assertEquals(ENVIRONMENT, other.recorded)
        assertEquals(OTHER_ENVIRONMENT, other.current)
    }

    @Test
    fun `an interval without a baseline entry is left out`() {
        val comparison = baselineOf(p95FrameMs = 10f).compare(
            ENVIRONMENT,
            listOf(IntervalReport(IntervalId.Mark("scroll"), statsOf(p95FrameMs = 20f))),
        )

        assertEquals(emptyList(), assertIs<BaselineComparison.Compared>(comparison).intervals)
    }

    @Test
    fun `a delta reports the change against the baseline`() {
        val comparison = baselineOf(p95FrameMs = 10f).compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 12f)))

        val delta = assertIs<BaselineComparison.Compared>(comparison)
            .interval(IntervalId.Session)
            ?.delta(BaselineMetric.P95_MS)
        assertEquals(2f, delta?.change)
        assertEquals(20f, delta?.changePercent)
    }

    @Test
    fun `a metric the baseline reports as zero has no percentage to give`() {
        val comparison = baselineOf(p95FrameMs = 0f).compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 5f)))

        val delta = assertIs<BaselineComparison.Compared>(comparison)
            .interval(IntervalId.Session)
            ?.delta(BaselineMetric.P95_MS)
        assertEquals(5f, delta?.change)
        assertNull(delta?.changePercent)
    }

    @Test
    fun `phases rank by how much they grew`() {
        val baseline = Baseline(
            environment = ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to BaselineEntry.of(
                    statsOf(phases = PhaseAverages(layout = 4f, draw = 2f, input = 1f)),
                ),
            ),
        )

        val comparison = baseline.compare(
            ENVIRONMENT,
            listOf(IntervalReport(IntervalId.Session, statsOf(phases = PhaseAverages(layout = 7f, draw = 3f, input = 1f)))),
        )

        val grown = assertIs<BaselineComparison.Compared>(comparison).interval(IntervalId.Session)?.grownPhases
        assertEquals(listOf(FramePhase.LAYOUT, FramePhase.DRAW), grown?.map { it.phase })
        assertEquals(3f, grown?.first()?.changeMs)
    }

    @Test
    fun `a run that changed nothing grew no phase`() {
        val comparison = baselineOf(p95FrameMs = 10f).compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))

        val interval = assertIs<BaselineComparison.Compared>(comparison).interval(IntervalId.Session)
        assertTrue(interval?.grownPhases.orEmpty().isEmpty())
    }

    @Test
    fun `a delta summary names the metric, both figures and the change`() {
        val delta = MetricDelta(metric = BaselineMetric.P95_MS, baseline = 10f, current = 12f, baselineRuns = 3)

        assertEquals("p95 10.0 ms → 12.0 ms (+20%) from 3 baseline run(s)", delta.summary)
    }

    @Test
    fun `an interval that drew nothing neither enters a baseline nor compares against one`() {
        val empty = listOf(IntervalReport(IntervalId.Session, IntervalStats.EMPTY))

        assertEquals(emptyMap(), Baseline(ENVIRONMENT, emptyMap()).updatedWith(ENVIRONMENT, empty).entries)
        assertEquals(
            emptyList(),
            assertIs<BaselineComparison.Compared>(baselineOf(p95FrameMs = 10f).compare(ENVIRONMENT, empty)).intervals,
        )
    }

    @Test
    fun `a metric this run measured tainted is not compared`() {
        val comparison = baselineOf(p95FrameMs = 10f)
            .compare(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 30f)))

        val compared = assertIs<BaselineComparison.Compared>(comparison)
        assertNull(compared.interval(IntervalId.Session)?.delta(BaselineMetric.P95_MS))
    }

    @Test
    fun `a delta counts the runs that measured the metric cleanly, not every run recorded`() {
        val baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))
            .updatedWith(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 90f)))

        val comparison = assertIs<BaselineComparison.Compared>(
            baseline.compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 12f))),
        )

        assertEquals(2, baseline.entries.getValue(IntervalId.Session).runs)
        assertEquals(1, comparison.interval(IntervalId.Session)?.delta(BaselineMetric.P95_MS)?.baselineRuns)
    }

    @Test
    fun `a tainted run cannot move a clean baseline metric`() {
        val baseline = baselineOf(p95FrameMs = 10f)

        val updated = baseline.updatedWith(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 30f)))

        assertEquals(10f, updated.entries.getValue(IntervalId.Session).p95FrameMs)
    }

    @Test
    fun `a metric first recorded tainted is not compared until a clean run replaces it`() {
        val seeded = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 30f)))

        val before = assertIs<BaselineComparison.Compared>(
            seeded.compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 12f))),
        )
        assertNull(before.interval(IntervalId.Session)?.delta(BaselineMetric.P95_MS))

        val cleaned = seeded.updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))
        assertEquals(10f, cleaned.entries.getValue(IntervalId.Session).p95FrameMs)

        val after = assertIs<BaselineComparison.Compared>(
            cleaned.compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 12f))),
        )
        assertEquals(2f, after.interval(IntervalId.Session)?.delta(BaselineMetric.P95_MS)?.change)
    }

    @Test
    fun `a tainted run does not slow the clean average down`() {
        val baseline = baselineOf(p95FrameMs = 10f)
            .updatedWith(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 90f)))

        val updated = baseline.updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 20f)))

        assertEquals(15f, updated.entries.getValue(IntervalId.Session).p95FrameMs)
    }

    @Test
    fun `runs that only ever measured a metric tainted keep it out of comparisons`() {
        val baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 30f)))
            .updatedWith(ENVIRONMENT, listOf(taintedSessionOf(p95FrameMs = 20f)))

        assertEquals(25f, baseline.entries.getValue(IntervalId.Session).p95FrameMs)
        val comparison = assertIs<BaselineComparison.Compared>(
            baseline.compare(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 12f))),
        )
        assertNull(comparison.interval(IntervalId.Session)?.delta(BaselineMetric.P95_MS))
    }

    @Test
    fun `budget metrics neither move nor compare across frame budgets`() {
        val baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 17, jankPercent = 10f, p95FrameMs = 10f)))

        val updated = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f, p95FrameMs = 20f)))

        val session = updated.entries.getValue(IntervalId.Session)
        assertEquals(10f, session.jankPercent)
        assertEquals(15f, session.p95FrameMs)
        val comparison = assertIs<BaselineComparison.Compared>(
            updated.compare(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f, p95FrameMs = 20f))),
        )
        assertNull(comparison.interval(IntervalId.Session)?.delta(BaselineMetric.JANK_PERCENT))
        assertEquals(5f, comparison.interval(IntervalId.Session)?.delta(BaselineMetric.P95_MS)?.change)
    }

    @Test
    fun `three runs in a row under a new budget restart the budget metrics from it`() {
        var baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 17, jankPercent = 10f)))

        repeat(3) {
            baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        }

        val session = baseline.entries.getValue(IntervalId.Session)
        assertEquals(30f, session.jankPercent)
        assertEquals(8, session.frameBudgetMs)
        val comparison = assertIs<BaselineComparison.Compared>(
            baseline.compare(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f))),
        )
        assertEquals(0f, comparison.interval(IntervalId.Session)?.delta(BaselineMetric.JANK_PERCENT)?.change)
    }

    @Test
    fun `runs under different replacement budgets do not add up to a migration`() {
        var baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 17, jankPercent = 10f)))

        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 11, jankPercent = 30f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))

        val session = baseline.entries.getValue(IntervalId.Session)
        assertEquals(17, session.frameBudgetMs)
        assertEquals(10f, session.jankPercent)
    }

    @Test
    fun `a run under the recorded budget resets the migration streak`() {
        var baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 17, jankPercent = 10f)))

        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 17, jankPercent = 12f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))

        val session = baseline.entries.getValue(IntervalId.Session)
        assertEquals(17, session.frameBudgetMs)
        assertEquals(11f, session.jankPercent)
    }

    @Test
    fun `a clean run under any budget replaces budget metrics no run measured cleanly`() {
        val mixed = IntervalReport(
            id = IntervalId.Session,
            stats = IntervalStats.EMPTY.copy(
                frames = 100,
                jankPercent = 50f,
                confidence = MeasurementConfidence(listOf(ConfidenceIssue.RefreshRateChanged(setOf(60, 120)))),
            ),
            frameBudgetMs = null,
        )
        val seeded = Baseline(ENVIRONMENT, emptyMap()).updatedWith(ENVIRONMENT, listOf(mixed))

        val cleaned = seeded.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 20f)))

        val session = cleaned.entries.getValue(IntervalId.Session)
        assertEquals(20f, session.jankPercent)
        assertEquals(8, session.frameBudgetMs)
    }

    @Test
    fun `a mixed run neither breaks nor advances the migration streak`() {
        var baseline = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 17, jankPercent = 10f)))

        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(mixedSession(jankPercent = 30f)))
        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        assertEquals(17, baseline.entries.getValue(IntervalId.Session).frameBudgetMs)

        baseline = baseline.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 30f)))
        val session = baseline.entries.getValue(IntervalId.Session)
        assertEquals(8, session.frameBudgetMs)
        assertEquals(30f, session.jankPercent)
    }

    @Test
    fun `a mixed run does not seed the budget metrics even when otherwise clean`() {
        val seeded = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(mixedSession(jankPercent = 50f)))

        val cleaned = seeded.updatedWith(ENVIRONMENT, listOf(sessionUnderBudget(budgetMs = 8, jankPercent = 20f)))

        val session = cleaned.entries.getValue(IntervalId.Session)
        assertEquals(20f, session.jankPercent)
        assertEquals(8, session.frameBudgetMs)
    }

    @Test
    fun `a tainted run cannot move the baseline phases`() {
        val baseline = Baseline(
            environment = ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to BaselineEntry.of(statsOf(phases = PhaseAverages(layout = 4f, total = 12f))),
            ),
        )

        val tainted = droppedReportsSessionOf(PhaseAverages(layout = 100f, total = 300f))
        val updated = baseline.updatedWith(ENVIRONMENT, listOf(tainted))

        val phases = updated.entries.getValue(IntervalId.Session).phases
        assertEquals(4f, phases.layout)
        assertEquals(12f, phases.total)
    }

    @Test
    fun `a tainted phase a clean run cannot measure is dropped, not laundered`() {
        val seeded = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(droppedReportsSessionOf(PhaseAverages(gpu = 40f))))

        val cleaned = seeded.updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))

        assertNull(cleaned.entries.getValue(IntervalId.Session).phases.gpu)
    }

    @Test
    fun `phases the current run cannot trust are not compared`() {
        val baseline = Baseline(
            environment = ENVIRONMENT,
            entries = mapOf(
                IntervalId.Session to BaselineEntry.of(
                    statsOf(phases = PhaseAverages(layout = 4f, swapBuffers = 2f)),
                ),
            ),
        )
        val emulator = IntervalReport(
            IntervalId.Session,
            IntervalStats.EMPTY.copy(
                frames = 100,
                phases = PhaseAverages(layout = 5f, swapBuffers = 10f),
                confidence = MeasurementConfidence(listOf(ConfidenceIssue.Emulator)),
            ),
        )

        val compared = assertIs<BaselineComparison.Compared>(baseline.compare(ENVIRONMENT, listOf(emulator)))

        val grown = compared.interval(IntervalId.Session)?.grownPhases.orEmpty()
        assertEquals(listOf(FramePhase.LAYOUT), grown.map { it.phase })
    }

    @Test
    fun `phases first recorded tainted are not compared until a clean run replaces them`() {
        val seeded = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(droppedReportsSessionOf(PhaseAverages(layout = 100f))))

        val current = IntervalReport(IntervalId.Session, statsOf(phases = PhaseAverages(layout = 7f)))
        val before = assertIs<BaselineComparison.Compared>(seeded.compare(ENVIRONMENT, listOf(current)))
        assertTrue(before.interval(IntervalId.Session)?.phases.orEmpty().isEmpty())

        val cleaned = seeded.updatedWith(ENVIRONMENT, listOf(current))
        assertEquals(7f, cleaned.entries.getValue(IntervalId.Session).phases.layout)
    }

    @Test
    fun `a phase averages over the runs that reported it, not over every run`() {
        val seeded = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))
            .updatedWith(ENVIRONMENT, listOf(gpuSessionOf(gpuMs = 10f)))

        val averaged = seeded.updatedWith(ENVIRONMENT, listOf(gpuSessionOf(gpuMs = 20f)))

        assertEquals(15f, averaged.entries.getValue(IntervalId.Session).phases.gpu)
    }

    @Test
    fun `a run that measured a phase under an issue of its own does not weigh in on the average`() {
        val seeded = Baseline(ENVIRONMENT, emptyMap())
            .updatedWith(ENVIRONMENT, listOf(gpuSessionOf(gpuMs = 10f)))
            .updatedWith(ENVIRONMENT, listOf(emulatorGpuSessionOf(gpuMs = 100f)))
            .updatedWith(ENVIRONMENT, listOf(sessionOf(p95FrameMs = 10f)))

        val averaged = seeded.updatedWith(ENVIRONMENT, listOf(gpuSessionOf(gpuMs = 20f)))

        assertEquals(15f, averaged.entries.getValue(IntervalId.Session).phases.gpu)
    }

    private fun gpuSessionOf(gpuMs: Float) =
        IntervalReport(IntervalId.Session, statsOf(phases = PhaseAverages(gpu = gpuMs)))

    private fun emulatorGpuSessionOf(gpuMs: Float) = IntervalReport(
        IntervalId.Session,
        statsOf(phases = PhaseAverages(gpu = gpuMs))
            .copy(confidence = MeasurementConfidence(listOf(ConfidenceIssue.Emulator))),
    )

    private fun mixedSession(jankPercent: Float) = IntervalReport(
        id = IntervalId.Session,
        stats = IntervalStats.EMPTY.copy(frames = 100, jankPercent = jankPercent),
        frameBudgetMs = null,
    )

    private fun droppedReportsSessionOf(phases: PhaseAverages) = IntervalReport(
        IntervalId.Session,
        IntervalStats.EMPTY.copy(
            frames = 100,
            phases = phases,
            confidence = MeasurementConfidence(listOf(ConfidenceIssue.DroppedReports(3))),
        ),
    )

    private fun sessionUnderBudget(budgetMs: Int, jankPercent: Float = 0f, p95FrameMs: Float = 0f) = IntervalReport(
        id = IntervalId.Session,
        stats = IntervalStats.EMPTY.copy(frames = 100, jankPercent = jankPercent, p95FrameMs = p95FrameMs),
        frameBudgetMs = budgetMs,
    )

    private fun taintedSessionOf(p95FrameMs: Float) = IntervalReport(
        IntervalId.Session,
        IntervalStats.EMPTY.copy(
            frames = 30,
            p95FrameMs = p95FrameMs,
            confidence = MeasurementConfidence(listOf(ConfidenceIssue.ShortSample(30))),
        ),
    )

    private fun baselineOf(p95FrameMs: Float, runs: Int = 1) = Baseline(
        environment = ENVIRONMENT,
        entries = mapOf(IntervalId.Session to BaselineEntry.of(statsOf(p95FrameMs = p95FrameMs)).copy(runs = runs)),
    )

    private fun sessionOf(p95FrameMs: Float) = IntervalReport(IntervalId.Session, statsOf(p95FrameMs = p95FrameMs))

    private fun statsOf(
        p95FrameMs: Float = 0f,
        phases: PhaseAverages = PhaseAverages.EMPTY,
    ) = IntervalStats.EMPTY.copy(frames = 100, p95FrameMs = p95FrameMs, phases = phases)

    private companion object {
        val ENVIRONMENT = BaselineEnvironment(
            manufacturer = "Google",
            model = "Pixel 8",
            apiLevel = 34,
        )

        val OTHER_ENVIRONMENT = ENVIRONMENT.copy(model = "Pixel 5")
    }
}
