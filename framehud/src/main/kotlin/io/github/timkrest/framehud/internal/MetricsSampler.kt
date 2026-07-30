package io.github.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Window

/**
 * Owns the thread `FrameMetrics` callbacks arrive on, plus the tick that keeps readings fresh once
 * the screen goes idle and no more callbacks come.
 */
internal class MetricsSampler(
    threadName: String,
    private val listener: Window.OnFrameMetricsAvailableListener,
    private val tickIntervalMs: () -> Long,
    private val onTick: () -> Unit,
) {

    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)
    private var boundWindow: Window? = null

    fun start() {
        val tick = object : Runnable {
            override fun run() {
                onTick()
                handler.postDelayed(this, tickIntervalMs())
            }
        }
        handler.postDelayed(tick, tickIntervalMs())
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

    fun post(action: () -> Unit): Boolean = handler.post(action)

    /** Runs what is already queued and drops the pending tick, so a final [post] still gets through. */
    fun quit() {
        unbind()
        thread.quitSafely()
        awaitTermination(thread)
    }

    private fun awaitTermination(thread: Thread) {
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (e: InterruptedException) {
                interrupted = true
                Log.w(LOG_TAG, "Interrupted while joining the metrics thread, retrying", e)
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
