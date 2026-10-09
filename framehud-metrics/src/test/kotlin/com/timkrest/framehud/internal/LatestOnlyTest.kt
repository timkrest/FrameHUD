// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class LatestOnlyTest {

    private val waiting = ArrayDeque<() -> Unit>()

    private val delivered = mutableListOf<String>()

    private var accepts = true

    private val latest = LatestOnly<String>(
        post = { task -> accepts.also { if (it) waiting.addLast(task) } },
        deliver = { delivered += it },
    )

    @Test
    fun `values offered while one waits arrive as the newest alone`() {
        latest.offer("cart")
        latest.offer("checkout")
        latest.offer("payment")
        runWaiting()

        assertEquals(listOf("payment"), delivered)
    }

    @Test
    fun `a value offered after the last delivery waits its own turn`() {
        latest.offer("cart")
        runWaiting()
        latest.offer("checkout")
        runWaiting()

        assertEquals(listOf("cart", "checkout"), delivered)
    }

    @Test
    fun `a value nothing would take does not hold up the next`() {
        accepts = false
        latest.offer("cart")
        accepts = true

        latest.offer("checkout")
        runWaiting()

        assertEquals(listOf("checkout"), delivered)
    }

    private fun runWaiting() {
        while (waiting.isNotEmpty()) waiting.removeFirst()()
    }
}
