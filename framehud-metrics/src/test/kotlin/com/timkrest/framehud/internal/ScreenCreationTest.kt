package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenCreationTest {

    @Test
    fun `time to display spans creation and the end of the first frame`() {
        val creation = ScreenCreation(startedAtNs = 10_000_000L)

        assertEquals(12.5f, creation.timeToDisplayMs(frameEndNs = 22_500_000L))
    }

    @Test
    fun `a frame that ended before the screen was created is no measurement`() {
        val creation = ScreenCreation(startedAtNs = 10_000_000L)

        assertNull(creation.timeToDisplayMs(frameEndNs = 9_000_000L))
    }
}
