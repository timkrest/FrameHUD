package com.timkrest.framehud.ui

import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PipelineStage

internal class PhaseDescriptor(
    val label: String,
    val select: (FramePhases) -> MetricValue,
)

internal fun stagePhases(stage: PipelineStage): List<PhaseDescriptor> = when (stage) {
    PipelineStage.CPU -> CPU_PHASES
    PipelineStage.RENDER -> RENDER_PHASES
    PipelineStage.GPU -> GPU_PHASES
}

internal fun worstPhase(phases: FramePhases, isEmulator: Boolean = false): PhaseDescriptor =
    (if (isEmulator) APP_OWNED_PHASES else ALL_PHASES).maxBy { it.select(phases).average }

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

private val GPU_PHASES = listOf(PhaseDescriptor(LABEL_GPU) { it.gpu ?: MetricValue.ZERO })

private val UNSTAGED_PHASES = listOf(
    PhaseDescriptor(LABEL_DELAY) { it.unknownDelay },
    PhaseDescriptor(LABEL_OTHER) { it.unattributed },
)

private val ALL_PHASES = CPU_PHASES + RENDER_PHASES + GPU_PHASES + UNSTAGED_PHASES

private val APP_OWNED_PHASES = CPU_PHASES + UNSTAGED_PHASES
