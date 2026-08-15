package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.instrumentation.DetectJankAfterTestSuccess
import com.timkrest.framehud.instrumentation.JankThresholds
import com.timkrest.framehud.instrumentation.SkipJankDetection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JankGateTest {

    @get:Rule
    val noJank = DetectJankAfterTestSuccess(
        thresholds = SMOKE_TEST_THRESHOLDS,
    )

    @Test
    fun theGateSeesTheFramesATestDrew() {
        ActivityScenario.launch(MainActivity::class.java).use { it.renderFrames(FRAMES_THE_GATE_NEEDS) }
    }

    @Test
    @SkipJankDetection
    fun anOptedOutTestIsLeftAlone() = Unit

    private companion object {
        val SMOKE_TEST_THRESHOLDS = JankThresholds(maxJankPercent = 100f, maxFrozenFrames = Int.MAX_VALUE)
        const val FRAMES_THE_GATE_NEEDS = ConfidenceIssue.ShortSample.MIN_FRAMES_P95 * 2
    }
}
