package com.timkrest.framehud.internal

import com.timkrest.framehud.MetricValue

internal class PhaseRings(capacity: Int) {

    private val rings = Array(FramePhase.entries.size) { RingBuffer(capacity) }

    operator fun get(phase: FramePhase): RingBuffer = rings[phase.ordinal]

    fun add(durationsMs: FloatArray) {
        for (index in rings.indices) {
            rings[index].add(durationsMs[index])
        }
    }

    fun clear() {
        rings.forEach(RingBuffer::clear)
    }
}

internal fun RingBuffer.toMetricValue(): MetricValue = MetricValue(
    current = last(),
    average = average(),
    peak = peak,
)
