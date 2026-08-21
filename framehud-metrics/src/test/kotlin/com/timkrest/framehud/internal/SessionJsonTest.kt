package com.timkrest.framehud.internal

import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.JankCause
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.MeasuredMetric
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.SampledCall
import com.timkrest.framehud.ThermalLevel
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
        val expected = """{"schema":7,""" +
            """"generatedAt":"2023-11-14T22:13:20.000Z","generatedAtMs":1700000000000,""" +
            """"frameHudVersion":"1.2.3",""" +
            """"app":{"packageName":"com.example.app","versionName":"9.9","versionCode":42},""" +
            """"device":{"manufacturer":"Google","model":"Pixel 8","apiLevel":34},""" +
            """"display":{"refreshRateHz":60.0,"frameBudgetMs":16.6},""" +
            """"measurement":{"enabled":true,"frozen":false,"screen":null,"mark":null,"context":{}},""" +
            """"session":{$stats},""" +
            """"screen":{"name":null,$stats},""" +
            """"intervals":[],"baseline":null,""" +
            """"window":{"fps":0,"jankPercent":0.0,"p95FrameMs":0.0,"worstFrameMs":0.0,""" +
            """"frameBudgetMs":16.6,""" +
            """"phases":{"bottleneckStage":"CPU","unknownDelay":$zeroPhase,"input":$zeroPhase,""" +
            """"animation":$zeroPhase,"layout":$zeroPhase,"draw":$zeroPhase,"sync":$zeroPhase,""" +
            """"commandIssue":$zeroPhase,"swapBuffers":$zeroPhase,"gpu":null,"total":$zeroPhase,""" +
            """"overrun":$zeroPhase},"frames":[]},""" +
            """"worstFrames":[],""" +
            """"memory":{"usedHeapMb":0,"maxHeapMb":0,"nativeHeapMb":0,"peakUsedHeapMb":0,""" +
            """"peakNativeHeapMb":0,"gcCount":0,"gcTimeMs":0},""" +
            """"thermal":{"level":"UNKNOWN","headroom":null},""" +
            """"process":{"cpuPercent":null,"peakCpuPercent":null,"pssMb":null,"peakPssMb":null,""" +
            """"threads":null,"peakThreads":null,"openFiles":null,"peakOpenFiles":null},""" +
            """"counters":[],"incidents":[]}"""
        assertEquals(expected, json)
    }

    @Test
    fun `the screen, context and frame window land in the export`() {
        val json = sessionSnapshotFixture(
            screenName = "product/{id}",
            mark = "scroll",
            context = mapOf("variant" to "b"),
            session = IntervalStats.EMPTY.copy(frames = 120, jankPercent = 7.5f),
            window = windowOf(totalsMs = floatArrayOf(10f, 40f), deadlinesMs = floatArrayOf(16f, 16f)),
        ).toJson()

        assertContains(json, """"screen":"product/{id}","mark":"scroll","context":{"variant":"b"}""")
        assertContains(json, """"screen":{"name":"product/{id}",""")
        assertContains(json, """"frames":120""")
        assertContains(json, """"jankPercent":7.5""")
        assertContains(json, """"frames":[{"totalMs":10.0,"deadlineMs":16.0},{"totalMs":40.0,"deadlineMs":16.0}]""")
    }

    @Test
    fun `an incident carries the diagnosis that opened it, its window and where the trigger fell`() {
        val diagnosis = JankDiagnosis(
            cause = JankCause.Stage(stage = PipelineStage.CPU, averageMs = 12.5f),
            severity = JankSeverity.SEVERE,
            jankPercent = 40f,
            worstFrameMs = 51f,
            frameBudgetMs = 16.6f,
        )
        val burst = FrameHudEvent.JankBurst(
            diagnosis = diagnosis,
            screen = "cart",
            mark = "scroll",
            context = mapOf("variant" to "b"),
        )
        val json = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture(trigger = burst, stats = IntervalStats.EMPTY.copy(frames = 2, frozenFrames = 1)),
            ),
        ).toJson()

        assertContains(json, """"occurrences":1,"firstAt":"2023-11-14T22:13:20.000Z","firstAtMs":1700000000000""")
        assertContains(
            json,
            """"trigger":{"type":"jankBurst","diagnosis":{"severity":"SEVERE","jankPercent":40.0,""" +
                """"worstFrameMs":51.0,"frameBudgetMs":16.6,""" +
                """"cause":{"type":"stage","stage":"CPU","averageMs":12.5}},""" +
                """"screen":"cart","mark":"scroll","context":{"variant":"b"}}""",
        )
        assertContains(
            json,
            """"worst":{"at":"2023-11-14T22:13:20.000Z","atMs":1700000000000,""" +
                """"framesBeforeTrigger":1,"stats":{"frames":2,""",
        )
        assertContains(json, """"frames":[{"totalMs":10.0,"deadlineMs":16.0},{"totalMs":40.0,"deadlineMs":16.0}]""")
    }

    @Test
    fun `a frozen frame incident names the count that opened it`() {
        val json = sessionSnapshotFixture(incidents = listOf(incidentFixture())).toJson()

        assertContains(json, """"trigger":{"type":"frozenFrames","count":1,"screen":"cart","mark":null,"context":{}}""")
    }

    @Test
    fun `an incident carries the counters the app kept when it fired`() {
        val json = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture(
                    counters = listOf(CounterReading(name = "decode queue", value = 31, peakSinceReset = 31)),
                ),
            ),
        ).toJson()

        assertContains(json, """"counters":[{"name":"decode queue","value":31,"peak":31}]""")
    }

    @Test
    fun `an incident carries the stack the main thread stood in`() {
        val json = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture(
                    mainThreadBlock = MainThreadBlock(
                        durationMs = 850,
                        stacksTaken = 3,
                        calls = listOf(SampledCall(name = "Repo.load(Repo.kt:12)", samples = 3)),
                    ),
                ),
            ),
        ).toJson()

        assertContains(
            json,
            """"mainThreadBlock":{"durationMs":850,"stacksTaken":3,""" +
                """"calls":[{"name":"Repo.load(Repo.kt:12)","samples":3}]}""",
        )
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
        val json = sessionSnapshotFixture(session = IntervalStats.EMPTY.copy(confidence = confidence)).toJson()

        assertContains(json, """"suspect":true""")
        assertContains(json, """{"type":"thermalThrottling","worstLevel":"SEVERE","affected":[""")
        assertContains(json, """{"type":"lowBattery","powerSaveMode":true,"levelPercent":12,"affected":[""")
        assertContains(json, """{"type":"refreshRateChanged","ratesHz":[60,120],"affected":[""")
        assertContains(json, MeasuredMetric.JANK_PERCENT.name)
    }

    @Test
    fun `counters the app kept reach the export with their peaks`() {
        val json = sessionSnapshotFixture(
            counters = listOf(CounterReading(name = "decode queue", value = 4, peakSinceReset = 31)),
        ).toJson()

        assertContains(json, """"counters":[{"name":"decode queue","value":4,"peak":31}]""")
    }

    @Test
    fun `a report says which trace was asked to keep itself and how often`() {
        val json = sessionSnapshotFixture(
            flightRecording = FlightRecording(trigger = "framehud_incident", timesAsked = 2),
        ).toJson()

        assertContains(json, """"perfetto":{"trigger":"framehud_incident","timesAsked":2}""")
    }

    @Test
    fun `a run recording no trace writes no trigger of its own`() {
        val json = sessionSnapshotFixture().toJson()

        assertFalse(json.contains("perfetto"), "a run without a trigger named one anyway")
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

    @Test
    fun `intervals and their deltas against the baseline reach the export`() {
        val json = sessionSnapshotFixture(
            intervals = listOf(
                IntervalReport(IntervalId.Screen("cart"), IntervalStats.EMPTY.copy(frames = 90), frameBudgetMs = 17),
            ),
            baseline = comparisonFixture(),
        ).toJson()

        assertContains(json, """"intervals":[{"interval":"screen:cart","frameBudgetMs":17,"frames":90""")
        assertContains(json, """"baseline":{"comparable":true""")
        assertContains(
            json,
            """{"metric":"P95_MS","baseline":10.0,"baselineRuns":3,"current":12.0,"change":2.0,"changePercent":20.0}""",
        )
        assertContains(json, """{"phase":"LAYOUT","baselineMs":4.0,"currentMs":7.0,"changeMs":3.0}""")
    }

    @Test
    fun `the session carries its own budget and does not repeat itself among the intervals`() {
        val json = sessionSnapshotFixture(
            intervals = listOf(
                IntervalReport(IntervalId.Session, IntervalStats.EMPTY.copy(frames = 90), frameBudgetMs = 17),
                IntervalReport(IntervalId.Screen("cart"), IntervalStats.EMPTY.copy(frames = 90)),
            ),
        ).toJson()

        assertContains(json, """"session":{"frameBudgetMs":17,""")
        assertContains(json, """"intervals":[{"interval":"screen:cart",""")
        assertFalse(json.contains(""""interval":"session""""), "the session is exported twice")
    }

    @Test
    fun `a baseline from another device exports both environments instead of deltas`() {
        val json = sessionSnapshotFixture(
            baseline = BaselineComparison.OtherEnvironment(
                recorded = BASELINE_ENVIRONMENT,
                current = BASELINE_ENVIRONMENT.copy(model = "Pixel 5"),
            ),
        ).toJson()

        assertContains(json, """"baseline":{"comparable":false""")
        assertContains(json, """"recordedEnvironment":{"manufacturer":"Google","model":"Pixel 8","apiLevel":34}""")
        assertContains(json, """"environment":{"manufacturer":"Google","model":"Pixel 5","apiLevel":34}""")
    }
}
