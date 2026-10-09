// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import kotlin.math.roundToInt

/** Budgets that round to the same millisecond judge frames alike; a display deadline jitters below that. */
internal fun sameFrameBudget(a: Float?, b: Float?): Boolean =
    a != null && b != null && a.roundToInt() == b.roundToInt()
