package com.timkrest.framehud.sample.ui

import java.util.Locale

const val NOT_REPORTED: String = "—"

fun formatMs(value: Float): String = String.format(Locale.US, "%.1f ms", value)

fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f%%", value)

fun formatSeconds(durationMs: Long): String = String.format(Locale.US, "%.1f s", durationMs / MS_PER_SECOND)

fun Enum<*>.readable(): String = name.lowercase(Locale.US).replace('_', ' ')

fun <T : Any> formatOrMissing(value: T?, format: (T) -> String): String =
    if (value == null) NOT_REPORTED else format(value)

fun formatWithPeak(value: Int?, peak: Int?): String = formatWithPeak(value, peak) { it.toString() }

fun <T : Any> formatWithPeak(value: T?, peak: T?, format: (T) -> String): String = when {
    value == null -> NOT_REPORTED
    peak == null || peak == value -> format(value)
    else -> "${format(value)} ▲ ${format(peak)}"
}

private const val MS_PER_SECOND = 1_000f
