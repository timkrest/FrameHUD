// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.timkrest.framehud.PerformanceMetrics

internal fun buildMiniLine(
    metrics: PerformanceMetrics,
    isEmulator: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    val window = metrics.window
    appendColored(
        text = formatFps(window.fps).padStart(FPS_FIELD_WIDTH),
        color = fpsColor(fps = window.fps, refreshRateHz = metrics.display.refreshRateHz),
    )
    append(MINI_SEPARATOR)
    appendColored(text = formatJankShort(window.jankPercent), color = jankColor(window.jankPercent))
    val verdict = panelVerdict(phases = metrics.phases, jankPercent = window.jankPercent, isEmulator = isEmulator)
    if (verdict is PanelVerdict.Attention) {
        append(MINI_SEPARATOR)
        appendColored(
            text = formatVerdictShort(verdict.phaseLabel).padStart(VERDICT_FIELD_WIDTH),
            color = verdictColor(verdict),
        )
    }
}

internal val MINI_WIDEST_READING: String =
    formatFps(WIDEST_FPS_READING) + MINI_SEPARATOR + formatJankShort(ALL_FRAMES_JANKY) +
        MINI_SEPARATOR + formatVerdictShort(LONGEST_PHASE_LABEL)

private const val WIDEST_FPS_READING = 999

private const val ALL_FRAMES_JANKY = 100f

private val FPS_FIELD_WIDTH = formatFps(WIDEST_FPS_READING).length

private val VERDICT_FIELD_WIDTH = formatVerdictShort(LONGEST_PHASE_LABEL).length
