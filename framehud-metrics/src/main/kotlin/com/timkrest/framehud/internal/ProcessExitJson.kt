// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import com.timkrest.framehud.ExitReason
import com.timkrest.framehud.ProcessExit

internal fun JsonObjectScope.putProcessExit(exit: ProcessExit) {
    put(REASON, exit.reason.name)
    put(DESCRIPTION, exit.description)
    put(ENDED_AT_MS, exit.endedAtEpochMs)
    put(SCREEN, exit.screen)
    put(MARK, exit.mark)
}

internal fun JsonValue.processExit(): ProcessExit? = readOrNull {
    ProcessExit.of(
        reason = string(REASON)?.let(::exitReasonNamed) ?: return@readOrNull null,
        description = string(DESCRIPTION),
        endedAtEpochMs = long(ENDED_AT_MS) ?: return@readOrNull null,
        screen = string(SCREEN),
        mark = string(MARK),
    )
}

private fun exitReasonNamed(name: String): ExitReason = ExitReason.entries.firstOrNull { it.name == name } ?: ExitReason.UNKNOWN

private const val REASON = "reason"
private const val DESCRIPTION = "description"
private const val ENDED_AT_MS = "endedAtMs"
private const val SCREEN = "screen"
private const val MARK = "mark"
