package com.timkrest.framehud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.timkrest.framehud.FrameHistory
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun FrameSparkline(
    history: FrameHistory,
    frameBudgetMs: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(SparklineCornerRadius))
            .background(SparklineBackground),
    ) {
        drawFrameHistory(history = history, frameBudgetMs = frameBudgetMs)
    }
}

private fun DrawScope.drawFrameHistory(history: FrameHistory, frameBudgetMs: Float) {
    if (history.size == 0 || frameBudgetMs <= 0f) return

    val maxBars = (size.width / SparklineMinSlotWidth.toPx()).toInt().coerceAtLeast(1)
    val visibleCount = min(history.size, maxBars)
    val firstIndex = history.size - visibleCount

    var peakMs = 0f
    for (index in firstIndex until history.size) {
        peakMs = max(peakMs, history[index])
    }
    val scaleMs = max(frameBudgetMs * SPARKLINE_SCALE_FACTOR, peakMs)

    val slotWidth = size.width / visibleCount
    val barWidth = max(slotWidth - SparklineBarGap.toPx(), slotWidth * SPARKLINE_MIN_BAR_FRACTION)
    for (position in 0 until visibleCount) {
        val valueMs = history[firstIndex + position]
        if (valueMs <= 0f) continue
        val barHeight = (valueMs / scaleMs).coerceIn(0f, 1f) * size.height
        drawRect(
            color = sparklineBarColor(valueMs = valueMs, frameBudgetMs = frameBudgetMs),
            topLeft = Offset(x = position * slotWidth, y = size.height - barHeight),
            size = Size(width = barWidth, height = barHeight),
        )
    }

    val budgetY = size.height - (frameBudgetMs / scaleMs) * size.height
    drawLine(
        color = SparklineBudgetLine,
        start = Offset(x = 0f, y = budgetY),
        end = Offset(x = size.width, y = budgetY),
        strokeWidth = SparklineBudgetStroke.toPx(),
    )
}

private fun sparklineBarColor(valueMs: Float, frameBudgetMs: Float): Color = when {
    valueMs > frameBudgetMs * SPARKLINE_SEVERE_FACTOR -> TextWarning
    valueMs > frameBudgetMs -> TextCaution
    else -> TextGood
}
