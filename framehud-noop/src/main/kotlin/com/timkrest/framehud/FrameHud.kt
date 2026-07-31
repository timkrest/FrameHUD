package com.timkrest.framehud

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No-op stand-in for the real `FrameHud`, matching its API exactly. Depend on it in release builds:
 * calls still compile, but nothing is measured, no window is added and no `SYSTEM_ALERT_WINDOW`
 * permission reaches your manifest.
 */
public object FrameHud {

    public var config: FrameHudConfig = FrameHudConfig()

    public val isFrozen: StateFlow<Boolean> = MutableStateFlow(false)

    public val metrics: StateFlow<PerformanceMetrics> = MutableStateFlow(PerformanceMetrics.EMPTY)

    public val vsyncRate: StateFlow<Int> = MutableStateFlow(0)

    public val memoryStats: StateFlow<MemoryStats> = MutableStateFlow(MemoryStats.EMPTY)

    public val thermalStats: StateFlow<ThermalStats> = MutableStateFlow(ThermalStats.EMPTY)

    public fun install(application: Application): Unit = Unit

    public fun show(): Unit = Unit

    public fun hide(): Unit = Unit

    public fun toggle(): Unit = Unit

    public fun reset(): Unit = Unit

    public fun toggleFreeze(): Unit = Unit

    public fun awaitSessionStats(timeoutMs: Long): SessionStats? = null
}
