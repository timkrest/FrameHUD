// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.compose

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrollMarkTest {

    private val marks = RecordedMarks()

    private val feed = ScrollMark("feed", marks)

    @Test
    fun `a scroll holds the mark until it stops`() {
        feed.scrolling(true)
        assertEquals("feed", marks.current)

        feed.scrolling(false)
        assertNull(marks.current)
    }

    @Test
    fun `a list standing still leaves a mark set elsewhere alone`() {
        marks.current = "checkout"

        feed.scrolling(false)
        feed.release()

        assertEquals("checkout", marks.current)
        assertEquals(listOf<String?>("checkout"), marks.assigned)
    }

    @Test
    fun `a scroll replaces the mark before it, and stopping leaves none`() {
        marks.current = "checkout"

        feed.scrolling(true)
        feed.scrolling(false)

        assertEquals(listOf("checkout", "feed", null), marks.assigned)
    }

    @Test
    fun `a mark set during the scroll outlives it`() {
        feed.scrolling(true)
        marks.current = "checkout"

        feed.scrolling(false)
        feed.release()

        assertEquals("checkout", marks.current)
    }

    @Test
    fun `leaving the composition mid-scroll lets go of the mark`() {
        feed.scrolling(true)

        feed.release()

        assertNull(marks.current)
    }

    private class RecordedMarks : Marks {
        val assigned = mutableListOf<String?>()

        override var current: String? = null
            set(value) {
                field = value
                assigned += value
            }
    }
}
