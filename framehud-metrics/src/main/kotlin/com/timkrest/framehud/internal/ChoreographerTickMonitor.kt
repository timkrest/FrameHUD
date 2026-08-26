package com.timkrest.framehud.internal

import android.view.Choreographer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

internal class ChoreographerTickMonitor(private val clock: MetricsClock) {

    private val readings = FreezableReading(0)

    @get:AnyThread
    val ticksPerSecond: StateFlow<Int> = readings.published

    val liveTicksPerSecond: Int get() = readings.live

    @Volatile
    @get:AnyThread
    var lastTickUptimeMs: Long = 0L
        private set

    private var activeCallback: Choreographer.FrameCallback? = null
    private var windowStartNs: Long? = null
    private var tickCount = 0

    @MainThread
    fun start() {
        if (activeCallback != null) return
        lastTickUptimeMs = clock.uptimeMs()
        windowStartNs = null
        tickCount = 0
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (activeCallback !== this) return
                lastTickUptimeMs = clock.uptimeMs()
                onTick(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        activeCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    @MainThread
    fun stop() {
        activeCallback?.let(Choreographer.getInstance()::removeFrameCallback)
        activeCallback = null
        readings.reset(0)
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    private fun onTick(frameTimeNanos: Long) {
        val startNs = windowStartNs
        if (startNs == null) {
            windowStartNs = frameTimeNanos
            return
        }
        tickCount++
        val elapsedNs = frameTimeNanos - startNs
        if (elapsedNs >= NS_PER_SECOND) {
            readings.update(((tickCount * NS_PER_SECOND).toFloat() / elapsedNs).roundToInt())
            tickCount = 0
            windowStartNs = frameTimeNanos
        }
    }
}
