package com.timkrest.framehud

import androidx.compose.runtime.Immutable

public sealed interface IntervalId {

    /** The screen or the mark this covers, or `session`. */
    public val name: String

    public val label: String

    public data object Session : IntervalId {
        override val name: String get() = "session"
        override val label: String get() = name
    }

    /** Every visit to the screen, added up. */
    public data class Screen(override val name: String) : IntervalId {
        init {
            require(name.isNotBlank()) { "A screen name must not be blank" }
        }

        override val label: String get() = "screen $name"
    }

    /** Every stretch the mark covered, added up. */
    public data class Mark(override val name: String) : IntervalId {
        init {
            require(name.isNotBlank()) { "A mark name must not be blank" }
        }

        override val label: String get() = "mark $name"
    }
}

@Immutable
@ConsistentCopyVisibility
public data class IntervalReport private constructor(
    val id: IntervalId,
    val stats: IntervalStats,
    /** Null when no single budget judged nearly every frame. */
    val frameBudgetMs: Int? = null,
) {
    init {
        require(frameBudgetMs == null || frameBudgetMs > 0) {
            "A frame budget must be positive, got $frameBudgetMs"
        }
    }

    public companion object {
        @InternalFrameHudApi
        public fun of(id: IntervalId, stats: IntervalStats, frameBudgetMs: Int? = null): IntervalReport =
            IntervalReport(id = id, stats = stats, frameBudgetMs = frameBudgetMs)
    }
}
