// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.compose

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.timkrest.framehud.FrameHud

/**
 * Sets `FrameHud.mark` to [name] while [state] scrolls, and clears it when the scroll stops or this
 * leaves the composition. A mark set elsewhere while the list stands still is left alone. [name]
 * follows the naming rule `FrameHud.mark` states.
 */
@Composable
public fun MarkWhileScrolling(state: ScrollableState, name: String) {
    LaunchedEffect(state, name) {
        val mark = ScrollMark(name, FrameHudMarks)
        try {
            snapshotFlow { state.isScrollInProgress }.collect { mark.scrolling(it) }
        } finally {
            mark.release()
        }
    }
}

private object FrameHudMarks : Marks {
    override var current: String?
        get() = FrameHud.mark
        set(value) {
            FrameHud.mark = value
        }
}
