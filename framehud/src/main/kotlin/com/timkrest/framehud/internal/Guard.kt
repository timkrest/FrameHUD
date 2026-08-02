package com.timkrest.framehud.internal

import android.util.Log

/**
 * Runs [block], logging anything it throws instead of letting it escape.
 *
 * A debug overlay must not take the app down with it, so every call it makes into the platform goes
 * through here — the metrics thread above all, where an exception escaping a `HandlerThread` reaches
 * the uncaught handler and kills the process. Losing a reading or a window update is the acceptable
 * failure; the log line says which one.
 */
internal inline fun guarded(what: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed while $what", e)
    }
}
