package com.timkrest.framehud.internal

import android.os.SystemClock

/** The clocks the metrics thread reads, behind an interface so the aggregation can be tested. */
internal interface MetricsClock {

    /** Milliseconds since boot: throttling, and how long a session has been collecting. */
    fun elapsedRealtimeMs(): Long

    /** The clock `FrameMetrics` timestamps come from, used to age frames out of the FPS window. */
    fun nanoTime(): Long
}

internal object SystemMetricsClock : MetricsClock {

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun nanoTime(): Long = System.nanoTime()
}
