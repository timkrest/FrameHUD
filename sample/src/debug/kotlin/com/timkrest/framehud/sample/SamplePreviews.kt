package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.timkrest.framehud.sample.load.ActiveLoads
import com.timkrest.framehud.sample.load.Load
import com.timkrest.framehud.sample.load.LoadChips
import com.timkrest.framehud.sample.load.RowCard
import com.timkrest.framehud.sample.readouts.MetricsReadout
import com.timkrest.framehud.sample.ui.SampleSwitch
import kotlinx.coroutines.flow.MutableStateFlow

@Preview(name = "Load chips", showBackground = true)
@Composable
private fun LoadChipsPreview() {
    LoadChips(
        active = ActiveLoads().toggled(Load.Overdraw).toggled(Load.BackgroundDecode),
        onToggle = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Switch", showBackground = true)
@Composable
private fun SampleSwitchPreview() {
    SampleSwitch(
        title = "Judge frames by a budget",
        subtitle = "The same frames, judged against a fixed budget instead of the display deadline.",
        checked = MutableStateFlow(true),
        onCheckedChange = {},
    )
}

@Preview(name = "Row card", showBackground = true)
@Composable
private fun RowCardPreview() {
    RowCard(index = 3, active = ActiveLoads(), onOpen = {})
}

@Preview(name = "Metrics readout", showBackground = true)
@Composable
private fun MetricsReadoutPreview() {
    MetricsReadout()
}
