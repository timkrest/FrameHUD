package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Load chips", showBackground = true)
@Composable
private fun LoadChipsPreview() {
    LoadChips(
        active = ActiveLoads().toggled(Load.Overdraw).toggled(Load.GcChurn),
        onToggle = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Row card", showBackground = true)
@Composable
private fun RowCardPreview() {
    RowCard(index = 3, active = ActiveLoads())
}

@Preview(name = "Sample row", showBackground = true)
@Composable
private fun SampleRowPreview() {
    SampleRow(index = 7, active = ActiveLoads())
}

@Preview(name = "Metrics readout", showBackground = true)
@Composable
private fun MetricsReadoutPreview() {
    MetricsReadout(modifier = Modifier.fillMaxWidth())
}
