package com.timkrest.framehud.internal

import android.util.Log
import com.timkrest.framehud.InternalFrameHudApi

@InternalFrameHudApi
public inline fun guarded(what: String, block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (e: Exception) {
        GuardedFailures.report(what, e)
        false
    } catch (e: LinkageError) {
        GuardedFailures.report(what, e)
        false
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
