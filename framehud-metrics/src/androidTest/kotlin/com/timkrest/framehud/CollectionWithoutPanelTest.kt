package com.timkrest.framehud

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CollectionWithoutPanelTest {

    @Before
    fun resetCollector() {
        FrameHud.reset()
    }

    @Test
    fun theSessionOutlivesTheScreen() {
        ActivityScenario.launch(BlankActivity::class.java).use {
            assertTrue(await { FrameHud.gateStats().isCollecting }, "no panel, so nothing started collecting")
        }

        assertTrue(await { FrameHud.gateStats().isCollecting }, "the session went away with the screen")
    }
}
