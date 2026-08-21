package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.timkrest.framehud.sample.load.ActiveLoads
import com.timkrest.framehud.sample.load.LoadEffects
import com.timkrest.framehud.sample.load.LoadScreen
import com.timkrest.framehud.sample.readouts.ReadoutsScreen
import com.timkrest.framehud.sample.session.SessionScreen

@Composable
fun SampleApp(onOpenRow: (Int) -> Unit) {
    var destination by remember { mutableStateOf(SampleDestination.Load) }
    var active by remember { mutableStateOf(ActiveLoads()) }

    MeasuredScreen(name = destination.screen)
    LoadEffects(active = active)

    Scaffold(topBar = { SampleTabs(destination = destination, onSelect = { destination = it }) }) { insets ->
        val contentPadding = insets.spaced()
        when (destination) {
            SampleDestination.Load -> LoadScreen(
                destination = destination,
                active = active,
                onToggle = { load -> active = active.toggled(load) },
                onOpenRow = onOpenRow,
                contentPadding = contentPadding,
            )

            SampleDestination.Readouts -> ReadoutsScreen(
                destination = destination,
                contentPadding = contentPadding,
            )

            SampleDestination.Session -> SessionScreen(
                destination = destination,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun SampleTabs(destination: SampleDestination, onSelect: (SampleDestination) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = destination.ordinal,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SampleDestination.entries.forEach { entry ->
            Tab(
                selected = entry == destination,
                onClick = { onSelect(entry) },
                text = { Text(text = entry.title, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun PaddingValues.spaced(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + SCREEN_SPACING,
        top = calculateTopPadding() + SCREEN_SPACING,
        end = calculateEndPadding(direction) + SCREEN_SPACING,
        bottom = calculateBottomPadding() + SCREEN_SPACING,
    )
}

private val SCREEN_SPACING = 16.dp
