// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Numbers for one interval, named by the [IntervalId] next to them. Background time does not count. */
@Immutable
@ConsistentCopyVisibility
public data class IntervalStats private constructor(
    val frames: Int,
    val durationMs: Long,
    val p50FrameMs: Float,
    val p95FrameMs: Float,
    val p99FrameMs: Float,
    /** 0..100. */
    val jankPercent: Float,
    /** Summed overrun of the frames that missed their deadline. */
    val lostTimeMs: Float,
    val frozenFrames: Int,
    val maxJankStreak: Int,
    val droppedReports: Int,
    val phases: PhaseAverages,
    val confidence: MeasurementConfidence,
) {
    public companion object {
        @InternalFrameHudApi
        public fun of(
            frames: Int = 0,
            durationMs: Long = 0L,
            p50FrameMs: Float = 0f,
            p95FrameMs: Float = 0f,
            p99FrameMs: Float = 0f,
            jankPercent: Float = 0f,
            lostTimeMs: Float = 0f,
            frozenFrames: Int = 0,
            maxJankStreak: Int = 0,
            droppedReports: Int = 0,
            phases: PhaseAverages = PhaseAverages.EMPTY,
            confidence: MeasurementConfidence = MeasurementConfidence.CLEAN,
        ): IntervalStats = IntervalStats(
            frames = frames,
            durationMs = durationMs,
            p50FrameMs = p50FrameMs,
            p95FrameMs = p95FrameMs,
            p99FrameMs = p99FrameMs,
            jankPercent = jankPercent,
            lostTimeMs = lostTimeMs,
            frozenFrames = frozenFrames,
            maxJankStreak = maxJankStreak,
            droppedReports = droppedReports,
            phases = phases,
            confidence = confidence,
        )

        /** What Play Vitals counts as a frozen frame. */
        public const val FROZEN_FRAME_MS: Float = 700f

        public val EMPTY: IntervalStats = of()
    }
}
