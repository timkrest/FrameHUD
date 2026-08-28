package com.timkrest.framehud.internal

import com.timkrest.framehud.IntervalId

internal fun IntervalId.key(): String = when (this) {
    IntervalId.Session -> SESSION
    is IntervalId.Screen -> "$SCREEN_PREFIX$name"
    is IntervalId.Mark -> "$MARK_PREFIX$name"
}

internal fun intervalId(key: String): IntervalId? = when {
    key == SESSION -> IntervalId.Session
    key.startsWith(SCREEN_PREFIX) -> key.removePrefix(SCREEN_PREFIX).takeIf { it.isNotBlank() }?.let(IntervalId::Screen)
    key.startsWith(MARK_PREFIX) -> key.removePrefix(MARK_PREFIX).takeIf { it.isNotBlank() }?.let(IntervalId::Mark)
    else -> null
}

private const val SESSION = "session"
private const val SCREEN_PREFIX = "screen:"
private const val MARK_PREFIX = "mark:"
