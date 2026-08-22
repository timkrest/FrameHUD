package com.timkrest.framehud.internal

import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.MainThreadBlock

@AnyThread
internal class MainThreadWatchdog(
    private val mainThread: Thread,
    private val lastTickMs: () -> Long,
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
) {

    private val sampler = MainThreadSampler()

    private val poller = PollingThread(WATCHDOG_THREAD_NAME)

    @Volatile
    private var latest = MainThreadBlock.NONE

    @Volatile
    private var latestAtMs = 0L

    private var isBlocked = false

    val latestBlock: MainThreadBlock
        get() = if (nowMs() - latestAtMs > BLOCK_EXPLAINS_JANK_FOR_MS) MainThreadBlock.NONE else latest

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
        val quietMs = nowMs() - tickMs
        if (quietMs < BLOCKED_AFTER_MS) {
            endBlock(drewAtMs = tickMs)
            return BLOCKED_AFTER_MS - quietMs
        }
        guarded("sampling the main thread") {
            if (!isBlocked) {
                isBlocked = true
                sampler.beginBlock(tickMs)
            }
            sampler.sample(mainThread.stackTrace)
            recordBlock(endedMs = nowMs())
        }
        return SAMPLE_INTERVAL_MS
    }

    private fun forgetBlock() {
        isBlocked = false
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
        const val SAMPLE_INTERVAL_MS = 100L
        const val BLOCK_EXPLAINS_JANK_FOR_MS = 2_000L
    }
}
