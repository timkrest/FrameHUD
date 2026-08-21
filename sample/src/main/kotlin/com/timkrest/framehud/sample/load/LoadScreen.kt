package com.timkrest.framehud.sample.load

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.sample.SampleDestination
import com.timkrest.framehud.sample.SampleFrameHud
import com.timkrest.framehud.sample.readouts.MetricsReadout
import com.timkrest.framehud.sample.ui.SampleHeader

@Composable
fun LoadScreen(
    destination: SampleDestination,
    active: ActiveLoads,
    onToggle: (Load) -> Unit,
    onOpenRow: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
            FrameHud.mark = if (isScrolling) SampleFrameHud.SCROLL_MARK else null
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(contentType = ContentType.HEADER) {
            SampleHeader(title = destination.title, subtitle = destination.subtitle)
        }
        item(contentType = ContentType.CHIPS) {
            LoadChips(active = active, onToggle = onToggle, modifier = Modifier.fillMaxWidth())
        }
        item(contentType = ContentType.BUDGET) {
            BudgetSwitch()
        }
        item(contentType = ContentType.READOUT) {
            MetricsReadout()
        }
        items(count = ROW_COUNT, key = { it }, contentType = { ContentType.ROW }) { index ->
            SampleRow(index = index, active = active, onOpen = { onOpenRow(index) })
        }
    }
}

private enum class ContentType {
    HEADER,
    CHIPS,
    BUDGET,
    READOUT,
    ROW,
}

private const val ROW_COUNT = 300
