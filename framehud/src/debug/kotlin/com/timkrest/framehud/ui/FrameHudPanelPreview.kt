package com.timkrest.framehud.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.InternalFrameHudApi
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

@Preview(name = "Marked", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun FrameHudPanelMarkedPreview() {
    FrameHudPanel(state = previewState(activeMark = "scroll"), actions = previewActions())
}

private fun previewState(
    activeMark: String? = null,
    isCollapsed: Boolean = false,
    isFrozen: Boolean = false,
    isEmulator: Boolean = false,
) = PanelState(
    metrics = MutableStateFlow(PREVIEW_METRICS),
    choreographerTicksPerSecond = MutableStateFlow(PREVIEW_CHOREOGRAPHER_TICKS_PER_SECOND),
    memory = MutableStateFlow(PREVIEW_MEMORY),
    thermal = MutableStateFlow(ThermalStats.EMPTY),
    activeMark = MutableStateFlow(activeMark),
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

private const val PREVIEW_CHOREOGRAPHER_TICKS_PER_SECOND = 120

private val PREVIEW_FRAME_PATTERN_MS = floatArrayOf(11f, 14f, 12f, 23f, 13f, 17f, 11f, 57f, 12f, 15f, 20f, 13f)

@OptIn(InternalFrameHudApi::class)
private fun previewHistory(): FrameHistory {
    val totalsMs = FloatArray(FrameHudConfig.DEFAULT_METRICS_SAMPLE_WINDOW_FRAMES) {
        PREVIEW_FRAME_PATTERN_MS[it % PREVIEW_FRAME_PATTERN_MS.size]
    }
    val deadlinesMs = FloatArray(totalsMs.size) { DisplayInfo.DEFAULT.frameBudgetMs }
    return FrameHistory.of(totalsMs = totalsMs, deadlinesMs = deadlinesMs)
}

private val PREVIEW_METRICS = PerformanceMetrics(
    window = FrameWindowStats(
        fps = 55,
        jankPercent = 33.3f,
        p95FrameMs = 23f,
        worstFrameMs = 57f,
        history = previewHistory(),
    ),
)

private val PREVIEW_MEMORY = MemoryStats(
    usedHeapMb = 84,
    maxHeapMb = 256,
    nativeHeapMb = 37,
    peakUsedHeapMb = 96,
    peakNativeHeapMb = 41,
    gcCount = 3,
    gcTimeMs = 18L,
)
