package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.timkrest.framehud.PerformanceMetrics

@Composable
internal fun PanelCollapsedContent(metrics: PerformanceMetrics, isEmulator: Boolean, actions: PanelActions) {
    val summary = remember(metrics, isEmulator) { buildCollapsedLine(metrics = metrics, isEmulator = isEmulator) }
    val measurer = rememberTextMeasurer()
    val summaryWidth = with(LocalDensity.current) {
        remember(measurer) {
            measurer.measure(
                text = COLLAPSED_WIDEST_READING,
                style = PanelTextStyle,
                softWrap = false,
                maxLines = 1,
            ).size.width
        }.toDp()
    }
    Row(
        modifier = Modifier
            .height(CollapsedRowHeight)
            .tapAndHold(onTap = actions.toggleCollapsed, onHold = actions.toggleFrozen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrameSparkline(
            window = metrics.window,
            modifier = Modifier
                .width(SparklineMiniWidth)
                .height(SparklineMiniHeight),
        )
        Spacer(Modifier.width(ItemSpacing))
        BasicText(
            text = summary,
            modifier = Modifier.widthIn(min = summaryWidth),
            style = PanelTextStyle,
            softWrap = false,
            maxLines = 1,
        )
    }
}
