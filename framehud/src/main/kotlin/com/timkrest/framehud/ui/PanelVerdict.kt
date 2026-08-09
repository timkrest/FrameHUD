package com.timkrest.framehud.ui

import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.JankSeverity

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
