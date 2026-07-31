package io.github.timkrest.framehud.sample

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

/**
 * Everything scrolls in one list, so the rows keep the screen instead of being squeezed under a
 * fixed header — and scrolling the header produces frames too.
 */
@Composable
fun SampleScreen(title: String, subtitle: String, actions: (@Composable () -> Unit)? = null) {
    var active by remember { mutableStateOf(emptySet<Load>()) }

    if (Load.GcChurn in active) GcChurn()

    Scaffold { insets ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            item {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            item {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                LoadChips(
                    active = active,
                    onToggle = { load -> active = if (load in active) active - load else active + load },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                MetricsReadout(modifier = Modifier.fillMaxWidth())
            }
            if (actions != null) {
                item { actions() }
            }
            items(count = ROW_COUNT, key = { it }) { index ->
                SampleRow(index = index, active = active)
            }
        }
    }
}
