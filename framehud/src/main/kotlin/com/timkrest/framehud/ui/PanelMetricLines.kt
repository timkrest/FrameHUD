// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats

internal fun buildPanelLines(
    metrics: PerformanceMetrics,
    memory: MemoryStats,
    thermal: ThermalStats,
    process: ProcessStats = ProcessStats.EMPTY,
    counters: List<CounterReading> = emptyList(),
    isEmulator: Boolean = false,
    detail: PanelDetail = PanelDetail.FULL,
): PanelLines {
    val rows = MetricRows(metrics = metrics, isEmulator = isEmulator)
    return PanelLines(
        when (detail) {
            PanelDetail.FULL -> rows.everyStage() +
                sessionRows(metrics.session, memory, thermal, process, counters)

            PanelDetail.FRAMES -> rows.stagesTheAppOwns()
            PanelDetail.MINI -> emptyList()
        },
    )
}

private class MetricRows(metrics: PerformanceMetrics, private val isEmulator: Boolean) {

    private val phases = metrics.phases

    private val window = metrics.window

    private val verdict = panelVerdict(
        phases = phases,
        jankPercent = window.jankPercent,
        isEmulator = isEmulator,
    )

    private val rowContext = MetricRowContext(
        frameBudgetMs = window.frameBudgetMs,
        attentionLabel = (verdict as? PanelVerdict.Attention)?.phaseLabel,
    )

    fun stagesTheAppOwns(): List<PanelLine> = verdictAndCpu() + delayRow() + totals() + windowSummary()

    fun everyStage(): List<PanelLine> =
        verdictAndCpu() + renderRows() + gpuRows() + delayRow() + totals() + pipelineRows() + windowSummary()

    private fun verdictAndCpu(): List<PanelLine> = listOf(
        textRow(formatVerdict(verdict), verdictColor(verdict)),
        textRow(CPU_COLUMNS_HEADER_LINE, TextHeader),
    ) + stageRows(PipelineStage.CPU)

    private fun renderRows(): List<PanelLine> =
        listOf(sectionRow(LABEL_RENDER_SECTION)) + stageRows(PipelineStage.RENDER, dimmed = isEmulator)

    private fun gpuRows(): List<PanelLine> = listOf(sectionRow(LABEL_GPU_SECTION)) + when (phases.gpu) {
        null -> listOf(textRow(GPU_UNAVAILABLE_LINE, TextHeader))
        else -> stageRows(PipelineStage.GPU, dimmed = isEmulator)
    }

    private fun delayRow(): List<PanelLine> =
        listOf(metricRow(LABEL_DELAY, phases.unknownDelay, rowContext)).separatedFromRowsAbove()

    private fun totals(): List<PanelLine> = listOf(
        metricRow(LABEL_OTHER, phases.unattributed, rowContext),
        metricRow(LABEL_TOTAL, phases.total, rowContext, MetricRowKind.TOTAL),
    )

    private fun pipelineRows(): List<PanelLine> = listOf(
        metricRow(LABEL_OVERRUN, phases.overrun, rowContext, MetricRowKind.OVERRUN),
        metricRow(
            label = pipeLabel(phases.bottleneckStage),
            value = phases.bottleneck,
            rowContext = rowContext,
            dimmed = isEmulator && phases.bottleneckStage != PipelineStage.CPU,
        ),
    )

    private fun windowSummary(): List<PanelLine> =
        listOf(textRow(formatWindowSummary(window), jankColor(window.jankPercent))).separatedFromRowsAbove()

    private fun stageRows(stage: PipelineStage, dimmed: Boolean = false): List<PanelLine> =
        stagePhases(stage).map { phase ->
            metricRow(phase.label, phase.select(phases), rowContext, dimmed = dimmed)
        }

    private fun sectionRow(label: String): PanelLine =
        textRow(if (isEmulator) label + LABEL_HOST_SECTION else label, TextHeader)
}

private fun sessionRows(
    session: IntervalStats,
    memory: MemoryStats,
    thermal: ThermalStats,
    process: ProcessStats,
    counters: List<CounterReading>,
): List<PanelLine> = listOfNotNull(
    textRow(formatSessionLatency(session), TextNormal),
    textRow(formatSessionTotals(session), jankColor(session.jankPercent)),
    session.lostTimeMs.takeIf { it > 0f }?.let { textRow(formatLostTime(it), jankColor(session.jankPercent)) },
    session.droppedReports.takeIf { it > 0 }?.let { textRow(formatDroppedReports(it), TextCaution) },
    textRow(formatMemory(memory), TextHeader),
    textRow(formatGc(memory), TextHeader),
    thermal.takeIf { it.level != ThermalLevel.UNKNOWN }?.let { textRow(formatThermal(it), thermalColor(it.level)) },
) + formatProcessLines(process).map { textRow(it, TextHeader) } + counterRows(counters)

private fun counterRows(counters: List<CounterReading>): List<PanelLine> {
    val listed = counters.take(LISTED_COUNTERS).map { textRow(formatCounterLine(it), TextHeader) }
    val hidden = (counters.size - LISTED_COUNTERS).takeIf { it > 0 }
    return (listed + listOfNotNull(hidden?.let { textRow(formatMoreCounters(it), TextHeader) }))
        .separatedFromRowsAbove()
}

private const val LISTED_COUNTERS = 4
