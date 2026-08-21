package com.timkrest.framehud

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal fun <T> await(timeoutMs: Long = AWAIT_TIMEOUT_MS, read: suspend () -> T): T =
    runBlocking { withTimeout(timeoutMs) { read() } }

internal const val AWAIT_TIMEOUT_MS: Long = 5_000L

internal fun awaitCollectorStarted() {
    await { FrameHud.sessionStats() }
}
