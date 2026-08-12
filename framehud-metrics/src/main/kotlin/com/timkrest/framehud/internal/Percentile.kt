package com.timkrest.framehud.internal

import kotlin.math.ceil

internal const val P50 = 50f
internal const val P95 = 95f
internal const val P99 = 99f

internal fun nearestRank(percent: Float, count: Int): Int =
    ceil(percent / PERCENT * count).toInt().coerceIn(1, count)
