// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.internal.PERCENT
import com.timkrest.framehud.internal.formatInvariant
import java.util.Locale

public sealed interface JankCause {

    public val summary: String

    public data object None : JankCause {
        override val summary: String get() = "no jank"
    }

    @ConsistentCopyVisibility
    public data class Thermal private constructor(val level: ThermalLevel) : JankCause {
        override val summary: String get() = "thermal throttling (${level.name.lowercase(Locale.US)})"

        public companion object {
            @InternalFrameHudApi
            public fun of(level: ThermalLevel): Thermal = Thermal(level = level)
        }
    }

    /** [timePercent] is the share of the session spent in GC pauses, 0..100. */
    @ConsistentCopyVisibility
    public data class Gc private constructor(val timePercent: Float) : JankCause {
        override val summary: String get() = formatInvariant("GC pauses take %.1f%% of the session", timePercent)

        public companion object {
            @InternalFrameHudApi
            public fun of(timePercent: Float): Gc = Gc(timePercent = timePercent)
        }
    }

    /** Ticks keep arriving on a still screen, so a low count means a blocked main thread, not an idle one. */
    @ConsistentCopyVisibility
    public data class VsyncStarvation private constructor(val ticksPerSecond: Int, val refreshRateHz: Float) : JankCause {
        override val summary: String
            get() = formatInvariant("main thread handled %d ticks/s on a %.0f Hz display", ticksPerSecond, refreshRateHz)

        public companion object {
            @InternalFrameHudApi
            public fun of(ticksPerSecond: Int, refreshRateHz: Float): VsyncStarvation =
                VsyncStarvation(ticksPerSecond = ticksPerSecond, refreshRateHz = refreshRateHz)
        }
    }

    /** Frames start late: work queued before rendering is holding the main thread. */
    @ConsistentCopyVisibility
    public data class LateStart private constructor(val delayMs: Float) : JankCause {
        override val summary: String get() = formatInvariant("frames start %.1f ms late", delayMs)

        public companion object {
            @InternalFrameHudApi
            public fun of(delayMs: Float): LateStart = LateStart(delayMs = delayMs)
        }
    }

    @ConsistentCopyVisibility
    public data class Stage private constructor(val stage: PipelineStage, val averageMs: Float) : JankCause {
        override val summary: String
            get() = formatInvariant("%s bound, %.1f ms per frame", stage.name.lowercase(Locale.US), averageMs)

        public companion object {
            @InternalFrameHudApi
            public fun of(stage: PipelineStage, averageMs: Float): Stage = Stage(stage = stage, averageMs = averageMs)
        }
    }
}

public enum class JankSeverity {
    NONE,
    WARNING,
    SEVERE,
    ;

    public companion object {
        public const val WARNING_JANK_PERCENT: Float = 5f
        public const val SEVERE_JANK_PERCENT: Float = 20f

        public fun of(jankPercent: Float): JankSeverity = when {
            jankPercent >= SEVERE_JANK_PERCENT -> SEVERE
            jankPercent >= WARNING_JANK_PERCENT -> WARNING
            else -> NONE
        }
    }
}

/** Diagnosis for the current rolling window. */
@Immutable
@ConsistentCopyVisibility
public data class JankDiagnosis private constructor(
    val cause: JankCause,
    val severity: JankSeverity,
    val jankPercent: Float,
    val worstFrameMs: Float,
    val frameBudgetMs: Float,
) {
    public val summary: String
        get() = formatInvariant(
            "jank %.1f%%, worst %.1f ms of %.1f ms — %s",
            jankPercent,
            worstFrameMs,
            frameBudgetMs,
            cause.summary,
        )

    public companion object {
        public val HEALTHY: JankDiagnosis = JankDiagnosis(
            cause = JankCause.None,
            severity = JankSeverity.NONE,
            jankPercent = 0f,
            worstFrameMs = 0f,
            frameBudgetMs = 0f,
        )

        @InternalFrameHudApi
        public fun of(
            cause: JankCause,
            severity: JankSeverity,
            jankPercent: Float,
            worstFrameMs: Float,
            frameBudgetMs: Float,
        ): JankDiagnosis = JankDiagnosis(
            cause = cause,
            severity = severity,
            jankPercent = jankPercent,
            worstFrameMs = worstFrameMs,
            frameBudgetMs = frameBudgetMs,
        )

        private const val MIN_GC_TIME_PERCENT = 2f

        private const val VSYNC_STARVATION_RATIO = 0.7f

        @InternalFrameHudApi
        public fun of(
            metrics: PerformanceMetrics,
            memory: MemoryStats,
            thermal: ThermalStats,
            choreographerTicksPerSecond: Int,
        ): JankDiagnosis {
            val severity = JankSeverity.of(metrics.window.jankPercent)
            if (severity == JankSeverity.NONE) return HEALTHY

            val phases = metrics.phases
            val refreshRateHz = metrics.display.refreshRateHz
            val gcTimePercent = gcTimePercent(memory, metrics.session)
            val cause = when {
                thermal.level.isThrottling -> JankCause.Thermal.of(thermal.level)
                gcTimePercent >= MIN_GC_TIME_PERCENT -> JankCause.Gc.of(gcTimePercent)
                isVsyncStarved(choreographerTicksPerSecond, refreshRateHz) ->
                    JankCause.VsyncStarvation.of(choreographerTicksPerSecond, refreshRateHz)

                phases.unknownDelay.average > phases.bottleneck.average ->
                    JankCause.LateStart.of(phases.unknownDelay.average)

                else -> JankCause.Stage.of(phases.bottleneckStage, phases.bottleneck.average)
            }
            return JankDiagnosis(
                cause = cause,
                severity = severity,
                jankPercent = metrics.window.jankPercent,
                worstFrameMs = metrics.window.worstFrameMs,
                frameBudgetMs = metrics.window.frameBudgetMs,
            )
        }

        private fun gcTimePercent(memory: MemoryStats, session: IntervalStats): Float =
            if (session.durationMs > 0L) memory.gcTimeMs.toFloat() / session.durationMs * PERCENT else 0f

        private fun isVsyncStarved(choreographerTicksPerSecond: Int, refreshRateHz: Float): Boolean =
            choreographerTicksPerSecond > 0 &&
                choreographerTicksPerSecond < refreshRateHz * VSYNC_STARVATION_RATIO
    }
}
