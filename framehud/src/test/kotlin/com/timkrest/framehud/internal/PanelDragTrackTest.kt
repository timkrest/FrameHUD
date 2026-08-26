package com.timkrest.framehud.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Test
import kotlin.test.assertEquals

class PanelDragTrackTest {

    @Test
    fun `a panel moves by what the finger travelled on screen`() {
        val track = trackGrabbedAt(Offset(x = 700f, y = 400f))

        assertEquals(GRABBED_FROM_END + 40f, track.fromEndAt(screenX = 660f, panel = PANEL))
        assertEquals(GRABBED_FROM_TOP + 90f, track.fromTopAt(screenY = 490f, panel = PANEL))
    }

    @Test
    fun `a panel stops where it meets the edge it is dragged towards`() {
        val track = trackGrabbedAt(Offset(x = 700f, y = 400f))

        assertEquals(HOST.width - PANEL.width.toFloat(), track.fromEndAt(screenX = -4000f, panel = PANEL))
        assertEquals(0f, track.fromEndAt(screenX = 4000f, panel = PANEL))
        assertEquals(HOST.height - PANEL.height.toFloat(), track.fromTopAt(screenY = 4000f, panel = PANEL))
        assertEquals(0f, track.fromTopAt(screenY = -4000f, panel = PANEL))
    }

    @Test
    fun `a panel that grew while it was dragged is kept inside the host`() {
        val track = trackGrabbedAt(Offset(x = 700f, y = 400f))
        val grown = IntSize(width = PANEL.width + 200, height = PANEL.height + 100)

        assertEquals(HOST.width - grown.width.toFloat(), track.fromEndAt(screenX = -4000f, panel = grown))
        assertEquals(HOST.height - grown.height.toFloat(), track.fromTopAt(screenY = 4000f, panel = grown))
    }

    @Test
    fun `a panel wider than the host has nowhere to travel`() {
        val track = trackGrabbedAt(Offset(x = 700f, y = 400f))
        val oversized = IntSize(width = HOST.width + 1, height = HOST.height + 1)

        assertEquals(0f, track.fromEndAt(screenX = -4000f, panel = oversized))
        assertEquals(0f, track.fromTopAt(screenY = 4000f, panel = oversized))
    }

    private fun trackGrabbedAt(screen: Offset) = PanelDragTrack(
        host = HOST,
        grabbedAt = screen,
        grabbedFromEnd = GRABBED_FROM_END,
        grabbedFromTop = GRABBED_FROM_TOP,
    )

    private companion object {
        val HOST = IntSize(width = 1080, height = 2200)
        val PANEL = IntSize(width = 600, height = 400)
        const val GRABBED_FROM_END = 24f
        const val GRABBED_FROM_TOP = 144f
    }
}
