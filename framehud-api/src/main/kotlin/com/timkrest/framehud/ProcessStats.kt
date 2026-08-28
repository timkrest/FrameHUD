package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Peaks run from the last reset. A null figure is one this run has no reading for. */
@Immutable
@ConsistentCopyVisibility
public data class ProcessStats private constructor(
    /** Percent of one core, so eight busy cores read 800. Null until a second sample. */
    val cpuPercent: Float?,
    val peakCpuPercent: Float?,
    val pssMb: Int?,
    val peakPssMb: Int?,
    val threads: Int?,
    val peakThreads: Int?,
    /** Open file descriptors, sockets and pipes included. */
    val openFiles: Int?,
    val peakOpenFiles: Int?,
) {
    public companion object {
        public val EMPTY: ProcessStats = of()

        @InternalFrameHudApi
        public fun of(
            cpuPercent: Float? = null,
            peakCpuPercent: Float? = null,
            pssMb: Int? = null,
            peakPssMb: Int? = null,
            threads: Int? = null,
            peakThreads: Int? = null,
            openFiles: Int? = null,
            peakOpenFiles: Int? = null,
        ): ProcessStats = ProcessStats(
            cpuPercent = cpuPercent,
            peakCpuPercent = peakCpuPercent,
            pssMb = pssMb,
            peakPssMb = peakPssMb,
            threads = threads,
            peakThreads = peakThreads,
            openFiles = openFiles,
            peakOpenFiles = peakOpenFiles,
        )
    }
}
