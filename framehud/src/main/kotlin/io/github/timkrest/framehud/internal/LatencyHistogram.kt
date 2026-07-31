package io.github.timkrest.framehud.internal

import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/**
 * Frame times bucketed so a session of any length costs a fixed amount of memory: 0.25 ms buckets
 * up to 64 ms, then buckets widening by 5% each. Percentiles are therefore approximate, and read as
 * the upper bound of the bucket the rank lands in.
 */
internal class LatencyHistogram {

    private val fineBuckets = IntArray(FINE_BUCKET_COUNT)
    private val tailBuckets = IntArray(TAIL_BUCKET_COUNT)

    var count = 0
        private set

    private var max = 0f

    fun add(valueMs: Float) {
        count++
        if (valueMs > max) max = valueMs
        if (valueMs < FINE_RANGE_MS) {
            fineBuckets[(valueMs / FINE_BUCKET_WIDTH_MS).toInt().coerceIn(0, fineBuckets.lastIndex)]++
            return
        }
        val tailIndex = (ln(valueMs / FINE_RANGE_MS) / LN_TAIL_RATIO).toInt()
        tailBuckets[tailIndex.coerceIn(0, tailBuckets.lastIndex)]++
    }

    fun percentile(percent: Float): Float {
        if (count == 0) return 0f
        val rank = nearestRank(percent.coerceIn(0f, PERCENT), count)
        var cumulative = 0
        for (index in fineBuckets.indices) {
            cumulative += fineBuckets[index]
            if (cumulative >= rank) return upperBoundMs((index + 1) * FINE_BUCKET_WIDTH_MS)
        }
        for (index in tailBuckets.indices) {
            cumulative += tailBuckets[index]
            if (cumulative >= rank) return upperBoundMs(TAIL_BOUNDS_MS[index])
        }
        return max
    }

    fun clear() {
        fineBuckets.fill(0)
        tailBuckets.fill(0)
        count = 0
        max = 0f
    }

    private fun upperBoundMs(boundMs: Float): Float = min(boundMs, max)

    companion object {
        private const val FINE_BUCKET_WIDTH_MS = 0.25f
        private const val FINE_BUCKET_COUNT = 256
        private const val FINE_RANGE_MS = FINE_BUCKET_COUNT * FINE_BUCKET_WIDTH_MS
        private const val TAIL_RATIO = 1.05f
        private const val TAIL_BUCKET_COUNT = 188

        private val LN_TAIL_RATIO = ln(TAIL_RATIO)
        private val TAIL_BOUNDS_MS = FloatArray(TAIL_BUCKET_COUNT) { index ->
            // The last bucket is open-ended, so its bound is whatever the longest frame turned out to be.
            if (index == TAIL_BUCKET_COUNT - 1) {
                Float.POSITIVE_INFINITY
            } else {
                FINE_RANGE_MS * TAIL_RATIO.pow(index + 1)
            }
        }
    }
}
