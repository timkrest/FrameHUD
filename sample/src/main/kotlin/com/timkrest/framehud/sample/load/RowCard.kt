package com.timkrest.framehud.sample.load

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RowCard(index: Int, active: ActiveLoads, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(rowColor(index))
                    .drawBehind {
                        if (Load.BlockMainThread in active) Thread.sleep(BLOCK_MAIN_THREAD_MS)
                        if (Load.Overdraw in active) {
                            repeat(OVERDRAW_LAYERS) {
                                drawRect(color = Color.White.copy(alpha = OVERDRAW_ALPHA))
                            }
                        }
                    },
            )
            Column {
                Text(text = "Row $index", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Tap to open it as its own measured screen",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun rowColor(index: Int): Color = ROW_COLORS[index % ROW_COLORS.size]

private val ROW_COLORS = listOf(
    Color(0xFF4C6EF5),
    Color(0xFF12B886),
    Color(0xFFF59F00),
    Color(0xFFE64980),
)

private const val BLOCK_MAIN_THREAD_MS = 6L
private const val OVERDRAW_LAYERS = 60
private const val OVERDRAW_ALPHA = 0.03f
