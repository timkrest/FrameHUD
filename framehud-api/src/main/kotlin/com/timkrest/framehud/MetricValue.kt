package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** One timing, in milliseconds: the latest frame, and the average over the rolling window. */
@Immutable
public data class MetricValue(
    val current: Float = 0f,
    val average: Float = 0f,
    /**
     * Highest value since the last reset, or null when the timing tracks no peak — either because
     * it is derived from other timings, or because no sample has landed yet. Timings such as
     * [FramePhases.overrun] go negative, so zero cannot stand in for "no peak".
     */
    val peak: Float? = null,
) {
    public companion object {
        public val ZERO: MetricValue = MetricValue()
    }
}

/** Derived timings carry no peak of their own, so [MetricValue.peak] stays null. */
internal operator fun MetricValue.plus(other: MetricValue): MetricValue = MetricValue(
    current = current + other.current,
    average = average + other.average,
)

/** Clamped at zero — phases must not sum to a negative timing. */
internal operator fun MetricValue.minus(other: MetricValue): MetricValue = MetricValue(
    current = (current - other.current).coerceAtLeast(0f),
    average = (average - other.average).coerceAtLeast(0f),
)
