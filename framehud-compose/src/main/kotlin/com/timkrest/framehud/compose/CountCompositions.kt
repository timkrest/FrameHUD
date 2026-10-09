// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.timkrest.framehud.FrameHud

/**
 * Adds one to the counter [name] every time the composable it is called from composes, the first
 * composition included. [name] follows the naming rule `FrameHud.counter` states.
 */
@Composable
@NonRestartableComposable
public fun CountCompositions(name: String) {
    val counter = remember(name) { FrameHud.counter(name) }
    SideEffect { counter.add(1) }
}
