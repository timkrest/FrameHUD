package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** The rolling window of recent frames, sized by [FrameHudConfig.metricsSampleWindowFrames]. */
@Immutable
public data class FrameWindowStats(
    /** Frames completed in the last second. Zero while the screen is idle. */
    val fps: Int = 0,
    /** Share of janky frames in the window, 0..100. */
    val jankPercent: Float = 0f,
    val p95FrameMs: Float = 0f,
    val worstFrameMs: Float = 0f,
    /** What judged the latest frame in the window, or what is in force while it holds none. */
    val frameBudgetMs: Float = DisplayInfo.DEFAULT.frameBudgetMs,
    val history: FrameHistory = FrameHistory.EMPTY,
) {
    init {
        require(frameBudgetMs > 0f) { "frameBudgetMs must be positive, was $frameBudgetMs" }
    }

    public companion object {
        public val EMPTY: FrameWindowStats = FrameWindowStats()
    }
}
