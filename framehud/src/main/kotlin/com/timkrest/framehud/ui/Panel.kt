package com.timkrest.framehud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun Panel(state: PanelState, actions: PanelActions, modifier: Modifier = Modifier) {
    val metrics by state.metrics.collectAsStateWithLifecycle()
    val choreographerTicksPerSecond by state.choreographerTicksPerSecond.collectAsStateWithLifecycle()
    val memory by state.memory.collectAsStateWithLifecycle()
    val thermal by state.thermal.collectAsStateWithLifecycle()
    val activeMark by state.activeMark.collectAsStateWithLifecycle()
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
            Column(modifier = Modifier.width(PanelWidth)) {
                PanelHeader(
                    metrics = metrics,
                    status = remember(isFrozen, activeMark, choreographerTicksPerSecond, metrics.display) {
                        HeaderStatus.of(
                            isFrozen = isFrozen,
                            activeMark = activeMark,
                            choreographerTicksPerSecond = choreographerTicksPerSecond,
                            frameBudgetMs = metrics.display.frameBudgetMs,
                        )
                    },
                    canRequestOverlayPermission = state.canRequestOverlayPermission,
                    isEmulator = state.isEmulator,
                    actions = actions,
                )
                PanelExpandedContent(
                    metrics = metrics,
                    memory = memory,
                    thermal = thermal,
                    isEmulator = state.isEmulator,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
