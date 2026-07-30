package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.PipelineStage

/** A frame phase as the panel shows it: the row label, and where to read the timing from. */
internal class PhaseDescriptor(
    val label: String,
    val select: (PerformanceMetrics) -> MetricValue,
)

internal fun stagePhases(stage: PipelineStage): List<PhaseDescriptor> = when (stage) {
    PipelineStage.CPU -> CPU_PHASES
    PipelineStage.RENDER -> RENDER_PHASES
    PipelineStage.GPU -> GPU_PHASES
}

/** Includes delay and other, which belong to no stage but can still be the slowest thing on screen. */
internal fun worstPhase(metrics: PerformanceMetrics): PhaseDescriptor =
    ALL_PHASES.maxBy { it.select(metrics).average }

private val CPU_PHASES = listOf(
    PhaseDescriptor(LABEL_INPUT) { it.input },
    PhaseDescriptor(LABEL_ANIMATION) { it.animation },
    PhaseDescriptor(LABEL_LAYOUT) { it.layout },
    PhaseDescriptor(LABEL_DRAW) { it.draw },
)

private val RENDER_PHASES = listOf(
    PhaseDescriptor(LABEL_SYNC) { it.sync },
    PhaseDescriptor(LABEL_COMMAND) { it.commandIssue },
    PhaseDescriptor(LABEL_SWAP) { it.swapBuffers },
)

private val GPU_PHASES = listOf(PhaseDescriptor(LABEL_GPU) { it.gpu })

private val ALL_PHASES = CPU_PHASES + RENDER_PHASES + GPU_PHASES + listOf(
    PhaseDescriptor(LABEL_DELAY) { it.unknownDelay },
    PhaseDescriptor(LABEL_OTHER) { it.other },
)
