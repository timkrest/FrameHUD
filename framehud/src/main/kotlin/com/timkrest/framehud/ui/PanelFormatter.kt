package com.timkrest.framehud.ui

import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalStats
import com.timkrest.framehud.internal.MS_PER_SECOND
import java.util.Locale

private const val LABEL_WIDTH = 8
private const val VALUE_WIDTH = 5
private const val MARK_WIDTH = 14

private val HEADER_LAYOUT = "%-${LABEL_WIDTH}s %${VALUE_WIDTH}s %${VALUE_WIDTH}s %${VALUE_WIDTH}s"

internal val CPU_COLUMNS_HEADER_LINE: String =
    format(HEADER_LAYOUT, LABEL_CPU_SECTION, LABEL_COLUMN_NOW, LABEL_COLUMN_AVG, LABEL_COLUMN_PEAK)

internal val GPU_UNAVAILABLE_LINE: String = format(HEADER_LAYOUT, LABEL_GPU, LABEL_GPU_NA, LABEL_GPU_NA, "")

internal fun formatMetricLine(label: String, value: MetricValue): String {
    val row = format("%-${LABEL_WIDTH}s %s %s", label, formatColumn(value.current), formatColumn(value.average))
    val peak = value.peak ?: return row
    return "$row ${formatColumn(peak)}"
}

private fun formatColumn(valueMs: Float): String {
    val withDecimal = format("%${VALUE_WIDTH}.1f", valueMs)
    return if (withDecimal.length <= VALUE_WIDTH) withDecimal else format("%${VALUE_WIDTH}.0f", valueMs)
}

internal fun formatFps(fps: Int): String = if (fps == 0) LABEL_IDLE else format("%d FPS", fps)

internal fun formatMark(mark: String): String {
    val label = if (mark.length <= MARK_WIDTH) mark else mark.take(MARK_WIDTH - 1) + ELLIPSIS
    return MARK_PREFIX + label
}

internal fun formatTiming(choreographerTicksPerSecond: Int, frameBudgetMs: Float): String =
    format("ui %d/s · %.1fms", choreographerTicksPerSecond, frameBudgetMs)

internal fun formatJankShort(jankPercent: Float): String = format("jank %.1f%%", jankPercent)

internal fun formatWindowSummary(window: FrameWindowStats): String = format(
    "$LABEL_WINDOW  jank %4.1f%%  p95 %5.1f  max %5.1f",
    window.jankPercent,
    window.p95FrameMs,
    window.worstFrameMs,
)

internal fun formatSessionLatency(session: SessionStats): String = format(
    "$LABEL_SESSION  p50 %5.1f  p95 %5.1f  p99 %5.1f",
    session.p50FrameMs,
    session.p95FrameMs,
    session.p99FrameMs,
)

internal fun formatSessionTotals(session: SessionStats): String = format(
    "$LABEL_SESSION %df %s jank %.1f%% frz%d run%d",
    session.frames,
    formatDuration(session.durationMs),
    session.jankPercent,
    session.frozenFrames,
    session.maxJankStreak,
)

internal fun formatLostTime(lostTimeMs: Float): String = if (lostTimeMs < MS_PER_SECOND) {
    format("$LABEL_LOST_TIME %.0fms", lostTimeMs)
} else {
    format("$LABEL_LOST_TIME %.1fs", lostTimeMs / MS_PER_SECOND)
}

internal fun formatMemory(memory: MemoryStats): String = format(
    "$LABEL_MEMORY %d/%d ▲%d · nat %d ▲%d MB",
    memory.usedHeapMb,
    memory.maxHeapMb,
    memory.peakUsedHeapMb,
    memory.nativeHeapMb,
    memory.peakNativeHeapMb,
)

internal fun formatGc(memory: MemoryStats): String =
    format("$LABEL_GC x%d · %d ms", memory.gcCount, memory.gcTimeMs)

internal fun formatDroppedReports(droppedReports: Int): String = format("$LABEL_DROPPED x%d", droppedReports)

internal fun formatThermal(thermal: ThermalStats): String {
    val level = thermal.level.name.lowercase(Locale.US)
    val headroom = thermal.headroom ?: return format("$LABEL_THERMAL %s", level)
    return format("$LABEL_THERMAL %s · hr %.2f", level, headroom)
}

internal fun formatVerdict(verdict: PanelVerdict): String = when (verdict) {
    PanelVerdict.Ok -> LABEL_VERDICT_OK
    is PanelVerdict.Attention -> format("⚠ %s %.1f ms", verdict.phaseLabel, verdict.phaseAvgMs)
}

internal fun formatVerdictShort(verdict: PanelVerdict.Attention): String = format("⚠%s", verdict.phaseLabel)

internal fun pipeLabel(stage: PipelineStage): String = when (stage) {
    PipelineStage.CPU -> LABEL_PIPE_CPU
    PipelineStage.RENDER -> LABEL_PIPE_RENDER
    PipelineStage.GPU -> LABEL_PIPE_GPU
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / MS_PER_SECOND
    val wholeSeconds = seconds.toLong()
    return if (wholeSeconds < SECONDS_PER_MINUTE) {
        format("%.1fs", seconds)
    } else {
        format("%dm%02ds", wholeSeconds / SECONDS_PER_MINUTE, wholeSeconds % SECONDS_PER_MINUTE)
    }
}

private fun format(template: String, vararg args: Any?): String = String.format(Locale.US, template, *args)

private const val SECONDS_PER_MINUTE = 60L
