package com.timkrest.framehud

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.timkrest.framehud.internal.LOG_TAG
import com.timkrest.framehud.internal.baselineFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal class FrameHudCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ENABLE -> {
                FrameHud.show()
                resultData = "enabled"
            }
            ACTION_DISABLE -> {
                FrameHud.hide()
                resultData = "disabled"
            }
            ACTION_RESET -> {
                FrameHud.reset()
                resultData = "reset"
            }
            ACTION_SCREEN -> resultData = setName("screen", intent.name) { FrameHud.screen = it }
            ACTION_MARK -> resultData = setName("mark", intent.name) { FrameHud.mark = it }
            ACTION_CONTEXT -> {
                val pairs = intent.contextPairs()
                FrameHud.context = pairs
                resultData = if (pairs.isEmpty()) "context cleared" else "context $pairs"
            }
            ACTION_EXPORT -> respondAsync("export") {
                FrameHud.exportSession()?.json?.absolutePath ?: "nothing collected"
            }
            ACTION_RETAIN -> {
                FrameHud.retainTrace()
                resultData = FrameHud.config.perfettoTrigger?.let { "asked $it" } ?: "no Perfetto trigger configured"
            }
            ACTION_BASELINE -> {
                val file = (context.applicationContext as? Application)?.let(::baselineFile)
                respondAsync("baseline") {
                    when {
                        FrameHud.saveBaseline() == null -> "nothing collected"
                        else -> file?.absolutePath ?: "baseline updated"
                    }
                }
            }
        }
    }

    private fun setName(command: String, name: String?, set: (String?) -> Unit): String = try {
        set(name)
        "$command ${name ?: "cleared"}"
    } catch (e: IllegalArgumentException) {
        Log.w(LOG_TAG, "The $command command over adb failed", e)
        "failed: ${e.message}"
    }

    private val Intent.name: String? get() = extrasIfLoadable()?.getString(EXTRA_NAME)

    private fun Intent.extrasIfLoadable(): Bundle? = try {
        extras?.apply { keySet() }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Ignoring the extras of $action: a sender named a class this process cannot load", e)
        null
    }

    private fun Intent.contextPairs(): Map<String, String> {
        val bundle = extrasIfLoadable() ?: return emptyMap()
        return buildMap {
            for (key in bundle.keySet()) {
                if (key.isBlank()) continue
                val value = bundle.getString(key)?.takeIf { it.isNotBlank() } ?: continue
                put(key, value)
            }
        }
    }

    private fun respondAsync(command: String, respond: suspend () -> String) {
        val pending = goAsync()
        Thread({
            try {
                pending.resultData = runBlocking { withTimeout(COMMAND_TIMEOUT_MS) { respond() } }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "The $command command over adb failed", e)
                pending.resultData = "failed: $e"
            } finally {
                pending.finish()
            }
        }, "framehud-$command").start()
    }

    companion object {
        const val ACTION_ENABLE = "com.timkrest.framehud.ENABLE"
        const val ACTION_DISABLE = "com.timkrest.framehud.DISABLE"
        const val ACTION_RESET = "com.timkrest.framehud.RESET"
        const val ACTION_SCREEN = "com.timkrest.framehud.SCREEN"
        const val ACTION_MARK = "com.timkrest.framehud.MARK"
        const val ACTION_CONTEXT = "com.timkrest.framehud.CONTEXT"
        const val ACTION_EXPORT = "com.timkrest.framehud.EXPORT"
        const val ACTION_BASELINE = "com.timkrest.framehud.BASELINE"
        const val ACTION_RETAIN = "com.timkrest.framehud.RETAIN"
        const val EXTRA_NAME = "name"
        const val COMMAND_TIMEOUT_MS = 5_000L
    }
}
