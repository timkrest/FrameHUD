package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.PerformanceMetrics

@Composable
internal fun PanelMetricsContent(
    state: PanelState,
    metrics: PerformanceMetrics,
    detail: PanelDetail,
    modifier: Modifier = Modifier,
) {
    val memory by state.memory.collectAsStateWithLifecycle()
    val thermal by state.thermal.collectAsStateWithLifecycle()
    val process by state.process.collectAsStateWithLifecycle()
    val counters by state.counters.collectAsStateWithLifecycle()

    val lines = remember(metrics, memory, thermal, process, counters, state.isEmulator, detail) {
        buildPanelLines(
            metrics = metrics,
            memory = memory,
            thermal = thermal,
            process = process,
            counters = counters,
            isEmulator = state.isEmulator,
            detail = detail,
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
