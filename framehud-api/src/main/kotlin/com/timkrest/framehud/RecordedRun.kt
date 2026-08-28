package com.timkrest.framehud

import androidx.compose.runtime.Immutable

@Immutable
@ConsistentCopyVisibility
public data class RecordedRun private constructor(
    /** When the run was last written, not when it started. */
    val recordedAtEpochMs: Long,
    val environment: BaselineEnvironment,
    /** Null when the manifest names no version. */
    val appVersionName: String?,
    val appVersionCode: Long,
    /** The session, every screen and every mark the run measured. */
    val intervals: List<IntervalReport>,
) {
    init {
        require(recordedAtEpochMs > 0L) { "recordedAtEpochMs is a wall clock reading, got $recordedAtEpochMs" }
        require(appVersionCode >= 0L) { "An app version code is not negative, got $appVersionCode" }
        require(intervals.any { it.id == IntervalId.Session }) {
            "A run records the session it measured, got ${intervals.map { it.id.label }}"
        }
        require(intervals.distinctBy { it.id }.size == intervals.size) {
            "A run measures each interval once, got ${intervals.map { it.id.label }}"
        }
    }

    public fun interval(id: IntervalId): IntervalReport? = intervals.firstOrNull { it.id == id }

    public companion object {
        @InternalFrameHudApi
        public fun of(
            recordedAtEpochMs: Long,
            environment: BaselineEnvironment,
            appVersionName: String?,
            appVersionCode: Long,
            intervals: List<IntervalReport>,
        ): RecordedRun = RecordedRun(
            recordedAtEpochMs = recordedAtEpochMs,
            environment = environment,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            intervals = intervals,
        )
    }
}
