package io.github.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * A snapshot of how the app is rendering: one frame broken down by [phases], the [window] of recent
 * frames around it, the [session] since the last reset, and what the [display] asks of each frame.
 */
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
