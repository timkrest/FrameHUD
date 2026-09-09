package com.timkrest.framehud.shared

import com.timkrest.framehud.FrameHud
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal const val AWAIT_TIMEOUT_MS: Long = 5_000L

internal fun <T> await(timeoutMs: Long = AWAIT_TIMEOUT_MS, read: suspend () -> T): T =
    runBlocking { withTimeout(timeoutMs) { read() } }

internal fun awaitCollectorStarted() {
    await { FrameHud.sessionStats() }
}
