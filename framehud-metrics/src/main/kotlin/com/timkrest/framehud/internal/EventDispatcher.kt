package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.WorkerThread
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.ThermalLevel

@WorkerThread
internal class EventDispatcher(
    private val clock: MetricsClock,
    private val config: () -> FrameHudConfig,
    private val onSlowListener: (callMs: Float) -> Unit,
) {

    var isInBurst: Boolean = false
        private set

    private var lastFrozenFrames = 0
    private var lastThermalLevel = ThermalLevel.UNKNOWN

    fun onFirstFrame(timeToDisplayMs: Float, screen: String?, context: Map<String, String>) {
        emit(FrameHudEvent.FirstFrame(timeToDisplayMs = timeToDisplayMs, screen = screen, context = context))
    }

    fun onUsableFrame(timeToUsableMs: Float, screen: String?, context: Map<String, String>) {
        emit(FrameHudEvent.UsableFrame(timeToUsableMs = timeToUsableMs, screen = screen, context = context))
    }

    fun onSample(
        diagnosis: JankDiagnosis,
        frozenFrames: Int,
        thermalLevel: ThermalLevel,
        screen: String?,
        mark: String?,
        context: Map<String, String>,
    ): FrameHudEvent.IncidentTrigger? {
        val burst = takeBurst(diagnosis, screen, mark, context)?.also(::emit)
        val frozen = takeFrozenFrames(frozenFrames, screen, mark, context)?.also(::emit)
        takeThermalChange(thermalLevel, screen, mark, context)?.also(::emit)
        return burst ?: frozen
    }

    private fun takeBurst(
        diagnosis: JankDiagnosis,
        screen: String?,
        mark: String?,
        context: Map<String, String>,
    ): FrameHudEvent.JankBurst? {
        val burst = diagnosis.severity != JankSeverity.NONE
        val started = burst && !isInBurst
        isInBurst = burst
        if (!started) return null
        return FrameHudEvent.JankBurst(diagnosis = diagnosis, screen = screen, mark = mark, context = context)
    }

    private fun takeFrozenFrames(
        frozenFrames: Int,
        screen: String?,
        mark: String?,
        context: Map<String, String>,
    ): FrameHudEvent.FrozenFrames? {
        val added = frozenFrames - lastFrozenFrames
        lastFrozenFrames = frozenFrames
        if (added <= 0) return null
        return FrameHudEvent.FrozenFrames(count = added, screen = screen, mark = mark, context = context)
    }

    private fun takeThermalChange(
        thermalLevel: ThermalLevel,
        screen: String?,
        mark: String?,
        context: Map<String, String>,
    ): FrameHudEvent.ThermalChanged? {
        if (thermalLevel == lastThermalLevel) return null
        val isFirstReading = lastThermalLevel == ThermalLevel.UNKNOWN
        lastThermalLevel = thermalLevel
        if (isFirstReading && !thermalLevel.isThrottling) return null
        return FrameHudEvent.ThermalChanged(level = thermalLevel, screen = screen, mark = mark, context = context)
    }

    fun onScreenEnded(
        listenersWhenItEnded: List<FrameHudEventListener>,
        stats: IntervalStats,
        screen: String?,
        context: Map<String, String>,
    ) {
        if (stats.frames > 0) {
            listenersWhenItEnded.emit(
                FrameHudEvent.ScreenEnded(stats = stats, screen = screen, context = context),
            )
        }
    }

    fun onMarkEnded(
        listenersWhenItEnded: List<FrameHudEventListener>,
        stats: IntervalStats,
        mark: String,
        screen: String?,
        context: Map<String, String>,
    ) {
        listenersWhenItEnded.emit(
            FrameHudEvent.MarkEnded(stats = stats, mark = mark, screen = screen, context = context),
        )
    }

    fun onInternalFailure(
        what: String,
        error: Throwable,
        screen: String?,
        mark: String?,
        context: Map<String, String>,
    ) {
        emit(
            FrameHudEvent.InternalFailure(
                what = what,
                error = error,
                screen = screen,
                mark = mark,
                context = context,
            ),
        )
    }

    fun reset() {
        isInBurst = false
        lastFrozenFrames = 0
        lastThermalLevel = ThermalLevel.UNKNOWN
    }

    private fun emit(event: FrameHudEvent) {
        config().eventListeners.emit(event)
    }

    private fun List<FrameHudEventListener>.emit(event: FrameHudEvent) {
        forEach { listener ->
            val startNs = clock.nanoTime()
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                warnAbout(listener, event, e)
            } catch (e: LinkageError) {
                warnAbout(listener, event, e)
            }
            val elapsedMs = (clock.nanoTime() - startNs) / NS_PER_MS
            if (elapsedMs > SLOW_LISTENER_THRESHOLD_MS) onSlowListener(elapsedMs)
        }
    }

    private fun warnAbout(listener: FrameHudEventListener, event: FrameHudEvent, thrown: Throwable) {
        Log.w(LOG_TAG, "Listener ${listener.javaClass.name} threw on $event", thrown)
    }

    private companion object {
        const val SLOW_LISTENER_THRESHOLD_MS = 50f
    }
}
