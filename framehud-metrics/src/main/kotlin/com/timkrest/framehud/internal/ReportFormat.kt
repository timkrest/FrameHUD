// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

internal fun peaked(value: String, peak: String?): String = if (peak == null) value else "$value, peak $peak"

internal fun formatMs(value: Float?): String = if (value == null) "—" else "${formatFloat(value)} ms"

internal fun formatPercent(value: Float?): String = if (value == null) "—" else "${formatFloat(value)}%"

internal fun formatSeconds(durationMs: Long): String = formatInvariant("%.1f s", durationMs / MS_PER_SECOND)

internal fun formatFloat(value: Float): String = formatInvariant("%.1f", value)
