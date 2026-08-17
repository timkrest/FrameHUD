package com.timkrest.framehud

public enum class BaselineMetric {

    P50_MS,

    P95_MS,

    P99_MS,

    /** 0..100. */
    JANK_PERCENT,

    /** Spread over every frame the interval collected, janky or not. */
    LOST_TIME_MS_PER_FRAME,

    /** Frames over [SessionStats.FROZEN_FRAME_MS], 0..100. */
    FROZEN_PERCENT,

    ;

    @InternalFrameHudApi
    public val confidenceMetric: MeasuredMetric
        get() = when (this) {
            P50_MS -> MeasuredMetric.P50
            P95_MS -> MeasuredMetric.P95
            P99_MS -> MeasuredMetric.P99
            JANK_PERCENT -> MeasuredMetric.JANK_PERCENT
            LOST_TIME_MS_PER_FRAME -> MeasuredMetric.LOST_TIME
            FROZEN_PERCENT -> MeasuredMetric.FROZEN_FRAMES
        }
}
