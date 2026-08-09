package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.SessionStats
import java.util.Locale

/** Infinite thresholds are ignored. */
public data class JankThresholds(
    val maxJankPercent: Float = JankSeverity.WARNING_JANK_PERCENT,
    val maxFrozenFrames: Int = 0,
    val maxP95FrameMs: Float = Float.POSITIVE_INFINITY,
    val maxDroppedReports: Int = 0,
)

internal fun JankThresholds.violations(stats: SessionStats): List<String> = buildList {
    if (stats.jankPercent > maxJankPercent) {
        add(format("jank %.1f%% over %.1f%%", stats.jankPercent, maxJankPercent))
    }
    if (stats.frozenFrames > maxFrozenFrames) {
        add("${stats.frozenFrames} frozen frame(s), allowed $maxFrozenFrames")
    }
    if (stats.p95FrameMs > maxP95FrameMs) {
        add(format("p95 %.1f ms over %.1f ms", stats.p95FrameMs, maxP95FrameMs))
    }
    if (stats.droppedReports > maxDroppedReports) {
        add(
            "${stats.droppedReports} dropped report(s), allowed $maxDroppedReports — " +
                "every figure above is undersampled",
        )
    }
}

private fun format(template: String, vararg args: Any): String = String.format(Locale.US, template, *args)
