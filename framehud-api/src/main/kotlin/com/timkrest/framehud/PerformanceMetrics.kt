package com.timkrest.framehud

import androidx.compose.runtime.Immutable

@Immutable
@ConsistentCopyVisibility
public data class PerformanceMetrics private constructor(
    val phases: FramePhases,
    val window: FrameWindowStats,
    val session: IntervalStats,
    val display: DisplayInfo,
) {
    public companion object {
        public val EMPTY: PerformanceMetrics = of()

        @InternalFrameHudApi
        public fun of(
            phases: FramePhases = FramePhases.EMPTY,
            window: FrameWindowStats = FrameWindowStats.EMPTY,
            session: IntervalStats = IntervalStats.EMPTY,
            display: DisplayInfo = DisplayInfo.DEFAULT,
        ): PerformanceMetrics = PerformanceMetrics(
            phases = phases,
            window = window,
            session = session,
            display = display,
        )
    }
}

@InternalFrameHudApi
public fun PerformanceMetrics.withSession(session: IntervalStats): PerformanceMetrics =
    PerformanceMetrics.of(phases = phases, window = window, session = session, display = display)
