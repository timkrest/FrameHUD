// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementConfidenceTest {

    @Test
    fun `issuesAffecting only returns issues that taint the metric`() {
        assertFalse(MeasurementConfidence.CLEAN.isSuspect)

        val confidence = MeasurementConfidence.of(
            issues = listOf(ConfidenceIssue.Emulator, ConfidenceIssue.RefreshRateChanged.of(setOf(60, 120))),
        )
        assertTrue(confidence.isSuspect)
        assertEquals(listOf(ConfidenceIssue.Emulator), confidence.issuesAffecting(IntervalFigure.RENDER_PHASES))
        assertEquals(
            listOf(ConfidenceIssue.RefreshRateChanged.of(setOf(60, 120))),
            confidence.issuesAffecting(IntervalFigure.JANK_PERCENT),
        )
    }

    @Test
    fun `dropped reports, a slow listener, throttling and low battery taint every metric`() {
        val allMetrics = IntervalFigure.entries.toSet()
        assertEquals(allMetrics, ConfidenceIssue.DroppedReports.of(3).affected)
        assertEquals(allMetrics, ConfidenceIssue.SlowListener.of(80f).affected)
        assertEquals(allMetrics, ConfidenceIssue.ThermalThrottling.of(ThermalLevel.SEVERE).affected)
        assertEquals(allMetrics, ConfidenceIssue.LowBattery.of(powerSaveMode = true, levelPercent = null).affected)
    }

    @Test
    fun `an emulator leaves jank percent conclusive`() {
        assertEquals(setOf(IntervalFigure.RENDER_PHASES), ConfidenceIssue.Emulator.affected)
        assertFalse(IntervalFigure.JANK_PERCENT in ConfidenceIssue.Emulator.affected)
    }

    @Test
    fun `a refresh rate change leaves p95 and frozen frames conclusive`() {
        val affected = ConfidenceIssue.RefreshRateChanged.of(setOf(60, 120)).affected
        assertEquals(
            setOf(IntervalFigure.JANK_PERCENT, IntervalFigure.LOST_TIME, IntervalFigure.MAX_JANK_STREAK),
            affected,
        )
        assertFalse(IntervalFigure.P95 in affected)
        assertFalse(IntervalFigure.FROZEN_FRAMES in affected)
    }

    @Test
    fun `a short sample only taints the percentiles it cannot estimate yet`() {
        assertEquals(emptySet(), ConfidenceIssue.ShortSample.of(300).affected)
        assertEquals(setOf(IntervalFigure.P99), ConfidenceIssue.ShortSample.of(299).affected)

        assertEquals(setOf(IntervalFigure.P99), ConfidenceIssue.ShortSample.of(60).affected)
        assertEquals(
            setOf(IntervalFigure.P99, IntervalFigure.P95, IntervalFigure.JANK_PERCENT),
            ConfidenceIssue.ShortSample.of(59).affected,
        )

        assertEquals(
            setOf(IntervalFigure.P99, IntervalFigure.P95, IntervalFigure.JANK_PERCENT),
            ConfidenceIssue.ShortSample.of(20).affected,
        )
        assertEquals(
            setOf(IntervalFigure.P99, IntervalFigure.P95, IntervalFigure.JANK_PERCENT, IntervalFigure.P50),
            ConfidenceIssue.ShortSample.of(19).affected,
        )
    }
}
