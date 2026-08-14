package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.JankSeverity

/** Infinite thresholds are ignored. */
public data class JankThresholds(
    val maxJankPercent: Float = JankSeverity.WARNING_JANK_PERCENT,
    val maxFrozenFrames: Int = 0,
    val maxP95FrameMs: Float = Float.POSITIVE_INFINITY,
)
