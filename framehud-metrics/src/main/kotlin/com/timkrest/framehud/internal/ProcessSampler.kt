package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.ProcessStats

@WorkerThread
internal class ProcessSampler(private val clock: MetricsClock, private val probe: ProcessProbe) {

    private var previous: CpuTime? = null

    private var peaks = Peaks()

    fun sample(): ProcessStats {
        val atMs = clock.elapsedRealtimeMs()
        val cpuTimeMs = probe.cpuTimeMs()
        val cpuPercent = cpuPercentSince(cpuTimeMs, atMs)
        if (cpuTimeMs != null) previous = CpuTime(cpuTimeMs = cpuTimeMs, atMs = atMs)

        val pssMb = probe.pssMb()
        val threads = probe.threads()
        val openFiles = probe.openFiles()
        peaks.cpuPercent = higher(peaks.cpuPercent, cpuPercent)
        peaks.pssMb = higher(peaks.pssMb, pssMb)
        peaks.threads = higher(peaks.threads, threads)
        peaks.openFiles = higher(peaks.openFiles, openFiles)

        return ProcessStats(
            cpuPercent = cpuPercent,
            peakCpuPercent = peaks.cpuPercent,
            pssMb = pssMb,
            peakPssMb = peaks.pssMb,
            threads = threads,
            peakThreads = peaks.threads,
            openFiles = openFiles,
            peakOpenFiles = peaks.openFiles,
        )
    }

    fun reset() {
        previous = null
        peaks = Peaks()
    }

    private fun cpuPercentSince(cpuTimeMs: Long?, atMs: Long): Float? {
        val previous = previous ?: return null
        if (cpuTimeMs == null) return null
        val elapsedMs = atMs - previous.atMs
        if (elapsedMs <= 0L) return null
        return (cpuTimeMs - previous.cpuTimeMs) * PERCENT / elapsedMs
    }

    private class CpuTime(val cpuTimeMs: Long, val atMs: Long)

    private class Peaks {
        var cpuPercent: Float? = null
        var pssMb: Int? = null
        var threads: Int? = null
        var openFiles: Int? = null
    }
}

private fun <T : Comparable<T>> higher(peak: T?, value: T?): T? = when {
    value == null -> peak
    peak == null -> value
    else -> maxOf(peak, value)
}
