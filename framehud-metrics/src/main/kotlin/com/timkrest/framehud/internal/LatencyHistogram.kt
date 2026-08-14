package com.timkrest.framehud.internal

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** Bucketed rather than kept raw, so a session of any length costs a fixed amount of memory. */
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

    private companion object {
        const val FINE_BUCKET_WIDTH_MS = 0.25f
        const val FINE_BUCKET_COUNT = 256
        const val FINE_RANGE_MS = FINE_BUCKET_COUNT * FINE_BUCKET_WIDTH_MS
        const val TAIL_RATIO = 1.05f
        const val TAIL_MAX_MS = 10f * 60f * MS_PER_SECOND

        val LN_TAIL_RATIO = ln(TAIL_RATIO)
        val TAIL_BUCKET_COUNT = ceil(ln(TAIL_MAX_MS / FINE_RANGE_MS) / LN_TAIL_RATIO).toInt()
        val TAIL_BOUNDS_MS = FloatArray(TAIL_BUCKET_COUNT) { index ->
            if (index == TAIL_BUCKET_COUNT - 1) {
                Float.POSITIVE_INFINITY
            } else {
                FINE_RANGE_MS * TAIL_RATIO.pow(index + 1)
            }
        }
    }
}
