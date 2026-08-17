package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.BaselineMetric
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.instrumentation.BaselineThresholds
import com.timkrest.framehud.instrumentation.JankAssertions
import com.timkrest.framehud.instrumentation.JankThresholds
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class BaselineGateTest {

    @Before
    fun startClean() {
        FrameHud.reset()
    }

    @After
    fun forgetTheBaseline() {
        FrameHud.baselineOverride = null
    }

    @Test
    fun aRunSlowerThanItsBaselineFailsTheGate() {
        FrameHud.baselineOverride = baselineOf(p95FrameMs = UNREACHABLE_P95_MS)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(FRAMES_THE_GATE_NEEDS)

            val failure = assertFailsWith<AssertionError> {
                JankAssertions.assertNoJank(TAG, JankThresholds.baselineOnly(P95_ONLY))
            }
            assertContains(assertNotNull(failure.message), "p95")
        }
    }

    @Test
    fun aRunWithinItsBaselinePassesTheGate() {
        FrameHud.baselineOverride = baselineOf(p95FrameMs = FORGIVING_P95_MS)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.renderFrames(FRAMES_THE_GATE_NEEDS)

            JankAssertions.assertNoJank(TAG, JankThresholds.baselineOnly(P95_ONLY))
        }
    }

    private fun baselineOf(p95FrameMs: Float) = Baseline(
        environment = BaselineEnvironment.current(),
        entries = mapOf(
            IntervalId.Session to BaselineEntry.of(
                SessionStats.EMPTY.copy(frames = FRAMES_THE_GATE_NEEDS, p95FrameMs = p95FrameMs),
            ),
        ),
    )

    private companion object {
        const val TAG = "baseline gate"
        const val UNREACHABLE_P95_MS = 0.1f
        const val FORGIVING_P95_MS = 10_000f
        const val FRAMES_THE_GATE_NEEDS = ConfidenceIssue.ShortSample.MIN_FRAMES_P95 * 2
        val P95_ONLY = BaselineThresholds(metrics = setOf(BaselineMetric.P95_MS))
    }
}
