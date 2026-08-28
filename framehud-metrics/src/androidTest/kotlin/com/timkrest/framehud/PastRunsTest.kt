package com.timkrest.framehud

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.internal.historyFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class PastRunsTest {

    private val application: Application
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Before
    fun startKeepingRuns() {
        historyFile(application).delete()
        setKeptRuns(KEPT_RUNS)
        FrameHud.reset()
    }

    @After
    fun stopKeepingRuns() {
        setKeptRuns(0)
        historyFile(application).delete()
    }

    @Test
    fun aRunThatLeftTheForegroundIsHistoryEvenWhenTheNextOneStartsAtOnce() {
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FrameHud.reset() }

        val previous = awaitRecordedRun()
        val session = assertNotNull(previous.interval(IntervalId.Session), "the run recorded no session")
        assertTrue(session.stats.frames > 0, "the run recorded no frame")
    }

    private fun setKeptRuns(kept: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            FrameHud.config = FrameHud.config.copy(keptRuns = kept)
        }
    }

    private fun awaitRecordedRun(): RecordedRun {
        val deadlineMs = SystemClock.uptimeMillis() + AWAIT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadlineMs) {
            await { FrameHud.history() }.firstOrNull()?.let { return it }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail("the run that ended is not history")
    }

    private companion object {
        const val FRAMES = 30
        const val KEPT_RUNS = 5
        const val POLL_INTERVAL_MS = 50L
    }
}
