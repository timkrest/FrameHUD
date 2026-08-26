package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.ThermalLevel
import kotlin.math.max
import kotlin.math.roundToInt

@WorkerThread
internal class SessionAccumulator(private val clock: MetricsClock, isEmulator: Boolean = false) {

    private val totals = LatencyHistogram()
    private val confidence = ConfidenceTracker(isEmulator)
    private val framesPerBudgetMs = mutableMapOf<Int, FrameCount>()
    private val phaseSumsMs = DoubleArray(FramePhase.entries.size)
    private var hasReportedGpuDuration = false
    private var collectingSinceMs: Long? = null
    private var collectedMs = 0L
    private var jankyFrames = 0
    private var lostTimeSumMs = 0.0
    private var frozenFrames = 0
    private var droppedReports = 0
    private var currentJankStreak = 0
    private var maxJankStreak = 0

    fun addFrame(durationsMs: FloatArray, overrunMs: Float, refreshRateHz: Float, frameBudgetMs: Float) {
        framesPerBudgetMs.getOrPut(frameBudgetMs.roundToInt()) { FrameCount() }.frames++
        val totalMs = durationsMs[FramePhase.TOTAL.ordinal]
        totals.add(totalMs)
        for (ordinal in phaseSumsMs.indices) {
            phaseSumsMs[ordinal] += durationsMs[ordinal]
        }
        if (durationsMs[FramePhase.GPU.ordinal] > 0f) hasReportedGpuDuration = true
        if (overrunMs > 0f) {
            jankyFrames++
            lostTimeSumMs += overrunMs
            currentJankStreak++
            maxJankStreak = max(maxJankStreak, currentJankStreak)
        } else {
            currentJankStreak = 0
        }
        if (totalMs > IntervalStats.FROZEN_FRAME_MS) frozenFrames++
        confidence.addRefreshRate(refreshRateHz)
    }

    fun addDroppedReports(count: Int) {
        droppedReports += count
    }

    fun frameBudgetMs(): Int? {
        val (budget, counted) = framesPerBudgetMs.maxByOrNull { it.value.frames } ?: return null
        return budget.takeIf { counted.frames >= totals.count * DOMINANT_BUDGET_SHARE }
    }

    fun addThermalLevel(level: ThermalLevel) = confidence.addThermalLevel(level)

    fun addSlowListener(callMs: Float) = confidence.addSlowListener(callMs)

    fun addBattery(sample: BatterySample) = confidence.addBattery(sample)

    fun startCollecting() {
        if (collectingSinceMs == null) collectingSinceMs = clock.elapsedRealtimeMs()
    }

    fun stopCollecting() {
        currentJankStreak = 0
        val startedMs = collectingSinceMs ?: return
        collectedMs += clock.elapsedRealtimeMs() - startedMs
        collectingSinceMs = null
    }

    fun stats(): IntervalStats {
        val frames = totals.count
        return IntervalStats(
            frames = frames,
            durationMs = collectedDurationMs(),
            p50FrameMs = totals.percentile(P50),
            p95FrameMs = totals.percentile(P95),
            p99FrameMs = totals.percentile(P99),
            jankPercent = if (frames == 0) 0f else jankyFrames * PERCENT / frames,
            lostTimeMs = lostTimeSumMs.toFloat(),
            frozenFrames = frozenFrames,
            maxJankStreak = maxJankStreak,
            droppedReports = droppedReports,
            phases = phaseAverages(frames),
            confidence = confidence.confidence(frames = frames, droppedReports = droppedReports),
        )
    }

    private fun phaseAverages(frames: Int): PhaseAverages {
        if (frames == 0) return PhaseAverages.EMPTY
        return PhaseAverages.of { phase ->
            if (phase == FramePhase.GPU && !hasReportedGpuDuration) {
                null
            } else {
                (phaseSumsMs[phase.ordinal] / frames).toFloat()
            }
        }
    }

    fun clear() {
        totals.clear()
        confidence.clear()
        framesPerBudgetMs.clear()
        phaseSumsMs.fill(0.0)
        hasReportedGpuDuration = false
        jankyFrames = 0
        lostTimeSumMs = 0.0
        frozenFrames = 0
        droppedReports = 0
        currentJankStreak = 0
        maxJankStreak = 0
        collectedMs = 0L
        if (collectingSinceMs != null) collectingSinceMs = clock.elapsedRealtimeMs()
    }

    private fun collectedDurationMs(): Long =
        collectedMs + (collectingSinceMs?.let { clock.elapsedRealtimeMs() - it } ?: 0L)

    private class FrameCount {
        var frames = 0
    }

    private companion object {
        const val DOMINANT_BUDGET_SHARE = 0.95f
    }
}
