package com.timkrest.framehud.ui

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.StateFlow

/** What the panel reads. The flow references never change, which keeps the panel skippable. */
@Immutable
internal class PanelState(
    val metrics: StateFlow<PerformanceMetrics>,
    val vsyncRate: StateFlow<Int>,
    val memory: StateFlow<MemoryStats>,
    val thermal: StateFlow<ThermalStats>,
    val isCollapsed: StateFlow<Boolean>,
    val isFrozen: StateFlow<Boolean>,
    val canRequestOverlayPermission: Boolean,
    /** Render-thread and GPU rows are dimmed: the host GPU makes them meaningless. */
    val isEmulator: Boolean,
)

@Immutable
internal class PanelActions(
    val toggleCollapsed: () -> Unit,
    val toggleFrozen: () -> Unit,
    val reset: () -> Unit,
    val drag: (dx: Float, dy: Float) -> Unit,
    val requestOverlayPermission: () -> Unit,
)
