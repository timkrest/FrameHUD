package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.os.Build
import android.view.FrameMetrics
import com.timkrest.framehud.FramePhase

internal fun FrameMetrics.readPhaseDurationsMs(into: FloatArray) {
    for (phase in FramePhase.entries) {
        into[phase.ordinal] = if (Build.VERSION.SDK_INT >= phase.minSdk()) {
            (getMetric(phase.metricId()) / NS_PER_MS).coerceAtLeast(0f)
        } else {
            0f
        }
    }
}

@SuppressLint("InlinedApi")
private fun FramePhase.metricId(): Int = when (this) {
    FramePhase.UNKNOWN_DELAY -> FrameMetrics.UNKNOWN_DELAY_DURATION
    FramePhase.INPUT -> FrameMetrics.INPUT_HANDLING_DURATION
    FramePhase.ANIMATION -> FrameMetrics.ANIMATION_DURATION
    FramePhase.LAYOUT -> FrameMetrics.LAYOUT_MEASURE_DURATION
    FramePhase.DRAW -> FrameMetrics.DRAW_DURATION
    FramePhase.SYNC -> FrameMetrics.SYNC_DURATION
    FramePhase.COMMAND_ISSUE -> FrameMetrics.COMMAND_ISSUE_DURATION
    FramePhase.SWAP_BUFFERS -> FrameMetrics.SWAP_BUFFERS_DURATION
    FramePhase.GPU -> FrameMetrics.GPU_DURATION
    FramePhase.TOTAL -> FrameMetrics.TOTAL_DURATION
}

private fun FramePhase.minSdk(): Int = when (this) {
    FramePhase.GPU -> Build.VERSION_CODES.S
    else -> Build.VERSION_CODES.N
}
