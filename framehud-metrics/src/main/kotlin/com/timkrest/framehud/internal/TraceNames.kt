// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

private const val TRACE_FIELD_SEPARATOR = '|'

private const val TRACE_LABEL_KEPT_LENGTH = 127

private const val SCREEN_SECTION_PREFIX = "framehud:screen:"

private const val MARK_SECTION_PREFIX = "framehud:mark:"

private const val COUNTER_TRACK_PREFIX = "framehud:counter:"

private val LONGEST_TRACE_PREFIX_LENGTH =
    maxOf(SCREEN_SECTION_PREFIX.length, MARK_SECTION_PREFIX.length, COUNTER_TRACK_PREFIX.length)

internal val MAX_TRACE_NAME_LENGTH = TRACE_LABEL_KEPT_LENGTH - LONGEST_TRACE_PREFIX_LENGTH

internal fun screenSectionName(screen: String): String = "$SCREEN_SECTION_PREFIX$screen"

internal fun markSectionName(mark: String): String = "$MARK_SECTION_PREFIX$mark"

internal fun counterTrackName(counter: String): String = "$COUNTER_TRACK_PREFIX$counter"

internal fun requireNameStandsApart(what: String, name: String) {
    whyNameBlends(name)?.let { reason -> throw IllegalArgumentException("$what $reason") }
}

internal fun nameStandsApart(name: String): Boolean = whyNameBlends(name) == null

private fun whyNameBlends(name: String): String? = when {
    name.isBlank() -> "must not be blank"
    name.any(::breaksTraceRecord) ->
        "must carry nothing a trace record ends or splits on, or a trace merges it with another name"
    name.length > MAX_TRACE_NAME_LENGTH ->
        "must fit $MAX_TRACE_NAME_LENGTH characters, or a trace cuts it down to one another name could share, " +
            "got ${name.length}"
    else -> null
}

private fun breaksTraceRecord(character: Char): Boolean =
    character == TRACE_FIELD_SEPARATOR || character.isISOControl()
