package io.github.timkrest.framehud.internal

import io.github.timkrest.framehud.MetricValue

internal class PhaseRings(capacity: Int) {

    private val rings = Array(FramePhase.entries.size) { RingBuffer(capacity) }

    operator fun get(phase: FramePhase): RingBuffer = rings[phase.ordinal]

    fun add(durationsMs: FloatArray) {
        for (phase in FramePhase.entries) {
            rings[phase.ordinal].add(durationsMs[phase.ordinal])
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
