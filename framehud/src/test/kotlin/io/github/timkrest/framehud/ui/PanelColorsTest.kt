package io.github.timkrest.framehud.ui

import org.junit.Test
import kotlin.test.assertEquals

class PanelColorsTest {

    @Test
    fun `fps is judged against the refresh rate, not a fixed sixty`() {
        assertEquals(TextGood, fpsColor(fps = 60, refreshRateHz = 60f))
        assertEquals(TextWarning, fpsColor(fps = 60, refreshRateHz = 120f))
        assertEquals(TextNormal, fpsColor(fps = 100, refreshRateHz = 120f))
        assertEquals(TextGood, fpsColor(fps = 118, refreshRateHz = 120f))
        assertEquals(TextHeader, fpsColor(fps = 0, refreshRateHz = 120f))
    }
}
