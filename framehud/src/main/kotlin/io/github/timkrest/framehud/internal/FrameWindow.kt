package io.github.timkrest.framehud.internal

import io.github.timkrest.framehud.MetricValue

internal class FrameWindow(size: Int) {

    private val phaseRings = PhaseRings(size)
    private val jankFlags = RingBuffer(size)
    private val overruns = RingBuffer(size)
    private val timestamps = FrameTimestamps(FPS_WINDOW_CAPACITY)

    fun add(
        durationsMs: FloatArray,
        isJanky: Boolean,
        overrunMs: Float,
        frameEndNs: Long,
    ) {
        phaseRings.add(durationsMs)
        jankFlags.add(if (isJanky) 1f else 0f)
        overruns.add(overrunMs)
        timestamps.add(frameEndNs)
    }

    fun metricValue(phase: FramePhase): MetricValue = phaseRings[phase].toMetricValue()

    fun overrunValue(): MetricValue = overruns.toMetricValue()

    fun jankPercent(): Float = jankFlags.average() * PERCENT

    fun fps(nowNs: Long): Int = timestamps.countSince(nowNs - FPS_WINDOW_NS)

    fun totalPercentile(percent: Float): Float = phaseRings[FramePhase.TOTAL].percentile(percent)

    fun worstTotalMs(): Float = phaseRings[FramePhase.TOTAL].windowMax()

    fun totalHistory(): FloatArray = phaseRings[FramePhase.TOTAL].snapshot()

    fun clear() {
        phaseRings.clear()
        jankFlags.clear()
        overruns.clear()
        timestamps.clear()
    }

    companion object {
        private const val FPS_WINDOW_NS = 1_000_000_000L
        private const val FPS_WINDOW_CAPACITY = 360
    }
}
