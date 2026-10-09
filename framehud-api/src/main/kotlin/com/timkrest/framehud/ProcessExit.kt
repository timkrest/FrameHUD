// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.compose.runtime.Immutable

@Immutable
@ConsistentCopyVisibility
public data class ProcessExit private constructor(
    val reason: ExitReason,
    /** The system's own words, when it gave any. */
    val description: String?,
    val endedAtEpochMs: Long,
    /**
     * The screen in front of the user as the process ended. Null when none was showing, or when its
     * name was too long for the 128 bytes the system keeps; the mark is the first to go.
     */
    val screen: String?,
    val mark: String?,
) {
    init {
        require(endedAtEpochMs > 0L) { "endedAtEpochMs is a wall clock reading, got $endedAtEpochMs" }
    }

    public companion object {
        @InternalFrameHudApi
        public fun of(
            reason: ExitReason,
            description: String?,
            endedAtEpochMs: Long,
            screen: String?,
            mark: String?,
        ): ProcessExit = ProcessExit(
            reason = reason,
            description = description,
            endedAtEpochMs = endedAtEpochMs,
            screen = screen,
            mark = mark,
        )
    }
}
