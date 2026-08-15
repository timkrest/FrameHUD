package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Peaks and GC counters run from the last reset. Time in the background does not count. */
@Immutable
public data class MemoryStats(
    val usedHeapMb: Int = 0,
    val maxHeapMb: Int = 0,
    val nativeHeapMb: Int = 0,
    val peakUsedHeapMb: Int = 0,
    val peakNativeHeapMb: Int = 0,
    val gcCount: Int = 0,
    val gcTimeMs: Long = 0L,
) {
    public companion object {
        public val EMPTY: MemoryStats = MemoryStats()
    }
}
