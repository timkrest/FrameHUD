package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread

@WorkerThread
internal class FlightRecorder(
    private val clock: MetricsClock,
    private val trigger: PerfettoTrigger = SystemPerfettoTrigger,
) {

    private var askedAtMs: Long? = null

    var timesAsked: Int = 0
        private set

    fun retainTrace(name: String) {
        val nowMs = clock.elapsedRealtimeMs()
        val asked = askedAtMs
        if (asked != null && nowMs - asked < ASK_AGAIN_AFTER_MS) return
        askedAtMs = nowMs
        timesAsked++
        trigger.activate(name)
    }

    fun reset() {
        askedAtMs = null
        timesAsked = 0
    }

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
