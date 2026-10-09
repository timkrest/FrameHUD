// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.instrumentation.FrameHudResetRule
import com.timkrest.framehud.shared.drawFrames
import com.timkrest.framehud.shared.pollFor
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

    private fun awaitCount(done: (Int) -> Boolean): Int = pollFor { compositions().takeIf(done) } ?: compositions()

    private fun compositions(): Int = FrameHud.counters.value.firstOrNull { it.name == PROBE_COMPOSITIONS }?.value ?: 0

    private companion object {
        const val CHANGES = 3
        const val FRAMES_TO_SETTLE = 3
    }
}
