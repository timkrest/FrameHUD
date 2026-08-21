package com.timkrest.framehud.sample.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.Incident
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleLine
import com.timkrest.framehud.sample.ui.SampleNote
import com.timkrest.framehud.sample.ui.formatMs
import com.timkrest.framehud.sample.ui.formatPercent

@Composable
fun SessionActions(
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onShare: () -> Unit,
    onSaveBaseline: () -> Unit,
    onToggleCollecting: () -> Unit,
    onMeasureDialog: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onRefresh) { Text(text = "Read again", maxLines = 1) }
        OutlinedButton(onClick = onShare) { Text(text = "Share report", maxLines = 1) }
        OutlinedButton(onClick = onSaveBaseline) { Text(text = "Save baseline", maxLines = 1) }
        OutlinedButton(onClick = onToggleCollecting) { Text(text = "Toggle collecting", maxLines = 1) }
        OutlinedButton(onClick = onMeasureDialog) { Text(text = "Measure a dialog", maxLines = 1) }
        OutlinedButton(onClick = onReset) { Text(text = "Reset", maxLines = 1) }
    }
}

@Composable
fun IntervalsCard(intervals: List<IntervalReport>) {
    SampleCard(title = "Intervals") {
        if (intervals.isEmpty()) SampleNote(text = "Nothing has been measured yet.")
        intervals.forEach { interval ->
            SampleLine(label = interval.id.label, value = interval.budgetSummary())
        }
    }
}

@Composable
fun ScreensCard(screens: List<IntervalReport>) {
    SampleCard(title = "Screens, worst first") {
        if (screens.isEmpty()) SampleNote(text = "No screen has drawn a frame yet.")
        screens.forEach { screen ->
            SampleLine(label = screen.id.name, value = screen.jankSummary())
        }
    }
}

@Composable
fun IncidentsCard(incidents: List<Incident>) {
    SampleCard(title = "Incidents") {
        if (incidents.isEmpty()) {
            SampleNote(text = "No jank burst and no frozen frame so far.")
            SampleNote(text = "Switch a load on, scroll the list, then read this again.")
        } else {
            SampleNote(text = "Every case below kept what the app looked like when it fired.")
        }
    }
}

@Composable
fun BaselineCard(comparison: BaselineComparison?) {
    SampleCard(title = "Baseline") {
        when (comparison) {
            null -> SampleNote(text = "Not read yet.")

            BaselineComparison.NoBaseline ->
                SampleNote(text = "No run has recorded a baseline on this device yet.")

            is BaselineComparison.OtherEnvironment -> SampleNote(
                text = "Recorded on ${comparison.recorded.label}, running on ${comparison.current.label}.",
            )

            is BaselineComparison.Compared -> comparison.intervals.forEach { interval ->
                SampleNote(text = interval.id.label)
                interval.metrics.forEach { delta -> SampleNote(text = delta.summary) }
                interval.uncompared.forEach { gap -> SampleNote(text = gap.summary) }
            }
        }
    }
}

private fun IntervalReport.budgetSummary(): String = when {
    stats.frames == 0 -> stats.noFrames()
    frameBudgetMs != null -> "${stats.frames} frames, $frameBudgetMs ms"
    else -> "${stats.frames} frames, mixed budgets"
}

private fun IntervalReport.jankSummary(): String {
    if (stats.frames == 0) return stats.noFrames()
    return "${formatPercent(stats.jankPercent)} jank, p95 ${formatMs(stats.p95FrameMs)}, ${stats.frozenFrames} frozen"
}

private fun IntervalStats.noFrames(): String =
    if (droppedReports == 0) "no frames yet" else "no frames yet, $droppedReports dropped report(s)"
