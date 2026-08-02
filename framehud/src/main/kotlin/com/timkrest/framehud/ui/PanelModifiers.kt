package com.timkrest.framehud.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.dragHandle(onDrag: (dx: Float, dy: Float) -> Unit): Modifier = pointerInput(onDrag) {
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
