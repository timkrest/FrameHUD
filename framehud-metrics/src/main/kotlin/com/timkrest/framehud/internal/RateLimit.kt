package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread

@WorkerThread
internal class RateLimit(private val clock: MetricsClock, private val intervalMs: Long) {

    private var lastTakenMs: Long? = null

    fun tryTake(): Boolean {
        val nowMs = clock.elapsedRealtimeMs()
        val takenMs = lastTakenMs
        if (takenMs != null && nowMs - takenMs < intervalMs) return false
        lastTakenMs = nowMs
        return true
    }

    fun clear() {
        lastTakenMs = null
    }
}
