package com.timkrest.framehud

import android.app.Activity
import android.view.Choreographer
import android.view.Window
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

internal fun <A : Activity> ActivityScenario<A>.drawFrames(count: Int) {
    lateinit var drawn: CountDownLatch
    onActivity { drawn = it.window.postFrames(count) }
    awaitFrames(drawn, count)
}

internal fun Window.postFrames(count: Int): CountDownLatch {
    val drawn = CountDownLatch(1)
    val view = decorView
    var remaining = count
    Choreographer.getInstance().postFrameCallback(
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                view.invalidate()
                if (--remaining > 0) Choreographer.getInstance().postFrameCallback(this) else drawn.countDown()
            }
        },
    )
    return drawn
}

internal fun awaitFrames(drawn: CountDownLatch, count: Int) {
    assertTrue(drawn.await(count * SLOWEST_FRAME_MS + GRACE_MS, TimeUnit.MILLISECONDS), "drew fewer than $count frames")
}

private const val SLOWEST_FRAME_MS = 100L
private const val GRACE_MS = 5_000L
