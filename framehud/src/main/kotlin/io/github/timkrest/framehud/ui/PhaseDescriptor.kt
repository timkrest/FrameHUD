package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.FramePhases
import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.PipelineStage

/** A frame phase as the panel shows it: the row label, and where to read the timing from. */
internal class PhaseDescriptor(
    val label: String,
    val select: (FramePhases) -> MetricValue,
)

internal fun stagePhases(stage: PipelineStage): List<PhaseDescriptor> = when (stage) {
    PipelineStage.CPU -> CPU_PHASES
    PipelineStage.RENDER -> RENDER_PHASES
    PipelineStage.GPU -> GPU_PHASES
}

/**
 * Includes delay and other, which belong to no stage but can still be the slowest thing on screen.
 *
 * On an emulator the render-thread and GPU phases are left out: they time the host machine, so
 * blaming them would send the reader after someone else's hardware.
 */
internal fun worstPhase(phases: FramePhases, isEmulator: Boolean = false): PhaseDescriptor =
    (if (isEmulator) DEVICE_PHASES else ALL_PHASES).maxBy { it.select(phases).average }

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

private val UNSTAGED_PHASES = listOf(
    PhaseDescriptor(LABEL_DELAY) { it.unknownDelay },
    PhaseDescriptor(LABEL_OTHER) { it.other },
)

private val ALL_PHASES = CPU_PHASES + RENDER_PHASES + GPU_PHASES + UNSTAGED_PHASES

/** What the app itself is responsible for, wherever the frame is actually rendered. */
private val DEVICE_PHASES = CPU_PHASES + UNSTAGED_PHASES
