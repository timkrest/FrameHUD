package com.timkrest.framehud.internal

import com.timkrest.framehud.FramePhase

internal fun phaseDurationsMs(totalMs: Float, layoutMs: Float = 0f): FloatArray =
    FloatArray(FramePhase.entries.size).also {
        it[FramePhase.TOTAL.ordinal] = totalMs
        it[FramePhase.LAYOUT.ordinal] = layoutMs
    }
