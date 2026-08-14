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
    val frozenFrames: Int = 0,
    val maxJankStreak: Int = 0,
    val droppedReports: Int = 0,
    val confidence: MeasurementConfidence = MeasurementConfidence.CLEAN,
) {
    public companion object {
        /** What Play Vitals counts as a frozen frame. */
        public const val FROZEN_FRAME_MS: Float = 700f

        public val EMPTY: SessionStats = SessionStats()
    }
}
