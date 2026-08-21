package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.PerformanceMetrics
import java.util.Locale

@Composable
fun MetricsReadout(modifier: Modifier = Modifier) {
    val metrics by FrameHud.metrics.collectAsStateWithLifecycle()
    val diagnosis by FrameHud.diagnosis.collectAsStateWithLifecycle()
    val lastEvent by SampleEvents.last.collectAsStateWithLifecycle()

    val summary = remember(metrics) { formatMetricsSummary(metrics) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(text = "FrameHud.metrics", style = MaterialTheme.typography.labelLarge)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "JankDiagnosis: ${diagnosis.summary}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Last event: ${lastEvent?.summary ?: "none yet"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatMetricsSummary(metrics: PerformanceMetrics): String = String.format(
    Locale.US,
    "%d FPS · budget %.1f ms · %s bound %.1f ms",
    metrics.window.fps,
    metrics.window.frameBudgetMs,
    metrics.phases.bottleneckStage.name.lowercase(Locale.US),
    metrics.phases.bottleneck.average,
)
