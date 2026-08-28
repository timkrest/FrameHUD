package com.timkrest.framehud

/**
 * Occurrences that blamed the same thing on the same screen under the same mark and context, and the
 * [worst] of them by lost time.
 */
@ConsistentCopyVisibility
public data class Incident private constructor(
    val occurrences: Int,
    val firstAtEpochMs: Long,
    val lastAtEpochMs: Long,
    val worst: IncidentWindow,
) {
    init {
        require(occurrences > 0) { "An incident happened at least once, was $occurrences" }
    }

    public companion object {
        @InternalFrameHudApi
        public fun of(
            occurrences: Int,
            firstAtEpochMs: Long,
            lastAtEpochMs: Long,
            worst: IncidentWindow,
        ): Incident = Incident(
            occurrences = occurrences,
            firstAtEpochMs = firstAtEpochMs,
            lastAtEpochMs = lastAtEpochMs,
            worst = worst,
        )
    }
}

/**
 * [memory], [thermal], [process], [counters] and [mainThreadBlock] are readings of the moment
 * [trigger] fired, not averages over [frames].
 */
@ConsistentCopyVisibility
public data class IncidentWindow private constructor(
    val trigger: FrameHudEvent.IncidentTrigger,
    val triggeredAtEpochMs: Long,
    val stats: IntervalStats,
    val frames: FrameHistory,
    val framesBeforeTrigger: Int,
    val memory: MemoryStats,
    val thermal: ThermalStats,
    val process: ProcessStats,
    val counters: List<CounterReading>,
    val mainThreadBlock: MainThreadBlock,
) {
    init {
        require(framesBeforeTrigger in 0..frames.size) {
            "framesBeforeTrigger must fall within the ${frames.size} frame(s), was $framesBeforeTrigger"
        }
    }

    public companion object {
        @InternalFrameHudApi
        public fun of(
            trigger: FrameHudEvent.IncidentTrigger,
            triggeredAtEpochMs: Long,
            stats: IntervalStats,
            frames: FrameHistory,
            framesBeforeTrigger: Int,
            memory: MemoryStats,
            thermal: ThermalStats,
            process: ProcessStats,
            counters: List<CounterReading>,
            mainThreadBlock: MainThreadBlock,
        ): IncidentWindow = IncidentWindow(
            trigger = trigger,
            triggeredAtEpochMs = triggeredAtEpochMs,
            stats = stats,
            frames = frames,
            framesBeforeTrigger = framesBeforeTrigger,
            memory = memory,
            thermal = thermal,
            process = process,
            counters = counters,
            mainThreadBlock = mainThreadBlock,
        )
    }
}
