package com.timkrest.framehud.sample.readouts

import androidx.compose.runtime.Composable
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleLine
import com.timkrest.framehud.sample.ui.SampleNote
import com.timkrest.framehud.sample.ui.formatMs
import com.timkrest.framehud.sample.ui.formatPercent
import com.timkrest.framehud.sample.ui.formatSeconds
import java.util.Locale

@Composable
fun IntervalStatsCard(title: String, stats: IntervalStats) {
    SampleCard(title = title) {
        if (stats.frames == 0) {
            SampleNote(text = "No frame has been collected yet.")
        } else {
            SampleLine(label = "frames", value = stats.frames.toString())
            SampleLine(label = "duration", value = formatSeconds(stats.durationMs))
            SampleLine(label = "p50 / p95 / p99", value = stats.percentiles())
            SampleLine(label = "jank", value = formatPercent(stats.jankPercent))
            SampleLine(label = "lost time", value = formatMs(stats.lostTimeMs))
            SampleLine(label = "frozen frames", value = stats.frozenFrames.toString())
            SampleLine(label = "longest jank streak", value = stats.maxJankStreak.toString())
        }
        SampleLine(label = "dropped reports", value = stats.droppedReports.toString())
        stats.confidence.issues.forEach { issue -> SampleNote(text = issue.summary) }
    }
}

private fun IntervalStats.percentiles(): String =
    String.format(Locale.US, "%.1f / %.1f / %.1f ms", p50FrameMs, p95FrameMs, p99FrameMs)
