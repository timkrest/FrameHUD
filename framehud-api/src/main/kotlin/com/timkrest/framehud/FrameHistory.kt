// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Recent frames in chronological order. */
@Immutable
public class FrameHistory private constructor(
    private val totalsMs: FloatArray,
    private val budgetsMs: FloatArray,
) {

    public val size: Int get() = totalsMs.size

    public fun totalMsAt(index: Int): Float = totalsMs[index]

    public fun budgetMsAt(index: Int): Float = budgetsMs[index]

    public companion object {
        public val EMPTY: FrameHistory = FrameHistory(FloatArray(0), FloatArray(0))

        @InternalFrameHudApi
        public fun of(totalsMs: FloatArray, budgetsMs: FloatArray): FrameHistory {
            require(totalsMs.size == budgetsMs.size) {
                "totalsMs and budgetsMs must be the same length, were ${totalsMs.size} and ${budgetsMs.size}"
            }
            return if (totalsMs.isEmpty()) EMPTY else FrameHistory(totalsMs, budgetsMs)
        }
    }
}
