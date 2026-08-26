package com.timkrest.framehud.internal

internal class TestMetricsClock : MetricsClock {

    var elapsedMs: Long = 0L
    var uptime: Long = 0L
    var nanos: Long = 0L
    var epoch: Long = 0L

    override fun elapsedRealtimeMs(): Long = elapsedMs

    override fun uptimeMs(): Long = uptime

    override fun nanoTime(): Long = nanos

    override fun epochMs(): Long = epoch
}
