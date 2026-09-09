package com.timkrest.framehud.ui

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal enum class PanelDetail {
    FULL,
    FRAMES,
    MINI,
    ;

    fun next(): PanelDetail = entries[(ordinal + 1) % entries.size]
}

internal enum class PanelView {
    METRICS,
    SCREENS,
    ;

    fun next(): PanelView = entries[(ordinal + 1) % entries.size]
}

@Immutable
internal class PanelState(
    val metrics: StateFlow<PerformanceMetrics>,
    val choreographerTicksPerSecond: StateFlow<Int>,
    val memory: StateFlow<MemoryStats>,
    val thermal: StateFlow<ThermalStats>,
    val process: StateFlow<ProcessStats>,
    val counters: StateFlow<List<CounterReading>>,
    val activeMark: StateFlow<String?>,
    val view: StateFlow<PanelView>,
    val screens: Flow<List<IntervalReport>>,
    val detail: StateFlow<PanelDetail>,
    val isFrozen: StateFlow<Boolean>,
    val canRequestOverlayPermission: Boolean,
    val isEmulator: Boolean,
)

@Immutable
internal class PanelActions(
    val showNextDetail: () -> Unit,
    val toggleView: () -> Unit,
    val toggleFrozen: () -> Unit,
    val reset: () -> Unit,
    val drag: PanelDrag,
    val requestOverlayPermission: () -> Unit,
)
