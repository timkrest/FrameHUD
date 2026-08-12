package com.timkrest.framehud.internal

import android.util.Log
import com.timkrest.framehud.InternalFrameHudApi

@InternalFrameHudApi
public inline fun guarded(what: String, block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed while $what", e)
        false
    }
