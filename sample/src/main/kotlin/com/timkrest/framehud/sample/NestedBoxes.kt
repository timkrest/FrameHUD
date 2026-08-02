package com.timkrest.framehud.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable

@Composable
fun NestedBoxes(depth: Int, content: @Composable () -> Unit) {
    if (depth == 0) {
        content()
        return
    }
    Box(propagateMinConstraints = true) {
        NestedBoxes(depth = depth - 1, content = content)
    }
}
