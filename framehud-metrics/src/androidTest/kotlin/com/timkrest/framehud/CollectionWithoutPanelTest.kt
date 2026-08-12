package com.timkrest.framehud

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class CollectionWithoutPanelTest {

    @Before
    fun resetCollector() {
        FrameHud.reset()
    }

    @Test
    fun aResumedScreenStartsCollecting() {
        ActivityScenario.launch(BlankActivity::class.java).use {
            assertNotNull(FrameHud.awaitSessionStats(TIMEOUT_MS), "no panel, so nothing started collecting")
        }
    }

    @Test
    fun theSessionOutlivesTheScreen() {
        ActivityScenario.launch(BlankActivity::class.java).use {
            assertNotNull(FrameHud.awaitSessionStats(TIMEOUT_MS), "no panel, so nothing started collecting")
        }

        assertNotNull(FrameHud.awaitSessionStats(TIMEOUT_MS), "the session went away with the screen")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
