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

    private var collectingSince: GcCounters? = null
    private var collected = GcCounters.NONE
    private var peakUsedHeapMb = 0
    private var peakNativeHeapMb = 0

    fun startCollecting() {
        if (collectingSince == null) collectingSince = readGcCounters()
    }

    fun stopCollecting() {
        val started = collectingSince ?: return
        collected += readGcCounters() - started
        collectingSince = null
    }

    fun sample() {
        val gc = collectedGc()
        val runtime = Runtime.getRuntime()
        val usedHeapMb = ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).toInt()
        val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB).toInt()
        peakUsedHeapMb = max(peakUsedHeapMb, usedHeapMb)
        peakNativeHeapMb = max(peakNativeHeapMb, nativeHeapMb)

        readings.update(
            MemoryStats.of(
                usedHeapMb = usedHeapMb,
                maxHeapMb = (runtime.maxMemory() / BYTES_PER_MB).toInt(),
                nativeHeapMb = nativeHeapMb,
                peakUsedHeapMb = peakUsedHeapMb,
                peakNativeHeapMb = peakNativeHeapMb,
                gcCount = gc.count,
                gcTimeMs = gc.timeMs,
            ),
        )
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun reset() {
        collected = GcCounters.NONE
        if (collectingSince != null) collectingSince = readGcCounters()
        peakUsedHeapMb = 0
        peakNativeHeapMb = 0
        readings.reset(MemoryStats.EMPTY)
    }

    private fun collectedGc(): GcCounters =
        collected + (collectingSince?.let { readGcCounters() - it } ?: GcCounters.NONE)

    private fun readGcCounters() = GcCounters(
        count = Debug.getRuntimeStat(STAT_GC_COUNT)?.toIntOrNull() ?: 0,
        timeMs = Debug.getRuntimeStat(STAT_GC_TIME)?.toLongOrNull() ?: 0L,
    )

    private data class GcCounters(val count: Int, val timeMs: Long) {

        operator fun plus(other: GcCounters) = GcCounters(count + other.count, timeMs + other.timeMs)

        operator fun minus(other: GcCounters) = GcCounters(count - other.count, timeMs - other.timeMs)

        companion object {
            val NONE = GcCounters(count = 0, timeMs = 0L)
        }
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
        const val STAT_GC_COUNT = "art.gc.gc-count"
        const val STAT_GC_TIME = "art.gc.gc-time"
    }
}
