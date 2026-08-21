package com.timkrest.framehud.sample

import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FrameHudEventListener
import com.timkrest.framehud.IntervalId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SampleFrameHud : FrameHudEventListener {

    const val SCROLL_MARK: String = "scroll"

    const val PERFETTO_TRIGGER: String = "framehud_incident"

    private val strictBudgetsMs = mapOf(
        IntervalId.Session to SESSION_BUDGET_MS,
        IntervalId.Mark(SCROLL_MARK) to SCROLL_BUDGET_MS,
    )

    val strictBudgetsLabel: String = strictBudgetsMs.entries.joinToString(separator = " and ") { (interval, budgetMs) ->
        "${interval.label} $budgetMs ms"
    }

    private val _lastEvent = MutableStateFlow<FrameHudEvent?>(null)
    val lastEvent: StateFlow<FrameHudEvent?> = _lastEvent.asStateFlow()

    private val _strictBudgets = MutableStateFlow(false)
    val strictBudgets: StateFlow<Boolean> = _strictBudgets.asStateFlow()

    private val _flightRecorder = MutableStateFlow(false)
    val flightRecorder: StateFlow<Boolean> = _flightRecorder.asStateFlow()

    override fun onEvent(event: FrameHudEvent) {
        _lastEvent.value = event
    }

    fun listen() {
        val listeners = FrameHud.config.eventListeners
        if (this in listeners) return
        FrameHud.config = FrameHud.config.copy(eventListeners = listeners + this)
    }

    fun setStrictBudgets(strict: Boolean) {
        FrameHud.config = FrameHud.config.copy(frameBudgetsMs = if (strict) strictBudgetsMs else emptyMap())
        _strictBudgets.value = FrameHud.config.frameBudgetsMs.isNotEmpty()
    }

    fun setFlightRecorder(recording: Boolean) {
        FrameHud.config = FrameHud.config.copy(perfettoTrigger = if (recording) PERFETTO_TRIGGER else null)
        _flightRecorder.value = FrameHud.config.perfettoTrigger != null
    }
}

private const val SESSION_BUDGET_MS = 16
private const val SCROLL_BUDGET_MS = 8
