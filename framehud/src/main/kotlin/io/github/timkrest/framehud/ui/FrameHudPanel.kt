package io.github.timkrest.framehud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timkrest.framehud.MemoryStats
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.StateFlow

/** What the panel reads. The flow references never change, which keeps the panel skippable. */
@Immutable
internal class PanelState(
    val metrics: StateFlow<PerformanceMetrics>,
    val vsyncRate: StateFlow<Int>,
    val memory: StateFlow<MemoryStats>,
    val thermal: StateFlow<ThermalStats>,
    val isCollapsed: StateFlow<Boolean>,
    val isFrozen: StateFlow<Boolean>,
)

/** What the panel can trigger. [requestOverlayPermission] is null when there is nothing to ask for. */
internal class PanelActions(
    val toggleCollapsed: () -> Unit,
    val toggleFrozen: () -> Unit,
    val reset: () -> Unit,
    val drag: (dx: Float, dy: Float) -> Unit,
    val requestOverlayPermission: (() -> Unit)?,
)

@Composable
internal fun FrameHudPanel(state: PanelState, actions: PanelActions, modifier: Modifier = Modifier) {
    val metrics by state.metrics.collectAsStateWithLifecycle()
    val vsyncRate by state.vsyncRate.collectAsStateWithLifecycle()
    val memory by state.memory.collectAsStateWithLifecycle()
    val thermal by state.thermal.collectAsStateWithLifecycle()
    val isCollapsed by state.isCollapsed.collectAsStateWithLifecycle()
    val isFrozen by state.isFrozen.collectAsStateWithLifecycle()

    val frozenBorder = if (isFrozen) {
        Modifier.border(width = PanelBorderWidth, color = TextFrozen, shape = PanelShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(PanelShape)
            .background(OverlayBackground)
            .then(frozenBorder)
            .dragHandle(actions.drag)
            .padding(PanelPadding),
    ) {
        if (isCollapsed) {
            CollapsedContent(metrics = metrics, actions = actions)
        } else {
            ExpandedContent(
                metrics = metrics,
                vsyncRate = vsyncRate,
                memory = memory,
                thermal = thermal,
                isFrozen = isFrozen,
                actions = actions,
                modifier = Modifier.width(PanelWidth),
            )
        }
    }
}

@Composable
private fun CollapsedContent(metrics: PerformanceMetrics, actions: PanelActions) {
    val summary = remember(metrics) { buildCollapsedLine(metrics = metrics) }
    Row(
        modifier = Modifier
            .height(CollapsedRowHeight)
            .tapAndHold(onTap = actions.toggleCollapsed, onHold = actions.toggleFrozen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrameSparkline(
            history = metrics.history,
            frameBudgetMs = metrics.frameBudgetMs,
            modifier = Modifier
                .width(SparklineMiniWidth)
                .height(SparklineMiniHeight),
        )
        Spacer(Modifier.width(ItemSpacing))
        BasicText(text = summary, style = PanelTextStyle, softWrap = false, maxLines = 1)
    }
}

@Composable
private fun ExpandedContent(
    metrics: PerformanceMetrics,
    vsyncRate: Int,
    memory: MemoryStats,
    thermal: ThermalStats,
    isFrozen: Boolean,
    actions: PanelActions,
    modifier: Modifier = Modifier,
) {
    val lines = remember(metrics, memory, thermal) {
        buildPanelLines(metrics = metrics, memory = memory, thermal = thermal)
    }
    Column(modifier = modifier) {
        PanelHeader(
            metrics = metrics,
            vsyncRate = vsyncRate,
            isFrozen = isFrozen,
            actions = actions,
        )

        FrameSparkline(
            history = metrics.history,
            frameBudgetMs = metrics.frameBudgetMs,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight),
        )

        PanelTextBlock(lines = lines, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PanelHeader(
    metrics: PerformanceMetrics,
    vsyncRate: Int,
    isFrozen: Boolean,
    actions: PanelActions,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tapAndHold(onTap = {}, onHold = actions.toggleFrozen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricText(
            text = if (isFrozen) {
                LABEL_HEADER_FROZEN
            } else {
                formatTiming(vsyncRate = vsyncRate, frameBudgetMs = metrics.frameBudgetMs)
            },
            color = if (isFrozen) TextFrozen else TextHeader,
        )
        Spacer(Modifier.weight(1f))
        FpsText(metrics = metrics)
        Spacer(Modifier.width(ItemSpacing))
        actions.requestOverlayPermission?.let { request ->
            PanelIconButton(icon = ICON_DETACH, onClick = request)
        }
        PanelIconButton(icon = ICON_COLLAPSE, onClick = actions.toggleCollapsed)
        PanelIconButton(icon = ICON_RESET, onClick = actions.reset)
    }
}
