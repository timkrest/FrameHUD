package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread

@WorkerThread
internal class FlightRecorder(
    private val clock: MetricsClock,
    private val trigger: PerfettoTrigger = SystemPerfettoTrigger,
) {

    private val timesAsked = mutableMapOf<String, Int>()

    private var lastAsk: Ask? = null

    fun retainTrace(name: String) {
        val nowMs = clock.elapsedRealtimeMs()
        val standing = lastAsk?.takeIf { it.name == name }
        if (standing != null && nowMs - standing.atMs < ASK_AGAIN_AFTER_MS) return
        lastAsk = Ask(name = name, atMs = nowMs)
        timesAsked[name] = (timesAsked[name] ?: 0) + 1
        trigger.activate(name)
    }

    fun recordingFor(configured: String?): FlightRecording? {
        val name = lastAsk?.name ?: configured ?: return null
        return FlightRecording(trigger = name, timesAsked = timesAsked[name] ?: 0)
    }

    fun reset() {
        lastAsk = null
        timesAsked.clear()
    }

    private class Ask(val name: String, val atMs: Long)

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
