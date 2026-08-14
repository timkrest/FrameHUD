package com.timkrest.framehud.instrumentation

import android.util.Log
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.LogcatEventListener

public object JankAssertions {

    /**
     * Fails when frames collected since [FrameHud.reset] exceed [thresholds], or when no frames were
     * collected. A threshold whose figure a confidence issue taints is inconclusive rather than
     * failed. Must not be called from the main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun assertNoJank(
        tag: String,
        thresholds: JankThresholds = JankThresholds(),
        onInconclusive: OnInconclusive = OnInconclusive.FAIL,
    ) {
        val stats = FrameHud.awaitSessionStats(STATS_TIMEOUT_MS)
            ?: throw AssertionError("$tag: FrameHud is not collecting. Enable it and resume an activity first.")
        if (stats.frames == 0) {
            throw AssertionError("$tag: no frames were collected — did the screen draw anything?")
        }

        thresholds.verdict(tag, stats).throwOrWarn(onInconclusive) { message -> Log.w(LogcatEventListener.TAG, message) }
    }

    private const val STATS_TIMEOUT_MS = 1_000L
}
