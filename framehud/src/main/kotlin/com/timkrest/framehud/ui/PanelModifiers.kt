// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun Modifier.dragHandle(drag: PanelDrag): Modifier = pointerInput(drag) {
    var grabbed: GrabbedPanel? = null
    detectDragGestures(onDragStart = { grabbed = drag.grab() }) { change, _ ->
        change.consume()
        grabbed?.followPointer()
    }
}

internal fun Modifier.tapAndHold(onTap: () -> Unit, onHold: () -> Unit): Modifier = combinedClickable(
    indication = null,
    interactionSource = null,
    onLongClick = onHold,
    onClick = onTap,
)
