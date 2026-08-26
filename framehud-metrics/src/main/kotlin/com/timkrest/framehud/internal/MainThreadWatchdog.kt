package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.MainThreadBlock
import kotlin.math.min

@AnyThread
internal class MainThreadWatchdog(
    private val mainThread: Thread,
    private val lastTickMs: () -> Long,
    private val clock: MetricsClock = SystemMetricsClock,
) {

    private val sampler = MainThreadSampler()

    private val poller = PollingThread(WATCHDOG_THREAD_NAME)

    @Volatile
    private var latest = MainThreadBlock.NONE

    @Volatile
    private var latestAtMs = 0L

    private var isBlocked = false

    private var blockedAfterTickMs = 0L

    private var sampleIntervalMs = FIRST_SAMPLE_INTERVAL_MS

    val latestBlock: MainThreadBlock
        get() = if (clock.uptimeMs() - latestAtMs > BLOCK_EXPLAINS_JANK_FOR_MS) MainThreadBlock.NONE else latest

    fun startWatching() {
        poller.startPolling(beforeFirstPoll = ::forgetBlock, poll = ::watchOnce)
    }

    fun stopWatching() {
        poller.stopPolling()
    }

    fun stop() {
        poller.quit()
    }

    @WorkerThread
    private fun watchOnce(): Long {
        val tickMs = lastTickMs()
        val quietMs = clock.uptimeMs() - tickMs
        if (quietMs < BLOCKED_AFTER_MS) {
            endBlock(drewAtMs = tickMs)
            return BLOCKED_AFTER_MS - quietMs
        }
        guarded("sampling the main thread") {
            if (tickMs != blockedAfterTickMs) endBlock(drewAtMs = tickMs)
            if (!isBlocked) {
                isBlocked = true
                blockedAfterTickMs = tickMs
                sampleIntervalMs = FIRST_SAMPLE_INTERVAL_MS
                sampler.beginBlock(tickMs)
            }
            sampler.sample(mainThread.stackTrace)
            recordBlock(endedMs = clock.uptimeMs())
        }
        val takeNextStackInMs = sampleIntervalMs
        sampleIntervalMs = min(sampleIntervalMs * 2, MAX_SAMPLE_INTERVAL_MS)
        return takeNextStackInMs
    }

    private fun forgetBlock() {
        isBlocked = false
        blockedAfterTickMs = 0L
        sampleIntervalMs = FIRST_SAMPLE_INTERVAL_MS
        latest = MainThreadBlock.NONE
        latestAtMs = 0
    }

    private fun endBlock(drewAtMs: Long) {
        if (!isBlocked) return
        recordBlock(endedMs = drewAtMs)
        isBlocked = false
    }

    private fun recordBlock(endedMs: Long) {
        latest = sampler.blockAt(endedMs)
        latestAtMs = endedMs
    }

    private companion object {
        const val WATCHDOG_THREAD_NAME = "framehud-watchdog"
        const val BLOCKED_AFTER_MS = 300L
        const val FIRST_SAMPLE_INTERVAL_MS = 100L
        const val MAX_SAMPLE_INTERVAL_MS = 800L
        const val BLOCK_EXPLAINS_JANK_FOR_MS = 2_000L
    }
}
