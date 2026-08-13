package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread

@WorkerThread
internal class WorstFrames(private val capacity: Int) {

    private val frames = ArrayList<Frame>(capacity)

    /** Runs per frame, so the common case — not a worst frame — is one comparison, no allocation. */
    private var entryBarMs = Float.MAX_VALUE

    fun add(totalMs: Float, endNs: Long) {
        if (frames.size < capacity) {
            frames += Frame(totalMs = totalMs, endNs = endNs)
            if (frames.size == capacity) entryBarMs = weakestMs()
            return
        }
        if (totalMs <= entryBarMs) return
        frames[weakestIndex()] = Frame(totalMs = totalMs, endNs = endNs)
        entryBarMs = weakestMs()
    }

    fun snapshot(): List<Frame> = frames.sortedByDescending { it.totalMs }

    fun clear() {
        frames.clear()
        entryBarMs = Float.MAX_VALUE
    }

    private fun weakestIndex(): Int {
        var weakest = 0
        for (index in 1 until frames.size) {
            if (frames[index].totalMs < frames[weakest].totalMs) weakest = index
        }
        return weakest
    }

    private fun weakestMs(): Float = frames[weakestIndex()].totalMs

    data class Frame(val totalMs: Float, val endNs: Long)
}
