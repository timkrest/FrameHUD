package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ThermalLevel
import kotlinx.coroutines.flow.StateFlow

@WorkerThread
internal class FrameAggregator(
    private var config: FrameHudConfig,
    private val clock: MetricsClock,
    isEmulator: Boolean,
) {

    private val frameWindow = FrameWindow(config.metricsSampleWindowFrames)

    private val accumulators = IntervalAccumulators(clock, isEmulator)

    private val worstFrames = WorstFrames(WORST_FRAME_CAPACITY)

    private val readings = FreezableReading(PerformanceMetrics.EMPTY)

    @get:AnyThread
    val metrics: StateFlow<PerformanceMetrics> = readings.published

    val liveMetrics: PerformanceMetrics get() = readings.live

    val screenName: String? get() = accumulators.screenName

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

        frameWindow.add(durationsMs = durationsMs, overrunMs = overrunMs, frameEndNs = frameEndNs)
        accumulators.addFrame(
            durationsMs = durationsMs,
            overrunMs = overrunMs,
            refreshRateHz = display.refreshRateHz,
            frameBudgetMs = display.frameBudgetMs,
        )
        worstFrames.add(totalMs = totalMs, endNs = frameEndNs)

        isDrainingToIdle = true
        maybeEmit()
    }

    fun addDroppedReports(count: Int) = accumulators.addDroppedReports(count)

    fun addThermalLevel(level: ThermalLevel) = accumulators.addThermalLevel(level)

    fun addBattery(sample: BatterySample) = accumulators.addBattery(sample)

    fun addSlowListener(callMs: Float) = accumulators.addSlowListener(callMs)

    fun onTick() {
        if (isDrainingToIdle) maybeEmit() else refreshLiveSession()
    }

    private fun refreshLiveSession() {
        readings.updateLive(readings.live.copy(session = accumulators.sessionStats()))
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun startCollecting(label: String? = null) = accumulators.startCollecting(label)

    fun stopCollecting() = accumulators.stopCollecting()

    fun restartScreen(label: String? = null): IntervalStats = accumulators.restartScreen(label)

    fun beginMark(name: String) = accumulators.beginMark(name)

    fun endMark(): IntervalStats? = accumulators.endMark()

    fun refreshMetricsIgnoringThrottle(): PerformanceMetrics {
        emitMetrics(clock.elapsedRealtimeMs())
        return liveMetrics
    }

    fun sessionStats(): IntervalStats = accumulators.sessionStats()

    fun screenStats(): IntervalStats = accumulators.screenStats()

    fun intervals(): List<IntervalReport> = accumulators.intervals()

    fun worstFrames(): List<WorstFrames.Frame> = worstFrames.snapshot()

    fun reset() {
        frameWindow.clear()
        accumulators.clear()
        worstFrames.clear()
        isDrainingToIdle = false
        lastUpdateTime = 0L
        readings.reset(PerformanceMetrics.EMPTY)
    }

    fun updateConfig(newConfig: FrameHudConfig) {
        val previousWindowFrames = config.metricsSampleWindowFrames
        config = newConfig
        if (newConfig.metricsSampleWindowFrames == previousWindowFrames) return
        frameWindow.resizeTo(newConfig.metricsSampleWindowFrames)
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
            phases = frameWindow.phases(hasReportedGpuDuration),
            window = FrameWindowStats(
                fps = fps,
                jankPercent = frameWindow.jankPercent(),
                p95FrameMs = frameWindow.totalPercentile(P95),
                worstFrameMs = frameWindow.worstTotalMs(),
                history = frameWindow.history(),
            ),
            session = accumulators.sessionStats(),
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
