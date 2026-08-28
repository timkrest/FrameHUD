package com.timkrest.framehud.internal

import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.RecordedRun

internal val RECORDED_ENVIRONMENT = BaselineEnvironment(manufacturer = "Google", model = "Pixel 8", apiLevel = 34)

internal fun recordedRun(
    recordedAtEpochMs: Long = 1_700_000_000_000L,
    appVersionName: String? = "1.2.3",
    intervals: List<IntervalReport> = listOf(recordedInterval(IntervalId.Session, recordedStats())),
): RecordedRun = RecordedRun.of(
    recordedAtEpochMs = recordedAtEpochMs,
    environment = RECORDED_ENVIRONMENT,
    appVersionName = appVersionName,
    appVersionCode = 42L,
    intervals = intervals,
)

internal fun storedRun(
    runId: String = "run:0",
    recordedAtEpochMs: Long = 1_700_000_000_000L,
    appVersionName: String? = "1.2.3",
    intervals: List<IntervalReport> = listOf(recordedInterval(IntervalId.Session, recordedStats())),
): StoredRun = StoredRun(
    runId = runId,
    run = recordedRun(recordedAtEpochMs = recordedAtEpochMs, appVersionName = appVersionName, intervals = intervals),
)

internal fun recordedInterval(
    id: IntervalId,
    stats: IntervalStats = recordedStats(),
    frameBudgetMs: Int? = null,
): IntervalReport = IntervalReport.of(id = id, stats = stats, frameBudgetMs = frameBudgetMs)

internal fun recordedStats(
    p95FrameMs: Float = 10f,
    issues: List<ConfidenceIssue> = emptyList(),
): IntervalStats = IntervalStats(
    frames = 300,
    durationMs = 5_000L,
    p50FrameMs = 8f,
    p95FrameMs = p95FrameMs,
    p99FrameMs = p95FrameMs * 2f,
    jankPercent = 3.5f,
    lostTimeMs = 120f,
    frozenFrames = 1,
    maxJankStreak = 4,
    droppedReports = 2,
    phases = PhaseAverages.of(layout = 2f, draw = 3f, total = 8f),
    confidence = MeasurementConfidence(issues),
)
