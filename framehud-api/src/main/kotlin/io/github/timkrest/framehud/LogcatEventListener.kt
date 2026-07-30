package io.github.timkrest.framehud

import android.util.Log

/** Default listener: writes every event to logcat under [TAG]. */
public object LogcatEventListener : FrameHudEventListener {

    /** The tag every FrameHud log line carries, panel internals included: `adb logcat -s FrameHud`. */
    public const val TAG: String = "FrameHud"

    override fun onEvent(event: FrameHudEvent) {
        if (event.isSevere()) Log.w(TAG, event.summary) else Log.i(TAG, event.summary)
    }

    private fun FrameHudEvent.isSevere(): Boolean = when (this) {
        is FrameHudEvent.JankBurst -> diagnosis.severity == JankSeverity.SEVERE
        is FrameHudEvent.FrozenFrames -> true
        is FrameHudEvent.ThermalChanged -> level.isThrottling
        is FrameHudEvent.ScreenEnded -> false
    }
}
