package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsableFrameWatchTest {

    private val clock = TestMetricsClock()
    private val watch = UsableFrameWatch(clock)
    private val window = Any()

    @Test
    fun `the frame after the report ends the measurement with time from the screen start`() {
        val start = ScreenStart(startedAtNs = ms(0))
        watch.expectScreen(window, start)
        clock.nanos = ms(100)
        watch.reportUsable(start)

        assertEquals(110f, watch.onFrame(window, frameEndNs = ms(110)))
    }

    @Test
    fun `a frame displayed before the report does not end the measurement`() {
        val start = ScreenStart(startedAtNs = ms(0))
        watch.expectScreen(window, start)
        clock.nanos = ms(100)
        watch.reportUsable(start)

        assertNull(watch.onFrame(window, frameEndNs = ms(90)))
        assertEquals(116f, watch.onFrame(window, frameEndNs = ms(116)))
    }

    @Test
    fun `a frame without a report does not end the measurement`() {
        watch.expectScreen(window, ScreenStart(startedAtNs = ms(0)))

        assertNull(watch.onFrame(window, frameEndNs = ms(50)))
    }

    @Test
    fun `a screen measures usable once`() {
        watch.expectScreen(window, ScreenStart(startedAtNs = ms(0)))
        watch.reportUsable()
        watch.onFrame(window, frameEndNs = ms(10))

        watch.reportUsable()
        assertNull(watch.onFrame(window, frameEndNs = ms(20)))
    }

    @Test
    fun `a report naming the start of a restarted screen is ignored`() {
        val launch = ScreenStart(startedAtNs = ms(0))
        watch.expectScreen(window, launch)
        watch.restartScreen()
        watch.reportUsable(launch)

        assertNull(watch.onFrame(window, frameEndNs = ms(10)))
    }

    @Test
    fun `a report naming the start of a replaced screen is ignored`() {
        val previous = ScreenStart(startedAtNs = ms(0))
        watch.expectScreen(window, previous)
        val nextWindow = Any()
        watch.expectScreen(nextWindow, ScreenStart(startedAtNs = ms(0)))
        watch.reportUsable(previous)

        assertNull(watch.onFrame(nextWindow, frameEndNs = ms(10)))
    }

    @Test
    fun `a report naming no start applies to the measured screen`() {
        watch.expectScreen(window, ScreenStart(startedAtNs = ms(0)))
        watch.reportUsable()

        assertEquals(10f, watch.onFrame(window, frameEndNs = ms(10)))
    }

    @Test
    fun `a restarted screen measures again, from the restart`() {
        watch.expectScreen(window, ScreenStart(startedAtNs = ms(0)))
        watch.reportUsable()
        watch.onFrame(window, frameEndNs = ms(10))

        clock.nanos = ms(500)
        watch.restartScreen()
        watch.reportUsable()

        assertEquals(40f, watch.onFrame(window, frameEndNs = ms(540)))
    }

    @Test
    fun `a frame from a window no longer measured does not end the measurement`() {
        watch.expectScreen(window, ScreenStart(startedAtNs = ms(0)))
        watch.reportUsable()

        assertNull(watch.onFrame(Any(), frameEndNs = ms(10)))
        assertEquals(12f, watch.onFrame(window, frameEndNs = ms(12)))
    }

    @Test
    fun `a forgotten screen reports nothing`() {
        val start = ScreenStart(startedAtNs = ms(0))
        watch.expectScreen(window, start)
        watch.forgetScreen()
        watch.reportUsable(start)

        assertNull(watch.onFrame(window, frameEndNs = ms(10)))
    }

    private fun ms(value: Long): Long = value * 1_000_000L
}
