package com.timkrest.framehud

import java.util.Locale

/** Something worth reporting outside the panel. */
public sealed interface FrameHudEvent {

    /** Activity on screen when the event fired, when one was bound. */
    public val screen: String?

    /** One line ready for a log, a notification or a test failure message. */
    public val summary: String

    /** The rolling window crossed [JankSeverity.WARNING]. Sent once per burst, not per frame. */
    public data class JankBurst(val diagnosis: JankDiagnosis, override val screen: String?) : FrameHudEvent {
        override val summary: String get() = "${screen.orNoScreen()}: ${diagnosis.summary}"
    }

    /** Frames over 700 ms seen since the previous sample — the Play Vitals definition. */
    public data class FrozenFrames(val count: Int, override val screen: String?) : FrameHudEvent {
        override val summary: String get() = "${screen.orNoScreen()}: $count frozen frame(s)"
    }

    public data class ThermalChanged(val level: ThermalLevel, override val screen: String?) : FrameHudEvent {
        override val summary: String
            get() = "${screen.orNoScreen()}: thermal status is now ${level.name.lowercase(Locale.US)}"
    }

    /** Collection for one screen finished — it paused, was replaced, or the panel was disabled. */
    public data class ScreenEnded(val stats: SessionStats, override val screen: String?) : FrameHudEvent {
        override val summary: String
            get() = format(
                "%s: %d frames in %.1fs, jank %.1f%%, p95 %.1f ms, frozen %d",
                screen.orNoScreen(),
                stats.frames,
                stats.durationMs / MS_PER_SECOND,
                stats.jankPercent,
                stats.p95FrameMs,
                stats.frozenFrames,
            )
    }
}

/** Receives [FrameHudEvent]s on the metrics thread — do not block, and do not touch views. */
public fun interface FrameHudEventListener {
    public fun onEvent(event: FrameHudEvent)
}

private fun String?.orNoScreen(): String = this ?: "no screen"
