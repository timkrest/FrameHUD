package com.timkrest.framehud.internal

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.tracing.Trace
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import kotlin.math.roundToInt

/** Emits trace markers for Perfetto to record; FrameHUD never reads them back. */
internal class FrameHudTracer {

    private var openScreen: String? = null
    private var openMark: String? = null
    private var inJankBurst = false

    @MainThread
    fun screenChanged(label: String?) {
        if (openScreen == label) return
        openScreen?.let { Trace.endAsyncSection(screenSection(it), COOKIE) }
        openScreen = label
        label?.let { Trace.beginAsyncSection(screenSection(it), COOKIE) }
    }

    @MainThread
    fun markChanged(name: String?) {
        if (openMark == name) return
        openMark?.let { Trace.endAsyncSection(markSection(it), COOKIE) }
        openMark = name
        name?.let { Trace.beginAsyncSection(markSection(it), COOKIE) }
    }

    @WorkerThread
    fun jankBurstChanged(active: Boolean) {
        if (inJankBurst == active) return
        inJankBurst = active
        if (active) {
            Trace.beginAsyncSection(JANK_BURST_SECTION, COOKIE)
        } else {
            Trace.endAsyncSection(JANK_BURST_SECTION, COOKIE)
        }
    }

    @WorkerThread
    fun publishCounters(metrics: PerformanceMetrics, memory: MemoryStats) {
        if (!Trace.isEnabled()) return
        Trace.setCounter("framehud.jank_percent", metrics.window.jankPercent.roundToInt())
        Trace.setCounter("framehud.p95_ms", metrics.window.p95FrameMs.roundToInt())
        Trace.setCounter("framehud.frozen_frames", metrics.session.frozenFrames)
        Trace.setCounter("framehud.used_heap_mb", memory.usedHeapMb)
    }

    private fun screenSection(label: String): String = "framehud:screen:$label"

    private fun markSection(name: String): String = "framehud:mark:$name"

    private companion object {
        const val JANK_BURST_SECTION = "framehud:jank_burst"
        const val COOKIE = 0
    }
}
