package com.timkrest.framehud.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.timkrest.framehud.FrameHud
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SampleEvents.register()
        setContent {
            ReportDrawn()
            MaterialTheme {
                SampleScreen(
                    title = "FrameHUD sample",
                    subtitle = "Scroll the list, then switch loads on and watch the panel react.",
                    actions = {
                        SampleActions(
                            onToggleOverlay = FrameHud::toggle,
                            onOpenDetails = { startActivity(Intent(this, DetailsActivity::class.java)) },
                            onShareReport = ::shareReport,
                        )
                    },
                )
            }
        }
    }

    private fun shareReport() {
        lifecycleScope.launch {
            val export = FrameHud.exportSession() ?: return@launch
            FrameHud.shareSession(this@MainActivity, export)
        }
    }
}

@Composable
private fun SampleActions(onToggleOverlay: () -> Unit, onOpenDetails: () -> Unit, onShareReport: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onToggleOverlay) { Text(text = "Toggle overlay", maxLines = 1) }
        Button(onClick = onOpenDetails) { Text(text = "Second screen", maxLines = 1) }
        OutlinedButton(onClick = onShareReport) { Text(text = "Share report", maxLines = 1) }
    }
}
