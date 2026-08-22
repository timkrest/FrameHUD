package com.timkrest.framehud.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PerfettoTriggerTest {

    @Test
    fun anAppIsAllowedToAskPerfettoToRetainItsTrace() {
        assumeTrue(
            "the platform ships no Perfetto trigger to run",
            File(SystemPerfettoTrigger.TRIGGER_BINARY).exists(),
        )

        val asked = SystemPerfettoTrigger.askOnThisThread("framehud_incident")

        assertTrue(asked, "the app was not allowed to run the trigger a flight recorder waits for")
    }
}
