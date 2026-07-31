package com.timkrest.framehud.internal

import android.util.Log
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats

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
