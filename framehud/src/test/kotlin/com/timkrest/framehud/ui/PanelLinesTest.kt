package com.timkrest.framehud.ui

import androidx.compose.ui.graphics.Color
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PanelLinesTest {

    @Test
    fun `the gpu row reads n a until the phase reports data`() {
        val withoutGpu = lines(metrics(gpuMs = null)).gpuRow()
        assertTrue(withoutGpu.contains("n/a"), withoutGpu)

        val withGpu = lines(metrics(gpuMs = 4f)).gpuRow()
        assertTrue(withGpu.contains("4.0"), withGpu)
    }

    @Test
    fun `the process rows wait for a reading and leave out what the platform withheld`() {
        assertFalse(lines(metrics()).any { it.startsWith(LABEL_PROCESS_CPU) || it.startsWith(LABEL_THREADS) })

        val sampled = lines(metrics(), process = ProcessStats.of(cpuPercent = 42f, peakCpuPercent = 61f, threads = 38))
        assertTrue(sampled.any { it == "$LABEL_PROCESS_CPU 42% ▲61" }, sampled.toString())
        assertTrue(sampled.any { it == "$LABEL_THREADS 38" }, sampled.toString())
    }

    @Test
    fun `a counter row marks a peak only once it stands above the value`() {
        val rows = lines(
            metrics(),
            counters = listOf(
                CounterReading.of(name = "decode queue", value = 4, peakSinceReset = 31),
                CounterReading.of(name = "cache misses", value = 12, peakSinceReset = 12),
            ),
        )

        assertTrue(rows.any { it.startsWith("decode queue") && it.endsWith("4 \u25B231") }, rows.toString())
        assertTrue(rows.any { it.startsWith("cache misses") && it.endsWith("12") }, rows.toString())
    }

    @Test
    fun `a divider sets the counters the app keeps apart from what FrameHud measured`() {
        val divided = buildPanelLines(
            metrics = metrics(),
            memory = MemoryStats.EMPTY,
            thermal = ThermalStats.EMPTY,
            counters = listOf(
                CounterReading.of(name = "decode queue", value = 4, peakSinceReset = 4),
                CounterReading.of(name = "cache misses", value = 12, peakSinceReset = 12),
            ),
        ).values.filter { it.hasSeparatorAbove }.map { it.text }

        assertTrue(divided.last().startsWith("decode queue"), divided.toString())
    }

    @Test
    fun `counters past the listed rows are counted instead of shown`() {
        val rows = lines(
            metrics(),
            counters = List(6) { CounterReading.of(name = "counter-$it", value = it, peakSinceReset = it) },
        )

        assertTrue(rows.any { it == "+2 more counters" }, rows.toString())
        assertFalse(rows.any { it.startsWith("counter-4") }, rows.toString())
    }

    @Test
    fun `dropped reports are called out only when the system dropped some`() {
        assertFalse(lines(metrics()).any { it.startsWith(LABEL_DROPPED) })
        assertTrue(lines(metrics(droppedReports = 3)).any { it == "$LABEL_DROPPED x3" })
    }

    @Test
    fun `the thermal row waits for a level the platform knows`() {
        assertFalse(lines(metrics(), thermal = ThermalStats.EMPTY).any { it.startsWith(LABEL_THERMAL) })

        val throttled = ThermalStats.of(level = ThermalLevel.LIGHT, headroom = null)
        assertTrue(lines(metrics(), thermal = throttled).any { it == "$LABEL_THERMAL light" })
    }

    @Test
    fun `the blamed phase is the only marked row`() {
        val marked = lines(metrics(jankPercent = 30f, layoutMs = 12f)).filter { it.endsWith(ATTENTION_MARKER) }
        assertEquals(1, marked.size, marked.toString())
        assertTrue(marked.single().startsWith("layout"), marked.single())
    }

    @Test
    fun `on an emulator the marked row is one the app can do something about`() {
        val lines = buildPanelLines(
            metrics = metrics(jankPercent = 30f, layoutMs = 4f, swapMs = 12f),
            memory = MemoryStats.EMPTY,
            thermal = ThermalStats.EMPTY,
            isEmulator = true,
        )
        val marked = lines.values.map { it.text }.filter { it.endsWith(ATTENTION_MARKER) }
        assertEquals(1, marked.size, marked.toString())
        assertTrue(marked.single().startsWith(LABEL_LAYOUT), marked.single())
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
            thermal = ThermalStats.of(level = ThermalLevel.MODERATE, headroom = 0.5f),
        )
        assertEquals(panelLines.values.size, panelLines.toAnnotatedString().text.lines().size)
    }

    @Test
    fun `a divider opens the unstaged rows and the summaries, and nothing else`() {
        val divided = panelLines(isEmulator = false).values.filter { it.hasSeparatorAbove }.map { it.text }

        assertEquals(2, divided.size, divided.toString())
        assertTrue(divided.first().startsWith(LABEL_DELAY), divided.first())
        assertTrue(divided.last().startsWith(LABEL_WINDOW), divided.last())
    }

    @Test
    fun `the load bar is a share of the frame budget`() {
        val row = MetricRowContext(frameBudgetMs = 16f, attentionLabel = null)
        assertEquals(0.5f, row.loadFractionOf(MetricValue.of(average = 8f)), TOLERANCE)
        assertEquals(1f, row.loadFractionOf(MetricValue.of(average = 40f)), TOLERANCE)

        val noBudget = MetricRowContext(frameBudgetMs = 0f, attentionLabel = null)
        assertEquals(0f, noBudget.loadFractionOf(MetricValue.of(average = 8f)), TOLERANCE)
    }

    @Test
    fun `on an emulator only the host-rendered rows are dimmed`() {
        val onDevice = panelLines(isEmulator = false)
        val onEmulator = panelLines(isEmulator = true)

        assertEquals(TextDimmed, onEmulator.colorOf(LABEL_SWAP))
        assertNotEquals(TextDimmed, onDevice.colorOf(LABEL_SWAP))
        assertEquals(onDevice.colorOf(LABEL_DRAW), onEmulator.colorOf(LABEL_DRAW))
    }

    @Test
    fun `the dimmed sections say they were measured on the host`() {
        assertEquals(
            LABEL_RENDER_SECTION + LABEL_HOST_SECTION,
            panelLines(isEmulator = true).textOf(LABEL_RENDER_SECTION),
        )
        assertEquals(LABEL_RENDER_SECTION, panelLines(isEmulator = false).textOf(LABEL_RENDER_SECTION))
    }

    private fun panelLines(isEmulator: Boolean): PanelLines = buildPanelLines(
        metrics = metrics(layoutMs = 4f),
        memory = MemoryStats.EMPTY,
        thermal = ThermalStats.EMPTY,
        isEmulator = isEmulator,
    )

    private fun PanelLines.colorOf(label: String): Color = lineStartingWith(label).color

    private fun PanelLines.textOf(label: String): String = lineStartingWith(label).text

    private fun PanelLines.lineStartingWith(label: String): PanelLine = values.single { it.text.startsWith(label) }

    private fun lines(
        metrics: PerformanceMetrics,
        memory: MemoryStats = MemoryStats.EMPTY,
        thermal: ThermalStats = ThermalStats.EMPTY,
        process: ProcessStats = ProcessStats.EMPTY,
        counters: List<CounterReading> = emptyList(),
    ): List<String> = buildPanelLines(
        metrics = metrics,
        memory = memory,
        thermal = thermal,
        process = process,
        counters = counters,
    ).values.map { it.text }

    private fun List<String>.gpuRow(): String = single { it.startsWith(LABEL_GPU) }

    @Test
    fun `the collapsed line keeps one width while the readings change`() {
        val quiet = listOf(0, 60, 999).map { buildCollapsedLine(metrics(fps = it)).text }
        assertEquals(setOf(COLLAPSED_WIDEST_READING.length), quiet.map { it.length }.toSet(), quiet.toString())

        val attention = listOf(
            metrics(fps = 60, jankPercent = 50f, layoutMs = 9f),
            metrics(fps = 60, jankPercent = 50f, swapMs = 9f),
            metrics(fps = 60, jankPercent = 50f, gpuMs = 9f),
        ).map { buildCollapsedLine(it).text }
        assertEquals(1, attention.map { it.length }.distinct().size, attention.toString())
        assertTrue(attention.all { it.length > COLLAPSED_WIDEST_READING.length }, attention.toString())
    }

    private fun metrics(
        fps: Int = 0,
        jankPercent: Float = 0f,
        layoutMs: Float = 0f,
        swapMs: Float = 0f,
        gpuMs: Float? = null,
        droppedReports: Int = 0,
    ) = PerformanceMetrics.of(
        phases = FramePhases.of(
            layout = MetricValue.of(current = layoutMs, average = layoutMs),
            swapBuffers = MetricValue.of(current = swapMs, average = swapMs),
            gpu = gpuMs?.let { MetricValue.of(current = it, average = it) },
        ),
        window = FrameWindowStats.of(fps = fps, jankPercent = jankPercent),
        session = IntervalStats.EMPTY.copy(droppedReports = droppedReports),
    )

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
