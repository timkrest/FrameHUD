package com.timkrest.framehud.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fits the widest line the panel prints — the session totals of a long run — in a monospace 10sp. */
internal val PanelWidth = 250.dp
internal val ItemSpacing = 6.dp
internal val IconButtonSize = 26.dp
internal val CollapsedRowHeight = 26.dp
internal val ButtonCornerRadius = 6.dp
internal val PanelPadding = 8.dp
internal val PanelCornerRadius = 8.dp
internal val PanelBorderWidth = 1.dp
internal val DividerThickness = 1.dp
internal val SparklineHeight = 30.dp
internal val SparklineMiniWidth = 56.dp
internal val SparklineMiniHeight = 14.dp
internal val SparklineCornerRadius = 3.dp
internal val SparklineBudgetStroke = 1.dp
internal val SparklineBarGap = 0.5.dp
internal val SparklineMinSlotWidth = 2.dp

internal const val FPS_GOOD_THRESHOLD = 0.95f
internal const val FPS_WARN_THRESHOLD = 0.75f
internal const val SPARKLINE_SCALE_FACTOR = 2f
internal const val SPARKLINE_SEVERE_FACTOR = 2f
internal const val SPARKLINE_MIN_BAR_FRACTION = 0.6f
internal const val LOAD_BAR_ALPHA = 0.16f

private const val BACKGROUND_ALPHA = 0.97f
private const val BUTTON_PRESSED_ALPHA = 0.34f

internal val PanelShape = RoundedCornerShape(PanelCornerRadius)
internal val ButtonShape = RoundedCornerShape(ButtonCornerRadius)

internal val OverlayBackground = Color.Black.copy(alpha = BACKGROUND_ALPHA)
internal val ButtonBackgroundPressed = Color.White.copy(alpha = BUTTON_PRESSED_ALPHA)
internal val TextNormal = Color(0xFFCCCCCC)
internal val TextHeader = Color(0xFF888888)

internal val TextDimmed = Color(0xFF666666)
internal val TextWarning = Color(0xFFFF6B6B)
internal val TextCaution = Color(0xFFFFC66D)
internal val TextGood = Color(0xFF66CC66)
internal val TextFrozen = Color(0xFF6FB7FF)
internal val DividerColor = Color(0xFF555555)
internal val SparklineBackground = Color(0xFF161616)
internal val SparklineBudgetLine = Color(0xFF6E6E6E)

internal val MetricStyle = TextStyle(
    fontSize = 10.sp,
    fontFamily = FontFamily.Monospace,
    lineHeight = 13.sp,
)

internal val PanelTextStyle = MetricStyle.copy(color = TextNormal)

internal val IconButtonStyle = TextStyle(
    fontSize = 14.sp,
    fontFamily = FontFamily.Monospace,
    color = TextHeader,
)
