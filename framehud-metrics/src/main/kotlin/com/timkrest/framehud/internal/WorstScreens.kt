package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport

internal fun List<IntervalReport>.worstScreensFirst(): List<IntervalReport> =
    filter { it.id is IntervalId.Screen }.sortedWith(WORST_FIRST)

private val IntervalReport.hasTooFewFramesToRankByP95: Boolean
    get() = stats.frames < ConfidenceIssue.ShortSample.MIN_FRAMES_P95

private val WORST_FIRST =
    compareBy<IntervalReport> { it.hasTooFewFramesToRankByP95 }
        .thenByDescending { it.stats.frozenFrames }
        .thenByDescending { it.stats.jankPercent }
        .thenByDescending { it.stats.p95FrameMs }
