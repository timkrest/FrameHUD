package io.github.timkrest.framehud.ui

import androidx.compose.ui.graphics.Color
import io.github.timkrest.framehud.JankSeverity
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.ThermalLevel

internal fun fpsColor(metrics: PerformanceMetrics): Color {
    if (metrics.fps == 0) return TextHeader
    val targetFps = metrics.effectiveRefreshRate
    return when {
        metrics.fps >= targetFps * FPS_GOOD_THRESHOLD -> TextGood
        metrics.fps >= targetFps * FPS_WARN_THRESHOLD -> TextNormal
        else -> TextWarning
    }
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
    level == ThermalLevel.UNKNOWN -> TextHeader
    level >= ThermalLevel.SEVERE -> TextWarning
    level.isThrottling -> TextCaution
    else -> TextHeader
}
