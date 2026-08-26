package com.timkrest.framehud.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced

@Composable
internal fun Modifier.dragHandle(drag: PanelDrag): Modifier {
    var placement by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onPlaced { placement = it }.pointerInput(drag) {
        detectDragGestures(
            onDragStart = { at -> placement.onScreen(at)?.let(drag::grab) },
            onDragEnd = drag::release,
            onDragCancel = drag::release,
        ) { change, _ ->
            change.consume()
            placement.onScreen(change.position)?.let(drag::moveTo)
        }
    }
}

internal fun Modifier.tapAndHold(onTap: () -> Unit, onHold: () -> Unit): Modifier = combinedClickable(
    indication = null,
    interactionSource = null,
    onLongClick = onHold,
    onClick = onTap,
)

private fun LayoutCoordinates?.onScreen(local: Offset): Offset? =
    this?.takeIf { it.isAttached }?.localToScreen(local)
