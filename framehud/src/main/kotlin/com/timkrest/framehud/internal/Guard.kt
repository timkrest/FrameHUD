package com.timkrest.framehud.internal

import android.util.Log

/** Keeps overlay failures from crashing the host app. */
internal inline fun guarded(what: String, block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed while $what", e)
        false
    }
