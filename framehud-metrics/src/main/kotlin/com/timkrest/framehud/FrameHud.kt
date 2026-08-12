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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Frame timings for the activity in focus. Collection needs no panel; the `framehud` artifact adds
 * one that draws in its own window, outside the metrics it reports.
 */
@MainThread
public object FrameHud {

    @Volatile
    private var currentConfig = FrameHudConfig()

    private val engine = MetricsEngine(::currentConfig)

    private val activityTracker = ActivityTracker(onFocused = ::onActivityFocused, onLost = ::onActivityLost)

    private val _isFrozen = MutableStateFlow(false)

    private var application: Application? = null

    private var panel: FrameHudPanel? = null

    @get:AnyThread
    @set:MainThread
    public var config: FrameHudConfig
        get() = currentConfig
        set(value) = applyConfig(value)

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

    @InternalFrameHudApi
    @get:AnyThread
    public val activeMark: StateFlow<String?> get() = engine.activeMark

    @InternalFrameHudApi
    public val focusedActivity: Activity? get() = activityTracker.focusedActivity

    /**
     * Installs FrameHud when automatic installation is disabled. Call from [Application.onCreate]
     * before the first activity is resumed. Repeated calls are ignored.
     */
    public fun install(application: Application) {
        checkMainThread()
        if (this.application != null) {
            Log.w(LOG_TAG, "Already installed, ignoring install()")
            return
        }
        this.application = application
        application.registerActivityLifecycleCallbacks(activityTracker)
    }

    @InternalFrameHudApi
    public fun attachPanel(panel: FrameHudPanel) {
        checkMainThread()
        if (this.panel != null) {
            Log.w(LOG_TAG, "A panel is already attached, ignoring attachPanel()")
            return
        }
        this.panel = panel
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

    /** Clears rolling and session metrics, including peaks, and unfreezes the readings. */
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
        if (currentConfig == newConfig) return
        currentConfig = newConfig

        if (!newConfig.enabled) stopCollecting()
        engine.applyConfig(newConfig)
        if (newConfig.enabled) startCollecting()
        panel?.onConfigChanged()
    }

    private fun startCollecting() {
        val application = application ?: return
        engine.start(application)
        val activity = activityTracker.focusedActivity ?: return
        engine.bindWindow(
            window = activity.window,
            screen = activity.javaClass.simpleName,
            creation = activityTracker.takeScreenCreation(activity),
        )
    }

    private fun stopCollecting() {
        engine.stop()
        _isFrozen.value = false
    }

    private fun onActivityFocused(activity: Activity, previous: Activity?) {
        if (previous !== activity) engine.unbindWindow()
        if (currentConfig.enabled) startCollecting()
        panel?.onScreenFocused()
    }

    private fun onActivityLost() {
        engine.unbindWindow()
        panel?.onScreenLost()
    }

    @AnyThread
    private fun setFrozen(frozen: Boolean) {
        _isFrozen.value = frozen
        engine.setFrozen(frozen)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "FrameHud must be used from the main thread" }
    }
}
