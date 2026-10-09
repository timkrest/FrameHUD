// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

/** What the user had in front of them, in the few bytes the system keeps past a process's death. */
internal data class ShownState(val run: String?, val screen: String?, val mark: String?) {

    fun toSummary(): ByteArray = sequenceOf(this, copy(mark = null), copy(screen = null, mark = null))
        .map { it.encoded() }
        .first { it.size <= MAX_SUMMARY_BYTES }

    private fun encoded(): ByteArray =
        listOf(run, screen, mark).joinToString(SEPARATOR) { it.orEmpty() }.toByteArray(Charsets.UTF_8)

    companion object {
        const val MAX_SUMMARY_BYTES = 128

        val NOTHING = ShownState(run = null, screen = null, mark = null)

        fun fromSummary(summary: ByteArray?): ShownState {
            val lines = summary?.toString(Charsets.UTF_8)?.split(SEPARATOR) ?: return NOTHING
            val run = lines.first().takeIf(RunIds::isRunId) ?: return NOTHING
            return ShownState(
                run = run,
                screen = lines.getOrNull(1)?.ifEmpty { null },
                mark = lines.getOrNull(2)?.ifEmpty { null },
            )
        }

        private const val SEPARATOR = "\n"
    }
}
