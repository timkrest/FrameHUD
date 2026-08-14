package com.timkrest.framehud

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementConfidenceTest {

    @Test
    fun `issuesAffecting only returns issues that taint the metric`() {
        assertFalse(MeasurementConfidence.CLEAN.isSuspect)

        val confidence = MeasurementConfidence(
            issues = listOf(ConfidenceIssue.Emulator, ConfidenceIssue.RefreshRateChanged(setOf(60, 120))),
        )
        assertTrue(confidence.isSuspect)
        assertEquals(listOf(ConfidenceIssue.Emulator), confidence.issuesAffecting(MeasuredMetric.RENDER_PHASES))
        assertEquals(
            listOf(ConfidenceIssue.RefreshRateChanged(setOf(60, 120))),
            confidence.issuesAffecting(MeasuredMetric.JANK_PERCENT),
        )
    }

    @Test
    fun `dropped reports, a slow listener, throttling and low battery taint every metric`() {
        val allMetrics = MeasuredMetric.entries.toSet()
        assertEquals(allMetrics, ConfidenceIssue.DroppedReports(3).affected)
        assertEquals(allMetrics, ConfidenceIssue.SlowListener(80f).affected)
        assertEquals(allMetrics, ConfidenceIssue.ThermalThrottling(ThermalLevel.SEVERE).affected)
        assertEquals(allMetrics, ConfidenceIssue.LowBattery(powerSaveMode = true, levelPercent = null).affected)
    }

    @Test
    fun `an emulator leaves jank percent conclusive`() {
        assertEquals(setOf(MeasuredMetric.RENDER_PHASES), ConfidenceIssue.Emulator.affected)
        assertFalse(MeasuredMetric.JANK_PERCENT in ConfidenceIssue.Emulator.affected)
    }

    @Test
    fun `a refresh rate change leaves p95 and frozen frames conclusive`() {
        val affected = ConfidenceIssue.RefreshRateChanged(setOf(60, 120)).affected
        assertEquals(
            setOf(MeasuredMetric.JANK_PERCENT, MeasuredMetric.LOST_TIME, MeasuredMetric.MAX_JANK_STREAK),
            affected,
        )
        assertFalse(MeasuredMetric.P95 in affected)
        assertFalse(MeasuredMetric.FROZEN_FRAMES in affected)
    }

    @Test
    fun `a short sample only taints the percentiles it cannot estimate yet`() {
        assertEquals(emptySet(), ConfidenceIssue.ShortSample(300).affected)
        assertEquals(setOf(MeasuredMetric.P99), ConfidenceIssue.ShortSample(299).affected)

        assertEquals(setOf(MeasuredMetric.P99), ConfidenceIssue.ShortSample(60).affected)
        assertEquals(
            setOf(MeasuredMetric.P99, MeasuredMetric.P95, MeasuredMetric.JANK_PERCENT),
            ConfidenceIssue.ShortSample(59).affected,
        )

        assertEquals(
            setOf(MeasuredMetric.P99, MeasuredMetric.P95, MeasuredMetric.JANK_PERCENT),
            ConfidenceIssue.ShortSample(20).affected,
        )
        assertEquals(
            setOf(MeasuredMetric.P99, MeasuredMetric.P95, MeasuredMetric.JANK_PERCENT, MeasuredMetric.P50),
            ConfidenceIssue.ShortSample(19).affected,
        )
    }
}
