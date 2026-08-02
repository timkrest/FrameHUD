package com.timkrest.framehud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun FrameHudPanel(state: PanelState, actions: PanelActions, modifier: Modifier = Modifier) {
    val metrics by state.metrics.collectAsStateWithLifecycle()
    val vsyncRate by state.vsyncRate.collectAsStateWithLifecycle()
    val memory by state.memory.collectAsStateWithLifecycle()
    val thermal by state.thermal.collectAsStateWithLifecycle()
    val isCollapsed by state.isCollapsed.collectAsStateWithLifecycle()
    val isFrozen by state.isFrozen.collectAsStateWithLifecycle()

    val frozenBorder = remember(isFrozen) {
        if (isFrozen) Modifier.border(width = PanelBorderWidth, color = TextFrozen, shape = PanelShape) else Modifier
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
            PanelCollapsedContent(metrics = metrics, isEmulator = state.isEmulator, actions = actions)
        } else {
            PanelExpandedContent(
                metrics = metrics,
                vsyncRate = vsyncRate,
                memory = memory,
                thermal = thermal,
                isFrozen = isFrozen,
                canRequestOverlayPermission = state.canRequestOverlayPermission,
                isEmulator = state.isEmulator,
                actions = actions,
                modifier = Modifier.width(PanelWidth),
            )
        }
    }
}
