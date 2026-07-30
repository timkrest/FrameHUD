package io.github.timkrest.framehud.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.timkrest.framehud.FrameHud

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SampleScreen(onToggleOverlay = FrameHud::toggle)
            }
        }
    }
}

private enum class Load(val label: String) {
    BlockMainThread("Block main thread"),
    Overdraw("Overdraw"),
    Allocate("Allocate"),
    HeavyLayout("Heavy layout"),
    GcChurn("GC churn"),
}

@Composable
private fun SampleScreen(onToggleOverlay: () -> Unit) {
    var active by remember { mutableStateOf(emptySet<Load>()) }

    if (Load.GcChurn in active) GcChurn()

    Scaffold { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            Text(
                text = "FrameHUD sample",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text = "Scroll the list, then switch loads on and watch the panel react.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Load.entries.forEach { load ->
                    FilterChip(
                        selected = load in active,
                        onClick = { active = if (load in active) active - load else active + load },
                        label = { Text(load.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            FilterChip(
                selected = false,
                onClick = onToggleOverlay,
                label = { Text("Toggle overlay") },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(count = ROW_COUNT, key = { it }) { index ->
                    SampleRow(index = index, active = active)
                }
            }
        }
    }
}

@Composable
private fun GcChurn() {
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            repeat(CHURN_BLOCKS) { garbageSink = ByteArray(CHURN_BLOCK_BYTES) }
        }
    }
}

@Composable
private fun NestedBoxes(depth: Int, content: @Composable () -> Unit) {
    if (depth == 0) {
        content()
        return
    }
    Box(propagateMinConstraints = true) {
        NestedBoxes(depth = depth - 1, content = content)
    }
}

@Composable
private fun SampleRow(index: Int, active: Set<Load>) {
    if (Load.Allocate in active) {
        remember(index, active) { List(ALLOCATION_SIZE) { "row $index allocation $it" } }
    }
    if (Load.HeavyLayout in active) {
        NestedBoxes(depth = NESTING_DEPTH) { RowCard(index = index, active = active) }
    } else {
        RowCard(index = index, active = active)
    }
}

@Composable
private fun RowCard(index: Int, active: Set<Load>) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                        if (Load.BlockMainThread in active) Thread.sleep(BLOCK_MS)
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
                    text = "Tap the chips above to make this list stutter",
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

private var garbageSink: ByteArray? = null

private const val ROW_COUNT = 300
private const val ALLOCATION_SIZE = 2_000
private const val OVERDRAW_LAYERS = 60
private const val OVERDRAW_ALPHA = 0.03f
private const val BLOCK_MS = 6L
private const val NESTING_DEPTH = 40
private const val CHURN_BLOCKS = 200
private const val CHURN_BLOCK_BYTES = 64 * 1024
