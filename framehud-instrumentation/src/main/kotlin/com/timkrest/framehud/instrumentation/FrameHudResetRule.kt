package com.timkrest.framehud.instrumentation

import android.os.Handler
import android.os.Looper
import com.timkrest.framehud.FrameHud
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.CountDownLatch

/**
 * Clears the screen, mark, context and baseline override that outlive a test, on top of what
 * [FrameHud.reset] clears, before and after each one. Writes the main-thread properties on the
 * main thread, whichever thread the test runs on. Ending a mark a test left open reaches event
 * listeners, so a rule that installs one belongs inside this one.
 */
public class FrameHudResetRule : TestWatcher() {

    override fun starting(description: Description): Unit = clear()

    override fun finished(description: Description): Unit = clear()

    private fun clear() {
        onMainThread {
            FrameHud.screen = null
            FrameHud.mark = null
            FrameHud.context = emptyMap()
        }
        FrameHud.baselineOverride = null
        FrameHud.reset()
    }
}

private fun onMainThread(action: () -> Unit) {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() === mainLooper) return action()

    val done = CountDownLatch(1)
    var failure: Throwable? = null
    Handler(mainLooper).post {
        try {
            action()
        } catch (e: Throwable) {
            failure = e
        } finally {
            done.countDown()
        }
    }
    done.await()
    failure?.let { throw it }
}
