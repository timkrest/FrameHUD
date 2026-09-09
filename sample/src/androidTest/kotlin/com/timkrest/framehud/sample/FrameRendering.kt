// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.app.Activity
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.shared.await
import com.timkrest.framehud.shared.drawFrames

internal const val FRAMES_FOR_AN_EVENT = 40

internal fun ActivityScenario<out Activity>.renderCollectedFrames(count: Int = FRAMES_FOR_AN_EVENT) {
    val before = await { FrameHud.sessionStats() }.frames
    drawFrames(count)
    val deadlineMs = SystemClock.elapsedRealtime() + COLLECTION_TIMEOUT_MS
    while (SystemClock.elapsedRealtime() < deadlineMs) {
        if (await { FrameHud.sessionStats() }.frames > before) return
        SystemClock.sleep(COLLECTION_POLL_MS)
    }
    error("None of the $count rendered frame(s) were reported within $COLLECTION_TIMEOUT_MS ms")
}

private const val COLLECTION_TIMEOUT_MS = 5_000L
private const val COLLECTION_POLL_MS = 25L
