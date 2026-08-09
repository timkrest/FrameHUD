package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.timkrest.framehud.PerformanceMetrics

@Composable
internal fun PanelHeader(
    metrics: PerformanceMetrics,
    vsyncRate: Int,
    isFrozen: Boolean,
    canRequestOverlayPermission: Boolean,
    isEmulator: Boolean,
    actions: PanelActions,
) {
    val timing = remember(vsyncRate, metrics.display.frameBudgetMs) {
        formatTiming(vsyncRate = vsyncRate, frameBudgetMs = metrics.display.frameBudgetMs)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tapAndHold(onTap = {}, onHold = actions.toggleFrozen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEmulator) {
            MetricText(text = LABEL_EMULATOR, color = TextCaution)
            Spacer(Modifier.width(ItemSpacing))
        }
        MetricText(
            text = if (isFrozen) LABEL_HEADER_FROZEN else timing,
            color = if (isFrozen) TextFrozen else TextHeader,
        )
        Spacer(Modifier.weight(1f))
        FpsText(fps = metrics.window.fps, refreshRateHz = metrics.display.refreshRateHz)
        Spacer(Modifier.width(ItemSpacing))
        if (canRequestOverlayPermission) {
            PanelIconButton(icon = ICON_DETACH, onClick = actions.requestOverlayPermission)
        }
        PanelIconButton(icon = ICON_COLLAPSE, onClick = actions.toggleCollapsed)
        PanelIconButton(icon = ICON_RESET, onClick = actions.reset)
    }
}
