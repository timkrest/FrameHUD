// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

/** A figure of [IntervalStats] that a [ConfidenceIssue] can taint. */
public enum class IntervalFigure {
    P50,
    P95,
    P99,
    JANK_PERCENT,
    LOST_TIME,
    FROZEN_FRAMES,
    MAX_JANK_STREAK,
    UI_THREAD_PHASES,
    RENDER_PHASES,
}
