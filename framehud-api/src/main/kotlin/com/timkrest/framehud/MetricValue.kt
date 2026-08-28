package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** One timing, in milliseconds: the latest frame, and the average over the rolling window. */
@Immutable
@ConsistentCopyVisibility
public data class MetricValue private constructor(
    val current: Float,
    val average: Float,
    /**
     * Highest value since the last reset, or null when the timing tracks no peak — either because
     * it is derived from other timings, or because no sample has landed yet. Timings such as
     * [FramePhases.overrun] go negative, so zero cannot stand in for "no peak".
     */
    val peak: Float?,
) {
    public companion object {
        public val ZERO: MetricValue = of()

        @InternalFrameHudApi
        public fun of(current: Float = 0f, average: Float = 0f, peak: Float? = null): MetricValue =
            MetricValue(current = current, average = average, peak = peak)
    }
}

internal operator fun MetricValue.plus(other: MetricValue): MetricValue = MetricValue.of(
    current = current + other.current,
    average = average + other.average,
    peak = null,
)

internal operator fun MetricValue.minus(other: MetricValue): MetricValue = MetricValue.of(
    current = (current - other.current).coerceAtLeast(0f),
    average = (average - other.average).coerceAtLeast(0f),
    peak = null,
)
