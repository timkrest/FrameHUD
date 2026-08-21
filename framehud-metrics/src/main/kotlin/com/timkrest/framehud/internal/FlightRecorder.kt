package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread

@WorkerThread
internal class FlightRecorder(
    private val clock: MetricsClock,
    private val trigger: PerfettoTrigger = SystemPerfettoTrigger,
) {

    private var asked: Asked? = null

    fun retainTrace(name: String) {
        val nowMs = clock.elapsedRealtimeMs()
        val standing = asked?.takeIf { it.name == name }
        if (standing != null && nowMs - standing.atMs < ASK_AGAIN_AFTER_MS) return
        asked = Asked(name = name, atMs = nowMs, times = (standing?.times ?: 0) + 1)
        trigger.activate(name)
    }

    fun recordingFor(configured: String?): FlightRecording? {
        asked?.let { return FlightRecording(trigger = it.name, timesAsked = it.times) }
        return configured?.let { FlightRecording(trigger = it, timesAsked = 0) }
    }

    fun reset() {
        asked = null
    }

    private class Asked(val name: String, val atMs: Long, val times: Int)

    private companion object {
        const val ASK_AGAIN_AFTER_MS = 5_000L
    }
}

internal class FlightRecording(val trigger: String, val timesAsked: Int)

internal fun interface PerfettoTrigger {
    fun activate(name: String)
}

internal object SystemPerfettoTrigger : PerfettoTrigger {

    override fun activate(name: String) {
        Thread({ askOnThisThread(name) }, TRIGGER_THREAD_NAME).start()
    }

    fun askOnThisThread(name: String): Boolean = guarded("asking Perfetto to retain the trace") {
        val process = ProcessBuilder(TRIGGER_BINARY, name)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { it.readBytes() }
        process.waitFor()
    }

    private const val TRIGGER_BINARY = "/system/bin/trigger_perfetto"
    private const val TRIGGER_THREAD_NAME = "framehud-flight"
}
