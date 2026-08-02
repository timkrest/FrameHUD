package com.timkrest.framehud.internal

import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.InternalFrameHudApi
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns frames into what the panel and the listeners read: the rolling window, the session
 * aggregates and the per-screen ones.
 *
 * Takes plain numbers rather than `FrameMetrics`, so the rules that matter — what counts as janky,
 * what the frame budget is, when a reading is published — are covered by unit tests.
 * [FrameMetricsCollector] does the platform reading.
 *
 * Confined to the metrics thread, apart from [metrics] and [setFrozen].
 */
internal class FrameAggregator(private var config: FrameHudConfig, private val clock: MetricsClock) {

    private var frameWindow = FrameWindow(config.windowSize())

    private val session = SessionAccumulator(clock)

    private val screen = SessionAccumulator(clock)

    private val _metrics = MutableStateFlow(PerformanceMetrics.EMPTY)
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    @Volatile
    private var isFrozen = false

    private var lastUpdateTime = 0L
    private var lastRefreshRateHz = config.refreshRateHz()
    private var lastFrameBudgetMs = MS_PER_SECOND / config.refreshRateHz()
    private var isDrainingToIdle = false

    /**
     * API 31 exposes `GPU_DURATION`, but many drivers and emulators answer 0 forever, which is
     * indistinguishable from an idle GPU. The phase counts as available only once real data lands.
     */
    private var hasGpuSample = false

    fun addFrame(
        durationsMs: FloatArray,
        totalDurationNs: Long,
        deadlineNs: Long?,
        frameEndNs: Long,
        refreshRateHz: Float?,
    ) {
        lastRefreshRateHz = refreshRateHz ?: config.refreshRateHz()

        if (!hasGpuSample && durationsMs[FramePhase.GPU.ordinal] > 0f) hasGpuSample = true
        val totalMs = durationsMs[FramePhase.TOTAL.ordinal]

        // Milliseconds are too coarse to tell a frame that lands exactly on the deadline from one
        // that missed it.
        val overrunMs = if (deadlineNs != null) {
            lastFrameBudgetMs = deadlineNs / NS_PER_MS
            (totalDurationNs - deadlineNs) / NS_PER_MS
        } else {
            lastFrameBudgetMs = MS_PER_SECOND / lastRefreshRateHz
            totalMs - lastFrameBudgetMs
        }
        val isJanky = overrunMs > 0f

        frameWindow.add(durationsMs = durationsMs, isJanky = isJanky, overrunMs = overrunMs, frameEndNs = frameEndNs)
        session.addFrame(totalMs = totalMs, isJanky = isJanky)
        screen.addFrame(totalMs = totalMs, isJanky = isJanky)

        isDrainingToIdle = true
        maybeEmit()
    }

    fun addDroppedReports(count: Int) {
        session.addDroppedReports(count)
        screen.addDroppedReports(count)
    }

    fun onTick() {
        if (!isDrainingToIdle) return
        maybeEmit()
    }

    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
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

    fun sessionStats(): SessionStats = session.stats()

    fun screenStats(): SessionStats = screen.stats()

    fun reset() {
        frameWindow.clear()
        session.clear()
        screen.clear()
        isDrainingToIdle = false
        lastUpdateTime = 0L
        _metrics.value = PerformanceMetrics.EMPTY
    }

    fun updateConfig(newConfig: FrameHudConfig) {
        val previousWindowSize = config.windowSize()
        config = newConfig
        if (newConfig.windowSize() != previousWindowSize) frameWindow = FrameWindow(newConfig.windowSize())
    }

    private fun maybeEmit() {
        if (isFrozen) return
        val now = clock.elapsedRealtimeMs()
        if (now - lastUpdateTime < config.throttleMs()) return
        lastUpdateTime = now
        emitMetrics()
    }

    @OptIn(InternalFrameHudApi::class)
    private fun emitMetrics() {
        val fps = frameWindow.fps(clock.nanoTime())
        _metrics.value = PerformanceMetrics(
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
                isGpuAvailable = FramePhase.GPU.isAvailable && hasGpuSample,
            ),
            window = FrameWindowStats(
                fps = fps,
                jankPercent = frameWindow.jankPercent(),
                p95FrameMs = frameWindow.totalPercentile(P95),
                worstFrameMs = frameWindow.worstTotalMs(),
                history = FrameHistory.of(frameWindow.totalHistory()),
            ),
            session = session.stats(),
            display = DisplayInfo(refreshRateHz = lastRefreshRateHz, frameBudgetMs = lastFrameBudgetMs),
        )
        if (fps == 0) isDrainingToIdle = false
    }
}

private fun FrameHudConfig.throttleMs(): Long = metricsThrottleIntervalMs.coerceAtLeast(0L)

private fun FrameHudConfig.windowSize(): Int = metricsSampleWindowSize.coerceAtLeast(1)

private fun FrameHudConfig.refreshRateHz(): Float =
    fallbackRefreshRateHz.takeIf { it > 0f } ?: DisplayInfo.DEFAULT_REFRESH_RATE_HZ
