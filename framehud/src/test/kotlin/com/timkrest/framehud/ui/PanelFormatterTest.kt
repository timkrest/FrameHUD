package com.timkrest.framehud.ui

import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelFormatterTest {

    @Test
    fun `metric rows line up under the header`() {
        val row = formatMetricLine(label = "layout", value = MetricValue(current = 1.2f, average = 3.4f, peak = 15.6f))
        assertEquals(CPU_COLUMNS_HEADER_LINE.length, row.length)
        assertEquals(CPU_COLUMNS_HEADER_LINE.length, GPU_UNAVAILABLE_LINE.length)

        val avgColumnEnd = CPU_COLUMNS_HEADER_LINE.indexOf("avg") + "avg".length
        assertEquals(avgColumnEnd, row.indexOf("3.4") + "3.4".length)
    }

    @Test
    fun `every label a metric row can carry leaves the columns where the header put them`() {
        val labels = PipelineStage.entries.flatMap { stage ->
            stagePhases(stage).map { it.label } + pipeLabel(stage)
        } + listOf(LABEL_DELAY, LABEL_OTHER, LABEL_TOTAL, LABEL_OVERRUN)

        labels.forEach { label ->
            val row = formatMetricLine(label, MetricValue(current = 1.2f, average = 3.4f, peak = 15.6f))
            assertEquals(CPU_COLUMNS_HEADER_LINE.length, row.length, row)
        }
    }

    @Test
    fun `a frame that ran for seconds drops its decimal rather than push the columns right`() {
        val row = formatMetricLine(LABEL_TOTAL, MetricValue(current = 1200.4f, average = 987.6f, peak = 60_000f))

        assertEquals(CPU_COLUMNS_HEADER_LINE.length, row.length, row)
        assertTrue(row.contains(" 1200 "), row)
        assertTrue(row.contains("987.6"), row)
    }

    @Test
    fun `a timing that never went positive still prints its peak column`() {
        val row = formatMetricLine(label = "over", value = MetricValue(current = -6.7f, average = -5.2f, peak = -1.1f))
        assertEquals(CPU_COLUMNS_HEADER_LINE.length, row.length)
        assertTrue(row.contains("-1.1"), row)
    }

    @Test
    fun `derived timings stop after two columns`() {
        val row = formatMetricLine(label = "other", value = MetricValue(current = 1.2f, average = 3.4f))
        assertTrue(row.length < CPU_COLUMNS_HEADER_LINE.length)
    }

    @Test
    fun `duration switches to minutes at sixty seconds`() {
        assertTrue(sessionTotals(durationMs = 59_900L).contains("59.9s"))
        assertTrue(sessionTotals(durationMs = 60_000L).contains("1m00s"))
        assertTrue(sessionTotals(durationMs = 61_400L).contains("1m01s"))
        assertTrue(sessionTotals(durationMs = 3_599_000L).contains("59m59s"))
    }

    @Test
    fun `lost time stays in milliseconds below a second, so a small loss is still readable`() {
        assertEquals("lost 40ms", formatLostTime(40f))
        assertEquals("lost 999ms", formatLostTime(999f))
        assertEquals("lost 1.0s", formatLostTime(1_000f))
        assertEquals("lost 12.5s", formatLostTime(12_500f))
    }

    @Test
    fun `no frames reads as idle`() {
        assertEquals("idle", formatFps(0))
        assertEquals("60 FPS", formatFps(60))
    }

    @Test
    fun `a long mark is cut to a fixed width instead of running off the panel`() {
        val cut = formatMark("a".repeat(64))
        assertTrue(cut.endsWith(ELLIPSIS), cut)
        assertEquals(cut.length, formatMark("b".repeat(200)).length)
    }

    @Test
    fun `thermal headroom is dropped when the platform reports none`() {
        val level = ThermalLevel.MODERATE
        assertEquals("therm moderate", formatThermal(ThermalStats(level = level, headroom = null)))
        assertEquals("therm moderate · hr 0.42", formatThermal(ThermalStats(level = level, headroom = 0.42f)))
    }

    @Test
    fun `numbers keep their dots on a comma locale`() {
        withLocale(Locale.GERMANY) {
            assertEquals(
                "ui 60/s · 16.7ms",
                formatTiming(choreographerTicksPerSecond = 60, frameBudgetMs = 16.7f),
            )
            assertEquals("jank   7.5%", formatJankShort(7.5f))
            val row = formatMetricLine(label = "draw", value = MetricValue(current = 1.5f, average = 2.5f))
            assertFalse(row.contains(','), row)
        }
    }

    private fun sessionTotals(durationMs: Long): String =
        formatSessionTotals(IntervalStats.EMPTY.copy(frames = 120, durationMs = durationMs))

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
