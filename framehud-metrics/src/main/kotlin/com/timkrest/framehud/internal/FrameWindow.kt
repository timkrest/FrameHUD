package com.timkrest.framehud.internal

import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.MetricValue

internal class FrameWindow(size: Int) {

    private val phaseRings = PhaseRings(size)
    private val jank = JankRatio(size)
    private val overruns = RingBuffer(size)
    private val timestamps = FrameTimestamps(capacity = MAX_TRACKED_REFRESH_RATE_HZ)

    fun add(durationsMs: FloatArray, overrunMs: Float, frameEndNs: Long) {
        phaseRings.add(durationsMs)
        jank.add(isJanky = overrunMs > 0f)
        overruns.add(overrunMs)
        timestamps.add(frameEndNs)
    }

    fun metricValue(phase: FramePhase): MetricValue = phaseRings[phase].toMetricValue()

    fun overrunValue(): MetricValue = overruns.toMetricValue()

    fun phases(hasGpuDuration: Boolean): FramePhases = FramePhases(
        unknownDelay = metricValue(FramePhase.UNKNOWN_DELAY),
        input = metricValue(FramePhase.INPUT),
        animation = metricValue(FramePhase.ANIMATION),
        layout = metricValue(FramePhase.LAYOUT),
        draw = metricValue(FramePhase.DRAW),
        sync = metricValue(FramePhase.SYNC),
        commandIssue = metricValue(FramePhase.COMMAND_ISSUE),
        swapBuffers = metricValue(FramePhase.SWAP_BUFFERS),
        gpu = if (hasGpuDuration) metricValue(FramePhase.GPU) else null,
        total = metricValue(FramePhase.TOTAL),
        overrun = overrunValue(),
    )

    fun jankPercent(): Float = jank.percent()

    fun fps(nowNs: Long): Int = timestamps.countSince(nowNs - NS_PER_SECOND)

    fun totalPercentile(percent: Float): Float = phaseRings[FramePhase.TOTAL].percentile(percent)

    fun worstTotalMs(): Float = phaseRings[FramePhase.TOTAL].windowMax()

    fun history(): FrameHistory {
        val totalsMs = phaseRings[FramePhase.TOTAL].snapshot()
        val overrunsMs = overruns.snapshot()
        return FrameHistory.of(
            totalsMs = totalsMs,
            deadlinesMs = FloatArray(totalsMs.size) { totalsMs[it] - overrunsMs[it] },
        )
    }

    fun resizeTo(size: Int) {
        phaseRings.resizeTo(size)
        jank.resizeTo(size)
        overruns.resizeTo(size)
        timestamps.clear()
    }

    fun clear() {
        phaseRings.clear()
        jank.clear()
        overruns.clear()
        timestamps.clear()
    }

    private companion object {
        const val MAX_TRACKED_REFRESH_RATE_HZ = 360
    }
}
