package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.FrameHud

public object JankAssertions {

    /**
     * Fails when the frames collected since the last [FrameHud.reset] break [thresholds], and also
     * when nothing was collected — a silently green gate is worse than no gate. Call from the test
     * thread, not the main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun assertNoJank(tag: String, thresholds: JankThresholds = JankThresholds()) {
        val stats = FrameHud.awaitSessionStats(STATS_TIMEOUT_MS)
            ?: throw AssertionError("$tag: FrameHud is not collecting. Enable it and resume an activity first.")
        if (stats.frames == 0) {
            throw AssertionError("$tag: no frames were collected — did the screen draw anything?")
        }

        val violations = thresholds.violations(stats)
        if (violations.isNotEmpty()) {
            throw AssertionError("$tag: ${violations.joinToString("; ")} over ${stats.frames} frames")
        }
    }

    private const val STATS_TIMEOUT_MS = 1_000L
}
