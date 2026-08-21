package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import org.junit.Test
import kotlin.test.assertEquals

class WorstScreensTest {

    @Test
    fun `frozen frames outrank jank, and jank outranks a slow p95`() {
        val ranked = listOf(
            screen("slow", p95FrameMs = 90f),
            screen("janky", jankPercent = 30f),
            screen("frozen", frozenFrames = 1),
        ).worstScreensFirst()

        assertEquals(listOf("frozen", "janky", "slow"), ranked.map { it.id.name })
    }

    @Test
    fun `a sample too short to judge comes last however bad it reads`() {
        val ranked = listOf(
            screen("glimpse", frames = ConfidenceIssue.ShortSample.MIN_FRAMES_P95 - 1, frozenFrames = 9),
            screen("measured", jankPercent = 1f),
        ).worstScreensFirst()

        assertEquals(listOf("measured", "glimpse"), ranked.map { it.id.name })
    }

    @Test
    fun `the session and the marks are left out`() {
        val ranked = listOf(
            IntervalReport(IntervalId.Session, IntervalStats.EMPTY),
            IntervalReport(IntervalId.Mark("scroll"), IntervalStats.EMPTY),
            screen("cart"),
        ).worstScreensFirst()

        assertEquals(listOf("cart"), ranked.map { it.id.name })
    }

    private fun screen(
        name: String,
        frames: Int = 600,
        jankPercent: Float = 0f,
        p95FrameMs: Float = 0f,
        frozenFrames: Int = 0,
    ) = IntervalReport(
        id = IntervalId.Screen(name),
        stats = IntervalStats(
            frames = frames,
            jankPercent = jankPercent,
            p95FrameMs = p95FrameMs,
            frozenFrames = frozenFrames,
        ),
    )
}
