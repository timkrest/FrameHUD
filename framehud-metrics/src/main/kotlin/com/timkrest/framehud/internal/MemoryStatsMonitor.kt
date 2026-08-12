package com.timkrest.framehud.internal

import android.os.Debug
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.MemoryStats
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

@WorkerThread
internal class MemoryStatsMonitor {

    private val readings = FreezableReading(MemoryStats.EMPTY)

    @get:AnyThread
    val stats: StateFlow<MemoryStats> = readings.published

    val liveStats: MemoryStats get() = readings.live

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

        readings.update(
            MemoryStats(
                usedHeapMb = usedHeapMb,
                maxHeapMb = (runtime.maxMemory() / BYTES_PER_MB).toInt(),
                nativeHeapMb = nativeHeapMb,
                peakUsedHeapMb = peakUsedHeapMb,
                peakNativeHeapMb = peakNativeHeapMb,
                gcCount = readGcCount() - baseline.count,
                gcTimeMs = readGcTimeMs() - baseline.timeMs,
            ),
        )
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun reset() {
        gcBaseline = readGcBaseline()
        peakUsedHeapMb = 0
        peakNativeHeapMb = 0
        readings.reset(MemoryStats.EMPTY)
    }

    private fun readGcBaseline(): GcBaseline = GcBaseline(count = readGcCount(), timeMs = readGcTimeMs())

    private fun readGcCount(): Int = Debug.getRuntimeStat(STAT_GC_COUNT)?.toIntOrNull() ?: 0

    private fun readGcTimeMs(): Long = Debug.getRuntimeStat(STAT_GC_TIME)?.toLongOrNull() ?: 0L

    private data class GcBaseline(val count: Int, val timeMs: Long)

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
        const val STAT_GC_COUNT = "art.gc.gc-count"
        const val STAT_GC_TIME = "art.gc.gc-time"
    }
}
