package com.timkrest.framehud.internal

internal const val NO_DEADLINE_NS: Long = 0L

internal const val UNKNOWN_REFRESH_RATE_HZ: Float = 0f

internal fun overrunAgainst(budgetMs: Int?, totalMs: Float, displayOverrunMs: Float): Float =
    if (budgetMs == null) displayOverrunMs else totalMs - budgetMs

internal fun frameBudgetMs(deadlineNs: Long, refreshRateHz: Float): Float =
    if (deadlineNs > NO_DEADLINE_NS) deadlineNs / NS_PER_MS else MS_PER_SECOND / refreshRateHz
