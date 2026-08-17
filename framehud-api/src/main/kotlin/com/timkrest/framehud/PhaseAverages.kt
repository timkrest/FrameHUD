package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * Milliseconds an average frame of one interval spent in each phase, the phases [FramePhases]
 * describes. That type breaks down the last frames drawn; this breaks down every frame a session,
 * a screen or a mark collected.
 *
 * The stages overlap, so [total] is not their sum and sustained throughput is limited by
 * [bottleneckStage].
 */
@Immutable
public data class PhaseAverages(
    /** Vsync signal to the frame actually starting. Grows when the main thread is busy elsewhere. */
    val unknownDelay: Float = 0f,
    val input: Float = 0f,
    val animation: Float = 0f,
    val layout: Float = 0f,
    val draw: Float = 0f,
    /** Display list sync to the render thread, plus bitmap upload to GPU textures. */
    val sync: Float = 0f,
    val commandIssue: Float = 0f,
    /** Waiting for the GPU to finish the previous frame, then presenting this one. */
    val swapBuffers: Float = 0f,
    /** Null until `FrameMetrics` reports GPU time: it needs API 31+ and a driver that supports it. */
    val gpu: Float? = null,
    val total: Float = 0f,
) {
    public val cpu: Float = input + animation + layout + draw

    public val render: Float = sync + commandIssue + swapBuffers

    public val unattributed: Float = (total - unknownDelay - cpu - render).coerceAtLeast(0f)

    public val bottleneckStage: PipelineStage = when {
        cpu >= render && cpu >= (gpu ?: 0f) -> PipelineStage.CPU
        render >= (gpu ?: 0f) -> PipelineStage.RENDER
        else -> PipelineStage.GPU
    }

    public val bottleneck: Float = when (bottleneckStage) {
        PipelineStage.CPU -> cpu
        PipelineStage.RENDER -> render
        PipelineStage.GPU -> gpu ?: 0f
    }

    public operator fun get(phase: FramePhase): Float? = when (phase) {
        FramePhase.UNKNOWN_DELAY -> unknownDelay
        FramePhase.INPUT -> input
        FramePhase.ANIMATION -> animation
        FramePhase.LAYOUT -> layout
        FramePhase.DRAW -> draw
        FramePhase.SYNC -> sync
        FramePhase.COMMAND_ISSUE -> commandIssue
        FramePhase.SWAP_BUFFERS -> swapBuffers
        FramePhase.GPU -> gpu
        FramePhase.TOTAL -> total
    }

    public companion object {
        public val EMPTY: PhaseAverages = PhaseAverages()

        @InternalFrameHudApi
        public fun of(average: (FramePhase) -> Float?): PhaseAverages {
            fun ms(phase: FramePhase) = average(phase) ?: 0f
            return PhaseAverages(
                unknownDelay = ms(FramePhase.UNKNOWN_DELAY),
                input = ms(FramePhase.INPUT),
                animation = ms(FramePhase.ANIMATION),
                layout = ms(FramePhase.LAYOUT),
                draw = ms(FramePhase.DRAW),
                sync = ms(FramePhase.SYNC),
                commandIssue = ms(FramePhase.COMMAND_ISSUE),
                swapBuffers = ms(FramePhase.SWAP_BUFFERS),
                gpu = average(FramePhase.GPU),
                total = ms(FramePhase.TOTAL),
            )
        }
    }
}
