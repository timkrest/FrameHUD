// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import com.timkrest.framehud.FrameHistory

internal const val NO_DEADLINE_NS: Long = 0L

internal const val UNKNOWN_REFRESH_RATE_HZ: Float = 0f

internal fun overrunAgainst(budgetMs: Float?, totalMs: Float, displayOverrunMs: Float): Float =
    if (budgetMs == null) displayOverrunMs else totalMs - budgetMs

/** Each frame's budget is what is left of it once its overrun is taken away. */
internal fun frameHistoryOf(totalsMs: FloatArray, overrunsMs: FloatArray): FrameHistory =
    FrameHistory.of(totalsMs = totalsMs, budgetsMs = FloatArray(totalsMs.size) { totalsMs[it] - overrunsMs[it] })

internal fun frameBudgetMs(deadlineNs: Long, refreshRateHz: Float): Float =
    if (deadlineNs > NO_DEADLINE_NS) deadlineNs / NS_PER_MS else MS_PER_SECOND / refreshRateHz
