package com.timkrest.framehud.sample.session

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.timkrest.framehud.FrameHud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun rememberSessionState(): SessionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { SessionState(scope) }
}

@Stable
class SessionState(private val scope: CoroutineScope) {

    var report by mutableStateOf(SessionReport())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var dialogShown by mutableStateOf(false)
        private set

    fun read() {
        scope.launch { readReport() }
    }

    fun reset() {
        FrameHud.reset()
        message = "Session cleared."
        read()
    }

    fun share(activity: Activity?) {
        scope.launch {
            val export = try {
                FrameHud.exportSession()
            } catch (e: IOException) {
                message = "The session could not be written: ${e.message}"
                return@launch
            }
            if (export == null) {
                message = "Nothing has been collected yet."
                return@launch
            }
            message = "Wrote ${export.json.name} and ${export.html.name} to ${export.json.parent}"
            activity?.let { FrameHud.shareSession(it, export) }
        }
    }

    fun saveBaseline() {
        scope.launch {
            message = try {
                when (val baseline = FrameHud.saveBaseline()) {
                    null -> "This session recorded no frame, so the baseline is unchanged."
                    else -> "This run joined the baseline for ${baseline.environment.label}."
                }
            } catch (e: IOException) {
                "The baseline was left as it was: ${e.message}"
            }
            readReport()
        }
    }

    private suspend fun readReport() {
        try {
            report = SessionReport.read()
        } catch (e: IOException) {
            message = "FrameHUD could not read what it wrote: ${e.message}"
        }
    }

    fun retainTrace() {
        val trigger = FrameHud.config.perfettoTrigger
        if (trigger == null) {
            message = "Switch the flight recorder on first, then a trace has something to hear from."
            return
        }
        FrameHud.retainTrace()
        message = "Asked the $trigger trace to keep what it holds."
    }

    fun showDialog() {
        dialogShown = true
    }

    fun hideDialog() {
        dialogShown = false
    }
}
