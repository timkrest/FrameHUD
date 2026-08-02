package com.timkrest.framehud.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun SampleRow(index: Int, active: ActiveLoads) {
    if (Load.Allocate in active) {
        remember(index, active) { List(ALLOCATION_SIZE) { "row $index allocation $it" } }
    }
    if (Load.HeavyLayout in active) {
        NestedBoxes(depth = NESTING_DEPTH) { RowCard(index = index, active = active) }
    } else {
        RowCard(index = index, active = active)
    }
}

private const val ALLOCATION_SIZE = 2_000
private const val NESTING_DEPTH = 40
