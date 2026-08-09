package com.timkrest.framehud.internal

import android.content.Context
import android.util.Log
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

/** Readings are safe from any thread. The rest is main thread only, except [awaitSessionStats]. */
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

    private val samplerLock = Any()

    @Volatile
    private var sampler: MetricsSampler? = null

    private var isRunning = false

    val metrics: StateFlow<PerformanceMetrics> get() = aggregator.metrics
    val vsyncRate: StateFlow<Int> get() = vsyncMonitor.ratePerSecond
    val memoryStats: StateFlow<MemoryStats> get() = memoryMonitor.stats
    val thermalStats: StateFlow<ThermalStats> get() = thermalMonitor.stats

    fun start(context: Context) {
        isRunning = true
        thermalMonitor.bind(context)
        val running = sampler ?: startSampler(config().metricsThreadName)
        running.post(::sampleMonitors)
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
        val sampler = sampler?.takeIf { isRunning } ?: return
        if (!sampler.bind(window)) return
        screenName = screen
        sampler.post(aggregator::startCollecting)
        vsyncMonitor.start()
    }

    fun unbindWindow() {
        val sampler = sampler ?: return
        if (!sampler.unbind()) return
        val endedScreen = screenName
        screenName = null
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
        synchronized(samplerLock) { sampler = started }
        return started
    }

    private fun replaceSampler(threadName: String) {
        val previous = sampler ?: return
        val started = startSampler(threadName, predecessor = previous)
        previous.quit()?.let(started::bind)
        if (isRunning) started.startTicking()
    }

    private fun onMetricsThread(action: () -> Unit) {
        synchronized(samplerLock) {
            val running = sampler ?: return action()
            if (!running.post(action)) Log.w(LOG_TAG, "The metrics thread is gone, dropped a metrics task")
        }
    }

    private fun sampleMonitors() {
        memoryMonitor.sample()
        thermalMonitor.sample()
    }

    private fun onTick() {
        aggregator.onTick()
        sampleMonitors()
        if (sampler?.isBound != true) return
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
