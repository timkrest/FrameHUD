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
public data class FramePhases(
    /** Vsync signal to the frame actually starting. Grows when the main thread is busy elsewhere. */
    val unknownDelay: MetricValue = MetricValue.ZERO,
    val input: MetricValue = MetricValue.ZERO,
    val animation: MetricValue = MetricValue.ZERO,
    val layout: MetricValue = MetricValue.ZERO,
    val draw: MetricValue = MetricValue.ZERO,
    /** Display list sync to the render thread, plus bitmap upload to GPU textures. */
    val sync: MetricValue = MetricValue.ZERO,
    val commandIssue: MetricValue = MetricValue.ZERO,
    /** Waiting for the GPU to finish the previous frame, then presenting this one. */
    val swapBuffers: MetricValue = MetricValue.ZERO,
    /** Null until `FrameMetrics` reports GPU time: it needs API 31+ and a driver that supports it. */
    val gpu: MetricValue? = null,
    val total: MetricValue = MetricValue.ZERO,
    /** [total] minus [DisplayInfo.frameBudgetMs]. Negative means the frame finished with headroom. */
    val overrun: MetricValue = MetricValue.ZERO,
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
        PipelineStage.GPU -> gpu?.copy(peak = null) ?: MetricValue.ZERO
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
        public val EMPTY: FramePhases = FramePhases()
    }
}

/** A stage of the rendering pipeline. Stages run in parallel, not in sequence. */
public enum class PipelineStage {
    CPU,
    RENDER,
    GPU,
}
