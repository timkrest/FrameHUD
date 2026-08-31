package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MeasuredMetric
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.internal.formatInvariant
import org.junit.AssumptionViolatedException

public enum class OnInconclusive {
    FAIL,
    WARN,

    /** Throws [AssumptionViolatedException], which a JUnit runner reports as a skipped test. */
    SKIP,
}

internal sealed interface GateVerdict {
    data object Pass : GateVerdict
    data class Fail(val message: String) : GateVerdict
    data class Inconclusive(val message: String) : GateVerdict

    /** Nothing was checked, and no run can be blamed for it. */
    data class Skipped(val message: String) : GateVerdict
}

internal fun GateVerdict.throwOrWarn(onInconclusive: OnInconclusive, warn: (String) -> Unit) {
    when (this) {
        GateVerdict.Pass -> Unit
        is GateVerdict.Skipped -> warn(message)
        is GateVerdict.Fail -> throw AssertionError(message)
        is GateVerdict.Inconclusive -> when (onInconclusive) {
            OnInconclusive.FAIL -> throw AssertionError(message)
            OnInconclusive.WARN -> warn(message)
            OnInconclusive.SKIP -> throw AssumptionViolatedException(message)
        }
    }
}

internal fun JankThresholds.verdict(tag: String, stats: IntervalStats): GateVerdict = gateVerdict(
    tag = tag,
    checks = thresholdChecks(stats),
    confidence = stats.confidence,
    tail = "over ${stats.frames} frames",
)

internal sealed interface GateCheck {

    val metric: MeasuredMetric

    data class Measured(override val metric: MeasuredMetric, val violationMessage: String?) : GateCheck

    data class Unjudged(override val metric: MeasuredMetric, val reason: String) : GateCheck
}

internal fun gateVerdict(
    tag: String,
    checks: List<GateCheck>,
    confidence: MeasurementConfidence,
    tail: String,
): GateVerdict {
    val (tainted, trusted) = checks.filterIsInstance<GateCheck.Measured>()
        .partition { confidence.issuesAffecting(it.metric).isNotEmpty() }

    val trustedViolations = trusted.mapNotNull { it.violationMessage }
    if (trustedViolations.isNotEmpty()) {
        return GateVerdict.Fail("$tag: ${trustedViolations.joinToString("; ")} $tail")
    }

    val doubts = tainted.flatMap { confidence.issuesAffecting(it.metric) }.distinct().map { it.summary } +
        checks.filterIsInstance<GateCheck.Unjudged>().map { it.reason }
    if (doubts.isEmpty()) return GateVerdict.Pass

    val taintedViolations = tainted.mapNotNull { it.violationMessage }
    val doubted = doubts.joinToString("; ")
    val reason = if (taintedViolations.isEmpty()) doubted else "${taintedViolations.joinToString("; ")}, but $doubted"
    return GateVerdict.Inconclusive("$tag: inconclusive — $reason $tail")
}

private fun JankThresholds.thresholdChecks(stats: IntervalStats): List<GateCheck> = listOfNotNull(
    limitCheck(MeasuredMetric.JANK_PERCENT, stats.jankPercent, maxJankPercent, "jank %.1f%% over %.1f%%"),
    frozenFramesCheck(stats.frozenFrames),
    limitCheck(MeasuredMetric.P95, stats.p95FrameMs, maxP95FrameMs, "p95 %.1f ms over %.1f ms"),
    limitCheck(MeasuredMetric.LOST_TIME, stats.lostTimeMs, maxLostTimeMs, "lost time %.1f ms over %.1f ms"),
)

private fun JankThresholds.frozenFramesCheck(frozenFrames: Int): GateCheck.Measured? {
    if (maxFrozenFrames == Int.MAX_VALUE) return null
    return GateCheck.Measured(
        metric = MeasuredMetric.FROZEN_FRAMES,
        violationMessage = "$frozenFrames frozen frame(s), allowed $maxFrozenFrames"
            .takeIf { frozenFrames > maxFrozenFrames },
    )
}

private fun limitCheck(metric: MeasuredMetric, value: Float, limit: Float, violation: String): GateCheck.Measured? {
    if (!limit.isFinite()) return null
    return GateCheck.Measured(metric, formatInvariant(violation, value, limit).takeIf { value > limit })
}
