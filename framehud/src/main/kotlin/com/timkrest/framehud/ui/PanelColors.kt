package com.timkrest.framehud.ui

import androidx.compose.ui.graphics.Color
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.ThermalLevel

internal fun fpsColor(fps: Int, refreshRateHz: Float): Color = when {
    fps == 0 -> TextHeader
    fps >= refreshRateHz * FPS_GOOD_THRESHOLD -> TextGood
    fps >= refreshRateHz * FPS_WARN_THRESHOLD -> TextNormal
    else -> TextWarning
}

internal fun jankColor(jankPercent: Float): Color = when (JankSeverity.of(jankPercent)) {
    JankSeverity.SEVERE -> TextWarning
    JankSeverity.WARNING -> TextNormal
    JankSeverity.NONE -> TextHeader
}

internal fun verdictColor(verdict: PanelVerdict): Color = when (verdict) {
    PanelVerdict.Ok -> TextGood
    is PanelVerdict.Attention -> if (verdict.isSevere) TextWarning else TextCaution
}

internal fun metricRowColor(
    valueMs: Float,
    frameBudgetMs: Float,
    kind: MetricRowKind,
    isAttention: Boolean,
): Color = when {
    valueMs > frameBudgetMs -> TextWarning
    isAttention -> TextCaution
    kind == MetricRowKind.TOTAL -> TextGood
    else -> TextNormal
}

internal fun thermalColor(level: ThermalLevel): Color = when {
    level >= ThermalLevel.SEVERE -> TextWarning
    level.isThrottling -> TextCaution
    else -> TextHeader
}
