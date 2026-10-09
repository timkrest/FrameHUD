// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.shared

import android.os.SystemClock
import com.timkrest.framehud.FrameHud
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal const val AWAIT_TIMEOUT_MS: Long = 5_000L

private const val POLL_INTERVAL_MS = 20L

internal fun <T> await(timeoutMs: Long = AWAIT_TIMEOUT_MS, read: suspend () -> T): T =
    runBlocking { withTimeout(timeoutMs) { read() } }

/** Reads until [read] answers, or null once [AWAIT_TIMEOUT_MS] has passed. */
internal fun <T : Any> pollFor(read: () -> T?): T? {
    val deadlineMs = SystemClock.uptimeMillis() + AWAIT_TIMEOUT_MS
    while (true) {
        read()?.let { return it }
        if (SystemClock.uptimeMillis() >= deadlineMs) return null
        SystemClock.sleep(POLL_INTERVAL_MS)
    }
}

internal fun awaitUntil(condition: () -> Boolean): Boolean = pollFor { condition().takeIf { it } } != null

internal fun awaitCollectorStarted() {
    await { FrameHud.sessionStats() }
}
