// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

public enum class BaselineMetric {

    P50_MS,

    P95_MS,

    P99_MS,

    /** 0..100. */
    JANK_PERCENT,

    /** Spread over every frame the interval collected, janky or not. */
    LOST_TIME_MS_PER_FRAME,

    /** Frames over [IntervalStats.FROZEN_FRAME_MS], 0..100. */
    FROZEN_PERCENT,

    ;

    @InternalFrameHudApi
    public val figure: IntervalFigure
        get() = when (this) {
            P50_MS -> IntervalFigure.P50
            P95_MS -> IntervalFigure.P95
            P99_MS -> IntervalFigure.P99
            JANK_PERCENT -> IntervalFigure.JANK_PERCENT
            LOST_TIME_MS_PER_FRAME -> IntervalFigure.LOST_TIME
            FROZEN_PERCENT -> IntervalFigure.FROZEN_FRAMES
        }
}
