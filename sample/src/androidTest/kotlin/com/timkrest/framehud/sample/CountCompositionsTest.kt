// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.instrumentation.FrameHudResetRule
import com.timkrest.framehud.shared.drawFrames
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CountCompositionsTest {

    @get:Rule
    val reset = FrameHudResetRule()

    @Test
    fun eachRecompositionAddsOne() {
        ActivityScenario.launch(CompositionProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_TO_SETTLE)
            val before = awaitCount { it > 0 }

            repeat(CHANGES) {
                scenario.onActivity { it.change() }
                scenario.drawFrames(FRAMES_TO_SETTLE)
            }

            assertEquals(before + CHANGES, awaitCount { it == before + CHANGES })
        }
    }

    private fun awaitCount(done: (Int) -> Boolean): Int {
        val deadlineMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
        var count = 0
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            count = FrameHud.counters.value.firstOrNull { it.name == PROBE_COMPOSITIONS }?.value ?: 0
            if (done(count)) return count
            SystemClock.sleep(POLL_MS)
        }
        return count
    }

    private companion object {
        const val CHANGES = 3
        const val FRAMES_TO_SETTLE = 3
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
