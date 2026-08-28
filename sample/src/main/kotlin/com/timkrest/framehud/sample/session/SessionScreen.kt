package com.timkrest.framehud.sample.session

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.sample.SampleDestination
import com.timkrest.framehud.sample.readouts.IntervalStatsCard
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleHeader
import com.timkrest.framehud.sample.ui.SampleNote

@Composable
fun SessionScreen(
    destination: SampleDestination,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val state = rememberSessionState()
    val report = state.report

    LaunchedEffect(state) { state.read() }

    if (state.dialogShown) {
        MeasuredDialog(onDismiss = state::hideDialog)
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item { SampleHeader(title = destination.title, subtitle = destination.subtitle) }
        item {
            SessionActions(
                onRefresh = state::read,
                onReset = state::reset,
                onShare = { state.share(activity) },
                onSaveBaseline = state::saveBaseline,
                onToggleCollecting = FrameHud::toggle,
                onMeasureDialog = state::showDialog,
                onRetainTrace = state::retainTrace,
            )
        }
        item { FreezeSwitch() }
        item { FlightRecorderSwitch() }
        item { PastRunsSwitch() }
        state.message?.let { text ->
            item { SampleCard(title = "Last action") { SampleNote(text = text) } }
        }
        item { IntervalStatsCard(title = "Session so far", stats = report.stats) }
        item { IntervalsCard(intervals = report.intervals) }
        item { ScreensCard(screens = report.worstScreens) }
        item { IncidentsCard(incidents = report.incidents) }
        items(report.incidents) { incident -> IncidentCard(incident = incident) }
        item { BaselineCard(comparison = report.comparison) }
        item { PastRunsCard(runs = report.pastRuns) }
    }
}
