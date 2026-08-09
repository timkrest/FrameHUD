package com.timkrest.framehud.internal

import android.view.Choreographer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
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
internal class ChoreographerTickMonitor {

    private val _ticksPerSecond = MutableStateFlow(0)
    val ticksPerSecond: StateFlow<Int> = _ticksPerSecond.asStateFlow()

    private var activeCallback: Choreographer.FrameCallback? = null
    private var windowStartNs: Long? = null
    private var tickCount = 0

    @Volatile
    private var isFrozen = false

    @MainThread
    fun start() {
        if (activeCallback != null) return
        windowStartNs = null
        tickCount = 0
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (activeCallback !== this) return
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
        _ticksPerSecond.value = 0
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
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
            if (!isFrozen) {
                _ticksPerSecond.value = ((tickCount * NS_PER_SECOND).toFloat() / elapsedNs).roundToInt()
            }
            tickCount = 0
            windowStartNs = frameTimeNanos
        }
    }
}
