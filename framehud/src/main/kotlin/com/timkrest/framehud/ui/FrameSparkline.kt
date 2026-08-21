package com.timkrest.framehud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameWindowStats
import kotlin.math.max

@Composable
internal fun FrameSparkline(window: FrameWindowStats, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .clip(SparklineShape)
            .background(SparklineBackground),
    ) {
        drawFrameHistory(history = window.history, frameBudgetMs = window.frameBudgetMs)
    }
}

private fun DrawScope.drawFrameHistory(history: FrameHistory, frameBudgetMs: Float) {
    val slotCount = (size.width / SparklineMinSlotWidth.toPx()).toInt()
    val frames = worstFramePerSlot(history = history, slotCount = slotCount)
    if (frames.size == 0) return

    val fullHeightMs = budgetDoublingCovering(frames = frames, frameBudgetMs = frameBudgetMs)
    val slotWidth = size.width / slotCount
    val barWidth = max(slotWidth - SparklineBarGap.toPx(), slotWidth * SPARKLINE_MIN_BAR_FRACTION)
    val firstSlotX = size.width - frames.size * slotWidth
    for (bar in 0 until frames.size) {
        val totalMs = frames.totalMsAt(bar)
        if (totalMs <= 0f) continue
        val barHeight = (totalMs / fullHeightMs).coerceIn(0f, 1f) * size.height
        drawRect(
            color = sparklineBarColor(totalMs = totalMs, deadlineMs = frames.deadlineMsAt(bar)),
            topLeft = Offset(x = firstSlotX + bar * slotWidth, y = size.height - barHeight),
            size = Size(width = barWidth, height = barHeight),
        )
    }

    val budgetY = size.height - (frameBudgetMs / fullHeightMs) * size.height
    drawLine(
        color = SparklineBudgetLine,
        start = Offset(x = 0f, y = budgetY),
        end = Offset(x = size.width, y = budgetY),
        strokeWidth = SparklineBudgetStroke.toPx(),
    )
}
