package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MeasurementConfidence
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.fail

class JankThresholdsTest {

    @Test
    fun `a limit that cannot judge is refused at construction`() {
        assertFailsWith<IllegalArgumentException> { JankThresholds(maxJankPercent = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { JankThresholds(maxP95FrameMs = -1f) }
    }

    @Test
    fun `a clean session passes`() {
        val verdict = JankThresholds().verdict(TAG, session(jankPercent = 2f, p95FrameMs = 40f))
        assertIs<GateVerdict.Pass>(verdict)
    }

    @Test
    fun `an untainted violation fails`() {
        val verdict = JankThresholds().verdict(TAG, session(jankPercent = 12f, frozenFrames = 1))
        val failed = assertIs<GateVerdict.Fail>(verdict)
        assertContains(failed.message, "jank")
        assertContains(failed.message, "frozen")
    }

    @Test
    fun `p95 is only checked once a limit is set, and a short sample taints it`() {
        val stats = session(p95FrameMs = 30f)
        assertIs<GateVerdict.Pass>(JankThresholds().verdict(TAG, stats))
        assertIs<GateVerdict.Fail>(JankThresholds(maxP95FrameMs = 20f).verdict(TAG, stats))

        val short = session(p95FrameMs = 30f, issues = listOf(ConfidenceIssue.ShortSample(30)))
        assertIs<GateVerdict.Inconclusive>(JankThresholds(maxP95FrameMs = 20f).verdict(TAG, short))
    }

    @Test
    fun `a violation tainted by a confidence issue is inconclusive, and the message still names it`() {
        val stats = session(jankPercent = 12f, issues = listOf(ConfidenceIssue.DroppedReports(3)))
        val verdict = JankThresholds().verdict(TAG, stats)
        val inconclusive = assertIs<GateVerdict.Inconclusive>(verdict)
        assertContains(inconclusive.message, "jank 12.0% over")
        assertContains(inconclusive.message, "dropped")
    }

    @Test
    fun `lost time is only checked once a limit is set, and a refresh rate change taints it`() {
        val stats = session(lostTimeMs = 800f)
        assertIs<GateVerdict.Pass>(JankThresholds().verdict(TAG, stats))
        assertIs<GateVerdict.Fail>(JankThresholds(maxLostTimeMs = 500f).verdict(TAG, stats))

        val rateChanged = session(lostTimeMs = 800f, issues = listOf(ConfidenceIssue.RefreshRateChanged(setOf(60, 120))))
        assertIs<GateVerdict.Inconclusive>(JankThresholds(maxLostTimeMs = 500f).verdict(TAG, rateChanged))
    }

    @Test
    fun `an issue taints the checks over the metrics it names, and leaves the rest conclusive`() {
        val emulator = listOf(ConfidenceIssue.Emulator)
        assertIs<GateVerdict.Fail>(JankThresholds().verdict(TAG, session(jankPercent = 12f, issues = emulator)))

        val rateChanged = listOf(ConfidenceIssue.RefreshRateChanged(setOf(60, 120)))
        assertIs<GateVerdict.Fail>(JankThresholds().verdict(TAG, session(frozenFrames = 1, issues = rateChanged)))
        assertIs<GateVerdict.Fail>(
            JankThresholds(maxP95FrameMs = 20f).verdict(TAG, session(p95FrameMs = 30f, issues = rateChanged)),
        )
        assertIs<GateVerdict.Inconclusive>(
            JankThresholds().verdict(TAG, session(jankPercent = 12f, issues = rateChanged)),
        )
    }

    @Test
    fun `a check turned off neither fails nor taints the run`() {
        val stats = session(frozenFrames = 3, issues = listOf(ConfidenceIssue.DroppedReports(1)))
        val thresholds = JankThresholds(maxJankPercent = Float.POSITIVE_INFINITY, maxFrozenFrames = Int.MAX_VALUE)

        assertIs<GateVerdict.Pass>(thresholds.verdict(TAG, stats))
    }

    @Test
    fun `a passing but tainted checked threshold is still inconclusive`() {
        val stats = session(jankPercent = 1f, issues = listOf(ConfidenceIssue.DroppedReports(1)))
        assertIs<GateVerdict.Inconclusive>(JankThresholds().verdict(TAG, stats))
    }

    @Test
    fun `an untainted violation fails even in warn mode`() {
        val verdict = JankThresholds().verdict(TAG, session(jankPercent = 12f))
        assertFailsWith<AssertionError> { verdict.throwOrWarn(OnInconclusive.WARN) { fail("should not warn") } }
    }

    @Test
    fun `an inconclusive verdict fails in strict mode and only logs in warn mode`() {
        val verdict = GateVerdict.Inconclusive("boom")

        assertFailsWith<AssertionError> { verdict.throwOrWarn(OnInconclusive.FAIL) { fail("should not warn") } }

        var logged: String? = null
        verdict.throwOrWarn(OnInconclusive.WARN) { logged = it }
        assertEquals("boom", logged)
    }

    private fun session(
        jankPercent: Float = 0f,
        frozenFrames: Int = 0,
        p95FrameMs: Float = 0f,
        lostTimeMs: Float = 0f,
        issues: List<ConfidenceIssue> = emptyList(),
    ) = IntervalStats.EMPTY.copy(
        frames = 500,
        jankPercent = jankPercent,
        frozenFrames = frozenFrames,
        p95FrameMs = p95FrameMs,
        lostTimeMs = lostTimeMs,
        confidence = MeasurementConfidence(issues),
    )

    private companion object {
        const val TAG = "sample test"
    }
}
