package com.timkrest.framehud

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.internal.ActivityTracker
import com.timkrest.framehud.internal.LOG_TAG
import com.timkrest.framehud.internal.MetricsEngine
import com.timkrest.framehud.internal.PanelHost
import com.timkrest.framehud.internal.isEmulatorDevice
import com.timkrest.framehud.internal.openOverlayPermissionSettings
import com.timkrest.framehud.ui.PanelActions
import com.timkrest.framehud.ui.PanelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Draggable debug panel for frame timings. It follows the focused activity and renders in a
 * separate window, outside the metrics it collects.
 */
@MainThread
public object FrameHud {

    @Volatile
    private var currentConfig = FrameHudConfig()

    @get:AnyThread
    @set:MainThread
    public var config: FrameHudConfig
        get() = currentConfig
        set(value) = applyConfig(value)

    private val engine = MetricsEngine(::currentConfig)

    private val _isFrozen = MutableStateFlow(false)

    /** Whether displayed readings are frozen. Collection continues in the background. */
    @get:AnyThread
    public val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    /** Updated at most every [FrameHudConfig.metricsThrottleIntervalMs]. */
    @get:AnyThread
    public val metrics: StateFlow<PerformanceMetrics> get() = engine.metrics

    /** Choreographer ticks handled by the main thread per second, over the latest completed interval. */
    @get:AnyThread
    public val choreographerTicksPerSecond: StateFlow<Int> get() = engine.choreographerTicksPerSecond

    @get:AnyThread
    public val memoryStats: StateFlow<MemoryStats> get() = engine.memoryStats

    @get:AnyThread
    public val thermalStats: StateFlow<ThermalStats> get() = engine.thermalStats

    /** Attributes frames to an interaction rather than to the activity in focus. */
    @get:AnyThread
    @set:MainThread
    public var mark: String?
        get() = engine.activeMark.value
        set(value) {
            checkMainThread()
            engine.setMark(value)
        }

    private val isPanelCollapsed = MutableStateFlow(false)

    private var panelHost: PanelHost? = null

    private val activityTracker = ActivityTracker(onFocused = ::onActivityFocused, onLost = ::onActivityLost)

    /**
     * Installs FrameHud when automatic installation is disabled. Call from [Application.onCreate]
     * before the first activity is resumed. Repeated calls are ignored.
     */
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
        application.registerActivityLifecycleCallbacks(activityTracker)
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

    /** Clears rolling and session metrics, including peaks, and unfreezes the panel. */
    @AnyThread
    public fun reset() {
        setFrozen(false)
        engine.reset()
    }

    @AnyThread
    public fun toggleFreeze() {
        setFrozen(!_isFrozen.value)
    }

    /**
     * Returns session metrics since [reset], or null if nothing was collected or the read timed out.
     * The last session remains available after collection stops. This method blocks and is intended
     * for instrumentation tests.
     */
    @WorkerThread
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
        if (!host.show(activityTracker.focusedActivity)) return
        engine.start(host.application)
        bindActivityMetrics()
    }

    private fun stopPanel() {
        engine.stop()
        panelHost?.dismiss()
        _isFrozen.value = false
    }

    private fun onActivityFocused(activity: Activity, previous: Activity?) {
        if (previous !== activity) engine.unbindWindow()
        if (currentConfig.enabled) {
            panelHost?.dismissIfWindowModeChanged()
            startPanel()
        }
        bindActivityMetrics()
        panelHost?.makeVisible()
    }

    private fun onActivityLost() {
        engine.unbindWindow()
        val host = panelHost ?: return
        if (host.isAppWindow) stopPanel() else host.hideAfterActivitySwap()
    }

    private fun bindActivityMetrics() {
        val activity = activityTracker.focusedActivity ?: return
        engine.bindWindow(
            window = activity.window,
            screen = activity.javaClass.simpleName,
            creation = activityTracker.takeScreenCreation(activity),
        )
    }

    @AnyThread
    private fun setFrozen(frozen: Boolean) {
        _isFrozen.value = frozen
        engine.setFrozen(frozen)
    }

    private fun panelState(canRequestOverlayPermission: Boolean) = PanelState(
        metrics = metrics,
        choreographerTicksPerSecond = choreographerTicksPerSecond,
        memory = memoryStats,
        thermal = thermalStats,
        activeMark = engine.activeMark,
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
        activityTracker.focusedActivity?.let(::openOverlayPermissionSettings)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "FrameHud must be used from the main thread" }
    }
}
