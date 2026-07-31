package io.github.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * A frame broken down by pipeline phase, sampled from `FrameMetrics`. All timings are milliseconds.
 *
 * [input], [animation], [layout] and [draw] happen on the UI thread; [sync], [commandIssue] and
 * [swapBuffers] on the render thread; [gpu] on the GPU. Those three stages run in parallel, so
 * under sustained load the frame rate is bound by [bottleneck] rather than by [total].
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
    val isGpuAvailable: Boolean = false,
) {
    public val cpu: MetricValue get() = input + animation + layout + draw

    public val render: MetricValue get() = sync + commandIssue + swapBuffers

    /** Unattributed remainder of [total]. Normally near zero. */
    public val other: MetricValue get() = total - unknownDelay - cpu - render

    /** Stage with the highest average. */
    public val bottleneckStage: PipelineStage
        get() {
            val cpuAvg = cpu.average
            val renderAvg = render.average
            return when {
                cpuAvg >= renderAvg && cpuAvg >= gpu.average -> PipelineStage.CPU
                renderAvg >= gpu.average -> PipelineStage.RENDER
                else -> PipelineStage.GPU
            }
        }

    public val bottleneck: MetricValue
        get() = when (bottleneckStage) {
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
