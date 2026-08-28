package com.timkrest.framehud.sample.session

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.Incident
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.RecordedRun

@Immutable
data class SessionReport(
    val stats: IntervalStats = IntervalStats.EMPTY,
    val intervals: List<IntervalReport> = emptyList(),
    val worstScreens: List<IntervalReport> = emptyList(),
    val incidents: List<Incident> = emptyList(),
    val comparison: BaselineComparison? = null,
    val pastRuns: List<RecordedRun> = emptyList(),
) {
    companion object {
        suspend fun read(): SessionReport = SessionReport(
            stats = FrameHud.sessionStats(),
            intervals = FrameHud.intervals(),
            worstScreens = FrameHud.screens(),
            incidents = FrameHud.incidents(),
            comparison = FrameHud.compareWithBaseline(),
            pastRuns = FrameHud.history(),
        )
    }
}
