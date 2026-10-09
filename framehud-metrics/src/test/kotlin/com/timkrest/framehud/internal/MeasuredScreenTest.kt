// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MeasuredScreenTest {

    private val screen = MeasuredScreen()

    @Test
    fun `a bound screen is named by its activity until overridden`() {
        assertEquals("Main", screen.bind("Main"))
        assertEquals("Main", screen.active)
        assertNull(screen.screenOverride)
    }

    @Test
    fun `renaming a bound screen ends the previous label and activates the new one`() {
        screen.bind("Main")

        val rename = assertIs<MeasuredScreen.Rename.Renamed>(screen.rename("checkout"))

        assertEquals("Main", rename.previous)
        assertEquals("checkout", rename.current)
        assertEquals("checkout", screen.active)
    }

    @Test
    fun `assigning the name already in effect changes nothing`() {
        screen.bind("Main")
        screen.rename("checkout")

        assertIs<MeasuredScreen.Rename.None>(screen.rename("checkout"))
        assertEquals("checkout", screen.active)
    }

    @Test
    fun `an override equal to the activity name is recorded without a restart`() {
        screen.bind("Main")

        assertIs<MeasuredScreen.Rename.None>(screen.rename("Main"))
        assertEquals("Main", screen.screenOverride)

        assertIs<MeasuredScreen.Rename.None>(screen.rename(null))
        assertEquals("Main", screen.active)
    }

    @Test
    fun `the override survives a new activity`() {
        screen.bind("Main")
        screen.rename("checkout")
        screen.unbind()

        assertEquals("checkout", screen.bind("Details"))
    }

    @Test
    fun `clearing the override returns to the activity name`() {
        screen.bind("Main")
        screen.rename("checkout")

        val rename = assertIs<MeasuredScreen.Rename.Renamed>(screen.rename(null))

        assertEquals("checkout", rename.previous)
        assertEquals("Main", rename.current)
    }

    @Test
    fun `unbinding reports the ended label and deactivates it`() {
        screen.bind("Main")
        screen.rename("checkout")

        assertEquals("checkout", screen.unbind())
        assertNull(screen.active)
    }

    @Test
    fun `a fragment taking over the activity renames the bound screen`() {
        screen.bind("Main")

        val rename = assertIs<MeasuredScreen.Rename.Renamed>(screen.rebind("Cart"))

        assertEquals("Main", rename.previous)
        assertEquals("Cart", rename.current)
        assertEquals("Cart", screen.active)
    }

    @Test
    fun `rebinding to the name in effect changes nothing`() {
        screen.bind("Cart")

        assertIs<MeasuredScreen.Rename.None>(screen.rebind("Cart"))
        assertEquals("Cart", screen.active)
    }

    @Test
    fun `an override holds while the fragment under it changes, and clearing it returns to that fragment`() {
        screen.bind("Cart")
        screen.rename("checkout")

        assertIs<MeasuredScreen.Rename.None>(screen.rebind("Payment"))
        assertEquals("checkout", screen.active)

        val rename = assertIs<MeasuredScreen.Rename.Renamed>(screen.rename(null))
        assertEquals("checkout", rename.previous)
        assertEquals("Payment", rename.current)
    }

    @Test
    fun `rebinding with no window bound names nothing`() {
        assertIs<MeasuredScreen.Rename.None>(screen.rebind("Cart"))

        assertNull(screen.active)
        assertEquals("Main", screen.bind("Main"))
    }

    @Test
    fun `a rename with no window bound names the next screen without activating one`() {
        assertIs<MeasuredScreen.Rename.WhileUnbound>(screen.rename("checkout"))

        assertNull(screen.active)
        assertEquals("checkout", screen.bind("Main"))
    }
}
