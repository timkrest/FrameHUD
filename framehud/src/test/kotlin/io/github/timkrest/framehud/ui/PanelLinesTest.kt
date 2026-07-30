package io.github.timkrest.framehud.ui

import io.github.timkrest.framehud.MemoryStats
import io.github.timkrest.framehud.MetricValue
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.SessionStats
import io.github.timkrest.framehud.ThermalLevel
import io.github.timkrest.framehud.ThermalStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelLinesTest {

    @Test
    fun `the gpu row reads n a until the phase reports data`() {
        val withoutGpu = lines(metrics(isGpuAvailable = false, gpuMs = 4f)).gpuRow()
        assertTrue(withoutGpu.contains("n/a"), withoutGpu)

        val withGpu = lines(metrics(isGpuAvailable = true, gpuMs = 4f)).gpuRow()
        assertTrue(withGpu.contains("4.0"), withGpu)
    }

    @Test
    fun `dropped reports are called out only when the system dropped some`() {
        assertFalse(lines(metrics()).any { it.startsWith("drop") })
        assertTrue(lines(metrics(droppedReports = 3)).any { it == "drop x3" })
    }

    @Test
    fun `the thermal row waits for a level the platform knows`() {
        assertFalse(lines(metrics(), thermal = ThermalStats.EMPTY).any { it.startsWith("therm") })

        val throttled = ThermalStats(level = ThermalLevel.LIGHT, headroom = null)
        assertTrue(lines(metrics(), thermal = throttled).any { it == "therm light" })
    }

    @Test
    fun `the blamed phase is the only marked row`() {
        val marked = lines(metrics(jankPercent = 30f, layoutMs = 12f)).filter { it.endsWith(ATTENTION_MARKER) }
        assertEquals(1, marked.size, marked.toString())
        assertTrue(marked.single().startsWith("layout"), marked.single())
    }

    @Test
    fun `nothing is marked while the window is healthy`() {
        assertFalse(lines(metrics(jankPercent = 1f, layoutMs = 12f)).any { it.endsWith(ATTENTION_MARKER) })
    }

    @Test
    fun `one rendered line per panel line, so the decorations stay aligned`() {
        val panelLines = buildPanelLines(
            metrics = metrics(jankPercent = 30f, layoutMs = 12f, droppedReports = 2),
            memory = MemoryStats.EMPTY,
            thermal = ThermalStats(level = ThermalLevel.MODERATE, headroom = 0.5f),
        )
        assertEquals(panelLines.values.size, panelLines.toAnnotatedString().text.lines().size)
    }

    @Test
    fun `the load bar is a share of the frame budget`() {
        val row = MetricRowContext(frameBudgetMs = 16f, attentionLabel = null)
        assertEquals(0.5f, row.loadFractionOf(MetricValue(average = 8f)), TOLERANCE)
        assertEquals(1f, row.loadFractionOf(MetricValue(average = 40f)), TOLERANCE)

        val noBudget = MetricRowContext(frameBudgetMs = 0f, attentionLabel = null)
        assertEquals(0f, noBudget.loadFractionOf(MetricValue(average = 8f)), TOLERANCE)
    }

    private fun lines(
        metrics: PerformanceMetrics,
        memory: MemoryStats = MemoryStats.EMPTY,
        thermal: ThermalStats = ThermalStats.EMPTY,
    ): List<String> = buildPanelLines(metrics = metrics, memory = memory, thermal = thermal).values.map { it.text }

    private fun List<String>.gpuRow(): String = single { it.startsWith(LABEL_GPU) }

    private fun metrics(
        jankPercent: Float = 0f,
        layoutMs: Float = 0f,
        gpuMs: Float = 0f,
        isGpuAvailable: Boolean = false,
        droppedReports: Int = 0,
    ) = PerformanceMetrics(
        layout = MetricValue(current = layoutMs, average = layoutMs),
        gpu = MetricValue(current = gpuMs, average = gpuMs),
        windowJankPercent = jankPercent,
        session = SessionStats.EMPTY.copy(droppedReports = droppedReports),
        isGpuAvailable = isGpuAvailable,
        frameBudgetMs = 16.7f,
    )

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
