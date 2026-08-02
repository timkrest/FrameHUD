package com.timkrest.framehud.internal

import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Counts the Choreographer ticks the main thread serves each second — the reading that tells a
 * blocked main thread apart from a screen with nothing to draw, since neither produces frames.
 *
 * It costs a frame callback posted for as long as a window is bound, which wakes the main thread
 * every vsync even on a still screen. Nothing cheaper reports the same thing, so it is kept to the
 * shortest span that answers the question: [start] and [stop] follow the bound window, not the
 * process.
 */
internal class VsyncRateMonitor {

    private val _ratePerSecond = MutableStateFlow(0)
    val ratePerSecond: StateFlow<Int> = _ratePerSecond.asStateFlow()

    private var activeCallback: Choreographer.FrameCallback? = null
    private var windowStartNs: Long? = null
    private var tickCount = 0

    @Volatile
    private var isFrozen = false

    fun start() {
        if (activeCallback != null) return
        windowStartNs = null
        tickCount = 0
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (activeCallback !== this) return
                onVsync(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        activeCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        activeCallback?.let(Choreographer.getInstance()::removeFrameCallback)
        activeCallback = null
        _ratePerSecond.value = 0
    }

    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
    }

    private fun onVsync(frameTimeNanos: Long) {
        val startNs = windowStartNs
        if (startNs == null) {
            windowStartNs = frameTimeNanos
            return
        }
        tickCount++
        val elapsedNs = frameTimeNanos - startNs
        if (elapsedNs >= NS_PER_SECOND) {
            if (!isFrozen) _ratePerSecond.value = ((tickCount * NS_PER_SECOND).toFloat() / elapsedNs).roundToInt()
            tickCount = 0
            windowStartNs = frameTimeNanos
        }
    }
}
