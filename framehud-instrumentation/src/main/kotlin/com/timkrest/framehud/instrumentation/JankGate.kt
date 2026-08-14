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

private fun JankThresholds.thresholdChecks(stats: SessionStats): List<ThresholdCheck> = buildList {
    if (maxJankPercent.isFinite()) {
        add(
            ThresholdCheck(
                metric = MeasuredMetric.JANK_PERCENT,
                violationMessage = format("jank %.1f%% over %.1f%%", stats.jankPercent, maxJankPercent)
                    .takeIf { stats.jankPercent > maxJankPercent },
            ),
        )
    }
    add(
        ThresholdCheck(
            metric = MeasuredMetric.FROZEN_FRAMES,
            violationMessage = "${stats.frozenFrames} frozen frame(s), allowed $maxFrozenFrames"
                .takeIf { stats.frozenFrames > maxFrozenFrames },
        ),
    )
    if (maxP95FrameMs.isFinite()) {
        add(
            ThresholdCheck(
                metric = MeasuredMetric.P95,
                violationMessage = format("p95 %.1f ms over %.1f ms", stats.p95FrameMs, maxP95FrameMs)
                    .takeIf { stats.p95FrameMs > maxP95FrameMs },
            ),
        )
    }
}

private fun format(template: String, vararg args: Any): String = String.format(Locale.US, template, *args)
