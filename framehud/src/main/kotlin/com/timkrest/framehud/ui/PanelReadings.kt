// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timkrest.framehud.PerformanceMetrics

@Composable
internal fun PanelReadings(
    state: PanelState,
    actions: PanelActions,
    metrics: PerformanceMetrics,
    detail: PanelDetail,
    isFrozen: Boolean,
) {
    val choreographerTicksPerSecond by state.choreographerTicksPerSecond.collectAsStateWithLifecycle()
    val activeMark by state.activeMark.collectAsStateWithLifecycle()
    val view by state.view.collectAsStateWithLifecycle()
    val frameBudgetMs = metrics.window.frameBudgetMs

    Column(modifier = Modifier.width(PanelWidth)) {
        PanelHeader(
            metrics = metrics,
            status = remember(isFrozen, view, activeMark, choreographerTicksPerSecond, frameBudgetMs) {
                HeaderStatus.of(
                    isFrozen = isFrozen,
                    view = view,
                    activeMark = activeMark,
                    choreographerTicksPerSecond = choreographerTicksPerSecond,
                    frameBudgetMs = frameBudgetMs,
                )
            },
            canRequestOverlayPermission = state.canRequestOverlayPermission,
            isEmulator = state.isEmulator,
            actions = actions,
        )
        when (view) {
            PanelView.METRICS -> PanelMetricsContent(
                state = state,
                metrics = metrics,
                detail = detail,
                modifier = Modifier.fillMaxWidth(),
            )

            PanelView.SCREENS -> PanelScreensContent(
                screens = state.screens,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
