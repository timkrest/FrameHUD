package io.github.timkrest.framehud.internal

import android.util.Log
import io.github.timkrest.framehud.FrameHudEvent
import io.github.timkrest.framehud.FrameHudEventListener
import io.github.timkrest.framehud.JankDiagnosis
import io.github.timkrest.framehud.JankSeverity
import io.github.timkrest.framehud.MemoryStats
import io.github.timkrest.framehud.PerformanceMetrics
import io.github.timkrest.framehud.SessionStats
import io.github.timkrest.framehud.ThermalLevel
import io.github.timkrest.framehud.ThermalStats

/**
 * One event per jank burst, not per frame. Confined to the metrics thread.
 *
 * State advances even without listeners, so attaching one later does not replay history as a spike.
 */
internal class EventDispatcher {

    private var wasInBurst = false
    private var lastFrozenFrames = 0
    private var lastThermalLevel = ThermalLevel.UNKNOWN

    fun onSample(
        listeners: List<FrameHudEventListener>,
        metrics: PerformanceMetrics,
        memory: MemoryStats,
        thermal: ThermalStats,
        vsyncRate: Int,
        screen: String?,
    ) {
        val diagnosis = JankDiagnosis.of(metrics = metrics, memory = memory, thermal = thermal, vsyncRate = vsyncRate)
        val isInBurst = diagnosis.severity != JankSeverity.NONE
        if (isInBurst && !wasInBurst) {
            listeners.emit(FrameHudEvent.JankBurst(diagnosis = diagnosis, screen = screen))
        }
        wasInBurst = isInBurst

        val frozenFrames = metrics.session.frozenFrames
        if (frozenFrames > lastFrozenFrames) {
            listeners.emit(FrameHudEvent.FrozenFrames(count = frozenFrames - lastFrozenFrames, screen = screen))
        }
        lastFrozenFrames = frozenFrames

        if (thermal.level != lastThermalLevel) {
            val isFirstReading = lastThermalLevel == ThermalLevel.UNKNOWN
            if (!isFirstReading || thermal.level.isThrottling) {
                listeners.emit(FrameHudEvent.ThermalChanged(level = thermal.level, screen = screen))
            }
            lastThermalLevel = thermal.level
        }
    }

    fun onScreenEnded(listeners: List<FrameHudEventListener>, stats: SessionStats, screen: String?) {
        if (stats.frames > 0) listeners.emit(FrameHudEvent.ScreenEnded(stats = stats, screen = screen))
    }

    fun reset() {
        wasInBurst = false
        lastFrozenFrames = 0
        lastThermalLevel = ThermalLevel.UNKNOWN
    }

    private fun List<FrameHudEventListener>.emit(event: FrameHudEvent) {
        forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Listener ${listener.javaClass.name} threw on $event", e)
            }
        }
    }
}
