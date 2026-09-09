package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class PanelWindowBoundsTest {

    @Test
    fun `a panel stops where it meets the edge it is dragged towards`() {
        assertEquals(0, (-4000).insideHost(hostSize = HOST, panelSize = PANEL))
        assertEquals(HOST - PANEL, 4000.insideHost(hostSize = HOST, panelSize = PANEL))
    }

    @Test
    fun `a panel larger than the host has nowhere to travel`() {
        assertEquals(0, 4000.insideHost(hostSize = HOST, panelSize = HOST + 1))
    }

    private companion object {
        const val HOST = 1080
        const val PANEL = 600
    }
}
