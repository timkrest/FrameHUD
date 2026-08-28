package com.timkrest.framehud

import android.app.Activity
import android.app.Application
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No-op replacement for `FrameHud`. It keeps release calls compiling without collecting metrics,
 * adding a window, or merging the overlay permission.
 */
@MainThread
public object FrameHud {

    @Volatile
    @get:AnyThread
    @set:MainThread
    public var config: FrameHudConfig = FrameHudConfig()

    @get:AnyThread
    public val isFrozen: StateFlow<Boolean> = MutableStateFlow(false)

    @get:AnyThread
    public val metrics: StateFlow<PerformanceMetrics> = MutableStateFlow(PerformanceMetrics.EMPTY)

    @get:AnyThread
    public val choreographerTicksPerSecond: StateFlow<Int> = MutableStateFlow(0)

    @get:AnyThread
    public val memoryStats: StateFlow<MemoryStats> = MutableStateFlow(MemoryStats.EMPTY)

    @get:AnyThread
    public val thermalStats: StateFlow<ThermalStats> = MutableStateFlow(ThermalStats.EMPTY)

    @get:AnyThread
    public val processStats: StateFlow<ProcessStats> = MutableStateFlow(ProcessStats.EMPTY)

    @get:AnyThread
    public val counters: StateFlow<List<CounterReading>> = MutableStateFlow(emptyList())

    @AnyThread
    public fun counter(name: String): FrameHudCounter = NoopCounter

    @get:AnyThread
    public val diagnosis: StateFlow<JankDiagnosis> = MutableStateFlow(JankDiagnosis.HEALTHY)

    @Volatile
    @get:AnyThread
    @set:MainThread
    public var screen: String? = null

    @Volatile
    @get:AnyThread
    @set:MainThread
    public var context: Map<String, String> = emptyMap()

    @Volatile
    @get:AnyThread
    @set:MainThread
    public var mark: String? = null

    @Volatile
    @get:AnyThread
    @set:AnyThread
    public var baselineOverride: Baseline? = null

    public fun install(application: Application): Unit = Unit

    public fun measureWindow(window: Window, screen: String): Unit = Unit

    public fun forgetWindow(window: Window): Unit = Unit

    public fun show(): Unit = Unit

    public fun hide(): Unit = Unit

    public fun toggle(): Unit = Unit

    @AnyThread
    public fun reportUsable(): Unit = Unit

    @AnyThread
    public fun retainTrace(): Unit = Unit

    @AnyThread
    public fun reset(): Unit = Unit

    @AnyThread
    public fun toggleFreeze(): Unit = Unit

    @AnyThread
    public suspend fun sessionStats(): IntervalStats = IntervalStats.EMPTY

    @AnyThread
    public suspend fun intervals(): List<IntervalReport> = emptyList()

    @AnyThread
    public suspend fun screens(): List<IntervalReport> = emptyList()

    @AnyThread
    public suspend fun incidents(): List<Incident> = emptyList()

    @AnyThread
    public suspend fun exportSession(): SessionExport? = null

    @AnyThread
    public suspend fun saveBaseline(): Baseline? = null

    @AnyThread
    public suspend fun compareWithBaseline(): BaselineComparison = BaselineComparison.NoBaseline

    @AnyThread
    public suspend fun history(): List<RecordedRun> = emptyList()

    public fun shareSession(activity: Activity, export: SessionExport): Unit = Unit
}

private object NoopCounter : FrameHudCounter {
    override fun set(value: Int) = Unit
    override fun add(delta: Int) = Unit
}
