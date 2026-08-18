package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.WorkerThread
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.ThermalLevel

@WorkerThread
internal class EventDispatcher(private val clock: MetricsClock, private val onSlowListener: (callMs: Float) -> Unit) {

    var isInBurst: Boolean = false
        private set

    private var lastFrozenFrames = 0
    private var lastThermalLevel = ThermalLevel.UNKNOWN

    fun onFirstFrame(
        listeners: List<FrameHudEventListener>,
        timeToDisplayMs: Float,
        screen: String?,
        context: Map<String, String>,
    ) {
        listeners.emit(FrameHudEvent.FirstFrame(timeToDisplayMs = timeToDisplayMs, screen = screen, context = context))
    }

    fun onSample(
        listeners: List<FrameHudEventListener>,
        diagnosis: JankDiagnosis,
        frozenFrames: Int,
        thermalLevel: ThermalLevel,
        screen: String?,
        mark: String?,
        context: Map<String, String>,
    ) {
        val burst = diagnosis.severity != JankSeverity.NONE
        if (burst && !isInBurst) {
            listeners.emit(
                FrameHudEvent.JankBurst(diagnosis = diagnosis, screen = screen, mark = mark, context = context),
            )
        }
        isInBurst = burst

        if (frozenFrames > lastFrozenFrames) {
            listeners.emit(
                FrameHudEvent.FrozenFrames(
                    count = frozenFrames - lastFrozenFrames,
                    screen = screen,
                    mark = mark,
                    context = context,
                ),
            )
        }
        lastFrozenFrames = frozenFrames

        if (thermalLevel != lastThermalLevel) {
            val isFirstReading = lastThermalLevel == ThermalLevel.UNKNOWN
            if (!isFirstReading || thermalLevel.isThrottling) {
                listeners.emit(
                    FrameHudEvent.ThermalChanged(level = thermalLevel, screen = screen, mark = mark, context = context),
                )
            }
            lastThermalLevel = thermalLevel
        }
    }

    fun onScreenEnded(
        listeners: List<FrameHudEventListener>,
        stats: IntervalStats,
        screen: String?,
        context: Map<String, String>,
    ) {
        if (stats.frames > 0) {
            listeners.emit(FrameHudEvent.ScreenEnded(stats = stats, screen = screen, context = context))
        }
    }

    fun onMarkEnded(
        listeners: List<FrameHudEventListener>,
        stats: IntervalStats,
        mark: String,
        screen: String?,
        context: Map<String, String>,
    ) {
        listeners.emit(FrameHudEvent.MarkEnded(stats = stats, mark = mark, screen = screen, context = context))
    }

    fun reset() {
        isInBurst = false
        lastFrozenFrames = 0
        lastThermalLevel = ThermalLevel.UNKNOWN
    }

    private fun List<FrameHudEventListener>.emit(event: FrameHudEvent) {
        forEach { listener ->
            val startNs = clock.nanoTime()
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Listener ${listener.javaClass.name} threw on $event", e)
            }
            val elapsedMs = (clock.nanoTime() - startNs) / NS_PER_MS
            if (elapsedMs > SLOW_LISTENER_THRESHOLD_MS) onSlowListener(elapsedMs)
        }
    }

    private companion object {
        const val SLOW_LISTENER_THRESHOLD_MS = 50f
    }
}
