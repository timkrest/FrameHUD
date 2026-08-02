package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadChips(active: ActiveLoads, onToggle: (Load) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Load.entries.forEach { load ->
            FilterChip(
                selected = load in active,
                onClick = { onToggle(load) },
                label = { Text(load.label, maxLines = 1) },
            )
        }
    }
}
