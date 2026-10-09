// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import com.timkrest.framehud.FrameHistory
import kotlin.math.max
import kotlin.math.min

internal fun worstFramePerSlot(history: FrameHistory, slotCount: Int): FrameHistory {
    if (history.size == 0 || slotCount <= 0) return FrameHistory.EMPTY

    val barCount = min(history.size, slotCount)
    val totalsMs = FloatArray(barCount)
    val budgetsMs = FloatArray(barCount)
    for (bar in 0 until barCount) {
        var worst = bar * history.size / barCount
        val until = (bar + 1) * history.size / barCount
        for (index in worst + 1 until until) {
            if (history.overrunMsAt(index) > history.overrunMsAt(worst)) worst = index
        }
        totalsMs[bar] = history.totalMsAt(worst)
        budgetsMs[bar] = history.budgetMsAt(worst)
    }
    return FrameHistory.of(totalsMs = totalsMs, budgetsMs = budgetsMs)
}

internal fun budgetDoublingCovering(bars: FrameHistory, frameBudgetMs: Float): Float {
    var peakMs = 0f
    for (bar in 0 until bars.size) peakMs = max(peakMs, bars.totalMsAt(bar))

    val ceilingMs = frameBudgetMs * SPARKLINE_MAX_SCALE_BUDGETS
    var fullHeightMs = frameBudgetMs * SPARKLINE_MIN_SCALE_BUDGETS
    while (fullHeightMs < peakMs && fullHeightMs < ceilingMs) fullHeightMs *= 2f
    return fullHeightMs
}

private fun FrameHistory.overrunMsAt(index: Int): Float = totalMsAt(index) - budgetMsAt(index)
