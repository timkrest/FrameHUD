// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.ApplicationExitInfo
import com.timkrest.framehud.ExitReason
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RunExitsTest {

    private val exit = processExit()

    private val summary = ShownState(run = "0a1b2c3d:1", screen = "checkout", mark = "scroll").toSummary()

    @Test
    fun `an exit carries the run, screen and mark its process left behind`() {
        val ended = RunExit.of(ApplicationExitInfo.REASON_ANR, "Input dispatching timed out", 300L, summary)

        assertEquals("0a1b2c3d:1", ended?.runId)
        assertEquals(ExitReason.ANR, ended?.exit?.reason)
        assertEquals("checkout", ended?.exit?.screen)
        assertEquals("scroll", ended?.exit?.mark)
    }

    @Test
    fun `an ANR keeps the main thread stack it was read with`() {
        val stack = listOf("com.example.Cart.load(Cart.kt:42)")

        assertEquals(stack, RunExit.of(ApplicationExitInfo.REASON_ANR, null, 300L, summary, stack)?.exit?.mainThreadStack)
    }

    @Test
    fun `a process that left no run behind ended outside any kept run`() {
        assertNull(RunExit.of(ApplicationExitInfo.REASON_CRASH, null, 300L, null))
        assertNull(RunExit.of(ApplicationExitInfo.REASON_CRASH, null, 300L, "level=3".toByteArray()))
    }

    @Test
    fun `an exit with no time to it is skipped rather than failing the rest`() {
        assertNull(RunExit.of(ApplicationExitInfo.REASON_CRASH, null, 0L, summary))
    }

    @Test
    fun `a reason this build does not know reads as unknown`() {
        assertEquals(ExitReason.UNKNOWN, RunExit.of(99, null, 300L, summary)?.exit?.reason)
    }

    @Test
    fun `an exit goes to the run that was going when the process ended, not to the one before a reset`() {
        val runs = listOf(storedRun(runId = "0a1b2c3d:2"), storedRun(runId = "0a1b2c3d:1"))

        val ended = runs.withExits(listOf(RunExit(runId = "0a1b2c3d:2", exit = exit)))

        assertEquals(exit, ended[0].run.exit)
        assertNull(ended[1].run.exit)
    }

    @Test
    fun `an exit whose run was never written goes nowhere`() {
        val runs = listOf(storedRun(runId = "0a1b2c3d:1"))

        assertSame(runs, runs.withExits(listOf(RunExit(runId = "0a1b2c3d:2", exit = exit))))
    }

    @Test
    fun `a run that already ended keeps its exit`() {
        val runs = listOf(storedRun(runId = "0a1b2c3d:1", exit = exit))

        assertSame(runs, runs.withExits(listOf(RunExit(runId = "0a1b2c3d:1", exit = processExit(reason = ExitReason.CRASH)))))
    }
}
