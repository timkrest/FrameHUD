package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.ThermalLevel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionAccumulatorTest {

    @Test
    fun `jank percent is the share of janky frames`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addFrame(totalMs = 10f, isJanky = true)
        session.addFrame(totalMs = 10f, isJanky = false)
        session.addFrame(totalMs = 10f, isJanky = true)
        session.addFrame(totalMs = 10f, isJanky = false)
        assertEquals(50f, session.stats().jankPercent, TOLERANCE)
    }

    @Test
    fun `frozen frames are counted above 700 ms`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addFrame(totalMs = 699f, isJanky = true)
        session.addFrame(totalMs = 701f, isJanky = true)
        assertEquals(1, session.stats().frozenFrames)
    }

    @Test
    fun `max jank streak tracks the longest run`() {
        val session = SessionAccumulator(TestMetricsClock())
        listOf(true, true, false, true).forEach { session.addFrame(totalMs = 10f, isJanky = it) }
        assertEquals(2, session.stats().maxJankStreak)
    }

    @Test
    fun `percentiles come from the frame histogram clamped to the observed max`() {
        val session = SessionAccumulator(TestMetricsClock())
        repeat(99) { session.addFrame(totalMs = 10f, isJanky = false) }
        session.addFrame(totalMs = 100f, isJanky = true)
        assertEquals(100, session.stats().frames)
        assertEquals(10.25f, session.stats().p50FrameMs, TOLERANCE)
    }

    @Test
    fun `duration is zero when collection never started`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addFrame(totalMs = 10f, isJanky = false)
        assertEquals(0L, session.stats().durationMs)
    }

    @Test
    fun `a session under 300 frames is a short sample, at 300 it is not`() {
        val short = SessionAccumulator(TestMetricsClock())
        repeat(299) { short.addFrame(totalMs = 10f, isJanky = false) }
        assertIs<ConfidenceIssue.ShortSample>(short.stats().confidence.issues.single())

        val full = SessionAccumulator(TestMetricsClock())
        repeat(300) { full.addFrame(totalMs = 10f, isJanky = false) }
        assertTrue(full.stats().confidence.issues.isEmpty())
    }

    @Test
    fun `a small rounding drift in the refresh rate does not count as a change`() {
        val session = SessionAccumulator(TestMetricsClock())
        repeat(300) { session.addFrame(totalMs = 10f, isJanky = false, refreshRateHz = 59.94f) }
        session.addFrame(totalMs = 10f, isJanky = false, refreshRateHz = 60f)
        assertTrue(session.stats().confidence.issues.none { it is ConfidenceIssue.RefreshRateChanged })
    }

    @Test
    fun `an actual refresh rate change is reported with every rate seen`() {
        val session = SessionAccumulator(TestMetricsClock())
        repeat(300) { session.addFrame(totalMs = 10f, isJanky = false, refreshRateHz = 60f) }
        session.addFrame(totalMs = 10f, isJanky = false, refreshRateHz = 120f)
        val issue = session.stats().confidence.issues.filterIsInstance<ConfidenceIssue.RefreshRateChanged>().single()
        assertEquals(setOf(60, 120), issue.ratesHz)
    }

    @Test
    fun `dropped reports become a confidence issue`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addDroppedReports(3)
        val issue = session.stats().confidence.issues.filterIsInstance<ConfidenceIssue.DroppedReports>().single()
        assertEquals(3, issue.count)
    }

    @Test
    fun `only throttling levels raise a thermal issue, and the worst one wins`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addThermalLevel(ThermalLevel.LIGHT)
        assertTrue(
            session.stats().confidence.issues.none { it is ConfidenceIssue.ThermalThrottling },
            "light is not throttling",
        )

        session.addThermalLevel(ThermalLevel.MODERATE)
        session.addThermalLevel(ThermalLevel.SEVERE)
        session.addThermalLevel(ThermalLevel.MODERATE)
        val issue = session.stats().confidence.issues.filterIsInstance<ConfidenceIssue.ThermalThrottling>().single()
        assertEquals(ThermalLevel.SEVERE, issue.worstLevel)
    }

    @Test
    fun `a slow listener is reported with the worst duration seen`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addSlowListener(60f)
        session.addSlowListener(90f)
        session.addSlowListener(70f)
        val issue = session.stats().confidence.issues.filterIsInstance<ConfidenceIssue.SlowListener>().single()
        assertEquals(90f, issue.longestCallMs, TOLERANCE)
    }

    @Test
    fun `low battery is reported for power save mode or a level at or under 15 percent`() {
        val powerSave = SessionAccumulator(TestMetricsClock())
        powerSave.addBattery(BatterySample(powerSaveMode = true, levelPercent = 80))
        assertTrue(powerSave.stats().confidence.issues.any { it is ConfidenceIssue.LowBattery })

        val healthy = SessionAccumulator(TestMetricsClock())
        healthy.addBattery(BatterySample(powerSaveMode = false, levelPercent = 16))
        assertTrue(healthy.stats().confidence.issues.none { it is ConfidenceIssue.LowBattery })

        val lowLevel = SessionAccumulator(TestMetricsClock())
        lowLevel.addBattery(BatterySample(powerSaveMode = false, levelPercent = 15))
        val issue = lowLevel.stats().confidence.issues.filterIsInstance<ConfidenceIssue.LowBattery>().single()
        assertEquals(15, issue.levelPercent)
    }

    @Test
    fun `an emulator is reported on every session regardless of frames`() {
        val session = SessionAccumulator(TestMetricsClock(), isEmulator = true)
        assertTrue(session.stats().confidence.issues.contains(ConfidenceIssue.Emulator))
    }

    @Test
    fun `clear resets every tracked issue but the short sample an empty session always carries`() {
        val session = SessionAccumulator(TestMetricsClock())
        session.addDroppedReports(1)
        session.addThermalLevel(ThermalLevel.SEVERE)
        session.addSlowListener(90f)
        session.addBattery(BatterySample(powerSaveMode = true, levelPercent = 5))
        session.addFrame(totalMs = 10f, isJanky = false, refreshRateHz = 60f)
        session.addFrame(totalMs = 10f, isJanky = false, refreshRateHz = 120f)

        session.clear()

        val issue = assertIs<ConfidenceIssue.ShortSample>(session.stats().confidence.issues.single())
        assertEquals(0, issue.frames)
    }

    private fun SessionAccumulator.addFrame(totalMs: Float, isJanky: Boolean, refreshRateHz: Float = DEFAULT_REFRESH_RATE_HZ) {
        addFrame(totalMs = totalMs, isJanky = isJanky, refreshRateHz = refreshRateHz)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
        const val DEFAULT_REFRESH_RATE_HZ = 60f
    }
}
