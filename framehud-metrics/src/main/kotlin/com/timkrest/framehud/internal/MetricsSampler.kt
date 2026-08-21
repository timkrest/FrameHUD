package com.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread

@MainThread
internal class MetricsSampler(
    threadName: String,
    private val listener: Window.OnFrameMetricsAvailableListener,
    private val tickIntervalMs: () -> Long,
    private val onTick: () -> Unit,
    previousSampler: MetricsSampler? = null,
) {

    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)

    private var tickGeneration = 0

    init {
        if (previousSampler != null) handler.post { waitForTermination(previousSampler.thread) }
    }

    val threadName: String get() = thread.name

    fun startTicking() {
        handler.post { postTick(++tickGeneration) }
    }

    fun stopTicking() {
        handler.post { tickGeneration++ }
    }

    fun bind(window: Window) {
        window.addOnFrameMetricsAvailableListener(listener, handler)
    }

    fun unbind(window: Window) {
        guarded("removing the frame metrics listener") { window.removeOnFrameMetricsAvailableListener(listener) }
    }

    @AnyThread
    fun post(action: () -> Unit): Boolean = handler.post { guarded("running a metrics task", action) }

    fun quit() {
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

    private fun waitForTermination(thread: Thread) {
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
