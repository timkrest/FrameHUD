// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.util.Log
import com.timkrest.framehud.InternalFrameHudApi

@InternalFrameHudApi
public inline fun guarded(what: String, block: () -> Unit): Boolean = guarded(what, otherwise = false) {
    block()
    true
}

@InternalFrameHudApi
public inline fun <T> guarded(what: String, otherwise: T, block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        GuardedFailures.report(what, e)
        otherwise
    } catch (e: LinkageError) {
        GuardedFailures.report(what, e)
        otherwise
    }

@InternalFrameHudApi
public object GuardedFailures {

    @Volatile
    private var listener: ((what: String, error: Throwable) -> Unit)? = null

    public fun reportTo(listener: ((what: String, error: Throwable) -> Unit)?) {
        this.listener = listener
    }

    public fun report(what: String, error: Throwable) {
        Log.w(LOG_TAG, "Failed while $what", error)
        listener?.invoke(what, error)
    }
}
