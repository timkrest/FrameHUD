package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.os.Build
import android.view.FrameMetrics

internal enum class FramePhase(val metricId: Int) {
    UNKNOWN_DELAY(FrameMetrics.UNKNOWN_DELAY_DURATION),
    INPUT(FrameMetrics.INPUT_HANDLING_DURATION),
    ANIMATION(FrameMetrics.ANIMATION_DURATION),
    LAYOUT(FrameMetrics.LAYOUT_MEASURE_DURATION),
    DRAW(FrameMetrics.DRAW_DURATION),
    SYNC(FrameMetrics.SYNC_DURATION),
    COMMAND_ISSUE(FrameMetrics.COMMAND_ISSUE_DURATION),
    SWAP_BUFFERS(FrameMetrics.SWAP_BUFFERS_DURATION),

    @SuppressLint("InlinedApi")
    GPU(FrameMetrics.GPU_DURATION),
    TOTAL(FrameMetrics.TOTAL_DURATION),
    ;

    val isAvailable: Boolean
        get() = when (this) {
            GPU -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            else -> true
        }
}

internal fun FrameMetrics.readPhaseDurationsMs(into: FloatArray) {
    for (phase in FramePhase.entries) {
        into[phase.ordinal] = if (phase.isAvailable) {
            (getMetric(phase.metricId) / NS_PER_MS).coerceAtLeast(0f)
        } else {
            0f
        }
    }
}
