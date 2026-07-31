package io.github.timkrest.framehud.sample

import io.github.timkrest.framehud.FrameHud
import io.github.timkrest.framehud.FrameHudEvent
import io.github.timkrest.framehud.FrameHudEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the latest event so the screen can show what a listener receives. Called off the main thread. */
object SampleEvents : FrameHudEventListener {

    private val _last = MutableStateFlow<FrameHudEvent?>(null)
    val last: StateFlow<FrameHudEvent?> = _last.asStateFlow()

    override fun onEvent(event: FrameHudEvent) {
        _last.value = event
    }

    fun register() {
        val listeners = FrameHud.config.eventListeners
        if (this in listeners) return
        FrameHud.config = FrameHud.config.copy(eventListeners = listeners + this)
    }
}
