package com.timkrest.framehud.sample.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.sample.ui.SampleSwitch

@Composable
fun FreezeSwitch() {
    val frozen by FrameHud.isFrozen.collectAsStateWithLifecycle()

    SampleSwitch(
        title = "Frozen readings",
        subtitle = "The panel holds the numbers on screen while collection carries on underneath.",
        checked = frozen,
        onCheckedChange = { FrameHud.toggleFreeze() },
    )
}
