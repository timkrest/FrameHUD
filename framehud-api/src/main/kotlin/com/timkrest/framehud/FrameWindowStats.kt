package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** The rolling window of recent frames, sized by [FrameHudConfig.metricsSampleWindowFrames]. */
@Immutable
@ConsistentCopyVisibility
public data class FrameWindowStats private constructor(
    /** Frames completed in the last second. Zero while the screen is idle. */
    val fps: Int,
    /** Share of janky frames in the window, 0..100. */
    val jankPercent: Float,
    val p95FrameMs: Float,
    val worstFrameMs: Float,
    /** What judged the latest frame in the window, or what is in force while it holds none. */
    val frameBudgetMs: Float,
    val history: FrameHistory,
) {
    init {
        require(frameBudgetMs > 0f) { "frameBudgetMs must be positive, was $frameBudgetMs" }
    }

    public companion object {
        public val EMPTY: FrameWindowStats = of()

        @InternalFrameHudApi
        public fun of(
            fps: Int = 0,
            jankPercent: Float = 0f,
            p95FrameMs: Float = 0f,
            worstFrameMs: Float = 0f,
            frameBudgetMs: Float = DisplayInfo.DEFAULT.frameBudgetMs,
            history: FrameHistory = FrameHistory.EMPTY,
        ): FrameWindowStats = FrameWindowStats(
            fps = fps,
            jankPercent = jankPercent,
            p95FrameMs = p95FrameMs,
            worstFrameMs = worstFrameMs,
            frameBudgetMs = frameBudgetMs,
            history = history,
        )
    }
}
