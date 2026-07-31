package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.FramePhases
import io.github.timkrest.framehud.JankSeverity

/** The panel's top line: either nothing to do, or the one phase worth looking at. */
internal sealed interface PanelVerdict {
    data object Ok : PanelVerdict

    data class Attention(
        val phaseLabel: String,
        val phaseAvgMs: Float,
        val isSevere: Boolean,
    ) : PanelVerdict
}

internal fun panelVerdict(phases: FramePhases, jankPercent: Float, isEmulator: Boolean = false): PanelVerdict {
    val severity = JankSeverity.of(jankPercent)
    if (severity == JankSeverity.NONE) return PanelVerdict.Ok
    val worst = worstPhase(phases, isEmulator)
    return PanelVerdict.Attention(
        phaseLabel = worst.label,
        phaseAvgMs = worst.select(phases).average,
        isSevere = severity == JankSeverity.SEVERE,
    )
}
