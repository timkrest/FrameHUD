package com.timkrest.framehud.sample.readouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.sample.SampleDestination
import com.timkrest.framehud.sample.ui.SampleHeader

@Composable
fun ReadoutsScreen(
    destination: SampleDestination,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val metrics by FrameHud.metrics.collectAsStateWithLifecycle()
    val diagnosis by FrameHud.diagnosis.collectAsStateWithLifecycle()
    val memory by FrameHud.memoryStats.collectAsStateWithLifecycle()
    val thermal by FrameHud.thermalStats.collectAsStateWithLifecycle()
    val process by FrameHud.processStats.collectAsStateWithLifecycle()
    val counters by FrameHud.counters.collectAsStateWithLifecycle()
    val ticksPerSecond by FrameHud.choreographerTicksPerSecond.collectAsStateWithLifecycle()
    val frozen by FrameHud.isFrozen.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item { SampleHeader(title = destination.title, subtitle = destination.subtitle) }
        item { WindowCard(window = metrics.window) }
        item { PhasesCard(phases = metrics.phases) }
        item { IntervalStatsCard(title = "Session", stats = metrics.session) }
        item { DiagnosisCard(diagnosis = diagnosis) }
        item { ProcessCard(process = process) }
        item { MemoryCard(memory = memory) }
        item { ThermalCard(thermal = thermal) }
        item { CountersCard(counters = counters) }
        item { CollectionCard(display = metrics.display, ticksPerSecond = ticksPerSecond, frozen = frozen) }
    }
}
