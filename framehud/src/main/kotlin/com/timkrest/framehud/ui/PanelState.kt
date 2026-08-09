package com.timkrest.framehud.ui

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.StateFlow

@Immutable
internal class PanelState(
    val metrics: StateFlow<PerformanceMetrics>,
    val choreographerTicksPerSecond: StateFlow<Int>,
    val memory: StateFlow<MemoryStats>,
    val thermal: StateFlow<ThermalStats>,
    val activeMark: StateFlow<String?>,
    val isCollapsed: StateFlow<Boolean>,
    val isFrozen: StateFlow<Boolean>,
    val canRequestOverlayPermission: Boolean,
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
