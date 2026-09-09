// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.timkrest.framehud.MetricValue

@Immutable
internal data class PanelLine(
    val text: String,
    val color: Color,
    val loadFraction: Float,
    val hasSeparatorAbove: Boolean,
)

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

internal fun textRow(text: String, color: Color): PanelLine =
    PanelLine(text = text, color = color, loadFraction = 0f, hasSeparatorAbove = false)

internal fun metricRow(
    label: String,
    value: MetricValue,
    rowContext: MetricRowContext,
    kind: MetricRowKind = MetricRowKind.PHASE,
    dimmed: Boolean = false,
): PanelLine {
    val isAttention = label == rowContext.attentionLabel && !dimmed
    val text = formatMetricLine(label = label, value = value)
    return PanelLine(
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
        hasSeparatorAbove = false,
    )
}

internal fun List<PanelLine>.separatedFromRowsAbove(): List<PanelLine> =
    mapIndexed { index, line -> if (index == 0) line.copy(hasSeparatorAbove = true) else line }

internal fun PanelLines.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    values.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        appendColored(text = line.text, color = line.color)
    }
}

internal fun AnnotatedString.Builder.appendColored(text: String, color: Color) {
    withStyle(SpanStyle(color = color)) { append(text) }
}
