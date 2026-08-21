package com.timkrest.framehud

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.internal.PERCENT
import com.timkrest.framehud.internal.format
import com.timkrest.framehud.internal.formatChangePercent
import com.timkrest.framehud.internal.label
import com.timkrest.framehud.internal.reason

@Immutable
public data class MetricDelta(
    val metric: BaselineMetric,
    val baseline: Float,
    val current: Float,
    /** Runs whose measurement of [metric] entered the baseline's average. */
    val baselineRuns: Int,
) {
    public val change: Float = current - baseline

    /** Null when the baseline is zero. */
    public val changePercent: Float? = if (baseline == 0f) null else change / baseline * PERCENT

    public val summary: String
        get() = buildString {
            append(metric.label()).append(' ')
            append(metric.format(baseline)).append(" → ").append(metric.format(current))
            changePercent?.let { append(' ').append(formatChangePercent(it)) }
            append(" from ").append(baselineRuns).append(" baseline run(s)")
        }
}

@Immutable
public data class PhaseDelta(
    val phase: FramePhase,
    val baselineMs: Float,
    val currentMs: Float,
) {
    public val changeMs: Float = currentMs - baselineMs
}

public enum class ComparisonGap {
    BASELINE_HAS_NONE,
    RUN_UNTRUSTED,
    OTHER_FRAME_BUDGET,
}

@Immutable
public data class UncomparedMetric(
    val metric: BaselineMetric,
    val gap: ComparisonGap,
) {
    public val summary: String get() = "${metric.label()} ${gap.reason()}"
}

@Immutable
public data class IntervalComparison(
    val id: IntervalId,
    val recordedRuns: Int,
    val currentFrames: Int,
    val metrics: List<MetricDelta>,
    val uncompared: List<UncomparedMetric>,
    val phases: List<PhaseDelta>,
) {
    /** Longest growth first. */
    public val grownPhases: List<PhaseDelta> =
        phases.filter { it.changeMs > 0f }.sortedByDescending { it.changeMs }

    public fun delta(metric: BaselineMetric): MetricDelta? = metrics.firstOrNull { it.metric == metric }

    public fun gap(metric: BaselineMetric): ComparisonGap? = uncompared.firstOrNull { it.metric == metric }?.gap
}

public sealed interface BaselineComparison {

    @Immutable
    public data class Compared(val intervals: List<IntervalComparison>) : BaselineComparison {
        public fun interval(id: IntervalId): IntervalComparison? = intervals.firstOrNull { it.id == id }
    }

    @Immutable
    public data class OtherEnvironment(
        val recorded: BaselineEnvironment,
        val current: BaselineEnvironment,
    ) : BaselineComparison

    /** No run has recorded a baseline on this device yet. */
    public data object NoBaseline : BaselineComparison
}
