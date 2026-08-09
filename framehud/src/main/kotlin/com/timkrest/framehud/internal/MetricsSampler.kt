package com.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread

/**
 * Owns the thread `FrameMetrics` callbacks arrive on, plus the tick that keeps readings fresh once
 * the screen goes idle and no more callbacks come.
 *
 * The thread outlives a single screen: stopping only unbinds the window and stops the tick, so
 * leaving a screen never waits on it. It is torn down only when the configured thread name changes,
 * and even then the replacement waits for it on its own thread — never on the caller's.
 */
@MainThread
internal class MetricsSampler(
    threadName: String,
    private val listener: Window.OnFrameMetricsAvailableListener,
    private val tickIntervalMs: () -> Long,
    private val onTick: () -> Unit,
    predecessor: MetricsSampler? = null,
) {

    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var boundWindow: Window? = null

    @Volatile
    private var boundDisplay: Display? = null

    private var tickGeneration = 0

    init {
        // The aggregator is shared with the thread being replaced; keep the two from overlapping.
        if (predecessor != null) handler.post { awaitTermination(predecessor.thread) }
    }

    val threadName: String get() = thread.name

    @get:AnyThread
    val isBound: Boolean get() = boundWindow != null

    @get:AnyThread
    val display: Display? get() = boundDisplay

    fun startTicking() {
        handler.post { postTick(++tickGeneration) }
    }

    fun stopTicking() {
        handler.post { tickGeneration++ }
    }

    fun bind(window: Window): Boolean {
        if (boundWindow === window) return false
        unbind()
        boundWindow = window
        boundDisplay = window.decorView.display
        window.addOnFrameMetricsAvailableListener(listener, handler)
        return true
    }

    fun unbind(): Boolean {
        val window = boundWindow ?: return false
        boundWindow = null
        boundDisplay = null
        guarded("removing the frame metrics listener") { window.removeOnFrameMetricsAvailableListener(listener) }
        return true
    }

    @AnyThread
    fun post(action: () -> Unit): Boolean = handler.post { guarded("running a metrics task", action) }

    fun quit(): Window? {
        val window = boundWindow
        unbind()
        stopTicking()
        thread.quitSafely()
        return window
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
