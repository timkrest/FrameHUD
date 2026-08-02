package com.timkrest.framehud.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
internal fun MetricText(text: String, color: Color) {
    val style = remember(color) { MetricStyle.copy(color = color) }
    BasicText(text = text, style = style, softWrap = false, maxLines = 1)
}
