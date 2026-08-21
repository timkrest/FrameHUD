package com.timkrest.framehud.sample.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.sample.SampleFrameHud
import com.timkrest.framehud.sample.ui.SampleSwitch

@Composable
fun FlightRecorderSwitch(modifier: Modifier = Modifier) {
    val recording by SampleFrameHud.flightRecorder.collectAsStateWithLifecycle()

    SampleSwitch(
        title = "Perfetto flight recorder",
        subtitle = "An incident asks the ${SampleFrameHud.PERFETTO_TRIGGER} trace to keep the seconds " +
            "around it. Start that trace over adb first.",
        checked = recording,
        onCheckedChange = SampleFrameHud::setFlightRecorder,
        modifier = modifier,
    )
}
