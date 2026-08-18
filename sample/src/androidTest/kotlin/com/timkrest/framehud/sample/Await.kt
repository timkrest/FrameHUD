package com.timkrest.framehud.sample

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun <T> await(timeoutMs: Long = AWAIT_TIMEOUT_MS, read: suspend () -> T): T =
    runBlocking { withTimeout(timeoutMs) { read() } }

private const val AWAIT_TIMEOUT_MS: Long = 2_000L
