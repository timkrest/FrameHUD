package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.ThermalLevel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HistoryJsonTest {

    @Test
    fun `a run survives a round trip`() {
        val runs = listOf(
            storedRun(
                recordedAtEpochMs = 1_700_000_000_000L,
                intervals = listOf(
                    recordedInterval(IntervalId.Session, recordedStats(p95FrameMs = 12.5f)),
                    recordedInterval(IntervalId.Screen("product/{id}"), recordedStats(p95FrameMs = 20f), frameBudgetMs = 8),
                    recordedInterval(IntervalId.Mark("scroll"), recordedStats(p95FrameMs = 30f)),
                ),
            ),
            storedRun(recordedAtEpochMs = 1_600_000_000_000L, appVersionName = null),
        )

        assertEquals(runs, read(runs.toHistoryJson()))
    }

    @Test
    fun `every confidence issue survives a round trip`() {
        val issues = listOf(
            ConfidenceIssue.DroppedReports(count = 4),
            ConfidenceIssue.SlowListener(longestCallMs = 12.5f),
            ConfidenceIssue.ThermalThrottling(worstLevel = ThermalLevel.SEVERE),
            ConfidenceIssue.LowBattery(powerSaveMode = true, levelPercent = 12),
            ConfidenceIssue.LowBattery(powerSaveMode = false, levelPercent = null),
            ConfidenceIssue.RefreshRateChanged(ratesHz = setOf(60, 120)),
            ConfidenceIssue.Emulator,
            ConfidenceIssue.ShortSample(frames = 7),
        )
        val runs = listOf(storedRun(intervals = listOf(recordedInterval(IntervalId.Session, recordedStats(issues = issues)))))

        assertEquals(issues, read(runs.toHistoryJson())?.single()?.run?.interval(IntervalId.Session)?.stats?.confidence?.issues)
    }

    @Test
    fun `a file written by another schema is kept for the build that reads it`() {
        val json = listOf(storedRun()).toHistoryJson()
            .replace(""""schema":$HISTORY_SCHEMA_VERSION""", """"schema":${HISTORY_SCHEMA_VERSION + 1}""")

        assertIs<Parsed.Unreadable>(parseHistory(json))
    }

    @Test
    fun `a file naming no schema is refused`() {
        assertIs<Parsed.Rejected>(parseHistory("""{"runs":[]}"""))
    }

    @Test
    fun `a run missing a figure rejects the file rather than reading zeros`() {
        val json = listOf(storedRun()).toHistoryJson().replace(""""jankPercent":""", """"wasJankPercent":""")

        assertIs<Parsed.Rejected>(parseHistory(json))
    }

    @Test
    fun `a run naming an interval this build cannot read rejects the file`() {
        val marked = listOf(recordedInterval(IntervalId.Session), recordedInterval(IntervalId.Mark("fling")))
        val json = listOf(storedRun(intervals = marked))
            .toHistoryJson()
            .replace(""""interval":"mark:fling"""", """"interval":"gesture:fling"""")

        assertIs<Parsed.Rejected>(parseHistory(json))
    }

    private fun read(json: String): List<StoredRun>? = (parseHistory(json) as? Parsed.Read)?.value
}
