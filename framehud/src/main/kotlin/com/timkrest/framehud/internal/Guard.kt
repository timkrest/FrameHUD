package com.timkrest.framehud.internal

import android.util.Log

/**
 * Runs [block], logging anything it throws instead of letting it escape.
 *
 * Everything the metrics thread runs goes through here. An exception on a `HandlerThread` reaches
 * the platform's uncaught handler, which kills the process — and a debug overlay must not take the
 * app down with it. Losing a reading is the acceptable failure; the log line says which one.
 */
internal inline fun guarded(what: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed while $what", e)
    }
}
