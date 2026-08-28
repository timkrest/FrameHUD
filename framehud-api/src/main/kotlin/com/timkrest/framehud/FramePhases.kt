package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * A frame broken down by pipeline phase, sampled from `FrameMetrics`. All timings are milliseconds.
 *
 * [input], [animation], [layout] and [draw] run on the UI thread; [sync], [commandIssue] and
 * [swapBuffers] run on the render thread; [gpu] runs on the GPU. The stages overlap, so sustained
 * throughput is limited by [bottleneck], not [total].
 */
@Immutable
@ConsistentCopyVisibility
public data class FramePhases private constructor(
    /** Vsync signal to the frame actually starting. Grows when the main thread is busy elsewhere. */
    val unknownDelay: MetricValue,
    val input: MetricValue,
    val animation: MetricValue,
    val layout: MetricValue,
    val draw: MetricValue,
    /** Display list sync to the render thread, plus bitmap upload to GPU textures. */
    val sync: MetricValue,
    val commandIssue: MetricValue,
    /** Waiting for the GPU to finish the previous frame, then presenting this one. */
    val swapBuffers: MetricValue,
    /** Null until `FrameMetrics` reports GPU time: it needs API 31+ and a driver that supports it. */
    val gpu: MetricValue?,
    val total: MetricValue,
    /** [total] minus [FrameWindowStats.frameBudgetMs]. Negative means the frame finished with headroom. */
    val overrun: MetricValue,
) {
    public val cpu: MetricValue = input + animation + layout + draw

    public val render: MetricValue = sync + commandIssue + swapBuffers

    public val unattributed: MetricValue = total - unknownDelay - cpu - render

    private val gpuAverage: Float = gpu?.average ?: 0f

    public val bottleneckStage: PipelineStage = when {
        cpu.average >= render.average && cpu.average >= gpuAverage -> PipelineStage.CPU
        render.average >= gpuAverage -> PipelineStage.RENDER
        else -> PipelineStage.GPU
    }

    public val bottleneck: MetricValue = when (bottleneckStage) {
        PipelineStage.CPU -> cpu
        PipelineStage.RENDER -> render
        PipelineStage.GPU -> gpu?.let { MetricValue.of(current = it.current, average = it.average) } ?: MetricValue.ZERO
    }

    public operator fun get(phase: FramePhase): MetricValue? = when (phase) {
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
        public val EMPTY: FramePhases = of()

        @InternalFrameHudApi
        public fun of(
            unknownDelay: MetricValue = MetricValue.ZERO,
            input: MetricValue = MetricValue.ZERO,
            animation: MetricValue = MetricValue.ZERO,
            layout: MetricValue = MetricValue.ZERO,
            draw: MetricValue = MetricValue.ZERO,
            sync: MetricValue = MetricValue.ZERO,
            commandIssue: MetricValue = MetricValue.ZERO,
            swapBuffers: MetricValue = MetricValue.ZERO,
            gpu: MetricValue? = null,
            total: MetricValue = MetricValue.ZERO,
            overrun: MetricValue = MetricValue.ZERO,
        ): FramePhases = FramePhases(
            unknownDelay = unknownDelay,
            input = input,
            animation = animation,
            layout = layout,
            draw = draw,
            sync = sync,
            commandIssue = commandIssue,
            swapBuffers = swapBuffers,
            gpu = gpu,
            total = total,
            overrun = overrun,
        )
    }
}

/** A stage of the rendering pipeline. Stages run in parallel, not in sequence. */
public enum class PipelineStage {
    CPU,
    RENDER,
    GPU,
}
