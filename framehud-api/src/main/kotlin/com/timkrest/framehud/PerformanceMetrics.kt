package com.timkrest.framehud

import androidx.compose.runtime.Immutable

@Immutable
public data class PerformanceMetrics(
    val phases: FramePhases = FramePhases.EMPTY,
    val window: FrameWindowStats = FrameWindowStats.EMPTY,
    val session: SessionStats = SessionStats.EMPTY,
    val display: DisplayInfo = DisplayInfo.DEFAULT,
) {
    public companion object {
        public val EMPTY: PerformanceMetrics = PerformanceMetrics()
    }
}
