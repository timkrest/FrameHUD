package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MetricsThreadTest {

    @get:Rule
    val config = FrameHudConfigRule()

    @Test
    fun renamingTheMetricsThreadKeepsCollecting() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames()
            runOnMain { FrameHud.config = FrameHud.config.copy(metricsThreadName = RENAMED_THREAD) }
            FrameHud.reset()

            scenario.renderFrames()

            val stats = await { FrameHud.sessionStats() }
            assertTrue(stats.frames > 0, "no frames reached the renamed thread")
        }
    }

    private companion object {
        const val RENAMED_THREAD = "framehud-renamed"
    }
}
