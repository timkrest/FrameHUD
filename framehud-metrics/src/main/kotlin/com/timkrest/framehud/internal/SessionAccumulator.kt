package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel
import kotlin.math.max

@WorkerThread
internal class SessionAccumulator(private val clock: MetricsClock, isEmulator: Boolean = false) {

    private val totals = LatencyHistogram()
    private val confidence = ConfidenceTracker(isEmulator)
    private var collectingSinceMs: Long? = null
    private var collectedMs = 0L
    private var jankyFrames = 0
    private var frozenFrames = 0
    private var droppedReports = 0
    private var currentJankStreak = 0
    private var maxJankStreak = 0

    fun addFrame(totalMs: Float, isJanky: Boolean, refreshRateHz: Float) {
        totals.add(totalMs)
        if (isJanky) {
            jankyFrames++
            currentJankStreak++
            maxJankStreak = max(maxJankStreak, currentJankStreak)
        } else {
            currentJankStreak = 0
        }
        if (totalMs > SessionStats.FROZEN_FRAME_MS) frozenFrames++
        confidence.addRefreshRate(refreshRateHz)
    }

    fun addDroppedReports(count: Int) {
        droppedReports += count
    }

    fun addThermalLevel(level: ThermalLevel) = confidence.addThermalLevel(level)

    fun addSlowListener(callMs: Float) = confidence.addSlowListener(callMs)

    fun addBattery(sample: BatterySample) = confidence.addBattery(sample)

    fun startCollecting() {
        if (collectingSinceMs == null) collectingSinceMs = clock.elapsedRealtimeMs()
    }

    fun stopCollecting() {
        val startedMs = collectingSinceMs ?: return
        collectedMs += clock.elapsedRealtimeMs() - startedMs
        collectingSinceMs = null
    }

    fun stats(): SessionStats {
        val frames = totals.count
        return SessionStats(
            frames = frames,
            durationMs = collectedDurationMs(),
            p50FrameMs = totals.percentile(P50),
            p95FrameMs = totals.percentile(P95),
            p99FrameMs = totals.percentile(P99),
            jankPercent = if (frames == 0) 0f else jankyFrames * PERCENT / frames,
            frozenFrames = frozenFrames,
            maxJankStreak = maxJankStreak,
            droppedReports = droppedReports,
            confidence = confidence.confidence(frames = frames, droppedReports = droppedReports),
        )
    }

    fun clear() {
        totals.clear()
        confidence.clear()
        jankyFrames = 0
        frozenFrames = 0
        droppedReports = 0
        currentJankStreak = 0
        maxJankStreak = 0
        collectedMs = 0L
        if (collectingSinceMs != null) collectingSinceMs = clock.elapsedRealtimeMs()
    }

    private fun collectedDurationMs(): Long =
        collectedMs + (collectingSinceMs?.let { clock.elapsedRealtimeMs() - it } ?: 0L)
}
