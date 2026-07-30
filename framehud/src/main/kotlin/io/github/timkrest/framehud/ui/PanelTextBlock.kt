package io.github.timkrest.framehud.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult

@Composable
internal fun PanelTextBlock(lines: PanelLines, modifier: Modifier = Modifier) {
    val text = remember(lines) { lines.toAnnotatedString() }
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        style = PanelTextStyle,
        softWrap = false,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.drawBehind { drawLineDecorations(lines = lines, layout = layoutResult.value) },
    )
}

private fun DrawScope.drawLineDecorations(lines: PanelLines, layout: TextLayoutResult?) {
    if (layout == null) return
    lines.values.forEachIndexed { index, line ->
        if (index >= layout.lineCount) return@forEachIndexed
        val top = layout.getLineTop(index)
        if (line.loadFraction > 0f) {
            drawRect(
                color = line.color.copy(alpha = LOAD_BAR_ALPHA),
                topLeft = Offset(x = 0f, y = top),
                size = Size(width = size.width * line.loadFraction, height = layout.getLineBottom(index) - top),
            )
        }
        if (line.hasSeparatorAbove) {
            drawRect(
                color = DividerColor,
                topLeft = Offset(x = 0f, y = top),
                size = Size(width = size.width, height = DividerThickness.toPx()),
            )
        }
    }
}
