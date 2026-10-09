// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import java.util.UUID

/** Short enough to leave with the system beside the screen and mark. */
internal class RunIds(private val process: String = newProcessToken()) {

    fun of(runNumber: Int): String = "$process:$runNumber"

    companion object {
        fun isRunId(text: String): Boolean = RUN_ID.matches(text)

        private fun newProcessToken(): String = UUID.randomUUID().toString().take(PROCESS_TOKEN_LENGTH)

        private const val PROCESS_TOKEN_LENGTH = 8

        private val RUN_ID = Regex("[0-9a-f]{$PROCESS_TOKEN_LENGTH}:\\d+")
    }
}
