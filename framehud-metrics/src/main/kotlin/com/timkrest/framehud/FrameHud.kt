package com.timkrest.framehud

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.util.Log
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import com.timkrest.framehud.internal.ActivityTracker
import com.timkrest.framehud.internal.GuardedFailures
import com.timkrest.framehud.internal.LOG_TAG
import com.timkrest.framehud.internal.MetricsEngine
import com.timkrest.framehud.internal.ScreenStart
import com.timkrest.framehud.internal.baselineFile
import com.timkrest.framehud.internal.exportAuthority
import com.timkrest.framehud.internal.exportDirectory
import com.timkrest.framehud.internal.readBaseline
import com.timkrest.framehud.internal.requireNameStandsApart
import com.timkrest.framehud.internal.sessionSnapshot
import com.timkrest.framehud.internal.writeBaseline
import com.timkrest.framehud.internal.writeTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Frame timings for the activity in focus. Collection needs no panel; the `framehud` artifact adds
 * one that draws in its own window, outside the metrics it reports.
 */
@MainThread
public object FrameHud {

    @Volatile
    private var currentConfig = FrameHudConfig()

    private val engine = MetricsEngine(::currentConfig)

    init {
        GuardedFailures.reportTo(engine::onInternalFailure)
    }

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

    /** Sampled every few seconds while a screen draws. */
    @get:AnyThread
    public val processStats: StateFlow<ProcessStats> get() = engine.processStats

    /** Sampled while a screen draws. */
    @get:AnyThread
    public val counters: StateFlow<List<CounterReading>> get() = engine.counters

    /**
     * Answers the counter already tracked under the name, or starts one. Rejects a name a trace
     * could not tell apart from another: blank, over 110 characters, or carrying a `|` or a control
     * character. Past sixteen names it answers one that discards what it is given.
     */
    @AnyThread
    public fun counter(name: String): FrameHudCounter = engine.counter(name)

    /** What the rolling window says about jank, and what it blames. Healthy while no screen draws. */
    @get:AnyThread
    public val diagnosis: StateFlow<JankDiagnosis> get() = engine.diagnosis

    /**
     * Names the screen the user sees — a route pattern like `product/{id}`, not `product/12345` —
     * replacing the activity class in stats and events. A new name closes the stats of the previous
     * screen and starts the next; the window stays bound. The name holds until the next assignment,
     * even across activities, so an app that names screens must name every screen it shows. Null
     * returns to naming screens by activity class. Rejects a name a trace could not tell apart from
     * another: blank, over 110 characters, or carrying a `|` or a control character.
     */
    @get:AnyThread
    @set:MainThread
    public var screen: String?
        get() = engine.screenOverride
        set(value) {
            checkMainThread()
            value?.let { requireNameStandsApart("A screen name", it) }
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

    /**
     * Reports that the measured screen is showing its data. The measurement ends at the end of the
     * next displayed frame, which [FrameHudEvent.UsableFrame] reports as time to usable. The first
     * screen of a [ComponentActivity] created while FrameHud collects needs no call, because its
     * `FullyDrawnReporter` reports for it and the `ReportDrawnWhen` an app already has for
     * Macrobenchmark covers the launch. Every other screen needs this call: a screen renamed
     * through [screen], an Activity returned to, an Activity without the reporter. Repeating a
     * report for the same screen changes nothing, and a new screen measures again.
     */
    @AnyThread
    public fun reportUsable() {
        engine.reportUsable()
    }

    /**
     * Measures a window FrameHUD cannot find on its own — a dialog, a popup, a presentation on a
     * second display — as a screen of its own named [screen], next to the activity in focus. Its
     * frames count towards the session and towards that screen; the live readings, marks and events
     * stay with the activity. Call once the window is showing, so the refresh rate of its display is
     * known, and [forgetWindow] before it goes away. Measuring the same window twice, or measuring
     * while FrameHUD is hidden, does nothing. [screen] follows the naming rule [FrameHud.screen]
     * states.
     */
    public fun measureWindow(window: Window, screen: String) {
        checkMainThread()
        requireNameStandsApart("A screen name", screen)
        engine.measureWindow(window, screen)
    }

    /** Stops measuring a window given to [measureWindow]. Hiding FrameHUD stops measuring them all. */
    public fun forgetWindow(window: Window) {
        checkMainThread()
        engine.forgetWindow(window)
    }

    /**
     * Attributes frames to an interaction rather than to the activity in focus. Holds until it is
     * cleared or the screen changes, so an interaction that ends without clearing keeps taking the
     * frames after it. Rejects a name a trace could not tell apart from another: blank, over 110
     * characters, or carrying a `|` or a control character.
     */
    @get:AnyThread
    @set:MainThread
    public var mark: String?
        get() = engine.activeMark.value
        set(value) {
            checkMainThread()
            value?.let { requireNameStandsApart("A mark name", it) }
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

    /** Metrics of the session since [reset]. The last session outlives collection. */
    @AnyThread
    public suspend fun sessionStats(): IntervalStats = engine.sessionStats() ?: IntervalStats.EMPTY

    /**
     * What the run measured per interval since [reset]: the session, every screen and every mark,
     * including the ones that drew no frame.
     */
    @AnyThread
    public suspend fun intervals(): List<IntervalReport> = engine.intervals().orEmpty()

    /**
     * What the run measured per screen since [reset], worst first: most frozen frames, then most
     * jank, then the slowest p95. A screen with too few frames to judge its p95 comes last however
     * bad it reads.
     */
    @AnyThread
    public suspend fun screens(): List<IntervalReport> = engine.screens().orEmpty()

    /**
     * What each jank burst and frozen frame since [reset] was drawn around, worst case first.
     * Occurrences that blame the same thing on the same screen under the same mark and context fold
     * into one incident. Only the worst cases are kept, and a window still filling closes with the
     * frames it has.
     */
    @AnyThread
    public suspend fun incidents(): List<Incident> = engine.incidents().orEmpty()

    /**
     * Writes the session since [reset] into `framehud/` and returns both files, or null when
     * nothing was collecting. The directory is the app's external files one, where `adb pull`
     * reaches it without root, or its internal one when the device reports no external storage,
     * which `adb pull` cannot read at all. Throws on an I/O failure, and never uploads anything.
     * The app may put its own diagnostic files next to the returned ones.
     */
    @AnyThread
    public suspend fun exportSession(): SessionExport? {
        val application = installedApplication("export")
        val stats = engine.exportStats() ?: return null
        return withContext(Dispatchers.IO) {
            val snapshot = sessionSnapshot(
                application = application,
                stats = stats,
                isEnabled = currentConfig.enabled,
                isFrozen = _isFrozen.value,
                baseline = loadedBaseline(),
            )
            snapshot.writeTo(exportDirectory(application))
        }
    }

    /**
     * Averages the session since [reset] into `framehud/baseline.json` as one run and returns what
     * the file now holds. Null when the session recorded no frame. Throws when the write fails. A
     * second call before the next [reset] changes nothing. Reads and writes the file even when
     * [baselineOverride] is set.
     */
    @AnyThread
    public suspend fun saveBaseline(): Baseline? {
        val application = installedApplication("save a baseline")
        val stats = engine.baselineStats()?.takeIf { it.session.frames > 0 } ?: return null
        val file = baselineFile(application)
        return withContext(Dispatchers.IO) {
            synchronized(baselineLock) {
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
    }

    /**
     * Compares the session since [reset] with [baselineOverride], or with `framehud/baseline.json`
     * when none is set.
     */
    @AnyThread
    public suspend fun compareWithBaseline(): BaselineComparison = gateStats().comparison

    @InternalFrameHudApi
    @AnyThread
    public suspend fun gateStats(): GateStats {
        val baseline = withContext(Dispatchers.IO) { loadedBaseline() }
        val stats = engine.baselineStats()
        return GateStats(
            session = stats?.session ?: IntervalStats.EMPTY,
            comparison = when {
                baseline == null -> BaselineComparison.NoBaseline
                stats == null -> BaselineComparison.Compared(emptyList())
                else -> baseline.compare(stats.environment, stats.intervals)
            },
            isCollecting = stats != null,
        )
    }

    @InternalFrameHudApi
    public class GateStats(
        public val session: IntervalStats,
        public val comparison: BaselineComparison,
        public val isCollecting: Boolean,
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
    private fun loadedBaseline(): Baseline? =
        baselineOverride ?: application?.let { readBaseline(baselineFile(it)) }

    @AnyThread
    private fun installedApplication(action: String): Application =
        checkNotNull(application) { "FrameHud is not installed, cannot $action" }

    private fun applyConfig(newConfig: FrameHudConfig) {
        checkMainThread()
        if (currentConfig == newConfig) return
        currentConfig = newConfig

        if (!newConfig.enabled) stopCollecting()
        engine.applyConfig(newConfig)
        if (newConfig.enabled) startCollecting()
        panel?.onConfigChanged()
    }

    private fun startCollecting(start: ScreenStart? = null) {
        val application = application ?: return
        engine.start(application)
        val activity = activityTracker.focusedActivity ?: return
        engine.bindWindow(window = activity.window, screen = activity.javaClass.simpleName, start = start)
        if (start != null) reportUsableWhenFullyDrawn(activity, start)
    }

    private fun reportUsableWhenFullyDrawn(activity: Activity, start: ScreenStart) {
        if (activity !is ComponentActivity) return
        activity.fullyDrawnReporter.addOnReportDrawnListener { engine.reportUsable(start) }
    }

    private fun stopCollecting() {
        engine.stop()
        setFrozen(false)
    }

    private fun onActivityFocused(activity: Activity, previous: Activity?) {
        if (previous !== activity) engine.unbindWindow()
        val start = activityTracker.takeScreenStart(activity)
        if (currentConfig.enabled) startCollecting(start)
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

    private const val EXPORT_MIME_TYPE = "*/*"
}
