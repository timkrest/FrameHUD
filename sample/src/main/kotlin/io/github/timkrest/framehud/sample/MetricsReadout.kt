package io.github.timkrest.framehud.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timkrest.framehud.FrameHud
import io.github.timkrest.framehud.JankDiagnosis
import java.util.Locale

/** The same numbers the panel draws, read through the public API. */
@Composable
fun MetricsReadout(modifier: Modifier = Modifier) {
    val metrics by FrameHud.metrics.collectAsStateWithLifecycle()
    val memory by FrameHud.memoryStats.collectAsStateWithLifecycle()
    val thermal by FrameHud.thermalStats.collectAsStateWithLifecycle()
    val vsyncRate by FrameHud.vsyncRate.collectAsStateWithLifecycle()
    val lastEvent by SampleEvents.last.collectAsStateWithLifecycle()

    val diagnosis = JankDiagnosis.of(
        metrics = metrics,
        memory = memory,
        thermal = thermal,
        vsyncRate = vsyncRate,
    )

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(text = "FrameHud.metrics", style = MaterialTheme.typography.labelLarge)
            Text(
                text = String.format(
                    Locale.US,
                    "%d FPS · budget %.1f ms · %s bound %.1f ms",
                    metrics.window.fps,
                    metrics.display.frameBudgetMs,
                    metrics.phases.bottleneckStage.name.lowercase(Locale.US),
                    metrics.phases.bottleneck.average,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
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
