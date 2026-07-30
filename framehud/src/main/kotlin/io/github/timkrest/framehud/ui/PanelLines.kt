package io.github.timkrest.framehud.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.timkrest.framehud.MemoryStats
import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.PipelineStage
import io.github.timkrest.framehud.ThermalLevel
import io.github.timkrest.framehud.ThermalStats

/** One rendered line. [loadFraction] fills the row behind the text as a bar, 0..1. */
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
): PanelLines {
    val verdict = panelVerdict(metrics)
    val rowContext = MetricRowContext(
        frameBudgetMs = metrics.frameBudgetMs,
        attentionLabel = (verdict as? PanelVerdict.Attention)?.phaseLabel,
    )
    return buildList {
        addText(formatVerdict(verdict), verdictColor(verdict))
        addText(CPU_COLUMNS_HEADER_LINE, TextHeader)
        stagePhases(PipelineStage.CPU).forEach { phase ->
            addMetric(phase.label, phase.select(metrics), rowContext)
        }
        addStage(header = LABEL_RENDER_SECTION, stage = PipelineStage.RENDER, metrics = metrics, rowContext = rowContext)
        if (metrics.isGpuAvailable) {
            addStage(header = LABEL_GPU_SECTION, stage = PipelineStage.GPU, metrics = metrics, rowContext = rowContext)
        } else {
            addText(LABEL_GPU_SECTION, TextHeader)
            addText(GPU_UNAVAILABLE_LINE, TextHeader)
        }
        addMetric(LABEL_DELAY, metrics.unknownDelay, rowContext, separatorAbove = true)
        addMetric(LABEL_OTHER, metrics.other, rowContext)
        addMetric(LABEL_TOTAL, metrics.total, rowContext, MetricRowKind.TOTAL)
        addMetric(LABEL_OVERRUN, metrics.overrun, rowContext)
        addMetric(pipeLabel(metrics.bottleneckStage), metrics.bottleneck, rowContext)
        addText(formatWindowSummary(metrics), jankColor(metrics.windowJankPercent), separatorAbove = true)
        addText(formatSessionLatency(metrics.session), TextNormal)
        addText(formatSessionTotals(metrics.session), jankColor(metrics.session.jankPercent))
        if (metrics.session.droppedReports > 0) {
            addText(formatDroppedReports(metrics.session.droppedReports), TextCaution)
        }
        addText(formatMemory(memory), TextHeader)
        addText(formatGc(memory), TextHeader)
        if (thermal.level != ThermalLevel.UNKNOWN) {
            addText(formatThermal(thermal), thermalColor(thermal.level))
        }
    }.let(::PanelLines)
}

internal fun buildCollapsedLine(metrics: PerformanceMetrics): AnnotatedString = buildAnnotatedString {
    appendColored(text = formatFps(metrics.fps), color = fpsColor(metrics))
    append(COLLAPSED_SEPARATOR)
    appendColored(
        text = formatJankShort(metrics.windowJankPercent),
        color = jankColor(metrics.windowJankPercent),
    )
    val verdict = panelVerdict(metrics)
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
    metrics: PerformanceMetrics,
    rowContext: MetricRowContext,
) {
    addText(header, TextHeader)
    stagePhases(stage).forEach { phase ->
        addMetric(label = phase.label, value = phase.select(metrics), rowContext = rowContext)
    }
}

private fun MutableList<PanelLine>.addMetric(
    label: String,
    value: MetricValue,
    rowContext: MetricRowContext,
    kind: MetricRowKind = MetricRowKind.PHASE,
    separatorAbove: Boolean = false,
) {
    val isAttention = label == rowContext.attentionLabel
    val text = formatMetricLine(label = label, value = value)
    add(
        PanelLine(
            text = if (isAttention) text + ATTENTION_MARKER else text,
            color = metricRowColor(
                valueMs = value.average,
                frameBudgetMs = rowContext.frameBudgetMs,
                kind = kind,
                isAttention = isAttention,
            ),
            loadFraction = rowContext.loadFractionOf(value),
            hasSeparatorAbove = separatorAbove,
        ),
    )
}

private fun MutableList<PanelLine>.addText(text: String, color: Color, separatorAbove: Boolean = false) {
    add(PanelLine(text = text, color = color, loadFraction = 0f, hasSeparatorAbove = separatorAbove))
}
