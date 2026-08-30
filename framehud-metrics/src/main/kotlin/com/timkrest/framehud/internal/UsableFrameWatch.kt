package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import java.util.concurrent.atomic.AtomicReference

internal class UsableFrameWatch(private val clock: MetricsClock) {

    data class UsableFrame(val timeToUsableMs: Float, val screen: String?)

    private sealed class State(val window: Any)

    private class Waiting(window: Any, val screen: String?, val start: ScreenStart) : State(window)

    private class Armed(window: Any, val screen: String?, val start: ScreenStart, val reportedAtNs: Long) :
        State(window)

    private class Measured(window: Any) : State(window)

    private val state = AtomicReference<State?>(null)

    @AnyThread
    fun expectScreen(window: Any, screen: String?, start: ScreenStart) {
        state.set(Waiting(window, screen, start))
    }

    @AnyThread
    fun restartScreen(screen: String?) {
        update { current -> Waiting(current.window, screen, ScreenStart(clock.nanoTime())) }
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
            Armed(current.window, current.screen, current.start, reportedAtNs = clock.nanoTime())
        }
    }

    @AnyThread
    fun onFrame(window: Any, frameEndNs: Long): UsableFrame? {
        val current = state.get()
        if (current !is Armed || current.window !== window) return null
        val displayedBeforeTheReport = frameEndNs < current.reportedAtNs
        if (displayedBeforeTheReport) return null
        if (!state.compareAndSet(current, Measured(current.window))) return null
        val timeToUsableMs = current.start.elapsedMs(frameEndNs) ?: return null
        return UsableFrame(timeToUsableMs = timeToUsableMs, screen = current.screen)
    }

    private inline fun update(next: (State) -> State) {
        while (true) {
            val current = state.get() ?: return
            if (state.compareAndSet(current, next(current))) return
        }
    }
}
