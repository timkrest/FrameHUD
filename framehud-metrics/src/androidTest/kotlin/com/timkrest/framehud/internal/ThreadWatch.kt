package com.timkrest.framehud.internal

import android.os.SystemClock

internal const val TIMEOUT_MS = 5_000L

internal fun awaitUntil(condition: () -> Boolean): Boolean {
    val deadlineMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
    while (SystemClock.elapsedRealtime() < deadlineMs) {
        if (condition()) return true
        SystemClock.sleep(POLL_INTERVAL_MS)
    }
    return condition()
}

internal fun awaitThreadGone(name: String): Boolean =
    awaitUntil { Thread.getAllStackTraces().keys.none { it.name == name } }

internal fun threadsNamed(name: String): Int = Thread.getAllStackTraces().keys.count { it.name == name }

private const val POLL_INTERVAL_MS = 10L
