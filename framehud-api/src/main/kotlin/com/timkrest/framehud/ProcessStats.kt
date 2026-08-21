package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** Peaks run from the last reset. A null figure is one this device would not report. */
@Immutable
public data class ProcessStats(
    /** Percent of one core, so eight busy cores read 800. Null until a second sample. */
    val cpuPercent: Float? = null,
    val peakCpuPercent: Float? = null,
    val pssMb: Int? = null,
    val peakPssMb: Int? = null,
    val threads: Int? = null,
    val peakThreads: Int? = null,
    /** Open file descriptors, sockets and pipes included. */
    val openFiles: Int? = null,
    val peakOpenFiles: Int? = null,
) {
    public companion object {
        public val EMPTY: ProcessStats = ProcessStats()
    }
}
