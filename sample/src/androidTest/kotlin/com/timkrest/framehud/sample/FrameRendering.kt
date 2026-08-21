package com.timkrest.framehud.sample

import android.app.Activity
import android.os.SystemClock
import android.view.Choreographer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.FrameHud
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun ActivityScenario<out Activity>.renderFrames(count: Int = FRAMES_FOR_AN_EVENT) {
    val done = CountDownLatch(1)
    onActivity { activity ->
        val view = activity.window.decorView
        var remaining = count
        Choreographer.getInstance().postFrameCallback(
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    view.invalidate()
                    if (--remaining > 0) {
                        Choreographer.getInstance().postFrameCallback(this)
                    } else {
                        done.countDown()
                    }
                }
            },
        )
    }
    val timeoutMs = count * SLOWEST_FRAME_MS + RENDER_GRACE_MS
    check(done.await(timeoutMs, TimeUnit.MILLISECONDS)) { "The screen drew fewer than $count frames" }
}

fun ActivityScenario<out Activity>.renderCollectedFrames(count: Int = FRAMES_FOR_AN_EVENT) {
    val before = await { FrameHud.sessionStats() }.frames
    renderFrames(count)
    val deadlineMs = SystemClock.elapsedRealtime() + COLLECTION_TIMEOUT_MS
    while (SystemClock.elapsedRealtime() < deadlineMs) {
        if (await { FrameHud.sessionStats() }.frames > before) return
        SystemClock.sleep(COLLECTION_POLL_MS)
    }
    error("None of the $count rendered frame(s) were reported within $COLLECTION_TIMEOUT_MS ms")
}

fun runOnMain(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}

private const val FRAMES_FOR_AN_EVENT = 40
private const val COLLECTION_TIMEOUT_MS = 5_000L
private const val COLLECTION_POLL_MS = 25L
private const val SLOWEST_FRAME_MS = 100L
private const val RENDER_GRACE_MS = 5_000L
