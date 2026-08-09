package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class FreezableReadingTest {

    private val reading = FreezableReading("first")

    @Test
    fun `freezing holds the published value while the live one follows the measurement`() {
        reading.setFrozen(true)

        reading.update("second")

        assertEquals("first", reading.published.value)
        assertEquals("second", reading.live)
    }

    @Test
    fun `thawing publishes the value measured while frozen`() {
        reading.setFrozen(true)
        reading.update("second")

        reading.setFrozen(false)

        assertEquals("second", reading.published.value)
    }

    @Test
    fun `a reset clears both readings even while frozen`() {
        reading.setFrozen(true)
        reading.update("second")

        reading.reset("empty")

        assertEquals("empty", reading.published.value)
        assertEquals("empty", reading.live)
    }
}
