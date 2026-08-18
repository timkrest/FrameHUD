package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.BaselineMetric
import com.timkrest.framehud.JankSeverity

/** A threshold no run can reach turns its check off: [Float.POSITIVE_INFINITY], [Int.MAX_VALUE]. */
public data class JankThresholds(
    val maxJankPercent: Float = JankSeverity.WARNING_JANK_PERCENT,
    val maxFrozenFrames: Int = 0,
    val maxP95FrameMs: Float = Float.POSITIVE_INFINITY,
    /** Summed over the whole run, unlike [BaselineMetric.LOST_TIME_MS_PER_FRAME], which a baseline compares. */
    val maxLostTimeMs: Float = Float.POSITIVE_INFINITY,
    val baseline: BaselineThresholds? = null,
) {
    init {
        require(maxJankPercent >= 0f) { "maxJankPercent must be zero or more, got $maxJankPercent" }
        require(maxFrozenFrames >= 0) { "maxFrozenFrames must be zero or more, got $maxFrozenFrames" }
        require(maxP95FrameMs >= 0f) { "maxP95FrameMs must be zero or more, got $maxP95FrameMs" }
        require(maxLostTimeMs >= 0f) { "maxLostTimeMs must be zero or more, got $maxLostTimeMs" }
    }

    public companion object {

        @JvmStatic
        @JvmOverloads
        public fun baselineOnly(baseline: BaselineThresholds = BaselineThresholds()): JankThresholds = JankThresholds(
            maxJankPercent = Float.POSITIVE_INFINITY,
            maxFrozenFrames = Int.MAX_VALUE,
            baseline = baseline,
        )
    }
}
