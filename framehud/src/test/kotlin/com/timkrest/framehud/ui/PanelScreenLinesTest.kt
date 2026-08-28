package com.timkrest.framehud.ui

import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelScreenLinesTest {

    @Test
    fun `a screen reads as its name and the numbers that ranked it`() {
        val line = rows(screen("cart", frames = 640, jankPercent = 18.4f, p95FrameMs = 31.2f, frozenFrames = 2))
            .single()

        assertContains(line, "cart")
        assertContains(line, "640")
        assertContains(line, "18.4")
        assertContains(line, "31.2")
        assertTrue(line.endsWith("2"), line)
    }

    @Test
    fun `a name too long for the column is cut short`() {
        val line = rows(screen("settings/notifications/email")).single()

        assertTrue(line.startsWith("settings/n$ELLIPSIS "), line)
    }

    @Test
    fun `the screens left out of the list are counted`() {
        val screens = List(12) { screen("screen$it") }

        assertEquals(formatMoreScreens(4), rows(*screens.toTypedArray()).last())
    }

    @Test
    fun `an empty run says nothing was measured`() {
        assertEquals(listOf(LABEL_NO_SCREENS), rows())
    }

    private fun rows(vararg screens: IntervalReport): List<String> =
        buildScreenLines(screens.toList()).values.map { it.text } - SCREEN_COLUMNS_HEADER_LINE

    private fun screen(
        name: String,
        frames: Int = 100,
        jankPercent: Float = 0f,
        p95FrameMs: Float = 0f,
        frozenFrames: Int = 0,
    ) = IntervalReport.of(
        id = IntervalId.Screen(name),
        stats = IntervalStats(
            frames = frames,
            jankPercent = jankPercent,
            p95FrameMs = p95FrameMs,
            frozenFrames = frozenFrames,
        ),
    )
}
