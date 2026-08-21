package com.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.ProcessStats
import kotlinx.coroutines.flow.StateFlow

@AnyThread
internal class ProcessStatsMonitor(
    clock: MetricsClock,
    private val sampleIntervalMs: Long,
    probe: ProcessProbe = SystemProcessProbe,
) {

    private val readings = FreezableReading(ProcessStats.EMPTY)

    private val sampler = ProcessSampler(clock, probe)

    val stats: StateFlow<ProcessStats> = readings.published

    val liveStats: ProcessStats get() = readings.live

    @Volatile
    private var thread: HandlerThread? = null

    @Volatile
    private var handler: Handler? = null

    private var sampleGeneration = 0

    fun startCollecting() {
        val running = handler ?: startThread()
        running.post { sample(++sampleGeneration) }
    }

    fun stopCollecting() {
        handler?.post { sampleGeneration++ }
    }

    fun stop() {
        stopCollecting()
        thread?.quit()
        thread = null
        handler = null
    }

    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun reset() = onSamplingThread {
        sampler.reset()
        readings.reset(ProcessStats.EMPTY)
    }

    @WorkerThread
    private fun sample(generation: Int) {
        if (generation != sampleGeneration) return
        guarded("sampling process stats") { readings.update(sampler.sample()) }
        handler?.postDelayed({ sample(generation) }, sampleIntervalMs)
    }

    private fun startThread(): Handler {
        val started = HandlerThread(PROCESS_THREAD_NAME, Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        thread = started
        return Handler(started.looper).also { handler = it }
    }

    private fun onSamplingThread(action: () -> Unit) {
        val running = handler
        if (running == null) action() else running.post(action)
    }

    private companion object {
        const val PROCESS_THREAD_NAME = "framehud-process"
    }
}
