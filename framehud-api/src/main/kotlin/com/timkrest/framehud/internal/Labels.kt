package com.timkrest.framehud.internal

import com.timkrest.framehud.BaselineMetric
import com.timkrest.framehud.ComparisonGap
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.InternalFrameHudApi
import com.timkrest.framehud.PhaseDelta

@InternalFrameHudApi
public fun BaselineMetric.label(): String = when (this) {
    BaselineMetric.P50_MS -> "p50"
    BaselineMetric.P95_MS -> "p95"
    BaselineMetric.P99_MS -> "p99"
    BaselineMetric.JANK_PERCENT -> "jank"
    BaselineMetric.LOST_TIME_MS_PER_FRAME -> "lost time"
    BaselineMetric.FROZEN_PERCENT -> "frozen frames"
}

@InternalFrameHudApi
public fun BaselineMetric.format(value: Float): String = when (this) {
    BaselineMetric.JANK_PERCENT, BaselineMetric.FROZEN_PERCENT -> formatInvariant("%.1f%%", value)
    BaselineMetric.LOST_TIME_MS_PER_FRAME -> formatInvariant("%.2f ms/frame", value)
    BaselineMetric.P50_MS, BaselineMetric.P95_MS, BaselineMetric.P99_MS -> formatInvariant("%.1f ms", value)
}

@InternalFrameHudApi
public fun formatChangePercent(percent: Float): String = formatInvariant("(%+.0f%%)", percent)

@InternalFrameHudApi
public fun ComparisonGap.reason(): String = when (this) {
    ComparisonGap.BASELINE_HAS_NONE -> "has no baseline run to compare against"
    ComparisonGap.RUN_UNTRUSTED -> "was measured with a confidence issue"
    ComparisonGap.OTHER_FRAME_BUDGET -> "was judged under another frame budget"
}

@InternalFrameHudApi
public fun FramePhase.label(): String = when (this) {
    FramePhase.UNKNOWN_DELAY -> "Delay before start"
    FramePhase.INPUT -> "Input"
    FramePhase.ANIMATION -> "Animation"
    FramePhase.LAYOUT -> "Layout"
    FramePhase.DRAW -> "Draw"
    FramePhase.SYNC -> "Sync"
    FramePhase.COMMAND_ISSUE -> "Command issue"
    FramePhase.SWAP_BUFFERS -> "Swap buffers"
    FramePhase.GPU -> "GPU"
    FramePhase.TOTAL -> "Total"
}

@InternalFrameHudApi
public fun PhaseDelta.grewTheMost(): String =
    formatInvariant("%s grew the most, %.1f → %.1f ms", phase.label(), baselineMs, currentMs)
