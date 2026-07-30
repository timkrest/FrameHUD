package io.github.timkrest.framehud.internal

import android.os.Build
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.ChecksSdkIntAtLeast
import io.github.timkrest.framehud.FrameHistory
import io.github.timkrest.framehud.FrameHudConfig
import io.github.timkrest.framehud.InternalFrameHudApi
import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.PipelineStage
import io.github.timkrest.framehud.SessionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Confined to the thread the callbacks are delivered on, apart from [metrics] and [setFrozen]. */
internal class FrameMetricsCollector(config: FrameHudConfig) : Window.OnFrameMetricsAvailableListener {

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private val hasApi26FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val hasApi31FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private var updateThrottleMs = config.throttleMs()
    private var fallbackRefreshRate = config.refreshRateHz()
    private var windowSize = config.windowSize()

    private var frameWindow = FrameWindow(windowSize)

    private val session = SessionAccumulator()

    /** Restarted for every bound window, so events can be attributed per screen. */
    private val screen = SessionAccumulator()
    private val scratchDurationsMs = FloatArray(FramePhase.entries.size)

    private val _metrics = MutableStateFlow(PerformanceMetrics.EMPTY)
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    @Volatile
    private var isFrozen = false

    private var lastUpdateTime = 0L
    private var lastRefreshRateHz = fallbackRefreshRate
    private var lastDeadlineMs = 0f
    private var isDrainingToIdle = false

    /**
     * API 31 exposes `GPU_DURATION`, but many drivers and emulators answer 0 forever, which is
     * indistinguishable from an idle GPU. The phase counts as available only once real data lands.
     */
    private var hasGpuSample = false

    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        session.addDroppedReports(dropCountSinceLastInvocation)
        screen.addDroppedReports(dropCountSinceLastInvocation)
        if (frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L) return

        lastRefreshRateHz = window.currentRefreshRate()
        if (hasApi31FrameMetrics) lastDeadlineMs = frameMetrics.getMetric(FrameMetrics.DEADLINE) / NS_PER_MS

        frameMetrics.readPhaseDurationsMs(scratchDurationsMs)
        if (!hasGpuSample && scratchDurationsMs[FramePhase.GPU.ordinal] > 0f) hasGpuSample = true
        val totalMs = scratchDurationsMs[FramePhase.TOTAL.ordinal]
        val isJanky = isFrameJanky(frameMetrics, totalMs = totalMs)

        frameWindow.add(
            durationsMs = scratchDurationsMs,
            isJanky = isJanky,
            overrunMs = frameOverrunMs(frameMetrics, totalMs = totalMs),
            frameEndNs = frameEndTimestampNs(frameMetrics),
        )
        session.addFrame(totalMs = totalMs, isJanky = isJanky)
        screen.addFrame(totalMs = totalMs, isJanky = isJanky)

        isDrainingToIdle = true
        maybeEmit()
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

    /** Resizing the window drops the frames it holds; session aggregates survive. */
    fun updateConfig(config: FrameHudConfig) {
        updateThrottleMs = config.throttleMs()
        fallbackRefreshRate = config.refreshRateHz()
        val newWindowSize = config.windowSize()
        if (newWindowSize != windowSize) {
            windowSize = newWindowSize
            frameWindow = FrameWindow(newWindowSize)
        }
    }

    private fun maybeEmit() {
        if (isFrozen) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdateTime < updateThrottleMs) return
        lastUpdateTime = now
        emitMetrics()
    }

    @OptIn(InternalFrameHudApi::class)
    private fun emitMetrics() {
        val unknownDelay = frameWindow.metricValue(FramePhase.UNKNOWN_DELAY)
        val input = frameWindow.metricValue(FramePhase.INPUT)
        val animation = frameWindow.metricValue(FramePhase.ANIMATION)
        val layout = frameWindow.metricValue(FramePhase.LAYOUT)
        val draw = frameWindow.metricValue(FramePhase.DRAW)
        val sync = frameWindow.metricValue(FramePhase.SYNC)
        val commandIssue = frameWindow.metricValue(FramePhase.COMMAND_ISSUE)
        val swapBuffers = frameWindow.metricValue(FramePhase.SWAP_BUFFERS)
        val gpu = frameWindow.metricValue(FramePhase.GPU)
        val total = frameWindow.metricValue(FramePhase.TOTAL)

        val cpu = input + animation + layout + draw
        val render = sync + commandIssue + swapBuffers
        val other = MetricValue(
            current = (total.current - unknownDelay.current - cpu.current - render.current).coerceAtLeast(0f),
            average = (total.average - unknownDelay.average - cpu.average - render.average).coerceAtLeast(0f),
        )

        val bottleneckStage = dominantStage(cpuAvg = cpu.average, renderAvg = render.average, gpuAvg = gpu.average)
        val bottleneck = when (bottleneckStage) {
            PipelineStage.CPU -> cpu
            PipelineStage.RENDER -> render
            PipelineStage.GPU -> MetricValue(current = gpu.current, average = gpu.average)
        }
        val fps = frameWindow.fps(System.nanoTime())

        _metrics.value = PerformanceMetrics(
            unknownDelay = unknownDelay,
            input = input,
            animation = animation,
            layout = layout,
            draw = draw,
            sync = sync,
            commandIssue = commandIssue,
            swapBuffers = swapBuffers,
            gpu = gpu,
            total = total,
            other = other,
            overrun = frameWindow.overrunValue(),
            bottleneck = bottleneck,
            bottleneckStage = bottleneckStage,
            fps = fps,
            windowJankPercent = frameWindow.jankPercent(),
            windowP95FrameMs = frameWindow.totalPercentile(P95),
            windowWorstFrameMs = frameWindow.worstTotalMs(),
            session = session.stats(),
            history = FrameHistory.of(frameWindow.totalHistory()),
            isGpuAvailable = FramePhase.GPU.isAvailable && hasGpuSample,
            refreshRate = lastRefreshRateHz,
            frameBudgetMs = frameBudgetMs(),
        )
        if (fps == 0) isDrainingToIdle = false
    }

    private fun isFrameJanky(frameMetrics: FrameMetrics, totalMs: Float): Boolean =
        if (hasApi31FrameMetrics) {
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION) > frameMetrics.getMetric(FrameMetrics.DEADLINE)
        } else {
            totalMs > frameBudgetMs()
        }

    private fun frameOverrunMs(frameMetrics: FrameMetrics, totalMs: Float): Float =
        if (hasApi31FrameMetrics) {
            (frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION) - frameMetrics.getMetric(FrameMetrics.DEADLINE)) / NS_PER_MS
        } else {
            totalMs - frameBudgetMs()
        }

    private fun frameBudgetMs(): Float =
        lastDeadlineMs.takeIf { it > 0f } ?: (MS_PER_SECOND / lastRefreshRateHz)

    private fun frameEndTimestampNs(frameMetrics: FrameMetrics): Long =
        if (hasApi26FrameMetrics) {
            frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) + frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        } else {
            System.nanoTime()
        }

    private fun Window.currentRefreshRate(): Float =
        (decorView.display?.refreshRate ?: fallbackRefreshRate)
            .takeIf { it > 0f }
            ?: fallbackRefreshRate

    private fun dominantStage(cpuAvg: Float, renderAvg: Float, gpuAvg: Float): PipelineStage = when {
        cpuAvg >= renderAvg && cpuAvg >= gpuAvg -> PipelineStage.CPU
        renderAvg >= gpuAvg -> PipelineStage.RENDER
        else -> PipelineStage.GPU
    }
}

/** Derived timings carry no peak of their own, so [MetricValue.peak] stays zero. */
private operator fun MetricValue.plus(other: MetricValue): MetricValue = MetricValue(
    current = current + other.current,
    average = average + other.average,
)

private fun FrameHudConfig.throttleMs(): Long = metricsThrottleIntervalMs.coerceAtLeast(0L)

private fun FrameHudConfig.windowSize(): Int = metricsSampleWindowSize.coerceAtLeast(1)

private fun FrameHudConfig.refreshRateHz(): Float =
    fallbackRefreshRateHz.takeIf { it > 0f } ?: PerformanceMetrics.DEFAULT_REFRESH_RATE_HZ
