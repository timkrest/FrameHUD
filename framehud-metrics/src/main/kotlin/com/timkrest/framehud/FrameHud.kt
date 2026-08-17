package com.timkrest.framehud

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import com.timkrest.framehud.internal.ActivityTracker
import com.timkrest.framehud.internal.LOG_TAG
import com.timkrest.framehud.internal.MetricsEngine
import com.timkrest.framehud.internal.baselineFile
import com.timkrest.framehud.internal.exportAuthority
import com.timkrest.framehud.internal.exportDirectory
import com.timkrest.framehud.internal.readBaseline
import com.timkrest.framehud.internal.sessionSnapshot
import com.timkrest.framehud.internal.writeBaseline
import com.timkrest.framehud.internal.writeTo
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

    private val freezeLock = Any()

    private val baselineLock = Any()

    private var savedSessionId: Int? = null

    @Volatile
    private var application: Application? = null

    private var panel: FrameHudPanel? = null

    @get:AnyThread
    @set:MainThread
    public var config: FrameHudConfig
        get() = currentConfig
        set(value) = applyConfig(value)

    /** Collection continues in the background while frozen. */
    @get:AnyThread
    public val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    /** Updated at most every [FrameHudConfig.metricsThrottleIntervalMs]. */
    @get:AnyThread
    public val metrics: StateFlow<PerformanceMetrics> get() = engine.metrics

    /** Ticks the main thread handled, over the latest completed interval. */
    @get:AnyThread
    public val choreographerTicksPerSecond: StateFlow<Int> get() = engine.choreographerTicksPerSecond

    @get:AnyThread
    public val memoryStats: StateFlow<MemoryStats> get() = engine.memoryStats

    @get:AnyThread
    public val thermalStats: StateFlow<ThermalStats> get() = engine.thermalStats

    /**
     * Names the screen the user sees — a route pattern like `product/{id}`, not `product/12345` —
     * replacing the activity class in stats and events. A new name closes the stats of the previous
     * screen and starts the next; the window stays bound. The name holds until the next assignment,
     * even across activities, so an app that names screens must name every screen it shows. Null
     * returns to naming screens by activity class. Must not be blank.
     */
    @get:AnyThread
    @set:MainThread
    public var screen: String?
        get() = engine.screenOverride
        set(value) {
            checkMainThread()
            require(value == null || value.isNotBlank()) { "A screen name must not be blank" }
            engine.setScreen(value)
        }

    /**
     * Measurement context kept next to the screen and mark — a UI variant, an action, a test
     * scenario. Events carry the pairs set at the moment they fired. Keys and values must not be
     * blank. Changing the context does not close any stats; it only annotates what follows.
     */
    @get:AnyThread
    @set:MainThread
    public var context: Map<String, String>
        get() = engine.context
        set(value) {
            checkMainThread()
            require(value.all { (key, entry) -> key.isNotBlank() && entry.isNotBlank() }) {
                "Context keys and values must not be blank, got $value"
            }
            engine.setContext(value)
        }

    /** Attributes frames to an interaction rather than to the activity in focus. Must not be blank. */
    @get:AnyThread
    @set:MainThread
    public var mark: String?
        get() = engine.activeMark.value
        set(value) {
            checkMainThread()
            require(value == null || value.isNotBlank()) { "A mark name must not be blank" }
            engine.setMark(value)
        }

    /** What reports and the jank gate compare this run against. Null reads `framehud/baseline.json`. */
    @Volatile
    @get:AnyThread
    @set:AnyThread
    public var baselineOverride: Baseline? = null

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
        synchronized(freezeLock) { setFrozen(!_isFrozen.value) }
    }

    /**
     * Returns session metrics since [reset], or null if nothing was collected or the read timed out.
     * The last session remains available after collection stops. This method blocks and is intended
     * for instrumentation tests.
     */
    @WorkerThread
    public fun awaitSessionStats(timeoutMs: Long): SessionStats? {
        checkBlockingAllowed("awaitSessionStats")
        return engine.awaitSessionStats(timeoutMs)
    }

    /**
     * Writes the session since [reset] into `framehud/` under the app's external files directory —
     * `adb pull`-able without root — and returns both files, or null if nothing was collected or
     * the read timed out. Blocks for the read and the write, throws on an I/O failure, and never
     * uploads anything. The app may put its own diagnostic files next to the returned ones.
     */
    @WorkerThread
    public fun exportSession(timeoutMs: Long): SessionExport? {
        checkBlockingAllowed("exportSession")
        val application = application ?: run {
            Log.w(LOG_TAG, "Not installed, nothing to export")
            return null
        }
        val stats = engine.awaitExportStats(timeoutMs) ?: return null
        val snapshot = sessionSnapshot(
            application = application,
            stats = stats,
            isEnabled = currentConfig.enabled,
            isFrozen = _isFrozen.value,
            baseline = loadedBaseline(application),
        )
        return snapshot.writeTo(exportDirectory(application))
    }

    /**
     * Averages the session since [reset] into `framehud/baseline.json` as one run and returns what
     * the file now holds. Null when nothing was collected or the read timed out. Blocks, and throws
     * when the write fails. A second call before the next [reset] changes nothing. Reads and writes
     * the file even when [baselineOverride] is set.
     */
    @WorkerThread
    public fun saveBaseline(timeoutMs: Long): Baseline? {
        checkBlockingAllowed("saveBaseline")
        val application = application ?: run {
            Log.w(LOG_TAG, "Not installed, no baseline to save")
            return null
        }
        val stats = engine.awaitBaselineStats(timeoutMs) ?: return null
        if (stats.session.frames == 0) return null
        val file = baselineFile(application)
        return synchronized(baselineLock) {
            val stored = readBaseline(file)
            if (stats.sessionId == savedSessionId) {
                Log.w(LOG_TAG, "This session is already in the baseline; reset before measuring the next run")
                return@synchronized stored
            }
            val updated = (stored ?: Baseline(stats.environment, emptyMap()))
                .updatedWith(stats.environment, stats.intervals)
            writeBaseline(file, updated)
            savedSessionId = stats.sessionId
            updated
        }
    }

    /**
     * Compares the session since [reset] with [baselineOverride], or with `framehud/baseline.json`
     * when none is set. Null when the device has no baseline yet, when nothing was collected, or
     * when the read timed out. Blocks.
     */
    @WorkerThread
    public fun awaitBaselineComparison(timeoutMs: Long): BaselineComparison? {
        checkBlockingAllowed("awaitBaselineComparison")
        return gateStats(timeoutMs)?.comparison
    }

    @InternalFrameHudApi
    @WorkerThread
    public fun awaitGateStats(timeoutMs: Long): GateStats? {
        checkBlockingAllowed("awaitGateStats")
        return gateStats(timeoutMs)
    }

    @WorkerThread
    private fun gateStats(timeoutMs: Long): GateStats? {
        val stats = engine.awaitBaselineStats(timeoutMs) ?: return null
        val baseline = application?.let(::loadedBaseline)
        return GateStats(
            session = stats.session,
            comparison = baseline?.compare(stats.environment, stats.intervals),
        )
    }

    @InternalFrameHudApi
    public class GateStats(
        public val session: SessionStats,
        public val comparison: BaselineComparison?,
    )

    /**
     * Opens the system share sheet with both report files. Only exports under the app's own
     * storage can be shared — the library's file provider exposes nothing else.
     */
    public fun shareSession(activity: Activity, export: SessionExport) {
        checkMainThread()
        val authority = exportAuthority(activity)
        val uris = arrayListOf(
            FileProvider.getUriForFile(activity, authority, export.json),
            FileProvider.getUriForFile(activity, authority, export.html),
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(EXPORT_MIME_TYPE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "FrameHUD session"))
    }

    @WorkerThread
    private fun loadedBaseline(application: Application): Baseline? =
        baselineOverride ?: readBaseline(baselineFile(application))

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
        setFrozen(false)
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
        synchronized(freezeLock) {
            _isFrozen.value = frozen
            engine.setFrozen(frozen)
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) { "FrameHud must be used from the main thread" }
    }

    @AnyThread
    private fun checkBlockingAllowed(method: String) {
        check(Looper.myLooper() !== Looper.getMainLooper()) {
            "$method blocks; call it from a test or background thread"
        }
    }

    private const val EXPORT_MIME_TYPE = "*/*"
}
