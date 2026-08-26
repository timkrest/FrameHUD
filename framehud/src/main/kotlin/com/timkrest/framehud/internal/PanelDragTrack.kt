package com.timkrest.framehud.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

internal class PanelDragTrack(
    private val host: IntSize,
    private val grabbedAt: Offset,
    private val grabbedFromEnd: Float,
    private val grabbedFromTop: Float,
) {
    fun fromEndAt(screenX: Float, panel: IntSize): Float =
        (grabbedFromEnd - (screenX - grabbedAt.x)).insideHost(host.width, panel.width)

    fun fromTopAt(screenY: Float, panel: IntSize): Float =
        (grabbedFromTop + (screenY - grabbedAt.y)).insideHost(host.height, panel.height)
}

internal fun Float.insideHost(hostSize: Int, panelSize: Int): Float =
    coerceIn(0f, (hostSize - panelSize).coerceAtLeast(0).toFloat())
