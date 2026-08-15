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
    memory: MemoryStats,
    thermal: ThermalStats,
    isEmulator: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = remember(metrics, memory, thermal, isEmulator) {
        buildPanelLines(metrics = metrics, memory = memory, thermal = thermal, isEmulator = isEmulator)
    }
    Column(modifier = modifier) {
        FrameSparkline(
            history = metrics.window.history,
            display = metrics.display,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight),
        )

        PanelTextBlock(lines = lines, modifier = Modifier.fillMaxWidth())
    }
}
