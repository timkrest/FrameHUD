package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Throttling state from `PowerManager`. Timings taken while throttling are not comparable to cold ones. */
@Immutable
public data class ThermalStats(
    val level: ThermalLevel,
    /**
     * How close the device is to severe throttling: `1.0` is that point and higher is past it.
     * Lighter throttling starts below `1.0`. Null when the platform reports nothing.
     */
    val headroom: Float?,
) {
    public companion object {
        public val EMPTY: ThermalStats = ThermalStats(level = ThermalLevel.UNKNOWN, headroom = null)
    }
}

/** Mirrors `PowerManager.THERMAL_STATUS_*`. */
public enum class ThermalLevel {
    UNKNOWN,
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    ;

    /** Throttling hard enough to distort measurements. */
    public val isThrottling: Boolean get() = this >= MODERATE
}
