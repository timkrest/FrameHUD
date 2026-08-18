package com.timkrest.framehud.instrumentation

import android.util.Log
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.LogcatEventListener
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

public object JankAssertions {

    /**
     * Fails when frames collected since [FrameHud.reset] exceed [thresholds], or when no frames were
     * collected. A threshold whose figure a confidence issue taints is inconclusive rather than
     * failed. A device with no baseline yet logs and passes.
     */
    @JvmStatic
    @JvmOverloads
    public fun assertNoJank(
        tag: String,
        thresholds: JankThresholds = JankThresholds(),
        onInconclusive: OnInconclusive = OnInconclusive.FAIL,
    ) {
        val gate = runBlocking { withTimeoutOrNull(STATS_TIMEOUT_MS) { FrameHud.gateStats() } }
            ?: throw AssertionError("$tag: the metrics thread did not answer within $STATS_TIMEOUT_MS ms")
        if (!gate.isCollecting) {
            throw AssertionError("$tag: FrameHud is not collecting. Enable it and resume an activity first.")
        }
        val stats = gate.session
        if (stats.frames == 0) {
            throw AssertionError("$tag: no frames were collected — did the screen draw anything?")
        }

        thresholds.verdict(tag, stats).throwOrWarn(onInconclusive, ::warn)

        val baseline = thresholds.baseline ?: return
        baseline.verdict(tag, gate.comparison, stats).throwOrWarn(onInconclusive, ::warn)
    }

    private fun warn(message: String) = Log.w(LogcatEventListener.TAG, message)

    private const val STATS_TIMEOUT_MS = 1_000L
}
