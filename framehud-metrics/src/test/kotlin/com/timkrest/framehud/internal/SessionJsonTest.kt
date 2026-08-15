package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.MeasuredMetric
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SessionJsonTest {

    @Test
    fun `an empty session exports the full schema`() {
        val json = sessionSnapshotFixture().toJson()

        val averages = """"bottleneckStage":"CPU","unknownDelayMs":0.0,"inputMs":0.0,"animationMs":0.0,""" +
            """"layoutMs":0.0,"drawMs":0.0,"syncMs":0.0,"commandIssueMs":0.0,"swapBuffersMs":0.0,""" +
            """"gpuMs":null,"totalMs":0.0"""
        val stats = """"frames":0,"durationMs":0,"p50FrameMs":0.0,"p95FrameMs":0.0,"p99FrameMs":0.0,""" +
            """"jankPercent":0.0,"lostTimeMs":0.0,"frozenFrames":0,"maxJankStreak":0,"droppedReports":0,""" +
            """"phases":{$averages},"confidence":{"suspect":false,"issues":[]}"""
        val zeroPhase = """{"averageMs":0.0,"peakSinceResetMs":null}"""
        val expected = """{"schema":4,""" +
            """"generatedAt":"2023-11-14T22:13:20.000Z","generatedAtMs":1700000000000,""" +
            """"frameHudVersion":"1.2.3",""" +
            """"app":{"packageName":"com.example.app","versionName":"9.9","versionCode":42},""" +
            """"device":{"manufacturer":"Google","model":"Pixel 8","apiLevel":34},""" +
            """"display":{"refreshRateHz":60.0,"frameBudgetMs":16.6},""" +
            """"measurement":{"enabled":true,"frozen":false,"screen":null,"mark":null,"context":{}},""" +
            """"session":{$stats},""" +
            """"screen":{"name":null,$stats},""" +
            """"window":{"fps":0,"jankPercent":0.0,"p95FrameMs":0.0,"worstFrameMs":0.0,""" +
            """"phases":{"bottleneckStage":"CPU","unknownDelay":$zeroPhase,"input":$zeroPhase,""" +
            """"animation":$zeroPhase,"layout":$zeroPhase,"draw":$zeroPhase,"sync":$zeroPhase,""" +
            """"commandIssue":$zeroPhase,"swapBuffers":$zeroPhase,"gpu":null,"total":$zeroPhase,""" +
            """"overrun":$zeroPhase},"frames":[]},""" +
            """"worstFrames":[],""" +
            """"memory":{"usedHeapMb":0,"maxHeapMb":0,"nativeHeapMb":0,"peakUsedHeapMb":0,""" +
            """"peakNativeHeapMb":0,"gcCount":0,"gcTimeMs":0},""" +
            """"thermal":{"level":"UNKNOWN","headroom":null}}"""
        assertEquals(expected, json)
    }

    @Test
    fun `the screen, context and frame window land in the export`() {
        val json = sessionSnapshotFixture(
            screenName = "product/{id}",
            mark = "scroll",
            context = mapOf("variant" to "b"),
            session = SessionStats.EMPTY.copy(frames = 120, jankPercent = 7.5f),
            window = windowOf(totalsMs = floatArrayOf(10f, 40f), deadlinesMs = floatArrayOf(16f, 16f)),
        ).toJson()

        assertContains(json, """"screen":"product/{id}","mark":"scroll","context":{"variant":"b"}""")
        assertContains(json, """"screen":{"name":"product/{id}",""")
        assertContains(json, """"frames":120""")
        assertContains(json, """"jankPercent":7.5""")
        assertContains(json, """"frames":[{"totalMs":10.0,"deadlineMs":16.0},{"totalMs":40.0,"deadlineMs":16.0}]""")
    }

    @Test
    fun `confidence issues carry their type, detail fields and affected metrics`() {
        val confidence = MeasurementConfidence(
            issues = listOf(
                ConfidenceIssue.ThermalThrottling(ThermalLevel.SEVERE),
                ConfidenceIssue.LowBattery(powerSaveMode = true, levelPercent = 12),
                ConfidenceIssue.RefreshRateChanged(setOf(60, 120)),
            ),
        )
        val json = sessionSnapshotFixture(session = SessionStats.EMPTY.copy(confidence = confidence)).toJson()

        assertContains(json, """"suspect":true""")
        assertContains(json, """{"type":"thermalThrottling","worstLevel":"SEVERE","affected":[""")
        assertContains(json, """{"type":"lowBattery","powerSaveMode":true,"levelPercent":12,"affected":[""")
        assertContains(json, """{"type":"refreshRateChanged","ratesHz":[60,120],"affected":[""")
        assertContains(json, MeasuredMetric.JANK_PERCENT.name)
    }

    @Test
    fun `a worst frame carries a wall-clock timestamp`() {
        val oneSecondBeforeTakenNs = TAKEN_AT_NS - 1_000_000_000L
        val json = sessionSnapshotFixture(
            worstFrames = listOf(WorstFrames.Frame(totalMs = 812.5f, endNs = oneSecondBeforeTakenNs)),
        ).toJson()

        assertContains(
            json,
            """"worstFrames":[{"totalMs":812.5,"at":"2023-11-14T22:13:19.000Z","atMs":1699999999000}]""",
        )
    }
}
