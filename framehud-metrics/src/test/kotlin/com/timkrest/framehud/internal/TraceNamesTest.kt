// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraceNamesTest {

    @Test
    fun `a name reaches the trace as the app wrote it, on a row of its own kind`() {
        assertEquals("framehud:screen:cart", screenSectionName("cart"))
        assertEquals("framehud:mark:cart", markSectionName("cart"))
        assertEquals("framehud:counter:cart", counterTrackName("cart"))
    }

    @Test
    fun `the longest name FrameHud takes still reaches the trace whole`() {
        assertEquals(110, MAX_TRACE_NAME_LENGTH, "the length FrameHud documents to callers moved")
        assertEquals(127, counterTrackName("q".repeat(MAX_TRACE_NAME_LENGTH)).length)
    }

    @Test
    fun `a name a trace would cut down to one another name could share is rejected`() {
        requireNameStandsApart(WHAT, "q".repeat(MAX_TRACE_NAME_LENGTH))

        assertFailsWith<IllegalArgumentException> {
            requireNameStandsApart(WHAT, "q".repeat(MAX_TRACE_NAME_LENGTH + 1))
        }
    }

    @Test
    fun `a name that would cut a trace record short is rejected, however short the name`() {
        listOf("decode|queue", "decode\nqueue", "decode\u0000queue", "decode\tqueue").forEach { name ->
            assertFailsWith<IllegalArgumentException>("accepted $name") { requireNameStandsApart(WHAT, name) }
        }
    }

    @Test
    fun `a name no report could tell apart is rejected where it is written`() {
        assertFailsWith<IllegalArgumentException> { requireNameStandsApart(WHAT, " ") }
    }

    @Test
    fun `a name FrameHud picks itself is passed over where an app's would be rejected`() {
        assertTrue(nameStandsApart("CartFragment"))
        listOf("", " ", "decode|queue", "q".repeat(MAX_TRACE_NAME_LENGTH + 1)).forEach { name ->
            assertFalse(nameStandsApart(name), "took \"$name\"")
        }
    }

    private companion object {
        const val WHAT = "A screen name"
    }
}
