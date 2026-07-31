package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Refresh rate and the frame budget that follows from it. Both are positive. */
@Immutable
public data class DisplayInfo(
    val refreshRateHz: Float = DEFAULT_REFRESH_RATE_HZ,
    /** The system deadline (API 31+), otherwise `1000 / refreshRateHz`. */
    val frameBudgetMs: Float = MS_PER_SECOND / DEFAULT_REFRESH_RATE_HZ,
) {
    init {
        require(refreshRateHz > 0f) { "refreshRateHz must be positive, was $refreshRateHz" }
        require(frameBudgetMs > 0f) { "frameBudgetMs must be positive, was $frameBudgetMs" }
    }

    public companion object {
        /** Assumed when the display reports no usable rate. */
        public const val DEFAULT_REFRESH_RATE_HZ: Float = 60f

        public val DEFAULT: DisplayInfo = DisplayInfo()
    }
}
