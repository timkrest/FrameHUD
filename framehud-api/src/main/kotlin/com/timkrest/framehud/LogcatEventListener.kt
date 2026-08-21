package com.timkrest.framehud

import android.util.Log

public object LogcatEventListener : FrameHudEventListener {

    public const val TAG: String = "FrameHud"

    override fun onEvent(event: FrameHudEvent) {
        when (event.logAs()) {
            LogAs.WARNING -> Log.w(TAG, event.summary)
            LogAs.INFO -> Log.i(TAG, event.summary)
            LogAs.NOTHING_IT_REACHED_LOGCAT_WITH_ITS_STACK -> Unit
        }
    }

    private enum class LogAs { WARNING, INFO, NOTHING_IT_REACHED_LOGCAT_WITH_ITS_STACK }

    private fun FrameHudEvent.logAs(): LogAs = when (this) {
        is FrameHudEvent.FirstFrame -> LogAs.INFO
        is FrameHudEvent.UsableFrame -> LogAs.INFO
        is FrameHudEvent.JankBurst -> if (diagnosis.severity == JankSeverity.SEVERE) LogAs.WARNING else LogAs.INFO
        is FrameHudEvent.FrozenFrames -> LogAs.WARNING
        is FrameHudEvent.ThermalChanged -> if (level.isThrottling) LogAs.WARNING else LogAs.INFO
        is FrameHudEvent.ScreenEnded -> LogAs.INFO
        is FrameHudEvent.MarkEnded -> LogAs.INFO
        is FrameHudEvent.InternalFailure -> LogAs.NOTHING_IT_REACHED_LOGCAT_WITH_ITS_STACK
    }
}
