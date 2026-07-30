package io.github.timkrest.framehud.internal

import kotlin.math.max
import kotlin.math.min

/**
 * The last [capacity] samples, with a running sum so the average costs nothing to read. Sampling
 * runs on every frame, so nothing here allocates once the buffer is built.
 */
internal class RingBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    private val buffer = FloatArray(capacity)
    private var writePos = 0
    private var runningSum = 0f

    /** Reused by [percentile]; only the rings that report percentiles ever allocate it. */
    private var sortScratch: FloatArray? = null

    var size = 0
        private set

    /** Highest value since the last [clear], including samples the window has since evicted. */
    var peak = 0f
        private set

    fun add(value: Float) {
        if (size == capacity) {
            runningSum -= buffer[writePos]
        } else {
            size++
        }
        buffer[writePos] = value
        runningSum += value
        writePos = (writePos + 1) % capacity
        peak = max(peak, value)
    }

    fun average(): Float {
        if (size == 0) return 0f
        return runningSum / size
    }

    fun last(): Float {
        if (size == 0) return 0f
        return buffer[(writePos - 1 + capacity) % capacity]
    }

    fun windowMax(): Float {
        if (size == 0) return 0f
        var result = buffer[0]
        for (i in 1 until size) {
            result = max(result, buffer[i])
        }
        return result
    }

    fun percentile(percent: Float): Float {
        if (size == 0) return 0f
        val normalized = percent.coerceIn(0f, PERCENT)
        if (normalized == 0f) return windowMin()
        if (normalized == PERCENT) return windowMax()
        return sortedSamples()[nearestRank(normalized, size) - 1]
    }

    /** The window in insertion order, oldest first. */
    fun snapshot(): FloatArray {
        if (size == 0) return EMPTY_SNAPSHOT
        val start = (writePos - size + capacity) % capacity
        return FloatArray(size) { buffer[(start + it) % capacity] }
    }

    fun clear() {
        writePos = 0
        size = 0
        runningSum = 0f
        peak = 0f
    }

    private fun windowMin(): Float {
        if (size == 0) return 0f
        var result = buffer[0]
        for (i in 1 until size) {
            result = min(result, buffer[i])
        }
        return result
    }

    /** Sorted, so out of insertion order — fine for percentiles, wrong for anything positional. */
    private fun sortedSamples(): FloatArray {
        val scratch = sortScratch ?: FloatArray(capacity).also { sortScratch = it }
        buffer.copyInto(destination = scratch, endIndex = size)
        scratch.sort(fromIndex = 0, toIndex = size)
        return scratch
    }

    companion object {
        private const val DEFAULT_CAPACITY = 60
        private val EMPTY_SNAPSHOT = FloatArray(0)
    }
}
