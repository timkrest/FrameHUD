package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.instrumentation.DetectJankAfterTestSuccess
import com.timkrest.framehud.instrumentation.JankThresholds
import com.timkrest.framehud.instrumentation.SkipJankDetection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Thresholds are deliberately loose: this checks that the gate runs, not how fast an emulator is. */
@RunWith(AndroidJUnit4::class)
class JankGateTest {

    @get:Rule
    val noJank = DetectJankAfterTestSuccess(
        JankThresholds(maxJankPercent = 100f, maxFrozenFrames = Int.MAX_VALUE),
    )

    @Test
    fun theGateSeesTheFramesATestDrew() {
        ActivityScenario.launch(MainActivity::class.java).use { it.renderFrames(RENDER_MS) }
    }

    /** Without the opt-out the gate would fail this test for collecting nothing. */
    @Test
    @SkipJankDetection
    fun anOptedOutTestIsLeftAlone() = Unit

    private companion object {
        const val RENDER_MS = 700L
    }
}
