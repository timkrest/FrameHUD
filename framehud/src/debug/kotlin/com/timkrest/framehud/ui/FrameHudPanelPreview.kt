package com.timkrest.framehud.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.MutableStateFlow

@Preview(name = "Expanded", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun FrameHudPanelExpandedPreview() {
    FrameHudPanel(state = previewState(), actions = previewActions())
}

@Preview(name = "Collapsed", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun FrameHudPanelCollapsedPreview() {
    FrameHudPanel(state = previewState(isCollapsed = true), actions = previewActions())
}

@Preview(name = "Frozen", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun FrameHudPanelFrozenPreview() {
    FrameHudPanel(state = previewState(isFrozen = true), actions = previewActions())
}

@Preview(name = "Emulator", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun FrameHudPanelEmulatorPreview() {
    FrameHudPanel(state = previewState(isEmulator = true), actions = previewActions())
}

private fun previewState(
    isCollapsed: Boolean = false,
    isFrozen: Boolean = false,
    isEmulator: Boolean = false,
) = PanelState(
    metrics = MutableStateFlow(PerformanceMetrics.EMPTY),
    vsyncRate = MutableStateFlow(PREVIEW_VSYNC_RATE),
    memory = MutableStateFlow(PREVIEW_MEMORY),
    thermal = MutableStateFlow(ThermalStats.EMPTY),
    isCollapsed = MutableStateFlow(isCollapsed),
    isFrozen = MutableStateFlow(isFrozen),
    canRequestOverlayPermission = true,
    isEmulator = isEmulator,
)

private fun previewActions() = PanelActions(
    toggleCollapsed = {},
    toggleFrozen = {},
    reset = {},
    drag = { _, _ -> },
    requestOverlayPermission = {},
)

private const val PREVIEW_VSYNC_RATE = 120

private val PREVIEW_MEMORY = MemoryStats(
    usedHeapMb = 84,
    maxHeapMb = 256,
    nativeHeapMb = 37,
    peakUsedHeapMb = 96,
    peakNativeHeapMb = 41,
    gcCount = 3,
    gcTimeMs = 18L,
)
