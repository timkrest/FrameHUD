// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import com.timkrest.framehud.FrameHistory
import kotlin.math.max

internal fun frameChart(history: FrameHistory, label: String, framesBeforeTrigger: Int? = null): String {
    var scaleMs = 0f
    for (index in 0 until history.size) {
        scaleMs = max(scaleMs, max(history.totalMsAt(index), history.budgetMsAt(index)))
    }
    if (scaleMs <= 0f) return ""
    scaleMs *= CHART_HEADROOM

    return buildString {
        append("<svg viewBox=\"0 0 $CHART_WIDTH $CHART_HEIGHT\" preserveAspectRatio=\"none\" role=\"img\" ")
        append("aria-label=\"").append(escapeHtml(label)).append("\">\n")
        val step = CHART_WIDTH / history.size
        val barWidth = step * CHART_BAR_SHARE
        val barWidthText = formatFloat(barWidth)
        for (index in 0 until history.size) {
            val totalMs = history.totalMsAt(index)
            val budgetMs = history.budgetMsAt(index)
            val height = CHART_HEIGHT * (totalMs / scaleMs)
            val cssClass = if (totalMs > budgetMs) "janky" else "ok"
            append("<rect class=\"").append(cssClass).append("\" x=\"").append(formatFloat(step * index))
            append("\" y=\"").append(formatFloat(CHART_HEIGHT - height))
            append("\" width=\"").append(barWidthText)
            append("\" height=\"").append(formatFloat(height)).append("\"/>\n")

            val budgetY = CHART_HEIGHT * (1f - budgetMs / scaleMs)
            append("<line class=\"budget\" x1=\"").append(formatFloat(step * index))
            append("\" x2=\"").append(formatFloat(step * index + barWidth))
            append("\" y1=\"").append(formatFloat(budgetY))
            append("\" y2=\"").append(formatFloat(budgetY)).append("\"/>\n")
        }
        if (framesBeforeTrigger != null) {
            val triggerX = formatFloat(step * framesBeforeTrigger)
            append("<line class=\"trigger\" x1=\"").append(triggerX).append("\" x2=\"").append(triggerX)
            append("\" y1=\"0\" y2=\"").append(formatFloat(CHART_HEIGHT)).append("\"/>\n")
        }
        append("</svg>\n")
    }
}

private const val CHART_WIDTH = 600f
private const val CHART_HEIGHT = 120f
private const val CHART_BAR_SHARE = 0.8f
private const val CHART_HEADROOM = 1.05f
