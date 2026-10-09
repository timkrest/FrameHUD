// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class AnrTraceTest {

    @Test
    fun `the main thread's frames and the lock it waits on come back top first`() {
        assertEquals(
            listOf(
                "com.example.cart.CartRepository.load(CartRepository.kt:42)",
                "- waiting to lock <0x0a1b2c3d> (a java.lang.Object) held by thread 23",
                "com.example.cart.CartFragment.onResume(CartFragment.kt:18)",
                "android.os.Looper.loop(Looper.java:288)",
            ),
            mainThreadStackOf(TRACE.lineSequence()),
        )
    }

    @Test
    fun `a trace with no main thread gives no stack`() {
        assertEquals(emptyList(), mainThreadStackOf(TRACE.replace("\"main\"", "\"worker\"").lineSequence()))
    }

    @Test
    fun `a stack is cut at sixty-four lines`() {
        val deep = "\"main\" prio=5 tid=1 Native\n" + (1..100).joinToString("\n") { "  at Deep.call$it(Deep.kt:$it)" }

        assertEquals(64, mainThreadStackOf(deep.lineSequence()).size)
    }

    private companion object {
        val TRACE = """
            ----- pid 4242 at 2026-10-09 22:41:03.512 -----
            Cmd line: com.example

            DALVIK THREADS (31):
            "main" prio=5 tid=1 Blocked
              | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72c0b4c8 self=0xb400007a5e2c7be0
              | sysTid=4242 nice=-10 cgrp=top-app sched=0/0 handle=0x7b8a3b44f8
              at com.example.cart.CartRepository.load(CartRepository.kt:42)
              - waiting to lock <0x0a1b2c3d> (a java.lang.Object) held by thread 23
              at com.example.cart.CartFragment.onResume(CartFragment.kt:18)
              at android.os.Looper.loop(Looper.java:288)

            "Signal Catcher" daemon prio=10 tid=6 Runnable
              at java.lang.Object.wait(Native method)
        """.trimIndent()
    }
}
