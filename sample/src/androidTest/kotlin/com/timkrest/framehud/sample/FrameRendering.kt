// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.shared.AWAIT_TIMEOUT_MS
import com.timkrest.framehud.shared.await
import com.timkrest.framehud.shared.awaitUntil
import com.timkrest.framehud.shared.drawFrames

internal const val FRAMES_FOR_AN_EVENT = 40

internal fun ActivityScenario<out Activity>.renderCollectedFrames(count: Int = FRAMES_FOR_AN_EVENT) {
    val before = await { FrameHud.sessionStats() }.frames
    drawFrames(count)
    check(awaitUntil { await { FrameHud.sessionStats() }.frames > before }) {
        "None of the $count rendered frame(s) were reported within $AWAIT_TIMEOUT_MS ms"
    }
}
