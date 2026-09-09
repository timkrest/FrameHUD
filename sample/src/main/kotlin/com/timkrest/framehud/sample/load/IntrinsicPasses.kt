package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout

@Composable
fun IntrinsicPasses(passes: Int, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val measurable = measurables.single()
        repeat(passes) { measurable.maxIntrinsicHeight(constraints.maxWidth) }
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
