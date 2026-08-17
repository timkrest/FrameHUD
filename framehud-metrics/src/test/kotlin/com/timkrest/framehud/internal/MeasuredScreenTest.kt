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
    fun `a rename with no window bound names the next screen without activating one`() {
        assertIs<MeasuredScreen.Rename.WhileUnbound>(screen.rename("checkout"))

        assertNull(screen.active)
        assertEquals("checkout", screen.bind("Main"))
    }
}
