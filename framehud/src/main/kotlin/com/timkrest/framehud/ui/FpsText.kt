package com.timkrest.framehud.ui

import androidx.compose.runtime.Composable

@Composable
internal fun FpsText(fps: Int, refreshRateHz: Float) {
    MetricText(text = formatFps(fps), color = fpsColor(fps = fps, refreshRateHz = refreshRateHz))
}
