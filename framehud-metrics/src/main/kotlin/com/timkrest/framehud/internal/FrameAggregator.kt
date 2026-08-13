package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import kotlinx.coroutines.flow.StateFlow

@WorkerThread
internal class FrameAggregator(private var config: FrameHudConfig, private val clock: MetricsClock) {

    private var frameWindow = FrameWindow(config.metricsSampleWindowFrames)

    private val session = SessionAccumulator(clock)

    private val screen = SessionAccumulator(clock)

    private var mark: SessionAccumulator? = null

    private val worstFrames = WorstFrames(WORST_FRAME_CAPACITY)

    private val readings = FreezableReading(PerformanceMetrics.EMPTY)

    @get:AnyThread
    val metrics: StateFlow<PerformanceMetrics> = readings.published

    val liveMetrics: PerformanceMetrics get() = readings.live

    private var lastUpdateTime = 0L
    private var display = displayOf(config.fallbackRefreshRateHz)
    private var isDrainingToIdle = false

    private var hasReportedGpuDuration = false

    fun addFrame(
        durationsMs: FloatArray,
        totalDurationNs: Long,
        deadlineNs: Long?,
        frameEndNs: Long,
        refreshRateHz: Float?,
    ) {
        display = displayOf(refreshRateHz ?: config.fallbackRefreshRateHz, deadlineNs)

        if (!hasReportedGpuDuration && durationsMs[FramePhase.GPU.ordinal] > 0f) {
            hasReportedGpuDuration = true
        }
        val totalMs = durationsMs[FramePhase.TOTAL.ordinal]
        val overrunMs = frameOverrunMs(totalDurationNs, deadlineNs, totalMs)
        val isJanky = overrunMs > 0f

        frameWindow.add(durationsMs = durationsMs, isJanky = isJanky, overrunMs = overrunMs, frameEndNs = frameEndNs)
        session.addFrame(totalMs = totalMs, isJanky = isJanky)
        screen.addFrame(totalMs = totalMs, isJanky = isJanky)
        mark?.addFrame(totalMs = totalMs, isJanky = isJanky)
        worstFrames.add(totalMs = totalMs, endNs = frameEndNs)

        isDrainingToIdle = true
        maybeEmit()
    }

    fun addDroppedReports(count: Int) {
        session.addDroppedReports(count)
        screen.addDroppedReports(count)
        mark?.addDroppedReports(count)
    }

    fun onTick() {
        if (!isDrainingToIdle) return
        maybeEmit()
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun startCollecting() {
        session.startCollecting()
        screen.clear()
        screen.startCollecting()
    }

    fun stopCollecting() {
        session.stopCollecting()
        screen.stopCollecting()
    }

    fun restartScreen(): SessionStats {
        screen.stopCollecting()
        val ended = screen.stats()
        screen.clear()
        screen.startCollecting()
        return ended
    }

    fun beginMark() {
        mark = SessionAccumulator(clock).apply { startCollecting() }
    }

    fun endMark(): SessionStats? {
        val ended = mark ?: return null
        mark = null
        ended.stopCollecting()
        return ended.stats()
    }

    /** Recomputes instead of waiting for the throttle, so an export never carries lagged readings. */
    fun refreshMetrics(): PerformanceMetrics {
        emitMetrics(clock.elapsedRealtimeMs())
        return liveMetrics
    }

    fun sessionStats(): SessionStats = session.stats()

    fun screenStats(): SessionStats = screen.stats()

    fun worstFrames(): List<WorstFrames.Frame> = worstFrames.snapshot()

    fun reset() {
        frameWindow.clear()
        session.clear()
        screen.clear()
        mark?.clear()
        worstFrames.clear()
        isDrainingToIdle = false
        lastUpdateTime = 0L
        readings.reset(PerformanceMetrics.EMPTY)
    }

    fun updateConfig(newConfig: FrameHudConfig) {
        val previousWindowFrames = config.metricsSampleWindowFrames
        config = newConfig
        if (newConfig.metricsSampleWindowFrames == previousWindowFrames) return
        frameWindow = FrameWindow(newConfig.metricsSampleWindowFrames)
        emitMetrics(clock.elapsedRealtimeMs())
    }

    private fun maybeEmit() {
        val now = clock.elapsedRealtimeMs()
        if (now - lastUpdateTime < config.metricsThrottleIntervalMs) return
        emitMetrics(now)
    }

    private fun emitMetrics(now: Long) {
        lastUpdateTime = now
        val fps = frameWindow.fps(clock.nanoTime())
        val metrics = PerformanceMetrics(
            phases = FramePhases(
                unknownDelay = frameWindow.metricValue(FramePhase.UNKNOWN_DELAY),
                input = frameWindow.metricValue(FramePhase.INPUT),
                animation = frameWindow.metricValue(FramePhase.ANIMATION),
                layout = frameWindow.metricValue(FramePhase.LAYOUT),
                draw = frameWindow.metricValue(FramePhase.DRAW),
                sync = frameWindow.metricValue(FramePhase.SYNC),
                commandIssue = frameWindow.metricValue(FramePhase.COMMAND_ISSUE),
                swapBuffers = frameWindow.metricValue(FramePhase.SWAP_BUFFERS),
                gpu = frameWindow.metricValue(FramePhase.GPU),
                total = frameWindow.metricValue(FramePhase.TOTAL),
                overrun = frameWindow.overrunValue(),
                isGpuAvailable = FramePhase.GPU.isAvailable && hasReportedGpuDuration,
            ),
            window = FrameWindowStats(
                fps = fps,
                jankPercent = frameWindow.jankPercent(),
                p95FrameMs = frameWindow.totalPercentile(P95),
                worstFrameMs = frameWindow.worstTotalMs(),
                history = frameWindow.history(),
            ),
            session = session.stats(),
            display = display,
        )
        readings.update(metrics)
        if (fps == 0) isDrainingToIdle = false
    }

    private fun frameOverrunMs(totalDurationNs: Long, deadlineNs: Long?, totalDurationMs: Float): Float =
        if (deadlineNs != null) {
            (totalDurationNs - deadlineNs) / NS_PER_MS
        } else {
            totalDurationMs - display.frameBudgetMs
        }
}

private const val WORST_FRAME_CAPACITY = 10

private fun displayOf(refreshRateHz: Float, deadlineNs: Long? = null) = DisplayInfo(
    refreshRateHz = refreshRateHz,
    frameBudgetMs = if (deadlineNs != null) deadlineNs / NS_PER_MS else MS_PER_SECOND / refreshRateHz,
)
