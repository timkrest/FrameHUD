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
    fun `the thresholds themselves already count`() {
        assertEquals(JankSeverity.NONE, JankSeverity.of(JankSeverity.WARNING_JANK_PERCENT - 0.1f))
        assertEquals(JankSeverity.WARNING, JankSeverity.of(JankSeverity.WARNING_JANK_PERCENT))
        assertEquals(JankSeverity.SEVERE, JankSeverity.of(JankSeverity.SEVERE_JANK_PERCENT))
    }

    @Test
    fun `the diagnosis quotes the budget the window was judged by, not the display's`() {
        val diagnosis = diagnose(metrics = metrics(jankPercent = 30f, frameBudgetMs = 8f))

        assertEquals(8f, diagnosis.frameBudgetMs, TOLERANCE)
    }

    @Test
    fun `throttling outranks every other cause`() {
        val diagnosis = diagnose(
            metrics = metrics(jankPercent = 30f, unknownDelayMs = 20f),
            memory = MemoryStats.EMPTY.copy(gcTimeMs = 5_000L),
            thermal = ThermalStats(level = ThermalLevel.SEVERE, headroom = null),
            choreographerTicksPerSecond = 10,
        )
        assertEquals(JankCause.Thermal(ThermalLevel.SEVERE), diagnosis.cause)
    }

    @Test
    fun `gc pauses win over vsync starvation`() {
        val diagnosis = diagnose(
            metrics = metrics(jankPercent = 30f, sessionDurationMs = 10_000L),
            memory = MemoryStats.EMPTY.copy(gcTimeMs = 300L),
            choreographerTicksPerSecond = 10,
        )
        val cause = assertIs<JankCause.Gc>(diagnosis.cause)
        assertEquals(0.03f, cause.timeShare, TOLERANCE)
    }

    @Test
    fun `gc is blamed once it takes the share that counts, and not a millisecond before`() {
        fun causeFor(gcTimeMs: Long) = diagnose(
            metrics = metrics(jankPercent = 30f, sessionDurationMs = 10_000L, busiestStageMs = 12f),
            memory = MemoryStats.EMPTY.copy(gcTimeMs = gcTimeMs),
        ).cause

        assertIs<JankCause.Stage>(causeFor(gcTimeMs = 199L))
        assertIs<JankCause.Gc>(causeFor(gcTimeMs = 200L))
    }

    @Test
    fun `starved vsync is reported before frame phases`() {
        val diagnosis = diagnose(
            metrics = metrics(jankPercent = 30f, busiestStageMs = 12f),
            choreographerTicksPerSecond = 30,
        )
        assertEquals(JankCause.VsyncStarvation(ticksPerSecond = 30, refreshRateHz = 60f), diagnosis.cause)
    }

    @Test
    fun `late start beats the busiest stage`() {
        val diagnosis = diagnose(metrics = metrics(jankPercent = 30f, unknownDelayMs = 14f, busiestStageMs = 9f))
        assertEquals(JankCause.LateStart(delayMs = 14f), diagnosis.cause)
    }

    @Test
    fun `otherwise the busiest stage is the cause`() {
        val diagnosis = diagnose(
            metrics = metrics(
                jankPercent = 30f,
                unknownDelayMs = 1f,
                busiestStageMs = 11f,
                bottleneckStage = PipelineStage.GPU,
            ),
        )
        assertEquals(JankCause.Stage(stage = PipelineStage.GPU, averageMs = 11f), diagnosis.cause)
    }

    private fun diagnose(
        metrics: PerformanceMetrics,
        memory: MemoryStats = MemoryStats.EMPTY,
        thermal: ThermalStats = ThermalStats.EMPTY,
        choreographerTicksPerSecond: Int = 60,
    ): JankDiagnosis = JankDiagnosis.of(
        metrics = metrics,
        memory = memory,
        thermal = thermal,
        choreographerTicksPerSecond = choreographerTicksPerSecond,
    )

    private fun metrics(
        jankPercent: Float,
        unknownDelayMs: Float = 0f,
        busiestStageMs: Float = 0f,
        bottleneckStage: PipelineStage = PipelineStage.CPU,
        sessionDurationMs: Long = 1_000L,
        frameBudgetMs: Float = 16.7f,
    ): PerformanceMetrics {
        val busiestStage = MetricValue(average = busiestStageMs)
        val unknownDelay = MetricValue(average = unknownDelayMs)
        return PerformanceMetrics(
            phases = when (bottleneckStage) {
                PipelineStage.CPU -> FramePhases(unknownDelay = unknownDelay, draw = busiestStage)
                PipelineStage.RENDER -> FramePhases(unknownDelay = unknownDelay, sync = busiestStage)
                PipelineStage.GPU -> FramePhases(unknownDelay = unknownDelay, gpu = busiestStage)
            },
            window = FrameWindowStats(jankPercent = jankPercent, frameBudgetMs = frameBudgetMs),
            session = IntervalStats.EMPTY.copy(durationMs = sessionDurationMs),
            display = DisplayInfo(refreshRateHz = 60f),
        )
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
