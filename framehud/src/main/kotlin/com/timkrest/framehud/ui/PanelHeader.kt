package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.timkrest.framehud.PerformanceMetrics

@Immutable
internal class HeaderStatus private constructor(val text: String, val color: Color) {

    companion object {
        fun of(isFrozen: Boolean, activeMark: String?, choreographerTicksPerSecond: Int, frameBudgetMs: Float) = when {
            isFrozen -> HeaderStatus(LABEL_HEADER_FROZEN, TextFrozen)
            activeMark != null -> HeaderStatus(formatMark(activeMark), TextNormal)
            else -> HeaderStatus(formatTiming(choreographerTicksPerSecond, frameBudgetMs), TextHeader)
        }
    }
}

@Composable
internal fun PanelHeader(
    metrics: PerformanceMetrics,
    status: HeaderStatus,
    canRequestOverlayPermission: Boolean,
    isEmulator: Boolean,
    actions: PanelActions,
) {
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
        MetricText(text = status.text, color = status.color)
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
