package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.SessionStats
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
        val html = sessionSnapshotFixture(session = SessionStats.EMPTY.copy(confidence = confidence)).toHtml()
        assertContains(html, "running on an emulator")
    }

    @Test
    fun `the report is a self-contained page with the session on it`() {
        val html = sessionSnapshotFixture(
            screenName = "cart",
            context = mapOf("variant" to "b"),
            session = SessionStats.EMPTY.copy(frames = 120, p95FrameMs = 18f, jankPercent = 7.5f, frozenFrames = 1),
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
    fun `the stylesheet and the markup name the same classes`() {
        val html = everyStyledElement().toHtml()
        val styles = html.substringAfter("<style>").substringBefore("</style>")
        val body = html.substringAfter("</head>")

        val inMarkup = Regex("""class="([^"]+)"""").findAll(body).map { it.groupValues[1] }.toSet()
        val inStylesheet = Regex("""\.([a-z-]+)\s*\{""").findAll(styles).map { it.groupValues[1] }.toSet()

        assertEquals(inStylesheet, inMarkup, "a class is styled but never rendered, or rendered but never styled")
        assertContains(styles, "svg {", message = "the chart has no size of its own and collapses without a rule")
    }

    /** A snapshot that renders every class the report can put on the page. */
    private fun everyStyledElement() = sessionSnapshotFixture(
        context = mapOf("variant" to "b"),
        session = SessionStats.EMPTY.copy(droppedReports = 2),
        window = windowOf(totalsMs = floatArrayOf(10f, 40f), deadlinesMs = floatArrayOf(16f, 16f)),
        worstFrames = listOf(WorstFrames.Frame(totalMs = 812.5f, endNs = TAKEN_AT_NS)),
    )

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
            session = SessionStats.EMPTY.copy(phases = PhaseAverages(layout = 9f, total = 12f)),
            screen = SessionStats.EMPTY.copy(phases = PhaseAverages(layout = 4f, total = 6f)),
            phases = FramePhases(layout = MetricValue(average = 9f, peak = 21f)),
        ).toHtml()

        assertContains(html, "Session cpu bound at 9.0 ms per frame, screen cpu bound at 4.0 ms per frame.")
        assertContains(html, "<tr><th>Layout</th><td>9.0 ms</td><td>4.0 ms</td><td>21.0 ms</td></tr>")
        assertContains(html, "<tr><th>UI thread</th><td>9.0 ms</td><td>4.0 ms</td><td>—</td></tr>")
    }

    @Test
    fun `a screen issue the whole session does not share is called out`() {
        val screen = SessionStats.EMPTY.copy(
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
}
