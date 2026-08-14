package com.timkrest.framehud.sample

import android.app.Activity
import android.os.SystemClock
import android.view.Choreographer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun ActivityScenario<out Activity>.renderFrames(durationMs: Long) {
    val done = CountDownLatch(1)
    onActivity { activity ->
        val view = activity.window.decorView
        val endMs = SystemClock.elapsedRealtime() + durationMs
        Choreographer.getInstance().postFrameCallback(
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    view.invalidate()
                    if (SystemClock.elapsedRealtime() < endMs) {
                        Choreographer.getInstance().postFrameCallback(this)
                    } else {
                        done.countDown()
                    }
                }
            },
        )
    }
    check(done.await(durationMs + RENDER_GRACE_MS, TimeUnit.MILLISECONDS)) { "The screen never drew" }
}

fun runOnMain(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}

private const val RENDER_GRACE_MS = 5_000L
