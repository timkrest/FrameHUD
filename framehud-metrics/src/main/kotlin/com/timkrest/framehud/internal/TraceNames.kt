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
    require(name.isNotBlank()) { "$what must not be blank" }
    require(name.none(::breaksTraceRecord)) {
        "$what must carry nothing a trace record ends or splits on, or a trace merges it with " +
            "another name"
    }
    require(name.length <= MAX_TRACE_NAME_LENGTH) {
        "$what must fit $MAX_TRACE_NAME_LENGTH characters, or a trace cuts it down to one another " +
            "name could share, got ${name.length}"
    }
}

private fun breaksTraceRecord(character: Char): Boolean =
    character == TRACE_FIELD_SEPARATOR || character.isISOControl()
