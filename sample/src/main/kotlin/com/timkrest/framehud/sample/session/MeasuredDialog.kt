package com.timkrest.framehud.sample.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.timkrest.framehud.FrameHud

@Composable
fun MeasuredDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        MeasureThisWindow(screen = DIALOG_SCREEN)
        Card {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
            ) {
                CircularProgressIndicator()
                Text(text = "Measured as “$DIALOG_SCREEN”", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "A dialog draws in a window of its own, which FrameHUD cannot find " +
                        "until the app hands it over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) { Text(text = "Close") }
            }
        }
    }
}

@Composable
private fun MeasureThisWindow(screen: String) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window, screen) {
        FrameHud.measureWindow(window, screen)
        onDispose { FrameHud.forgetWindow(window) }
    }
}

private const val DIALOG_SCREEN = "dialog"
