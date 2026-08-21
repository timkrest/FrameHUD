package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.timkrest.framehud.sample.load.ActiveLoads
import com.timkrest.framehud.sample.load.BudgetSwitch
import com.timkrest.framehud.sample.load.Load
import com.timkrest.framehud.sample.load.LoadChips
import com.timkrest.framehud.sample.load.RowCard
import com.timkrest.framehud.sample.readouts.MetricsReadout

@Preview(name = "Load chips", showBackground = true)
@Composable
private fun LoadChipsPreview() {
    LoadChips(
        active = ActiveLoads().toggled(Load.Overdraw).toggled(Load.BackgroundDecode),
        onToggle = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Budget switch", showBackground = true)
@Composable
private fun BudgetSwitchPreview() {
    BudgetSwitch()
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
