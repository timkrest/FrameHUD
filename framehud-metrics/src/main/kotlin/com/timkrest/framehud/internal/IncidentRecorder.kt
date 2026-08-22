package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.Incident
import com.timkrest.framehud.IncidentWindow
import com.timkrest.framehud.JankCause
import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats

@WorkerThread
internal class IncidentRecorder(
    private val clock: MetricsClock,
    private val isEmulator: Boolean,
    framesBeforeTrigger: Int,
    private val framesAfterTrigger: Int,
    private val keptIncidents: Int,
) {

    private val frames = FrameLog(framesBeforeTrigger + framesAfterTrigger)

    private val cases = LinkedHashMap<IncidentKey, Case>()

    private var droppedReportsBeforeNextFrame = 0

    private var pending: PendingIncident? = null

    fun addFrame(
        durationsMs: FloatArray,
        overrunMs: Float,
        refreshRateHz: Float,
        frameBudgetMs: Float,
        endNs: Long,
    ) {
        frames.add(
            durationsMs = durationsMs,
            overrunMs = overrunMs,
            refreshRateHz = refreshRateHz,
            frameBudgetMs = frameBudgetMs,
            endNs = endNs,
            droppedReports = droppedReportsBeforeNextFrame,
        )
        droppedReportsBeforeNextFrame = 0
        val pending = pending ?: return
        pending.framesAfterTrigger++
        if (pending.framesAfterTrigger >= framesAfterTrigger) close(pending)
    }

    fun addDroppedReports(count: Int) {
        droppedReportsBeforeNextFrame += count
    }

    fun arm(trigger: FrameHudEvent.IncidentTrigger, readings: IncidentReadings) {
        if (pending != null) return
        pending = PendingIncident(
            trigger = trigger,
            triggeredAtEpochMs = clock.epochMs(),
            armedAtMs = clock.elapsedRealtimeMs(),
            readings = readings,
        )
    }

    fun onTick() {
        val pending = pending ?: return
        if (clock.elapsedRealtimeMs() - pending.armedAtMs >= WAIT_FOR_TRAILING_FRAMES_MS) close(pending)
    }

    fun incidents(): List<Incident> {
        pending?.let(::close)
        return cases.values.map { it.toIncident() }.sortedByDescending { it.worst.stats.lostTimeMs }
    }

    fun endScreen() {
        pending?.let(::close)
        frames.clear()
    }

    fun clear() {
        pending = null
        cases.clear()
        frames.clear()
        droppedReportsBeforeNextFrame = 0
    }

    private fun close(pending: PendingIncident) {
        this.pending = null
        val readings = pending.readings
        val measured = SessionAccumulator(clock, isEmulator)
        measured.addThermalLevel(readings.thermal.level)
        measured.addBattery(readings.battery)
        frames.replayInto(measured)
        record(
            IncidentWindow(
                trigger = pending.trigger,
                triggeredAtEpochMs = pending.triggeredAtEpochMs,
                stats = measured.stats().copy(durationMs = frames.durationMs()),
                frames = frames.history(),
                framesBeforeTrigger = frames.size - pending.framesAfterTrigger,
                memory = readings.memory,
                thermal = readings.thermal,
                process = readings.process,
                counters = readings.counters,
                mainThreadBlock = readings.mainThreadBlock,
            ),
        )
    }

    private fun record(window: IncidentWindow) {
        val key = window.trigger.groupKey()
        val known = cases[key]
        if (known != null) {
            known.add(window)
            return
        }
        if (cases.size >= keptIncidents) {
            val mildest = cases.entries.minByOrNull { it.value.worst.stats.lostTimeMs } ?: return
            if (window.stats.lostTimeMs <= mildest.value.worst.stats.lostTimeMs) return
            cases.remove(mildest.key)
        }
        cases[key] = Case(window)
    }

    private class Case(private var window: IncidentWindow) {

        private val firstAtEpochMs = window.triggeredAtEpochMs
        private var lastAtEpochMs = window.triggeredAtEpochMs
        private var occurrences = 1

        val worst: IncidentWindow get() = window

        fun add(window: IncidentWindow) {
            occurrences++
            lastAtEpochMs = window.triggeredAtEpochMs
            if (window.stats.lostTimeMs > this.window.stats.lostTimeMs) this.window = window
        }

        fun toIncident() = Incident(
            occurrences = occurrences,
            firstAtEpochMs = firstAtEpochMs,
            lastAtEpochMs = lastAtEpochMs,
            worst = window,
        )
    }

    private class PendingIncident(
        val trigger: FrameHudEvent.IncidentTrigger,
        val triggeredAtEpochMs: Long,
        val armedAtMs: Long,
        val readings: IncidentReadings,
    ) {
        var framesAfterTrigger = 0
    }

    private companion object {
        const val WAIT_FOR_TRAILING_FRAMES_MS = 1_000L
    }
}

internal class IncidentReadings(
    val memory: MemoryStats,
    val thermal: ThermalStats,
    val process: ProcessStats,
    val battery: BatterySample,
    val counters: List<CounterReading>,
    val mainThreadBlock: MainThreadBlock,
)

private data class IncidentKey(
    val screen: String?,
    val mark: String?,
    val context: Map<String, String>,
    val cause: JankCause?,
)

private fun FrameHudEvent.IncidentTrigger.groupKey() = IncidentKey(
    screen = screen,
    mark = mark,
    context = context,
    cause = when (this) {
        is FrameHudEvent.FrozenFrames -> null
        is FrameHudEvent.JankBurst -> diagnosis.cause.withoutReadings()
    },
)

private fun JankCause.withoutReadings(): JankCause = when (this) {
    JankCause.None, is JankCause.Thermal -> this
    is JankCause.Gc -> JankCause.Gc(timeShare = 0f)
    is JankCause.VsyncStarvation -> JankCause.VsyncStarvation(ticksPerSecond = 0, refreshRateHz = 0f)
    is JankCause.LateStart -> JankCause.LateStart(delayMs = 0f)
    is JankCause.Stage -> JankCause.Stage(stage = stage, averageMs = 0f)
}
