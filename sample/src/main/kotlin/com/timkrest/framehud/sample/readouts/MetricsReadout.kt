package com.timkrest.framehud.sample.readouts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.sample.SampleFrameHud
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleLine
import com.timkrest.framehud.sample.ui.SampleNote
import com.timkrest.framehud.sample.ui.formatMs
import com.timkrest.framehud.sample.ui.formatPercent
import com.timkrest.framehud.sample.ui.readable

@Composable
fun MetricsReadout(modifier: Modifier = Modifier) {
    val metrics by FrameHud.metrics.collectAsStateWithLifecycle()
    val diagnosis by FrameHud.diagnosis.collectAsStateWithLifecycle()
    val lastEvent by SampleFrameHud.lastEvent.collectAsStateWithLifecycle()
    val phases = metrics.phases

    SampleCard(title = "FrameHud.metrics", modifier = modifier) {
        SampleLine(label = "fps", value = metrics.window.fps.toString())
        SampleLine(label = "frame budget", value = formatMs(metrics.window.frameBudgetMs))
        SampleLine(label = "jank", value = formatPercent(metrics.window.jankPercent))
        SampleLine(
            label = "${phases.bottleneckStage.readable()} bound",
            value = formatMs(phases.bottleneck.average),
        )
        SampleNote(text = diagnosis.summary)
        SampleNote(text = "Last event: ${lastEvent?.summary ?: "none yet"}")
    }
}
