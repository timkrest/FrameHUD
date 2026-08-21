package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FramePhase

@WorkerThread
internal class FrameLog(private val capacity: Int) {

    private val phaseCount = FramePhase.entries.size

    private val phaseDurationsMs = FloatArray(capacity * phaseCount)
    private val overrunsMs = FloatArray(capacity)
    private val refreshRatesHz = FloatArray(capacity)
    private val frameBudgetsMs = FloatArray(capacity)
    private val endsNs = LongArray(capacity)
    private val droppedReportsBefore = IntArray(capacity)

    private val replayed = FloatArray(phaseCount)

    private var writePos = 0

    var size = 0
        private set

    fun add(
        durationsMs: FloatArray,
        overrunMs: Float,
        refreshRateHz: Float,
        frameBudgetMs: Float,
        endNs: Long,
        droppedReports: Int,
    ) {
        durationsMs.copyInto(phaseDurationsMs, destinationOffset = writePos * phaseCount)
        overrunsMs[writePos] = overrunMs
        refreshRatesHz[writePos] = refreshRateHz
        frameBudgetsMs[writePos] = frameBudgetMs
        endsNs[writePos] = endNs
        droppedReportsBefore[writePos] = droppedReports
        writePos = (writePos + 1) % capacity
        if (size < capacity) size++
    }

    fun replayInto(accumulator: SessionAccumulator) {
        for (index in 0 until size) {
            val slot = slotOf(index)
            phaseDurationsMs.copyInto(replayed, 0, slot * phaseCount, (slot + 1) * phaseCount)
            accumulator.addDroppedReports(droppedReportsBefore[slot])
            accumulator.addFrame(
                durationsMs = replayed,
                overrunMs = overrunsMs[slot],
                refreshRateHz = refreshRatesHz[slot],
                frameBudgetMs = frameBudgetsMs[slot],
            )
        }
    }

    fun history(): FrameHistory {
        val totalsMs = FloatArray(size)
        val deadlinesMs = FloatArray(size)
        for (index in 0 until size) {
            val slot = slotOf(index)
            val totalMs = phaseDurationsMs[slot * phaseCount + FramePhase.TOTAL.ordinal]
            totalsMs[index] = totalMs
            deadlinesMs[index] = totalMs - overrunsMs[slot]
        }
        return FrameHistory.of(totalsMs = totalsMs, deadlinesMs = deadlinesMs)
    }

    fun durationMs(): Long =
        if (size < 2) 0L else (endsNs[slotOf(size - 1)] - endsNs[slotOf(0)]) / NS_PER_MS_LONG

    fun clear() {
        writePos = 0
        size = 0
    }

    private fun slotOf(index: Int): Int = (writePos - size + index + capacity) % capacity
}
