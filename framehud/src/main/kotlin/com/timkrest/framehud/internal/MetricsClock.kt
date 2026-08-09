package com.timkrest.framehud.internal

import android.os.SystemClock

internal interface MetricsClock {

    fun elapsedRealtimeMs(): Long

    fun nanoTime(): Long
}

internal object SystemMetricsClock : MetricsClock {

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun nanoTime(): Long = System.nanoTime()
}
