package com.timkrest.framehud

/**
 * Occurrences that blamed the same thing on the same screen under the same mark and context, and the
 * [worst] of them by lost time.
 */
public data class Incident(
    val occurrences: Int,
    val firstAtEpochMs: Long,
    val lastAtEpochMs: Long,
    val worst: IncidentWindow,
) {
    init {
        require(occurrences > 0) { "An incident happened at least once, was $occurrences" }
    }
}

/**
 * [memory], [thermal] and [process] are readings of the moment [trigger] fired, not averages over
 * [frames].
 */
public data class IncidentWindow(
    val trigger: FrameHudEvent.IncidentTrigger,
    val triggeredAtEpochMs: Long,
    val stats: IntervalStats,
    val frames: FrameHistory,
    val framesBeforeTrigger: Int,
    val memory: MemoryStats,
    val thermal: ThermalStats,
    val process: ProcessStats,
) {
    init {
        require(framesBeforeTrigger in 0..frames.size) {
            "framesBeforeTrigger must fall within the ${frames.size} frame(s), was $framesBeforeTrigger"
        }
    }
}
