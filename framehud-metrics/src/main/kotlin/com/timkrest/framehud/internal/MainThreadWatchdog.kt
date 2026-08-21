package com.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
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

    @Volatile
    private var thread: HandlerThread? = null

    @Volatile
    private var handler: Handler? = null

    @Volatile
    private var latest = MainThreadBlock.NONE

    @Volatile
    private var latestAtMs = 0L

    private var isBlocked = false

    private var watchGeneration = 0

    val latestBlock: MainThreadBlock
        get() = if (nowMs() - latestAtMs > BLOCK_EXPLAINS_JANK_FOR_MS) MainThreadBlock.NONE else latest

    fun startWatching() {
        val running = handler ?: startThread()
        running.post {
            forgetBlock()
            watch(++watchGeneration)
        }
    }

    fun stopWatching() {
        handler?.post { watchGeneration++ }
    }

    fun stop() {
        stopWatching()
        thread?.quit()
        thread = null
        handler = null
    }

    @WorkerThread
    private fun watch(generation: Int) {
        if (generation != watchGeneration) return
        val tickMs = lastTickMs()
        val quietMs = nowMs() - tickMs
        if (quietMs < BLOCKED_AFTER_MS) {
            endBlock(drewAtMs = tickMs)
            handler?.postDelayed({ watch(generation) }, BLOCKED_AFTER_MS - quietMs)
            return
        }
        guarded("sampling the main thread") {
            if (!isBlocked) {
                isBlocked = true
                sampler.beginBlock(tickMs)
            }
            sampler.sample(mainThread.stackTrace)
            recordBlock(endedMs = nowMs())
        }
        handler?.postDelayed({ watch(generation) }, SAMPLE_INTERVAL_MS)
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

    private fun startThread(): Handler {
        val started = HandlerThread(WATCHDOG_THREAD_NAME, Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        thread = started
        return Handler(started.looper).also { handler = it }
    }

    private companion object {
        const val WATCHDOG_THREAD_NAME = "framehud-watchdog"
        const val BLOCKED_AFTER_MS = 300L
        const val SAMPLE_INTERVAL_MS = 100L
        const val BLOCK_EXPLAINS_JANK_FOR_MS = 2_000L
    }
}
