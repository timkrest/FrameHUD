package com.timkrest.framehud.internal

import android.content.Context
import android.util.Log
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.FrameHudConfig
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

    private val aggregator = FrameAggregator(config(), clock)
    private val eventDispatcher = EventDispatcher()
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

    @Volatile
    private var boundScreen: String? = null

    @Volatile
    var screenOverride: String? = null
        private set

    @Volatile
    var context: Map<String, String> = emptyMap()
        private set

    fun setContext(value: Map<String, String>) {
        context = value.toMap()
    }

    @Volatile
    private var activeScreen: String? = null

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
            sampleMonitors()
        }
    }

    fun stop() {
        isRunning = false
        unbindWindow()
        sampler?.post(thermalMonitor::unbind)
        setFrozen(false)
    }

    fun bindWindow(window: Window, screen: String?, creation: ScreenCreation?) {
        val sampler = sampler?.takeIf { isRunning } ?: return
        if (sampler.isBoundTo(window)) return
        boundScreen = screen
        val label = screenOverride ?: screen
        activeScreen = label
        tracer.screenChanged(label)
        collector.expectFirstFrame(window = window, creation = creation)
        sampler.bind(window)
        sampler.post(aggregator::startCollecting)
        choreographerTickMonitor.start()
    }

    fun setScreen(name: String?) {
        if (screenOverride == name) return
        val previous = activeScreen
        screenOverride = name
        val current = name ?: boundScreen
        if (current == previous) return
        endMark()
        activeScreen = current
        tracer.screenChanged(current)
        val sampler = sampler?.takeIf { it.isBound } ?: return
        val listeners = config().eventListeners
        val endedContext = context
        sampler.post {
            eventDispatcher.onScreenEnded(
                listeners = listeners,
                stats = aggregator.restartScreen(),
                screen = previous,
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
        onAggregates(aggregator::beginMark)
    }

    fun unbindWindow() {
        val sampler = sampler ?: return
        if (sampler.unbind() == null) return
        collector.forgetFirstFrame()
        endMark()
        val endedScreen = activeScreen
        boundScreen = null
        activeScreen = screenOverride
        tracer.screenChanged(null)
        val listeners = config().eventListeners
        val endedContext = context
        sampler.post {
            aggregator.stopCollecting()
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
            metrics = aggregator.refreshMetrics(),
            memory = memoryMonitor.liveStats,
            thermal = thermalMonitor.liveStats,
            worstFrames = aggregator.worstFrames(),
            screenName = activeScreen,
            mark = _activeMark.value,
            context = context,
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

    private fun endMark() {
        val ended = _activeMark.value ?: return
        _activeMark.value = null
        tracer.markChanged(null)
        val listeners = config().eventListeners
        val screen = activeScreen
        val endedContext = context
        onMetricsThread {
            aggregator.endMark()?.let { stats ->
                eventDispatcher.onMarkEnded(
                    listeners = listeners,
                    stats = stats,
                    mark = ended,
                    screen = screen,
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
            screen = activeScreen,
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
            screen = activeScreen,
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

    /** Captured in one metrics-thread read, so the names describe the same instant as the numbers. */
    class ExportStats(
        val session: SessionStats,
        val screen: SessionStats,
        val metrics: PerformanceMetrics,
        val memory: MemoryStats,
        val thermal: ThermalStats,
        val worstFrames: List<WorstFrames.Frame>,
        val screenName: String?,
        val mark: String?,
        val context: Map<String, String>,
    )
}
