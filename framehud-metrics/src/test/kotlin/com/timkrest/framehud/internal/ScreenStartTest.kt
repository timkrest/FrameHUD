package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenStartTest {

    @Test
    fun `elapsed time spans the start of the screen and the end of the frame`() {
        val start = ScreenStart(startedAtNs = 10_000_000L)

        assertEquals(12.5f, start.elapsedMs(frameEndNs = 22_500_000L))
    }

    @Test
    fun `a frame that ended before the screen started is no measurement`() {
        val start = ScreenStart(startedAtNs = 10_000_000L)

        assertNull(start.elapsedMs(frameEndNs = 9_000_000L))
    }
}
