package com.timkrest.framehud.internal

import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.PhaseAverages

internal fun JsonObjectScope.putPhaseAverages(phases: PhaseAverages) {
    for (phase in FramePhase.entries) put(phase.jsonKey(), phases[phase])
}

internal fun JsonValue.Obj.phaseAverages(): PhaseAverages? {
    for (phase in FramePhase.entries) {
        val valid = when (val average = members[phase.jsonKey()]) {
            is JsonValue.Num -> average.value.isFinite() && average.value >= 0.0
            JsonValue.Null -> phase == FramePhase.GPU
            else -> false
        }
        if (!valid) return null
    }
    return PhaseAverages.of { phase -> float(phase.jsonKey()) }
}

private fun FramePhase.jsonKey(): String = when (this) {
    FramePhase.UNKNOWN_DELAY -> "unknownDelayMs"
    FramePhase.INPUT -> "inputMs"
    FramePhase.ANIMATION -> "animationMs"
    FramePhase.LAYOUT -> "layoutMs"
    FramePhase.DRAW -> "drawMs"
    FramePhase.SYNC -> "syncMs"
    FramePhase.COMMAND_ISSUE -> "commandIssueMs"
    FramePhase.SWAP_BUFFERS -> "swapBuffersMs"
    FramePhase.GPU -> "gpuMs"
    FramePhase.TOTAL -> "totalMs"
}
