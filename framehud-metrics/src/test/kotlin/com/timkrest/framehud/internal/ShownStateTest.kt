// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShownStateTest {

    @Test
    fun `a run, a screen and a mark come back as they were left`() {
        val shown = ShownState(run = RUN, screen = "product/{id}", mark = "scroll")

        assertEquals(shown, ShownState.fromSummary(shown.toSummary()))
    }

    @Test
    fun `a run in the background comes back with no screen`() {
        val shown = ShownState(run = RUN, screen = null, mark = null)

        assertEquals(shown, ShownState.fromSummary(shown.toSummary()))
    }

    @Test
    fun `a summary this build did not leave reads as nothing`() {
        assertEquals(ShownState.NOTHING, ShownState.fromSummary(null))
        assertEquals(ShownState.NOTHING, ShownState.fromSummary(ShownState.NOTHING.toSummary()))
        assertEquals(ShownState.NOTHING, ShownState.fromSummary("level=3\ncheckout".toByteArray()))
    }

    @Test
    fun `a mark that does not fit beside the screen is left out rather than cut`() {
        val shown = ShownState(run = RUN, screen = "s".repeat(100), mark = "m".repeat(40))

        assertEquals(shown.copy(mark = null), ShownState.fromSummary(shown.toSummary()))
    }

    @Test
    fun `a screen that does not fit leaves the run alone rather than half a name`() {
        val shown = ShownState(run = RUN, screen = "экран".repeat(20), mark = "scroll")

        assertEquals(ShownState(run = RUN, screen = null, mark = null), ShownState.fromSummary(shown.toSummary()))
    }

    @Test
    fun `no summary is longer than the system keeps`() {
        val longest = ShownState(run = RUN, screen = "q".repeat(MAX_TRACE_NAME_LENGTH), mark = "ж".repeat(MAX_TRACE_NAME_LENGTH))

        assertTrue(longest.toSummary().size <= ShownState.MAX_SUMMARY_BYTES)
    }

    private companion object {
        const val RUN = "0a1b2c3d:1"
    }
}
