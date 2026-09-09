// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.os.SystemClock
import com.timkrest.framehud.FrameHudEvent
import kotlin.test.fail

internal const val EVENT_TIMEOUT_MS = 5_000L
internal const val EVENT_POLL_INTERVAL_MS = 50L

internal inline fun <reified T : FrameHudEvent> List<FrameHudEvent>.awaitEvents(count: Int): List<T> {
    val deadlineMs = SystemClock.elapsedRealtime() + EVENT_TIMEOUT_MS
    while (SystemClock.elapsedRealtime() < deadlineMs) {
        val matching = filterIsInstance<T>()
        if (matching.size >= count) return matching.take(count)
        SystemClock.sleep(EVENT_POLL_INTERVAL_MS)
    }
    fail("Expected $count ${T::class.java.simpleName} events, saw ${map { it.summary }}")
}
