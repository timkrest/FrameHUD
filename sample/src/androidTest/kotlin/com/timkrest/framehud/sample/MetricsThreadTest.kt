package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MetricsThreadTest {

    @get:Rule
    val config = FrameHudConfigRule()

    @Test
    fun renamingTheMetricsThreadKeepsCollecting() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(RENDER_MS)
            runOnMain { FrameHud.config = FrameHud.config.copy(metricsThreadName = RENAMED_THREAD) }
            FrameHud.reset()

            scenario.renderFrames(RENDER_MS)

            val stats = assertNotNull(FrameHud.awaitSessionStats(STATS_TIMEOUT_MS), "nothing was collecting")
            assertTrue(stats.frames > 0, "no frames reached the renamed thread")
        }
    }

    private companion object {
        const val RENAMED_THREAD = "framehud-renamed"
        const val RENDER_MS = 700L
        const val STATS_TIMEOUT_MS = 2_000L
    }
}
