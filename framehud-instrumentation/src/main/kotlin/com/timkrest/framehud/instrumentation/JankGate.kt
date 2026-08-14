package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.MeasuredMetric
import com.timkrest.framehud.SessionStats
import java.util.Locale

public enum class OnInconclusive {
    FAIL,
    WARN,
}

internal sealed interface GateVerdict {
    data object Pass : GateVerdict
    data class Fail(val message: String) : GateVerdict
    data class Inconclusive(val message: String) : GateVerdict
}

internal fun GateVerdict.throwOrWarn(onInconclusive: OnInconclusive, warn: (String) -> Unit) {
    when (this) {
        GateVerdict.Pass -> Unit
        is GateVerdict.Fail -> throw AssertionError(message)
        is GateVerdict.Inconclusive -> when (onInconclusive) {
            OnInconclusive.FAIL -> throw AssertionError(message)
            OnInconclusive.WARN -> warn(message)
        }
    }
}

internal fun JankThresholds.verdict(tag: String, stats: SessionStats): GateVerdict {
    val (tainted, trusted) = thresholdChecks(stats)
        .partition { stats.confidence.issuesAffecting(it.metric).isNotEmpty() }
    val tail = "over ${stats.frames} frames"

    val trustedViolations = trusted.mapNotNull { it.violationMessage }
    if (trustedViolations.isNotEmpty()) {
        return GateVerdict.Fail("$tag: ${trustedViolations.joinToString("; ")} $tail")
    }
    if (tainted.isEmpty()) return GateVerdict.Pass

    val issues = tainted
        .flatMap { stats.confidence.issuesAffecting(it.metric) }
        .distinct()
        .joinToString("; ") { it.summary }
    val taintedViolations = tainted.mapNotNull { it.violationMessage }
    val reason = if (taintedViolations.isEmpty()) issues else "${taintedViolations.joinToString("; ")}, but $issues"
    return GateVerdict.Inconclusive("$tag: inconclusive — $reason $tail")
}

private class ThresholdCheck(val metric: MeasuredMetric, val violationMessage: String?)

private fun JankThresholds.thresholdChecks(stats: SessionStats): List<ThresholdCheck> = listOfNotNull(
    limitCheck(MeasuredMetric.JANK_PERCENT, stats.jankPercent, maxJankPercent, "jank %.1f%% over %.1f%%"),
    ThresholdCheck(
        metric = MeasuredMetric.FROZEN_FRAMES,
        violationMessage = "${stats.frozenFrames} frozen frame(s), allowed $maxFrozenFrames"
            .takeIf { stats.frozenFrames > maxFrozenFrames },
    ),
    limitCheck(MeasuredMetric.P95, stats.p95FrameMs, maxP95FrameMs, "p95 %.1f ms over %.1f ms"),
    limitCheck(MeasuredMetric.LOST_TIME, stats.lostTimeMs, maxLostTimeMs, "lost time %.1f ms over %.1f ms"),
)

private fun limitCheck(metric: MeasuredMetric, value: Float, limit: Float, violation: String): ThresholdCheck? {
    if (!limit.isFinite()) return null
    return ThresholdCheck(metric, format(violation, value, limit).takeIf { value > limit })
}

private fun format(template: String, vararg args: Any): String = String.format(Locale.US, template, *args)
