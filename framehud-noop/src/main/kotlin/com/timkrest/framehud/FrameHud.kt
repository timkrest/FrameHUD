package com.timkrest.framehud

import android.app.Application
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No-op stand-in for the real `FrameHud`, matching its API exactly. Depend on it in release builds:
 * calls still compile, but nothing is measured, no window is added and no `SYSTEM_ALERT_WINDOW`
 * permission reaches your manifest.
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

    @Volatile
    @get:AnyThread
    @set:MainThread
    public var mark: String? = null

    public fun install(application: Application): Unit = Unit

    public fun show(): Unit = Unit

    public fun hide(): Unit = Unit

    public fun toggle(): Unit = Unit

    @AnyThread
    public fun reset(): Unit = Unit

    @AnyThread
    public fun toggleFreeze(): Unit = Unit

    @WorkerThread
    public fun awaitSessionStats(timeoutMs: Long): SessionStats? = null
}
