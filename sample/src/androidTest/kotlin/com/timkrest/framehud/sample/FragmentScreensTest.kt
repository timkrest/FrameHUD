// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.instrumentation.FrameHudResetRule
import com.timkrest.framehud.shared.runOnMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FragmentScreensTest {

    private val events = CopyOnWriteArrayList<FrameHudEvent>()
    private val listener = FrameHudEventListener { events += it }

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(FrameHudResetRule())
        .around(FrameHudConfigRule { it.copy(eventListeners = it.eventListeners + listener) })

    @Test
    fun eachFragmentShownIsAScreenOfItsOwn() {
        ActivityScenario.launch(FragmentProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            scenario.onActivity { it.show(CheckoutProbeFragment()) }
            scenario.renderCollectedFrames()
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(listOf(CART, CHECKOUT), ended.map { it.screen })
        assertTrue(ended.all { it.stats.frames > 0 }, "a fragment screen reported no frames")
    }

    @Test
    fun aHostIsNamedAfterTheDestinationItShows() {
        ActivityScenario.launch(FragmentProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            scenario.onActivity { it.show(HostProbeFragment()) }
            scenario.renderCollectedFrames()
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(listOf(CART, CHECKOUT), ended.map { it.screen })
    }

    @Test
    fun aNamedScreenHoldsWhileFragmentsChangeUnderIt() {
        runOnMain { FrameHud.screen = "cart" }
        ActivityScenario.launch(FragmentProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            scenario.onActivity { it.show(CheckoutProbeFragment()) }
            scenario.renderCollectedFrames()
            runOnMain { FrameHud.screen = null }
            scenario.renderCollectedFrames()
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(listOf("cart", CHECKOUT), ended.map { it.screen })
    }

    @Test
    fun turningFragmentNamesOffLeavesTheActivityName() {
        runOnMain { FrameHud.config = FrameHud.config.copy(nameScreensByFragment = false) }
        ActivityScenario.launch(FragmentProbeActivity::class.java).use { scenario ->
            scenario.renderCollectedFrames()
            scenario.onActivity { it.show(CheckoutProbeFragment()) }
            scenario.renderCollectedFrames()
            runOnMain { FrameHud.config = FrameHud.config.copy(nameScreensByFragment = true) }
            scenario.renderCollectedFrames()
        }

        val ended = events.awaitEvents<FrameHudEvent.ScreenEnded>(count = 2)
        assertEquals(listOf(FragmentProbeActivity::class.java.simpleName, CHECKOUT), ended.map { it.screen })
    }

    private companion object {
        val CART: String = CartProbeFragment::class.java.simpleName
        val CHECKOUT: String = CheckoutProbeFragment::class.java.simpleName
    }
}
