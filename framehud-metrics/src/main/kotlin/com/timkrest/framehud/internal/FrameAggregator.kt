package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.Incident
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

    private val accumulators = IntervalAccumulators(clock, isEmulator, config.frameBudgetsMs)

    private val worstFrames = WorstFrames(WORST_FRAME_CAPACITY)

    private val incidents = IncidentRecorder(
        clock = clock,
        isEmulator = isEmulator,
        framesBeforeTrigger = INCIDENT_FRAMES_BEFORE_TRIGGER,
        framesAfterTrigger = INCIDENT_FRAMES_AFTER_TRIGGER,
        keptIncidents = KEPT_INCIDENTS,
    )

    private val metricsReadings = FreezableReading(PerformanceMetrics.EMPTY)

    @get:AnyThread
    val metrics: StateFlow<PerformanceMetrics> = metricsReadings.published

    val liveMetrics: PerformanceMetrics get() = metricsReadings.live

    val screenName: String? get() = accumulators.screenName

    private var lastUpdateTime = 0L
    private var display = displayOf(config.fallbackRefreshRateHz)
    private var isDrainingToIdle = false

    private var hasReportedGpuDuration = false

    fun addFrame(
        screen: String?,
        durationsMs: FloatArray,
        totalDurationNs: Long,
        deadlineNs: Long?,
        frameEndNs: Long,
        refreshRateHz: Float?,
    ) {
        val frameDisplay = displayOf(refreshRateHz ?: config.fallbackRefreshRateHz, deadlineNs)
        val totalMs = durationsMs[FramePhase.TOTAL.ordinal]
        val overrunMs = frameOverrunMs(totalDurationNs, deadlineNs, totalMs, frameDisplay)

        accumulators.addFrame(
            fromScreen = screen,
            durationsMs = durationsMs,
            overrunMs = overrunMs,
            refreshRateHz = frameDisplay.refreshRateHz,
            frameBudgetMs = frameDisplay.frameBudgetMs,
        )
        if (!isMeasuredScreen(screen)) return

        display = frameDisplay
        val judgedOverrunMs =
            overrunAgainst(accumulators.activeBudgetMs, totalMs = totalMs, displayOverrunMs = overrunMs)
        hasReportedGpuDuration = hasReportedGpuDuration || durationsMs[FramePhase.GPU.ordinal] > 0f
        frameWindow.add(durationsMs = durationsMs, overrunMs = judgedOverrunMs, frameEndNs = frameEndNs)
        worstFrames.add(totalMs = totalMs, endNs = frameEndNs)
        incidents.addFrame(
            durationsMs = durationsMs,
            overrunMs = judgedOverrunMs,
            refreshRateHz = frameDisplay.refreshRateHz,
            frameBudgetMs = budgetInForceMs(),
            endNs = frameEndNs,
        )

        isDrainingToIdle = true
        maybeEmit()
    }

    fun addDroppedReports(screen: String?, count: Int) {
        accumulators.addDroppedReports(fromScreen = screen, count = count)
        if (isMeasuredScreen(screen)) incidents.addDroppedReports(count)
    }

    fun addThermalLevel(level: ThermalLevel) = accumulators.addThermalLevel(level)

    fun addBattery(sample: BatterySample) = accumulators.addBattery(sample)

    fun addSlowListener(callMs: Float) = accumulators.addSlowListener(callMs)

    fun onTick() {
        incidents.onTick()
        if (isDrainingToIdle) maybeEmit() else refreshLiveSession()
    }

    private fun refreshLiveSession() {
        metricsReadings.updateLive(metricsReadings.live.copy(session = accumulators.sessionStats()))
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        metricsReadings.setFrozen(frozen)
    }

    fun startCollecting(label: String? = null) = accumulators.startCollecting(label)

    fun stopCollecting() {
        accumulators.stopCollecting()
        incidents.endScreen()
    }

    fun restartScreen(label: String? = null): IntervalStats {
        incidents.endScreen()
        return accumulators.restartScreen(label)
    }

    fun beginWindow(screen: String) = accumulators.beginWindow(screen)

    fun endWindow(screen: String?) = accumulators.endWindow(screen)

    fun armIncident(trigger: FrameHudEvent.IncidentTrigger, readings: IncidentReadings) =
        incidents.arm(trigger, readings)

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

    fun incidents(): List<Incident> = incidents.incidents()

    fun reset() {
        frameWindow.clear()
        accumulators.clear()
        worstFrames.clear()
        incidents.clear()
        isDrainingToIdle = false
        lastUpdateTime = 0L
        metricsReadings.reset(
            PerformanceMetrics(
                window = FrameWindowStats(frameBudgetMs = budgetInForceMs()),
                display = display,
            ),
        )
    }

    fun updateConfig(newConfig: FrameHudConfig) {
        val previousWindowFrames = config.metricsSampleWindowFrames
        config = newConfig
        accumulators.updateBudgets(newConfig.frameBudgetsMs)
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
        val history = frameWindow.history()
        val judgedBudgetMs = history.latestBudgetMs() ?: budgetInForceMs()
        val metrics = PerformanceMetrics(
            phases = frameWindow.phases(hasReportedGpuDuration),
            window = FrameWindowStats(
                fps = fps,
                jankPercent = frameWindow.jankPercent(),
                p95FrameMs = frameWindow.totalPercentile(P95),
                worstFrameMs = frameWindow.worstTotalMs(),
                frameBudgetMs = judgedBudgetMs,
                history = history,
            ),
            session = accumulators.sessionStats(),
            display = display,
        )
        metricsReadings.update(metrics)
        if (fps == 0) isDrainingToIdle = false
    }

    private fun budgetInForceMs(): Float = accumulators.activeBudgetMs?.toFloat() ?: display.frameBudgetMs

    private fun isMeasuredScreen(screen: String?): Boolean = screen == accumulators.screenName

    private fun frameOverrunMs(
        totalDurationNs: Long,
        deadlineNs: Long?,
        totalDurationMs: Float,
        frameDisplay: DisplayInfo,
    ): Float = if (deadlineNs != null) {
        (totalDurationNs - deadlineNs) / NS_PER_MS
    } else {
        totalDurationMs - frameDisplay.frameBudgetMs
    }
}

private const val WORST_FRAME_CAPACITY = 10

private const val INCIDENT_FRAMES_BEFORE_TRIGGER = 60

private const val INCIDENT_FRAMES_AFTER_TRIGGER = 30

private const val KEPT_INCIDENTS = 20

private fun FrameHistory.latestBudgetMs(): Float? = if (size == 0) null else deadlineMsAt(size - 1)

private fun displayOf(refreshRateHz: Float, deadlineNs: Long? = null) = DisplayInfo(
    refreshRateHz = refreshRateHz,
    frameBudgetMs = if (deadlineNs != null) deadlineNs / NS_PER_MS else MS_PER_SECOND / refreshRateHz,
)
