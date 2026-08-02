package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.timkrest.framehud.PerformanceMetrics

@Composable
internal fun PanelCollapsedContent(metrics: PerformanceMetrics, isEmulator: Boolean, actions: PanelActions) {
    val summary = remember(metrics, isEmulator) { buildCollapsedLine(metrics = metrics, isEmulator = isEmulator) }
    Row(
        modifier = Modifier
            .height(CollapsedRowHeight)
            .tapAndHold(onTap = actions.toggleCollapsed, onHold = actions.toggleFrozen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrameSparkline(
            history = metrics.window.history,
            frameBudgetMs = metrics.display.frameBudgetMs,
            modifier = Modifier
                .width(SparklineMiniWidth)
                .height(SparklineMiniHeight),
        )
        Spacer(Modifier.width(ItemSpacing))
        BasicText(text = summary, style = PanelTextStyle, softWrap = false, maxLines = 1)
    }
}
