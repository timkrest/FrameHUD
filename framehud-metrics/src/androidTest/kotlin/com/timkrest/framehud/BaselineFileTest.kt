package com.timkrest.framehud

import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.internal.baselineFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BaselineFileTest {

    private val application: Application
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Before
    fun startClean() {
        FrameHud.baselineOverride = null
        baselineFile(application).delete()
        FrameHud.reset()
    }

    @After
    fun removeBaseline() {
        FrameHud.baselineOverride = null
        baselineFile(application).delete()
    }

    @Test
    fun aSavedBaselineIsReadBackByTheNextRunAndReachesTheReport() {
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES)
            val saved = assertNotNull(await { FrameHud.saveBaseline() }, "nothing was collecting")
            assertTrue(saved.entries.containsKey(IntervalId.Session), "the session never reached the baseline")
            assertTrue(baselineFile(application).length() > 0L, "the baseline file is empty")

            FrameHud.baselineOverride = null
            FrameHud.reset()
            scenario.drawFrames(FRAMES)

            val compared = assertIs<BaselineComparison.Compared>(await { FrameHud.compareWithBaseline() })
            assertNotNull(compared.interval(IntervalId.Session), "the session was not compared")

            val export = assertNotNull(await { FrameHud.exportSession() }, "nothing was collecting")
            assertContains(export.json.readText(), """"baseline":{"comparable":true""")
        }
    }

    @Test
    fun savingTwiceWithoutAResetWeighsTheRunOnce() {
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES)
            assertNotNull(await { FrameHud.saveBaseline() }, "nothing was collecting")

            val again = assertNotNull(await { FrameHud.saveBaseline() }, "the baseline was not read back")

            assertEquals(1, again.entries.getValue(IntervalId.Session).runs)
        }
    }

    private companion object {
        const val FRAMES = 30
    }
}
