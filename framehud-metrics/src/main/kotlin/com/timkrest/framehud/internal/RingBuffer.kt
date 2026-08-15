package com.timkrest.framehud.internal

import kotlin.math.max
import kotlin.math.min

internal class RingBuffer(capacity: Int) {

    private var buffer = FloatArray(capacity)
    private var writePos = 0
    private var runningSum = 0f

    private var sortScratch: FloatArray? = null

    private var peakValue = 0f
    private var hasPeak = false

    private val capacity: Int get() = buffer.size

    var size = 0
        private set

    /** Highest value since [clear], including samples no longer in the ring. */
    val peak: Float? get() = if (hasPeak) peakValue else null

    fun add(value: Float) {
        if (size == capacity) {
            runningSum -= buffer[writePos]
        } else {
            size++
        }
        buffer[writePos] = value
        runningSum += value
        writePos = (writePos + 1) % capacity
        if (!hasPeak || value > peakValue) {
            peakValue = value
            hasPeak = true
        }
    }

    fun average(): Float {
        if (size == 0) return 0f
        return runningSum / size
    }

    fun last(): Float {
        if (size == 0) return 0f
        return buffer[(writePos - 1 + capacity) % capacity]
    }

    fun windowMax(): Float = reduceWindow { best, sample -> max(best, sample) }

    fun percentile(percent: Float): Float {
        if (size == 0) return 0f
        val normalized = percent.coerceIn(0f, PERCENT)
        if (normalized == 0f) return windowMin()
        if (normalized == PERCENT) return windowMax()
        return sortedSamples()[nearestRank(normalized, size) - 1]
    }

    fun snapshot(): FloatArray {
        if (size == 0) return EMPTY_SNAPSHOT
        return FloatArray(size).also(::copySamplesInto)
    }

    fun resizeTo(capacity: Int) {
        if (capacity == buffer.size) return
        buffer = FloatArray(capacity)
        sortScratch = null
        forgetSamples()
    }

    fun clear() {
        forgetSamples()
        peakValue = 0f
        hasPeak = false
    }

    private fun forgetSamples() {
        writePos = 0
        size = 0
        runningSum = 0f
    }

    private fun windowMin(): Float = reduceWindow { best, sample -> min(best, sample) }

    private inline fun reduceWindow(pick: (Float, Float) -> Float): Float {
        if (size == 0) return 0f
        val start = startIndex()
        var result = buffer[start]
        for (i in 1 until size) {
            result = pick(result, buffer[(start + i) % capacity])
        }
        return result
    }

    private fun sortedSamples(): FloatArray {
        val scratch = sortScratch ?: FloatArray(capacity).also { sortScratch = it }
        copySamplesInto(scratch)
        scratch.sort(fromIndex = 0, toIndex = size)
        return scratch
    }

    private fun copySamplesInto(destination: FloatArray) {
        val start = startIndex()
        val untilWrap = min(size, capacity - start)
        buffer.copyInto(destination, destinationOffset = 0, startIndex = start, endIndex = start + untilWrap)
        if (untilWrap < size) {
            buffer.copyInto(destination, destinationOffset = untilWrap, startIndex = 0, endIndex = size - untilWrap)
        }
    }

    private fun startIndex(): Int = (writePos - size + capacity) % capacity

    private companion object {
        val EMPTY_SNAPSHOT = FloatArray(0)
    }
}
