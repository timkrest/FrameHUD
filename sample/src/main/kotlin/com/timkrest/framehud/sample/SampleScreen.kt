package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SampleScreen(title: String, subtitle: String, actions: (@Composable () -> Unit)? = null) {
    var active by remember { mutableStateOf(ActiveLoads()) }

    if (Load.GcChurn in active) GcChurn()

    Scaffold { insets ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            item(contentType = ContentType.TITLE) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            item(contentType = ContentType.SUBTITLE) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            item(contentType = ContentType.CHIPS) {
                LoadChips(
                    active = active,
                    onToggle = { load -> active = active.toggled(load) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(contentType = ContentType.READOUT) {
                MetricsReadout(modifier = Modifier.fillMaxWidth())
            }
            if (actions != null) {
                item(contentType = ContentType.ACTIONS) { actions() }
            }
            items(count = ROW_COUNT, key = { it }, contentType = { ContentType.ROW }) { index ->
                SampleRow(index = index, active = active)
            }
        }
    }
}

private enum class ContentType {
    TITLE,
    SUBTITLE,
    CHIPS,
    READOUT,
    ACTIONS,
    ROW,
}

private const val ROW_COUNT = 300
