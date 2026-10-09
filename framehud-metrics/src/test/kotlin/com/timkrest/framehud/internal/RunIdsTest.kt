// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RunIdsTest {

    @Test
    fun `a run is named by its process and its number`() {
        assertEquals("0a1b2c3d:2", RunIds("0a1b2c3d").of(2))
    }

    @Test
    fun `two processes never share a run id`() {
        assertNotEquals(RunIds().of(1), RunIds().of(1))
    }

    @Test
    fun `an id this build gave is recognised, and anything else an app left with the system is not`() {
        assertTrue(RunIds.isRunId(RunIds().of(12)))
        listOf("", "level=3", "0a1b2c3d", "0A1B2C3D:1", "0a1b2c3d:1:2", "0a1b2c3d-4e5f:1").forEach { text ->
            assertFalse(RunIds.isRunId(text), "took \"$text\" for a run id")
        }
    }
}
