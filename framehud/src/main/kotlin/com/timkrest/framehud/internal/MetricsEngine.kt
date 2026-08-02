package com.timkrest.framehud.internal

import android.content.Context
import android.view.Window
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The metrics thread, the aggregation it feeds, the monitors sampled alongside it and the events
 * they produce.
 *
 * Readings are safe from any thread. The rest is main thread only, except [awaitSessionStats].
 */
internal class MetricsEngine(
    private val config: () -> FrameHudConfig,
    clock: MetricsClock = SystemMetricsClock,
) {

    private val aggregator = FrameAggregator(config(), clock)
    private val collector = FrameMetricsCollector(aggregator, clock)
    private val vsyncMonitor = VsyncRateMonitor()
    private val memoryMonitor = MemoryStatsMonitor()
    private val thermalMonitor = ThermalMonitor()
    private val eventDispatcher = EventDispatcher()

    @Volatile
    private var screenName: String? = null

    /**
     * Outlives a single screen: started on the first [start] and replaced only when the configured
     * thread name changes. [stop] leaves it idle instead of tearing it down, so leaving a screen
     * never waits on a thread — and the aggregates stay readable afterwards.
     */
    @Volatile
    private var sampler: MetricsSampler? = null

    /** The thread outlives [stop], so its absence cannot stand in for "collecting". */
    private var isRunning = false

    val metrics: StateFlow<PerformanceMetrics> get() = aggregator.metrics
    val vsyncRate: StateFlow<Int> get() = vsyncMonitor.ratePerSecond
    val memoryStats: StateFlow<MemoryStats> get() = memoryMonitor.stats
    val thermalStats: StateFlow<ThermalStats> get() = thermalMonitor.stats

    fun start(context: Context) {
        isRunning = true
        thermalMonitor.bind(context)
        val running = sampler ?: startSampler(config().metricsThreadName)
        running.post(::sampleMonitors) // the first tick is a whole throttle interval away
        running.startTicking()
    }

    fun stop() {
        isRunning = false
        unbindWindow()
        thermalMonitor.unbind()
        sampler?.stopTicking()
        setFrozen(false)
    }

    fun bindWindow(window: Window, screen: String?) {
        screenName = screen
        val sampler = sampler?.takeIf { isRunning } ?: return
        if (!sampler.bind(window)) return
        sampler.post(aggregator::startCollecting)
        vsyncMonitor.start()
    }

    fun unbindWindow() {
        val sampler = sampler ?: return
        if (!sampler.unbind()) return
        // Captured now — the next screen may bind before this runs.
        val endedScreen = screenName
        val listeners = config().eventListeners
        sampler.post {
            aggregator.stopCollecting()
            eventDispatcher.onScreenEnded(listeners = listeners, stats = aggregator.screenStats(), screen = endedScreen)
        }
        vsyncMonitor.stop()
    }

    fun applyConfig(newConfig: FrameHudConfig) {
        val running = sampler
        if (running != null && running.threadName != newConfig.metricsThreadName) {
            replaceSampler(newConfig.metricsThreadName)
        }
        onMetricsThread { aggregator.updateConfig(newConfig) }
    }

    fun setFrozen(frozen: Boolean) {
        aggregator.setFrozen(frozen)
        memoryMonitor.setFrozen(frozen)
        vsyncMonitor.setFrozen(frozen)
        thermalMonitor.setFrozen(frozen)
    }

    fun reset() {
        onMetricsThread {
            aggregator.reset()
            memoryMonitor.reset()
            eventDispatcher.reset()
        }
    }

    /**
     * Blocks. Null when nothing was ever collected or the read timed out.
     *
     * The metrics thread survives [stop], so a test can still read the aggregates once the last
     * activity is gone.
     */
    fun awaitSessionStats(timeoutMs: Long): SessionStats? {
        val sampler = sampler ?: return null
        val stats = AtomicReference<SessionStats>()
        val done = CountDownLatch(1)
        val posted = sampler.post {
            try {
                stats.set(aggregator.sessionStats())
            } finally {
                done.countDown()
            }
        }
        if (!posted) return null
        return if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) stats.get() else null
    }

    private fun startSampler(threadName: String, predecessor: MetricsSampler? = null): MetricsSampler {
        val started = MetricsSampler(
            threadName = threadName,
            listener = collector,
            tickIntervalMs = ::tickIntervalMs,
            onTick = ::onTick,
            predecessor = predecessor,
        )
        sampler = started
        return started
    }

    private fun replaceSampler(threadName: String) {
        val previous = sampler ?: return
        val window = previous.quit()
        val started = startSampler(threadName, predecessor = previous)
        window?.let(started::bind)
        if (isRunning) started.startTicking()
    }

    private fun onMetricsThread(action: () -> Unit) {
        if (sampler?.post(action) != true) action()
    }

    private fun sampleMonitors() {
        memoryMonitor.sample()
        thermalMonitor.sample()
    }

    private fun onTick() {
        aggregator.onTick()
        sampleMonitors()
        eventDispatcher.onSample(
            listeners = config().eventListeners,
            metrics = aggregator.metrics.value,
            memory = memoryMonitor.stats.value,
            thermal = thermalMonitor.stats.value,
            vsyncRate = vsyncMonitor.ratePerSecond.value,
            screen = screenName,
        )
    }

    private fun tickIntervalMs(): Long = maxOf(config().metricsThrottleIntervalMs, MIN_TICK_INTERVAL_MS)

    private companion object {
        const val MIN_TICK_INTERVAL_MS = 250L
    }
}
