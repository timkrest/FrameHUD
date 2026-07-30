package io.github.timkrest.framehud.internal

import android.os.Debug
import io.github.timkrest.framehud.MemoryStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

internal class MemoryStatsMonitor {

    private val _stats = MutableStateFlow(MemoryStats.EMPTY)
    val stats: StateFlow<MemoryStats> = _stats.asStateFlow()

    @Volatile
    private var isFrozen = false

    private var baselineGcCount = NO_BASELINE
    private var baselineGcTimeMs = 0L
    private var peakUsedHeapMb = 0
    private var peakNativeHeapMb = 0

    fun sample() {
        if (baselineGcCount == NO_BASELINE) {
            baselineGcCount = readGcCount()
            baselineGcTimeMs = readGcTimeMs()
        }
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
            gcCount = readGcCount() - baselineGcCount,
            gcTimeMs = readGcTimeMs() - baselineGcTimeMs,
        )
    }

    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
    }

    fun reset() {
        baselineGcCount = readGcCount()
        baselineGcTimeMs = readGcTimeMs()
        peakUsedHeapMb = 0
        peakNativeHeapMb = 0
        _stats.value = MemoryStats.EMPTY
    }

    private fun readGcCount(): Int = Debug.getRuntimeStat(STAT_GC_COUNT)?.toIntOrNull() ?: 0

    private fun readGcTimeMs(): Long = Debug.getRuntimeStat(STAT_GC_TIME)?.toLongOrNull() ?: 0L

    companion object {
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val STAT_GC_COUNT = "art.gc.gc-count"
        private const val STAT_GC_TIME = "art.gc.gc-time"
        private const val NO_BASELINE = -1
    }
}
