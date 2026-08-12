package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.WorkerThread
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PerformanceMetrics
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats

@WorkerThread
internal class EventDispatcher {

    private var wasInBurst = false
    private var lastFrozenFrames = 0
    private var lastThermalLevel = ThermalLevel.UNKNOWN

    fun onFirstFrame(listeners: List<FrameHudEventListener>, timeToDisplayMs: Float, screen: String?) {
        listeners.emit(FrameHudEvent.FirstFrame(timeToDisplayMs = timeToDisplayMs, screen = screen))
    }

    fun onSample(
        listeners: List<FrameHudEventListener>,
        metrics: PerformanceMetrics,
        memory: MemoryStats,
        thermal: ThermalStats,
        choreographerTicksPerSecond: Int,
        screen: String?,
        mark: String?,
    ) {
        val diagnosis = JankDiagnosis.of(
            metrics = metrics,
            memory = memory,
            thermal = thermal,
            choreographerTicksPerSecond = choreographerTicksPerSecond,
        )
        val isInBurst = diagnosis.severity != JankSeverity.NONE
        if (isInBurst && !wasInBurst) {
            listeners.emit(FrameHudEvent.JankBurst(diagnosis = diagnosis, screen = screen, mark = mark))
        }
        wasInBurst = isInBurst

        val frozenFrames = metrics.session.frozenFrames
        if (frozenFrames > lastFrozenFrames) {
            listeners.emit(
                FrameHudEvent.FrozenFrames(count = frozenFrames - lastFrozenFrames, screen = screen, mark = mark),
            )
        }
        lastFrozenFrames = frozenFrames

        if (thermal.level != lastThermalLevel) {
            val isFirstReading = lastThermalLevel == ThermalLevel.UNKNOWN
            if (!isFirstReading || thermal.level.isThrottling) {
                listeners.emit(FrameHudEvent.ThermalChanged(level = thermal.level, screen = screen, mark = mark))
            }
            lastThermalLevel = thermal.level
        }
    }

    fun onScreenEnded(listeners: List<FrameHudEventListener>, stats: SessionStats, screen: String?) {
        if (stats.frames > 0) listeners.emit(FrameHudEvent.ScreenEnded(stats = stats, screen = screen))
    }

    fun onMarkEnded(
        listeners: List<FrameHudEventListener>,
        stats: SessionStats,
        mark: String,
        screen: String?,
    ) {
        listeners.emit(FrameHudEvent.MarkEnded(stats = stats, mark = mark, screen = screen))
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
