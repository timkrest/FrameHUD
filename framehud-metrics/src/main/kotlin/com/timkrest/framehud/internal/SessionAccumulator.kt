package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel
import kotlin.math.max

@WorkerThread
internal class SessionAccumulator(private val clock: MetricsClock, isEmulator: Boolean = false) {

    private val totals = LatencyHistogram()
    private val confidence = ConfidenceTracker(isEmulator)
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

    fun addFrame(durationsMs: FloatArray, overrunMs: Float, refreshRateHz: Float) {
        val totalMs = durationsMs[FramePhase.TOTAL.ordinal]
        totals.add(totalMs)
        for (phase in FramePhase.entries) {
            phaseSumsMs[phase.ordinal] += durationsMs[phase.ordinal]
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
        fun average(phase: FramePhase) = (phaseSumsMs[phase.ordinal] / frames).toFloat()
        return PhaseAverages(
            unknownDelay = average(FramePhase.UNKNOWN_DELAY),
            input = average(FramePhase.INPUT),
            animation = average(FramePhase.ANIMATION),
            layout = average(FramePhase.LAYOUT),
            draw = average(FramePhase.DRAW),
            sync = average(FramePhase.SYNC),
            commandIssue = average(FramePhase.COMMAND_ISSUE),
            swapBuffers = average(FramePhase.SWAP_BUFFERS),
            gpu = average(FramePhase.GPU),
            total = average(FramePhase.TOTAL),
            isGpuAvailable = FramePhase.GPU.isAvailable && hasReportedGpuDuration,
        )
    }

    fun clear() {
        totals.clear()
        confidence.clear()
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
}
