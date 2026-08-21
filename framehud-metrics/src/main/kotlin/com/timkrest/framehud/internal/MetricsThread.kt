package com.timkrest.framehud.internal

import android.util.Log
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread

@AnyThread
internal class MetricsThread(
    private val listener: Window.OnFrameMetricsAvailableListener,
    private val tickIntervalMs: () -> Long,
    private val onTick: () -> Unit,
) {

    private val lock = Any()

    @Volatile
    private var sampler: MetricsSampler? = null

    val started: MetricsSampler? get() = sampler

    fun require(threadName: String): MetricsSampler = synchronized(lock) {
        sampler ?: start(threadName, replacing = null)
    }

    fun post(threadName: String, action: () -> Unit) {
        synchronized(lock) { postOrWarn(sampler ?: start(threadName, replacing = null), action) }
    }

    fun postOrRunHere(action: () -> Unit) {
        synchronized(lock) {
            val running = sampler ?: return action()
            postOrWarn(running, action)
        }
    }

    @MainThread
    fun renameTo(threadName: String, rebind: () -> List<Window>, keepTicking: Boolean) {
        synchronized(lock) {
            val previous = sampler ?: return
            if (previous.threadName == threadName) return
            val running = start(threadName, replacing = previous)
            val rebound = rebind()
            rebound.forEach(previous::unbind)
            previous.quit()
            rebound.forEach(running::bind)
            if (keepTicking) running.startTicking()
        }
    }

    private fun start(threadName: String, replacing: MetricsSampler?): MetricsSampler = MetricsSampler(
        threadName = threadName,
        listener = listener,
        tickIntervalMs = tickIntervalMs,
        onTick = onTick,
        previousSampler = replacing,
    ).also { sampler = it }

    private fun postOrWarn(sampler: MetricsSampler, action: () -> Unit) {
        if (!sampler.post(action)) Log.w(LOG_TAG, "The metrics thread is gone, dropped a metrics task")
    }
}
