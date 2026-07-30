package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.JankSeverity
import io.github.timkrest.framehud.PerformanceMetrics

/** The panel's top line: either nothing to do, or the one phase worth looking at. */
internal sealed interface PanelVerdict {
    data object Ok : PanelVerdict

    data class Attention(
        val phaseLabel: String,
        val phaseAvgMs: Float,
        val isSevere: Boolean,
    ) : PanelVerdict
}

internal fun panelVerdict(metrics: PerformanceMetrics): PanelVerdict {
    val severity = JankSeverity.of(metrics.windowJankPercent)
    if (severity == JankSeverity.NONE) return PanelVerdict.Ok
    val worst = worstPhase(metrics)
    return PanelVerdict.Attention(
        phaseLabel = worst.label,
        phaseAvgMs = worst.select(metrics).average,
        isSevere = severity == JankSeverity.SEVERE,
    )
}
