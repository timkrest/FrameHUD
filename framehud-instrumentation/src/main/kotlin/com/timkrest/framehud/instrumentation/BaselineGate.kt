package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.BaselineMetric
import com.timkrest.framehud.ComparisonGap
import com.timkrest.framehud.IntervalComparison
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MetricDelta
import com.timkrest.framehud.internal.grewTheMost

internal fun BaselineThresholds.verdict(
    tag: String,
    comparison: BaselineComparison,
    stats: IntervalStats,
): GateVerdict = when (comparison) {
    is BaselineComparison.OtherEnvironment -> GateVerdict.Inconclusive(
        "$tag: inconclusive — the baseline was recorded on ${comparison.recorded.label}, " +
            "this run measured ${comparison.current.label}",
    )

    BaselineComparison.NoBaseline ->
        GateVerdict.Skipped("$tag: no baseline on this device yet, nothing to compare against")

    is BaselineComparison.Compared -> comparison.interval(IntervalId.Session)
        ?.let { session -> sessionVerdict(tag, session, stats) }
        ?: GateVerdict.Inconclusive("$tag: inconclusive — the baseline holds no session to compare against")
}

private fun BaselineThresholds.sessionVerdict(
    tag: String,
    session: IntervalComparison,
    stats: IntervalStats,
): GateVerdict = gateVerdict(
    tag = tag,
    checks = metrics.map { metric -> session.checkOf(metric, maxRelativeIncreasePercent) },
    confidence = stats.confidence,
    tail = "over ${session.currentFrames} frames${session.blame()}",
)

private fun IntervalComparison.checkOf(metric: BaselineMetric, maxRelativeIncreasePercent: Float): GateCheck {
    delta(metric)?.let { delta ->
        val violation = delta.summary.takeIf { delta.isRegression(maxRelativeIncreasePercent) }
        return GateCheck.Measured(metric.confidenceMetric, violation)
    }
    val left = uncompared.first { it.metric == metric }
    return when (left.gap) {
        ComparisonGap.RUN_UNTRUSTED -> GateCheck.Measured(metric.confidenceMetric, violationMessage = null)
        ComparisonGap.BASELINE_HAS_NONE,
        ComparisonGap.OTHER_FRAME_BUDGET,
        -> GateCheck.Unjudged(metric.confidenceMetric, left.summary)
    }
}

private fun IntervalComparison.blame(): String {
    val grown = grownPhases.firstOrNull() ?: return ""
    return "; ${grown.grewTheMost()}"
}

private fun MetricDelta.isRegression(maxRelativeIncreasePercent: Float): Boolean {
    if (change < metric.noiseFloor()) return false
    val percent = changePercent ?: return true
    return percent > maxRelativeIncreasePercent
}

private fun BaselineMetric.noiseFloor(): Float = when (this) {
    BaselineMetric.LOST_TIME_MS_PER_FRAME -> NOISE_FLOOR_MS_PER_FRAME
    BaselineMetric.JANK_PERCENT, BaselineMetric.FROZEN_PERCENT -> NOISE_FLOOR_PERCENTAGE_POINTS
    BaselineMetric.P50_MS, BaselineMetric.P95_MS, BaselineMetric.P99_MS -> NOISE_FLOOR_MS
}

private const val NOISE_FLOOR_MS = 0.5f

private const val NOISE_FLOOR_PERCENTAGE_POINTS = 0.5f

private const val NOISE_FLOOR_MS_PER_FRAME = 0.1f
