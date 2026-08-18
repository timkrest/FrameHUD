package com.timkrest.framehud

import androidx.compose.runtime.Immutable

public sealed interface IntervalId {

    public val label: String

    public data object Session : IntervalId {
        override val label: String get() = "session"
    }

    /** Every visit to the screen, added up. */
    public data class Screen(val name: String) : IntervalId {
        init {
            require(name.isNotBlank()) { "A screen name must not be blank" }
        }

        override val label: String get() = "screen $name"
    }

    /** Every stretch the mark covered, added up. */
    public data class Mark(val name: String) : IntervalId {
        init {
            require(name.isNotBlank()) { "A mark name must not be blank" }
        }

        override val label: String get() = "mark $name"
    }
}

@Immutable
public data class IntervalReport(
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
}
