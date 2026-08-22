package com.timkrest.framehud.internal

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

    private val poller = PollingThread(PROCESS_THREAD_NAME)

    val stats: StateFlow<ProcessStats> = readings.published

    val liveStats: ProcessStats get() = readings.live

    fun startCollecting() {
        poller.startPolling(poll = ::sampleOnce)
    }

    fun stopCollecting() {
        poller.stopPolling()
    }

    fun stop() {
        poller.quit()
    }

    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun reset() = poller.postOrRunHere {
        sampler.reset()
        readings.reset(ProcessStats.EMPTY)
    }

    @WorkerThread
    private fun sampleOnce(): Long {
        guarded("sampling process stats") { readings.update(sampler.sample()) }
        return sampleIntervalMs
    }

    private companion object {
        const val PROCESS_THREAD_NAME = "framehud-process"
    }
}
