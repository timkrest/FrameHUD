package com.timkrest.framehud.internal

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardTest {

    private val reported = mutableListOf<Pair<String, Throwable>>()

    @After
    fun forgetTheListener() {
        GuardedFailures.reportTo(null)
    }

    @Test
    fun `a call that fails against a library the app ships another version of is caught, not the app's crash`() {
        GuardedFailures.reportTo { what, error -> reported += what to error }
        val missing: Throwable = NoSuchMethodError("androidx.compose.runtime.SnapshotKt.current")

        assertFalse(guarded("reading frame metrics") { throw missing })

        assertEquals(listOf("reading frame metrics" to missing), reported)
    }

    @Test
    fun `an error the process cannot go on after is left to the app`() {
        GuardedFailures.reportTo { what, error -> reported += what to error }

        assertFailsWith<OutOfMemoryError> { guarded("reading frame metrics") { throw OutOfMemoryError() } }

        assertTrue(reported.isEmpty(), reported.toString())
    }
}
