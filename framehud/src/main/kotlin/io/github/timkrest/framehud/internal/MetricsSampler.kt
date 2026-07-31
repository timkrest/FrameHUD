package io.github.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Window

/**
 * Owns the thread `FrameMetrics` callbacks arrive on, plus the tick that keeps readings fresh once
 * the screen goes idle and no more callbacks come.
 *
 * The thread outlives a single screen: stopping only unbinds the window and stops the tick, so
 * leaving a screen never waits on it. It is torn down only when the configured thread name changes,
 * and even then the replacement waits for it on its own thread — never on the caller's.
 *
 * Main thread only, apart from [post].
 */
internal class MetricsSampler(
    threadName: String,
    private val listener: Window.OnFrameMetricsAvailableListener,
    private val tickIntervalMs: () -> Long,
    private val onTick: () -> Unit,
    predecessor: Thread? = null,
) {

    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)

    var boundWindow: Window? = null
        private set

    /** Metrics thread only. Bumped to retire a tick that is already in flight. */
    private var tickGeneration = 0

    init {
        // The aggregator is shared with the thread being replaced; keep the two from overlapping.
        if (predecessor != null) handler.post { awaitTermination(predecessor) }
    }

    val metricsThread: Thread get() = thread

    fun startTicking() {
        handler.post { postTick(++tickGeneration) }
    }

    fun stopTicking() {
        handler.post { tickGeneration++ }
    }

    /** False when [window] is already bound. */
    fun bind(window: Window): Boolean {
        if (boundWindow === window) return false
        unbind()
        boundWindow = window
        window.addOnFrameMetricsAvailableListener(listener, handler)
        return true
    }

    /** False when nothing was bound. */
    fun unbind(): Boolean {
        val window = boundWindow ?: return false
        boundWindow = null
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to remove the frame metrics listener", e)
        }
        return true
    }

    fun post(action: () -> Unit): Boolean = handler.post { guarded("running a metrics task", action) }

    /** Runs what is already queued, then lets the thread finish. Never blocks the caller. */
    fun quit() {
        unbind()
        stopTicking()
        thread.quitSafely()
    }

    private fun postTick(generation: Int) {
        handler.postDelayed(
            {
                if (generation != tickGeneration) return@postDelayed
                guarded("sampling metrics", onTick)
                postTick(generation)
            },
            tickIntervalMs(),
        )
    }

    private fun awaitTermination(thread: Thread) {
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (e: InterruptedException) {
                interrupted = true
                Log.w(LOG_TAG, "Interrupted while joining the previous metrics thread, retrying", e)
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
