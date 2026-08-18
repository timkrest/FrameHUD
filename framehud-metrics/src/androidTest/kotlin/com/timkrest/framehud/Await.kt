package com.timkrest.framehud

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal fun <T> await(timeoutMs: Long = AWAIT_TIMEOUT_MS, read: suspend () -> T): T =
    runBlocking { withTimeout(timeoutMs) { read() } }

internal const val AWAIT_TIMEOUT_MS: Long = 5_000L

/** Blocks until the metrics thread answers, so what follows sees a started collector. */
internal fun awaitCollector() {
    await { FrameHud.sessionStats() }
}
