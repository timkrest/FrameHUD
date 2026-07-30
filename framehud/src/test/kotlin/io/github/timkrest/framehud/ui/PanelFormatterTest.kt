package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.SessionStats
import io.github.timkrest.framehud.ThermalLevel
import io.github.timkrest.framehud.ThermalStats
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
    fun `no frames reads as idle`() {
        assertEquals("idle", formatFps(0))
        assertEquals("60 FPS", formatFps(60))
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
            assertEquals("ui 60/s · 16.7ms", formatTiming(vsyncRate = 60, frameBudgetMs = 16.7f))
            assertEquals("jank 7.5%", formatJankShort(7.5f))
            val row = formatMetricLine(label = "draw", value = MetricValue(current = 1.5f, average = 2.5f))
            assertFalse(row.contains(','), row)
        }
    }

    private fun sessionTotals(durationMs: Long): String =
        formatSessionTotals(SessionStats.EMPTY.copy(frames = 120, durationMs = durationMs))

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
