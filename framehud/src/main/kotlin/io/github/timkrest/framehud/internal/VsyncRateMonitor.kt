package io.github.timkrest.framehud.internal

import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

internal class VsyncRateMonitor {

    private val _ratePerSecond = MutableStateFlow(0)
    val ratePerSecond: StateFlow<Int> = _ratePerSecond.asStateFlow()

    private var activeCallback: Choreographer.FrameCallback? = null
    private var windowStartNs = 0L
    private var tickCount = 0

    @Volatile
    private var isFrozen = false

    fun start() {
        if (activeCallback != null) return
        windowStartNs = 0L
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
        if (windowStartNs == 0L) {
            windowStartNs = frameTimeNanos
            return
        }
        tickCount++
        val elapsedNs = frameTimeNanos - windowStartNs
        if (elapsedNs >= WINDOW_NS) {
            if (!isFrozen) _ratePerSecond.value = (tickCount * NS_PER_SECOND / elapsedNs).roundToInt()
            tickCount = 0
            windowStartNs = frameTimeNanos
        }
    }

    companion object {
        private const val WINDOW_NS = 1_000_000_000L
        private const val NS_PER_SECOND = 1_000_000_000f
    }
}
