package com.timkrest.framehud.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

@Preview(name = "Expanded", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun PanelExpandedPreview() {
    Panel(state = previewState(), actions = previewActions())
}

@Preview(name = "Collapsed", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun PanelCollapsedPreview() {
    Panel(state = previewState(isCollapsed = true), actions = previewActions())
}

@Preview(name = "Frozen", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun PanelFrozenPreview() {
    Panel(state = previewState(isFrozen = true), actions = previewActions())
}

@Preview(name = "Emulator", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun PanelEmulatorPreview() {
    Panel(state = previewState(isEmulator = true), actions = previewActions())
}

@Preview(name = "Marked", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun PanelMarkedPreview() {
    Panel(state = previewState(activeMark = "scroll"), actions = previewActions())
}

@Preview(name = "Screens", showBackground = true, backgroundColor = 0xFF202020)
@Composable
private fun PanelScreensPreview() {
    Panel(state = previewState(view = PanelView.SCREENS), actions = previewActions())
}

private fun previewState(
    activeMark: String? = null,
    view: PanelView = PanelView.METRICS,
    isCollapsed: Boolean = false,
    isFrozen: Boolean = false,
    isEmulator: Boolean = false,
) = PanelState(
    metrics = MutableStateFlow(PREVIEW_METRICS),
    choreographerTicksPerSecond = MutableStateFlow(PREVIEW_CHOREOGRAPHER_TICKS_PER_SECOND),
    memory = MutableStateFlow(PREVIEW_MEMORY),
    thermal = MutableStateFlow(ThermalStats.EMPTY),
    process = MutableStateFlow(PREVIEW_PROCESS),
    counters = MutableStateFlow(PREVIEW_COUNTERS),
    activeMark = MutableStateFlow(activeMark),
    view = MutableStateFlow(view),
    screens = flowOf(PREVIEW_SCREENS),
    isCollapsed = MutableStateFlow(isCollapsed),
    isFrozen = MutableStateFlow(isFrozen),
    canRequestOverlayPermission = true,
    isEmulator = isEmulator,
)

private fun previewActions() = PanelActions(
    toggleCollapsed = {},
    toggleView = {},
    toggleFrozen = {},
    reset = {},
    drag = { _, _ -> },
    requestOverlayPermission = {},
)

private val PREVIEW_COUNTERS = listOf(
    CounterReading(name = "decode queue", value = 4, peakSinceReset = 31),
    CounterReading(name = "cache misses", value = 12, peakSinceReset = 12),
)

private const val PREVIEW_CHOREOGRAPHER_TICKS_PER_SECOND = 120

private val PREVIEW_PROCESS = ProcessStats(
    cpuPercent = 42f,
    peakCpuPercent = 61f,
    pssMb = 210,
    peakPssMb = 228,
    threads = 38,
    peakThreads = 41,
    openFiles = 129,
    peakOpenFiles = 140,
)

private val PREVIEW_SCREENS = listOf(
    previewScreen(name = "checkout", frames = 640, jankPercent = 18.4f, p95FrameMs = 31.2f, frozenFrames = 2),
    previewScreen(name = "product/{id}", frames = 1_820, jankPercent = 6.1f, p95FrameMs = 19.7f, frozenFrames = 0),
    previewScreen(name = "cart", frames = 90, jankPercent = 2.2f, p95FrameMs = 13.4f, frozenFrames = 0),
)

private fun previewScreen(
    name: String,
    frames: Int,
    jankPercent: Float,
    p95FrameMs: Float,
    frozenFrames: Int,
) = IntervalReport(
    id = IntervalId.Screen(name),
    stats = IntervalStats(
        frames = frames,
        jankPercent = jankPercent,
        p95FrameMs = p95FrameMs,
        frozenFrames = frozenFrames,
    ),
)

private val PREVIEW_FRAME_PATTERN_MS = floatArrayOf(11f, 14f, 12f, 23f, 13f, 17f, 11f, 57f, 12f, 15f, 20f, 13f)

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
