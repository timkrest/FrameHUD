// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.app.ApplicationExitInfo
import com.timkrest.framehud.ExitReason
import com.timkrest.framehud.ProcessExit

internal class RunExit(val runId: String, val exit: ProcessExit) {

    companion object {
        fun of(reason: Int, description: String?, endedAtEpochMs: Long, summary: ByteArray?): RunExit? {
            val shown = ShownState.fromSummary(summary)
            val run = shown.run ?: return null
            if (endedAtEpochMs <= 0L) return null
            val exit = ProcessExit.of(
                reason = exitReasonOf(reason),
                description = description,
                endedAtEpochMs = endedAtEpochMs,
                screen = shown.screen,
                mark = shown.mark,
            )
            return RunExit(runId = run, exit = exit)
        }
    }
}

internal fun List<StoredRun>.withExits(exits: List<RunExit>): List<StoredRun> {
    val byRun = exits.associate { it.runId to it.exit }
    if (none { it.run.exit == null && it.runId in byRun }) return this
    return map { stored ->
        val exit = byRun[stored.runId]
        if (exit == null || stored.run.exit != null) stored else stored.copy(run = stored.run.withExit(exit))
    }
}

@SuppressLint("InlinedApi")
private fun exitReasonOf(reason: Int): ExitReason = when (reason) {
    ApplicationExitInfo.REASON_ANR -> ExitReason.ANR
    ApplicationExitInfo.REASON_CRASH -> ExitReason.CRASH
    ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitReason.CRASH_NATIVE
    ApplicationExitInfo.REASON_LOW_MEMORY -> ExitReason.LOW_MEMORY
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> ExitReason.EXCESSIVE_RESOURCE_USAGE
    ApplicationExitInfo.REASON_FREEZER -> ExitReason.FREEZER
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ExitReason.INITIALIZATION_FAILURE
    ApplicationExitInfo.REASON_SIGNALED -> ExitReason.SIGNALED
    ApplicationExitInfo.REASON_EXIT_SELF -> ExitReason.EXIT_SELF
    ApplicationExitInfo.REASON_USER_REQUESTED -> ExitReason.USER_REQUESTED
    ApplicationExitInfo.REASON_USER_STOPPED -> ExitReason.USER_STOPPED
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> ExitReason.PERMISSION_CHANGE
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> ExitReason.DEPENDENCY_DIED
    ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> ExitReason.PACKAGE_STATE_CHANGE
    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> ExitReason.PACKAGE_UPDATED
    ApplicationExitInfo.REASON_OTHER -> ExitReason.OTHER
    else -> ExitReason.UNKNOWN
}
