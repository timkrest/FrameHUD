package com.timkrest.framehud

import androidx.annotation.WorkerThread
import com.timkrest.framehud.internal.MS_PER_SECOND
import com.timkrest.framehud.internal.formatInvariant
import java.util.Locale

public sealed interface FrameHudEvent {

    /** Screen in focus when the event fired: `FrameHud.screen` when set, the activity class otherwise. */
    public val screen: String?

    /** Interaction open when the event fired, from `FrameHud.mark`. */
    public val mark: String?

    /** Measurement context pairs set when the event fired, from `FrameHud.context`. */
    public val context: Map<String, String>

    /** One line ready for a log, a notification or a test failure message. */
    public val summary: String

    /**
     * Emitted at most once for each Activity instance, and only on API 29+, where
     * `onActivityPreCreated` provides a start timestamp before `onCreate`. [timeToDisplayMs] spans
     * that callback and the end of the first frame. The frame itself is not included in rolling or
     * session stats.
     */
    public data class FirstFrame(
        val timeToDisplayMs: Float,
        override val screen: String?,
        override val context: Map<String, String> = emptyMap(),
    ) : FrameHudEvent {

        /** Always null. */
        override val mark: String? get() = null

        override val summary: String get() = formatInvariant("%s: first frame in %.1f ms", origin(), timeToDisplayMs)
    }

    /**
     * Emitted at most once per measured screen, after the app reported it usable and the next
     * frame was displayed. [timeToUsableMs] spans the start of that screen and the end of that
     * frame: a new Activity starts at its creation, before `onCreate` on API 29+ and at
     * `super.onCreate` below, any other screen when its measurement began. The frame counts in
     * rolling and session stats unless it is also the first draw, which does not.
     */
    public data class UsableFrame(
        val timeToUsableMs: Float,
        override val screen: String?,
        override val context: Map<String, String> = emptyMap(),
    ) : FrameHudEvent {

        /** Always null. */
        override val mark: String? get() = null

        override val summary: String get() = formatInvariant("%s: usable in %.1f ms", origin(), timeToUsableMs)
    }

    public sealed interface IncidentTrigger : FrameHudEvent

    /** The rolling window crossed [JankSeverity.WARNING]. Sent once per burst, not per frame. */
    public data class JankBurst(
        val diagnosis: JankDiagnosis,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String> = emptyMap(),
    ) : IncidentTrigger {
        override val summary: String get() = "${origin()}: ${diagnosis.summary}"
    }

    /** Frames over [IntervalStats.FROZEN_FRAME_MS] seen since the previous sample. */
    public data class FrozenFrames(
        val count: Int,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String> = emptyMap(),
    ) : IncidentTrigger {
        override val summary: String get() = "${origin()}: $count frozen frame(s)"
    }

    public data class ThermalChanged(
        val level: ThermalLevel,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String> = emptyMap(),
    ) : FrameHudEvent {
        override val summary: String
            get() = "${origin()}: thermal status is now ${level.name.lowercase(Locale.US)}"
    }

    /**
     * Collection ended because the screen paused, was replaced, renamed, or FrameHud was disabled.
     * [stats] cover only the frames drawn on that screen.
     */
    public data class ScreenEnded(
        val stats: IntervalStats,
        override val screen: String?,
        override val context: Map<String, String> = emptyMap(),
    ) : FrameHudEvent {

        /** Always null. */
        override val mark: String? get() = null

        override val summary: String get() = stats.summarize(origin())
    }

    /**
     * An interaction ended because `FrameHud.mark` was cleared or its screen went away. [stats]
     * contain only frames drawn while the mark was active.
     */
    public data class MarkEnded(
        val stats: IntervalStats,
        override val screen: String?,
        override val mark: String,
        override val context: Map<String, String> = emptyMap(),
    ) : FrameHudEvent {
        override val summary: String get() = stats.summarize(origin())
    }
}

/** Every listener shares one thread with collection, so a slow [onEvent] costs readings. */
public fun interface FrameHudEventListener {
    @WorkerThread
    public fun onEvent(event: FrameHudEvent)
}

private fun FrameHudEvent.origin(): String {
    val name = listOfNotNull(screen, mark).joinToString(separator = "/").ifEmpty { "no screen" }
    if (context.isEmpty()) return name
    return context.entries.joinToString(separator = ", ", prefix = "$name [", postfix = "]") { (key, value) ->
        "$key=$value"
    }
}

private fun IntervalStats.summarize(origin: String): String {
    val summary = formatInvariant(
        "%s: %d frames in %.1fs, jank %.1f%%, lost %.0f ms, p95 %.1f ms, frozen %d",
        origin,
        frames,
        durationMs / MS_PER_SECOND,
        jankPercent,
        lostTimeMs,
        p95FrameMs,
        frozenFrames,
    )
    return if (confidence.isSuspect) "$summary (suspect measurement)" else summary
}
