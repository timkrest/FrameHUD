// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.instrumentation.FrameHudResetRule
import com.timkrest.framehud.shared.drawFrames
import com.timkrest.framehud.shared.runOnMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MarkEventsTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val deliveryThreads = CopyOnWriteArrayList<String>()
    private val listener = FrameHudEventListener {
        deliveryThreads += Thread.currentThread().name
        events += it
    }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(FrameHudResetRule())
        .around(FrameHudConfigRule { it.copy(eventListeners = it.eventListeners + listener) })

    @Test
    fun aMarkLeftOpenEndsWithTheScreenItRanOn() {
        ActivityScenario.launch(ReportingProbeActivity::class.java).use { scenario ->
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
            runOnMain { FrameHud.mark = MARK }
            scenario.drawFrames(FRAMES_FOR_AN_EVENT)
        }

        val ended = events.awaitEvents<FrameHudEvent.MarkEnded>(count = 1).single()
        assertEquals(MARK, ended.mark)
        assertEquals(ReportingProbeActivity::class.java.simpleName, ended.screen)
        assertTrue(ended.stats.frames > 0, "the mark reported no frames")

        val intervals = events.filter { it is FrameHudEvent.MarkEnded || it is FrameHudEvent.ScreenEnded }
        assertIs<FrameHudEvent.MarkEnded>(intervals.first(), "the screen was summed up before the mark inside it")

        val metricsThread = FrameHud.config.metricsThreadName
        assertEquals(listOf(metricsThread), deliveryThreads.distinct(), "an event skipped the metrics thread")
    }

    private companion object {
        const val MARK = "checkout"
    }
}
