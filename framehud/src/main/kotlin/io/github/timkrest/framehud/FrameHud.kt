package io.github.timkrest.framehud

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.remember
import io.github.timkrest.framehud.internal.EventDispatcher
import io.github.timkrest.framehud.internal.FrameMetricsCollector
import io.github.timkrest.framehud.internal.LOG_TAG
import io.github.timkrest.framehud.internal.MemoryStatsMonitor
import io.github.timkrest.framehud.internal.MetricsSampler
import io.github.timkrest.framehud.internal.PanelPosition
import io.github.timkrest.framehud.internal.PanelWindow
import io.github.timkrest.framehud.internal.PanelWindowMode
import io.github.timkrest.framehud.internal.ThermalMonitor
import io.github.timkrest.framehud.internal.VsyncRateMonitor
import io.github.timkrest.framehud.internal.canDrawOverlays
import io.github.timkrest.framehud.internal.openOverlayPermissionSettings
import io.github.timkrest.framehud.internal.systemOverlayContext
import io.github.timkrest.framehud.ui.FrameHudPanel
import io.github.timkrest.framehud.ui.PanelActions
import io.github.timkrest.framehud.ui.PanelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A draggable debug panel showing frame timings on top of your app. Installs itself in the main
 * process and follows the resumed activity; nothing to call.
 *
 * The panel renders in its own window, so it stays out of the metrics of the window it measures.
 *
 * Call from the main thread, except for the readings, [reset] and [toggleFreeze], which are safe
 * from anywhere, and [awaitSessionStats], which blocks and must not run on the main thread.
 */
public object FrameHud {

    @Volatile
    private var currentConfig = FrameHudConfig()

    public var config: FrameHudConfig
        get() = currentConfig
        set(value) = applyConfig(value)

    private val collector = FrameMetricsCollector(currentConfig)
    private val vsyncMonitor = VsyncRateMonitor()
    private val memoryMonitor = MemoryStatsMonitor()
    private val thermalMonitor = ThermalMonitor()
    private val eventDispatcher = EventDispatcher()

    private val _isFrozen = MutableStateFlow(false)

    /** Readings held still for inspection. Collection continues while frozen. */
    public val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    /** Updated at most every [FrameHudConfig.metricsThrottleIntervalMs]. */
    public val metrics: StateFlow<PerformanceMetrics> get() = collector.metrics

    /** Choreographer ticks the main thread served during the last second. */
    public val vsyncRate: StateFlow<Int> get() = vsyncMonitor.ratePerSecond

    public val memoryStats: StateFlow<MemoryStats> get() = memoryMonitor.stats

    public val thermalStats: StateFlow<ThermalStats> get() = thermalMonitor.stats

    private val isPanelCollapsed = MutableStateFlow(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hidePanelInBackground = Runnable { panel?.setVisible(false) }

    private val panelState = PanelState(
        metrics = metrics,
        vsyncRate = vsyncRate,
        memory = memoryStats,
        thermal = thermalStats,
        isCollapsed = isPanelCollapsed,
        isFrozen = isFrozen,
    )

    private var application: Application? = null
    private var boundActivityRef: WeakReference<Activity>? = null

    /** Read from the metrics thread to label events, and outlives the activity it names. */
    @Volatile
    private var boundScreenName: String? = null

    /** In [PanelWindowMode.APP] the window holds the activity, and is torn down when it pauses. */
    @SuppressLint("StaticFieldLeak")
    private var panel: PanelWindow? = null

    /** Reached from test threads through [reset] and [awaitSessionStats]. */
    @Volatile
    private var sampler: MetricsSampler? = null
    private var panelPosition: PanelPosition? = null
    private var hasLoggedAppWindowFallback = false

    private val boundActivity: Activity? get() = boundActivityRef?.get()

    /** Called by the installer; only needed by hand when that installer is removed. Idempotent. */
    public fun install(application: Application) {
        checkMainThread()
        if (this.application != null) {
            Log.w(LOG_TAG, "Already installed, ignoring install()")
            return
        }
        this.application = application
        application.registerActivityLifecycleCallbacks(LifecycleCallbacks)
    }

    public fun show() {
        config = currentConfig.copy(enabled = true)
    }

    public fun hide() {
        config = currentConfig.copy(enabled = false)
    }

    public fun toggle() {
        config = currentConfig.copy(enabled = !currentConfig.enabled)
    }

    /** Clears the window, the session aggregates and the peaks. */
    public fun reset() {
        setFrozen(false)
        onMetricsThread {
            collector.reset()
            memoryMonitor.reset()
            eventDispatcher.reset()
        }
    }

    public fun toggleFreeze() {
        setFrozen(!_isFrozen.value)
    }

    /**
     * Session aggregates read on the metrics thread, or null when nothing is collecting or the read
     * timed out. Blocks the caller, so it is for instrumentation tests rather than production code.
     */
    public fun awaitSessionStats(timeoutMs: Long): SessionStats? {
        check(Looper.myLooper() !== Looper.getMainLooper()) {
            "awaitSessionStats blocks; call it from a test or background thread"
        }
        val stats = AtomicReference<SessionStats>()
        val done = CountDownLatch(1)
        val posted = sampler?.post {
            stats.set(collector.sessionStats())
            done.countDown()
        }
        if (posted != true) return null
        return if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) stats.get() else null
    }

    private fun applyConfig(newConfig: FrameHudConfig) {
        checkMainThread()
        val previous = currentConfig
        if (previous == newConfig) return
        currentConfig = newConfig

        if (!newConfig.enabled || restartsPanel(previous, newConfig)) stopPanel()
        onMetricsThread { collector.updateConfig(newConfig) }
        if (newConfig.enabled) startPanel()
    }

    private fun restartsPanel(previous: FrameHudConfig, next: FrameHudConfig): Boolean =
        previous.overlayMode != next.overlayMode || previous.metricsThreadName != next.metricsThreadName

    /** Runs on the metrics thread, or right here when no sampler is running. */
    private fun onMetricsThread(action: () -> Unit) {
        if (sampler?.post(action) != true) action()
    }

    private fun setFrozen(frozen: Boolean) {
        _isFrozen.value = frozen
        collector.setFrozen(frozen)
        memoryMonitor.setFrozen(frozen)
        vsyncMonitor.setFrozen(frozen)
        thermalMonitor.setFrozen(frozen)
    }

    private fun sampleMonitors() {
        memoryMonitor.sample()
        thermalMonitor.sample()
    }

    private fun onTick() {
        collector.onTick()
        sampleMonitors()
        eventDispatcher.onSample(
            listeners = currentConfig.eventListeners,
            metrics = collector.metrics.value,
            memory = memoryMonitor.stats.value,
            thermal = thermalMonitor.stats.value,
            vsyncRate = vsyncMonitor.ratePerSecond.value,
            screen = boundScreenName,
        )
    }

    private fun attachToActivity(activity: Activity) {
        mainHandler.removeCallbacks(hidePanelInBackground)
        if (boundActivity !== activity) {
            unbindMetrics()
            boundActivityRef = WeakReference(activity)
            boundScreenName = activity.javaClass.simpleName
        }
        if (currentConfig.enabled) {
            stopPanelIfWindowModeChanged()
            startPanel()
        }
        bindMetrics(activity)
        panel?.setVisible(true)
    }

    private fun detachFromActivity(activity: Activity) {
        if (boundActivity !== activity) return
        unbindMetrics()
        boundActivityRef = null
        if (panel?.mode == PanelWindowMode.APP) {
            stopPanel()
        } else {
            mainHandler.postDelayed(hidePanelInBackground, BACKGROUND_HIDE_DELAY_MS)
        }
    }

    private fun startPanel() {
        if (panel != null) return
        val application = application ?: return
        val mode = resolveWindowMode(application)
        val windowContext = when (mode) {
            PanelWindowMode.SYSTEM -> systemOverlayContext(application)
            PanelWindowMode.APP -> boundActivity ?: return
        }
        if (mode == PanelWindowMode.APP && canOfferOverlayPermission()) logAppWindowFallbackOnce()

        thermalMonitor.bind(application)
        sampler = MetricsSampler(
            threadName = currentConfig.metricsThreadName,
            listener = collector,
            tickIntervalMs = ::tickIntervalMs,
            onTick = ::onTick,
        ).apply {
            post(::sampleMonitors) // the first tick is a whole throttle interval away
            start()
        }
        panel = createPanel(context = windowContext, mode = mode).apply { show() }
        boundActivity?.let(::bindMetrics)
    }

    private fun stopPanel() {
        mainHandler.removeCallbacks(hidePanelInBackground)
        unbindMetrics()
        thermalMonitor.unbind()
        sampler?.quit()
        sampler = null
        panel?.let { window ->
            panelPosition = window.position
            window.dismiss()
        }
        panel = null
        setFrozen(false)
    }

    /** Picks up an overlay permission granted while the panel was already running. */
    private fun stopPanelIfWindowModeChanged() {
        val current = panel ?: return
        val application = application ?: return
        if (current.mode != resolveWindowMode(application)) stopPanel()
    }

    private fun bindMetrics(activity: Activity) {
        val sampler = sampler ?: return
        if (!sampler.bind(activity.window)) return
        sampler.post(collector::startCollecting)
        vsyncMonitor.start()
    }

    private fun unbindMetrics() {
        val sampler = sampler ?: return
        if (!sampler.unbind()) return
        sampler.post {
            collector.stopCollecting()
            eventDispatcher.onScreenEnded(
                listeners = currentConfig.eventListeners,
                stats = collector.screenStats(),
                screen = boundScreenName,
            )
        }
        vsyncMonitor.stop()
    }

    private fun createPanel(context: Context, mode: PanelWindowMode): PanelWindow = PanelWindow(
        context = context,
        mode = mode,
        startPosition = panelPosition,
    ) { onDrag ->
        FrameHudPanel(
            state = panelState,
            actions = remember(mode, onDrag) { panelActions(mode = mode, onDrag = onDrag) },
        )
    }

    private fun panelActions(mode: PanelWindowMode, onDrag: (dx: Float, dy: Float) -> Unit) = PanelActions(
        toggleCollapsed = { isPanelCollapsed.value = !isPanelCollapsed.value },
        toggleFrozen = ::toggleFreeze,
        reset = ::reset,
        drag = onDrag,
        requestOverlayPermission = ::requestOverlayPermission
            .takeIf { mode == PanelWindowMode.APP && canOfferOverlayPermission() },
    )

    private fun tickIntervalMs(): Long = maxOf(currentConfig.metricsThrottleIntervalMs, MIN_TICK_INTERVAL_MS)

    private fun resolveWindowMode(context: Context): PanelWindowMode =
        if (currentConfig.overlayMode == OverlayMode.PREFER_SYSTEM && canDrawOverlays(context)) {
            PanelWindowMode.SYSTEM
        } else {
            PanelWindowMode.APP
        }

    private fun canOfferOverlayPermission(): Boolean =
        currentConfig.overlayMode == OverlayMode.PREFER_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private fun logAppWindowFallbackOnce() {
        if (hasLoggedAppWindowFallback) return
        hasLoggedAppWindowFallback = true
        Log.i(LOG_TAG, "No SYSTEM_ALERT_WINDOW permission: the panel stays inside the app window")
    }

    private fun requestOverlayPermission() {
        boundActivity?.let(::openOverlayPermissionSettings)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "FrameHud must be used from the main thread" }
    }

    private object LifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = attachToActivity(activity)

        override fun onActivityPaused(activity: Activity) = detachFromActivity(activity)

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private const val MIN_TICK_INTERVAL_MS = 250L

    /** Long enough to cover an activity swap, so the panel does not blink between screens. */
    private const val BACKGROUND_HIDE_DELAY_MS = 300L
}
