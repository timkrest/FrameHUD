package io.github.timkrest.framehud.internal

import android.os.SystemClock
import io.github.timkrest.framehud.SessionStats
import kotlin.math.max

/** Confined to the metrics thread, like the collector that owns it. */
internal class SessionAccumulator {

    private val totals = LatencyHistogram()
    private var collectingSinceMs = 0L
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
        if (totalMs > FROZEN_FRAME_MS) frozenFrames++
    }

    fun addDroppedReports(count: Int) {
        droppedReports += count
    }

    fun startCollecting() {
        if (collectingSinceMs == 0L) collectingSinceMs = SystemClock.elapsedRealtime()
    }

    fun stopCollecting() {
        if (collectingSinceMs == 0L) return
        collectedMs += SystemClock.elapsedRealtime() - collectingSinceMs
        collectingSinceMs = 0L
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
        if (collectingSinceMs != 0L) collectingSinceMs = SystemClock.elapsedRealtime()
    }

    private fun collectedDurationMs(): Long {
        val startedMs = collectingSinceMs
        return collectedMs + if (startedMs == 0L) 0L else SystemClock.elapsedRealtime() - startedMs
    }

    companion object {
        private const val FROZEN_FRAME_MS = 700f
    }
}
