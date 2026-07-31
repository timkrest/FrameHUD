package com.timkrest.framehud.internal

internal class FrameTimestamps(private val capacity: Int) {

    private val buffer = LongArray(capacity)
    private var writePos = 0
    private var size = 0

    fun add(timestampNs: Long) {
        buffer[writePos] = timestampNs
        writePos = (writePos + 1) % capacity
        if (size < capacity) size++
    }

    fun countSince(cutoffNs: Long): Int {
        var count = 0
        for (i in 0 until size) {
            if (buffer[i] >= cutoffNs) count++
        }
        return count
    }

    fun clear() {
        writePos = 0
        size = 0
    }
}
