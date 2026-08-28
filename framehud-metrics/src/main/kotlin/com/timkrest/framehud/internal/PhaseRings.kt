package com.timkrest.framehud.internal

import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.MetricValue

internal class PhaseRings(capacity: Int) {

    private val rings = Array(FramePhase.entries.size) { RingBuffer(capacity) }

    operator fun get(phase: FramePhase): RingBuffer = rings[phase.ordinal]

    fun add(durationsMs: FloatArray) {
        for (index in rings.indices) {
            rings[index].add(durationsMs[index])
        }
    }

    fun resizeTo(capacity: Int) {
        rings.forEach { it.resizeTo(capacity) }
    }

    fun clear() {
        rings.forEach(RingBuffer::clear)
    }
}

internal fun RingBuffer.toMetricValue(): MetricValue = MetricValue.of(
    current = last(),
    average = average(),
    peak = peakSinceClear,
)
