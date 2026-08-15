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
    val unknownDelay: Float = 0f,
    val input: Float = 0f,
    val animation: Float = 0f,
    val layout: Float = 0f,
    val draw: Float = 0f,
    val sync: Float = 0f,
    val commandIssue: Float = 0f,
    val swapBuffers: Float = 0f,
    val gpu: Float = 0f,
    val total: Float = 0f,
    /**
     * True once `FrameMetrics` reported a positive GPU duration, which needs API 31+ and a driver
     * that supports it. While false, [gpu] means "not reported" rather than "no GPU time".
     */
    val isGpuAvailable: Boolean = false,
) {
    public val cpu: Float = input + animation + layout + draw

    public val render: Float = sync + commandIssue + swapBuffers

    public val other: Float = (total - unknownDelay - cpu - render).coerceAtLeast(0f)

    public val bottleneckStage: PipelineStage = when {
        cpu >= render && cpu >= gpu -> PipelineStage.CPU
        render >= gpu -> PipelineStage.RENDER
        else -> PipelineStage.GPU
    }

    public companion object {
        public val EMPTY: PhaseAverages = PhaseAverages()
    }
}
