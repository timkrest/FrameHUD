package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.JankSeverity

/** A threshold no run can reach turns its check off: [Float.POSITIVE_INFINITY], [Int.MAX_VALUE]. */
public data class JankThresholds(
    val maxJankPercent: Float = JankSeverity.WARNING_JANK_PERCENT,
    val maxFrozenFrames: Int = 0,
    val maxP95FrameMs: Float = Float.POSITIVE_INFINITY,
    val maxLostTimeMs: Float = Float.POSITIVE_INFINITY,
)
