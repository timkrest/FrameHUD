package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ThermalStats

@Composable
internal fun PanelExpandedContent(
    metrics: PerformanceMetrics,
    vsyncRate: Int,
    memory: MemoryStats,
    thermal: ThermalStats,
    isFrozen: Boolean,
    canRequestOverlayPermission: Boolean,
    isEmulator: Boolean,
    actions: PanelActions,
    modifier: Modifier = Modifier,
) {
    val lines = remember(metrics, memory, thermal, isEmulator) {
        buildPanelLines(metrics = metrics, memory = memory, thermal = thermal, isEmulator = isEmulator)
    }
    Column(modifier = modifier) {
        PanelHeader(
            metrics = metrics,
            vsyncRate = vsyncRate,
            isFrozen = isFrozen,
            canRequestOverlayPermission = canRequestOverlayPermission,
            isEmulator = isEmulator,
            actions = actions,
        )

        FrameSparkline(
            history = metrics.window.history,
            frameBudgetMs = metrics.display.frameBudgetMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight),
        )

        PanelTextBlock(lines = lines, modifier = Modifier.fillMaxWidth())
    }
}
