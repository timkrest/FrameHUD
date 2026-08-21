package com.timkrest.framehud.internal

import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.SampledCall
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHtmlTest {

    @Test
    fun `a clean session says the measurement has no issues`() {
        val html = sessionSnapshotFixture().toHtml()
        assertContains(html, "Measurement confidence")
        assertContains(html, "No issues.")
    }

    @Test
    fun `confidence issues list their summaries`() {
        val confidence = MeasurementConfidence(issues = listOf(ConfidenceIssue.Emulator))
        val html = sessionSnapshotFixture(session = IntervalStats.EMPTY.copy(confidence = confidence)).toHtml()
        assertContains(html, "running on an emulator")
    }

    @Test
    fun `the report is a self-contained page with the session on it`() {
        val html = sessionSnapshotFixture(
            screenName = "cart",
            context = mapOf("variant" to "b"),
            session = IntervalStats.EMPTY.copy(frames = 120, p95FrameMs = 18f, jankPercent = 7.5f, frozenFrames = 1),
            window = windowOf(totalsMs = floatArrayOf(10f, 40f), deadlinesMs = floatArrayOf(16f, 16f)),
            worstFrames = listOf(WorstFrames.Frame(totalMs = 812.5f, endNs = TAKEN_AT_NS - 1_000_000_000L)),
        ).toHtml()

        assertTrue(html.startsWith("<!doctype html>"))
        assertFalse(html.contains("http://") || html.contains("https://"), "the report must not load anything")
        assertContains(html, "com.example.app 9.9 (42)")
        assertContains(html, "Google Pixel 8, API 34")
        assertContains(html, "cart")
        assertContains(html, "<code>variant=b</code>")
        assertContains(html, "7.5%")
        assertContains(html, "18.0 ms")
        assertContains(html, "22:13:19.000")
        assertContains(html, "812.5 ms")
        assertContains(html, "class=\"janky\"")
        assertContains(html, "FrameHUD 1.2.3")
    }

    @Test
    fun `the counters section shows what the app kept, peak included`() {
        val html = sessionSnapshotFixture(
            counters = listOf(CounterReading(name = "decode queue", value = 4, peakSinceReset = 31)),
        ).toHtml()

        assertContains(html, "Counters")
        assertContains(html, "decode queue")
        assertContains(html, "4, peak 31")
    }

    @Test
    fun `the screens section ranks what the run measured`() {
        val html = sessionSnapshotFixture(
            intervals = listOf(
                IntervalReport(IntervalId.Screen("cart"), IntervalStats.EMPTY.copy(frames = 600)),
                IntervalReport(
                    IntervalId.Screen("checkout"),
                    IntervalStats.EMPTY.copy(frames = 600, frozenFrames = 2),
                ),
                IntervalReport(IntervalId.Mark("scroll"), IntervalStats.EMPTY.copy(frames = 600)),
            ),
        ).toHtml()

        val screens = html.substringAfter("Screens").substringBefore("</section>")
        assertTrue(screens.indexOf("checkout") < screens.indexOf("cart"), screens)
        assertFalse(screens.contains("scroll"), "marks are not screens")
    }

    @Test
    fun `the environment section reports the process next to the heap`() {
        val html = sessionSnapshotFixture(
            process = ProcessStats(cpuPercent = 42f, peakCpuPercent = 61f, pssMb = 210, peakPssMb = 228),
        ).toHtml()

        assertContains(html, "42.0%, peak 61.0%")
        assertContains(html, "210 MB, peak 228 MB")
        assertFalse(html.contains("Open files"), "a figure the platform withheld has no row")
    }

    @Test
    fun `the stylesheet and the markup name the same classes`() {
        val html = everyStyledElement().toHtml()
        val styles = html.substringAfter("<style>").substringBefore("</style>")
        val body = html.substringAfter("</head>")

        val inMarkup = Regex("""class="([^"]+)"""").findAll(body).map { it.groupValues[1] }.toSet()
        val inStylesheet = Regex("""\.([a-z-]+)\s*\{""").findAll(styles).map { it.groupValues[1] }.toSet()

        assertEquals(inStylesheet, inMarkup, "a class is styled but never rendered, or rendered but never styled")
        assertContains(styles, "svg {", message = "the chart has no size of its own and collapses without a rule")
    }

    private fun everyStyledElement() = sessionSnapshotFixture(
        context = mapOf("variant" to "b"),
        session = IntervalStats.EMPTY.copy(droppedReports = 2),
        window = windowOf(totalsMs = floatArrayOf(10f, 40f), deadlinesMs = floatArrayOf(16f, 16f)),
        worstFrames = listOf(WorstFrames.Frame(totalMs = 812.5f, endNs = TAKEN_AT_NS)),
        incidents = listOf(incidentFixture()),
    )

    @Test
    fun `an incident names its trigger, sizes its window and marks where the trigger fell`() {
        val html = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture(stats = IntervalStats.EMPTY.copy(frames = 2, jankPercent = 50f, p95FrameMs = 40f)),
            ),
        ).toHtml()

        assertContains(html, "Incidents")
        assertContains(html, "cart: 1 frozen frame(s) · at 22:13:20.000")
        assertContains(html, "2 frames, 1 before the trigger · jank 50.0% · p95 40.0 ms")
        assertContains(html, "class=\"trigger\"")
    }

    @Test
    fun `an incident carries the process reading it was drawn under`() {
        val html = sessionSnapshotFixture(
            incidents = listOf(incidentFixture(process = ProcessStats(cpuPercent = 180f, pssMb = 240))),
        ).toHtml()

        assertContains(html, "· cpu 180.0% · pss 240 MB")
    }

    @Test
    fun `an incident carries the counters the app kept when it fired`() {
        val html = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture(
                    counters = listOf(CounterReading(name = "decode queue", value = 31, peakSinceReset = 31)),
                ),
            ),
        ).toHtml()

        assertContains(html, "· decode queue 31")
    }

    @Test
    fun `an incident carries the stack the main thread stood in`() {
        val html = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture(
                    mainThreadBlock = MainThreadBlock(
                        durationMs = 850,
                        stacksTaken = 3,
                        calls = listOf(SampledCall(name = "Repo.load(Repo.kt:12)", samples = 3)),
                    ),
                ),
            ),
        ).toHtml()

        assertContains(html, "Main thread blocked 850 ms, 3 stacks taken")
        assertContains(html, "<li>3x Repo.load(Repo.kt:12)</li>")
    }

    @Test
    fun `a case that happened more than once says how often and over what stretch`() {
        val html = sessionSnapshotFixture(
            incidents = listOf(
                incidentFixture().copy(occurrences = 7, lastAtEpochMs = TAKEN_AT_EPOCH_MS + 45_000L),
            ),
        ).toHtml()

        assertContains(html, "7 times between 22:13:20.000 and 22:14:05.000, worst at 22:13:20.000")
    }

    @Test
    fun `screen names, marks and context are escaped`() {
        val html = sessionSnapshotFixture(
            screenName = "cart/<script>alert(1)</script>",
            mark = "a\"b",
            context = mapOf("variant" to "a&b"),
        ).toHtml()

        assertFalse(html.contains("<script>"), "the screen name leaked into markup")
        assertContains(html, "cart/&lt;script&gt;alert(1)&lt;/script&gt;")
        assertContains(html, "a&quot;b")
        assertContains(html, "variant=a&amp;b")
    }

    @Test
    fun `a phase is reported per interval, with the peak the whole session reached`() {
        val html = sessionSnapshotFixture(
            session = IntervalStats.EMPTY.copy(phases = PhaseAverages(layout = 9f, total = 12f)),
            screen = IntervalStats.EMPTY.copy(phases = PhaseAverages(layout = 4f, total = 6f)),
            phases = FramePhases(layout = MetricValue(average = 9f, peak = 21f)),
        ).toHtml()

        assertContains(html, "Session cpu bound at 9.0 ms per frame, screen cpu bound at 4.0 ms per frame.")
        assertContains(html, "<tr><th>Layout</th><td>9.0 ms</td><td>4.0 ms</td><td>21.0 ms</td></tr>")
        assertContains(html, "<tr><th>UI thread</th><td>9.0 ms</td><td>4.0 ms</td><td>—</td></tr>")
    }

    @Test
    fun `a screen issue the whole session does not share is called out`() {
        val screen = IntervalStats.EMPTY.copy(
            confidence = MeasurementConfidence(issues = listOf(ConfidenceIssue.ShortSample(12))),
        )
        val html = sessionSnapshotFixture(screen = screen).toHtml()

        assertContains(html, "only 12 frame(s) collected — this screen only")
    }

    @Test
    fun `an empty window says so instead of drawing an empty chart`() {
        val html = sessionSnapshotFixture().toHtml()

        assertContains(html, "No frames in the window.")
        assertFalse(html.contains("<svg"), "an empty window still drew a chart")
    }

    @Test
    fun `the report puts the baseline next to this run and names the phase that grew`() {
        val html = sessionSnapshotFixture(baseline = comparisonFixture()).toHtml()

        assertContains(html, "Baseline")
        assertContains(html, "baseline of 3 run(s)")
        assertContains(html, "<td>10.0 ms</td><td>3</td><td>12.0 ms</td><td>+2.0 ms (+20%)</td>")
        assertContains(html, "Layout grew the most, 4.0 → 7.0 ms.")
    }

    @Test
    fun `a baseline from another device says so instead of showing deltas`() {
        val html = sessionSnapshotFixture(
            baseline = BaselineComparison.OtherEnvironment(
                recorded = BASELINE_ENVIRONMENT,
                current = BASELINE_ENVIRONMENT.copy(model = "Pixel 5"),
            ),
        ).toHtml()

        assertContains(html, "Recorded on Google Pixel 8, API 34")
        assertContains(html, "this run is Google Pixel 5, API 34")
    }

    @Test
    fun `a run without a baseline shows no baseline section`() {
        assertFalse(sessionSnapshotFixture().toHtml().contains("Baseline"))
    }
}
