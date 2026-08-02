package com.timkrest.framehud.internal

import com.timkrest.framehud.SessionStats
import kotlin.math.max

/** Confined to the metrics thread, like the aggregator that owns it. */
internal class SessionAccumulator(private val clock: MetricsClock) {

    private val totals = LatencyHistogram()
    private var collectingSinceMs: Long? = null
    private var collectedMs = 0L
    private var jankyFrames = 0
    private var frozenFrames = 0
    private var droppedReports = 0
    private var currentJankStreak = 0
    private var maxJankStreak = 0

    fun addFrame(totalMs: Float, isJanky: Boolean) {
        totals.add(totalMs)
        if (isJanky) {
            jankyFrames++
            currentJankStreak++
            maxJankStreak = max(maxJankStreak, currentJankStreak)
        } else {
            currentJankStreak = 0
        }
        if (totalMs > SessionStats.FROZEN_FRAME_MS) frozenFrames++
    }

    fun addDroppedReports(count: Int) {
        droppedReports += count
    }

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
        )
    }

    fun clear() {
        totals.clear()
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
