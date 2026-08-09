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
    /** Translating draw commands into GPU calls. */
    val commandIssue: MetricValue = MetricValue.ZERO,
    /** Waiting for the GPU to finish the previous frame, then presenting this one. */
    val swapBuffers: MetricValue = MetricValue.ZERO,
    /** Requires API 31+ and a driver that reports it; see [isGpuAvailable]. */
    val gpu: MetricValue = MetricValue.ZERO,
    /** Full frame time, vsync to completion. */
    val total: MetricValue = MetricValue.ZERO,
    /** [total] minus [DisplayInfo.frameBudgetMs]. Negative means the frame finished with headroom. */
    val overrun: MetricValue = MetricValue.ZERO,
    /** True after `FrameMetrics` reports a positive GPU duration. */
    val isGpuAvailable: Boolean = false,
) {
    public val cpu: MetricValue = input + animation + layout + draw

    public val render: MetricValue = sync + commandIssue + swapBuffers

    /** Unattributed remainder of [total]. Normally near zero. */
    public val other: MetricValue = total - unknownDelay - cpu - render

    public val bottleneckStage: PipelineStage = when {
        cpu.average >= render.average && cpu.average >= gpu.average -> PipelineStage.CPU
        render.average >= gpu.average -> PipelineStage.RENDER
        else -> PipelineStage.GPU
    }

    /** Timing for [bottleneckStage]. Its [MetricValue.peak] is always null. */
    public val bottleneck: MetricValue = when (bottleneckStage) {
        PipelineStage.CPU -> cpu
        PipelineStage.RENDER -> render
        PipelineStage.GPU -> gpu.copy(peak = null)
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
