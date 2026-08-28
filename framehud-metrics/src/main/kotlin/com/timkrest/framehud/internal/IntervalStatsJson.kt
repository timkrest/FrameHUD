package com.timkrest.framehud.internal

import com.timkrest.framehud.IntervalStats

internal fun JsonObjectScope.putIntervalStats(stats: IntervalStats) {
    put(FRAMES, stats.frames)
    put(DURATION_MS, stats.durationMs)
    put(P50_MS, stats.p50FrameMs)
    put(P95_MS, stats.p95FrameMs)
    put(P99_MS, stats.p99FrameMs)
    put(JANK_PERCENT, stats.jankPercent)
    put(LOST_TIME_MS, stats.lostTimeMs)
    put(FROZEN_FRAMES, stats.frozenFrames)
    put(MAX_JANK_STREAK, stats.maxJankStreak)
    put(DROPPED_REPORTS, stats.droppedReports)
    putObject(PHASES) {
        put(BOTTLENECK_STAGE, stats.phases.bottleneckStage.name)
        putPhaseAverages(stats.phases)
    }
    putObject(CONFIDENCE) { putConfidence(stats.confidence) }
}

internal fun JsonValue.intervalStats(): IntervalStats? = readOrNull {
    IntervalStats(
        frames = int(FRAMES) ?: return@readOrNull null,
        durationMs = long(DURATION_MS) ?: return@readOrNull null,
        p50FrameMs = float(P50_MS) ?: return@readOrNull null,
        p95FrameMs = float(P95_MS) ?: return@readOrNull null,
        p99FrameMs = float(P99_MS) ?: return@readOrNull null,
        jankPercent = float(JANK_PERCENT) ?: return@readOrNull null,
        lostTimeMs = float(LOST_TIME_MS) ?: return@readOrNull null,
        frozenFrames = int(FROZEN_FRAMES) ?: return@readOrNull null,
        maxJankStreak = int(MAX_JANK_STREAK) ?: return@readOrNull null,
        droppedReports = int(DROPPED_REPORTS) ?: return@readOrNull null,
        phases = obj(PHASES)?.phaseAverages() ?: return@readOrNull null,
        confidence = obj(CONFIDENCE)?.confidence() ?: return@readOrNull null,
    )
}

private const val FRAMES = "frames"
private const val DURATION_MS = "durationMs"
private const val P50_MS = "p50FrameMs"
private const val P95_MS = "p95FrameMs"
private const val P99_MS = "p99FrameMs"
private const val JANK_PERCENT = "jankPercent"
private const val LOST_TIME_MS = "lostTimeMs"
private const val FROZEN_FRAMES = "frozenFrames"
private const val MAX_JANK_STREAK = "maxJankStreak"
private const val DROPPED_REPORTS = "droppedReports"
private const val PHASES = "phases"
private const val BOTTLENECK_STAGE = "bottleneckStage"
private const val CONFIDENCE = "confidence"
