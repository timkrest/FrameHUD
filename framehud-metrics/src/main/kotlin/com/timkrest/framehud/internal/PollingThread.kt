package com.timkrest.framehud.internal

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread

@AnyThread
internal class PollingThread(private val threadName: String) {

    private val lock = Any()

    private var thread: HandlerThread? = null

    @Volatile
    private var handler: Handler? = null

    @Volatile
    private var generation = 0

    fun startPolling(beforeFirstPoll: () -> Unit = {}, poll: () -> Long) {
        val running = synchronized(lock) { handler ?: start() }
        running.post {
            beforeFirstPoll()
            pollAgain(++generation, poll)
        }
    }

    fun stopPolling() {
        handler?.post { generation++ }
    }

    fun quit() {
        stopPolling()
        synchronized(lock) {
            thread?.quit()
            thread = null
            handler = null
        }
    }

    fun postOrRunHere(action: () -> Unit) {
        val running = handler ?: return action()
        running.post(action)
    }

    @WorkerThread
    private fun pollAgain(startedAs: Int, poll: () -> Long) {
        if (startedAs != generation) return
        val nextPollInMs = poll()
        handler?.postDelayed({ pollAgain(startedAs, poll) }, nextPollInMs)
    }

    private fun start(): Handler {
        val started = HandlerThread(threadName, Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        thread = started
        return Handler(started.looper).also { handler = it }
    }
}
