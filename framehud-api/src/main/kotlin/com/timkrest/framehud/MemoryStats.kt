package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Peaks and GC counters run from the last reset. Time in the background does not count. */
@Immutable
@ConsistentCopyVisibility
public data class MemoryStats private constructor(
    val usedHeapMb: Int,
    val maxHeapMb: Int,
    val nativeHeapMb: Int,
    val peakUsedHeapMb: Int,
    val peakNativeHeapMb: Int,
    val gcCount: Int,
    val gcTimeMs: Long,
) {
    public companion object {
        public val EMPTY: MemoryStats = of()

        @InternalFrameHudApi
        public fun of(
            usedHeapMb: Int = 0,
            maxHeapMb: Int = 0,
            nativeHeapMb: Int = 0,
            peakUsedHeapMb: Int = 0,
            peakNativeHeapMb: Int = 0,
            gcCount: Int = 0,
            gcTimeMs: Long = 0L,
        ): MemoryStats = MemoryStats(
            usedHeapMb = usedHeapMb,
            maxHeapMb = maxHeapMb,
            nativeHeapMb = nativeHeapMb,
            peakUsedHeapMb = peakUsedHeapMb,
            peakNativeHeapMb = peakNativeHeapMb,
            gcCount = gcCount,
            gcTimeMs = gcTimeMs,
        )
    }
}
