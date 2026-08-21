package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.timkrest.framehud.FrameHud

@Composable
fun SampleRow(index: Int, active: ActiveLoads, onOpen: () -> Unit) {
    SideEffect { rowsComposed.add(1) }
    if (Load.Allocate in active) {
        remember(index, active) { List(ALLOCATION_SIZE) { "row $index allocation $it" } }
    }
    if (Load.HeavyLayout in active) {
        NestedBoxes(depth = NESTING_DEPTH) { RowCard(index = index, active = active, onOpen = onOpen) }
    } else {
        RowCard(index = index, active = active, onOpen = onOpen)
    }
}

private val rowsComposed by lazy { FrameHud.counter("rows composed") }

private const val ALLOCATION_SIZE = 2_000
private const val NESTING_DEPTH = 40
