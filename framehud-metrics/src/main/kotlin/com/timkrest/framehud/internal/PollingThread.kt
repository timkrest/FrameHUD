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

    private var handler: Handler? = null

    private var generation = 0

    fun startPolling(beforeFirstPoll: () -> Unit = {}, poll: () -> Long) {
        synchronized(lock) {
            val running = handler ?: start()
            val startedAs = ++generation
            running.post {
                beforeFirstPoll()
                pollAgain(running, startedAs, poll)
            }
        }
    }

    fun stopPolling() {
        synchronized(lock) { generation++ }
    }

    fun quit() {
        synchronized(lock) {
            generation++
            thread?.quit()
            thread = null
            handler = null
        }
    }

    fun postOrRunHere(action: () -> Unit) {
        val running = synchronized(lock) { handler } ?: return action()
        running.post(action)
    }

    @WorkerThread
    private fun pollAgain(on: Handler, startedAs: Int, poll: () -> Long) {
        if (synchronized(lock) { startedAs != generation }) return
        val nextPollInMs = poll()
        on.postDelayed({ pollAgain(on, startedAs, poll) }, nextPollInMs)
    }

    private fun start(): Handler {
        val started = HandlerThread(threadName, Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        thread = started
        return Handler(started.looper).also { handler = it }
    }
}
