// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.timkrest.framehud.IntervalStats
import kotlinx.coroutines.delay

@Composable
fun Freeze() {
    LaunchedEffect(Unit) {
        while (true) {
            delay(DRAWING_BETWEEN_FREEZES_MS)
            freezeMainThread()
        }
    }
}

private fun freezeMainThread() {
    Thread.sleep(PAST_FROZEN_FRAME_MS)
}

private const val DRAWING_BETWEEN_FREEZES_MS = 5_000L

private val PAST_FROZEN_FRAME_MS = IntervalStats.FROZEN_FRAME_MS.toLong() + 200L
