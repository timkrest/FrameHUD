// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class ProcessStateSummaryTest {

    private val published = mutableListOf<ShownState>()

    private val summary = ProcessStateSummary(firstRun = RUN) { published += ShownState.fromSummary(it) }

    @Test
    fun `nothing is left with the system while runs are not kept`() {
        summary.screenChanged("cart")
        summary.markChanged("scroll")

        assertEquals(emptyList(), published)
    }

    @Test
    fun `keeping runs leaves what is shown at that moment, and every change after it`() {
        summary.screenChanged("cart")
        summary.setPublishing(true)
        summary.markChanged("scroll")
        summary.markChanged("scroll")
        summary.screenChanged(null)

        assertEquals(
            listOf(ShownState(RUN, "cart", null), ShownState(RUN, "cart", "scroll"), ShownState(RUN, null, "scroll")),
            published,
        )
    }

    @Test
    fun `a reset leaves the next run's id`() {
        summary.setPublishing(true)
        summary.runChanged("0a1b2c3d:2")

        assertEquals("0a1b2c3d:2", published.last().run)
    }

    @Test
    fun `no longer keeping runs takes back what was left`() {
        summary.screenChanged("cart")
        summary.setPublishing(true)

        summary.setPublishing(false)

        assertEquals(ShownState.NOTHING, published.last())
    }

    private companion object {
        const val RUN = "0a1b2c3d:1"
    }
}
