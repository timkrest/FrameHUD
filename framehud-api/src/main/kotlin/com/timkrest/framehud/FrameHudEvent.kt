// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.annotation.WorkerThread
import com.timkrest.framehud.internal.MS_PER_SECOND
import com.timkrest.framehud.internal.formatInvariant
import java.util.Locale

public sealed interface FrameHudEvent {

    /** Screen in focus when the event fired: `FrameHud.screen` when set, the fragment or activity class otherwise. */
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
    @ConsistentCopyVisibility
    public data class FirstFrame private constructor(
        val timeToDisplayMs: Float,
        override val screen: String?,
        override val context: Map<String, String>,
    ) : FrameHudEvent {

        /** Always null. */
        override val mark: String? get() = null

        override val summary: String get() = formatInvariant("%s: first frame in %.1f ms", origin(), timeToDisplayMs)

        public companion object {
            @InternalFrameHudApi
            public fun of(
                timeToDisplayMs: Float,
                screen: String?,
                context: Map<String, String> = emptyMap(),
            ): FirstFrame = FirstFrame(
                timeToDisplayMs = timeToDisplayMs,
                screen = screen,
                context = context,
            )
        }
    }

    /**
     * Emitted at most once per measured screen, after the app reported it usable and the next
     * frame was displayed. [timeToUsableMs] spans the start of that screen and the end of that
     * frame: a new Activity starts at its creation, before `onCreate` on API 29+ and at
     * `super.onCreate` below, any other screen when its measurement began. The frame counts in
     * rolling and session stats unless it is also the first draw, which does not.
     */
    @ConsistentCopyVisibility
    public data class UsableFrame private constructor(
        val timeToUsableMs: Float,
        override val screen: String?,
        override val context: Map<String, String>,
    ) : FrameHudEvent {

        /** Always null. */
        override val mark: String? get() = null

        override val summary: String get() = formatInvariant("%s: usable in %.1f ms", origin(), timeToUsableMs)

        public companion object {
            @InternalFrameHudApi
            public fun of(
                timeToUsableMs: Float,
                screen: String?,
                context: Map<String, String> = emptyMap(),
            ): UsableFrame = UsableFrame(
                timeToUsableMs = timeToUsableMs,
                screen = screen,
                context = context,
            )
        }
    }

    public sealed interface IncidentTrigger : FrameHudEvent

    /** The rolling window crossed [JankSeverity.WARNING]. Sent once per burst, not per frame. */
    @ConsistentCopyVisibility
    public data class JankBurst private constructor(
        val diagnosis: JankDiagnosis,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String>,
    ) : IncidentTrigger {
        override val summary: String get() = "${origin()}: ${diagnosis.summary}"

        public companion object {
            @InternalFrameHudApi
            public fun of(
                diagnosis: JankDiagnosis,
                screen: String?,
                mark: String?,
                context: Map<String, String> = emptyMap(),
            ): JankBurst = JankBurst(
                diagnosis = diagnosis,
                screen = screen,
                mark = mark,
                context = context,
            )
        }
    }

    /** Frames over [IntervalStats.FROZEN_FRAME_MS] seen since the previous sample. */
    @ConsistentCopyVisibility
    public data class FrozenFrames private constructor(
        val count: Int,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String>,
    ) : IncidentTrigger {
        override val summary: String get() = "${origin()}: $count frozen frame(s)"

        public companion object {
            @InternalFrameHudApi
            public fun of(
                count: Int,
                screen: String?,
                mark: String?,
                context: Map<String, String> = emptyMap(),
            ): FrozenFrames = FrozenFrames(
                count = count,
                screen = screen,
                mark = mark,
                context = context,
            )
        }
    }

    @ConsistentCopyVisibility
    public data class ThermalChanged private constructor(
        val level: ThermalLevel,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String>,
    ) : FrameHudEvent {
        override val summary: String
            get() = "${origin()}: thermal status is now ${level.name.lowercase(Locale.US)}"

        public companion object {
            @InternalFrameHudApi
            public fun of(
                level: ThermalLevel,
                screen: String?,
                mark: String?,
                context: Map<String, String> = emptyMap(),
            ): ThermalChanged = ThermalChanged(
                level = level,
                screen = screen,
                mark = mark,
                context = context,
            )
        }
    }

    /**
     * Collection ended because the screen paused, was replaced, renamed, or FrameHud was disabled.
     * [stats] cover only the frames drawn on that screen.
     */
    @ConsistentCopyVisibility
    public data class ScreenEnded private constructor(
        val stats: IntervalStats,
        override val screen: String?,
        override val context: Map<String, String>,
    ) : FrameHudEvent {

        /** Always null. */
        override val mark: String? get() = null

        override val summary: String get() = stats.summarize(origin())

        public companion object {
            @InternalFrameHudApi
            public fun of(
                stats: IntervalStats,
                screen: String?,
                context: Map<String, String> = emptyMap(),
            ): ScreenEnded = ScreenEnded(
                stats = stats,
                screen = screen,
                context = context,
            )
        }
    }

    /**
     * An interaction ended because `FrameHud.mark` was cleared or its screen went away. [stats]
     * contain only frames drawn while the mark was active.
     */
    @ConsistentCopyVisibility
    public data class MarkEnded private constructor(
        val stats: IntervalStats,
        override val screen: String?,
        override val mark: String,
        override val context: Map<String, String>,
    ) : FrameHudEvent {
        override val summary: String get() = stats.summarize(origin())

        public companion object {
            @InternalFrameHudApi
            public fun of(
                stats: IntervalStats,
                screen: String?,
                mark: String,
                context: Map<String, String> = emptyMap(),
            ): MarkEnded = MarkEnded(
                stats = stats,
                screen = screen,
                mark = mark,
                context = context,
            )
        }
    }

    /**
     * FrameHUD caught a failure of its own while it did [what], and went on measuring without
     * whatever that call would have given. Emitted once per [what] until `FrameHud.reset()`; the
     * stack trace reaches logcat every time, where it happened.
     */
    @ConsistentCopyVisibility
    public data class InternalFailure private constructor(
        val what: String,
        val error: Throwable,
        override val screen: String?,
        override val mark: String?,
        override val context: Map<String, String>,
    ) : FrameHudEvent {
        override val summary: String get() = "${origin()}: FrameHUD failed while $what ($error)"

        public companion object {
            @InternalFrameHudApi
            public fun of(
                what: String,
                error: Throwable,
                screen: String?,
                mark: String?,
                context: Map<String, String> = emptyMap(),
            ): InternalFailure = InternalFailure(
                what = what,
                error = error,
                screen = screen,
                mark = mark,
                context = context,
            )
        }
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
