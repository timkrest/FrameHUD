package com.timkrest.framehud.internal

import android.os.Debug
import com.timkrest.framehud.MemoryStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

internal class MemoryStatsMonitor {

    private val _stats = MutableStateFlow(MemoryStats.EMPTY)
    val stats: StateFlow<MemoryStats> = _stats.asStateFlow()

    @Volatile
    private var isFrozen = false

    private var gcBaseline: GcBaseline? = null
    private var peakUsedHeapMb = 0
    private var peakNativeHeapMb = 0

    fun sample() {
        val baseline = gcBaseline ?: readGcBaseline().also { gcBaseline = it }
        val runtime = Runtime.getRuntime()
        val usedHeapMb = ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).toInt()
        val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB).toInt()
        peakUsedHeapMb = max(peakUsedHeapMb, usedHeapMb)
        peakNativeHeapMb = max(peakNativeHeapMb, nativeHeapMb)
        if (isFrozen) return

        _stats.value = MemoryStats(
            usedHeapMb = usedHeapMb,
            maxHeapMb = (runtime.maxMemory() / BYTES_PER_MB).toInt(),
            nativeHeapMb = nativeHeapMb,
            peakUsedHeapMb = peakUsedHeapMb,
            peakNativeHeapMb = peakNativeHeapMb,
            gcCount = readGcCount() - baseline.count,
            gcTimeMs = readGcTimeMs() - baseline.timeMs,
        )
    }

    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
    }

    fun reset() {
        gcBaseline = readGcBaseline()
        peakUsedHeapMb = 0
        peakNativeHeapMb = 0
        _stats.value = MemoryStats.EMPTY
    }

    private fun readGcBaseline(): GcBaseline = GcBaseline(count = readGcCount(), timeMs = readGcTimeMs())

    private fun readGcCount(): Int = Debug.getRuntimeStat(STAT_GC_COUNT)?.toIntOrNull() ?: 0

    private fun readGcTimeMs(): Long = Debug.getRuntimeStat(STAT_GC_TIME)?.toLongOrNull() ?: 0L

    /** GC counters are cumulative for the process, so they are only meaningful against a baseline. */
    private data class GcBaseline(val count: Int, val timeMs: Long)

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
        const val STAT_GC_COUNT = "art.gc.gc-count"
        const val STAT_GC_TIME = "art.gc.gc-time"
    }
}
