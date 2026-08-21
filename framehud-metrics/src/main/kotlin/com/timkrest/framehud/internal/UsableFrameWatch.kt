package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import java.util.concurrent.atomic.AtomicReference

internal class UsableFrameWatch(private val clock: MetricsClock) {

    private sealed class State(val window: Any)

    private class Waiting(window: Any, val start: ScreenStart) : State(window)

    private class Armed(window: Any, val start: ScreenStart, val reportedAtNs: Long) : State(window)

    private class Measured(window: Any) : State(window)

    private val state = AtomicReference<State?>(null)

    @AnyThread
    fun expectScreen(window: Any, start: ScreenStart) {
        state.set(Waiting(window, start))
    }

    @AnyThread
    fun restartScreen() {
        update { current -> Waiting(current.window, ScreenStart(clock.nanoTime())) }
    }

    @AnyThread
    fun forgetScreen() {
        state.set(null)
    }

    @AnyThread
    fun reportUsable(start: ScreenStart? = null) {
        update { current ->
            if (current !is Waiting) return
            if (start != null && current.start !== start) return
            Armed(current.window, current.start, reportedAtNs = clock.nanoTime())
        }
    }

    @AnyThread
    fun onFrame(window: Any, frameEndNs: Long): Float? {
        val current = state.get()
        if (current !is Armed || current.window !== window) return null
        val displayedBeforeTheReport = frameEndNs < current.reportedAtNs
        if (displayedBeforeTheReport) return null
        if (!state.compareAndSet(current, Measured(current.window))) return null
        return current.start.elapsedMs(frameEndNs)
    }

    private inline fun update(next: (State) -> State) {
        while (true) {
            val current = state.get() ?: return
            if (state.compareAndSet(current, next(current))) return
        }
    }
}
