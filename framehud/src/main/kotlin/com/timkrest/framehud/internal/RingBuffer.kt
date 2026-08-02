package com.timkrest.framehud.internal

import kotlin.math.max
import kotlin.math.min

/**
 * The last [capacity] samples, with a running sum so the average costs nothing to read. Sampling
 * runs on every frame, so nothing here allocates once the buffer is built.
 */
internal class RingBuffer(private val capacity: Int) {

    private val buffer = FloatArray(capacity)
    private var writePos = 0
    private var runningSum = 0f

    private var sortScratch: FloatArray? = null

    private var peakValue = 0f
    private var hasPeak = false

    var size = 0
        private set

    /**
     * Highest value since the last [clear], including samples the window has since evicted, or null
     * while nothing has been added. Samples can be negative, so zero cannot stand in for "no peak".
     */
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

    fun clear() {
        writePos = 0
        size = 0
        runningSum = 0f
        peakValue = 0f
        hasPeak = false
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
