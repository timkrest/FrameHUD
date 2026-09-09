// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun Panel(state: PanelState, actions: PanelActions, modifier: Modifier = Modifier) {
    val metrics by state.metrics.collectAsStateWithLifecycle()
    val detail by state.detail.collectAsStateWithLifecycle()
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
            .tapAndHold(onTap = actions.showNextDetail, onHold = actions.toggleFrozen)
            .padding(PanelPadding),
    ) {
        when (detail) {
            PanelDetail.MINI -> PanelMiniContent(metrics = metrics, isEmulator = state.isEmulator)
            PanelDetail.FRAMES, PanelDetail.FULL -> PanelReadings(
                state = state,
                actions = actions,
                metrics = metrics,
                detail = detail,
                isFrozen = isFrozen,
            )
        }
    }
}
