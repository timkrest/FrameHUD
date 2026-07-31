package com.timkrest.framehud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.dragHandle(onDrag: (dx: Float, dy: Float) -> Unit): Modifier = pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        onDrag(dragAmount.x, dragAmount.y)
    }
}

internal fun Modifier.tapAndHold(onTap: () -> Unit, onHold: () -> Unit): Modifier = combinedClickable(
    indication = null,
    interactionSource = null,
    onLongClick = onHold,
    onClick = onTap,
)

@Composable
internal fun FpsText(fps: Int, refreshRateHz: Float) {
    MetricText(text = formatFps(fps), color = fpsColor(fps = fps, refreshRateHz = refreshRateHz))
}

@Composable
internal fun MetricText(text: String, color: Color) {
    val style = remember(color) { MetricStyle.copy(color = color) }
    BasicText(text = text, style = style, softWrap = false, maxLines = 1)
}

@Composable
internal fun PanelIconButton(icon: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(IconButtonSize)
            .clip(ButtonShape)
            .background(if (isPressed) ButtonBackgroundPressed else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = icon, style = IconButtonStyle, softWrap = false, maxLines = 1)
    }
}
