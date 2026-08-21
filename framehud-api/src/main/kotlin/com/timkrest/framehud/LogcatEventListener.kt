package com.timkrest.framehud

import android.util.Log

public object LogcatEventListener : FrameHudEventListener {

    public const val TAG: String = "FrameHud"

    override fun onEvent(event: FrameHudEvent) {
        if (event.isSevere()) Log.w(TAG, event.summary) else Log.i(TAG, event.summary)
    }

    private fun FrameHudEvent.isSevere(): Boolean = when (this) {
        is FrameHudEvent.FirstFrame -> false
        is FrameHudEvent.UsableFrame -> false
        is FrameHudEvent.JankBurst -> diagnosis.severity == JankSeverity.SEVERE
        is FrameHudEvent.FrozenFrames -> true
        is FrameHudEvent.ThermalChanged -> level.isThrottling
        is FrameHudEvent.ScreenEnded -> false
        is FrameHudEvent.MarkEnded -> false
    }
}
