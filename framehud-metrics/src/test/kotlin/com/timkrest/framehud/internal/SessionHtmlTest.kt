package com.timkrest.framehud.internal

import com.timkrest.framehud.SessionStats
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHtmlTest {

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
    fun `an empty window says so instead of drawing an empty chart`() {
        val html = sessionSnapshotFixture().toHtml()

        assertContains(html, "No frames in the window.")
        assertFalse(html.contains("<svg"), "an empty window still drew a chart")
    }
}
