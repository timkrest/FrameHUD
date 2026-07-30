package io.github.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * A snapshot of frame timing, sampled from `FrameMetrics`. All timings are milliseconds.
 *
 * [input], [animation], [layout] and [draw] happen on the UI thread; [sync], [commandIssue] and
 * [swapBuffers] on the render thread; [gpu] on the GPU. Those stages run in parallel, so under
 * sustained load the frame rate is bound by [bottleneck] rather than by [total].
 */
@Immutable
public data class PerformanceMetrics(
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
    /** Requires API 31+; see [isGpuAvailable]. */
    val gpu: MetricValue = MetricValue.ZERO,
    /** Full frame time, vsync to completion. */
    val total: MetricValue = MetricValue.ZERO,
    /** The part of [total] not attributed to any phase. Normally near zero. */
    val other: MetricValue = MetricValue.ZERO,
    /** [total] minus [frameBudgetMs]. Negative means the frame finished with headroom. */
    val overrun: MetricValue = MetricValue.ZERO,
    /** Timings of the stage with the highest average. */
    val bottleneck: MetricValue = MetricValue.ZERO,
    val bottleneckStage: PipelineStage = PipelineStage.CPU,
    val fps: Int = 0,
    /** Share of janky frames in the window, 0..100. */
    val windowJankPercent: Float = 0f,
    val windowP95FrameMs: Float = 0f,
    val windowWorstFrameMs: Float = 0f,
    val session: SessionStats = SessionStats.EMPTY,
    /** Recent frame times, oldest first. */
    val history: FrameHistory = FrameHistory.EMPTY,
    val isGpuAvailable: Boolean = false,
    val refreshRate: Float = DEFAULT_REFRESH_RATE_HZ,
    /** The system deadline (API 31+), otherwise `1000 / refreshRate`. */
    val frameBudgetMs: Float = MS_PER_SECOND / DEFAULT_REFRESH_RATE_HZ,
) {
    /** [refreshRate], falling back to [DEFAULT_REFRESH_RATE_HZ] when the display reports nothing usable. */
    public val effectiveRefreshRate: Float
        get() = refreshRate.takeIf { it > 0f } ?: DEFAULT_REFRESH_RATE_HZ

    public companion object {
        public const val DEFAULT_REFRESH_RATE_HZ: Float = 60f

        public val EMPTY: PerformanceMetrics = PerformanceMetrics()
    }
}

/** A stage of the rendering pipeline. Stages run in parallel, not in sequence. */
public enum class PipelineStage {
    CPU,
    RENDER,
    GPU,
}

/** One timing, in milliseconds: the latest frame, and the average over the rolling window. */
@Immutable
public data class MetricValue(
    val current: Float = 0f,
    val average: Float = 0f,
    /** Highest value since the last reset. Zero for derived timings, which track no peak. */
    val peak: Float = 0f,
) {
    public companion object {
        public val ZERO: MetricValue = MetricValue()
    }
}

/** Aggregates since the last reset. Time spent in the background is not counted. */
@Immutable
public data class SessionStats(
    val frames: Int,
    val durationMs: Long,
    val p50FrameMs: Float,
    val p95FrameMs: Float,
    val p99FrameMs: Float,
    /** 0..100. */
    val jankPercent: Float,
    /** Frames over 700 ms — the Play Vitals definition. */
    val frozenFrames: Int,
    /** Longest run of consecutive janky frames. */
    val maxJankStreak: Int,
    /** Reports the system dropped before delivery. Above zero, averages and percentiles are undersampled. */
    val droppedReports: Int,
) {
    public companion object {
        public val EMPTY: SessionStats = SessionStats(
            frames = 0,
            durationMs = 0L,
            p50FrameMs = 0f,
            p95FrameMs = 0f,
            p99FrameMs = 0f,
            jankPercent = 0f,
            frozenFrames = 0,
            maxJankStreak = 0,
            droppedReports = 0,
        )
    }
}
