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
    val process by state.process.collectAsStateWithLifecycle()
    val counters by state.counters.collectAsStateWithLifecycle()
    val activeMark by state.activeMark.collectAsStateWithLifecycle()
    val view by state.view.collectAsStateWithLifecycle()
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
                    status = remember(isFrozen, view, activeMark, choreographerTicksPerSecond, metrics.window.frameBudgetMs) {
                        HeaderStatus.of(
                            isFrozen = isFrozen,
                            view = view,
                            activeMark = activeMark,
                            choreographerTicksPerSecond = choreographerTicksPerSecond,
                            frameBudgetMs = metrics.window.frameBudgetMs,
                        )
                    },
                    canRequestOverlayPermission = state.canRequestOverlayPermission,
                    isEmulator = state.isEmulator,
                    actions = actions,
                )
                when (view) {
                    PanelView.METRICS -> PanelExpandedContent(
                        metrics = metrics,
                        memory = memory,
                        thermal = thermal,
                        process = process,
                        counters = counters,
                        isEmulator = state.isEmulator,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PanelView.SCREENS -> PanelScreensContent(
                        screens = state.screens,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
