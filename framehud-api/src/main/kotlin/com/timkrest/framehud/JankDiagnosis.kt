package com.timkrest.framehud

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.internal.PERCENT
import java.util.Locale

public sealed interface JankCause {

    public val summary: String

    public data object None : JankCause {
        override val summary: String get() = "no jank"
    }

    public data class Thermal(val level: ThermalLevel) : JankCause {
        override val summary: String get() = "thermal throttling (${level.name.lowercase(Locale.US)})"
    }

    /** [timeShare] is the fraction of the session spent in GC pauses, 0..1. */
    public data class Gc(val timeShare: Float) : JankCause {
        override val summary: String get() = formatInvariant("GC pauses take %.1f%% of the session", timeShare * PERCENT)
    }

    /** Ticks keep arriving on a still screen, so a low count means a blocked main thread, not an idle one. */
    public data class VsyncStarvation(val ticksPerSecond: Int, val refreshRateHz: Float) : JankCause {
        override val summary: String
            get() = formatInvariant("main thread handled %d ticks/s on a %.0f Hz display", ticksPerSecond, refreshRateHz)
    }

    /** Frames start late: work queued before rendering is holding the main thread. */
    public data class LateStart(val delayMs: Float) : JankCause {
        override val summary: String get() = formatInvariant("frames start %.1f ms late", delayMs)
    }

    public data class Stage(val stage: PipelineStage, val averageMs: Float) : JankCause {
        override val summary: String
            get() = formatInvariant("%s bound, %.1f ms per frame", stage.name.lowercase(Locale.US), averageMs)
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
public data class JankDiagnosis(
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

        private const val MIN_GC_TIME_SHARE = 0.02f

        private const val VSYNC_STARVATION_RATIO = 0.7f

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
            val gcTimeShare = gcTimeShare(memory, metrics.session)
            val cause = when {
                thermal.level.isThrottling -> JankCause.Thermal(thermal.level)
                gcTimeShare >= MIN_GC_TIME_SHARE -> JankCause.Gc(gcTimeShare)
                isVsyncStarved(choreographerTicksPerSecond, refreshRateHz) ->
                    JankCause.VsyncStarvation(choreographerTicksPerSecond, refreshRateHz)

                phases.unknownDelay.average > phases.bottleneck.average ->
                    JankCause.LateStart(phases.unknownDelay.average)

                else -> JankCause.Stage(phases.bottleneckStage, phases.bottleneck.average)
            }
            return JankDiagnosis(
                cause = cause,
                severity = severity,
                jankPercent = metrics.window.jankPercent,
                worstFrameMs = metrics.window.worstFrameMs,
                frameBudgetMs = metrics.display.frameBudgetMs,
            )
        }

        private fun gcTimeShare(memory: MemoryStats, session: SessionStats): Float =
            if (session.durationMs > 0L) memory.gcTimeMs.toFloat() / session.durationMs else 0f

        private fun isVsyncStarved(choreographerTicksPerSecond: Int, refreshRateHz: Float): Boolean =
            choreographerTicksPerSecond > 0 &&
                choreographerTicksPerSecond < refreshRateHz * VSYNC_STARVATION_RATIO
    }
}
