package com.timkrest.framehud.internal

import android.content.Context
import android.util.Log
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@MainThread
internal class MetricsEngine(
    private val config: () -> FrameHudConfig,
    clock: MetricsClock = SystemMetricsClock,
) {

    private val aggregator = FrameAggregator(config(), clock, isEmulator = isRunningOnEmulator())
    private val eventDispatcher = EventDispatcher(clock = clock, onSlowListener = aggregator::addSlowListener)
    private val tracer = FrameHudTracer()
    private val collector = FrameMetricsCollector(
        aggregator = aggregator,
        clock = clock,
        display = { sampler?.display },
        onFirstFrame = ::onFirstFrame,
    )
    private val choreographerTickMonitor = ChoreographerTickMonitor()
    private val memoryMonitor = MemoryStatsMonitor()
    private val thermalMonitor = ThermalMonitor()
    private val batteryMonitor = BatteryMonitor()

    private val measuredScreen = MeasuredScreen()

    private var sessionId = 0

    val screenOverride: String? get() = measuredScreen.screenOverride

    @Volatile
    var context: Map<String, String> = emptyMap()
        private set

    fun setContext(value: Map<String, String>) {
        context = value.toMap()
    }

    private val _activeMark = MutableStateFlow<String?>(null)

    private val samplerLock = Any()

    @Volatile
    private var sampler: MetricsSampler? = null

    private var isRunning = false

    @get:AnyThread
    val metrics: StateFlow<PerformanceMetrics> get() = aggregator.metrics

    @get:AnyThread
    val choreographerTicksPerSecond: StateFlow<Int> get() = choreographerTickMonitor.ticksPerSecond

    @get:AnyThread
    val memoryStats: StateFlow<MemoryStats> get() = memoryMonitor.stats

    @get:AnyThread
    val thermalStats: StateFlow<ThermalStats> get() = thermalMonitor.stats

    @get:AnyThread
    val activeMark: StateFlow<String?> = _activeMark.asStateFlow()

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        requireSampler().post {
            thermalMonitor.bind(context)
            batteryMonitor.bind(context)
        }
    }

    fun stop() {
        isRunning = false
        unbindWindow()
        sampler?.post {
            thermalMonitor.unbind()
            batteryMonitor.unbind()
        }
    }

    fun bindWindow(window: Window, screen: String?, creation: ScreenCreation?) {
        val sampler = sampler?.takeIf { isRunning } ?: return
        if (sampler.isBoundTo(window)) return
        val label = measuredScreen.bind(screen)
        tracer.screenChanged(label)
        collector.expectFirstFrame(window = window, creation = creation)
        sampler.bind(window)
        sampler.post {
            memoryMonitor.startCollecting()
            sampleMonitors()
            aggregator.startCollecting(label)
        }
        choreographerTickMonitor.start()
    }

    fun setScreen(name: String?) {
        when (val rename = measuredScreen.rename(name)) {
            MeasuredScreen.Rename.None -> Unit
            MeasuredScreen.Rename.WhileUnbound -> endMark()
            is MeasuredScreen.Rename.Renamed -> restartScreen(rename)
        }
    }

    private fun restartScreen(rename: MeasuredScreen.Rename.Renamed) {
        endMark(endedScreen = rename.previous)
        val sampler = sampler?.takeIf { it.isBound } ?: return
        tracer.screenChanged(rename.current)
        val listeners = config().eventListeners
        val endedContext = context
        sampler.post {
            eventDispatcher.onScreenEnded(
                listeners = listeners,
                stats = aggregator.restartScreen(rename.current),
                screen = rename.previous,
                context = endedContext,
            )
        }
    }

    fun setMark(name: String?) {
        if (_activeMark.value == name) return
        endMark()
        if (name == null) return
        _activeMark.value = name
        tracer.markChanged(name)
        onAggregates { aggregator.beginMark(name) }
    }

    fun unbindWindow() {
        val sampler = sampler ?: return
        if (sampler.unbind() == null) return
        collector.forgetFirstFrame()
        endMark()
        val endedScreen = measuredScreen.unbind()
        tracer.screenChanged(null)
        val listeners = config().eventListeners
        val endedContext = context
        sampler.post {
            aggregator.stopCollecting()
            memoryMonitor.stopCollecting()
            eventDispatcher.onScreenEnded(
                listeners = listeners,
                stats = aggregator.screenStats(),
                screen = endedScreen,
                context = endedContext,
            )
            tracer.jankBurstChanged(false)
        }
        choreographerTickMonitor.stop()
    }

    fun applyConfig(newConfig: FrameHudConfig) {
        val running = sampler
        if (running != null && running.threadName != newConfig.metricsThreadName) {
            replaceSampler(newConfig.metricsThreadName)
        }
        onAggregates { aggregator.updateConfig(newConfig) }
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        aggregator.setFrozen(frozen)
        memoryMonitor.setFrozen(frozen)
        choreographerTickMonitor.setFrozen(frozen)
        thermalMonitor.setFrozen(frozen)
    }

    @AnyThread
    fun reset() {
        onAggregates {
            sessionId++
            aggregator.reset()
            memoryMonitor.reset()
            eventDispatcher.reset()
        }
    }

    @WorkerThread
    fun awaitSessionStats(timeoutMs: Long): SessionStats? = awaitOnMetricsThread(timeoutMs, aggregator::sessionStats)

    @WorkerThread
    fun awaitExportStats(timeoutMs: Long): ExportStats? = awaitOnMetricsThread(timeoutMs) {
        ExportStats(
            session = aggregator.sessionStats(),
            screen = aggregator.screenStats(),
            intervals = aggregator.intervals(),
            metrics = aggregator.refreshMetricsIgnoringThrottle(),
            memory = memoryMonitor.liveStats,
            thermal = thermalMonitor.liveStats,
            worstFrames = aggregator.worstFrames(),
            screenName = aggregator.screenName,
            mark = _activeMark.value,
            context = context,
        )
    }

    @WorkerThread
    fun awaitBaselineStats(timeoutMs: Long): BaselineStats? = awaitOnMetricsThread(timeoutMs) {
        BaselineStats(
            sessionId = sessionId,
            session = aggregator.sessionStats(),
            environment = BaselineEnvironment.current(),
            intervals = aggregator.intervals(),
        )
    }

    @WorkerThread
    private fun <T : Any> awaitOnMetricsThread(timeoutMs: Long, read: () -> T): T? {
        val sampler = sampler ?: return null
        val result = AtomicReference<T>()
        val done = CountDownLatch(1)
        val posted = sampler.post {
            try {
                result.set(read())
            } finally {
                done.countDown()
            }
        }
        if (!posted) return null
        return if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) result.get() else null
    }

    private fun endMark(endedScreen: String? = measuredScreen.active) {
        val ended = _activeMark.value ?: return
        _activeMark.value = null
        tracer.markChanged(null)
        val listeners = config().eventListeners
        val endedContext = context
        onMetricsThread {
            aggregator.endMark()?.let { stats ->
                eventDispatcher.onMarkEnded(
                    listeners = listeners,
                    stats = stats,
                    mark = ended,
                    screen = endedScreen,
                    context = endedContext,
                )
            }
        }
    }

    @WorkerThread
    private fun onFirstFrame(timeToDisplayMs: Float) {
        eventDispatcher.onFirstFrame(
            listeners = config().eventListeners,
            timeToDisplayMs = timeToDisplayMs,
            screen = aggregator.screenName,
            context = context,
        )
    }

    private fun startSampler(threadName: String, previousSampler: MetricsSampler? = null): MetricsSampler {
        val started = MetricsSampler(
            threadName = threadName,
            listener = collector,
            tickIntervalMs = ::tickIntervalMs,
            onTick = ::onTick,
            previousSampler = previousSampler,
        )
        synchronized(samplerLock) { sampler = started }
        return started
    }

    private fun replaceSampler(threadName: String) {
        synchronized(samplerLock) {
            val previous = sampler ?: return
            val started = startSampler(threadName, previousSampler = previous)
            previous.quit()?.let(started::bind)
        }
    }

    @AnyThread
    private fun requireSampler(): MetricsSampler = synchronized(samplerLock) {
        sampler ?: startSampler(config().metricsThreadName)
    }

    @AnyThread
    private fun onMetricsThread(action: () -> Unit) {
        synchronized(samplerLock) { postOrWarn(requireSampler(), action) }
    }

    @AnyThread
    private fun onAggregates(action: () -> Unit) {
        synchronized(samplerLock) {
            val running = sampler ?: return action()
            postOrWarn(running, action)
        }
    }

    private fun postOrWarn(sampler: MetricsSampler, action: () -> Unit) {
        if (!sampler.post(action)) Log.w(LOG_TAG, "The metrics thread is gone, dropped a metrics task")
    }

    @WorkerThread
    private fun sampleMonitors() {
        memoryMonitor.sample()
        thermalMonitor.sample()
        batteryMonitor.sample()
        aggregator.addThermalLevel(thermalMonitor.liveStats.level)
        aggregator.addBattery(batteryMonitor.sample)
    }

    @WorkerThread
    private fun onTick() {
        aggregator.onTick()
        sampleMonitors()
        if (sampler?.isBound != true) return
        eventDispatcher.onSample(
            listeners = config().eventListeners,
            metrics = aggregator.liveMetrics,
            memory = memoryMonitor.liveStats,
            thermal = thermalMonitor.liveStats,
            choreographerTicksPerSecond = choreographerTickMonitor.liveTicksPerSecond,
            screen = aggregator.screenName,
            mark = _activeMark.value,
            context = context,
        )
        tracer.jankBurstChanged(eventDispatcher.isInBurst)
        tracer.publishCounters(metrics = aggregator.liveMetrics, memory = memoryMonitor.liveStats)
    }

    private fun tickIntervalMs(): Long = maxOf(config().metricsThrottleIntervalMs, MIN_TICK_INTERVAL_MS)

    private companion object {
        const val MIN_TICK_INTERVAL_MS = 250L
    }

    class BaselineStats(
        val sessionId: Int,
        val session: SessionStats,
        val environment: BaselineEnvironment,
        val intervals: List<IntervalStats>,
    )

    class ExportStats(
        val session: SessionStats,
        val screen: SessionStats,
        val intervals: List<IntervalStats>,
        val metrics: PerformanceMetrics,
        val memory: MemoryStats,
        val thermal: ThermalStats,
        val worstFrames: List<WorstFrames.Frame>,
        val screenName: String?,
        val mark: String?,
        val context: Map<String, String>,
    )
}
