package com.timkrest.framehud.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats

@Immutable
internal data class PanelLine(
    val text: String,
    val color: Color,
    val loadFraction: Float,
    val hasSeparatorAbove: Boolean,
)

/** Wraps the list so the panel composables stay skippable. */
@Immutable
internal data class PanelLines(val values: List<PanelLine>)

internal enum class MetricRowKind {
    PHASE,
    TOTAL,
    OVERRUN,
}

@Immutable
internal data class MetricRowContext(
    val frameBudgetMs: Float,
    val attentionLabel: String?,
) {
    fun loadFractionOf(value: MetricValue): Float =
        if (frameBudgetMs > 0f) (value.average / frameBudgetMs).coerceIn(0f, 1f) else 0f
}

internal fun buildPanelLines(
    metrics: PerformanceMetrics,
    memory: MemoryStats,
    thermal: ThermalStats,
    isEmulator: Boolean = false,
): PanelLines {
    val phases = metrics.phases
    val window = metrics.window
    val session = metrics.session
    val verdict = panelVerdict(phases = phases, jankPercent = window.jankPercent, isEmulator = isEmulator)
    val rowContext = MetricRowContext(
        frameBudgetMs = metrics.display.frameBudgetMs,
        attentionLabel = (verdict as? PanelVerdict.Attention)?.phaseLabel,
    )
    return buildList {
        addText(formatVerdict(verdict), verdictColor(verdict))
        addText(CPU_COLUMNS_HEADER_LINE, TextHeader)
        stagePhases(PipelineStage.CPU).forEach { phase ->
            addMetric(phase.label, phase.select(phases), rowContext)
        }
        addStage(LABEL_RENDER_SECTION, PipelineStage.RENDER, phases, rowContext, dimmed = isEmulator)
        if (phases.isGpuAvailable) {
            addStage(LABEL_GPU_SECTION, PipelineStage.GPU, phases, rowContext, dimmed = isEmulator)
        } else {
            addText(sectionHeader(LABEL_GPU_SECTION, isHostMeasured = isEmulator), TextHeader)
            addText(GPU_UNAVAILABLE_LINE, TextHeader)
        }
        addMetric(LABEL_DELAY, phases.unknownDelay, rowContext, separatorAbove = true)
        addMetric(LABEL_OTHER, phases.other, rowContext)
        addMetric(LABEL_TOTAL, phases.total, rowContext, MetricRowKind.TOTAL)
        addMetric(LABEL_OVERRUN, phases.overrun, rowContext, MetricRowKind.OVERRUN)
        val bottleneckStage = phases.bottleneckStage
        addMetric(
            label = pipeLabel(bottleneckStage),
            value = phases.bottleneck,
            rowContext = rowContext,
            dimmed = isEmulator && bottleneckStage != PipelineStage.CPU,
        )
        addText(formatWindowSummary(window), jankColor(window.jankPercent), separatorAbove = true)
        addText(formatSessionLatency(session), TextNormal)
        addText(formatSessionTotals(session), jankColor(session.jankPercent))
        if (session.droppedReports > 0) {
            addText(formatDroppedReports(session.droppedReports), TextCaution)
        }
        addText(formatMemory(memory), TextHeader)
        addText(formatGc(memory), TextHeader)
        if (thermal.level != ThermalLevel.UNKNOWN) {
            addText(formatThermal(thermal), thermalColor(thermal.level))
        }
    }.let(::PanelLines)
}

internal fun buildCollapsedLine(
    metrics: PerformanceMetrics,
    isEmulator: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    val window = metrics.window
    appendColored(
        text = formatFps(window.fps),
        color = fpsColor(fps = window.fps, refreshRateHz = metrics.display.refreshRateHz),
    )
    append(COLLAPSED_SEPARATOR)
    appendColored(text = formatJankShort(window.jankPercent), color = jankColor(window.jankPercent))
    val verdict = panelVerdict(phases = metrics.phases, jankPercent = window.jankPercent, isEmulator = isEmulator)
    if (verdict is PanelVerdict.Attention) {
        append(COLLAPSED_SEPARATOR)
        appendColored(text = formatVerdictShort(verdict), color = verdictColor(verdict))
    }
}

/** One string for the whole block, so the panel lays out its text in a single pass. */
internal fun PanelLines.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    values.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        appendColored(text = line.text, color = line.color)
    }
}

private fun AnnotatedString.Builder.appendColored(text: String, color: Color) {
    withStyle(SpanStyle(color = color)) { append(text) }
}

private fun MutableList<PanelLine>.addStage(
    header: String,
    stage: PipelineStage,
    phases: FramePhases,
    rowContext: MetricRowContext,
    dimmed: Boolean = false,
) {
    addText(sectionHeader(header, isHostMeasured = dimmed), TextHeader)
    stagePhases(stage).forEach { phase ->
        addMetric(phase.label, phase.select(phases), rowContext, dimmed = dimmed)
    }
}

private fun sectionHeader(label: String, isHostMeasured: Boolean): String =
    if (isHostMeasured) label + LABEL_HOST_SECTION else label

private fun MutableList<PanelLine>.addMetric(
    label: String,
    value: MetricValue,
    rowContext: MetricRowContext,
    kind: MetricRowKind = MetricRowKind.PHASE,
    separatorAbove: Boolean = false,
    dimmed: Boolean = false,
) {
    val isAttention = label == rowContext.attentionLabel && !dimmed
    val text = formatMetricLine(label = label, value = value)
    add(
        PanelLine(
            text = if (isAttention) text + ATTENTION_MARKER else text,
            color = if (dimmed) {
                TextDimmed
            } else {
                metricRowColor(
                    valueMs = value.average,
                    frameBudgetMs = rowContext.frameBudgetMs,
                    kind = kind,
                    isAttention = isAttention,
                )
            },
            loadFraction = rowContext.loadFractionOf(value),
            hasSeparatorAbove = separatorAbove,
        ),
    )
}

private fun MutableList<PanelLine>.addText(text: String, color: Color, separatorAbove: Boolean = false) {
    add(PanelLine(text = text, color = color, loadFraction = 0f, hasSeparatorAbove = separatorAbove))
}
