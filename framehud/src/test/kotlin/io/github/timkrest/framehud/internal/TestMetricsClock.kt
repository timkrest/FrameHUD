package io.github.timkrest.framehud.internal

internal class TestMetricsClock : MetricsClock {

    var elapsedMs: Long = 0L
    var nanos: Long = 0L

    override fun elapsedRealtimeMs(): Long = elapsedMs

    override fun nanoTime(): Long = nanos
}
