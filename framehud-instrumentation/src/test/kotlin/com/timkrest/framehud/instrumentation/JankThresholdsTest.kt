package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.SessionStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JankThresholdsTest {

    @Test
    fun `a clean session has nothing to report`() {
        val violations = JankThresholds().violations(session(jankPercent = 2f, p95FrameMs = 40f))
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `jank share and frozen frames are checked by default`() {
        val violations = JankThresholds().violations(session(jankPercent = 12f, frozenFrames = 1))
        assertEquals(2, violations.size)
    }

    @Test
    fun `p95 is only checked once a limit is set`() {
        val stats = session(p95FrameMs = 30f)
        assertTrue(JankThresholds().violations(stats).isEmpty())
        assertEquals(1, JankThresholds(maxP95FrameMs = 20f).violations(stats).size)
    }

    @Test
    fun `dropped reports fail the gate, since they undersample everything else`() {
        val stats = session(droppedReports = 3)
        assertEquals(1, JankThresholds().violations(stats).size)
        assertTrue(JankThresholds(maxDroppedReports = 3).violations(stats).isEmpty())
    }

    private fun session(
        jankPercent: Float = 0f,
        frozenFrames: Int = 0,
        p95FrameMs: Float = 0f,
        droppedReports: Int = 0,
    ) = SessionStats.EMPTY.copy(
        frames = 500,
        jankPercent = jankPercent,
        frozenFrames = frozenFrames,
        p95FrameMs = p95FrameMs,
        droppedReports = droppedReports,
    )
}
