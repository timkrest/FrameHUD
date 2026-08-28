package com.timkrest.framehud

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.internal.MS_PER_SECOND

@Immutable
@ConsistentCopyVisibility
public data class DisplayInfo private constructor(
    val refreshRateHz: Float,
    /** The system deadline (API 31+), otherwise `1000 / refreshRateHz`. */
    val frameBudgetMs: Float,
) {
    init {
        require(refreshRateHz > 0f) { "refreshRateHz must be positive, was $refreshRateHz" }
        require(frameBudgetMs > 0f) { "frameBudgetMs must be positive, was $frameBudgetMs" }
    }

    public companion object {
        public const val DEFAULT_REFRESH_RATE_HZ: Float = 60f

        public val DEFAULT: DisplayInfo = of()

        @InternalFrameHudApi
        public fun of(
            refreshRateHz: Float = DEFAULT_REFRESH_RATE_HZ,
            frameBudgetMs: Float = MS_PER_SECOND / DEFAULT_REFRESH_RATE_HZ,
        ): DisplayInfo = DisplayInfo(refreshRateHz = refreshRateHz, frameBudgetMs = frameBudgetMs)
    }
}
