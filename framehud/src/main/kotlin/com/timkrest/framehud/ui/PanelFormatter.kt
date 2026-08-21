package com.timkrest.framehud.ui

import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats
import com.timkrest.framehud.internal.MS_PER_SECOND
import com.timkrest.framehud.internal.formatInvariant
import java.util.Locale

private const val LABEL_WIDTH = 8
private const val VALUE_WIDTH = 5
private const val MARK_WIDTH = 14
private const val SCREEN_NAME_WIDTH = 11
private const val COUNTER_NAME_WIDTH = 14

private val HEADER_LAYOUT = "%-${LABEL_WIDTH}s %${VALUE_WIDTH}s %${VALUE_WIDTH}s %${VALUE_WIDTH}s"

private const val SCREEN_ROW_LAYOUT = "%-${SCREEN_NAME_WIDTH}s %4s %5s %5s %3s"

private const val COUNTER_ROW_LAYOUT = "%-${COUNTER_NAME_WIDTH}s %6d%s"

internal val CPU_COLUMNS_HEADER_LINE: String =
    formatInvariant(HEADER_LAYOUT, LABEL_CPU_SECTION, LABEL_COLUMN_NOW, LABEL_COLUMN_AVG, LABEL_COLUMN_PEAK)

internal val GPU_UNAVAILABLE_LINE: String = formatInvariant(HEADER_LAYOUT, LABEL_GPU, LABEL_GPU_NA, LABEL_GPU_NA, "")

internal fun formatMetricLine(label: String, value: MetricValue): String {
    val row = formatInvariant("%-${LABEL_WIDTH}s %s %s", label, formatColumn(value.current), formatColumn(value.average))
    val peak = value.peak ?: return row
    return "$row ${formatColumn(peak)}"
}

private fun formatColumn(valueMs: Float): String {
    val withDecimal = formatInvariant("%${VALUE_WIDTH}.1f", valueMs)
    return if (withDecimal.length <= VALUE_WIDTH) withDecimal else formatInvariant("%${VALUE_WIDTH}.0f", valueMs)
}

internal fun formatFps(fps: Int): String = if (fps == 0) LABEL_IDLE else formatInvariant("%d FPS", fps)

internal fun formatMark(mark: String): String = MARK_PREFIX + truncated(mark, MARK_WIDTH)

internal val SCREEN_COLUMNS_HEADER_LINE: String = formatInvariant(
    SCREEN_ROW_LAYOUT,
    LABEL_COLUMN_SCREEN,
    LABEL_COLUMN_FRAMES,
    LABEL_COLUMN_JANK,
    LABEL_COLUMN_P95,
    LABEL_COLUMN_FROZEN,
)

internal fun formatScreenLine(screen: IntervalReport): String = formatInvariant(
    SCREEN_ROW_LAYOUT,
    truncated(screen.id.name, SCREEN_NAME_WIDTH),
    screen.stats.frames.toString(),
    formatInvariant("%.1f", screen.stats.jankPercent),
    formatInvariant("%.1f", screen.stats.p95FrameMs),
    screen.stats.frozenFrames.toString(),
)

internal fun formatMoreScreens(count: Int): String = formatInvariant("+%d more", count)

internal fun formatCounterLine(counter: CounterReading): String = formatInvariant(
    COUNTER_ROW_LAYOUT,
    truncated(counter.name, COUNTER_NAME_WIDTH),
    counter.value,
    peakOf(counter.peakSinceReset.takeIf { it > counter.value }),
)

internal fun formatMoreCounters(count: Int): String = formatInvariant("+%d more counters", count)

private fun truncated(text: String, width: Int): String =
    if (text.length <= width) text else text.take(width - 1) + ELLIPSIS

internal fun formatTiming(choreographerTicksPerSecond: Int, frameBudgetMs: Float): String =
    formatInvariant("ui %d/s · %.1fms", choreographerTicksPerSecond, frameBudgetMs)

internal fun formatJankShort(jankPercent: Float): String = formatInvariant("jank %.1f%%", jankPercent)

internal fun formatWindowSummary(window: FrameWindowStats): String = formatInvariant(
    "$LABEL_WINDOW  jank %4.1f%%  p95 %5.1f  max %5.1f",
    window.jankPercent,
    window.p95FrameMs,
    window.worstFrameMs,
)

internal fun formatSessionLatency(session: IntervalStats): String = formatInvariant(
    "$LABEL_SESSION  p50 %5.1f  p95 %5.1f  p99 %5.1f",
    session.p50FrameMs,
    session.p95FrameMs,
    session.p99FrameMs,
)

internal fun formatSessionTotals(session: IntervalStats): String = formatInvariant(
    "$LABEL_SESSION %df %s jank %.1f%% frz%d run%d",
    session.frames,
    formatDuration(session.durationMs),
    session.jankPercent,
    session.frozenFrames,
    session.maxJankStreak,
)

internal fun formatLostTime(lostTimeMs: Float): String = if (lostTimeMs < MS_PER_SECOND) {
    formatInvariant("$LABEL_LOST_TIME %.0fms", lostTimeMs)
} else {
    formatInvariant("$LABEL_LOST_TIME %.1fs", lostTimeMs / MS_PER_SECOND)
}

internal fun formatMemory(memory: MemoryStats): String = formatInvariant(
    "$LABEL_MEMORY %d/%d%s · nat %d%s MB",
    memory.usedHeapMb,
    memory.maxHeapMb,
    peakOf(memory.peakUsedHeapMb),
    memory.nativeHeapMb,
    peakOf(memory.peakNativeHeapMb),
)

internal fun formatGc(memory: MemoryStats): String =
    formatInvariant("$LABEL_GC x%d · %d ms", memory.gcCount, memory.gcTimeMs)

internal fun formatDroppedReports(droppedReports: Int): String = formatInvariant("$LABEL_DROPPED x%d", droppedReports)

internal fun formatThermal(thermal: ThermalStats): String {
    val level = thermal.level.name.lowercase(Locale.US)
    val headroom = thermal.headroom ?: return formatInvariant("$LABEL_THERMAL %s", level)
    return formatInvariant("$LABEL_THERMAL %s · hr %.2f", level, headroom)
}

internal fun formatProcessLines(process: ProcessStats): List<String> = listOfNotNull(
    joinParts(
        process.cpuPercent?.let { formatInvariant("$LABEL_PROCESS_CPU %.0f%%%s", it, peakOf(process.peakCpuPercent)) },
        process.pssMb?.let { formatInvariant("$LABEL_PSS %d%s MB", it, peakOf(process.peakPssMb)) },
    ),
    joinParts(
        process.threads?.let { formatInvariant("$LABEL_THREADS %d%s", it, peakOf(process.peakThreads)) },
        process.openFiles?.let { formatInvariant("$LABEL_OPEN_FILES %d%s", it, peakOf(process.peakOpenFiles)) },
    ),
)

private fun peakOf(value: Float?): String = value?.let { formatInvariant(" ▲%.0f", it) }.orEmpty()

private fun peakOf(value: Int?): String = value?.let { formatInvariant(" ▲%d", it) }.orEmpty()

private fun joinParts(vararg parts: String?): String? =
    parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(PART_SEPARATOR)

internal fun formatVerdict(verdict: PanelVerdict): String = when (verdict) {
    PanelVerdict.Ok -> LABEL_VERDICT_OK
    is PanelVerdict.Attention -> formatInvariant("⚠ %s %.1f ms", verdict.phaseLabel, verdict.phaseAvgMs)
}

internal fun formatVerdictShort(verdict: PanelVerdict.Attention): String = formatInvariant("⚠%s", verdict.phaseLabel)

internal fun pipeLabel(stage: PipelineStage): String = when (stage) {
    PipelineStage.CPU -> LABEL_PIPE_CPU
    PipelineStage.RENDER -> LABEL_PIPE_RENDER
    PipelineStage.GPU -> LABEL_PIPE_GPU
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / MS_PER_SECOND
    val wholeSeconds = seconds.toLong()
    return if (wholeSeconds < SECONDS_PER_MINUTE) {
        formatInvariant("%.1fs", seconds)
    } else {
        formatInvariant("%dm%02ds", wholeSeconds / SECONDS_PER_MINUTE, wholeSeconds % SECONDS_PER_MINUTE)
    }
}

private const val SECONDS_PER_MINUTE = 60L
