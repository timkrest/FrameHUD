package com.timkrest.framehud.internal

import android.content.Context
import android.os.Looper
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameHudCounter
import com.timkrest.framehud.Incident
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@MainThread
internal class MetricsEngine(
    private val config: () -> FrameHudConfig,
    clock: MetricsClock = SystemMetricsClock,
) {

    private val aggregator = FrameAggregator(config(), clock, isEmulator = isEmulatorDevice)
    private val eventDispatcher = EventDispatcher(
        clock = clock,
        config = config,
        onSlowListener = aggregator::addSlowListener,
    )
    private val tracer = FrameHudTracer()
    private val windows = MeasuredWindows()
    private val collector = FrameMetricsCollector(
        aggregator = aggregator,
        clock = clock,
        windows = windows,
        onFirstFrame = ::onFirstFrame,
        onUsableFrame = ::onUsableFrame,
    )
    private val choreographerTickMonitor = ChoreographerTickMonitor()
    private val memoryMonitor = MemoryStatsMonitor()
    private val thermalMonitor = ThermalMonitor()
    private val batteryMonitor = BatteryMonitor()
    private val processMonitor = ProcessStatsMonitor(clock, PROCESS_SAMPLE_INTERVAL_MS)
    private val counterRegistry = CounterRegistry()
    private val flightRecorder = FlightRecorder(clock)
    private val mainThreadWatchdog = MainThreadWatchdog(
        mainThread = Looper.getMainLooper().thread,
        lastTickMs = choreographerTickMonitor::lastTickUptimeMs,
    )

    private val measuredScreen = MeasuredScreen()

    private var sessionId = 0

    private val reportedFailures = mutableSetOf<String>()

    val screenOverride: String? get() = measuredScreen.screenOverride

    @Volatile
    var context: Map<String, String> = emptyMap()
        private set

    fun setContext(value: Map<String, String>) {
        context = value.toMap()
    }

    private val _activeMark = MutableStateFlow<String?>(null)

    private val diagnosisReadings = FreezableReading(JankDiagnosis.HEALTHY)

    private val metricsThread = MetricsThread(
        listener = collector,
        tickIntervalMs = ::tickIntervalMs,
        onTick = ::onTick,
    )

    @Volatile
    private var focusedWindow: Window? = null

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
    val processStats: StateFlow<ProcessStats> get() = processMonitor.stats

    @get:AnyThread
    val counters: StateFlow<List<CounterReading>> get() = counterRegistry.counters

    @get:AnyThread
    val activeMark: StateFlow<String?> = _activeMark.asStateFlow()

    @get:AnyThread
    val diagnosis: StateFlow<JankDiagnosis> = diagnosisReadings.published

    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        metricsThread.require(config().metricsThreadName).post {
            thermalMonitor.bind(context)
            batteryMonitor.bind(context)
        }
    }

    fun stop() {
        isRunning = false
        unbindWindow()
        forgetAllWindows()
        mainThreadWatchdog.stop()
        metricsThread.started?.post {
            thermalMonitor.unbind()
            batteryMonitor.unbind()
            processMonitor.stop()
        }
    }

    fun bindWindow(window: Window, screen: String?, start: ScreenStart?) {
        val sampler = metricsThread.started?.takeIf { isRunning } ?: return
        if (focusedWindow === window) return
        unbindFocusedWindow()
        val label = measuredScreen.bind(screen)
        tracer.screenChanged(label)
        collector.expectScreen(window = window, start = start)
        focusedWindow = window
        windows.add(window, label)
        sampler.post {
            memoryMonitor.startCollecting()
            processMonitor.startCollecting()
            sampleMonitors()
            aggregator.startCollecting(label)
        }
        sampler.bind(window)
        sampler.startTicking()
        choreographerTickMonitor.start()
        mainThreadWatchdog.startWatching()
    }

    fun measureWindow(window: Window, screen: String) {
        val sampler = metricsThread.started?.takeIf { isRunning } ?: return
        if (windows[window] != null) return
        windows.add(window, screen)
        sampler.post { aggregator.beginWindow(screen) }
        sampler.bind(window)
    }

    fun forgetWindow(window: Window) {
        val sampler = metricsThread.started ?: return
        if (window === focusedWindow) return
        val screen = (windows.remove(window) ?: return).screen
        sampler.unbind(window)
        if (windows.stillMeasures(screen)) return
        sampler.post { aggregator.endWindow(screen) }
    }

    private fun forgetAllWindows() {
        val sampler = metricsThread.started ?: return
        val forgotten = windows.clear()
        if (forgotten.isEmpty()) return
        forgotten.mapNotNull { it.get() }.forEach(sampler::unbind)
        val screens = forgotten.map { it.screen }
        sampler.post { screens.forEach(aggregator::endWindow) }
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
        val sampler = metricsThread.started ?: return
        val measured = focusedWindow?.let(windows::get) ?: return
        tracer.screenChanged(rename.current)
        collector.restartScreen()
        val listeners = config().eventListeners
        val endedContext = context
        sampler.post {
            measured.screen = rename.current
            eventDispatcher.onScreenEnded(
                listenersWhenItEnded = listeners,
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

    @AnyThread
    fun counter(name: String): FrameHudCounter = counterRegistry.counter(name)

    @AnyThread
    fun reportUsable(start: ScreenStart? = null) {
        collector.reportUsable(start)
    }

    @AnyThread
    fun retainTrace() {
        onAggregates(::askForTheTrace)
    }

    fun unbindWindow() {
        val sampler = metricsThread.started ?: return
        if (!unbindFocusedWindow()) return
        sampler.stopTicking()
        collector.forgetScreen()
        endMark()
        val endedScreen = measuredScreen.unbind()
        tracer.screenChanged(null)
        val listeners = config().eventListeners
        val endedContext = context
        sampler.post {
            diagnosisReadings.update(JankDiagnosis.HEALTHY)
            aggregator.stopCollecting()
            memoryMonitor.stopCollecting()
            processMonitor.stopCollecting()
            eventDispatcher.onScreenEnded(
                listenersWhenItEnded = listeners,
                stats = aggregator.screenStats(),
                screen = endedScreen,
                context = endedContext,
            )
            tracer.jankBurstChanged(false)
        }
        choreographerTickMonitor.stop()
        mainThreadWatchdog.stopWatching()
    }

    fun applyConfig(newConfig: FrameHudConfig) {
        metricsThread.renameTo(
            threadName = newConfig.metricsThreadName,
            rebind = windows::windows,
            keepTicking = focusedWindow != null,
        )
        onAggregates { aggregator.updateConfig(newConfig) }
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        diagnosisReadings.setFrozen(frozen)
        aggregator.setFrozen(frozen)
        memoryMonitor.setFrozen(frozen)
        choreographerTickMonitor.setFrozen(frozen)
        thermalMonitor.setFrozen(frozen)
        processMonitor.setFrozen(frozen)
        counterRegistry.setFrozen(frozen)
    }

    @AnyThread
    fun reset() {
        onAggregates {
            sessionId++
            aggregator.reset()
            memoryMonitor.reset()
            processMonitor.reset()
            eventDispatcher.reset()
            counterRegistry.reset()
            flightRecorder.reset()
            reportedFailures.clear()
            diagnosisReadings.reset(JankDiagnosis.HEALTHY)
        }
    }

    @AnyThread
    suspend fun sessionStats(): IntervalStats? = readOnMetricsThread(aggregator::sessionStats)

    @AnyThread
    suspend fun intervals(): List<IntervalReport>? = readOnMetricsThread(aggregator::intervals)

    @AnyThread
    suspend fun incidents(): List<Incident>? = readOnMetricsThread(aggregator::incidents)

    @AnyThread
    suspend fun screens(): List<IntervalReport>? = readOnMetricsThread { aggregator.intervals().worstScreensFirst() }

    @AnyThread
    suspend fun exportStats(): ExportStats? = readOnMetricsThread {
        ExportStats(
            session = aggregator.sessionStats(),
            screen = aggregator.screenStats(),
            intervals = aggregator.intervals(),
            metrics = aggregator.refreshMetricsIgnoringThrottle(),
            memory = memoryMonitor.liveStats,
            thermal = thermalMonitor.liveStats,
            process = processMonitor.liveStats,
            counters = counterRegistry.liveCounters,
            worstFrames = aggregator.worstFrames(),
            incidents = aggregator.incidents(),
            screenName = aggregator.screenName,
            mark = _activeMark.value,
            context = context,
            flightRecording = flightRecorder.recordingFor(config().perfettoTrigger),
        )
    }

    @AnyThread
    suspend fun baselineStats(): BaselineStats? = readOnMetricsThread {
        BaselineStats(
            sessionId = sessionId,
            session = aggregator.sessionStats(),
            environment = BaselineEnvironment.current(),
            intervals = aggregator.intervals(),
        )
    }

    @AnyThread
    private suspend fun <T : Any> readOnMetricsThread(read: () -> T): T? = suspendCancellableCoroutine { waiting ->
        val sampler = metricsThread.started
        val posted = sampler != null &&
            sampler.post {
                if (waiting.isActive) waiting.resumeWith(runCatching(read))
            }
        if (!posted) waiting.resume(null)
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
                    listenersWhenItEnded = listeners,
                    stats = stats,
                    mark = ended,
                    screen = endedScreen,
                    context = endedContext,
                )
            }
        }
    }

    @AnyThread
    fun onInternalFailure(what: String, error: Throwable) {
        onMetricsThread {
            if (!reportedFailures.add(what)) return@onMetricsThread
            eventDispatcher.onInternalFailure(
                what = what,
                error = error,
                screen = aggregator.screenName,
                mark = _activeMark.value,
                context = context,
            )
        }
    }

    @WorkerThread
    private fun onFirstFrame(timeToDisplayMs: Float) {
        eventDispatcher.onFirstFrame(
            timeToDisplayMs = timeToDisplayMs,
            screen = aggregator.screenName,
            context = context,
        )
    }

    @WorkerThread
    private fun onUsableFrame(timeToUsableMs: Float) {
        eventDispatcher.onUsableFrame(
            timeToUsableMs = timeToUsableMs,
            screen = aggregator.screenName,
            context = context,
        )
    }

    private fun unbindFocusedWindow(): Boolean {
        val window = focusedWindow ?: return false
        focusedWindow = null
        windows.remove(window)
        metricsThread.started?.unbind(window)
        return true
    }

    @AnyThread
    private fun onMetricsThread(action: () -> Unit) {
        metricsThread.post(config().metricsThreadName, action)
    }

    @AnyThread
    private fun onAggregates(action: () -> Unit) {
        metricsThread.postOrRunHere(action)
    }

    @WorkerThread
    private fun askForTheTrace() {
        config().perfettoTrigger?.let(flightRecorder::retainTrace)
    }

    @WorkerThread
    private fun sampleMonitors() {
        memoryMonitor.sample()
        thermalMonitor.sample()
        batteryMonitor.sample()
        counterRegistry.sample()
        aggregator.addThermalLevel(thermalMonitor.liveStats.level)
        aggregator.addBattery(batteryMonitor.sample)
    }

    @WorkerThread
    private fun onTick() {
        windows.takeEndedScreens().forEach(aggregator::endWindow)
        aggregator.onTick()
        sampleMonitors()
        if (focusedWindow == null) return
        val metrics = aggregator.liveMetrics
        val memory = memoryMonitor.liveStats
        val thermal = thermalMonitor.liveStats
        val counters = counterRegistry.liveCounters
        val diagnosis = JankDiagnosis.of(
            metrics = metrics,
            memory = memory,
            thermal = thermal,
            choreographerTicksPerSecond = choreographerTickMonitor.liveTicksPerSecond,
        )
        diagnosisReadings.update(diagnosis)
        val trigger = eventDispatcher.onSample(
            diagnosis = diagnosis,
            frozenFrames = metrics.session.frozenFrames,
            thermalLevel = thermal.level,
            screen = aggregator.screenName,
            mark = _activeMark.value,
            context = context,
        )
        if (trigger != null) {
            aggregator.armIncident(
                trigger = trigger,
                readings = IncidentReadings(
                    memory = memory,
                    thermal = thermal,
                    process = processMonitor.liveStats,
                    battery = batteryMonitor.sample,
                    counters = counters,
                    mainThreadBlock = mainThreadWatchdog.latestBlock,
                ),
            )
            askForTheTrace()
        }
        tracer.jankBurstChanged(eventDispatcher.isInBurst)
        tracer.publishCounters(metrics = metrics, memory = memory, counters = counters)
    }

    private fun tickIntervalMs(): Long = maxOf(config().metricsThrottleIntervalMs, MIN_TICK_INTERVAL_MS)

    private companion object {
        const val MIN_TICK_INTERVAL_MS = 250L
        const val PROCESS_SAMPLE_INTERVAL_MS = 5_000L
    }

    class BaselineStats(
        val sessionId: Int,
        val session: IntervalStats,
        val environment: BaselineEnvironment,
        val intervals: List<IntervalReport>,
    )

    class ExportStats(
        val session: IntervalStats,
        val screen: IntervalStats,
        val intervals: List<IntervalReport>,
        val metrics: PerformanceMetrics,
        val memory: MemoryStats,
        val thermal: ThermalStats,
        val process: ProcessStats,
        val counters: List<CounterReading>,
        val worstFrames: List<WorstFrames.Frame>,
        val incidents: List<Incident>,
        val screenName: String?,
        val mark: String?,
        val context: Map<String, String>,
        val flightRecording: FlightRecording?,
    )
}
