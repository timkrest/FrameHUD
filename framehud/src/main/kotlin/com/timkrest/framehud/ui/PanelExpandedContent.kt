package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats

@Composable
internal fun PanelExpandedContent(
    metrics: PerformanceMetrics,
    memory: MemoryStats,
    thermal: ThermalStats,
    process: ProcessStats,
    counters: List<CounterReading>,
    isEmulator: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = remember(metrics, memory, thermal, process, counters, isEmulator) {
        buildPanelLines(
            metrics = metrics,
            memory = memory,
            thermal = thermal,
            process = process,
            counters = counters,
            isEmulator = isEmulator,
        )
    }
    Column(modifier = modifier) {
        FrameSparkline(
            window = metrics.window,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight),
        )

        PanelTextBlock(lines = lines, modifier = Modifier.fillMaxWidth())
    }
}
