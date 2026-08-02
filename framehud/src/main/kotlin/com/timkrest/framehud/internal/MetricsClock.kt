package com.timkrest.framehud.internal

import android.os.SystemClock

/** The clocks the metrics thread reads, behind an interface so the aggregation can be tested. */
internal interface MetricsClock {

    fun elapsedRealtimeMs(): Long

    fun nanoTime(): Long
}

internal object SystemMetricsClock : MetricsClock {

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun nanoTime(): Long = System.nanoTime()
}
