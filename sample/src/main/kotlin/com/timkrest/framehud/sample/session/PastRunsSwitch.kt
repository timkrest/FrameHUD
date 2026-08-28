package com.timkrest.framehud.sample.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.sample.SampleFrameHud
import com.timkrest.framehud.sample.ui.SampleSwitch

@Composable
fun PastRunsSwitch(modifier: Modifier = Modifier) {
    val keeping by SampleFrameHud.keepsRuns.collectAsStateWithLifecycle()

    SampleSwitch(
        title = "Keep past runs",
        subtitle = "Writes the last ${SampleFrameHud.KEPT_RUNS} runs to framehud/history.json every " +
            "time the app leaves the foreground. Switch it on, leave the app, come back and hit Reset.",
        checked = keeping,
        onCheckedChange = SampleFrameHud::setKeepsRuns,
        modifier = modifier,
    )
}
