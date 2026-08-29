package com.timkrest.framehud

import android.app.Activity
import android.view.Choreographer
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

internal fun awaitCollectorStarted() {
    runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { FrameHud.sessionStats() } }
}

internal fun <A : Activity> ActivityScenario<A>.drawFrames(count: Int) {
    val drawn = CountDownLatch(1)
    onActivity { activity ->
        val view = activity.window.decorView
        var remaining = count
        Choreographer.getInstance().postFrameCallback(
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    view.invalidate()
                    if (--remaining > 0) Choreographer.getInstance().postFrameCallback(this) else drawn.countDown()
                }
            },
        )
    }
    assertTrue(drawn.await(count * SLOWEST_FRAME_MS + GRACE_MS, TimeUnit.MILLISECONDS), "drew fewer than $count frames")
}

private const val AWAIT_TIMEOUT_MS = 5_000L
private const val SLOWEST_FRAME_MS = 100L
private const val GRACE_MS = 5_000L
