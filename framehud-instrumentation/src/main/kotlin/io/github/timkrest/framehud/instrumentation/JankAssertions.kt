package io.github.timkrest.framehud.instrumentation

import io.github.timkrest.framehud.FrameHud

public object JankAssertions {

    /**
     * Fails when the frames collected since the last [FrameHud.reset] break [thresholds], and also
     * when nothing was collected — a silently green gate is worse than no gate. Call from the test
     * thread, not the main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun assertNoJank(tag: String, thresholds: JankThresholds = JankThresholds()) {
        val stats = checkNotNull(FrameHud.awaitSessionStats(STATS_TIMEOUT_MS)) {
            "$tag: FrameHud is not collecting. Enable it and resume an activity before asserting."
        }
        check(stats.frames > 0) { "$tag: no frames were collected — did the screen draw anything?" }

        val violations = thresholds.violations(stats)
        if (violations.isNotEmpty()) {
            throw AssertionError("$tag: ${violations.joinToString("; ")} over ${stats.frames} frames")
        }
    }

    private const val STATS_TIMEOUT_MS = 1_000L
}
