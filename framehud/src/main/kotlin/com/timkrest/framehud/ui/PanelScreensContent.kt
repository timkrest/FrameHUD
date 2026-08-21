package com.timkrest.framehud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.IntervalReport
import kotlinx.coroutines.flow.Flow

@Composable
internal fun PanelScreensContent(
    screens: Flow<List<IntervalReport>>,
    modifier: Modifier = Modifier,
) {
    val listed by screens.collectAsStateWithLifecycle(initialValue = emptyList())
    PanelTextBlock(lines = remember(listed) { buildScreenLines(listed) }, modifier = modifier)
}
