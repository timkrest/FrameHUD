package com.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
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

    @Volatile
    private var boundWindow: Window? = null

    @Volatile
    private var boundDisplay: Display? = null

    private var tickGeneration = 0

    init {
        if (previousSampler != null) handler.post { waitForTermination(previousSampler.thread) }
    }

    val threadName: String get() = thread.name

    @get:AnyThread
    val isBound: Boolean get() = boundWindow != null

    @get:AnyThread
    val display: Display? get() = boundDisplay

    fun isBoundTo(window: Window): Boolean = boundWindow === window

    fun startTicking() {
        handler.post { postTick(++tickGeneration) }
    }

    fun stopTicking() {
        handler.post { tickGeneration++ }
    }

    fun bind(window: Window) {
        unbind()
        boundWindow = window
        boundDisplay = window.decorView.display
        window.addOnFrameMetricsAvailableListener(listener, handler)
        startTicking()
    }

    fun unbind(): Window? {
        val window = boundWindow ?: return null
        boundWindow = null
        boundDisplay = null
        guarded("removing the frame metrics listener") { window.removeOnFrameMetricsAvailableListener(listener) }
        stopTicking()
        return window
    }

    @AnyThread
    fun post(action: () -> Unit): Boolean = handler.post { guarded("running a metrics task", action) }

    fun quit(): Window? {
        val window = unbind()
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

    /** Two metrics threads alive at once would both write to the same aggregates. */
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
