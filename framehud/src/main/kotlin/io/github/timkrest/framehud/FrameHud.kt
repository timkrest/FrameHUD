package io.github.timkrest.framehud

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.util.Log
import io.github.timkrest.framehud.internal.LOG_TAG
import io.github.timkrest.framehud.internal.MetricsEngine
import io.github.timkrest.framehud.internal.PanelHost
import io.github.timkrest.framehud.internal.isEmulatorDevice
import io.github.timkrest.framehud.internal.openOverlayPermissionSettings
import io.github.timkrest.framehud.ui.PanelActions
import io.github.timkrest.framehud.ui.PanelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

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

    private val engine = MetricsEngine(::currentConfig)

    private val _isFrozen = MutableStateFlow(false)

    /** Readings held still for inspection. Collection continues while frozen. */
    public val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    /** Updated at most every [FrameHudConfig.metricsThrottleIntervalMs]. */
    public val metrics: StateFlow<PerformanceMetrics> get() = engine.metrics

    /** Choreographer ticks the main thread served during the last second. */
    public val vsyncRate: StateFlow<Int> get() = engine.vsyncRate

    public val memoryStats: StateFlow<MemoryStats> get() = engine.memoryStats

    public val thermalStats: StateFlow<ThermalStats> get() = engine.thermalStats

    private val isPanelCollapsed = MutableStateFlow(false)

    private var panelHost: PanelHost? = null

    private var boundActivityRef: WeakReference<Activity>? = null

    private val boundActivity: Activity? get() = boundActivityRef?.get()

    /** Called by the installer; only needed by hand when that installer is removed. Idempotent. */
    public fun install(application: Application) {
        checkMainThread()
        if (panelHost != null) {
            Log.w(LOG_TAG, "Already installed, ignoring install()")
            return
        }
        panelHost = PanelHost(
            application = application,
            config = ::currentConfig,
            panelState = ::panelState,
            panelActions = ::panelActions,
        )
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
        engine.reset()
    }

    public fun toggleFreeze() {
        setFrozen(!_isFrozen.value)
    }

    /**
     * Session aggregates since the last [reset], or null when nothing was collected or the read
     * timed out. Still answers once the panel has stopped, so a test can assert after its activity
     * is gone. Blocks the caller, so it is for instrumentation tests rather than production code.
     */
    public fun awaitSessionStats(timeoutMs: Long): SessionStats? {
        check(Looper.myLooper() !== Looper.getMainLooper()) {
            "awaitSessionStats blocks; call it from a test or background thread"
        }
        return engine.awaitSessionStats(timeoutMs)
    }

    private fun applyConfig(newConfig: FrameHudConfig) {
        checkMainThread()
        val previous = currentConfig
        if (previous == newConfig) return
        val needsWindowSwap = previous.overlayMode != newConfig.overlayMode
        currentConfig = newConfig

        if (!newConfig.enabled || needsWindowSwap) stopPanel()
        engine.applyConfig(newConfig)
        if (newConfig.enabled) startPanel()
    }

    private fun startPanel() {
        val host = panelHost ?: return
        if (host.isShowing) return
        if (!host.show(boundActivity)) return
        engine.start(host.application)
        bindActivityMetrics()
    }

    private fun stopPanel() {
        engine.stop()
        panelHost?.dismiss()
        _isFrozen.value = false
    }

    private fun attachToActivity(activity: Activity) {
        if (boundActivity !== activity) {
            engine.unbindWindow()
            boundActivityRef = WeakReference(activity)
        }
        if (currentConfig.enabled) {
            panelHost?.dismissIfWindowModeChanged()
            startPanel()
        }
        bindActivityMetrics()
        panelHost?.makeVisible()
    }

    private fun detachFromActivity(activity: Activity) {
        if (boundActivity !== activity) return
        engine.unbindWindow()
        boundActivityRef = null
        val host = panelHost ?: return
        if (host.isAppWindow) stopPanel() else host.hideAfterActivitySwap()
    }

    private fun bindActivityMetrics() {
        val activity = boundActivity ?: return
        engine.bindWindow(window = activity.window, screen = activity.javaClass.simpleName)
    }

    private fun setFrozen(frozen: Boolean) {
        _isFrozen.value = frozen
        engine.setFrozen(frozen)
    }

    private fun panelState(canRequestOverlayPermission: Boolean) = PanelState(
        metrics = metrics,
        vsyncRate = vsyncRate,
        memory = memoryStats,
        thermal = thermalStats,
        isCollapsed = isPanelCollapsed,
        isFrozen = isFrozen,
        canRequestOverlayPermission = canRequestOverlayPermission,
        isEmulator = isEmulatorDevice,
    )

    private fun panelActions(onDrag: (dx: Float, dy: Float) -> Unit) = PanelActions(
        toggleCollapsed = { isPanelCollapsed.value = !isPanelCollapsed.value },
        toggleFrozen = ::toggleFreeze,
        reset = ::reset,
        drag = onDrag,
        requestOverlayPermission = ::requestOverlayPermission,
    )

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
}
