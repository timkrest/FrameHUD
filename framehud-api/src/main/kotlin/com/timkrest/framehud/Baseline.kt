package com.timkrest.framehud

import android.os.Build
import androidx.compose.runtime.Immutable
import com.timkrest.framehud.internal.PERCENT
import kotlin.math.min

/**
 * The device a baseline is valid on. The display refresh rate is deliberately not part of it: an
 * adaptive-refresh display switches rates with screen content, which would discard the baseline
 * between two runs on the same device.
 */
@Immutable
public data class BaselineEnvironment(
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
) {
    init {
        require(apiLevel >= 1) { "An API level must be positive, got $apiLevel" }
    }

    public val label: String get() = "$manufacturer $model, API $apiLevel"

    public companion object {
        public fun current(): BaselineEnvironment = BaselineEnvironment(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            apiLevel = Build.VERSION.SDK_INT,
        )
    }
}

@InternalFrameHudApi
@Immutable
public data class BaselineTrust(
    val cleanRuns: Map<MeasuredMetric, Int> = emptyMap(),
    val gpuRuns: Int? = null,
    val candidateBudget: BudgetCandidate? = null,
) {
    internal fun check(runs: Int, frameBudgetMs: Int?) {
        require(cleanRuns.values.all { it in 0..runs }) {
            "A metric cannot be measured cleanly more often than the $runs run(s) recorded, got $cleanRuns"
        }
        require(gpuRuns == null || gpuRuns in 0..runs) {
            "No more than the $runs run(s) recorded can report a GPU duration, got $gpuRuns"
        }
        require(candidateBudget == null || candidateBudget.budgetMs != frameBudgetMs) {
            "The budget in effect is not a candidate to replace itself, got $frameBudgetMs"
        }
        require(candidateBudget == null || candidateBudget.runs <= runs) {
            "A candidate cannot be proven by more than the $runs run(s) recorded, got ${candidateBudget?.runs}"
        }
    }
}

@InternalFrameHudApi
@Immutable
public data class BudgetCandidate(
    val budgetMs: Int,
    val runs: Int,
) {
    init {
        require(budgetMs > 0) { "A frame budget must be positive, got $budgetMs" }
        require(runs in 1 until RUNS_THAT_PROVE_A_NEW_BUDGET) {
            "A candidate replaces the budget on run $RUNS_THAT_PROVE_A_NEW_BUDGET, so it holds fewer, got $runs"
        }
    }
}

@Immutable
@ConsistentCopyVisibility
public data class BaselineEntry internal constructor(
    val runs: Int,
    val p50FrameMs: Float,
    val p95FrameMs: Float,
    val p99FrameMs: Float,
    /** 0..100. */
    val jankPercent: Float,
    /** Spread over every frame the interval collected, janky or not. */
    val lostTimeMsPerFrame: Float,
    /** 0..100. */
    val frozenPercent: Float,
    val phases: PhaseAverages,
    /**
     * The budget, in whole milliseconds, that judged [BaselineMetric.JANK_PERCENT] and
     * [BaselineMetric.LOST_TIME_MS_PER_FRAME]. Null when no single budget covered the runs. Both
     * figures move and compare only between runs under the same budget.
     */
    val frameBudgetMs: Int? = null,
    @property:InternalFrameHudApi
    val trust: BaselineTrust = BaselineTrust(),
) {
    init {
        require(runs >= 1) { "A baseline entry holds at least one run, got $runs" }
        require(p50FrameMs >= 0f && p95FrameMs >= 0f && p99FrameMs >= 0f) {
            "Percentiles are durations, got $p50FrameMs/$p95FrameMs/$p99FrameMs ms"
        }
        require(jankPercent in 0f..PERCENT) { "jankPercent runs 0..100, got $jankPercent" }
        require(lostTimeMsPerFrame >= 0f) { "lostTimeMsPerFrame is a duration, got $lostTimeMsPerFrame" }
        require(frozenPercent in 0f..PERCENT) { "frozenPercent runs 0..100, got $frozenPercent" }
        require(frameBudgetMs == null || frameBudgetMs > 0) {
            "A frame budget must be positive, got $frameBudgetMs"
        }
        trust.check(runs, frameBudgetMs)
    }

    public operator fun get(metric: BaselineMetric): Float = when (metric) {
        BaselineMetric.P50_MS -> p50FrameMs
        BaselineMetric.P95_MS -> p95FrameMs
        BaselineMetric.P99_MS -> p99FrameMs
        BaselineMetric.JANK_PERCENT -> jankPercent
        BaselineMetric.LOST_TIME_MS_PER_FRAME -> lostTimeMsPerFrame
        BaselineMetric.FROZEN_PERCENT -> frozenPercent
    }

    internal fun trustedValue(metric: BaselineMetric): Float? = if (cleanRunsOf(metric) == 0) null else this[metric]

    internal fun cleanRunsOf(metric: BaselineMetric): Int = cleanRunsOf(metric.confidenceMetric)

    internal fun cleanRunsOfPhase(phase: FramePhase): Int = phase.trustedAs().minOf(::cleanRunsOf)

    private fun cleanRunsOf(category: MeasuredMetric): Int = trust.cleanRuns[category] ?: runs

    private fun measuredRunsOfPhase(phase: FramePhase): Int = when (phase) {
        FramePhase.GPU -> trust.gpuRuns ?: runs
        else -> cleanRunsOfPhase(phase)
    }

    internal fun averagedWith(currentRun: BaselineEntry): BaselineEntry {
        val totalRuns = runs + currentRun.runs
        val currentRunWeight = 1f / min(totalRuns, RUNS_A_BASELINE_REMEMBERS)
        val budget = budgetMove(currentRun)
        fun moveOf(category: MeasuredMetric): BudgetMove =
            if (category in BUDGET_CATEGORIES) budget else BudgetMove.Follows
        fun merged(metric: BaselineMetric): Float = when (moveOf(metric.confidenceMetric)) {
            is BudgetMove.Keeps -> this[metric]
            BudgetMove.Restarts -> currentRun[metric]
            BudgetMove.Follows -> trustedMean(
                baseline = this[metric],
                current = currentRun[metric],
                trustedBefore = cleanRunsOf(metric),
                trustedAdded = currentRun.cleanRunsOf(metric),
                currentRunWeight = currentRunWeight,
            )
        }
        return BaselineEntry(
            runs = totalRuns,
            p50FrameMs = merged(BaselineMetric.P50_MS),
            p95FrameMs = merged(BaselineMetric.P95_MS),
            p99FrameMs = merged(BaselineMetric.P99_MS),
            jankPercent = merged(BaselineMetric.JANK_PERCENT),
            lostTimeMsPerFrame = merged(BaselineMetric.LOST_TIME_MS_PER_FRAME),
            frozenPercent = merged(BaselineMetric.FROZEN_PERCENT),
            phases = PhaseAverages.of { phase -> mergedPhase(currentRun, phase, currentRunWeight) },
            frameBudgetMs = if (budget is BudgetMove.Keeps) frameBudgetMs else currentRun.frameBudgetMs,
            trust = BaselineTrust(
                cleanRuns = TRUST_CATEGORIES.mapNotNull { category ->
                    val trusted = when (moveOf(category)) {
                        is BudgetMove.Keeps -> cleanRunsOf(category)
                        BudgetMove.Restarts -> currentRun.cleanRunsOf(category)
                        BudgetMove.Follows -> cleanRunsOf(category) + currentRun.cleanRunsOf(category)
                    }
                    trusted.takeIf { it != totalRuns }?.let { category to it }
                }.toMap(),
                gpuRuns = (gpuRuns() + currentRun.gpuRuns()).takeIf { it != totalRuns },
                candidateBudget = (budget as? BudgetMove.Keeps)?.candidate,
            ),
        )
    }

    private fun gpuRuns(): Int = if (phases.gpu == null) 0 else trust.gpuRuns ?: runs

    private fun mergedPhase(currentRun: BaselineEntry, phase: FramePhase, currentRunWeight: Float): Float? {
        val baseline = phases[phase]
        val current = currentRun.phases[phase]
        val trustedBefore = cleanRunsOfPhase(phase)
        val trustedAdded = currentRun.cleanRunsOfPhase(phase)
        return when {
            baseline == null -> current?.takeIf { trustedAdded > 0 || trustedBefore == 0 }
            current == null -> baseline.takeIf { trustedBefore > 0 || trustedAdded == 0 }
            else -> trustedMean(
                baseline = baseline,
                current = current,
                trustedBefore = measuredRunsOfPhase(phase),
                trustedAdded = currentRun.measuredRunsOfPhase(phase),
                currentRunWeight = currentRunWeight,
            )
        }
    }

    private fun budgetMove(currentRun: BaselineEntry): BudgetMove {
        val hasHistory = BUDGET_CATEGORIES.any { cleanRunsOf(it) > 0 }
        val matches = frameBudgetMs != null && frameBudgetMs == currentRun.frameBudgetMs
        if (!hasHistory || matches) return BudgetMove.Follows
        val budgetMs = currentRun.frameBudgetMs ?: return BudgetMove.Keeps(trust.candidateBudget)
        val provenRuns = (trust.candidateBudget?.takeIf { it.budgetMs == budgetMs }?.runs ?: 0) + 1
        if (provenRuns >= RUNS_THAT_PROVE_A_NEW_BUDGET) return BudgetMove.Restarts
        return BudgetMove.Keeps(BudgetCandidate(budgetMs, provenRuns))
    }

    public companion object {

        /**
         * A [frameBudgetMs] of null keeps jank percent and lost time out of the entry. [runs] says
         * how many runs [stats] already average, which is what a comparison reports back.
         */
        public fun of(stats: IntervalStats, frameBudgetMs: Int? = null, runs: Int = 1): BaselineEntry {
            val cleanRuns = TRUST_CATEGORIES.mapNotNull { category ->
                val unjudged = category in BUDGET_CATEGORIES && frameBudgetMs == null
                if (unjudged || stats.confidence.issuesAffecting(category).isNotEmpty()) category to 0 else null
            }.toMap()
            val measuredGpu = stats.phases.gpu != null && cleanRuns[MeasuredMetric.RENDER_PHASES] != 0
            return BaselineEntry(
                runs = runs,
                p50FrameMs = stats.p50FrameMs,
                p95FrameMs = stats.p95FrameMs,
                p99FrameMs = stats.p99FrameMs,
                jankPercent = stats.jankPercent,
                lostTimeMsPerFrame = stats.lostTimeMs.per(stats.frames),
                frozenPercent = stats.frozenFrames.toFloat().per(stats.frames) * PERCENT,
                phases = stats.phases,
                frameBudgetMs = frameBudgetMs,
                trust = BaselineTrust(cleanRuns = cleanRuns, gpuRuns = if (measuredGpu) null else 0),
            )
        }

        @InternalFrameHudApi
        public fun restored(
            runs: Int,
            p50FrameMs: Float = 0f,
            p95FrameMs: Float = 0f,
            p99FrameMs: Float = 0f,
            jankPercent: Float = 0f,
            lostTimeMsPerFrame: Float = 0f,
            frozenPercent: Float = 0f,
            phases: PhaseAverages = PhaseAverages.EMPTY,
            frameBudgetMs: Int? = null,
            trust: BaselineTrust = BaselineTrust(),
        ): BaselineEntry = BaselineEntry(
            runs = runs,
            p50FrameMs = p50FrameMs,
            p95FrameMs = p95FrameMs,
            p99FrameMs = p99FrameMs,
            jankPercent = jankPercent,
            lostTimeMsPerFrame = lostTimeMsPerFrame,
            frozenPercent = frozenPercent,
            phases = phases,
            frameBudgetMs = frameBudgetMs,
            trust = trust,
        )

        private fun Float.per(frames: Int): Float = if (frames == 0) 0f else this / frames
    }
}

/** What earlier runs measured on one device, per interval. */
@Immutable
public data class Baseline(
    val environment: BaselineEnvironment,
    val entries: Map<IntervalId, BaselineEntry>,
) {

    /** Keeps the entries this run did not measure, and starts over when [environment] differs. */
    public fun updatedWith(environment: BaselineEnvironment, intervals: List<IntervalReport>): Baseline {
        val recorded = intervals.withFrames().associate { it.id to BaselineEntry.of(it.stats, it.frameBudgetMs) }
        if (environment != this.environment) return Baseline(environment, recorded)
        return Baseline(
            environment = environment,
            entries = entries + recorded.mapValues { (id, entry) -> entries[id]?.averagedWith(entry) ?: entry },
        )
    }

    /** Intervals this baseline never recorded, and intervals that drew no frame, are left out. */
    public fun compare(environment: BaselineEnvironment, intervals: List<IntervalReport>): BaselineComparison {
        if (environment != this.environment) {
            return BaselineComparison.OtherEnvironment(recorded = this.environment, current = environment)
        }
        return BaselineComparison.Compared(
            intervals.withFrames().mapNotNull { report -> entries[report.id]?.compareWith(report) },
        )
    }
}

private fun List<IntervalReport>.withFrames(): List<IntervalReport> = filter { it.stats.frames > 0 }

private val BUDGET_CATEGORIES = setOf(MeasuredMetric.JANK_PERCENT, MeasuredMetric.LOST_TIME)

private val PHASE_CATEGORIES = setOf(MeasuredMetric.UI_THREAD_PHASES, MeasuredMetric.RENDER_PHASES)

private val COMPARED_PHASES = FramePhase.entries - FramePhase.TOTAL

private val TRUST_CATEGORIES = BaselineMetric.entries.mapTo(mutableSetOf()) { it.confidenceMetric } + PHASE_CATEGORIES

private const val RUNS_A_BASELINE_REMEMBERS = 10

private const val RUNS_THAT_PROVE_A_NEW_BUDGET = 3

private fun trustedMean(
    baseline: Float,
    current: Float,
    trustedBefore: Int,
    trustedAdded: Int,
    currentRunWeight: Float,
): Float = when {
    trustedBefore == 0 && trustedAdded == 0 -> mean(baseline, current, currentRunWeight)
    trustedBefore == 0 -> current
    trustedAdded == 0 -> baseline
    else -> mean(baseline, current, 1f / min(trustedBefore + trustedAdded, RUNS_A_BASELINE_REMEMBERS))
}

private sealed interface BudgetMove {
    data object Follows : BudgetMove
    data class Keeps(val candidate: BudgetCandidate?) : BudgetMove
    data object Restarts : BudgetMove
}

private fun FramePhase.trustedAs(): Set<MeasuredMetric> = when (this) {
    FramePhase.UNKNOWN_DELAY,
    FramePhase.INPUT,
    FramePhase.ANIMATION,
    FramePhase.LAYOUT,
    FramePhase.DRAW,
    -> setOf(MeasuredMetric.UI_THREAD_PHASES)

    FramePhase.SYNC,
    FramePhase.COMMAND_ISSUE,
    FramePhase.SWAP_BUFFERS,
    FramePhase.GPU,
    -> setOf(MeasuredMetric.RENDER_PHASES)

    FramePhase.TOTAL -> PHASE_CATEGORIES
}

private fun mean(baseline: Float, current: Float, weight: Float): Float = baseline + (current - baseline) * weight

private fun BaselineEntry.compareWith(report: IntervalReport): IntervalComparison {
    val stats = report.stats
    val current = BaselineEntry.of(stats, report.frameBudgetMs)
    val budgetsMatch = frameBudgetMs != null && frameBudgetMs == report.frameBudgetMs
    val deltas = mutableListOf<MetricDelta>()
    val gaps = mutableListOf<UncomparedMetric>()
    for (metric in BaselineMetric.entries) {
        val baseline = trustedValue(metric)
        val measured = current.trustedValue(metric)
        when {
            metric.confidenceMetric in BUDGET_CATEGORIES && !budgetsMatch ->
                gaps += UncomparedMetric.of(metric, ComparisonGap.OTHER_FRAME_BUDGET)
            baseline == null -> gaps += UncomparedMetric.of(metric, ComparisonGap.BASELINE_HAS_NONE)
            measured == null -> gaps += UncomparedMetric.of(metric, ComparisonGap.RUN_UNTRUSTED)
            else -> deltas += MetricDelta.of(
                metric = metric,
                baseline = baseline,
                current = measured,
                baselineRuns = cleanRunsOf(metric),
            )
        }
    }
    return IntervalComparison.of(
        id = report.id,
        recordedRuns = runs,
        currentFrames = stats.frames,
        metrics = deltas,
        uncompared = gaps,
        phases = COMPARED_PHASES.mapNotNull { phase ->
            if (cleanRunsOfPhase(phase) == 0 || current.cleanRunsOfPhase(phase) == 0) return@mapNotNull null
            val baseline = phases[phase] ?: return@mapNotNull null
            val measured = current.phases[phase] ?: return@mapNotNull null
            PhaseDelta.of(phase = phase, baselineMs = baseline, currentMs = measured)
        },
    )
}
