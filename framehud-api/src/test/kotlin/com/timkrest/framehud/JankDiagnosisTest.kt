package com.timkrest.framehud

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JankDiagnosisTest {

    @Test
    fun `no jank means healthy`() {
        val diagnosis = diagnose(metrics = metrics(jankPercent = 1f))
        assertEquals(JankDiagnosis.HEALTHY, diagnosis)
    }

    @Test
    fun `severity follows the jank share`() {
        assertEquals(JankSeverity.WARNING, diagnose(metrics = metrics(jankPercent = 6f)).severity)
        assertEquals(JankSeverity.SEVERE, diagnose(metrics = metrics(jankPercent = 25f)).severity)
    }

    @Test
    fun `the thresholds themselves already count`() {
        assertEquals(JankSeverity.NONE, JankSeverity.of(JankSeverity.WARNING_JANK_PERCENT - 0.1f))
        assertEquals(JankSeverity.WARNING, JankSeverity.of(JankSeverity.WARNING_JANK_PERCENT))
        assertEquals(JankSeverity.SEVERE, JankSeverity.of(JankSeverity.SEVERE_JANK_PERCENT))
    }

    @Test
    fun `throttling outranks every other cause`() {
        val diagnosis = diagnose(
            metrics = metrics(jankPercent = 30f, unknownDelayMs = 20f),
            memory = MemoryStats.EMPTY.copy(gcTimeMs = 5_000L),
            thermal = ThermalStats(level = ThermalLevel.SEVERE, headroom = null),
            vsyncRate = 10,
        )
        assertEquals(JankCause.Thermal(ThermalLevel.SEVERE), diagnosis.cause)
    }

    @Test
    fun `gc pauses win over vsync starvation`() {
        val diagnosis = diagnose(
            metrics = metrics(jankPercent = 30f, sessionDurationMs = 10_000L),
            memory = MemoryStats.EMPTY.copy(gcTimeMs = 300L),
            vsyncRate = 10,
        )
        val cause = assertIs<JankCause.Gc>(diagnosis.cause)
        assertEquals(0.03f, cause.timeShare, TOLERANCE)
    }

    @Test
    fun `brief gc is not blamed`() {
        val diagnosis = diagnose(
            metrics = metrics(jankPercent = 30f, sessionDurationMs = 10_000L, bottleneckMs = 12f),
            memory = MemoryStats.EMPTY.copy(gcTimeMs = 100L),
        )
        assertIs<JankCause.Stage>(diagnosis.cause)
    }

    @Test
    fun `starved vsync is reported before frame phases`() {
        val diagnosis = diagnose(metrics = metrics(jankPercent = 30f, bottleneckMs = 12f), vsyncRate = 30)
        assertEquals(JankCause.VsyncStarvation(ticksPerSecond = 30, refreshRateHz = 60f), diagnosis.cause)
    }

    @Test
    fun `late start beats the busiest stage`() {
        val diagnosis = diagnose(metrics = metrics(jankPercent = 30f, unknownDelayMs = 14f, bottleneckMs = 9f))
        assertEquals(JankCause.LateStart(delayMs = 14f), diagnosis.cause)
    }

    @Test
    fun `otherwise the busiest stage is the cause`() {
        val diagnosis = diagnose(
            metrics = metrics(
                jankPercent = 30f,
                unknownDelayMs = 1f,
                bottleneckMs = 11f,
                bottleneckStage = PipelineStage.GPU,
            ),
        )
        assertEquals(JankCause.Stage(stage = PipelineStage.GPU, averageMs = 11f), diagnosis.cause)
    }

    private fun diagnose(
        metrics: PerformanceMetrics,
        memory: MemoryStats = MemoryStats.EMPTY,
        thermal: ThermalStats = ThermalStats.EMPTY,
        vsyncRate: Int = 60,
    ): JankDiagnosis = JankDiagnosis.of(metrics = metrics, memory = memory, thermal = thermal, vsyncRate = vsyncRate)

    /** Bottleneck is derived — load the phase that should win. */
    private fun metrics(
        jankPercent: Float,
        unknownDelayMs: Float = 0f,
        bottleneckMs: Float = 0f,
        bottleneckStage: PipelineStage = PipelineStage.CPU,
        sessionDurationMs: Long = 1_000L,
    ): PerformanceMetrics {
        val busiest = MetricValue(average = bottleneckMs)
        val unknownDelay = MetricValue(average = unknownDelayMs)
        return PerformanceMetrics(
            phases = when (bottleneckStage) {
                PipelineStage.CPU -> FramePhases(unknownDelay = unknownDelay, draw = busiest)
                PipelineStage.RENDER -> FramePhases(unknownDelay = unknownDelay, sync = busiest)
                PipelineStage.GPU -> FramePhases(unknownDelay = unknownDelay, gpu = busiest)
            },
            window = FrameWindowStats(jankPercent = jankPercent),
            session = SessionStats.EMPTY.copy(durationMs = sessionDurationMs),
            display = DisplayInfo(refreshRateHz = 60f, frameBudgetMs = 16.7f),
        )
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
