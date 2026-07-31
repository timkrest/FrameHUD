package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Aggregates since the last reset. Time spent in the background is not counted. */
@Immutable
public data class SessionStats(
    val frames: Int = 0,
    val durationMs: Long = 0L,
    val p50FrameMs: Float = 0f,
    val p95FrameMs: Float = 0f,
    val p99FrameMs: Float = 0f,
    /** 0..100. */
    val jankPercent: Float = 0f,
    /** Frames over 700 ms — the Play Vitals definition. */
    val frozenFrames: Int = 0,
    /** Longest run of consecutive janky frames. */
    val maxJankStreak: Int = 0,
    /** Reports the system dropped before delivery. Above zero, averages and percentiles are undersampled. */
    val droppedReports: Int = 0,
) {
    public companion object {
        public val EMPTY: SessionStats = SessionStats()
    }
}
