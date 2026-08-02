package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class JankRatioTest {

    @Test
    fun `an empty window reports no jank`() {
        assertEquals(0f, JankRatio(capacity = 4).percent(), TOLERANCE)
    }

    @Test
    fun `the share counts only the frames added so far`() {
        val jank = JankRatio(capacity = 100)
        jank.add(isJanky = true)
        jank.add(isJanky = false)
        assertEquals(50f, jank.percent(), TOLERANCE)
    }

    @Test
    fun `frames the window has evicted stop counting`() {
        val jank = JankRatio(capacity = 2)
        jank.add(isJanky = true)
        jank.add(isJanky = true)
        assertEquals(100f, jank.percent(), TOLERANCE)

        jank.add(isJanky = false)
        assertEquals(50f, jank.percent(), TOLERANCE)
        jank.add(isJanky = false)
        assertEquals(0f, jank.percent(), TOLERANCE)
    }

    @Test
    fun `the count stays right across several wraps`() {
        val jank = JankRatio(capacity = 3)
        repeat(10) { index -> jank.add(isJanky = index % 3 == 0) }
        // The window holds frames 7, 8 and 9, of which only 9 was janky.
        assertEquals(100f / 3f, jank.percent(), TOLERANCE)
    }

    @Test
    fun `clear empties the window`() {
        val jank = JankRatio(capacity = 4)
        jank.add(isJanky = true)
        jank.clear()
        assertEquals(0f, jank.percent(), TOLERANCE)

        jank.add(isJanky = false)
        assertEquals(0f, jank.percent(), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
